package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.domain.status.DailyStatusView;
import java.util.Optional;

/** Discord boundary intentionally free of JDA types. */
public interface DailyStatusMessageGateway {
    long publishOrEdit(long channelId, Optional<Long> existingMessageId, DailyStatus status, boolean contentChanged);
    default long publishOrEdit(long channelId, Optional<Long> existingMessageId, DailyStatusView view, boolean contentChanged) {
        return publishOrEdit(channelId, existingMessageId, view.status(), contentChanged);
    }
}
