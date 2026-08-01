package de.venomenon.gridwordsbot.domain.reporting;

/** A deterministic permanent rejection because visible report content cannot fit Discord's limits. */
public final class ReportRenderingException extends RuntimeException {
    public ReportRenderingException(String message) {
        super(message);
    }
}
