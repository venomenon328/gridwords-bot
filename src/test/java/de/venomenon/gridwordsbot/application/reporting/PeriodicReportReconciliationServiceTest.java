package de.venomenon.gridwordsbot.application.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryExpiration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryKey;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryMetadata;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryScope;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportNoOp;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationAction;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationCandidate;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationPlan;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportReconciliationPlanner;
import de.venomenon.gridwordsbot.domain.reporting.ReportDueAt;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
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
import org.mockito.InOrder;

class PeriodicReportReconciliationServiceTest {
    private static final long GUILD_ID = 41L;
    private static final long CHANNEL_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-08-03T06:00:00Z");
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalTime WEEKLY_TIME = LocalTime.of(8, 0);
    private static final LocalTime MONTHLY_TIME = LocalTime.of(8, 15);

    @Test
    void forwardsTheExactScopeAnchorAndPlanningFactsUsingOneCapturedInstant() {
        Fixture fixture = fixture();
        LocalDate anchor = LocalDate.of(2026, 7, 1);
        PeriodicReportReconciliationCandidate candidate = candidate(
                ReportType.MONTHLY, LocalDate.of(2026, 7, 1), PeriodicReportReconciliationAction.DELIVER_OR_RECONCILE);
        PeriodicReportNoOp report = new PeriodicReportNoOp(ReportType.MONTHLY, candidate.period());
        PeriodicReportDeliveryScope scope = new PeriodicReportDeliveryScope(GUILD_ID, CHANNEL_ID, ReportType.MONTHLY);
        when(fixture.store.findLatestPeriodStart(scope)).thenReturn(Optional.of(anchor));
        when(fixture.planner.plan(ReportType.MONTHLY, NOW, MONTHLY_TIME, BERLIN, Optional.of(anchor)))
                .thenReturn(new PeriodicReportReconciliationPlan(List.of(candidate)));
        when(fixture.reports.generate(ReportType.MONTHLY, candidate.period())).thenReturn(report);

        fixture.service.reconcile(GUILD_ID, CHANNEL_ID, ReportType.MONTHLY, MONTHLY_TIME, BERLIN);

        verify(fixture.store).findLatestPeriodStart(scope);
        verify(fixture.planner).plan(ReportType.MONTHLY, NOW, MONTHLY_TIME, BERLIN, Optional.of(anchor));
        verify(fixture.delivery).deliver(key(candidate), metadata(candidate), report);
        assertThat(fixture.clock.instantCalls).isOne();
    }

    @Test
    void expiresWithoutGeneratingOrDeliveringAReport() {
        Fixture fixture = fixture();
        PeriodicReportReconciliationCandidate candidate = candidate(
                ReportType.WEEKLY, LocalDate.of(2026, 7, 20), PeriodicReportReconciliationAction.EXPIRE);
        stubPlan(fixture, ReportType.WEEKLY, WEEKLY_TIME, List.of(candidate));

        fixture.service.reconcile(GUILD_ID, CHANNEL_ID, ReportType.WEEKLY, WEEKLY_TIME, BERLIN);

        verify(fixture.store).expire(new PeriodicReportDeliveryExpiration(key(candidate), metadata(candidate)), NOW);
        verify(fixture.reports, never()).generate(any(), any());
        verify(fixture.delivery, never()).deliver(any(), any(), any());
    }

    @Test
    void processesExpirationsBeforeTheLatestDeliveryCandidate() {
        Fixture fixture = fixture();
        PeriodicReportReconciliationCandidate first = candidate(
                ReportType.WEEKLY, LocalDate.of(2026, 7, 6), PeriodicReportReconciliationAction.EXPIRE);
        PeriodicReportReconciliationCandidate second = candidate(
                ReportType.WEEKLY, LocalDate.of(2026, 7, 13), PeriodicReportReconciliationAction.EXPIRE);
        PeriodicReportReconciliationCandidate latest = candidate(
                ReportType.WEEKLY, LocalDate.of(2026, 7, 20), PeriodicReportReconciliationAction.DELIVER_OR_RECONCILE);
        PeriodicReportNoOp report = new PeriodicReportNoOp(ReportType.WEEKLY, latest.period());
        stubPlan(fixture, ReportType.WEEKLY, WEEKLY_TIME, List.of(first, second, latest));
        when(fixture.reports.generate(ReportType.WEEKLY, latest.period())).thenReturn(report);

        fixture.service.reconcile(GUILD_ID, CHANNEL_ID, ReportType.WEEKLY, WEEKLY_TIME, BERLIN);

        InOrder order = inOrder(fixture.store, fixture.reports, fixture.delivery);
        order.verify(fixture.store).expire(new PeriodicReportDeliveryExpiration(key(first), metadata(first)), NOW);
        order.verify(fixture.store).expire(new PeriodicReportDeliveryExpiration(key(second), metadata(second)), NOW);
        order.verify(fixture.reports).generate(ReportType.WEEKLY, latest.period());
        order.verify(fixture.delivery).deliver(key(latest), metadata(latest), report);
    }

    @Test
    void keepsWeeklyAndMonthlyScopesAndKeysSeparateForTheSameChannel() {
        Fixture fixture = fixture();
        PeriodicReportReconciliationCandidate weekly = candidate(
                ReportType.WEEKLY, LocalDate.of(2026, 7, 27), PeriodicReportReconciliationAction.DELIVER_OR_RECONCILE);
        PeriodicReportReconciliationCandidate monthly = candidate(
                ReportType.MONTHLY, LocalDate.of(2026, 7, 1), PeriodicReportReconciliationAction.DELIVER_OR_RECONCILE);
        stubPlan(fixture, ReportType.WEEKLY, WEEKLY_TIME, List.of(weekly));
        stubPlan(fixture, ReportType.MONTHLY, MONTHLY_TIME, List.of(monthly));
        when(fixture.reports.generate(ReportType.WEEKLY, weekly.period()))
                .thenReturn(new PeriodicReportNoOp(ReportType.WEEKLY, weekly.period()));
        when(fixture.reports.generate(ReportType.MONTHLY, monthly.period()))
                .thenReturn(new PeriodicReportNoOp(ReportType.MONTHLY, monthly.period()));

        fixture.service.reconcile(GUILD_ID, CHANNEL_ID, ReportType.WEEKLY, WEEKLY_TIME, BERLIN);
        fixture.service.reconcile(GUILD_ID, CHANNEL_ID, ReportType.MONTHLY, MONTHLY_TIME, BERLIN);

        verify(fixture.store).findLatestPeriodStart(new PeriodicReportDeliveryScope(GUILD_ID, CHANNEL_ID, ReportType.WEEKLY));
        verify(fixture.store).findLatestPeriodStart(new PeriodicReportDeliveryScope(GUILD_ID, CHANNEL_ID, ReportType.MONTHLY));
        verify(fixture.delivery).deliver(eq(key(weekly)), any(), any());
        verify(fixture.delivery).deliver(eq(key(monthly)), any(), any());
    }

    @Test
    void rejectsMissingConstructionAndRunArguments() {
        PeriodicReportDeliveryStore store = mock(PeriodicReportDeliveryStore.class);
        PeriodicReportReconciliationPlanner planner = mock(PeriodicReportReconciliationPlanner.class);
        PeriodicReportUseCase reports = mock(PeriodicReportUseCase.class);
        PeriodicReportDeliveryService delivery = mock(PeriodicReportDeliveryService.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        assertThatNullPointerException().isThrownBy(() -> new PeriodicReportReconciliationService(null, planner, reports, delivery, clock));
        assertThatNullPointerException().isThrownBy(() -> new PeriodicReportReconciliationService(store, null, reports, delivery, clock));
        assertThatNullPointerException().isThrownBy(() -> new PeriodicReportReconciliationService(store, planner, null, delivery, clock));
        assertThatNullPointerException().isThrownBy(() -> new PeriodicReportReconciliationService(store, planner, reports, null, clock));
        assertThatNullPointerException().isThrownBy(() -> new PeriodicReportReconciliationService(store, planner, reports, delivery, null));
        PeriodicReportReconciliationService service = new PeriodicReportReconciliationService(store, planner, reports, delivery, clock);
        assertThatNullPointerException().isThrownBy(() -> service.reconcile(GUILD_ID, CHANNEL_ID, null, WEEKLY_TIME, BERLIN));
        assertThatNullPointerException().isThrownBy(() -> service.reconcile(GUILD_ID, CHANNEL_ID, ReportType.WEEKLY, null, BERLIN));
        assertThatNullPointerException().isThrownBy(() -> service.reconcile(GUILD_ID, CHANNEL_ID, ReportType.WEEKLY, WEEKLY_TIME, null));
    }

    private static void stubPlan(
            Fixture fixture,
            ReportType type,
            LocalTime reportTime,
            List<PeriodicReportReconciliationCandidate> candidates) {
        PeriodicReportDeliveryScope scope = new PeriodicReportDeliveryScope(GUILD_ID, CHANNEL_ID, type);
        when(fixture.store.findLatestPeriodStart(scope)).thenReturn(Optional.empty());
        when(fixture.planner.plan(type, NOW, reportTime, BERLIN, Optional.empty()))
                .thenReturn(new PeriodicReportReconciliationPlan(candidates));
    }

    private static PeriodicReportReconciliationCandidate candidate(
            ReportType type, LocalDate start, PeriodicReportReconciliationAction action) {
        ReportPeriod period = type.periodStartingOn(start);
        LocalTime reportTime = type == ReportType.WEEKLY ? WEEKLY_TIME : MONTHLY_TIME;
        ReportDueAt dueAt = type.dueAt(period, reportTime, BERLIN);
        return new PeriodicReportReconciliationCandidate(
                type, period, dueAt, dueAt.instant().plus(type.catchUpDuration()), action);
    }

    private static PeriodicReportDeliveryKey key(PeriodicReportReconciliationCandidate candidate) {
        return new PeriodicReportDeliveryKey(GUILD_ID, CHANNEL_ID, candidate.type(), candidate.period().startDate());
    }

    private static PeriodicReportDeliveryMetadata metadata(PeriodicReportReconciliationCandidate candidate) {
        return new PeriodicReportDeliveryMetadata(candidate.period(), candidate.dueAt(), candidate.catchUpEndsAt());
    }

    private static Fixture fixture() {
        PeriodicReportDeliveryStore store = mock(PeriodicReportDeliveryStore.class);
        PeriodicReportReconciliationPlanner planner = mock(PeriodicReportReconciliationPlanner.class);
        PeriodicReportUseCase reports = mock(PeriodicReportUseCase.class);
        PeriodicReportDeliveryService delivery = mock(PeriodicReportDeliveryService.class);
        CountingClock clock = new CountingClock(NOW);
        return new Fixture(
                new PeriodicReportReconciliationService(store, planner, reports, delivery, clock),
                store, planner, reports, delivery, clock);
    }

    private record Fixture(
            PeriodicReportReconciliationService service,
            PeriodicReportDeliveryStore store,
            PeriodicReportReconciliationPlanner planner,
            PeriodicReportUseCase reports,
            PeriodicReportDeliveryService delivery,
            CountingClock clock) { }

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
