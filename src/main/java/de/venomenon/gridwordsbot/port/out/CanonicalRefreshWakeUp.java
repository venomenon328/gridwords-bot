package de.venomenon.gridwordsbot.port.out;

/**
 * Best-effort in-memory wake-up for already durable canonical refresh work. A failed wake-up is
 * safe because the canonical publication recovery query remains the source of truth.
 */
@FunctionalInterface
public interface CanonicalRefreshWakeUp {
    void wakeUp(long gameResultId);
}
