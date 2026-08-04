package de.venomenon.gridwordsbot.adapter.discord.inbound;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseOption;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle;
import java.util.List;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** Ephemeral display for persisted initial options. Package 5 deliberately adds no follow-up controls. */
final class ExcuseOpenEmbedRenderer {

    MessageEmbed render(List<ExcuseOption> options) {
        String description = options.stream()
                .map(option -> "**" + option.position() + ". " + styleName(option.style()) + "**\n> "
                        + option.renderedText())
                .collect(java.util.stream.Collectors.joining("\n\n"));
        return new EmbedBuilder()
                .setTitle("Ausreden zur Auswahl")
                .setDescription(description)
                .build();
    }

    private static String styleName(ExcuseStyle style) {
        return switch (style) {
            case TECHNICAL -> "technisch";
            case TACTICAL -> "taktisch";
            case BUREAUCRATIC -> "bürokratisch";
            case DRAMATIC -> "dramatisch";
            case COSMIC -> "kosmisch";
            case NORTHERN_GERMAN -> "norddeutsch";
            case SPORTING -> "sportlich";
            case LEGAL -> "juristisch";
        };
    }
}
