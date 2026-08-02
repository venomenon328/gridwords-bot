package de.venomenon.gridwordsbot.adapter.discord.status;

import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.in.PersonalStatusUseCase;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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
        return new EmbedBuilder()
                .setTitle("Dein Status")
                .addField("Teilnahme", participation(status.participation()), false)
                .addField("Reminder", status.reminderOptIn() ? "An" : "Aus", true)
                .addField("GridWords", submission(status.latestGridWordsSubmission()), false)
                .addField("QuadWords", submission(status.latestQuadWordsSubmission()), false)
                .build();
    }

    private static String participation(PersonalStatusUseCase.ParticipationStatus participation) {
        if (!participation.active()) {
            return "Inaktiv";
        }
        String value = "Aktiv seit " + DATE.format(participation.activeFrom().orElseThrow());
        return participation.activeUntil()
                .map(end -> value + "\nBis einschließlich " + DATE.format(end))
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
