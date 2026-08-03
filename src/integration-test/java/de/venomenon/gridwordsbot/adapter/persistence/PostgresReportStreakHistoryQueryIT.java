package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.reporting.ReportStreakHistory;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
class PostgresReportStreakHistoryQueryIT {
    private static final LocalDate CUTOFF = LocalDate.of(2026, 7, 29);
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 8, 3, 10, 0, 0, 0, ZoneOffset.UTC);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;
    private PostgresReportStreakHistoryQuery query;

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(dataSource);
        query = new PostgresReportStreakHistoryQuery(jdbc);
    }

    @Test
    void readsCompleteHistoryOnlyThroughTheCutoffIncludingSeparatedParticipationAndBoardlessQuadWords() {
        insertPlayer(10L, "Returning");
        insertPlayer(20L, "Leaving");
        insertPlayer(30L, "Future");
        insertPeriod(10L, date(2026, 7, 1), date(2026, 7, 25));
        insertPeriod(10L, date(2026, 7, 27), null);
        insertPeriod(20L, date(2026, 7, 1), date(2026, 8, 1));
        insertPeriod(30L, date(2026, 7, 30), null);
        insertResult(10L, "GRIDWORDS", date(2026, 7, 1), true, 3, 6, "gridwords-share-v1");
        insertResult(10L, "QUADWORDS", date(2026, 7, 27), false, null, 9, "quadwords-share-v1");
        insertResult(20L, "QUADWORDS", date(2026, 7, 29), true, 7, 9, "quadwords-share-v1");
        insertResult(10L, "GRIDWORDS", date(2026, 7, 30), true, 2, 6, "gridwords-share-v1");
        insertResult(30L, "QUADWORDS", date(2026, 7, 30), true, 5, 9, "quadwords-share-v1");

        ReportStreakHistory history = query.findThrough(CUTOFF);

        assertThat(history.participationPeriods()).extracting(period -> period.playerId())
                .containsExactly(10L, 10L, 20L);
        assertThat(history.participationPeriods()).extracting(period -> period.activeFrom())
                .containsExactly(date(2026, 7, 1), date(2026, 7, 27), date(2026, 7, 1));
        assertThat(history.results()).extracting(result -> result.playerId())
                .containsExactly(10L, 10L, 20L);
        assertThat(history.results()).extracting(result -> result.gameDate())
                .containsExactly(date(2026, 7, 1), date(2026, 7, 27), date(2026, 7, 29));
        assertThat(history.results()).extracting(result -> result.gameType())
                .containsExactly(GameType.GRIDWORDS, GameType.QUADWORDS, GameType.QUADWORDS);
        assertThat(history.results().get(1).outcome()).isInstanceOf(de.venomenon.gridwordsbot.domain.model.ShareOutcome.Unsolved.class);
    }

    private void insertPlayer(long playerId, String displayName) {
        jdbc.update("""
                INSERT INTO player (
                    discord_user_id, display_name, active, administrator, reminder_opt_in, created_at, updated_at)
                VALUES (?, ?, FALSE, FALSE, FALSE, ?, ?)
                """, playerId, displayName, NOW, NOW);
    }

    private void insertPeriod(long playerId, LocalDate activeFrom, LocalDate inactiveFrom) {
        jdbc.update("""
                INSERT INTO player_participation_period (
                    player_id, game_type, active_from, inactive_from, created_at, updated_at)
                SELECT ?, game.game_type, ?, ?, ?, ?
                FROM (VALUES ('GRIDWORDS'), ('QUADWORDS')) AS game(game_type)
                """, playerId, activeFrom, inactiveFrom, NOW, NOW);
    }

    private void insertResult(long playerId, String gameType, LocalDate gameDate,
            boolean solved, Integer attempts, int maximumAttempts, String parserVersion) {
        jdbc.update("""
                INSERT INTO game_result (
                    player_id, game_type, game_date, solved, attempts_used, max_attempts, duration_seconds,
                    normalized_board, raw_share_text, parser_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 90, ?, 'valid share', ?, ?, ?)
                """, playerId, gameType, gameDate, solved, attempts, maximumAttempts, gameType.equals("GRIDWORDS") ? "grid board" : null, parserVersion, NOW, NOW);
    }

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }
}
