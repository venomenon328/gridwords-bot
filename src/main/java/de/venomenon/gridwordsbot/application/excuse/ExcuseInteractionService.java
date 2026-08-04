package de.venomenon.gridwordsbot.application.excuse;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibility;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOption;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOptionSelection;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseRound;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelection;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelectionRequest;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelector;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseState;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle;
import de.venomenon.gridwordsbot.port.in.ExcuseInteractionUseCase;
import de.venomenon.gridwordsbot.port.out.CanonicalRefreshWakeUp;
import de.venomenon.gridwordsbot.port.out.ExcuseStateStore;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Executes authorized ephemeral picks, rerolls, and decline without touching Discord directly. */
public final class ExcuseInteractionService implements ExcuseInteractionUseCase {

    private final ExcuseStateStore states;
    private final ExcuseCatalog catalog;
    private final ExcuseSelector selector;
    private final Clock clock;
    private final CanonicalRefreshWakeUp refreshWakeUp;
    private final ExcuseInteractionAuthorizer authorizer;
    private final ExcuseAvailableContextResolver contextResolver;

    public ExcuseInteractionService(
            long configuredGuildId,
            long configuredChannelId,
            GameResultStore results,
            PlayerStore players,
            SubmissionStore submissions,
            ExcuseStateStore states,
            de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityPolicy eligibilityPolicy,
            ExcuseCatalog catalog,
            ExcuseSelector selector,
            Clock clock,
            CanonicalRefreshWakeUp refreshWakeUp) {
        this.states = Objects.requireNonNull(states, "states");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.refreshWakeUp = Objects.requireNonNull(refreshWakeUp, "refreshWakeUp");
        this.authorizer = new ExcuseInteractionAuthorizer(
                configuredGuildId, configuredChannelId, results, submissions, states, clock);
        this.contextResolver = new ExcuseAvailableContextResolver(players, eligibilityPolicy);
    }

    @Override
    public Result openStyleMenu(ActionRequest request) {
        Authorization authorization = authorize(request);
        if (authorization.rejected() != null) {
            return authorization.rejected();
        }
        AuthorizedContext authorized = authorization.authorized();
        if (authorized.state().rerollUsed()) {
            return new Rejected(Reason.REROLL_UNAVAILABLE);
        }
        List<ExcuseStyle> styles = availableStyles(authorized);
        return styles.isEmpty() ? new Rejected(Reason.REROLL_UNAVAILABLE) : new StyleMenu(styles);
    }

    @Override
    public Result selectStyle(StyleRequest request) {
        Authorization authorization = authorize(request.action());
        if (authorization.rejected() != null) {
            return authorization.rejected();
        }
        AuthorizedContext authorized = authorization.authorized();
        if (authorized.state().rerollUsed() || !availableStyles(authorized).contains(request.style())) {
            return new Rejected(Reason.REROLL_UNAVAILABLE);
        }
        Set<String> shownTemplateIds = shownTemplateIds(authorized);
        try {
            Optional<ExcuseSelection> created = states.loadOrCreateStyleRerollOptions(
                    request.action().gameResultId(), request.action().contextGeneration(),
                    () -> selector.select(
                                    catalog,
                                    authorized.eligibility().context(),
                                    ExcuseSelectionRequest.styleReroll(request.style(), shownTemplateIds))
                            .orElseThrow(NoRerollOptions::new));
            return created.<Result>map(selection -> new Options(selection.options(), List.of()))
                    .orElseGet(() -> new Rejected(Reason.REROLL_UNAVAILABLE));
        } catch (NoRerollOptions exception) {
            return new Rejected(Reason.REROLL_UNAVAILABLE);
        }
    }

    @Override
    public Result pick(PickRequest request) {
        Authorization authorization = authorize(request.action());
        if (authorization.rejected() != null) {
            return authorization.rejected();
        }
        AuthorizedContext authorized = authorization.authorized();
        Optional<ExcuseState> selected = states.selectAndRequestCanonicalRefresh(new ExcuseOptionSelection(
                request.action().gameResultId(), request.action().contextGeneration(), request.round(), request.position(),
                clock.instant()));
        if (selected.isEmpty()) {
            return new Rejected(Reason.OPTION_UNAVAILABLE);
        }
        wakeUp(request.action().gameResultId());
        return Selected.INSTANCE;
    }

    @Override
    public Result decline(ActionRequest request) {
        Authorization authorization = authorize(request);
        if (authorization.rejected() != null) {
            return authorization.rejected();
        }
        if (states.declineAndRequestCanonicalRefresh(
                request.gameResultId(), request.contextGeneration(), clock.instant()).isEmpty()) {
            return new Rejected(Reason.OFFER_UNAVAILABLE);
        }
        wakeUp(request.gameResultId());
        return Declined.INSTANCE;
    }

    private Authorization authorize(ActionRequest request) {
        ExcuseInteractionAuthorizer.Result result = authorizer.authorizeFollowUp(new ExcuseInteractionAuthorizer.FollowUpRequest(
                request.guildId(), request.channelId(), request.gameResultId(), request.actorId(),
                request.contextGeneration()));
        if (result instanceof ExcuseInteractionAuthorizer.Rejected rejected) {
            return new Authorization(null, new Rejected(switch (rejected.reason()) {
                case NOT_RESULT_AUTHOR -> Reason.NOT_RESULT_AUTHOR;
                case CONTEXT_MISMATCH -> Reason.CONTEXT_MISMATCH;
                case OFFER_UNAVAILABLE -> Reason.OFFER_UNAVAILABLE;
            }));
        }
        ExcuseInteractionAuthorizer.Authorized authorized = (ExcuseInteractionAuthorizer.Authorized) result;
        ExcuseEligibility eligibility = contextResolver.resolve(authorized.gameResult(), authorized.state()).orElse(null);
        if (eligibility == null) {
            return new Authorization(null, new Rejected(Reason.OFFER_UNAVAILABLE));
        }
        return new Authorization(new AuthorizedContext(authorized.state(), eligibility), null);
    }

    private List<ExcuseStyle> availableStyles(AuthorizedContext authorized) {
        return selector.availableStyles(catalog, authorized.eligibility().context(), shownTemplateIds(authorized));
    }

    private Set<String> shownTemplateIds(AuthorizedContext authorized) {
        int generation = authorized.state().offer().orElseThrow().contextGeneration();
        return states.findOptions(authorized.state().gameResultId(), generation).stream()
                .map(ExcuseOption::templateId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private void wakeUp(long gameResultId) {
        try {
            refreshWakeUp.wakeUp(gameResultId);
        } catch (RuntimeException ignored) {
            // The state transition already committed the durable refresh request for recovery.
        }
    }

    private record AuthorizedContext(ExcuseState state, ExcuseEligibility eligibility) {
    }

    private record Authorization(AuthorizedContext authorized, Rejected rejected) {
        private Authorization {
            if ((authorized == null) == (rejected == null)) {
                throw new IllegalArgumentException("authorization must contain exactly one outcome");
            }
        }
    }

    private static final class NoRerollOptions extends RuntimeException {
    }
}
