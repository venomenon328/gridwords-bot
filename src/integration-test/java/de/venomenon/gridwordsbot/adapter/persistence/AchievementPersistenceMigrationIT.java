package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AchievementPersistenceMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    @Test
    void cleanInstallContainsAchievementPersistenceSchema() throws Exception {
        String schema = "achievement_clean_install";
        var source = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            migrate(source, schema, "classpath:db/changelog/db.changelog-master.yaml");
            assertThat(jdbc.queryForList("""
                    SELECT table_name
                    FROM information_schema.tables
                    WHERE table_schema=? AND table_name LIKE 'achievement_%'
                    ORDER BY table_name
                    """, String.class, schema))
                    .containsExactly(
                            "achievement_announcement",
                            "achievement_announcement_item",
                            "achievement_award_state",
                            "achievement_bootstrap_state",
                            "achievement_event");
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void upgradeFrom023PreservesExistingResultParticipationExcuseAndRecordData() throws Exception {
        String schema = "achievement_upgrade_023";
        var source = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            migrate(source, schema, "classpath:db/changelog/db.changelog-up-to-023.yaml");
            Instant now = Instant.parse("2026-08-08T07:00:00Z");
            Timestamp timestamp = Timestamp.from(now);
            Timestamp expires = Timestamp.from(now.plusSeconds(900));

            jdbc.update("""
                    INSERT INTO %s.player (
                        discord_user_id, display_name, active, administrator, reminder_opt_in, created_at, updated_at)
                    VALUES (99, 'legacy-player', TRUE, FALSE, TRUE, ?, ?)
                    """.formatted(schema), timestamp, timestamp);
            jdbc.update("""
                    INSERT INTO %s.player_participation_period (
                        player_id, game_type, active_from, inactive_from, created_at, updated_at)
                    VALUES (99, 'GRIDWORDS', DATE '2026-08-01', NULL, ?, ?)
                    """.formatted(schema), timestamp, timestamp);
            Long resultId = jdbc.queryForObject("""
                    INSERT INTO %s.game_result (
                        player_id, game_type, game_date, solved, attempts_used, max_attempts,
                        duration_seconds, normalized_board, raw_share_text, parser_version, created_at, updated_at)
                    VALUES (99, 'GRIDWORDS', DATE '2026-08-08', TRUE, 3, 6, 60,
                        'ABCDE', 'legacy share', 'gridwords-share-v1', ?, ?)
                    RETURNING id
                    """.formatted(schema), Long.class, timestamp, timestamp);
            jdbc.update("""
                    INSERT INTO %s.submission (
                        source_message_id, guild_id, channel_id, author_player_id, raw_message_content,
                        processing_state, game_result_id, received_at, updated_at, original_deleted_at)
                    VALUES (900, 10, 20, 99, 'legacy share', 'COMPLETED', ?, ?, ?, ?)
                    """.formatted(schema), resultId, timestamp, timestamp, timestamp);
            jdbc.update("""
                    INSERT INTO %s.game_result_excuse (
                        game_result_id, trigger_source_message_id, status, catalog_version, context_version,
                        context_generation, offered_at, expires_at, reroll_used, created_at, updated_at)
                    VALUES (?, 900, 'AVAILABLE', 'catalog-v1', 'context-v1', 1, ?, ?, FALSE, ?, ?)
                    """.formatted(schema), resultId, timestamp, expires, timestamp, timestamp);
            jdbc.update("""
                    INSERT INTO %s.game_result_excuse_offer_context (
                        game_result_id, original_received_at, comparison_game_type, compared_result_count,
                        all_compared_results_solved, highest_solved_attempts, longest_duration_seconds,
                        context_fingerprint, created_at, updated_at)
                    VALUES (?, ?, 'GRIDWORDS', 2, TRUE, 4, 120, ?, ?, ?)
                    """.formatted(schema), resultId, timestamp, "a".repeat(64), timestamp, timestamp);
            jdbc.update("""
                    INSERT INTO %s.record_state (
                        guild_id, definition_key, definition_version, scope_type, scope_key, holder_player_id,
                        value_kind, attempts, duration_millis,
                        source_type, source_game_result_id, source_game_result_version,
                        source_game_player_id, source_game_type, source_game_date, source_game_first_accepted_at,
                        running, lock_version, created_at, updated_at)
                    VALUES (
                        10, 'result.gridwords.fewest_attempts', 'records-v1', 'PERSONAL', 'player:99', 99,
                        'ATTEMPTS_AND_DURATION', 3, 60000,
                        'GAME_RESULT', ?, 0, 99, 'GRIDWORDS', DATE '2026-08-08', ?,
                        FALSE, 0, ?, ?)
                    """.formatted(schema), resultId, timestamp, timestamp, timestamp);

            migrate(source, schema, "classpath:db/changelog/db.changelog-master.yaml");

            assertThat(jdbc.queryForObject(
                    "SELECT display_name FROM " + schema + ".player WHERE discord_user_id=99", String.class))
                    .isEqualTo("legacy-player");
            assertThat(jdbc.queryForObject(
                    "SELECT raw_share_text FROM " + schema + ".game_result WHERE id=?", String.class, resultId))
                    .isEqualTo("legacy share");
            assertThat(jdbc.queryForMap("""
                    SELECT game_type, active_from, inactive_from
                    FROM %s.player_participation_period WHERE player_id=99
                    """.formatted(schema)))
                    .containsEntry("game_type", "GRIDWORDS")
                    .containsEntry("active_from", java.sql.Date.valueOf("2026-08-01"))
                    .containsEntry("inactive_from", null);
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM " + schema + ".game_result_excuse WHERE game_result_id=?",
                    String.class, resultId)).isEqualTo("AVAILABLE");
            assertThat(jdbc.queryForObject(
                    "SELECT context_fingerprint FROM " + schema
                            + ".game_result_excuse_offer_context WHERE game_result_id=?",
                    String.class, resultId)).isEqualTo("a".repeat(64));
            assertThat(jdbc.queryForObject("""
                    SELECT definition_key FROM %s.record_state
                    WHERE guild_id=10 AND holder_player_id=99
                    """.formatted(schema), String.class))
                    .isEqualTo("result.gridwords.fewest_attempts");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.tables
                    WHERE table_schema=? AND table_name='achievement_award_state'
                    """, Integer.class, schema)).isOne();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM %s.databasechangelog
                    WHERE id='024-achievement-persistence'
                    """.formatted(schema), Integer.class)).isOne();
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private static DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static void migrate(DriverManagerDataSource source, String schema, String changelog) throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setDefaultSchema(schema);
        liquibase.setChangeLog(changelog);
        liquibase.afterPropertiesSet();
    }
}
