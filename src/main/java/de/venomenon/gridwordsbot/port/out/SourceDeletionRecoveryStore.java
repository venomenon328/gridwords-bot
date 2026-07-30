package de.venomenon.gridwordsbot.port.out;

import java.util.OptionalLong;

/** Persistence boundary for explicitly reactivating permanently classified source deletions. */
@FunctionalInterface
public interface SourceDeletionRecoveryStore {

    /**
     * Clears the permanent classification only at a controlled recovery boundary.
     * An empty result ID applies to all otherwise publishable results, while a present ID scopes the reset.
     *
     * @return number of reactivated submissions
     */
    int reactivatePermanentFailures(OptionalLong gameResultId);
}
