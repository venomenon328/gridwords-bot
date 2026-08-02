package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.out.LatestValidSubmissionQuery.LatestValidSubmission;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresLatestValidSubmissionQueryIT {
    private static final long PLAYER = 801L;
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 8, 2, 12, 0, 0, 0, ZoneOffset.UTC);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;
    private PostgresLatestValidSubmissionQuery query;

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(dataSource);
        query = new PostgresLatestValidSubmissionQuery(jdbc);
    }

    @Test
    void selectsBothGamesInOneQueryUsingReceivedAtThenSourceMessageIdAndExcludesOnlySupersededSubmissions() {
        insertPlayer(PLAYER, "Player");
        insertPlayer(802L, "Other");

        long olderGrid = insertResult(PLAYER, GameType.GRIDWORDS, date(2026, 7, 27), true, 2, 61);
        long tieBreakingGrid = insertResult(PLAYER, GameType.GRIDWORDS, date(2026, 7, 28), true, 4, 87);
        long supersededGrid = insertResult(PLAYER, GameType.GRIDWORDS, date(2026, 7, 29), true, 6, 121);
        long failedDeliveryQuad = insertResult(PLAYER, GameType.QUADWORDS, date(2026, 7, 26), false, null, 333);
        long otherPlayerGrid = insertResult(802L, GameType.GRIDWORDS, date(2026, 7, 30), true, 1, 30);

        OffsetDateTime tiedReceivedAt = NOW.minusHours(2);
        insertSubmission(700L, PLAYER, olderGrid, "RESULT_STORED", tiedReceivedAt);
        insertSubmission(701L, PLAYER, tieBreakingGrid, "RESULT_STORED", tiedReceivedAt);
        insertSubmission(702L, PLAYER, supersededGrid, "SUPERSEDED", NOW.minusHours(1));
        insertSubmission(703L, PLAYER, failedDeliveryQuad, "FAILED_RETRYABLE", NOW.minusMinutes(30));
        insertSubmission(704L, 802L, otherPlayerGrid, "RESULT_STORED", NOW);

        List<LatestValidSubmission> latest = query.findLatestValidSubmissions(PLAYER);

        assertThat(latest).extracting(LatestValidSubmission::gameType)
                .containsExactly(GameType.GRIDWORDS, GameType.QUADWORDS);
        assertThat(latest.get(0).gameDate()).isEqualTo(date(2026, 7, 28));
        assertThat(latest.get(0).outcome()).isEqualTo(new ShareOutcome.Solved(4, 6));
        assertThat(latest.get(0).duration().getSeconds()).isEqualTo(87);
        assertThat(latest.get(0).receivedAt()).isEqualTo(tiedReceivedAt.toInstant());
        assertThat(latest.get(1).gameDate()).isEqualTo(date(2026, 7, 26));
        assertThat(latest.get(1).outcome()).isEqualTo(new ShareOutcome.Unsolved(9));
        assertThat(latest.get(1).duration().getSeconds()).isEqualTo(333);
        assertThat(latest.get(1).receivedAt()).isEqualTo(NOW.minusMinutes(30).toInstant());
    }

    @Test
    void usesTheLatestSubmissionTimestampWithTheCurrentCorrectedGameResultState() {
        long playerId = 803L;
        insertPlayer(playerId, "Corrected Player");
        LocalDate gameDate = date(2026, 7, 31);
        long gameResultId = insertResult(playerId, GameType.GRIDWORDS, gameDate, true, 5, 145);
        insertSubmission(710L, playerId, gameResultId, "COMPLETED", NOW.minusHours(3));

        jdbc.update("""
                UPDATE game_result
                SET solved = TRUE, attempts_used = 2, duration_seconds = 42, updated_at = ?
                WHERE id = ?
                """, NOW.minusMinutes(5), gameResultId);
        insertSubmission(711L, playerId, gameResultId, "RESULT_STORED", NOW.minusMinutes(1));

        List<LatestValidSubmission> latest = query.findLatestValidSubmissions(playerId);

        assertThat(latest).singleElement().satisfies(submission -> {
            assertThat(submission.gameType()).isEqualTo(GameType.GRIDWORDS);
            assertThat(submission.gameDate()).isEqualTo(gameDate);
            assertThat(submission.outcome()).isEqualTo(new ShareOutcome.Solved(2, 6));
            assertThat(submission.duration().getSeconds()).isEqualTo(42);
            assertThat(submission.receivedAt()).isEqualTo(NOW.minusMinutes(1).toInstant());
        });
    }

    private void insertPlayer(long id, String name) {
        jdbc.update("""
                INSERT INTO player (
                    discord_user_id, display_name, active, administrator, reminder_opt_in, created_at, updated_at)
                VALUES (?, ?, FALSE, FALSE, FALSE, ?, ?)
                """, id, name, NOW, NOW);
    }

    private long insertResult(
            long playerId, GameType gameType, LocalDate gameDate, boolean solved, Integer attempts, long durationSeconds) {
        if (gameType == GameType.GRIDWORDS) {
            return jdbc.queryForObject("""
                    INSERT INTO game_result (
                        player_id, game_type, game_date, solved, attempts_used, max_attempts, duration_seconds,
                        normalized_board, raw_share_text, parser_version, created_at, updated_at)
                    VALUES (?, 'GRIDWORDS', ?, ?, ?, 6, ?, 'board', 'share', 'gridwords-share-v1', ?, ?)
                    RETURNING id
                    """, Long.class, playerId, gameDate, solved, attempts, durationSeconds, NOW, NOW);
        }
        return jdbc.queryForObject("""
                INSERT INTO game_result (
                    player_id, game_type, game_date, solved, attempts_used, max_attempts, duration_seconds,
                    raw_share_text, parser_version, created_at, updated_at)
                VALUES (?, 'QUADWORDS', ?, ?, ?, 9, ?, 'share', 'quadwords-share-v2', ?, ?)
                RETURNING id
                """, Long.class, playerId, gameDate, solved, attempts, durationSeconds, NOW, NOW);
    }

    private void insertSubmission(
            long sourceMessageId, long playerId, long gameResultId, String state, OffsetDateTime receivedAt) {
        jdbc.update("""
                INSERT INTO submission (
                    source_message_id, guild_id, channel_id, author_player_id, raw_message_content,
                    processing_state, game_result_id, received_at, updated_at)
                VALUES (?, 1, 1, ?, 'share', ?, ?, ?, ?)
                """, sourceMessageId, playerId, state, gameResultId, receivedAt, receivedAt);
    }

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }
}
