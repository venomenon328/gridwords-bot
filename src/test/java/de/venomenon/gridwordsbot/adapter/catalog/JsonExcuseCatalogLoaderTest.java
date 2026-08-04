package de.venomenon.gridwordsbot.adapter.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JsonExcuseCatalogLoaderTest {

    private final JsonExcuseCatalogLoader loader = new JsonExcuseCatalogLoader();

    @Test
    void loadsTheVersionedTestCatalogStrictly() {
        ExcuseCatalog catalog = loader.loadResource(
                getClass().getClassLoader(), "excuses/test-catalog.json");

        assertEquals("test-v1", catalog.version());
        assertEquals(4, catalog.templates().size());
        assertEquals("quadwords.board.legal.01", catalog.templates().getLast().id());
        assertTrue(getClass().getClassLoader().getResource(JsonExcuseCatalogLoader.SCHEMA_RESOURCE) != null);
    }

    @Test
    void rejectsUnknownFieldsEnumsAndConditions() {
        String json = """
                {
                  "version": "v1",
                  "unexpected": true,
                  "templates": [{
                    "id": "broken.01",
                    "style": "UNKNOWN_STYLE",
                    "games": ["GRIDWORDS", "CHESS"],
                    "topic": "GENERAL",
                    "specificity": 10,
                    "weight": 100,
                    "requiresAll": ["NOT_A_FACT"],
                    "excludesAny": [],
                    "text": "Text",
                    "selectable": true
                  }]
                }
                """;

        ExcuseCatalogLoadException exception = assertThrows(
                ExcuseCatalogLoadException.class, () -> loader.load(stream(json)));

        assertTrue(exception.errors().stream().anyMatch(error -> error.contains("unknown field unexpected")));
        assertTrue(exception.errors().stream().anyMatch(error -> error.contains("unknown value UNKNOWN_STYLE")));
        assertTrue(exception.errors().stream().anyMatch(error -> error.contains("unknown value CHESS")));
        assertTrue(exception.errors().stream().anyMatch(error -> error.contains("unknown value NOT_A_FACT")));
    }

    @Test
    void rejectsUnknownPlaceholdersDuringCatalogValidation() {
        String json = validSingleTemplate("Text mit {unknownValue}.");

        ExcuseCatalogLoadException exception = assertThrows(
                ExcuseCatalogLoadException.class, () -> loader.load(stream(json)));

        assertTrue(exception.errors().stream().anyMatch(error -> error.contains("unknown placeholders")));
    }

    @Test
    void reportsMissingResourcesAndInvalidJson() {
        assertThrows(ExcuseCatalogLoadException.class,
                () -> loader.loadResource(getClass().getClassLoader(), "excuses/missing.json"));
        assertThrows(ExcuseCatalogLoadException.class, () -> loader.load(stream("not-json")));
    }

    private static String validSingleTemplate(String text) {
        return """
                {
                  "version": "v1",
                  "templates": [{
                    "id": "valid.01",
                    "style": "TECHNICAL",
                    "games": ["GRIDWORDS", "QUADWORDS"],
                    "topic": "GENERAL",
                    "specificity": 0,
                    "weight": 100,
                    "requiresAll": [],
                    "excludesAny": [],
                    "text": "%s",
                    "selectable": true
                  }]
                }
                """.formatted(text);
    }

    private static ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
