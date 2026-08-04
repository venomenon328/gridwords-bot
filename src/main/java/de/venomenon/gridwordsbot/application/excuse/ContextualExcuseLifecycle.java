package de.venomenon.gridwordsbot.application.excuse;

import de.venomenon.gridwordsbot.domain.excuse.DailyComparisonSnapshot;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibility;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityPolicy;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityRequest;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityThresholds;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOffer;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferContext;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferMetadata;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseRevalidation;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseState;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStatus;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseTemplate;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseTemplateRenderer;
import de.venomenon.gridwordsbot.domain.model.DailyGameParticipation;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PriorValidResultQuery;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/**
 * Contextual offer decision and revalidation. The configured business zone remains explicit at
 * this boundary even though stored game dates have already been normalized to it.
 */
public final class ContextualExcuseLifecycle implements ExcuseLifecycle {

    private static final String CONTEXT_VERSION = "excuse-context-v1";

    private final PriorValidResultQuery priorValidResultQuery;
    private final ZoneId businessZone;
    private final ExcuseEligibilityPolicy policy = new ExcuseEligibilityPolicy(ExcuseEligibilityThresholds.defaults());
    private final ExcuseTemplateRenderer renderer = new ExcuseTemplateRenderer();

    public ContextualExcuseLifecycle(PriorValidResultQuery priorValidResultQuery, ZoneId businessZone) {
        this.priorValidResultQuery = Objects.requireNonNull(priorValidResultQuery, "priorValidResultQuery");
        this.businessZone = Objects.requireNonNull(businessZone, "businessZone");
    }

    @Override
    public void decideForNewResult(
            GameResultStore.StoredGameResult result,
            long sourceMessageId,
            Instant originalReceivedAt,
            DailyGameParticipation participation,
            Context lifecycleContext) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(originalReceivedAt, "originalReceivedAt");
        Objects.requireNonNull(participation, "participation");
        Objects.requireNonNull(lifecycleContext, "lifecycleContext");

        ExcuseEligibility eligibility = evaluate(result, originalReceivedAt, participation, null, lifecycleContext);
        ExcuseOfferContext offerContext = ExcuseOfferContext.initial(originalReceivedAt, eligibility);
        if (!eligibility.eligible() || !hasThreeRenderableTemplates(eligibility, result.playerId(), lifecycleContext)) {
            lifecycleContext.states().initializeNotOffered(result.id());
            return;
        }
        Instant offeredAt = lifecycleContext.contextualClock().instant();
        ExcuseOffer offer = new ExcuseOffer(
                result.id(), result.playerId(), result.parsedResult().gameType(),
                new ExcuseOfferMetadata(
                        sourceMessageId,
                        lifecycleContext.contextualCatalog().version(),
                        CONTEXT_VERSION,
                        1,
                        offeredAt,
                        offeredAt.plus(lifecycleContext.contextualOfferLifetime())));
        if (lifecycleContext.states().initializeAvailable(offer, offerContext).isEmpty()) {
            // A cooldown loss is the one negative decision for this new result; a replay cannot retry it.
            lifecycleContext.states().initializeNotOffered(result.id());
        }
    }

    @Override
    public void revalidateExistingResult(
            GameResultStore.StoredGameResult result,
            DailyGameParticipation participation,
            Context lifecycleContext) {
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(participation, "participation");
        Objects.requireNonNull(lifecycleContext, "lifecycleContext");

        ExcuseState state = lifecycleContext.states().find(result.id()).orElseThrow(
                () -> new IllegalStateException("newer result has no excuse state: " + result.id()));
        if (state.status() != ExcuseStatus.AVAILABLE && state.status() != ExcuseStatus.SELECTED) {
            return;
        }
        ExcuseOfferContext frozenContext = state.offerContext().orElseThrow(
                () -> new IllegalStateException("active excuse state has no frozen offer context"));
        ExcuseEligibility eligibility = evaluate(
                result,
                frozenContext.originalReceivedAt(),
                participation,
                frozenContext.dailyComparison(),
                lifecycleContext);
        ExcuseOfferContext currentContext = frozenContext.withCurrentFingerprint(eligibility);

        if (state.status() == ExcuseStatus.AVAILABLE) {
            ExcuseRevalidation.Outcome outcome = !eligibility.eligible()
                    || !hasThreeRenderableTemplates(eligibility, result.playerId(), lifecycleContext)
                    ? ExcuseRevalidation.Outcome.INVALIDATE
                    : currentContext.contextFingerprint().equals(frozenContext.contextFingerprint())
                            ? ExcuseRevalidation.Outcome.KEEP_AVAILABLE
                            : ExcuseRevalidation.Outcome.REPLACE_AVAILABLE_CONTEXT;
            lifecycleContext.states().revalidateAndRequestCanonicalRefresh(
                    new ExcuseRevalidation(result.id(), outcome, currentContext));
            return;
        }

        boolean selectedTextStillMatches = eligibility.eligible()
                && state.selection().flatMap(selection -> lifecycleContext.contextualCatalog().templates().stream()
                        .filter(template -> template.id().equals(selection.templateId()))
                        .findFirst()
                        .filter(template -> template.supports(eligibility.context()))
                        .flatMap(template -> renderer.render(template, eligibility.context()))
                        .filter(selection.renderedText()::equals))
                        .isPresent();
        lifecycleContext.states().revalidateAndRequestCanonicalRefresh(new ExcuseRevalidation(
                result.id(),
                selectedTextStillMatches
                        ? ExcuseRevalidation.Outcome.KEEP_SELECTED
                        : ExcuseRevalidation.Outcome.INVALIDATE,
                currentContext));
    }

    private ExcuseEligibility evaluate(
            GameResultStore.StoredGameResult result,
            Instant receivedAt,
            DailyGameParticipation participation,
            DailyComparisonSnapshot frozenComparison,
            Context lifecycleContext) {
        List<de.venomenon.gridwordsbot.domain.excuse.ExcuseDailyResult> prior = frozenComparison == null
                ? priorValidResultQuery.findPriorValidResults(
                        result.playerId(),
                        result.parsedResult().gameType(),
                        result.parsedResult().gameDate(),
                        participation.playersFor(result.parsedResult().gameType()))
                : List.of();
        return policy.evaluate(new ExcuseEligibilityRequest(
                result.playerId(), result.parsedResult(), receivedAt, participation, prior, false), frozenComparison);
    }

    private boolean hasThreeRenderableTemplates(
            ExcuseEligibility eligibility,
            long playerId,
            Context lifecycleContext) {
        java.util.Set<String> hardExcluded = lifecycleContext.states().findRecentSelections(playerId, 1).stream()
                .map(de.venomenon.gridwordsbot.domain.excuse.ExcuseSelectionHistoryEntry::templateId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        long candidates = lifecycleContext.contextualCatalog().templates().stream()
                .filter(ExcuseTemplate::selectable)
                .filter(template -> template.supports(eligibility.context()))
                .filter(template -> !hardExcluded.contains(template.id()))
                .filter(template -> renderer.render(template, eligibility.context()).isPresent())
                .count();
        return candidates >= 3;
    }
}
