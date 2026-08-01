package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaim;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryClaimRequest;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryContent;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryExpiration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryFailure;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryFailureCategory;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryKey;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryMetadata;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryPageProgress;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryRegistration;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliverySnapshot;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryScope;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportDeliveryState;
import de.venomenon.gridwordsbot.domain.reporting.ReportDueAt;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
import de.venomenon.gridwordsbot.port.out.PeriodicReportDeliveryStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** PostgreSQL source of truth for short, token-fenced periodic report delivery state transitions. */
public final class PostgresPeriodicReportDeliveryStore implements PeriodicReportDeliveryStore {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PostgresPeriodicReportDeliveryStore(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PeriodicReportDeliverySnapshot register(PeriodicReportDeliveryRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        Instant now = clock.instant();
        PeriodicReportDeliveryKey key = registration.key();
        var metadata = registration.metadata();
        int inserted = jdbc.update("""
                INSERT INTO periodic_report_delivery (
                    guild_id, channel_id, report_type, period_start, period_end,
                    due_local_date, due_local_time, due_zone, due_at, catch_up_ends_at,
                    delivery_state, content_fingerprint, expected_page_count, attempt_count, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, 0, ?, ?)
                ON CONFLICT (guild_id, channel_id, report_type, period_start) DO NOTHING
                """, key.guildId(), key.channelId(), key.reportType().name(), key.periodStart(),
                metadata.period().endDate(), metadata.dueAt().localDate(), metadata.dueAt().localTime(),
                metadata.dueAt().zone().getId(), utc(metadata.dueAt().instant()), utc(metadata.catchUpEndsAt()),
                registration.content().map(PeriodicReportDeliveryContent::fingerprint).orElse(null),
                registration.content().map(PeriodicReportDeliveryContent::expectedPageCount).orElse(null), utc(now), utc(now));
        PeriodicReportDeliverySnapshot snapshot = find(key)
                .orElseThrow(() -> new IllegalStateException("registered periodic report delivery is missing"));
        if (inserted == 0 && !snapshot.registration().equals(registration)) {
            throw new IllegalStateException("conflicting periodic report delivery registration");
        }
        return snapshot;
    }

    @Override
    public Optional<PeriodicReportDeliverySnapshot> find(PeriodicReportDeliveryKey key) {
        Objects.requireNonNull(key, "key");
        return jdbc.query("""
                SELECT id, guild_id, channel_id, report_type, period_start, period_end,
                    due_local_date, due_local_time, due_zone, due_at, catch_up_ends_at,
                    delivery_state, content_fingerprint, expected_page_count, claim_token, claim_until,
                    attempt_count, next_retry_at, failure_category, safe_error, completed_at, created_at, updated_at
                FROM periodic_report_delivery
                WHERE guild_id = ? AND channel_id = ? AND report_type = ? AND period_start = ?
                """, (rs, row) -> snapshot(rs), key.guildId(), key.channelId(), key.reportType().name(), key.periodStart())
                .stream().findFirst();
    }

    @Override
    public Optional<LocalDate> findLatestPeriodStart(PeriodicReportDeliveryScope scope) {
        Objects.requireNonNull(scope, "scope");
        return jdbc.query("""
                SELECT max(period_start) AS period_start
                FROM periodic_report_delivery
                WHERE guild_id = ? AND channel_id = ? AND report_type = ?
                """, (rs, row) -> rs.getObject("period_start", LocalDate.class), scope.guildId(), scope.channelId(),
                scope.reportType().name()).stream().filter(Objects::nonNull).findFirst();
    }

    @Override
    public PeriodicReportDeliverySnapshot expire(PeriodicReportDeliveryExpiration expiration, Instant completedAt) {
        Objects.requireNonNull(expiration, "expiration");
        Objects.requireNonNull(completedAt, "completedAt");
        if (completedAt.isBefore(expiration.metadata().catchUpEndsAt())) {
            throw new IllegalArgumentException("completedAt must not be before catchUpEndsAt");
        }

        PeriodicReportDeliveryKey key = expiration.key();
        PeriodicReportDeliveryMetadata metadata = expiration.metadata();
        Instant updatedAt = clock.instant();
        jdbc.update("""
                INSERT INTO periodic_report_delivery (
                    guild_id, channel_id, report_type, period_start, period_end,
                    due_local_date, due_local_time, due_zone, due_at, catch_up_ends_at,
                    delivery_state, attempt_count, completed_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'EXPIRED', 0, ?, ?, ?)
                ON CONFLICT (guild_id, channel_id, report_type, period_start) DO UPDATE
                SET delivery_state = 'EXPIRED', claim_token = NULL, claim_until = NULL, next_retry_at = NULL,
                    failure_category = NULL, safe_error = NULL, completed_at = EXCLUDED.completed_at,
                    updated_at = EXCLUDED.updated_at
                WHERE periodic_report_delivery.period_end = EXCLUDED.period_end
                  AND periodic_report_delivery.due_local_date = EXCLUDED.due_local_date
                  AND periodic_report_delivery.due_local_time = EXCLUDED.due_local_time
                  AND periodic_report_delivery.due_zone = EXCLUDED.due_zone
                  AND periodic_report_delivery.due_at = EXCLUDED.due_at
                  AND periodic_report_delivery.catch_up_ends_at = EXCLUDED.catch_up_ends_at
                  AND (periodic_report_delivery.delivery_state IN ('OPEN', 'RETRYABLE')
                       OR (periodic_report_delivery.delivery_state = 'CLAIMED'
                           AND periodic_report_delivery.claim_until <= EXCLUDED.completed_at))
                """, key.guildId(), key.channelId(), key.reportType().name(), key.periodStart(),
                metadata.period().endDate(), metadata.dueAt().localDate(), metadata.dueAt().localTime(),
                metadata.dueAt().zone().getId(), utc(metadata.dueAt().instant()), utc(metadata.catchUpEndsAt()),
                utc(completedAt), utc(updatedAt), utc(updatedAt));

        PeriodicReportDeliverySnapshot snapshot = find(key)
                .orElseThrow(() -> new IllegalStateException("expired periodic report delivery is missing"));
        if (!snapshot.registration().key().equals(key) || !snapshot.registration().metadata().equals(metadata)) {
            throw new IllegalStateException("conflicting periodic report delivery expiration");
        }
        return snapshot;
    }

    @Override
    public Optional<PeriodicReportDeliveryClaim> claim(
            PeriodicReportDeliveryKey key, PeriodicReportDeliveryClaimRequest request) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(request, "request");
        UUID token = UUID.randomUUID();
        return jdbc.query("""
                UPDATE periodic_report_delivery
                SET delivery_state = 'CLAIMED', claim_token = ?, claim_until = ?, next_retry_at = NULL,
                    completed_at = NULL, attempt_count = attempt_count + 1, updated_at = ?
                WHERE guild_id = ? AND channel_id = ? AND report_type = ? AND period_start = ?
                  AND due_at <= ?
                  AND ? < catch_up_ends_at
                  AND (
                      delivery_state IN ('OPEN', 'SUCCEEDED')
                      OR (delivery_state = 'RETRYABLE' AND next_retry_at <= ?)
                      OR (delivery_state = 'CLAIMED' AND claim_until <= ?)
                  )
                RETURNING claim_token, claim_until
                """, (rs, row) -> new PeriodicReportDeliveryClaim(
                rs.getObject("claim_token", UUID.class), instant(rs, "claim_until")), token, utc(request.leaseUntil()),
                utc(request.claimedAt()), key.guildId(), key.channelId(), key.reportType().name(), key.periodStart(),
                utc(request.claimedAt()), utc(request.claimedAt()), utc(request.claimedAt()), utc(request.claimedAt()))
                .stream().findFirst();
    }

    @Override
    public boolean recordPage(PeriodicReportDeliveryKey key, UUID claimToken, PeriodicReportDeliveryPageProgress progress) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(claimToken, "claimToken");
        Objects.requireNonNull(progress, "progress");
        return jdbc.update("""
                INSERT INTO periodic_report_delivery_page (delivery_id, page_index, discord_message_id, created_at)
                SELECT delivery.id, ?, ?, ?
                FROM periodic_report_delivery delivery
                WHERE delivery.guild_id = ? AND delivery.channel_id = ?
                  AND delivery.report_type = ? AND delivery.period_start = ?
                  AND delivery.delivery_state = 'CLAIMED' AND delivery.claim_token = ?
                  AND delivery.expected_page_count IS NOT NULL AND ? < delivery.expected_page_count
                  AND ? = (SELECT count(*) FROM periodic_report_delivery_page page WHERE page.delivery_id = delivery.id)
                ON CONFLICT DO NOTHING
                """, progress.pageIndex(), progress.messageId(), utc(clock.instant()), key.guildId(), key.channelId(),
                key.reportType().name(), key.periodStart(), claimToken, progress.pageIndex(), progress.pageIndex()) == 1;
    }

    @Override
    public boolean replacePage(PeriodicReportDeliveryKey key, UUID claimToken, PeriodicReportDeliveryPageProgress progress) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(claimToken, "claimToken");
        Objects.requireNonNull(progress, "progress");
        return jdbc.update("""
                UPDATE periodic_report_delivery_page page
                SET discord_message_id = ?, created_at = ?
                FROM periodic_report_delivery delivery
                WHERE page.delivery_id = delivery.id
                  AND delivery.guild_id = ? AND delivery.channel_id = ?
                  AND delivery.report_type = ? AND delivery.period_start = ?
                  AND delivery.delivery_state = 'CLAIMED' AND delivery.claim_token = ?
                  AND page.page_index = ?
                  AND NOT EXISTS (
                      SELECT 1 FROM periodic_report_delivery_page other
                      WHERE other.delivery_id = delivery.id AND other.discord_message_id = ?
                        AND other.page_index <> page.page_index)
                """, progress.messageId(), utc(clock.instant()), key.guildId(), key.channelId(), key.reportType().name(),
                key.periodStart(), claimToken, progress.pageIndex(), progress.messageId()) == 1;
    }

    @Override
    public boolean replaceContent(
            PeriodicReportDeliveryKey key, UUID claimToken, PeriodicReportDeliveryContent content) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(claimToken, "claimToken");
        Objects.requireNonNull(content, "content");
        Integer changed = jdbc.queryForObject("""
                WITH target AS (
                    UPDATE periodic_report_delivery
                    SET content_fingerprint = ?, expected_page_count = ?, failure_category = NULL, safe_error = NULL,
                        updated_at = ?
                    WHERE guild_id = ? AND channel_id = ? AND report_type = ? AND period_start = ?
                      AND delivery_state = 'CLAIMED' AND claim_token = ?
                    RETURNING id
                ), removed AS (
                    DELETE FROM periodic_report_delivery_page page
                    USING target
                    WHERE page.delivery_id = target.id
                )
                SELECT count(*) FROM target
                """, Integer.class, content.fingerprint(), content.expectedPageCount(), utc(clock.instant()), key.guildId(),
                key.channelId(), key.reportType().name(), key.periodStart(), claimToken);
        return changed != null && changed == 1;
    }
    @Override
    public boolean markSucceeded(PeriodicReportDeliveryKey key, UUID claimToken, Instant completedAt) {
        return transitionToTerminal(key, claimToken, completedAt, "SUCCEEDED", """
                content_fingerprint IS NOT NULL
                AND expected_page_count = (SELECT count(*) FROM periodic_report_delivery_page page WHERE page.delivery_id = periodic_report_delivery.id)
                """);
    }

    @Override
    public boolean markNoOp(PeriodicReportDeliveryKey key, UUID claimToken, Instant completedAt) {
        return transitionToTerminal(key, claimToken, completedAt, "NO_OP", """
                content_fingerprint IS NULL
                AND NOT EXISTS (SELECT 1 FROM periodic_report_delivery_page page WHERE page.delivery_id = periodic_report_delivery.id)
                """);
    }

    @Override
    public boolean markRetryableFailure(
            PeriodicReportDeliveryKey key,
            UUID claimToken,
            PeriodicReportDeliveryFailure failure,
            Instant nextRetryAt) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(claimToken, "claimToken");
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(nextRetryAt, "nextRetryAt");
        if (failure.category() == PeriodicReportDeliveryFailureCategory.PERMANENT) {
            throw new IllegalArgumentException("retryable failure must not be permanent");
        }
        return jdbc.update("""
                UPDATE periodic_report_delivery
                SET delivery_state = 'RETRYABLE', claim_token = NULL, claim_until = NULL, next_retry_at = ?,
                    failure_category = ?, safe_error = ?, completed_at = NULL, updated_at = ?
                WHERE guild_id = ? AND channel_id = ? AND report_type = ? AND period_start = ?
                  AND delivery_state = 'CLAIMED' AND claim_token = ?
                """, utc(nextRetryAt), failure.category().name(), failure.safeMessage(), utc(clock.instant()),
                key.guildId(), key.channelId(), key.reportType().name(), key.periodStart(), claimToken) == 1;
    }

    @Override
    public boolean markPermanentFailure(
            PeriodicReportDeliveryKey key,
            UUID claimToken,
            PeriodicReportDeliveryFailure failure,
            Instant completedAt) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(claimToken, "claimToken");
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(completedAt, "completedAt");
        if (failure.category() != PeriodicReportDeliveryFailureCategory.PERMANENT) {
            throw new IllegalArgumentException("permanent failure needs PERMANENT category");
        }
        return jdbc.update("""
                UPDATE periodic_report_delivery
                SET delivery_state = 'FAILED_PERMANENT', claim_token = NULL, claim_until = NULL, next_retry_at = NULL,
                    failure_category = ?, safe_error = ?, completed_at = ?, updated_at = ?
                WHERE guild_id = ? AND channel_id = ? AND report_type = ? AND period_start = ?
                  AND delivery_state = 'CLAIMED' AND claim_token = ?
                """, failure.category().name(), failure.safeMessage(), utc(completedAt), utc(completedAt),
                key.guildId(), key.channelId(), key.reportType().name(), key.periodStart(), claimToken) == 1;
    }

    @Override
    public boolean markExpired(PeriodicReportDeliveryKey key, Instant completedAt) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(completedAt, "completedAt");
        return jdbc.update("""
                UPDATE periodic_report_delivery
                SET delivery_state = 'EXPIRED', claim_token = NULL, claim_until = NULL, next_retry_at = NULL,
                    completed_at = ?, updated_at = ?
                WHERE guild_id = ? AND channel_id = ? AND report_type = ? AND period_start = ?
                  AND catch_up_ends_at <= ?
                  AND (delivery_state IN ('OPEN', 'RETRYABLE')
                       OR (delivery_state = 'CLAIMED' AND claim_until <= ?))
                """, utc(completedAt), utc(completedAt), key.guildId(), key.channelId(), key.reportType().name(),
                key.periodStart(), utc(completedAt), utc(completedAt)) == 1;
    }

    private boolean transitionToTerminal(
            PeriodicReportDeliveryKey key, UUID claimToken, Instant completedAt, String targetState, String condition) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(claimToken, "claimToken");
        Objects.requireNonNull(completedAt, "completedAt");
        return jdbc.update("""
                UPDATE periodic_report_delivery
                SET delivery_state = ?, claim_token = NULL, claim_until = NULL, next_retry_at = NULL,
                    failure_category = NULL, safe_error = NULL, completed_at = ?, updated_at = ?
                WHERE guild_id = ? AND channel_id = ? AND report_type = ? AND period_start = ?
                  AND delivery_state = 'CLAIMED' AND claim_token = ?
                  AND (""" + condition + ")", targetState, utc(completedAt), utc(completedAt), key.guildId(),
                key.channelId(), key.reportType().name(), key.periodStart(), claimToken) == 1;
    }

    private PeriodicReportDeliverySnapshot snapshot(ResultSet rs) throws SQLException {
        long deliveryId = rs.getLong("id");
        ReportPeriod period = new ReportPeriod(date(rs, "period_start"), date(rs, "period_end"));
        ReportDueAt dueAt = new ReportDueAt(date(rs, "due_local_date"), rs.getObject("due_local_time", LocalTime.class),
                ZoneId.of(rs.getString("due_zone")));
        PeriodicReportDeliveryRegistration registration = new PeriodicReportDeliveryRegistration(
                new PeriodicReportDeliveryKey(rs.getLong("guild_id"), rs.getLong("channel_id"),
                        ReportType.valueOf(rs.getString("report_type")), date(rs, "period_start")),
                new PeriodicReportDeliveryMetadata(period, dueAt, instant(rs, "catch_up_ends_at")), optionalContent(rs));
        PeriodicReportDeliveryState state = PeriodicReportDeliveryState.valueOf(rs.getString("delivery_state"));
        Optional<UUID> claimToken = optionalUuid(rs, "claim_token");
        Optional<PeriodicReportDeliveryClaim> claim = claimToken.map(token -> {
            try {
                return new PeriodicReportDeliveryClaim(token, instant(rs, "claim_until"));
            } catch (SQLException exception) {
                throw new IllegalStateException("unable to read periodic report delivery claim", exception);
            }
        });
        Optional<PeriodicReportDeliveryFailure> failure = optionalString(rs, "failure_category").map(category -> {
            try {
                return new PeriodicReportDeliveryFailure(
                        PeriodicReportDeliveryFailureCategory.valueOf(category), rs.getString("safe_error"));
            } catch (SQLException exception) {
                throw new IllegalStateException("unable to read periodic report delivery failure", exception);
            }
        });
        return new PeriodicReportDeliverySnapshot(registration, state, claim, rs.getInt("attempt_count"),
                optionalInstant(rs, "next_retry_at"), failure, pageProgress(deliveryId), optionalInstant(rs, "completed_at"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private List<PeriodicReportDeliveryPageProgress> pageProgress(long deliveryId) {
        return jdbc.query("""
                SELECT page_index, discord_message_id
                FROM periodic_report_delivery_page
                WHERE delivery_id = ?
                ORDER BY page_index
                """, (rs, row) -> new PeriodicReportDeliveryPageProgress(
                rs.getInt("page_index"), rs.getLong("discord_message_id")), deliveryId);
    }

    private static Optional<PeriodicReportDeliveryContent> optionalContent(ResultSet rs) throws SQLException {
        String fingerprint = rs.getString("content_fingerprint");
        return fingerprint == null ? Optional.empty() : Optional.of(new PeriodicReportDeliveryContent(
                fingerprint, rs.getInt("expected_page_count")));
    }

    private static Optional<String> optionalString(ResultSet rs, String column) throws SQLException {
        return Optional.ofNullable(rs.getString(column));
    }

    private static Optional<UUID> optionalUuid(ResultSet rs, String column) throws SQLException {
        return Optional.ofNullable(rs.getObject(column, UUID.class));
    }

    private static Optional<Instant> optionalInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static LocalDate date(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDate.class);
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
