package de.venomenon.gridwordsbot.adapter.discord.inbound;

import de.venomenon.gridwordsbot.adapter.discord.status.DailyResultComponentCodec;
import de.venomenon.gridwordsbot.adapter.discord.status.DailyResultDetailsEmbedRenderer;
import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.port.in.DailyResultDetailsUseCase;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/** Thin interaction edge: defer immediately and move all durable reads to the bounded worker. */
public final class DailyResultDetailsInteractionListener extends ListenerAdapter {
    private final GridwordsBotProperties properties; private final Executor executor; private final DailyResultDetailsUseCase details;
    private final DailyResultComponentCodec codec = new DailyResultComponentCodec(); private final DailyResultDetailsEmbedRenderer renderer = new DailyResultDetailsEmbedRenderer();
    public DailyResultDetailsInteractionListener(GridwordsBotProperties properties, Executor executor, DailyResultDetailsUseCase details) { this.properties=properties; this.executor=executor; this.details=details; }
    @Override public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().startsWith("daily-result:")) return;
        if (!event.isFromGuild() || event.getGuild().getIdLong()!=properties.discord().guildId() || event.getChannel().getIdLong()!=properties.discord().channelId()) return;
        event.deferReply(true).queue(hook -> { try { executor.execute(() -> reply(event, hook)); } catch (RejectedExecutionException exception) { hook.editOriginal("Der Bot ist gerade ausgelastet. Bitte versuche es erneut.").queue(); } });
    }
    private void reply(StringSelectInteractionEvent event, net.dv8tion.jda.api.interactions.InteractionHook hook) {
        try { var component=codec.decode(event.getComponentId()); var target=event.getValues().size()==1 ? codec.target(event.getValues().getFirst()) : java.util.Optional.<Long>empty(); if(component.isEmpty()||target.isEmpty()) { hook.editOriginal("Diese Auswahl ist ungültig oder veraltet.").queue(); return; }
            var c=component.get(); var result=details.get(new DailyResultDetailsUseCase.Request(event.getGuild().getIdLong(), event.getChannel().getIdLong(), event.getMessageIdLong(), c.gameDate(), c.gameType(), c.pageIndex(), target.get())); hook.editOriginalEmbeds(renderer.render(result)).queue();
        } catch (RuntimeException exception) { hook.editOriginal("Die Ergebnisdetails konnten nicht geladen werden. Bitte versuche es erneut.").queue(); }
    }
}