package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.achievement.persistence.AchievementAwardState;
import de.venomenon.gridwordsbot.port.out.AchievementAwardStateStore;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.time.LocalDate;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresAchievementAwardStateStore implements AchievementAwardStateStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PostgresAchievementAwardStateStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<AchievementAwardState.Snapshot> find(AchievementAwardState.Key key) {
        Objects.requireNonNull(key, "key");
        return jdbc.query("""
                SELECT * FROM achievement_award_state
                 WHERE guild_id=? AND participant_id=? AND achievement_key=?
                """, AchievementJdbcMapping::award,
                key.guildId(), key.participantId(), key.achievementKey().value()).stream().findFirst();
    }

    @Override
    public List<AchievementAwardState.Snapshot> findAll(long guildId, long participantId) {
        if (guildId <= 0 || participantId <= 0) throw new IllegalArgumentException("ids must be positive");
        return jdbc.query("""
                SELECT * FROM achievement_award_state
                 WHERE guild_id=? AND participant_id=?
                 ORDER BY achievement_key
                """, AchievementJdbcMapping::award, guildId, participantId);
    }

    @Override
    public List<AchievementAwardState.Snapshot> findActiveForPeriod(
            long guildId, Set<Long> participantIds, LocalDate startDate, LocalDate endDate) {
        if (guildId <= 0) throw new IllegalArgumentException("guildId must be positive");
        Objects.requireNonNull(participantIds, "participantIds");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        if (startDate.isAfter(endDate)) throw new IllegalArgumentException("startDate must not be after endDate");
        if (participantIds.isEmpty()) return List.of();
        if (participantIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("participantIds must be positive");
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(participantIds.size(), "?"));
        java.util.List<Object> parameters = new java.util.ArrayList<>();
        parameters.add(guildId);
        parameters.addAll(participantIds.stream().sorted().toList());
        parameters.add(startDate);
        parameters.add(endDate);
        return jdbc.query("""
                SELECT * FROM achievement_award_state
                 WHERE guild_id=? AND participant_id IN (""" + placeholders + """
                       ) AND award_status='ACTIVE' AND earned_on BETWEEN ? AND ?
                 ORDER BY participant_id, earned_on, achievement_key
                """, AchievementJdbcMapping::award, parameters.toArray());
    }

    @Override
    public AchievementAwardState.InitializationResult initialize(
            AchievementAwardState.Key key, AchievementAwardState.Write write) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(write, "write");
        Instant now = clock.instant();
        int inserted = jdbc.update("""
                INSERT INTO achievement_award_state (
                    guild_id, participant_id, achievement_key, definition_version, award_status,
                    earned_on, detected_at, evidence_kind, evidence_reference, invalidated_at,
                    lock_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?)
                ON CONFLICT (guild_id, participant_id, achievement_key) DO NOTHING
                """,
                key.guildId(), key.participantId(), key.achievementKey().value(), write.definitionVersion().value(),
                write.status().name(), write.earnedOn(), Timestamp.from(write.detectedAt()), write.evidenceKind().name(),
                write.evidenceReference(), write.invalidatedAt().map(Timestamp::from).orElse(null),
                Timestamp.from(now), Timestamp.from(now));
        AchievementAwardState.Snapshot current = find(key).orElseThrow(
                () -> new IllegalStateException("achievement award state vanished after initialization"));
        if (inserted == 1) {
            return new AchievementAwardState.InitializationResult(AchievementAwardState.InitializationStatus.CREATED, current);
        }
        return new AchievementAwardState.InitializationResult(
                current.write().equals(write)
                        ? AchievementAwardState.InitializationStatus.UNCHANGED
                        : AchievementAwardState.InitializationStatus.CONFLICT,
                current);
    }

    @Override
    public AchievementAwardState.UpdateResult update(
            AchievementAwardState.Key key,
            AchievementAwardState.LockVersion expectedLockVersion,
            AchievementAwardState.Write write) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(expectedLockVersion, "expectedLockVersion");
        Objects.requireNonNull(write, "write");
        Optional<AchievementAwardState.Snapshot> before = find(key);
        if (before.isEmpty()) return new AchievementAwardState.UpdateResult(AchievementAwardState.UpdateStatus.MISSING, Optional.empty());
        AchievementAwardState.Snapshot current = before.orElseThrow();
        if (!current.lockVersion().equals(expectedLockVersion)) {
            return new AchievementAwardState.UpdateResult(AchievementAwardState.UpdateStatus.VERSION_CONFLICT, Optional.of(current));
        }
        if (current.write().equals(write)) {
            return new AchievementAwardState.UpdateResult(AchievementAwardState.UpdateStatus.UNCHANGED, Optional.of(current));
        }
        Instant now = clock.instant();
        int updated = jdbc.update("""
                UPDATE achievement_award_state
                   SET definition_version=?, award_status=?, earned_on=?, detected_at=?, evidence_kind=?,
                       evidence_reference=?, invalidated_at=?, lock_version=lock_version+1, updated_at=?
                 WHERE guild_id=? AND participant_id=? AND achievement_key=? AND lock_version=?
                """,
                write.definitionVersion().value(), write.status().name(), write.earnedOn(), Timestamp.from(write.detectedAt()),
                write.evidenceKind().name(), write.evidenceReference(), write.invalidatedAt().map(Timestamp::from).orElse(null),
                Timestamp.from(now), key.guildId(), key.participantId(), key.achievementKey().value(), expectedLockVersion.value());
        Optional<AchievementAwardState.Snapshot> after = find(key);
        if (updated == 1) {
            return new AchievementAwardState.UpdateResult(AchievementAwardState.UpdateStatus.UPDATED, after);
        }
        return after.isEmpty()
                ? new AchievementAwardState.UpdateResult(AchievementAwardState.UpdateStatus.MISSING, Optional.empty())
                : new AchievementAwardState.UpdateResult(AchievementAwardState.UpdateStatus.VERSION_CONFLICT, after);
    }
}
