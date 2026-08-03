package de.venomenon.gridwordsbot.domain.excuse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExcuseSelectorTest {

    @Test
    void requiresAtLeastThreeFullyRenderableCandidates() {
        ExcuseCatalog catalog = new ExcuseCatalog("v1", List.of(
                ExcuseTestFixtures.general("one.01", ExcuseStyle.TECHNICAL, ExcuseTopic.GENERAL),
                ExcuseTestFixtures.general("two.01", ExcuseStyle.LEGAL, ExcuseTopic.RESPONSIBILITY),
                ExcuseTestFixtures.template(
                        "missing.01", ExcuseStyle.COSMIC, ExcuseTopic.GRID_CONFLICT, 0, 100, Set.of(), "{score}")));
        ExcuseSelector selector = selector(0);

        assertTrue(selector.select(
                catalog,
                ExcuseContext.forGame(GameType.GRIDWORDS),
                ExcuseSelectionRequest.initial(Set.of(), Set.of())).isEmpty());
    }

    @Test
    void selectsThreeDifferentStylesAndTopicsWhenPossible() {
        ExcuseCatalog catalog = new ExcuseCatalog("v1", List.of(
                ExcuseTestFixtures.general("technical.01", ExcuseStyle.TECHNICAL, ExcuseTopic.TECHNICAL_FAILURE),
                ExcuseTestFixtures.general("legal.01", ExcuseStyle.LEGAL, ExcuseTopic.RESPONSIBILITY),
                ExcuseTestFixtures.general("cosmic.01", ExcuseStyle.COSMIC, ExcuseTopic.GRID_CONFLICT),
                ExcuseTestFixtures.general("dramatic.01", ExcuseStyle.DRAMATIC, ExcuseTopic.LONG_TERM_PLAN)));

        ExcuseSelection selection = selector(0, 0, 0, 0, 0, 0).select(
                catalog,
                ExcuseContext.forGame(GameType.GRIDWORDS),
                ExcuseSelectionRequest.initial(Set.of(), Set.of())).orElseThrow();

        assertEquals(3, selection.options().stream().map(ExcuseOption::templateId).distinct().count());
        assertEquals(3, selection.options().stream().map(ExcuseOption::style).distinct().count());
        assertEquals(3, selection.options().stream().map(ExcuseOption::topic).distinct().count());
    }

    @Test
    void guaranteesOneContextSpecificTemplateAndPrefersItsHighestSpecificity() {
        ExcuseTemplate lower = ExcuseTestFixtures.template(
                "board.lower.01", ExcuseStyle.LEGAL, ExcuseTopic.SINGLE_BOARD_BLAME, 10, 100,
                Set.of(ExcuseFact.UNIQUE_WORST_BOARD), "Niedrig");
        ExcuseTemplate higher = ExcuseTestFixtures.template(
                "board.higher.01", ExcuseStyle.LEGAL, ExcuseTopic.SINGLE_BOARD_BLAME, 30, 1,
                Set.of(ExcuseFact.UNIQUE_WORST_BOARD), "Hoch");
        ExcuseCatalog catalog = new ExcuseCatalog("v1", List.of(
                lower,
                higher,
                ExcuseTestFixtures.general("technical.01", ExcuseStyle.TECHNICAL, ExcuseTopic.TECHNICAL_FAILURE),
                ExcuseTestFixtures.general("cosmic.01", ExcuseStyle.COSMIC, ExcuseTopic.GRID_CONFLICT)));
        ExcuseContext context = new ExcuseContext(
                GameType.QUADWORDS,
                Set.of(ExcuseFact.UNIQUE_WORST_BOARD),
                Map.of());

        ExcuseSelection selection = selector(0, 0, 0, 0, 0, 0).select(
                catalog, context, ExcuseSelectionRequest.initial(Set.of(), Set.of())).orElseThrow();

        assertEquals("board.higher.01", selection.options().getFirst().templateId());
    }

    @Test
    void usesWeightsWithinTheChosenStyle() {
        ExcuseCatalog catalog = new ExcuseCatalog("v1", List.of(
                ExcuseTestFixtures.template(
                        "technical.light.01", ExcuseStyle.TECHNICAL, ExcuseTopic.GENERAL, 0, 1, Set.of(), "Light"),
                ExcuseTestFixtures.template(
                        "technical.heavy.01", ExcuseStyle.TECHNICAL, ExcuseTopic.GENERAL, 0, 9, Set.of(), "Heavy"),
                ExcuseTestFixtures.general("technical.third.01", ExcuseStyle.TECHNICAL, ExcuseTopic.GENERAL)));

        ExcuseSelection selection = selector(0, 9, 0, 0, 0, 0).select(
                catalog,
                ExcuseContext.forGame(GameType.GRIDWORDS),
                ExcuseSelectionRequest.styleReroll(ExcuseStyle.TECHNICAL, Set.of())).orElseThrow();

        assertEquals("technical.heavy.01", selection.options().getFirst().templateId());
    }

    @Test
    void styleRerollRestrictsAllOptionsAndHonorsExcludedIds() {
        ExcuseCatalog catalog = new ExcuseCatalog("v1", List.of(
                ExcuseTestFixtures.general("legal.01", ExcuseStyle.LEGAL, ExcuseTopic.RESPONSIBILITY),
                ExcuseTestFixtures.general("legal.02", ExcuseStyle.LEGAL, ExcuseTopic.GRID_CONFLICT),
                ExcuseTestFixtures.general("legal.03", ExcuseStyle.LEGAL, ExcuseTopic.LONG_TERM_PLAN),
                ExcuseTestFixtures.general("legal.04", ExcuseStyle.LEGAL, ExcuseTopic.GENERAL),
                ExcuseTestFixtures.general("technical.01", ExcuseStyle.TECHNICAL, ExcuseTopic.GENERAL)));

        ExcuseSelection selection = selector(0).select(
                catalog,
                ExcuseContext.forGame(GameType.QUADWORDS),
                ExcuseSelectionRequest.styleReroll(ExcuseStyle.LEGAL, Set.of("legal.01"))).orElseThrow();

        assertTrue(selection.options().stream().allMatch(option -> option.style() == ExcuseStyle.LEGAL));
        assertTrue(selection.options().stream().noneMatch(option -> option.templateId().equals("legal.01")));
    }

    private static ExcuseSelector selector(int... values) {
        return new ExcuseSelector(new ExcuseTemplateRenderer(), new SequenceRandom(values));
    }

    private static final class SequenceRandom implements ExcuseRandom {
        private final Deque<Integer> values = new ArrayDeque<>();

        private SequenceRandom(int... values) {
            for (int value : values) {
                this.values.add(value);
            }
        }

        @Override
        public int nextInt(int bound) {
            int value = values.isEmpty() ? 0 : values.removeFirst();
            return Math.floorMod(value, bound);
        }
    }
}
