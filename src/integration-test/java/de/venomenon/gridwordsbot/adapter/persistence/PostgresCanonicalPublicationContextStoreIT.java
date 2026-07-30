package de.venomenon.gridwordsbot.adapter.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import liquibase.integration.spring.SpringLiquibase;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresCanonicalPublicationContextStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;
    private PostgresCanonicalPublicationContextStore store;

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(dataSource);
        store = new PostgresCanonicalPublicationContextStore(jdbc);
    }

    @Test
    void aggregatesContextAcrossHistoricalSubmissionsOfOneResult() {
        OffsetDateTime now = OffsetDateTime.parse("2026-07-29T08:00:00Z");
        jdbc.update("""
                INSERT INTO player (
                    discord_user_id, display_name, active, administrator, created_at, updated_at)
                VALUES (7001, 'Context player', TRUE, FALSE, ?, ?)
                """, now, now);
        long resultId = jdbc.queryForObject("""
                INSERT INTO game_result (
                    player_id, game_type, game_date, solved, attempts_used, max_attempts,
                    duration_seconds, normalized_board, raw_share_text, parser_version, created_at, updated_at)
                VALUES (7001, 'GRIDWORDS', DATE '2026-07-29', TRUE, 3, 6,
                    42, '⬜⬜⬜⬜⬜', 'share', 'gridwords-share-v1', ?, ?)
                RETURNING id
                """, Long.class, now, now);
        jdbc.update("""
                INSERT INTO submission (
                    source_message_id, guild_id, channel_id, author_player_id, raw_message_content,
                    processing_state, game_result_id, personal_complete_established,
                    personal_perfect_established, shared_complete_established,
                    shared_perfect_established, received_at, updated_at)
                VALUES
                    (7101, 1, 2, 7001, 'first', 'COMPLETED', ?, TRUE, FALSE, TRUE, FALSE, ?, ?),
                    (7102, 1, 2, 7001, 'correction', 'COMPLETED', ?, FALSE, TRUE, FALSE, TRUE, ?, ?)
                """, resultId, now, now, resultId, now.plusSeconds(1), now.plusSeconds(1));

        var context = store.findForResult(resultId);

        assertTrue(context.personalCompleteEstablished());
        assertTrue(context.personalPerfectEstablished());
        assertTrue(context.sharedCompleteEstablished());
        assertTrue(context.sharedPerfectEstablished());

        var missing = store.findForResult(resultId + 1000);
        assertFalse(missing.personalCompleteEstablished());
        assertFalse(missing.personalPerfectEstablished());
        assertFalse(missing.sharedCompleteEstablished());
        assertFalse(missing.sharedPerfectEstablished());
    }
}
