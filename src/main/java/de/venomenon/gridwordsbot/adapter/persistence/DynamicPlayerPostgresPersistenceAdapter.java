package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.port.out.ReminderCandidateStore.ReminderCandidate;
import de.venomenon.gridwordsbot.port.out.SubmissionStore.ResultStorage;
import de.venomenon.gridwordsbot.port.out.SubmissionStore.StoredSubmission;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database-profile specialization for dynamic players.
 *
 * <p>The established persistence adapter remains the single implementation of result, publication and deletion
 * state. This specialization adds a stable transaction-wide lock before a valid result can mutate participant
 * periods, synchronizes the externally configured administrator flag, and applies reminder opt-out semantics when
 * an unknown or inactive player becomes active.</p>
 */
@Repository
@Primary
@Profile("database")
public class DynamicPlayerPostgresPersistenceAdapter extends PostgresPersistenceAdapter {
    private static final long PARTICIPATION_CONTEXT_LOCK = 7_320_019L;

    private final JdbcTemplate jdbc;

    public DynamicPlayerPostgresPersistenceAdapter(JdbcTemplate jdbc, Clock clock, ZoneId businessZone) {
        super(jdbc, clock, businessZone);
        this.jdbc = jdbc;
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
    public List<ReminderCandidate> findReminderCandidates(LocalDate gameDate) {
        return jdbc.query("""
                SELECT p.discord_user_id, p.display_name, p.reminder_opt_in,
                    NOT EXISTS (
                        SELECT 1 FROM game_result r
                        WHERE r.player_id = p.discord_user_id
                          AND r.game_type = 'GRIDWORDS'
                          AND r.game_date = ?
                    ) AS missing_gridwords,
                    NOT EXISTS (
                        SELECT 1 FROM game_result r
                        WHERE r.player_id = p.discord_user_id
                          AND r.game_type = 'QUADWORDS'
                          AND r.game_date = ?
                    ) AS missing_quadwords
                FROM player p
                WHERE EXISTS (
                    SELECT 1
                    FROM player_participation_period pp
                    WHERE pp.player_id = p.discord_user_id
                      AND pp.active_from <= ?
                      AND (pp.inactive_from IS NULL OR ? < pp.inactive_from)
                )
                ORDER BY LOWER(p.display_name), p.discord_user_id
                """, (rs, row) -> {
                    List<GameType> missing = new java.util.ArrayList<>();
                    if (rs.getBoolean("missing_gridwords")) missing.add(GameType.GRIDWORDS);
                    if (rs.getBoolean("missing_quadwords")) missing.add(GameType.QUADWORDS);
                    return missing.isEmpty() ? null : new ReminderCandidate(
                            rs.getLong("discord_user_id"),
                            rs.getString("display_name"),
                            missing,
                            rs.getBoolean("reminder_opt_in"));
                }, gameDate, gameDate, gameDate, gameDate).stream()
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
