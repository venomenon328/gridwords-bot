package de.venomenon.gridwordsbot.adapter.discord.canonical;

import de.venomenon.gridwordsbot.application.canonical.CanonicalMessageComponent;
import de.venomenon.gridwordsbot.application.canonical.CanonicalResultMessage;
import de.venomenon.gridwordsbot.port.out.DiscordDeliveryException;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

/** Maps the small transport-neutral canonical component model to JDA action rows. */
final class CanonicalComponentRenderer {

    private final ExcuseComponentCodec codec = new ExcuseComponentCodec();

    List<ActionRow> render(CanonicalResultMessage message) {
        return message.components().stream().map(this::row).toList();
    }

    private ActionRow row(CanonicalMessageComponent component) {
        if (component instanceof CanonicalMessageComponent.ExcuseOpen open) {
            return ActionRow.of(Button.primary(codec.encodeOpen(open.gameResultId()), "Ausrede wählen"));
        }
        throw DiscordDeliveryException.permanent("unknown canonical component", null);
    }
}
