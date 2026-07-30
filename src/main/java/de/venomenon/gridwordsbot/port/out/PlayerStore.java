package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Persistence boundary for dynamic players and their historical participation. */
public interface PlayerStore extends ReminderCandidateStore {
    StoredPlayer upsert(PlayerUpsert request);
    Optional<StoredPlayer> findByDiscordUserId(long discordUserId);
    default List<StoredPlayer> findActivePlayers() { throw new UnsupportedOperationException("active-player lookup is not available"); }
    default List<ParticipationPeriod> findParticipationPeriods() { throw new UnsupportedOperationException("participation periods are not available"); }
    default Optional<ParticipationPeriod> findParticipationPeriod(long discordUserId, LocalDate date) {
        Objects.requireNonNull(date, "date");
        return findParticipationPeriods().stream()
                .filter(period -> period.playerId() == discordUserId && period.contains(date))
                .findFirst();
    }
    default StoredPlayer synchronizeProfile(ProfileUpdate request) {
        StoredPlayer existing = findByDiscordUserId(request.discordUserId()).orElse(null);
        return upsert(new PlayerUpsert(
                request.discordUserId(),
                request.displayName(),
                existing != null && existing.active(),
                request.administrator()));
    }
    default StoredPlayer activate(ParticipationChange request) { throw new UnsupportedOperationException("participation changes are not available"); }
    default StoredPlayer deactivate(ParticipationChange request) { throw new UnsupportedOperationException("participation changes are not available"); }
    default StoredPlayer setReminderOptIn(ProfileUpdate request, boolean reminderOptIn) { throw new UnsupportedOperationException("reminder opt-in is not available"); }
    @Override default List<ReminderCandidate> findReminderCandidates(LocalDate gameDate) { throw new UnsupportedOperationException("reminder candidates are not available"); }

    record PlayerUpsert(long discordUserId, String displayName, boolean active, boolean administrator) {
        public PlayerUpsert {
            if (discordUserId <= 0) throw new IllegalArgumentException("discordUserId must be positive");
            Objects.requireNonNull(displayName, "displayName");
            if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
        }
    }
    record ProfileUpdate(long discordUserId, String displayName, boolean administrator) {
        public ProfileUpdate {
            if (discordUserId <= 0) throw new IllegalArgumentException("discordUserId must be positive");
            Objects.requireNonNull(displayName, "displayName");
            if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
        }
    }
    record ParticipationChange(ProfileUpdate profile, LocalDate effectiveDate) {
        public ParticipationChange { Objects.requireNonNull(profile, "profile"); Objects.requireNonNull(effectiveDate, "effectiveDate"); }
    }
    record StoredPlayer(long discordUserId, String displayName, boolean active, boolean administrator, boolean reminderOptIn, Instant createdAt, Instant updatedAt) {
        public StoredPlayer(long discordUserId, String displayName, boolean active, boolean administrator, Instant createdAt, Instant updatedAt) {
            this(discordUserId, displayName, active, administrator, false, createdAt, updatedAt);
        }
        public StoredPlayer {
            if (discordUserId <= 0) throw new IllegalArgumentException("discordUserId must be positive");
            Objects.requireNonNull(displayName, "displayName");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }
}
