package de.venomenon.gridwordsbot.application.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.GameType;
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
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportParticipantSection;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportSharedSection;
import de.venomenon.gridwordsbot.domain.reporting.RenderedPeriodicReport;
import de.venomenon.gridwordsbot.domain.reporting.ReportDueAt;
import de.venomenon.gridwordsbot.domain.reporting.ReportGameStatistics;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipant;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportPersonalDayCounts;
import de.venomenon.gridwordsbot.domain.reporting.ReportPersonalStreaks;
import de.venomenon.gridwordsbot.domain.reporting.ReportPlayerGameStatistics;
import de.venomenon.gridwordsbot.domain.reporting.ReportSharedDayCounts;
import de.venomenon.gridwordsbot.domain.reporting.ReportSharedStreaks;
import de.venomenon.gridwordsbot.domain.reporting.ReportStreakSnapshot;
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
    private static final PeriodicReportDeliveryKey KEY =
            new PeriodicReportDeliveryKey(1, 2, ReportType.WEEKLY, PERIOD.startDate());
    private static final PeriodicReportDeliveryMetadata METADATA = new PeriodicReportDeliveryMetadata(
            PERIOD,
            new ReportDueAt(LocalDate.of(2026, 8, 3), LocalTime.NOON, ZoneOffset.UTC),
            NOW.plus(Duration.ofHours(72)));

    @Test
    void deliversOnePagePersistsItsMessageIdAndMarksSuccess() {
        PeriodicReport report = report();
        PeriodicReportRenderer renderer = new PeriodicReportRenderer();
        RenderedPeriodicReport rendered = renderer.render(report);
        assertThat(rendered.pages()).hasSize(1);

        PeriodicReportDeliveryRegistration registration = registration(rendered);
        PeriodicReportDeliveryClaim claim =
                new PeriodicReportDeliveryClaim(new UUID(0, 7), NOW.plus(Duration.ofMinutes(2)));
        PeriodicReportDeliveryClaimRequest claimRequest =
                new PeriodicReportDeliveryClaimRequest(NOW, NOW.plus(Duration.ofMinutes(2)));
        PeriodicReportDeliverySnapshot open = snapshot(
                registration,
                PeriodicReportDeliveryState.OPEN,
                Optional.empty(),
                0,
                List.of(),
                Optional.empty());
        PeriodicReportDeliverySnapshot claimed = snapshot(
                registration,
                PeriodicReportDeliveryState.CLAIMED,
                Optional.of(claim),
                1,
                List.of(),
                Optional.empty());
        PeriodicReportMessageGateway.ReportPage page =
                new PeriodicReportMessageGateway.ReportPage(0, rendered.pages().get(0));
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

        service(store, messages, renderer).deliver(KEY, METADATA, report);

        InOrder order = inOrder(store, messages);
        order.verify(store).find(KEY);
        order.verify(store).register(registration);
        order.verify(store).claim(KEY, claimRequest);
        order.verify(store).find(KEY);
        order.verify(messages).findExactMatches(KEY.channelId(), page);
        order.verify(messages).create(KEY.channelId(), page);
        order.verify(store).recordPage(KEY, claim.token(), progress);
        order.verify(messages).findExactMatches(KEY.channelId(), page);
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
        when(store.find(KEY)).thenReturn(Optional.of(terminalSnapshot(state, report)));

        service(store, messages, renderer, now).deliver(KEY, METADATA, report);

        verify(store).find(KEY);
        verifyNoMoreInteractions(store);
        verifyNoInteractions(messages, renderer);
    }

    private static PeriodicReportDeliveryService service(
            PeriodicReportDeliveryStore store,
            PeriodicReportMessageGateway messages,
            PeriodicReportRenderer renderer) {
        return service(store, messages, renderer, NOW);
    }

    private static PeriodicReportDeliveryService service(
            PeriodicReportDeliveryStore store,
            PeriodicReportMessageGateway messages,
            PeriodicReportRenderer renderer,
            Instant now) {
        return new PeriodicReportDeliveryService(store, messages, renderer, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static PeriodicReportDeliverySnapshot terminalSnapshot(
            PeriodicReportDeliveryState state, PeriodicReport report) {
        RenderedPeriodicReport rendered = new PeriodicReportRenderer().render(report);
        PeriodicReportDeliveryRegistration registration = registration(rendered);
        List<PeriodicReportDeliveryPageProgress> pages = state == PeriodicReportDeliveryState.SUCCEEDED
                ? List.of(new PeriodicReportDeliveryPageProgress(0, 100))
                : List.of();
        return snapshot(registration, state, Optional.empty(), 1, pages, Optional.of(NOW));
    }

    private static PeriodicReportDeliveryRegistration registration(RenderedPeriodicReport rendered) {
        return new PeriodicReportDeliveryRegistration(
                KEY,
                METADATA,
                Optional.of(new PeriodicReportDeliveryContent(
                        rendered.contentFingerprint(), rendered.pages().size())));
    }

    private static PeriodicReportDeliverySnapshot snapshot(
            PeriodicReportDeliveryRegistration registration,
            PeriodicReportDeliveryState state,
            Optional<PeriodicReportDeliveryClaim> claim,
            int attemptCount,
            List<PeriodicReportDeliveryPageProgress> pages,
            Optional<Instant> completedAt) {
        return new PeriodicReportDeliverySnapshot(
                registration,
                state,
                claim,
                attemptCount,
                Optional.empty(),
                Optional.empty(),
                pages,
                completedAt,
                NOW,
                NOW);
    }

    private static PeriodicReport report() {
        ReportGameStatistics grid = game(GameType.GRIDWORDS);
        ReportGameStatistics quad = game(GameType.QUADWORDS);
        PeriodicReportParticipantSection participant = new PeriodicReportParticipantSection(
                new ReportParticipant(1, "P1", PERIOD.startDate(), List.of(PERIOD.startDate())),
                new ReportPlayerGameStatistics(1, grid, quad),
                new ReportPersonalDayCounts(1, 0, 0, 0),
                new ReportPersonalStreaks(
                        new ReportStreakSnapshot(0, 0),
                        new ReportStreakSnapshot(0, 0),
                        new ReportStreakSnapshot(0, 0),
                        new ReportStreakSnapshot(0, 0),
                        new ReportStreakSnapshot(0, 0)));
        return new PeriodicReport(
                ReportType.WEEKLY,
                PERIOD,
                List.of(participant),
                new PeriodicReportSharedSection(
                        new ReportSharedDayCounts(0, 0, 0),
                        new ReportSharedStreaks(
                                new ReportStreakSnapshot(0, 0),
                                new ReportStreakSnapshot(0, 0))));
    }

    private static ReportGameStatistics game(GameType type) {
        return new ReportGameStatistics(
                type,
                1,
                0,
                0,
                0,
                1,
                Optional.empty(),
                0,
                0,
                Duration.ZERO,
                0,
                Optional.empty());
    }
}
