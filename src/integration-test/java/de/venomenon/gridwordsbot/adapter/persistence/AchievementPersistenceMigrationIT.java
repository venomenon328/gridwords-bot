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
    void upgradeFrom023RetainsExistingDataAndAddsAchievementSchema() throws Exception {
        String schema = "achievement_upgrade_023";
        var source = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            migrate(source, schema, "classpath:db/changelog/db.changelog-up-to-023.yaml");
            Instant now = Instant.parse("2026-08-08T07:00:00Z");
            jdbc.update("""
                    INSERT INTO %s.player (
                        discord_user_id, display_name, active, administrator, created_at, updated_at)
                    VALUES (99, 'legacy-player', TRUE, FALSE, ?, ?)
                    """.formatted(schema), Timestamp.from(now), Timestamp.from(now));

            migrate(source, schema, "classpath:db/changelog/db.changelog-master.yaml");

            assertThat(jdbc.queryForObject(
                    "SELECT display_name FROM " + schema + ".player WHERE discord_user_id=99", String.class))
                    .isEqualTo("legacy-player");
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
