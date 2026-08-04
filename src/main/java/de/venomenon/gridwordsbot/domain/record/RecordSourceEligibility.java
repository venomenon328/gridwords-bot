package de.venomenon.gridwordsbot.domain.record;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.util.Objects;

/** Typisierte Eignung der kanonischen Quelle einer Rekorddefinition. */
public sealed interface RecordSourceEligibility
        permits RecordSourceEligibility.SolvedGameResult, RecordSourceEligibility.StreakRun {
    RecordSourceType sourceType();

    record SolvedGameResult(GameType game) implements RecordSourceEligibility {
        public SolvedGameResult {
            Objects.requireNonNull(game, "game");
        }

        @Override
        public RecordSourceType sourceType() {
            return RecordSourceType.GAME_RESULT;
        }

        public boolean accepts(GameType candidateGame, ShareOutcome outcome) {
            Objects.requireNonNull(candidateGame, "candidateGame");
            Objects.requireNonNull(outcome, "outcome");
            return game == candidateGame && outcome instanceof ShareOutcome.Solved;
        }
    }

    record StreakRun(StreakRecordMetric metric) implements RecordSourceEligibility {
        public StreakRun {
            Objects.requireNonNull(metric, "metric");
        }

        @Override
        public RecordSourceType sourceType() {
            return RecordSourceType.STREAK_RUN;
        }

        public boolean accepts(StreakRecordMetric candidateMetric) {
            return metric == Objects.requireNonNull(candidateMetric, "candidateMetric");
        }
    }
}
