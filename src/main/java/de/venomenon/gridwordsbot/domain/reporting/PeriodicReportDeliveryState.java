package de.venomenon.gridwordsbot.domain.reporting;

/** Explicit durable state of a logical periodic report delivery. */
public enum PeriodicReportDeliveryState {
    OPEN,
    CLAIMED,
    RETRYABLE,
    SUCCEEDED,
    NO_OP,
    EXPIRED,
    FAILED_PERMANENT;

    /** Only unfinished work without a current lease may be claimed again. */
    public boolean isClaimable() {
        return this == OPEN || this == RETRYABLE;
    }

    /** Terminal deliveries never become eligible for an automatic retry. */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == NO_OP || this == EXPIRED || this == FAILED_PERMANENT;
    }
}
