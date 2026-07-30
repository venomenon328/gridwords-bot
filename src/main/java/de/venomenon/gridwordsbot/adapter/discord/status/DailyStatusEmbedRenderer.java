package de.venomenon.gridwordsbot.adapter.discord.status;

import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.port.out.DiscordDeliveryException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
        String title = "Wortspiele · " + status.gameDate().format(DATE);
        String key = statusKey(channelId, status);
        List<MessageEmbed> embeds = new ArrayList<>();
        EmbedBuilder current = new EmbedBuilder().setTitle(title).setFooter(key);
        int fields = 0;
        for (DailyStatus.PlayerLine player : status.players()) {
            if (fields == MAX_FIELDS) {
                embeds.add(current.build());
                if (embeds.size() == MAX_EMBEDS) {
                    throw permanent("daily status needs more than ten embeds");
                }
                current = new EmbedBuilder().setTitle(title + " · Fortsetzung").setFooter(key);
                fields = 0;
            }
            String value = playerValue(player);
            requireLimit(player.displayName(), MAX_FIELD_NAME, "player name");
            requireLimit(value, MAX_FIELD_VALUE, "player status");
            current.addField(player.displayName(), value, false);
            fields++;
        }
        String shared = "Gemeinsam komplett: " + status.sharedComplete()
                + " · Gemeinsam perfekt: " + status.sharedPerfect();
        if (fields == MAX_FIELDS) {
            embeds.add(current.build());
            if (embeds.size() == MAX_EMBEDS) {
                throw permanent("daily status needs more than ten embeds");
            }
            current = new EmbedBuilder().setTitle(title + " · Gemeinsam").setFooter(key);
        }
        current.addField("Gemeinsame Serien", shared, false);
        embeds.add(current.build());
        int total = embeds.stream().mapToInt(MessageEmbed::getLength).sum();
        if (total > MAX_TOTAL) {
            throw permanent("daily status exceeds Discord's total embed character limit");
        }
        return List.copyOf(embeds);
    }

    static String statusKey(long channelId, DailyStatus status) {
        return "gridwords-daily-status:" + channelId + ":" + status.gameDate();
    }

    private static String playerValue(DailyStatus.PlayerLine player) {
        return "GridWords: " + result(player.gridWords())
                + "\nQuadWords: " + result(player.quadWords())
                + "\n🔥 Aktivität: " + player.streaks().personalActivity()
                + " · Komplett: " + player.streaks().personalComplete()
                + "\nGridWords gelöst: " + player.streaks().personalGridWordsSolved()
                + " · QuadWords gelöst: " + player.streaks().personalQuadWordsSolved()
                + " · Perfekt: " + player.streaks().personalPerfect();
    }

    private static String result(Optional<ParsedGameResult> result) {
        if (result.isEmpty()) {
            return "⬜ noch nicht eingereicht";
        }
        ParsedGameResult value = result.get();
        String symbol = value.outcome() instanceof ShareOutcome.Solved ? "✅" : "❌";
        return symbol + " " + outcome(value.outcome()) + " · "
                + value.duration().toMinutes() + ":" + String.format("%02d", value.duration().toSecondsPart());
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