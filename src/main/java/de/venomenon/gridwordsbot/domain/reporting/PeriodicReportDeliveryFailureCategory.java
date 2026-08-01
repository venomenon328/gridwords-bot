package de.venomenon.gridwordsbot.domain.reporting;

/** Persistable classification of a safely described delivery failure. */
public enum PeriodicReportDeliveryFailureCategory {
    RETRYABLE,
    PERMANENT,
    UNKNOWN
}
