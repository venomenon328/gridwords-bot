package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.application.reporting.ReportGameStatisticsProjector;
import de.venomenon.gridwordsbot.application.reporting.ReportParticipantProjector;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportPlayerGameStatistics;
import de.venomenon.gridwordsbot.port.out.ReportParticipantQuery.ParticipantProfile;
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
class PostgresReportParticipantQueryIT {
    private static final ReportPeriod PERIOD = new ReportPeriod(date(2026, 7, 27), date(2026, 8, 2));
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 8, 3, 10, 0, 0, 0, ZoneOffset.UTC);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;
    private PostgresReportParticipantQuery query;

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(dataSource);
        query = new PostgresReportParticipantQuery(jdbc);
    }

    @Test
    void readsOnlyTouchingPeriodsWithCurrentNamesHistoricalStartsAndStableOrdering() {
        insertPlayer(10L, "Renamed player");
        insertPeriod(10L, date(2026, 7, 1), date(2026, 7, 29));
        insertPeriod(10L, date(2026, 8, 1), null);

        insertPlayer(15L, "Equal early id");
        insertPeriod(15L, date(2026, 7, 5), null);

        insertPlayer(20L, "Equal late id");
        insertPeriod(20L, date(2026, 7, 5), date(2026, 7, 30));

        insertPlayer(30L, "Last day join");
        insertPeriod(30L, date(2026, 8, 2), null);

        insertPlayer(35L, "Switching games");
        insertGamePeriod(35L, GameType.GRIDWORDS, date(2026, 7, 27), date(2026, 7, 31));
        insertGamePeriod(35L, GameType.QUADWORDS, date(2026, 7, 29), null);

        insertPlayer(40L, "Before period");
        insertPeriod(40L, date(2026, 7, 1), date(2026, 7, 27));

        List<ParticipantProfile> participants = query.findParticipantsTouching(PERIOD);

        assertThat(participants).extracting(ParticipantProfile::discordUserId).containsExactly(10L, 15L, 20L, 35L, 30L);
        ParticipantProfile returning = participants.getFirst();
        assertThat(returning.displayName()).isEqualTo("Renamed player");
        assertThat(returning.firstParticipationStart()).isEqualTo(date(2026, 7, 1));
        assertThat(returning.participationPeriods()).extracting(period -> period.gameType())
                .containsExactly(GameType.GRIDWORDS, GameType.GRIDWORDS, GameType.QUADWORDS, GameType.QUADWORDS);
        assertThat(returning.participationPeriods()).extracting(period -> period.activeFrom())
                .containsExactly(date(2026, 7, 1), date(2026, 8, 1), date(2026, 7, 1), date(2026, 8, 1));
        assertThat(returning.participationPeriods()).extracting(period -> period.inactiveFrom())
                .containsExactly(date(2026, 7, 29), null, date(2026, 7, 29), null);
        assertThat(participants.get(1).participationPeriods().getFirst().inactiveFrom()).isNull();
        assertThat(participants.get(2).participationPeriods().getFirst().inactiveFrom()).isEqualTo(date(2026, 7, 30));
        assertThat(participants.get(3).participationPeriods()).extracting(
                        period -> period.gameType(), period -> period.activeFrom(), period -> period.inactiveFrom())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                GameType.GRIDWORDS, date(2026, 7, 27), date(2026, 7, 31)),
                        org.assertj.core.groups.Tuple.tuple(
                                GameType.QUADWORDS, date(2026, 7, 29), null));
        assertThat(participants.get(4).participationPeriods().getFirst().activeFrom()).isEqualTo(date(2026, 8, 2));

        insertResult(35L, GameType.GRIDWORDS, date(2026, 7, 27));
        insertResult(35L, GameType.QUADWORDS, date(2026, 7, 29));
        var basis = new ReportParticipantProjector(query).project(PERIOD);
        ReportPlayerGameStatistics switching = new ReportGameStatisticsProjector(
                new PostgresReportGameResultQuery(jdbc)).project(basis).stream()
                .filter(statistics -> statistics.discordUserId() == 35L)
                .findFirst()
                .orElseThrow();
        assertThat(switching.gridWords()).extracting("possibleDays", "submitted", "missing")
                .containsExactly(4, 1, 3);
        assertThat(switching.quadWords()).extracting("possibleDays", "submitted", "missing")
                .containsExactly(5, 1, 4);
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

    private void insertGamePeriod(
            long playerId, GameType gameType, LocalDate activeFrom, LocalDate inactiveFrom) {
        jdbc.update("""
                INSERT INTO player_participation_period (
                    player_id, game_type, active_from, inactive_from, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, playerId, gameType.name(), activeFrom, inactiveFrom, NOW, NOW);
    }

    private void insertResult(long playerId, GameType gameType, LocalDate gameDate) {
        jdbc.update("""
                INSERT INTO game_result (
                    player_id, game_type, game_date, solved, attempts_used, max_attempts, duration_seconds,
                    normalized_board, raw_share_text, parser_version, created_at, updated_at)
                VALUES (?, ?, ?, TRUE, 1, ?, 30, ?, 'valid share', ?, ?, ?)
                """,
                playerId,
                gameType.name(),
                gameDate,
                gameType == GameType.GRIDWORDS ? 6 : 9,
                gameType == GameType.GRIDWORDS ? "grid board" : null,
                gameType == GameType.GRIDWORDS ? "gridwords-share-v1" : "quadwords-share-v1",
                NOW,
                NOW);
    }

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }
}
