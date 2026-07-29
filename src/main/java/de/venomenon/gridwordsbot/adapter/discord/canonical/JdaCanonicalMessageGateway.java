package de.venomenon.gridwordsbot.adapter.discord.canonical;

import de.venomenon.gridwordsbot.application.canonical.CanonicalResultMessage;
import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import java.util.List;
import java.util.OptionalLong;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

/** JDA adapter; all calls are made by the application worker, never in a database transaction. */
public final class JdaCanonicalMessageGateway implements CanonicalMessageGateway {

    private final JDA jda;
    private final CanonicalEmbedRenderer renderer = new CanonicalEmbedRenderer();

    public JdaCanonicalMessageGateway(JDA jda) {
        this.jda = jda;
    }

    @Override
    public long create(long channelId, CanonicalResultMessage message) {
        return channel(channelId)
                .sendMessageEmbeds(renderer.render(message))
                .setAllowedMentions(List.of())
                .complete()
                .getIdLong();
    }

    @Override
    public void edit(long channelId, long messageId, CanonicalResultMessage message) {
        try {
            channel(channelId)
                    .retrieveMessageById(messageId)
                    .complete()
                    .editMessageEmbeds(renderer.render(message))
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
            throw exception;
        }
    }

    static boolean isUnknownMessage(ErrorResponseException exception) {
        return exception.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE;
    }

    private TextChannel channel(long channelId) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            throw new IllegalStateException("configured text channel is unavailable");
        }
        return channel;
    }
}
