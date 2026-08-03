package de.venomenon.gridwordsbot.domain.excuse;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.Duration;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure policy for the explicitly defined MVP offer reasons. */
public final class ExcuseEligibilityPolicy {

    private final ExcuseEligibilityThresholds thresholds;

    public ExcuseEligibilityPolicy(ExcuseEligibilityThresholds thresholds) {
        this.thresholds = java.util.Objects.requireNonNull(thresholds, "thresholds");
    }

    public ExcuseEligibility evaluate(ExcuseEligibilityRequest request) {
        return evaluate(request, null);
    }

    /** Reuses the historical comparison snapshot when a correction is revalidated. */
    public ExcuseEligibility evaluate(
            ExcuseEligibilityRequest request, DailyComparisonSnapshot frozenComparison) {
        java.util.Objects.requireNonNull(request, "request");
        ParsedGameResult result = request.result();
        QuadWordsBoardAnalysis boardAnalysis = result.quadWordsBoards()
                .map(boards -> QuadWordsBoardAnalysis.analyze(
                        boards, thresholds.worstSolvedBoardMinimumAttempt(), thresholds.worstBoardMinimumGap()))
                .orElseGet(QuadWordsBoardAnalysis::boardless);
        if (frozenComparison != null && frozenComparison.gameType() != result.gameType()) {
            throw new IllegalArgumentException("frozen comparison must match the result game type");
        }
        DailyComparisonSnapshot comparison = frozenComparison == null
                ? DailyComparisonSnapshot.from(result.gameType(), eligiblePriorResults(request))
                : frozenComparison;
        EnumSet<ExcuseReason> reasons = EnumSet.noneOf(ExcuseReason.class);
        if (isRelevantParticipant(request) && !request.exclusivePositivePriorityEvent()) {
            evaluateAbsoluteReasons(result, request, boardAnalysis, reasons);
            if (isDailyOutlier(result, comparison)) {
                reasons.add(ExcuseReason.CLEAR_CURRENT_DAILY_OUTLIER);
            }
        }
        Set<ExcuseCondition> conditions = new LinkedHashSet<>();
        conditions.addAll(reasons);
        conditions.addAll(boardAnalysis.facts());
        return new ExcuseEligibility(
                !reasons.isEmpty(),
                reasons,
                new ExcuseContext(result.gameType(), conditions, placeholders(result, boardAnalysis)),
                comparison,
                boardAnalysis);
    }

    private void evaluateAbsoluteReasons(
            ParsedGameResult result,
            ExcuseEligibilityRequest request,
            QuadWordsBoardAnalysis boardAnalysis,
            Set<ExcuseReason> reasons) {
        if (result.outcome() instanceof ShareOutcome.Unsolved) {
            reasons.add(ExcuseReason.NOT_SOLVED);
        }
        if (!request.receivedAt().atZone(thresholds.businessZone()).toLocalTime().isBefore(thresholds.veryLateSubmissionAt())) {
            reasons.add(ExcuseReason.VERY_LATE_SUBMISSION);
        }
        if (result.gameType() == GameType.GRIDWORDS) {
            if (result.outcome() instanceof ShareOutcome.Solved solved && solved.attemptsUsed() == 6) {
                reasons.add(ExcuseReason.GRIDWORDS_LAST_ATTEMPT);
            }
            if (result.duration().compareTo(thresholds.gridWordsVerySlow()) >= 0) {
                reasons.add(ExcuseReason.GRIDWORDS_VERY_SLOW);
            }
            return;
        }
        if (result.duration().compareTo(thresholds.quadWordsVerySlow()) >= 0) {
            reasons.add(ExcuseReason.QUADWORDS_VERY_SLOW);
        }
        if (boardAnalysis.singleBoardCollapse()) {
            reasons.add(ExcuseReason.QUADWORDS_SINGLE_BOARD_COLLAPSE);
        }
    }

    private boolean isDailyOutlier(ParsedGameResult current, DailyComparisonSnapshot comparison) {
        if (!comparison.hasAtLeastTwoResults()) {
            return false;
        }
        if (current.outcome() instanceof ShareOutcome.Unsolved && comparison.allComparedResultsSolved()) {
            return true;
        }
        if (current.gameType() == GameType.GRIDWORDS && current.outcome() instanceof ShareOutcome.Solved solved
                && comparison.allComparedResultsSolved() && comparison.highestSolvedAttempts().isPresent()
                && solved.attemptsUsed() >= thresholds.gridWordsOutlierMinimumAttempts()
                && solved.attemptsUsed() - comparison.highestSolvedAttempts().getAsInt()
                >= thresholds.gridWordsOutlierMinimumAttemptGap()) {
            return true;
        }
        Duration minimumDuration = current.gameType() == GameType.GRIDWORDS
                ? thresholds.gridWordsOutlierMinimumDuration() : thresholds.quadWordsOutlierMinimumDuration();
        Duration minimumGap = current.gameType() == GameType.GRIDWORDS
                ? thresholds.gridWordsOutlierMinimumDurationGap() : thresholds.quadWordsOutlierMinimumDurationGap();
        return current.duration().compareTo(minimumDuration) >= 0
                && current.duration().minus(comparison.longestDuration()).compareTo(minimumGap) >= 0;
    }

    private List<ExcuseDailyResult> eligiblePriorResults(ExcuseEligibilityRequest request) {
        return request.priorResults().stream()
                .filter(candidate -> candidate.playerId() != request.playerId())
                .filter(candidate -> request.participation().playersFor(request.result().gameType()).contains(candidate.playerId()))
                .toList();
    }

    private boolean isRelevantParticipant(ExcuseEligibilityRequest request) {
        return request.participation().playersFor(request.result().gameType()).contains(request.playerId());
    }

    private static Map<ExcusePlaceholder, String> placeholders(
            ParsedGameResult result, QuadWordsBoardAnalysis boardAnalysis) {
        java.util.EnumMap<ExcusePlaceholder, String> values = new java.util.EnumMap<>(ExcusePlaceholder.class);
        values.put(ExcusePlaceholder.SCORE, score(result.outcome()));
        values.put(ExcusePlaceholder.DURATION, duration(result.duration()));
        boardAnalysis.uniqueWorstBoard().ifPresent(
                position -> values.put(ExcusePlaceholder.WORST_BOARD, position.displayName()));
        return values;
    }

    private static String score(ShareOutcome outcome) {
        return outcome instanceof ShareOutcome.Solved solved
                ? solved.attemptsUsed() + "/" + solved.maxAttempts()
                : "X/" + outcome.maxAttempts();
    }

    private static String duration(Duration duration) {
        long seconds = duration.toSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainingSeconds = seconds % 60;
        if (hours > 0) {
            return "%d:%02d:%02d".formatted(hours, minutes, remainingSeconds);
        }
        return "%02d:%02d".formatted(minutes, remainingSeconds);
    }
}
