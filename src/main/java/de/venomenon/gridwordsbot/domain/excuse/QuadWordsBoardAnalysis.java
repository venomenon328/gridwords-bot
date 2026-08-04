package de.venomenon.gridwordsbot.domain.excuse;

import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Derived, typed QuadWords board facts. No board fact is emitted for a boardless submission. */
public record QuadWordsBoardAnalysis(
        boolean boardsPresent,
        List<OptionalInt> solutionAttempts,
        Optional<QuadWordsBoardPosition> uniqueWorstBoard,
        boolean significantWorstBoardGap,
        boolean singleBoardCollapse,
        Set<ExcuseFact> facts) {

    private static final String SOLVED_ROW = "\uD83D\uDFE9".repeat(5);

    public QuadWordsBoardAnalysis {
        solutionAttempts = List.copyOf(Objects.requireNonNull(solutionAttempts, "solutionAttempts"));
        Objects.requireNonNull(uniqueWorstBoard, "uniqueWorstBoard");
        facts = Set.copyOf(Objects.requireNonNull(facts, "facts"));
        if (!boardsPresent && (!solutionAttempts.isEmpty() || uniqueWorstBoard.isPresent()
                || significantWorstBoardGap || singleBoardCollapse || !facts.equals(Set.of(ExcuseFact.BOARDLESS_SUBMISSION)))) {
            throw new IllegalArgumentException("boardless analyses must not expose board facts");
        }
        if (boardsPresent && solutionAttempts.size() != 4) {
            throw new IllegalArgumentException("a board analysis requires four solution entries");
        }
    }

    public static QuadWordsBoardAnalysis boardless() {
        return new QuadWordsBoardAnalysis(false, List.of(), Optional.empty(), false, false,
                Set.of(ExcuseFact.BOARDLESS_SUBMISSION));
    }

    public static QuadWordsBoardAnalysis analyze(QuadWordsBoards boards, int minimumWorstAttempt, int minimumGap) {
        Objects.requireNonNull(boards, "boards");
        if (minimumWorstAttempt < 1 || minimumGap < 1) {
            throw new IllegalArgumentException("board thresholds must be positive");
        }
        List<OptionalInt> attempts = boards.ordered().stream().map(QuadWordsBoardAnalysis::solutionAttempt).toList();
        int solvedCount = (int) attempts.stream().filter(OptionalInt::isPresent).count();
        EnumSet<ExcuseFact> facts = EnumSet.of(ExcuseFact.FOUR_BOARDS_PRESENT);
        if (solvedCount == 4) {
            facts.add(ExcuseFact.ALL_BOARDS_SOLVED);
        }
        if (solvedCount == 3) {
            facts.add(ExcuseFact.THREE_BOARDS_SOLVED_ONE_UNSOLVED);
        }

        Optional<QuadWordsBoardPosition> uniqueWorst = uniqueWorst(attempts, solvedCount);
        uniqueWorst.ifPresent(position -> {
            facts.add(ExcuseFact.UNIQUE_WORST_BOARD);
            facts.add(position.worstPositionFact());
        });
        boolean significantGap = false;
        if (solvedCount == 4 && uniqueWorst.isPresent()) {
            int worstAttempt = attempts.get(uniqueWorst.orElseThrow().ordinal()).getAsInt();
            int secondWorstAttempt = attempts.stream().mapToInt(OptionalInt::getAsInt).sorted().skip(2).findFirst().orElseThrow();
            significantGap = worstAttempt >= minimumWorstAttempt && worstAttempt - secondWorstAttempt >= minimumGap;
            if (significantGap) {
                facts.add(ExcuseFact.SIGNIFICANT_WORST_BOARD_GAP);
            }
        }
        boolean collapse = solvedCount == 3 || (solvedCount == 4 && significantGap);
        return new QuadWordsBoardAnalysis(true, attempts, uniqueWorst, significantGap, collapse, facts);
    }

    private static OptionalInt solutionAttempt(QuadWordsBoard board) {
        for (int index = 0; index < board.rows().size(); index++) {
            if (SOLVED_ROW.equals(board.rows().get(index))) {
                return OptionalInt.of(index + 1);
            }
        }
        return OptionalInt.empty();
    }

    private static Optional<QuadWordsBoardPosition> uniqueWorst(List<OptionalInt> attempts, int solvedCount) {
        if (solvedCount == 3) {
            for (int index = 0; index < attempts.size(); index++) {
                if (attempts.get(index).isEmpty()) {
                    return Optional.of(QuadWordsBoardPosition.values()[index]);
                }
            }
        }
        if (solvedCount != 4) {
            return Optional.empty();
        }
        int maximum = attempts.stream().mapToInt(OptionalInt::getAsInt).max().orElseThrow();
        int occurrences = (int) attempts.stream().filter(value -> value.getAsInt() == maximum).count();
        if (occurrences != 1) {
            return Optional.empty();
        }
        for (int index = 0; index < attempts.size(); index++) {
            if (attempts.get(index).getAsInt() == maximum) {
                return Optional.of(QuadWordsBoardPosition.values()[index]);
            }
        }
        throw new IllegalStateException("unique worst board was not found");
    }
}
