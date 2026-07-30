package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.port.out.DailyStatusStore;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL compare-and-set claims. Every public operation is a short database transaction. */
@Repository
@Profile("database")
public class PostgresDailyStatusStore implements DailyStatusStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public PostgresDailyStatusStore(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    @Override @Transactional
    public Optional<StatusDelivery> claimStatus(long guildId, long channelId, LocalDate date, Instant leaseUntil) {
        UUID token = UUID.randomUUID(); Instant now = clock.instant();
        jdbc.update("""
INSERT INTO daily_status_message (guild_id, channel_id, game_date, delivery_state, created_at, updated_at)
                VALUES (?, ?, ?, 'PENDING', ?, ?) ON CONFLICT (guild_id, channel_id, game_date) DO NOTHING
""",
                guildId, channelId, date, utc(now), utc(now));
        return jdbc.query("""
UPDATE daily_status_message SET delivery_state = 'CLAIMED', claim_token = ?, claim_until = ?, updated_at = ?
                WHERE guild_id = ? AND channel_id = ? AND game_date = ?
                  AND (delivery_state IN ('PENDING','RETRYABLE','PERMANENT') OR claim_until IS NULL OR claim_until < ?)
                RETURNING bot_message_id
""", (rs, row) -> new StatusDelivery(guildId, channelId, date, token,
                optionalLong(rs, "bot_message_id")), token, utc(leaseUntil), utc(now), guildId, channelId, date, utc(now)).stream().findFirst();
    }
    @Override @Transactional
    public void completeStatus(StatusDelivery claim, long messageId, String fingerprint) {
        updateExactlyOne("""
UPDATE daily_status_message SET bot_message_id = ?, content_fingerprint = ?, delivery_state = 'DELIVERED',
                claim_token = NULL, claim_until = NULL, last_error = NULL, updated_at = ? WHERE guild_id = ? AND channel_id = ? AND game_date = ? AND claim_token = ?
""",
                messageId, fingerprint, utc(clock.instant()), claim.guildId(), claim.channelId(), claim.gameDate(), claim.claimToken());
    }
    @Override @Transactional
    public void failStatus(StatusDelivery claim, String safeError, boolean permanent) {
        updateExactlyOne("""
UPDATE daily_status_message SET delivery_state = ?, claim_token = NULL, claim_until = NULL, last_error = ?, updated_at = ?
                WHERE guild_id = ? AND channel_id = ? AND game_date = ? AND claim_token = ?
""", permanent ? "PERMANENT" : "RETRYABLE", safeError,
                utc(clock.instant()), claim.guildId(), claim.channelId(), claim.gameDate(), claim.claimToken());
    }
    @Override @Transactional
    public Optional<ReminderDelivery> claimReminder(long guildId, long channelId, LocalDate date, int stage, LocalTime time, Instant leaseUntil) {
        UUID token = UUID.randomUUID(); Instant now = clock.instant();
        jdbc.update("""
INSERT INTO reminder_delivery (guild_id, channel_id, game_date, reminder_stage, scheduled_time, delivery_state, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'PENDING', ?, ?) ON CONFLICT (guild_id, channel_id, game_date, reminder_stage) DO NOTHING
""",
                guildId, channelId, date, stage, time, utc(now), utc(now));
        return jdbc.query("""
UPDATE reminder_delivery SET delivery_state = 'CLAIMED', claim_token = ?, claim_until = ?, updated_at = ?
                WHERE guild_id = ? AND channel_id = ? AND game_date = ? AND reminder_stage = ?
                  AND (delivery_state IN ('PENDING','RETRYABLE') OR claim_until IS NULL OR claim_until < ?) RETURNING scheduled_time
""",
                (rs, row) -> new ReminderDelivery(guildId, channelId, date, stage, rs.getObject("scheduled_time", LocalTime.class), token), token,
                utc(leaseUntil), utc(now), guildId, channelId, date, stage, utc(now)).stream().findFirst();
    }
    @Override @Transactional
    public void completeReminder(ReminderDelivery claim, ReminderState state, Optional<Long> messageId) {
        updateExactlyOne("""
UPDATE reminder_delivery SET delivery_state = ?, bot_message_id = ?, claim_token = NULL, claim_until = NULL,
                last_error = NULL, updated_at = ? WHERE guild_id = ? AND channel_id = ? AND game_date = ? AND reminder_stage = ? AND claim_token = ?
""",
                state.name(), messageId.orElse(null), utc(clock.instant()), claim.guildId(), claim.channelId(), claim.gameDate(), claim.stage(), claim.claimToken());
    }
    @Override @Transactional
    public void failReminder(ReminderDelivery claim, String safeError, boolean permanent) {
        updateExactlyOne("""
UPDATE reminder_delivery SET delivery_state = ?, claim_token = NULL, claim_until = NULL, last_error = ?, updated_at = ?
                WHERE guild_id = ? AND channel_id = ? AND game_date = ? AND reminder_stage = ? AND claim_token = ?
""", permanent ? "PERMANENT" : "RETRYABLE",
                safeError, utc(clock.instant()), claim.guildId(), claim.channelId(), claim.gameDate(), claim.stage(), claim.claimToken());
    }
    @Override @Transactional
    public void expireOpenRemindersBefore(long guildId, long channelId, LocalDate today) {
        jdbc.update("""
UPDATE reminder_delivery SET delivery_state = 'EXPIRED', claim_token = NULL, claim_until = NULL, updated_at = ?
                WHERE guild_id = ? AND channel_id = ? AND game_date < ? AND delivery_state IN ('PENDING','RETRYABLE','CLAIMED')
""", utc(clock.instant()), guildId, channelId, today);
    }
    private void updateExactlyOne(String sql, Object... args) { if (jdbc.update(sql, args) != 1) throw new IllegalStateException("delivery claim was lost"); }
    private static Optional<Long> optionalLong(ResultSet rs, String name) throws java.sql.SQLException { long value = rs.getLong(name); return rs.wasNull() ? Optional.empty() : Optional.of(value); }
    private static OffsetDateTime utc(Instant instant) { return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC); }
}
