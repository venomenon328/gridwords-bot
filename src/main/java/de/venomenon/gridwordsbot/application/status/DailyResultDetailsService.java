package de.venomenon.gridwordsbot.application.status;

import de.venomenon.gridwordsbot.application.achievement.AchievementEmojiResolver;
import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordScopeType;
import de.venomenon.gridwordsbot.domain.record.ResultRecordMetric;
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
    private final RecordDefinitionCatalog recordCatalog;
    private final AchievementDefinitionCatalog achievementCatalog;
    private final AchievementEmojiResolver achievementEmojis;

    public DailyResultDetailsService(
            DailyStatusInteractionContextQuery contexts,
            DailyResultDetailsQuery results,
            RecordDefinitionCatalog recordCatalog,
            AchievementDefinitionCatalog achievementCatalog,
            AchievementEmojiResolver achievementEmojis) {
        this.contexts = Objects.requireNonNull(contexts);
        this.results = Objects.requireNonNull(results);
        this.recordCatalog = Objects.requireNonNull(recordCatalog);
        this.achievementCatalog = Objects.requireNonNull(achievementCatalog);
        this.achievementEmojis = Objects.requireNonNull(achievementEmojis);
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
        return results.find(
                        request.guildId(),
                        request.targetDiscordUserId(),
                        request.gameType(),
                        request.gameDate(),
                        recordCatalog.version())
                .<Result>map(result -> found(target.get().displayName(), result))
                .orElseGet(() -> new Missing(target.get().displayName(), request.gameType(), request.gameDate()));
    }

    private Found found(String displayName, DailyResultDetailsQuery.Details details) {
        List<CurrentRecord> records = details.currentRecords().stream()
                .map(record -> recordCatalog.find(new de.venomenon.gridwordsbot.domain.record.RecordDefinitionKey(record.definitionKey()))
                        .filter(definition -> definition.metric() instanceof ResultRecordMetric)
                        .map(definition -> new CurrentRecord(metric(definition.metric().slug()), scope(record.scopeType()))))
                .flatMap(java.util.Optional::stream)
                .toList();
        java.util.Set<String> keys = java.util.Set.copyOf(details.activeAchievementKeys());
        List<Achievement> achievements = achievementCatalog.definitions().stream()
                .filter(definition -> keys.contains(definition.key().value()))
                .map(definition -> new Achievement(achievementEmojis.resolve(definition.key())
                        .orElse(definition.fallbackEmoji()), definition.displayName()))
                .toList();
        return new Found(displayName, details.result(), details.selectedExcuse(), records, achievements);
    }

    private static String metric(String slug) {
        return switch (slug) {
            case "fewest-attempts" -> "Wenigste Versuche";
            case "fastest-solution" -> "Schnellste Lösung";
            case "slowest-successful-solution" -> "Langsamste erfolgreiche Lösung";
            default -> slug;
        };
    }

    private static String scope(RecordScopeType scope) {
        return switch (scope) {
            case PERSONAL -> "Persönlich";
            case SERVER_INDIVIDUAL -> "Serverweit";
            case SHARED -> "Gemeinsam";
        };
    }
}
