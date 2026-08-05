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
import de.venomenon.gridwordsbot.port.out.RecordBootstrapMetrics;
import de.venomenon.gridwordsbot.port.out.RecordEventIdempotencyConflictException;
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
    private final Duration retryBackoff;
    private final RecordDefinitionCatalog catalog;
    private final RecordBootstrapMetrics metrics;

    public RecordBootstrapCoordinator(RecordBootstrapStore bootstrapStore, RecordHistoryQuery historyQuery,
            RecordStateService stateService, RecordDefinitionCatalog catalog, Clock clock) {
        this(bootstrapStore, historyQuery, stateService, catalog, new RecordBootstrapProjection(catalog, new StreakRunAnalyzer()), clock,
                Duration.ofMinutes(2), Duration.ofMinutes(1), (result, failureCategory, duration) -> { });
    }
    RecordBootstrapCoordinator(RecordBootstrapStore bootstrapStore, RecordHistoryQuery historyQuery,
            RecordStateService stateService, RecordDefinitionCatalog catalog, RecordBootstrapProjection projection, Clock clock,
            Duration leaseDuration, Duration retryBackoff, RecordBootstrapMetrics metrics) {
        this.bootstrapStore = java.util.Objects.requireNonNull(bootstrapStore); this.historyQuery = java.util.Objects.requireNonNull(historyQuery);
        this.stateService = java.util.Objects.requireNonNull(stateService); this.projection = java.util.Objects.requireNonNull(projection);
        this.clock = java.util.Objects.requireNonNull(clock); this.leaseDuration = java.util.Objects.requireNonNull(leaseDuration);
        this.retryBackoff = java.util.Objects.requireNonNull(retryBackoff);
        this.catalog = java.util.Objects.requireNonNull(catalog);
        this.metrics = java.util.Objects.requireNonNull(metrics);
        if (leaseDuration.isZero() || leaseDuration.isNegative()) throw new IllegalArgumentException("leaseDuration must be positive");
        if (retryBackoff.isZero() || retryBackoff.isNegative()) throw new IllegalArgumentException("retryBackoff must be positive");
    }

    public RecordBootstrapCoordinator(RecordBootstrapStore bootstrapStore, RecordHistoryQuery historyQuery,
            RecordStateService stateService, RecordDefinitionCatalog catalog, Clock clock,
            Duration leaseDuration, Duration retryBackoff, RecordBootstrapMetrics metrics) {
        this(bootstrapStore, historyQuery, stateService, catalog, new RecordBootstrapProjection(catalog, new StreakRunAnalyzer()), clock,
                leaseDuration, retryBackoff, metrics);
    }

    public BootstrapRunResult run(long guildId) {
        long startedNanos = System.nanoTime();
        BootstrapRunResult result = BootstrapRunResult.UNKNOWN;
        Optional<RecordWorkFailureCategory> failureCategory = Optional.empty();
        RecordBootstrapKey key = new RecordBootstrapKey(guildId, catalog.version());
        java.util.UUID token = null;
        int attempt = 0;
        try {
            bootstrapStore.register(key);
            var now = clock.instant();
            Optional<de.venomenon.gridwordsbot.domain.record.RecordLeaseClaim> claim = bootstrapStore.claim(key,
                    new RecordLeaseClaimRequest(now, now.plus(leaseDuration)));
            if (claim.isEmpty()) return result = BootstrapRunResult.NOT_CLAIMED;
            token = claim.orElseThrow().token();
            var snapshot = bootstrapStore.find(key).orElseThrow(
                    () -> new RecordPermanentFailure("claimed bootstrap is missing", null));
            attempt = snapshot.attemptCount();
            java.time.Instant detectedAt = snapshot.startedAt()
                    .orElseThrow(() -> new RecordPermanentFailure("claimed bootstrap has no stable startedAt", null));
            RecordBootstrapLog.started(guildId, key.definitionVersion().value(), snapshot.attemptCount(), leaseDuration);
            if (!bootstrapStore.renewLease(key, token, leaseRequest())) {
                RecordBootstrapLog.lostLease(guildId, key.definitionVersion().value(), snapshot.attemptCount(), elapsed(startedNanos));
                return result = BootstrapRunResult.LOST_LEASE;
            }
            RecordHistorySnapshot history = historyQuery.load(guildId);
            if (!bootstrapStore.renewLease(key, token, leaseRequest())) {
                RecordBootstrapLog.lostLease(guildId, key.definitionVersion().value(), snapshot.attemptCount(), elapsed(startedNanos));
                return result = BootstrapRunResult.LOST_LEASE;
            }
            StreakRunAnalysisWindow window = analysisWindow(history, clock);
            String bootstrapKey = guildId + ":" + key.definitionVersion().value();
            RecordBootstrapProjection.Projection projectionResult = projection.projectWithRunCount(guildId, history, window);
            java.util.List<RecordBootstrapProjection.Candidate> targets = projectionResult.candidates();
            Map<de.venomenon.gridwordsbot.domain.record.RecordStateKey, RecordBootstrapProjection.Candidate> targetsByKey =
                    targets.stream().collect(Collectors.toUnmodifiableMap(
                            RecordBootstrapProjection.Candidate::key, candidate -> candidate));
            int createdStates = 0;
            int replacedStates = 0;
            for (RecordBootstrapProjection.Candidate candidate : targets) {
                if (!bootstrapStore.renewLease(key, token, leaseRequest())) {
                    RecordBootstrapLog.lostLease(guildId, key.definitionVersion().value(), snapshot.attemptCount(), elapsed(startedNanos));
                    return result = BootstrapRunResult.LOST_LEASE;
                }
                RecordStateService.RebuildResult reconciliation = stateService.reconcileCanonicalTarget(candidate, bootstrapKey, detectedAt);
                if (reconciliation == RecordStateService.RebuildResult.CREATED) createdStates++;
                if (reconciliation == RecordStateService.RebuildResult.REPLACED) replacedStates++;
                if (reconciliation == RecordStateService.RebuildResult.RETRY_EXHAUSTED) {
                    throw new RecordRetryableFailure("record state CAS retry exhausted", null);
                }
            }
            int removedStates = 0;
            for (de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot current :
                    stateService.states(guildId, key.definitionVersion())) {
                if (targetsByKey.containsKey(current.key())) continue;
                if (!bootstrapStore.renewLease(key, token, leaseRequest())) {
                    RecordBootstrapLog.lostLease(guildId, key.definitionVersion().value(), snapshot.attemptCount(), elapsed(startedNanos));
                    return result = BootstrapRunResult.LOST_LEASE;
                }
                RecordStateService.RebuildResult removal = stateService.removeAbsentCanonicalTarget(current.key());
                if (removal == RecordStateService.RebuildResult.REMOVED) removedStates++;
                if (removal == RecordStateService.RebuildResult.RETRY_EXHAUSTED) {
                    throw new RecordRetryableFailure("record state CAS retry exhausted", null);
                }
            }
            result = bootstrapStore.markSucceeded(key, token, clock.instant()) ? BootstrapRunResult.SUCCEEDED : BootstrapRunResult.LOST_LEASE;
            if (result == BootstrapRunResult.SUCCEEDED) {
                RecordBootstrapLog.succeeded(guildId, key.definitionVersion().value(), snapshot.attemptCount(), history.results().size(),
                        projectionResult.derivedRunCount(), targets.size(), createdStates, replacedStates, removedStates, elapsed(startedNanos));
            } else {
                RecordBootstrapLog.lostLease(guildId, key.definitionVersion().value(), snapshot.attemptCount(), elapsed(startedNanos));
            }
            return result;
        } catch (RecordRetryableFailure ex) {
            failureCategory = Optional.of(RecordWorkFailureCategory.RETRYABLE);
            if (token == null) throw ex;
            boolean marked = bootstrapStore.markRetryableFailure(key, token, new RecordWorkFailure(RecordWorkFailureCategory.RETRYABLE,
                    "record bootstrap retryable failure"), clock.instant().plus(retryBackoff));
            result = marked ? BootstrapRunResult.RETRY_SCHEDULED : BootstrapRunResult.LOST_LEASE;
            if (marked) {
                RecordBootstrapLog.retryScheduled(guildId, key.definitionVersion().value(), attempt, retryBackoff, result, elapsed(startedNanos));
            } else {
                RecordBootstrapLog.lostLease(guildId, key.definitionVersion().value(), attempt, elapsed(startedNanos));
            }
            return result;
        } catch (RecordPermanentFailure | RecordEventIdempotencyConflictException ex) {
            failureCategory = Optional.of(RecordWorkFailureCategory.PERMANENT);
            if (token == null) throw ex;
            boolean marked = bootstrapStore.markPermanentFailure(key, token,
                    new RecordWorkFailure(RecordWorkFailureCategory.PERMANENT, "record bootstrap permanent failure"), clock.instant());
            result = marked ? BootstrapRunResult.FAILED_PERMANENT : BootstrapRunResult.LOST_LEASE;
            if (marked) {
                RecordBootstrapLog.permanentFailure(guildId, key.definitionVersion().value(), attempt, result, elapsed(startedNanos));
            } else {
                RecordBootstrapLog.lostLease(guildId, key.definitionVersion().value(), attempt, elapsed(startedNanos));
            }
            return result;
        } catch (RuntimeException ex) {
            failureCategory = Optional.of(RecordWorkFailureCategory.UNKNOWN);
            result = BootstrapRunResult.UNKNOWN;
            RecordBootstrapLog.unknownFailure(guildId, key.definitionVersion().value(), attempt, elapsed(startedNanos), ex);
            throw ex;
        } finally {
            metrics.record(result, failureCategory, Duration.ofNanos(System.nanoTime() - startedNanos));
        }
    }

    private RecordLeaseClaimRequest leaseRequest() { var now = clock.instant(); return new RecordLeaseClaimRequest(now, now.plus(leaseDuration)); }
    private static Duration elapsed(long startedNanos) { return Duration.ofNanos(System.nanoTime() - startedNanos); }
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
    public enum BootstrapRunResult { SUCCEEDED, NOT_CLAIMED, LOST_LEASE, RETRY_SCHEDULED, FAILED_PERMANENT, UNKNOWN }
}
