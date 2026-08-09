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
import java.util.OptionalInt;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

class PersonalStatusEmbedRendererTest {
    private final PersonalStatusEmbedRenderer renderer = new PersonalStatusEmbedRenderer(ZoneId.of("Europe/Berlin"));

    @Test
    void rendersDashboardInTodayStreakParticipationAndLatestSubmissionOrder() {
        PersonalStatusUseCase.LatestSubmission grid = new PersonalStatusUseCase.LatestSubmission(
                GameType.GRIDWORDS, new ShareOutcome.Solved(4, 6), Duration.ofSeconds(125),
                LocalDate.of(2026, 7, 28), Instant.parse("2026-07-28T21:15:00Z"));
        PersonalStatusUseCase.LatestSubmission quad = new PersonalStatusUseCase.LatestSubmission(
                GameType.QUADWORDS, new ShareOutcome.Unsolved(9), Duration.ofSeconds(603),
                LocalDate.of(2026, 7, 27), Instant.parse("2026-01-28T21:15:00Z"));
        PersonalStatusUseCase.PersonalStatus status = new PersonalStatusUseCase.PersonalStatus(
                true,
                PersonalStatusUseCase.TodayGameStatus.submitted(
                        GameType.GRIDWORDS, new ShareOutcome.Solved(3, 6), Duration.ofSeconds(85)),
                PersonalStatusUseCase.TodayGameStatus.open(GameType.QUADWORDS),
                new PersonalStatusUseCase.PersonalStreaks(
                        OptionalInt.of(23), OptionalInt.of(7), OptionalInt.of(11), OptionalInt.of(4), OptionalInt.of(3)),
                new PersonalStatusUseCase.ParticipationStatus(
                        true, Optional.of(LocalDate.of(2026, 7, 20)), Optional.of(LocalDate.of(2026, 7, 30))),
                new PersonalStatusUseCase.ParticipationStatus(
                        true, Optional.of(LocalDate.of(2026, 7, 21)), Optional.empty()),
                true,
                Optional.of(grid),
                Optional.of(quad));

        MessageEmbed embed = renderer.render(status);

        assertThat(embed.getTitle()).isEqualTo("Dein Status");
        assertThat(embed.getFields()).extracting(MessageEmbed.Field::getName).containsExactly(
                "Heute", "Laufende Serien", "Teilnahme & Reminder",
                "Letzte GridWords-Einreichung", "Letzte QuadWords-Einreichung");
        assertThat(embed.getFields().get(0).getValue()).isEqualTo(
                "🟩 GridWords: ✅ 3/6 · 1:25\n🟦 QuadWords: ⬜ noch nicht eingereicht");
        assertThat(embed.getFields().get(1).getValue()).isEqualTo(
                "🔥 Aktivität: 23 Tage\n✅ Komplett: 7 Tage\n🟩 GridWords gelöst: 11 Tage\n"
                        + "🟦 QuadWords gelöst: 4 Tage\n💎 Perfekt: 3 Tage");
        assertThat(embed.getFields().get(2).getValue()).isEqualTo(
                "GridWords: aktiv seit 20.07.2026 · bis einschließlich 30.07.2026\n"
                        + "QuadWords: aktiv seit 21.07.2026\n🔔 Reminder: an");
        assertThat(embed.getFields().get(3).getValue())
                .isEqualTo("Gelöst · 4/6 · 2:05\nSpieltag: 28.07.2026\nEingereicht: 28.07.2026 23:15 Uhr");
        assertThat(embed.getFields().get(4).getValue())
                .isEqualTo("Nicht gelöst · X/9 · 10:03\nSpieltag: 27.07.2026\nEingereicht: 28.01.2026 22:15 Uhr");
    }

    @Test
    void rendersTodaysUnsolvedResultWithXAndDuration() {
        PersonalStatusUseCase.PersonalStatus status = new PersonalStatusUseCase.PersonalStatus(
                true,
                PersonalStatusUseCase.TodayGameStatus.notParticipating(GameType.GRIDWORDS),
                PersonalStatusUseCase.TodayGameStatus.submitted(
                        GameType.QUADWORDS, new ShareOutcome.Unsolved(9), Duration.ofSeconds(125)),
                new PersonalStatusUseCase.PersonalStreaks(
                        OptionalInt.of(1), OptionalInt.empty(), OptionalInt.empty(), OptionalInt.of(0), OptionalInt.empty()),
                PersonalStatusUseCase.ParticipationStatus.inactive(),
                new PersonalStatusUseCase.ParticipationStatus(
                        true, Optional.of(LocalDate.of(2026, 8, 9)), Optional.empty()),
                true,
                Optional.empty(),
                Optional.empty());

        MessageEmbed embed = renderer.render(status);

        assertThat(embed.getFields().getFirst().getValue()).isEqualTo(
                "🟩 GridWords: — keine Teilnahme\n🟦 QuadWords: ❌ X/9 · 2:05");
    }

    @Test
    void rendersNonParticipationAndNonApplicableStreaksAsDashInsteadOfZero() {
        PersonalStatusUseCase.PersonalStatus status = new PersonalStatusUseCase.PersonalStatus(
                true,
                PersonalStatusUseCase.TodayGameStatus.open(GameType.GRIDWORDS),
                PersonalStatusUseCase.TodayGameStatus.notParticipating(GameType.QUADWORDS),
                new PersonalStatusUseCase.PersonalStreaks(
                        OptionalInt.of(4), OptionalInt.empty(), OptionalInt.of(2), OptionalInt.empty(), OptionalInt.empty()),
                new PersonalStatusUseCase.ParticipationStatus(
                        true, Optional.of(LocalDate.of(2026, 8, 1)), Optional.empty()),
                PersonalStatusUseCase.ParticipationStatus.inactive(),
                false,
                Optional.empty(),
                Optional.empty());

        MessageEmbed embed = renderer.render(status);

        assertThat(embed.getFields().get(0).getValue()).isEqualTo(
                "🟩 GridWords: ⬜ noch nicht eingereicht\n🟦 QuadWords: — keine Teilnahme");
        assertThat(embed.getFields().get(1).getValue()).contains(
                "🔥 Aktivität: 4 Tage",
                "✅ Komplett: —",
                "🟩 GridWords gelöst: 2 Tage",
                "🟦 QuadWords gelöst: —",
                "💎 Perfekt: —");
        assertThat(embed.getFields().get(2).getValue()).contains("🔕 Reminder: aus");
    }

    @Test
    void rendersUnknownPlayerWithoutInventingPersistedState() {
        MessageEmbed embed = renderer.render(PersonalStatusUseCase.PersonalStatus.unknown());

        assertThat(embed.getTitle()).isEqualTo("Dein Status");
        assertThat(embed.getDescription()).isEqualTo("Du hast noch kein Spielerprofil im Bot.");
        assertThat(embed.getFields()).isEmpty();
    }
}
