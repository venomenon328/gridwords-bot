package de.venomenon.gridwordsbot.application.excuse;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibility;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityPolicy;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseEligibilityRequest;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseOfferContext;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseState;
import de.venomenon.gridwordsbot.domain.model.DailyGameParticipation;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Reconstructs the frozen offer context using the same policy for open and every follow-up action. */
final class ExcuseAvailableContextResolver {

    private final PlayerStore players;
    private final ExcuseEligibilityPolicy eligibilityPolicy;

    ExcuseAvailableContextResolver(PlayerStore players, ExcuseEligibilityPolicy eligibilityPolicy) {
        this.players = Objects.requireNonNull(players, "players");
        this.eligibilityPolicy = Objects.requireNonNull(eligibilityPolicy, "eligibilityPolicy");
    }

    Optional<ExcuseEligibility> resolve(GameResultStore.StoredGameResult result, ExcuseState state) {
        ExcuseOfferContext frozenContext = state.offerContext().orElse(null);
        if (frozenContext == null) {
            return Optional.empty();
        }
        DailyGameParticipation participation = DailyGameParticipation.fromPeriods(
                result.parsedResult().gameDate(), players.findGameParticipationPeriods());
        ExcuseEligibility eligibility = eligibilityPolicy.evaluate(new ExcuseEligibilityRequest(
                result.playerId(), result.parsedResult(), frozenContext.originalReceivedAt(), participation,
                List.of(), false), frozenContext.dailyComparison());
        return eligibility.eligible() ? Optional.of(eligibility) : Optional.empty();
    }
}
