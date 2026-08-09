package de.venomenon.gridwordsbot.adapter.discord.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.domain.status.DailyStatusView;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import de.venomenon.gridwordsbot.port.out.DiscordDeliveryException;
import java.time.LocalDate;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Collections;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class DailyStatusComponentRendererTest {
    @Test
    void rejectsMoreThanFiftyParticipantsBeforeDiscordIo() {
        DailyStatus status = new DailyStatus(LocalDate.of(2026, 8, 3), IntStream.rangeClosed(1, 51)
                .mapToObj(id -> new DailyStatus.PlayerLine(id, "Player " + id, Optional.empty(), Optional.empty(),
                        new StreakSummary(0, 0, 0, 0, 0, 0, 0))).toList(), 0, 0);

        assertThatThrownBy(() -> new DailyStatusComponentRenderer().render(DailyStatusView.versionOne(status)))
                .isInstanceOf(DiscordDeliveryException.class)
                .matches(error -> ((DiscordDeliveryException) error).permanent());
    }

    @Test
    void rendersStatusAwareOptionsAndExactlyTwoGameLinksWithinFiveRows() {
        DailyStatus.PlayerLine solved = new DailyStatus.PlayerLine(1L, "A".repeat(140),
                Optional.of(result(new ShareOutcome.Solved(3, 6))), Optional.empty(), streaks());
        DailyStatus.PlayerLine unsolved = new DailyStatus.PlayerLine(2L, "B",
                Optional.of(result(new ShareOutcome.Unsolved(6))), Optional.empty(), streaks());
        DailyStatus.PlayerLine missing = new DailyStatus.PlayerLine(3L, "C", Optional.empty(), Optional.empty(), streaks());

        var rows = new DailyStatusComponentRenderer().render(DailyStatusView.versionOne(
                new DailyStatus(LocalDate.of(2026, 8, 3), List.of(solved, unsolved, missing), 0, 0)));

        assertThat(rows).hasSize(3);
        var options = rows.getFirst().getComponents().getFirst().asStringSelectMenu().getOptions();
        assertThat(options).extracting(option -> option.getLabel()).allSatisfy(label -> assertThat(label.length()).isLessThanOrEqualTo(100));
        assertThat(options).extracting(option -> option.getLabel()).anySatisfy(label -> assertThat(label).startsWith("✅ "))
                .anySatisfy(label -> assertThat(label).isEqualTo("❌ B"))
                .anySatisfy(label -> assertThat(label).isEqualTo("⬜ C"));
        assertThat(options).extracting(option -> option.getDescription()).contains("3/6 · 1:25", "X/6 · 1:25", "Noch nicht eingereicht");
        assertThat(rows.getLast().getComponents()).extracting(component -> component.asButton().getUrl())
                .containsExactly("https://gridgames.app/gridwords", "https://gridgames.app/quadwords");
    }

    @Test
    void rendersFiftyParticipantsAsFourSelectRowsAndOneLinkRow() {
        DailyStatus status = new DailyStatus(LocalDate.of(2026, 8, 3), IntStream.rangeClosed(1, 50)
                .mapToObj(id -> new DailyStatus.PlayerLine(id, "Player " + id, Optional.empty(), Optional.empty(), streaks()))
                .toList(), 0, 0);

        assertThat(new DailyStatusComponentRenderer().render(DailyStatusView.versionOne(status))).hasSize(5);
    }

    private static ParsedGameResult result(ShareOutcome outcome) {
        return new ParsedGameResult(GameType.GRIDWORDS, LocalDate.of(2026, 8, 3), outcome, Duration.ofSeconds(85),
                java.util.OptionalInt.empty(), Optional.of(new NormalizedBoard(Collections.nCopies(
                        outcome instanceof ShareOutcome.Solved solved ? solved.attemptsUsed() : 6, "⬜⬜⬜⬜⬜"))));
    }

    private static StreakSummary streaks() {
        return new StreakSummary(0, 0, 0, 0, 0, 0, 0);
    }
}
