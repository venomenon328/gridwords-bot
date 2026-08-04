package de.venomenon.gridwordsbot.application.excuse;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog;
import de.venomenon.gridwordsbot.domain.model.DailyGameParticipation;
import de.venomenon.gridwordsbot.port.out.ExcuseStateStore;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Decides the first excuse state for a stored result and revalidates existing active states.
 * The persistence adapter owns the surrounding transaction and supplies its transaction-local
 * state-store dependencies through {@link Context}.
 */
public interface ExcuseLifecycle {

    void decideForNewResult(
            GameResultStore.StoredGameResult result,
            long sourceMessageId,
            Instant originalReceivedAt,
            DailyGameParticipation participation,
            Context context);

    void revalidateExistingResult(
            GameResultStore.StoredGameResult result,
            DailyGameParticipation participation,
            Context context);

    /** Dependencies shared by the enabled lifecycle and intentionally available to the no-op path. */
    record Context(ExcuseStateStore states, ExcuseCatalog catalog, Clock clock, Duration offerLifetime) {
        public Context {
            Objects.requireNonNull(states, "states");
        }

        public static Context noOp(ExcuseStateStore states) {
            return new Context(states, null, null, null);
        }

        public ExcuseCatalog contextualCatalog() {
            return Objects.requireNonNull(catalog, "contextual lifecycle requires catalog");
        }

        public Clock contextualClock() {
            return Objects.requireNonNull(clock, "contextual lifecycle requires clock");
        }

        public Duration contextualOfferLifetime() {
            return Objects.requireNonNull(offerLifetime, "contextual lifecycle requires offerLifetime");
        }
    }
}
