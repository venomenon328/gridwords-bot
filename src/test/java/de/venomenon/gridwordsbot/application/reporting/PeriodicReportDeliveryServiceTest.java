package de.venomenon.gridwordsbot.application.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReport;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaim;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaimRequest;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryContent;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryFailure;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryFailureCategory;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryKey;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryMetadata;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryPageProgress;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryRegistration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliverySnapshot;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryState;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportNoOp;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportParticipantSection;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportResult;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportSharedSection;
import de.venomenon.gridwordsbot.domain.reporting.RenderedReportPage;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PeriodicReportDeliveryServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-03T12:00:00Z");
    private static final ReportPeriod PERIOD = new ReportPeriod(LocalDate.of(2026, 7, 27), LocalDate.of(2026, 8, 2));
    private static final PeriodicReportDeliveryKey KEY = new PeriodicReportDeliveryKey(1, 2, ReportType.WEEKLY, PERIOD.startDate());
    private static final PeriodicReportDeliveryMetadata METADATA = new PeriodicReportDeliveryMetadata(
            PERIOD, new ReportDueAt(LocalDate.of(2026, 8, 3), LocalTime.NOON, ZoneOffset.UTC), NOW.plus(Duration.ofHours(72)));

    @Test
    void publishesAllPagesInVisibleAndPersistedOrderThenMarksSuccess() {
        List<String> events = new ArrayList<>();
        RecordingStore store = new RecordingStore(events);
        RecordingGateway gateway = new RecordingGateway(events);

        service(store, gateway).deliver(KEY, METADATA, report(26));

        assertThat(gateway.pages()).extracting(PeriodicReportMessageGateway.ReportPage::pageIndex)
                .containsExactly(0, 1);
        assertThat(store.progress).containsExactly(
                new PeriodicReportDeliveryPageProgress(0, 100),
                new PeriodicReportDeliveryPageProgress(1, 101));
        assertThat(store.succeeded).isTrue();
        assertThat(events).containsSubsequence(
                "find", "register", "claim", "create-0", "record-0", "create-1", "record-1", "succeeded");
    }

    @Test
    void completesNoOpWithoutDiscordIoAndKeepsItsReplayStable() {
        List<String> events = new ArrayList<>();
        RecordingStore store = new RecordingStore(events);
        RecordingGateway gateway = new RecordingGateway(events);
        PeriodicReportNoOp noOp = new PeriodicReportNoOp(ReportType.WEEKLY, PERIOD);

        service(store, gateway).deliver(KEY, METADATA, noOp);

        assertThat(store.noOp).isTrue();
        assertThat(gateway.pages()).isEmpty();
        store.existing = Optional.of(terminalNoOp(store.registration));
        service(store, gateway).deliver(KEY, METADATA, noOp);

        assertThat(gateway.pages()).isEmpty();
        assertThat(store.claimCalls).isEqualTo(1);
    }

    @Test
    void doesNotCallDiscordWhenTheClaimIsUnavailable() {
        RecordingStore store = new RecordingStore(new ArrayList<>());
        store.claimAvailable = false;
        RecordingGateway gateway = new RecordingGateway(new ArrayList<>());

        service(store, gateway).deliver(KEY, METADATA, report(1));

        assertThat(gateway.pages()).isEmpty();
        assertThat(store.progress).isEmpty();
        assertThat(store.succeeded).isFalse();
    }

    @Test
    void leavesAnAlreadyTerminalDeliveryUntouchedWithoutRenderingOrDiscordIo() {
        RecordingStore store = new RecordingStore(new ArrayList<>());
        PeriodicReportNoOp noOp = new PeriodicReportNoOp(ReportType.WEEKLY, PERIOD);
        store.existing = Optional.of(terminalNoOp(noOpRegistration()));
        RecordingGateway gateway = new RecordingGateway(new ArrayList<>());

        service(store, gateway).deliver(KEY, METADATA, noOp);

        assertThat(store.registerCalls).isZero();
        assertThat(store.claimCalls).isZero();
        assertThat(gateway.pages()).isEmpty();
    }

    @Test
    void stopsBeforeTheNextDiscordCallWhenPagePersistenceLosesTheClaim() {
        RecordingStore store = new RecordingStore(new ArrayList<>());
        store.acceptPages = false;
        RecordingGateway gateway = new RecordingGateway(new ArrayList<>());

        service(store, gateway).deliver(KEY, METADATA, report(26));

        assertThat(gateway.pages()).extracting(PeriodicReportMessageGateway.ReportPage::pageIndex).containsExactly(0);
        assertThat(store.succeeded).isFalse();
    }

    @Test
    void persistsRetryableGatewayFailureWithDeterministicBoundedBackoff() {
        RecordingStore store = new RecordingStore(new ArrayList<>());
        RecordingGateway gateway = new RecordingGateway(new ArrayList<>());
        gateway.failure = new PeriodicReportMessageGateway.RetryableMessageException("temporary Discord issue", null);

        service(store, gateway).deliver(KEY, METADATA, report(1));

        assertThat(store.retryFailure.orElseThrow().category()).isEqualTo(PeriodicReportDeliveryFailureCategory.RETRYABLE);
        assertThat(store.nextRetryAt).contains(NOW.plusSeconds(30));
        assertThat(PeriodicReportDeliveryService.retryAt(NOW, 100)).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void persistsPermanentGatewayFailureWithoutRetry() {
        RecordingStore store = new RecordingStore(new ArrayList<>());
        RecordingGateway gateway = new RecordingGateway(new ArrayList<>());
        gateway.failure = new PeriodicReportMessageGateway.PermanentMessageException("missing permission", null);

        service(store, gateway).deliver(KEY, METADATA, report(1));

        assertThat(store.permanentFailure.orElseThrow().category()).isEqualTo(PeriodicReportDeliveryFailureCategory.PERMANENT);
        assertThat(store.nextRetryAt).isEmpty();
    }

    @Test
    void persistsUnknownGatewayOutcomeForLaterReconciliationWithoutAnotherDiscordCall() {
        RecordingStore store = new RecordingStore(new ArrayList<>());
        RecordingGateway gateway = new RecordingGateway(new ArrayList<>());
        gateway.failure = new PeriodicReportMessageGateway.UnknownMessageException("unknown create outcome", null);

        service(store, gateway).deliver(KEY, METADATA, report(1));

        assertThat(store.retryFailure.orElseThrow().category()).isEqualTo(PeriodicReportDeliveryFailureCategory.UNKNOWN);
        assertThat(store.nextRetryAt).contains(NOW.plusSeconds(30));
    }
    @Test
    void persistsUnexpectedFailuresAsUnknownRetryableWork() {
        RecordingStore store = new RecordingStore(new ArrayList<>());
        RecordingGateway gateway = new RecordingGateway(new ArrayList<>());
        gateway.failure = new IllegalStateException("unexpected");

        service(store, gateway).deliver(KEY, METADATA, report(1));

        assertThat(store.retryFailure.orElseThrow().category()).isEqualTo(PeriodicReportDeliveryFailureCategory.UNKNOWN);
        assertThat(store.nextRetryAt).contains(NOW.plusSeconds(30));
    }

    private static PeriodicReportDeliveryService service(RecordingStore store, RecordingGateway gateway) {
        return new PeriodicReportDeliveryService(store, gateway, new PeriodicReportRenderer(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static PeriodicReport report(int playerCount) {
        List<PeriodicReportParticipantSection> participants = java.util.stream.IntStream.rangeClosed(1, playerCount)
                .mapToObj(PeriodicReportDeliveryServiceTest::player).toList();
        return new PeriodicReport(ReportType.WEEKLY, PERIOD, participants, new PeriodicReportSharedSection(
                new ReportSharedDayCounts(playerCount < 2 ? 0 : 7, 0, 0),
                new ReportSharedStreaks(new ReportStreakSnapshot(0, 0), new ReportStreakSnapshot(0, 0))));
    }

    private static PeriodicReportParticipantSection player(int index) {
        ReportGameStatistics grid = game(GameType.GRIDWORDS);
        ReportGameStatistics quad = game(GameType.QUADWORDS);
        return new PeriodicReportParticipantSection(
                new ReportParticipant(index, "P" + index, PERIOD.startDate(), List.of(PERIOD.startDate())),
                new ReportPlayerGameStatistics(index, grid, quad),
                new ReportPersonalDayCounts(1, 0, 0, 0),
                new ReportPersonalStreaks(new ReportStreakSnapshot(0, 0), new ReportStreakSnapshot(0, 0),
                        new ReportStreakSnapshot(0, 0), new ReportStreakSnapshot(0, 0), new ReportStreakSnapshot(0, 0)));
    }

    private static ReportGameStatistics game(GameType type) {
        return new ReportGameStatistics(type, 1, 0, 0, 0, 1, Optional.empty(), 0, 0,
                Duration.ZERO, 0, Optional.empty());
    }

    private static PeriodicReportDeliveryRegistration noOpRegistration() {
        return new PeriodicReportDeliveryRegistration(KEY, METADATA, Optional.empty());
    }

    private static PeriodicReportDeliverySnapshot terminalNoOp(PeriodicReportDeliveryRegistration registration) {
        return new PeriodicReportDeliverySnapshot(registration, PeriodicReportDeliveryState.NO_OP, Optional.empty(), 1,
                Optional.empty(), Optional.empty(), List.of(), Optional.of(NOW), NOW, NOW);
    }

    private static final class RecordingStore implements PeriodicReportDeliveryStore {
        private final List<String> events;
        private Optional<PeriodicReportDeliverySnapshot> existing = Optional.empty();
        private PeriodicReportDeliveryRegistration registration;
        private boolean claimAvailable = true;
        private boolean acceptPages = true;
        private int registerCalls;
        private int claimCalls;
        private boolean succeeded;
        private boolean noOp;
        private final List<PeriodicReportDeliveryPageProgress> progress = new ArrayList<>();
        private Optional<PeriodicReportDeliveryFailure> retryFailure = Optional.empty();
        private Optional<PeriodicReportDeliveryFailure> permanentFailure = Optional.empty();
        private Optional<Instant> nextRetryAt = Optional.empty();

        private RecordingStore(List<String> events) {
            this.events = events;
        }

        @Override public PeriodicReportDeliverySnapshot register(PeriodicReportDeliveryRegistration value) {
            events.add("register");
            registerCalls++;
            registration = value;
            return new PeriodicReportDeliverySnapshot(value, PeriodicReportDeliveryState.OPEN, Optional.empty(), 0,
                    Optional.empty(), Optional.empty(), List.of(), Optional.empty(), NOW, NOW);
        }

        @Override public Optional<PeriodicReportDeliverySnapshot> find(PeriodicReportDeliveryKey key) {
            events.add("find");
            return existing;
        }

        @Override public Optional<PeriodicReportDeliveryClaim> claim(
                PeriodicReportDeliveryKey key, PeriodicReportDeliveryClaimRequest request) {
            events.add("claim");
            claimCalls++;
            return claimAvailable ? Optional.of(new PeriodicReportDeliveryClaim(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"), request.leaseUntil())) : Optional.empty();
        }

        @Override public boolean recordPage(PeriodicReportDeliveryKey key, UUID token, PeriodicReportDeliveryPageProgress page) {
            events.add("record-" + page.pageIndex());
            if (acceptPages) progress.add(page);
            return acceptPages;
        }

        @Override public boolean markSucceeded(PeriodicReportDeliveryKey key, UUID token, Instant completedAt) {
            events.add("succeeded");
            succeeded = true;
            return true;
        }

        @Override public boolean markNoOp(PeriodicReportDeliveryKey key, UUID token, Instant completedAt) {
            events.add("no-op");
            noOp = true;
            return true;
        }

        @Override public boolean markRetryableFailure(
                PeriodicReportDeliveryKey key, UUID token, PeriodicReportDeliveryFailure failure, Instant retryAt) {
            events.add("retryable");
            retryFailure = Optional.of(failure);
            nextRetryAt = Optional.of(retryAt);
            return true;
        }

        @Override public boolean markPermanentFailure(
                PeriodicReportDeliveryKey key, UUID token, PeriodicReportDeliveryFailure failure, Instant completedAt) {
            events.add("permanent");
            permanentFailure = Optional.of(failure);
            return true;
        }

        @Override public boolean markExpired(PeriodicReportDeliveryKey key, Instant completedAt) { return false; }
    }

    private static final class RecordingGateway implements PeriodicReportMessageGateway {
        private final List<String> events;
        private final List<ReportPage> pages = new ArrayList<>();
        private RuntimeException failure;

        private RecordingGateway(List<String> events) {
            this.events = events;
        }

        @Override public long create(long channelId, ReportPage page) {
            events.add("create-" + page.pageIndex());
            if (failure != null) throw failure;
            pages.add(page);
            return 99L + pages.size();
        }

        @Override public void edit(long channelId, long messageId, ReportPage page) { throw new UnsupportedOperationException(); }
        @Override public PublishedReportPage load(long channelId, long messageId) { throw new UnsupportedOperationException(); }
        @Override public List<PublishedReportPage> findExactMatches(long channelId, ReportPage page) {
            throw new UnsupportedOperationException();
        }
        @Override public void delete(long channelId, long messageId) { throw new UnsupportedOperationException(); }

        private List<ReportPage> pages() {
            return List.copyOf(pages);
        }
    }
}
