package de.venomenon.gridwordsbot.port.in;

/** Bounded reconciliation of due excuse offers; all public delivery remains in canonical recovery. */
public interface ExcuseExpirationUseCase {

    /** Expires at most the configured bounded number of due offers. */
    int reconcile();

    /** Lets an interaction atomically discover and persist an already due expiration. */
    boolean expireIfDue(long gameResultId);
}
