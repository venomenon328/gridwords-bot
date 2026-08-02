package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.port.out.ChannelMessageRetirementStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL claims for retiring visible bot messages without touching business result rows. */
@Repository
@Profile("database")
public class PostgresChannelMessageRetirementStore implements ChannelMessageRetirementStore {
    private static final Duration RETRY_DELAY = Duration.ofMinutes(5);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PostgresChannelMessageRetirementStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public List<ResultMessage> findResultMessagesBefore(long guildId, long channelId, LocalDate before) {
        Instant now = clock.instant();
        return jdbc.query("""
                SELECT DISTINCT r.id, r.canonical_message_id, r.game_date, r.game_type
                FROM game_result r
                JOIN submission s ON s.game_result_id = r.id
                LEFT JOIN canonical_result_retirement retirement ON retirement.game_result_id = r.id
                WHERE s.guild_id = ? AND s.channel_id = ?
                  AND r.game_date < ?
                  AND (retirement.game_result_id IS NULL
                    OR retirement.retirement_state = 'ACTIVE'
                    OR (retirement.retirement_state = 'RETRYABLE'
                        AND (retirement.retry_after IS NULL OR retirement.retry_after <= ?))
                    OR (retirement.retirement_state = 'CLAIMED'
                        AND retirement.claim_until < ?))
                ORDER BY r.game_date, r.game_type, r.id
                """, (rs, row) -> {
                    long resultId = rs.getLong("id");
                    String gameType = rs.getString("game_type").toLowerCase(Locale.ROOT);
                    return new ResultMessage(
                            resultId,
                            channelId,
                            optionalLong(rs, "canonical_message_id"),
                            rs.getObject("game_date", LocalDate.class),
                            gameType + "-result-" + resultId);
                }, guildId, channelId, before, utc(now), utc(now));
    }

    @Override
    public List<ReminderMessage> findReminderMessagesBefore(long guildId, long channelId, LocalDate before) {
        Instant now = clock.instant();
        return jdbc.query("""
                SELECT delivery.game_date, delivery.reminder_stage, delivery.discord_message_id
                FROM reminder_delivery delivery
                LEFT JOIN reminder_message_retirement retirement
                  ON retirement.guild_id = delivery.guild_id
                 AND retirement.channel_id = delivery.channel_id
                 AND retirement.game_date = delivery.game_date
                 AND retirement.reminder_stage = delivery.reminder_stage
                WHERE delivery.guild_id = ? AND delivery.channel_id = ?
                  AND delivery.game_date < ?
                  AND (retirement.guild_id IS NULL
                    OR retirement.retirement_state = 'ACTIVE'
                    OR (retirement.retirement_state = 'RETRYABLE'
                        AND (retirement.retry_after IS NULL OR retirement.retry_after <= ?))
                    OR (retirement.retirement_state = 'CLAIMED'
                        AND retirement.claim_until < ?))
                ORDER BY delivery.game_date, delivery.reminder_stage
                """, (rs, row) -> new ReminderMessage(
                        guildId,
                        channelId,
                        rs.getObject("game_date", LocalDate.class),
                        rs.getInt("reminder_stage"),
                        optionalLong(rs, "discord_message_id")),
                guildId, channelId, before, utc(now), utc(now));
    }

    @Override
    public List<ReminderMessage> findFirstReminderMessagesReadyForRetirement(
            long guildId, long channelId, LocalDate date) {
        Instant now = clock.instant();
        return jdbc.query("""
                SELECT first_stage.discord_message_id
                FROM reminder_delivery first_stage
                JOIN reminder_delivery second_stage
                  ON second_stage.guild_id = first_stage.guild_id
                 AND second_stage.channel_id = first_stage.channel_id
                 AND second_stage.game_date = first_stage.game_date
                 AND second_stage.reminder_stage = 2
                LEFT JOIN reminder_message_retirement retirement
                  ON retirement.guild_id = first_stage.guild_id
                 AND retirement.channel_id = first_stage.channel_id
                 AND retirement.game_date = first_stage.game_date
                 AND retirement.reminder_stage = first_stage.reminder_stage
                WHERE first_stage.guild_id = ? AND first_stage.channel_id = ?
                  AND first_stage.game_date = ? AND first_stage.reminder_stage = 1
                  AND ((second_stage.delivery_state = 'SENT' AND second_stage.discord_message_id IS NOT NULL)
                    OR second_stage.delivery_state = 'NO_CANDIDATES')
                  AND (retirement.guild_id IS NULL
                    OR retirement.retirement_state = 'ACTIVE'
                    OR (retirement.retirement_state = 'RETRYABLE'
                        AND (retirement.retry_after IS NULL OR retirement.retry_after <= ?))
                    OR (retirement.retirement_state = 'CLAIMED'
                        AND retirement.claim_until < ?))
                """, (rs, row) -> new ReminderMessage(
                        guildId, channelId, date, 1, optionalLong(rs, "discord_message_id")),
                guildId, channelId, date, utc(now), utc(now));
    }

    @Override
    @Transactional
    public Optional<ResultRetirementClaim> claimResultMessage(long resultId, Instant leaseUntil) {
        UUID token = UUID.randomUUID();
        Instant now = clock.instant();

        // The CTE locks the same game_result row that publication updates and inserts the durable retirement intent
        // in the same statement. Therefore publication and retirement cannot both win the same race.
        return jdbc.query("""
                WITH locked_result AS (
                    SELECT id
                    FROM game_result
                    WHERE id = ? AND canonical_publish_claim_token IS NULL
                    FOR UPDATE
                )
                INSERT INTO canonical_result_retirement
                    (game_result_id, retirement_state, claim_token, claim_until, created_at, updated_at)
                SELECT id, 'CLAIMED', ?, ?, ?, ?
                FROM locked_result
                ON CONFLICT (game_result_id) DO UPDATE
                SET retirement_state = 'CLAIMED', claim_token = EXCLUDED.claim_token,
                    claim_until = EXCLUDED.claim_until, updated_at = EXCLUDED.updated_at
                WHERE canonical_result_retirement.retirement_state = 'ACTIVE'
                   OR (canonical_result_retirement.retirement_state = 'RETRYABLE'
                       AND (canonical_result_retirement.retry_after IS NULL
                            OR canonical_result_retirement.retry_after <= EXCLUDED.updated_at))
                   OR (canonical_result_retirement.retirement_state = 'CLAIMED'
                       AND canonical_result_retirement.claim_until < EXCLUDED.updated_at)
                RETURNING game_result_id
                """, (rs, row) -> new ResultRetirementClaim(rs.getLong("game_result_id"), token, leaseUntil),
                resultId, token, utc(leaseUntil), utc(now), utc(now)).stream().findFirst();
    }

    @Override
    @Transactional
    public Optional<ReminderRetirementClaim> claimReminderMessage(
            long guildId, long channelId, LocalDate date, int stage, Instant leaseUntil) {
        UUID token = UUID.randomUUID();
        Instant now = clock.instant();
        return jdbc.query("""
                INSERT INTO reminder_message_retirement
                    (guild_id, channel_id, game_date, reminder_stage, retirement_state,
                     claim_token, claim_until, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'CLAIMED', ?, ?, ?, ?)
                ON CONFLICT (guild_id, channel_id, game_date, reminder_stage) DO UPDATE
                SET retirement_state = 'CLAIMED', claim_token = EXCLUDED.claim_token,
                    claim_until = EXCLUDED.claim_until, updated_at = EXCLUDED.updated_at
                WHERE reminder_message_retirement.retirement_state = 'ACTIVE'
                   OR (reminder_message_retirement.retirement_state = 'RETRYABLE'
                       AND (reminder_message_retirement.retry_after IS NULL
                            OR reminder_message_retirement.retry_after <= EXCLUDED.updated_at))
                   OR (reminder_message_retirement.retirement_state = 'CLAIMED'
                       AND reminder_message_retirement.claim_until < EXCLUDED.updated_at)
                RETURNING reminder_stage
                """, (rs, row) -> new ReminderRetirementClaim(
                        guildId, channelId, date, rs.getInt("reminder_stage"), token, leaseUntil),
                guildId, channelId, date, stage, token, utc(leaseUntil), utc(now), utc(now))
                .stream().findFirst();
    }

    @Override
    @Transactional
    public void completeResultRetirement(ResultRetirementClaim claim) {
        Instant now = clock.instant();
        updateExactlyOne("""
                UPDATE canonical_result_retirement
                SET retirement_state = 'RETIRED', claim_token = NULL, claim_until = NULL,
                    retry_after = NULL, last_error = NULL, retired_at = ?, updated_at = ?
                WHERE game_result_id = ? AND claim_token = ?
                """, utc(now), utc(now), claim.resultId(), claim.token());
    }

    @Override
    @Transactional
    public void completeReminderRetirement(ReminderRetirementClaim claim) {
        Instant now = clock.instant();
        updateExactlyOne("""
                UPDATE reminder_message_retirement
                SET retirement_state = 'RETIRED', claim_token = NULL, claim_until = NULL,
                    retry_after = NULL, last_error = NULL, retired_at = ?, updated_at = ?
                WHERE guild_id = ? AND channel_id = ? AND game_date = ? AND reminder_stage = ? AND claim_token = ?
                """, utc(now), utc(now), claim.guildId(), claim.channelId(),
                claim.gameDate(), claim.stage(), claim.token());
    }

    @Override
    @Transactional
    public void failResultRetirement(ResultRetirementClaim claim, String safeError, boolean permanent) {
        Instant now = clock.instant();
        updateExactlyOne("""
                UPDATE canonical_result_retirement
                SET retirement_state = ?, claim_token = NULL, claim_until = NULL, retry_after = ?,
                    last_error = ?, updated_at = ?
                WHERE game_result_id = ? AND claim_token = ?
                """, permanent ? "PERMANENT" : "RETRYABLE",
                permanent ? null : utc(now.plus(RETRY_DELAY)), safeError, utc(now),
                claim.resultId(), claim.token());
    }

    @Override
    @Transactional
    public void failReminderRetirement(ReminderRetirementClaim claim, String safeError, boolean permanent) {
        Instant now = clock.instant();
        updateExactlyOne("""
                UPDATE reminder_message_retirement
                SET retirement_state = ?, claim_token = NULL, claim_until = NULL, retry_after = ?,
                    last_error = ?, updated_at = ?
                WHERE guild_id = ? AND channel_id = ? AND game_date = ? AND reminder_stage = ? AND claim_token = ?
                """, permanent ? "PERMANENT" : "RETRYABLE",
                permanent ? null : utc(now.plus(RETRY_DELAY)), safeError, utc(now),
                claim.guildId(), claim.channelId(), claim.gameDate(), claim.stage(), claim.token());
    }

    @Override
    public boolean isCanonicalPublicationAllowed(long resultId) {
        Integer blocked = jdbc.queryForObject("""
                SELECT count(*) FROM canonical_result_retirement
                WHERE game_result_id = ? AND retirement_state <> 'ACTIVE'
                """, Integer.class, resultId);
        return blocked == null || blocked == 0;
    }

    private void updateExactlyOne(String sql, Object... args) {
        if (jdbc.update(sql, args) != 1) {
            throw new IllegalStateException("retirement claim was lost");
        }
    }

    private static OptionalLong optionalLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? OptionalLong.empty() : OptionalLong.of(value);
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
