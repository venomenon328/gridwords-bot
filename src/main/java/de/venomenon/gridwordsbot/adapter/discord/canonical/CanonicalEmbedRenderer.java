package de.venomenon.gridwordsbot.adapter.discord.canonical;

import de.venomenon.gridwordsbot.application.canonical.CanonicalResultMessage;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

final class CanonicalEmbedRenderer {

    MessageEmbed render(CanonicalResultMessage message) {
        String outcome = outcome(message);
        String duration = String.format("%d:%02d", message.duration().toMinutes(), message.duration().toSecondsPart());
        String title = "\uD83D\uDFE9 GridWords · "
                + message.gameDate().format(DateTimeFormatter.ofPattern("d. MMMM uuuu", Locale.GERMAN));
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(message.playerDisplayName() + " · " + outcome + " · " + duration
                        + "\n\n" + message.board().canonicalText()
                        + "\n\n" + series(message))
                .setFooter(message.publicationKey())
                .build();
    }

    private static String outcome(CanonicalResultMessage message) {
        if (message.outcome() instanceof ShareOutcome.Solved solved) {
            return "gelöst in " + solved.attemptsUsed() + "/" + solved.maxAttempts();
        }
        return "nicht gelöst · X/" + message.outcome().maxAttempts();
    }

    private static String series(CanonicalResultMessage message) {
        StringBuilder series = new StringBuilder()
                .append("\uD83D\uDD25 Aktivität: ").append(days(message.streaks().personalActivity()))
                .append("\n\uD83D\uDFE9 GridWords gelöst: ").append(daysOrNone(message.streaks().personalGridWordsSolved()));
        appendContextual(series, "Komplett", message.personalComplete());
        appendContextual(series, "Perfekt", message.personalPerfect());
        appendContextual(series, "Gemeinsam komplett", message.sharedComplete());
        appendContextual(series, "Gemeinsam perfekt", message.sharedPerfect());
        return series.toString();
    }

    private static void appendContextual(StringBuilder series, String label, java.util.OptionalInt streak) {
        if (streak.isPresent()) {
            series.append("\n").append(label).append(": ").append(days(streak.getAsInt()));
        }
    }

    private static String days(int count) {
        return count + " " + (count == 1 ? "Tag" : "Tage");
    }

    private static String daysOrNone(int count) {
        return count == 0 ? "keine laufende Serie" : days(count);
    }
}