package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.port.out.DailyStatusStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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
class PostgresDailyStatusStoreIT {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private static final Instant NOW = Instant.parse("2026-07-30T18:00:00Z");
    private static final LocalDate DATE = LocalDate.of(2026, 7, 30);
    private JdbcTemplate jdbc;
    private PostgresDailyStatusStore store;

    @BeforeAll
    void migrate() throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(dataSource);
        store = new PostgresDailyStatusStore(jdbc, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @BeforeEach
    void cleanDeliveries() {
        jdbc.update("DELETE FROM reminder_delivery");
        jdbc.update("DELETE FROM daily_status_message");
    }

    @Test
    void migrationCreatesStatesBackoffColumnsAndConstraints() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_name IN ('daily_status_message','reminder_delivery')
                  AND column_name = 'retry_after'
                """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE constraint_name IN ('ck_daily_status_delivery_state','ck_daily_status_claim',
                    'ck_reminder_delivery_state','ck_reminder_delivery_claim')
                """, Integer.class)).isEqualTo(4);
    }

    @Test
    void deliveredStatusIsNotReclaimedUnlessContentChangesOrPresenceIsReconciled() {
        DailyStatusStore.StatusDelivery claim = store.claimStatus(1, 2, DATE, "one", false,
                NOW.plusSeconds(60)).orElseThrow();
        store.completeStatus(claim, 99, "one");

        assertThat(store.claimStatus(1, 2, DATE, "one", false, NOW.plusSeconds(60))).isEmpty();
        DailyStatusStore.StatusDelivery presence = store.claimStatus(1, 2, DATE, "one", true,
                NOW.plusSeconds(60)).orElseThrow();
        assertThat(presence.discordMessageId()).contains(99L);
        assertThat(presence.previousFingerprint()).contains("one");
        store.completeStatus(presence, 99, "one");
        assertThat(store.claimStatus(1, 2, DATE, "two", false, NOW.plusSeconds(60))).isPresent();
    }

    @Test
    void permanentStatusFailureDoesNotHotLoopButChangedContentReactivatesIt() {
        DailyStatusStore.StatusDelivery claim = store.claimStatus(1, 2, DATE, "bad", false,
                NOW.plusSeconds(60)).orElseThrow();
        store.failStatus(claim, "too large", true);

        assertThat(store.claimStatus(1, 2, DATE, "bad", true, NOW.plusSeconds(60))).isEmpty();
        assertThat(store.claimStatus(1, 2, DATE, "fixed", false, NOW.plusSeconds(60))).isPresent();
    }

    @Test
    void retryableStatusHonorsBackoffAndExpiredLeaseCanBeTakenOver() {
        DailyStatusStore.StatusDelivery failed = store.claimStatus(1, 2, DATE, "one", false,
                NOW.plusSeconds(60)).orElseThrow();
        store.failStatus(failed, "network", false);
        assertThat(store.claimStatus(1, 2, DATE, "one", false, NOW.plusSeconds(60))).isEmpty();
        PostgresDailyStatusStore later = new PostgresDailyStatusStore(jdbc,
                Clock.fixed(NOW.plusSeconds(301), ZoneOffset.UTC));
        assertThat(later.claimStatus(1, 2, DATE, "one", false, NOW.plusSeconds(600))).isPresent();

        cleanDeliveries();
        store.claimStatus(1, 2, DATE, "one", false, NOW.minusSeconds(1)).orElseThrow();
        assertThat(store.claimStatus(1, 2, DATE, "one", false, NOW.plusSeconds(60))).isPresent();
    }

    @Test
    void successfulReminderUsesRealDiscordMessageColumnAndIsTerminal() {
        DailyStatusStore.ReminderDelivery claim = store.claimReminder(1, 2, DATE, 1, LocalTime.of(18, 0),
                NOW.plusSeconds(60)).orElseThrow();
        store.completeReminder(claim, DailyStatusStore.ReminderState.SENT, Optional.of(88L));

        assertThat(jdbc.queryForObject("SELECT discord_message_id FROM reminder_delivery", Long.class)).isEqualTo(88L);
        assertThat(store.claimReminder(1, 2, DATE, 1, LocalTime.of(18, 0), NOW.plusSeconds(60))).isEmpty();
    }

    @Test
    void noCandidatesSupersededExpiredAndPermanentAreTerminal() {
        DailyStatusStore.ReminderDelivery noCandidates = store.claimReminder(1, 2, DATE, 1, LocalTime.of(18, 0),
                NOW.plusSeconds(60)).orElseThrow();
        store.completeReminder(noCandidates, DailyStatusStore.ReminderState.NO_CANDIDATES, Optional.empty());
        assertThat(store.claimReminder(1, 2, DATE, 1, LocalTime.of(18, 0), NOW.plusSeconds(60))).isEmpty();


        DailyStatusStore.ReminderDelivery old = store.claimReminder(1, 2, DATE.minusDays(1), 1,
                LocalTime.of(18, 0), NOW.minusSeconds(1)).orElseThrow();
        store.expireOpenRemindersBefore(1, 2, DATE);
        assertThat(jdbc.queryForObject("SELECT delivery_state FROM reminder_delivery WHERE game_date = ?",
                String.class, old.gameDate())).isEqualTo("EXPIRED");

        DailyStatusStore.ReminderDelivery permanent = store.claimReminder(3, 4, DATE, 1,
                LocalTime.of(18, 0), NOW.plusSeconds(60)).orElseThrow();
        store.failReminder(permanent, "missing permission", true);
        assertThat(store.claimReminder(3, 4, DATE, 1, LocalTime.of(18, 0), NOW.plusSeconds(60))).isEmpty();
    }

    @Test
    void retryableReminderHonorsBackoff() {
        DailyStatusStore.ReminderDelivery claim = store.claimReminder(1, 2, DATE, 1, LocalTime.of(18, 0),
                NOW.plusSeconds(60)).orElseThrow();
        store.failReminder(claim, "network", false);
        assertThat(store.claimReminder(1, 2, DATE, 1, LocalTime.of(18, 0), NOW.plusSeconds(60))).isEmpty();
        PostgresDailyStatusStore later = new PostgresDailyStatusStore(jdbc,
                Clock.fixed(NOW.plusSeconds(301), ZoneOffset.UTC));
        assertThat(later.claimReminder(1, 2, DATE, 1, LocalTime.of(18, 0), NOW.plusSeconds(600))).isPresent();
    }

    @Test
    void concurrentWorkersAcquireExactlyOneStatusClaimAndStaleOwnerCannotComplete() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Optional<DailyStatusStore.StatusDelivery>> action = () -> store.claimStatus(
                    1, 2, DATE, "fingerprint", false, NOW.plusSeconds(60));
            List<Optional<DailyStatusStore.StatusDelivery>> claims = List.of(
                    executor.submit(action).get(), executor.submit(action).get());
            assertThat(claims).filteredOn(Optional::isPresent).hasSize(1);
            DailyStatusStore.StatusDelivery owner = claims.stream().flatMap(Optional::stream).findFirst().orElseThrow();
            DailyStatusStore.StatusDelivery stale = new DailyStatusStore.StatusDelivery(
                    1, 2, DATE, java.util.UUID.randomUUID(), Optional.empty(), Optional.empty(), "fingerprint");
            assertThatThrownBy(() -> store.completeStatus(stale, 77L, "fingerprint"))
                    .isInstanceOf(IllegalStateException.class);
            store.completeStatus(owner, 77L, "fingerprint");
        }
    }
    @Test
    void concurrentWorkersAcquireExactlyOneReminderClaimAndStaleOwnerCannotComplete() throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Optional<DailyStatusStore.ReminderDelivery>> action = () -> store.claimReminder(
                    1, 2, DATE, 1, LocalTime.of(18, 0), NOW.plusSeconds(60));
            List<Optional<DailyStatusStore.ReminderDelivery>> claims = List.of(
                    executor.submit(action).get(), executor.submit(action).get());
            assertThat(claims).filteredOn(Optional::isPresent).hasSize(1);
            DailyStatusStore.ReminderDelivery owner = claims.stream().flatMap(Optional::stream).findFirst().orElseThrow();
            DailyStatusStore.ReminderDelivery stale = new DailyStatusStore.ReminderDelivery(
                    1, 2, DATE, 1, LocalTime.of(18, 0), java.util.UUID.randomUUID());
            assertThatThrownBy(() -> store.completeReminder(stale, DailyStatusStore.ReminderState.SENT,
                    Optional.of(77L))).isInstanceOf(IllegalStateException.class);
            store.completeReminder(owner, DailyStatusStore.ReminderState.SENT, Optional.of(77L));
        }
    }

    @Test
    void uniqueKeysAndCheckConstraintsRejectInvalidRows() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO reminder_delivery
                    (guild_id, channel_id, game_date, reminder_stage, scheduled_time, delivery_state, created_at, updated_at)
                VALUES (-1, 2, ?, 1, '18:00', 'PENDING', now(), now())
                """, DATE)).isInstanceOf(Exception.class);
        DailyStatusStore.ReminderDelivery terminal = store.claimReminder(1, 2, DATE, 1, LocalTime.of(18, 0), NOW.plusSeconds(60)).orElseThrow();
        store.completeReminder(terminal, DailyStatusStore.ReminderState.NO_CANDIDATES, Optional.empty());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reminder_delivery", Integer.class)).isOne();
    }
}