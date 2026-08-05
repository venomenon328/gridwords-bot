package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordStateWrite;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresRecordStateSourceOrderIT {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");
    private static final Instant FIRST_ACCEPTED_AT = Instant.parse("2026-08-04T09:15:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;
    private PostgresRecordStateStore states;

    @BeforeAll
    void migrate() throws Exception {
        var source = dataSource();
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(source);
        states = new PostgresRecordStateStore(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM record_state");
    }

    @Test
    void gameResultAcceptanceTimestampRoundTripsLosslessly() {
        RecordStateKey key = new RecordStateKey(
                1,
                new RecordDefinitionKey("result.gridwords.fewest-attempts.personal"),
                RecordDefinitionVersion.RECORDS_V1,
                new RecordScope.Personal(7));
        RecordStateWrite write = new RecordStateWrite(
                Optional.of(7L),
                new AttemptsDurationRecordValue(2, Duration.ofSeconds(50)),
                new RecordSourceReference.GameResult(
                        42, 3, 7, GameType.GRIDWORDS, LocalDate.of(2026, 8, 4)),
                Optional.of(FIRST_ACCEPTED_AT),
                false);

        states.initialize(key, write);

        var restored = states.find(key).orElseThrow();
        assertThat(restored.sourceGameFirstAcceptedAt()).contains(FIRST_ACCEPTED_AT);
        assertThat(restored.source()).isEqualTo(write.source());
    }

    @Test
    void migration019BackfillsExistingStatesAndAllowsPreviousBinaryWrites() throws Exception {
        String schema = "record_upgrade_018";
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            var source = dataSource();
            SpringLiquibase legacy = new SpringLiquibase();
            legacy.setDataSource(source);
            legacy.setDefaultSchema(schema);
            legacy.setChangeLog("classpath:db/changelog/db.changelog-up-to-018.yaml");
            legacy.afterPropertiesSet();
            JdbcTemplate legacyJdbc = new JdbcTemplate(source);

            legacyJdbc.update("""
                    INSERT INTO %s.player
                        (discord_user_id,display_name,active,administrator,created_at,updated_at)
                    VALUES (99,'legacy',TRUE,FALSE,?,?)
                    """.formatted(schema),
                    java.sql.Timestamp.from(FIRST_ACCEPTED_AT),
                    java.sql.Timestamp.from(FIRST_ACCEPTED_AT));
            legacyJdbc.update("""
                    INSERT INTO %s.game_result
                        (id,player_id,game_type,game_date,solved,attempts_used,max_attempts,duration_seconds,
                         normalized_board,raw_share_text,parser_version,created_at,updated_at,version)
                    VALUES (42,99,'GRIDWORDS','2026-08-04',TRUE,2,6,50,
                            'board','share','parser-v1',?,?,0)
                    """.formatted(schema),
                    java.sql.Timestamp.from(FIRST_ACCEPTED_AT),
                    java.sql.Timestamp.from(FIRST_ACCEPTED_AT));
            insertLegacyState(legacyJdbc, schema,
                    "result.gridwords.fewest-attempts.personal", "player:99", "ATTEMPTS_AND_DURATION");

            SpringLiquibase current = new SpringLiquibase();
            current.setDataSource(source);
            current.setDefaultSchema(schema);
            current.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
            current.afterPropertiesSet();

            OffsetDateTime backfilled = legacyJdbc.queryForObject("""
                    SELECT source_game_first_accepted_at
                    FROM %s.record_state
                    WHERE definition_key='result.gridwords.fewest-attempts.personal'
                    """.formatted(schema), OffsetDateTime.class);
            assertThat(backfilled).isNotNull();
            assertThat(backfilled.toInstant()).isEqualTo(FIRST_ACCEPTED_AT);

            // A rollback to the pre-019 application omits the new column. The additive schema must accept it.
            insertLegacyState(legacyJdbc, schema,
                    "result.gridwords.fastest.personal", "player:100", "DURATION");
            assertThat(legacyJdbc.queryForObject("""
                    SELECT source_game_first_accepted_at IS NULL
                    FROM %s.record_state
                    WHERE definition_key='result.gridwords.fastest.personal'
                    """.formatted(schema), Boolean.class)).isTrue();
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    private static void insertLegacyState(
            JdbcTemplate jdbc,
            String schema,
            String definitionKey,
            String scopeKey,
            String valueKind) {
        Integer attempts = "ATTEMPTS_AND_DURATION".equals(valueKind) ? 2 : null;
        jdbc.update("""
                INSERT INTO %s.record_state
                    (guild_id,definition_key,definition_version,scope_type,scope_key,holder_player_id,
                     value_kind,attempts,duration_millis,
                     source_type,source_game_result_id,source_game_result_version,source_game_player_id,
                     source_game_type,source_game_date,running,lock_version,created_at,updated_at)
                VALUES (1,?,'records-v1','PERSONAL',?,99,
                        ?,?,50000,
                        'GAME_RESULT',42,0,99,'GRIDWORDS','2026-08-04',FALSE,0,?,?)
                """.formatted(schema),
                definitionKey,
                scopeKey,
                valueKind,
                attempts,
                java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW));
    }

    private static DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
