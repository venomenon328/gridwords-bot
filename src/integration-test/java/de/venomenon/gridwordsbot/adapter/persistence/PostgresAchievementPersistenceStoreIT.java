package de.venomenon.gridwordsbot.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionVersion;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvidence;
import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAnnouncement;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementWork;
import java.time.Clock;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresAchievementPersistenceStoreIT {
    private static final Instant NOW = Instant.parse("2026-08-08T07:00:00Z");
    private static final LocalDate EARNED_ON = LocalDate.of(2026, 8, 7);
    private static final AchievementDefinitionVersion V1 = AchievementDefinitionVersion.ACHIEVEMENTS_V1;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.6-alpine");

    private JdbcTemplate jdbc;
    private PostgresAchievementAwardStateStore awards;
    private PostgresAchievementEventStore events;
    private PostgresAchievementBootstrapStore bootstraps;
    private PostgresAchievementAnnouncementStore announcements;

    @BeforeAll
    void migrate() throws Exception {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var liquibase = new SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        jdbc = new JdbcTemplate(source);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        awards = new PostgresAchievementAwardStateStore(jdbc, clock);
        events = new PostgresAchievementEventStore(jdbc, clock);
        bootstraps = new PostgresAchievementBootstrapStore(jdbc, clock);
        announcements = new PostgresAchievementAnnouncementStore(jdbc, clock);
    }

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM achievement_announcement_item");
        jdbc.update("DELETE FROM achievement_announcement");
        jdbc.update("DELETE FROM achievement_event");
        jdbc.update("DELETE FROM achievement_bootstrap_state");
        jdbc.update("DELETE FROM achievement_award_state");
    }

    @Test
    void schemaHasTheFiveAdditiveAchievementTablesAndBusinessKeys() {
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema='public' AND table_name LIKE 'achievement_%'
                ORDER BY table_name
                """, String.class)).containsExactly(
                        "achievement_announcement",
                        "achievement_announcement_item",
                        "achievement_award_state",
                        "achievement_bootstrap_state",
                        "achievement_event");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE constraint_name IN (
                    'uq_achievement_award_state_business',
                    'uq_achievement_announcement_idempotency',
                    'uq_achievement_announcement_item_event')
                """, Integer.class)).isEqualTo(3);
    }

    @Test
    void concurrentAwardInitializationCreatesExactlyOneStateAndCasPreventsLostUpdates() throws Exception {
        AchievementAwardState.Key key = awardKey("participation.1.gridwords");
        AchievementAwardState.Write initial = activeWrite("result:1");
        var initialized = concurrently(
                () -> awards.initialize(key, initial),
                () -> awards.initialize(key, initial));
        assertThat(initialized)
                .extracting(AchievementAwardState.InitializationResult::status)
                .containsExactlyInAnyOrder(
                        AchievementAwardState.InitializationStatus.CREATED,
                        AchievementAwardState.InitializationStatus.UNCHANGED);

        AchievementAwardState.Write invalidated = new AchievementAwardState.Write(
                V1,
                AchievementAwardState.Status.INVALIDATED,
                EARNED_ON,
                NOW.plusSeconds(10),
                AchievementEvidence.Kind.GAME_RESULT,
                "result:1",
                Optional.of(NOW.plusSeconds(10)));
        AchievementAwardState.Write corrected = new AchievementAwardState.Write(
                V1,
                AchievementAwardState.Status.ACTIVE,
                EARNED_ON.plusDays(1),
                NOW.plusSeconds(11),
                AchievementEvidence.Kind.GAME_RESULT,
                "result:2",
                Optional.empty());
        var raced = concurrently(
                () -> awards.update(key, AchievementAwardState.LockVersion.initial(), invalidated),
                () -> awards.update(key, AchievementAwardState.LockVersion.initial(), corrected));
        assertThat(raced).extracting(AchievementAwardState.UpdateResult::status)
                .containsExactlyInAnyOrder(
                        AchievementAwardState.UpdateStatus.UPDATED,
                        AchievementAwardState.UpdateStatus.VERSION_CONFLICT);
        assertThat(awards.find(key).orElseThrow().lockVersion().value()).isEqualTo(1);
    }

    @Test
    void awardRoundTripsInvalidationAndReadsDeterministically() {
        AchievementAwardState.Key second = awardKey("participation.10.gridwords");
        AchievementAwardState.Key first = awardKey("participation.1.gridwords");
        awards.initialize(second, activeWrite("aggregate:10"));
        awards.initialize(first, activeWrite("result:1"));

        AchievementAwardState.Snapshot current = awards.find(first).orElseThrow();
        AchievementAwardState.Write invalidated = new AchievementAwardState.Write(
                V1,
                AchievementAwardState.Status.INVALIDATED,
                current.write().earnedOn(),
                NOW.plusSeconds(20),
                current.write().evidenceKind(),
                current.write().evidenceReference(),
                Optional.of(NOW.plusSeconds(20)));
        assertThat(awards.update(first, current.lockVersion(), invalidated).status())
                .isEqualTo(AchievementAwardState.UpdateStatus.UPDATED);
        assertThat(awards.find(first).orElseThrow().write().status())
                .isEqualTo(AchievementAwardState.Status.INVALIDATED);
        assertThat(awards.findAll(1, 7)).extracting(state -> state.key().achievementKey().value())
                .containsExactly("participation.1.gridwords", "participation.10.gridwords");
    }

    @Test
    void postgresEnforcesAwardStatusEvidenceAndInvalidationChecks() {
        awards.initialize(awardKey("participation.1.gridwords"), activeWrite("result:1"));
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE achievement_award_state
                   SET award_status='INVALIDATED'
                 WHERE guild_id=1 AND participant_id=7 AND achievement_key='participation.1.gridwords'
                """)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE achievement_award_state
                   SET evidence_kind='NOT_A_KIND'
                 WHERE guild_id=1 AND participant_id=7 AND achievement_key='participation.1.gridwords'
                """)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void eventAppendIsIdempotentConflictsAreVisibleAndHistorySurvivesStateChanges() {
        AchievementEventFact.Draft first = event(
                UUID.randomUUID(), "unlock:1", "participation.1.gridwords", AchievementEventFact.Type.UNLOCKED, "result:1");
        assertThat(events.append(first).appended()).isTrue();
        assertThat(events.append(first).appended()).isFalse();

        AchievementEventFact.Draft conflict = event(
                UUID.randomUUID(), "unlock:1", "participation.1.gridwords", AchievementEventFact.Type.UNLOCKED, "result:other");
        assertThatThrownBy(() -> events.append(conflict))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("idempotency conflict");

        AchievementAwardState.Key key = awardKey("participation.1.gridwords");
        awards.initialize(key, activeWrite("result:1"));
        AchievementAwardState.Snapshot state = awards.find(key).orElseThrow();
        awards.update(key, state.lockVersion(), new AchievementAwardState.Write(
                V1, AchievementAwardState.Status.INVALIDATED, EARNED_ON, NOW.plusSeconds(30),
                AchievementEvidence.Kind.GAME_RESULT, "result:1", Optional.of(NOW.plusSeconds(30))));

        assertThat(events.findByParticipant(1, 7)).hasSize(1);
        assertThat(events.find(first.eventId())).isPresent();
    }

    @Test
    void bootstrapClaimsAreTokenFencedAndExpiredClaimsCanBeRecovered() {
        AchievementWork.BootstrapKey key = new AchievementWork.BootstrapKey(1, V1);
        assertThat(bootstraps.register(key).state()).isEqualTo(AchievementWork.State.OPEN);
        AchievementWork.LeaseClaim claim = bootstraps.claim(
                key, new AchievementWork.LeaseClaimRequest(NOW, NOW.plusSeconds(60))).orElseThrow();
        assertThat(bootstraps.markSucceeded(key, UUID.randomUUID(), NOW.plusSeconds(10))).isFalse();
        assertThat(bootstraps.renewLease(
                key, claim.token(), new AchievementWork.LeaseClaimRequest(NOW.plusSeconds(10), NOW.plusSeconds(120))))
                .isTrue();
        assertThat(bootstraps.markSucceeded(key, claim.token(), NOW.plusSeconds(20))).isTrue();
        assertThat(bootstraps.find(key).orElseThrow().state()).isEqualTo(AchievementWork.State.SUCCEEDED);

        AchievementWork.BootstrapKey recoveryKey = new AchievementWork.BootstrapKey(
                2, new AchievementDefinitionVersion("achievements-v2"));
        bootstraps.register(recoveryKey);
        AchievementWork.LeaseClaim expired = bootstraps.claim(
                recoveryKey, new AchievementWork.LeaseClaimRequest(NOW, NOW.plusSeconds(5))).orElseThrow();
        assertThat(bootstraps.markSucceeded(recoveryKey, expired.token(), NOW.plusSeconds(6))).isFalse();
        AchievementWork.LeaseClaim recovered = bootstraps.claim(
                recoveryKey, new AchievementWork.LeaseClaimRequest(NOW.plusSeconds(6), NOW.plusSeconds(66))).orElseThrow();
        assertThat(recovered.token()).isNotEqualTo(expired.token());
    }

    @Test
    void retryableBootstrapFailureKeepsSafeFailureAndCanBeClaimedWhenDue() {
        AchievementWork.BootstrapKey key = new AchievementWork.BootstrapKey(1, V1);
        bootstraps.register(key);
        AchievementWork.LeaseClaim claim = bootstraps.claim(
                key, new AchievementWork.LeaseClaimRequest(NOW, NOW.plusSeconds(60))).orElseThrow();
        AchievementWork.Failure failure = new AchievementWork.Failure(
                AchievementWork.FailureCategory.UNKNOWN, "temporary database uncertainty");
        assertThat(bootstraps.markRetryableFailure(key, claim.token(), failure, NOW.plusSeconds(30))).isTrue();
        AchievementWork.BootstrapSnapshot retry = bootstraps.find(key).orElseThrow();
        assertThat(retry.state()).isEqualTo(AchievementWork.State.RETRYABLE);
        assertThat(retry.failure()).contains(failure);
        assertThat(bootstraps.claim(
                key, new AchievementWork.LeaseClaimRequest(NOW.plusSeconds(29), NOW.plusSeconds(60)))).isEmpty();
        assertThat(bootstraps.claim(
                key, new AchievementWork.LeaseClaimRequest(NOW.plusSeconds(30), NOW.plusSeconds(90)))).isPresent();
    }

    @Test
    void announcementRegistrationItemsAndDeliveryAreIdempotentAndDeterministic() {
        AchievementEventFact.Draft first = event(
                UUID.randomUUID(), "event:first", "participation.1.gridwords", AchievementEventFact.Type.UNLOCKED, "result:1");
        AchievementEventFact.Draft second = event(
                UUID.randomUUID(), "event:second", "streak.success.1.gridwords", AchievementEventFact.Type.UNLOCKED, "result:1");
        events.append(first);
        events.append(second);
        AchievementAnnouncement.Registration registration = announcement("live:submission:1");
        AchievementAnnouncement.Snapshot registered = announcements.register(registration);
        assertThat(announcements.register(registration).id()).isEqualTo(registered.id());
        assertThatThrownBy(() -> announcements.register(new AchievementAnnouncement.Registration(
                1, 99, 7, V1, AchievementAnnouncement.Type.LIVE_UNLOCK_BATCH,
                registration.idempotencyKey(), "renderer-v1", "a".repeat(64))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("idempotency conflict");

        assertThat(announcements.replaceItems(registration.key(), List.of(second.eventId(), first.eventId()))).isTrue();
        assertThat(announcements.findItems(registration.key())).extracting(AchievementAnnouncement.Item::eventId)
                .containsExactly(second.eventId(), first.eventId());
        assertThatThrownBy(() -> announcements.replaceItems(registration.key(), List.of(first.eventId(), first.eventId())))
                .isInstanceOf(IllegalArgumentException.class);

        AchievementWork.LeaseClaim claim = announcements.claim(
                registration.key(), new AchievementWork.LeaseClaimRequest(NOW, NOW.plusSeconds(60))).orElseThrow();
        assertThat(announcements.markDelivered(registration.key(), UUID.randomUUID(), 1234L, NOW.plusSeconds(10))).isFalse();
        assertThat(announcements.markDelivered(registration.key(), claim.token(), 1234L, NOW.plusSeconds(10))).isTrue();
        assertThat(announcements.markSynchronized(registration.key(), claim.token(), NOW.plusSeconds(20))).isTrue();

        AchievementAnnouncement.Snapshot synchronizedAnnouncement = announcements.find(registration.key()).orElseThrow();
        assertThat(synchronizedAnnouncement.deliveryState()).isEqualTo(AchievementAnnouncement.DeliveryState.SYNCHRONIZED);
        assertThat(synchronizedAnnouncement.discordMessageId()).contains(1234L);
        assertThat(synchronizedAnnouncement.deliveredAt()).contains(NOW.plusSeconds(10));
        assertThat(synchronizedAnnouncement.synchronizedAt()).contains(NOW.plusSeconds(20));
        assertThat(announcements.wasSynchronized(1, 7, new AchievementKey("participation.1.gridwords"))).isTrue();
        assertThat(announcements.updatePendingContent(registration.key(), "renderer-v2", "b".repeat(64))).isFalse();
        assertThat(announcements.replaceItems(registration.key(), List.of(first.eventId()))).isFalse();
    }

    @Test
    void pendingAnnouncementCanBeReducedOrSuppressedWithoutDeletingEventFacts() {
        AchievementEventFact.Draft first = event(
                UUID.randomUUID(), "event:pending", "participation.1.gridwords", AchievementEventFact.Type.UNLOCKED, "result:1");
        events.append(first);
        AchievementAnnouncement.Registration registration = announcement("live:pending");
        announcements.register(registration);
        announcements.replaceItems(registration.key(), List.of(first.eventId()));
        assertThat(announcements.updatePendingContent(registration.key(), "renderer-v2", "b".repeat(64))).isTrue();
        assertThat(announcements.replaceItems(registration.key(), List.of())).isTrue();
        assertThat(announcements.markSuppressed(registration.key(), NOW.plusSeconds(10))).isTrue();
        assertThat(announcements.find(registration.key()).orElseThrow().deliveryState())
                .isEqualTo(AchievementAnnouncement.DeliveryState.SUPPRESSED);
        assertThat(events.find(first.eventId())).isPresent();
    }

    private AchievementAwardState.Key awardKey(String key) {
        return new AchievementAwardState.Key(1, 7, new AchievementKey(key));
    }

    private AchievementAwardState.Write activeWrite(String evidence) {
        return new AchievementAwardState.Write(
                V1, AchievementAwardState.Status.ACTIVE, EARNED_ON, NOW,
                AchievementEvidence.Kind.GAME_RESULT, evidence, Optional.empty());
    }

    private AchievementEventFact.Draft event(
            UUID id, String idempotency, String achievementKey, AchievementEventFact.Type type, String evidence) {
        return new AchievementEventFact.Draft(
                id, idempotency, awardKey(achievementKey), V1, type, EARNED_ON,
                AchievementEvidence.Kind.GAME_RESULT, evidence,
                AchievementEventFact.ProcessingOrigin.LIVE_SUBMISSION, NOW);
    }

    private AchievementAnnouncement.Registration announcement(String idempotency) {
        return new AchievementAnnouncement.Registration(
                1, 2, 7, V1, AchievementAnnouncement.Type.LIVE_UNLOCK_BATCH,
                idempotency, "renderer-v1", "a".repeat(64));
    }

    private static <T> List<T> concurrently(Callable<T> first, Callable<T> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> { ready.countDown(); start.await(); return first.call(); });
            var two = executor.submit(() -> { ready.countDown(); start.await(); return second.call(); });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        }
    }
}
