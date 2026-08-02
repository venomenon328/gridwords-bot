package de.venomenon.gridwordsbot.adapter.discord.status;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.port.in.DailyResultDetailsUseCase;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DailyResultDetailsEmbedRendererTest {
    @Test void rendersMissingResultWithoutTechnicalDetails() {
        var embed = new DailyResultDetailsEmbedRenderer().render(new DailyResultDetailsUseCase.Missing("Player", GameType.QUADWORDS, LocalDate.of(2026, 8, 3)));
        assertThat(embed.getDescription()).contains("Player", "liegt kein Ergebnis vor").doesNotContain("raw", "submission", "parser");
    }
}