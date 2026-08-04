package de.venomenon.gridwordsbot.application.excuse;

import de.venomenon.gridwordsbot.domain.model.DailyGameParticipation;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import java.time.Instant;
import java.util.Objects;

/** Disabled lifecycle that still records the mandatory negative first decision. */
public final class NoOpExcuseLifecycle implements ExcuseLifecycle {

    public static final NoOpExcuseLifecycle INSTANCE = new NoOpExcuseLifecycle();

    private NoOpExcuseLifecycle() {
    }

    @Override
    public void decideForNewResult(
            GameResultStore.StoredGameResult result,
            long sourceMessageId,
            Instant originalReceivedAt,
            DailyGameParticipation participation,
            Context context) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(context, "context").states().initializeNotOffered(result.id());
    }

    @Override
    public void revalidateExistingResult(
            GameResultStore.StoredGameResult result,
            DailyGameParticipation participation,
            Context context) {
        // A disabled feature never alters a persisted first decision during replay or correction.
    }
}
