package de.venomenon.gridwordsbot.port.out;

import java.util.OptionalLong;
import java.util.List;

/** Persistence boundary for explicitly reactivating permanently classified source deletions. */
@FunctionalInterface
public interface SourceDeletionRecoveryStore {

    /**
     * Returns result IDs with a permanently classified source deletion.  The
     * caller must apply the business-date admission rule before reactivating
     * any of them.
     */
    default List<Long> findPermanentlyFailedResultIds() {
        return List.of();
    }

    /**
     * Clears the permanent classification only at a controlled recovery boundary.
     * An empty result ID applies to all otherwise publishable results, while a present ID scopes the reset.
     *
     * @return number of reactivated submissions
     */
    int reactivatePermanentFailures(OptionalLong gameResultId);
}
