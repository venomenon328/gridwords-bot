package de.venomenon.gridwordsbot.application.status;

import de.venomenon.gridwordsbot.domain.status.DailyStatusView;
import de.venomenon.gridwordsbot.port.in.DailyResultDetailsUseCase;
import de.venomenon.gridwordsbot.port.out.DailyResultDetailsQuery;
import de.venomenon.gridwordsbot.port.out.DailyStatusInteractionContextQuery;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

/** Validates an interaction exclusively against durable facts before loading the current result. */
public final class DailyResultDetailsService implements DailyResultDetailsUseCase {
    private final DailyStatusInteractionContextQuery contexts; private final DailyResultDetailsQuery results;
    public DailyResultDetailsService(DailyStatusInteractionContextQuery contexts, DailyResultDetailsQuery results) { this.contexts = Objects.requireNonNull(contexts); this.results = Objects.requireNonNull(results); }
    @Override public Result get(Request request) {
        var context = contexts.findCurrent(request.guildId(), request.channelId(), request.messageId(), request.gameDate());
        if (context.isEmpty()) return new Rejected(Reason.STATUS_NOT_CURRENT);
        var participants = context.get().participants().stream().sorted(Comparator.comparing((DailyStatusInteractionContextQuery.Participant player) -> player.displayName().toLowerCase(Locale.ROOT)).thenComparingLong(DailyStatusInteractionContextQuery.Participant::discordUserId)).toList();
        var target = participants.stream().filter(player -> player.discordUserId() == request.targetDiscordUserId()).findFirst();
        if (target.isEmpty()) return new Rejected(Reason.TARGET_NOT_PARTICIPATING);
        int pageCount = Math.max(1, (participants.size() + DailyStatusView.OPTIONS_PER_PAGE - 1) / DailyStatusView.OPTIONS_PER_PAGE);
        if (request.pageIndex() >= pageCount) return new Rejected(Reason.PAGE_NOT_OFFERED);
        int from = request.pageIndex() * DailyStatusView.OPTIONS_PER_PAGE; int to = Math.min(participants.size(), from + DailyStatusView.OPTIONS_PER_PAGE);
        if (participants.subList(from, to).stream().noneMatch(player -> player.discordUserId() == request.targetDiscordUserId())) return new Rejected(Reason.TARGET_NOT_ON_PAGE);
        return results.find(request.targetDiscordUserId(), request.gameType(), request.gameDate()).<Result>map(result -> new Found(target.get().displayName(), result)).orElseGet(() -> new Missing(target.get().displayName(), request.gameType(), request.gameDate()));
    }
}