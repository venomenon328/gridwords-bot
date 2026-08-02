package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.port.out.ChannelMessageRetirementStore;
import de.venomenon.gridwordsbot.port.out.DailyStatusStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
class PostgresChannelMessageRetirementStoreIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final Instant NOW = Instant.parse("2026-07-31T04:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 7, 30);
    private JdbcTemplate jdbc;
    private PostgresChannelMessageRetirementStore store;

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(dataSource);
        store = new PostgresChannelMessageRetirementStore(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM reminder_message_retirement");
        jdbc.update("DELETE FROM canonical_result_retirement");
        jdbc.update("DELETE FROM reminder_delivery");
        jdbc.update("DELETE FROM submission");
        jdbc.update("DELETE FROM game_result");
        jdbc.update("DELETE FROM player");
    }

    @Test
    void resultClaimsBackoffLeasesAndRetirementFenceArePersistent() {
        long resultId = insertCanonicalResult();

        assertThat(store.findResultMessagesBefore(11L, 12L, DATE.plusDays(1)))
                .extracting(ChannelMessageRetirementStore.ResultMessage::resultId).containsExactly(resultId);

        ChannelMessageRetirementStore.ResultRetirementClaim claim = store.claimResultMessage(
                resultId, NOW.plusSeconds(60)).orElseThrow();
        assertThat(store.claimResultMessage(resultId, NOW.plusSeconds(60))).isEmpty();

        store.failResultRetirement(claim, "network", false);
        assertThat(store.findResultMessagesBefore(11L, 12L, DATE.plusDays(1))).isEmpty();

        PostgresChannelMessageRetirementStore later = new PostgresChannelMessageRetirementStore(
                jdbc, Clock.fixed(NOW.plusSeconds(301), ZoneOffset.UTC));
        ChannelMessageRetirementStore.ResultRetirementClaim retried = later.claimResultMessage(
                resultId, NOW.plusSeconds(600)).orElseThrow();
        later.completeResultRetirement(retried);

        assertThat(later.isCanonicalPublicationAllowed(resultId)).isFalse();
        assertThat(jdbc.queryForObject("SELECT retirement_state FROM canonical_result_retirement WHERE game_result_id = ?",
                String.class, resultId)).isEqualTo("RETIRED");
    }

    @Test
    void firstReminderIsRetirableOnlyAfterDurableSecondStageSuccessOrNoCandidates() {
        PostgresDailyStatusStore deliveries = new PostgresDailyStatusStore(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
        DailyStatusStore.ReminderDelivery first = deliveries.claimReminder(
                11L, 12L, DATE, 1, LocalTime.of(16, 0), NOW.plusSeconds(60)).orElseThrow();
        deliveries.completeReminder(first, DailyStatusStore.ReminderState.SENT, Optional.of(91L));
        DailyStatusStore.ReminderDelivery second = deliveries.claimReminder(
                11L, 12L, DATE, 2, LocalTime.of(22, 0), NOW.plusSeconds(60)).orElseThrow();
        deliveries.failReminder(second, "forbidden", true);

        assertThat(store.findFirstReminderMessagesReadyForRetirement(11L, 12L, DATE)).isEmpty();

        jdbc.update("UPDATE reminder_delivery SET delivery_state = 'NO_CANDIDATES', claim_token = NULL,"
                + " claim_until = NULL, retry_after = NULL WHERE guild_id = 11 AND channel_id = 12"
                + " AND game_date = ? AND reminder_stage = 2", DATE);

        ChannelMessageRetirementStore.ReminderMessage candidate = store
                .findFirstReminderMessagesReadyForRetirement(11L, 12L, DATE).getFirst();
        ChannelMessageRetirementStore.ReminderRetirementClaim claim = store.claimReminderMessage(
                11L, 12L, DATE, 1, NOW.plusSeconds(60)).orElseThrow();
        store.completeReminderRetirement(claim);

        assertThat(candidate.messageId()).isEqualTo(91L);
        assertThat(jdbc.queryForObject("SELECT retirement_state FROM reminder_message_retirement"
                + " WHERE guild_id = 11 AND channel_id = 12 AND game_date = ? AND reminder_stage = 1",
                String.class, DATE)).isEqualTo("RETIRED");
    }

    private long insertCanonicalResult() {
        jdbc.update("INSERT INTO player (discord_user_id, display_name, active, administrator, created_at, updated_at)"
                + " VALUES (1, 'Player', TRUE, FALSE, ?, ?)", java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        Long resultId = jdbc.queryForObject("""
                INSERT INTO game_result (player_id, game_type, game_date, solved, attempts_used, max_attempts,
                    duration_seconds, normalized_board, raw_share_text, parser_version, canonical_message_id,
                    created_at, updated_at)
                VALUES (1, 'GRIDWORDS', ?, TRUE, 3, 6, 42, 'board', 'share', 'test', 99, ?, ?)
                RETURNING id
                """, Long.class, DATE, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        jdbc.update("""
                INSERT INTO submission (source_message_id, guild_id, channel_id, author_player_id, raw_message_content,
                    processing_state, game_result_id, received_at, updated_at)
                VALUES (77, 11, 12, 1, 'share', 'CANONICAL_MESSAGE_PUBLISHED', ?, ?, ?)
                """, resultId, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        return resultId;
    }
}
