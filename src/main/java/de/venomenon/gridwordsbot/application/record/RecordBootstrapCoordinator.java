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
import de.venomenon.gridwordsbot.port.out.RecordRetryableFailure;
import de.venomenon.gridwordsbot.port.out.RecordPermanentFailure;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Claims one bootstrap lease, scans outside a transaction, and materializes each state in short transactions. */
public class RecordBootstrapCoordinator {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private final RecordBootstrapStore bootstrapStore; private final RecordHistoryQuery historyQuery;
    private final RecordStateService stateService; private final RecordBootstrapProjection projection; private final Clock clock;
    private final Duration leaseDuration;
    private final RecordDefinitionCatalog catalog;

    public RecordBootstrapCoordinator(RecordBootstrapStore bootstrapStore, RecordHistoryQuery historyQuery,
            RecordStateService stateService, RecordDefinitionCatalog catalog, Clock clock) {
        this(bootstrapStore, historyQuery, stateService, catalog, new RecordBootstrapProjection(catalog, new StreakRunAnalyzer()), clock,
                Duration.ofMinutes(2));
    }
    RecordBootstrapCoordinator(RecordBootstrapStore bootstrapStore, RecordHistoryQuery historyQuery,
            RecordStateService stateService, RecordDefinitionCatalog catalog, RecordBootstrapProjection projection, Clock clock, Duration leaseDuration) {
        this.bootstrapStore = java.util.Objects.requireNonNull(bootstrapStore); this.historyQuery = java.util.Objects.requireNonNull(historyQuery);
        this.stateService = java.util.Objects.requireNonNull(stateService); this.projection = java.util.Objects.requireNonNull(projection);
        this.clock = java.util.Objects.requireNonNull(clock); this.leaseDuration = java.util.Objects.requireNonNull(leaseDuration);
        this.catalog = java.util.Objects.requireNonNull(catalog);
        if (leaseDuration.isZero() || leaseDuration.isNegative()) throw new IllegalArgumentException("leaseDuration must be positive");
    }

    public BootstrapRunResult run(long guildId) {
        RecordBootstrapKey key = new RecordBootstrapKey(guildId, catalog.version());
        bootstrapStore.register(key);
        var now = clock.instant();
        Optional<de.venomenon.gridwordsbot.domain.record.RecordLeaseClaim> claim = bootstrapStore.claim(key,
                new RecordLeaseClaimRequest(now, now.plus(leaseDuration)));
        if (claim.isEmpty()) return BootstrapRunResult.NOT_CLAIMED;
        var token = claim.orElseThrow().token();
        java.time.Instant detectedAt = bootstrapStore.find(key).flatMap(de.venomenon.gridwordsbot.domain.record.RecordBootstrapSnapshot::startedAt)
                .orElseThrow(() -> new IllegalStateException("claimed bootstrap has no stable startedAt"));
        try {
            if (!bootstrapStore.renewLease(key, token, leaseRequest())) return BootstrapRunResult.LOST_LEASE;
            RecordHistorySnapshot history = historyQuery.load(guildId);
            if (!bootstrapStore.renewLease(key, token, leaseRequest())) return BootstrapRunResult.LOST_LEASE;
            StreakRunAnalysisWindow window = analysisWindow(history, clock);
            String bootstrapKey = guildId + ":" + key.definitionVersion().value();
            java.util.List<RecordBootstrapProjection.Candidate> targets = projection.project(guildId, history, window);
            Map<de.venomenon.gridwordsbot.domain.record.RecordStateKey, RecordBootstrapProjection.Candidate> targetsByKey =
                    targets.stream().collect(Collectors.toUnmodifiableMap(
                            RecordBootstrapProjection.Candidate::key, candidate -> candidate));
            for (RecordBootstrapProjection.Candidate candidate : targets) {
                if (!bootstrapStore.renewLease(key, token, leaseRequest())) return BootstrapRunResult.LOST_LEASE;
                if (stateService.reconcileCanonicalTarget(candidate, bootstrapKey, detectedAt)
                        == RecordStateService.RebuildResult.RETRY_EXHAUSTED) {
                    throw new RecordRetryableFailure("record state CAS retry exhausted", null);
                }
            }
            for (de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot current :
                    stateService.states(guildId, key.definitionVersion())) {
                if (targetsByKey.containsKey(current.key())) continue;
                if (!bootstrapStore.renewLease(key, token, leaseRequest())) return BootstrapRunResult.LOST_LEASE;
                if (stateService.removeAbsentCanonicalTarget(current.key())
                        == RecordStateService.RebuildResult.RETRY_EXHAUSTED) {
                    throw new RecordRetryableFailure("record state CAS retry exhausted", null);
                }
            }
            return bootstrapStore.markSucceeded(key, token, clock.instant()) ? BootstrapRunResult.SUCCEEDED : BootstrapRunResult.LOST_LEASE;
        } catch (RecordRetryableFailure ex) {
            boolean marked = bootstrapStore.markRetryableFailure(key, token, new RecordWorkFailure(RecordWorkFailureCategory.RETRYABLE,
                    "record bootstrap retryable failure"), clock.instant().plus(Duration.ofMinutes(1)));
            return marked ? BootstrapRunResult.RETRY_SCHEDULED : BootstrapRunResult.LOST_LEASE;
        } catch (RecordPermanentFailure ex) {
            boolean marked = bootstrapStore.markPermanentFailure(key, token,
                    new RecordWorkFailure(RecordWorkFailureCategory.PERMANENT, "record bootstrap permanent failure"), clock.instant());
            return marked ? BootstrapRunResult.FAILED_PERMANENT : BootstrapRunResult.LOST_LEASE;
        }
    }

    private RecordLeaseClaimRequest leaseRequest() { var now = clock.instant(); return new RecordLeaseClaimRequest(now, now.plus(leaseDuration)); }
    static StreakRunAnalysisWindow analysisWindow(RecordHistorySnapshot history, Clock clock) {
        LocalDate earliest = history.results().stream().map(RecordHistorySnapshot.Result::gameDate)
                .min(LocalDate::compareTo).orElseGet(() -> history.participationPeriods().stream()
                        .map(p -> p.activeFrom()).min(LocalDate::compareTo).orElse(null));
        if (earliest == null) return new StreakRunAnalysisWindow(LocalDate.of(1970, 1, 1), LocalDate.of(1970, 1, 1), true);
        var local = clock.instant().atZone(BERLIN);
        LocalDate today = local.toLocalDate();
        LocalDate firstOpen = local.toLocalTime().isBefore(LocalTime.of(6, 0)) ? today.minusDays(1) : today;
        if (firstOpen.isBefore(earliest)) firstOpen = earliest;
        return new StreakRunAnalysisWindow(earliest, today, firstOpen);
    }
    public enum BootstrapRunResult { SUCCEEDED, NOT_CLAIMED, LOST_LEASE, RETRY_SCHEDULED, FAILED_PERMANENT }
}
