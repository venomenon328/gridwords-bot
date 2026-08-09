package de.venomenon.gridwordsbot.adapter.discord.status;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.in.PersonalStatusUseCase;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** Renders only the structured personal status projection; no persisted message content is exposed. */
public final class PersonalStatusEmbedRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu");
    private final DateTimeFormatter receivedAt;

    public PersonalStatusEmbedRenderer(ZoneId zoneId) {
        Objects.requireNonNull(zoneId, "zoneId");
        receivedAt = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm 'Uhr'", Locale.ROOT).withZone(zoneId);
    }

    public MessageEmbed render(PersonalStatusUseCase.PersonalStatus status) {
        Objects.requireNonNull(status, "status");
        EmbedBuilder embed = new EmbedBuilder().setTitle("Dein Status");
        if (!status.known()) {
            return embed.setDescription("Du hast noch kein Spielerprofil im Bot.").build();
        }
        return embed
                .addField("Heute", today(status.gridWordsToday()) + "\n" + today(status.quadWordsToday()), false)
                .addField("Laufende Serien", streaks(status.streaks()), false)
                .addField("Teilnahme & Reminder", participationAndReminder(status), false)
                .addField("Letzte GridWords-Einreichung", submission(status.latestGridWordsSubmission()), false)
                .addField("Letzte QuadWords-Einreichung", submission(status.latestQuadWordsSubmission()), false)
                .build();
    }

    private static String today(PersonalStatusUseCase.TodayGameStatus status) {
        String game = status.gameType() == GameType.GRIDWORDS ? "🟩 GridWords" : "🟦 QuadWords";
        if (!status.participating()) {
            return game + ": — keine Teilnahme";
        }
        if (status.outcome().isEmpty()) {
            return game + ": ⬜ noch nicht eingereicht";
        }
        ShareOutcome outcome = status.outcome().orElseThrow();
        String score = outcome instanceof ShareOutcome.Solved solved
                ? "✅ " + solved.attemptsUsed() + "/" + solved.maxAttempts()
                : "❌ X/" + outcome.maxAttempts();
        return game + ": " + score + " · " + duration(status.duration().orElseThrow());
    }

    private static String streaks(PersonalStatusUseCase.PersonalStreaks streaks) {
        return "🔥 Aktivität: " + streak(streaks.activity())
                + "\n✅ Komplett: " + streak(streaks.complete())
                + "\n🟩 GridWords gelöst: " + streak(streaks.gridWordsSolved())
                + "\n🟦 QuadWords gelöst: " + streak(streaks.quadWordsSolved())
                + "\n💎 Perfekt: " + streak(streaks.perfect());
    }

    private static String streak(OptionalInt value) {
        if (value.isEmpty()) return "—";
        int days = value.getAsInt();
        return days + (days == 1 ? " Tag" : " Tage");
    }

    private static String participationAndReminder(PersonalStatusUseCase.PersonalStatus status) {
        return "GridWords: " + participation(status.gridWordsParticipation())
                + "\nQuadWords: " + participation(status.quadWordsParticipation())
                + "\n" + (status.reminderOptIn() ? "🔔 Reminder: an" : "🔕 Reminder: aus");
    }

    private static String participation(PersonalStatusUseCase.ParticipationStatus participation) {
        if (!participation.active()) {
            return "inaktiv";
        }
        String value = "aktiv seit " + DATE.format(participation.activeFrom().orElseThrow());
        return participation.activeUntil()
                .map(end -> value + " · bis einschließlich " + DATE.format(end))
                .orElse(value);
    }

    private String submission(Optional<PersonalStatusUseCase.LatestSubmission> submission) {
        if (submission.isEmpty()) {
            return "Noch keine gültige Einreichung.";
        }
        PersonalStatusUseCase.LatestSubmission latest = submission.orElseThrow();
        return outcome(latest.outcome())
                + " · " + duration(latest.duration())
                + "\nSpieltag: " + DATE.format(latest.gameDate())
                + "\nEingereicht: " + receivedAt.format(latest.receivedAt());
    }

    private static String outcome(ShareOutcome outcome) {
        if (outcome instanceof ShareOutcome.Solved solved) {
            return "Gelöst · " + solved.attemptsUsed() + "/" + solved.maxAttempts();
        }
        return "Nicht gelöst · X/" + outcome.maxAttempts();
    }

    private static String duration(Duration duration) {
        return String.format(Locale.ROOT, "%d:%02d", duration.toMinutes(), duration.toSecondsPart());
    }
}
