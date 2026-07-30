package de.venomenon.gridwordsbot.adapter.discord.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.application.canonical.CanonicalResultMessage;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
                new NormalizedBoard(Collections.nCopies(6, "⬜⬜⬜⬜⬜")),
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
                new NormalizedBoard(Collections.nCopies(6, "⬜⬜⬜⬜⬜")),
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

    @Test
    void rendersQuadWordsAsCompactTwoByTwoPairsWithVisiblePairHeights() {
        String topLeft = "⬜🟨🟩⬜🟨";
        String topRight = "🟨🟩⬜🟨🟩";
        String bottomLeft = "🟩⬜🟨🟩⬜";
        String bottomRight = "🟨⬜🟩⬜🟨";
        QuadWordsBoards boards = new QuadWordsBoards(
                solvedBoard(topLeft, 7), solvedBoard(topRight, 9),
                solvedBoard(bottomLeft, 4), solvedBoard(bottomRight, 6));

        String[] pairs = quadWordsGrid(new CanonicalEmbedRenderer().render(quadWordsMessage(boards))).split("\n\n");
        String[] topPair = pairs[0].split("\n", -1);
        String[] bottomPair = pairs[1].split("\n", -1);

        assertThat(pairs).hasSize(2);
        assertThat(topPair).hasSize(9);
        assertThat(bottomPair).hasSize(6);
        assertThat(topPair[0]).isEqualTo(topLeft + "  " + topRight);
        assertThat(bottomPair[0]).isEqualTo(bottomLeft + "  " + bottomRight);
        assertThat(topPair[7]).isEqualTo("⬜⬜⬜⬜⬜  " + topRight);
        assertThat(bottomPair[4]).isEqualTo("⬜⬜⬜⬜⬜  " + bottomRight);
    }

    @Test
    void keepsEveryStoredRowForAnUnsolvedNineRowQuadWordsBoard() {
        String unsolved = "⬜🟨🟩⬜🟨";
        QuadWordsBoards boards = new QuadWordsBoards(
                new QuadWordsBoard(Collections.nCopies(9, unsolved)), solvedBoard(unsolved, 2),
                solvedBoard(unsolved, 2), solvedBoard(unsolved, 2));

        String[] topPair = quadWordsGrid(new CanonicalEmbedRenderer().render(quadWordsMessage(boards)))
                .split("\n\n")[0].split("\n", -1);

        assertThat(topPair).hasSize(9);
        for (String line : topPair) {
            assertThat(line).startsWith(unsolved);
        }
        assertThat(topPair[8]).isEqualTo(unsolved + "  ⬜⬜⬜⬜⬜");
    }

    @Test
    void preservesCorrectionContextAndPublicationKeyAndUsesMonospaceForBothGames() {
        String row = "⬜🟨🟩⬜🟨";
        QuadWordsBoards boards = new QuadWordsBoards(
                solvedBoard(row, 7), solvedBoard(row, 9), solvedBoard(row, 4), solvedBoard(row, 6));
        CanonicalEmbedRenderer renderer = new CanonicalEmbedRenderer();
        var embed = renderer.render(quadWordsMessage(boards));
        CanonicalResultMessage correction = new CanonicalResultMessage(
                "Tobias",
                GameType.QUADWORDS,
                LocalDate.of(2026, 7, 29),
                new ShareOutcome.Solved(8, 9),
                Duration.ofSeconds(560),
                null,
                quadWordsMessage(boards).streaks(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                Optional.of(boards),
                "quadwords-result-4");

        var edited = renderer.renderForEdit(correction, embed);
        var gridWordsEmbed = renderer.render(solvedMessage(
                OptionalInt.of(8), OptionalInt.of(3), OptionalInt.of(5), OptionalInt.of(2)));
        String expectedGridWordsBoard = "⬜⬜⬜⬜⬜\n🟨🟨🟨🟨🟨\n🟩🟩🟩🟩🟩";

        assertThat(embed.getDescription()).doesNotContain("Oben links", "Oben rechts", "Unten links", "Unten rechts");
        assertThat(edited.getDescription()).contains(
                "8/9", "✅ Komplett: 8 Tage", "💎 Perfekt: 3 Tage",
                "🤝 Gemeinsam komplett: 5 Tage", "🏆 Gemeinsam perfekt: 2 Tage");
        assertThat(DiscordPublicationKey.matches("quadwords-result-4", edited.getFooter().getText())).isTrue();
        assertThat(gridWordsEmbed.getDescription()).contains("```\n" + expectedGridWordsBoard + "\n```");
        assertThat(embed.getDescription().length()).isLessThanOrEqualTo(4096);
        assertThat(embed.getTitle().length()).isLessThanOrEqualTo(256);
        assertThat(embed.getFooter().getText().length()).isLessThanOrEqualTo(2048);
    }

    private static CanonicalResultMessage quadWordsMessage(QuadWordsBoards boards) {
        return new CanonicalResultMessage("Tobias", GameType.QUADWORDS,
                LocalDate.of(2026, 7, 29), new ShareOutcome.Solved(9, 9), Duration.ofSeconds(587), null,
                new StreakSummary(12, 8, 7, 4, 3, 5, 2), OptionalInt.of(8), OptionalInt.of(3),
                OptionalInt.of(5), OptionalInt.of(2), Optional.of(boards), "quadwords-result-4");
    }

    private static QuadWordsBoard solvedBoard(String activeRow, int height) {
        List<String> rows = new java.util.ArrayList<>(Collections.nCopies(9, "⬜⬜⬜⬜⬜"));
        for (int index = 0; index < height - 1; index++) {
            rows.set(index, activeRow);
        }
        rows.set(height - 1, "🟩🟩🟩🟩🟩");
        return new QuadWordsBoard(rows);
    }

    private static String quadWordsGrid(net.dv8tion.jda.api.entities.MessageEmbed embed) {
        String description = embed.getDescription();
        int start = description.indexOf("```\n") + 4;
        int end = description.indexOf("\n```", start);
        return description.substring(start, end);
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
                        "⬜⬜⬜⬜⬜",
                        "🟨🟨🟨🟨🟨",
                        "🟩🟩🟩🟩🟩")),
                new StreakSummary(12, 8, 7, 4, 3, 5, 2),
                personalComplete,
                personalPerfect,
                sharedComplete,
                sharedPerfect,
                "gridwords-result-4");
    }
}
