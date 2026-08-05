package de.venomenon.gridwordsbot.domain.record;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.streak.StreakDayAssessment;
import de.venomenon.gridwordsbot.domain.streak.StreakDayClassifier;
import de.venomenon.gridwordsbot.domain.streak.StreakGameResult;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure derivation of all positive and record-only negative streak runs. */
public final class StreakRunAnalyzer {
    public StreakRunAnalysis analyze(List<StreakGameResult> results,
            List<GameParticipationPeriod> participationPeriods, StreakRunAnalysisWindow window) {
        Objects.requireNonNull(window, "window");
        List<GameParticipationPeriod> periods = List.copyOf(Objects.requireNonNull(participationPeriods,
                "participationPeriods"));
        StreakDayClassifier classifier = new StreakDayClassifier(results, periods);
        List<StreakRun> runs = new ArrayList<>();

        List<Long> players = periods.stream().map(GameParticipationPeriod::playerId).distinct().sorted().toList();
        for (long playerId : players) {
            RecordScope.Personal owner = new RecordScope.Personal(playerId);
            for (StreakRecordMetric metric : StreakRecordMetric.values()) {
                runs.addAll(analyzeMetric(metric, owner, window,
                        day -> personalAssessment(classifier, metric, playerId, day, window.dayClosed(day))));
            }
        }
        for (StreakRecordMetric metric : StreakRecordMetric.values()) {
            if (metric.sharedScopeAllowed()) {
                runs.addAll(analyzeMetric(metric, new RecordScope.Shared(), window,
                        day -> sharedAssessment(classifier, metric, day, window.dayClosed(day))));
            }
        }
        return new StreakRunAnalysis(runs);
    }

    private List<StreakRun> analyzeMetric(StreakRecordMetric metric, RecordScope owner,
            StreakRunAnalysisWindow window, DayAssessment assessment) {
        List<StreakRun> runs = new ArrayList<>();
        LocalDate start = null;
        LocalDate end = null;
        for (LocalDate day = window.firstDate(); !day.isAfter(window.asOfDate()); day = day.plusDays(1)) {
            StreakDayAssessment value = assessment.on(day);
            switch (value.state()) {
                case MET -> {
                    if (start == null) start = day;
                    end = day;
                }
                case PENDING -> {
                    if (!day.equals(window.asOfDate()) || window.asOfDateClosed()) {
                        throw new IllegalStateException("pending is only valid for an open final day");
                    }
                }
                case VIOLATED -> {
                    if (start != null) {
                        runs.add(new StreakRun(new StreakRunIdentity(metric, owner, start), end,
                                status(value.boundaryReason().orElseThrow())));
                        start = null;
                        end = null;
                    }
                }
            }
        }
        if (start != null) {
            runs.add(new StreakRun(new StreakRunIdentity(metric, owner, start), end, StreakRunStatus.RUNNING));
        }
        return runs;
    }

    private static StreakDayAssessment personalAssessment(StreakDayClassifier classifier,
            StreakRecordMetric metric, long playerId, LocalDate day, boolean dayClosed) {
        return switch (metric) {
            case ACTIVITY -> classifier.personalActivity(playerId, day, dayClosed);
            case COMPLETE -> classifier.personalComplete(playerId, day, dayClosed);
            case GRIDWORDS_SOLVED -> classifier.personalSolved(playerId, day, GameType.GRIDWORDS, dayClosed);
            case QUADWORDS_SOLVED -> classifier.personalSolved(playerId, day, GameType.QUADWORDS, dayClosed);
            case PERFECT -> classifier.personalPerfect(playerId, day, dayClosed);
            case GRIDWORDS_DROUGHT -> classifier.personalDrought(playerId, day, GameType.GRIDWORDS, dayClosed);
            case QUADWORDS_DROUGHT -> classifier.personalDrought(playerId, day, GameType.QUADWORDS, dayClosed);
            case WITHOUT_PERFECT_DAY -> classifier.personalWithoutPerfectDay(playerId, day, dayClosed);
        };
    }

    private static StreakDayAssessment sharedAssessment(StreakDayClassifier classifier,
            StreakRecordMetric metric, LocalDate day, boolean dayClosed) {
        return switch (metric) {
            case GRIDWORDS_SOLVED -> classifier.sharedSolved(day, GameType.GRIDWORDS, dayClosed);
            case QUADWORDS_SOLVED -> classifier.sharedSolved(day, GameType.QUADWORDS, dayClosed);
            case COMPLETE -> classifier.sharedComplete(day, dayClosed);
            case PERFECT -> classifier.sharedPerfect(day, dayClosed);
            default -> throw new IllegalArgumentException("metric does not support shared runs");
        };
    }

    private static StreakRunStatus status(StreakDayAssessment.BoundaryReason reason) {
        return switch (reason) {
            case RESULT -> StreakRunStatus.ENDED_BY_RESULT;
            case DAY_CLOSE -> StreakRunStatus.ENDED_BY_DAY_CLOSE;
            case PARTICIPATION -> StreakRunStatus.ENDED_BY_PARTICIPATION;
        };
    }

    @FunctionalInterface
    private interface DayAssessment {
        StreakDayAssessment on(LocalDate day);
    }
}
