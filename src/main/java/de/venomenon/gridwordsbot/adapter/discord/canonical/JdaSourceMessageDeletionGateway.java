package de.venomenon.gridwordsbot.adapter.discord.canonical;

import de.venomenon.gridwordsbot.port.out.SourceMessageDeletionGateway;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.exceptions.MissingAccessException;
import net.dv8tion.jda.api.requests.ErrorResponse;

/** JDA implementation for the isolated source-message deletion boundary. */
public final class JdaSourceMessageDeletionGateway implements SourceMessageDeletionGateway {

    private final JDA jda;

    public JdaSourceMessageDeletionGateway(JDA jda) {
        this.jda = jda;
    }

    @Override
    public DeletionResult deleteSourceMessage(long channelId, long sourceMessageId) {
        var channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            return DeletionResult.PERMANENT_FAILURE;
        }
        try {
            channel.deleteMessageById(sourceMessageId).complete();
            return DeletionResult.DELETED;
        } catch (MissingAccessException exception) {
            return DeletionResult.PERMANENT_FAILURE;
        } catch (InsufficientPermissionException exception) {
            return DeletionResult.PERMANENT_FAILURE;
        } catch (ErrorResponseException exception) {
            return classify(exception);
        } catch (RuntimeException exception) {
            return DeletionResult.RETRYABLE_FAILURE;
        }
    }

    private static DeletionResult classify(ErrorResponseException exception) {
        return switch (exception.getErrorResponse()) {
            case UNKNOWN_MESSAGE -> DeletionResult.ALREADY_MISSING;
            case MISSING_ACCESS, MISSING_PERMISSIONS, UNKNOWN_CHANNEL -> DeletionResult.PERMANENT_FAILURE;
            default -> DeletionResult.RETRYABLE_FAILURE;
        };
    }
}
