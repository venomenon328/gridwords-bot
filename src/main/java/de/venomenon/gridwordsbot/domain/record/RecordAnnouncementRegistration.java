package de.venomenon.gridwordsbot.domain.record;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.UUID;

/** Desired announcement projection and its currently valid audit facts. */
public record RecordAnnouncementRegistration(
        RecordAnnouncementKey key,
        RecordAnnouncementSubject subject,
        RecordAnnouncementPhase phase,
        RecordAnnouncementProjection desiredProjection,
        String rendererVersion,
        String contentFingerprint,
        List<UUID> eventIds) {
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    public RecordAnnouncementRegistration {
        Objects.requireNonNull(key, "key"); Objects.requireNonNull(subject, "subject"); Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(desiredProjection, "desiredProjection"); Objects.requireNonNull(rendererVersion, "rendererVersion");
        Objects.requireNonNull(contentFingerprint, "contentFingerprint");
        if (rendererVersion.isBlank() || rendererVersion.length() > 64) throw new IllegalArgumentException("rendererVersion is invalid");
        if (!SHA_256.matcher(contentFingerprint).matches()) throw new IllegalArgumentException("contentFingerprint must be canonical SHA-256");
        eventIds = List.copyOf(Objects.requireNonNull(eventIds, "eventIds"));
        if (eventIds.stream().anyMatch(Objects::isNull) || eventIds.stream().distinct().count() != eventIds.size()) {
            throw new IllegalArgumentException("eventIds must be non-null and distinct");
        }
    }
}
