package de.venomenon.gridwordsbot.domain.streak;

import static org.assertj.core.api.Assertions.assertThat;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class StreakCalculatorTest {
    private static final long TOBIAS = 1L;
    private static final long GEORGIA = 2L;
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 29);
    private final StreakCalculator calculator = new StreakCalculator();

    @Test
    void calculatesAllSevenIndependentStreaksAndKeepsAnIncompleteTodayOpen() {
        List<StreakCalculator.PlayerResult> results = List.of(
                result(TOBIAS, TODAY.minusDays(1), GameType.GRIDWORDS, true), result(TOBIAS, TODAY.minusDays(1), GameType.QUADWORDS, true),
                result(GEORGIA, TODAY.minusDays(1), GameType.GRIDWORDS, true), result(GEORGIA, TODAY.minusDays(1), GameType.QUADWORDS, true),
                result(TOBIAS, TODAY, GameType.GRIDWORDS, true));
        StreakSummary streaks = calculator.calculate(results, List.of(TOBIAS, GEORGIA), TOBIAS, TODAY);
        assertThat(streaks.personalActivity()).isEqualTo(2);
        assertThat(streaks.personalComplete()).isEqualTo(1);
        assertThat(streaks.personalGridWordsSolved()).isEqualTo(2);
        assertThat(streaks.personalQuadWordsSolved()).isEqualTo(1);
        assertThat(streaks.personalPerfect()).isEqualTo(1);
        assertThat(streaks.sharedComplete()).isEqualTo(1);
        assertThat(streaks.sharedPerfect()).isEqualTo(1);
    }

    @Test
    void unsolvedTodayEndsRelevantSolvedAndPerfectStreakImmediately() {
        List<StreakCalculator.PlayerResult> results = List.of(
                result(TOBIAS, TODAY.minusDays(1), GameType.GRIDWORDS, true), result(TOBIAS, TODAY.minusDays(1), GameType.QUADWORDS, true),
                result(GEORGIA, TODAY.minusDays(1), GameType.GRIDWORDS, true), result(GEORGIA, TODAY.minusDays(1), GameType.QUADWORDS, true),
                result(TOBIAS, TODAY, GameType.GRIDWORDS, false));
        StreakSummary streaks = calculator.calculate(results, List.of(TOBIAS, GEORGIA), TOBIAS, TODAY);
        assertThat(streaks.personalActivity()).isEqualTo(2);
        assertThat(streaks.personalGridWordsSolved()).isZero();
        assertThat(streaks.personalPerfect()).isZero();
    }

    private static StreakCalculator.PlayerResult result(long player, LocalDate date, GameType type, boolean solved) {
        ShareOutcome outcome = solved ? new ShareOutcome.Solved(1, type == GameType.GRIDWORDS ? 6 : 9) : new ShareOutcome.Unsolved(type == GameType.GRIDWORDS ? 6 : 9);
        Optional<NormalizedBoard> board = type == GameType.GRIDWORDS ? Optional.of(new NormalizedBoard(java.util.Collections.nCopies(solved ? 1 : 6, "\u2b1c\u2b1c\u2b1c\u2b1c\u2b1c"))) : Optional.empty();
        return new StreakCalculator.PlayerResult(player, new ParsedGameResult(type, date, outcome, Duration.ofSeconds(1), OptionalInt.empty(), board));
    }
}
