package de.venomenon.gridwordsbot.domain.achievement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AchievementDefinitionCatalogTest {

    @Test
    void achievementsV1IsCompleteUniqueDeterministicAndImmutable() {
        AchievementDefinitionCatalog first = AchievementDefinitionCatalog.achievementsV1();
        AchievementDefinitionCatalog second = AchievementDefinitionCatalog.achievementsV1();

        assertThat(first).isSameAs(second);
        assertThat(first.version()).isEqualTo(AchievementDefinitionVersion.ACHIEVEMENTS_V1);
        assertThat(first.definitions()).hasSize(60);
        assertThat(first.definitions().stream().map(AchievementDefinition::key))
                .doesNotHaveDuplicates()
                .containsExactlyElementsOf(expectedKeys());
        assertThat(first.definitions().stream().map(AchievementDefinition::displayName))
                .doesNotHaveDuplicates();
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> first.definitions().clear());
    }

    @Test
    void achievementsV2KeepsTheCompleteV1CatalogAndAddsTheTwoBoardPatternDefinitions() {
        AchievementDefinitionCatalog v1 = AchievementDefinitionCatalog.achievementsV1();
        AchievementDefinitionCatalog v2 = AchievementDefinitionCatalog.achievementsV2();

        assertThat(v2.version()).isEqualTo(AchievementDefinitionVersion.ACHIEVEMENTS_V2);
        assertThat(v2.definitions()).hasSize(62);
        assertThat(v2.definitions().subList(0, 60))
                .extracting(AchievementDefinition::key)
                .containsExactlyElementsOf(v1.definitions().stream().map(AchievementDefinition::key).toList());
        assertThat(v2.find(new AchievementKey("situational.deja_vu.gridwords")).orElseThrow())
                .extracting(AchievementDefinition::displayName, AchievementDefinition::rule)
                .containsExactly("GW: Déjà-vu", new AchievementRule.ConsecutiveSameSuccessfulResults(GameType.GRIDWORDS, 3));
        assertThat(v2.find(new AchievementKey("situational.repeated_pattern.gridwords")).orElseThrow())
                .extracting(AchievementDefinition::fallbackEmoji, AchievementDefinition::displayName, AchievementDefinition::rule)
                .containsExactly("🪞", "GW: Mustertreue", new AchievementRule.GridWordsRepeatedPattern(3));
        assertThat(v2.find(new AchievementKey("situational.all_yellow_row")).orElseThrow())
                .extracting(AchievementDefinition::fallbackEmoji, AchievementDefinition::displayName, AchievementDefinition::rule)
                .containsExactly("🟨", "Alles da, nichts sitzt", new AchievementRule.AllYellowBoardRow());
    }

    @Test
    void usesTheSpecifiedCategoryAndScopeDistribution() {
        Map<AchievementCategory, Long> byCategory = AchievementDefinitionCatalog.achievementsV1().definitions().stream()
                .collect(Collectors.groupingBy(
                        AchievementDefinition::category,
                        Collectors.counting()));
        Map<AchievementScope, Long> byScope = AchievementDefinitionCatalog.achievementsV1().definitions().stream()
                .collect(Collectors.groupingBy(
                        AchievementDefinition::scope,
                        Collectors.counting()));

        assertThat(byCategory).containsExactlyInAnyOrderEntriesOf(Map.of(
                AchievementCategory.EXPERIENCE, 13L,
                AchievementCategory.RELIABILITY, 13L,
                AchievementCategory.PERFORMANCE, 21L,
                AchievementCategory.SPECIAL, 13L));
        assertThat(byScope).containsExactlyInAnyOrderEntriesOf(Map.of(
                AchievementScope.GRIDWORDS, 20L,
                AchievementScope.QUADWORDS, 22L,
                AchievementScope.CROSS_GAME, 13L,
                AchievementScope.GLOBAL, 5L));
    }

    @Test
    void achievementsV2HasTheSpecifiedExtendedScopeAndCategoryDistribution() {
        Map<AchievementCategory, Long> byCategory = AchievementDefinitionCatalog.achievementsV2().definitions().stream()
                .collect(Collectors.groupingBy(AchievementDefinition::category, Collectors.counting()));
        Map<AchievementScope, Long> byScope = AchievementDefinitionCatalog.achievementsV2().definitions().stream()
                .collect(Collectors.groupingBy(AchievementDefinition::scope, Collectors.counting()));

        assertThat(byCategory).containsExactlyInAnyOrderEntriesOf(Map.of(
                AchievementCategory.EXPERIENCE, 13L,
                AchievementCategory.RELIABILITY, 13L,
                AchievementCategory.PERFORMANCE, 21L,
                AchievementCategory.SPECIAL, 15L));
        assertThat(byScope).containsExactlyInAnyOrderEntriesOf(Map.of(
                AchievementScope.GRIDWORDS, 21L,
                AchievementScope.QUADWORDS, 22L,
                AchievementScope.CROSS_GAME, 13L,
                AchievementScope.GLOBAL, 6L));
    }

    @Test
    void appliesTheRequiredDisplayNamePrefixes() {
        AchievementDefinitionCatalog.achievementsV1().definitions().forEach(definition -> {
            switch (definition.scope()) {
                case GRIDWORDS -> assertThat(definition.displayName()).startsWith("GW:");
                case QUADWORDS -> assertThat(definition.displayName()).startsWith("QW:");
                case CROSS_GAME -> assertThat(definition.displayName()).startsWith("GW+QW:");
                case GLOBAL -> assertThat(definition.displayName())
                        .doesNotStartWith("GW:")
                        .doesNotStartWith("QW:")
                        .doesNotStartWith("GW+QW:");
            }
            assertThat(definition.fallbackEmoji()).isNotBlank();
            assertThat(definition.description()).isNotBlank();
        });
    }

    @Test
    void exactAttemptRulesRemainIndependentExactDefinitions() {
        assertThat(rule("performance.solve.1.gridwords"))
                .isEqualTo(new AchievementRule.ExactSolvedAttempts(GameType.GRIDWORDS, 1));
        assertThat(rule("performance.solve.2.gridwords"))
                .isEqualTo(new AchievementRule.ExactSolvedAttempts(GameType.GRIDWORDS, 2));
        assertThat(rule("performance.solve.3.gridwords"))
                .isEqualTo(new AchievementRule.ExactSolvedAttempts(GameType.GRIDWORDS, 3));
        assertThat(rule("performance.solve.4.quadwords"))
                .isEqualTo(new AchievementRule.ExactSolvedAttempts(GameType.QUADWORDS, 4));
        assertThat(rule("performance.solve.5.quadwords"))
                .isEqualTo(new AchievementRule.ExactSolvedAttempts(GameType.QUADWORDS, 5));
        assertThat(rule("performance.solve.6.quadwords"))
                .isEqualTo(new AchievementRule.ExactSolvedAttempts(GameType.QUADWORDS, 6));
    }

    @Test
    void modelsTheSpecifiedSpecialRuleParameters() {
        assertThat(rule("situational.last_chance.gridwords"))
                .isEqualTo(new AchievementRule.ExactSolvedAttempts(GameType.GRIDWORDS, 6));
        assertThat(rule("situational.last_chance.quadwords"))
                .isEqualTo(new AchievementRule.ExactSolvedAttempts(GameType.QUADWORDS, 9));
        assertThat(rule("situational.quadwords.consecutive_board_attempts"))
                .isEqualTo(new AchievementRule.QuadWordsConsecutiveBoardAttempts());
        assertThat(rule("situational.quadwords.outlier_board"))
                .isEqualTo(new AchievementRule.QuadWordsOutlierBoard(3));
        assertThat(rule("situational.crossgame.double_last_chance"))
                .isEqualTo(new AchievementRule.CrossGameExactAttempts(6, 9));
        assertThat(rule("situational.crossgame.perfect_double"))
                .isEqualTo(new AchievementRule.CrossGameExactAttempts(1, 4));
        assertThat(rule("situational.deja_vu.gridwords"))
                .isEqualTo(new AchievementRule.ConsecutiveSameSuccessfulResults(GameType.GRIDWORDS, 3));
        assertThat(rule("situational.deja_vu.quadwords"))
                .isEqualTo(new AchievementRule.ConsecutiveSameSuccessfulResults(GameType.QUADWORDS, 3));
        assertThat(rule("situational.failure_run.3.gridwords"))
                .isEqualTo(new AchievementRule.ConsecutiveFailures(GameType.GRIDWORDS, 3));
        assertThat(rule("situational.failure_run.3.quadwords"))
                .isEqualTo(new AchievementRule.ConsecutiveFailures(GameType.QUADWORDS, 3));
        assertThat(rule("timing.before_0700"))
                .isEqualTo(new AchievementRule.LocalTimeBefore(LocalTime.of(7, 0)));
        assertThat(rule("timing.after_2300"))
                .isEqualTo(new AchievementRule.LocalTimeAtOrAfter(LocalTime.of(23, 0)));
    }

    @Test
    void v2ModelsTheSpecifiedBoardPatternRuleParameters() {
        assertThat(rule(AchievementDefinitionCatalog.achievementsV2(), "situational.repeated_pattern.gridwords"))
                .isEqualTo(new AchievementRule.GridWordsRepeatedPattern(3));
        assertThat(rule(AchievementDefinitionCatalog.achievementsV2(), "situational.all_yellow_row"))
                .isEqualTo(new AchievementRule.AllYellowBoardRow());
    }

    @Test
    void rejectsDuplicateKeysAndDisplayNames() {
        AchievementDefinition first = testDefinition("test.one", "Erster Test");
        AchievementDefinition duplicateKey = new AchievementDefinition(
                first.key(),
                AchievementDefinitionVersion.ACHIEVEMENTS_V1,
                AchievementCategory.EXPERIENCE,
                AchievementScope.GLOBAL,
                "✅",
                "Zweiter Test",
                "Zweite Testdefinition.",
                new AchievementRule.TotalResultCount(2));
        AchievementDefinition duplicateName = testDefinition("test.two", "Erster Test");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> AchievementDefinitionCatalog.of(
                        AchievementDefinitionVersion.ACHIEVEMENTS_V1,
                        List.of(first, duplicateKey)))
                .withMessageContaining("duplicate achievement key");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AchievementDefinitionCatalog.of(
                        AchievementDefinitionVersion.ACHIEVEMENTS_V1,
                        List.of(first, duplicateName)))
                .withMessageContaining("duplicate achievement display name");
    }

    @Test
    void rejectsInvalidScopePrefixesAndRuleCombinations() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AchievementDefinition(
                new AchievementKey("test.gridwords"),
                AchievementDefinitionVersion.ACHIEVEMENTS_V1,
                AchievementCategory.EXPERIENCE,
                AchievementScope.GRIDWORDS,
                "✅",
                "Ohne Präfix",
                "Test.",
                new AchievementRule.ParticipationCount(GameType.GRIDWORDS, 1)))
                .withMessageContaining("GW:");

        assertThatIllegalArgumentException().isThrownBy(() -> new AchievementDefinition(
                new AchievementKey("test.scope"),
                AchievementDefinitionVersion.ACHIEVEMENTS_V1,
                AchievementCategory.EXPERIENCE,
                AchievementScope.QUADWORDS,
                "✅",
                "QW: Falscher Scope",
                "Test.",
                new AchievementRule.ParticipationCount(GameType.GRIDWORDS, 1)))
                .withMessageContaining("scope");
    }

    @Test
    void rejectsInvalidRuleThresholdsAndSolvedAttemptRanges() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AchievementRule.ParticipationCount(GameType.GRIDWORDS, 0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AchievementRule.ExactSolvedAttempts(GameType.GRIDWORDS, 7));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AchievementRule.ExactSolvedAttempts(GameType.QUADWORDS, 3));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AchievementRule.QuadWordsOutlierBoard(0));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AchievementRule.ConsecutiveFailures(GameType.GRIDWORDS, 1));
    }

    @Test
    void evidenceAndEvaluationAreTransportNeutralUniqueAndImmutable() {
        AchievementEvidence first = new AchievementEvidence(
                new AchievementKey("participation.1.gridwords"),
                LocalDate.of(2026, 8, 8),
                AchievementEvidence.Kind.GAME_RESULT,
                "game-result:42");
        AchievementEvaluation evaluation = new AchievementEvaluation(List.of(first));

        assertThat(evaluation.find(first.achievementKey())).contains(first);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> evaluation.achievements().clear());
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AchievementEvaluation(List.of(first, first)))
                .withMessageContaining("duplicate achievement evidence");
    }

    private static AchievementRule rule(String key) {
        return rule(AchievementDefinitionCatalog.achievementsV1(), key);
    }

    private static AchievementRule rule(AchievementDefinitionCatalog catalog, String key) {
        return catalog
                .find(new AchievementKey(key))
                .orElseThrow()
                .rule();
    }

    private static AchievementDefinition testDefinition(String key, String displayName) {
        return new AchievementDefinition(
                new AchievementKey(key),
                AchievementDefinitionVersion.ACHIEVEMENTS_V1,
                AchievementCategory.EXPERIENCE,
                AchievementScope.GLOBAL,
                "✅",
                displayName,
                "Testdefinition.",
                new AchievementRule.TotalResultCount(1));
    }

    private static List<AchievementKey> expectedKeys() {
        return List.of(
                "participation.1.gridwords",
                "participation.10.gridwords",
                "participation.25.gridwords",
                "participation.50.gridwords",
                "participation.100.gridwords",
                "participation.1.quadwords",
                "participation.10.quadwords",
                "participation.25.quadwords",
                "participation.50.quadwords",
                "participation.100.quadwords",
                "streak.participation.10.gridwords",
                "streak.participation.25.gridwords",
                "streak.participation.50.gridwords",
                "streak.participation.100.gridwords",
                "streak.participation.10.quadwords",
                "streak.participation.25.quadwords",
                "streak.participation.50.quadwords",
                "streak.participation.100.quadwords",
                "streak.success.1.gridwords",
                "streak.success.10.gridwords",
                "streak.success.25.gridwords",
                "streak.success.50.gridwords",
                "streak.success.100.gridwords",
                "streak.success.1.quadwords",
                "streak.success.10.quadwords",
                "streak.success.25.quadwords",
                "streak.success.50.quadwords",
                "streak.success.100.quadwords",
                "performance.solve.1.gridwords",
                "performance.solve.2.gridwords",
                "performance.solve.3.gridwords",
                "performance.solve.4.quadwords",
                "performance.solve.5.quadwords",
                "performance.solve.6.quadwords",
                "crossgame.participation.1",
                "crossgame.participation.10",
                "crossgame.participation.25",
                "crossgame.participation.50",
                "crossgame.participation.100",
                "crossgame.success.1",
                "crossgame.success.10",
                "crossgame.success.25",
                "crossgame.success.50",
                "crossgame.success.100",
                "experience.total.100",
                "experience.total.200",
                "experience.total.300",
                "situational.last_chance.gridwords",
                "situational.last_chance.quadwords",
                "situational.quadwords.consecutive_board_attempts",
                "situational.quadwords.outlier_board",
                "situational.crossgame.equal_final_score",
                "situational.crossgame.double_last_chance",
                "situational.deja_vu.gridwords",
                "situational.deja_vu.quadwords",
                "timing.before_0700",
                "timing.after_2300",
                "situational.crossgame.perfect_double",
                "situational.failure_run.3.gridwords",
                "situational.failure_run.3.quadwords")
                .stream()
                .map(AchievementKey::new)
                .toList();
    }
}
