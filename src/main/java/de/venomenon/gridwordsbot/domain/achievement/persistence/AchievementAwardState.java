package de.venomenon.gridwordsbot.domain.achievement.persistence;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionVersion;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvidence;
import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Transportneutrale Verträge für den materialisierten aktuellen Achievement-Vergabestatus. */
public final class AchievementAwardState {
    private AchievementAwardState() {}

    public enum Status { ACTIVE, INVALIDATED }

    public record Key(long guildId, long participantId, AchievementKey achievementKey) {
        public Key {
            if (guildId <= 0) throw new IllegalArgumentException("guildId must be positive");
            if (participantId <= 0) throw new IllegalArgumentException("participantId must be positive");
            Objects.requireNonNull(achievementKey, "achievementKey");
        }
    }

    public record LockVersion(long value) {
        public LockVersion {
            if (value < 0) throw new IllegalArgumentException("lock version must not be negative");
        }
        public static LockVersion initial() { return new LockVersion(0); }
        public LockVersion next() { return new LockVersion(Math.addExact(value, 1)); }
    }

    public record Write(
            AchievementDefinitionVersion definitionVersion,
            Status status,
            LocalDate earnedOn,
            Instant detectedAt,
            AchievementEvidence.Kind evidenceKind,
            String evidenceReference,
            Optional<Instant> invalidatedAt) {
        public Write {
            Objects.requireNonNull(definitionVersion, "definitionVersion");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(earnedOn, "earnedOn");
            Objects.requireNonNull(detectedAt, "detectedAt");
            Objects.requireNonNull(evidenceKind, "evidenceKind");
            evidenceReference = requireText(evidenceReference, "evidenceReference");
            invalidatedAt = Objects.requireNonNull(invalidatedAt, "invalidatedAt");
            if ((status == Status.INVALIDATED) != invalidatedAt.isPresent()) {
                throw new IllegalArgumentException("invalidatedAt must be present exactly for INVALIDATED state");
            }
        }
    }

    public record Snapshot(Key key, Write write, LockVersion lockVersion, Instant createdAt, Instant updatedAt) {
        public Snapshot {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(write, "write");
            Objects.requireNonNull(lockVersion, "lockVersion");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
            if (updatedAt.isBefore(createdAt)) throw new IllegalArgumentException("updatedAt must not precede createdAt");
        }
    }

    public enum InitializationStatus { CREATED, UNCHANGED, CONFLICT }

    public record InitializationResult(InitializationStatus status, Snapshot snapshot) {
        public InitializationResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(snapshot, "snapshot");
        }
    }

    public enum UpdateStatus { UPDATED, UNCHANGED, VERSION_CONFLICT, MISSING }

    public record UpdateResult(UpdateStatus status, Optional<Snapshot> snapshot) {
        public UpdateResult {
            Objects.requireNonNull(status, "status");
            snapshot = Objects.requireNonNull(snapshot, "snapshot");
            if ((status == UpdateStatus.MISSING) == snapshot.isPresent()) {
                throw new IllegalArgumentException("only MISSING updates may omit the snapshot");
            }
        }
    }

    static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
