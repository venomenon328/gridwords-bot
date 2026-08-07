package de.venomenon.gridwordsbot.adapter.discord.record;

import de.venomenon.gridwordsbot.application.record.RenderedRecordAnnouncementPage;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementMessageGateway;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
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
    private static final Pattern PAGE_MARKER = Pattern.compile(
            "(record-announcement:[0-9a-f]{64})\\|page:([1-9]\\d*)/([1-9]\\d*)");
    private static final Pattern HIDDEN_DESCRIPTION_MARKER = Pattern.compile(
            "\\[\\u2063\\]\\(https://gridwords.invalid/record/([0-9a-f]{64})/([1-9]\\d*)/([1-9]\\d*)\\)$");
    private static final String NONCE_PREFIX = "ra:";
    private static final int NONCE_HASH_BYTES = 11;
    private static final String INVISIBLE_LINK_LABEL = "\u2063";
    private final JDA jda;

    public JdaRecordAnnouncementMessageGateway(JDA jda) { this.jda = Objects.requireNonNull(jda, "jda"); }

    @Override
    public long create(long channelId, RenderedRecordAnnouncementPage page) {
        try {
            return channel(channelId).sendMessageEmbeds(embed(page))
                    .setNonce(publicationNonce(publicationKey(page), page.position()))
                    .setAllowedMentions(List.of()).complete().getIdLong();
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
            String noncePrefix = publicationNoncePrefix(publicationKey);
            while (true) {
                List<Message> batch = history.retrievePast(100).complete();
                for (Message message : batch) {
                    if (!message.getAuthor().equals(jda.getSelfUser())) continue;
                    Matcher hidden = hiddenDescriptionMarker(message).orElse(null);
                    if (hidden != null && ("record-announcement:" + hidden.group(1)).equals(publicationKey)) {
                        pages.add(new PublishedPage(message.getIdLong(), Integer.parseInt(hidden.group(2)) - 1));
                        continue;
                    }
                    Integer noncePosition = noncePosition(message.getNonce(), noncePrefix);
                    if (noncePosition != null) {
                        pages.add(new PublishedPage(message.getIdLong(), noncePosition));
                        continue;
                    }
                    legacyFooter(message)
                            .filter(value -> value.group(1).equals(publicationKey))
                            .ifPresent(match -> pages.add(
                                    new PublishedPage(message.getIdLong(), Integer.parseInt(match.group(2)) - 1)));
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
        return new EmbedBuilder().setTitle(page.title()).setDescription(page.description() + hiddenMarker(page)).build();
    }

    private static String hiddenMarker(RenderedRecordAnnouncementPage page) {
        Matcher marker = pageMarker(page);
        String hash = marker.group(1).substring("record-announcement:".length());
        return "[" + INVISIBLE_LINK_LABEL + "](https://gridwords.invalid/record/" + hash + "/"
                + marker.group(2) + "/" + marker.group(3) + ")";
    }

    private static Matcher pageMarker(RenderedRecordAnnouncementPage page) {
        Matcher matcher = PAGE_MARKER.matcher(page.footer());
        if (!matcher.matches() || Integer.parseInt(matcher.group(2)) - 1 != page.position()) {
            throw new IllegalArgumentException("record announcement page marker is invalid");
        }
        return matcher;
    }

    private static String publicationKey(RenderedRecordAnnouncementPage page) {
        return pageMarker(page).group(1);
    }

    static String publicationNonce(String publicationKey, int position) {
        if (position < 0) throw new IllegalArgumentException("position must not be negative");
        return publicationNoncePrefix(publicationKey) + Integer.toString(position, 36);
    }

    private static String publicationNoncePrefix(String publicationKey) {
        Objects.requireNonNull(publicationKey, "publicationKey");
        byte[] digest = sha256(publicationKey);
        byte[] shortened = new byte[NONCE_HASH_BYTES];
        System.arraycopy(digest, 0, shortened, 0, shortened.length);
        return NONCE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(shortened) + ":";
    }

    private static Integer noncePosition(String nonce, String expectedPrefix) {
        if (nonce == null || !nonce.startsWith(expectedPrefix) || nonce.length() == expectedPrefix.length()) return null;
        try {
            int position = Integer.parseInt(nonce.substring(expectedPrefix.length()), 36);
            return position >= 0 ? position : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static java.util.Optional<Matcher> hiddenDescriptionMarker(Message message) {
        if (message.getEmbeds().size() != 1) return java.util.Optional.empty();
        String description = message.getEmbeds().getFirst().getDescription();
        if (description == null) return java.util.Optional.empty();
        Matcher matcher = HIDDEN_DESCRIPTION_MARKER.matcher(description);
        return matcher.find() ? java.util.Optional.of(matcher) : java.util.Optional.empty();
    }

    private static java.util.Optional<Matcher> legacyFooter(Message message) {
        if (message.getEmbeds().size() != 1) return java.util.Optional.empty();
        String text = java.util.Optional.ofNullable(message.getEmbeds().getFirst().getFooter())
                .map(MessageEmbed.Footer::getText).orElse(null);
        if (text == null) return java.util.Optional.empty();
        Matcher matcher = PAGE_MARKER.matcher(text);
        return matcher.matches() ? java.util.Optional.of(matcher) : java.util.Optional.empty();
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static MessageGatewayException classified(String message, ErrorResponse response, Throwable cause) {
        return switch (response) {
            case MISSING_ACCESS, MISSING_PERMISSIONS, UNKNOWN_CHANNEL -> new PermanentMessageException(message, cause);
            default -> new RetryableMessageException(message, cause);
        };
    }
}
