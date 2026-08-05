package de.venomenon.gridwordsbot.domain.streak;

import java.util.Objects;
import java.util.Optional;

/** Result of evaluating one calendar day for one streak condition. */
public record StreakDayAssessment(State state, Optional<BoundaryReason> boundaryReason) {
    public StreakDayAssessment {
        Objects.requireNonNull(state, "state");
        boundaryReason = Objects.requireNonNull(boundaryReason, "boundaryReason");
        if ((state == State.VIOLATED) != boundaryReason.isPresent()) {
            throw new IllegalArgumentException("only violated days require a boundary reason");
        }
    }

    public static StreakDayAssessment met() {
        return new StreakDayAssessment(State.MET, Optional.empty());
    }

    public static StreakDayAssessment pending() {
        return new StreakDayAssessment(State.PENDING, Optional.empty());
    }

    public static StreakDayAssessment violated(BoundaryReason reason) {
        return new StreakDayAssessment(State.VIOLATED, Optional.of(Objects.requireNonNull(reason, "reason")));
    }

    public enum State { MET, PENDING, VIOLATED }

    public enum BoundaryReason { RESULT, DAY_CLOSE, PARTICIPATION }
}
