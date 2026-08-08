package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionVersion;
import de.venomenon.gridwordsbot.domain.achievement.AchievementEvidence;
import de.venomenon.gridwordsbot.domain.achievement.AchievementKey;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAnnouncement;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementEventFact;
import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementWork;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

final class AchievementJdbcMapping {
    private AchievementJdbcMapping() {}

    static AchievementAwardState.Snapshot award(ResultSet rs, int rowNum) throws SQLException {
        var key = new AchievementAwardState.Key(
                rs.getLong("guild_id"), rs.getLong("participant_id"), new AchievementKey(rs.getString("achievement_key")));
        var write = new AchievementAwardState.Write(
                new AchievementDefinitionVersion(rs.getString("definition_version")),
                AchievementAwardState.Status.valueOf(rs.getString("award_status")),
                rs.getObject("earned_on", java.time.LocalDate.class),
                instant(rs, "detected_at"),
                AchievementEvidence.Kind.valueOf(rs.getString("evidence_kind")),
                rs.getString("evidence_reference"),
                optionalInstant(rs, "invalidated_at"));
        return new AchievementAwardState.Snapshot(
                key, write, new AchievementAwardState.LockVersion(rs.getLong("lock_version")),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    static AchievementEventFact.Snapshot event(ResultSet rs, int rowNum) throws SQLException {
        var awardKey = new AchievementAwardState.Key(
                rs.getLong("guild_id"), rs.getLong("participant_id"), new AchievementKey(rs.getString("achievement_key")));
        var draft = new AchievementEventFact.Draft(
                rs.getObject("event_id", UUID.class),
                rs.getString("idempotency_key"),
                awardKey,
                new AchievementDefinitionVersion(rs.getString("definition_version")),
                AchievementEventFact.Type.valueOf(rs.getString("event_type")),
                rs.getObject("earned_on", java.time.LocalDate.class),
                AchievementEvidence.Kind.valueOf(rs.getString("evidence_kind")),
                rs.getString("evidence_reference"),
                AchievementEventFact.ProcessingOrigin.valueOf(rs.getString("processing_origin")),
                instant(rs, "detected_at"));
        return new AchievementEventFact.Snapshot(draft, instant(rs, "created_at"));
    }

    static AchievementWork.BootstrapSnapshot bootstrap(ResultSet rs, int rowNum) throws SQLException {
        AchievementWork.Failure failure = null;
        String category = rs.getString("failure_category");
        if (category != null) {
            failure = new AchievementWork.Failure(AchievementWork.FailureCategory.valueOf(category), rs.getString("safe_error"));
        }
        return new AchievementWork.BootstrapSnapshot(
                new AchievementWork.BootstrapKey(rs.getLong("guild_id"), new AchievementDefinitionVersion(rs.getString("definition_version"))),
                AchievementWork.State.valueOf(rs.getString("bootstrap_state")),
                optionalUuid(rs, "claim_token"), optionalInstant(rs, "claim_until"),
                optionalInstant(rs, "started_at"), optionalInstant(rs, "completed_at"),
                rs.getInt("attempt_count"), optionalInstant(rs, "next_retry_at"), Optional.ofNullable(failure),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    static AchievementAnnouncement.Snapshot announcement(ResultSet rs, int rowNum) throws SQLException {
        var registration = new AchievementAnnouncement.Registration(
                rs.getLong("guild_id"), rs.getLong("channel_id"), rs.getLong("participant_id"),
                new AchievementDefinitionVersion(rs.getString("definition_version")),
                AchievementAnnouncement.Type.valueOf(rs.getString("announcement_type")),
                rs.getString("idempotency_key"), rs.getString("renderer_version"), rs.getString("content_fingerprint"));
        AchievementWork.Failure failure = null;
        String category = rs.getString("failure_category");
        if (category != null) {
            failure = new AchievementWork.Failure(AchievementWork.FailureCategory.valueOf(category), rs.getString("safe_error"));
        }
        Long messageId = (Long) rs.getObject("discord_message_id");
        return new AchievementAnnouncement.Snapshot(
                rs.getLong("id"), registration, AchievementAnnouncement.DeliveryState.valueOf(rs.getString("delivery_state")),
                optionalUuid(rs, "claim_token"), optionalInstant(rs, "claim_until"), rs.getInt("attempt_count"),
                optionalInstant(rs, "next_retry_at"), Optional.ofNullable(failure), Optional.ofNullable(messageId),
                optionalInstant(rs, "delivered_at"), optionalInstant(rs, "synchronized_at"),
                optionalInstant(rs, "externally_removed_at"), optionalInstant(rs, "suppressed_at"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        if (value == null) throw new SQLException("required timestamp is null: " + column);
        return value.toInstant();
    }

    static Optional<Instant> optionalInstant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }

    static Optional<UUID> optionalUuid(ResultSet rs, String column) throws SQLException {
        return Optional.ofNullable(rs.getObject(column, UUID.class));
    }
}
