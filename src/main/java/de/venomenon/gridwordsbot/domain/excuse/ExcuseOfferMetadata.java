package de.venomenon.gridwordsbot.domain.excuse;

import java.time.Instant;
import java.util.Objects;

/** Immutable facts captured when an excuse is first offered. */
public record ExcuseOfferMetadata(
        long triggerSourceMessageId,
        String catalogVersion,
        String contextVersion,
        int contextGeneration,
        Instant offeredAt,
        Instant expiresAt) {

    public ExcuseOfferMetadata {
        if (triggerSourceMessageId <= 0) {
            throw new IllegalArgumentException("triggerSourceMessageId must be positive");
        }
        if (catalogVersion == null || catalogVersion.isBlank()) {
            throw new IllegalArgumentException("catalogVersion must not be blank");
        }
        if (contextVersion == null || contextVersion.isBlank()) {
            throw new IllegalArgumentException("contextVersion must not be blank");
        }
        if (contextGeneration < 1) {
            throw new IllegalArgumentException("contextGeneration must be positive");
        }
        Objects.requireNonNull(offeredAt, "offeredAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(offeredAt)) {
            throw new IllegalArgumentException("expiresAt must be after offeredAt");
        }
    }
}
