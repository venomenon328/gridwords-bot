package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.reporting.ReportGameResult;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
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
class PostgresReportGameResultQueryIT {
    private static final ReportPeriod PERIOD = new ReportPeriod(date(2026, 7, 27), date(2026, 8, 2));
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 8, 3, 10, 0, 0, 0, ZoneOffset.UTC);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;
    private PostgresReportGameResultQuery query;

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(dataSource);
        query = new PostgresReportGameResultQuery(jdbc);
    }

    @Test
    void readsOnlyRequestedPlayersAndInclusivePeriodResultsIncludingBoardlessLegacyQuadWords() {
        insertPlayer(10L, "One");
        insertPlayer(20L, "Two");
        insertPlayer(30L, "Outside selection");

        insertGridWords(10L, date(2026, 7, 27), true, 3, 90);
        insertBoardBackedQuadWords(10L, date(2026, 7, 28), false, null, 205);
        insertBoardlessLegacyQuadWords(10L, date(2026, 8, 2), true, 7, 195);
        insertGridWords(20L, date(2026, 8, 2), false, null, 120);
        insertGridWords(10L, date(2026, 7, 26), true, 2, 75);
        insertBoardlessLegacyQuadWords(30L, date(2026, 7, 27), true, 5, 180);

        List<ReportGameResult> results = query.findResults(PERIOD, Set.of(10L, 20L));

        assertThat(results).extracting(ReportGameResult::playerId).containsExactly(10L, 10L, 10L, 20L);
        assertThat(results).extracting(ReportGameResult::gameType).containsExactly(
                GameType.GRIDWORDS, GameType.QUADWORDS, GameType.QUADWORDS, GameType.GRIDWORDS);
        assertThat(results).extracting(ReportGameResult::gameDate).containsExactly(
                date(2026, 7, 27), date(2026, 7, 28), date(2026, 8, 2), date(2026, 8, 2));
        assertThat(results.get(0).outcome()).isEqualTo(new ShareOutcome.Solved(3, 6));
        assertThat(results.get(1).outcome()).isEqualTo(new ShareOutcome.Unsolved(9));
        assertThat(results.get(2).outcome()).isEqualTo(new ShareOutcome.Solved(7, 9));
        assertThat(results.get(2).duration().getSeconds()).isEqualTo(195);
    }

    private void insertPlayer(long playerId, String displayName) {
        jdbc.update("""
                INSERT INTO player (
                    discord_user_id, display_name, active, administrator, reminder_opt_in, created_at, updated_at)
                VALUES (?, ?, FALSE, FALSE, FALSE, ?, ?)
                """, playerId, displayName, NOW, NOW);
    }

    private void insertGridWords(long playerId, LocalDate gameDate, boolean solved, Integer attempts, long durationSeconds) {
        jdbc.update("""
                INSERT INTO game_result (
                    player_id, game_type, game_date, solved, attempts_used, max_attempts, duration_seconds,
                    normalized_board, raw_share_text, parser_version, canonical_message_id, created_at, updated_at)
                VALUES (?, 'GRIDWORDS', ?, ?, ?, 6, ?, 'grid board', 'valid share', 'gridwords-share-v1', 123, ?, ?)
                """, playerId, gameDate, solved, attempts, durationSeconds, NOW, NOW);
    }

    private void insertBoardBackedQuadWords(
            long playerId, LocalDate gameDate, boolean solved, Integer attempts, long durationSeconds) {
        jdbc.update("""
                INSERT INTO game_result (
                    player_id, game_type, game_date, solved, attempts_used, max_attempts, duration_seconds,
                    normalized_board, raw_share_text, parser_version,
                    quadwords_top_left_board, quadwords_top_right_board,
                    quadwords_bottom_left_board, quadwords_bottom_right_board, created_at, updated_at)
                VALUES (?, 'QUADWORDS', ?, ?, ?, 9, ?, NULL, 'image share', 'quadwords-image-v2',
                    'top left', 'top right', 'bottom left', 'bottom right', ?, ?)
                """, playerId, gameDate, solved, attempts, durationSeconds, NOW, NOW);
    }

    private void insertBoardlessLegacyQuadWords(
            long playerId, LocalDate gameDate, boolean solved, Integer attempts, long durationSeconds) {
        jdbc.update("""
                INSERT INTO game_result (
                    player_id, game_type, game_date, solved, attempts_used, max_attempts, duration_seconds,
                    normalized_board, raw_share_text, parser_version, created_at, updated_at)
                VALUES (?, 'QUADWORDS', ?, ?, ?, 9, ?, NULL, 'legacy share', 'quadwords-share-v1', ?, ?)
                """, playerId, gameDate, solved, attempts, durationSeconds, NOW, NOW);
    }

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }
}
