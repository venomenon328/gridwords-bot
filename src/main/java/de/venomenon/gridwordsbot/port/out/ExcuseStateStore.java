package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseOffer;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferContext;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOption;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOptionSelection;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelection;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelectionHistoryEntry;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseState;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseRevalidation;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseRound;
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

    /**
     * Returns all shown options for the active context generation in deterministic round/position
     * order. The caller uses these IDs to exclude every already displayed template from a reroll.
     */
    default List<ExcuseOption> findOptions(long gameResultId, int contextGeneration) {
        throw new UnsupportedOperationException("option lookup is not available");
    }

    /** Returns only the persisted round that is currently actionable for this offer. */
    default List<ExcuseOption> findActiveOptions(long gameResultId, int contextGeneration, ExcuseRound round) {
        return findOptions(gameResultId, contextGeneration).stream()
                .filter(option -> option.round() == round)
                .toList();
    }

    /** Persists all three reroll options and consumes the one allowed reroll atomically. */
    Optional<ExcuseState> storeStyleRerollOptions(long gameResultId, int contextGeneration, List<ExcuseOption> options);

    /**
     * Locks one available generation and atomically creates the one allowed reroll round. The
     * factory is invoked only by the transaction that wins the reroll, so losing requests do not
     * consume the injected random source.
     */
    default Optional<ExcuseSelection> loadOrCreateStyleRerollOptions(
            long gameResultId, int contextGeneration, Supplier<ExcuseSelection> optionsFactory) {
        throw new UnsupportedOperationException("style reroll load-or-create is not available");
    }

    /** Selects only an option from the currently active persisted round and copies its snapshot atomically. */
    Optional<ExcuseState> select(ExcuseOptionSelection selection);

    /**
     * Performs selection and durably increments the existing canonical refresh generation in the
     * same database transaction. Callers may only wake the already-persisted refresh pipeline.
     */
    default Optional<ExcuseState> selectAndRequestCanonicalRefresh(ExcuseOptionSelection selection) {
        throw new UnsupportedOperationException("atomic excuse selection refresh is not available");
    }

    /** Declines only the current available context generation. */
    Optional<ExcuseState> decline(long gameResultId, int contextGeneration, Instant declinedAt);

    /** Performs decline and the canonical refresh request in one database transaction. */
    default Optional<ExcuseState> declineAndRequestCanonicalRefresh(
            long gameResultId, int contextGeneration, Instant declinedAt) {
        throw new UnsupportedOperationException("atomic excuse decline refresh is not available");
    }

    /** Returns a bounded, deterministic list of active offers whose expiry is due. */
    List<ExcuseState> findDueExpirations(Instant now, int limit);

    /** Returns no more than ten still-valid selected templates across both games. */
    List<ExcuseSelectionHistoryEntry> findRecentSelections(long playerId, int limit);
}
