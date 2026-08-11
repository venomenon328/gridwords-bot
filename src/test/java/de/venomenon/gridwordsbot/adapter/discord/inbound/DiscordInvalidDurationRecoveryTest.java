package de.venomenon.gridwordsbot.adapter.discord.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.port.in.InboundSharedMessage;
import de.venomenon.gridwordsbot.port.in.InvalidDurationRecoveryUseCase;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongFunction;
import org.junit.jupiter.api.Test;

class DiscordInvalidDurationRecoveryTest {

    @Test
    void reloadsRecoverableMessagesAndContinuesAfterOneSourceFailure() {
        InvalidDurationRecoveryUseCase useCase = mock(InvalidDurationRecoveryUseCase.class);
        InboundSharedMessage first = message(101L);
        InboundSharedMessage second = message(102L);
        when(useCase.findCandidates(11L, 12L)).thenReturn(List.of(101L, 102L, 103L));
        when(useCase.recover(first)).thenReturn(true);
        when(useCase.recover(second)).thenReturn(false);
        LongFunction<InboundSharedMessage> loader = id -> switch ((int) id) {
            case 101 -> first;
            case 102 -> second;
            case 103 -> throw new IllegalStateException("source unavailable");
            default -> throw new AssertionError("unexpected source id " + id);
        };
        DiscordInvalidDurationRecovery recovery = new DiscordInvalidDurationRecovery(
                11L, 12L, useCase, loader);

        assertThat(recovery.recover()).isEqualTo(1);
        verify(useCase).recover(first);
        verify(useCase).recover(second);
    }

    @Test
    void refusesSourceMessageWithMismatchingIdentity() {
        InvalidDurationRecoveryUseCase useCase = mock(InvalidDurationRecoveryUseCase.class);
        when(useCase.findCandidates(11L, 12L)).thenReturn(List.of(201L));
        InboundSharedMessage wrongChannel = new InboundSharedMessage(
                11L, 13L, 201L, 101L, "Player", "content", List.of(), Instant.EPOCH);
        DiscordInvalidDurationRecovery recovery = new DiscordInvalidDurationRecovery(
                11L, 12L, useCase, ignored -> wrongChannel);

        assertThat(recovery.recover()).isZero();
        verify(useCase, never()).recover(wrongChannel);
    }

    @Test
    void discoveryFailureDoesNotPreventApplicationReadiness() {
        InvalidDurationRecoveryUseCase useCase = mock(InvalidDurationRecoveryUseCase.class);
        when(useCase.findCandidates(11L, 12L)).thenThrow(new IllegalStateException("database unavailable"));
        AtomicBoolean sourceLoaded = new AtomicBoolean();
        DiscordInvalidDurationRecovery recovery = new DiscordInvalidDurationRecovery(
                11L, 12L, useCase, ignored -> {
                    sourceLoaded.set(true);
                    return message(301L);
                });

        assertThat(recovery.recover()).isZero();
        assertThat(sourceLoaded).isFalse();
    }

    private static InboundSharedMessage message(long messageId) {
        return new InboundSharedMessage(
                11L, 12L, messageId, 101L, "Player", "content", List.of(), Instant.EPOCH);
    }
}
