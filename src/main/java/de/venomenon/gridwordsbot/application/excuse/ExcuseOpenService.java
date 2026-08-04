package de.venomenon.gridwordsbot.application.excuse;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibility;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityPolicy;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityRequest;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferContext;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelectionRequest;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelector;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseState;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStatus;
import de.venomenon.gridwordsbot.domain.model.DailyGameParticipation;
import de.venomenon.gridwordsbot.port.in.ExcuseOpenUseCase;
import de.venomenon.gridwordsbot.port.out.ExcuseStateStore;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

/**
 * Opens the ephemeral first round only after validating the canonical Discord message against
 * persisted server state. Initial options are created or reused by one store operation.
 */
public final class ExcuseOpenService implements ExcuseOpenUseCase {

    private final long configuredGuildId;
    private final long configuredChannelId;
    private final GameResultStore results;
    private final PlayerStore players;
    private final SubmissionStore submissions;
    private final ExcuseStateStore states;
    private final ExcuseEligibilityPolicy eligibilityPolicy;
    private final ExcuseCatalog catalog;
    private final ExcuseSelector selector;
    private final Clock clock;

    public ExcuseOpenService(
            long configuredGuildId,
            long configuredChannelId,
            GameResultStore results,
            PlayerStore players,
            SubmissionStore submissions,
            ExcuseStateStore states,
            ExcuseEligibilityPolicy eligibilityPolicy,
            ExcuseCatalog catalog,
            ExcuseSelector selector,
            Clock clock) {
        if (configuredGuildId <= 0 || configuredChannelId <= 0) {
            throw new IllegalArgumentException("configured Discord IDs must be positive");
        }
        this.configuredGuildId = configuredGuildId;
        this.configuredChannelId = configuredChannelId;
        this.results = Objects.requireNonNull(results, "results");
        this.players = Objects.requireNonNull(players, "players");
        this.submissions = Objects.requireNonNull(submissions, "submissions");
        this.states = Objects.requireNonNull(states, "states");
        this.eligibilityPolicy = Objects.requireNonNull(eligibilityPolicy, "eligibilityPolicy");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Result open(Request request) {
        Objects.requireNonNull(request, "request");
        if (request.guildId() != configuredGuildId || request.channelId() != configuredChannelId) {
            return rejected(Reason.CONTEXT_MISMATCH);
        }
        GameResultStore.StoredGameResult result = results.findById(request.gameResultId()).orElse(null);
        if (result == null) {
            return rejected(Reason.CONTEXT_MISMATCH);
        }
        if (result.playerId() != request.actorId()) {
            return rejected(Reason.NOT_RESULT_AUTHOR);
        }
        if (result.canonicalMessageId().isEmpty()
                || result.canonicalMessageId().getAsLong() != request.canonicalMessageId()) {
            return rejected(Reason.CONTEXT_MISMATCH);
        }
        SubmissionStore.CanonicalRefreshCandidate publication = submissions
                .findCurrentCanonicalPublicationCandidate(request.gameResultId()).orElse(null);
        if (publication == null || !isCurrentPublishedContext(publication.submission(), request)) {
            return rejected(Reason.CONTEXT_MISMATCH);
        }
        ExcuseState state = states.find(request.gameResultId()).orElse(null);
        if (state == null || state.status() != ExcuseStatus.AVAILABLE
                || state.offer().isEmpty() || !clock.instant().isBefore(state.offer().orElseThrow().expiresAt())) {
            return rejected(Reason.OFFER_UNAVAILABLE);
        }
        ExcuseOfferContext frozenContext = state.offerContext().orElse(null);
        if (frozenContext == null) {
            return rejected(Reason.OFFER_UNAVAILABLE);
        }
        DailyGameParticipation participation = DailyGameParticipation.fromPeriods(
                result.parsedResult().gameDate(), players.findGameParticipationPeriods());
        ExcuseEligibility eligibility = eligibilityPolicy.evaluate(new ExcuseEligibilityRequest(
                result.playerId(), result.parsedResult(), frozenContext.originalReceivedAt(), participation,
                List.of(), false), frozenContext.dailyComparison());
        if (!eligibility.eligible()) {
            return rejected(Reason.OFFER_UNAVAILABLE);
        }
        try {
            return states.loadOrCreateInitialOptions(
                            request.gameResultId(),
                            state.offer().orElseThrow().contextGeneration(),
                            () -> selector.select(
                                            catalog,
                                            eligibility.context(),
                                            ExcuseSelectionRequest.initial(java.util.Set.of(), java.util.Set.of()))
                                    .orElseThrow(NoInitialOptions::new))
                    .<Result>map(selection -> new Options(selection.options()))
                    .orElseGet(() -> rejected(Reason.OFFER_UNAVAILABLE));
        } catch (NoInitialOptions exception) {
            return rejected(Reason.OFFER_UNAVAILABLE);
        }
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

    private static Rejected rejected(Reason reason) {
        return new Rejected(reason);
    }

    private static final class NoInitialOptions extends RuntimeException {
    }
}
