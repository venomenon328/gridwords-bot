package de.venomenon.gridwordsbot.adapter.discord.status;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.port.out.DailyStatusMessageGateway;
import de.venomenon.gridwordsbot.port.out.DiscordDeliveryException;
import de.venomenon.gridwordsbot.port.out.ReminderCandidateStore;
import de.venomenon.gridwordsbot.port.out.ReminderMessageGateway;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;

/** JDA-only edge adapter with stable, non-visible delivery keys for crash reconciliation. */
public final class JdaDailyStatusMessageGateway implements DailyStatusMessageGateway, ReminderMessageGateway {
    private static final int PAGE_SIZE = 100;
    private static final String GRIDWORDS_URL = "https://gridgames.app/gridwords";
    private static final String QUADWORDS_URL = "https://gridgames.app/quadwords";

    private final JDA jda;
    private final DailyStatusEmbedRenderer renderer = new DailyStatusEmbedRenderer();

    public JdaDailyStatusMessageGateway(JDA jda) {
        this.jda = jda;
    }

    @Override
    public long publishOrEdit(long channelId, Optional<Long> existing, DailyStatus status, boolean contentChanged) {
        try {
            TextChannel channel = channel(channelId);
            var embeds = renderer.render(channelId, status);
            Optional<Message> target = existing.flatMap(id -> retrieve(channel, id));
            if (target.isEmpty()) {
                target = findStatusByTitle(channel, DailyStatusEmbedRenderer.statusTitle(status));
            }
            if (target.isPresent()) {
                if (contentChanged || existing.isEmpty() || existing.get() != target.get().getIdLong()) {
                    target.get().editMessageEmbeds(embeds).setAllowedMentions(Collections.emptyList()).complete();
                }
                return target.get().getIdLong();
            }
            return channel.sendMessageEmbeds(embeds)
                    .setAllowedMentions(Collections.emptyList())
                    .complete()
                    .getIdLong();
        } catch (DiscordDeliveryException exception) {
            throw exception;
        } catch (ErrorResponseException exception) {
            throw classified("daily status Discord request failed", exception);
        } catch (RuntimeException exception) {
            throw DiscordDeliveryException.retryable("daily status Discord request failed", exception);
        }
    }

    @Override
    public long send(long channelId, LocalDate gameDate, int stage,
            List<ReminderCandidateStore.ReminderCandidate> candidates, Set<Long> allowed) {
        Set<Long> selectedMentions = candidates.stream()
                .filter(ReminderCandidateStore.ReminderCandidate::reminderOptIn)
                .map(ReminderCandidateStore.ReminderCandidate::discordUserId)
                .collect(Collectors.toUnmodifiableSet());
        if (!allowed.equals(selectedMentions)) {
            throw new IllegalArgumentException("allowed users must exactly match reminder opt-ins");
        }

        String key = reminderKey(channelId, gameDate, stage);
        String content = reminderContent(candidates, key);
        if (content.length() > Message.MAX_CONTENT_LENGTH) {
            throw DiscordDeliveryException.permanent("reminder exceeds Discord message limit", null);
        }

        try {
            TextChannel channel = channel(channelId);
            Optional<Message> existing = findReminderByKey(channel, key);
            if (existing.isPresent()) {
                suppressEmbeds(existing.get());
                return existing.get().getIdLong();
            }

            MessageCreateAction create = channel.sendMessage(content);
            if (allowed.isEmpty()) {
                create.setAllowedMentions(Collections.emptyList());
            } else {
                create.setAllowedMentions(List.of(Message.MentionType.USER))
                        .mentionUsers(allowed.stream().map(String::valueOf).toList());
            }
            Message sent = create.complete();
            suppressEmbeds(sent);
            return sent.getIdLong();
        } catch (DiscordDeliveryException exception) {
            throw exception;
        } catch (ErrorResponseException exception) {
            throw classified("reminder Discord request failed", exception);
        } catch (RuntimeException exception) {
            throw DiscordDeliveryException.retryable("reminder Discord request failed", exception);
        }
    }

    @Override
    public void delete(long channelId, long messageId) {
        try {
            channel(channelId).deleteMessageById(messageId).complete();
        } catch (ErrorResponseException exception) {
            if (exception.getErrorResponse() != ErrorResponse.UNKNOWN_MESSAGE) {
                throw classified("reminder deletion failed", exception);
            }
        } catch (RuntimeException exception) {
            throw DiscordDeliveryException.retryable("reminder deletion failed", exception);
        }
    }

    private static void suppressEmbeds(Message message) {
        var action = message.suppressEmbeds(true);
        if (action != null) action.complete();
    }

    private static String reminderContent(
            List<ReminderCandidateStore.ReminderCandidate> candidates,
            String key) {
        List<String> lines = new ArrayList<>();
        lines.add("**Denkt bitte noch an eure Wortspiele:**");
        addGameLine(lines, "GridWords", GRIDWORDS_URL, GameType.GRIDWORDS, candidates, key);
        addGameLine(lines, "QuadWords", QUADWORDS_URL, GameType.QUADWORDS, candidates, key);
        return String.join("\n", lines);
    }

    private static void addGameLine(
            List<String> lines,
            String label,
            String url,
            GameType gameType,
            List<ReminderCandidateStore.ReminderCandidate> candidates,
            String key) {
        String players = candidates.stream()
                .filter(candidate -> candidate.missingGames().contains(gameType))
                .map(JdaDailyStatusMessageGateway::renderPlayer)
                .collect(Collectors.joining(", "));
        if (!players.isEmpty()) {
            // The fragment is not rendered by Discord but keeps stage-specific crash reconciliation possible without
            // exposing an implementation key as message text or an embed footer.
            lines.add("**[" + label + "](" + url + "#" + key + ")**: " + players);
        }
    }

    private static String renderPlayer(ReminderCandidateStore.ReminderCandidate candidate) {
        return candidate.reminderOptIn()
                ? "<@" + candidate.discordUserId() + ">"
                : plainDisplayName(candidate.displayName());
    }

    private static String plainDisplayName(String displayName) {
        return displayName
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("~", "\\~")
                .replace("`", "\\`")
                .replace("<", "‹")
                .replace(">", "›")
                .replace("@", "@\u200B");
    }

    static String reminderKey(long channelId, LocalDate date, int stage) {
        return "gridwords-reminder:" + channelId + ":" + date + ":" + stage;
    }

    private Optional<Message> retrieve(TextChannel channel, long messageId) {
        try {
            return Optional.of(channel.retrieveMessageById(messageId).complete());
        } catch (ErrorResponseException exception) {
            if (exception.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private Optional<Message> findStatusByTitle(TextChannel channel, String title) {
        return findAndDeduplicate(channel, message -> message.getEmbeds().stream()
                .map(embed -> embed.getTitle())
                .anyMatch(title::equals));
    }

    private Optional<Message> findReminderByKey(TextChannel channel, String key) {
        String marker = "#" + key;
        return findAndDeduplicate(channel, message -> Optional.ofNullable(message.getContentRaw())
                .orElse("")
                .contains(marker));
    }

    private Optional<Message> findAndDeduplicate(TextChannel channel, Predicate<Message> matchesDelivery) {
        SelfUser self = jda.getSelfUser();
        MessageHistory history = channel.getHistory();
        ArrayList<Message> matches = new ArrayList<>();
        while (true) {
            List<Message> page = history.retrievePast(PAGE_SIZE).complete();
            page.stream()
                    .filter(message -> self.equals(message.getAuthor()))
                    .filter(matchesDelivery)
                    .forEach(matches::add);
            if (page.size() < PAGE_SIZE) {
                break;
            }
        }
        Optional<Message> canonical = matches.stream()
                .min(java.util.Comparator.comparingLong(Message::getIdLong));
        if (canonical.isPresent()) {
            for (Message duplicate : matches) {
                if (duplicate.getIdLong() != canonical.get().getIdLong()) {
                    duplicate.delete().complete();
                }
            }
        }
        return canonical;
    }

    private TextChannel channel(long id) {
        TextChannel channel = jda.getTextChannelById(id);
        if (channel == null) {
            throw DiscordDeliveryException.permanent("configured text channel is unavailable", null);
        }
        return channel;
    }

    private static DiscordDeliveryException classified(String message, ErrorResponseException exception) {
        ErrorResponse response = exception.getErrorResponse();
        boolean permanent = response == ErrorResponse.MISSING_ACCESS
                || response == ErrorResponse.MISSING_PERMISSIONS
                || response == ErrorResponse.UNKNOWN_CHANNEL;
        return permanent
                ? DiscordDeliveryException.permanent(message, exception)
                : DiscordDeliveryException.retryable(message, exception);
    }
}
