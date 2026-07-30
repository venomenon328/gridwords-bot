package de.venomenon.gridwordsbot.adapter.discord.status;

import de.venomenon.gridwordsbot.port.out.DailyStatusMessageGateway;
import de.venomenon.gridwordsbot.port.out.DiscordDeliveryException;
import de.venomenon.gridwordsbot.port.out.ReminderCandidateStore;
import de.venomenon.gridwordsbot.port.out.ReminderMessageGateway;
import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

/** JDA-only edge adapter with stable delivery keys for crash reconciliation. */
public final class JdaDailyStatusMessageGateway implements DailyStatusMessageGateway, ReminderMessageGateway {
    private static final int PAGE_SIZE = 100;
    private final JDA jda;
    private final DailyStatusEmbedRenderer renderer = new DailyStatusEmbedRenderer();

    public JdaDailyStatusMessageGateway(JDA jda) {
        this.jda = jda;
    }

    @Override
    public long publishOrEdit(long channelId, Optional<Long> existing, DailyStatus status, boolean contentChanged) {
        try {
            TextChannel channel = channel(channelId);
            List<MessageEmbed> embeds = renderer.render(channelId, status);
            String key = DailyStatusEmbedRenderer.statusKey(channelId, status);
            Optional<Message> target = existing.flatMap(id -> retrieve(channel, id));
            if (target.isEmpty()) {
                target = findByKey(channel, key);
            }
            if (target.isPresent()) {
                if (contentChanged || existing.isEmpty() || existing.get() != target.get().getIdLong()) {
                    target.get().editMessageEmbeds(embeds).setAllowedMentions(Collections.emptyList()).complete();
                }
                return target.get().getIdLong();
            }
            return channel.sendMessageEmbeds(embeds).setAllowedMentions(Collections.emptyList()).complete().getIdLong();
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
        Set<Long> selected = candidates.stream().map(ReminderCandidateStore.ReminderCandidate::discordUserId)
                .collect(Collectors.toUnmodifiableSet());
        if (!allowed.equals(selected)) {
            throw new IllegalArgumentException("allowed users must exactly match reminder candidates");
        }
        String content = candidates.stream().map(candidate -> "<@" + candidate.discordUserId() + ">: "
                + candidate.missingGames().stream().map(game -> switch (game) {
                    case GRIDWORDS -> "GridWords";
                    case QUADWORDS -> "QuadWords";
                }).collect(Collectors.joining(", "))).collect(Collectors.joining("\n"));
        if (content.length() > Message.MAX_CONTENT_LENGTH) {
            throw DiscordDeliveryException.permanent("reminder exceeds Discord message limit", null);
        }
        String key = reminderKey(channelId, gameDate, stage);
        MessageEmbed marker = new EmbedBuilder()
                .setDescription("Erinnerung · " + gameDate + " · Stufe " + stage)
                .setFooter(key)
                .build();
        try {
            TextChannel channel = channel(channelId);
            Optional<Message> existing = findByKey(channel, key);
            if (existing.isPresent()) {
                return existing.get().getIdLong();
            }
            return channel.sendMessage(content).addEmbeds(marker)
                    .setAllowedMentions(List.of(Message.MentionType.USER))
                    .mentionUsers(allowed.stream().map(String::valueOf).toList())
                    .complete().getIdLong();
        } catch (DiscordDeliveryException exception) {
            throw exception;
        } catch (ErrorResponseException exception) {
            throw classified("reminder Discord request failed", exception);
        } catch (RuntimeException exception) {
            throw DiscordDeliveryException.retryable("reminder Discord request failed", exception);
        }
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

    private Optional<Message> findByKey(TextChannel channel, String key) {
        SelfUser self = jda.getSelfUser();
        MessageHistory history = channel.getHistory();
        java.util.ArrayList<Message> matches = new java.util.ArrayList<>();
        while (true) {
            List<Message> page = history.retrievePast(PAGE_SIZE).complete();
            page.stream()
                    .filter(message -> self.equals(message.getAuthor()))
                    .filter(message -> message.getEmbeds().stream()
                            .map(MessageEmbed::getFooter)
                            .filter(java.util.Objects::nonNull)
                            .map(MessageEmbed.Footer::getText)
                            .anyMatch(key::equals))
                    .forEach(matches::add);
            if (page.size() < PAGE_SIZE) {
                break;
            }
        }
        Optional<Message> canonical = matches.stream().min(java.util.Comparator.comparingLong(Message::getIdLong));
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