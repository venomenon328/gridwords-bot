package de.venomenon.gridwordsbot.adapter.discord.inbound;

import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.parsing.AttachmentMetadata;
import de.venomenon.gridwordsbot.domain.parsing.AttachmentReference;
import de.venomenon.gridwordsbot.port.in.InboundSharedMessage;
import de.venomenon.gridwordsbot.port.in.ProcessSharedResultUseCase;
import de.venomenon.gridwordsbot.port.in.ProcessingResult;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Thin JDA listener: filter and copy on the gateway thread, then process on a bounded executor. */
public final class DiscordInboundListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(DiscordInboundListener.class);
    private static final String ACCEPTED = "✅";
    private static final String REJECTED = "⚠️";

    private final GridwordsBotProperties properties;
    private final Clock clock;
    private final Executor executor;
    private final ProcessSharedResultUseCase useCase;
    private final DiscordReactionGateway reactions;

    public DiscordInboundListener(
            GridwordsBotProperties properties,
            Clock clock,
            Executor executor,
            ProcessSharedResultUseCase useCase,
            DiscordReactionGateway reactions) {
        this.properties = properties;
        this.clock = clock;
        this.executor = executor;
        this.useCase = useCase;
        this.reactions = reactions;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!isRelevant(event)) {
            return;
        }

        Message message = event.getMessage();
        InboundSharedMessage inbound = copy(event, message);
        try {
            executor.execute(() -> process(inbound, message));
        } catch (RejectedExecutionException exception) {
            log.warn("Inbound queue is full: guildId={}, channelId={}, messageId={}, authorId={}, step=enqueue",
                    inbound.guildId(), inbound.channelId(), inbound.messageId(), inbound.authorId());
        }
    }

    private boolean isRelevant(MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.isWebhookMessage()) {
            return false;
        }
        User author = event.getAuthor();
        if (author.isBot()) {
            return false;
        }
        if (event.getGuild().getIdLong() != properties.discord().guildId()
                || event.getChannel().getIdLong() != properties.discord().channelId()) {
            return false;
        }
        long authorId = author.getIdLong();
        return authorId == properties.players().first().userId()
                || authorId == properties.players().second().userId();
    }

    private InboundSharedMessage copy(MessageReceivedEvent event, Message message) {
        User author = event.getAuthor();
        Member member = event.getMember();
        String displayName = member == null ? author.getName() : member.getEffectiveName();
        List<AttachmentMetadata> attachments = message.getAttachments().stream()
                .map(attachment -> new AttachmentMetadata(
                        attachment.getFileName(),
                        attachment.getContentType(),
                        attachment.getSize(),
                        Optional.of(new AttachmentReference(
                                event.getChannel().getIdLong(), message.getIdLong(), attachment.getIdLong()))))
                .toList();
        return new InboundSharedMessage(
                event.getGuild().getIdLong(), event.getChannel().getIdLong(), message.getIdLong(), author.getIdLong(),
                displayName, message.getContentRaw(), attachments, clock.instant());
    }

    private void process(InboundSharedMessage inbound, Message message) {
        try {
            ProcessingResult result = useCase.process(inbound);
            if (result instanceof ProcessingResult.Rejected) {
                reactions.addReaction(message, REJECTED);
            }
        } catch (RuntimeException exception) {
            log.error("Inbound processing failed: guildId={}, channelId={}, messageId={}, authorId={}, step=process",
                    inbound.guildId(), inbound.channelId(), inbound.messageId(), inbound.authorId(), exception);
        }
    }
}
