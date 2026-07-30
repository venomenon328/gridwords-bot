package de.venomenon.gridwordsbot.adapter.discord.status;

import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.port.out.DailyStatusMessageGateway;
import de.venomenon.gridwordsbot.port.out.ReminderCandidateStore;
import de.venomenon.gridwordsbot.port.out.ReminderMessageGateway;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

/** JDA-only edge adapter. Statuses never permit mentions; reminders permit only their selected user IDs. */
public final class JdaDailyStatusMessageGateway implements DailyStatusMessageGateway, ReminderMessageGateway {
    private final JDA jda;
    public JdaDailyStatusMessageGateway(JDA jda) { this.jda = jda; }
    @Override public long publishOrEdit(long channelId, Optional<Long> existing, DailyStatus status) {
        String content = renderStatus(status);
        if (content.length() > 2_000) throw new IllegalStateException("daily status exceeds Discord message limit");
        TextChannel channel = channel(channelId);
        if (existing.isPresent()) try {
            Message message = channel.retrieveMessageById(existing.get()).complete();
            message.editMessage(content).setAllowedMentions(List.of()).complete();
            return existing.get();
        } catch (ErrorResponseException error) {
            if (error.getErrorResponse() != ErrorResponse.UNKNOWN_MESSAGE) throw error;
        }
        return channel.sendMessage(content).setAllowedMentions(List.of()).complete().getIdLong();
    }
    @Override public long send(long channelId, List<ReminderCandidateStore.ReminderCandidate> candidates, Set<Long> allowed) {
        if (!allowed.equals(candidates.stream().map(ReminderCandidateStore.ReminderCandidate::discordUserId).collect(java.util.stream.Collectors.toSet())))
            throw new IllegalArgumentException("allowed users must exactly match reminder candidates");
        String content = candidates.stream().map(candidate -> "<@" + candidate.discordUserId() + ">: "
                + candidate.missingGames().stream().map(game -> game == de.venomenon.gridwordsbot.domain.model.GameType.GRIDWORDS ? "GridWords" : "QuadWords").collect(java.util.stream.Collectors.joining(", "))).collect(java.util.stream.Collectors.joining("\n"));
        if (content.length() > 2_000) throw new IllegalStateException("reminder exceeds Discord message limit");
        return channel(channelId).sendMessage(content).setAllowedMentions(List.of(Message.MentionType.USER)).mentionUsers(allowed.stream().map(String::valueOf).toList()).complete().getIdLong();
    }
    private static String renderStatus(DailyStatus status) {
        StringBuilder output = new StringBuilder("Wortspiele Â· ").append(status.gameDate().format(DateTimeFormatter.ofPattern("d. MMMM uuuu", java.util.Locale.GERMAN)));
        for (DailyStatus.PlayerLine player : status.players()) output.append("\n\n").append(player.displayName()).append("\nGridWords: ").append(result(player.gridWords())).append(" Â· QuadWords: ").append(result(player.quadWords()))
                .append("\nðŸ”¥ AktivitÃ¤t: ").append(player.streaks().personalActivity()).append(" Â· Komplett: ").append(player.streaks().personalComplete())
                .append(" Â· GridWords gelÃ¶st: ").append(player.streaks().personalGridWordsSolved()).append(" Â· QuadWords gelÃ¶st: ").append(player.streaks().personalQuadWordsSolved()).append(" Â· Perfekt: ").append(player.streaks().personalPerfect());
        return output.append("\n\nGemeinsam Â· Komplett: ").append(status.sharedComplete()).append(" Â· Perfekt: ").append(status.sharedPerfect()).toString();
    }
    private static String result(Optional<ParsedGameResult> result) { if (result.isEmpty()) return "â¬œ"; ParsedGameResult value = result.get(); return (value.outcome() instanceof ShareOutcome.Solved ? "âœ… " : "âŒ ") + outcome(value.outcome()) + " Â· " + value.duration().toMinutesPart() + ":" + String.format("%02d", value.duration().toSecondsPart()); }
    private static String outcome(ShareOutcome outcome) { return outcome instanceof ShareOutcome.Solved solved ? solved.attemptsUsed() + "/" + solved.maxAttempts() : "X/" + outcome.maxAttempts(); }
    private TextChannel channel(long id) { TextChannel channel = jda.getTextChannelById(id); if (channel == null) throw new IllegalStateException("configured text channel is unavailable"); return channel; }
}
