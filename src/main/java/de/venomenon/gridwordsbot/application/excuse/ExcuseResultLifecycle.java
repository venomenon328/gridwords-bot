package de.venomenon.gridwordsbot.application.excuse;

import de.venomenon.gridwordsbot.domain.excuse.DailyComparisonSnapshot;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibility;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityPolicy;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityRequest;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOffer;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferContext;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferMetadata;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseRevalidation;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseState;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStatus;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseTemplate;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseTemplateRenderer;
import de.venomenon.gridwordsbot.domain.model.DailyGameParticipation;
import de.venomenon.gridwordsbot.port.out.ExcuseDailyResultQuery;
import de.venomenon.gridwordsbot.port.out.ExcuseStateStore;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Decides an offer exactly once for a newly inserted result and revalidates only an existing
 * available or selected state after corrections. Its caller owns the surrounding result transaction.
 */
public final class ExcuseResultLifecycle {

    private static final String CONTEXT_VERSION = "excuse-context-v1";

    private final boolean enabled;
    private final ExcuseStateStore states;
    private final ExcuseDailyResultQuery dailyResults;
    private final ExcuseEligibilityPolicy policy;
    private final ExcuseCatalog catalog;
    private final ExcuseTemplateRenderer renderer;
    private final Clock clock;
    private final Duration offerLifetime;

    private ExcuseResultLifecycle(
            boolean enabled,
            ExcuseStateStore states,
            ExcuseDailyResultQuery dailyResults,
            ExcuseEligibilityPolicy policy,
            ExcuseCatalog catalog,
            ExcuseTemplateRenderer renderer,
            Clock clock,
            Duration offerLifetime) {
        this.enabled = enabled;
        this.states = Objects.requireNonNull(states, "states");
        this.dailyResults = dailyResults;
        this.policy = policy;
        this.catalog = catalog;
        this.renderer = renderer;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.offerLifetime = offerLifetime;
    }

    public static ExcuseResultLifecycle disabled(ExcuseStateStore states, Clock clock) {
        return new ExcuseResultLifecycle(false, states, null, null, null, null, clock, null);
    }

    public static ExcuseResultLifecycle enabled(
            ExcuseStateStore states,
            ExcuseDailyResultQuery dailyResults,
            ExcuseEligibilityPolicy policy,
            ExcuseCatalog catalog,
            Clock clock,
            Duration offerLifetime) {
        if (offerLifetime == null || offerLifetime.isZero() || offerLifetime.isNegative()) {
            throw new IllegalArgumentException("offerLifetime must be positive");
        }
        return new ExcuseResultLifecycle(
                true, states, Objects.requireNonNull(dailyResults, "dailyResults"),
                Objects.requireNonNull(policy, "policy"), Objects.requireNonNull(catalog, "catalog"),
                new ExcuseTemplateRenderer(), clock, offerLifetime);
    }

    public void decideForNewResult(
            GameResultStore.StoredGameResult result,
            long sourceMessageId,
            Instant originalReceivedAt,
            DailyGameParticipation participation) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(originalReceivedAt, "originalReceivedAt");
        Objects.requireNonNull(participation, "participation");
        if (!enabled) {
            states.initializeNotOffered(result.id());
            return;
        }

        ExcuseEligibility eligibility = evaluate(result, originalReceivedAt, participation, null);
        ExcuseOfferContext offerContext = ExcuseOfferContext.initial(originalReceivedAt, eligibility);
        if (!eligibility.eligible() || !hasThreeRenderableTemplates(eligibility)) {
            states.initializeNotOffered(result.id());
            return;
        }
        Instant offeredAt = clock.instant();
        ExcuseOffer offer = new ExcuseOffer(
                result.id(), result.playerId(), result.parsedResult().gameType(),
                new ExcuseOfferMetadata(
                        sourceMessageId, catalog.version(), CONTEXT_VERSION, 1, offeredAt, offeredAt.plus(offerLifetime)));
        if (states.initializeAvailable(offer, offerContext).isEmpty()) {
            // A cooldown loss is the one negative decision for this new result; a replay cannot retry it.
            states.initializeNotOffered(result.id());
        }
    }

    public void revalidateExistingResult(
            GameResultStore.StoredGameResult result,
            DailyGameParticipation participation) {
        if (!enabled) {
            return;
        }
        ExcuseState state = states.find(result.id()).orElseThrow(
                () -> new IllegalStateException("newer result has no excuse state: " + result.id()));
        if (state.status() != ExcuseStatus.AVAILABLE && state.status() != ExcuseStatus.SELECTED) {
            return;
        }
        ExcuseOfferContext frozenContext = state.offerContext().orElseThrow(
                () -> new IllegalStateException("active excuse state has no frozen offer context"));
        ExcuseEligibility eligibility = evaluate(
                result, frozenContext.originalReceivedAt(), participation, frozenContext.dailyComparison());
        ExcuseOfferContext currentContext = frozenContext.withCurrentFingerprint(eligibility);

        if (state.status() == ExcuseStatus.AVAILABLE) {
            ExcuseRevalidation.Outcome outcome = !eligibility.eligible() || !hasThreeRenderableTemplates(eligibility)
                    ? ExcuseRevalidation.Outcome.INVALIDATE
                    : currentContext.contextFingerprint().equals(frozenContext.contextFingerprint())
                            ? ExcuseRevalidation.Outcome.KEEP_AVAILABLE
                            : ExcuseRevalidation.Outcome.REPLACE_AVAILABLE_CONTEXT;
            states.revalidate(new ExcuseRevalidation(result.id(), outcome, currentContext));
            return;
        }

        boolean selectedTextStillMatches = eligibility.eligible()
                && state.selection().flatMap(selection -> catalog.templates().stream()
                        .filter(template -> template.id().equals(selection.templateId()))
                        .findFirst()
                        .filter(template -> template.supports(eligibility.context()))
                        .flatMap(template -> renderer.render(template, eligibility.context()))
                        .filter(selection.renderedText()::equals))
                        .isPresent();
        states.revalidate(new ExcuseRevalidation(
                result.id(), selectedTextStillMatches
                        ? ExcuseRevalidation.Outcome.KEEP_SELECTED
                        : ExcuseRevalidation.Outcome.INVALIDATE,
                currentContext));
    }

    private ExcuseEligibility evaluate(
            GameResultStore.StoredGameResult result,
            Instant receivedAt,
            DailyGameParticipation participation,
            DailyComparisonSnapshot frozenComparison) {
        List<de.venomenon.gridwordsbot.domain.excuse.ExcuseDailyResult> prior = frozenComparison == null
                ? dailyResults.findPriorValidResults(
                        result.playerId(), result.parsedResult().gameType(), result.parsedResult().gameDate(),
                        participation.playersFor(result.parsedResult().gameType()))
                : List.of();
        return policy.evaluate(new ExcuseEligibilityRequest(
                result.playerId(), result.parsedResult(), receivedAt, participation, prior, false), frozenComparison);
    }

    private boolean hasThreeRenderableTemplates(ExcuseEligibility eligibility) {
        long candidates = catalog.templates().stream()
                .filter(ExcuseTemplate::selectable)
                .filter(template -> template.supports(eligibility.context()))
                .filter(template -> renderer.render(template, eligibility.context()).isPresent())
                .count();
        return candidates >= 3;
    }
}
