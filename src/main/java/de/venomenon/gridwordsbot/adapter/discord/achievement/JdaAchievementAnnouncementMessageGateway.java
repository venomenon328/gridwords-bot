package de.venomenon.gridwordsbot.adapter.discord.achievement;

import de.venomenon.gridwordsbot.application.achievement.RenderedAchievementAnnouncement;
import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementMessageGateway;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

/** JDA-only, mention-safe Send-before-ACK gateway for Achievement announcements. */
public final class JdaAchievementAnnouncementMessageGateway implements AchievementAnnouncementMessageGateway {
    private static final String NONCE_PREFIX = "aa:";
    private static final Pattern MARKER = Pattern.compile("achievement-announcement:[0-9a-f]{64}");
    private final JDA jda;

    public JdaAchievementAnnouncementMessageGateway(JDA jda) { this.jda = Objects.requireNonNull(jda, "jda"); }

    @Override
    public long create(long channelId, RenderedAchievementAnnouncement announcement) {
        try {
            return channel(channelId).sendMessageEmbeds(embeds(announcement))
                    .setNonce(nonce(announcement.publicationKey())).setAllowedMentions(List.of()).complete().getIdLong();
        } catch (ErrorResponseException exception) {
            throw classified("achievement announcement creation failed", exception.getErrorResponse(), exception);
        } catch (MessageGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetryableMessageException("achievement announcement creation failed", exception);
        }
    }

    @Override
    public boolean exists(long channelId, long messageId) {
        try {
            channel(channelId).retrieveMessageById(messageId).complete();
            return true;
        } catch (ErrorResponseException exception) {
            if (exception.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) return false;
            throw classified("achievement announcement existence check failed", exception.getErrorResponse(), exception);
        } catch (MessageGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetryableMessageException("achievement announcement existence check failed", exception);
        }
    }

    @Override
    public List<Long> discoverCreatedMessages(
            long channelId, String publicationKey, RenderedAchievementAnnouncement expected) {
        Objects.requireNonNull(publicationKey, "publicationKey");
        Objects.requireNonNull(expected, "expected");
        try {
            MessageHistory history = channel(channelId).getHistory();
            List<Long> matches = new ArrayList<>();
            String expectedNonce = nonce(publicationKey);
            while (true) {
                List<Message> batch = history.retrievePast(100).complete();
                for (Message message : batch) {
                    if (!message.getAuthor().equals(jda.getSelfUser())) continue;
                    if (expectedNonce.equals(message.getNonce())) matches.add(message.getIdLong());
                }
                if (batch.size() < 100) {
                    matches.sort(Comparator.naturalOrder());
                    return List.copyOf(matches);
                }
            }
        } catch (ErrorResponseException exception) {
            throw classified("achievement announcement create discovery failed", exception.getErrorResponse(), exception);
        } catch (MessageGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetryableMessageException("achievement announcement create discovery failed", exception);
        }
    }

    @Override
    public void delete(long channelId, long messageId) {
        try {
            channel(channelId).deleteMessageById(messageId).complete();
        } catch (ErrorResponseException exception) {
            if (exception.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) return;
            throw classified("achievement announcement duplicate deletion failed", exception.getErrorResponse(), exception);
        } catch (MessageGatewayException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new RetryableMessageException("achievement announcement duplicate deletion failed", exception);
        }
    }

    static String nonce(String publicationKey) {
        if (!MARKER.matcher(publicationKey).matches()) throw new IllegalArgumentException("invalid publication key");
        byte[] digest = sha256(publicationKey);
        byte[] shortened = java.util.Arrays.copyOf(digest, 11);
        return NONCE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(shortened);
    }

    private static List<MessageEmbed> embeds(RenderedAchievementAnnouncement announcement) {
        return announcement.embeds().stream()
                .map(embed -> new EmbedBuilder().setTitle(embed.title()).setDescription(embed.description()).build())
                .toList();
    }

    private TextChannel channel(long channelId) {
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) throw new PermanentMessageException("configured achievement announcement channel is unavailable", null);
        return channel;
    }

    private static byte[] sha256(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }

    private static MessageGatewayException classified(String operation, ErrorResponse response, Throwable cause) {
        String safe = operation + " (discord_error=" + response.name() + ")";
        return switch (response) {
            case MISSING_ACCESS, MISSING_PERMISSIONS, UNKNOWN_CHANNEL -> new PermanentMessageException(safe, cause);
            default -> new RetryableMessageException(safe, cause);
        };
    }
}
