package de.venomenon.gridwordsbot.application.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionVersion;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvaluator;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvidence;
import de.venomenon.gridwordsbot.domain.achievement.AchievementHistorySnapshot;
import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAnnouncement;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementWork;
import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementStore;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import de.venomenon.gridwordsbot.port.out.AchievementEventStore;
import de.venomenon.gridwordsbot.port.out.AchievementHistoryQuery;
import de.venomenon.gridwordsbot.port.out.AchievementTransactionRunner;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class AchievementReconciliationServiceTest {
    private static final long GUILD_ID = 10L;
    private static final long PARTICIPANT_ID = 20L;
    private static final Instant NOW = Instant.parse("2026-08-08T07:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void reconcilesTheCompleteParticipantHistoryIncludingCrossGameAndGlobalFacts() {
        MutableHistory history = new MutableHistory(snapshot(
                result(1, Game.GRIDWORDS, true, 1),
                result(2, Game.QUADWORDS, true, 4)));
        InMemoryAwards awards = new InMemoryAwards();
        InMemoryEvents events = new InMemoryEvents();
        AchievementReconciliationService service = service(history, awards, events);

        var result = service.reconcile(request(AchievementEventFact.ProcessingOrigin.LIVE_SUBMISSION));

        assertThat(transition(result, "crossgame.participation.1")).isEqualTo(
                AchievementReconciliationService.TransitionType.UNLOCK);
        assertThat(transition(result, "crossgame.success.1")).isEqualTo(
                AchievementReconciliationService.TransitionType.UNLOCK);
        assertThat(transition(result, "situational.crossgame.perfect_double")).isEqualTo(
                AchievementReconciliationService.TransitionType.UNLOCK);
        assertThat(transition(result, "timing.after_2300")).isEqualTo(
                AchievementReconciliationService.TransitionType.UNLOCK);
        assertThat(awards.findAll(GUILD_ID, PARTICIPANT_ID)).allSatisfy(state ->
                assertThat(state.write().status()).isEqualTo(AchievementAwardState.Status.ACTIVE));
        assertThat(events.events()).hasSameSizeAs(awards.findAll(GUILD_ID, PARTICIPANT_ID));
    }

    @Test
    void replayOfAnAlreadyPersistedProjectionIsNoOpWithoutAdditionalEvents() {
        MutableHistory history = new MutableHistory(snapshot(result(1, Game.GRIDWORDS, true, 1)));
        InMemoryAwards awards = new InMemoryAwards();
        InMemoryEvents events = new InMemoryEvents();
        AchievementReconciliationService service = service(history, awards, events);

        service.reconcile(request(AchievementEventFact.ProcessingOrigin.LIVE_SUBMISSION));
        int firstEventCount = events.events().size();
        var replay = service.reconcile(request(AchievementEventFact.ProcessingOrigin.REPLAY));

        assertThat(replay.transitions()).allSatisfy(transition ->
                assertThat(transition.type()).isEqualTo(AchievementReconciliationService.TransitionType.NO_OP));
        assertThat(events.events()).hasSize(firstEventCount);
    }

    @Test
    void correctionInvalidatesAndLaterReactivatesWithoutDeletingTheAuditTrail() {
        MutableHistory history = new MutableHistory(snapshot(result(1, Game.GRIDWORDS, true, 1)));
        InMemoryAwards awards = new InMemoryAwards();
        InMemoryEvents events = new InMemoryEvents();
        AchievementReconciliationService service = service(history, awards, events);
        AchievementKey key = new AchievementKey("streak.success.1.gridwords");

        service.reconcile(request(AchievementEventFact.ProcessingOrigin.LIVE_SUBMISSION));
        history.snapshot = snapshot(result(1, Game.GRIDWORDS, false, 0));
        var invalidated = service.reconcile(request(AchievementEventFact.ProcessingOrigin.NORMAL_CORRECTION));

        assertThat(transition(invalidated, key.value())).isEqualTo(
                AchievementReconciliationService.TransitionType.INVALIDATE);
        assertThat(awards.find(new AchievementAwardState.Key(GUILD_ID, PARTICIPANT_ID, key)).orElseThrow()
                .write().status()).isEqualTo(AchievementAwardState.Status.INVALIDATED);

        history.snapshot = snapshot(result(1, Game.GRIDWORDS, true, 1));
        var reactivated = service.reconcile(request(AchievementEventFact.ProcessingOrigin.NORMAL_CORRECTION));

        assertThat(transition(reactivated, key.value())).isEqualTo(
                AchievementReconciliationService.TransitionType.REACTIVATE);
        assertThat(events.events().stream()
                .filter(event -> event.fact().awardKey().achievementKey().equals(key))
                .map(event -> event.fact().eventType()))
                .containsExactly(
                        AchievementEventFact.Type.UNLOCKED,
                        AchievementEventFact.Type.INVALIDATED,
                        AchievementEventFact.Type.REACTIVATED);
    }

    @Test
    void propagatesUnknownInfrastructureFailuresInsteadOfReportingNoOp() {
        MutableHistory history = new MutableHistory(snapshot(result(1, Game.GRIDWORDS, true, 1)));
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database unavailable");
        InMemoryEvents events = new InMemoryEvents();
        events.failure = failure;

        assertThatThrownBy(() -> service(history, new InMemoryAwards(), events)
                .reconcile(request(AchievementEventFact.ProcessingOrigin.LIVE_SUBMISSION)))
                .isSameAs(failure);
    }

    private static AchievementReconciliationService service(
            MutableHistory history, InMemoryAwards awards, InMemoryEvents events) {
        AchievementDefinitionCatalog catalog = AchievementDefinitionCatalog.achievementsV1();
        AchievementTransactionRunner transactions = new AchievementTransactionRunner() {
            @Override
            public <T> T inTransaction(java.util.function.Supplier<T> work) {
                return work.get();
            }
        };
        return new AchievementReconciliationService(
                history,
                new AchievementEvaluator(catalog),
                catalog,
                awards,
                events,
                new NoOpAnnouncements(),
                transactions,
                CLOCK,
                ZoneId.of("Europe/Berlin"));
    }

    private static AchievementReconciliationService.ReconciliationRequest request(
            AchievementEventFact.ProcessingOrigin origin) {
        return new AchievementReconciliationService.ReconciliationRequest(
                GUILD_ID, PARTICIPANT_ID, origin, Optional.empty());
    }

    private static AchievementReconciliationService.TransitionType transition(
            AchievementReconciliationService.ReconciliationResult result, String key) {
        return result.transitions().stream()
                .filter(transition -> transition.achievementKey().value().equals(key))
                .findFirst()
                .orElseThrow()
                .type();
    }

    private static AchievementHistorySnapshot snapshot(AchievementHistorySnapshot.Result... results) {
        LocalDate day = LocalDate.of(2026, 8, 7);
        return new AchievementHistorySnapshot(
                PARTICIPANT_ID,
                List.of(results),
                List.of(
                        new de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod(
                                PARTICIPANT_ID,
                                de.venomenon.gridwordsbot.domain.model.GameType.GRIDWORDS,
                                day,
                                null),
                        new de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod(
                                PARTICIPANT_ID,
                                de.venomenon.gridwordsbot.domain.model.GameType.QUADWORDS,
                                day,
                                null)));
    }

    private static AchievementHistorySnapshot.Result result(long id, Game game, boolean solved, int attempts) {
        return new AchievementHistorySnapshot.Result(
                id,
                game.type,
                LocalDate.of(2026, 8, 7),
                solved,
                solved ? OptionalInt.of(attempts) : OptionalInt.empty(),
                game == Game.GRIDWORDS ? Instant.parse("2026-08-07T21:30:00Z") : Instant.parse("2026-08-07T21:31:00Z"),
                Optional.empty());
    }

    private enum Game {
        GRIDWORDS(de.venomenon.gridwordsbot.domain.model.GameType.GRIDWORDS),
        QUADWORDS(de.venomenon.gridwordsbot.domain.model.GameType.QUADWORDS);

        private final de.venomenon.gridwordsbot.domain.model.GameType type;

        Game(de.venomenon.gridwordsbot.domain.model.GameType type) {
            this.type = type;
        }
    }

    private static final class MutableHistory implements AchievementHistoryQuery {
        private AchievementHistorySnapshot snapshot;

        private MutableHistory(AchievementHistorySnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public AchievementHistorySnapshot load(long guildId, long participantId) {
            assertThat(guildId).isEqualTo(GUILD_ID);
            assertThat(participantId).isEqualTo(PARTICIPANT_ID);
            return snapshot;
        }
    }

    private static final class InMemoryAwards implements AchievementAwardStateStore {
        private final Map<AchievementAwardState.Key, AchievementAwardState.Snapshot> states = new LinkedHashMap<>();

        @Override
        public Optional<AchievementAwardState.Snapshot> find(AchievementAwardState.Key key) {
            return Optional.ofNullable(states.get(key));
        }

        @Override
        public List<AchievementAwardState.Snapshot> findAll(long guildId, long participantId) {
            return states.values().stream()
                    .filter(state -> state.key().guildId() == guildId && state.key().participantId() == participantId)
                    .sorted(Comparator.comparing(state -> state.key().achievementKey().value()))
                    .toList();
        }

        @Override
        public AchievementAwardState.InitializationResult initialize(
                AchievementAwardState.Key key, AchievementAwardState.Write write) {
            AchievementAwardState.Snapshot existing = states.get(key);
            if (existing != null) {
                return new AchievementAwardState.InitializationResult(
                        existing.write().equals(write)
                                ? AchievementAwardState.InitializationStatus.UNCHANGED
                                : AchievementAwardState.InitializationStatus.CONFLICT,
                        existing);
            }
            AchievementAwardState.Snapshot created = new AchievementAwardState.Snapshot(
                    key, write, AchievementAwardState.LockVersion.initial(), NOW, NOW);
            states.put(key, created);
            return new AchievementAwardState.InitializationResult(
                    AchievementAwardState.InitializationStatus.CREATED, created);
        }

        @Override
        public AchievementAwardState.UpdateResult update(
                AchievementAwardState.Key key,
                AchievementAwardState.LockVersion expected,
                AchievementAwardState.Write write) {
            AchievementAwardState.Snapshot current = states.get(key);
            if (current == null) {
                return new AchievementAwardState.UpdateResult(AchievementAwardState.UpdateStatus.MISSING, Optional.empty());
            }
            if (!current.lockVersion().equals(expected)) {
                return new AchievementAwardState.UpdateResult(
                        AchievementAwardState.UpdateStatus.VERSION_CONFLICT, Optional.of(current));
            }
            if (current.write().equals(write)) {
                return new AchievementAwardState.UpdateResult(
                        AchievementAwardState.UpdateStatus.UNCHANGED, Optional.of(current));
            }
            AchievementAwardState.Snapshot changed = new AchievementAwardState.Snapshot(
                    key, write, current.lockVersion().next(), current.createdAt(), NOW);
            states.put(key, changed);
            return new AchievementAwardState.UpdateResult(
                    AchievementAwardState.UpdateStatus.UPDATED, Optional.of(changed));
        }
    }

    private static final class InMemoryEvents implements AchievementEventStore {
        private final Map<UUID, AchievementEventFact.Snapshot> byId = new LinkedHashMap<>();
        private final Map<String, AchievementEventFact.Snapshot> byIdempotency = new LinkedHashMap<>();
        private RuntimeException failure;

        @Override
        public AchievementEventFact.AppendResult append(AchievementEventFact.Draft draft) {
            if (failure != null) {
                throw failure;
            }
            AchievementEventFact.Snapshot existing = byIdempotency.get(draft.idempotencyKey());
            if (existing != null) {
                if (!existing.fact().equals(draft)) {
                    throw new IllegalStateException("achievement event idempotency conflict");
                }
                return new AchievementEventFact.AppendResult(false, existing);
            }
            AchievementEventFact.Snapshot snapshot = new AchievementEventFact.Snapshot(draft, NOW);
            byId.put(draft.eventId(), snapshot);
            byIdempotency.put(draft.idempotencyKey(), snapshot);
            return new AchievementEventFact.AppendResult(true, snapshot);
        }

        @Override
        public Optional<AchievementEventFact.Snapshot> find(UUID eventId) {
            return Optional.ofNullable(byId.get(eventId));
        }

        @Override
        public Optional<AchievementEventFact.Snapshot> findByIdempotencyKey(String idempotencyKey) {
            return Optional.ofNullable(byIdempotency.get(idempotencyKey));
        }

        @Override
        public List<AchievementEventFact.Snapshot> findByParticipant(long guildId, long participantId) {
            return events().stream()
                    .filter(event -> event.fact().awardKey().guildId() == guildId)
                    .filter(event -> event.fact().awardKey().participantId() == participantId)
                    .toList();
        }

        private List<AchievementEventFact.Snapshot> events() {
            return new ArrayList<>(byId.values());
        }
    }

    private static final class NoOpAnnouncements implements AchievementAnnouncementStore {
        @Override
        public AchievementAnnouncement.Snapshot register(AchievementAnnouncement.Registration registration) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AchievementAnnouncement.Snapshot> find(AchievementAnnouncement.Key key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AchievementAnnouncement.Snapshot> findPending(long guildId, long participantId) {
            return List.of();
        }

        @Override
        public List<AchievementAnnouncement.Item> findItems(AchievementAnnouncement.Key key) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean updatePendingContent(
                AchievementAnnouncement.Key key, String rendererVersion, String contentFingerprint) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean replaceItems(AchievementAnnouncement.Key key, List<UUID> eventIds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean wasSynchronized(long guildId, long participantId, AchievementKey achievementKey) {
            return false;
        }

        @Override
        public Optional<AchievementWork.LeaseClaim> claim(
                AchievementAnnouncement.Key key, AchievementWork.LeaseClaimRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<AchievementAnnouncement.Snapshot> claimNext(AchievementWork.LeaseClaimRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean renewLease(
                AchievementAnnouncement.Key key, UUID token, AchievementWork.LeaseClaimRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean markDelivered(AchievementAnnouncement.Key key, UUID token, long discordMessageId, Instant deliveredAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean markSynchronized(AchievementAnnouncement.Key key, UUID token, Instant synchronizedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean markRetryableFailure(
                AchievementAnnouncement.Key key,
                UUID token,
                AchievementWork.Failure failure,
                Instant nextRetryAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean markPermanentFailure(
                AchievementAnnouncement.Key key,
                UUID token,
                AchievementWork.Failure failure,
                Instant completedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean markExternallyRemoved(AchievementAnnouncement.Key key, UUID token, Instant removedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean markSuppressed(AchievementAnnouncement.Key key, Instant suppressedAt) {
            throw new UnsupportedOperationException();
        }
    }
}
