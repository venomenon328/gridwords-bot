package de.venomenon.gridwordsbot.adapter.discord.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import de.venomenon.gridwordsbot.port.out.DiscordDeliveryException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

class DailyStatusEmbedRendererTest {
    private static final LocalDate DATE = LocalDate.of(2026, 7, 30);
    private final DailyStatusEmbedRenderer renderer = new DailyStatusEmbedRenderer();

    @Test
    void rendersCorrectUtf8AllStatusesAndAllSevenStreaksWithoutVisibleTechnicalKey() {
        DailyStatus status = new DailyStatus(DATE, List.of(
                line(1, "Tobias", Optional.of(result(GameType.GRIDWORDS, true)), Optional.empty()),
                line(2, "Georgia", Optional.of(result(GameType.GRIDWORDS, false)),
                        Optional.of(result(GameType.QUADWORDS, true)))), 6, 5);

        List<MessageEmbed> embeds = renderer.render(12L, status);
        String rendered = embeds.stream().map(embed -> embed.getTitle() + embed.getFields().stream()
                .map(field -> field.getName() + field.getValue()).collect(java.util.stream.Collectors.joining()))
                .collect(java.util.stream.Collectors.joining());

        assertThat(rendered).contains("Wortspiele · 30. Juli 2026", "🔥 Aktivität", "✅ 1/9", "❌ X/6",
                "⬜ noch nicht eingereicht", "Komplett", "GridWords gelöst", "QuadWords gelöst", "Perfekt",
                "Gemeinsam komplett: 6", "Gemeinsam perfekt: 5");
        assertThat(rendered).doesNotContain("Â", "Ã", "â", "gridwords-daily-status:");
        assertThat(embeds).allSatisfy(embed -> assertThat(embed.getFooter()).isNull());
    }

    @Test
    void splitsMoreThanTwentyFiveFieldsIntoMultipleEmbedsWithoutDroppingPlayers() {
        List<DailyStatus.PlayerLine> players = new ArrayList<>();
        for (int index = 1; index <= 30; index++) players.add(line(index, "P" + index, Optional.empty(), Optional.empty()));
        List<MessageEmbed> embeds = renderer.render(12L, new DailyStatus(DATE, players, 0, 0));
        assertThat(embeds).hasSize(2);
        assertThat(embeds.stream().flatMap(embed -> embed.getFields().stream()).map(MessageEmbed.Field::getName))
                .contains("P1", "P30", "Gemeinsame Serien");
    }

    @Test
    void rejectsACompleteStatusThatCannotFitInsteadOfPublishingAPartialOne() {
        List<DailyStatus.PlayerLine> players = new ArrayList<>();
        for (int index = 1; index <= 120; index++) {
            players.add(line(index, "Player-" + index + "-" + "x".repeat(20), Optional.empty(), Optional.empty()));
        }
        assertThatThrownBy(() -> renderer.render(12L, new DailyStatus(DATE, players, 0, 0)))
                .isInstanceOf(DiscordDeliveryException.class)
                .extracting(error -> ((DiscordDeliveryException) error).permanent()).isEqualTo(true);
    }

    private static DailyStatus.PlayerLine line(long id, String name, Optional<ParsedGameResult> grid,
            Optional<ParsedGameResult> quad) {
        return new DailyStatus.PlayerLine(id, name, grid, quad, new StreakSummary(7, 6, 5, 4, 3, 2, 1));
    }

    private static ParsedGameResult result(GameType type, boolean solved) {
        int max = type == GameType.GRIDWORDS ? 6 : 9;
        return new ParsedGameResult(type, DATE,
                solved ? new ShareOutcome.Solved(1, max) : new ShareOutcome.Unsolved(max), Duration.ofSeconds(125),
                OptionalInt.empty(), type == GameType.GRIDWORDS
                        ? Optional.of(new NormalizedBoard(java.util.Collections.nCopies(solved ? 1 : 6, "⬜⬜⬜⬜⬜")))
                        : Optional.empty());
    }
}
