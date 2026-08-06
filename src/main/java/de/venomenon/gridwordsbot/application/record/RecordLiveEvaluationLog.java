package de.venomenon.gridwordsbot.application.record;

import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationClaim;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Structured operational logging for one claimed live-evaluation execution. */
final class RecordLiveEvaluationLog {
    private static final Logger LOG = LoggerFactory.getLogger(RecordLiveEvaluationCoordinator.class);

    private RecordLiveEvaluationLog() { }

    static void completed(RecordLiveEvaluationClaim claim, Duration duration) {
        LOG.info("record_live_evaluation_completed guild_id={} game_result_id={} game_result_version={} origin={} trigger_reference={} attempt={} result=completed duration_ms={}",
                claim.key().guildId(), claim.key().gameResultId(), claim.key().gameResultVersion(),
                claim.processingOrigin(), trigger(claim), claim.attemptCount(), duration.toMillis());
    }

    static void retryable(RecordLiveEvaluationClaim claim, Duration backoff, Duration duration) {
        LOG.warn("record_live_evaluation_retryable guild_id={} game_result_id={} game_result_version={} origin={} trigger_reference={} attempt={} result=retryable retry_backoff_ms={} duration_ms={}",
                claim.key().guildId(), claim.key().gameResultId(), claim.key().gameResultVersion(),
                claim.processingOrigin(), trigger(claim), claim.attemptCount(), backoff.toMillis(), duration.toMillis());
    }

    static void permanent(RecordLiveEvaluationClaim claim, Duration duration) {
        LOG.error("record_live_evaluation_permanent guild_id={} game_result_id={} game_result_version={} origin={} trigger_reference={} attempt={} result=permanent duration_ms={}",
                claim.key().guildId(), claim.key().gameResultId(), claim.key().gameResultVersion(),
                claim.processingOrigin(), trigger(claim), claim.attemptCount(), duration.toMillis());
    }

    static void lostLease(RecordLiveEvaluationClaim claim, Duration duration) {
        LOG.warn("record_live_evaluation_lost_lease guild_id={} game_result_id={} game_result_version={} origin={} trigger_reference={} attempt={} result=lost_lease duration_ms={}",
                claim.key().guildId(), claim.key().gameResultId(), claim.key().gameResultVersion(),
                claim.processingOrigin(), trigger(claim), claim.attemptCount(), duration.toMillis());
    }

    static void unknown(RecordLiveEvaluationClaim claim, Duration duration, RuntimeException failure) {
        LOG.error("record_live_evaluation_unknown guild_id={} game_result_id={} game_result_version={} origin={} trigger_reference={} attempt={} result=unknown duration_ms={}",
                claim.key().guildId(), claim.key().gameResultId(), claim.key().gameResultVersion(),
                claim.processingOrigin(), trigger(claim), claim.attemptCount(), duration.toMillis(), failure);
    }

    private static String trigger(RecordLiveEvaluationClaim claim) {
        return "live-result:" + claim.key().gameResultId();
    }
}
