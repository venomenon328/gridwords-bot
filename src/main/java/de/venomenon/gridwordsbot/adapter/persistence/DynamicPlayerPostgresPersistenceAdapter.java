package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.port.out.PlayerStore.ParticipationChange;
import de.venomenon.gridwordsbot.port.out.PlayerStore.ProfileUpdate;
import de.venomenon.gridwordsbot.port.out.PlayerStore.StoredPlayer;
import de.venomenon.gridwordsbot.port.out.SubmissionStore.ResultStorage;
import de.venomenon.gridwordsbot.port.out.SubmissionStore.StoredSubmission;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database-profile specialization for dynamic players.
 *
 * <p>The established persistence adapter remains the single implementation of result, publication and deletion
 * state. This specialization only adds a stable transaction-wide lock before a valid result can mutate participant
 * periods and synchronizes the externally configured administrator flag together with the Discord display name.</p>
 */
@Repository
@Primary
@Profile("database")
public class DynamicPlayerPostgresPersistenceAdapter extends PostgresPersistenceAdapter {
    private static final long PARTICIPATION_CONTEXT_LOCK = 7_320_019L;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DynamicPlayerPostgresPersistenceAdapter(JdbcTemplate jdbc, Clock clock, ZoneId businessZone) {
        super(jdbc, clock, businessZone);
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    @Transactional
    public StoredSubmission storeResult(ResultStorage request) {
        // Serialize before the first participation-period write. This prevents two first-time submissions from
        // upgrading their row/table locks in opposite order while publication context is calculated atomically.
        jdbc.execute("SELECT pg_advisory_xact_lock(" + PARTICIPATION_CONTEXT_LOCK + ")");
        return super.storeResult(request);
    }

    @Override
    @Transactional
    public StoredPlayer synchronizeProfile(ProfileUpdate request) {
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO player (
                    discord_user_id, display_name, active, administrator, reminder_opt_in, created_at, updated_at)
                VALUES (?, ?, FALSE, ?, FALSE, ?, ?)
                ON CONFLICT (discord_user_id) DO UPDATE SET
                    display_name = EXCLUDED.display_name,
                    administrator = EXCLUDED.administrator,
                    updated_at = EXCLUDED.updated_at
                """, request.discordUserId(), request.displayName(), request.administrator(), now, now);
        return findByDiscordUserId(request.discordUserId())
                .orElseThrow(() -> new IllegalStateException("player profile was not stored"));
    }

    @Override
    @Transactional
    public StoredPlayer activate(ParticipationChange request) {
        synchronizeProfile(request.profile());
        return super.activate(request);
    }

    @Override
    @Transactional
    public StoredPlayer deactivate(ParticipationChange request) {
        synchronizeProfile(request.profile());
        return super.deactivate(request);
    }

    @Override
    @Transactional
    public StoredPlayer setReminderOptIn(ProfileUpdate request, boolean reminderOptIn) {
        synchronizeProfile(request);
        return super.setReminderOptIn(request, reminderOptIn);
    }
}
