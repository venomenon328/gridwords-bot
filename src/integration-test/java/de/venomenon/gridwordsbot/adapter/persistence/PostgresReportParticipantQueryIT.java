package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
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

        insertPlayer(40L, "Before period");
        insertPeriod(40L, date(2026, 7, 1), date(2026, 7, 27));

        List<ParticipantProfile> participants = query.findParticipantsTouching(PERIOD);

        assertThat(participants).extracting(ParticipantProfile::discordUserId).containsExactly(10L, 15L, 20L, 30L);
        ParticipantProfile returning = participants.getFirst();
        assertThat(returning.displayName()).isEqualTo("Renamed player");
        assertThat(returning.firstParticipationStart()).isEqualTo(date(2026, 7, 1));
        assertThat(returning.participationPeriods()).extracting(period -> period.activeFrom())
                .containsExactly(date(2026, 7, 1), date(2026, 8, 1));
        assertThat(returning.participationPeriods()).extracting(period -> period.inactiveFrom())
                .containsExactly(date(2026, 7, 29), null);
        assertThat(participants.get(1).participationPeriods().getFirst().inactiveFrom()).isNull();
        assertThat(participants.get(2).participationPeriods().getFirst().inactiveFrom()).isEqualTo(date(2026, 7, 30));
        assertThat(participants.get(3).participationPeriods().getFirst().activeFrom()).isEqualTo(date(2026, 8, 2));
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

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }
}
