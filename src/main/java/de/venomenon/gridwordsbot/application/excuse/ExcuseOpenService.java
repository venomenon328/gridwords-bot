package de.venomenon.gridwordsbot.application.excuse;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibility;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityPolicy;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityRequest;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelectionRequest;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseSelector;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseState;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStatus;
import de.venomenon.gridwordsbot.port.in.ExcuseOpenUseCase;
import de.venomenon.gridwordsbot.port.out.ExcuseStateStore;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import de.venomenon.gridwordsbot.port.out.SubmissionStore;
import java.time.Clock;
import java.util.Objects;

/**
 * Opens the ephemeral first round only after validating the canonical Discord message against
 * persisted server state. Initial options are created or reused by one store operation.
 */
public final class ExcuseOpenService implements ExcuseOpenUseCase {

    private final ExcuseStateStore states;
    private final ExcuseCatalog catalog;
    private final ExcuseSelector selector;
    private final ExcuseInteractionAuthorizer authorizer;
    private final ExcuseAvailableContextResolver contextResolver;

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
        this.states = Objects.requireNonNull(states, "states");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.selector = Objects.requireNonNull(selector, "selector");
        this.authorizer = new ExcuseInteractionAuthorizer(
                configuredGuildId, configuredChannelId, results, submissions, states, clock);
        this.contextResolver = new ExcuseAvailableContextResolver(players, eligibilityPolicy);
    }

    @Override
    public Result open(Request request) {
        Objects.requireNonNull(request, "request");
        ExcuseInteractionAuthorizer.Result authorization = authorizer.authorize(
                new ExcuseInteractionAuthorizer.Request(
                        request.guildId(), request.channelId(), request.canonicalMessageId(), request.gameResultId(),
                        request.actorId(), null));
        if (authorization instanceof ExcuseInteractionAuthorizer.Rejected rejected) {
            return rejected(rejected.reason());
        }
        ExcuseInteractionAuthorizer.Authorized authorized = (ExcuseInteractionAuthorizer.Authorized) authorization;
        ExcuseEligibility eligibility = contextResolver.resolve(authorized.gameResult(), authorized.state()).orElse(null);
        if (eligibility == null) {
            return rejected(Reason.OFFER_UNAVAILABLE);
        }
        try {
            return states.loadOrCreateInitialOptions(
                            request.gameResultId(),
                            authorized.state().offer().orElseThrow().contextGeneration(),
                            () -> selector.select(
                                            catalog,
                                            eligibility.context(),
                                            ExcuseSelectionRequest.initial(java.util.Set.of(), java.util.Set.of()))
                                    .orElseThrow(NoInitialOptions::new))
                    .<Result>map(selection -> new Options(
                            authorized.state().offer().orElseThrow().contextGeneration(),
                            selection.options(),
                            selector.availableStyles(
                                    catalog,
                                    eligibility.context(),
                                    selection.options().stream().map(option -> option.templateId())
                                            .collect(java.util.stream.Collectors.toSet()))))
                    .orElseGet(() -> rejected(Reason.OFFER_UNAVAILABLE));
        } catch (NoInitialOptions exception) {
            return rejected(Reason.OFFER_UNAVAILABLE);
        }
    }

    private static Rejected rejected(ExcuseInteractionAuthorizer.Reason reason) {
        return switch (reason) {
            case NOT_RESULT_AUTHOR -> rejected(Reason.NOT_RESULT_AUTHOR);
            case CONTEXT_MISMATCH -> rejected(Reason.CONTEXT_MISMATCH);
            case OFFER_UNAVAILABLE -> rejected(Reason.OFFER_UNAVAILABLE);
        };
    }

    private static Rejected rejected(Reason reason) {
        return new Rejected(reason);
    }

    private static final class NoInitialOptions extends RuntimeException {
    }
}
