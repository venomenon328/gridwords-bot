package de.venomenon.gridwordsbot.application.record;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Structured operational logging kept beside the coordinator, never in record-domain types. */
final class RecordBootstrapLog {
    private static final Logger LOG = LoggerFactory.getLogger(RecordBootstrapCoordinator.class);
    private RecordBootstrapLog() { }
    static void started(long guildId, String version, int attempt, Duration lease) {
        LOG.info("record_bootstrap_started guild_id={} definition_version={} attempt={} lease_ms={}", guildId, version, attempt, lease.toMillis());
    }
    static void succeeded(long guildId, String version, int attempt, int results, int runs, int targets, int created, int replaced, int removed, Duration duration) {
        LOG.info("record_bootstrap_succeeded guild_id={} definition_version={} attempt={} duration_ms={} results={} derived_runs={} target_states={} initialized_states={} replaced_states={} removed_states={}",
                guildId, version, attempt, duration.toMillis(), results, runs, targets, created, replaced, removed);
    }
    static void lostLease(long guildId, String version, int attempt, Duration duration) {
        LOG.warn("record_bootstrap_lost_lease guild_id={} definition_version={} attempt={} duration_ms={}", guildId, version, attempt, duration.toMillis());
    }
    static void retryScheduled(long guildId, String version, int attempt, Duration backoff, RecordBootstrapCoordinator.BootstrapRunResult result, Duration duration) {
        LOG.warn("record_bootstrap_retry guild_id={} definition_version={} attempt={} retry_backoff_ms={} result={} duration_ms={}", guildId, version, attempt, backoff.toMillis(), result, duration.toMillis());
    }
    static void permanentFailure(long guildId, String version, int attempt, RecordBootstrapCoordinator.BootstrapRunResult result, Duration duration) {
        LOG.error("record_bootstrap_permanent_failure guild_id={} definition_version={} attempt={} result={} duration_ms={}", guildId, version, attempt, result, duration.toMillis());
    }
    static void unknownFailure(long guildId, String version, int attempt, Duration duration, RuntimeException failure) {
        LOG.error("record_bootstrap_unknown_failure guild_id={} definition_version={} attempt={} duration_ms={}", guildId, version, attempt, duration.toMillis(), failure);
    }
}
