package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter used only by the explicit legacy QuadWords upgrade compatibility profile.
 *
 * <p>Version-one QuadWords rows legitimately have no normalized boards. New results must contain all four boards.
 * This adapter reads both representations and upgrades a legacy row atomically when a new image-backed submission for
 * the same player and game day is stored. It is deliberately not a production database-profile candidate.</p>
 */
@Repository
@Profile("legacy-quadwords-compatibility")
public class LegacyCompatiblePostgresPersistenceAdapter extends PostgresPersistenceAdapter {

    private static final String LEGACY_QUADWORDS_PARSER_VERSION = "quadwords-share-v1";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public LegacyCompatiblePostgresPersistenceAdapter(JdbcTemplate jdbc, Clock clock) {
        super(jdbc, clock);
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public StoredPlayer upsert(de.venomenon.gridwordsbot.port.out.PlayerStore.PlayerUpsert request) {
        Instant now = clock.instant();
        return jdbc.queryForObject("""
                INSERT INTO player (discord_user_id, display_name, active, administrator, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (discord_user_id) DO UPDATE SET display_name = EXCLUDED.display_name,
                    active = EXCLUDED.active, administrator = EXCLUDED.administrator, updated_at = EXCLUDED.updated_at
                RETURNING *
                """, (rs, row) -> new StoredPlayer(rs.getLong("discord_user_id"), rs.getString("display_name"),
                        rs.getBoolean("active"), rs.getBoolean("administrator"), false,
                        rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("updated_at", OffsetDateTime.class).toInstant()), request.discordUserId(), request.displayName(),
                request.active(), request.administrator(), OffsetDateTime.ofInstant(now, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
    }

    @Override
    protected boolean participationEnabled() { return false; }

    @Override
    protected boolean excuseLifecycleEnabled() { return false; }

    @Override
    public Optional<StoredGameResult> find(long playerId, GameType gameType, java.time.LocalDate gameDate) {
        return jdbc.query("SELECT * FROM game_result WHERE player_id = ? AND game_type = ? AND game_date = ?",
                LEGACY_RESULT, playerId, gameType.name(), gameDate).stream().findFirst();
    }

    @Override
    public Optional<StoredGameResult> findById(long id) {
        return jdbc.query("SELECT * FROM game_result WHERE id = ?", LEGACY_RESULT, id).stream().findFirst();
    }

    @Override
    public List<StoredGameResult> findAll() {
        return jdbc.query("SELECT * FROM game_result", LEGACY_RESULT);
    }

    @Override
    @Transactional
    public StoredSubmission storeResult(SubmissionStore.ResultStorage request) {
        Optional<StoredGameResult> legacy = find(
                request.result().playerId(),
                request.result().parsedResult().gameType(),
                request.result().parsedResult().gameDate());
        if (legacy.filter(this::isLegacyBoardlessQuadWords).isPresent()) {
            upgradeLegacyQuadWords(request.result());
        }
        return super.storeResult(request);
    }

    private boolean isLegacyBoardlessQuadWords(StoredGameResult result) {
        return result.parsedResult().gameType() == GameType.QUADWORDS
                && result.parserVersion().equals(LEGACY_QUADWORDS_PARSER_VERSION)
                && result.parsedResult().quadWordsBoards().isEmpty();
    }

    private void upgradeLegacyQuadWords(GameResultStore.GameResultUpsert request) {
        ParsedGameResult parsed = request.parsedResult();
        QuadWordsBoards boards = parsed.quadWordsBoards()
                .orElseThrow(() -> new IllegalArgumentException("a QuadWords legacy upgrade requires four boards"));
        boolean solved = parsed.outcome() instanceof ShareOutcome.Solved;
        Integer attempts = solved ? ((ShareOutcome.Solved) parsed.outcome()).attemptsUsed() : null;
        int changed = jdbc.update("""
                UPDATE game_result SET solved = ?, attempts_used = ?, max_attempts = ?, duration_seconds = ?,
                    gridgames_streak = ?, normalized_board = NULL,
                    quadwords_top_left_board = ?, quadwords_top_right_board = ?,
                    quadwords_bottom_left_board = ?, quadwords_bottom_right_board = ?,
                    raw_share_text = ?, parser_version = ?, updated_at = ?, version = version + 1
                WHERE player_id = ? AND game_type = 'QUADWORDS' AND game_date = ?
                    AND parser_version = ? AND quadwords_top_left_board IS NULL
                """, solved, attempts, parsed.outcome().maxAttempts(), parsed.duration().getSeconds(),
                parsed.gridgamesStreak().isPresent() ? parsed.gridgamesStreak().getAsInt() : null,
                boards.topLeft().canonicalText(), boards.topRight().canonicalText(),
                boards.bottomLeft().canonicalText(), boards.bottomRight().canonicalText(),
                request.rawShareText(), request.parserVersion(), OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                request.playerId(), parsed.gameDate(), LEGACY_QUADWORDS_PARSER_VERSION);
        if (changed != 1) {
            throw new IllegalStateException("legacy QuadWords row changed during upgrade");
        }
    }

    private static final RowMapper<StoredGameResult> LEGACY_RESULT = (rs, row) -> {
        GameType type = GameType.valueOf(rs.getString("game_type"));
        int maximum = rs.getInt("max_attempts");
        ShareOutcome outcome = rs.getBoolean("solved")
                ? new ShareOutcome.Solved(rs.getInt("attempts_used"), maximum)
                : new ShareOutcome.Unsolved(maximum);
        String boardText = rs.getString("normalized_board");
        String topLeft = optionalColumn(rs, "quadwords_top_left_board");
        String topRight = optionalColumn(rs, "quadwords_top_right_board");
        String bottomLeft = optionalColumn(rs, "quadwords_bottom_left_board");
        String bottomRight = optionalColumn(rs, "quadwords_bottom_right_board");
        Optional<NormalizedBoard> gridBoard = type == GameType.GRIDWORDS && boardText != null
                ? Optional.of(new NormalizedBoard(List.of(boardText.split("\\n", -1)))) : Optional.empty();
        Optional<QuadWordsBoards> quadBoards = type == GameType.QUADWORDS
                && topLeft != null && topRight != null && bottomLeft != null && bottomRight != null
                ? Optional.of(new QuadWordsBoards(
                        QuadWordsBoard.fromCanonicalText(topLeft), QuadWordsBoard.fromCanonicalText(topRight),
                        QuadWordsBoard.fromCanonicalText(bottomLeft), QuadWordsBoard.fromCanonicalText(bottomRight)))
                : Optional.empty();
        ParsedGameResult parsed = new ParsedGameResult(
                type,
                rs.getObject("game_date", java.time.LocalDate.class),
                outcome,
                Duration.ofSeconds(rs.getLong("duration_seconds")),
                optionalInt(rs, "gridgames_streak"),
                gridBoard,
                quadBoards);
        return new StoredGameResult(
                rs.getLong("id"), rs.getLong("player_id"), parsed, rs.getString("raw_share_text"),
                rs.getString("parser_version"), optionalLong(rs, "canonical_message_id"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    };

    private static String optionalColumn(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException ignored) {
            return null;
        }
    }

    private static OptionalInt optionalInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? OptionalInt.empty() : OptionalInt.of(value);
    }

    private static OptionalLong optionalLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? OptionalLong.empty() : OptionalLong.of(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }
}
