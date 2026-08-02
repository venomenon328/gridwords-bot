package de.venomenon.gridwordsbot.adapter.discord.canonical;

import de.venomenon.gridwordsbot.application.canonical.CanonicalResultMessage;
import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import de.venomenon.gridwordsbot.port.out.DiscordDeliveryException;
import de.venomenon.gridwordsbot.port.out.CanonicalPublicationContextStore;
import java.util.List;
import java.util.OptionalInt;
import java.util.OptionalLong;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

/** JDA adapter; all calls are made by the application worker, never in a database transaction. */
public final class JdaCanonicalMessageGateway implements CanonicalMessageGateway {

    private final JDA jda;
    private final CanonicalPublicationContextStore publicationContextStore;
    private final CanonicalEmbedRenderer renderer = new CanonicalEmbedRenderer();

    public JdaCanonicalMessageGateway(JDA jda) {
        this(jda, CanonicalPublicationContextStore.none());
    }

    public JdaCanonicalMessageGateway(JDA jda, CanonicalPublicationContextStore publicationContextStore) {
        this.jda = jda;
        this.publicationContextStore = publicationContextStore;
    }

    @Override
    public long create(long channelId, CanonicalResultMessage message) {
        return channel(channelId)
                .sendMessageEmbeds(renderer.render(withHistoricalContext(message)))
                .setAllowedMentions(List.of())
                .complete()
                .getIdLong();
    }

    @Override
    public void edit(long channelId, long messageId, CanonicalResultMessage message) {
        try {
            Message original = channel(channelId).retrieveMessageById(messageId).complete();
            MessageEmbed existingEmbed = original.getEmbeds().stream().findFirst().orElse(null);
            original.editMessageEmbeds(renderer.renderForEdit(withHistoricalContext(message), existingEmbed))
                    .setAllowedMentions(List.of())
                    .complete();
        } catch (ErrorResponseException exception) {
            if (isUnknownMessage(exception)) {
                throw new UnknownMessageException();
            }
            throw exception;
        }
    }

    @Override
    public OptionalLong findByPublicationKey(long channelId, String publicationKey) {
        return findAllByPublicationKey(channelId, publicationKey).stream().mapToLong(Long::longValue).findFirst();
    }

    @Override
    public List<Long> findAllByPublicationKey(long channelId, String publicationKey) {
        MessageHistory history = channel(channelId).getHistory();
        List<Long> matching = new java.util.ArrayList<>();
        while (true) {
            List<Message> page = history.retrievePast(100).complete();
            page.stream()
                    .filter(message -> message.getAuthor().equals(jda.getSelfUser()))
                    .filter(message -> message.getEmbeds().stream().anyMatch(embed -> embed.getFooter() != null
                            && DiscordPublicationKey.matches(publicationKey, embed.getFooter().getText())))
                    .mapToLong(Message::getIdLong)
                    .forEach(matching::add);
            if (page.size() < 100) {
                return List.copyOf(matching);
            }
        }
    }

    @Override
    public void delete(long channelId, long messageId) {
        try {
            channel(channelId).deleteMessageById(messageId).complete();
        } catch (ErrorResponseException exception) {
            if (isUnknownMessage(exception)) {
                return;
            }
            throw classifiedDeletion(exception);
        } catch (RuntimeException exception) {
            throw DiscordDeliveryException.retryable("canonical deletion failed", exception);
        }
    }

    static boolean isUnknownMessage(ErrorResponseException exception) {
        return exception.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE;
    }


    private static DiscordDeliveryException classifiedDeletion(ErrorResponseException exception) {
        ErrorResponse response = exception.getErrorResponse();
        boolean permanent = response == ErrorResponse.MISSING_ACCESS
                || response == ErrorResponse.MISSING_PERMISSIONS
                || response == ErrorResponse.UNKNOWN_CHANNEL;
        return permanent
                ? DiscordDeliveryException.permanent("canonical deletion failed", exception)
                : DiscordDeliveryException.retryable("canonical deletion failed", exception);
    }
    private CanonicalResultMessage withHistoricalContext(CanonicalResultMessage message) {
        OptionalLong resultId = resultId(message.publicationKey());
        if (resultId.isEmpty()) {
            return message;
        }
        CanonicalPublicationContextStore.HistoricalContext history =
                publicationContextStore.findForResult(resultId.getAsLong());
        return new CanonicalResultMessage(
                message.playerDisplayName(),
                message.gameType(),
                message.gameDate(),
                message.outcome(),
                message.duration(),
                message.board(),
                message.streaks(),
                contextual(message.personalComplete(), history.personalCompleteEstablished(),
                        message.streaks().personalComplete()),
                contextual(message.personalPerfect(), history.personalPerfectEstablished(),
                        message.streaks().personalPerfect()),
                contextual(message.sharedComplete(), history.sharedCompleteEstablished(),
                        message.streaks().sharedComplete()),
                contextual(message.sharedPerfect(), history.sharedPerfectEstablished(),
                        message.streaks().sharedPerfect()),
                message.quadWordsBoards(),
                message.publicationKey());
    }

    private static OptionalInt contextual(OptionalInt current, boolean historicallyEstablished, int streak) {
        if (current.isPresent()) {
            return current;
        }
        return historicallyEstablished && streak > 0 ? OptionalInt.of(streak) : OptionalInt.empty();
    }

    private static OptionalLong resultId(String publicationKey) {
        int separator = publicationKey.lastIndexOf('-');
        if (separator < 0 || separator == publicationKey.length() - 1) {
            return OptionalLong.empty();
        }
        try {
            long resultId = Long.parseLong(publicationKey.substring(separator + 1));
            return resultId > 0 ? OptionalLong.of(resultId) : OptionalLong.empty();
        } catch (NumberFormatException ignored) {
            return OptionalLong.empty();
        }
    }

    private TextChannel channel(long channelId) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            throw new IllegalStateException("configured text channel is unavailable");
        }
        return channel;
    }
}
