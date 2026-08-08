package de.venomenon.gridwordsbot.adapter.discord.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.adapter.discord.status.PersonalStatusEmbedRenderer;
import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.port.in.PersonalStatusUseCase;
import de.venomenon.gridwordsbot.port.in.PlayerParticipationUseCase;
import java.time.ZoneId;
import java.util.List;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DiscordRecordsCommandRegistrationTest {
    @Test
    void centralGuildCommandUpdateIncludesRecordsAndAchievementCommandsExactlyOnce() {
        JDA jda = mock(JDA.class);
        Guild guild = mock(Guild.class);
        CommandListUpdateAction update = mock(CommandListUpdateAction.class);
        when(jda.getGuildById(11L)).thenReturn(guild);
        when(guild.updateCommands()).thenReturn(update);
        when(update.addCommands(any(CommandData.class), any(CommandData.class), any(CommandData.class),
                any(CommandData.class))).thenReturn(update);
        when(update.addCommands(any(CommandData.class))).thenReturn(update);

        new DiscordParticipationCommandListener(
                properties(), mock(PlayerParticipationUseCase.class), mock(PersonalStatusUseCase.class),
                new PersonalStatusEmbedRenderer(ZoneId.of("Europe/Berlin")))
                .registerCommands(jda);

        ArgumentCaptor<CommandData> additional = ArgumentCaptor.forClass(CommandData.class);
        verify(update, times(3)).addCommands(additional.capture());
        assertThat(additional.getAllValues()).extracting(CommandData::getName)
                .containsExactly("records", "achievements", "achievement-list");
        verify(update).queue();
    }

    private static GridwordsBotProperties properties() {
        return new GridwordsBotProperties(
                new GridwordsBotProperties.Discord(true, "token", 11L, 12L, List.of(101L)), null, null);
    }
}
