package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import de.venomenon.gridwordsbot.port.out.SubmissionConflictException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL-specific, conflict-safe implementation of the persistence ports. */
@Repository
@Profile("database")
public class PostgresPersistenceAdapter implements PlayerStore, GameResultStore, SubmissionStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PostgresPersistenceAdapter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public StoredPlayer upsert(PlayerUpsert request) {
        Instant now = clock.instant();
        return jdbc.queryForObject("""
                INSERT INTO player (discord_user_id, display_name, active, administrator, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (discord_user_id) DO UPDATE SET display_name = EXCLUDED.display_name,
                    active = EXCLUDED.active, administrator = EXCLUDED.administrator, updated_at = EXCLUDED.updated_at
                RETURNING *
                """, PLAYER, request.discordUserId(), request.displayName(), request.active(), request.administrator(), databaseTime(now), databaseTime(now));
    }

    @Override
    public Optional<StoredPlayer> findByDiscordUserId(long discordUserId) {
        return jdbc.query("SELECT * FROM player WHERE discord_user_id = ?", PLAYER, discordUserId).stream().findFirst();
    }

    @Override
    public StoredGameResult upsert(GameResultUpsert request) {
        return upsertResult(request, clock.instant());
    }

    @Override
    public Optional<StoredGameResult> find(long playerId, GameType gameType, java.time.LocalDate gameDate) {
        return jdbc.query("SELECT * FROM game_result WHERE player_id = ? AND game_type = ? AND game_date = ?", RESULT, playerId, gameType.name(), gameDate).stream().findFirst();
    }

    @Override
    public StoredGameResult setCanonicalMessageId(long resultId, long canonicalMessageId) {
        if (resultId <= 0 || canonicalMessageId <= 0) throw new IllegalArgumentException("IDs must be positive");
        StoredGameResult result = jdbc.queryForObject("UPDATE game_result SET canonical_message_id = ?, updated_at = ?, version = version + 1 WHERE id = ? RETURNING *", RESULT, canonicalMessageId, databaseTime(clock.instant()), resultId);
        if (result == null) throw new IllegalStateException("game result not found: " + resultId);
        return result;
    }

    @Override
    @Transactional
    public StoredSubmission register(SubmissionRegistration registration) {
        Instant now = clock.instant();
        int inserted = jdbc.update("""
                INSERT INTO submission (source_message_id, guild_id, channel_id, author_player_id, raw_message_content,
                    processing_state, received_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'RECEIVED', ?, ?)
                ON CONFLICT (source_message_id) DO NOTHING
                """, registration.sourceMessageId(), registration.guildId(), registration.channelId(), registration.authorPlayerId(), registration.rawMessageContent(), databaseTime(registration.receivedAt()), databaseTime(now));
        if (inserted == 1) {
            for (AttachmentSnapshot attachment : registration.attachments()) {
                jdbc.update("INSERT INTO submission_attachment (source_message_id, attachment_index, file_name, content_type, size_bytes) VALUES (?, ?, ?, ?, ?)",
                        registration.sourceMessageId(), attachment.index(), attachment.fileName(), attachment.contentType().orElse(null), attachment.sizeBytes());
            }
        }
        StoredSubmission stored = findRequired(registration.sourceMessageId());
        if (!sameSubmission(stored, registration)) {
            throw new SubmissionConflictException("source message ID is already registered with different immutable data");
        }
        return stored;
    }

    @Override
    public Optional<StoredSubmission> findBySourceMessageId(long sourceMessageId) {
        return jdbc.query("SELECT * FROM submission WHERE source_message_id = ?", SUBMISSION, sourceMessageId).stream().findFirst();
    }

    @Override
    @Transactional
    public StoredSubmission storeResult(ResultStorage request) {
        StoredSubmission existing = findRequired(request.sourceMessageId());
        if (existing.authorPlayerId() != request.result().playerId()) {
            throw new SubmissionConflictException("result player does not match submission author");
        }
        StoredGameResult result = upsertResult(request.result(), clock.instant());
        int changed = jdbc.update("""
                UPDATE submission SET game_result_id = ?, processing_state = 'RESULT_STORED', updated_at = ?, version = version + 1
                WHERE source_message_id = ? AND processing_state IN ('RECEIVED', 'VALIDATED')
                """, result.id(), databaseTime(clock.instant()), request.sourceMessageId());
        if (changed != 1) throw new SubmissionConflictException("submission state changed during result storage");
        return findRequired(request.sourceMessageId());
    }

    @Override
    @Transactional
    public boolean transition(long sourceMessageId, SubmissionState expectedState, SubmissionState targetState) {
        return jdbc.update("UPDATE submission SET processing_state = ?, updated_at = ?, version = version + 1 WHERE source_message_id = ? AND processing_state = ?",
                targetState.name(), databaseTime(clock.instant()), sourceMessageId, expectedState.name()) == 1;
    }

    private StoredGameResult upsertResult(GameResultUpsert request, Instant now) {
        ParsedGameResult parsed = request.parsedResult();
        boolean solved = parsed.outcome() instanceof ShareOutcome.Solved;
        Integer attempts = solved ? ((ShareOutcome.Solved) parsed.outcome()).attemptsUsed() : null;
        return jdbc.queryForObject("""
                INSERT INTO game_result (player_id, game_type, game_date, solved, attempts_used, max_attempts, duration_seconds,
                    gridgames_streak, normalized_board, raw_share_text, parser_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (player_id, game_type, game_date) DO UPDATE SET solved = EXCLUDED.solved,
                    attempts_used = EXCLUDED.attempts_used, max_attempts = EXCLUDED.max_attempts,
                    duration_seconds = EXCLUDED.duration_seconds, gridgames_streak = EXCLUDED.gridgames_streak,
                    normalized_board = EXCLUDED.normalized_board, raw_share_text = EXCLUDED.raw_share_text,
                    parser_version = EXCLUDED.parser_version, updated_at = EXCLUDED.updated_at, version = game_result.version + 1
                RETURNING *
                """, RESULT, request.playerId(), parsed.gameType().name(), parsed.gameDate(), solved, attempts,
                parsed.outcome().maxAttempts(), parsed.duration().getSeconds(),
                parsed.gridgamesStreak().isPresent() ? parsed.gridgamesStreak().getAsInt() : null,
                parsed.board().map(NormalizedBoard::canonicalText).orElse(null), request.rawShareText(), request.parserVersion(), databaseTime(now), databaseTime(now));
    }

    private static boolean equivalent(StoredGameResult stored, GameResultUpsert request) {
        return stored.playerId() == request.playerId() && stored.parsedResult().equals(request.parsedResult())
                && stored.rawShareText().equals(request.rawShareText()) && stored.parserVersion().equals(request.parserVersion());
    }

    private StoredSubmission findRequired(long sourceMessageId) {
        return findBySourceMessageId(sourceMessageId).orElseThrow(() -> new IllegalStateException("submission not found: " + sourceMessageId));
    }

    private boolean sameSubmission(StoredSubmission stored, SubmissionRegistration incoming) {
        return stored.guildId() == incoming.guildId() && stored.channelId() == incoming.channelId()
                && stored.authorPlayerId() == incoming.authorPlayerId() && stored.rawMessageContent().equals(incoming.rawMessageContent())
                && stored.attachments().equals(incoming.attachments());
    }

    private static final RowMapper<StoredPlayer> PLAYER = (rs, row) -> new StoredPlayer(rs.getLong("discord_user_id"), rs.getString("display_name"), rs.getBoolean("active"), rs.getBoolean("administrator"), instant(rs, "created_at"), instant(rs, "updated_at"));
    private static final RowMapper<StoredGameResult> RESULT = (rs, row) -> {
        GameType type = GameType.valueOf(rs.getString("game_type"));
        int maximum = rs.getInt("max_attempts");
        ShareOutcome outcome = rs.getBoolean("solved") ? new ShareOutcome.Solved(rs.getInt("attempts_used"), maximum) : new ShareOutcome.Unsolved(maximum);
        String boardText = rs.getString("normalized_board");
        ParsedGameResult parsed = new ParsedGameResult(type, rs.getObject("game_date", java.time.LocalDate.class), outcome,
                Duration.ofSeconds(rs.getLong("duration_seconds")), optionalInt(rs, "gridgames_streak"),
                boardText == null ? Optional.empty() : Optional.of(new NormalizedBoard(List.of(boardText.split("\\n")))));
        Long messageId = rs.getObject("canonical_message_id", Long.class);
        return new StoredGameResult(rs.getLong("id"), rs.getLong("player_id"), parsed, rs.getString("raw_share_text"), rs.getString("parser_version"), messageId == null ? OptionalLong.empty() : OptionalLong.of(messageId), instant(rs, "created_at"), instant(rs, "updated_at"));
    };
    private final RowMapper<StoredSubmission> SUBMISSION = (rs, row) -> new StoredSubmission(rs.getLong("source_message_id"), rs.getLong("guild_id"), rs.getLong("channel_id"), rs.getLong("author_player_id"), rs.getString("raw_message_content"), SubmissionState.valueOf(rs.getString("processing_state")), Optional.ofNullable(rs.getObject("game_result_id", Long.class)), attachments(rs.getLong("source_message_id")), instant(rs, "received_at"), instant(rs, "updated_at"));

    private List<AttachmentSnapshot> attachments(long sourceMessageId) {
        return jdbc.query("SELECT * FROM submission_attachment WHERE source_message_id = ? ORDER BY attachment_index", (rs, row) -> new AttachmentSnapshot(rs.getInt("attachment_index"), rs.getString("file_name"), Optional.ofNullable(rs.getString("content_type")), rs.getLong("size_bytes")), sourceMessageId);
    }
    private static OffsetDateTime databaseTime(Instant instant) { return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC); }
    private static Instant instant(ResultSet rs, String column) throws SQLException { return rs.getObject(column, OffsetDateTime.class).toInstant(); }
    private static OptionalInt optionalInt(ResultSet rs, String column) throws SQLException { Integer value = rs.getObject(column, Integer.class); return value == null ? OptionalInt.empty() : OptionalInt.of(value); }
}
