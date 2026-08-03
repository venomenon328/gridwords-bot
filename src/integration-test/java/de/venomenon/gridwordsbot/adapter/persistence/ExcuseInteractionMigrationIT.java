package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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

/** Verifies upgrade from the completed 10.6 schema without changing existing business data. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExcuseInteractionMigrationIT {

    private static final Instant NOW = Instant.parse("2026-08-03T10:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;

    @BeforeAll
    void migrateFromCurrentTenPointSixSchema() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        migrate(dataSource, "classpath:db/changelog/legacy-10.6-upgrade-test.yaml");
        jdbc = new JdbcTemplate(dataSource);
        OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO player (discord_user_id, display_name, active, administrator, reminder_opt_in, created_at, updated_at)
                VALUES (4601, 'Existing player', TRUE, FALSE, TRUE, ?, ?)
                """, now, now);
        jdbc.update("""
                INSERT INTO game_result (
                    player_id, game_type, game_date, solved, attempts_used, max_attempts, duration_seconds,
                    normalized_board, raw_share_text, parser_version, created_at, updated_at)
                VALUES (4601, 'GRIDWORDS', DATE '2026-08-02', TRUE, 5, 6, 120, 'board', 'existing share', 'v1', ?, ?)
                """, now, now);
        jdbc.update("""
                INSERT INTO submission (
                    source_message_id, guild_id, channel_id, author_player_id, raw_message_content,
                    processing_state, game_result_id, received_at, updated_at)
                SELECT 4601, 46, 42, 4601, 'existing share', 'RESULT_STORED', id, ?, ?
                FROM game_result WHERE player_id = 4601
                """, now, now);
        jdbc.update("""
                INSERT INTO player_participation_period (
                    player_id, game_type, active_from, inactive_from, created_at, updated_at)
                VALUES (4601, 'GRIDWORDS', DATE '2026-08-02', NULL, ?, ?)
                """, now, now);
        jdbc.update("INSERT INTO daily_status_message (guild_id, channel_id, game_date, created_at, updated_at) VALUES (46, 42, DATE '2026-08-02', ?, ?)", now, now);

        migrate(dataSource, "classpath:db/changelog/db.changelog-master.yaml");
    }

    @Test
    void backfillsEveryExistingResultAsTheTerminalNegativeDecision() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM game_result", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM game_result_excuse", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT status = 'NOT_OFFERED'
                    AND trigger_source_message_id IS NULL
                    AND offered_at IS NULL
                    AND expires_at IS NULL
                    AND reroll_used = FALSE
                    AND selected_template_id IS NULL
                FROM game_result_excuse
                """, Boolean.class)).isTrue();
    }

    @Test
    void preservesExistingResultSubmissionParticipationAndDeliveryRows() {
        assertThat(jdbc.queryForObject("SELECT raw_share_text FROM game_result", String.class)).isEqualTo("existing share");
        assertThat(jdbc.queryForObject("SELECT processing_state FROM submission", String.class)).isEqualTo("RESULT_STORED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM player_participation_period", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_status_message", Integer.class)).isEqualTo(1);
    }

    private static void migrate(DriverManagerDataSource dataSource, String changelog) throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changelog);
        liquibase.afterPropertiesSet();
    }
}
