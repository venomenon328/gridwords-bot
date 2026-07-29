package de.venomenon.gridwordsbot.port.out;

import java.time.Instant;

/** Schedules a controlled retry without exposing a framework scheduler to the application layer. */
public interface PublicationRetryScheduler {
    void schedule(Instant at, Runnable action);
}
