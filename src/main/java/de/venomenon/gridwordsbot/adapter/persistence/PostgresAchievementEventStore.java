package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact;
import de.venomenon.gridwordsbot.port.out.AchievementEventStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresAchievementEventStore implements AchievementEventStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PostgresAchievementEventStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AchievementEventFact.AppendResult append(AchievementEventFact.Draft draft) {
        Objects.requireNonNull(draft, "draft");
        Instant createdAt = clock.instant();
        try {
            jdbc.update("""
                    INSERT INTO achievement_event (
                        event_id, idempotency_key, guild_id, participant_id, achievement_key, definition_version,
                        event_type, earned_on, evidence_kind, evidence_reference, processing_origin, detected_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    draft.eventId(), draft.idempotencyKey(), draft.awardKey().guildId(), draft.awardKey().participantId(),
                    draft.awardKey().achievementKey().value(), draft.definitionVersion().value(), draft.eventType().name(),
                    draft.earnedOn(), draft.evidenceKind().name(), draft.evidenceReference(), draft.processingOrigin().name(),
                    Timestamp.from(draft.detectedAt()), Timestamp.from(createdAt));
            return new AchievementEventFact.AppendResult(true, find(draft.eventId()).orElseThrow());
        } catch (DuplicateKeyException duplicate) {
            Optional<AchievementEventFact.Snapshot> existing = findByIdempotencyKey(draft.idempotencyKey());
            if (existing.isPresent() && existing.orElseThrow().fact().equals(draft)) {
                return new AchievementEventFact.AppendResult(false, existing.orElseThrow());
            }
            Optional<AchievementEventFact.Snapshot> sameId = find(draft.eventId());
            if (sameId.isPresent() && sameId.orElseThrow().fact().equals(draft)) {
                return new AchievementEventFact.AppendResult(false, sameId.orElseThrow());
            }
            throw new IllegalStateException("achievement event idempotency conflict", duplicate);
        }
    }

    @Override
    public Optional<AchievementEventFact.Snapshot> find(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId");
        return jdbc.query("SELECT * FROM achievement_event WHERE event_id=?", AchievementJdbcMapping::event, eventId)
                .stream().findFirst();
    }

    @Override
    public Optional<AchievementEventFact.Snapshot> findByIdempotencyKey(String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        return jdbc.query("SELECT * FROM achievement_event WHERE idempotency_key=?", AchievementJdbcMapping::event, idempotencyKey)
                .stream().findFirst();
    }

    @Override
    public List<AchievementEventFact.Snapshot> findByParticipant(long guildId, long participantId) {
        if (guildId <= 0 || participantId <= 0) throw new IllegalArgumentException("ids must be positive");
        return jdbc.query("""
                SELECT * FROM achievement_event
                 WHERE guild_id=? AND participant_id=?
                 ORDER BY created_at, event_id
                """, AchievementJdbcMapping::event, guildId, participantId);
    }
}
