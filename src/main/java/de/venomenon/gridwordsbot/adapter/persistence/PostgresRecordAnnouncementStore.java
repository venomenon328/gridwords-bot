package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementKey;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementClaim;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementMessage;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementPhase;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementProjection;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementRegistration;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordAnnouncementSubject;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaim;
import de.venomenon.gridwordsbot.domain.record.RecordLeaseClaimRequest;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailure;
import de.venomenon.gridwordsbot.domain.record.RecordWorkFailureCategory;
import de.venomenon.gridwordsbot.domain.record.RecordWorkState;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementClaimConflictException;
import de.venomenon.gridwordsbot.port.out.RecordAnnouncementStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL delivery projection store. It persists intent only and performs no external I/O. */
public class PostgresRecordAnnouncementStore implements RecordAnnouncementStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final boolean publicAnnouncementsEnabled;

    public PostgresRecordAnnouncementStore(JdbcTemplate jdbc, Clock clock) {
        this(jdbc, clock, true);
    }

    public PostgresRecordAnnouncementStore(
            JdbcTemplate jdbc, Clock clock, boolean publicAnnouncementsEnabled) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc, "jdbc");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.publicAnnouncementsEnabled = publicAnnouncementsEnabled;
    }

    @Override
    @Transactional
    public RecordAnnouncementSnapshot registerOrUpdate(RecordAnnouncementRegistration registration) {
        java.util.Objects.requireNonNull(registration, "registration");
        Instant now = clock.instant();
        RecordAnnouncementKey key = registration.key();
        Optional<RecordAnnouncementSnapshot> existing = find(key);
        if (existing.isPresent()) {
            RecordAnnouncementSnapshot snapshot = existing.orElseThrow();
            if (snapshot.state() == RecordWorkState.EXTERNALLY_REMOVED
                    || snapshot.state() == RecordWorkState.SUPPRESSED) {
                // External removal and disabled-mode suppression are terminal
                // delivery decisions. Their current fact set must still track
                // canonical reconciliation, but neither may open a backlog.
                if (!sameRegistration(snapshot.registration(), registration)) {
                    int updated = jdbc.update("""
                            UPDATE record_announcement
                            SET subject_type=?,subject_key=?,announcement_phase=?,desired_projection=?,
                                renderer_version=?,content_fingerprint=?,updated_at=?
                            WHERE guild_id=? AND channel_id=? AND idempotency_key=?
                              AND delivery_state IN ('EXTERNALLY_REMOVED','SUPPRESSED')
                            """,
                            registration.subject().type().name(), registration.subject().key(),
                            registration.phase().name(), registration.desiredProjection().name(),
                            registration.rendererVersion(), registration.contentFingerprint(), RecordJdbcMapping.utc(now),
                            key.guildId(), key.channelId(), key.idempotencyKey());
                    if (updated != 1) {
                        RecordAnnouncementSnapshot concurrent = find(key).orElseThrow(() ->
                                new IllegalStateException("record announcement disappeared during terminal reconciliation"));
                        if (concurrent.state() != snapshot.state()) {
                            throw new IllegalStateException(
                                    "record announcement left terminal state during reconciliation");
                        }
                        if (!sameRegistration(concurrent.registration(), registration)) {
                            throw new IllegalStateException(
                                    "record announcement terminal reconciliation made no progress");
                        }
                        return concurrent;
                    }
                    replaceFacts(id(key), registration.eventIds());
                }
                return find(key).orElseThrow();
            }
            if (sameRegistration(snapshot.registration(), registration)) {
                return snapshot;
            }
            if (snapshot.state() == RecordWorkState.CLAIMED) {
                throw new RecordAnnouncementClaimConflictException();
            }
            int updated = jdbc.update(
                    """
                    UPDATE record_announcement
                    SET subject_type=?,subject_key=?,announcement_phase=?,desired_projection=?,
                        renderer_version=?,content_fingerprint=?,delivery_state='OPEN',
                        claim_token=NULL,claim_until=NULL,next_retry_at=NULL,failure_category=NULL,
                        safe_error=NULL,changed_at=CASE WHEN ?='EDIT' THEN NULL ELSE changed_at END,
                        updated_at=?
                    WHERE guild_id=? AND channel_id=? AND idempotency_key=?
                      AND delivery_state<>'CLAIMED' AND delivery_state<>'EXTERNALLY_REMOVED'
                    """,
                    registration.subject().type().name(),
                    registration.subject().key(),
                    registration.phase().name(),
                    registration.desiredProjection().name(),
                    registration.rendererVersion(),
                    registration.contentFingerprint(),
                    registration.desiredProjection().name(),
                    RecordJdbcMapping.utc(now),
                    key.guildId(),
                    key.channelId(),
                    key.idempotencyKey());
            if (updated != 1) {
                RecordAnnouncementSnapshot concurrent = find(key).orElseThrow(() ->
                        new IllegalStateException("record announcement disappeared during reconciliation"));
                if (concurrent.state() == RecordWorkState.CLAIMED
                        || concurrent.state() == RecordWorkState.EXTERNALLY_REMOVED) {
                    throw new RecordAnnouncementClaimConflictException();
                }
                throw new IllegalStateException("record announcement update made no progress");
            }
            replaceFacts(id(key), registration.eventIds());
            return find(key).orElseThrow();
        }

        int inserted = jdbc.update(
                """
                INSERT INTO record_announcement (
                    guild_id,channel_id,idempotency_key,subject_type,subject_key,announcement_phase,
                    desired_projection,renderer_version,content_fingerprint,delivery_state,
                    attempt_count,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,0,?,?)
                ON CONFLICT (guild_id,channel_id,idempotency_key) DO NOTHING
                """,
                key.guildId(),
                key.channelId(),
                key.idempotencyKey(),
                registration.subject().type().name(),
                registration.subject().key(),
                registration.phase().name(),
                registration.desiredProjection().name(),
                registration.rendererVersion(),
                registration.contentFingerprint(),
                publicAnnouncementsEnabled || registration.desiredProjection() != RecordAnnouncementProjection.CREATE
                        ? "OPEN" : "SUPPRESSED",
                RecordJdbcMapping.utc(now),
                RecordJdbcMapping.utc(now));
        if (inserted == 0) {
            RecordAnnouncementSnapshot concurrent = find(key).orElseThrow(() ->
                    new IllegalStateException("conflicting record announcement is missing"));
            if (sameRegistration(concurrent.registration(), registration)) return concurrent;
            if (concurrent.state() == RecordWorkState.CLAIMED
                    || concurrent.state() == RecordWorkState.EXTERNALLY_REMOVED
                    || concurrent.state() == RecordWorkState.SUPPRESSED) {
                throw new RecordAnnouncementClaimConflictException();
            }
            throw new IllegalStateException("record announcement insert conflict requires a fresh reconciliation");
        }
        replaceFacts(id(key), registration.eventIds());
        return find(key).orElseThrow();
    }

    @Override
    public Optional<RecordAnnouncementSnapshot> find(RecordAnnouncementKey key) {
        return jdbc.query(
                        """
                        SELECT *
                        FROM record_announcement
                        WHERE guild_id=? AND channel_id=? AND idempotency_key=?
                        """,
                        (rs, row) -> snapshot(rs),
                        key.guildId(),
                        key.channelId(),
                        key.idempotencyKey())
                .stream()
                .findFirst();
    }

    @Override
    public List<RecordAnnouncementSnapshot> findByEventId(UUID eventId) {
        java.util.Objects.requireNonNull(eventId, "eventId");
        return jdbc.query("""
                SELECT announcement.* FROM record_announcement announcement
                JOIN record_announcement_event fact ON fact.announcement_id=announcement.id
                WHERE fact.event_id=?
                ORDER BY announcement.id
                """, (rs, row) -> snapshot(rs), eventId);
    }

    @Override
    public Optional<RecordLeaseClaim> claim(
            RecordAnnouncementKey key, RecordLeaseClaimRequest request) {
        UUID token = UUID.randomUUID();
        return jdbc.query(
                        """
                        UPDATE record_announcement
                        SET delivery_state='CLAIMED',claim_token=?,claim_until=?,next_retry_at=NULL,
                            attempt_count=attempt_count+1,updated_at=?
                        WHERE guild_id=? AND channel_id=? AND idempotency_key=?
                          AND (delivery_state='OPEN'
                               OR (delivery_state='RETRYABLE' AND next_retry_at<=?)
                               OR (delivery_state='CLAIMED' AND claim_until<=?))
                        RETURNING claim_token,claim_until
                        """,
                        (rs, row) -> new RecordLeaseClaim(
                                rs.getObject("claim_token", UUID.class),
                                RecordJdbcMapping.instant(rs, "claim_until")),
                        token,
                        RecordJdbcMapping.utc(request.leaseUntil()),
                        RecordJdbcMapping.utc(request.claimedAt()),
                        key.guildId(),
                        key.channelId(),
                        key.idempotencyKey(),
                        RecordJdbcMapping.utc(request.claimedAt()),
                        RecordJdbcMapping.utc(request.claimedAt()))
                .stream()
                .findFirst();
    }

    @Override
    @Transactional
    public Optional<RecordAnnouncementClaim> claimNext(
            RecordLeaseClaimRequest request, boolean deliveryEnabled) {
        UUID token = UUID.randomUUID();
        return jdbc.query("""
                WITH candidate AS (
                    SELECT id FROM record_announcement
                    WHERE (delivery_state='OPEN'
                        OR (delivery_state='RETRYABLE' AND next_retry_at<=?)
                        OR (delivery_state='CLAIMED' AND claim_until<=?)
                        OR (delivery_state='SYNCHRONIZED' AND published_at IS NOT NULL AND deleted_at IS NULL))
                      AND (? OR published_at IS NOT NULL OR desired_projection='DELETE'
                           OR (desired_projection='CREATE' AND published_at IS NULL AND attempt_count>0))
                    ORDER BY updated_at, guild_id, channel_id, idempotency_key
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                UPDATE record_announcement announcement
                SET delivery_state='CLAIMED',claim_token=?,claim_until=?,next_retry_at=NULL,
                    attempt_count=attempt_count+1,updated_at=?
                FROM candidate
                WHERE announcement.id=candidate.id
                RETURNING announcement.guild_id,announcement.channel_id,announcement.idempotency_key,
                    announcement.claim_token,announcement.claim_until,announcement.attempt_count
                """, (rs, row) -> new RecordAnnouncementClaim(
                        new RecordAnnouncementKey(rs.getLong("guild_id"), rs.getLong("channel_id"),
                                rs.getString("idempotency_key")),
                        rs.getObject("claim_token", UUID.class), RecordJdbcMapping.instant(rs, "claim_until"),
                        rs.getInt("attempt_count")),
                RecordJdbcMapping.utc(request.claimedAt()), RecordJdbcMapping.utc(request.claimedAt()),
                deliveryEnabled, token, RecordJdbcMapping.utc(request.leaseUntil()),
                RecordJdbcMapping.utc(request.claimedAt())).stream().findFirst();
    }

    @Override
    public boolean renewLease(
            RecordAnnouncementKey key, UUID token, RecordLeaseClaimRequest request) {
        return jdbc.update(
                        """
                        UPDATE record_announcement
                        SET claim_until=?,updated_at=?
                        WHERE guild_id=? AND channel_id=? AND idempotency_key=?
                          AND delivery_state='CLAIMED' AND claim_token=? AND claim_until>?
                        """,
                        RecordJdbcMapping.utc(request.leaseUntil()),
                        RecordJdbcMapping.utc(request.claimedAt()),
                        key.guildId(),
                        key.channelId(),
                        key.idempotencyKey(),
                        token,
                        RecordJdbcMapping.utc(request.claimedAt()))
                == 1;
    }

    @Override
    @Transactional
    public boolean replaceMessages(
            RecordAnnouncementKey key, UUID token, List<RecordAnnouncementMessage> messages) {
        List<RecordAnnouncementMessage> replacement =
                List.copyOf(java.util.Objects.requireNonNull(messages, "messages"));
        for (int i = 0; i < replacement.size(); i++) {
            if (replacement.get(i).position() != i) {
                throw new IllegalArgumentException(
                        "messages must use contiguous visible positions");
            }
        }

        Instant now = clock.instant();
        Long announcementId = claimedIdForUpdate(key, token, now);
        if (announcementId == null) {
            return false;
        }

        jdbc.update(
                "DELETE FROM record_announcement_message WHERE announcement_id=?",
                announcementId);
        for (RecordAnnouncementMessage message : replacement) {
            jdbc.update(
                    """
                    INSERT INTO record_announcement_message (
                        announcement_id,message_position,discord_message_id,created_at)
                    VALUES (?,?,?,?)
                    """,
                    announcementId,
                    message.position(),
                    message.messageId(),
                    RecordJdbcMapping.utc(now));
        }
        return true;
    }

    @Override
    public boolean markSynchronized(
            RecordAnnouncementKey key, UUID token, Instant synchronizedAt) {
        Instant now = clock.instant();
        return jdbc.update(
                        """
                        UPDATE record_announcement
                        SET delivery_state='SYNCHRONIZED',claim_token=NULL,claim_until=NULL,
                            next_retry_at=NULL,failure_category=NULL,safe_error=NULL,
                            published_at=CASE WHEN desired_projection='CREATE' THEN ? ELSE published_at END,
                            changed_at=CASE WHEN desired_projection='EDIT' THEN ? ELSE changed_at END,
                            deleted_at=CASE WHEN desired_projection='DELETE' THEN ? ELSE deleted_at END,
                            updated_at=?
                        WHERE guild_id=? AND channel_id=? AND idempotency_key=?
                          AND delivery_state='CLAIMED' AND claim_token=? AND claim_until>?
                        """,
                        RecordJdbcMapping.utc(synchronizedAt),
                        RecordJdbcMapping.utc(synchronizedAt),
                        RecordJdbcMapping.utc(synchronizedAt),
                        RecordJdbcMapping.utc(synchronizedAt),
                        key.guildId(),
                        key.channelId(),
                        key.idempotencyKey(),
                        token,
                        RecordJdbcMapping.utc(now))
                == 1;
    }

    @Override
    public boolean markRetryableFailure(
            RecordAnnouncementKey key,
            UUID token,
            RecordWorkFailure failure,
            Instant nextRetryAt) {
        if (failure.category() == RecordWorkFailureCategory.PERMANENT) {
            throw new IllegalArgumentException("retryable failure cannot be permanent");
        }
        return failure(key, token, "RETRYABLE", failure, nextRetryAt, null);
    }

    @Override
    public boolean markPermanentFailure(
            RecordAnnouncementKey key,
            UUID token,
            RecordWorkFailure failure,
            Instant completedAt) {
        if (failure.category() != RecordWorkFailureCategory.PERMANENT) {
            throw new IllegalArgumentException("permanent failure needs PERMANENT category");
        }
        return failure(key, token, "FAILED_PERMANENT", failure, null, completedAt);
    }

    @Override
    public boolean markExternallyRemoved(
            RecordAnnouncementKey key, UUID token, Instant removedAt) {
        Instant now = clock.instant();
        return jdbc.update(
                        """
                        UPDATE record_announcement
                        SET delivery_state='EXTERNALLY_REMOVED',claim_token=NULL,claim_until=NULL,
                            next_retry_at=NULL,externally_removed_at=?,updated_at=?
                        WHERE guild_id=? AND channel_id=? AND idempotency_key=?
                          AND delivery_state='CLAIMED' AND claim_token=? AND claim_until>?
                        """,
                        RecordJdbcMapping.utc(removedAt),
                        RecordJdbcMapping.utc(removedAt),
                        key.guildId(),
                        key.channelId(),
                        key.idempotencyKey(),
                        token,
                        RecordJdbcMapping.utc(now))
                == 1;
    }

    @Override
    public boolean markSuppressed(RecordAnnouncementKey key, UUID token, Instant suppressedAt) {
        return jdbc.update("""
                UPDATE record_announcement
                SET delivery_state='SUPPRESSED',claim_token=NULL,claim_until=NULL,next_retry_at=NULL,
                    failure_category=NULL,safe_error=NULL,updated_at=?
                WHERE guild_id=? AND channel_id=? AND idempotency_key=?
                  AND delivery_state='CLAIMED' AND claim_token=? AND claim_until>?
                  AND published_at IS NULL AND desired_projection='CREATE'
                """, RecordJdbcMapping.utc(suppressedAt), key.guildId(), key.channelId(), key.idempotencyKey(),
                token, RecordJdbcMapping.utc(clock.instant())) == 1;
    }

    @Override
    public int suppressPendingCreates(Instant suppressedAt) {
        return jdbc.update("""
                UPDATE record_announcement announcement
                SET delivery_state='SUPPRESSED',claim_token=NULL,claim_until=NULL,next_retry_at=NULL,
                    failure_category=NULL,safe_error=NULL,updated_at=?
                WHERE delivery_state='OPEN' AND published_at IS NULL AND desired_projection='CREATE'
                  AND attempt_count=0
                  AND NOT EXISTS (
                      SELECT 1 FROM record_announcement_message message WHERE message.announcement_id=announcement.id)
                """, RecordJdbcMapping.utc(suppressedAt));
    }

    private boolean failure(
            RecordAnnouncementKey key,
            UUID token,
            String state,
            RecordWorkFailure failure,
            Instant retryAt,
            Instant completedAt) {
        Instant now = clock.instant();
        Instant updatedAt = completedAt == null ? now : completedAt;
        return jdbc.update(
                        """
                        UPDATE record_announcement
                        SET delivery_state=?,claim_token=NULL,claim_until=NULL,next_retry_at=?,
                            failure_category=?,safe_error=?,updated_at=?
                        WHERE guild_id=? AND channel_id=? AND idempotency_key=?
                          AND delivery_state='CLAIMED' AND claim_token=? AND claim_until>?
                        """,
                        state,
                        retryAt == null ? null : RecordJdbcMapping.utc(retryAt),
                        failure.category().name(),
                        failure.safeMessage(),
                        RecordJdbcMapping.utc(updatedAt),
                        key.guildId(),
                        key.channelId(),
                        key.idempotencyKey(),
                        token,
                        RecordJdbcMapping.utc(now))
                == 1;
    }

    private Long id(RecordAnnouncementKey key) {
        return jdbc.query(
                        """
                        SELECT id
                        FROM record_announcement
                        WHERE guild_id=? AND channel_id=? AND idempotency_key=?
                        """,
                        (rs, row) -> rs.getLong(1),
                        key.guildId(),
                        key.channelId(),
                        key.idempotencyKey())
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void replaceFacts(Long announcementId, List<UUID> eventIds) {
        if (announcementId == null) {
            throw new IllegalStateException("registered announcement is missing");
        }
        jdbc.update(
                "DELETE FROM record_announcement_event WHERE announcement_id=?",
                announcementId);
        for (UUID eventId : eventIds) {
            jdbc.update(
                    """
                    INSERT INTO record_announcement_event (announcement_id,event_id)
                    VALUES (?,?)
                    """,
                    announcementId,
                    eventId);
        }
    }

    private static boolean sameRegistration(
            RecordAnnouncementRegistration left, RecordAnnouncementRegistration right) {
        return left.key().equals(right.key())
                && left.subject().equals(right.subject())
                && left.phase() == right.phase()
                && left.desiredProjection() == right.desiredProjection()
                && left.rendererVersion().equals(right.rendererVersion())
                && left.contentFingerprint().equals(right.contentFingerprint())
                && new java.util.HashSet<>(left.eventIds())
                        .equals(new java.util.HashSet<>(right.eventIds()));
    }

    private Long claimedIdForUpdate(RecordAnnouncementKey key, UUID token, Instant now) {
        return jdbc.query(
                        """
                        SELECT id
                        FROM record_announcement
                        WHERE guild_id=? AND channel_id=? AND idempotency_key=?
                          AND delivery_state='CLAIMED' AND claim_token=? AND claim_until>?
                        FOR UPDATE
                        """,
                        (rs, row) -> rs.getLong(1),
                        key.guildId(),
                        key.channelId(),
                        key.idempotencyKey(),
                        token,
                        RecordJdbcMapping.utc(now))
                .stream()
                .findFirst()
                .orElse(null);
    }

    private RecordAnnouncementSnapshot snapshot(ResultSet rs) throws SQLException {
        long announcementId = rs.getLong("id");
        RecordAnnouncementKey key = new RecordAnnouncementKey(
                rs.getLong("guild_id"),
                rs.getLong("channel_id"),
                rs.getString("idempotency_key"));
        List<UUID> events = jdbc.query(
                """
                SELECT event_id
                FROM record_announcement_event
                WHERE announcement_id=?
                ORDER BY event_id
                """,
                (event, row) -> event.getObject(1, UUID.class),
                announcementId);
        List<RecordAnnouncementMessage> messages = jdbc.query(
                """
                SELECT message_position,discord_message_id
                FROM record_announcement_message
                WHERE announcement_id=?
                ORDER BY message_position
                """,
                (message, row) -> new RecordAnnouncementMessage(
                        message.getInt(1),
                        message.getLong(2)),
                announcementId);
        RecordAnnouncementRegistration registration = new RecordAnnouncementRegistration(
                key,
                new RecordAnnouncementSubject(
                        RecordAnnouncementSubject.Type.valueOf(rs.getString("subject_type")),
                        rs.getString("subject_key")),
                RecordAnnouncementPhase.valueOf(rs.getString("announcement_phase")),
                RecordAnnouncementProjection.valueOf(rs.getString("desired_projection")),
                rs.getString("renderer_version"),
                rs.getString("content_fingerprint"),
                events);
        String category = rs.getString("failure_category");
        return new RecordAnnouncementSnapshot(
                registration,
                RecordWorkState.valueOf(rs.getString("delivery_state")),
                Optional.ofNullable(rs.getObject("claim_token", UUID.class)),
                Optional.ofNullable(RecordJdbcMapping.instant(rs, "claim_until")),
                rs.getInt("attempt_count"),
                Optional.ofNullable(RecordJdbcMapping.instant(rs, "next_retry_at")),
                category == null
                        ? Optional.empty()
                        : Optional.of(new RecordWorkFailure(
                                RecordWorkFailureCategory.valueOf(category),
                                rs.getString("safe_error"))),
                messages,
                Optional.ofNullable(RecordJdbcMapping.instant(rs, "published_at")),
                Optional.ofNullable(RecordJdbcMapping.instant(rs, "changed_at")),
                Optional.ofNullable(RecordJdbcMapping.instant(rs, "deleted_at")),
                Optional.ofNullable(RecordJdbcMapping.instant(rs, "externally_removed_at")),
                RecordJdbcMapping.instant(rs, "created_at"),
                RecordJdbcMapping.instant(rs, "updated_at"));
    }
}
