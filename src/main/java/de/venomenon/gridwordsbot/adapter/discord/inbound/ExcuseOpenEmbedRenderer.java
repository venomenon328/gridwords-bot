package de.venomenon.gridwordsbot.adapter.discord.inbound;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseOption;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle;
import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import de.venomenon.gridwordsbot.adapter.discord.canonical.ExcuseComponentCodec;
import net.dv8tion.jda.api.entities.MessageEmbed;

/** Ephemeral display for persisted initial options. Package 5 deliberately adds no follow-up controls. */
final class ExcuseOpenEmbedRenderer {

    private final ExcuseComponentCodec codec = new ExcuseComponentCodec();

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

    EphemeralView options(
            long gameResultId,
            int contextGeneration,
            List<ExcuseOption> options,
            List<ExcuseStyle> availableRerollStyles) {
        List<Button> picks = options.stream()
                .map(option -> Button.primary(
                        codec.encodePick(gameResultId, contextGeneration, option.round(), option.position()),
                        option.position() + " w\u00e4hlen"))
                .toList();
        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(picks));
        List<Button> followUps = new ArrayList<>();
        if (!availableRerollStyles.isEmpty()) {
            followUps.add(Button.secondary(codec.encodeReroll(gameResultId, contextGeneration), "Anderer Stil"));
        }
        followUps.add(Button.danger(codec.encodeDecline(gameResultId, contextGeneration), "Keine Ausrede"));
        rows.add(ActionRow.of(followUps));
        return new EphemeralView(render(options), rows);
    }

    EphemeralView styleMenu(long gameResultId, int contextGeneration, List<ExcuseStyle> styles) {
        StringSelectMenu.Builder menu = StringSelectMenu.create(codec.encodeStyle(gameResultId, contextGeneration))
                .setPlaceholder("Stil ausw\u00e4hlen")
                .setRequiredRange(1, 1);
        styles.forEach(style -> menu.addOption(styleName(style), codec.encodeStyleValue(style)));
        return new EphemeralView(new EmbedBuilder()
                .setTitle("Anderen Stil w\u00e4hlen")
                .setDescription("W\u00e4hle einen Stil f\u00fcr drei neue Vorschl\u00e4ge.")
                .build(), List.of(ActionRow.of(menu.build())));
    }

    static String styleName(ExcuseStyle style) {
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

    record EphemeralView(MessageEmbed embed, List<ActionRow> components) {
        EphemeralView {
            java.util.Objects.requireNonNull(embed, "embed");
            components = List.copyOf(java.util.Objects.requireNonNull(components, "components"));
        }
    }
}
