package de.venomenon.gridwordsbot.domain.status;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DailyStatusViewTest {
    @Test
    void createsExactlyTwoMenusForUpToTwentyFivePlayersAndSortsDeterministically() {
        DailyStatusView view = DailyStatusView.versionOne(status(25));

        assertThat(view.resultMenuPages()).hasSize(2);
        assertThat(view.resultMenuPages().get(0).options()).extracting(DailyStatusView.PlayerOption::discordUserId)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 25).mapToLong(i -> i).boxed().toList());
        assertThat(view.resultMenuPages().get(1).options()).isEqualTo(view.resultMenuPages().get(0).options());
    }

    @Test
    void paginatesTwentySixAndFiftyPlayersForBothGames() {
        DailyStatusView twentySix = DailyStatusView.versionOne(status(26));
        DailyStatusView fifty = DailyStatusView.versionOne(status(50));

        assertThat(twentySix.resultMenuPages()).hasSize(4);
        assertThat(twentySix.resultMenuPages()).extracting(DailyStatusView.DailyResultMenuPage::options)
                .extracting(List::size).containsExactly(25, 1, 25, 1);
        assertThat(fifty.resultMenuPages()).extracting(DailyStatusView.DailyResultMenuPage::options)
                .extracting(List::size).containsExactly(25, 25, 25, 25);
    }

    @Test
    void createsTwoSingleOptionMenusForOnePlayer() {
        DailyStatusView view = DailyStatusView.versionOne(status(1));

        assertThat(view.resultMenuPages()).hasSize(2);
        assertThat(view.resultMenuPages()).allSatisfy(page -> assertThat(page.options()).hasSize(1));
    }
    private static DailyStatus status(int players) {
        List<DailyStatus.PlayerLine> lines = IntStream.rangeClosed(1, players)
                .mapToObj(id -> new DailyStatus.PlayerLine(id, String.format("Player %03d", id), Optional.empty(),
                        Optional.empty(), new StreakSummary(0, 0, 0, 0, 0, 0, 0)))
                .toList();
        return new DailyStatus(LocalDate.of(2026, 8, 3), lines, 0, 0);
    }
}