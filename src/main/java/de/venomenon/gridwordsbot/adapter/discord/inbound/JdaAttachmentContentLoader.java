package de.venomenon.gridwordsbot.adapter.discord.inbound;

import de.venomenon.gridwordsbot.domain.parsing.AttachmentMetadata;
import de.venomenon.gridwordsbot.domain.parsing.AttachmentReference;
import de.venomenon.gridwordsbot.port.out.AttachmentContentLoader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

/** JDA implementation that resolves and downloads exactly the attachment selected in an inbound snapshot. */
public final class JdaAttachmentContentLoader implements AttachmentContentLoader {

    /** Central default for the in-memory image parser boundary: 8 MiB. */
    public static final long DEFAULT_MAX_ATTACHMENT_BYTES = 8L * 1024 * 1024;

    private final JDA jda;
    private final long maximumAttachmentBytes;

    public JdaAttachmentContentLoader(JDA jda) {
        this(jda, DEFAULT_MAX_ATTACHMENT_BYTES);
    }

    public JdaAttachmentContentLoader(JDA jda, long maximumAttachmentBytes) {
        this.jda = Objects.requireNonNull(jda, "jda");
        if (maximumAttachmentBytes <= 0) {
            throw new IllegalArgumentException("maximumAttachmentBytes must be positive");
        }
        this.maximumAttachmentBytes = maximumAttachmentBytes;
    }

    @Override
    public byte[] load(AttachmentMetadata attachmentMetadata) {
        Objects.requireNonNull(attachmentMetadata, "attachmentMetadata");
        if (attachmentMetadata.size() > maximumAttachmentBytes) {
            throw tooLarge();
        }
        AttachmentReference reference = attachmentMetadata.reference()
                .orElseThrow(() -> new AttachmentUnavailableException("attachment has no retrieval reference"));

        try {
            TextChannel channel = jda.getTextChannelById(reference.channelId());
            if (channel == null) {
                throw new AttachmentUnavailableException("attachment channel is unavailable");
            }
            Message sourceMessage = channel.retrieveMessageById(reference.messageId()).complete();
            Message.Attachment attachment = sourceMessage.getAttachments().stream()
                    .filter(candidate -> candidate.getIdLong() == reference.attachmentId())
                    .findFirst()
                    .orElseThrow(() -> new AttachmentUnavailableException("referenced attachment is unavailable"));
            if (attachment.getSize() > maximumAttachmentBytes) {
                throw tooLarge();
            }
            try (InputStream input = attachment.getProxy().download().get()) {
                return readWithinLimit(input);
            }
        } catch (AttachmentContentLoadException exception) {
            throw exception;
        } catch (ErrorResponseException exception) {
            throw translateDiscordFailure(exception);
        } catch (InsufficientPermissionException exception) {
            throw new AttachmentUnavailableException("attachment cannot be accessed", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RetryableAttachmentException("attachment download was interrupted", exception);
        } catch (ExecutionException exception) {
            throw translateAsyncFailure(exception);
        } catch (IOException exception) {
            throw new RetryableAttachmentException("attachment download failed", exception);
        } catch (RuntimeException exception) {
            throw new RetryableAttachmentException("attachment download failed", exception);
        }
    }

    private byte[] readWithinLimit(InputStream input) throws IOException {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            long total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maximumAttachmentBytes) {
                    throw tooLarge();
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private AttachmentTooLargeException tooLarge() {
        return new AttachmentTooLargeException("attachment exceeds the configured byte limit");
    }

    private static AttachmentContentLoadException translateAsyncFailure(ExecutionException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof AttachmentContentLoadException loadFailure) {
            return loadFailure;
        }
        if (cause instanceof ErrorResponseException discordFailure) {
            return translateDiscordFailure(discordFailure);
        }
        if (cause instanceof InsufficientPermissionException permissionFailure) {
            return new AttachmentUnavailableException("attachment cannot be accessed", permissionFailure);
        }
        return new RetryableAttachmentException("attachment download failed", cause == null ? exception : cause);
    }

    private static AttachmentContentLoadException translateDiscordFailure(ErrorResponseException exception) {
        return switch (exception.getErrorResponse()) {
            case UNKNOWN_CHANNEL, UNKNOWN_MESSAGE, MISSING_ACCESS, MISSING_PERMISSIONS ->
                    new AttachmentUnavailableException("attachment cannot be accessed", exception);
            default -> new RetryableAttachmentException("Discord attachment retrieval failed", exception);
        };
    }
}
