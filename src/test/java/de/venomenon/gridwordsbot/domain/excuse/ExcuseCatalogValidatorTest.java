package de.venomenon.gridwordsbot.domain.excuse;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExcuseCatalogValidatorTest {

    private final ExcuseCatalogValidator validator = new ExcuseCatalogValidator();

    @Test
    void rejectsDuplicateIdsForbiddenMentionsUnknownPlaceholdersAndMalformedBraces() {
        List<ExcuseTemplate> templates = List.of(
                general("duplicate.01", "Normal"),
                general("duplicate.01", "@Everyone bitte prüfen"),
                general("unknown.01", "{planet} stand ungünstig"),
                general("malformed.01", "{game war schwierig"));

        ExcuseCatalogValidationException exception = assertThrows(
                ExcuseCatalogValidationException.class,
                () -> validator.validate(new ExcuseCatalog("v1", templates)));

        assertTrue(exception.errors().stream().anyMatch(error -> error.contains("duplicate template id")));
        assertTrue(exception.errors().stream().anyMatch(error -> error.contains("forbidden mention")));
        assertTrue(exception.errors().stream().anyMatch(error -> error.contains("unknown placeholders")));
        assertTrue(exception.errors().stream().anyMatch(error -> error.contains("malformed braces")));
    }

    @Test
    void rejectsContextRequirementsWithoutSpecificity() {
        ExcuseTemplate template = new ExcuseTemplate(
                "context.zero.01",
                ExcuseStyle.LEGAL,
                Set.of(GameType.QUADWORDS),
                ExcuseTopic.SINGLE_BOARD_BLAME,
                0,
                100,
                Set.of(ExcuseFact.UNIQUE_WORST_BOARD),
                Set.of(),
                "Das Board war schuld.",
                true);

        assertThrows(ExcuseCatalogValidationException.class,
                () -> validator.validate(new ExcuseCatalog("v1", List.of(template))));
    }

    @Test
    void validatesProductionCoverageSeparatelyFromStructuralValidation() {
        List<ExcuseTemplate> templates = new ArrayList<>();
        for (ExcuseStyle style : ExcuseStyle.values()) {
            for (int index = 1; index <= 6; index++) {
                templates.add(new ExcuseTemplate(
                        style.name().toLowerCase() + ".general." + index,
                        style,
                        EnumSet.allOf(GameType.class),
                        ExcuseTopic.GENERAL,
                        0,
                        100,
                        Set.of(),
                        Set.of(),
                        index <= 3 ? "Allgemeiner Text " + index : "{score} erklärt Text " + index,
                        true));
            }
        }
        ExcuseCatalog catalog = new ExcuseCatalog("v1", templates);

        assertDoesNotThrow(() -> validator.validate(catalog, ExcuseCatalogCoverage.production()));
    }

    @Test
    void reportsMissingProductionCoverageByStyle() {
        ExcuseCatalog catalog = new ExcuseCatalog("v1", List.of(general("technical.only.01", "Text")));

        ExcuseCatalogValidationException exception = assertThrows(
                ExcuseCatalogValidationException.class,
                () -> validator.validate(catalog, ExcuseCatalogCoverage.production()));

        assertTrue(exception.errors().stream().anyMatch(error -> error.contains("LEGAL has only 0 general")));
    }

    private static ExcuseTemplate general(String id, String text) {
        return new ExcuseTemplate(
                id,
                ExcuseStyle.TECHNICAL,
                EnumSet.allOf(GameType.class),
                ExcuseTopic.GENERAL,
                0,
                100,
                Set.of(),
                Set.of(),
                text,
                true);
    }
}
