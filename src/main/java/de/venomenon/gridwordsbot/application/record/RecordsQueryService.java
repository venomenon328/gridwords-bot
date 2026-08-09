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
        if (query.targetPlayerId().isPresent()
                && query.targetPlayerId().get() != query.requesterPlayerId()
                && !query.requesterAdministrator()) {
            return new Forbidden();
        }
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
                .filter(definition -> gameMatches(definition, query.game()))
                .map(definition -> entry(query.guildId(), personalPlayerId, definition, current, displays))
                .flatMap(Optional::stream)
                .filter(entry -> categoryMatches(entry, query.category()))
                .filter(entry -> scopeMatches(entry, query.scope()))
                .sorted(Comparator.comparing((Entry entry) -> entry.category().ordinal())
                        .thenComparing(entry -> entry.scope().ordinal())
                        .thenComparing(Entry::definitionKey))
                .toList();
        return new Ready(entries);
    }

    private Optional<Entry> entry(
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
        if (snapshot == null) return Optional.empty();
        Optional<String> holder = definition.scopeType() == RecordScopeType.SHARED
                ? Optional.empty()
                : snapshot.holderPlayerId().map(id -> displays.getOrDefault(id, UNKNOWN_PLAYER));
        return Optional.of(new Entry(
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
                Optional.of(snapshot.value()),
                Optional.of(snapshot.source()),
                snapshot.running()));
    }

    private static boolean gameMatches(RecordDefinition<?> definition, GameFilter filter) {
        if (filter == GameFilter.ALL) return true;
        if (definition.game().isEmpty()) return false;
        return filter == GameFilter.GRIDWORDS
                ? definition.game().orElseThrow() == GameType.GRIDWORDS
                : definition.game().orElseThrow() == GameType.QUADWORDS;
    }

    private static boolean categoryMatches(Entry entry, CategoryFilter filter) {
        return filter == CategoryFilter.ALL
                || (filter == CategoryFilter.RESULTS && entry.category() == Category.RESULTS)
                || (filter == CategoryFilter.SERIES && entry.category() == Category.SERIES);
    }

    private static boolean scopeMatches(Entry entry, ScopeFilter filter) {
        return filter == ScopeFilter.ALL
                || (filter == ScopeFilter.PERSONAL && entry.scope() == Scope.PERSONAL)
                || (filter == ScopeFilter.SERVER_INDIVIDUAL && entry.scope() == Scope.SERVER_INDIVIDUAL)
                || (filter == ScopeFilter.SHARED && entry.scope() == Scope.SHARED);
    }
}
