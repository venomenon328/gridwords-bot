package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RecordLiveEvaluationMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.6-alpine");

    @Test
    void upgradeFrom017PreservesProductionDataAndAddsTheCompleteRecordSchema() throws Exception {
        String schema = "record_upgrade_017";
        var source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            migrate(source, schema, "classpath:db/changelog/db.changelog-up-to-017.yaml");
            Instant now = Instant.parse("2026-08-05T21:00:00Z");
            Instant expires = now.plusSeconds(900);
            jdbc.update("""
                    INSERT INTO %s.player (
                        discord_user_id, display_name, active, administrator, reminder_opt_in, created_at, updated_at)
                    VALUES (1, 'Legacy Player', TRUE, FALSE, TRUE, ?, ?)
                    """.formatted(schema), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
            jdbc.update("""
                    INSERT INTO %s.player_participation_period (
                        player_id, game_type, active_from, inactive_from, created_at, updated_at)
                    VALUES (1, 'GRIDWORDS', DATE '2026-08-01', NULL, ?, ?)
                    """.formatted(schema), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
            Long resultId = jdbc.queryForObject("""
                    INSERT INTO %s.game_result (
                        player_id, game_type, game_date, solved, attempts_used, max_attempts,
                        duration_seconds, normalized_board, raw_share_text, parser_version, created_at, updated_at)
                    VALUES (1, 'GRIDWORDS', DATE '2026-08-05', TRUE, 3, 6, 60,
                        'ABCDE', 'legacy share', 'gridwords-share-v1', ?, ?)
                    RETURNING id
                    """.formatted(schema), Long.class, java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
            java.sql.Timestamp completedAt = java.sql.Timestamp.from(now);
            jdbc.update("""
                    INSERT INTO %s.submission (
                        source_message_id, guild_id, channel_id, author_player_id, raw_message_content,
                        processing_state, game_result_id, received_at, updated_at, original_deleted_at)
                    VALUES (100, 10, 20, 1, 'legacy share', 'COMPLETED', ?, ?, ?, ?)
                    """.formatted(schema), resultId, completedAt, completedAt, completedAt);
            jdbc.update("""
                    INSERT INTO %s.game_result_excuse (
                        game_result_id, trigger_source_message_id, status, catalog_version, context_version,
                        context_generation, offered_at, expires_at, reroll_used, created_at, updated_at)
                    VALUES (?, 100, 'AVAILABLE', 'catalog-v1', 'context-v1', 1, ?, ?, FALSE, ?, ?)
                    """.formatted(schema), resultId, java.sql.Timestamp.from(now), java.sql.Timestamp.from(expires),
                    java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
            jdbc.update("""
                    INSERT INTO %s.game_result_excuse_offer_context (
                        game_result_id, original_received_at, comparison_game_type, compared_result_count,
                        all_compared_results_solved, highest_solved_attempts, longest_duration_seconds,
                        context_fingerprint, created_at, updated_at)
                    VALUES (?, ?, 'GRIDWORDS', 2, TRUE, 4, 120, ?, ?, ?)
                    """.formatted(schema), resultId, java.sql.Timestamp.from(now), "a".repeat(64),
                    java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));

            migrate(source, schema, "classpath:db/changelog/db.changelog-master.yaml");

            assertThat(jdbc.queryForObject(
                    "SELECT display_name FROM " + schema + ".player WHERE discord_user_id = 1",
                    String.class)).isEqualTo("Legacy Player");
            assertThat(jdbc.queryForObject(
                    "SELECT reminder_opt_in FROM " + schema + ".player WHERE discord_user_id = 1",
                    Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject(
                    "SELECT raw_share_text FROM " + schema + ".game_result WHERE id = ?",
                    String.class, resultId)).isEqualTo("legacy share");
            assertThat(jdbc.queryForMap("""
                    SELECT game_type, active_from, inactive_from
                    FROM %s.player_participation_period WHERE player_id = 1
                    """.formatted(schema)))
                    .containsEntry("game_type", "GRIDWORDS")
                    .containsEntry("active_from", java.sql.Date.valueOf("2026-08-01"))
                    .containsEntry("inactive_from", null);
            assertThat(jdbc.queryForObject(
                    "SELECT status FROM " + schema + ".game_result_excuse WHERE game_result_id = ?",
                    String.class, resultId)).isEqualTo("AVAILABLE");
            assertThat(jdbc.queryForObject(
                    "SELECT context_fingerprint FROM " + schema
                            + ".game_result_excuse_offer_context WHERE game_result_id = ?",
                    String.class, resultId)).isEqualTo("a".repeat(64));

            for (String table : List.of(
                    "record_state", "record_event", "record_bootstrap", "record_announcement",
                    "record_announcement_event", "record_announcement_message",
                    "record_live_evaluation", "record_day_close")) {
                assertThat(jdbc.queryForObject("""
                        SELECT count(*) FROM information_schema.tables
                        WHERE table_schema = ? AND table_name = ?
                        """, Integer.class, schema, table)).as(table).isOne();
            }
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM pg_indexes
                    WHERE schemaname = ? AND indexname = 'idx_record_announcement_delivery_order'
                    """, Integer.class, schema)).isOne();
            assertThat(jdbc.queryForObject("""
                    SELECT count(*)
                    FROM pg_catalog.pg_trigger trigger_definition
                    JOIN pg_catalog.pg_class relation
                      ON relation.oid = trigger_definition.tgrelid
                    JOIN pg_catalog.pg_namespace namespace
                      ON namespace.oid = relation.relnamespace
                    WHERE namespace.nspname = ?
                      AND relation.relname = 'submission'
                      AND trigger_definition.tgname = 'trg_submission_record_live_evaluation'
                      AND NOT trigger_definition.tgisinternal
                    """, Integer.class, schema)).isOne();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM " + schema + ".record_live_evaluation",
                    Integer.class)).isZero();
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private static void migrate(
            DriverManagerDataSource source,
            String schema,
            String changelog) throws Exception {
        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setDefaultSchema(schema);
        liquibase.setChangeLog(changelog);
        liquibase.afterPropertiesSet();
    }
}
