package de.venomenon.gridwordsbot.domain.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class AchievementHistorySnapshotTest {

    @Test
    void normalizesResultsAndPeriodsIntoDeterministicOrder() {
        LocalDate first = LocalDate.of(2026, 1, 1);
        AchievementHistorySnapshot.Result grid = solved(2, GameType.GRIDWORDS, first, 3);
        AchievementHistorySnapshot.Result quad = solved(3, GameType.QUADWORDS, first, 5);
        AchievementHistorySnapshot.Result later = failed(1, GameType.GRIDWORDS, first.plusDays(1));
        GameParticipationPeriod gridPeriod = new GameParticipationPeriod(7, GameType.GRIDWORDS, first, null);
        GameParticipationPeriod quadPeriod = new GameParticipationPeriod(7, GameType.QUADWORDS, first, null);

        AchievementHistorySnapshot snapshot = new AchievementHistorySnapshot(
                7, List.of(later, quad, grid), List.of(quadPeriod, gridPeriod));

        assertThat(snapshot.results()).containsExactly(grid, quad, later);
        assertThat(snapshot.participationPeriods()).containsExactly(gridPeriod, quadPeriod);
        assertThat(snapshot.resultsFor(GameType.GRIDWORDS)).containsExactly(grid, later);
        assertThat(snapshot.streakResults()).hasSize(3);
    }

    @Test
    void rejectsDuplicateResultIdentityAndForeignParticipation() {
        LocalDate day = LocalDate.of(2026, 1, 1);
        AchievementHistorySnapshot.Result first = solved(1, GameType.GRIDWORDS, day, 2);
        AchievementHistorySnapshot.Result duplicate = solved(2, GameType.GRIDWORDS, day, 3);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AchievementHistorySnapshot(7, List.of(first, duplicate), List.of()))
                .withMessageContaining("duplicate game result");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AchievementHistorySnapshot(
                        7,
                        List.of(first),
                        List.of(new GameParticipationPeriod(8, GameType.GRIDWORDS, day, null))))
                .withMessageContaining("another participant");
    }

    @Test
    void enforcesCanonicalSolvedAttemptsAndQuadOnlyBoards() {
        LocalDate day = LocalDate.of(2026, 1, 1);

        assertThatIllegalArgumentException().isThrownBy(() -> new AchievementHistorySnapshot.Result(
                1, GameType.GRIDWORDS, day, true, OptionalInt.empty(), Instant.EPOCH, Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new AchievementHistorySnapshot.Result(
                1, GameType.QUADWORDS, day, true, OptionalInt.of(3), Instant.EPOCH, Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new AchievementHistorySnapshot.Result(
                1, GameType.GRIDWORDS, day, true, OptionalInt.of(2), Instant.EPOCH, Optional.of(boards())));
    }

    private static AchievementHistorySnapshot.Result solved(long id, GameType game, LocalDate day, int attempts) {
        return new AchievementHistorySnapshot.Result(
                id, game, day, true, OptionalInt.of(attempts), day.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(), Optional.empty());
    }

    private static AchievementHistorySnapshot.Result failed(long id, GameType game, LocalDate day) {
        return new AchievementHistorySnapshot.Result(
                id, game, day, false, OptionalInt.empty(), day.atStartOfDay(java.time.ZoneOffset.UTC).toInstant(), Optional.empty());
    }

    private static QuadWordsBoards boards() {
        QuadWordsBoard board = new QuadWordsBoard(List.of("🟩🟩🟩🟩🟩"));
        return new QuadWordsBoards(board, board, board, board);
    }
}
