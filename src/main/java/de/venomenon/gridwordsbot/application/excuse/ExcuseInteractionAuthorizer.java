package de.venomenon.gridwordsbot.application.excuse;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseState;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStatus;
import de.venomenon.gridwordsbot.port.out.ExcuseStateStore;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.util.Objects;

/** Shared server-side boundary for every ephemeral excuse interaction. */
final class ExcuseInteractionAuthorizer {

    private final long configuredGuildId;
    private final long configuredChannelId;
    private final GameResultStore results;
    private final SubmissionStore submissions;
    private final ExcuseStateStore states;
    private final Clock clock;

    ExcuseInteractionAuthorizer(
            long configuredGuildId,
            long configuredChannelId,
            GameResultStore results,
            SubmissionStore submissions,
            ExcuseStateStore states,
            Clock clock) {
        if (configuredGuildId <= 0 || configuredChannelId <= 0) {
            throw new IllegalArgumentException("configured Discord IDs must be positive");
        }
        this.configuredGuildId = configuredGuildId;
        this.configuredChannelId = configuredChannelId;
        this.results = Objects.requireNonNull(results, "results");
        this.submissions = Objects.requireNonNull(submissions, "submissions");
        this.states = Objects.requireNonNull(states, "states");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    Result authorize(Request request) {
        Objects.requireNonNull(request, "request");
        if (request.guildId() != configuredGuildId || request.channelId() != configuredChannelId) {
            return new Rejected(Reason.CONTEXT_MISMATCH);
        }
        GameResultStore.StoredGameResult result = results.findById(request.gameResultId()).orElse(null);
        if (result == null) {
            return new Rejected(Reason.CONTEXT_MISMATCH);
        }
        if (result.playerId() != request.actorId()) {
            return new Rejected(Reason.NOT_RESULT_AUTHOR);
        }
        if (result.canonicalMessageId().isEmpty()
                || result.canonicalMessageId().getAsLong() != request.canonicalMessageId()) {
            return new Rejected(Reason.CONTEXT_MISMATCH);
        }
        SubmissionStore.CanonicalRefreshCandidate publication = submissions
                .findCurrentCanonicalPublicationCandidate(request.gameResultId()).orElse(null);
        if (publication == null || !isCurrentPublishedContext(publication.submission(), request)) {
            return new Rejected(Reason.CONTEXT_MISMATCH);
        }
        ExcuseState state = states.find(request.gameResultId()).orElse(null);
        if (state == null || state.status() != ExcuseStatus.AVAILABLE
                || state.offer().isEmpty() || !clock.instant().isBefore(state.offer().orElseThrow().expiresAt())) {
            return new Rejected(Reason.OFFER_UNAVAILABLE);
        }
        if (request.contextGeneration() != null
                && state.offer().orElseThrow().contextGeneration() != request.contextGeneration()) {
            return new Rejected(Reason.OFFER_UNAVAILABLE);
        }
        return new Authorized(result, state);
    }

    private static boolean isCurrentPublishedContext(SubmissionStore.StoredSubmission submission, Request request) {
        if (submission.guildId() != request.guildId()
                || submission.channelId() != request.channelId()
                || submission.authorPlayerId() != request.actorId()
                || submission.gameResultId().isEmpty()
                || submission.gameResultId().orElseThrow() != request.gameResultId()) {
            return false;
        }
        return submission.state() == SubmissionStore.SubmissionState.CANONICAL_MESSAGE_PUBLISHED
                || submission.state() == SubmissionStore.SubmissionState.ORIGINAL_MESSAGE_DELETED
                || submission.state() == SubmissionStore.SubmissionState.COMPLETED;
    }

    record Request(
            long guildId,
            long channelId,
            long canonicalMessageId,
            long gameResultId,
            long actorId,
            Integer contextGeneration) {
        Request {
            if (guildId <= 0 || channelId <= 0 || canonicalMessageId <= 0 || gameResultId <= 0 || actorId <= 0) {
                throw new IllegalArgumentException("Discord and result IDs must be positive");
            }
            if (contextGeneration != null && contextGeneration < 1) {
                throw new IllegalArgumentException("contextGeneration must be positive when provided");
            }
        }
    }

    sealed interface Result permits Authorized, Rejected {
    }

    record Authorized(GameResultStore.StoredGameResult gameResult, ExcuseState state) implements Result {
        Authorized {
            Objects.requireNonNull(gameResult, "gameResult");
            Objects.requireNonNull(state, "state");
        }
    }

    record Rejected(Reason reason) implements Result {
        Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    enum Reason {
        CONTEXT_MISMATCH,
        NOT_RESULT_AUTHOR,
        OFFER_UNAVAILABLE
    }
}
