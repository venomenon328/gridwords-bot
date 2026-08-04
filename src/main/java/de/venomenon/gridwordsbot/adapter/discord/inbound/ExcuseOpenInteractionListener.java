package de.venomenon.gridwordsbot.adapter.discord.inbound;

import de.venomenon.gridwordsbot.adapter.discord.canonical.ExcuseComponentCodec;
import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.port.in.ExcuseOpenUseCase;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;

/** Thin JDA edge for the canonical excuse button; persistence and authorization stay in the use case. */
public final class ExcuseOpenInteractionListener extends ListenerAdapter {

    private static final String BUSY = "Der Bot ist gerade ausgelastet. Bitte versuche es erneut.";
    private static final String UNAVAILABLE = "Diese Ausrede ist nicht verfügbar oder nicht mehr aktuell.";
    private static final String FORBIDDEN = "Diesen Button kann nur der Ergebnisautor verwenden.";
    private static final String FAILURE = "Die Ausreden konnten nicht geladen werden. Bitte versuche es erneut.";

    private final GridwordsBotProperties properties;
    private final Executor executor;
    private final ExcuseOpenUseCase open;
    private final ExcuseComponentCodec codec = new ExcuseComponentCodec();
    private final ExcuseOpenEmbedRenderer renderer = new ExcuseOpenEmbedRenderer();

    public ExcuseOpenInteractionListener(
            GridwordsBotProperties properties,
            Executor executor,
            ExcuseOpenUseCase open) {
        this.properties = properties;
        this.executor = executor;
        this.open = open;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        ExcuseComponentCodec.Open component = codec.decodeOpen(event.getComponentId()).orElse(null);
        if (component == null) {
            return;
        }
        if (!event.isFromGuild()
                || event.getGuild().getIdLong() != properties.discord().guildId()
                || event.getChannel().getIdLong() != properties.discord().channelId()) {
            return;
        }
        event.deferReply(true).queue(hook -> {
            try {
                executor.execute(() -> reply(event, hook, component));
            } catch (RejectedExecutionException exception) {
                hook.editOriginal(BUSY).queue();
            }
        });
    }

    private void reply(ButtonInteractionEvent event, InteractionHook hook, ExcuseComponentCodec.Open component) {
        try {
            ExcuseOpenUseCase.Result result = open.open(new ExcuseOpenUseCase.Request(
                    event.getGuild().getIdLong(), event.getChannel().getIdLong(), event.getMessageIdLong(),
                    component.gameResultId(), event.getUser().getIdLong()));
            if (result instanceof ExcuseOpenUseCase.Options options) {
                ExcuseOpenEmbedRenderer.EphemeralView view = renderer.options(
                        component.gameResultId(), options.contextGeneration(),
                        options.options(), options.availableRerollStyles());
                hook.editOriginalEmbeds(view.embed()).setComponents(view.components()).queue();
                return;
            }
            ExcuseOpenUseCase.Rejected rejected = (ExcuseOpenUseCase.Rejected) result;
            hook.editOriginal(rejected.reason() == ExcuseOpenUseCase.Reason.NOT_RESULT_AUTHOR ? FORBIDDEN : UNAVAILABLE)
                    .queue();
        } catch (RuntimeException exception) {
            hook.editOriginal(FAILURE).queue();
        }
    }
}
