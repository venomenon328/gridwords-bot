package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAnnouncement;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementWork;
import de.venomenon.gridwordsbot.port.out.AchievementAnnouncementStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresAchievementAnnouncementStore implements AchievementAnnouncementStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PostgresAchievementAnnouncementStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public AchievementAnnouncement.Snapshot register(AchievementAnnouncement.Registration registration) {
        Objects.requireNonNull(registration, "registration");
        Instant now = clock.instant();
        int inserted = jdbc.update("""
                INSERT INTO achievement_announcement (
                    guild_id, channel_id, participant_id, definition_version, announcement_type,
                    idempotency_key, renderer_version, content_fingerprint, delivery_state,
                    attempt_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', 0, ?, ?)
                ON CONFLICT (guild_id, idempotency_key) DO NOTHING
                """,
                registration.guildId(), registration.channelId(), registration.participantId(),
                registration.definitionVersion().value(), registration.type().name(), registration.idempotencyKey(),
                registration.rendererVersion(), registration.contentFingerprint(), Timestamp.from(now), Timestamp.from(now));
        AchievementAnnouncement.Snapshot current = find(registration.key()).orElseThrow(
                () -> new IllegalStateException("achievement announcement vanished after registration"));
        if (inserted == 0 && !current.registration().equals(registration)) {
            throw new IllegalStateException("achievement announcement idempotency conflict");
        }
        return current;
    }

    @Override
    public Optional<AchievementAnnouncement.Snapshot> find(AchievementAnnouncement.Key key) {
        Objects.requireNonNull(key, "key");
        return jdbc.query("""
                SELECT * FROM achievement_announcement
                 WHERE guild_id=? AND idempotency_key=?
                """, AchievementJdbcMapping::announcement, key.guildId(), key.idempotencyKey()).stream().findFirst();
    }

    @Override
    public List<AchievementAnnouncement.Item> findItems(AchievementAnnouncement.Key key) {
        AchievementAnnouncement.Snapshot announcement = find(key).orElseThrow(
                () -> new IllegalArgumentException("unknown achievement announcement"));
        return jdbc.query("""
                SELECT item_position, event_id
                  FROM achievement_announcement_item
                 WHERE announcement_id=?
                 ORDER BY item_position
                """, (rs, row) -> new AchievementAnnouncement.Item(rs.getInt("item_position"), rs.getObject("event_id", UUID.class)),
                announcement.id());
    }

    @Override
    public boolean updatePendingContent(AchievementAnnouncement.Key key, String rendererVersion, String contentFingerprint) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(rendererVersion, "rendererVersion");
        Objects.requireNonNull(contentFingerprint, "contentFingerprint");
        if (rendererVersion.isBlank() || !contentFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("invalid renderer version or content fingerprint");
        }
        Instant now = clock.instant();
        return jdbc.update("""
                UPDATE achievement_announcement
                   SET renderer_version=?, content_fingerprint=?, updated_at=?
                 WHERE guild_id=? AND idempotency_key=?
                   AND delivery_state IN ('OPEN','RETRYABLE') AND discord_message_id IS NULL
                """, rendererVersion, contentFingerprint, Timestamp.from(now), key.guildId(), key.idempotencyKey()) == 1;
    }

    @Override
    public boolean replaceItems(AchievementAnnouncement.Key key, List<UUID> eventIds) {
        Objects.requireNonNull(key, "key");
        eventIds = List.copyOf(Objects.requireNonNull(eventIds, "eventIds"));
        if (new HashSet<>(eventIds).size() != eventIds.size()) {
            throw new IllegalArgumentException("announcement eventIds must be unique");
        }
        AchievementAnnouncement.Snapshot announcement = find(key).orElseThrow(
                () -> new IllegalArgumentException("unknown achievement announcement"));
        if (!(announcement.deliveryState() == AchievementAnnouncement.DeliveryState.OPEN
                || announcement.deliveryState() == AchievementAnnouncement.DeliveryState.RETRYABLE)
                || announcement.discordMessageId().isPresent()) {
            return false;
        }
        for (UUID eventId : eventIds) {
            Integer matches = jdbc.queryForObject("""
                    SELECT count(*) FROM achievement_event
                     WHERE event_id=? AND guild_id=? AND participant_id=?
                    """, Integer.class, eventId, announcement.registration().guildId(), announcement.registration().participantId());
            if (matches == null || matches != 1) {
                throw new IllegalArgumentException("announcement event does not belong to announcement participant: " + eventId);
            }
        }
        jdbc.update("DELETE FROM achievement_announcement_item WHERE announcement_id=?", announcement.id());
        for (int position = 0; position < eventIds.size(); position++) {
            jdbc.update("""
                    INSERT INTO achievement_announcement_item (announcement_id, item_position, event_id, created_at)
                    VALUES (?, ?, ?, ?)
                    """, announcement.id(), position, eventIds.get(position), Timestamp.from(clock.instant()));
        }
        return true;
    }

    @Override
    public boolean wasSynchronized(long guildId, long participantId, AchievementKey achievementKey) {
        Objects.requireNonNull(achievementKey, "achievementKey");
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                  FROM achievement_announcement a
                  JOIN achievement_announcement_item i ON i.announcement_id=a.id
                  JOIN achievement_event e ON e.event_id=i.event_id
                 WHERE a.guild_id=? AND a.participant_id=? AND e.achievement_key=?
                   AND a.delivery_state='SYNCHRONIZED'
                """, Integer.class, guildId, participantId, achievementKey.value());
        return count != null && count > 0;
    }

    @Override
    public Optional<AchievementWork.LeaseClaim> claim(
            AchievementAnnouncement.Key key, AchievementWork.LeaseClaimRequest request) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(request, "request");
        UUID token = UUID.randomUUID();
        int updated = jdbc.update("""
                UPDATE achievement_announcement
                   SET delivery_state='CLAIMED', claim_token=?, claim_until=?, attempt_count=attempt_count+1,
                       next_retry_at=NULL, failure_category=NULL, safe_error=NULL, completed_at=NULL, updated_at=?
                 WHERE guild_id=? AND idempotency_key=?
                   AND (
                       delivery_state='OPEN'
                       OR (delivery_state='RETRYABLE' AND next_retry_at <= ?)
                       OR (delivery_state='CLAIMED' AND claim_until <= ?)
                   )
                """, token, Timestamp.from(request.leaseUntil()), Timestamp.from(request.claimedAt()),
                key.guildId(), key.idempotencyKey(), Timestamp.from(request.claimedAt()), Timestamp.from(request.claimedAt()));
        return updated == 1 ? Optional.of(new AchievementWork.LeaseClaim(token, request.leaseUntil())) : Optional.empty();
    }

    @Override
    public Optional<AchievementAnnouncement.Snapshot> claimNext(AchievementWork.LeaseClaimRequest request) {
        Objects.requireNonNull(request, "request");
        List<AchievementAnnouncement.Key> candidates = jdbc.query("""
                SELECT guild_id, idempotency_key
                  FROM achievement_announcement
                 WHERE delivery_state='OPEN'
                    OR (delivery_state='RETRYABLE' AND next_retry_at <= ?)
                    OR (delivery_state='CLAIMED' AND claim_until <= ?)
                 ORDER BY created_at, id
                 LIMIT 8
                """, (rs, row) -> new AchievementAnnouncement.Key(rs.getLong(1), rs.getString(2)),
                Timestamp.from(request.claimedAt()), Timestamp.from(request.claimedAt()));
        for (AchievementAnnouncement.Key key : candidates) {
            if (claim(key, request).isPresent()) return find(key);
        }
        return Optional.empty();
    }

    @Override
    public boolean renewLease(
            AchievementAnnouncement.Key key, UUID token, AchievementWork.LeaseClaimRequest request) {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(request, "request");
        return jdbc.update("""
                UPDATE achievement_announcement
                   SET claim_until=?, updated_at=?
                 WHERE guild_id=? AND idempotency_key=? AND delivery_state='CLAIMED'
                   AND claim_token=? AND claim_until > ?
                """, Timestamp.from(request.leaseUntil()), Timestamp.from(request.claimedAt()), key.guildId(), key.idempotencyKey(),
                token, Timestamp.from(request.claimedAt())) == 1;
    }

    @Override
    public boolean markDelivered(
            AchievementAnnouncement.Key key, UUID token, long discordMessageId, Instant deliveredAt) {
        if (discordMessageId <= 0) throw new IllegalArgumentException("discordMessageId must be positive");
        Objects.requireNonNull(deliveredAt, "deliveredAt");
        return jdbc.update("""
                UPDATE achievement_announcement
                   SET discord_message_id=COALESCE(discord_message_id, ?), delivered_at=COALESCE(delivered_at, ?), updated_at=?
                 WHERE guild_id=? AND idempotency_key=? AND delivery_state='CLAIMED'
                   AND claim_token=? AND claim_until > ?
                   AND (discord_message_id IS NULL OR discord_message_id=?)
                """, discordMessageId, Timestamp.from(deliveredAt), Timestamp.from(deliveredAt), key.guildId(), key.idempotencyKey(),
                token, Timestamp.from(deliveredAt), discordMessageId) == 1;
    }

    @Override
    public boolean markSynchronized(AchievementAnnouncement.Key key, UUID token, Instant synchronizedAt) {
        Objects.requireNonNull(synchronizedAt, "synchronizedAt");
        return jdbc.update("""
                UPDATE achievement_announcement
                   SET delivery_state='SYNCHRONIZED', claim_token=NULL, claim_until=NULL,
                       synchronized_at=?, completed_at=?, next_retry_at=NULL, failure_category=NULL, safe_error=NULL, updated_at=?
                 WHERE guild_id=? AND idempotency_key=? AND delivery_state='CLAIMED'
                   AND claim_token=? AND claim_until > ? AND discord_message_id IS NOT NULL
                """, Timestamp.from(synchronizedAt), Timestamp.from(synchronizedAt), Timestamp.from(synchronizedAt),
                key.guildId(), key.idempotencyKey(), token, Timestamp.from(synchronizedAt)) == 1;
    }

    @Override
    public boolean markRetryableFailure(
            AchievementAnnouncement.Key key,
            UUID token,
            AchievementWork.Failure failure,
            Instant nextRetryAt) {
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(nextRetryAt, "nextRetryAt");
        if (failure.category() == AchievementWork.FailureCategory.PERMANENT) {
            throw new IllegalArgumentException("permanent failure cannot be retryable");
        }
        Instant now = clock.instant();
        if (!nextRetryAt.isAfter(now)) throw new IllegalArgumentException("nextRetryAt must be in the future");
        return jdbc.update("""
                UPDATE achievement_announcement
                   SET delivery_state='RETRYABLE', claim_token=NULL, claim_until=NULL, next_retry_at=?,
                       failure_category=?, safe_error=?, completed_at=NULL, updated_at=?
                 WHERE guild_id=? AND idempotency_key=? AND delivery_state='CLAIMED'
                   AND claim_token=? AND claim_until > ?
                """, Timestamp.from(nextRetryAt), failure.category().name(), failure.safeError(), Timestamp.from(now),
                key.guildId(), key.idempotencyKey(), token, Timestamp.from(now)) == 1;
    }

    @Override
    public boolean markPermanentFailure(
            AchievementAnnouncement.Key key,
            UUID token,
            AchievementWork.Failure failure,
            Instant completedAt) {
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(completedAt, "completedAt");
        if (failure.category() != AchievementWork.FailureCategory.PERMANENT) {
            throw new IllegalArgumentException("permanent completion requires PERMANENT failure category");
        }
        return jdbc.update("""
                UPDATE achievement_announcement
                   SET delivery_state='FAILED_PERMANENT', claim_token=NULL, claim_until=NULL, next_retry_at=NULL,
                       failure_category=?, safe_error=?, completed_at=?, updated_at=?
                 WHERE guild_id=? AND idempotency_key=? AND delivery_state='CLAIMED'
                   AND claim_token=? AND claim_until > ?
                """, failure.category().name(), failure.safeError(), Timestamp.from(completedAt), Timestamp.from(completedAt),
                key.guildId(), key.idempotencyKey(), token, Timestamp.from(completedAt)) == 1;
    }

    @Override
    public boolean markExternallyRemoved(AchievementAnnouncement.Key key, UUID token, Instant removedAt) {
        Objects.requireNonNull(removedAt, "removedAt");
        return jdbc.update("""
                UPDATE achievement_announcement
                   SET delivery_state='EXTERNALLY_REMOVED', claim_token=NULL, claim_until=NULL, next_retry_at=NULL,
                       failure_category=NULL, safe_error=NULL, externally_removed_at=?, completed_at=?, updated_at=?
                 WHERE guild_id=? AND idempotency_key=? AND delivery_state='CLAIMED'
                   AND claim_token=? AND claim_until > ? AND discord_message_id IS NOT NULL
                """, Timestamp.from(removedAt), Timestamp.from(removedAt), Timestamp.from(removedAt),
                key.guildId(), key.idempotencyKey(), token, Timestamp.from(removedAt)) == 1;
    }

    @Override
    public boolean markSuppressed(AchievementAnnouncement.Key key, Instant suppressedAt) {
        Objects.requireNonNull(suppressedAt, "suppressedAt");
        return jdbc.update("""
                UPDATE achievement_announcement
                   SET delivery_state='SUPPRESSED', claim_token=NULL, claim_until=NULL, next_retry_at=NULL,
                       failure_category=NULL, safe_error=NULL, suppressed_at=?, completed_at=?, updated_at=?
                 WHERE guild_id=? AND idempotency_key=?
                   AND delivery_state IN ('OPEN','RETRYABLE') AND discord_message_id IS NULL
                """, Timestamp.from(suppressedAt), Timestamp.from(suppressedAt), Timestamp.from(suppressedAt),
                key.guildId(), key.idempotencyKey()) == 1;
    }
}
