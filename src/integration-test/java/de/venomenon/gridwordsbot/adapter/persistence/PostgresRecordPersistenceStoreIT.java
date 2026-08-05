package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementKey;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementMessage;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementPhase;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementProjection;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementRegistration;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSubject;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventType;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordLockVersion;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordStateUpdate;
import de.venomenon.gridwordsbot.domain.record.RecordStateWrite;
import de.venomenon.gridwordsbot.domain.record.DurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.StreakRecordMetric;
import de.venomenon.gridwordsbot.domain.record.StreakRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailureCategory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
class PostgresRecordPersistenceStoreIT {
    private static final Instant NOW = Instant.parse("2026-08-05T08:00:00Z");
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");
    private JdbcTemplate jdbc;
    private PostgresRecordStateStore states;
    private PostgresRecordEventStore events;
    private PostgresRecordBootstrapStore bootstraps;
    private PostgresRecordAnnouncementStore announcements;
    @BeforeAll void migrate() throws Exception {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var liquibase = new SpringLiquibase(); liquibase.setDataSource(source); liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml"); liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(source); Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        states = new PostgresRecordStateStore(jdbc, clock); events = new PostgresRecordEventStore(jdbc, clock);
        bootstraps = new PostgresRecordBootstrapStore(jdbc, clock); announcements = new PostgresRecordAnnouncementStore(jdbc, clock);
    }
    @BeforeEach void clean() { jdbc.update("DELETE FROM record_announcement_message"); jdbc.update("DELETE FROM record_announcement_event"); jdbc.update("DELETE FROM record_announcement"); jdbc.update("DELETE FROM record_event"); jdbc.update("DELETE FROM record_bootstrap"); jdbc.update("DELETE FROM record_state"); }
    @Test void schemaHasAdditiveRecordTablesStableKeysAndTypedChecks() {
        assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name LIKE 'record_%' ORDER BY table_name", String.class))
                .contains("record_state", "record_event", "record_bootstrap", "record_announcement", "record_announcement_event", "record_announcement_message");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.table_constraints WHERE constraint_name IN ('uq_record_state_business_key','uq_record_announcement_idempotency')", Integer.class)).isEqualTo(2);
    }
    @Test void upgradeFromMigration017RetainsRepresentativeDataAndOnlyAddsRecordSchema() throws Exception {
        String schema = "record_upgrade_017";
        jdbc.execute("CREATE SCHEMA " + schema);
        try {
            var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
            SpringLiquibase legacy = new SpringLiquibase(); legacy.setDataSource(source); legacy.setDefaultSchema(schema); legacy.setChangeLog("classpath:db/changelog/db.changelog-up-to-017.yaml"); legacy.afterPropertiesSet();
            JdbcTemplate legacyJdbc = new JdbcTemplate(source);
            legacyJdbc.update("INSERT INTO " + schema + ".player (discord_user_id,display_name,active,administrator,created_at,updated_at) VALUES (99,'legacy',TRUE,FALSE,?,?)", java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
            SpringLiquibase current = new SpringLiquibase(); current.setDataSource(source); current.setDefaultSchema(schema); current.setChangeLog("classpath:db/changelog/db.changelog-master.yaml"); current.afterPropertiesSet();
            assertThat(legacyJdbc.queryForObject("SELECT display_name FROM " + schema + ".player WHERE discord_user_id=99", String.class)).isEqualTo("legacy");
            assertThat(legacyJdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=? AND table_name='record_state'", Integer.class, schema)).isOne();
        } finally {
            jdbc.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
    @Test void stateInitializesOnceAndUsesCompareAndSetWithoutLostUpdates() throws Exception {
        RecordStateKey key = stateKey(); RecordStateWrite first = write(3, Duration.ofSeconds(70));
        var initialized = concurrently(() -> states.initialize(key, first), () -> states.initialize(key, first));
        assertThat(initialized).filteredOn(outcome -> outcome instanceof de.venomenon.gridwordsbot.domain.record.RecordStateInitialization.Created).hasSize(1);
        RecordStateWrite better = write(2, Duration.ofSeconds(60)); RecordStateWrite best = write(1, Duration.ofSeconds(50));
        var racedUpdates = concurrently(() -> states.update(new RecordStateUpdate(key, RecordLockVersion.initial(), better)), () -> states.update(new RecordStateUpdate(key, RecordLockVersion.initial(), best)));
        assertThat(racedUpdates).extracting(de.venomenon.gridwordsbot.domain.record.RecordStateUpdateResult::status)
                .containsExactlyInAnyOrder(de.venomenon.gridwordsbot.domain.record.RecordStateUpdateResult.Status.UPDATED, de.venomenon.gridwordsbot.domain.record.RecordStateUpdateResult.Status.VERSION_CONFLICT);
        var current = states.find(key).orElseThrow();
        assertThat(states.update(new RecordStateUpdate(key, current.lockVersion(), better)).status()).isEqualTo(
                current.value().equals(better.value()) ? de.venomenon.gridwordsbot.domain.record.RecordStateUpdateResult.Status.UNCHANGED : de.venomenon.gridwordsbot.domain.record.RecordStateUpdateResult.Status.UPDATED);
        assertThat(states.update(new RecordStateUpdate(key, RecordLockVersion.initial(), first)).status()).isEqualTo(de.venomenon.gridwordsbot.domain.record.RecordStateUpdateResult.Status.VERSION_CONFLICT);
    }
    @Test void stateReadPortIsDeterministicAndCasRemovalDoesNotDeleteAuditEvents() {
        RecordStateKey first = stateKey();
        RecordStateKey second = new RecordStateKey(1, new RecordDefinitionKey("result.gridwords.slowest-successful-solution.personal"),
                RecordDefinitionVersion.RECORDS_V1, new RecordScope.Personal(2));
        states.initialize(second, new RecordStateWrite(Optional.of(2L), new DurationRecordValue(Duration.ofSeconds(91)),
                new RecordSourceReference.GameResult(12, 0, 2, GameType.GRIDWORDS, LocalDate.of(2026, 8, 2)), false));
        states.initialize(first, write(3, Duration.ofSeconds(70)));
        RecordEventDraft event = eventDraft();
        events.append(event);
        assertThat(states.findAll(1, RecordDefinitionVersion.RECORDS_V1)).extracting(state -> state.key().scopeKey())
                .containsExactly("player:1", "player:2");
        var snapshot = states.find(first).orElseThrow();
        assertThat(states.remove(first, snapshot.lockVersion())).isTrue();
        assertThat(states.find(first)).isEmpty();
        assertThat(events.find(event.eventId())).isPresent();
    }
    @Test void allTypedStateValuesAndSourcesRoundTripLosslessly() {
        RecordStateKey durationKey = new RecordStateKey(1,new RecordDefinitionKey("result.gridwords.fastest.personal"),RecordDefinitionVersion.RECORDS_V1,new RecordScope.Personal(1));
        states.initialize(durationKey, new RecordStateWrite(Optional.of(1L),new DurationRecordValue(Duration.ofMillis(1234)),new RecordSourceReference.GameResult(2,3,1,GameType.GRIDWORDS,LocalDate.of(2026,8,3)),false));
        RecordStateKey streakKey = new RecordStateKey(1,new RecordDefinitionKey("streak.gridwords-solved.shared"),RecordDefinitionVersion.RECORDS_V1,new RecordScope.Shared());
        states.initialize(streakKey, new RecordStateWrite(Optional.empty(),new StreakRecordValue(3,LocalDate.of(2026,8,1),LocalDate.of(2026,8,3)),new RecordSourceReference.StreakRun(StreakRecordMetric.GRIDWORDS_SOLVED,new RecordSourceReference.StreakRunOwner.Shared(),LocalDate.of(2026,8,1)),true));
        assertThat(states.find(durationKey).orElseThrow().value()).isEqualTo(new DurationRecordValue(Duration.ofMillis(1234)));
        assertThat(states.find(streakKey).orElseThrow().source().sourceType()).isEqualTo(de.venomenon.gridwordsbot.domain.record.RecordSourceType.STREAK_RUN);
    }
    @Test void eventIdempotencyInvalidationAndSupersessionRemainDurable() {
        RecordEventDraft draft = eventDraft();
        assertThat(events.append(draft).appended()).isTrue(); assertThat(events.append(draft).appended()).isFalse();
        RecordEventDraft conflictingReplay = new RecordEventDraft(UUID.randomUUID(), draft.idempotencyKey(), stateKey(), RecordEventType.RESULT_RECORD_BROKEN,
                Optional.empty(), new AttemptsDurationRecordValue(1, Duration.ofSeconds(59)), Optional.empty(), Optional.of(1L), Optional.empty(),
                draft.newSource(), draft.triggerKey(), draft.processingOrigin(), draft.detectedAt());
        assertThatThrownBy(() -> events.append(conflictingReplay)).isInstanceOf(de.venomenon.gridwordsbot.port.out.RecordEventIdempotencyConflictException.class);
        RecordEventDraft successor = new RecordEventDraft(UUID.randomUUID(),"event:result:2:v0",stateKey(),RecordEventType.RESULT_RECORD_BROKEN,Optional.empty(),new AttemptsDurationRecordValue(1,Duration.ofSeconds(50)),Optional.empty(),Optional.of(1L),Optional.empty(),new RecordSourceReference.GameResult(2,0,1,GameType.GRIDWORDS,LocalDate.of(2026,8,5)),"result:2:v0",RecordProcessingOrigin.LIVE_SUBMISSION,NOW);
        events.append(successor);
        assertThat(events.supersede(draft.eventId(), successor.eventId(), NOW.plusSeconds(1))).isTrue();
        assertThat(events.find(draft.eventId()).orElseThrow()).extracting(snapshot -> snapshot.validity(), snapshot -> snapshot.invalidatedAt()).containsExactly(de.venomenon.gridwordsbot.domain.record.RecordEventValidity.SUPERSEDED, Optional.of(NOW.plusSeconds(1)));
        assertThat(events.invalidate(successor.eventId(), NOW.plusSeconds(2))).isTrue();
        assertThat(events.find(successor.eventId()).orElseThrow().updatedAt()).isEqualTo(NOW.plusSeconds(2));
        assertThatIllegalArgumentException().isThrownBy(() -> events.supersede(successor.eventId(), successor.eventId(), NOW.plusSeconds(3)));
        assertThat(events.findByTriggerKey(1, "result:1:v0")).hasSize(1);
    }
    @Test void bootstrapClaimsAreLeasedAndFencedByTheirExactToken() {
        RecordBootstrapKey key = new RecordBootstrapKey(1, RecordDefinitionVersion.RECORDS_V1); bootstraps.register(key);
        var first = bootstraps.claim(key, request(NOW, NOW.plusSeconds(10))).orElseThrow();
        assertThat(bootstraps.markSucceeded(key, UUID.randomUUID(), NOW)).isFalse();
        var replacement = bootstraps.claim(key, request(NOW.plusSeconds(10), NOW.plusSeconds(20))).orElseThrow();
        assertThat(bootstraps.markRetryableFailure(key, first.token(), new RecordWorkFailure(RecordWorkFailureCategory.UNKNOWN, "stale"), NOW.plusSeconds(30))).isFalse();
        assertThat(bootstraps.markPermanentFailure(key, replacement.token(), new RecordWorkFailure(RecordWorkFailureCategory.PERMANENT, "safe"), NOW.plusSeconds(11))).isTrue();
    }
    @Test void announcementsAreIdempotentAndChangedIntentReopensOnlyWhenUnclaimed() {
        UUID event = events.append(eventDraft()).snapshot().draft().eventId();
        RecordAnnouncementKey key = new RecordAnnouncementKey(1, 2, "result:1:v0:live");
        RecordAnnouncementRegistration registration = new RecordAnnouncementRegistration(key, RecordAnnouncementSubject.player(1), RecordAnnouncementPhase.LIVE_EVALUATION, RecordAnnouncementProjection.CREATE, "records-renderer-v1", "a".repeat(64), List.of(event));
        announcements.registerOrUpdate(registration); var claim = announcements.claim(key, request(NOW, NOW.plusSeconds(10))).orElseThrow();
        assertThat(announcements.replaceMessages(key, claim.token(), List.of(new RecordAnnouncementMessage(0, 100), new RecordAnnouncementMessage(1, 101)))).isTrue();
        assertThat(announcements.markSynchronized(key, claim.token(), NOW)).isTrue();
        assertThat(announcements.registerOrUpdate(registration).state()).isEqualTo(de.venomenon.gridwordsbot.domain.record.RecordWorkState.SYNCHRONIZED);
        assertThat(announcements.claim(key, request(NOW.plusSeconds(1), NOW.plusSeconds(11)))).isEmpty();
        RecordAnnouncementRegistration changed = new RecordAnnouncementRegistration(key, RecordAnnouncementSubject.player(1), RecordAnnouncementPhase.LIVE_EVALUATION, RecordAnnouncementProjection.EDIT, "records-renderer-v1", "b".repeat(64), List.of(event));
        assertThat(announcements.registerOrUpdate(changed).state()).isEqualTo(de.venomenon.gridwordsbot.domain.record.RecordWorkState.OPEN);
        var removal = announcements.claim(key, request(NOW.plusSeconds(1), NOW.plusSeconds(11))).orElseThrow();
        assertThatThrownBy(() -> announcements.registerOrUpdate(registration)).isInstanceOf(de.venomenon.gridwordsbot.port.out.RecordAnnouncementClaimConflictException.class);
        assertThat(announcements.markExternallyRemoved(key, removal.token(), NOW.plusSeconds(2))).isTrue();
        assertThat(announcements.find(key).orElseThrow().messages()).containsExactly(new RecordAnnouncementMessage(0,100), new RecordAnnouncementMessage(1,101));
        assertThat(announcements.registerOrUpdate(registration).state()).isEqualTo(de.venomenon.gridwordsbot.domain.record.RecordWorkState.EXTERNALLY_REMOVED);
    }
    @Test void concurrentAnnouncementClaimsHaveOneWinnerAndStaleTokensAreFenced() throws Exception {
        UUID event = events.append(eventDraft()).snapshot().draft().eventId();
        RecordAnnouncementKey key = new RecordAnnouncementKey(1, 2, "result:1:v0:claim-race");
        announcements.registerOrUpdate(new RecordAnnouncementRegistration(key, RecordAnnouncementSubject.player(1), RecordAnnouncementPhase.LIVE_EVALUATION, RecordAnnouncementProjection.CREATE, "records-renderer-v1", "c".repeat(64), List.of(event)));
        var claims = concurrently(() -> announcements.claim(key, request(NOW, NOW.plusSeconds(10))), () -> announcements.claim(key, request(NOW, NOW.plusSeconds(10))));
        assertThat(claims).filteredOn(Optional::isPresent).hasSize(1);
        UUID winner = claims.stream().flatMap(Optional::stream).findFirst().orElseThrow().token();
        assertThat(announcements.markSynchronized(key, UUID.randomUUID(), NOW.plusSeconds(1))).isFalse();
        assertThat(announcements.markSynchronized(key, winner, NOW.plusSeconds(1))).isTrue();
        assertThat(announcements.claim(key, request(NOW.plusSeconds(2), NOW.plusSeconds(12)))).isEmpty();
    }
    @SafeVarargs private static <T> List<T> concurrently(Callable<T>... operations) throws Exception {
        CountDownLatch ready = new CountDownLatch(operations.length); CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(operations.length)) {
            var futures = java.util.Arrays.stream(operations).map(operation -> pool.submit(() -> {
                ready.countDown(); if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("race start timed out"); return operation.call();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return futures.stream().map(future -> { try { return future.get(10, TimeUnit.SECONDS); } catch (Exception error) { throw new AssertionError(error); } }).toList();
        }
    }
    private static RecordStateKey stateKey() { return new RecordStateKey(1,new RecordDefinitionKey("result.gridwords.fewest-attempts.personal"),RecordDefinitionVersion.RECORDS_V1,new RecordScope.Personal(1)); }
    private static RecordStateWrite write(int attempts,Duration duration) { return new RecordStateWrite(Optional.of(1L),new AttemptsDurationRecordValue(attempts,duration),new RecordSourceReference.GameResult(1,0,1,GameType.GRIDWORDS,LocalDate.of(2026,8,4)),false); }
    private static RecordEventDraft eventDraft() { return new RecordEventDraft(UUID.randomUUID(),"event:result:1:v0",stateKey(),RecordEventType.RESULT_RECORD_BROKEN,Optional.empty(),new AttemptsDurationRecordValue(2,Duration.ofSeconds(60)),Optional.empty(),Optional.of(1L),Optional.empty(),new RecordSourceReference.GameResult(1,0,1,GameType.GRIDWORDS,LocalDate.of(2026,8,4)),"result:1:v0",RecordProcessingOrigin.LIVE_SUBMISSION,NOW); }
    private static RecordLeaseClaimRequest request(Instant at,Instant until) { return new RecordLeaseClaimRequest(at,until); }
}
