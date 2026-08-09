package de.venomenon.gridwordsbot.adapter.discord.status;

import de.venomenon.gridwordsbot.adapter.discord.common.QuadWordsBoardFormatter;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.in.DailyResultDetailsUseCase;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** Bounded, safe rendering of read-only details; no raw shares or delivery state is exposed. */
public final class DailyResultDetailsEmbedRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d. MMMM uuuu", Locale.GERMAN);

    public MessageEmbed render(DailyResultDetailsUseCase.Result detail) {
        if (detail instanceof DailyResultDetailsUseCase.Missing missing) {
            return embed(
                    title(missing.gameType(), missing.gameDate()),
                    missing.playerDisplayName() + "\n\nFür diesen Spieltag liegt kein Ergebnis vor.");
        }
        if (!(detail instanceof DailyResultDetailsUseCase.Found found)) {
            return embed(
                    "Ergebnisdetails",
                    "Diese Auswahl ist nicht mehr gültig. Bitte nutze die aktuelle Tagesnachricht.");
        }

        ParsedGameResult result = found.result();
        StringBuilder description = new StringBuilder()
                .append(found.playerDisplayName())
                .append(" · ")
                .append(outcome(result.outcome()))
                .append(" · ")
                .append(String.format("%d:%02d", result.duration().toMinutes(), result.duration().toSecondsPart()));
        if (result.gameType() == GameType.GRIDWORDS) {
            description.append("\n\n").append(codeBlock(result.board().orElseThrow().canonicalText()));
        } else if (result.quadWordsBoards().isPresent()) {
            description.append("\n\n")
                    .append(codeBlock(QuadWordsBoardFormatter.format(result.quadWordsBoards().orElseThrow())));
        } else {
            description.append("\n\nFür dieses Ergebnis ist kein Board gespeichert.");
        }
        found.selectedExcuse().ifPresent(excuse -> description.append("\n\n> ").append(excuse));
        if (!found.currentRecords().isEmpty()) {
            description.append("\n\n🏆 **Aktuelle Rekorde**");
            found.currentRecords().forEach(record -> description.append("\n• ").append(record.scope())
                    .append(" · ").append(record.metric()));
        }
        if (!found.achievements().isEmpty()) {
            description.append("\n\n🏅 **An diesem Spieltag freigeschaltet**");
            found.achievements().forEach(achievement -> description.append("\n• ").append(achievement.emoji())
                    .append(" ").append(achievement.displayName()));
        }
        return embed(title(result.gameType(), result.gameDate()), description.toString());
    }

    private static MessageEmbed embed(String title, String description) {
        if (title.length() > MessageEmbed.TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("result detail title exceeds Discord embed limit");
        }
        if (description.length() > MessageEmbed.DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("result detail exceeds Discord embed limit");
        }
        return new EmbedBuilder().setTitle(title).setDescription(description).build();
    }

    private static String outcome(ShareOutcome outcome) {
        return outcome instanceof ShareOutcome.Solved solved
                ? "gelöst in " + solved.attemptsUsed() + "/" + solved.maxAttempts()
                : "nicht gelöst · X/" + outcome.maxAttempts();
    }

    private static String title(GameType type, LocalDate date) {
        return (type == GameType.GRIDWORDS ? "GridWords" : "QuadWords") + " · " + date.format(DATE);
    }

    private static String codeBlock(String content) {
        return "```\n" + content + "\n```";
    }
}
