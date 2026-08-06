package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
    void upgradeFrom019PreservesCanonicalDataAndAddsLiveEvaluationQueue() throws Exception {
        String schema = "record_live_upgrade_019";
        var source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            migrate(source, schema, "classpath:db/changelog/db.changelog-up-to-019.yaml");
            Instant now = Instant.parse("2026-08-05T21:00:00Z");
            jdbc.update("""
                    INSERT INTO %s.player (
                        discord_user_id, display_name, active, administrator, reminder_opt_in, created_at, updated_at)
                    VALUES (1, 'Legacy', TRUE, FALSE, FALSE, ?, ?)
                    """.formatted(schema), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
            jdbc.update("""
                    INSERT INTO %s.game_result (
                        player_id, game_type, game_date, solved, attempts_used, max_attempts,
                        duration_seconds, normalized_board, raw_share_text, parser_version, created_at, updated_at)
                    VALUES (1, 'GRIDWORDS', DATE '2026-08-05', TRUE, 3, 6, 60,
                        'ABCDE', 'legacy share', 'gridwords-share-v1', ?, ?)
                    """.formatted(schema), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));

            migrate(source, schema, "classpath:db/changelog/db.changelog-master.yaml");

            assertThat(jdbc.queryForObject(
                    "SELECT raw_share_text FROM " + schema + ".game_result WHERE player_id = 1",
                    String.class)).isEqualTo("legacy share");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.tables
                    WHERE table_schema = ? AND table_name = 'record_live_evaluation'
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
