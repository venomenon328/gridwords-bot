package de.venomenon.gridwordsbot.application.canonical;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
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
import java.util.UUID;

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
    private final SourceMessageReactionGateway recoveredReactionGateway;

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
            SourceMessageReactionGateway recoveredReactionGateway) {
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
        this.recoveredReactionGateway = Objects.requireNonNull(recoveredReactionGateway);
    }

    /** @return true only after the canonical ID and the submission state were persisted together. */
    public boolean publish(long sourceMessageId) {
        long resultId = 0;
        UUID claimToken = null;
        try {
            SubmissionStore.StoredSubmission submission = submissions.findBySourceMessageId(sourceMessageId)
                    .orElseThrow();
            if (submission.state() == SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED) {
                return true;
            }

            resultId = submission.gameResultId().orElseThrow();
            GameResultStore.StoredGameResult result = results.findById(resultId).orElseThrow();
            if (result.parsedResult().gameType() != GameType.GRIDWORDS) {
                return true;
            }

            GameResultStore.PublicationClaim claim = results.claimCanonicalPublication(
                    resultId,
                    clock.instant().plusSeconds(LEASE_SECONDS)).orElse(null);
            if (claim == null) {
                return false;
            }
            claimToken = claim.token();

            long canonicalMessageId = publishOrEdit(submission, result, canonicalMessage(result, submission.authorPlayerId()));
            boolean completed = submissions.completeCanonicalPublication(
                    sourceMessageId,
                    resultId,
                    canonicalMessageId,
                    claimToken);
            if (!completed) {
                throw new IllegalStateException("canonical publication completion was not accepted");
            }
            return true;
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
            return false;
        }
    }

    /**
     * Replays open publications after startup. A skipped active lease is retried after expiry rather than left open.
     */
    public void resumeOpenPublications() {
        boolean retryRequired = false;
        for (SubmissionStore.StoredSubmission submission : submissions.findGridWordsAwaitingCanonicalPublication()) {
            if (publish(submission.sourceMessageId())) {
                recoveredReactionGateway.addAcceptedReaction(submission.channelId(), submission.sourceMessageId());
            } else {
                retryRequired = true;
            }
        }
        if (retryRequired) {
            retryScheduler.schedule(clock.instant().plusSeconds(LEASE_SECONDS + 1), this::resumeOpenPublications);
        }
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

    private CanonicalResultMessage canonicalMessage(GameResultStore.StoredGameResult result, long playerId) {
        List<GameResultStore.StoredGameResult> allResults = results.findAll();
        LocalDate date = result.parsedResult().gameDate();
        StreakSummary streaks = streakCalculator.calculate(
                allResults.stream()
                        .map(stored -> new StreakCalculator.PlayerResult(stored.playerId(), stored.parsedResult()))
                        .toList(),
                configuredPlayerIds,
                playerId,
                clock.instant().atZone(zoneId).toLocalDate());

        boolean personalComplete = complete(allResults, playerId, date);
        boolean personalPerfect = personalComplete && perfect(allResults, playerId, date);
        boolean sharedComplete = configuredPlayerIds.stream().allMatch(id -> complete(allResults, id, date));
        boolean sharedPerfect = sharedComplete
                && configuredPlayerIds.stream().allMatch(id -> perfect(allResults, id, date));

        // A persisted canonical ID identifies a correction/re-render, not the event that established a day state.
        boolean establishesDayState = result.canonicalMessageId().isEmpty();
        return new CanonicalResultMessage(
                players.findByDiscordUserId(playerId).orElseThrow().displayName(),
                GameType.GRIDWORDS,
                date,
                result.parsedResult().outcome(),
                result.parsedResult().duration(),
                result.parsedResult().board().orElseThrow(),
                streaks,
                contextual(streaks.personalComplete(), establishesDayState && personalComplete),
                contextual(streaks.personalPerfect(), establishesDayState && personalPerfect),
                contextual(streaks.sharedComplete(), establishesDayState && sharedComplete),
                contextual(streaks.sharedPerfect(), establishesDayState && sharedPerfect),
                "gridwords-result-" + result.id());
    }

    private static OptionalInt contextual(int streak, boolean establishedByThisPublication) {
        return establishedByThisPublication ? OptionalInt.of(streak) : OptionalInt.empty();
    }

    private static boolean complete(
            List<GameResultStore.StoredGameResult> results,
            long playerId,
            LocalDate date) {
        return results.stream()
                .filter(result -> result.playerId() == playerId && result.parsedResult().gameDate().equals(date))
                .map(result -> result.parsedResult().gameType())
                .distinct()
                .count() == 2;
    }

    private static boolean perfect(
            List<GameResultStore.StoredGameResult> results,
            long playerId,
            LocalDate date) {
        return results.stream()
                .filter(result -> result.playerId() == playerId && result.parsedResult().gameDate().equals(date))
                .allMatch(result -> result.parsedResult().outcome() instanceof ShareOutcome.Solved);
    }
}