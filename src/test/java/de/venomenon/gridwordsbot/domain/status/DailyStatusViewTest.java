package de.venomenon.gridwordsbot.domain.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DailyStatusViewTest {
    @Test
    void createsDistinctDeterministicallySortedMenusForEachGame() {
        DailyStatusView view = DailyStatusView.versionOne(status(
                line(3, "Zulu", true, false),
                line(2, "alpha", true, true),
                line(1, "Alpha", false, true)));

        assertThat(view.resultMenuPages()).hasSize(2);
        assertThat(view.resultMenuPages().get(0).gameType()).isEqualTo(GameType.GRIDWORDS);
        assertThat(view.resultMenuPages().get(0).options()).extracting(DailyStatusView.PlayerOption::discordUserId)
                .containsExactly(2L, 3L);
        assertThat(view.resultMenuPages().get(1).gameType()).isEqualTo(GameType.QUADWORDS);
        assertThat(view.resultMenuPages().get(1).options()).extracting(DailyStatusView.PlayerOption::discordUserId)
                .containsExactly(1L, 2L);
    }

    @Test
    void omitsTheSelectorForAGameWithoutParticipants() {
        DailyStatusView view = DailyStatusView.versionOne(status(line(1, "Grid only", true, false)));

        assertThat(view.resultMenuPages()).singleElement().satisfies(page -> {
            assertThat(page.gameType()).isEqualTo(GameType.GRIDWORDS);
            assertThat(page.options()).singleElement().extracting(DailyStatusView.PlayerOption::discordUserId)
                    .isEqualTo(1L);
        });
    }

    @Test
    void paginatesEachGameIndependentlyWithoutDroppingParticipants() {
        List<DailyStatus.PlayerLine> players = IntStream.rangeClosed(1, 51)
                .mapToObj(id -> line(id, String.format("Player %03d", id), true, false))
                .toList();

        DailyStatusView view = DailyStatusView.versionOne(status(players.toArray(DailyStatus.PlayerLine[]::new)));

        assertThat(view.resultMenuPages()).hasSize(3);
        assertThat(view.resultMenuPages()).allSatisfy(page -> assertThat(page.gameType()).isEqualTo(GameType.GRIDWORDS));
        assertThat(view.resultMenuPages()).extracting(DailyStatusView.DailyResultMenuPage::options)
                .extracting(List::size).containsExactly(25, 25, 1);
        assertThat(view.resultMenuPages().stream()
                .flatMap(page -> page.options().stream())
                .map(DailyStatusView.PlayerOption::discordUserId)).containsExactlyElementsOf(
                        IntStream.rangeClosed(1, 51).mapToLong(id -> id).boxed().toList());
    }

    @Test
    void rejectsAnEmptyPageRatherThanCreatingAnInvalidDiscordMenu() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DailyStatusView.DailyResultMenuPage(
                GameType.GRIDWORDS, 0, 1, List.of()));
    }

    private static DailyStatus status(DailyStatus.PlayerLine... lines) {
        return new DailyStatus(LocalDate.of(2026, 8, 3), List.of(lines), 0, 0);
    }

    private static DailyStatus.PlayerLine line(long id, String name, boolean grid, boolean quad) {
        return new DailyStatus.PlayerLine(
                id,
                name,
                new DailyStatus.GameState(GameType.GRIDWORDS, grid, Optional.empty()),
                new DailyStatus.GameState(GameType.QUADWORDS, quad, Optional.empty()),
                new StreakSummary(0, 0, 0, 0, 0, 0, 0));
    }
}