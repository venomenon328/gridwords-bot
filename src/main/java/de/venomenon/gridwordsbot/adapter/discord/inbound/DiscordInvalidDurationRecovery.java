package de.venomenon.gridwordsbot.adapter.discord.inbound;

import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.domain.parsing.AttachmentMetadata;
import de.venomenon.gridwordsbot.domain.parsing.AttachmentReference;
import de.venomenon.gridwordsbot.port.in.InboundSharedMessage;
import de.venomenon.gridwordsbot.port.in.InvalidDurationRecoveryUseCase;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongFunction;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Reloads narrowly selected source messages after startup and hands them to the silent repair use case. */
@Component
@Profile("database")
@ConditionalOnProperty(prefix = "gridwords.discord", name = "enabled", havingValue = "true")
final class DiscordInvalidDurationRecovery {

    private static final Logger LOGGER = LoggerFactory.getLogger(DiscordInvalidDurationRecovery.class);

    private final long guildId;
    private final long channelId;
    private final InvalidDurationRecoveryUseCase recovery;
    private final LongFunction<InboundSharedMessage> sourceLoader;

    @Autowired
    DiscordInvalidDurationRecovery(
            JDA jda,
            GridwordsBotProperties properties,
            Clock clock,
            InvalidDurationRecoveryUseCase recovery) {
        this(
                properties.discord().guildId(),
                properties.discord().channelId(),
                recovery,
                sourceMessageId -> load(jda, properties.discord().channelId(), sourceMessageId, clock));
    }

    DiscordInvalidDurationRecovery(
            long guildId,
            long channelId,
            InvalidDurationRecoveryUseCase recovery,
            LongFunction<InboundSharedMessage> sourceLoader) {
        if (guildId <= 0 || channelId <= 0) {
            throw new IllegalArgumentException("Discord IDs must be positive");
        }
        this.guildId = guildId;
        this.channelId = channelId;
        this.recovery = Objects.requireNonNull(recovery);
        this.sourceLoader = Objects.requireNonNull(sourceLoader);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterStartup() {
        int recovered = recover();
        if (recovered > 0) {
            LOGGER.info("Recovered {} previously rejected invalid-duration submission(s) after startup", recovered);
        }
    }

    int recover() {
        List<Long> candidates;
        try {
            candidates = recovery.findCandidates(guildId, channelId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Parser recovery discovery failed; startup continues and recovery remains restartable", exception);
            return 0;
        }

        int recovered = 0;
        for (long sourceMessageId : candidates) {
            try {
                InboundSharedMessage message = Objects.requireNonNull(sourceLoader.apply(sourceMessageId));
                if (message.guildId() != guildId || message.channelId() != channelId
                        || message.messageId() != sourceMessageId) {
                    LOGGER.warn("Skipping parser recovery with mismatching Discord identity: sourceMessageId={}",
                            sourceMessageId);
                    continue;
                }
                if (recovery.recover(message)) {
                    recovered++;
                }
            } catch (RuntimeException exception) {
                LOGGER.warn("Parser recovery failed and remains restartable: sourceMessageId={}",
                        sourceMessageId, exception);
            }
        }
        return recovered;
    }

    private static InboundSharedMessage load(JDA jda, long channelId, long sourceMessageId, Clock clock) {
        Objects.requireNonNull(jda);
        Objects.requireNonNull(clock);
        var channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            throw new IllegalStateException("configured Discord channel is unavailable");
        }
        Message message = channel.retrieveMessageById(sourceMessageId).complete();
        User author = message.getAuthor();
        Member member = message.getMember();
        String displayName = member == null ? author.getName() : member.getEffectiveName();
        List<AttachmentMetadata> attachments = message.getAttachments().stream()
                .map(attachment -> new AttachmentMetadata(
                        attachment.getFileName(),
                        attachment.getContentType(),
                        attachment.getSize(),
                        Optional.of(new AttachmentReference(
                                channelId, sourceMessageId, attachment.getIdLong()))))
                .toList();
        return new InboundSharedMessage(
                message.getGuild().getIdLong(), channelId, sourceMessageId, author.getIdLong(),
                displayName, message.getContentRaw(), attachments, clock.instant());
    }
}
