package de.venomenon.gridwordsbot.domain.excuse;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.DailyGameParticipation;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExcuseEligibilityPolicyTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 3);
    private final ExcuseEligibilityPolicy policy = new ExcuseEligibilityPolicy(ExcuseEligibilityThresholds.defaults());

    @Test
    void appliesGeneralGridWordsReasonsAtInclusiveThresholds() {
        ExcuseEligibility eligibility = policy.evaluate(request(
                gridResult(new ShareOutcome.Solved(6, 6), Duration.ofMinutes(5)),
                Instant.parse("2026-08-03T21:30:00Z"), List.of(), Set.of(1L)));

        assertThat(eligibility.reasons()).containsExactlyInAnyOrder(
                ExcuseReason.GRIDWORDS_LAST_ATTEMPT,
                ExcuseReason.GRIDWORDS_VERY_SLOW,
                ExcuseReason.VERY_LATE_SUBMISSION);
        assertThat(eligibility.context().placeholders()).containsEntry(ExcusePlaceholder.SCORE, "6/6")
                .containsEntry(ExcusePlaceholder.DURATION, "05:00");
    }

    @Test
    void excludesOneSecondBeforeVeryLateAndOneSecondBelowGridWordsSlowThreshold() {
        ExcuseEligibility eligibility = policy.evaluate(request(
                gridResult(new ShareOutcome.Solved(5, 6), Duration.ofMinutes(5).minusSeconds(1)),
                Instant.parse("2026-08-03T21:29:59Z"), List.of(), Set.of(1L)));

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.reasons()).isEmpty();
    }

    @Test
    void evaluatesBerlinDateBoundaryAndDstFromTheSubmissionInstant() {
        ExcuseEligibility beforeMidnight = policy.evaluate(request(
                gridResult(new ShareOutcome.Solved(5, 6), Duration.ZERO),
                Instant.parse("2026-10-25T22:29:59Z"), List.of(), Set.of(1L)));
        ExcuseEligibility atLateTime = policy.evaluate(request(
                gridResult(new ShareOutcome.Solved(5, 6), Duration.ZERO),
                Instant.parse("2026-10-25T22:30:00Z"), List.of(), Set.of(1L)));

        assertThat(beforeMidnight.reasons()).doesNotContain(ExcuseReason.VERY_LATE_SUBMISSION);
        assertThat(atLateTime.reasons()).contains(ExcuseReason.VERY_LATE_SUBMISSION);
    }

    @Test
    void detectsBoardlessQuadWordsSlowButNeverInventsBoardFacts() {
        ExcuseEligibility eligibility = policy.evaluate(request(
                quadResult(new ShareOutcome.Solved(5, 9), Duration.ofMinutes(8), Optional.empty()),
                Instant.parse("2026-08-03T11:00:00Z"), List.of(), Set.of(1L)));

        assertThat(eligibility.reasons()).containsExactly(ExcuseReason.QUADWORDS_VERY_SLOW);
        assertThat(eligibility.context().conditions()).contains(ExcuseFact.BOARDLESS_SUBMISSION)
                .doesNotContain(ExcuseFact.FOUR_BOARDS_PRESENT, ExcuseReason.QUADWORDS_SINGLE_BOARD_COLLAPSE);
        assertThat(eligibility.context().placeholder(ExcusePlaceholder.WORST_BOARD)).isEmpty();
        assertThat(policy.evaluate(request(
                quadResult(new ShareOutcome.Solved(5, 9), Duration.ofMinutes(8).minusSeconds(1), Optional.empty()),
                Instant.parse("2026-08-03T11:00:00Z"), List.of(), Set.of(1L))).reasons())
                .doesNotContain(ExcuseReason.QUADWORDS_VERY_SLOW);
    }

    @Test
    void detectsQuadWordsSingleBoardCollapseAtInclusiveBoundary() {
        QuadWordsBoards boards = new QuadWordsBoards(board(8, 2), board(8, 4), board(8, 5), board(8, 8));
        ExcuseEligibility eligible = policy.evaluate(request(
                quadResult(new ShareOutcome.Solved(8, 9), Duration.ZERO, Optional.of(boards)),
                Instant.parse("2026-08-03T11:00:00Z"), List.of(), Set.of(1L)));
        QuadWordsBoards tooSmallGap = new QuadWordsBoards(board(8, 3), board(8, 5), board(8, 6), board(8, 8));
        ExcuseEligibility ineligible = policy.evaluate(request(
                quadResult(new ShareOutcome.Solved(8, 9), Duration.ZERO, Optional.of(tooSmallGap)),
                Instant.parse("2026-08-03T11:00:00Z"), List.of(), Set.of(1L)));

        assertThat(eligible.reasons()).containsExactly(ExcuseReason.QUADWORDS_SINGLE_BOARD_COLLAPSE);
        assertThat(eligible.context().placeholder(ExcusePlaceholder.WORST_BOARD)).contains("unten rechts");
        assertThat(ineligible.eligible()).isFalse();
    }

    @Test
    void detectsUnsolvedAndClearDailyOutlierOnlyAgainstParticipantsOfThatGame() {
        List<ExcuseDailyResult> prior = List.of(
                daily(2, GameType.GRIDWORDS, new ShareOutcome.Solved(3, 6), Duration.ofMinutes(2)),
                daily(3, GameType.GRIDWORDS, new ShareOutcome.Solved(4, 6), Duration.ofMinutes(3)),
                daily(4, GameType.GRIDWORDS, new ShareOutcome.Unsolved(6), Duration.ofMinutes(20)));
        ExcuseEligibility eligibility = policy.evaluate(request(
                gridResult(new ShareOutcome.Unsolved(6), Duration.ofMinutes(1)),
                Instant.parse("2026-08-03T11:00:00Z"), prior, Set.of(1L, 2L, 3L)));

        assertThat(eligibility.reasons()).contains(ExcuseReason.NOT_SOLVED, ExcuseReason.CLEAR_CURRENT_DAILY_OUTLIER);
        assertThat(eligibility.comparisonSnapshot().comparedResultCount()).isEqualTo(2);
        assertThat(eligibility.comparisonSnapshot().allComparedResultsSolved()).isTrue();
    }

    @Test
    void requiresTwoOtherResultsAndAppliesGridWordsAndQuadWordsOutlierGapsInclusively() {
        List<ExcuseDailyResult> oneOther = List.of(daily(2, GameType.GRIDWORDS,
                new ShareOutcome.Solved(3, 6), Duration.ofMinutes(2)));
        assertThat(policy.evaluate(request(gridResult(new ShareOutcome.Solved(5, 6), Duration.ofMinutes(4)),
                Instant.parse("2026-08-03T11:00:00Z"), oneOther, Set.of(1L, 2L))).reasons())
                .doesNotContain(ExcuseReason.CLEAR_CURRENT_DAILY_OUTLIER);

        List<ExcuseDailyResult> gridPeers = List.of(
                daily(2, GameType.GRIDWORDS, new ShareOutcome.Solved(3, 6), Duration.ofMinutes(2)),
                daily(3, GameType.GRIDWORDS, new ShareOutcome.Solved(3, 6), Duration.ofMinutes(2)));
        assertThat(policy.evaluate(request(gridResult(new ShareOutcome.Solved(5, 6), Duration.ofMinutes(3)),
                Instant.parse("2026-08-03T11:00:00Z"), gridPeers, Set.of(1L, 2L, 3L))).reasons())
                .contains(ExcuseReason.CLEAR_CURRENT_DAILY_OUTLIER);
        assertThat(policy.evaluate(request(gridResult(new ShareOutcome.Solved(4, 6), Duration.ofMinutes(3)),
                Instant.parse("2026-08-03T11:00:00Z"), gridPeers, Set.of(1L, 2L, 3L))).reasons())
                .doesNotContain(ExcuseReason.CLEAR_CURRENT_DAILY_OUTLIER);
        assertThat(policy.evaluate(request(gridResult(new ShareOutcome.Solved(3, 6), Duration.ofMinutes(3).plusSeconds(59)),
                Instant.parse("2026-08-03T11:00:00Z"), gridPeers, Set.of(1L, 2L, 3L))).reasons())
                .doesNotContain(ExcuseReason.CLEAR_CURRENT_DAILY_OUTLIER);

        List<ExcuseDailyResult> quadPeers = List.of(
                daily(2, GameType.QUADWORDS, new ShareOutcome.Solved(3, 9), Duration.ofMinutes(3)),
                daily(3, GameType.QUADWORDS, new ShareOutcome.Solved(4, 9), Duration.ofMinutes(3)));
        assertThat(policy.evaluate(request(quadResult(new ShareOutcome.Solved(5, 9), Duration.ofMinutes(6), Optional.empty()),
                Instant.parse("2026-08-03T11:00:00Z"), quadPeers, Set.of(1L, 2L, 3L))).reasons())
                .contains(ExcuseReason.CLEAR_CURRENT_DAILY_OUTLIER);
        assertThat(policy.evaluate(request(quadResult(new ShareOutcome.Solved(5, 9), Duration.ofMinutes(5).plusSeconds(59), Optional.empty()),
                Instant.parse("2026-08-03T11:00:00Z"), quadPeers, Set.of(1L, 2L, 3L))).reasons())
                .doesNotContain(ExcuseReason.CLEAR_CURRENT_DAILY_OUTLIER);
    }

    @Test
    void positivePriorityOrMissingRelevantParticipationSuppressesAllReasons() {
        ExcuseEligibility priority = policy.evaluate(request(
                gridResult(new ShareOutcome.Unsolved(6), Duration.ofMinutes(5)),
                Instant.parse("2026-08-03T21:30:00Z"), List.of(), Set.of(1L), true));
        ExcuseEligibility inactive = policy.evaluate(request(
                gridResult(new ShareOutcome.Unsolved(6), Duration.ofMinutes(5)),
                Instant.parse("2026-08-03T21:30:00Z"), List.of(), Set.of()));

        assertThat(priority.eligible()).isFalse();
        assertThat(inactive.eligible()).isFalse();
    }

    private static ExcuseEligibilityRequest request(
            ParsedGameResult result, Instant receivedAt, List<ExcuseDailyResult> priorResults, Set<Long> gameParticipants) {
        return request(result, receivedAt, priorResults, gameParticipants, false);
    }

    private static ExcuseEligibilityRequest request(
            ParsedGameResult result, Instant receivedAt, List<ExcuseDailyResult> priorResults,
            Set<Long> gameParticipants, boolean positivePriority) {
        Set<Long> grid = result.gameType() == GameType.GRIDWORDS ? gameParticipants : Set.of();
        Set<Long> quad = result.gameType() == GameType.QUADWORDS ? gameParticipants : Set.of();
        return new ExcuseEligibilityRequest(1, result, receivedAt,
                new DailyGameParticipation(DATE, grid, quad, gameParticipants, Set.of()), priorResults, positivePriority);
    }

    private static ExcuseDailyResult daily(long playerId, GameType gameType, ShareOutcome outcome, Duration duration) {
        return new ExcuseDailyResult(playerId, gameType, DATE, outcome, duration);
    }

    private static ParsedGameResult gridResult(ShareOutcome outcome, Duration duration) {
        int rows = outcome instanceof ShareOutcome.Solved solved ? solved.attemptsUsed() : 6;
        return new ParsedGameResult(GameType.GRIDWORDS, DATE, outcome, duration, OptionalInt.empty(),
                Optional.of(new NormalizedBoard(java.util.stream.IntStream.range(0, rows).mapToObj(ignored -> white()).toList())));
    }

    private static ParsedGameResult quadResult(ShareOutcome outcome, Duration duration, Optional<QuadWordsBoards> boards) {
        return new ParsedGameResult(GameType.QUADWORDS, DATE, outcome, duration, OptionalInt.empty(), Optional.empty(), boards);
    }

    private static QuadWordsBoard board(int rows, int solvedAttempt) {
        return new QuadWordsBoard(java.util.stream.IntStream.rangeClosed(1, rows)
                .mapToObj(attempt -> attempt == solvedAttempt ? green() : white()).toList());
    }

    private static String green() {
        return "🟩".repeat(5);
    }

    private static String white() {
        return "⬜".repeat(5);
    }
}
