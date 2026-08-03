package de.venomenon.gridwordsbot.domain.excuse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExcuseDomainModelTest {

    @Test
    void contextProvidesTheKnownGamePlaceholder() {
        ExcuseContext context = ExcuseContext.forGame(GameType.QUADWORDS);

        assertEquals("QuadWords", context.placeholder(ExcusePlaceholder.GAME).orElseThrow());
    }

    @Test
    void templateRejectsContradictoryConditions() {
        assertThrows(IllegalArgumentException.class, () -> new ExcuseTemplate(
                "contradictory.01",
                ExcuseStyle.LEGAL,
                Set.of(GameType.QUADWORDS),
                ExcuseTopic.SINGLE_BOARD_BLAME,
                10,
                100,
                Set.of(ExcuseFact.UNIQUE_WORST_BOARD),
                Set.of(ExcuseFact.UNIQUE_WORST_BOARD),
                "Text",
                true));
    }

    @Test
    void styleRerollRequiresAConcreteStyle() {
        assertThrows(IllegalArgumentException.class, () -> new ExcuseSelectionRequest(
                ExcuseRound.STYLE_REROLL, Optional.empty(), Set.of(), Set.of()));
    }

    @Test
    void selectionRequiresThreeOrderedDistinctOptions() {
        ExcuseOption first = new ExcuseOption(
                ExcuseRound.INITIAL, 1, "one.01", ExcuseStyle.TECHNICAL, ExcuseTopic.GENERAL, "One");
        ExcuseOption duplicate = new ExcuseOption(
                ExcuseRound.INITIAL, 2, "one.01", ExcuseStyle.LEGAL, ExcuseTopic.RESPONSIBILITY, "Two");
        ExcuseOption third = new ExcuseOption(
                ExcuseRound.INITIAL, 3, "three.01", ExcuseStyle.COSMIC, ExcuseTopic.GRID_CONFLICT, "Three");

        assertThrows(IllegalArgumentException.class,
                () -> new ExcuseSelection(ExcuseRound.INITIAL, List.of(first, duplicate, third)));
    }

    @Test
    void contextRejectsBlankPlaceholderValues() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExcuseContext(GameType.GRIDWORDS, Set.of(), Map.of(ExcusePlaceholder.SCORE, " ")));
    }
}
