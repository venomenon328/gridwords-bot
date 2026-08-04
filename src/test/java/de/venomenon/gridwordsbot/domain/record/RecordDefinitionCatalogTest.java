package de.venomenon.gridwordsbot.domain.record;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatUnsupportedOperationException;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecordDefinitionCatalogTest {

    @Test
    void recordsV1IsCompleteUniqueAndDeterministic() {
        RecordDefinitionCatalog first = RecordDefinitionCatalog.recordsV1();
        RecordDefinitionCatalog second = RecordDefinitionCatalog.recordsV1();

        assertThat(first).isSameAs(second);
        assertThat(first.version()).isEqualTo(RecordDefinitionVersion.RECORDS_V1);
        assertThat(first.definitions()).hasSize(32);
        assertThat(first.definitions().stream().map(RecordDefinition::key))
                .doesNotHaveDuplicates()
                .containsExactlyElementsOf(expectedKeys());
        assertThatUnsupportedOperationException().isThrownBy(() -> first.definitions().clear());
    }

    @Test
    void containsExactlyTheTwelveResultDefinitions() {
        List<RecordDefinition<?>> resultDefinitions = RecordDefinitionCatalog.recordsV1().definitions().stream()
                .filter(definition -> definition.metric() instanceof ResultRecordMetric)
                .toList();

        assertThat(resultDefinitions).hasSize(12);
        assertThat(resultDefinitions)
                .allSatisfy(definition -> {
                    assertThat(definition.game()).isPresent();
                    assertThat(definition.scopeType()).isNotEqualTo(RecordScopeType.SHARED);
                    assertThat(definition.sourceEligibility())
                            .isInstanceOf(RecordSourceEligibility.SolvedGameResult.class);
                    assertThat(definition.announcementThreshold())
                            .isInstanceOf(RecordAnnouncementThreshold.Result.class);
                });
    }

    @Test
    void resultEligibilityAcceptsOnlySolvedResultsOfTheConfiguredGame() {
        RecordDefinition<?> definition = RecordDefinitionCatalog.recordsV1()
                .find(new RecordDefinitionKey("result.gridwords.fastest-solution.personal"))
                .orElseThrow();
        var eligibility = (RecordSourceEligibility.SolvedGameResult) definition.sourceEligibility();

        assertThat(eligibility.accepts(GameType.GRIDWORDS, new ShareOutcome.Solved(3, 6))).isTrue();
        assertThat(eligibility.accepts(GameType.GRIDWORDS, new ShareOutcome.Unsolved(6))).isFalse();
        assertThat(eligibility.accepts(GameType.QUADWORDS, new ShareOutcome.Solved(3, 9))).isFalse();
    }

    @Test
    void containsAllPositiveAndNegativeStreakDefinitionsWithAllowedScopes() {
        List<RecordDefinition<?>> streakDefinitions = RecordDefinitionCatalog.recordsV1().definitions().stream()
                .filter(definition -> definition.metric() instanceof StreakRecordMetric)
                .toList();

        assertThat(streakDefinitions).hasSize(20);
        assertThat(streakDefinitions.stream()
                .filter(definition -> definition.scopeType() == RecordScopeType.SHARED)
                .map(definition -> (StreakRecordMetric) definition.metric()))
                .containsExactly(
                        StreakRecordMetric.COMPLETE,
                        StreakRecordMetric.GRIDWORDS_SOLVED,
                        StreakRecordMetric.QUADWORDS_SOLVED,
                        StreakRecordMetric.PERFECT);
        assertThat(streakDefinitions.stream()
                .filter(definition -> definition.polarity() == RecordPolarity.NEGATIVE)
                .map(RecordDefinition::scopeType))
                .doesNotContain(RecordScopeType.SHARED);
    }

    @Test
    void appliesTheSpecifiedAnnouncementThresholds() {
        RecordDefinitionCatalog catalog = RecordDefinitionCatalog.recordsV1();

        assertThat(threshold(catalog, "result.gridwords.fastest-solution.personal"))
                .isEqualTo(new RecordAnnouncementThreshold.Result(5, 1));
        assertThat(threshold(catalog, "result.gridwords.fastest-solution.server-individual"))
                .isEqualTo(new RecordAnnouncementThreshold.Result(10, 2));
        assertThat(threshold(catalog, "streak.activity.personal"))
                .isEqualTo(new RecordAnnouncementThreshold.Streak(7, 1, 1));
        assertThat(threshold(catalog, "streak.activity.server-individual"))
                .isEqualTo(new RecordAnnouncementThreshold.Streak(7, 2, 2));
        assertThat(threshold(catalog, "streak.complete.shared"))
                .isEqualTo(new RecordAnnouncementThreshold.Streak(7, 1, 0));
        assertThat(threshold(catalog, "streak.gridwords-drought.personal"))
                .isEqualTo(new RecordAnnouncementThreshold.Streak(3, 1, 1));
        assertThat(threshold(catalog, "streak.gridwords-drought.server-individual"))
                .isEqualTo(new RecordAnnouncementThreshold.Streak(3, 2, 2));
    }

    @Test
    void containsNoQuadWordsBoardDefinition() {
        assertThat(RecordDefinitionCatalog.recordsV1().definitions())
                .noneSatisfy(definition -> assertThat(definition.key().value()).contains("board"));
    }

    @Test
    void rejectsDuplicateKeysAndLogicalDefinitions() {
        RecordDefinition<?> definition = RecordDefinitionCatalog.recordsV1().definitions().getFirst();

        assertThatIllegalArgumentException().isThrownBy(() -> RecordDefinitionCatalog.of(
                RecordDefinitionVersion.RECORDS_V1, List.of(definition, definition)))
                .withMessageContaining("duplicate record definition key");
    }

    @Test
    void rejectsAKeyThatDoesNotMatchMetricGameAndScope() {
        RecordDefinition<DurationRecordValue> invalid = new RecordDefinition<>(
                new RecordDefinitionKey("result.gridwords.fastest-solution.server-individual"),
                RecordDefinitionVersion.RECORDS_V1,
                ResultRecordMetric.FASTEST_SOLUTION,
                Optional.of(GameType.GRIDWORDS),
                RecordScopeType.PERSONAL,
                RecordComparators.fastestDuration(),
                new RecordSourceEligibility.SolvedGameResult(GameType.GRIDWORDS),
                new RecordAnnouncementThreshold.Result(5, 1));

        assertThatIllegalArgumentException().isThrownBy(() -> RecordDefinitionCatalog.of(
                RecordDefinitionVersion.RECORDS_V1, List.of(invalid)))
                .withMessageContaining("definition key");
    }

    @Test
    void rejectsSharedResultAndNegativeSharedStreakCombinations() {
        RecordDefinition<DurationRecordValue> sharedResult = new RecordDefinition<>(
                new RecordDefinitionKey("result.gridwords.fastest-solution.shared"),
                RecordDefinitionVersion.RECORDS_V1,
                ResultRecordMetric.FASTEST_SOLUTION,
                Optional.of(GameType.GRIDWORDS),
                RecordScopeType.SHARED,
                RecordComparators.fastestDuration(),
                new RecordSourceEligibility.SolvedGameResult(GameType.GRIDWORDS),
                new RecordAnnouncementThreshold.Result(10, 2));
        RecordDefinition<StreakRecordValue> sharedDrought = new RecordDefinition<>(
                new RecordDefinitionKey("streak.gridwords-drought.shared"),
                RecordDefinitionVersion.RECORDS_V1,
                StreakRecordMetric.GRIDWORDS_DROUGHT,
                Optional.of(GameType.GRIDWORDS),
                RecordScopeType.SHARED,
                RecordComparators.longestStreak(),
                new RecordSourceEligibility.StreakRun(StreakRecordMetric.GRIDWORDS_DROUGHT),
                new RecordAnnouncementThreshold.Streak(3, 1, 0));

        assertThatIllegalArgumentException().isThrownBy(() -> RecordDefinitionCatalog.of(
                RecordDefinitionVersion.RECORDS_V1, List.of(sharedResult)))
                .withMessageContaining("shared scope");
        assertThatIllegalArgumentException().isThrownBy(() -> RecordDefinitionCatalog.of(
                RecordDefinitionVersion.RECORDS_V1, List.of(sharedDrought)))
                .withMessageContaining("does not allow shared scope");
    }

    @Test
    void rejectsComparatorAndMetricValueTypeMismatchBeforeCatalogCreation() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RecordDefinition<>(
                new RecordDefinitionKey("result.gridwords.fastest-solution.personal"),
                RecordDefinitionVersion.RECORDS_V1,
                ResultRecordMetric.FASTEST_SOLUTION,
                Optional.of(GameType.GRIDWORDS),
                RecordScopeType.PERSONAL,
                RecordComparators.longestStreak(),
                new RecordSourceEligibility.SolvedGameResult(GameType.GRIDWORDS),
                new RecordAnnouncementThreshold.Result(5, 1)))
                .withMessageContaining("comparator value type");
    }

    private static RecordAnnouncementThreshold threshold(RecordDefinitionCatalog catalog, String key) {
        return catalog.find(new RecordDefinitionKey(key)).orElseThrow().announcementThreshold();
    }

    private static List<RecordDefinitionKey> expectedKeys() {
        return List.of(
                "result.gridwords.fewest-attempts.personal",
                "result.gridwords.fewest-attempts.server-individual",
                "result.gridwords.fastest-solution.personal",
                "result.gridwords.fastest-solution.server-individual",
                "result.gridwords.slowest-successful-solution.personal",
                "result.gridwords.slowest-successful-solution.server-individual",
                "result.quadwords.fewest-attempts.personal",
                "result.quadwords.fewest-attempts.server-individual",
                "result.quadwords.fastest-solution.personal",
                "result.quadwords.fastest-solution.server-individual",
                "result.quadwords.slowest-successful-solution.personal",
                "result.quadwords.slowest-successful-solution.server-individual",
                "streak.activity.personal",
                "streak.activity.server-individual",
                "streak.complete.personal",
                "streak.complete.server-individual",
                "streak.complete.shared",
                "streak.gridwords-solved.personal",
                "streak.gridwords-solved.server-individual",
                "streak.gridwords-solved.shared",
                "streak.quadwords-solved.personal",
                "streak.quadwords-solved.server-individual",
                "streak.quadwords-solved.shared",
                "streak.perfect.personal",
                "streak.perfect.server-individual",
                "streak.perfect.shared",
                "streak.gridwords-drought.personal",
                "streak.gridwords-drought.server-individual",
                "streak.quadwords-drought.personal",
                "streak.quadwords-drought.server-individual",
                "streak.without-perfect-day.personal",
                "streak.without-perfect-day.server-individual")
                .stream()
                .map(RecordDefinitionKey::new)
                .toList();
    }
}
