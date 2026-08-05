package de.venomenon.gridwordsbot.domain.record;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordLiveEvaluationSnapshotTest {
    private static final Instant CREATED = Instant.parse("2026-08-05T21:00:00Z");
    private static final RecordLiveEvaluationKey KEY = new RecordLiveEvaluationKey(1, 2, 0);

    @Test
    void rejectsClaimStateWithoutCompleteTokenAndLease() {
        assertThatThrownBy(() -> snapshot(
                        RecordLiveEvaluationState.CLAIMED,
                        Optional.of(UUID.randomUUID()),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claim");
    }

    @Test
    void rejectsRetryAndPermanentStatesWithoutMatchingFailure() {
        assertThatThrownBy(() -> snapshot(
                        RecordLiveEvaluationState.RETRYABLE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(CREATED.plusSeconds(30)),
                        Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-permanent failure");

        assertThatThrownBy(() -> snapshot(
                        RecordLiveEvaluationState.FAILED_PERMANENT,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(CREATED.plusSeconds(1)),
                        Optional.empty(),
                        Optional.of(new RecordWorkFailure(RecordWorkFailureCategory.UNKNOWN, "unknown"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("permanent failure");
    }

    @Test
    void rejectsFailureDetailsOnSuccessfulWork() {
        assertThatThrownBy(() -> snapshot(
                        RecordLiveEvaluationState.SUCCEEDED,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(CREATED.plusSeconds(1)),
                        Optional.empty(),
                        Optional.of(new RecordWorkFailure(RecordWorkFailureCategory.RETRYABLE, "retry"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only failed");
    }

    private static RecordLiveEvaluationSnapshot snapshot(
            RecordLiveEvaluationState state,
            Optional<UUID> token,
            Optional<Instant> lease,
            Optional<Instant> completed,
            Optional<Instant> retry,
            Optional<RecordWorkFailure> failure) {
        return new RecordLiveEvaluationSnapshot(
                KEY,
                RecordProcessingOrigin.LIVE_SUBMISSION,
                state,
                token,
                lease,
                state == RecordLiveEvaluationState.OPEN ? Optional.empty() : Optional.of(CREATED),
                completed,
                1,
                retry,
                failure,
                CREATED,
                CREATED.plusSeconds(1));
    }
}
