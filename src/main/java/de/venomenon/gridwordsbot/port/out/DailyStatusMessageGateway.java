package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import java.util.Optional;

/** Discord boundary intentionally free of JDA types. */
public interface DailyStatusMessageGateway {
    long publishOrEdit(long channelId, Optional<Long> existingMessageId, DailyStatus status);
}
