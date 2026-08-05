package de.venomenon.gridwordsbot.domain.record;

import java.util.Objects;

/** Idempotency boundary of one logical, possibly multi-message record announcement. */
public record RecordAnnouncementKey(long guildId, long channelId, String idempotencyKey) {
    public RecordAnnouncementKey {
        if (guildId <= 0 || channelId <= 0) throw new IllegalArgumentException("guildId and channelId must be positive");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 256) throw new IllegalArgumentException("idempotencyKey is invalid");
    }
}
