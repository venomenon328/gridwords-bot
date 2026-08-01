package de.venomenon.gridwordsbot.adapter.discord.reporting;

import de.venomenon.gridwordsbot.domain.reporting.RenderedReportField;
import de.venomenon.gridwordsbot.domain.reporting.RenderedReportPage;
import de.venomenon.gridwordsbot.port.out.PeriodicReportMessageGateway;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

/** JDA adapter for the already-rendered, transport-neutral pages of a periodic report. */
public final class JdaPeriodicReportMessageGateway implements PeriodicReportMessageGateway {
    private static final int HISTORY_PAGE_SIZE = 100;
    private static final Pattern PAGE_FOOTER = Pattern.compile("Seite ([1-9]\\d*)/([1-9]\\d*)");

    private final JDA jda;

    public JdaPeriodicReportMessageGateway(JDA jda) {
        this.jda = Objects.requireNonNull(jda, "jda");
    }

    @Override
    public long create(long channelId, ReportPage page) {
        Objects.requireNonNull(page, "page");
        try {
            return channel(channelId)
                    .sendMessageEmbeds(embed(page.renderedPage()))
                    .setAllowedMentions(List.of())
                    .complete()
                    .getIdLong();
        } catch (ErrorResponseException exception) {
            throw creationFailure(exception.getErrorResponse(), exception);
        } catch (MessageGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetryableMessageException("periodic report creation failed", exception);
        }
    }

    @Override
    public void edit(long channelId, long messageId, ReportPage page) {
        Objects.requireNonNull(page, "page");
        try {
            channel(channelId)
                    .retrieveMessageById(messageId)
                    .complete()
                    .editMessageEmbeds(embed(page.renderedPage()))
                    .setAllowedMentions(List.of())
                    .complete();
        } catch (ErrorResponseException exception) {
            throw knownMessageFailure("periodic report edit failed", exception.getErrorResponse(), exception);
        } catch (MessageGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetryableMessageException("periodic report edit failed", exception);
        }
    }

    @Override
    public PublishedReportPage load(long channelId, long messageId) {
        try {
            Message message = channel(channelId).retrieveMessageById(messageId).complete();
            if (!isOwnMessage(message)) {
                throw new PermanentMessageException("periodic report message is not owned by this bot", null);
            }
            RenderedReportPage renderedPage = renderedPage(message);
            return new PublishedReportPage(message.getIdLong(), new ReportPage(pageIndex(message), renderedPage));
        } catch (ErrorResponseException exception) {
            throw knownMessageFailure("periodic report read failed", exception.getErrorResponse(), exception);
        } catch (MessageGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetryableMessageException("periodic report read failed", exception);
        }
    }

    @Override
    public List<PublishedReportPage> findExactMatches(long channelId, ReportPage page) {
        Objects.requireNonNull(page, "page");
        try {
            TextChannel channel = channel(channelId);
            MessageHistory history = channel.getHistory();
            List<PublishedReportPage> matches = new ArrayList<>();
            while (true) {
                List<Message> messages = history.retrievePast(HISTORY_PAGE_SIZE).complete();
                for (Message message : messages) {
                    if (isOwnMessage(message) && hasExactRenderedPage(message, page.renderedPage())) {
                        matches.add(new PublishedReportPage(message.getIdLong(), page));
                    }
                }
                if (messages.size() < HISTORY_PAGE_SIZE) {
                    matches.sort(Comparator.comparingLong(PublishedReportPage::messageId));
                    return List.copyOf(matches);
                }
            }
        } catch (ErrorResponseException exception) {
            throw classified("periodic report search failed", exception.getErrorResponse(), exception);
        } catch (MessageGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetryableMessageException("periodic report search failed", exception);
        }
    }

    @Override
    public void delete(long channelId, long messageId) {
        try {
            channel(channelId).deleteMessageById(messageId).complete();
        } catch (ErrorResponseException exception) {
            throw knownMessageFailure("periodic report deletion failed", exception.getErrorResponse(), exception);
        } catch (MessageGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetryableMessageException("periodic report deletion failed", exception);
        }
    }

    private TextChannel channel(long channelId) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            throw new PermanentMessageException("configured periodic report channel is unavailable", null);
        }
        return channel;
    }

    private boolean isOwnMessage(Message message) {
        User author = message.getAuthor();
        return author != null && author.equals(jda.getSelfUser());
    }

    private static MessageEmbed embed(RenderedReportPage page) {
        EmbedBuilder builder = new EmbedBuilder().setTitle(page.title());
        for (RenderedReportField field : page.fields()) {
            builder.addField(field.name(), field.value(), false);
        }
        page.footer().ifPresent(builder::setFooter);
        return builder.build();
    }

    private static boolean hasExactRenderedPage(Message message, RenderedReportPage expected) {
        try {
            return renderedPage(message).equals(expected);
        } catch (PermanentMessageException ignored) {
            return false;
        }
    }

    private static RenderedReportPage renderedPage(Message message) {
        if (message.getEmbeds().size() != 1) {
            throw new PermanentMessageException("periodic report message has no single report embed", null);
        }
        MessageEmbed embed = message.getEmbeds().getFirst();
        String title = embed.getTitle();
        if (title == null) {
            throw new PermanentMessageException("periodic report message has no report title", null);
        }
        List<RenderedReportField> fields = embed.getFields().stream()
                .map(field -> new RenderedReportField(field.getName(), field.getValue()))
                .toList();
        Optional<String> footer = Optional.ofNullable(embed.getFooter()).map(MessageEmbed.Footer::getText);
        return new RenderedReportPage(title, fields, footer);
    }

    private static int pageIndex(Message message) {
        Optional<String> footer = Optional.ofNullable(message.getEmbeds().getFirst().getFooter())
                .map(MessageEmbed.Footer::getText);
        if (footer.isEmpty()) {
            return 0;
        }
        Matcher matcher = PAGE_FOOTER.matcher(footer.get());
        if (!matcher.matches()) {
            throw new PermanentMessageException("periodic report page footer has no visible order", null);
        }
        int page = Integer.parseInt(matcher.group(1));
        int total = Integer.parseInt(matcher.group(2));
        if (page > total) {
            throw new PermanentMessageException("periodic report page footer has invalid visible order", null);
        }
        return page - 1;
    }

    static MessageGatewayException creationFailure(ErrorResponse response, Throwable cause) {
        if (response == ErrorResponse.UNKNOWN_MESSAGE) {
            return new UnknownMessageException("periodic report creation outcome is unknown", cause);
        }
        return classified("periodic report creation failed", response, cause);
    }

    static MessageGatewayException knownMessageFailure(String message, ErrorResponse response, Throwable cause) {
        if (response == ErrorResponse.UNKNOWN_MESSAGE) {
            return new MissingMessageException("periodic report message is missing");
        }
        return classified(message, response, cause);
    }

    static MessageGatewayException classified(String message, ErrorResponse response, Throwable cause) {
        return switch (response) {
            case MISSING_ACCESS, MISSING_PERMISSIONS, UNKNOWN_CHANNEL -> new PermanentMessageException(message, cause);
            default -> new RetryableMessageException(message, cause);
        };
    }
}
