package de.venomenon.gridwordsbot.config;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.adapter.discord.inbound.DiscordInboundListener;
import de.venomenon.gridwordsbot.application.submission.ConfiguredPlayerSynchronizer;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.DefaultApplicationArguments;

class DatabaseInboundStartupTest {

    @Test
    void synchronizesOnceBeforeRegisteringTheInboundListener() throws Exception {
        ConfiguredPlayerSynchronizer synchronizer = mock(ConfiguredPlayerSynchronizer.class);
        JDA jda = mock(JDA.class);
        DiscordInboundListener listener = mock(DiscordInboundListener.class);
        ObjectProvider<JDA> jdaProvider = provider(jda);
        ObjectProvider<DiscordInboundListener> listenerProvider = provider(listener);

        new DatabaseInboundStartup(synchronizer, jdaProvider, listenerProvider)
                .run(new DefaultApplicationArguments());

        InOrder order = inOrder(synchronizer, jda);
        order.verify(synchronizer).synchronize();
        order.verify(jda).addEventListener(listener);
    }

    @Test
    void onlySynchronizesPlayersWhenDiscordInboundIsUnavailable() throws Exception {
        ConfiguredPlayerSynchronizer synchronizer = mock(ConfiguredPlayerSynchronizer.class);
        ObjectProvider<JDA> jdaProvider = provider(null);
        ObjectProvider<DiscordInboundListener> listenerProvider = provider(null);

        new DatabaseInboundStartup(synchronizer, jdaProvider, listenerProvider)
                .run(new DefaultApplicationArguments());

        verify(synchronizer).synchronize();
        verify(jdaProvider).getIfAvailable();
        verify(listenerProvider).getIfAvailable();
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
