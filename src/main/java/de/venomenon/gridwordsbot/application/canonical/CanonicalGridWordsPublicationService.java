package de.venomenon.gridwordsbot.application.canonical;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.streak.StreakCalculator;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.PublicationRetryScheduler;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.LongConsumer;

/** Coordinates one resumable canonical game-result publication without a Discord call in a transaction. */
public final class CanonicalGridWordsPublicationService {

    private static final long LEASE_SECONDS = 60;
    private static final long REFRESH_DELAY_SECONDS = 1;

    private final GameResultStore results;
    private final PlayerStore players;
    private final SubmissionStore submissions;
    private final CanonicalMessageGateway discord;
    private final Clock clock;
    private final ZoneId zoneId;
    private final List<Long> configuredPlayerIds;
    private final StreakCalculator streakCalculator;
    private final PublicationRetryScheduler retryScheduler;
    private final LongConsumer postPublication;
    private final Set<Long> scheduledRetries = ConcurrentHashMap.newKeySet();
    private final ConcurrentMap<Long, RefreshSchedule> scheduledRefreshes = new ConcurrentHashMap<>();

    public CanonicalGridWordsPublicationService(
            GameResultStore results,
            PlayerStore players,
            SubmissionStore submissions,
            CanonicalMessageGateway discord,
            Clock clock,
            ZoneId zoneId,
            List<Long> configuredPlayerIds) {
        this(
                results,
                players,
                submissions,
                discord,
                clock,
                zoneId,
                configuredPlayerIds,
                (at, action) -> { });
    }

    public CanonicalGridWordsPublicationService(
            GameResultStore results,
            PlayerStore players,
            SubmissionStore submissions,
            CanonicalMessageGateway discord,
            Clock clock,
            ZoneId zoneId,
            List<Long> configuredPlayerIds,
            PublicationRetryScheduler retryScheduler) {
        this(results, players, submissions, discord, clock, zoneId, configuredPlayerIds, retryScheduler,
                sourceMessageId -> { });
    }

    public CanonicalGridWordsPublicationService(
            GameResultStore results,
            PlayerStore players,
            SubmissionStore submissions,
            CanonicalMessageGateway discord,
            Clock clock,
            ZoneId zoneId,
            List<Long> configuredPlayerIds,
            PublicationRetryScheduler retryScheduler,
            LongConsumer postPublication) {
        this.results = Objects.requireNonNull(results);
        this.players = Objects.requireNonNull(players);
        this.submissions = Objects.requireNonNull(submissions);
        this.discord = Objects.requireNonNull(discord);
        this.clock = Objects.requireNonNull(clock);
        this.zoneId = Objects.requireNonNull(zoneId);
        this.configuredPlayerIds = List.copyOf(Objects.requireNonNull(configuredPlayerIds));
        if (this.configuredPlayerIds.size() != 2
                || this.configuredPlayerIds.stream().distinct().count() != 2
                || this.configuredPlayerIds.stream().anyMatch(id -> id <= 0)) {
            throw new IllegalArgumentException("exactly two distinct configured player IDs are required");
        }
        this.streakCalculator = new StreakCalculator();
        this.retryScheduler = Objects.requireNonNull(retryScheduler);
        this.postPublication = Objects.requireNonNull(postPublication);
    }
    /** @return true only after the canonical ID and the submission state were persisted together. */
    public boolean publish(long sourceMessageId) {
        return publishAndHandOff(sourceMessageId) == PublicationOutcome.PUBLISHED;
    }

    /** Replays open publications and persisted refresh work after startup. */
    public void resumeOpenPublications() {
        for (SubmissionStore.StoredSubmission submission : submissions.findGridWordsAwaitingCanonicalPublication())
            publishAndHandOff(submission.sourceMessageId());
        for (SubmissionStore.StoredSubmission submission : submissions.findAwaitingCanonicalPublication(GameType.QUADWORDS))
            publishAndHandOff(submission.sourceMessageId());
        for (SubmissionStore.CanonicalRefreshCandidate refresh : submissions.findCanonicalRefreshCandidates()) {
            resumeCurrentRefresh(refresh.submission().gameResultId().orElseThrow());
        }
    }

private PublicationOutcome publishAndHandOff(long sourceMessageId) {
        PublicationOutcome outcome = publishOutcome(sourceMessageId);
        if (outcome == PublicationOutcome.PUBLISHED) {
            postPublication.accept(sourceMessageId);
        }
        return outcome;
    }
    private PublicationOutcome publishOutcome(long sourceMessageId) {
        long resultId = 0;
        UUID claimToken = null;
        boolean deliveryAttemptRecorded = false;
        try {
            SubmissionStore.StoredSubmission submission = submissions.findBySourceMessageId(sourceMessageId)
                    .orElseThrow();
            if (submission.state() == SubmissionStore.SubmissionState.SUPERSEDED) {
                return PublicationOutcome.SUPERSEDED;
            }

            resultId = submission.gameResultId().orElseThrow();
            GameResultStore.StoredGameResult result = results.findById(resultId).orElseThrow();
            if (!isPublishable(result)) {
                return PublicationOutcome.NOT_PUBLISHABLE;
            }
            if (submission.state() == SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED
                    || submission.state() == SubmissionStore.SubmissionState.ORIGINAL_MESSAGE_DELETED
                    || submission.state() == SubmissionStore.SubmissionState.COMPLETED) {
                return PublicationOutcome.PUBLISHED;
            }

            SubmissionStore.CanonicalPublicationPreparation preparation =
                    submissions.prepareCanonicalPublication(sourceMessageId, resultId);
            if (preparation == SubmissionStore.CanonicalPublicationPreparation.ALREADY_PUBLISHED) {
                return PublicationOutcome.PUBLISHED;
            }
            if (preparation == SubmissionStore.CanonicalPublicationPreparation.SUPERSEDED) {
                return PublicationOutcome.SUPERSEDED;
            }

            GameResultStore.PublicationClaim claim = results.claimCanonicalPublication(
                    resultId,
                    clock.instant().plusSeconds(LEASE_SECONDS)).orElse(null);
            if (claim == null) {
                scheduleRetry(sourceMessageId);
                return PublicationOutcome.RETRY_SCHEDULED;
            }
            claimToken = claim.token();
            submissions.beginCanonicalDelivery(sourceMessageId, resultId, claimToken);
            deliveryAttemptRecorded = true;

            long canonicalMessageId = publishOrEdit(
                    submission,
                    result,
                    canonicalMessage(result, submission.authorPlayerId(), submission.publicationContext()));
            boolean completed = submissions.completeCanonicalPublication(
                    sourceMessageId,
                    resultId,
                    canonicalMessageId,
                    claimToken);
            if (!completed) {
                throw new IllegalStateException("canonical publication completion was not accepted");
            }
            return PublicationOutcome.PUBLISHED;
        } catch (RuntimeException exception) {
            if (resultId != 0) {
                try {
                    submissions.markRetryableFailure(sourceMessageId, "canonical publication failed");
                } finally {
                    try {
                        if (claimToken != null) {
                            results.releaseCanonicalPublicationClaim(resultId, claimToken);
                        }
                    } finally {
                        if (deliveryAttemptRecorded) {
                            requestCurrentRefresh(resultId, REFRESH_DELAY_SECONDS);
                        }
                    }
                }
            }
            scheduleRetry(sourceMessageId);
            return PublicationOutcome.RETRY_SCHEDULED;
        }
    }

    private void scheduleRetry(long sourceMessageId) {
        if (!scheduledRetries.add(sourceMessageId)) {
            return;
        }
        try {
            retryScheduler.schedule(clock.instant().plusSeconds(LEASE_SECONDS + 1), () -> retryPublication(sourceMessageId));
        } catch (RejectedExecutionException ignored) {
            // Recovery on a subsequent startup remains available if scheduling is unavailable during shutdown.
            scheduledRetries.remove(sourceMessageId);
        }
    }

    private void retryPublication(long sourceMessageId) {
        PublicationOutcome outcome;
        try {
            outcome = publishAndHandOff(sourceMessageId);
        } finally {
            scheduledRetries.remove(sourceMessageId);
        }
        if (outcome == PublicationOutcome.RETRY_SCHEDULED) {
            scheduleRetry(sourceMessageId);
            return;
        }

    }
    /** Persists and schedules the reconciliation needed after a late stale Discord side effect. */
    private void requestCurrentRefresh(long resultId, long delaySeconds) {
        submissions.requestCanonicalRefresh(resultId);
        scheduleCurrentRefresh(resultId, delaySeconds);
    }

    private void resumeCurrentRefresh(long resultId) {
        RefreshSchedule schedule = startRefresh(resultId);
        if (schedule != null) {
            retryCurrentRefresh(resultId, schedule);
        }
    }

    /** Coalesces wake-ups without dropping a request that arrives while a refresh is running. */
    private void scheduleCurrentRefresh(long resultId, long delaySeconds) {
        RefreshSchedule schedule = startRefresh(resultId);
        if (schedule != null) {
            scheduleRefresh(resultId, schedule, delaySeconds);
        }
    }

    private RefreshSchedule startRefresh(long resultId) {
        RefreshSchedule[] newlyScheduled = new RefreshSchedule[1];
        scheduledRefreshes.compute(resultId, (ignored, current) -> {
            RefreshSchedule schedule = current == null ? new RefreshSchedule() : current;
            synchronized (schedule) {
                schedule.rerunRequested = true;
                if (!schedule.scheduledOrRunning) {
                    schedule.scheduledOrRunning = true;
                    newlyScheduled[0] = schedule;
                }
            }
            return schedule;
        });
        return newlyScheduled[0];
    }

    private void scheduleRefresh(long resultId, RefreshSchedule schedule, long delaySeconds) {
        try {
            retryScheduler.schedule(clock.instant().plusSeconds(delaySeconds), () -> retryCurrentRefresh(resultId, schedule));
        } catch (RejectedExecutionException ignored) {
            // The durable refresh request remains available to a later startup reconciliation.
            scheduledRefreshes.remove(resultId, schedule);
        }
    }

    private void retryCurrentRefresh(long resultId, RefreshSchedule schedule) {
        RefreshOutcome outcome = RefreshOutcome.RETRY_SCHEDULED;
        synchronized (schedule) {
            schedule.rerunRequested = false;
        }
        try {
            outcome = refreshCurrentPublication(resultId);
        } finally {
            RefreshOutcome completedOutcome = outcome;
            RefreshSchedule[] followUp = new RefreshSchedule[1];
            scheduledRefreshes.compute(resultId, (ignored, current) -> {
                if (current != schedule) {
                    return current;
                }
                synchronized (schedule) {
                    boolean needsAnotherPass = schedule.rerunRequested
                            || completedOutcome == RefreshOutcome.RETRY_SCHEDULED;
                    if (!needsAnotherPass) {
                        schedule.scheduledOrRunning = false;
                        return null;
                    }
                    schedule.rerunRequested = false;
                    schedule.scheduledOrRunning = true;
                    followUp[0] = schedule;
                    return schedule;
                }
            });
            if (followUp[0] != null) {
                long delay = completedOutcome == RefreshOutcome.RETRY_SCHEDULED
                        ? LEASE_SECONDS + 1
                        : REFRESH_DELAY_SECONDS;
                scheduleRefresh(resultId, followUp[0], delay);
            }
        }
    }

    private RefreshOutcome refreshCurrentPublication(long resultId) {
        UUID claimToken = null;
        try {
            SubmissionStore.CanonicalRefreshCandidate candidate = submissions
                    .findCurrentCanonicalPublicationCandidate(resultId)
                    .orElse(null);
            if (candidate == null) {
                return RefreshOutcome.COMPLETED;
            }
            SubmissionStore.StoredSubmission current = candidate.submission();
            if (current.state() != SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED
                    && current.state() != SubmissionStore.SubmissionState.ORIGINAL_MESSAGE_DELETED
                    && current.state() != SubmissionStore.SubmissionState.COMPLETED) {
                return switch (publishAndHandOff(current.sourceMessageId())) {
                    case NOT_PUBLISHABLE -> RefreshOutcome.COMPLETED;
                    case PUBLISHED, RETRY_SCHEDULED, SUPERSEDED -> RefreshOutcome.RETRY_SCHEDULED;
                };
            }

            GameResultStore.StoredGameResult result = results.findById(resultId).orElseThrow();
            if (!isPublishable(result)) return RefreshOutcome.COMPLETED;
            GameResultStore.PublicationClaim claim = results.claimCanonicalPublication(
                    resultId,
                    clock.instant().plusSeconds(LEASE_SECONDS)).orElse(null);
            if (claim == null) {
                return RefreshOutcome.RETRY_SCHEDULED;
            }
            claimToken = claim.token();
            SubmissionStore.CanonicalDeliveryAttempt deliveryAttempt = submissions.beginCanonicalDelivery(
                    current.sourceMessageId(), resultId, claimToken);
            long canonicalMessageId = publishOrEdit(
                    current,
                    result,
                    canonicalMessage(result, current.authorPlayerId(), current.publicationContext()));
            SubmissionStore.CanonicalRefreshCompletion completion = submissions.completeCanonicalRefresh(
                    current.sourceMessageId(), resultId, canonicalMessageId, claimToken, deliveryAttempt.refreshGeneration());
            return completion.refreshStillRequired() ? RefreshOutcome.RETRY_SCHEDULED : RefreshOutcome.COMPLETED;
        } catch (RuntimeException exception) {
            if (claimToken != null) {
                try {
                    results.releaseCanonicalPublicationClaim(resultId, claimToken);
                } catch (RuntimeException ignored) {
                    // The lease expires and the durable refresh request is retried by the scheduler or startup.
                }
            }
            return RefreshOutcome.RETRY_SCHEDULED;
        }
    }

    private long publishOrEdit(
            SubmissionStore.StoredSubmission submission,
            GameResultStore.StoredGameResult result,
            CanonicalResultMessage message) {
        long canonicalMessageId;
        if (result.canonicalMessageId().isPresent()) {
            long existingId = result.canonicalMessageId().getAsLong();
            try {
                discord.edit(submission.channelId(), existingId, message);
                canonicalMessageId = existingId;
            } catch (CanonicalMessageGateway.UnknownMessageException ignored) {
                canonicalMessageId = findOrCreateCanonicalMessage(submission.channelId(), message);
            }
        } else {
            canonicalMessageId = findOrCreateCanonicalMessage(submission.channelId(), message);
        }
        removeDuplicateCanonicalMessages(submission.channelId(), message.publicationKey(), canonicalMessageId);
        return canonicalMessageId;
    }

    private long findOrCreateCanonicalMessage(long channelId, CanonicalResultMessage message) {
        java.util.OptionalLong existing = discord.findAllByPublicationKey(channelId, message.publicationKey()).stream()
                .mapToLong(Long::longValue)
                .min();
        if (existing.isPresent()) {
            long canonicalMessageId = existing.getAsLong();
            discord.edit(channelId, canonicalMessageId, message);
            return canonicalMessageId;
        }
        return discord.create(channelId, message);
    }

    /** The persisted canonical ID wins; without one, the oldest Discord snowflake is the deterministic winner. */
    private void removeDuplicateCanonicalMessages(long channelId, String publicationKey, long canonicalMessageId) {
        for (Long messageId : discord.findAllByPublicationKey(channelId, publicationKey)) {
            if (messageId != canonicalMessageId) {
                discord.delete(channelId, messageId);
            }
        }
    }
    private CanonicalResultMessage canonicalMessage(
            GameResultStore.StoredGameResult result,
            long playerId,
            SubmissionStore.PublicationContext publicationContext) {
        List<GameResultStore.StoredGameResult> allResults = results.findAll();
        LocalDate date = result.parsedResult().gameDate();
        StreakSummary streaks = streakCalculator.calculate(
                allResults.stream()
                        .map(stored -> new StreakCalculator.PlayerResult(stored.playerId(), stored.parsedResult()))
                        .toList(),
                configuredPlayerIds,
                playerId,
                clock.instant().atZone(zoneId).toLocalDate());

        return new CanonicalResultMessage(
                players.findByDiscordUserId(playerId).orElseThrow().displayName(),
                result.parsedResult().gameType(),
                date,
                result.parsedResult().outcome(),
                result.parsedResult().duration(),
                result.parsedResult().board().orElse(null),
                streaks,
                contextual(streaks.personalComplete(), publicationContext.personalCompleteEstablished()),
                contextual(streaks.personalPerfect(), publicationContext.personalPerfectEstablished()),
                contextual(streaks.sharedComplete(), publicationContext.sharedCompleteEstablished()),
                contextual(streaks.sharedPerfect(), publicationContext.sharedPerfectEstablished()),
                result.parsedResult().quadWordsBoards(),
                result.parsedResult().gameType().name().toLowerCase(java.util.Locale.ROOT) + "-result-" + result.id());
    }

    private static boolean isPublishable(GameResultStore.StoredGameResult result) {
        return result.parsedResult().gameType() == GameType.GRIDWORDS || result.parsedResult().quadWordsBoards().isPresent();
    }

    private static OptionalInt contextual(int streak, boolean establishedByThisSubmission) {
        return establishedByThisSubmission && streak > 0 ? OptionalInt.of(streak) : OptionalInt.empty();
    }

    private enum PublicationOutcome {
        PUBLISHED,
        RETRY_SCHEDULED,
        SUPERSEDED,
        NOT_PUBLISHABLE
    }

    private enum RefreshOutcome {
        COMPLETED,
        RETRY_SCHEDULED
    }
    private static final class RefreshSchedule {
        private boolean scheduledOrRunning;
        private boolean rerunRequested;
    }
}
