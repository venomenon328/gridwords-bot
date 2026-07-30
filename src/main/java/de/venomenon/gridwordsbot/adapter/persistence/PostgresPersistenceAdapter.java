package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionConflictException;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

/**
 * PostgreSQL-specific, conflict-safe implementation of the persistence ports.
 *
 * <p>The concrete Spring bean is {@link LegacyCompatiblePostgresPersistenceAdapter}; this base class stays directly
 * constructible for focused integration tests and contains the common persistence implementation.</p>
 */
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
                ON CONFLICT (discord_user_id) DO UPDATE SET
                    display_name = EXCLUDED.display_name,
                    active = EXCLUDED.active,
                    administrator = EXCLUDED.administrator,
                    updated_at = EXCLUDED.updated_at
                RETURNING *
                """, PLAYER, request.discordUserId(), request.displayName(), request.active(), request.administrator(),
                timestamp(now), timestamp(now));
    }

    @Override
    public Optional<StoredPlayer> findByDiscordUserId(long discordUserId) {
        return jdbc.query("SELECT * FROM player WHERE discord_user_id = ?", PLAYER, discordUserId).stream().findFirst();
    }

    @Override
    public StoredGameResult upsert(GameResultUpsert request) {
        ParsedGameResult parsed = request.parsedResult();
        boolean solved = parsed.outcome() instanceof ShareOutcome.Solved;
        Integer attempts = solved ? ((ShareOutcome.Solved) parsed.outcome()).attemptsUsed() : null;
        String board = parsed.board().map(NormalizedBoard::canonicalText).orElse(null);
        QuadWordsBoards quadWordsBoards = parsed.quadWordsBoards().orElse(null);
        Instant now = clock.instant();
        return jdbc.queryForObject("""
                INSERT INTO game_result(
                    player_id, game_type, game_date, solved, attempts_used, max_attempts, duration_seconds,
                    gridgames_streak, normalized_board,
                    quadwords_top_left_board, quadwords_top_right_board,
                    quadwords_bottom_left_board, quadwords_bottom_right_board,
                    raw_share_text, parser_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (player_id, game_type, game_date) DO UPDATE SET
                    solved = EXCLUDED.solved,
                    attempts_used = EXCLUDED.attempts_used,
                    max_attempts = EXCLUDED.max_attempts,
                    duration_seconds = EXCLUDED.duration_seconds,
                    gridgames_streak = EXCLUDED.gridgames_streak,
                    normalized_board = EXCLUDED.normalized_board,
                    quadwords_top_left_board = EXCLUDED.quadwords_top_left_board,
                    quadwords_top_right_board = EXCLUDED.quadwords_top_right_board,
                    quadwords_bottom_left_board = EXCLUDED.quadwords_bottom_left_board,
                    quadwords_bottom_right_board = EXCLUDED.quadwords_bottom_right_board,
                    raw_share_text = EXCLUDED.raw_share_text,
                    parser_version = EXCLUDED.parser_version,
                    updated_at = EXCLUDED.updated_at,
                    version = game_result.version + 1
                RETURNING *
                """, RESULT, request.playerId(), parsed.gameType().name(), parsed.gameDate(), solved, attempts,
                parsed.outcome().maxAttempts(), parsed.duration().getSeconds(), optionalInteger(parsed.gridgamesStreak()),
                board,
                quadWordsBoards == null ? null : quadWordsBoards.topLeft().canonicalText(),
                quadWordsBoards == null ? null : quadWordsBoards.topRight().canonicalText(),
                quadWordsBoards == null ? null : quadWordsBoards.bottomLeft().canonicalText(),
                quadWordsBoards == null ? null : quadWordsBoards.bottomRight().canonicalText(),
                request.rawShareText(), request.parserVersion(), timestamp(now), timestamp(now));
    }

    @Override
    public Optional<StoredGameResult> find(long playerId, GameType gameType, LocalDate gameDate) {
        return jdbc.query(
                "SELECT * FROM game_result WHERE player_id = ? AND game_type = ? AND game_date = ?",
                RESULT,
                playerId,
                gameType.name(),
                gameDate).stream().findFirst();
    }

    @Override
    public Optional<StoredGameResult> findById(long id) {
        return jdbc.query("SELECT * FROM game_result WHERE id = ?", RESULT, id).stream().findFirst();
    }

    @Override
    public List<StoredGameResult> findAll() {
        return jdbc.query("SELECT * FROM game_result", RESULT);
    }

    @Override
    public List<StoredGameResult> findByPlayer(long playerId) {
        return jdbc.query("SELECT * FROM game_result WHERE player_id = ? ORDER BY game_date, game_type", RESULT, playerId);
    }

    @Override
    public StoredSubmission register(SubmissionRegistration request) {
        StoredSubmission submission = jdbc.queryForObject("""
                INSERT INTO submission(
                    source_message_id, guild_id, channel_id, author_id, raw_content, processing_state,
                    received_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'RECEIVED', ?, ?, ?)
                ON CONFLICT (source_message_id) DO UPDATE SET
                    source_message_id = EXCLUDED.source_message_id
                RETURNING *
                """, SUBMISSION, request.sourceMessageId(), request.guildId(), request.channelId(), request.authorId(),
                request.rawContent(), timestamp(request.receivedAt()), timestamp(clock.instant()), timestamp(clock.instant()));
        for (AttachmentSnapshot attachment : request.attachments()) {
            jdbc.update("""
                    INSERT INTO submission_attachment(
                        submission_id, attachment_index, filename, content_type, size_bytes, created_at)
                    VALUES ((SELECT id FROM submission WHERE source_message_id = ?), ?, ?, ?, ?, ?)
                    ON CONFLICT (submission_id, attachment_index) DO NOTHING
                    """, request.sourceMessageId(), attachment.index(), attachment.filename(),
                    attachment.contentType().orElse(null), attachment.size(), timestamp(clock.instant()));
        }
        return findBySourceMessageId(request.sourceMessageId()).orElseThrow();
    }

    @Override
    public void reject(RejectedSubmission request) {
        jdbc.update("""
                UPDATE submission
                SET processing_state = 'PARSE_REJECTED', parser_error_code = ?, updated_at = ?, version = version + 1
                WHERE source_message_id = ?
                  AND processing_state IN ('RECEIVED', 'VALIDATED', 'FAILED_RETRYABLE')
                """, request.parserErrorCode(), timestamp(clock.instant()), request.sourceMessageId());
    }

    @Override
    @Transactional
    public StoredSubmission storeResult(ResultStorage request) {
        StoredSubmission current = findBySourceMessageId(request.sourceMessageId()).orElseThrow();
        if (current.gameResultId().isPresent()) {
            return current;
        }
        if (current.state() != SubmissionState.RECEIVED
                && current.state() != SubmissionState.VALIDATED
                && current.state() != SubmissionState.FAILED_RETRYABLE) {
            return current;
        }
        StoredGameResult result = upsert(request.result());
        supersedeOlderSubmissions(request, result.id());
        jdbc.update("""
                UPDATE submission
                SET game_result_id = ?, processing_state = 'RESULT_STORED', parser_error_code = NULL,
                    technical_error_message = NULL, updated_at = ?, version = version + 1
                WHERE source_message_id = ?
                  AND game_result_id IS NULL
                  AND processing_state IN ('RECEIVED', 'VALIDATED', 'FAILED_RETRYABLE')
                """, result.id(), timestamp(clock.instant()), request.sourceMessageId());
        return findBySourceMessageId(request.sourceMessageId()).orElseThrow();
    }

    private void supersedeOlderSubmissions(ResultStorage request, long resultId) {
        jdbc.update("""
                UPDATE submission
                SET processing_state = 'SUPERSEDED', superseded_by_source_message_id = ?, updated_at = ?, version = version + 1
                WHERE game_result_id = ?
                  AND source_message_id <> ?
                  AND processing_state IN ('RESULT_STORED', 'CANONICAL_MESSAGE_PUBLISHED', 'FAILED_RETRYABLE')
                """, request.sourceMessageId(), timestamp(clock.instant()), resultId, request.sourceMessageId());
    }

    @Override
    public Optional<StoredSubmission> findBySourceMessageId(long sourceMessageId) {
        return jdbc.query("SELECT * FROM submission WHERE source_message_id = ?", SUBMISSION, sourceMessageId)
                .stream().findFirst();
    }

    @Override
    public List<StoredSubmission> findOpenCanonicalPublications() {
        return jdbc.query("""
                SELECT * FROM submission
                WHERE processing_state = 'RESULT_STORED'
                  AND game_result_id IS NOT NULL
                ORDER BY received_at, source_message_id
                """, SUBMISSION);
    }

    @Override
    public List<StoredSubmission> findOpenGridWordsSourceDeletions() {
        return jdbc.query("""
                SELECT s.*
                FROM submission s
                JOIN game_result g ON g.id = s.game_result_id
                WHERE g.game_type = 'GRIDWORDS'
                  AND s.processing_state IN (
                      'CANONICAL_MESSAGE_PUBLISHED', 'ORIGINAL_MESSAGE_DELETED', 'FAILED_RETRYABLE', 'SUPERSEDED')
                  AND s.original_deleted_at IS NULL
                ORDER BY s.received_at, s.source_message_id
                """, SUBMISSION);
    }

    @Override
    public List<StoredSubmission> findSupersededGridWordsSourcesReadyForDeletion(long gameResultId) {
        return jdbc.query("""
                SELECT s.*
                FROM submission s
                JOIN game_result g ON g.id = s.game_result_id
                WHERE s.game_result_id = ?
                  AND g.game_type = 'GRIDWORDS'
                  AND s.processing_state = 'SUPERSEDED'
                  AND s.original_deleted_at IS NULL
                  AND g.canonical_message_id IS NOT NULL
                  AND g.canonical_refresh_required = FALSE
                ORDER BY s.received_at, s.source_message_id
                """, SUBMISSION, gameResultId);
    }

    @Override
    public Optional<StoredSubmission> claimGridWordsSourceDeletion(
            long sourceMessageId,
            String ownerToken,
            Instant leaseUntil) {
        int updated = jdbc.update("""
                UPDATE submission s
                SET source_delete_claim_token = ?, source_delete_lease_until = ?, updated_at = ?, version = version + 1
                FROM game_result g
                WHERE s.source_message_id = ?
                  AND s.game_result_id = g.id
                  AND g.game_type = 'GRIDWORDS'
                  AND g.canonical_message_id IS NOT NULL
                  AND g.canonical_refresh_required = FALSE
                  AND s.original_deleted_at IS NULL
                  AND s.processing_state IN (
                      'CANONICAL_MESSAGE_PUBLISHED', 'ORIGINAL_MESSAGE_DELETED', 'FAILED_RETRYABLE', 'SUPERSEDED')
                  AND (s.source_delete_claim_token IS NULL
                      OR s.source_delete_lease_until IS NULL
                      OR s.source_delete_lease_until <= ?)
                """, ownerToken, timestamp(leaseUntil), timestamp(clock.instant()), sourceMessageId,
                timestamp(clock.instant()));
        if (updated == 0) {
            return Optional.empty();
        }
        return findBySourceMessageId(sourceMessageId)
                .filter(submission -> submission.sourceDeleteClaimToken().filter(ownerToken::equals).isPresent());
    }

    @Override
    public boolean markOriginalMessageDeleted(long sourceMessageId, String ownerToken, Instant deletedAt) {
        int updated = jdbc.update("""
                UPDATE submission
                SET processing_state = 'ORIGINAL_MESSAGE_DELETED', original_deleted_at = ?,
                    source_delete_failure_class = 'NONE', technical_error_message = NULL,
                    updated_at = ?, version = version + 1
                WHERE source_message_id = ?
                  AND source_delete_claim_token = ?
                  AND processing_state IN (
                      'CANONICAL_MESSAGE_PUBLISHED', 'FAILED_RETRYABLE', 'SUPERSEDED', 'ORIGINAL_MESSAGE_DELETED')
                """, timestamp(deletedAt), timestamp(clock.instant()), sourceMessageId, ownerToken);
        return updated == 1;
    }

    @Override
    public boolean completeOriginalMessageDeletion(long sourceMessageId, String ownerToken) {
        int updated = jdbc.update("""
                UPDATE submission
                SET processing_state = 'COMPLETED', source_delete_claim_token = NULL,
                    source_delete_lease_until = NULL, source_delete_failure_class = 'NONE',
                    technical_error_message = NULL, updated_at = ?, version = version + 1
                WHERE source_message_id = ?
                  AND source_delete_claim_token = ?
                  AND processing_state = 'ORIGINAL_MESSAGE_DELETED'
                  AND original_deleted_at IS NOT NULL
                """, timestamp(clock.instant()), sourceMessageId, ownerToken);
        return updated == 1;
    }

    @Override
    public boolean completeOriginalMessageDeletionWithoutClaim(long sourceMessageId) {
        int updated = jdbc.update("""
                UPDATE submission
                SET processing_state = 'COMPLETED', source_delete_claim_token = NULL,
                    source_delete_lease_until = NULL, source_delete_failure_class = 'NONE',
                    technical_error_message = NULL, updated_at = ?, version = version + 1
                WHERE source_message_id = ?
                  AND processing_state = 'ORIGINAL_MESSAGE_DELETED'
                  AND original_deleted_at IS NOT NULL
                """, timestamp(clock.instant()), sourceMessageId);
        return updated == 1;
    }

    @Override
    public boolean markGridWordsSourceDeletionFailure(
            long sourceMessageId,
            String ownerToken,
            SourceDeleteFailureClass failureClass,
            String safeTechnicalMessage) {
        int updated = jdbc.update("""
                UPDATE submission
                SET processing_state = 'FAILED_RETRYABLE', source_delete_failure_class = ?,
                    technical_error_message = ?, source_delete_claim_token = NULL,
                    source_delete_lease_until = NULL, updated_at = ?, version = version + 1
                WHERE source_message_id = ?
                  AND source_delete_claim_token = ?
                  AND processing_state IN ('CANONICAL_MESSAGE_PUBLISHED', 'FAILED_RETRYABLE', 'SUPERSEDED')
                """, failureClass.name(), safeTechnicalMessage, timestamp(clock.instant()), sourceMessageId, ownerToken);
        return updated == 1;
    }

    @Override
    public void markRetryableFailure(long sourceMessageId, String safeTechnicalMessage) {
        jdbc.update("""
                UPDATE submission
                SET processing_state = 'FAILED_RETRYABLE', technical_error_message = ?, updated_at = ?, version = version + 1
                WHERE source_message_id = ?
                  AND processing_state IN ('RESULT_STORED', 'FAILED_RETRYABLE')
                """, safeTechnicalMessage, timestamp(clock.instant()), sourceMessageId);
    }

    @Override
    public boolean transition(long sourceMessageId, SubmissionState expectedState, SubmissionState targetState) {
        int updated = jdbc.update("""
                UPDATE submission SET processing_state = ?, updated_at = ?, version = version + 1
                WHERE source_message_id = ? AND processing_state = ?
                """, targetState.name(), timestamp(clock.instant()), sourceMessageId, expectedState.name());
        return updated == 1;
    }

    @Override
    public Optional<StoredSubmission> claimCanonicalPublication(long sourceMessageId, String ownerToken) {
        String token = ownerToken == null ? UUID.randomUUID().toString() : ownerToken;
        int updated = jdbc.update("""
                UPDATE submission
                SET publication_owner_token = ?, processing_state = 'RESULT_STORED', updated_at = ?, version = version + 1
                WHERE source_message_id = ?
                  AND game_result_id IS NOT NULL
                  AND processing_state IN ('RESULT_STORED', 'FAILED_RETRYABLE')
                  AND publication_owner_token IS NULL
                """, token, timestamp(clock.instant()), sourceMessageId);
        if (updated == 0) {
            return Optional.empty();
        }
        return findBySourceMessageId(sourceMessageId)
                .filter(submission -> submission.publicationOwnerToken().filter(token::equals).isPresent());
    }

    @Override
    public boolean markCanonicalMessagePublished(long sourceMessageId, String ownerToken, long canonicalMessageId) {
        int updated = jdbc.update("""
                UPDATE submission
                SET processing_state = 'CANONICAL_MESSAGE_PUBLISHED', publication_owner_token = NULL,
                    canonical_message_id = ?, technical_error_message = NULL, updated_at = ?, version = version + 1
                WHERE source_message_id = ?
                  AND publication_owner_token = ?
                  AND processing_state = 'RESULT_STORED'
                """, canonicalMessageId, timestamp(clock.instant()), sourceMessageId, ownerToken);
        return updated == 1;
    }

    @Override
    public boolean clearCanonicalPublicationClaim(long sourceMessageId, String ownerToken) {
        int updated = jdbc.update("""
                UPDATE submission
                SET publication_owner_token = NULL, updated_at = ?, version = version + 1
                WHERE source_message_id = ? AND publication_owner_token = ?
                """, timestamp(clock.instant()), sourceMessageId, ownerToken);
        return updated == 1;
    }

    @Override
    public boolean markSubmissionSuperseded(long sourceMessageId, long supersededBySourceMessageId) {
        int updated = jdbc.update("""
                UPDATE submission
                SET processing_state = 'SUPERSEDED', superseded_by_source_message_id = ?,
                    updated_at = ?, version = version + 1
                WHERE source_message_id = ?
                  AND source_message_id <> ?
                  AND processing_state IN ('RESULT_STORED', 'CANONICAL_MESSAGE_PUBLISHED', 'FAILED_RETRYABLE')
                """, supersededBySourceMessageId, timestamp(clock.instant()), sourceMessageId,
                supersededBySourceMessageId);
        return updated == 1;
    }

    @Override
    public Optional<StoredSubmission> findCanonicalPublicationOwner(long gameResultId) {
        return jdbc.query("""
                SELECT * FROM submission
                WHERE game_result_id = ? AND publication_owner_token IS NOT NULL
                ORDER BY received_at DESC, source_message_id DESC
                LIMIT 1
                """, SUBMISSION, gameResultId).stream().findFirst();
    }

    @Override
    public List<StoredSubmission> findSubmissionsForResult(long gameResultId) {
        return jdbc.query("""
                SELECT * FROM submission WHERE game_result_id = ? ORDER BY received_at, source_message_id
                """, SUBMISSION, gameResultId);
    }

    @Override
    public boolean setCanonicalMessageId(long gameResultId, long canonicalMessageId) {
        return jdbc.update("""
                UPDATE game_result
                SET canonical_message_id = ?, canonical_refresh_required = FALSE,
                    updated_at = ?, version = version + 1
                WHERE id = ?
                """, canonicalMessageId, timestamp(clock.instant()), gameResultId) == 1;
    }

    @Override
    public boolean markCanonicalRefreshRequired(long gameResultId) {
        return jdbc.update("""
                UPDATE game_result SET canonical_refresh_required = TRUE, updated_at = ?, version = version + 1
                WHERE id = ?
                """, timestamp(clock.instant()), gameResultId) == 1;
    }

    @Override
    public boolean clearCanonicalRefreshRequired(long gameResultId) {
        return jdbc.update("""
                UPDATE game_result SET canonical_refresh_required = FALSE, updated_at = ?, version = version + 1
                WHERE id = ?
                """, timestamp(clock.instant()), gameResultId) == 1;
    }

    @Override
    public boolean updateCanonicalMessageIdIfExpected(long gameResultId, OptionalLong expected, long replacement) {
        if (expected.isPresent()) {
            return jdbc.update("""
                    UPDATE game_result
                    SET canonical_message_id = ?, updated_at = ?, version = version + 1
                    WHERE id = ? AND canonical_message_id = ?
                    """, replacement, timestamp(clock.instant()), gameResultId, expected.getAsLong()) == 1;
        }
        return jdbc.update("""
                UPDATE game_result
                SET canonical_message_id = ?, updated_at = ?, version = version + 1
                WHERE id = ? AND canonical_message_id IS NULL
                """, replacement, timestamp(clock.instant()), gameResultId) == 1;
    }

    @Override
    public boolean clearCanonicalMessageIdIfExpected(long gameResultId, long expectedCanonicalMessageId) {
        return jdbc.update("""
                UPDATE game_result
                SET canonical_message_id = NULL, canonical_refresh_required = TRUE,
                    updated_at = ?, version = version + 1
                WHERE id = ? AND canonical_message_id = ?
                """, timestamp(clock.instant()), gameResultId, expectedCanonicalMessageId) == 1;
    }

    @Override
    public boolean insertCanonicalDeliveryAttempt(CanonicalDeliveryAttempt attempt) {
        return jdbc.update("""
                INSERT INTO canonical_delivery_attempt(
                    game_result_id, source_message_id, attempt_token, target_message_id,
                    delivery_kind, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (attempt_token) DO NOTHING
                """, attempt.gameResultId(), attempt.sourceMessageId(), attempt.attemptToken(),
                attempt.targetMessageId().isPresent() ? attempt.targetMessageId().getAsLong() : null,
                attempt.deliveryKind().name(), attempt.status().name(), timestamp(attempt.createdAt()),
                timestamp(attempt.updatedAt())) == 1;
    }

    @Override
    public Optional<CanonicalDeliveryAttempt> findCanonicalDeliveryAttempt(String attemptToken) {
        return jdbc.query("SELECT * FROM canonical_delivery_attempt WHERE attempt_token = ?", DELIVERY_ATTEMPT,
                attemptToken).stream().findFirst();
    }

    @Override
    public List<CanonicalDeliveryAttempt> findOpenCanonicalDeliveryAttempts() {
        return jdbc.query("""
                SELECT * FROM canonical_delivery_attempt
                WHERE status IN ('PREPARED', 'DISCORD_CONFIRMED')
                ORDER BY created_at, id
                """, DELIVERY_ATTEMPT);
    }

    @Override
    public boolean confirmCanonicalDeliveryAttempt(String attemptToken, long targetMessageId) {
        return jdbc.update("""
                UPDATE canonical_delivery_attempt
                SET status = 'DISCORD_CONFIRMED', target_message_id = ?, updated_at = ?
                WHERE attempt_token = ? AND status = 'PREPARED'
                """, targetMessageId, timestamp(clock.instant()), attemptToken) == 1;
    }

    @Override
    public boolean completeCanonicalDeliveryAttempt(String attemptToken) {
        return jdbc.update("""
                UPDATE canonical_delivery_attempt SET status = 'COMPLETED', updated_at = ?
                WHERE attempt_token = ? AND status IN ('PREPARED', 'DISCORD_CONFIRMED')
                """, timestamp(clock.instant()), attemptToken) == 1;
    }

    @Override
    public boolean abandonCanonicalDeliveryAttempt(String attemptToken) {
        return jdbc.update("""
                UPDATE canonical_delivery_attempt SET status = 'ABANDONED', updated_at = ?
                WHERE attempt_token = ? AND status IN ('PREPARED', 'DISCORD_CONFIRMED')
                """, timestamp(clock.instant()), attemptToken) == 1;
    }

    private static final RowMapper<StoredPlayer> PLAYER = (rs, row) -> new StoredPlayer(
            rs.getLong("discord_user_id"),
            rs.getString("display_name"),
            rs.getBoolean("active"),
            rs.getBoolean("administrator"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));

    private static final RowMapper<StoredGameResult> RESULT = (rs, row) -> {
        GameType type = GameType.valueOf(rs.getString("game_type"));
        int maximum = rs.getInt("max_attempts");
        ShareOutcome outcome = rs.getBoolean("solved")
                ? new ShareOutcome.Solved(rs.getInt("attempts_used"), maximum)
                : new ShareOutcome.Unsolved(maximum);
        String boardText = rs.getString("normalized_board");
        Optional<NormalizedBoard> board = boardText == null
                ? Optional.empty()
                : Optional.of(new NormalizedBoard(List.of(boardText.split("\\n"))));
        Optional<QuadWordsBoards> quadWordsBoards = type == GameType.QUADWORDS
                ? Optional.of(new QuadWordsBoards(
                        QuadWordsBoard.fromCanonicalText(required(rs, "quadwords_top_left_board")),
                        QuadWordsBoard.fromCanonicalText(required(rs, "quadwords_top_right_board")),
                        QuadWordsBoard.fromCanonicalText(required(rs, "quadwords_bottom_left_board")),
                        QuadWordsBoard.fromCanonicalText(required(rs, "quadwords_bottom_right_board"))))
                : Optional.empty();
        ParsedGameResult parsed = new ParsedGameResult(
                type,
                rs.getObject("game_date", LocalDate.class),
                outcome,
                Duration.ofSeconds(rs.getLong("duration_seconds")),
                optionalInt(rs, "gridgames_streak"),
                board,
                quadWordsBoards);
        Long messageId = rs.getObject("canonical_message_id", Long.class);
        return new StoredGameResult(
                rs.getLong("id"),
                rs.getLong("player_id"),
                parsed,
                rs.getString("raw_share_text"),
                rs.getString("parser_version"),
                messageId == null ? OptionalLong.empty() : OptionalLong.of(messageId),
                instant(rs, "created_at"),
                instant(rs, "updated_at"));
    };

    private static final RowMapper<StoredSubmission> SUBMISSION = (rs, row) -> {
        long sourceMessageId = rs.getLong("source_message_id");
        return new StoredSubmission(
                sourceMessageId,
                rs.getLong("guild_id"),
                rs.getLong("channel_id"),
                rs.getLong("author_id"),
                rs.getString("raw_content"),
                SubmissionState.valueOf(rs.getString("processing_state")),
                optionalLong(rs, "game_result_id"),
                List.of(),
                optionalString(rs, "parser_error_code"),
                optionalString(rs, "technical_error_message"),
                instant(rs, "received_at"),
                instant(rs, "updated_at"),
                optionalString(rs, "publication_owner_token"),
                optionalLong(rs, "superseded_by_source_message_id"),
                optionalLong(rs, "canonical_message_id"),
                optionalInstant(rs, "original_deleted_at"),
                SourceDeleteFailureClass.valueOf(rs.getString("source_delete_failure_class")),
                optionalString(rs, "source_delete_claim_token"),
                optionalInstant(rs, "source_delete_lease_until"));
    };

    private static final RowMapper<CanonicalDeliveryAttempt> DELIVERY_ATTEMPT = (rs, row) -> new CanonicalDeliveryAttempt(
            rs.getLong("game_result_id"),
            rs.getLong("source_message_id"),
            rs.getString("attempt_token"),
            optionalLongValue(rs, "target_message_id"),
            CanonicalDeliveryKind.valueOf(rs.getString("delivery_kind")),
            CanonicalDeliveryStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));

    private static String required(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        if (value == null) {
            throw new SQLException(column + " is required for a QuadWords result");
        }
        return value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Optional<Instant> optionalInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }

    private static Optional<String> optionalString(ResultSet rs, String column) throws SQLException {
        return Optional.ofNullable(rs.getString(column));
    }

    private static Optional<Long> optionalLong(ResultSet rs, String column) throws SQLException {
        Long value = rs.getObject(column, Long.class);
        return Optional.ofNullable(value);
    }

    private static OptionalLong optionalLongValue(ResultSet rs, String column) throws SQLException {
        Long value = rs.getObject(column, Long.class);
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }

    private static OptionalInt optionalInt(ResultSet rs, String column) throws SQLException {
        Integer value = rs.getObject(column, Integer.class);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    private static Integer optionalInteger(OptionalInt value) {
        return value.isPresent() ? value.getAsInt() : null;
    }

    private static OffsetDateTime timestamp(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
