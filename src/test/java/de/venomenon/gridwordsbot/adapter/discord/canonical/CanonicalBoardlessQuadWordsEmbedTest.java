package de.venomenon.gridwordsbot.adapter.discord.canonical;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.application.canonical.CanonicalResultMessage;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class CanonicalBoardlessQuadWordsEmbedTest {

    @Test
    void omitsTheBoardSectionWithoutRenderingAnEmptyPlaceholder() {
        CanonicalResultMessage message = new CanonicalResultMessage(
                "Tobias",
                GameType.QUADWORDS,
                LocalDate.of(2026, 7, 29),
                new ShareOutcome.Solved(7, 9),
                Duration.ofSeconds(245),
                null,
                new StreakSummary(12, 8, 7, 4, 3, 5, 2),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                "quadwords-result-42");

        var embed = new CanonicalEmbedRenderer().render(message);

        assertThat(embed.getTitle()).contains("QuadWords", "29. Juli 2026");
        assertThat(embed.getDescription())
                .contains("Tobias", "gelöst in 7/9", "4:05", "QuadWords gelöst")
                .doesNotContain("```", "⬛⬛⬛⬛⬛", "⬜⬜⬜⬜⬜", "Bild", "Board");
        assertThat(DiscordPublicationKey.matches("quadwords-result-42", embed.getFooter().getText())).isTrue();
    }
}
