package de.venomenon.gridwordsbot.application.status;

import de.venomenon.gridwordsbot.domain.status.DailyStatusView;
import de.venomenon.gridwordsbot.port.in.DailyResultDetailsUseCase;
import de.venomenon.gridwordsbot.port.out.DailyResultDetailsQuery;
import de.venomenon.gridwordsbot.port.out.DailyStatusInteractionContextQuery;
import java.util.List;
import java.util.Objects;

/** Validates an interaction exclusively against durable facts before loading the current result. */
public final class DailyResultDetailsService implements DailyResultDetailsUseCase {
    private final DailyStatusInteractionContextQuery contexts;
    private final DailyResultDetailsQuery results;

    public DailyResultDetailsService(
            DailyStatusInteractionContextQuery contexts,
            DailyResultDetailsQuery results) {
        this.contexts = Objects.requireNonNull(contexts);
        this.results = Objects.requireNonNull(results);
    }

    @Override
    public Result get(Request request) {
        var context = contexts.findCurrent(
                request.guildId(), request.channelId(), request.messageId(), request.gameDate());
        if (context.isEmpty()) return new Rejected(Reason.STATUS_NOT_CURRENT);

        List<DailyStatusView.PlayerOption> participants = context.get().participants(request.gameType()).stream()
                .map(player -> new DailyStatusView.PlayerOption(player.discordUserId(), player.displayName()))
                .toList();
        var target = participants.stream()
                .filter(player -> player.discordUserId() == request.targetDiscordUserId())
                .findFirst();
        if (target.isEmpty()) return new Rejected(Reason.TARGET_NOT_PARTICIPATING);

        List<DailyStatusView.DailyResultMenuPage> pages =
                DailyStatusView.resultMenuPages(request.gameType(), participants);
        if (pages.size() > DailyStatusView.MAX_PAGES_PER_GAME || request.pageIndex() >= pages.size()) {
            return new Rejected(Reason.PAGE_NOT_OFFERED);
        }
        DailyStatusView.DailyResultMenuPage page = pages.get(request.pageIndex());
        if (page.options().stream().noneMatch(player -> player.discordUserId() == request.targetDiscordUserId())) {
            return new Rejected(Reason.TARGET_NOT_ON_PAGE);
        }
        return results.find(request.targetDiscordUserId(), request.gameType(), request.gameDate())
                .<Result>map(result -> new Found(target.get().displayName(), result))
                .orElseGet(() -> new Missing(target.get().displayName(), request.gameType(), request.gameDate()));
    }
}