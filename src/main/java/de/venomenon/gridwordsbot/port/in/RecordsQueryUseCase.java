package de.venomenon.gridwordsbot.port.in;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordValue;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only transport-neutral query boundary for the current materialized record state. */
public interface RecordsQueryUseCase {
    Result query(Query query);

    record Query(
            long guildId,
            long requesterPlayerId,
            Optional<Long> targetPlayerId,
            boolean requesterAdministrator,
            GameFilter game) {
        public Query {
            if (guildId <= 0) throw new IllegalArgumentException("guildId must be positive");
            if (requesterPlayerId <= 0) throw new IllegalArgumentException("requesterPlayerId must be positive");
            targetPlayerId = Objects.requireNonNull(targetPlayerId, "targetPlayerId");
            targetPlayerId.ifPresent(id -> {
                if (id <= 0) throw new IllegalArgumentException("targetPlayerId must be positive");
            });
            Objects.requireNonNull(game, "game");
        }

        public long effectivePersonalPlayerId() {
            return targetPlayerId.orElse(requesterPlayerId);
        }
    }

    sealed interface Result permits Ready, Unavailable, Forbidden { }

    record Ready(List<Entry> entries) implements Result {
        public Ready {
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }
    }

    record Unavailable() implements Result { }
    record Forbidden() implements Result { }

    record Entry(
            String definitionKey,
            String metricSlug,
            Optional<GameType> game,
            Category category,
            Scope scope,
            Optional<String> holderDisplay,
            Optional<RecordValue> value,
            Optional<RecordSourceReference> source,
            boolean running) {
        public Entry {
            definitionKey = Objects.requireNonNull(definitionKey, "definitionKey");
            metricSlug = Objects.requireNonNull(metricSlug, "metricSlug");
            game = Objects.requireNonNull(game, "game");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(scope, "scope");
            holderDisplay = Objects.requireNonNull(holderDisplay, "holderDisplay");
            value = Objects.requireNonNull(value, "value");
            source = Objects.requireNonNull(source, "source");
            if (value.isPresent() != source.isPresent()) {
                throw new IllegalArgumentException("value and source must either both be present or both absent");
            }
        }
    }

    enum GameFilter { ALL, GRIDWORDS, QUADWORDS }
    enum Category { RESULTS, SERIES }
    enum Scope { PERSONAL, SERVER_INDIVIDUAL, SHARED }
}
