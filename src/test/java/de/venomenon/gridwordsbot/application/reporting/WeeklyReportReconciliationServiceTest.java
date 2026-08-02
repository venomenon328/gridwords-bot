package de.venomenon.gridwordsbot.application.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryExpiration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryKey;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryMetadata;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryScope;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportNoOp;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationCandidate;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationPlanner;
import de.venomenon.gridwordsbot.port.out.PeriodicReportDeliveryStore;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class WeeklyReportReconciliationServiceTest {
    private static final long GUILD_ID = 41L;
    private static final long CHANNEL_ID = 42L;
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalTime REPORT_TIME = LocalTime.of(8, 0);
    private static final PeriodicReportDeliveryScope SCOPE =
            new PeriodicReportDeliveryScope(GUILD_ID, CHANNEL_ID, ReportType.WEEKLY);
    private static final Instant OPEN_NOW = Instant.parse("2026-08-03T06:00:00Z");
    private static final Instant CLOSED_NOW = Instant.parse("2026-08-06T06:00:00Z");

    @Test
    void generatesAndDeliversTheOnlyOpenCandidateWithoutAnAnchor() {
        Fixture fixture = fixture(OPEN_NOW, Optional.empty());
        PeriodicReportReconciliationCandidate candidate = onlyCandidate(OPEN_NOW, Optional.empty());
        PeriodicReportNoOp result = new PeriodicReportNoOp(ReportType.WEEKLY, candidate.period());
        when(fixture.reportUseCase.generate(ReportType.WEEKLY, candidate.period())).thenReturn(result);

        fixture.service.reconcile(GUILD_ID, CHANNEL_ID);

        verify(fixture.reportUseCase).generate(ReportType.WEEKLY, candidate.period());
        verify(fixture.deliveryService).deliver(key(candidate), metadata(candidate), result);
        verify(fixture.store, never()).expire(any(), any());
    }

    @Test
    void expiresTheOnlyCandidateAtTheExactCatchUpBoundaryWithoutGenerationOrDelivery() {
        Fixture fixture = fixture(CLOSED_NOW, Optional.empty());
        PeriodicReportReconciliationCandidate candidate = onlyCandidate(CLOSED_NOW, Optional.empty());

        fixture.service.reconcile(GUILD_ID, CHANNEL_ID);

        verify(fixture.store).expire(new PeriodicReportDeliveryExpiration(key(candidate), metadata(candidate)), CLOSED_NOW);
        verify(fixture.reportUseCase, never()).generate(any(), any());
        verify(fixture.deliveryService, never()).deliver(any(), any(), any());
    }

    @Test
    void expiresOlderCandidatesChronologicallyThenGeneratesAndDeliversTheLatestOpenOne() {
        Optional<LocalDate> anchor = Optional.of(LocalDate.of(2026, 7, 6));
        Fixture fixture = fixture(OPEN_NOW, anchor);
        List<PeriodicReportReconciliationCandidate> candidates = candidates(OPEN_NOW, anchor);
        PeriodicReportReconciliationCandidate latest = candidates.getLast();
        PeriodicReportNoOp result = new PeriodicReportNoOp(ReportType.WEEKLY, latest.period());
        when(fixture.reportUseCase.generate(ReportType.WEEKLY, latest.period())).thenReturn(result);

        fixture.service.reconcile(GUILD_ID, CHANNEL_ID);

        InOrder order = inOrder(fixture.store, fixture.reportUseCase, fixture.deliveryService);
        for (PeriodicReportReconciliationCandidate candidate : candidates.subList(0, candidates.size() - 1)) {
            order.verify(fixture.store).expire(new PeriodicReportDeliveryExpiration(key(candidate), metadata(candidate)), OPEN_NOW);
        }
        order.verify(fixture.reportUseCase).generate(ReportType.WEEKLY, latest.period());
        order.verify(fixture.deliveryService).deliver(key(latest), metadata(latest), result);
        verify(fixture.store, times(2)).expire(any(), eq(OPEN_NOW));
    }

    @Test
    void expiresEveryCandidateWhenAllAreOutsideTheCatchUpWindow() {
        Optional<LocalDate> anchor = Optional.of(LocalDate.of(2026, 7, 6));
        Fixture fixture = fixture(CLOSED_NOW, anchor);
        List<PeriodicReportReconciliationCandidate> candidates = candidates(CLOSED_NOW, anchor);

        fixture.service.reconcile(GUILD_ID, CHANNEL_ID);

        for (PeriodicReportReconciliationCandidate candidate : candidates) {
            verify(fixture.store).expire(new PeriodicReportDeliveryExpiration(key(candidate), metadata(candidate)), CLOSED_NOW);
        }
        verify(fixture.reportUseCase, never()).generate(any(), any());
        verify(fixture.deliveryService, never()).deliver(any(), any(), any());
    }

    @Test
    void reconcilesTheLatestPersistedPeriodAgainWhileItsCatchUpWindowIsOpen() {
        Optional<LocalDate> anchor = Optional.of(LocalDate.of(2026, 7, 27));
        Fixture fixture = fixture(OPEN_NOW, anchor);
        PeriodicReportReconciliationCandidate candidate = onlyCandidate(OPEN_NOW, anchor);
        PeriodicReportNoOp result = new PeriodicReportNoOp(ReportType.WEEKLY, candidate.period());
        when(fixture.reportUseCase.generate(ReportType.WEEKLY, candidate.period())).thenReturn(result);

        fixture.service.reconcile(GUILD_ID, CHANNEL_ID);

        verify(fixture.reportUseCase).generate(ReportType.WEEKLY, candidate.period());
        verify(fixture.deliveryService).deliver(key(candidate), metadata(candidate), result);
        verify(fixture.store, never()).expire(any(), any());
    }

    @Test
    void repeatedRunsDoNotInventOlderHistoryAndKeepOneDeliveryCandidatePerRun() {
        Fixture fixture = fixture(OPEN_NOW, Optional.empty());
        PeriodicReportReconciliationCandidate candidate = onlyCandidate(OPEN_NOW, Optional.empty());
        PeriodicReportNoOp result = new PeriodicReportNoOp(ReportType.WEEKLY, candidate.period());
        when(fixture.store.findLatestPeriodStart(SCOPE))
                .thenReturn(Optional.empty(), Optional.of(candidate.period().startDate()));
        when(fixture.reportUseCase.generate(ReportType.WEEKLY, candidate.period())).thenReturn(result);

        fixture.service.reconcile(GUILD_ID, CHANNEL_ID);
        fixture.service.reconcile(GUILD_ID, CHANNEL_ID);

        verify(fixture.reportUseCase, times(2)).generate(ReportType.WEEKLY, candidate.period());
        verify(fixture.deliveryService, times(2)).deliver(key(candidate), metadata(candidate), result);
        verify(fixture.store, never()).expire(any(), any());
    }

    @Test
    void forwardsTheExactScopeAndPlannedDeliveryFacts() {
        Fixture fixture = fixture(OPEN_NOW, Optional.empty());
        PeriodicReportReconciliationCandidate candidate = onlyCandidate(OPEN_NOW, Optional.empty());
        PeriodicReportNoOp result = new PeriodicReportNoOp(ReportType.WEEKLY, candidate.period());
        when(fixture.reportUseCase.generate(ReportType.WEEKLY, candidate.period())).thenReturn(result);

        fixture.service.reconcile(GUILD_ID, CHANNEL_ID);

        ArgumentCaptor<PeriodicReportDeliveryKey> key = ArgumentCaptor.forClass(PeriodicReportDeliveryKey.class);
        ArgumentCaptor<PeriodicReportDeliveryMetadata> metadata = ArgumentCaptor.forClass(PeriodicReportDeliveryMetadata.class);
        verify(fixture.store).findLatestPeriodStart(SCOPE);
        verify(fixture.deliveryService).deliver(key.capture(), metadata.capture(), eq(result));
        assertThat(key.getValue()).isEqualTo(new PeriodicReportDeliveryKey(
                GUILD_ID, CHANNEL_ID, ReportType.WEEKLY, candidate.period().startDate()));
        assertThat(metadata.getValue().period()).isEqualTo(candidate.period());
        assertThat(metadata.getValue().dueAt()).isEqualTo(candidate.dueAt());
        assertThat(metadata.getValue().catchUpEndsAt()).isEqualTo(candidate.catchUpEndsAt());
    }

    @Test
    void forwardsANoOpResultUnchangedToTheDeliveryService() {
        Fixture fixture = fixture(OPEN_NOW, Optional.empty());
        PeriodicReportReconciliationCandidate candidate = onlyCandidate(OPEN_NOW, Optional.empty());
        PeriodicReportNoOp noOp = new PeriodicReportNoOp(ReportType.WEEKLY, candidate.period());
        when(fixture.reportUseCase.generate(ReportType.WEEKLY, candidate.period())).thenReturn(noOp);

        fixture.service.reconcile(GUILD_ID, CHANNEL_ID);

        verify(fixture.deliveryService).deliver(key(candidate), metadata(candidate), noOp);
    }

    @Test
    void propagatesAnOlderExpirationFailureAndDoesNotGenerateOrDeliverLaterWork() {
        Optional<LocalDate> anchor = Optional.of(LocalDate.of(2026, 7, 6));
        Fixture fixture = fixture(OPEN_NOW, anchor);
        when(fixture.store.expire(any(), eq(OPEN_NOW))).thenThrow(new IllegalStateException("database unavailable"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service.reconcile(GUILD_ID, CHANNEL_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(fixture.reportUseCase, never()).generate(any(), any());
        verify(fixture.deliveryService, never()).deliver(any(), any(), any());
    }

    @Test
    void propagatesPlanningErrorsWithoutDeliveringWork() {
        Fixture fixture = fixture(OPEN_NOW, Optional.of(LocalDate.of(2026, 7, 28)));

        assertThatIllegalArgumentException().isThrownBy(() -> fixture.service.reconcile(GUILD_ID, CHANNEL_ID));

        verify(fixture.reportUseCase, never()).generate(any(), any());
        verify(fixture.deliveryService, never()).deliver(any(), any(), any());
        verify(fixture.store, never()).expire(any(), any());
    }

    @Test
    void propagatesGenerationErrorsWithoutCallingDelivery() {
        Fixture fixture = fixture(OPEN_NOW, Optional.empty());
        PeriodicReportReconciliationCandidate candidate = onlyCandidate(OPEN_NOW, Optional.empty());
        when(fixture.reportUseCase.generate(ReportType.WEEKLY, candidate.period()))
                .thenThrow(new IllegalStateException("generation unavailable"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service.reconcile(GUILD_ID, CHANNEL_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("generation unavailable");

        verify(fixture.deliveryService, never()).deliver(any(), any(), any());
    }

    @Test
    void usesOneCapturedInstantForPlanningAndEveryExpiration() {
        Optional<LocalDate> anchor = Optional.of(LocalDate.of(2026, 7, 6));
        CountingClock clock = new CountingClock(CLOSED_NOW);
        Fixture fixture = fixture(clock, anchor);

        fixture.service.reconcile(GUILD_ID, CHANNEL_ID);

        ArgumentCaptor<Instant> completedAt = ArgumentCaptor.forClass(Instant.class);
        verify(fixture.store, times(candidates(CLOSED_NOW, anchor).size())).expire(any(), completedAt.capture());
        assertThat(completedAt.getAllValues()).containsOnly(CLOSED_NOW);
        assertThat(clock.instantCalls).isOne();
    }

    private static Fixture fixture(Instant now, Optional<LocalDate> anchor) {
        return fixture(Clock.fixed(now, ZoneOffset.UTC), anchor);
    }

    private static Fixture fixture(Clock clock, Optional<LocalDate> anchor) {
        PeriodicReportDeliveryStore store = mock(PeriodicReportDeliveryStore.class);
        when(store.findLatestPeriodStart(SCOPE)).thenReturn(anchor);
        PeriodicReportUseCase reportUseCase = mock(PeriodicReportUseCase.class);
        PeriodicReportDeliveryService deliveryService = mock(PeriodicReportDeliveryService.class);
        WeeklyReportReconciliationService service = new WeeklyReportReconciliationService(
                store,
                new PeriodicReportReconciliationPlanner(),
                reportUseCase,
                deliveryService,
                clock,
                REPORT_TIME,
                BERLIN);
        return new Fixture(service, store, reportUseCase, deliveryService);
    }

    private static List<PeriodicReportReconciliationCandidate> candidates(Instant now, Optional<LocalDate> anchor) {
        return new PeriodicReportReconciliationPlanner().plan(ReportType.WEEKLY, now, REPORT_TIME, BERLIN, anchor).candidates();
    }

    private static PeriodicReportReconciliationCandidate onlyCandidate(Instant now, Optional<LocalDate> anchor) {
        List<PeriodicReportReconciliationCandidate> candidates = candidates(now, anchor);
        assertThat(candidates).hasSize(1);
        return candidates.getFirst();
    }

    private static PeriodicReportDeliveryKey key(PeriodicReportReconciliationCandidate candidate) {
        return new PeriodicReportDeliveryKey(GUILD_ID, CHANNEL_ID, ReportType.WEEKLY, candidate.period().startDate());
    }

    private static PeriodicReportDeliveryMetadata metadata(PeriodicReportReconciliationCandidate candidate) {
        return new PeriodicReportDeliveryMetadata(candidate.period(), candidate.dueAt(), candidate.catchUpEndsAt());
    }

    private record Fixture(
            WeeklyReportReconciliationService service,
            PeriodicReportDeliveryStore store,
            PeriodicReportUseCase reportUseCase,
            PeriodicReportDeliveryService deliveryService) { }

    private static final class CountingClock extends Clock {
        private final Instant now;
        private int instantCalls;

        private CountingClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            instantCalls++;
            return now;
        }
    }
}
