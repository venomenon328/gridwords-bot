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
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database bean used during the QuadWords image-parser rollout.
 *
 * <p>Version-one QuadWords rows legitimately have no normalized boards. New results must contain all four boards.
 * This adapter reads both representations and upgrades a legacy row atomically when a new image-backed submission for
 * the same player and game day is stored.</p>
 */
@Repository
@Primary
@Profile("database")
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
    public Optional<StoredGameResult> find(long playerId, GameType gameType, java.time.LocalDate gameDate) {
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
    @Transactional
    public SubmissionStore.StoredSubmission storeResult(SubmissionStore.ResultStorage request) {
        upgradeLegacyQuadWordsResult(request.result());
        return super.storeResult(request);
    }

    private void upgradeLegacyQuadWordsResult(GameResultStore.GameResultUpsert request) {
        ParsedGameResult parsed = request.parsedResult();
        if (parsed.gameType() != GameType.QUADWORDS || parsed.quadWordsBoards().isEmpty()) {
            return;
        }
        QuadWordsBoards boards = parsed.quadWordsBoards().orElseThrow();
        boolean solved = parsed.outcome() instanceof ShareOutcome.Solved;
        Integer attempts = solved ? ((ShareOutcome.Solved) parsed.outcome()).attemptsUsed() : null;
        jdbc.update("""
                UPDATE game_result
                SET solved = ?, attempts_used = ?, max_attempts = ?, duration_seconds = ?, gridgames_streak = ?,
                    quadwords_top_left_board = ?, quadwords_top_right_board = ?,
                    quadwords_bottom_left_board = ?, quadwords_bottom_right_board = ?,
                    raw_share_text = ?, parser_version = ?, updated_at = ?, version = version + 1
                WHERE player_id = ? AND game_type = 'QUADWORDS' AND game_date = ?
                  AND parser_version = ?
                  AND quadwords_top_left_board IS NULL
                  AND quadwords_top_right_board IS NULL
                  AND quadwords_bottom_left_board IS NULL
                  AND quadwords_bottom_right_board IS NULL
                """,
                solved,
                attempts,
                parsed.outcome().maxAttempts(),
                parsed.duration().getSeconds(),
                parsed.gridgamesStreak().isPresent() ? parsed.gridgamesStreak().getAsInt() : null,
                boards.topLeft().canonicalText(),
                boards.topRight().canonicalText(),
                boards.bottomLeft().canonicalText(),
                boards.bottomRight().canonicalText(),
                request.rawShareText(),
                request.parserVersion(),
                OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                request.playerId(),
                parsed.gameDate(),
                LEGACY_QUADWORDS_PARSER_VERSION);
    }

    private static final RowMapper<StoredGameResult> RESULT = (rs, row) -> {
        GameType type = GameType.valueOf(rs.getString("game_type"));
        int maximum = rs.getInt("max_attempts");
        ShareOutcome outcome = rs.getBoolean("solved")
                ? new ShareOutcome.Solved(rs.getInt("attempts_used"), maximum)
                : new ShareOutcome.Unsolved(maximum);
        String boardText = rs.getString("normalized_board");
        ParsedGameResult parsed = new ParsedGameResult(
                type,
                rs.getObject("game_date", java.time.LocalDate.class),
                outcome,
                Duration.ofSeconds(rs.getLong("duration_seconds")),
                optionalInt(rs, "gridgames_streak"),
                boardText == null ? Optional.empty() : Optional.of(new NormalizedBoard(List.of(boardText.split("\\n")))),
                quadWordsBoards(rs, type));
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

    private static Optional<QuadWordsBoards> quadWordsBoards(ResultSet rs, GameType type) throws SQLException {
        if (type != GameType.QUADWORDS) {
            return Optional.empty();
        }
        String topLeft = rs.getString("quadwords_top_left_board");
        String topRight = rs.getString("quadwords_top_right_board");
        String bottomLeft = rs.getString("quadwords_bottom_left_board");
        String bottomRight = rs.getString("quadwords_bottom_right_board");
        if (topLeft == null && topRight == null && bottomLeft == null && bottomRight == null) {
            return Optional.empty();
        }
        if (topLeft == null || topRight == null || bottomLeft == null || bottomRight == null) {
            throw new SQLException("QuadWords board columns are only partially populated");
        }
        return Optional.of(new QuadWordsBoards(
                QuadWordsBoard.fromCanonicalText(topLeft),
                QuadWordsBoard.fromCanonicalText(topRight),
                QuadWordsBoard.fromCanonicalText(bottomLeft),
                QuadWordsBoard.fromCanonicalText(bottomRight)));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static OptionalInt optionalInt(ResultSet rs, String column) throws SQLException {
        Integer value = rs.getObject(column, Integer.class);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }
}
