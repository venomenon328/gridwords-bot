package de.venomenon.gridwordsbot.domain.record;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Vollständiger, deterministischer und hart validierter codebasierter Rekordkatalog. */
public final class RecordDefinitionCatalog {
    private static final int RECORDS_V1_DEFINITION_COUNT = 32;
    private static final RecordDefinitionCatalog RECORDS_V1 = createRecordsV1();

    private final RecordDefinitionVersion version;
    private final List<RecordDefinition<?>> definitions;
    private final Map<RecordDefinitionKey, RecordDefinition<?>> byKey;

    private RecordDefinitionCatalog(
            RecordDefinitionVersion version, List<RecordDefinition<?>> definitions, boolean requireCompleteRecordsV1) {
        this.version = Objects.requireNonNull(version, "version");
        this.definitions = List.copyOf(Objects.requireNonNull(definitions, "definitions"));
        if (this.definitions.isEmpty()) {
            throw new IllegalArgumentException("definitions must not be empty");
        }

        LinkedHashMap<RecordDefinitionKey, RecordDefinition<?>> indexed = new LinkedHashMap<>();
        Set<LogicalDefinitionIdentity> identities = new LinkedHashSet<>();
        for (RecordDefinition<?> definition : this.definitions) {
            validateDefinition(definition);
            if (!version.equals(definition.definitionVersion())) {
                throw new IllegalArgumentException("definition version does not match catalog version");
            }
            if (indexed.putIfAbsent(definition.key(), definition) != null) {
                throw new IllegalArgumentException("duplicate record definition key: " + definition.key());
            }
            LogicalDefinitionIdentity identity = new LogicalDefinitionIdentity(
                    definition.metric(), definition.game(), definition.scopeType());
            if (!identities.add(identity)) {
                throw new IllegalArgumentException("duplicate logical record definition: " + identity);
            }
        }
        this.byKey = Map.copyOf(indexed);

        if (requireCompleteRecordsV1) {
            validateRecordsV1Completeness();
        }
    }

    public static RecordDefinitionCatalog recordsV1() {
        return RECORDS_V1;
    }

    public static RecordDefinitionCatalog of(
            RecordDefinitionVersion version, List<RecordDefinition<?>> definitions) {
        return new RecordDefinitionCatalog(version, definitions, false);
    }

    public RecordDefinitionVersion version() {
        return version;
    }

    public List<RecordDefinition<?>> definitions() {
        return definitions;
    }

    public Optional<RecordDefinition<?>> find(RecordDefinitionKey key) {
        return Optional.ofNullable(byKey.get(Objects.requireNonNull(key, "key")));
    }

    private static RecordDefinitionCatalog createRecordsV1() {
        List<RecordDefinition<?>> definitions = new ArrayList<>();
        for (GameType game : GameType.values()) {
            for (ResultRecordMetric metric : ResultRecordMetric.values()) {
                definitions.add(resultDefinition(game, metric, RecordScopeType.PERSONAL));
                definitions.add(resultDefinition(game, metric, RecordScopeType.SERVER_INDIVIDUAL));
            }
        }
        for (StreakRecordMetric metric : StreakRecordMetric.values()) {
            definitions.add(streakDefinition(metric, RecordScopeType.PERSONAL));
            definitions.add(streakDefinition(metric, RecordScopeType.SERVER_INDIVIDUAL));
            if (metric.sharedScopeAllowed()) {
                definitions.add(streakDefinition(metric, RecordScopeType.SHARED));
            }
        }
        return new RecordDefinitionCatalog(RecordDefinitionVersion.RECORDS_V1, definitions, true);
    }

    private static RecordDefinition<?> resultDefinition(
            GameType game, ResultRecordMetric metric, RecordScopeType scopeType) {
        RecordAnnouncementThreshold.Result threshold = scopeType == RecordScopeType.PERSONAL
                ? new RecordAnnouncementThreshold.Result(5, 1)
                : new RecordAnnouncementThreshold.Result(10, 2);
        RecordDefinitionKey key = expectedKey(metric, Optional.of(game), scopeType);
        RecordSourceEligibility eligibility = new RecordSourceEligibility.SolvedGameResult(game);
        return switch (metric) {
            case FEWEST_ATTEMPTS -> new RecordDefinition<>(key, RecordDefinitionVersion.RECORDS_V1, metric,
                    Optional.of(game), scopeType, RecordComparators.fewestAttempts(), eligibility, threshold);
            case FASTEST_SOLUTION -> new RecordDefinition<>(key, RecordDefinitionVersion.RECORDS_V1, metric,
                    Optional.of(game), scopeType, RecordComparators.fastestDuration(), eligibility, threshold);
            case SLOWEST_SUCCESSFUL_SOLUTION -> new RecordDefinition<>(key, RecordDefinitionVersion.RECORDS_V1,
                    metric, Optional.of(game), scopeType, RecordComparators.slowestDuration(), eligibility, threshold);
        };
    }

    private static RecordDefinition<StreakRecordValue> streakDefinition(
            StreakRecordMetric metric, RecordScopeType scopeType) {
        int minimumLength = metric.drought() ? 3 : 7;
        RecordAnnouncementThreshold.Streak threshold = switch (scopeType) {
            case PERSONAL -> new RecordAnnouncementThreshold.Streak(minimumLength, 1, 1);
            case SERVER_INDIVIDUAL -> new RecordAnnouncementThreshold.Streak(minimumLength, 2, 2);
            case SHARED -> new RecordAnnouncementThreshold.Streak(minimumLength, 1, 0);
        };
        return new RecordDefinition<>(
                expectedKey(metric, metric.fixedGame(), scopeType),
                RecordDefinitionVersion.RECORDS_V1,
                metric,
                metric.fixedGame(),
                scopeType,
                RecordComparators.longestStreak(),
                new RecordSourceEligibility.StreakRun(metric),
                threshold);
    }

    private static void validateDefinition(RecordDefinition<?> definition) {
        Objects.requireNonNull(definition, "definition");
        if (!definition.key().equals(expectedKey(definition.metric(), definition.game(), definition.scopeType()))) {
            throw new IllegalArgumentException("definition key does not match its metric, game and scope");
        }
        if (definition.sourceEligibility().sourceType() != definition.sourceType()) {
            throw new IllegalArgumentException("source eligibility does not match metric source type");
        }

        if (definition.metric() instanceof ResultRecordMetric) {
            validateResultDefinition(definition);
        } else if (definition.metric() instanceof StreakRecordMetric streakMetric) {
            validateStreakDefinition(definition, streakMetric);
        } else {
            throw new IllegalArgumentException("unsupported record metric type");
        }
    }

    private static void validateResultDefinition(RecordDefinition<?> definition) {
        GameType game = definition.game()
                .orElseThrow(() -> new IllegalArgumentException("result definition requires a game"));
        if (definition.scopeType() == RecordScopeType.SHARED) {
            throw new IllegalArgumentException("result definitions do not allow shared scope");
        }
        if (!(definition.sourceEligibility() instanceof RecordSourceEligibility.SolvedGameResult eligibility)
                || eligibility.game() != game) {
            throw new IllegalArgumentException("result definition requires solved-result eligibility for its game");
        }
        if (!(definition.announcementThreshold() instanceof RecordAnnouncementThreshold.Result)) {
            throw new IllegalArgumentException("result definition requires a result threshold");
        }
    }

    private static void validateStreakDefinition(
            RecordDefinition<?> definition, StreakRecordMetric streakMetric) {
        if (!definition.game().equals(streakMetric.fixedGame())) {
            throw new IllegalArgumentException("streak definition game does not match its metric");
        }
        if (definition.scopeType() == RecordScopeType.SHARED && !streakMetric.sharedScopeAllowed()) {
            throw new IllegalArgumentException("streak metric does not allow shared scope");
        }
        if (!(definition.sourceEligibility() instanceof RecordSourceEligibility.StreakRun eligibility)
                || eligibility.metric() != streakMetric) {
            throw new IllegalArgumentException("streak definition requires matching streak-run eligibility");
        }
        if (!(definition.announcementThreshold() instanceof RecordAnnouncementThreshold.Streak)) {
            throw new IllegalArgumentException("streak definition requires a streak threshold");
        }
    }

    private void validateRecordsV1Completeness() {
        if (!RecordDefinitionVersion.RECORDS_V1.equals(version)) {
            throw new IllegalArgumentException("records-v1 catalog must use records-v1 definition version");
        }
        if (definitions.size() != RECORDS_V1_DEFINITION_COUNT) {
            throw new IllegalArgumentException("records-v1 must contain exactly 32 definitions");
        }

        for (GameType game : GameType.values()) {
            for (ResultRecordMetric metric : ResultRecordMetric.values()) {
                requireDefinition(metric, Optional.of(game), RecordScopeType.PERSONAL);
                requireDefinition(metric, Optional.of(game), RecordScopeType.SERVER_INDIVIDUAL);
            }
        }
        for (StreakRecordMetric metric : StreakRecordMetric.values()) {
            requireDefinition(metric, metric.fixedGame(), RecordScopeType.PERSONAL);
            requireDefinition(metric, metric.fixedGame(), RecordScopeType.SERVER_INDIVIDUAL);
            if (metric.sharedScopeAllowed()) {
                requireDefinition(metric, metric.fixedGame(), RecordScopeType.SHARED);
            }
        }
        definitions.forEach(this::validateRecordsV1Threshold);
    }

    private void validateRecordsV1Threshold(RecordDefinition<?> definition) {
        RecordAnnouncementThreshold expected;
        if (definition.metric() instanceof ResultRecordMetric) {
            expected = definition.scopeType() == RecordScopeType.PERSONAL
                    ? new RecordAnnouncementThreshold.Result(5, 1)
                    : new RecordAnnouncementThreshold.Result(10, 2);
        } else {
            StreakRecordMetric metric = (StreakRecordMetric) definition.metric();
            int minimumLength = metric.drought() ? 3 : 7;
            expected = switch (definition.scopeType()) {
                case PERSONAL -> new RecordAnnouncementThreshold.Streak(minimumLength, 1, 1);
                case SERVER_INDIVIDUAL -> new RecordAnnouncementThreshold.Streak(minimumLength, 2, 2);
                case SHARED -> new RecordAnnouncementThreshold.Streak(minimumLength, 1, 0);
            };
        }
        if (!expected.equals(definition.announcementThreshold())) {
            throw new IllegalArgumentException("records-v1 definition uses the wrong announcement threshold: "
                    + definition.key());
        }
    }

    private void requireDefinition(RecordMetric metric, Optional<GameType> game, RecordScopeType scopeType) {
        RecordDefinitionKey key = expectedKey(metric, game, scopeType);
        if (!byKey.containsKey(key)) {
            throw new IllegalArgumentException("records-v1 is missing definition " + key);
        }
    }

    private static RecordDefinitionKey expectedKey(
            RecordMetric metric, Optional<GameType> game, RecordScopeType scopeType) {
        Objects.requireNonNull(metric, "metric");
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(scopeType, "scopeType");
        String prefix;
        if (metric instanceof ResultRecordMetric) {
            GameType resultGame = game
                    .orElseThrow(() -> new IllegalArgumentException("result definition requires a game"));
            prefix = "result." + resultGame.name().toLowerCase(Locale.ROOT) + "." + metric.slug();
        } else {
            prefix = "streak." + metric.slug();
        }
        return new RecordDefinitionKey(prefix + "." + scopeType.slug());
    }

    private record LogicalDefinitionIdentity(
            RecordMetric metric, Optional<GameType> game, RecordScopeType scopeType) {
        private LogicalDefinitionIdentity {
            Objects.requireNonNull(metric, "metric");
            game = Objects.requireNonNull(game, "game");
            Objects.requireNonNull(scopeType, "scopeType");
        }
    }
}
