package de.venomenon.gridwordsbot.adapter.discord.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.in.DailyResultDetailsUseCase;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class DailyResultDetailsEmbedRendererTest {
    private static final LocalDate DATE = LocalDate.of(2026, 8, 3);
    private final DailyResultDetailsEmbedRenderer renderer = new DailyResultDetailsEmbedRenderer();

    @Test
    void rendersMissingResultWithoutTechnicalDetails() {
        var embed = renderer.render(new DailyResultDetailsUseCase.Missing("Player", GameType.QUADWORDS, DATE));

        assertThat(embed.getTitle()).contains("QuadWords", "3. August 2026");
        assertThat(embed.getDescription())
                .contains("Player", "liegt kein Ergebnis vor")
                .doesNotContain("raw", "submission", "parser", "claim");
    }

    @Test
    void rendersSolvedGridWordsWithAttemptsDurationAndGrid() {
        ParsedGameResult result = new ParsedGameResult(
                GameType.GRIDWORDS,
                DATE,
                new ShareOutcome.Solved(3, 6),
                Duration.ofSeconds(85),
                OptionalInt.empty(),
                Optional.of(new NormalizedBoard(List.of(
                        "⬜⬜⬜⬜⬜",
                        "🟨🟨🟨🟨🟨",
                        "🟩🟩🟩🟩🟩"))));

        var embed = renderer.render(new DailyResultDetailsUseCase.Found("Player", result));

        assertThat(embed.getDescription()).contains(
                "Player · gelöst in 3/6 · 1:25",
                "```\n⬜⬜⬜⬜⬜\n🟨🟨🟨🟨🟨\n🟩🟩🟩🟩🟩\n```");
    }

    @Test
    void rendersUnsolvedGridWordsWithAllRows() {
        ParsedGameResult result = new ParsedGameResult(
                GameType.GRIDWORDS,
                DATE,
                new ShareOutcome.Unsolved(6),
                Duration.ofSeconds(125),
                OptionalInt.empty(),
                Optional.of(new NormalizedBoard(Collections.nCopies(6, "⬜🟨⬜🟨⬜"))));

        var embed = renderer.render(new DailyResultDetailsUseCase.Found("Player", result));

        assertThat(embed.getDescription()).contains("nicht gelöst · X/6 · 2:05");
        assertThat(grid(embed.getDescription()).split("\n", -1)).hasSize(6);
    }

    @Test
    void rendersSolvedQuadWordsWithCanonicalPairHeightsAndDarkPadding() {
        QuadWordsBoards boards = new QuadWordsBoards(
                solvedBoard("⬜🟨🟩⬜🟨", 7), solvedBoard("🟨🟩⬜🟨🟩", 9),
                solvedBoard("🟩⬜🟨🟩⬜", 4), solvedBoard("🟨⬜🟩⬜🟨", 6));
        ParsedGameResult result = new ParsedGameResult(
                GameType.QUADWORDS,
                DATE,
                new ShareOutcome.Solved(9, 9),
                Duration.ofSeconds(587),
                OptionalInt.empty(),
                Optional.empty(),
                Optional.of(boards));

        var embed = renderer.render(new DailyResultDetailsUseCase.Found("Player", result));
        String[] pairs = grid(embed.getDescription()).split("\n\n");
        String[] topPair = pairs[0].split("\n", -1);
        String[] bottomPair = pairs[1].split("\n", -1);

        assertThat(embed.getDescription()).contains("gelöst in 9/9 · 9:47");
        assertThat(topPair).hasSize(9);
        assertThat(bottomPair).hasSize(6);
        assertThat(topPair[7]).startsWith("⬛⬛⬛⬛⬛  ");
        assertThat(bottomPair[4]).startsWith("⬛⬛⬛⬛⬛  ");
    }

    @Test
    void rendersUnsolvedQuadWordsWithAllNineRows() {
        String row = "⬜🟨⬜🟨⬜";
        QuadWordsBoard board = new QuadWordsBoard(Collections.nCopies(9, row));
        ParsedGameResult result = new ParsedGameResult(
                GameType.QUADWORDS,
                DATE,
                new ShareOutcome.Unsolved(9),
                Duration.ofSeconds(600),
                OptionalInt.empty(),
                Optional.empty(),
                Optional.of(new QuadWordsBoards(board, board, board, board)));

        var embed = renderer.render(new DailyResultDetailsUseCase.Found("Player", result));

        assertThat(embed.getDescription()).contains("nicht gelöst · X/9 · 10:00");
        assertThat(grid(embed.getDescription()).split("\n\n")[0].split("\n", -1)).hasSize(9);
    }

    @Test
    void rendersBoardlessQuadWordsExplicitly() {
        ParsedGameResult result = new ParsedGameResult(
                GameType.QUADWORDS,
                DATE,
                new ShareOutcome.Solved(4, 9),
                Duration.ofSeconds(90),
                OptionalInt.empty(),
                Optional.empty(),
                Optional.empty());

        var embed = renderer.render(new DailyResultDetailsUseCase.Found("Player", result));

        assertThat(embed.getDescription())
                .contains("gelöst in 4/9", "Für dieses Ergebnis ist kein Board gespeichert.")
                .doesNotContain("```");
    }

    @Test
    void rendersRejectedSelectionWithoutInternalReason() {
        var embed = renderer.render(new DailyResultDetailsUseCase.Rejected(
                DailyResultDetailsUseCase.Reason.STATUS_NOT_CURRENT));

        assertThat(embed.getDescription())
                .contains("aktuelle Tagesnachricht")
                .doesNotContain("STATUS_NOT_CURRENT");
    }

    @Test
    void rejectsDescriptionsBeyondTheDiscordLimit() {
        ParsedGameResult result = new ParsedGameResult(
                GameType.GRIDWORDS,
                DATE,
                new ShareOutcome.Solved(1, 6),
                Duration.ZERO,
                OptionalInt.empty(),
                Optional.of(new NormalizedBoard(List.of("🟩🟩🟩🟩🟩"))));

        assertThatThrownBy(() -> renderer.render(new DailyResultDetailsUseCase.Found("P".repeat(4_100), result)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Discord embed limit");
    }

    private static QuadWordsBoard solvedBoard(String activeRow, int solutionHeight) {
        List<String> rows = new ArrayList<>(Collections.nCopies(9, "🟨⬜🟨⬜🟨"));
        for (int index = 0; index < solutionHeight - 1; index++) {
            rows.set(index, activeRow);
        }
        rows.set(solutionHeight - 1, "🟩🟩🟩🟩🟩");
        return new QuadWordsBoard(rows);
    }

    private static String grid(String description) {
        int start = description.indexOf("```\n") + 4;
        int end = description.indexOf("\n```", start);
        return description.substring(start, end);
    }
}
