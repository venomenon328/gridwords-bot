package de.venomenon.gridwordsbot.domain.achievement.persistence;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionVersion;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvidence;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Append-only Achievement-Ereignisfakten und ihre Idempotenzidentität. */
public final class AchievementEventFact {
    private AchievementEventFact() {}

    public enum Type { UNLOCKED, INVALIDATED, REACTIVATED }

    public enum ProcessingOrigin {
        LIVE_SUBMISSION,
        NORMAL_CORRECTION,
        DAY_CLOSE,
        PARTICIPATION_CHANGE,
        BOOTSTRAP,
        REPLAY,
        IMPORT,
        BACKFILL,
        ADMINISTRATIVE_REPAIR
    }

    public record Draft(
            UUID eventId,
            String idempotencyKey,
            AchievementAwardState.Key awardKey,
            AchievementDefinitionVersion definitionVersion,
            Type eventType,
            LocalDate earnedOn,
            AchievementEvidence.Kind evidenceKind,
            String evidenceReference,
            ProcessingOrigin processingOrigin,
            Instant detectedAt) {
        public Draft {
            Objects.requireNonNull(eventId, "eventId");
            idempotencyKey = AchievementAwardState.requireText(idempotencyKey, "idempotencyKey");
            Objects.requireNonNull(awardKey, "awardKey");
            Objects.requireNonNull(definitionVersion, "definitionVersion");
            Objects.requireNonNull(eventType, "eventType");
            Objects.requireNonNull(earnedOn, "earnedOn");
            Objects.requireNonNull(evidenceKind, "evidenceKind");
            evidenceReference = AchievementAwardState.requireText(evidenceReference, "evidenceReference");
            Objects.requireNonNull(processingOrigin, "processingOrigin");
            Objects.requireNonNull(detectedAt, "detectedAt");
        }
    }

    public record Snapshot(Draft fact, Instant createdAt) {
        public Snapshot {
            Objects.requireNonNull(fact, "fact");
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    public record AppendResult(boolean appended, Snapshot event) {
        public AppendResult { Objects.requireNonNull(event, "event"); }
    }
}
