package de.venomenon.gridwordsbot.adapter.discord.status;

import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.in.DailyResultDetailsUseCase;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** Bounded, safe rendering of read-only details; no raw shares or delivery state is exposed. */
public final class DailyResultDetailsEmbedRenderer {
    public MessageEmbed render(DailyResultDetailsUseCase.Result detail) {
        if (detail instanceof DailyResultDetailsUseCase.Missing missing) return new EmbedBuilder().setTitle(title(missing.gameType(), missing.gameDate()))
                .setDescription(missing.playerDisplayName() + "\n\nFür diesen Spieltag liegt kein Ergebnis vor.").build();
        if (!(detail instanceof DailyResultDetailsUseCase.Found found)) return new EmbedBuilder().setTitle("Ergebnisdetails")
                .setDescription("Diese Auswahl ist nicht mehr gültig. Bitte nutze die aktuelle Tagesnachricht.").build();
        ParsedGameResult result = found.result(); String outcome = result.outcome() instanceof ShareOutcome.Solved solved
                ? "gelöst in " + solved.attemptsUsed() + "/" + solved.maxAttempts() : "nicht gelöst · X/" + result.outcome().maxAttempts();
        String description = found.playerDisplayName() + " · " + outcome + " · " + String.format("%d:%02d", result.duration().toMinutes(), result.duration().toSecondsPart());
        if (result.gameType() == de.venomenon.gridwordsbot.domain.model.GameType.GRIDWORDS) description += "\n\n```\n" + result.board().orElseThrow().canonicalText() + "\n```";
        else if (result.quadWordsBoards().isPresent()) description += "\n\n```\n" + boards(result) + "\n```";
        else description += "\n\nFür dieses Ergebnis ist kein Board gespeichert.";
        if (description.length() > MessageEmbed.DESCRIPTION_MAX_LENGTH) throw new IllegalArgumentException("result detail exceeds Discord embed limit");
        return new EmbedBuilder().setTitle(title(result.gameType(), result.gameDate())).setDescription(description).build();
    }
    private static String title(de.venomenon.gridwordsbot.domain.model.GameType type, java.time.LocalDate date) { return (type == de.venomenon.gridwordsbot.domain.model.GameType.GRIDWORDS ? "GridWords" : "QuadWords") + " · " + date.format(DateTimeFormatter.ofPattern("d. MMMM uuuu", Locale.GERMAN)); }
    private static String boards(ParsedGameResult result) { var boards = result.quadWordsBoards().orElseThrow(); return pair(boards.topLeft(), boards.topRight()) + "\n\n" + pair(boards.bottomLeft(), boards.bottomRight()); }
    private static String pair(QuadWordsBoard left, QuadWordsBoard right) { List<String> lines = new ArrayList<>(); int height = Math.max(left.rows().size(), right.rows().size()); for(int i=0;i<height;i++) lines.add((i<left.rows().size()?left.rows().get(i):"⬛⬛⬛⬛⬛") + "  " + (i<right.rows().size()?right.rows().get(i):"⬛⬛⬛⬛⬛")); return String.join("\n", lines); }
}