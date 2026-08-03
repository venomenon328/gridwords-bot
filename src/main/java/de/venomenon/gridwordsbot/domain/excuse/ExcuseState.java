package de.venomenon.gridwordsbot.domain.excuse;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Complete persistent snapshot for the optional excuse workflow of one result. */
public record ExcuseState(
        long gameResultId,
        ExcuseStatus status,
        Optional<ExcuseOfferMetadata> offer,
        boolean rerollUsed,
        Optional<ExcuseSelectionSnapshot> selection,
        Instant createdAt,
        Instant updatedAt) {

    public ExcuseState {
        if (gameResultId <= 0) {
            throw new IllegalArgumentException("gameResultId must be positive");
        }
        Objects.requireNonNull(status, "status");
        offer = Objects.requireNonNull(offer, "offer");
        selection = Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
        if (status == ExcuseStatus.NOT_OFFERED
                && (offer.isPresent() || selection.isPresent() || rerollUsed)) {
            throw new IllegalArgumentException("not offered state must not retain offer data");
        }
        if (status != ExcuseStatus.NOT_OFFERED && offer.isEmpty()) {
            throw new IllegalArgumentException("offered workflow states require offer metadata");
        }
        if (status == ExcuseStatus.AVAILABLE && selection.isPresent()) {
            throw new IllegalArgumentException("available state must not have a selection");
        }
        if (status == ExcuseStatus.SELECTED && selection.isEmpty()) {
            throw new IllegalArgumentException("selected state requires a selection snapshot");
        }
    }
}
