package de.venomenon.gridwordsbot.adapter.discord.status;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.domain.status.DailyStatusView;
import de.venomenon.gridwordsbot.port.out.DiscordDeliveryException;
import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;

/** Renders the fixed v1 component contract after all transport-neutral pagination is complete. */
final class DailyStatusComponentRenderer {
    private static final int OPTION_LABEL_MAX_LENGTH = 100;
    private static final int OPTION_DESCRIPTION_MAX_LENGTH = 100;

    List<ActionRow> render(DailyStatusView view) {
        if (view.resultMenuPages().size() > 4 || view.resultMenuPages().stream()
                .anyMatch(page -> page.pageCount() > DailyStatusView.MAX_PAGES_PER_GAME)) {
            throw DiscordDeliveryException.permanent("daily status exceeds interactive result participant limit", null);
        }
        List<ActionRow> rows = new java.util.ArrayList<>(view.resultMenuPages().stream()
                .map(page -> ActionRow.of(menu(view, page))).toList());
        rows.add(ActionRow.of(
                Button.link(JdaDailyStatusMessageGateway.GRIDWORDS_URL, "🟩 GridWords spielen"),
                Button.link(JdaDailyStatusMessageGateway.QUADWORDS_URL, "🟦 QuadWords spielen")));
        return List.copyOf(rows);
    }

    private static StringSelectMenu menu(DailyStatusView view, DailyStatusView.DailyResultMenuPage page) {
        String game = page.gameType() == GameType.GRIDWORDS ? "GridWords" : "QuadWords";
        String suffix = page.pageCount() == 1 ? "" : " (" + (page.pageIndex() + 1) + "/" + page.pageCount() + ")";
        String id = "daily-result:v" + view.componentVersion() + ":" + view.status().gameDate() + ":"
                + (page.gameType() == GameType.GRIDWORDS ? "g" : "q") + ":" + page.pageIndex();
        StringSelectMenu.Builder builder = StringSelectMenu.create(id)
                .setPlaceholder(game + "-Details anzeigen ..." + suffix);
        for (DailyStatusView.PlayerOption option : page.options()) {
            builder.addOptions(renderOption(view, page.gameType(), option));
        }
        return builder.build();
    }

    private static SelectOption renderOption(
            DailyStatusView view,
            GameType gameType,
            DailyStatusView.PlayerOption option) {
        DailyStatus.PlayerLine player = view.status().players().stream()
                .filter(candidate -> candidate.discordUserId() == option.discordUserId())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("daily result option is missing from status"));
        if (!player.participates(gameType)) {
            throw new IllegalArgumentException("daily result option does not participate in menu game");
        }

        String visibleName = safeVisibleName(option.displayName());
        Optional<ParsedGameResult> result = player.result(gameType);
        String label;
        String description;
        if (result.isEmpty()) {
            label = "⬜ " + visibleName;
            description = "Noch nicht eingereicht";
        } else {
            ParsedGameResult parsed = result.orElseThrow();
            label = (parsed.outcome() instanceof ShareOutcome.Solved ? "✅ " : "❌ ") + visibleName;
            String outcome = parsed.outcome() instanceof ShareOutcome.Solved solved
                    ? solved.attemptsUsed() + "/" + solved.maxAttempts()
                    : "X/" + parsed.outcome().maxAttempts();
            long seconds = parsed.duration().toSeconds();
            description = outcome + " · " + (seconds / 60) + ":" + String.format("%02d", seconds % 60);
        }

        return SelectOption.of(limit(label, OPTION_LABEL_MAX_LENGTH), "user:" + option.discordUserId())
                .withDescription(limit(description, OPTION_DESCRIPTION_MAX_LENGTH));
    }

    private static String safeVisibleName(String displayName) {
        String normalized = displayName.replaceAll("[\\p{Cntrl}]", " ").trim();
        return normalized.isBlank() ? "Unbekannter Spieler" : normalized;
    }

    private static String limit(String text, int maximum) {
        if (text.codePointCount(0, text.length()) <= maximum) return text;
        return text.substring(0, text.offsetByCodePoints(0, maximum));
    }
}
