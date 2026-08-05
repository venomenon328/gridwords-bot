package de.venomenon.gridwordsbot.application.record;

import de.venomenon.gridwordsbot.domain.record.RecordBootstrapKey;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import de.venomenon.gridwordsbot.port.out.RecordBootstrapStore;
import java.util.Objects;

/** Small read boundary used by later live evaluation and command packages. */
public final class RecordBootstrapReadService {
    private final RecordBootstrapStore store;
    public RecordBootstrapReadService(RecordBootstrapStore store) { this.store = Objects.requireNonNull(store); }
    public RecordBootstrapReadiness readiness(RecordBootstrapKey key) {
        return store.find(key).map(snapshot -> switch (snapshot.state()) {
            case OPEN -> RecordBootstrapReadiness.OPEN;
            case CLAIMED -> RecordBootstrapReadiness.IN_PROGRESS;
            case RETRYABLE -> RecordBootstrapReadiness.RETRYABLE;
            case FAILED_PERMANENT -> RecordBootstrapReadiness.FAILED_PERMANENT;
            case SUCCEEDED -> RecordBootstrapReadiness.READY;
            default -> throw new IllegalStateException("invalid bootstrap work state: " + snapshot.state());
        }).orElse(RecordBootstrapReadiness.NOT_REGISTERED);
    }
}
