package de.venomenon.gridwordsbot.adapter.discord.canonical;

import de.venomenon.gridwordsbot.application.canonical.CanonicalResultMessage;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

final class CanonicalEmbedRenderer {

    private static final String PERSONAL_COMPLETE = "✅ Komplett: ";
    private static final String PERSONAL_PERFECT = "💎 Perfekt: ";
    private static final String SHARED_COMPLETE = "🤝 Gemeinsam komplett: ";
    private static final String SHARED_PERFECT = "🏆 Gemeinsam perfekt: ";

    MessageEmbed render(CanonicalResultMessage message) {
        String outcome = outcome(message);
        String duration = String.format("%d:%02d", message.duration().toMinutes(), message.duration().toSecondsPart());
        String title = "\uD83D\uDFE9 GridWords · "
                + message.gameDate().format(DateTimeFormatter.ofPattern("d. MMMM uuuu", Locale.GERMAN));
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(message.playerDisplayName() + " · " + outcome + " · " + duration
                        + "\n\n" + boardText(message)
                        + "\n\n" + series(message))
                .setFooter(DiscordPublicationKey.encode(message.publicationKey()))
                .build();
    }

    MessageEmbed renderForEdit(CanonicalResultMessage message, MessageEmbed existingEmbed) {
        MessageEmbed rendered = render(message);
        if (existingEmbed == null || existingEmbed.getDescription() == null) {
            return rendered;
        }
        String description = rendered.getDescription();
        description = preserveLine(description, existingEmbed.getDescription(), PERSONAL_COMPLETE, "Komplett: ", true);
        description = preserveLine(
                description, existingEmbed.getDescription(), SHARED_COMPLETE, "Gemeinsam komplett: ", true);
        boolean stillSolved = message.outcome() instanceof ShareOutcome.Solved;
        description = preserveLine(
                description, existingEmbed.getDescription(), PERSONAL_PERFECT, "Perfekt: ", stillSolved);
        description = preserveLine(
                description, existingEmbed.getDescription(), SHARED_PERFECT, "Gemeinsam perfekt: ", stillSolved);
        return new EmbedBuilder(rendered).setDescription(description).build();
    }

    private static String preserveLine(
            String current,
            String previous,
            String currentPrefix,
            String legacyPrefix,
            boolean preserve) {
        if (!preserve || current.contains(currentPrefix)) {
            return current;
        }
        Optional<String> previousLine = previous.lines()
                .filter(line -> line.startsWith(currentPrefix) || line.startsWith(legacyPrefix))
                .findFirst();
        if (previousLine.isEmpty()) {
            return current;
        }
        String line = previousLine.get();
        String value = line.substring(line.startsWith(currentPrefix) ? currentPrefix.length() : legacyPrefix.length());
        return current + "\n" + currentPrefix + value;
    }

    private static String outcome(CanonicalResultMessage message) {
        if (message.outcome() instanceof ShareOutcome.Solved solved) {
            return "gelöst in " + solved.attemptsUsed() + "/" + solved.maxAttempts();
        }
        return "nicht gelöst · X/" + message.outcome().maxAttempts();
    }


    private static String gameTitle(CanonicalResultMessage message) {
        return message.gameType() == de.venomenon.gridwordsbot.domain.model.GameType.GRIDWORDS
                ? "\uD83D\uDFE9 GridWords" : "\uD83D\uDFE6 QuadWords";
    }

    private static String boardText(CanonicalResultMessage message) {
        if (message.gameType() == de.venomenon.gridwordsbot.domain.model.GameType.GRIDWORDS) return message.board().canonicalText();
        var boards = message.quadWordsBoards().orElseThrow();
        return "Oben links\n" + boards.topLeft().canonicalText() + "\n\nOben rechts\n" + boards.topRight().canonicalText()
                + "\n\nUnten links\n" + boards.bottomLeft().canonicalText() + "\n\nUnten rechts\n" + boards.bottomRight().canonicalText();
    }

    private static String series(CanonicalResultMessage message) {
        StringBuilder series = new StringBuilder()
                .append("\uD83D\uDD25 Aktivität: ").append(days(message.streaks().personalActivity()))
                .append("\n\uD83D\uDFE9 GridWords gelöst: ")
                .append(daysOrNone(message.streaks().personalGridWordsSolved()));
        appendOptional(series, PERSONAL_COMPLETE, message.personalComplete());
        appendOptional(series, PERSONAL_PERFECT, message.personalPerfect());
        appendOptional(series, SHARED_COMPLETE, message.sharedComplete());
        appendOptional(series, SHARED_PERFECT, message.sharedPerfect());
        return series.toString();
    }

    private static void appendOptional(StringBuilder series, String label, java.util.OptionalInt streak) {
        if (streak.isPresent()) {
            series.append("\n").append(label).append(days(streak.getAsInt()));
        }
    }

    private static String days(int count) {
        return count + " " + (count == 1 ? "Tag" : "Tage");
    }

    private static String daysOrNone(int count) {
        return count == 0 ? "keine laufende Serie" : days(count);
    }
}
