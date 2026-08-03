package de.venomenon.gridwordsbot.adapter.discord.inbound;

import de.venomenon.gridwordsbot.adapter.discord.status.DailyResultComponentCodec;
import de.venomenon.gridwordsbot.adapter.discord.status.DailyResultDetailsEmbedRenderer;
import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.port.in.DailyResultDetailsUseCase;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;

/** Thin interaction edge: defer immediately and move all durable reads to the bounded worker. */
public final class DailyResultDetailsInteractionListener extends ListenerAdapter {
    private static final String PREFIX = "daily-result:";
    private static final String INVALID_SELECTION = "Diese Auswahl ist ungültig oder veraltet.";
    private static final String BUSY = "Der Bot ist gerade ausgelastet. Bitte versuche es erneut.";
    private static final String FAILURE = "Die Ergebnisdetails konnten nicht geladen werden. Bitte versuche es erneut.";

    private final GridwordsBotProperties properties;
    private final Executor executor;
    private final DailyResultDetailsUseCase details;
    private final DailyResultComponentCodec codec = new DailyResultComponentCodec();
    private final DailyResultDetailsEmbedRenderer renderer = new DailyResultDetailsEmbedRenderer();

    public DailyResultDetailsInteractionListener(
            GridwordsBotProperties properties,
            Executor executor,
            DailyResultDetailsUseCase details) {
        this.properties = properties;
        this.executor = executor;
        this.details = details;
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().startsWith(PREFIX)) {
            return;
        }
        if (!event.isFromGuild()
                || event.getGuild().getIdLong() != properties.discord().guildId()
                || event.getChannel().getIdLong() != properties.discord().channelId()) {
            return;
        }

        event.deferReply(true).queue(hook -> {
            try {
                executor.execute(() -> reply(event, hook));
            } catch (RejectedExecutionException exception) {
                hook.editOriginal(BUSY).queue();
            }
        });
    }

    private void reply(StringSelectInteractionEvent event, InteractionHook hook) {
        try {
            Optional<DailyResultComponentCodec.Component> component = codec.decode(event.getComponentId());
            Optional<Long> target = event.getValues().size() == 1
                    ? codec.target(event.getValues().getFirst())
                    : Optional.empty();
            if (component.isEmpty() || target.isEmpty()) {
                hook.editOriginal(INVALID_SELECTION).queue();
                return;
            }

            DailyResultComponentCodec.Component decoded = component.get();
            DailyResultDetailsUseCase.Result result = details.get(new DailyResultDetailsUseCase.Request(
                    event.getGuild().getIdLong(),
                    event.getChannel().getIdLong(),
                    event.getMessageIdLong(),
                    decoded.gameDate(),
                    decoded.gameType(),
                    decoded.pageIndex(),
                    target.get()));
            hook.editOriginalEmbeds(renderer.render(result)).queue();
        } catch (RuntimeException exception) {
            hook.editOriginal(FAILURE).queue();
        }
    }
}
