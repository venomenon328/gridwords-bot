package de.venomenon.gridwordsbot.domain.excuse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExcuseTemplateRendererTest {

    private final ExcuseTemplateRenderer renderer = new ExcuseTemplateRenderer();

    @Test
    void rendersOnlyKnownFullyAvailablePlaceholders() {
        ExcuseTemplate template = ExcuseTestFixtures.template(
                "technical.score.01",
                ExcuseStyle.TECHNICAL,
                ExcuseTopic.TECHNICAL_FAILURE,
                10,
                100,
                Set.of(ExcuseReason.GRIDWORDS_LAST_ATTEMPT),
                "{game} meldet {score} nach {duration}.");
        ExcuseContext context = new ExcuseContext(
                GameType.GRIDWORDS,
                Set.of(ExcuseReason.GRIDWORDS_LAST_ATTEMPT),
                Map.of(ExcusePlaceholder.SCORE, "6/6", ExcusePlaceholder.DURATION, "5:01"));

        assertEquals("GridWords meldet 6/6 nach 5:01.", renderer.render(template, context).orElseThrow());
    }

    @Test
    void discardsTheWholeTemplateWhenOneValueIsMissing() {
        ExcuseTemplate template = ExcuseTestFixtures.template(
                "technical.missing.01",
                ExcuseStyle.TECHNICAL,
                ExcuseTopic.TECHNICAL_FAILURE,
                10,
                100,
                Set.of(ExcuseReason.GRIDWORDS_LAST_ATTEMPT),
                "{game} meldet {score}.");

        assertTrue(renderer.render(template, ExcuseContext.forGame(GameType.GRIDWORDS)).isEmpty());
    }

    @Test
    void rejectsUnknownAndMalformedPlaceholders() {
        ExcuseTemplate unknown = ExcuseTestFixtures.general(
                "unknown.01", ExcuseStyle.COSMIC, ExcuseTopic.GENERAL);
        unknown = new ExcuseTemplate(
                unknown.id(), unknown.style(), unknown.games(), unknown.topic(), unknown.specificity(), unknown.weight(),
                unknown.requiresAll(), unknown.excludesAny(), "{planet} war schuld.", true);
        ExcuseTemplate malformed = new ExcuseTemplate(
                "malformed.01", unknown.style(), unknown.games(), unknown.topic(), 0, 100,
                Set.of(), Set.of(), "{game war schuld.", true);

        assertFalse(renderer.analyze(unknown.text()).valid());
        assertFalse(renderer.analyze(malformed.text()).valid());
        assertTrue(renderer.render(unknown, ExcuseContext.forGame(GameType.GRIDWORDS)).isEmpty());
        assertTrue(renderer.render(malformed, ExcuseContext.forGame(GameType.GRIDWORDS)).isEmpty());
    }
}
