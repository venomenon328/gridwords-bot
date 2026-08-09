package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.application.player.PersonalStatusService;
import de.venomenon.gridwordsbot.application.status.DailyStatusProjector;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.in.PersonalStatusUseCase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
class PostgresPersonalStatusReadOnlyIT {
    private static final long PLAYER = 9_001L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 9);
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-09T10:00:00Z"), ZoneOffset.UTC);

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;
    private PersonalStatusService service;

    @BeforeAll
    void migrateAndPrepare() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(dataSource);

        DynamicPlayerPostgresPersistenceAdapter persistence =
                new DynamicPlayerPostgresPersistenceAdapter(jdbc, CLOCK, BERLIN);
        service = new PersonalStatusService(
                persistence,
                new PostgresLatestValidSubmissionQuery(jdbc),
                new DailyStatusProjector(persistence, persistence),
                CLOCK,
                BERLIN);

        var storedAt = java.time.OffsetDateTime.of(2026, 8, 1, 12, 0, 0, 0, ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO player
                    (discord_user_id, display_name, active, administrator, reminder_opt_in, created_at, updated_at)
                VALUES (?, 'Persistierter Name', FALSE, FALSE, TRUE, ?, ?)
                """, PLAYER, storedAt, storedAt);
        jdbc.update("""
                INSERT INTO player_participation_period
                    (player_id, game_type, active_from, inactive_from, created_at, updated_at)
                VALUES (?, 'GRIDWORDS', ?, NULL, ?, ?)
                """, PLAYER, TODAY.minusDays(4), storedAt, storedAt);
        jdbc.update("""
                INSERT INTO game_result
                    (player_id, game_type, game_date, solved, attempts_used, max_attempts, duration_seconds,
                     normalized_board, raw_share_text, parser_version, created_at, updated_at)
                VALUES (?, 'GRIDWORDS', ?, TRUE, 1, 6, 42,
                        '🟩🟩🟩🟩🟩', 'share', 'gridwords-share-v1', ?, ?)
                """, PLAYER, TODAY, storedAt, storedAt);
    }

    @Test
    void statusReadsTodayAndStreaksWithoutMutatingPlayerOrParticipationFacts() {
        Map<String, Object> playerBefore = jdbc.queryForMap("""
                SELECT display_name, active, administrator, reminder_opt_in, created_at, updated_at
                FROM player WHERE discord_user_id = ?
                """, PLAYER);
        List<Map<String, Object>> periodsBefore = jdbc.queryForList("""
                SELECT game_type, active_from, inactive_from, created_at, updated_at
                FROM player_participation_period WHERE player_id = ? ORDER BY game_type, active_from
                """, PLAYER);
        int resultsBefore = jdbc.queryForObject(
                "SELECT count(*) FROM game_result WHERE player_id = ?", Integer.class, PLAYER);

        PersonalStatusUseCase.PersonalStatus status = service.status(
                new PersonalStatusUseCase.PlayerIdentity(PLAYER, "Neuer Discord-Name"));

        assertThat(status.known()).isTrue();
        assertThat(status.gridWordsToday().participating()).isTrue();
        assertThat(status.gridWordsToday().outcome()).contains(new ShareOutcome.Solved(1, 6));
        assertThat(status.gridWordsToday().duration()).contains(java.time.Duration.ofSeconds(42));
        assertThat(status.quadWordsToday().participating()).isFalse();
        assertThat(status.streaks().activity().orElseThrow()).isEqualTo(5);
        assertThat(status.streaks().gridWordsSolved().orElseThrow()).isEqualTo(1);
        assertThat(status.streaks().complete()).isEmpty();
        assertThat(status.streaks().perfect()).isEmpty();
        assertThat(status.reminderOptIn()).isTrue();

        assertThat(jdbc.queryForMap("""
                SELECT display_name, active, administrator, reminder_opt_in, created_at, updated_at
                FROM player WHERE discord_user_id = ?
                """, PLAYER)).isEqualTo(playerBefore);
        assertThat(jdbc.queryForList("""
                SELECT game_type, active_from, inactive_from, created_at, updated_at
                FROM player_participation_period WHERE player_id = ? ORDER BY game_type, active_from
                """, PLAYER)).isEqualTo(periodsBefore);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM game_result WHERE player_id = ?", Integer.class, PLAYER))
                .isEqualTo(resultsBefore);
        assertThat(jdbc.queryForObject(
                "SELECT display_name FROM player WHERE discord_user_id = ?", String.class, PLAYER))
                .isEqualTo("Persistierter Name");
        assertThat(jdbc.queryForObject(
                "SELECT active FROM player WHERE discord_user_id = ?", Boolean.class, PLAYER))
                .isFalse();
    }
}
