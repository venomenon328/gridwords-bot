package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
    @Test void stateInitializesOnceAndUsesCompareAndSetWithoutLostUpdates() throws Exception {
        RecordStateKey key = stateKey(); RecordStateWrite first = write(3, Duration.ofSeconds(70));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var outcomes = pool.invokeAll(List.of(() -> states.initialize(key, first), () -> states.initialize(key, first))).stream().map(future -> { try { return future.get(); } catch (Exception error) { throw new AssertionError(error); } }).toList();
            assertThat(outcomes).filteredOn(outcome -> outcome instanceof de.venomenon.gridwordsbot.domain.record.RecordStateInitialization.Created).hasSize(1);
        }
        assertThat(states.update(new RecordStateUpdate(key, RecordLockVersion.initial(), write(2, Duration.ofSeconds(60)))).status()).isEqualTo(de.venomenon.gridwordsbot.domain.record.RecordStateUpdateResult.Status.UPDATED);
        assertThat(states.update(new RecordStateUpdate(key, RecordLockVersion.initial(), first)).status()).isEqualTo(de.venomenon.gridwordsbot.domain.record.RecordStateUpdateResult.Status.VERSION_CONFLICT);
        assertThat(states.find(key).orElseThrow().value()).isEqualTo(new AttemptsDurationRecordValue(2, Duration.ofSeconds(60)));
    }
    @Test void eventIdempotencyAndInvalidationRemainDurable() {
        RecordEventDraft draft = eventDraft();
        assertThat(events.append(draft).appended()).isTrue(); assertThat(events.append(draft).appended()).isFalse();
        assertThat(events.invalidate(draft.eventId(), NOW.plusSeconds(1))).isTrue();
        assertThat(events.find(draft.eventId()).orElseThrow().validity()).isEqualTo(de.venomenon.gridwordsbot.domain.record.RecordEventValidity.INVALIDATED);
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
    @Test void announcementsKeepOrderedMessageIdsAndExternallyRemovedStateTerminal() {
        UUID event = events.append(eventDraft()).snapshot().draft().eventId();
        RecordAnnouncementKey key = new RecordAnnouncementKey(1, 2, "result:1:v0:live");
        RecordAnnouncementRegistration registration = new RecordAnnouncementRegistration(key, RecordAnnouncementSubject.player(1), RecordAnnouncementPhase.LIVE_EVALUATION, RecordAnnouncementProjection.CREATE, "records-renderer-v1", "a".repeat(64), List.of(event));
        announcements.registerOrUpdate(registration); var claim = announcements.claim(key, request(NOW, NOW.plusSeconds(10))).orElseThrow();
        assertThat(announcements.replaceMessages(key, claim.token(), List.of(new RecordAnnouncementMessage(0, 100), new RecordAnnouncementMessage(1, 101)))).isTrue();
        assertThat(announcements.markSynchronized(key, claim.token(), NOW)).isTrue(); var removal = announcements.claim(key, request(NOW.plusSeconds(1), NOW.plusSeconds(11))).orElseThrow();
        assertThat(announcements.markExternallyRemoved(key, removal.token(), NOW.plusSeconds(2))).isTrue();
        assertThat(announcements.find(key).orElseThrow().messages()).containsExactly(new RecordAnnouncementMessage(0,100), new RecordAnnouncementMessage(1,101));
        assertThat(announcements.registerOrUpdate(registration).state()).isEqualTo(de.venomenon.gridwordsbot.domain.record.RecordWorkState.EXTERNALLY_REMOVED);
    }
    private static RecordStateKey stateKey() { return new RecordStateKey(1,new RecordDefinitionKey("result.gridwords.fewest-attempts.personal"),RecordDefinitionVersion.RECORDS_V1,new RecordScope.Personal(1)); }
    private static RecordStateWrite write(int attempts,Duration duration) { return new RecordStateWrite(Optional.of(1L),new AttemptsDurationRecordValue(attempts,duration),new RecordSourceReference.GameResult(1,0,1,GameType.GRIDWORDS,LocalDate.of(2026,8,4)),false); }
    private static RecordEventDraft eventDraft() { return new RecordEventDraft(UUID.randomUUID(),"event:result:1:v0",stateKey(),RecordEventType.RESULT_RECORD_BROKEN,Optional.empty(),new AttemptsDurationRecordValue(2,Duration.ofSeconds(60)),Optional.empty(),Optional.of(1L),Optional.empty(),new RecordSourceReference.GameResult(1,0,1,GameType.GRIDWORDS,LocalDate.of(2026,8,4)),"result:1:v0",RecordProcessingOrigin.LIVE_SUBMISSION,NOW); }
    private static RecordLeaseClaimRequest request(Instant at,Instant until) { return new RecordLeaseClaimRequest(at,until); }
}
