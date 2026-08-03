package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameParticipationSelection;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoard;
import de.venomenon.gridwordsbot.domain.model.QuadWordsBoards;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionConflictException;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** PostgreSQL-specific, conflict-safe implementation of the persistence ports. */
@Repository
@Profile("database")
public class PostgresPersistenceAdapter implements PlayerStore, GameResultStore, SubmissionStore {

    private static final String CURRENT_BOARDLESS_QUADWORDS_VERSION = "quadwords-share-v2";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final ZoneId businessZone;

    public PostgresPersistenceAdapter(JdbcTemplate jdbc, Clock clock) {
        this(jdbc, clock, clock.getZone());
    }

    public PostgresPersistenceAdapter(JdbcTemplate jdbc, Clock clock, ZoneId businessZone) {
        this.jdbc = java.util.Objects.requireNonNull(jdbc);
        this.clock = java.util.Objects.requireNonNull(clock);
        this.businessZone = java.util.Objects.requireNonNull(businessZone);
    }

    @Override
    @Transactional
    public StoredPlayer upsert(PlayerUpsert request) {
        ProfileUpdate profile = new ProfileUpdate(
                request.discordUserId(), request.displayName(), request.administrator());
        LocalDate today = LocalDate.now(clock.withZone(businessZone));
        return request.active()
                ? activateGames(new GameParticipationChange(profile, GameParticipationSelection.BOTH, today))
                : deactivateGames(new GameParticipationChange(profile, GameParticipationSelection.BOTH, today));
    }

    @Override
    @Transactional
    public StoredPlayer synchronizeProfile(ProfileUpdate request) {
        OffsetDateTime now = databaseTime(clock.instant());
        jdbc.update("""
                INSERT INTO player (discord_user_id, display_name, active, administrator, reminder_opt_in, created_at, updated_at)
                VALUES (?, ?, FALSE, ?, FALSE, ?, ?)
                ON CONFLICT (discord_user_id) DO UPDATE SET
                    display_name = EXCLUDED.display_name,
                    administrator = EXCLUDED.administrator,
                    updated_at = EXCLUDED.updated_at
                """, request.discordUserId(), request.displayName(), request.administrator(), now, now);
        return findPlayer(request.discordUserId());
    }
    @Override
    public Optional<StoredPlayer> findByDiscordUserId(long discordUserId) {
        if (participationEnabled()) synchronizeActiveStatuses();
        return jdbc.query("SELECT * FROM player WHERE discord_user_id = ?", PLAYER, discordUserId).stream().findFirst();
    }

    @Override
    public StoredGameResult upsert(GameResultUpsert request) {
        return upsertResult(request, clock.instant());
    }

    @Override
    public Optional<StoredGameResult> find(long playerId, GameType gameType, java.time.LocalDate gameDate) {
        return jdbc.query("SELECT * FROM game_result WHERE player_id = ? AND game_type = ? AND game_date = ?", RESULT,
                playerId, gameType.name(), gameDate).stream().findFirst();
    }

    @Override
    public StoredGameResult setCanonicalMessageId(long resultId, long canonicalMessageId) {
        if (resultId <= 0 || canonicalMessageId <= 0) {
            throw new IllegalArgumentException("IDs must be positive");
        }
        StoredGameResult result = jdbc.queryForObject("""
                UPDATE game_result SET canonical_message_id = ?, updated_at = ?, version = version + 1
                WHERE id = ? RETURNING *
                """, RESULT, canonicalMessageId, databaseTime(clock.instant()), resultId);
        if (result == null) {
            throw new IllegalStateException("game result not found: " + resultId);
        }
        return result;
    }

    @Override
    @Transactional
    public StoredSubmission register(SubmissionRegistration registration) {
        Instant now = clock.instant();
        int inserted = jdbc.update("""
                INSERT INTO submission (source_message_id, guild_id, channel_id, author_player_id, raw_message_content,
                    processing_state, received_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'RECEIVED', ?, ?)
                ON CONFLICT (source_message_id) DO NOTHING
                """, registration.sourceMessageId(), registration.guildId(), registration.channelId(),
                registration.authorPlayerId(), registration.rawMessageContent(), databaseTime(registration.receivedAt()),
                databaseTime(now));
        if (inserted == 1) {
            for (AttachmentSnapshot attachment : registration.attachments()) {
                jdbc.update("""
                        INSERT INTO submission_attachment (source_message_id, attachment_index, file_name, content_type, size_bytes)
                        VALUES (?, ?, ?, ?, ?)
                        """, registration.sourceMessageId(), attachment.index(), attachment.fileName(),
                        attachment.contentType().orElse(null), attachment.sizeBytes());
            }
        }
        StoredSubmission stored = findRequired(registration.sourceMessageId());
        if (!sameSubmission(stored, registration)) {
            throw new SubmissionConflictException("source message ID is already registered with different immutable data");
        }
        return stored;
    }

    @Override
    public Optional<StoredSubmission> findBySourceMessageId(long sourceMessageId) {
        return jdbc.query("SELECT * FROM submission WHERE source_message_id = ?", SUBMISSION, sourceMessageId)
                .stream().findFirst();
    }

    /**
     * Serializes a submission's result link. An equivalent replay is read-only; a different replay is a conflict.
     */
    @Override
    @Transactional
    public StoredSubmission storeResult(ResultStorage request) {
        StoredSubmission existing = lockRequired(request.sourceMessageId());
        if (existing.authorPlayerId() != request.result().playerId()) {
            throw new SubmissionConflictException("result player does not match submission author");
        }
        if (existing.state() == SubmissionState.RESULT_STORED
                || existing.state() == SubmissionState.FAILED_RETRYABLE
                || existing.state() == SubmissionState.SUPERSEDED) {
            StoredGameResult storedResult = findResultById(existing.gameResultId().orElseThrow(
                    () -> new IllegalStateException("stored submission has no linked game result")));
            if (equivalent(storedResult, retainStoredQuadWordsBoards(request, storedResult).result())) {
                return existing;
            }
            throw new SubmissionConflictException("source message ID is already linked to a different result");
        }
        if (existing.state() != SubmissionState.RECEIVED && existing.state() != SubmissionState.VALIDATED) {
            throw new SubmissionConflictException("submission state does not allow result storage: " + existing.state());
        }

        if (participationEnabled()) activateGames(request.playerRegistration());
        lockParticipationTableForPublicationContext();
        List<StoredGameResult> before = findAll();
        List<ParticipationPeriod> periods = participationEnabled() ? findParticipationPeriods() : List.of();
        Optional<StoredGameResult> existingResult = findResultForUpdate(request.result());
        if (existingResult.isPresent()) {
            return storeAgainstExistingResult(retainStoredQuadWordsBoards(request, existingResult.get()),
                    existingResult.get(), before, periods);
        }

        Optional<StoredGameResult> insertedResult = insertResultIfAbsent(request.result(), clock.instant());
        if (insertedResult.isEmpty()) {
            StoredGameResult concurrentResult = findResultForUpdate(request.result())
                    .orElseThrow(() -> new IllegalStateException("concurrently inserted game result was not found"));
            return storeAgainstExistingResult(retainStoredQuadWordsBoards(request, concurrentResult),
                    concurrentResult, before, periods);
        }

        StoredGameResult result = insertedResult.get();
        PublicationContext publicationContext = publicationContext(before, findAll(), request.result().playerId(),
                result.parsedResult().gameDate(), periods);
        linkStoredResult(request.sourceMessageId(), result.id(), publicationContext);
        prepareCanonicalPublication(request.sourceMessageId(), result.id());
        return findRequired(request.sourceMessageId());
    }

    private StoredSubmission storeAgainstExistingResult(
            ResultStorage request,
            StoredGameResult existingResult,
            List<StoredGameResult> before,
            List<ParticipationPeriod> periods) {
        linkStoredResult(request.sourceMessageId(), existingResult.id(), PublicationContext.none());
        CanonicalPublicationPreparation preparation = prepareCanonicalPublication(request.sourceMessageId(), existingResult.id());
        if (preparation == CanonicalPublicationPreparation.SUPERSEDED) return findRequired(request.sourceMessageId());
        if (preparation != CanonicalPublicationPreparation.PUBLISHABLE) {
            throw new SubmissionConflictException("new submission cannot already be canonically published");
        }
        StoredGameResult result = upsertResult(request.result(), clock.instant());
        PublicationContext publicationContext = publicationContext(before, findAll(), request.result().playerId(),
                result.parsedResult().gameDate(), periods);
        updatePublicationContext(request.sourceMessageId(), result.id(), publicationContext);
        return findRequired(request.sourceMessageId());
    }
    /**
     * A later text-only QuadWords share cannot erase boards recovered from an earlier image-backed share.
     */
    private static ResultStorage retainStoredQuadWordsBoards(ResultStorage request, StoredGameResult storedResult) {
        ParsedGameResult incoming = request.result().parsedResult();
        Optional<QuadWordsBoards> storedBoards = storedResult.parsedResult().quadWordsBoards();
        if (incoming.gameType() != GameType.QUADWORDS || incoming.quadWordsBoards().isPresent()
                || storedBoards.isEmpty() || !CURRENT_BOARDLESS_QUADWORDS_VERSION.equals(request.result().parserVersion())) {
            return request;
        }
        ParsedGameResult retained = new ParsedGameResult(
                incoming.gameType(), incoming.gameDate(), incoming.outcome(), incoming.duration(),
                incoming.gridgamesStreak(), incoming.board(), storedBoards);
        GameResultUpsert result = new GameResultUpsert(
                request.result().playerId(), retained, request.result().rawShareText(), storedResult.parserVersion());
        return new ResultStorage(request.sourceMessageId(), result, request.playerRegistration());
    }

    private void linkStoredResult(long sourceMessageId, long resultId, PublicationContext publicationContext) {
        int changed = jdbc.update("""
                UPDATE submission SET game_result_id = ?, processing_state = 'RESULT_STORED',
                    personal_complete_established = ?, personal_perfect_established = ?,
                    shared_complete_established = ?, shared_perfect_established = ?,
                    updated_at = ?, version = version + 1
                WHERE source_message_id = ? AND processing_state IN ('RECEIVED', 'VALIDATED')
                """, resultId, publicationContext.personalCompleteEstablished(),
                publicationContext.personalPerfectEstablished(), publicationContext.sharedCompleteEstablished(),
                publicationContext.sharedPerfectEstablished(), databaseTime(clock.instant()), sourceMessageId);
        if (changed != 1) {
            throw new SubmissionConflictException("submission state changed during result storage");
        }
    }

    private void updatePublicationContext(long sourceMessageId, long resultId, PublicationContext publicationContext) {
        int changed = jdbc.update("""
                UPDATE submission SET personal_complete_established = ?, personal_perfect_established = ?,
                    shared_complete_established = ?, shared_perfect_established = ?,
                    updated_at = ?, version = version + 1
                WHERE source_message_id = ? AND game_result_id = ? AND processing_state = 'RESULT_STORED'
                """, publicationContext.personalCompleteEstablished(), publicationContext.personalPerfectEstablished(),
                publicationContext.sharedCompleteEstablished(), publicationContext.sharedPerfectEstablished(),
                databaseTime(clock.instant()), sourceMessageId, resultId);
        if (changed != 1) {
            throw new SubmissionConflictException("submission changed while recording publication context");
        }
    }
    @Override
    @Transactional
    public StoredSubmission reject(RejectedSubmission request) {
        StoredSubmission existing = lockRequired(request.sourceMessageId());
        if (existing.state() == SubmissionState.PARSE_REJECTED) {
            if (existing.parserErrorCode().filter(request.errorCode()::equals).isPresent()) {
                return existing;
            }
            throw new SubmissionConflictException("source message ID is already rejected with a different error code");
        }
        if (existing.state() != SubmissionState.RECEIVED && existing.state() != SubmissionState.VALIDATED) {
            throw new SubmissionConflictException("submission state does not allow rejection: " + existing.state());
        }
        int changed = jdbc.update("""
                UPDATE submission SET processing_state = 'PARSE_REJECTED', parser_error_code = ?,
                    technical_error_message = NULL, updated_at = ?, version = version + 1
                WHERE source_message_id = ? AND processing_state IN ('RECEIVED', 'VALIDATED')
                """, request.errorCode(), databaseTime(clock.instant()), request.sourceMessageId());
        if (changed != 1) {
            throw new SubmissionConflictException("submission state changed during rejection");
        }
        return findRequired(request.sourceMessageId());
    }

    @Override
    @Transactional
    public boolean transition(long sourceMessageId, SubmissionState expectedState, SubmissionState targetState) {
        return jdbc.update("""
                UPDATE submission SET processing_state = ?, updated_at = ?, version = version + 1
                WHERE source_message_id = ? AND processing_state = ?
                """, targetState.name(), databaseTime(clock.instant()), sourceMessageId, expectedState.name()) == 1;
    }

    @Override
    public List<StoredPlayer> findActivePlayers() {
        if (participationEnabled()) synchronizeActiveStatuses();
        return jdbc.query("SELECT * FROM player WHERE active = true ORDER BY discord_user_id", PLAYER);
    }

    @Override
    public List<StoredPlayer> findAllPlayers() {
        return jdbc.query("SELECT * FROM player ORDER BY discord_user_id", PLAYER);
    }

    @Override
    public List<ParticipationPeriod> findParticipationPeriods() {
        return ParticipationPeriodCompatibility.union(findGameParticipationPeriods());
    }

    @Override
    public List<GameParticipationPeriod> findGameParticipationPeriods() {
        return jdbc.query("""
                SELECT player_id, game_type, active_from, inactive_from
                FROM player_participation_period
                ORDER BY player_id, game_type, active_from
                """, (rs, row) -> new GameParticipationPeriod(
                rs.getLong("player_id"), GameType.valueOf(rs.getString("game_type")),
                rs.getObject("active_from", LocalDate.class), rs.getObject("inactive_from", LocalDate.class)));
    }

    @Override
    public Optional<GameParticipationPeriod> findGameParticipationPeriod(
            long discordUserId, GameType gameType, LocalDate date) {
        if (discordUserId <= 0) throw new IllegalArgumentException("discordUserId must be positive");
        java.util.Objects.requireNonNull(gameType, "gameType");
        java.util.Objects.requireNonNull(date, "date");
        return jdbc.query("""
                SELECT player_id, game_type, active_from, inactive_from
                FROM player_participation_period
                WHERE player_id = ? AND game_type = ?
                  AND active_from <= ? AND (inactive_from IS NULL OR ? < inactive_from)
                """, (rs, row) -> new GameParticipationPeriod(
                rs.getLong("player_id"), GameType.valueOf(rs.getString("game_type")),
                rs.getObject("active_from", LocalDate.class), rs.getObject("inactive_from", LocalDate.class)),
                discordUserId, gameType.name(), date, date).stream().findFirst();
    }

    @Override
    @Transactional
    public StoredPlayer activate(ParticipationChange request) {
        return activateGames(new GameParticipationChange(
                request.profile(), GameParticipationSelection.BOTH, request.effectiveDate()));
    }

    @Override
    @Transactional
    public StoredPlayer deactivate(ParticipationChange request) {
        return deactivateGames(new GameParticipationChange(
                request.profile(), GameParticipationSelection.BOTH, request.effectiveDate()));
    }

    @Override
    @Transactional
    public StoredPlayer activateGames(GameParticipationChange request) {
        synchronizeProfile(request.profile());
        long playerId = request.profile().discordUserId();
        lockPlayer(playerId);
        boolean wasGloballyActive = hasParticipationToday(playerId);
        for (GameType gameType : request.selection().gameTypes()) {
            activateGame(playerId, gameType, request.effectiveDate());
        }
        boolean isGloballyActive = hasParticipationToday(playerId);
        synchronizeActiveStatus(playerId);
        if (!wasGloballyActive && isGloballyActive) {
            jdbc.update("UPDATE player SET reminder_opt_in = TRUE, updated_at = ? WHERE discord_user_id = ?",
                    databaseTime(clock.instant()), playerId);
        }
        return findPlayer(playerId);
    }

    @Override
    @Transactional
    public StoredPlayer deactivateGames(GameParticipationChange request) {
        synchronizeProfile(request.profile());
        long playerId = request.profile().discordUserId();
        lockPlayer(playerId);
        for (GameType gameType : request.selection().gameTypes()) {
            deactivateGame(playerId, gameType, request.effectiveDate());
        }
        synchronizeActiveStatus(playerId);
        return findPlayer(playerId);
    }

    @Override
    @Transactional
    public StoredPlayer setReminderOptIn(ProfileUpdate request, boolean reminderOptIn) {
        synchronizeProfile(request);
        lockPlayer(request.discordUserId());
        jdbc.update("UPDATE player SET reminder_opt_in = ?, updated_at = ? WHERE discord_user_id = ?", reminderOptIn, databaseTime(clock.instant()), request.discordUserId());
        synchronizeActiveStatus(request.discordUserId());
        return findPlayer(request.discordUserId());
    }

    @Override
    public List<ReminderCandidate> findReminderCandidates(LocalDate gameDate) {
        return jdbc.query("""
                SELECT p.discord_user_id, p.display_name,
                    NOT EXISTS (SELECT 1 FROM game_result r WHERE r.player_id = p.discord_user_id AND r.game_type = 'GRIDWORDS' AND r.game_date = ?) AS missing_gridwords,
                    NOT EXISTS (SELECT 1 FROM game_result r WHERE r.player_id = p.discord_user_id AND r.game_type = 'QUADWORDS' AND r.game_date = ?) AS missing_quadwords
                FROM player p
                WHERE p.reminder_opt_in = TRUE
                  AND EXISTS (SELECT 1 FROM player_participation_period pp WHERE pp.player_id = p.discord_user_id
                      AND pp.active_from <= ? AND (pp.inactive_from IS NULL OR ? < pp.inactive_from))
                ORDER BY p.discord_user_id
                """, (rs, row) -> {
                    List<GameType> missing = new java.util.ArrayList<>();
                    if (rs.getBoolean("missing_gridwords")) missing.add(GameType.GRIDWORDS);
                    if (rs.getBoolean("missing_quadwords")) missing.add(GameType.QUADWORDS);
                    return missing.isEmpty() ? null : new ReminderCandidate(rs.getLong("discord_user_id"), rs.getString("display_name"), missing);
                }, gameDate, gameDate, gameDate, gameDate).stream().filter(java.util.Objects::nonNull).toList();
    }
    protected boolean participationEnabled() { return true; }

    private void lockParticipationTableForPublicationContext() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            jdbc.execute("LOCK TABLE player_participation_period IN SHARE ROW EXCLUSIVE MODE");
        }
    }
    private void lockPlayer(long discordUserId) {
        jdbc.queryForObject("SELECT discord_user_id FROM player WHERE discord_user_id = ? FOR UPDATE", Long.class, discordUserId);
    }

    private List<GameParticipationPeriod> periodsForUpdate(long playerId, GameType gameType) {
        return jdbc.query("""
                SELECT player_id, game_type, active_from, inactive_from
                FROM player_participation_period
                WHERE player_id = ? AND game_type = ?
                ORDER BY active_from FOR UPDATE
                """, (rs, row) -> new GameParticipationPeriod(
                rs.getLong("player_id"), GameType.valueOf(rs.getString("game_type")),
                rs.getObject("active_from", LocalDate.class), rs.getObject("inactive_from", LocalDate.class)),
                playerId, gameType.name());
    }

    private void activateGame(long playerId, GameType gameType, LocalDate effectiveDate) {
        List<GameParticipationPeriod> periods = periodsForUpdate(playerId, gameType);
        if (periods.stream().anyMatch(period -> period.contains(effectiveDate))) {
            return;
        }
        GameParticipationPeriod previous = periods.stream()
                .filter(period -> period.inactiveFrom() != null && !period.inactiveFrom().isAfter(effectiveDate))
                .max(java.util.Comparator.comparing(GameParticipationPeriod::activeFrom)).orElse(null);
        GameParticipationPeriod next = periods.stream()
                .filter(period -> period.activeFrom().isAfter(effectiveDate))
                .min(java.util.Comparator.comparing(GameParticipationPeriod::activeFrom)).orElse(null);
        OffsetDateTime now = databaseTime(clock.instant());
        if (previous != null && previous.inactiveFrom().equals(effectiveDate)) {
            jdbc.update("""
                    UPDATE player_participation_period SET inactive_from = ?, updated_at = ?
                    WHERE player_id = ? AND game_type = ? AND active_from = ?
                    """, next == null ? null : next.activeFrom(), now, playerId, gameType.name(), previous.activeFrom());
        } else {
            jdbc.update("""
                    INSERT INTO player_participation_period
                        (player_id, game_type, active_from, inactive_from, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, playerId, gameType.name(), effectiveDate, next == null ? null : next.activeFrom(), now, now);
        }
    }

    private void deactivateGame(long playerId, GameType gameType, LocalDate effectiveDate) {
        jdbc.update("""
                DELETE FROM player_participation_period
                WHERE player_id = ? AND game_type = ? AND active_from = ? AND inactive_from IS NULL
                """, playerId, gameType.name(), effectiveDate);
        jdbc.update("""
                UPDATE player_participation_period SET inactive_from = ?, updated_at = ?
                WHERE player_id = ? AND game_type = ? AND inactive_from IS NULL AND active_from < ?
                """, effectiveDate, databaseTime(clock.instant()), playerId, gameType.name(), effectiveDate);
    }

    private boolean hasParticipationToday(long playerId) {
        LocalDate today = LocalDate.now(clock.withZone(businessZone));
        Boolean active = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM player_participation_period
                    WHERE player_id = ? AND active_from <= ? AND (inactive_from IS NULL OR ? < inactive_from))
                """, Boolean.class, playerId, today, today);
        return Boolean.TRUE.equals(active);
    }

    private StoredPlayer findPlayer(long discordUserId) {
        return jdbc.query("SELECT * FROM player WHERE discord_user_id = ?", PLAYER, discordUserId).stream().findFirst().orElseThrow(() -> new IllegalStateException("player was not stored"));
    }

    private void synchronizeActiveStatuses() {
        LocalDate today = LocalDate.now(clock.withZone(businessZone));
        jdbc.update("""
                UPDATE player p SET active = EXISTS (SELECT 1 FROM player_participation_period pp WHERE pp.player_id = p.discord_user_id AND pp.active_from <= ? AND (pp.inactive_from IS NULL OR ? < pp.inactive_from)), updated_at = ?
                WHERE p.active IS DISTINCT FROM EXISTS (SELECT 1 FROM player_participation_period pp WHERE pp.player_id = p.discord_user_id AND pp.active_from <= ? AND (pp.inactive_from IS NULL OR ? < pp.inactive_from))
                """, today, today, databaseTime(clock.instant()), today, today);
    }

    private void synchronizeActiveStatus(long discordUserId) {
        LocalDate today = LocalDate.now(clock.withZone(businessZone));
        jdbc.update("""
                UPDATE player SET active = EXISTS (SELECT 1 FROM player_participation_period pp WHERE pp.player_id = player.discord_user_id AND pp.active_from <= ? AND (pp.inactive_from IS NULL OR ? < pp.inactive_from)), updated_at = ?
                WHERE discord_user_id = ?
                """, today, today, databaseTime(clock.instant()), discordUserId);
    }    @Override
    public Optional<StoredGameResult> findById(long id) {
        return jdbc.query("SELECT * FROM game_result WHERE id = ?", RESULT, id).stream().findFirst();
    }

    @Override
    public List<StoredGameResult> findAll() {
        return jdbc.query("SELECT * FROM game_result", RESULT);
    }

    /**
     * Serializes publication order on the mutable game-result row. The later tuple
     * {@code (received_at, source_message_id)} wins; older open submissions become terminally superseded.
     */
    @Override
    @Transactional
    public CanonicalPublicationPreparation prepareCanonicalPublication(long sourceMessageId, long gameResultId) {
        StoredSubmission submission = lockRequired(sourceMessageId);
        if (submission.gameResultId().filter(id -> id == gameResultId).isEmpty()) {
            throw new SubmissionConflictException("submission is not linked to the expected game result");
        }
        lockResult(gameResultId);
        if (submission.state() == SubmissionState.CANONICAL_MESSAGE_PUBLISHED
                || submission.state() == SubmissionState.ORIGINAL_MESSAGE_DELETED
                || submission.state() == SubmissionState.COMPLETED) {
            return CanonicalPublicationPreparation.ALREADY_PUBLISHED;
        }
        if (submission.state() == SubmissionState.SUPERSEDED) {
            return CanonicalPublicationPreparation.SUPERSEDED;
        }
        if (submission.state() != SubmissionState.RESULT_STORED
                && submission.state() != SubmissionState.FAILED_RETRYABLE) {
            throw new SubmissionConflictException("submission state does not allow canonical publication: "
                    + submission.state());
        }
        if (hasNewerLinkedSubmission(submission, gameResultId)) {
            supersedeSubmission(sourceMessageId, gameResultId);
            return CanonicalPublicationPreparation.SUPERSEDED;
        }
        supersedeOlderOpenSubmissions(submission, gameResultId);
        return CanonicalPublicationPreparation.PUBLISHABLE;
    }
    /**
     * Write-ahead fence for a Discord operation. It is committed before the REST call; a process death can only
     * leave reconciliation work, never an unrecorded visible side effect.
     */
    @Override
    @Transactional
    public CanonicalDeliveryAttempt beginCanonicalDelivery(long sourceMessageId, long resultId, UUID claimToken) {
        StoredSubmission submission = lockRequired(sourceMessageId);
        if (submission.gameResultId().filter(id -> id == resultId).isEmpty()
                || (submission.state() != SubmissionState.RESULT_STORED
                && submission.state() != SubmissionState.FAILED_RETRYABLE
                && submission.state() != SubmissionState.CANONICAL_MESSAGE_PUBLISHED
                && submission.state() != SubmissionState.ORIGINAL_MESSAGE_DELETED
                && submission.state() != SubmissionState.COMPLETED)) {
            throw new SubmissionConflictException("submission is not publishable for canonical delivery");
        }
        lockResult(resultId);
        List<Long> existing = jdbc.queryForList("SELECT refresh_generation FROM canonical_delivery_attempt WHERE claim_token = ? FOR UPDATE", Long.class, claimToken);
        if (!existing.isEmpty()) {
            return new CanonicalDeliveryAttempt(existing.getFirst());
        }
        Long generation = jdbc.query("""
                UPDATE game_result
                SET canonical_refresh_required = TRUE,
                    canonical_refresh_generation = canonical_refresh_generation + 1,
                    updated_at = ?, version = version + 1
                WHERE id = ? AND canonical_publish_claim_token = ?
                RETURNING canonical_refresh_generation
                """, rs -> rs.next() ? rs.getLong(1) : null,
                databaseTime(clock.instant()), resultId, claimToken);
        if (generation == null) {
            throw new SubmissionConflictException("canonical delivery claim was lost");
        }
        jdbc.update("""
                INSERT INTO canonical_delivery_attempt
                    (claim_token, game_result_id, source_message_id, refresh_generation, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, claimToken, resultId, sourceMessageId, generation, databaseTime(clock.instant()));
        return new CanonicalDeliveryAttempt(generation);
    }
    @Override
    public Optional<CanonicalRefreshCandidate> findCurrentCanonicalPublicationCandidate(long gameResultId) {
        return jdbc.query("""
                SELECT s.*, r.canonical_refresh_generation
                FROM submission s
                JOIN game_result r ON r.id = s.game_result_id
                WHERE s.game_result_id = ?
                  AND s.processing_state IN ('RESULT_STORED', 'FAILED_RETRYABLE', 'CANONICAL_MESSAGE_PUBLISHED', 'ORIGINAL_MESSAGE_DELETED', 'COMPLETED')
                ORDER BY s.received_at DESC, s.source_message_id DESC
                LIMIT 1
                """, this::refreshCandidate, gameResultId).stream().findFirst();
    }

    @Override
    public List<CanonicalRefreshCandidate> findCanonicalRefreshCandidates() {
        return jdbc.query("""
                SELECT s.*, r.canonical_refresh_generation
                FROM game_result r
                JOIN LATERAL (
                    SELECT *
                    FROM submission
                    WHERE game_result_id = r.id
                      AND processing_state IN ('RESULT_STORED', 'FAILED_RETRYABLE', 'CANONICAL_MESSAGE_PUBLISHED')
                    ORDER BY received_at DESC, source_message_id DESC
                    LIMIT 1
                ) s ON TRUE
                WHERE r.canonical_refresh_required = TRUE
                  AND (
                    r.game_type = 'GRIDWORDS'
                    OR (
                        r.game_type = 'QUADWORDS'
                        AND r.quadwords_top_left_board IS NOT NULL
                        AND r.quadwords_top_right_board IS NOT NULL
                        AND r.quadwords_bottom_left_board IS NOT NULL
                        AND r.quadwords_bottom_right_board IS NOT NULL
                    )
                    OR r.parser_version = 'quadwords-share-v2'
                  )
                ORDER BY r.id
                """, this::refreshCandidate);
    }

    @Override
    public void requestCanonicalRefresh(long resultId) {
        int changed = jdbc.update("""
                UPDATE game_result
                SET canonical_refresh_required = TRUE, canonical_refresh_generation = canonical_refresh_generation + 1,
                    updated_at = ?, version = version + 1
                WHERE id = ?
                """, databaseTime(clock.instant()), resultId);
        if (changed != 1) {
            throw new IllegalStateException("game result not found: " + resultId);
        }
    }

    /**
     * Completes one reconciliation generation and consumes every older write-ahead attempt. Attempts inserted by a
     * newer publisher remain durable, so a slow stale publisher cannot erase its own recovery obligation.
     */
    @Override
    @Transactional
    public CanonicalRefreshCompletion completeCanonicalRefresh(
            long sourceMessageId,
            long resultId,
            long canonicalMessageId,
            UUID claimToken,
            long refreshGeneration) {
        StoredSubmission submission = lockRequired(sourceMessageId);
        if (submission.gameResultId().filter(id -> id == resultId).isEmpty()
                || (submission.state() != SubmissionState.CANONICAL_MESSAGE_PUBLISHED
                && submission.state() != SubmissionState.ORIGINAL_MESSAGE_DELETED
                && submission.state() != SubmissionState.COMPLETED)) {
            throw new SubmissionConflictException("submission is not the published current source");
        }
        lockResult(resultId);
        if (hasNewerLinkedSubmission(submission, resultId)) {
            throw new SubmissionConflictException("a newer submission superseded the refresh source");
        }
        jdbc.update("DELETE FROM canonical_delivery_attempt WHERE game_result_id = ? AND refresh_generation <= ?",
                resultId, refreshGeneration);
        boolean outstanding = hasOutstandingCanonicalDeliveryAttempt(resultId);
        Boolean refreshStillRequired = jdbc.query("""
                UPDATE game_result
                SET canonical_message_id = ?,
                    canonical_refresh_required = CASE
                        WHEN canonical_refresh_generation = ? AND ? = FALSE THEN FALSE
                        ELSE TRUE
                    END,
                    canonical_publish_lease_until = NULL, canonical_publish_claim_token = NULL,
                    updated_at = ?, version = version + 1
                WHERE id = ? AND canonical_publish_claim_token = ?
                RETURNING canonical_refresh_required
                """, rs -> rs.next() ? rs.getBoolean(1) : null,
                canonicalMessageId, refreshGeneration, outstanding, databaseTime(clock.instant()), resultId, claimToken);
        if (refreshStillRequired == null) {
            throw new SubmissionConflictException("canonical refresh claim was lost");
        }
        return new CanonicalRefreshCompletion(refreshStillRequired);
    }
    @Override
    public Optional<PublicationClaim> claimCanonicalPublication(long resultId, Instant leaseUntil) {
        UUID token = UUID.randomUUID();
        Instant now = clock.instant();
        int changed = jdbc.update("""
                UPDATE game_result
                SET canonical_publish_lease_until = ?, canonical_publish_claim_token = ?, updated_at = ?
                WHERE id = ?
                  AND (canonical_publish_lease_until IS NULL OR canonical_publish_lease_until < ?)
                """, databaseTime(leaseUntil), token, databaseTime(now), resultId, databaseTime(now));
        return changed == 1 ? Optional.of(new PublicationClaim(token, leaseUntil)) : Optional.empty();
    }

    @Override
    public void releaseCanonicalPublicationClaim(long resultId, UUID claimToken) {
        jdbc.update("""
                UPDATE game_result
                SET canonical_publish_lease_until = NULL, canonical_publish_claim_token = NULL
                WHERE id = ? AND canonical_publish_claim_token = ?
                """, resultId, claimToken);
    }

    @Override
    @Transactional
    public boolean completeCanonicalPublication(
            long sourceMessageId,
            long resultId,
            long canonicalMessageId,
            UUID claimToken) {
        StoredSubmission submission = lockRequired(sourceMessageId);
        if (submission.gameResultId().filter(id -> id == resultId).isEmpty()
                || (submission.state() != SubmissionState.RESULT_STORED
                && submission.state() != SubmissionState.FAILED_RETRYABLE)) {
            throw new SubmissionConflictException("submission changed during canonical publication");
        }
        lockResult(resultId);
        List<Long> generations = jdbc.queryForList(
                "SELECT refresh_generation FROM canonical_delivery_attempt WHERE claim_token = ? FOR UPDATE",
                Long.class, claimToken);
        Long deliveryGeneration = generations.isEmpty() ? null : generations.getFirst();
        boolean otherOutstanding = hasOutstandingCanonicalDeliveryAttemptExcept(resultId, claimToken);
        Instant now = clock.instant();
        int claimedResult;
        if (deliveryGeneration == null) {
            claimedResult = jdbc.update("""
                    UPDATE game_result
                    SET canonical_message_id = ?, canonical_publish_lease_until = NULL,
                        canonical_publish_claim_token = NULL, updated_at = ?, version = version + 1
                    WHERE id = ? AND canonical_publish_claim_token = ?
                    """, canonicalMessageId, databaseTime(now), resultId, claimToken);
        } else {
            claimedResult = jdbc.update("""
                    UPDATE game_result
                    SET canonical_message_id = ?, canonical_publish_lease_until = NULL,
                        canonical_publish_claim_token = NULL,
                        canonical_refresh_required = CASE
                            WHEN canonical_refresh_generation = ? AND ? = FALSE THEN FALSE
                            ELSE TRUE
                        END,
                        updated_at = ?, version = version + 1
                    WHERE id = ? AND canonical_publish_claim_token = ?
                    """, canonicalMessageId, deliveryGeneration, otherOutstanding,
                    databaseTime(now), resultId, claimToken);
        }
        if (claimedResult != 1) {
            throw new SubmissionConflictException("publication claim was lost");
        }
        jdbc.update("DELETE FROM canonical_delivery_attempt WHERE claim_token = ?", claimToken);
        int completedSubmission = jdbc.update("""
                UPDATE submission
                SET processing_state = 'CANONICAL_MESSAGE_PUBLISHED', technical_error_message = NULL,
                    updated_at = ?, version = version + 1
                WHERE source_message_id = ?
                  AND game_result_id = ?
                  AND processing_state IN ('RESULT_STORED', 'FAILED_RETRYABLE')
                """, databaseTime(now), sourceMessageId, resultId);
        if (completedSubmission != 1) {
            throw new SubmissionConflictException("submission changed during canonical publication");
        }
        return true;
    }
    @Override
    public void markRetryableFailure(long sourceMessageId, String detail) {
        jdbc.update("""
                UPDATE submission
                SET processing_state = 'FAILED_RETRYABLE', technical_error_message = ?,
                    updated_at = ?, version = version + 1
                WHERE source_message_id = ?
                  AND processing_state IN ('RECEIVED', 'VALIDATED', 'RESULT_STORED', 'FAILED_RETRYABLE')
                """, detail, databaseTime(clock.instant()), sourceMessageId);
    }

    @Override
    public List<StoredSubmission> findAwaitingCanonicalPublication(GameType gameType) {
        return jdbc.query("""
                SELECT s.* FROM submission s JOIN game_result r ON r.id = s.game_result_id
                WHERE r.game_type = ?
                  AND s.processing_state IN ('RESULT_STORED', 'FAILED_RETRYABLE')
                  AND (
                    r.game_type = 'GRIDWORDS'
                    OR (
                        r.quadwords_top_left_board IS NOT NULL
                        AND r.quadwords_top_right_board IS NOT NULL
                        AND r.quadwords_bottom_left_board IS NOT NULL
                        AND r.quadwords_bottom_right_board IS NOT NULL
                    )
                    OR r.parser_version = 'quadwords-share-v2'
                  )
                """, SUBMISSION, gameType.name());
    }

    @Override
    public List<StoredSubmission> findGridWordsAwaitingCanonicalPublication() {
        return findAwaitingCanonicalPublication(GameType.GRIDWORDS);
    }

    @Override
    @Transactional
    public Optional<SourceDeletionClaim> claimOriginalSourceDeletion(long sourceMessageId, Instant leaseUntil) {
        StoredSubmission submission = lockRequired(sourceMessageId);
        if (submission.state() != SubmissionState.CANONICAL_MESSAGE_PUBLISHED
                && submission.state() != SubmissionState.SUPERSEDED) {
            return Optional.empty();
        }
        long resultId = submission.gameResultId().orElseThrow(
                () -> new SubmissionConflictException("source deletion requires a stored result"));
        lockResult(resultId);
        if (!isEligibleForOriginalSourceDeletion(submission, resultId)) {
            return Optional.empty();
        }
        UUID token = UUID.randomUUID();
        Instant now = clock.instant();
        int claimed = jdbc.update("""
                UPDATE submission
                SET source_delete_claim_token = ?, source_delete_lease_until = ?,
                    updated_at = ?, version = version + 1
                WHERE source_message_id = ?
                  AND processing_state IN ('CANONICAL_MESSAGE_PUBLISHED', 'SUPERSEDED')
                  AND (source_delete_lease_until IS NULL OR source_delete_lease_until < ?)
                """, token, databaseTime(leaseUntil), databaseTime(now), sourceMessageId, databaseTime(now));
        return claimed == 1 ? Optional.of(new SourceDeletionClaim(token, leaseUntil)) : Optional.empty();
    }

    @Override
    @Transactional
    public boolean recordOriginalSourceDeleted(long sourceMessageId, UUID claimToken) {
        int changed = jdbc.update("""
                UPDATE submission
                SET processing_state = 'ORIGINAL_MESSAGE_DELETED', original_deleted_at = ?,
                    source_delete_claim_token = NULL, source_delete_lease_until = NULL,
                    source_delete_failure_class = 'NONE', technical_error_message = NULL,
                    updated_at = ?, version = version + 1
                WHERE source_message_id = ?
                  AND source_delete_claim_token = ?
                  AND processing_state IN ('CANONICAL_MESSAGE_PUBLISHED', 'SUPERSEDED')
                """, databaseTime(clock.instant()), databaseTime(clock.instant()), sourceMessageId, claimToken);
        return changed == 1;
    }

    @Override
    @Transactional
    public boolean recordOriginalSourceDeletionFailure(
            long sourceMessageId, UUID claimToken, OriginalDeletionFailure failure, String safeTechnicalMessage) {
        if (failure == OriginalDeletionFailure.NONE) {
            throw new IllegalArgumentException("source deletion failure must be classified");
        }
        int changed = jdbc.update("""
                UPDATE submission
                SET source_delete_claim_token = NULL, source_delete_lease_until = NULL,
                    source_delete_failure_class = ?, technical_error_message = ?,
                    updated_at = ?, version = version + 1
                WHERE source_message_id = ?
                  AND source_delete_claim_token = ?
                  AND processing_state IN ('CANONICAL_MESSAGE_PUBLISHED', 'SUPERSEDED')
                """, failure.name(), safeTechnicalMessage, databaseTime(clock.instant()), sourceMessageId, claimToken);
        return changed == 1;
    }

    @Override
    @Transactional
    public boolean completeOriginalSourceDeletion(long sourceMessageId) {
        int changed = jdbc.update("""
                UPDATE submission
                SET processing_state = 'COMPLETED', updated_at = ?, version = version + 1
                WHERE source_message_id = ? AND processing_state = 'ORIGINAL_MESSAGE_DELETED'
                """, databaseTime(clock.instant()), sourceMessageId);
        if (changed == 1) {
            return true;
        }
        return findBySourceMessageId(sourceMessageId)
                .map(submission -> submission.state() == SubmissionState.COMPLETED)
                .orElse(false);
    }

    @Override
    public List<StoredSubmission> findAwaitingOriginalSourceDeletion(GameType gameType) {
        return jdbc.query("""
                SELECT s.*
                FROM submission s
                JOIN game_result r ON r.id = s.game_result_id
                WHERE r.game_type = ?
                  AND s.source_delete_failure_class <> 'PERMANENT'
                  AND (
                    r.game_type = 'GRIDWORDS'
                    OR (
                        r.quadwords_top_left_board IS NOT NULL
                        AND r.quadwords_top_right_board IS NOT NULL
                        AND r.quadwords_bottom_left_board IS NOT NULL
                        AND r.quadwords_bottom_right_board IS NOT NULL
                    )
                    OR r.parser_version = 'quadwords-share-v2'
                  )
                  AND (
                    s.processing_state = 'ORIGINAL_MESSAGE_DELETED'
                    OR (
                        s.processing_state = 'CANONICAL_MESSAGE_PUBLISHED'
                        AND r.canonical_message_id IS NOT NULL
                        AND r.canonical_message_id <> s.source_message_id
                    )
                    OR (
                        s.processing_state = 'SUPERSEDED'
                        AND r.canonical_message_id IS NOT NULL
                        AND r.canonical_message_id <> s.source_message_id
                        AND EXISTS (
                            SELECT 1
                            FROM submission newer
                            WHERE newer.game_result_id = s.game_result_id
                              AND newer.processing_state IN (
                                  'CANONICAL_MESSAGE_PUBLISHED', 'ORIGINAL_MESSAGE_DELETED', 'COMPLETED')
                              AND (newer.received_at > s.received_at
                                   OR (newer.received_at = s.received_at
                                       AND newer.source_message_id > s.source_message_id))
                        )
                    )
                  )
                ORDER BY s.source_message_id
                """, SUBMISSION, gameType.name());
    }

    @Override
    public List<StoredSubmission> findGridWordsAwaitingOriginalSourceDeletion() {
        return findAwaitingOriginalSourceDeletion(GameType.GRIDWORDS);
    }

    private boolean isEligibleForOriginalSourceDeletion(StoredSubmission submission, long resultId) {
        if (submission.originalDeletionFailure() == OriginalDeletionFailure.PERMANENT) {
            return false;
        }
        return jdbc.queryForList("""
                SELECT r.id
                FROM game_result r
                WHERE r.id = ?
                  AND r.canonical_message_id IS NOT NULL
                  AND r.canonical_message_id <> ?
                  AND (
                    r.game_type = 'GRIDWORDS'
                    OR (
                        r.game_type = 'QUADWORDS'
                        AND r.quadwords_top_left_board IS NOT NULL
                        AND r.quadwords_top_right_board IS NOT NULL
                        AND r.quadwords_bottom_left_board IS NOT NULL
                        AND r.quadwords_bottom_right_board IS NOT NULL
                    )
                    OR r.parser_version = 'quadwords-share-v2'
                  )
                """, Long.class, resultId, submission.sourceMessageId()).size() == 1
                && (submission.state() == SubmissionState.CANONICAL_MESSAGE_PUBLISHED
                || hasNewerConfirmedCanonicalSource(submission, resultId));
    }

    private boolean hasNewerConfirmedCanonicalSource(StoredSubmission submission, long resultId) {
        return !jdbc.queryForList("""
                SELECT source_message_id
                FROM submission
                WHERE game_result_id = ?
                  AND processing_state IN ('CANONICAL_MESSAGE_PUBLISHED', 'ORIGINAL_MESSAGE_DELETED', 'COMPLETED')
                  AND (received_at > ? OR (received_at = ? AND source_message_id > ?))
                LIMIT 1
                """, Long.class, resultId, databaseTime(submission.receivedAt()),
                databaseTime(submission.receivedAt()), submission.sourceMessageId()).isEmpty();
    }
    private boolean hasOutstandingCanonicalDeliveryAttempt(long resultId) {
        return !jdbc.queryForList("SELECT claim_token FROM canonical_delivery_attempt WHERE game_result_id = ? LIMIT 1",
                UUID.class, resultId).isEmpty();
    }

    private boolean hasOutstandingCanonicalDeliveryAttemptExcept(long resultId, UUID claimToken) {
        return !jdbc.queryForList("SELECT claim_token FROM canonical_delivery_attempt WHERE game_result_id = ? AND claim_token <> ? LIMIT 1",
                UUID.class, resultId, claimToken).isEmpty();
    }
    private CanonicalRefreshCandidate refreshCandidate(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CanonicalRefreshCandidate(SUBMISSION.mapRow(resultSet, rowNumber),
                resultSet.getLong("canonical_refresh_generation"));
    }
    private void lockResult(long gameResultId) {
        boolean found = !jdbc.queryForList("SELECT id FROM game_result WHERE id = ? FOR UPDATE", Long.class, gameResultId)
                .isEmpty();
        if (!found) {
            throw new IllegalStateException("game result not found: " + gameResultId);
        }
    }

    private boolean hasNewerLinkedSubmission(StoredSubmission submission, long gameResultId) {
        return !jdbc.queryForList("""
                SELECT source_message_id
                FROM submission
                WHERE game_result_id = ?
                  AND processing_state IN (
                      'RESULT_STORED', 'FAILED_RETRYABLE', 'CANONICAL_MESSAGE_PUBLISHED',
                      'ORIGINAL_MESSAGE_DELETED', 'COMPLETED', 'SUPERSEDED')
                  AND (received_at > ? OR (received_at = ? AND source_message_id > ?))
                LIMIT 1
                """, Long.class, gameResultId, databaseTime(submission.receivedAt()),
                databaseTime(submission.receivedAt()), submission.sourceMessageId()).isEmpty();
    }

    private void supersedeOlderOpenSubmissions(StoredSubmission submission, long gameResultId) {
        jdbc.update("""
                UPDATE submission
                SET processing_state = 'SUPERSEDED', technical_error_message = NULL,
                    updated_at = ?, version = version + 1
                WHERE game_result_id = ?
                  AND processing_state IN ('RESULT_STORED', 'FAILED_RETRYABLE', 'CANONICAL_MESSAGE_PUBLISHED')
                  AND (received_at < ? OR (received_at = ? AND source_message_id < ?))
                """, databaseTime(clock.instant()), gameResultId, databaseTime(submission.receivedAt()),
                databaseTime(submission.receivedAt()), submission.sourceMessageId());
    }

    private void supersedeSubmission(long sourceMessageId, long gameResultId) {
        int changed = jdbc.update("""
                UPDATE submission
                SET processing_state = 'SUPERSEDED', technical_error_message = NULL,
                    updated_at = ?, version = version + 1
                WHERE source_message_id = ?
                  AND game_result_id = ?
                  AND processing_state IN ('RESULT_STORED', 'FAILED_RETRYABLE')
                """, databaseTime(clock.instant()), sourceMessageId, gameResultId);
        if (changed != 1) {
            throw new SubmissionConflictException("submission changed during supersession");
        }
    }
    private static PublicationContext publicationContext(List<StoredGameResult> before, List<StoredGameResult> after,
            long playerId, LocalDate gameDate, List<ParticipationPeriod> periods) {
        boolean personalCompleteBefore = complete(before, playerId, gameDate);
        boolean personalPerfectBefore = perfect(before, playerId, gameDate);
        boolean sharedCompleteBefore = sharedComplete(before, periods, gameDate);
        boolean sharedPerfectBefore = sharedPerfect(before, periods, gameDate);
        return new PublicationContext(!personalCompleteBefore && complete(after, playerId, gameDate),
                !personalPerfectBefore && perfect(after, playerId, gameDate),
                !sharedCompleteBefore && sharedComplete(after, periods, gameDate),
                !sharedPerfectBefore && sharedPerfect(after, periods, gameDate));
    }

    private static boolean complete(List<StoredGameResult> results, long playerId, LocalDate gameDate) {
        return results.stream().filter(result -> result.playerId() == playerId && result.parsedResult().gameDate().equals(gameDate))
                .map(result -> result.parsedResult().gameType()).distinct().count() == GameType.values().length;
    }

    private static boolean perfect(List<StoredGameResult> results, long playerId, LocalDate gameDate) {
        List<StoredGameResult> games = results.stream().filter(result -> result.playerId() == playerId && result.parsedResult().gameDate().equals(gameDate)).toList();
        return complete(results, playerId, gameDate) && games.stream().allMatch(result -> result.parsedResult().outcome() instanceof ShareOutcome.Solved);
    }

    private static List<Long> activePlayers(List<ParticipationPeriod> periods, LocalDate gameDate) {
        return periods.stream().filter(period -> period.contains(gameDate)).map(ParticipationPeriod::playerId).distinct().toList();
    }

    private static boolean sharedComplete(List<StoredGameResult> results, List<ParticipationPeriod> periods, LocalDate gameDate) {
        List<Long> players = activePlayers(periods, gameDate);
        return players.size() >= 2 && players.stream().allMatch(playerId -> complete(results, playerId, gameDate));
    }

    private static boolean sharedPerfect(List<StoredGameResult> results, List<ParticipationPeriod> periods, LocalDate gameDate) {
        List<Long> players = activePlayers(periods, gameDate);
        return players.size() >= 2 && players.stream().allMatch(playerId -> perfect(results, playerId, gameDate));
    }
    private Optional<StoredGameResult> insertResultIfAbsent(GameResultUpsert request, Instant now) {
        ParsedGameResult parsed = request.parsedResult();
        boolean solved = parsed.outcome() instanceof ShareOutcome.Solved;
        Integer attempts = solved ? ((ShareOutcome.Solved) parsed.outcome()).attemptsUsed() : null;
        BoardColumns boards = boardColumns(parsed);
        return jdbc.query("""
                INSERT INTO game_result (player_id, game_type, game_date, solved, attempts_used, max_attempts,
                    duration_seconds, gridgames_streak, normalized_board, quadwords_top_left_board,
                    quadwords_top_right_board, quadwords_bottom_left_board, quadwords_bottom_right_board,
                    raw_share_text, parser_version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (player_id, game_type, game_date) DO NOTHING
                RETURNING *
                """, RESULT, request.playerId(), parsed.gameType().name(), parsed.gameDate(), solved, attempts,
                parsed.outcome().maxAttempts(), parsed.duration().getSeconds(),
                parsed.gridgamesStreak().isPresent() ? parsed.gridgamesStreak().getAsInt() : null,
                boards.gridWords(), boards.topLeft(), boards.topRight(), boards.bottomLeft(), boards.bottomRight(),
                request.rawShareText(), request.parserVersion(), databaseTime(now), databaseTime(now)).stream().findFirst();
    }

    private StoredGameResult upsertResult(GameResultUpsert request, Instant now) {
        ParsedGameResult parsed = request.parsedResult();
        boolean solved = parsed.outcome() instanceof ShareOutcome.Solved;
        Integer attempts = solved ? ((ShareOutcome.Solved) parsed.outcome()).attemptsUsed() : null;
        BoardColumns boards = boardColumns(parsed);
        return jdbc.queryForObject("""
                INSERT INTO game_result (player_id, game_type, game_date, solved, attempts_used, max_attempts, duration_seconds,
                    gridgames_streak, normalized_board, quadwords_top_left_board, quadwords_top_right_board,
                    quadwords_bottom_left_board, quadwords_bottom_right_board, raw_share_text, parser_version,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (player_id, game_type, game_date) DO UPDATE SET solved = EXCLUDED.solved,
                    attempts_used = EXCLUDED.attempts_used, max_attempts = EXCLUDED.max_attempts,
                    duration_seconds = EXCLUDED.duration_seconds, gridgames_streak = EXCLUDED.gridgames_streak,
                    normalized_board = EXCLUDED.normalized_board,
                    quadwords_top_left_board = CASE
                        WHEN EXCLUDED.parser_version = 'quadwords-share-v2'
                            AND EXCLUDED.quadwords_top_left_board IS NULL
                            AND game_result.quadwords_top_left_board IS NOT NULL
                        THEN game_result.quadwords_top_left_board
                        ELSE EXCLUDED.quadwords_top_left_board
                    END,
                    quadwords_top_right_board = CASE
                        WHEN EXCLUDED.parser_version = 'quadwords-share-v2'
                            AND EXCLUDED.quadwords_top_right_board IS NULL
                            AND game_result.quadwords_top_right_board IS NOT NULL
                        THEN game_result.quadwords_top_right_board
                        ELSE EXCLUDED.quadwords_top_right_board
                    END,
                    quadwords_bottom_left_board = CASE
                        WHEN EXCLUDED.parser_version = 'quadwords-share-v2'
                            AND EXCLUDED.quadwords_bottom_left_board IS NULL
                            AND game_result.quadwords_bottom_left_board IS NOT NULL
                        THEN game_result.quadwords_bottom_left_board
                        ELSE EXCLUDED.quadwords_bottom_left_board
                    END,
                    quadwords_bottom_right_board = CASE
                        WHEN EXCLUDED.parser_version = 'quadwords-share-v2'
                            AND EXCLUDED.quadwords_bottom_right_board IS NULL
                            AND game_result.quadwords_bottom_right_board IS NOT NULL
                        THEN game_result.quadwords_bottom_right_board
                        ELSE EXCLUDED.quadwords_bottom_right_board
                    END,
                    raw_share_text = EXCLUDED.raw_share_text,
                    parser_version = CASE
                        WHEN EXCLUDED.parser_version = 'quadwords-share-v2'
                            AND EXCLUDED.quadwords_top_left_board IS NULL
                            AND game_result.quadwords_top_left_board IS NOT NULL
                        THEN game_result.parser_version
                        ELSE EXCLUDED.parser_version
                    END,
                    updated_at = EXCLUDED.updated_at, version = game_result.version + 1
                RETURNING *
                """, RESULT, request.playerId(), parsed.gameType().name(), parsed.gameDate(), solved, attempts,
                parsed.outcome().maxAttempts(), parsed.duration().getSeconds(),
                parsed.gridgamesStreak().isPresent() ? parsed.gridgamesStreak().getAsInt() : null,
                boards.gridWords(), boards.topLeft(), boards.topRight(), boards.bottomLeft(), boards.bottomRight(),
                request.rawShareText(), request.parserVersion(), databaseTime(now), databaseTime(now));
    }

    private static BoardColumns boardColumns(ParsedGameResult parsed) {
        if (parsed.gameType() == GameType.GRIDWORDS) {
            return new BoardColumns(parsed.board().orElseThrow().canonicalText(), null, null, null, null);
        }
        return parsed.quadWordsBoards().map(quadWords -> new BoardColumns(null,
                        quadWords.topLeft().canonicalText(),
                        quadWords.topRight().canonicalText(),
                        quadWords.bottomLeft().canonicalText(),
                        quadWords.bottomRight().canonicalText()))
                .orElseGet(() -> new BoardColumns(null, null, null, null, null));
    }

    private record BoardColumns(
            String gridWords, String topLeft, String topRight, String bottomLeft, String bottomRight) { }

    private static boolean equivalent(StoredGameResult stored, GameResultUpsert request) {
        return stored.playerId() == request.playerId() && stored.parsedResult().equals(request.parsedResult())
                && stored.rawShareText().equals(request.rawShareText())
                && stored.parserVersion().equals(request.parserVersion());
    }

    private StoredSubmission lockRequired(long sourceMessageId) {
        return jdbc.query("SELECT * FROM submission WHERE source_message_id = ? FOR UPDATE", SUBMISSION, sourceMessageId)
                .stream().findFirst().orElseThrow(() -> new IllegalStateException("submission not found: " + sourceMessageId));
    }

    private Optional<StoredGameResult> findResultForUpdate(GameResultUpsert request) {
        return jdbc.query("""
                SELECT * FROM game_result
                WHERE player_id = ? AND game_type = ? AND game_date = ?
                FOR UPDATE
                """, RESULT, request.playerId(), request.parsedResult().gameType().name(),
                request.parsedResult().gameDate()).stream().findFirst();
    }
    private StoredGameResult findResultById(long resultId) {
        return jdbc.query("SELECT * FROM game_result WHERE id = ?", RESULT, resultId).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("game result not found: " + resultId));
    }

    private StoredSubmission findRequired(long sourceMessageId) {
        return findBySourceMessageId(sourceMessageId)
                .orElseThrow(() -> new IllegalStateException("submission not found: " + sourceMessageId));
    }

    private boolean sameSubmission(StoredSubmission stored, SubmissionRegistration incoming) {
        return stored.guildId() == incoming.guildId() && stored.channelId() == incoming.channelId()
                && stored.authorPlayerId() == incoming.authorPlayerId()
                && stored.rawMessageContent().equals(incoming.rawMessageContent())
                && stored.attachments().equals(incoming.attachments());
    }

    private static final RowMapper<StoredPlayer> PLAYER = (rs, row) -> new StoredPlayer(rs.getLong("discord_user_id"),
            rs.getString("display_name"), rs.getBoolean("active"), rs.getBoolean("administrator"), rs.getBoolean("reminder_opt_in"), instant(rs, "created_at"),
            instant(rs, "updated_at"));

    private static final RowMapper<StoredGameResult> RESULT = (rs, row) -> {
        GameType type = GameType.valueOf(rs.getString("game_type"));
        int maximum = rs.getInt("max_attempts");
        ShareOutcome outcome = rs.getBoolean("solved")
                ? new ShareOutcome.Solved(rs.getInt("attempts_used"), maximum)
                : new ShareOutcome.Unsolved(maximum);
        String boardText = rs.getString("normalized_board");
        String quadTopLeft = rs.getString("quadwords_top_left_board");
        Optional<QuadWordsBoards> quadWordsBoards = type == GameType.QUADWORDS && quadTopLeft != null
                ? Optional.of(new QuadWordsBoards(
                        QuadWordsBoard.fromCanonicalText(quadTopLeft),
                        QuadWordsBoard.fromCanonicalText(required(rs, "quadwords_top_right_board")),
                        QuadWordsBoard.fromCanonicalText(required(rs, "quadwords_bottom_left_board")),
                        QuadWordsBoard.fromCanonicalText(required(rs, "quadwords_bottom_right_board"))))
                : Optional.empty();
        ParsedGameResult parsed = new ParsedGameResult(type, rs.getObject("game_date", java.time.LocalDate.class), outcome,
                Duration.ofSeconds(rs.getLong("duration_seconds")), optionalInt(rs, "gridgames_streak"),
                boardText == null ? Optional.empty() : Optional.of(new NormalizedBoard(List.of(boardText.split("\\n")))),
                quadWordsBoards);
        Long messageId = rs.getObject("canonical_message_id", Long.class);
        return new StoredGameResult(rs.getLong("id"), rs.getLong("player_id"), parsed, rs.getString("raw_share_text"),
                rs.getString("parser_version"), messageId == null ? OptionalLong.empty() : OptionalLong.of(messageId),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    };

    private final RowMapper<StoredSubmission> SUBMISSION = (rs, row) -> new StoredSubmission(
            rs.getLong("source_message_id"), rs.getLong("guild_id"), rs.getLong("channel_id"),
            rs.getLong("author_player_id"), rs.getString("raw_message_content"),
            SubmissionState.valueOf(rs.getString("processing_state")),
            Optional.ofNullable(rs.getObject("game_result_id", Long.class)), attachments(rs.getLong("source_message_id")),
            Optional.ofNullable(rs.getString("parser_error_code")),
            Optional.ofNullable(rs.getString("technical_error_message")),
            new PublicationContext(
                    rs.getBoolean("personal_complete_established"),
                    rs.getBoolean("personal_perfect_established"),
                    rs.getBoolean("shared_complete_established"),
                    rs.getBoolean("shared_perfect_established")),
            optionalInstant(rs, "original_deleted_at"),
            OriginalDeletionFailure.valueOf(rs.getString("source_delete_failure_class")),
            optionalInstant(rs, "source_delete_lease_until"),
            instant(rs, "received_at"), instant(rs, "updated_at"));

    private List<AttachmentSnapshot> attachments(long sourceMessageId) {
        return jdbc.query("""
                SELECT * FROM submission_attachment WHERE source_message_id = ? ORDER BY attachment_index
                """, (rs, row) -> new AttachmentSnapshot(rs.getInt("attachment_index"), rs.getString("file_name"),
                Optional.ofNullable(rs.getString("content_type")), rs.getLong("size_bytes")), sourceMessageId);
    }

    private static OffsetDateTime databaseTime(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Optional<Instant> optionalInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? Optional.empty() : Optional.of(value.toInstant());
    }

    private static String required(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        if (value == null) throw new SQLException("required QuadWords board is missing: " + column);
        return value;
    }

    private static OptionalInt optionalInt(ResultSet rs, String column) throws SQLException {
        Integer value = rs.getObject(column, Integer.class);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }
}
