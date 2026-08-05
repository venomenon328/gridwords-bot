package de.venomenon.gridwordsbot.application.record;

import de.venomenon.gridwordsbot.domain.record.RecordBootstrapKey;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapProjection;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordHistorySnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailureCategory;
import de.venomenon.gridwordsbot.domain.record.StreakRunAnalysisWindow;
import de.venomenon.gridwordsbot.domain.record.StreakRunAnalyzer;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapStore;
import de.venomenon.gridwordsbot.port.out.RecordHistoryQuery;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

/** Claims one bootstrap lease, scans outside a transaction, and materializes each state in short transactions. */
public class RecordBootstrapCoordinator {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private final RecordBootstrapStore bootstrapStore; private final RecordHistoryQuery historyQuery;
    private final RecordStateService stateService; private final RecordBootstrapProjection projection; private final Clock clock;
    private final Duration leaseDuration;

    public RecordBootstrapCoordinator(RecordBootstrapStore bootstrapStore, RecordHistoryQuery historyQuery,
            RecordStateService stateService, RecordDefinitionCatalog catalog, Clock clock) {
        this(bootstrapStore, historyQuery, stateService, new RecordBootstrapProjection(catalog, new StreakRunAnalyzer()), clock,
                Duration.ofMinutes(2));
    }
    RecordBootstrapCoordinator(RecordBootstrapStore bootstrapStore, RecordHistoryQuery historyQuery,
            RecordStateService stateService, RecordBootstrapProjection projection, Clock clock, Duration leaseDuration) {
        this.bootstrapStore = java.util.Objects.requireNonNull(bootstrapStore); this.historyQuery = java.util.Objects.requireNonNull(historyQuery);
        this.stateService = java.util.Objects.requireNonNull(stateService); this.projection = java.util.Objects.requireNonNull(projection);
        this.clock = java.util.Objects.requireNonNull(clock); this.leaseDuration = java.util.Objects.requireNonNull(leaseDuration);
        if (leaseDuration.isZero() || leaseDuration.isNegative()) throw new IllegalArgumentException("leaseDuration must be positive");
    }

    public BootstrapRunResult run(long guildId) {
        RecordBootstrapKey key = new RecordBootstrapKey(guildId, RecordDefinitionCatalog.recordsV1().version());
        bootstrapStore.register(key);
        var now = clock.instant();
        Optional<de.venomenon.gridwordsbot.domain.record.RecordLeaseClaim> claim = bootstrapStore.claim(key,
                new RecordLeaseClaimRequest(now, now.plus(leaseDuration)));
        if (claim.isEmpty()) return BootstrapRunResult.NOT_CLAIMED;
        var token = claim.orElseThrow().token();
        try {
            RecordHistorySnapshot history = historyQuery.load(guildId);
            StreakRunAnalysisWindow window = window(history);
            String bootstrapKey = guildId + ":" + key.definitionVersion().value();
            for (RecordBootstrapProjection.Candidate candidate : projection.project(guildId, history, window)) {
                if (!bootstrapStore.renewLease(key, token, leaseRequest())) return BootstrapRunResult.LOST_LEASE;
                stateService.initializeSilently(candidate, bootstrapKey, now);
            }
            return bootstrapStore.markSucceeded(key, token, clock.instant()) ? BootstrapRunResult.SUCCEEDED : BootstrapRunResult.LOST_LEASE;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            bootstrapStore.markPermanentFailure(key, token, new RecordWorkFailure(RecordWorkFailureCategory.PERMANENT,
                    "record bootstrap invariant failed"), clock.instant());
            throw ex;
        }
    }

    private RecordLeaseClaimRequest leaseRequest() { var now = clock.instant(); return new RecordLeaseClaimRequest(now, now.plus(leaseDuration)); }
    private StreakRunAnalysisWindow window(RecordHistorySnapshot history) {
        LocalDate earliest = history.results().stream().map(RecordHistorySnapshot.Result::gameDate)
                .min(LocalDate::compareTo).orElseGet(() -> history.participationPeriods().stream()
                        .map(p -> p.activeFrom()).min(LocalDate::compareTo).orElse(null));
        if (earliest == null) return new StreakRunAnalysisWindow(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 1), true);
        var local = clock.instant().atZone(BERLIN);
        return new StreakRunAnalysisWindow(earliest, local.toLocalDate(), !local.toLocalTime().isBefore(LocalTime.of(6, 0)));
    }
    public enum BootstrapRunResult { SUCCEEDED, NOT_CLAIMED, LOST_LEASE }
}
