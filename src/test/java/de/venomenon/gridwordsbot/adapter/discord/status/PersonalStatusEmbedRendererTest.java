package de.venomenon.gridwordsbot.adapter.discord.status;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.in.PersonalStatusUseCase;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

class PersonalStatusEmbedRendererTest {
    @Test
    void rendersSeparateParticipationPeriodsWithoutBoardsOrRawText() {
        PersonalStatusUseCase.LatestSubmission grid = new PersonalStatusUseCase.LatestSubmission(
                GameType.GRIDWORDS, new ShareOutcome.Solved(4, 6), Duration.ofSeconds(125),
                LocalDate.of(2026, 7, 28), Instant.parse("2026-07-28T21:15:00Z"));
        PersonalStatusUseCase.LatestSubmission quad = new PersonalStatusUseCase.LatestSubmission(
                GameType.QUADWORDS, new ShareOutcome.Unsolved(9), Duration.ofSeconds(603),
                LocalDate.of(2026, 7, 27), Instant.parse("2026-01-28T21:15:00Z"));
        PersonalStatusUseCase.PersonalStatus status = new PersonalStatusUseCase.PersonalStatus(
                new PersonalStatusUseCase.ParticipationStatus(true, Optional.of(LocalDate.of(2026, 7, 20)),
                        Optional.of(LocalDate.of(2026, 7, 30))),
                new PersonalStatusUseCase.ParticipationStatus(false, Optional.empty(), Optional.empty()),
                true, Optional.of(grid), Optional.of(quad));

        MessageEmbed embed = new PersonalStatusEmbedRenderer(ZoneId.of("Europe/Berlin")).render(status);

        assertThat(embed.getTitle()).isEqualTo("Dein Status");
        assertThat(embed.getFields()).extracting(MessageEmbed.Field::getName).containsExactly(
                "GridWords-Teilnahme", "QuadWords-Teilnahme", "Reminder für aktive Spiele",
                "Letzte GridWords-Einreichung", "Letzte QuadWords-Einreichung");
        assertThat(embed.getFields().get(0).getValue()).isEqualTo("Aktiv seit 20.07.2026\nBis einschließlich 30.07.2026");
        assertThat(embed.getFields().get(1).getValue()).isEqualTo("Inaktiv");
        assertThat(embed.getFields().get(3).getValue())
                .isEqualTo("Gelöst · 4/6 · 2:05\nSpieltag: 28.07.2026\nEingereicht: 28.07.2026 23:15 Uhr");
        assertThat(embed.getFields().get(4).getValue())
                .isEqualTo("Nicht gelöst · X/9 · 10:03\nSpieltag: 27.07.2026\nEingereicht: 28.01.2026 22:15 Uhr");
        assertThat(embed.getDescription()).isNull();
    }

    @Test
    void rendersMissingResultsAndInactiveParticipationsClearly() {
        PersonalStatusUseCase.PersonalStatus status = new PersonalStatusUseCase.PersonalStatus(
                new PersonalStatusUseCase.ParticipationStatus(false, Optional.empty(), Optional.empty()),
                new PersonalStatusUseCase.ParticipationStatus(false, Optional.empty(), Optional.empty()),
                false, Optional.empty(), Optional.empty());

        MessageEmbed embed = new PersonalStatusEmbedRenderer(ZoneId.of("Europe/Berlin")).render(status);

        assertThat(embed.getFields().get(0).getValue()).isEqualTo("Inaktiv");
        assertThat(embed.getFields().get(1).getValue()).isEqualTo("Inaktiv");
        assertThat(embed.getFields().get(2).getValue()).isEqualTo("Aus");
        assertThat(embed.getFields().get(3).getValue()).isEqualTo("Noch keine gültige Einreichung.");
        assertThat(embed.getFields().get(4).getValue()).isEqualTo("Noch keine gültige Einreichung.");
    }
}
