package de.venomenon.gridwordsbot.application.record;

/** Public-record consumers may proceed only after the active version was fully initialized. */
public enum RecordBootstrapReadiness { NOT_REGISTERED, OPEN, IN_PROGRESS, RETRYABLE, FAILED_PERMANENT, READY }
