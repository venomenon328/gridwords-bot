package de.venomenon.gridwordsbot.application.reporting;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReport;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaim;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryContent;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryKey;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryMetadata;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryPageProgress;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryRegistration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliverySnapshot;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryState;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportParticipantSection;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportSharedSection;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PeriodicReportDeliveryRefreshTest {
    private static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private static final Instant NOW = Instant.parse("2026-08-10T07:25:00Z");
    private static final ReportPeriod PERIOD = new ReportPeriod(
            LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 9));
    private static final PeriodicReportDeliveryKey KEY = new PeriodicReportDeliveryKey(
            1L, 2L, ReportType.WEEKLY, PERIOD.startDate());
    private static final ReportDueAt DUE = new ReportDueAt(
            LocalDate.of(2026, 8, 10), LocalTime.of(8, 0), ZONE);
    private static final PeriodicReportDeliveryMetadata METADATA = new PeriodicReportDeliveryMetadata(
            PERIOD, DUE, DUE.instant().plus(Duration.ofHours(72)));

    @Test
    void explicitRefreshReplacesWholeSucceededPageGroupWhenVisibleFingerprintChanged() {
        PeriodicReport oldReport = report("Altes Layout");
        PeriodicReport newReport = report("Neues Layout");
        var oldRendered = new PeriodicReportRenderer().render(oldReport);
        var newRendered = new PeriodicReportRenderer().render(newReport);
        PeriodicReportDeliveryStore store = mock(PeriodicReportDeliveryStore.class);
        PeriodicReportMessageGateway gateway = mock(PeriodicReportMessageGateway.class);
        UUID token = UUID.fromString("00000000-0000-0000-0000-000000000119");
        PeriodicReportDeliveryClaim claim = new PeriodicReportDeliveryClaim(token, NOW.plusSeconds(60));
        var progress = List.of(new PeriodicReportDeliveryPageProgress(0, 900L));
        PeriodicReportDeliveryRegistration oldRegistration = registration(oldRendered.contentFingerprint());
        PeriodicReportDeliverySnapshot succeeded = snapshot(
                oldRegistration, PeriodicReportDeliveryState.SUCCEEDED, Optional.empty(), progress, Optional.of(NOW));
        PeriodicReportDeliverySnapshot claimed = snapshot(
                oldRegistration, PeriodicReportDeliveryState.CLAIMED, Optional.of(claim), progress, Optional.empty());

        when(store.find(KEY)).thenReturn(Optional.of(succeeded), Optional.of(claimed));
        when(store.claim(eq(KEY), any())).thenReturn(Optional.of(claim));
        when(store.replaceContent(eq(KEY), eq(token), any())).thenReturn(true);
        when(store.recordPage(eq(KEY), eq(token), any())).thenReturn(true);
        when(store.markSucceeded(eq(KEY), eq(token), any())).thenReturn(true);
        when(gateway.findExactMatches(eq(2L), any())).thenReturn(List.of());
        when(gateway.create(eq(2L), any())).thenReturn(901L);

        service(store, gateway).deliverRefreshingSucceededContent(KEY, METADATA, newReport);

        verify(gateway).delete(2L, 900L);
        verify(store).replaceContent(
                KEY,
                token,
                new PeriodicReportDeliveryContent(newRendered.contentFingerprint(), newRendered.pages().size()));
        verify(gateway).create(eq(2L), any());
        verify(store).recordPage(eq(KEY), eq(token), any());
        verify(store).markSucceeded(eq(KEY), eq(token), any());
    }

    @Test
    void explicitRefreshDoesNotRecreateSucceededPageGroupWhenFingerprintAlreadyMatches() {
        PeriodicReport report = report("Bereits neues Layout");
        var rendered = new PeriodicReportRenderer().render(report);
        PeriodicReportDeliveryStore store = mock(PeriodicReportDeliveryStore.class);
        PeriodicReportMessageGateway gateway = mock(PeriodicReportMessageGateway.class);
        UUID token = UUID.fromString("00000000-0000-0000-0000-000000000120");
        PeriodicReportDeliveryClaim claim = new PeriodicReportDeliveryClaim(token, NOW.plusSeconds(60));
        var progress = List.of(new PeriodicReportDeliveryPageProgress(0, 900L));
        PeriodicReportDeliveryRegistration registration = registration(rendered.contentFingerprint());
        PeriodicReportDeliverySnapshot succeeded = snapshot(
                registration, PeriodicReportDeliveryState.SUCCEEDED, Optional.empty(), progress, Optional.of(NOW));
        PeriodicReportDeliverySnapshot claimed = snapshot(
                registration, PeriodicReportDeliveryState.CLAIMED, Optional.of(claim), progress, Optional.empty());
        PeriodicReportMessageGateway.ReportPage expected = new PeriodicReportMessageGateway.ReportPage(
                0, rendered.pages().getFirst());
        PeriodicReportMessageGateway.PublishedReportPage published = new PeriodicReportMessageGateway.PublishedReportPage(
                900L, expected);

        when(store.find(KEY)).thenReturn(Optional.of(succeeded), Optional.of(claimed));
        when(store.claim(eq(KEY), any())).thenReturn(Optional.of(claim));
        when(store.markSucceeded(eq(KEY), eq(token), any())).thenReturn(true);
        when(gateway.load(2L, 900L)).thenReturn(published);
        when(gateway.findExactMatches(2L, expected)).thenReturn(List.of(published));

        service(store, gateway).deliverRefreshingSucceededContent(KEY, METADATA, report);

        verify(gateway, never()).delete(eq(2L), any(Long.class));
        verify(gateway, never()).create(eq(2L), any());
        verify(store, never()).replaceContent(eq(KEY), eq(token), any());
        verify(store).markSucceeded(eq(KEY), eq(token), any());
    }

    private static PeriodicReportDeliveryService service(
            PeriodicReportDeliveryStore store,
            PeriodicReportMessageGateway gateway) {
        return new PeriodicReportDeliveryService(
                store, gateway, new PeriodicReportRenderer(), Clock.fixed(NOW, ZONE));
    }

    private static PeriodicReportDeliveryRegistration registration(String fingerprint) {
        return new PeriodicReportDeliveryRegistration(
                KEY,
                METADATA,
                Optional.of(new PeriodicReportDeliveryContent(fingerprint, 1)));
    }

    private static PeriodicReportDeliverySnapshot snapshot(
            PeriodicReportDeliveryRegistration registration,
            PeriodicReportDeliveryState state,
            Optional<PeriodicReportDeliveryClaim> claim,
            List<PeriodicReportDeliveryPageProgress> progress,
            Optional<Instant> completedAt) {
        return new PeriodicReportDeliverySnapshot(
                registration,
                state,
                claim,
                1,
                Optional.empty(),
                Optional.empty(),
                progress,
                completedAt,
                NOW,
                NOW);
    }

    private static PeriodicReport report(String displayName) {
        LocalDate day = PERIOD.startDate();
        ReportGameStatistics grid = game(GameType.GRIDWORDS);
        ReportGameStatistics quad = game(GameType.QUADWORDS);
        PeriodicReportParticipantSection participant = new PeriodicReportParticipantSection(
                new ReportParticipant(1L, displayName, day, List.of(day), List.of(day), List.of(day), List.of(day)),
                new ReportPlayerGameStatistics(1L, grid, quad),
                new ReportPersonalDayCounts(1, 0, 0, 0),
                new ReportPersonalStreaks(zero(), zero(), zero(), zero(), zero()));
        return new PeriodicReport(
                ReportType.WEEKLY,
                PERIOD,
                List.of(participant),
                new PeriodicReportSharedSection(
                        new ReportSharedDayCounts(0, 0, 0),
                        new ReportSharedStreaks(zero(), zero())));
    }

    private static ReportGameStatistics game(GameType gameType) {
        return new ReportGameStatistics(
                gameType, 1, 0, 0, 0, 1, Optional.empty(),
                0, 0, Duration.ZERO, 0, Optional.empty());
    }

    private static ReportStreakSnapshot zero() {
        return new ReportStreakSnapshot(0, 0);
    }
}
