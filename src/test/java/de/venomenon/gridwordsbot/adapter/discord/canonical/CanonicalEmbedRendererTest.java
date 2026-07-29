package de.venomenon.gridwordsbot.adapter.discord.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.application.canonical.CanonicalResultMessage;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import net.dv8tion.jda.api.EmbedBuilder;
import org.junit.jupiter.api.Test;

class CanonicalEmbedRendererTest {

    @Test
    void rendersMandatoryAndContextualSeriesWithSymbolsAndHiddenPublicationKey() {
        CanonicalResultMessage message = solvedMessage(
                OptionalInt.of(8), OptionalInt.of(3), OptionalInt.of(5), OptionalInt.of(2));

        var embed = new CanonicalEmbedRenderer().render(message);

        assertThat(embed.getTitle()).contains("GridWords", "29. Juli 2026");
        assertThat(embed.getDescription()).contains(
                "Tobias",
                "1:25",
                "✅ Komplett: 8 Tage",
                "💎 Perfekt: 3 Tage",
                "🤝 Gemeinsam komplett: 5 Tage",
                "🏆 Gemeinsam perfekt: 2 Tage");
        assertThat(embed.getDescription()).doesNotContain("Spielserie", "@everyone");
        assertThat(embed.getFooter().getText()).doesNotContain("gridwords-result-4");
        assertThat(DiscordPublicationKey.matches("gridwords-result-4", embed.getFooter().getText())).isTrue();
    }

    @Test
    void preservesAndMigratesCompletedDayLinesWhenAStillSolvedCorrectionIsEdited() {
        CanonicalEmbedRenderer renderer = new CanonicalEmbedRenderer();
        var previous = new EmbedBuilder()
                .setDescription("alt\n\nKomplett: 8 Tage\nPerfekt: 3 Tage"
                        + "\nGemeinsam komplett: 5 Tage\nGemeinsam perfekt: 2 Tage")
                .build();
        CanonicalResultMessage correction = solvedMessage(
                OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty());

        var edited = renderer.renderForEdit(correction, previous);

        assertThat(edited.getDescription()).contains(
                "✅ Komplett: 8 Tage",
                "💎 Perfekt: 3 Tage",
                "🤝 Gemeinsam komplett: 5 Tage",
                "🏆 Gemeinsam perfekt: 2 Tage");
        assertThat(edited.getDescription()).doesNotContain("\nKomplett: ", "\nPerfekt: ");
    }

    @Test
    void dropsPerfectLinesWhenACorrectionIsNowUnsolved() {
        CanonicalEmbedRenderer renderer = new CanonicalEmbedRenderer();
        var previous = new EmbedBuilder()
                .setDescription("alt\n\nKomplett: 8 Tage\nPerfekt: 3 Tage"
                        + "\nGemeinsam komplett: 5 Tage\nGemeinsam perfekt: 2 Tage")
                .build();
        CanonicalResultMessage correction = new CanonicalResultMessage(
                "Tobias",
                GameType.GRIDWORDS,
                LocalDate.of(2026, 7, 29),
                new ShareOutcome.Unsolved(6),
                Duration.ofSeconds(10),
                new NormalizedBoard(Collections.nCopies(6, "\u2b1c\u2b1c\u2b1c\u2b1c\u2b1c")),
                new StreakSummary(1, 8, 0, 4, 0, 5, 0),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                "key");

        var edited = renderer.renderForEdit(correction, previous);

        assertThat(edited.getDescription())
                .contains("✅ Komplett: 8 Tage", "🤝 Gemeinsam komplett: 5 Tage")
                .doesNotContain("💎 Perfekt", "🏆 Gemeinsam perfekt");
    }

    @Test
    void rendersUnsolvedOutcomeAndNoRunningSeries() {
        CanonicalResultMessage message = new CanonicalResultMessage(
                "Tobias",
                GameType.GRIDWORDS,
                LocalDate.of(2026, 7, 29),
                new ShareOutcome.Unsolved(6),
                Duration.ofSeconds(10),
                new NormalizedBoard(Collections.nCopies(6, "\u2b1c\u2b1c\u2b1c\u2b1c\u2b1c")),
                new StreakSummary(1, 0, 0, 0, 0, 0, 0),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                "key");

        assertThat(new CanonicalEmbedRenderer().render(message).getDescription())
                .contains("X/6", "keine laufende Serie")
                .doesNotContain("Komplett", "Perfekt", "Gemeinsam");
    }

    private static CanonicalResultMessage solvedMessage(
            OptionalInt personalComplete,
            OptionalInt personalPerfect,
            OptionalInt sharedComplete,
            OptionalInt sharedPerfect) {
        return new CanonicalResultMessage(
                "Tobias",
                GameType.GRIDWORDS,
                LocalDate.of(2026, 7, 29),
                new ShareOutcome.Solved(3, 6),
                Duration.ofSeconds(85),
                new NormalizedBoard(List.of(
                        "\u2b1c\u2b1c\u2b1c\u2b1c\u2b1c",
                        "\ud83d\udfe8\ud83d\udfe8\ud83d\udfe8\ud83d\udfe8\ud83d\udfe8",
                        "\ud83d\udfe9\ud83d\udfe9\ud83d\udfe9\ud83d\udfe9\ud83d\udfe9")),
                new StreakSummary(12, 8, 7, 4, 3, 5, 2),
                personalComplete,
                personalPerfect,
                sharedComplete,
                sharedPerfect,
                "gridwords-result-4");
    }
}
