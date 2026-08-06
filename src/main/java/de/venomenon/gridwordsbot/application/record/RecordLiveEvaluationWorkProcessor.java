package de.venomenon.gridwordsbot.application.record;

import de.venomenon.gridwordsbot.domain.record.RecordLiveEvaluationClaim;
import java.util.function.BooleanSupplier;

/** Executes one token-owned record-evaluation job after it has been claimed. */
@FunctionalInterface
public interface RecordLiveEvaluationWorkProcessor {
    RecordLiveEvaluationProcessor.ProcessingResult process(
            RecordLiveEvaluationClaim claim, BooleanSupplier leaseOwned);
}
