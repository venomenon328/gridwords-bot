package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseOffer;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferContext;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOption;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOptionSelection;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelection;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelectionHistoryEntry;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseState;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseRevalidation;
import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Persistent boundary for one excuse state per game result. Implementations serialize positive
 * offer decisions per player and game so a cooldown cannot be passed concurrently.
 */
public interface ExcuseStateStore {

    Optional<ExcuseState> find(long gameResultId);

    /** Creates the negative initial decision only when no decision exists yet. */
    ExcuseState initializeNotOffered(long gameResultId);

    /**
     * Creates a positive initial decision only when this result has no state and its player/game
     * cooldown is satisfied. An empty result leaves an existing state or a cooldown-blocked result unchanged.
     */
    Optional<ExcuseState> initializeAvailable(ExcuseOffer offer);

    /** Creates a positive decision and its immutable original submission/day-comparison facts together. */
    default Optional<ExcuseState> initializeAvailable(ExcuseOffer offer, ExcuseOfferContext offerContext) {
        throw new UnsupportedOperationException("offer contexts are not available");
    }

    /** Applies a correction revalidation only to the expected currently active state. */
    default Optional<ExcuseState> revalidate(ExcuseRevalidation revalidation) {
        throw new UnsupportedOperationException("excuse revalidation is not available");
    }

    /** A convenience read; callers requiring concurrency safety must use initializeAvailable. */
    boolean cooldownSatisfied(long playerId, GameType gameType, Instant offeredAt);

    Optional<ExcuseState> storeInitialOptions(long gameResultId, int contextGeneration, List<ExcuseOption> options);

    /**
     * Locks one available context generation, returns its three existing initial options, or invokes the pure factory
     * and persists exactly three new options before returning them. This is the only first-open persistence path.
     */
    default Optional<ExcuseSelection> loadOrCreateInitialOptions(
            long gameResultId, int contextGeneration, Supplier<ExcuseSelection> optionsFactory) {
        throw new UnsupportedOperationException("initial option load-or-create is not available");
    }

    /** Persists all three reroll options and consumes the one allowed reroll atomically. */
    Optional<ExcuseState> storeStyleRerollOptions(long gameResultId, int contextGeneration, List<ExcuseOption> options);

    /** Selects only an option from the currently active persisted round and copies its snapshot atomically. */
    Optional<ExcuseState> select(ExcuseOptionSelection selection);

    /** Declines only the current available context generation. */
    Optional<ExcuseState> decline(long gameResultId, int contextGeneration, Instant declinedAt);

    /** Returns a bounded, deterministic list of active offers whose expiry is due. */
    List<ExcuseState> findDueExpirations(Instant now, int limit);

    /** Returns no more than ten still-valid selected templates across both games. */
    List<ExcuseSelectionHistoryEntry> findRecentSelections(long playerId, int limit);
}
