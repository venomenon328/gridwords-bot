package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseOffer;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferContext;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferMetadata;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOption;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOptionSelection;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseRound;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseRevalidation;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelectionHistoryEntry;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelection;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelectionSnapshot;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseState;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStatus;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseTopic;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.port.out.ExcuseStateStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL source of truth for persisted excuse state, shown options, cooldown, and selection history. */
@Repository
@Profile("database")
public class PostgresExcuseStateStore implements ExcuseStateStore {

    private static final int COOLDOWN_DAYS = 3;
    private static final int MAX_SELECTION_HISTORY = 10;

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ZoneId businessZone;

    @Autowired
    public PostgresExcuseStateStore(JdbcTemplate jdbc, Clock clock) {
        this(jdbc, clock, clock.getZone());
    }

    public PostgresExcuseStateStore(JdbcTemplate jdbc, Clock clock, ZoneId businessZone) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.businessZone = Objects.requireNonNull(businessZone, "businessZone");
    }

    @Override
    public Optional<ExcuseState> find(long gameResultId) {
        requirePositive(gameResultId, "gameResultId");
        return jdbc.query("""
                SELECT excuse.*, context.original_received_at, context.comparison_game_type,
                    context.compared_result_count, context.all_compared_results_solved,
                    context.highest_solved_attempts, context.longest_duration_seconds, context.context_fingerprint
                FROM game_result_excuse excuse
                LEFT JOIN game_result_excuse_offer_context context ON context.game_result_id = excuse.game_result_id
                WHERE excuse.game_result_id = ?
                """, this::state, gameResultId).stream().findFirst();
    }

    @Override
    @Transactional
    public ExcuseState initializeNotOffered(long gameResultId) {
        requirePositive(gameResultId, "gameResultId");
        Instant now = clock.instant();
        jdbc.update("""
                INSERT INTO game_result_excuse (
                    game_result_id, status, context_generation, reroll_used, created_at, updated_at)
                VALUES (?, 'NOT_OFFERED', 0, FALSE, ?, ?)
                ON CONFLICT (game_result_id) DO NOTHING
                """, gameResultId, utc(now), utc(now));
        return find(gameResultId).orElseThrow(
                () -> new IllegalStateException("excuse state could not be initialized for result " + gameResultId));
    }

    @Override
    @Transactional
    public Optional<ExcuseState> initializeAvailable(ExcuseOffer offer) {
        return initializeAvailableInternal(offer, Optional.empty());
    }

    @Override
    @Transactional
    public Optional<ExcuseState> initializeAvailable(ExcuseOffer offer, ExcuseOfferContext offerContext) {
        return initializeAvailableInternal(offer, Optional.of(Objects.requireNonNull(offerContext, "offerContext")));
    }

    private Optional<ExcuseState> initializeAvailableInternal(
            ExcuseOffer offer, Optional<ExcuseOfferContext> offerContext) {
        Objects.requireNonNull(offer, "offer");
        lockOfferDecision(offer.playerId(), offer.gameType());
        if (find(offer.gameResultId()).isPresent() || !cooldownSatisfiedInternal(
                offer.playerId(), offer.gameType(), offer.metadata().offeredAt())) {
            return Optional.empty();
        }

        ExcuseOfferMetadata metadata = offer.metadata();
        Instant now = clock.instant();
        int inserted = jdbc.update("""
                INSERT INTO game_result_excuse (
                    game_result_id, trigger_source_message_id, status, catalog_version, context_version,
                    context_generation, offered_at, expires_at, reroll_used, created_at, updated_at)
                SELECT result.id, ?, 'AVAILABLE', ?, ?, ?, ?, ?, FALSE, ?, ?
                FROM game_result result
                WHERE result.id = ? AND result.player_id = ? AND result.game_type = ?
                ON CONFLICT (game_result_id) DO NOTHING
                """, metadata.triggerSourceMessageId(), metadata.catalogVersion(), metadata.contextVersion(),
                metadata.contextGeneration(), utc(metadata.offeredAt()), utc(metadata.expiresAt()), utc(now), utc(now),
                offer.gameResultId(), offer.playerId(), offer.gameType().name());
        if (inserted != 1) {
            return Optional.empty();
        }
        offerContext.ifPresent(context -> storeOfferContext(offer.gameResultId(), context, now));
        return find(offer.gameResultId());
    }

    @Override
    @Transactional
    public Optional<ExcuseState> revalidate(ExcuseRevalidation revalidation) {
        Objects.requireNonNull(revalidation, "revalidation");
        Optional<ExcuseState> locked = findForUpdate(revalidation.gameResultId());
        if (locked.isEmpty()) {
            return Optional.empty();
        }
        ExcuseState state = locked.get();
        boolean applies = switch (revalidation.outcome()) {
            case KEEP_AVAILABLE, REPLACE_AVAILABLE_CONTEXT -> state.status() == ExcuseStatus.AVAILABLE;
            case KEEP_SELECTED -> state.status() == ExcuseStatus.SELECTED;
            case INVALIDATE -> state.status() == ExcuseStatus.AVAILABLE || state.status() == ExcuseStatus.SELECTED;
        };
        if (!applies) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        switch (revalidation.outcome()) {
            case KEEP_AVAILABLE -> updateOfferContext(revalidation.gameResultId(), revalidation.offerContext(), now);
            case REPLACE_AVAILABLE_CONTEXT -> {
                jdbc.update("DELETE FROM game_result_excuse_option WHERE game_result_id = ?", revalidation.gameResultId());
                int changed = jdbc.update("""
                        UPDATE game_result_excuse
                        SET context_generation = context_generation + 1, updated_at = ?
                        WHERE game_result_id = ? AND status = 'AVAILABLE'
                        """, utc(now), revalidation.gameResultId());
                if (changed != 1) {
                    throw new IllegalStateException("available excuse state changed during revalidation");
                }
                updateOfferContext(revalidation.gameResultId(), revalidation.offerContext(), now);
            }
            case KEEP_SELECTED -> updateOfferContext(revalidation.gameResultId(), revalidation.offerContext(), now);
            case INVALIDATE -> {
                // A selected snapshot is protected by a deferred foreign key to its persisted option.
                // Keep that immutable evidence when invalidating a selection; available options can be removed.
                if (state.status() == ExcuseStatus.AVAILABLE) {
                    jdbc.update("DELETE FROM game_result_excuse_option WHERE game_result_id = ?", revalidation.gameResultId());
                }
                int changed = jdbc.update("""
                        UPDATE game_result_excuse
                        SET status = 'INVALIDATED', updated_at = ?
                        WHERE game_result_id = ? AND status IN ('AVAILABLE', 'SELECTED')
                        """, utc(now), revalidation.gameResultId());
                if (changed != 1) {
                    throw new IllegalStateException("excuse state changed during invalidation");
                }
                updateOfferContext(revalidation.gameResultId(), revalidation.offerContext(), now);
            }
        }
        return find(revalidation.gameResultId());
    }

    @Override
    public boolean cooldownSatisfied(long playerId, GameType gameType, Instant offeredAt) {
        requirePositive(playerId, "playerId");
        Objects.requireNonNull(gameType, "gameType");
        Objects.requireNonNull(offeredAt, "offeredAt");
        return cooldownSatisfiedInternal(playerId, gameType, offeredAt);
    }

    @Override
    @Transactional
    public Optional<ExcuseState> storeInitialOptions(long gameResultId, int contextGeneration, List<ExcuseOption> options) {
        return storeOptions(gameResultId, contextGeneration, ExcuseRound.INITIAL, options);
    }

    @Override
    @Transactional
    public Optional<ExcuseSelection> loadOrCreateInitialOptions(
            long gameResultId, int contextGeneration, Supplier<ExcuseSelection> optionsFactory) {
        requirePositive(gameResultId, "gameResultId");
        requirePositive(contextGeneration, "contextGeneration");
        Objects.requireNonNull(optionsFactory, "optionsFactory");
        Optional<ExcuseState> locked = findForUpdate(gameResultId);
        if (locked.isEmpty()) {
            return Optional.empty();
        }
        ExcuseState state = locked.get();
        if (state.status() != ExcuseStatus.AVAILABLE
                || state.offer().orElseThrow().contextGeneration() != contextGeneration
                || !clock.instant().isBefore(state.offer().orElseThrow().expiresAt())) {
            return Optional.empty();
        }
        List<ExcuseOption> existing = initialOptions(gameResultId, contextGeneration);
        if (!existing.isEmpty()) {
            if (existing.size() != 3) {
                throw new IllegalStateException("persisted initial excuse options are incomplete");
            }
            return Optional.of(new ExcuseSelection(ExcuseRound.INITIAL, existing));
        }
        ExcuseSelection created = Objects.requireNonNull(optionsFactory.get(), "optionsFactory result");
        if (created.round() != ExcuseRound.INITIAL) {
            throw new IllegalArgumentException("initial option factory must create the initial round");
        }
        validateOptions(ExcuseRound.INITIAL, created.options());
        insertOptions(gameResultId, contextGeneration, created.options(), clock.instant());
        return Optional.of(created);
    }

    @Override
    @Transactional
    public Optional<ExcuseState> storeStyleRerollOptions(
            long gameResultId, int contextGeneration, List<ExcuseOption> options) {
        return storeOptions(gameResultId, contextGeneration, ExcuseRound.STYLE_REROLL, options);
    }

    @Override
    @Transactional
    public Optional<ExcuseState> select(ExcuseOptionSelection selection) {
        Objects.requireNonNull(selection, "selection");
        Optional<ExcuseState> locked = findForUpdate(selection.gameResultId());
        if (locked.isEmpty()) {
            return Optional.empty();
        }
        ExcuseState state = locked.get();
        if (state.status() != ExcuseStatus.AVAILABLE
                || state.offer().orElseThrow().contextGeneration() != selection.contextGeneration()
                || !selection.selectedAt().isBefore(state.offer().orElseThrow().expiresAt())) {
            return Optional.empty();
        }
        ExcuseRound currentRound = state.rerollUsed() ? ExcuseRound.STYLE_REROLL : ExcuseRound.INITIAL;
        Optional<ExcuseOption> option = jdbc.query("""
                SELECT round, position, template_id, style, topic, rendered_text
                FROM game_result_excuse_option
                WHERE game_result_id = ? AND context_generation = ? AND round = ? AND position = ?
                """, (rs, row) -> option(rs), selection.gameResultId(), selection.contextGeneration(),
                currentRound.name(), selection.position()).stream().findFirst();
        if (option.isEmpty()) {
            return Optional.empty();
        }
        ExcuseOption persisted = option.get();
        Instant now = clock.instant();
        int changed = jdbc.update("""
                UPDATE game_result_excuse
                SET status = 'SELECTED', selected_round = ?, selected_position = ?, selected_template_id = ?,
                    selected_style = ?, selected_topic = ?, selected_rendered_text = ?, selected_at = ?, updated_at = ?
                WHERE game_result_id = ? AND status = 'AVAILABLE' AND context_generation = ?
                """, currentRound.name(), persisted.position(), persisted.templateId(), persisted.style().name(),
                persisted.topic().name(), persisted.renderedText(), utc(selection.selectedAt()), utc(now),
                selection.gameResultId(), selection.contextGeneration());
        return changed == 1 ? find(selection.gameResultId()) : Optional.empty();
    }

    @Override
    @Transactional
    public Optional<ExcuseState> decline(long gameResultId, int contextGeneration, Instant declinedAt) {
        requirePositive(gameResultId, "gameResultId");
        requirePositive(contextGeneration, "contextGeneration");
        Objects.requireNonNull(declinedAt, "declinedAt");
        Optional<ExcuseState> locked = findForUpdate(gameResultId);
        if (locked.isEmpty() || locked.get().status() != ExcuseStatus.AVAILABLE
                || locked.get().offer().orElseThrow().contextGeneration() != contextGeneration
                || !declinedAt.isBefore(locked.get().offer().orElseThrow().expiresAt())) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        int changed = jdbc.update("""
                UPDATE game_result_excuse
                SET status = 'DECLINED', updated_at = ?
                WHERE game_result_id = ? AND status = 'AVAILABLE' AND context_generation = ?
                """, utc(now), gameResultId, contextGeneration);
        return changed == 1 ? find(gameResultId) : Optional.empty();
    }

    @Override
    public List<ExcuseState> findDueExpirations(Instant now, int limit) {
        Objects.requireNonNull(now, "now");
        requirePositive(limit, "limit");
        return jdbc.query("""
                SELECT excuse.*, context.original_received_at, context.comparison_game_type,
                    context.compared_result_count, context.all_compared_results_solved,
                    context.highest_solved_attempts, context.longest_duration_seconds, context.context_fingerprint
                FROM game_result_excuse excuse
                LEFT JOIN game_result_excuse_offer_context context ON context.game_result_id = excuse.game_result_id
                WHERE excuse.status = 'AVAILABLE' AND excuse.expires_at <= ?
                ORDER BY excuse.expires_at, excuse.game_result_id
                LIMIT ?
                """, this::state, utc(now), limit);
    }

    @Override
    public List<ExcuseSelectionHistoryEntry> findRecentSelections(long playerId, int limit) {
        requirePositive(playerId, "playerId");
        requirePositive(limit, "limit");
        int boundedLimit = Math.min(limit, MAX_SELECTION_HISTORY);
        return jdbc.query("""
                SELECT excuse.selected_template_id, excuse.selected_topic, excuse.selected_at
                FROM game_result_excuse excuse
                JOIN game_result result ON result.id = excuse.game_result_id
                WHERE result.player_id = ? AND excuse.status = 'SELECTED'
                ORDER BY excuse.selected_at DESC, excuse.game_result_id DESC
                LIMIT ?
                """, (rs, row) -> new ExcuseSelectionHistoryEntry(
                rs.getString("selected_template_id"), ExcuseTopic.valueOf(rs.getString("selected_topic")),
                instant(rs, "selected_at")), playerId, boundedLimit);
    }

    private Optional<ExcuseState> storeOptions(
            long gameResultId, int contextGeneration, ExcuseRound expectedRound, List<ExcuseOption> options) {
        requirePositive(gameResultId, "gameResultId");
        requirePositive(contextGeneration, "contextGeneration");
        validateOptions(expectedRound, options);
        Optional<ExcuseState> locked = findForUpdate(gameResultId);
        if (locked.isEmpty()) {
            return Optional.empty();
        }
        ExcuseState state = locked.get();
        if (state.status() != ExcuseStatus.AVAILABLE
                || state.offer().orElseThrow().contextGeneration() != contextGeneration
                || (expectedRound == ExcuseRound.INITIAL && state.rerollUsed())
                || (expectedRound == ExcuseRound.STYLE_REROLL && state.rerollUsed())) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        insertOptions(gameResultId, contextGeneration, options, now);
        if (expectedRound == ExcuseRound.STYLE_REROLL) {
            int changed = jdbc.update("""
                    UPDATE game_result_excuse
                    SET reroll_used = TRUE, updated_at = ?
                    WHERE game_result_id = ? AND status = 'AVAILABLE' AND reroll_used = FALSE
                    """, utc(now), gameResultId);
            if (changed != 1) {
                throw new IllegalStateException("excuse state changed while storing reroll options");
            }
        }
        return find(gameResultId);
    }

    private Optional<ExcuseState> findForUpdate(long gameResultId) {
        return jdbc.query("""
                SELECT excuse.*, context.original_received_at, context.comparison_game_type,
                    context.compared_result_count, context.all_compared_results_solved,
                    context.highest_solved_attempts, context.longest_duration_seconds, context.context_fingerprint
                FROM game_result_excuse excuse
                LEFT JOIN game_result_excuse_offer_context context ON context.game_result_id = excuse.game_result_id
                WHERE excuse.game_result_id = ?
                FOR UPDATE OF excuse
                """, this::state, gameResultId).stream().findFirst();
    }

    private List<ExcuseOption> initialOptions(long gameResultId, int contextGeneration) {
        return jdbc.query("""
                SELECT round, position, template_id, style, topic, rendered_text
                FROM game_result_excuse_option
                WHERE game_result_id = ? AND context_generation = ? AND round = 'INITIAL'
                ORDER BY position
                """, (rs, row) -> option(rs), gameResultId, contextGeneration);
    }

    private void insertOptions(
            long gameResultId, int contextGeneration, List<ExcuseOption> options, Instant createdAt) {
        for (ExcuseOption option : options) {
            jdbc.update("""
                    INSERT INTO game_result_excuse_option (
                        game_result_id, context_generation, round, position, template_id, style, topic,
                        rendered_text, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, gameResultId, contextGeneration, option.round().name(), option.position(), option.templateId(),
                    option.style().name(), option.topic().name(), option.renderedText(), utc(createdAt));
        }
    }

    private boolean cooldownSatisfiedInternal(long playerId, GameType gameType, Instant offeredAt) {
        Optional<Instant> lastOffer = jdbc.query("""
                SELECT excuse.offered_at
                FROM game_result_excuse excuse
                JOIN game_result result ON result.id = excuse.game_result_id
                WHERE result.player_id = ? AND result.game_type = ? AND excuse.offered_at IS NOT NULL
                ORDER BY excuse.offered_at DESC, excuse.game_result_id DESC
                LIMIT 1
                """, (rs, row) -> instant(rs, "offered_at"), playerId, gameType.name()).stream().findFirst();
        LocalDate firstEligibleDate = LocalDate.ofInstant(offeredAt, businessZone).minusDays(COOLDOWN_DAYS);
        return lastOffer.map(previous -> !LocalDate.ofInstant(previous, businessZone).isAfter(firstEligibleDate)).orElse(true);
    }

    private void lockOfferDecision(long playerId, GameType gameType) {
        // Collisions only serialize otherwise independent offers; the same player/game always uses the same lock.
        long lockKey = 31L * playerId + gameType.ordinal();
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", lockKey);
    }

    private static void validateOptions(ExcuseRound expectedRound, List<ExcuseOption> options) {
        Objects.requireNonNull(expectedRound, "expectedRound");
        options = List.copyOf(Objects.requireNonNull(options, "options"));
        if (options.size() != 3) {
            throw new IllegalArgumentException("exactly three options must be stored atomically");
        }
        Set<Integer> positions = new HashSet<>();
        Set<String> templateIds = new HashSet<>();
        for (ExcuseOption option : options) {
            if (option.round() != expectedRound) {
                throw new IllegalArgumentException("all options must belong to the expected round");
            }
            if (!positions.add(option.position()) || !templateIds.add(option.templateId())) {
                throw new IllegalArgumentException("options must have unique positions and template IDs");
            }
        }
        if (!positions.equals(Set.of(1, 2, 3))) {
            throw new IllegalArgumentException("options must occupy positions 1, 2, and 3");
        }
    }

    private ExcuseState state(ResultSet rs, int row) throws SQLException {
        ExcuseStatus status = ExcuseStatus.valueOf(rs.getString("status"));
        Optional<ExcuseOfferMetadata> offer = optionalOffer(rs);
        Optional<ExcuseSelectionSnapshot> selection = optionalSelection(rs);
        return new ExcuseState(
                rs.getLong("game_result_id"), status, offer, optionalOfferContext(rs), rs.getBoolean("reroll_used"), selection,
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static ExcuseOption option(ResultSet rs) throws SQLException {
        return new ExcuseOption(
                ExcuseRound.valueOf(rs.getString("round")), rs.getInt("position"), rs.getString("template_id"),
                ExcuseStyle.valueOf(rs.getString("style")), ExcuseTopic.valueOf(rs.getString("topic")),
                rs.getString("rendered_text"));
    }

    private static Optional<ExcuseOfferMetadata> optionalOffer(ResultSet rs) throws SQLException {
        Long sourceMessageId = rs.getObject("trigger_source_message_id", Long.class);
        return sourceMessageId == null ? Optional.empty() : Optional.of(new ExcuseOfferMetadata(
                sourceMessageId, rs.getString("catalog_version"), rs.getString("context_version"),
                rs.getInt("context_generation"), instant(rs, "offered_at"), instant(rs, "expires_at")));
    }

    private static Optional<ExcuseSelectionSnapshot> optionalSelection(ResultSet rs) throws SQLException {
        String templateId = rs.getString("selected_template_id");
        return templateId == null ? Optional.empty() : Optional.of(new ExcuseSelectionSnapshot(
                ExcuseRound.valueOf(rs.getString("selected_round")), rs.getInt("selected_position"), templateId,
                ExcuseStyle.valueOf(rs.getString("selected_style")),
                ExcuseTopic.valueOf(rs.getString("selected_topic")), rs.getString("selected_rendered_text"),
                instant(rs, "selected_at")));
    }

    private static Optional<ExcuseOfferContext> optionalOfferContext(ResultSet rs) throws SQLException {
        OffsetDateTime originalReceivedAt = rs.getObject("original_received_at", OffsetDateTime.class);
        if (originalReceivedAt == null) {
            return Optional.empty();
        }
        Integer highestSolvedAttempts = rs.getObject("highest_solved_attempts", Integer.class);
        return Optional.of(new ExcuseOfferContext(
                originalReceivedAt.toInstant(),
                new de.venomenon.gridwordsbot.domain.excuse.DailyComparisonSnapshot(
                        GameType.valueOf(rs.getString("comparison_game_type")),
                        rs.getInt("compared_result_count"),
                        rs.getBoolean("all_compared_results_solved"),
                        highestSolvedAttempts == null ? java.util.OptionalInt.empty()
                                : java.util.OptionalInt.of(highestSolvedAttempts),
                        java.time.Duration.ofSeconds(rs.getLong("longest_duration_seconds"))),
                rs.getString("context_fingerprint")));
    }

    private void storeOfferContext(long gameResultId, ExcuseOfferContext context, Instant now) {
        jdbc.update("""
                INSERT INTO game_result_excuse_offer_context (
                    game_result_id, original_received_at, comparison_game_type, compared_result_count,
                    all_compared_results_solved, highest_solved_attempts, longest_duration_seconds,
                    context_fingerprint, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, gameResultId, utc(context.originalReceivedAt()), context.dailyComparison().gameType().name(),
                context.dailyComparison().comparedResultCount(), context.dailyComparison().allComparedResultsSolved(),
                context.dailyComparison().highestSolvedAttempts().isPresent()
                        ? context.dailyComparison().highestSolvedAttempts().getAsInt() : null,
                context.dailyComparison().longestDuration().toSeconds(), context.contextFingerprint(), utc(now), utc(now));
    }

    private void updateOfferContext(long gameResultId, ExcuseOfferContext context, Instant now) {
        int changed = jdbc.update("""
                UPDATE game_result_excuse_offer_context
                SET context_fingerprint = ?, updated_at = ?
                WHERE game_result_id = ?
                    AND original_received_at = ?
                    AND comparison_game_type = ?
                    AND compared_result_count = ?
                    AND all_compared_results_solved = ?
                    AND highest_solved_attempts IS NOT DISTINCT FROM ?
                    AND longest_duration_seconds = ?
                """, context.contextFingerprint(), utc(now), gameResultId, utc(context.originalReceivedAt()),
                context.dailyComparison().gameType().name(), context.dailyComparison().comparedResultCount(),
                context.dailyComparison().allComparedResultsSolved(),
                context.dailyComparison().highestSolvedAttempts().isPresent()
                        ? context.dailyComparison().highestSolvedAttempts().getAsInt() : null,
                context.dailyComparison().longestDuration().toSeconds());
        if (changed != 1) {
            throw new IllegalStateException("frozen excuse offer context changed unexpectedly");
        }
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
