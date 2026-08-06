package de.venomenon.gridwordsbot.application.record;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.RecordBootstrapKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinition;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordScopeType;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.RecordStateSnapshot;
import de.venomenon.gridwordsbot.domain.record.ResultRecordMetric;
import de.venomenon.gridwordsbot.port.in.RecordsQueryUseCase;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Reads only the materialized current record projection; it never scans or rebuilds canonical history. */
public final class RecordsQueryService implements RecordsQueryUseCase {
    private static final String UNKNOWN_PLAYER = "Ehemaliger Spieler";

    private final RecordStateReadService states;
    private final RecordBootstrapReadService bootstrap;
    private final RecordDefinitionCatalog catalog;
    private final PlayerStore players;

    public RecordsQueryService(
            RecordStateReadService states,
            RecordBootstrapReadService bootstrap,
            RecordDefinitionCatalog catalog,
            PlayerStore players) {
        this.states = java.util.Objects.requireNonNull(states, "states");
        this.bootstrap = java.util.Objects.requireNonNull(bootstrap, "bootstrap");
        this.catalog = java.util.Objects.requireNonNull(catalog, "catalog");
        this.players = java.util.Objects.requireNonNull(players, "players");
    }

    @Override
    public Result query(Query query) {
        java.util.Objects.requireNonNull(query, "query");
        if (bootstrap.readiness(new RecordBootstrapKey(query.guildId(), catalog.version()))
                != RecordBootstrapReadiness.READY) {
            return new Unavailable();
        }

        Map<RecordStateKey, RecordStateSnapshot> current = states.list(query.guildId(), catalog.version()).stream()
                .collect(Collectors.toUnmodifiableMap(RecordStateSnapshot::key, Function.identity()));
        Map<Long, String> displays = players.findAllPlayers().stream()
                .collect(Collectors.toMap(PlayerStore.StoredPlayer::discordUserId, PlayerStore.StoredPlayer::displayName,
                        (left, right) -> left));
        long personalPlayerId = query.effectivePersonalPlayerId();

        List<Entry> entries = catalog.definitions().stream()
                .filter(definition -> categoryMatches(definition, query.category()))
                .filter(definition -> gameMatches(definition, query.game()))
                .filter(definition -> scopeMatches(definition.scopeType(), query.scope()))
                .map(definition -> entry(query.guildId(), personalPlayerId, definition, current, displays))
                .sorted(Comparator.comparing(Entry::definitionKey)
                        .thenComparing(entry -> entry.scope().ordinal()))
                .toList();
        return new Ready(entries);
    }

    private Entry entry(
            long guildId,
            long personalPlayerId,
            RecordDefinition<?> definition,
            Map<RecordStateKey, RecordStateSnapshot> current,
            Map<Long, String> displays) {
        RecordScope scope = switch (definition.scopeType()) {
            case PERSONAL -> new RecordScope.Personal(personalPlayerId);
            case SERVER_INDIVIDUAL -> new RecordScope.ServerIndividual();
            case SHARED -> new RecordScope.Shared();
        };
        RecordStateSnapshot snapshot = current.get(new RecordStateKey(
                guildId, definition.key(), definition.definitionVersion(), scope));
        Optional<String> holder = snapshot == null || definition.scopeType() == RecordScopeType.SHARED
                ? Optional.empty()
                : snapshot.holderPlayerId().map(id -> displays.getOrDefault(id, UNKNOWN_PLAYER));
        return new Entry(
                definition.key().value(),
                definition.metric().slug(),
                definition.game(),
                definition.metric() instanceof ResultRecordMetric ? Category.RESULTS : Category.SERIES,
                switch (definition.scopeType()) {
                    case PERSONAL -> Scope.PERSONAL;
                    case SERVER_INDIVIDUAL -> Scope.SERVER_INDIVIDUAL;
                    case SHARED -> Scope.SHARED;
                },
                holder,
                snapshot == null ? Optional.empty() : Optional.of(snapshot.value()),
                snapshot == null ? Optional.empty() : Optional.of(snapshot.source()),
                snapshot != null && snapshot.running());
    }

    private static boolean categoryMatches(RecordDefinition<?> definition, CategoryFilter filter) {
        if (filter == CategoryFilter.ALL) return true;
        boolean result = definition.metric() instanceof ResultRecordMetric;
        return filter == CategoryFilter.RESULTS ? result : !result;
    }

    private static boolean gameMatches(RecordDefinition<?> definition, GameFilter filter) {
        if (filter == GameFilter.ALL) return true;
        Optional<GameType> game = definition.game();
        if (game.isEmpty()) return false;
        return filter == GameFilter.GRIDWORDS ? game.get() == GameType.GRIDWORDS : game.get() == GameType.QUADWORDS;
    }

    private static boolean scopeMatches(RecordScopeType scope, ScopeFilter filter) {
        return switch (filter) {
            case ALL -> true;
            case PERSONAL -> scope == RecordScopeType.PERSONAL;
            case SERVER_INDIVIDUAL -> scope == RecordScopeType.SERVER_INDIVIDUAL;
            case SHARED -> scope == RecordScopeType.SHARED;
        };
    }
}
