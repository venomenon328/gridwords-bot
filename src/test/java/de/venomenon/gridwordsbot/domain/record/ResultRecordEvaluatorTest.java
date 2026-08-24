package de.venomenon.gridwordsbot.domain.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResultRecordEvaluatorTest {
    private static final GameType GAME = GameType.GRIDWORDS;
    private static final LocalDate CANDIDATE_DATE = LocalDate.of(2026, 8, 1);
    private static final Instant CANDIDATE_ACCEPTED_AT = Instant.parse("2026-08-01T12:00:00Z");
    private static final long CANDIDATE_PLAYER = 11;

    private final ResultRecordEvaluator evaluator = new ResultRecordEvaluator();

    @Test
    void initializesAllDefinitionsSilentlyFromTheCanonicalBestSource() {
        ResultRecordObservation candidate = observation(
                100, CANDIDATE_PLAYER, CANDIDATE_DATE, CANDIDATE_ACCEPTED_AT, 2, 120);
        Instant sharedAcceptedAt = Instant.parse("2026-07-29T10:00:00Z");
        ResultRecordObservation laterDate = observation(
                20, CANDIDATE_PLAYER, LocalDate.of(2026, 7, 30), sharedAcceptedAt.minusSeconds(10), 2, 120);
        ResultRecordObservation laterAcceptance = observation(
                21, CANDIDATE_PLAYER, LocalDate.of(2026, 7, 29), sharedAcceptedAt.plusSeconds(1), 2, 120);
        ResultRecordObservation largerStableId = observation(
                22, CANDIDATE_PLAYER, LocalDate.of(2026, 7, 29), sharedAcceptedAt, 2, 120);
        ResultRecordObservation canonical = observation(
                19, CANDIDATE_PLAYER, LocalDate.of(2026, 7, 29), sharedAcceptedAt, 2, 120);

        ResultRecordEvaluationSet result = evaluator.evaluate(
                candidate,
                new ResultRecordHistorySnapshot(List.of(laterDate, laterAcceptance, largerStableId, canonical)),
                List.of(),
                RecordProcessingOrigin.LIVE_SUBMISSION);

        assertThat(result.evaluations()).hasSize(6);
        assertThat(result.publicAnnouncements()).isEmpty();
        assertThat(result.evaluations()).allSatisfy(evaluation -> {
            assertThat(evaluation.action()).isEqualTo(ResultRecordEvaluationAction.INITIALIZED);
            assertThat(evaluation.previousState()).isEmpty();
            assertThat(evaluation.resultingSourceReference().resultId()).isEqualTo(19);
        });
    }

    @Test
    void fewestAttemptsPrioritizesAttemptsAndThenUsesDurationAsTieBreaker() {
        ResultRecordObservation fewerButSlower = candidate(2, 600);
        ResultRecordObservation moreButFaster = observation(
                1, CANDIDATE_PLAYER, CANDIDATE_DATE.minusDays(1), CANDIDATE_ACCEPTED_AT.minusSeconds(1), 3, 1);

        ResultRecordEvaluation first = evaluation(evaluator.evaluate(
                fewerButSlower,
                ResultRecordHistorySnapshot.empty(),
                List.of(state(moreButFaster, ResultRecordMetric.FEWEST_ATTEMPTS,
                        new RecordScope.Personal(CANDIDATE_PLAYER))),
                RecordProcessingOrigin.LIVE_SUBMISSION),
                ResultRecordMetric.FEWEST_ATTEMPTS,
                RecordScopeType.PERSONAL);

        assertThat(first.action()).isEqualTo(ResultRecordEvaluationAction.IMPROVED);
        assertThat(first.previousValue()).contains(new AttemptsDurationRecordValue(3, Duration.ofSeconds(1)));
        assertThat(first.resultingValue()).isEqualTo(new AttemptsDurationRecordValue(2, Duration.ofSeconds(600)));

        ResultRecordObservation sameAttemptsFaster = candidate(3, 5);
        ResultRecordObservation sameAttemptsSlower = observation(
                2, CANDIDATE_PLAYER, CANDIDATE_DATE.minusDays(1), CANDIDATE_ACCEPTED_AT.minusSeconds(1), 3, 10);
        ResultRecordEvaluation tieBreaker = evaluation(evaluator.evaluate(
                sameAttemptsFaster,
                ResultRecordHistorySnapshot.empty(),
                List.of(state(sameAttemptsSlower, ResultRecordMetric.FEWEST_ATTEMPTS,
                        new RecordScope.Personal(CANDIDATE_PLAYER))),
                RecordProcessingOrigin.LIVE_SUBMISSION),
                ResultRecordMetric.FEWEST_ATTEMPTS,
                RecordScopeType.PERSONAL);

        assertThat(tieBreaker.action()).isEqualTo(ResultRecordEvaluationAction.IMPROVED);
    }

    @Test
    void fastestAndSlowestSolutionsIgnoreAttemptCount() {
        ResultRecordObservation candidate = candidate(6, 100);
        ResultRecordObservation fastestCurrent = observation(
                1, CANDIDATE_PLAYER, CANDIDATE_DATE.minusDays(2), CANDIDATE_ACCEPTED_AT.minusSeconds(2), 1, 101);
        ResultRecordObservation slowestCurrent = observation(
                2, CANDIDATE_PLAYER, CANDIDATE_DATE.minusDays(1), CANDIDATE_ACCEPTED_AT.minusSeconds(1), 1, 99);

        ResultRecordEvaluationSet result = evaluator.evaluate(
                candidate,
                ResultRecordHistorySnapshot.empty(),
                List.of(
                        state(fastestCurrent, ResultRecordMetric.FASTEST_SOLUTION,
                                new RecordScope.Personal(CANDIDATE_PLAYER)),
                        state(slowestCurrent, ResultRecordMetric.SLOWEST_SUCCESSFUL_SOLUTION,
                                new RecordScope.Personal(CANDIDATE_PLAYER))),
                RecordProcessingOrigin.LIVE_SUBMISSION);

        assertThat(evaluation(result, ResultRecordMetric.FASTEST_SOLUTION, RecordScopeType.PERSONAL).action())
                .isEqualTo(ResultRecordEvaluationAction.IMPROVED);
        assertThat(evaluation(result, ResultRecordMetric.SLOWEST_SUCCESSFUL_SOLUTION,
                RecordScopeType.PERSONAL).action()).isEqualTo(ResultRecordEvaluationAction.IMPROVED);
    }

    @Test
    void equalValuesRemainUnchangedAndKeepTheExistingSource() {
        ResultRecordObservation candidate = candidate(2, 120);
        ResultRecordObservation existing = observation(
                7, CANDIDATE_PLAYER, CANDIDATE_DATE.minusDays(2), CANDIDATE_ACCEPTED_AT.minusSeconds(20), 2, 120);
        ResultRecordStateSnapshot current = state(
                existing, ResultRecordMetric.FEWEST_ATTEMPTS, new RecordScope.Personal(CANDIDATE_PLAYER));

        ResultRecordEvaluation result = evaluation(evaluator.evaluate(
                candidate,
                new ResultRecordHistorySnapshot(List.of(existing)),
                List.of(current),
                RecordProcessingOrigin.LIVE_SUBMISSION),
                ResultRecordMetric.FEWEST_ATTEMPTS,
                RecordScopeType.PERSONAL);

        assertThat(result.action()).isEqualTo(ResultRecordEvaluationAction.UNCHANGED);
        assertThat(result.resultingState()).isEqualTo(current);
        assertThat(result.resultingSourceReference().resultId()).isEqualTo(7);
        assertThat(result.publicAnnouncementEligible()).isFalse();
    }

    @Test
    void personalAnnouncementRequiresFivePriorResultsAndNeverCountsTheCandidate() {
        ResultRecordObservation candidate = candidate(2, 100);
        ResultRecordObservation currentSource = observation(
                1, CANDIDATE_PLAYER, CANDIDATE_DATE.minusDays(20), CANDIDATE_ACCEPTED_AT.minusSeconds(20), 3, 100);
        ResultRecordStateSnapshot current = state(
                currentSource, ResultRecordMetric.FEWEST_ATTEMPTS,
                new RecordScope.Personal(CANDIDATE_PLAYER));

        ResultRecordEvaluation withFour = evaluation(evaluator.evaluate(
                candidate,
                new ResultRecordHistorySnapshot(history(4, CANDIDATE_PLAYER, 10)),
                List.of(current),
                RecordProcessingOrigin.LIVE_SUBMISSION),
                ResultRecordMetric.FEWEST_ATTEMPTS,
                RecordScopeType.PERSONAL);
        ResultRecordEvaluation withFive = evaluation(evaluator.evaluate(
                candidate,
                new ResultRecordHistorySnapshot(history(5, CANDIDATE_PLAYER, 20)),
                List.of(current),
                RecordProcessingOrigin.LIVE_SUBMISSION),
                ResultRecordMetric.FEWEST_ATTEMPTS,
                RecordScopeType.PERSONAL);

        assertThat(withFour.action()).isEqualTo(ResultRecordEvaluationAction.IMPROVED);
        assertThat(withFour.publicAnnouncementEligible()).isFalse();
        assertThat(withFive.publicAnnouncementEligible()).isTrue();
        assertThatIllegalArgumentException().isThrownBy(() -> evaluator.evaluate(
                candidate,
                new ResultRecordHistorySnapshot(List.of(candidate)),
                List.of(current),
                RecordProcessingOrigin.LIVE_SUBMISSION))
                .withMessageContaining("candidate");
    }

    @Test
    void serverAnnouncementRequiresTenPriorResultsFromAtLeastTwoPlayers() {
        ResultRecordObservation candidate = candidate(2, 100);
        ResultRecordObservation currentSource = observation(
                1, 77, CANDIDATE_DATE.minusDays(30), CANDIDATE_ACCEPTED_AT.minusSeconds(30), 3, 100);
        ResultRecordStateSnapshot current = state(
                currentSource, ResultRecordMetric.FEWEST_ATTEMPTS, new RecordScope.ServerIndividual());
        List<ResultRecordObservation> onePlayer = history(10, 77, 30);
        List<ResultRecordObservation> twoPlayers = new ArrayList<>(onePlayer);
        twoPlayers.set(9, observation(
                39, 88, CANDIDATE_DATE.minusDays(1), CANDIDATE_ACCEPTED_AT.minusSeconds(1), 4, 200));

        ResultRecordEvaluation onePlayerResult = evaluation(evaluator.evaluate(
                candidate,
                new ResultRecordHistorySnapshot(onePlayer),
                List.of(current),
                RecordProcessingOrigin.LIVE_SUBMISSION),
                ResultRecordMetric.FEWEST_ATTEMPTS,
                RecordScopeType.SERVER_INDIVIDUAL);
        ResultRecordEvaluation twoPlayerResult = evaluation(evaluator.evaluate(
                candidate,
                new ResultRecordHistorySnapshot(twoPlayers),
                List.of(current),
                RecordProcessingOrigin.LIVE_SUBMISSION),
                ResultRecordMetric.FEWEST_ATTEMPTS,
                RecordScopeType.SERVER_INDIVIDUAL);

        assertThat(onePlayerResult.publicAnnouncementEligible()).isFalse();
        assertThat(twoPlayerResult.publicAnnouncementEligible()).isTrue();
    }

    @Test
    void personalAndServerScopesAreEvaluatedIndependently() {
        ResultRecordObservation candidate = candidate(2, 100);
        ResultRecordObservation personalCurrent = observation(
                1, CANDIDATE_PLAYER, CANDIDATE_DATE.minusDays(2), CANDIDATE_ACCEPTED_AT.minusSeconds(2), 3, 100);
        ResultRecordObservation serverCurrent = observation(
                2, 77, CANDIDATE_DATE.minusDays(1), CANDIDATE_ACCEPTED_AT.minusSeconds(1), 1, 100);

        ResultRecordEvaluationSet result = evaluator.evaluate(
                candidate,
                ResultRecordHistorySnapshot.empty(),
                List.of(
                        state(personalCurrent, ResultRecordMetric.FEWEST_ATTEMPTS,
                                new RecordScope.Personal(CANDIDATE_PLAYER)),
                        state(serverCurrent, ResultRecordMetric.FEWEST_ATTEMPTS,
                                new RecordScope.ServerIndividual())),
                RecordProcessingOrigin.LIVE_SUBMISSION);

        assertThat(evaluation(result, ResultRecordMetric.FEWEST_ATTEMPTS, RecordScopeType.PERSONAL).action())
                .isEqualTo(ResultRecordEvaluationAction.IMPROVED);
        assertThat(evaluation(result, ResultRecordMetric.FEWEST_ATTEMPTS,
                RecordScopeType.SERVER_INDIVIDUAL).action()).isEqualTo(ResultRecordEvaluationAction.UNCHANGED);
    }

    @Test
    void oneCandidateCanImproveSeveralDefinitionsInDeterministicAggregableOrder() {
        ResultRecordObservation candidate = candidate(2, 300);
        List<ResultRecordStateSnapshot> states = new ArrayList<>();
        for (RecordScope scope : List.of(
                new RecordScope.Personal(CANDIDATE_PLAYER), new RecordScope.ServerIndividual())) {
            long holder = scope instanceof RecordScope.Personal ? CANDIDATE_PLAYER : 77;
            states.add(state(observation(
                    1000 + states.size(), holder, CANDIDATE_DATE.minusDays(3),
                    CANDIDATE_ACCEPTED_AT.minusSeconds(3), 3, 250),
                    ResultRecordMetric.FEWEST_ATTEMPTS, scope));
            states.add(state(observation(
                    1000 + states.size(), holder, CANDIDATE_DATE.minusDays(2),
                    CANDIDATE_ACCEPTED_AT.minusSeconds(2), 3, 400),
                    ResultRecordMetric.FASTEST_SOLUTION, scope));
            states.add(state(observation(
                    1000 + states.size(), holder, CANDIDATE_DATE.minusDays(1),
                    CANDIDATE_ACCEPTED_AT.minusSeconds(1), 3, 200),
                    ResultRecordMetric.SLOWEST_SUCCESSFUL_SOLUTION, scope));
        }
        List<ResultRecordObservation> prior = new ArrayList<>(history(5, CANDIDATE_PLAYER, 200));
        prior.addAll(history(5, 77, 300));

        ResultRecordEvaluationSet result = evaluator.evaluate(
                candidate,
                new ResultRecordHistorySnapshot(prior),
                states,
                RecordProcessingOrigin.NORMAL_CORRECTION);

        assertThat(result.evaluations()).extracting(evaluation ->
                        ((ResultRecordMetric) evaluation.definition().metric()).name()
                                + ":" + evaluation.scope().type().name())
                .containsExactly(
                        "FEWEST_ATTEMPTS:PERSONAL",
                        "FEWEST_ATTEMPTS:SERVER_INDIVIDUAL",
                        "FASTEST_SOLUTION:PERSONAL",
                        "FASTEST_SOLUTION:SERVER_INDIVIDUAL",
                        "SLOWEST_SUCCESSFUL_SOLUTION:PERSONAL",
                        "SLOWEST_SUCCESSFUL_SOLUTION:SERVER_INDIVIDUAL");
        assertThat(result.evaluations())
                .allSatisfy(evaluation -> assertThat(evaluation.action())
                        .isEqualTo(ResultRecordEvaluationAction.IMPROVED));
        assertThat(result.publicAnnouncements()).hasSize(6);
    }

    @Test
    void onlyPubliclyEligibleOriginsCanAnnounceAnImprovement() {
        ResultRecordObservation candidate = candidate(2, 100);
        ResultRecordObservation currentSource = observation(
                1, CANDIDATE_PLAYER, CANDIDATE_DATE.minusDays(20), CANDIDATE_ACCEPTED_AT.minusSeconds(20), 3, 100);
        ResultRecordStateSnapshot current = state(
                currentSource, ResultRecordMetric.FEWEST_ATTEMPTS,
                new RecordScope.Personal(CANDIDATE_PLAYER));
        ResultRecordHistorySnapshot history = new ResultRecordHistorySnapshot(
                history(5, CANDIDATE_PLAYER, 400));

        assertThat(evaluation(evaluator.evaluate(
                candidate, history, List.of(current), RecordProcessingOrigin.LIVE_SUBMISSION),
                ResultRecordMetric.FEWEST_ATTEMPTS, RecordScopeType.PERSONAL)
                .publicAnnouncementEligible()).isTrue();
        assertThat(evaluation(evaluator.evaluate(
                candidate, history, List.of(current), RecordProcessingOrigin.NORMAL_CORRECTION),
                ResultRecordMetric.FEWEST_ATTEMPTS, RecordScopeType.PERSONAL)
                .publicAnnouncementEligible()).isTrue();

        for (RecordProcessingOrigin origin : List.of(
                RecordProcessingOrigin.BOOTSTRAP,
                RecordProcessingOrigin.REPLAY,
                RecordProcessingOrigin.IMPORT,
                RecordProcessingOrigin.BACKFILL,
                RecordProcessingOrigin.ADMINISTRATIVE_REPAIR)) {
            assertThat(evaluation(evaluator.evaluate(candidate, history, List.of(current), origin),
                    ResultRecordMetric.FEWEST_ATTEMPTS, RecordScopeType.PERSONAL)
                    .publicAnnouncementEligible()).as(origin.name()).isFalse();
        }
    }

    @Test
    void rejectsGameConflictsWrongVersionsUnrelatedDefinitionsAndDuplicateStates() {
        ResultRecordObservation candidate = candidate(2, 100);
        ResultRecordObservation quadWords = new ResultRecordObservation(
                500,
                0,
                CANDIDATE_PLAYER,
                GameType.QUADWORDS,
                CANDIDATE_DATE.minusDays(1),
                CANDIDATE_ACCEPTED_AT.minusSeconds(1),
                new ShareOutcome.Solved(2, 9),
                Duration.ofSeconds(100));
        assertThatIllegalArgumentException().isThrownBy(() -> evaluator.evaluate(
                candidate,
                new ResultRecordHistorySnapshot(List.of(quadWords)),
                List.of(),
                RecordProcessingOrigin.LIVE_SUBMISSION))
                .withMessageContaining("game");

        ResultRecordStateSnapshot valid = state(
                observation(1, CANDIDATE_PLAYER, CANDIDATE_DATE.minusDays(1),
                        CANDIDATE_ACCEPTED_AT.minusSeconds(1), 3, 100),
                ResultRecordMetric.FEWEST_ATTEMPTS,
                new RecordScope.Personal(CANDIDATE_PLAYER));
        ResultRecordStateSnapshot wrongVersion = new ResultRecordStateSnapshot(
                valid.definitionKey(),
                new RecordDefinitionVersion("records-v2"),
                valid.scope(),
                valid.source());
        assertThatIllegalArgumentException().isThrownBy(() -> evaluator.evaluate(
                candidate,
                ResultRecordHistorySnapshot.empty(),
                List.of(wrongVersion),
                RecordProcessingOrigin.LIVE_SUBMISSION))
                .withMessageContaining("version");

        ResultRecordStateSnapshot streakState = new ResultRecordStateSnapshot(
                new RecordDefinitionKey("streak.activity.personal"),
                RecordDefinitionVersion.RECORDS_V1,
                new RecordScope.Personal(CANDIDATE_PLAYER),
                valid.source());
        assertThatIllegalArgumentException().isThrownBy(() -> evaluator.evaluate(
                candidate,
                ResultRecordHistorySnapshot.empty(),
                List.of(streakState),
                RecordProcessingOrigin.LIVE_SUBMISSION))
                .withMessageContaining("result-record");

        assertThatIllegalArgumentException().isThrownBy(() -> evaluator.evaluate(
                candidate,
                ResultRecordHistorySnapshot.empty(),
                List.of(valid, valid),
                RecordProcessingOrigin.LIVE_SUBMISSION))
                .withMessageContaining("duplicate");
    }

    @Test
    void rejectsIncompleteOrWrongVersionCatalogs() {
        RecordDefinition<?> existing = definition(
                GAME, ResultRecordMetric.FEWEST_ATTEMPTS, RecordScopeType.PERSONAL);
        RecordDefinitionCatalog incomplete = RecordDefinitionCatalog.of(
                RecordDefinitionVersion.RECORDS_V1, List.of(existing));
        assertThatIllegalArgumentException().isThrownBy(() -> new ResultRecordEvaluator(incomplete))
                .withMessageContaining("complete");

        RecordDefinition<AttemptsDurationRecordValue> otherVersionDefinition = new RecordDefinition<>(
                existing.key(),
                new RecordDefinitionVersion("records-v2"),
                existing.metric(),
                existing.game(),
                existing.scopeType(),
                RecordComparators.fewestAttempts(),
                existing.sourceEligibility(),
                existing.announcementThreshold());
        RecordDefinitionCatalog otherVersion = RecordDefinitionCatalog.of(
                new RecordDefinitionVersion("records-v2"), List.of(otherVersionDefinition));
        assertThatIllegalArgumentException().isThrownBy(() -> new ResultRecordEvaluator(otherVersion))
                .withMessageContaining("complete");
    }

    private static ResultRecordObservation candidate(int attempts, long seconds) {
        return observation(900, CANDIDATE_PLAYER, CANDIDATE_DATE, CANDIDATE_ACCEPTED_AT, attempts, seconds);
    }

    private static ResultRecordObservation observation(
            long resultId,
            long playerId,
            LocalDate gameDate,
            Instant firstAcceptedAt,
            int attempts,
            long seconds) {
        return new ResultRecordObservation(
                resultId,
                0,
                playerId,
                GAME,
                gameDate,
                firstAcceptedAt,
                new ShareOutcome.Solved(attempts, 6),
                Duration.ofSeconds(seconds));
    }

    private static List<ResultRecordObservation> history(int count, long playerId, long firstId) {
        List<ResultRecordObservation> results = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            results.add(observation(
                    firstId + index,
                    playerId,
                    CANDIDATE_DATE.minusDays(count - index + 1L),
                    CANDIDATE_ACCEPTED_AT.minusSeconds(count - index + 1L),
                    4,
                    200 + index));
        }
        return results;
    }

    private static ResultRecordStateSnapshot state(
            ResultRecordObservation source, ResultRecordMetric metric, RecordScope scope) {
        RecordDefinition<?> definition = definition(source.game(), metric, scope.type());
        return new ResultRecordStateSnapshot(
                definition.key(), definition.definitionVersion(), scope, source);
    }

    private static RecordDefinition<?> definition(
            GameType game, ResultRecordMetric metric, RecordScopeType scopeType) {
        return RecordDefinitionCatalog.recordsV1().definitions().stream()
                .filter(candidate -> candidate.metric() == metric)
                .filter(candidate -> candidate.game().equals(Optional.of(game)))
                .filter(candidate -> candidate.scopeType() == scopeType)
                .findFirst()
                .orElseThrow();
    }

    private static ResultRecordEvaluation evaluation(
            ResultRecordEvaluationSet set, ResultRecordMetric metric, RecordScopeType scopeType) {
        return set.find(metric, scopeType).orElseThrow();
    }
}
