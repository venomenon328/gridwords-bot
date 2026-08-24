package de.venomenon.gridwordsbot.domain.achievement;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.streak.StreakDayAssessment;
import de.venomenon.gridwordsbot.domain.streak.StreakDayClassifier;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/** Reine, deterministische historische Auswertung des kuratierten Achievement-Katalogs. */
public final class AchievementEvaluator {
    public static final ZoneId DEFAULT_TIME_ZONE = ZoneId.of("Europe/Berlin");
    private static final String SOLVED_BOARD_ROW = "\uD83D\uDFE9".repeat(5);

    private final AchievementDefinitionCatalog catalog;

    public AchievementEvaluator() {
        this(AchievementDefinitionCatalog.achievementsV2());
    }

    public AchievementEvaluator(AchievementDefinitionCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    public AchievementEvaluation evaluate(AchievementHistorySnapshot snapshot) {
        return evaluate(snapshot, DEFAULT_TIME_ZONE);
    }

    public AchievementEvaluation evaluate(AchievementHistorySnapshot snapshot, ZoneId timeZone) {
        Context context = new Context(
                Objects.requireNonNull(snapshot, "snapshot"),
                Objects.requireNonNull(timeZone, "timeZone"));
        List<AchievementEvidence> evidence = new ArrayList<>();
        for (AchievementDefinition definition : catalog.definitions()) {
            evaluate(definition.rule(), context).ifPresent(fact -> evidence.add(new AchievementEvidence(
                    definition.key(), fact.earnedOn(), fact.kind(), fact.reference())));
        }
        return new AchievementEvaluation(evidence);
    }

    private Optional<EarnedFact> evaluate(AchievementRule rule, Context context) {
        return switch (rule) {
            case AchievementRule.ParticipationCount value -> countResult(
                    context.resultsFor(value.game()), value.threshold(), "participation");
            case AchievementRule.ParticipationStreak value -> streak(
                    context, value.game(), value.threshold(), false);
            case AchievementRule.SuccessStreak value -> streak(
                    context, value.game(), value.threshold(), true);
            case AchievementRule.ExactSolvedAttempts value -> firstResult(
                    context.resultsFor(value.game()),
                    result -> result.solved() && result.attempts().orElseThrow() == value.attempts());
            case AchievementRule.CrossGameParticipationCount value -> crossGameCount(
                    context, value.threshold(), false);
            case AchievementRule.CrossGameSuccessCount value -> crossGameCount(
                    context, value.threshold(), true);
            case AchievementRule.TotalResultCount value -> countResult(
                    context.snapshot().results(), value.threshold(), "total-results");
            case AchievementRule.QuadWordsConsecutiveBoardAttempts ignored -> firstResult(
                    context.resultsFor(GameType.QUADWORDS), AchievementEvaluator::isConsecutiveBoardCompletion);
            case AchievementRule.QuadWordsOutlierBoard value -> firstResult(
                    context.resultsFor(GameType.QUADWORDS),
                    result -> hasOutlierBoard(result, value.minimumGap()));
            case AchievementRule.CrossGameEqualFinalScore ignored -> firstCrossGameDay(
                    context, pair -> pair.bothSolved()
                            && pair.gridWords().attempts().orElseThrow() == pair.quadWords().attempts().orElseThrow());
            case AchievementRule.CrossGameExactAttempts value -> firstCrossGameDay(
                    context, pair -> pair.bothSolved()
                            && pair.gridWords().attempts().orElseThrow() == value.gridWordsAttempts()
                            && pair.quadWords().attempts().orElseThrow() == value.quadWordsAttempts());
            case AchievementRule.ConsecutiveSameSuccessfulResults value -> consecutiveSameSuccess(
                    context.resultsFor(value.game()), value.resultCount());
            case AchievementRule.ConsecutiveFailures value -> consecutiveFailures(
                    context.resultsFor(value.game()), value.resultCount());
            case AchievementRule.GridWordsRepeatedPattern value -> firstResult(
                    context.resultsFor(GameType.GRIDWORDS), result -> hasRepeatedGridWordsPattern(result, value.rowCount()));
            case AchievementRule.AllYellowBoardRow ignored -> firstResult(
                    context.snapshot().results(), AchievementEvaluator::hasAllYellowBoardRow);
            case AchievementRule.LocalTimeBefore value -> firstTimedResult(
                    context, time -> time.isBefore(value.exclusiveUpperBound()));
            case AchievementRule.LocalTimeAtOrAfter value -> firstTimedResult(
                    context, time -> !time.isBefore(value.lowerBound()));
        };
    }

    private static Optional<EarnedFact> countResult(
            List<AchievementHistorySnapshot.Result> results, int threshold, String aggregateType) {
        if (results.size() < threshold) {
            return Optional.empty();
        }
        AchievementHistorySnapshot.Result thresholdResult = results.get(threshold - 1);
        return Optional.of(new EarnedFact(
                thresholdResult.gameDate(),
                AchievementEvidence.Kind.AGGREGATE,
                aggregateType + ":" + threshold + ":game-result:" + thresholdResult.resultId()));
    }

    private static Optional<EarnedFact> crossGameCount(Context context, int threshold, boolean requireSuccess) {
        int count = 0;
        for (Map.Entry<LocalDate, DayResults> entry : context.days().entrySet()) {
            DayResults pair = entry.getValue();
            if (!pair.complete() || (requireSuccess && !pair.bothSolved())) {
                continue;
            }
            count++;
            if (count == threshold) {
                return Optional.of(new EarnedFact(
                        entry.getKey(),
                        AchievementEvidence.Kind.GAME_DAY,
                        (requireSuccess ? "cross-game-success:" : "cross-game-participation:")
                                + threshold + ":" + entry.getKey()));
            }
        }
        return Optional.empty();
    }

    private static Optional<EarnedFact> streak(
            Context context, GameType game, int threshold, boolean requireSuccess) {
        List<AchievementHistorySnapshot.Result> gameResults = context.resultsFor(game);
        if (gameResults.isEmpty()) {
            return Optional.empty();
        }
        LocalDate firstDate = gameResults.getFirst().gameDate();
        LocalDate lastDate = gameResults.getLast().gameDate();
        int running = 0;
        LocalDate runStart = null;
        for (LocalDate day = firstDate; !day.isAfter(lastDate); day = day.plusDays(1)) {
            StreakDayAssessment assessment = requireSuccess
                    ? context.classifier().personalSolved(context.snapshot().participantId(), day, game, true)
                    : context.classifier().personalParticipation(context.snapshot().participantId(), day, game, true);
            if (assessment.state() == StreakDayAssessment.State.MET) {
                if (running == 0) {
                    runStart = day;
                }
                running++;
                if (running == threshold) {
                    return Optional.of(new EarnedFact(
                            day,
                            AchievementEvidence.Kind.STREAK,
                            "streak:" + game.name().toLowerCase() + ":"
                                    + (requireSuccess ? "success" : "participation") + ":"
                                    + runStart + ":" + day + ":" + threshold));
                }
            } else {
                running = 0;
                runStart = null;
            }
        }
        return Optional.empty();
    }

    private static Optional<EarnedFact> firstResult(
            List<AchievementHistorySnapshot.Result> results,
            Predicate<AchievementHistorySnapshot.Result> predicate) {
        return results.stream()
                .filter(predicate)
                .findFirst()
                .map(result -> new EarnedFact(
                        result.gameDate(),
                        AchievementEvidence.Kind.GAME_RESULT,
                        "game-result:" + result.resultId()));
    }

    private static Optional<EarnedFact> firstCrossGameDay(Context context, Predicate<DayResults> predicate) {
        return context.days().entrySet().stream()
                .filter(entry -> entry.getValue().complete())
                .filter(entry -> predicate.test(entry.getValue()))
                .findFirst()
                .map(entry -> new EarnedFact(
                        entry.getKey(),
                        AchievementEvidence.Kind.GAME_DAY,
                        "game-day:" + entry.getKey()));
    }

    private static Optional<EarnedFact> consecutiveSameSuccess(
            List<AchievementHistorySnapshot.Result> results, int requiredCount) {
        Integer currentAttempts = null;
        List<AchievementHistorySnapshot.Result> sequence = new ArrayList<>();
        for (AchievementHistorySnapshot.Result result : results) {
            if (!result.solved()) {
                currentAttempts = null;
                sequence.clear();
                continue;
            }
            int attempts = result.attempts().orElseThrow();
            if (currentAttempts == null || currentAttempts != attempts) {
                currentAttempts = attempts;
                sequence.clear();
            }
            sequence.add(result);
            if (sequence.size() == requiredCount) {
                return Optional.of(sequenceFact(sequence, "same-success"));
            }
        }
        return Optional.empty();
    }

    private static Optional<EarnedFact> consecutiveFailures(
            List<AchievementHistorySnapshot.Result> results, int requiredCount) {
        List<AchievementHistorySnapshot.Result> sequence = new ArrayList<>();
        for (AchievementHistorySnapshot.Result result : results) {
            if (result.solved()) {
                sequence.clear();
                continue;
            }
            sequence.add(result);
            if (sequence.size() == requiredCount) {
                return Optional.of(sequenceFact(sequence, "failures"));
            }
        }
        return Optional.empty();
    }

    private static EarnedFact sequenceFact(List<AchievementHistorySnapshot.Result> sequence, String type) {
        AchievementHistorySnapshot.Result last = sequence.getLast();
        String ids = sequence.stream()
                .map(result -> Long.toString(result.resultId()))
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();
        return new EarnedFact(
                last.gameDate(), AchievementEvidence.Kind.RESULT_SEQUENCE, type + ":game-results:" + ids);
    }

    private static Optional<EarnedFact> firstTimedResult(Context context, Predicate<LocalTime> predicate) {
        return context.snapshot().results().stream()
                .filter(result -> predicate.test(result.receivedAt().atZone(context.timeZone()).toLocalTime()))
                .findFirst()
                .map(result -> new EarnedFact(
                        result.gameDate(),
                        AchievementEvidence.Kind.GAME_RESULT,
                        "game-result:" + result.resultId()));
    }

    private static boolean isConsecutiveBoardCompletion(AchievementHistorySnapshot.Result result) {
        Optional<int[]> attempts = boardSolutionAttempts(result);
        if (!result.solved() || attempts.isEmpty()) {
            return false;
        }
        int[] sorted = attempts.orElseThrow();
        Arrays.sort(sorted);
        for (int index = 1; index < sorted.length; index++) {
            if (sorted[index] != sorted[0] + index) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasOutlierBoard(AchievementHistorySnapshot.Result result, int minimumGap) {
        Optional<int[]> attempts = boardSolutionAttempts(result);
        if (!result.solved() || attempts.isEmpty()) {
            return false;
        }
        int[] sorted = attempts.orElseThrow();
        Arrays.sort(sorted);
        return sorted[3] - sorted[2] >= minimumGap;
    }

    private static Optional<int[]> boardSolutionAttempts(AchievementHistorySnapshot.Result result) {
        if (result.game() != GameType.QUADWORDS || result.quadWordsBoards().isEmpty()) {
            return Optional.empty();
        }
        QuadWordsBoards boards = result.quadWordsBoards().orElseThrow();
        int[] attempts = new int[4];
        for (int index = 0; index < boards.ordered().size(); index++) {
            Optional<Integer> solvedAt = solutionAttempt(boards.ordered().get(index));
            if (solvedAt.isEmpty()) {
                return Optional.empty();
            }
            attempts[index] = solvedAt.orElseThrow();
        }
        return Optional.of(attempts);
    }

    private static Optional<Integer> solutionAttempt(QuadWordsBoard board) {
        for (int index = 0; index < board.rows().size(); index++) {
            if (SOLVED_BOARD_ROW.equals(board.rows().get(index))) {
                return Optional.of(index + 1);
            }
        }
        return Optional.empty();
    }

    private static boolean hasRepeatedGridWordsPattern(AchievementHistorySnapshot.Result result, int requiredCount) {
        if (result.game() != GameType.GRIDWORDS || result.gridWordsBoard().isEmpty()) {
            return false;
        }
        String previous = null;
        int runLength = 0;
        for (String row : result.gridWordsBoard().orElseThrow().rows()) {
            if (row.equals(previous)) {
                runLength++;
            } else {
                previous = row;
                runLength = 1;
            }
            if (runLength >= requiredCount) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAllYellowBoardRow(AchievementHistorySnapshot.Result result) {
        if (result.game() == GameType.GRIDWORDS) {
            return result.gridWordsBoard().map(AchievementEvaluator::hasAllYellowRow).orElse(false);
        }
        return result.quadWordsBoards().stream()
                .flatMap(boards -> boards.ordered().stream())
                .anyMatch(AchievementEvaluator::hasAllYellowRow);
    }

    private static boolean hasAllYellowRow(NormalizedBoard board) {
        return board.rows().contains("\uD83D\uDFE8".repeat(5));
    }

    private static boolean hasAllYellowRow(QuadWordsBoard board) {
        return board.rows().contains("\uD83D\uDFE8".repeat(5));
    }

    private record EarnedFact(LocalDate earnedOn, AchievementEvidence.Kind kind, String reference) {
        private EarnedFact {
            Objects.requireNonNull(earnedOn, "earnedOn");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(reference, "reference");
        }
    }

    private record DayResults(
            AchievementHistorySnapshot.Result gridWords,
            AchievementHistorySnapshot.Result quadWords) {
        boolean complete() {
            return gridWords != null && quadWords != null;
        }

        boolean bothSolved() {
            return complete() && gridWords.solved() && quadWords.solved();
        }
    }

    private static final class Context {
        private final AchievementHistorySnapshot snapshot;
        private final ZoneId timeZone;
        private final Map<GameType, List<AchievementHistorySnapshot.Result>> resultsByGame;
        private final Map<LocalDate, DayResults> days;
        private final StreakDayClassifier classifier;

        private Context(AchievementHistorySnapshot snapshot, ZoneId timeZone) {
            this.snapshot = snapshot;
            this.timeZone = timeZone;
            this.resultsByGame = new EnumMap<>(GameType.class);
            for (GameType game : GameType.values()) {
                resultsByGame.put(game, snapshot.resultsFor(game));
            }
            Map<LocalDate, AchievementHistorySnapshot.Result> gridWords = new LinkedHashMap<>();
            Map<LocalDate, AchievementHistorySnapshot.Result> quadWords = new LinkedHashMap<>();
            for (AchievementHistorySnapshot.Result result : snapshot.results()) {
                (result.game() == GameType.GRIDWORDS ? gridWords : quadWords).put(result.gameDate(), result);
            }
            Map<LocalDate, DayResults> indexedDays = new LinkedHashMap<>();
            snapshot.results().stream().map(AchievementHistorySnapshot.Result::gameDate).distinct().sorted()
                    .forEach(day -> indexedDays.put(day, new DayResults(gridWords.get(day), quadWords.get(day))));
            this.days = Collections.unmodifiableMap(new LinkedHashMap<>(indexedDays));
            this.classifier = new StreakDayClassifier(snapshot.streakResults(), snapshot.participationPeriods());
        }

        AchievementHistorySnapshot snapshot() {
            return snapshot;
        }

        ZoneId timeZone() {
            return timeZone;
        }

        List<AchievementHistorySnapshot.Result> resultsFor(GameType game) {
            return resultsByGame.get(game);
        }

        Map<LocalDate, DayResults> days() {
            return days;
        }

        StreakDayClassifier classifier() {
            return classifier;
        }
    }
}
