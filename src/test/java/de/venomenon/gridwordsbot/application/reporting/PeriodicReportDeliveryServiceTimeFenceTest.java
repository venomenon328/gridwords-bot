package de.venomenon.gridwordsbot.application.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.reporting.PeriodicReport;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaim;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaimRequest;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryContent;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryKey;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryMetadata;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryRegistration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliverySnapshot;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryState;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportNoOp;
import de.venomenon.gridwordsbot.domain.reporting.RenderedPeriodicReport;
import de.venomenon.gridwordsbot.domain.reporting.RenderedReportPage;
import de.venomenon.gridwordsbot.domain.reporting.ReportDueAt;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
import de.venomenon.gridwordsbot.port.out.PeriodicReportDeliveryStore;
import de.venomenon.gridwordsbot.port.out.PeriodicReportMessageGateway;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PeriodicReportDeliveryServiceTimeFenceTest {
    private static final Instant DUE = Instant.parse("2026-08-03T12:00:00Z");
    private static final Instant END = DUE.plus(Duration.ofHours(72));
    private static final ReportPeriod PERIOD = new ReportPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
    private static final PeriodicReportDeliveryKey KEY = new PeriodicReportDeliveryKey(1, 2, ReportType.WEEKLY, PERIOD.startDate());
    private static final PeriodicReportDeliveryMetadata METADATA = new PeriodicReportDeliveryMetadata(
            PERIOD, new ReportDueAt(LocalDate.of(2026, 8, 3), LocalTime.NOON, ZoneOffset.UTC), END);

    @Test
    void expiresAFirstRegistrationAtTheExactCatchUpEndWithoutClaimOrGatewayIo() {
        PeriodicReportDeliveryStore store = mock(PeriodicReportDeliveryStore.class);
        PeriodicReportMessageGateway gateway = mock(PeriodicReportMessageGateway.class);
        PeriodicReportDeliveryRegistration registration = new PeriodicReportDeliveryRegistration(KEY, METADATA, Optional.empty());
        when(store.find(KEY)).thenReturn(Optional.empty());
        when(store.register(registration)).thenReturn(snapshot(registration, PeriodicReportDeliveryState.OPEN, Optional.empty()));

        service(store, gateway, Clock.fixed(END, ZoneOffset.UTC)).deliver(
                KEY, METADATA, new PeriodicReportNoOp(ReportType.WEEKLY, PERIOD));

        verify(store).markExpired(KEY, END);
        verify(store, never()).claim(any(), any());
        verify(gateway, never()).create(anyLong(), any());
    }

    @Test
    void capsTheClaimLeaseAtThePersistedCatchUpEnd() {
        PeriodicReportDeliveryStore store = mock(PeriodicReportDeliveryStore.class);
        PeriodicReportMessageGateway gateway = mock(PeriodicReportMessageGateway.class);
        PeriodicReportDeliveryRegistration registration = new PeriodicReportDeliveryRegistration(KEY, METADATA, Optional.empty());
        PeriodicReportDeliverySnapshot open = snapshot(registration, PeriodicReportDeliveryState.OPEN, Optional.empty());
        PeriodicReportDeliveryClaim claim = new PeriodicReportDeliveryClaim(new UUID(0, 1), END);
        PeriodicReportDeliverySnapshot claimed = snapshot(registration, PeriodicReportDeliveryState.CLAIMED, Optional.of(claim));
        when(store.find(KEY)).thenReturn(Optional.empty(), Optional.of(claimed), Optional.of(claimed));
        when(store.register(registration)).thenReturn(open);
        when(store.claim(any(), any())).thenReturn(Optional.of(claim));

        service(store, gateway, Clock.fixed(END.minusSeconds(30), ZoneOffset.UTC)).deliver(
                KEY, METADATA, new PeriodicReportNoOp(ReportType.WEEKLY, PERIOD));

        ArgumentCaptor<PeriodicReportDeliveryClaimRequest> request = ArgumentCaptor.forClass(PeriodicReportDeliveryClaimRequest.class);
        verify(store).claim(org.mockito.ArgumentMatchers.eq(KEY), request.capture());
        assertThat(request.getValue().leaseUntil()).isEqualTo(END);
    }

    @Test
    void doesNotCreateWhenTheWindowEndsDuringExactMatchLookup() {
        MutableClock clock = new MutableClock(END.minusSeconds(1));
        PeriodicReportDeliveryStore store = mock(PeriodicReportDeliveryStore.class);
        PeriodicReportMessageGateway gateway = mock(PeriodicReportMessageGateway.class);
        PeriodicReportRenderer renderer = mock(PeriodicReportRenderer.class);
        PeriodicReport report = mock(PeriodicReport.class);
        when(report.reportType()).thenReturn(ReportType.WEEKLY);
        when(report.period()).thenReturn(PERIOD);
        RenderedReportPage page = new RenderedReportPage("title", List.of(), Optional.empty());
        RenderedPeriodicReport rendered = new RenderedPeriodicReport(List.of(page), "a".repeat(64));
        when(renderer.render(report)).thenReturn(rendered);
        PeriodicReportDeliveryRegistration registration = new PeriodicReportDeliveryRegistration(
                KEY, METADATA, Optional.of(new PeriodicReportDeliveryContent(rendered.contentFingerprint(), 1)));
        PeriodicReportDeliveryClaim claim = new PeriodicReportDeliveryClaim(new UUID(0, 1), END);
        PeriodicReportDeliverySnapshot claimed = snapshot(registration, PeriodicReportDeliveryState.CLAIMED, Optional.of(claim));
        when(store.find(KEY)).thenReturn(Optional.empty(), Optional.of(claimed), Optional.of(claimed), Optional.of(claimed));
        when(store.register(registration)).thenReturn(snapshot(registration, PeriodicReportDeliveryState.OPEN, Optional.empty()));
        when(store.claim(any(), any())).thenReturn(Optional.of(claim));
        when(gateway.findExactMatches(anyLong(), any())).thenAnswer(invocation -> {
            clock.set(END);
            return List.of();
        });

        new PeriodicReportDeliveryService(store, gateway, renderer, clock).deliver(KEY, METADATA, report);

        verify(gateway, never()).create(anyLong(), any());
        verify(store).markExpired(KEY, END);
        verify(store, never()).recordPage(any(), any(), any());
    }

    private static PeriodicReportDeliveryService service(
            PeriodicReportDeliveryStore store, PeriodicReportMessageGateway gateway, Clock clock) {
        return new PeriodicReportDeliveryService(store, gateway, new PeriodicReportRenderer(), clock);
    }

    private static PeriodicReportDeliverySnapshot snapshot(
            PeriodicReportDeliveryRegistration registration,
            PeriodicReportDeliveryState state,
            Optional<PeriodicReportDeliveryClaim> claim) {
        return new PeriodicReportDeliverySnapshot(
                registration, state, claim, state == PeriodicReportDeliveryState.CLAIMED ? 1 : 0,
                Optional.empty(), Optional.empty(), List.of(), Optional.empty(), DUE, DUE);
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        private MutableClock(Instant now) { this.now = now; }
        private void set(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
