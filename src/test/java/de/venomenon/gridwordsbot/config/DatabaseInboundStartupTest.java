package de.venomenon.gridwordsbot.config;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordInboundListener;
import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordParticipationCommandListener;
import de.venomenon.gridwordsbot.application.canonical.CanonicalGridWordsPublicationService;
import de.venomenon.gridwordsbot.application.canonical.GridWordsSourceDeletionService;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.DefaultApplicationArguments;

class DatabaseInboundStartupTest {

    @Test
    void resumesDurableWorkBeforeRegisteringInboundAndCommandListeners() throws Exception {
        CanonicalGridWordsPublicationService canonical = mock(CanonicalGridWordsPublicationService.class);
        GridWordsSourceDeletionService deletion = mock(GridWordsSourceDeletionService.class);
        JDA jda = mock(JDA.class);
        DiscordInboundListener inbound = mock(DiscordInboundListener.class);
        DiscordParticipationCommandListener commands = mock(DiscordParticipationCommandListener.class);

        new DatabaseInboundStartup(
                provider(jda), provider(inbound), provider(commands), provider(canonical), provider(deletion))
                .run(new DefaultApplicationArguments());

        InOrder order = inOrder(canonical, deletion, jda, commands);
        order.verify(canonical).resumeOpenPublications();
        order.verify(deletion).resumeOpenDeletions();
        order.verify(jda).addEventListener(inbound);
        order.verify(jda).addEventListener(commands);
        order.verify(commands).registerCommands(jda);
    }

    @Test
    void remainsOfflineWhenDiscordIsDisabledButStillRunsRecovery() throws Exception {
        CanonicalGridWordsPublicationService canonical = mock(CanonicalGridWordsPublicationService.class);
        GridWordsSourceDeletionService deletion = mock(GridWordsSourceDeletionService.class);
        ObjectProvider<JDA> jda = provider(null);
        ObjectProvider<DiscordInboundListener> inbound = provider(null);
        ObjectProvider<DiscordParticipationCommandListener> commands = provider(null);

        new DatabaseInboundStartup(jda, inbound, commands, provider(canonical), provider(deletion))
                .run(new DefaultApplicationArguments());

        verify(canonical).resumeOpenPublications();
        verify(deletion).resumeOpenDeletions();
        verify(jda).getIfAvailable();
        verify(inbound).getIfAvailable();
        verify(commands).getIfAvailable();
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
