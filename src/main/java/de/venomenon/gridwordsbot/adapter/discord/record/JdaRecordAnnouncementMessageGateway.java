package de.venomenon.gridwordsbot.adapter.discord.record;

import de.venomenon.gridwordsbot.application.record.RenderedRecordAnnouncementPage;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementMessageGateway;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

/** JDA-only adapter for record-announcement pages. */
public final class JdaRecordAnnouncementMessageGateway implements RecordAnnouncementMessageGateway {
    private static final Pattern FOOTER = Pattern.compile("(record-announcement:[0-9a-f]{64})\\|page:([1-9]\\d*)/([1-9]\\d*)");
    private final JDA jda;

    public JdaRecordAnnouncementMessageGateway(JDA jda) { this.jda = Objects.requireNonNull(jda, "jda"); }

    @Override
    public long create(long channelId, RenderedRecordAnnouncementPage page) {
        try {
            return channel(channelId).sendMessageEmbeds(embed(page)).setAllowedMentions(List.of()).complete().getIdLong();
        } catch (ErrorResponseException exception) {
            throw classified("record announcement creation failed", exception.getErrorResponse(), exception);
        } catch (MessageGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetryableMessageException("record announcement creation failed", exception);
        }
    }

    @Override
    public void edit(long channelId, long messageId, RenderedRecordAnnouncementPage page) {
        try {
            channel(channelId).retrieveMessageById(messageId).complete().editMessageEmbeds(embed(page))
                    .setAllowedMentions(List.of()).complete();
        } catch (ErrorResponseException exception) {
            if (exception.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                throw new MissingMessageException("record announcement message is missing");
            }
            throw classified("record announcement edit failed", exception.getErrorResponse(), exception);
        } catch (MessageGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetryableMessageException("record announcement edit failed", exception);
        }
    }

    @Override
    public void delete(long channelId, long messageId) {
        try {
            channel(channelId).deleteMessageById(messageId).complete();
        } catch (ErrorResponseException exception) {
            if (exception.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) return;
            throw classified("record announcement deletion failed", exception.getErrorResponse(), exception);
        } catch (MessageGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetryableMessageException("record announcement deletion failed", exception);
        }
    }

    @Override
    public List<PublishedPage> findByPublicationKey(long channelId, String publicationKey) {
        try {
            MessageHistory history = channel(channelId).getHistory();
            List<PublishedPage> pages = new ArrayList<>();
            while (true) {
                List<Message> batch = history.retrievePast(100).complete();
                for (Message message : batch) {
                    if (!message.getAuthor().equals(jda.getSelfUser())) continue;
                    footer(message).filter(value -> value.group(1).equals(publicationKey)).ifPresent(match ->
                            pages.add(new PublishedPage(message.getIdLong(), Integer.parseInt(match.group(2)) - 1)));
                }
                if (batch.size() < 100) {
                    pages.sort(Comparator.comparingInt(PublishedPage::position).thenComparingLong(PublishedPage::messageId));
                    return List.copyOf(pages);
                }
            }
        } catch (ErrorResponseException exception) {
            throw classified("record announcement search failed", exception.getErrorResponse(), exception);
        } catch (MessageGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetryableMessageException("record announcement search failed", exception);
        }
    }

    private TextChannel channel(long channelId) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) throw new PermanentMessageException("configured record announcement channel is unavailable", null);
        return channel;
    }

    private static MessageEmbed embed(RenderedRecordAnnouncementPage page) {
        return new EmbedBuilder().setTitle(page.title()).setDescription(page.description()).setFooter(page.footer()).build();
    }

    private static java.util.Optional<Matcher> footer(Message message) {
        if (message.getEmbeds().size() != 1) return java.util.Optional.empty();
        String text = java.util.Optional.ofNullable(message.getEmbeds().getFirst().getFooter())
                .map(MessageEmbed.Footer::getText).orElse(null);
        if (text == null) return java.util.Optional.empty();
        Matcher matcher = FOOTER.matcher(text);
        return matcher.matches() ? java.util.Optional.of(matcher) : java.util.Optional.empty();
    }

    private static MessageGatewayException classified(String message, ErrorResponse response, Throwable cause) {
        return switch (response) {
            case MISSING_ACCESS, MISSING_PERMISSIONS, UNKNOWN_CHANNEL -> new PermanentMessageException(message, cause);
            default -> new RetryableMessageException(message, cause);
        };
    }
}
