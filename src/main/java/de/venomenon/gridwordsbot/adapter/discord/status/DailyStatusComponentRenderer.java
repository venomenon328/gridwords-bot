package de.venomenon.gridwordsbot.adapter.discord.status;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.status.DailyStatusView;
import de.venomenon.gridwordsbot.port.out.DiscordDeliveryException;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;

/** Renders the fixed v1 component contract after all transport-neutral pagination is complete. */
final class DailyStatusComponentRenderer {
    List<ActionRow> render(DailyStatusView view) {
        if (view.resultMenuPages().size() > 4 || view.resultMenuPages().stream()
                .anyMatch(page -> page.pageCount() > DailyStatusView.MAX_PAGES_PER_GAME)) {
            throw DiscordDeliveryException.permanent("daily status exceeds interactive result participant limit", null);
        }
        return view.resultMenuPages().stream().map(page -> ActionRow.of(menu(view, page))).toList();
    }

    private static StringSelectMenu menu(DailyStatusView view, DailyStatusView.DailyResultMenuPage page) {
        String game = page.gameType() == GameType.GRIDWORDS ? "GridWords" : "QuadWords";
        String suffix = page.pageCount() == 1 ? "" : " (" + (page.pageIndex() + 1) + "/" + page.pageCount() + ")";
        String id = "daily-result:v" + view.componentVersion() + ":" + view.status().gameDate() + ":"
                + (page.gameType() == GameType.GRIDWORDS ? "g" : "q") + ":" + page.pageIndex();
        StringSelectMenu.Builder builder = StringSelectMenu.create(id)
                .setPlaceholder(game + "-Details anzeigen ..." + suffix);
        for (DailyStatusView.PlayerOption option : page.options()) {
            builder.addOptions(SelectOption.of(option.displayName(), "user:" + option.discordUserId()));
        }
        return builder.build();
    }
}