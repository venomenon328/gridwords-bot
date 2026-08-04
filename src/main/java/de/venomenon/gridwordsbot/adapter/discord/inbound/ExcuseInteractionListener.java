package de.venomenon.gridwordsbot.adapter.discord.inbound;

import de.venomenon.gridwordsbot.adapter.discord.canonical.ExcuseComponentCodec;
import de.venomenon.gridwordsbot.config.GridwordsBotProperties;
import de.venomenon.gridwordsbot.port.in.ExcuseInteractionUseCase;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;

/** Thin JDA dispatcher for non-open excuse components; canonical messages are never edited here. */
public final class ExcuseInteractionListener extends ListenerAdapter {

    private static final String BUSY = "Der Bot ist gerade ausgelastet. Bitte versuche es erneut.";
    private static final String UNAVAILABLE = "Diese Ausrede ist nicht verf\u00fcgbar oder nicht mehr aktuell.";
    private static final String FORBIDDEN = "Diesen Button kann nur der Ergebnisautor verwenden.";
    private static final String SELECTED = "Die Ausrede wird in der Ergebnisnachricht \u00fcbernommen.";
    private static final String DECLINED = "Es wurde keine Ausrede ausgew\u00e4hlt.";
    private static final String FAILURE = "Die Ausreden konnten nicht verarbeitet werden. Bitte versuche es erneut.";

    private final GridwordsBotProperties properties;
    private final Executor executor;
    private final ExcuseInteractionUseCase interactions;
    private final ExcuseComponentCodec codec = new ExcuseComponentCodec();
    private final ExcuseOpenEmbedRenderer renderer = new ExcuseOpenEmbedRenderer();

    public ExcuseInteractionListener(
            GridwordsBotProperties properties, Executor executor, ExcuseInteractionUseCase interactions) {
        this.properties = properties;
        this.executor = executor;
        this.interactions = interactions;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        ExcuseComponentCodec.Pick pick = codec.decodePick(event.getComponentId()).orElse(null);
        ExcuseComponentCodec.Reroll reroll = pick == null ? codec.decodeReroll(event.getComponentId()).orElse(null) : null;
        ExcuseComponentCodec.Decline decline = pick == null && reroll == null
                ? codec.decodeDecline(event.getComponentId()).orElse(null) : null;
        if (pick == null && reroll == null && decline == null || !inConfiguredContext(event)) {
            return;
        }
        long gameResultId = pick != null ? pick.gameResultId() : reroll != null ? reroll.gameResultId() : decline.gameResultId();
        int contextGeneration = pick != null ? pick.contextGeneration()
                : reroll != null ? reroll.contextGeneration() : decline.contextGeneration();
        event.deferReply(true).queue(hook -> dispatch(hook, gameResultId, contextGeneration, () -> {
            ExcuseInteractionUseCase.ActionRequest action = action(event,
                    gameResultId, contextGeneration);
            return pick != null
                    ? interactions.pick(new ExcuseInteractionUseCase.PickRequest(action, pick.round(), pick.position()))
                    : reroll != null ? interactions.openStyleMenu(action) : interactions.decline(action);
        }));
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        ExcuseComponentCodec.Style style = codec.decodeStyle(event.getComponentId()).orElse(null);
        if (style == null || !inConfiguredContext(event)) {
            return;
        }
        event.deferReply(true).queue(hook -> dispatch(hook, style.gameResultId(), style.contextGeneration(), () -> {
            List<String> values = event.getValues();
            de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle selected = values.size() == 1
                    ? codec.decodeStyleValue(values.getFirst()).orElse(null) : null;
            if (selected == null) {
                return new ExcuseInteractionUseCase.Rejected(ExcuseInteractionUseCase.Reason.REROLL_UNAVAILABLE);
            }
            return interactions.selectStyle(new ExcuseInteractionUseCase.StyleRequest(
                    action(event, style.gameResultId(), style.contextGeneration()), selected));
        }));
    }

    private void dispatch(
            InteractionHook hook,
            long gameResultId,
            int contextGeneration,
            java.util.function.Supplier<ExcuseInteractionUseCase.Result> action) {
        try {
            executor.execute(() -> reply(hook, gameResultId, contextGeneration, action));
        } catch (RejectedExecutionException exception) {
            hook.editOriginal(BUSY).queue();
        }
    }

    private void reply(
            InteractionHook hook,
            long gameResultId,
            int contextGeneration,
            java.util.function.Supplier<ExcuseInteractionUseCase.Result> action) {
        try {
            ExcuseInteractionUseCase.Result result = action.get();
            if (result instanceof ExcuseInteractionUseCase.Options options) {
                ExcuseOpenEmbedRenderer.EphemeralView view = renderer.options(
                        gameResultId, contextGeneration, options.options(), options.availableRerollStyles());
                hook.editOriginalEmbeds(view.embed()).setComponents(view.components()).queue();
                return;
            }
            if (result instanceof ExcuseInteractionUseCase.StyleMenu menu) {
                ExcuseOpenEmbedRenderer.EphemeralView view = renderer.styleMenu(
                        gameResultId, contextGeneration, menu.styles());
                hook.editOriginalEmbeds(view.embed()).setComponents(view.components()).queue();
                return;
            }
            if (result == ExcuseInteractionUseCase.Selected.INSTANCE) {
                hook.editOriginal(SELECTED).queue();
            } else if (result == ExcuseInteractionUseCase.Declined.INSTANCE) {
                hook.editOriginal(DECLINED).queue();
            } else {
                ExcuseInteractionUseCase.Rejected rejected = (ExcuseInteractionUseCase.Rejected) result;
                hook.editOriginal(rejected.reason() == ExcuseInteractionUseCase.Reason.NOT_RESULT_AUTHOR ? FORBIDDEN : UNAVAILABLE)
                        .queue();
            }
        } catch (RuntimeException exception) {
            hook.editOriginal(FAILURE).queue();
        }
    }

    private ExcuseInteractionUseCase.ActionRequest action(
            ButtonInteractionEvent event, long resultId, int contextGeneration) {
        return new ExcuseInteractionUseCase.ActionRequest(
                event.getGuild().getIdLong(), event.getChannel().getIdLong(), resultId,
                event.getUser().getIdLong(), contextGeneration);
    }

    private ExcuseInteractionUseCase.ActionRequest action(
            StringSelectInteractionEvent event, long resultId, int contextGeneration) {
        return new ExcuseInteractionUseCase.ActionRequest(
                event.getGuild().getIdLong(), event.getChannel().getIdLong(), resultId,
                event.getUser().getIdLong(), contextGeneration);
    }

    private boolean inConfiguredContext(ButtonInteractionEvent event) {
        return event.isFromGuild()
                && event.getGuild().getIdLong() == properties.discord().guildId()
                && event.getChannel().getIdLong() == properties.discord().channelId();
    }

    private boolean inConfiguredContext(StringSelectInteractionEvent event) {
        return event.isFromGuild()
                && event.getGuild().getIdLong() == properties.discord().guildId()
                && event.getChannel().getIdLong() == properties.discord().channelId();
    }
}
