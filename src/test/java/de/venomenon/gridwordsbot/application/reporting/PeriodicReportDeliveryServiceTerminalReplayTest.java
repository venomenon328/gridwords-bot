package de.venomenon.gridwordsbot.application.reporting;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.reporting.PeriodicReport;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaim;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaimRequest;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryContent;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryKey;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryMetadata;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryPageProgress;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryRegistration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliverySnapshot;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryState;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class PeriodicReportDeliveryServiceTerminalReplayTest {
    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
    private static final ReportPeriod PERIOD = new ReportPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
    private static final PeriodicReportDeliveryKey KEY = new PeriodicReportDeliveryKey(1, 2, ReportType.WEEKLY, PERIOD.startDate());
    private static final PeriodicReportDeliveryMetadata METADATA = new PeriodicReportDeliveryMetadata(
            PERIOD, new ReportDueAt(LocalDate.of(2026, 8, 3), LocalTime.NOON, ZoneOffset.UTC), NOW.plus(Duration.ofHours(72)));
    private static final RenderedPeriodicReport RENDERED = new RenderedPeriodicReport(
            List.of(new RenderedReportPage("title", List.of(), Optional.empty())), "a".repeat(64));

    @Test
    void deliversOnePagePersistsItsMessageIdAndMarksSuccess() {
        PeriodicReport report = report();
        PeriodicReportRenderer renderer = mock(PeriodicReportRenderer.class);
        when(renderer.render(report)).thenReturn(RENDERED);
        PeriodicReportDeliveryRegistration registration = registration();
        PeriodicReportDeliveryClaim claim = new PeriodicReportDeliveryClaim(new UUID(0, 7), NOW.plus(Duration.ofMinutes(2)));
        PeriodicReportDeliveryClaimRequest claimRequest = new PeriodicReportDeliveryClaimRequest(NOW, claim.leaseUntil());
        PeriodicReportDeliverySnapshot open = snapshot(registration, PeriodicReportDeliveryState.OPEN, Optional.empty(), 0, List.of(), Optional.empty());
        PeriodicReportDeliverySnapshot claimed = snapshot(registration, PeriodicReportDeliveryState.CLAIMED, Optional.of(claim), 1, List.of(), Optional.empty());
        PeriodicReportMessageGateway.ReportPage page = new PeriodicReportMessageGateway.ReportPage(0, RENDERED.pages().getFirst());
        PeriodicReportDeliveryPageProgress progress = new PeriodicReportDeliveryPageProgress(0, 100);
        PeriodicReportDeliveryStore store = mock(PeriodicReportDeliveryStore.class);
        PeriodicReportMessageGateway messages = mock(PeriodicReportMessageGateway.class);
        when(store.find(KEY)).thenReturn(Optional.empty(), Optional.of(claimed));
        when(store.register(registration)).thenReturn(open);
        when(store.claim(KEY, claimRequest)).thenReturn(Optional.of(claim));
        when(messages.findExactMatches(KEY.channelId(), page)).thenReturn(List.of());
        when(messages.create(KEY.channelId(), page)).thenReturn(100L);
        when(store.recordPage(KEY, claim.token(), progress)).thenReturn(true);
        when(store.markSucceeded(KEY, claim.token(), NOW)).thenReturn(true);

        service(store, messages, renderer, NOW).deliver(KEY, METADATA, report);

        InOrder order = inOrder(store, messages);
        order.verify(store).find(KEY);
        order.verify(store).register(registration);
        order.verify(store).claim(KEY, claimRequest);
        order.verify(store, times(2)).find(KEY);
        order.verify(messages).findExactMatches(KEY.channelId(), page);
        order.verify(store, times(2)).find(KEY);
        order.verify(messages).create(KEY.channelId(), page);
        order.verify(store, times(2)).find(KEY);
        order.verify(store).recordPage(KEY, claim.token(), progress);
        order.verify(store).find(KEY);
        order.verify(messages).findExactMatches(KEY.channelId(), page);
        order.verify(store, times(2)).find(KEY);
        order.verify(store).markSucceeded(KEY, claim.token(), NOW);
        verifyNoMoreInteractions(store, messages);
    }

    @Test
    void replayOfSucceededDeliveryDoesNotRenderRegisterClaimOrCallDiscord() {
        assertTerminalReplayDoesNothing(PeriodicReportDeliveryState.SUCCEEDED, METADATA.catchUpEndsAt());
    }

    @Test
    void replayOfExpiredDeliveryDoesNotRenderRegisterClaimOrCallDiscord() {
        assertTerminalReplayDoesNothing(PeriodicReportDeliveryState.EXPIRED, NOW);
    }

    private static void assertTerminalReplayDoesNothing(PeriodicReportDeliveryState state, Instant now) {
        PeriodicReport report = report();
        PeriodicReportDeliveryStore store = mock(PeriodicReportDeliveryStore.class);
        PeriodicReportMessageGateway messages = mock(PeriodicReportMessageGateway.class);
        PeriodicReportRenderer renderer = mock(PeriodicReportRenderer.class);
        List<PeriodicReportDeliveryPageProgress> pages = state == PeriodicReportDeliveryState.SUCCEEDED
                ? List.of(new PeriodicReportDeliveryPageProgress(0, 100)) : List.of();
        when(store.find(KEY)).thenReturn(Optional.of(snapshot(registration(), state, Optional.empty(), 1, pages, Optional.of(NOW))));

        service(store, messages, renderer, now).deliver(KEY, METADATA, report);

        verify(store).find(KEY);
        verifyNoMoreInteractions(store);
        verifyNoInteractions(messages, renderer);
    }

    private static PeriodicReport report() {
        PeriodicReport report = mock(PeriodicReport.class);
        when(report.reportType()).thenReturn(ReportType.WEEKLY);
        when(report.period()).thenReturn(PERIOD);
        return report;
    }

    private static PeriodicReportDeliveryRegistration registration() {
        return new PeriodicReportDeliveryRegistration(
                KEY, METADATA, Optional.of(new PeriodicReportDeliveryContent(RENDERED.contentFingerprint(), 1)));
    }

    private static PeriodicReportDeliveryService service(
            PeriodicReportDeliveryStore store, PeriodicReportMessageGateway messages, PeriodicReportRenderer renderer, Instant now) {
        return new PeriodicReportDeliveryService(store, messages, renderer, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static PeriodicReportDeliverySnapshot snapshot(
            PeriodicReportDeliveryRegistration registration,
            PeriodicReportDeliveryState state,
            Optional<PeriodicReportDeliveryClaim> claim,
            int attemptCount,
            List<PeriodicReportDeliveryPageProgress> pages,
            Optional<Instant> completedAt) {
        return new PeriodicReportDeliverySnapshot(
                registration, state, claim, attemptCount, Optional.empty(), Optional.empty(), pages, completedAt, NOW, NOW);
    }
}
