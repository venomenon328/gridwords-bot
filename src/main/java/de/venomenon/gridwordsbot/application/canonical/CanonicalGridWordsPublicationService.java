package de.venomenon.gridwordsbot.application.canonical;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.streak.StreakCalculator;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import de.venomenon.gridwordsbot.port.out.CanonicalMessageGateway;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.PublicationRetryScheduler;
import de.venomenon.gridwordsbot.port.out.SourceMessageReactionGateway;
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
import java.util.concurrent.RejectedExecutionException;

/** Coordinates one resumable canonical GridWords publication without a Discord call in a transaction. */
public final class CanonicalGridWordsPublicationService {

    private static final long LEASE_SECONDS = 60;

    private final GameResultStore results;
    private final PlayerStore players;
    private final SubmissionStore submissions;
    private final CanonicalMessageGateway discord;
    private final Clock clock;
    private final ZoneId zoneId;
    private final List<Long> configuredPlayerIds;
    private final StreakCalculator streakCalculator;
    private final PublicationRetryScheduler retryScheduler;
    private final SourceMessageReactionGateway acceptedReactionGateway;
    private final Set<Long> scheduledRetries = ConcurrentHashMap.newKeySet();

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
                (at, action) -> { },
                (channelId, sourceMessageId) -> { });
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
            SourceMessageReactionGateway acceptedReactionGateway) {
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
        this.acceptedReactionGateway = Objects.requireNonNull(acceptedReactionGateway);
    }

    /** @return true only after the canonical ID and the submission state were persisted together. */
    public boolean publish(long sourceMessageId) {
        return publishOutcome(sourceMessageId) == PublicationOutcome.PUBLISHED;
    }

    /** Replays open publications after startup; superseded sources neither publish nor receive an acceptance reaction. */
    public void resumeOpenPublications() {
        for (SubmissionStore.StoredSubmission submission : submissions.findGridWordsAwaitingCanonicalPublication()) {
            if (publishOutcome(submission.sourceMessageId()) == PublicationOutcome.PUBLISHED) {
                acknowledgeDeferredPublication(submission);
            }
        }
    }

    private PublicationOutcome publishOutcome(long sourceMessageId) {
        long resultId = 0;
        UUID claimToken = null;
        try {
            SubmissionStore.StoredSubmission submission = submissions.findBySourceMessageId(sourceMessageId)
                    .orElseThrow();
            if (submission.state() == SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED) {
                return PublicationOutcome.PUBLISHED;
            }
            if (submission.state() == SubmissionStore.SubmissionState.SUPERSEDED) {
                return PublicationOutcome.SUPERSEDED;
            }

            resultId = submission.gameResultId().orElseThrow();
            GameResultStore.StoredGameResult result = results.findById(resultId).orElseThrow();
            if (result.parsedResult().gameType() != GameType.GRIDWORDS) {
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
                    if (claimToken != null) {
                        results.releaseCanonicalPublicationClaim(resultId, claimToken);
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
        PublicationOutcome outcome = publishOutcome(sourceMessageId);
        scheduledRetries.remove(sourceMessageId);
        if (outcome == PublicationOutcome.RETRY_SCHEDULED) {
            scheduleRetry(sourceMessageId);
            return;
        }
        if (outcome == PublicationOutcome.PUBLISHED) {
            submissions.findBySourceMessageId(sourceMessageId).ifPresent(this::acknowledgeDeferredPublication);
        }
    }

    private void acknowledgeDeferredPublication(SubmissionStore.StoredSubmission submission) {
        acceptedReactionGateway.addAcceptedReaction(submission.channelId(), submission.sourceMessageId());
    }

    private long publishOrEdit(
            SubmissionStore.StoredSubmission submission,
            GameResultStore.StoredGameResult result,
            CanonicalResultMessage message) {
        if (result.canonicalMessageId().isPresent()) {
            long existingId = result.canonicalMessageId().getAsLong();
            try {
                discord.edit(submission.channelId(), existingId, message);
                return existingId;
            } catch (CanonicalMessageGateway.UnknownMessageException ignored) {
                // The held result lease makes this controlled replacement unique.
            }
        }
        return discord.findByPublicationKey(submission.channelId(), message.publicationKey())
                .orElseGet(() -> discord.create(submission.channelId(), message));
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
                GameType.GRIDWORDS,
                date,
                result.parsedResult().outcome(),
                result.parsedResult().duration(),
                result.parsedResult().board().orElseThrow(),
                streaks,
                contextual(streaks.personalComplete(), publicationContext.personalCompleteEstablished()),
                contextual(streaks.personalPerfect(), publicationContext.personalPerfectEstablished()),
                contextual(streaks.sharedComplete(), publicationContext.sharedCompleteEstablished()),
                contextual(streaks.sharedPerfect(), publicationContext.sharedPerfectEstablished()),
                "gridwords-result-" + result.id());
    }

    private static OptionalInt contextual(int streak, boolean establishedByThisSubmission) {
        return establishedByThisSubmission && streak > 0 ? OptionalInt.of(streak) : OptionalInt.empty();
    }

    private enum PublicationOutcome {
        PUBLISHED,
        RETRY_SCHEDULED,
        SUPERSEDED
    }
}
