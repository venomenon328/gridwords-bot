package de.venomenon.gridwordsbot.application.excuse;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseState;
import de.venomenon.gridwordsbot.port.in.ExcuseExpirationUseCase;
import de.venomenon.gridwordsbot.port.out.CanonicalRefreshWakeUp;
import de.venomenon.gridwordsbot.port.out.ExcuseStateStore;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Durable, bounded expiry reconciliation. A successful state transition is already recoverable
 * before the best-effort wake-up is attempted.
 */
public final class ExcuseExpirationService implements ExcuseExpirationUseCase {

    private final ExcuseStateStore states;
    private final CanonicalRefreshWakeUp refreshWakeUp;
    private final Clock clock;
    private final int pageSize;
    private final int maxPages;

    public ExcuseExpirationService(
            ExcuseStateStore states,
            CanonicalRefreshWakeUp refreshWakeUp,
            Clock clock,
            int pageSize,
            int maxPages) {
        this.states = Objects.requireNonNull(states, "states");
        this.refreshWakeUp = Objects.requireNonNull(refreshWakeUp, "refreshWakeUp");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (pageSize < 1 || maxPages < 1) {
            throw new IllegalArgumentException("expiry page limits must be positive");
        }
        this.pageSize = pageSize;
        this.maxPages = maxPages;
    }

    @Override
    public int reconcile() {
        int expired = 0;
        Instant now = clock.instant();
        for (int page = 0; page < maxPages; page++) {
            List<ExcuseState> due = states.findDueExpirations(now, pageSize);
            if (due.isEmpty()) {
                break;
            }
            for (ExcuseState state : due) {
                if (expire(state.gameResultId(), now)) {
                    expired++;
                }
            }
            if (due.size() < pageSize) {
                break;
            }
        }
        return expired;
    }

    @Override
    public boolean expireIfDue(long gameResultId) {
        if (gameResultId <= 0) {
            throw new IllegalArgumentException("gameResultId must be positive");
        }
        return expire(gameResultId, clock.instant());
    }

    private boolean expire(long gameResultId, Instant now) {
        if (states.expireAndRequestCanonicalRefresh(gameResultId, now).isEmpty()) {
            return false;
        }
        try {
            refreshWakeUp.wakeUp(gameResultId);
        } catch (RuntimeException ignored) {
            // The committed refresh request is resumed by the existing canonical recovery path.
        }
        return true;
    }
}
