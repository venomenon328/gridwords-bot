package de.venomenon.gridwordsbot.adapter.discord.status;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.port.out.DiscordDeliveryException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** Complete, limit-checked status rendering for one Discord message with up to ten embeds. */
final class DailyStatusEmbedRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d. MMMM uuuu", Locale.GERMAN);
    private static final int MAX_EMBEDS = 10;
    private static final int MAX_FIELDS = 25;
    private static final int MAX_FIELD_NAME = 256;
    private static final int MAX_FIELD_VALUE = 1_024;
    private static final int MAX_TOTAL = 6_000;

    List<MessageEmbed> render(long channelId, DailyStatus status) {
        String title = statusTitle(status);
        List<MessageEmbed> embeds = new ArrayList<>();
        EmbedBuilder current = new EmbedBuilder().setTitle(title);
        int fields = 0;
        for (DailyStatus.PlayerLine player : status.players()) {
            if (fields == MAX_FIELDS) {
                embeds.add(current.build());
                if (embeds.size() == MAX_EMBEDS) {
                    throw permanent("daily status needs more than ten embeds");
                }
                current = new EmbedBuilder().setTitle(title + " · Fortsetzung");
                fields = 0;
            }
            String value = playerValue(player);
            requireLimit(player.displayName(), MAX_FIELD_NAME, "player name");
            requireLimit(value, MAX_FIELD_VALUE, "player status");
            current.addField(player.displayName(), value, false);
            fields++;
        }
        String shared = "GridWords gelöst: " + status.sharedGridWordsSolved()
                + " · QuadWords gelöst: " + status.sharedQuadWordsSolved()
                + "\nKomplett: " + status.sharedComplete()
                + " · Perfekt: " + status.sharedPerfect();
        if (fields == MAX_FIELDS) {
            embeds.add(current.build());
            if (embeds.size() == MAX_EMBEDS) {
                throw permanent("daily status needs more than ten embeds");
            }
            current = new EmbedBuilder().setTitle(title + " · Gemeinsam");
        }
        current.addField("Gemeinsame Serien", shared, false);
        embeds.add(current.build());
        int total = embeds.stream().mapToInt(MessageEmbed::getLength).sum();
        if (total > MAX_TOTAL) {
            throw permanent("daily status exceeds Discord's total embed character limit");
        }
        return List.copyOf(embeds);
    }

    static String statusTitle(DailyStatus status) {
        return "Wortspiele · " + status.gameDate().format(DATE);
    }

    private static String playerValue(DailyStatus.PlayerLine player) {
        return "GridWords: " + result(player.gridWordsState())
                + "\nQuadWords: " + result(player.quadWordsState())
                + "\n🔥 Aktivität: " + player.streaks().personalActivity()
                + " · Komplett: " + applicable(player, GameType.GRIDWORDS, GameType.QUADWORDS,
                        player.streaks().personalComplete())
                + "\nGridWords gelöst: " + applicable(player, GameType.GRIDWORDS,
                        player.streaks().personalGridWordsSolved())
                + " · QuadWords gelöst: " + applicable(player, GameType.QUADWORDS,
                        player.streaks().personalQuadWordsSolved())
                + " · Perfekt: " + applicable(player, GameType.GRIDWORDS, GameType.QUADWORDS,
                        player.streaks().personalPerfect());
    }

    private static String result(DailyStatus.GameState state) {
        if (!state.participating()) {
            return "— nimmt nicht teil";
        }
        if (state.result().isEmpty()) {
            return "⬜ noch nicht eingereicht";
        }
        ParsedGameResult value = state.result().get();
        String symbol = value.outcome() instanceof ShareOutcome.Solved ? "✅" : "❌";
        return symbol + " " + outcome(value.outcome()) + " · "
                + value.duration().toMinutes() + ":" + String.format("%02d", value.duration().toSecondsPart());
    }

    private static String applicable(DailyStatus.PlayerLine player, GameType gameType, int value) {
        return player.participates(gameType) ? String.valueOf(value) : "—";
    }

    private static String applicable(
            DailyStatus.PlayerLine player, GameType firstGame, GameType secondGame, int value) {
        return player.participates(firstGame) && player.participates(secondGame) ? String.valueOf(value) : "—";
    }

    private static String outcome(ShareOutcome outcome) {
        return outcome instanceof ShareOutcome.Solved solved
                ? solved.attemptsUsed() + "/" + solved.maxAttempts()
                : "X/" + outcome.maxAttempts();
    }

    private static void requireLimit(String value, int limit, String label) {
        if (value.length() > limit) {
            throw permanent(label + " exceeds Discord limit");
        }
    }

    private static DiscordDeliveryException permanent(String message) {
        return DiscordDeliveryException.permanent(message, null);
    }
}