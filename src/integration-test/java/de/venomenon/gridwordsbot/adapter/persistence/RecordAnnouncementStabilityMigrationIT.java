package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RecordAnnouncementStabilityMigrationIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.6-alpine");

    @Test
    void upgradeFrom022SchedulesOneIdBasedCleanupEditOnlyForPublishedCreatesWithMessageIds() throws Exception {
        String schema = "record_announcement_stability_upgrade";
        var source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            migrate(source, schema, "classpath:db/changelog/db.changelog-up-to-022.yaml");
            Instant now = Instant.parse("2026-08-07T00:00:00Z");
            insertAnnouncement(jdbc, schema, "published-create", "CREATE", "SYNCHRONIZED", now, now);
            insertMessage(jdbc, schema, "published-create", now);
            insertAnnouncement(jdbc, schema, "published-create-without-id", "CREATE", "SYNCHRONIZED", now, now);
            insertAnnouncement(jdbc, schema, "unpublished-create", "CREATE", "SYNCHRONIZED", null, now);
            insertAnnouncement(jdbc, schema, "published-edit", "EDIT", "SYNCHRONIZED", now, now);

            migrate(source, schema, "classpath:db/changelog/db.changelog-master.yaml");

            assertThat(projectionState(jdbc, schema, "published-create"))
                    .containsEntry("desired_projection", "EDIT")
                    .containsEntry("delivery_state", "OPEN")
                    .containsEntry("changed_at", null);
            assertThat(state(jdbc, schema, "published-create-without-id")).isEqualTo("SYNCHRONIZED");
            assertThat(state(jdbc, schema, "unpublished-create")).isEqualTo("SYNCHRONIZED");
            assertThat(state(jdbc, schema, "published-edit")).isEqualTo("SYNCHRONIZED");
            assertThat(jdbc.queryForObject("""
                    SELECT count(*) FROM %s.record_announcement
                    WHERE delivery_state = 'OPEN' AND desired_projection = 'EDIT'
                    """.formatted(schema), Integer.class)).isOne();
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private static void insertAnnouncement(
            JdbcTemplate jdbc,
            String schema,
            String key,
            String projection,
            String state,
            Instant publishedAt,
            Instant now) {
        jdbc.update("""
                INSERT INTO %s.record_announcement (
                    guild_id, channel_id, idempotency_key, subject_type, subject_key,
                    announcement_phase, desired_projection, renderer_version, content_fingerprint,
                    delivery_state, attempt_count, published_at, created_at, updated_at)
                VALUES (1, 2, ?, 'PLAYER', '1', 'LIVE_EVALUATION', ?, 'records-v1-discord-4', ?,
                        ?, 1, ?, ?, ?)
                """.formatted(schema), key, projection, "a".repeat(64), state,
                publishedAt == null ? null : Timestamp.from(publishedAt), Timestamp.from(now), Timestamp.from(now));
    }

    private static String state(JdbcTemplate jdbc, String schema, String key) {
        return jdbc.queryForObject("""
                SELECT delivery_state FROM %s.record_announcement WHERE idempotency_key = ?
                """.formatted(schema), String.class, key);
    }

    private static Map<String, Object> projectionState(JdbcTemplate jdbc, String schema, String key) {
        return jdbc.queryForMap("""
                SELECT desired_projection, delivery_state, changed_at
                FROM %s.record_announcement WHERE idempotency_key = ?
                """.formatted(schema), key);
    }

    private static void insertMessage(JdbcTemplate jdbc, String schema, String key, Instant now) {
        jdbc.update("""
                INSERT INTO %s.record_announcement_message (
                    announcement_id, message_position, discord_message_id, created_at)
                SELECT id, 0, 100, ? FROM %s.record_announcement WHERE idempotency_key = ?
                """.formatted(schema, schema), Timestamp.from(now), key);
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
