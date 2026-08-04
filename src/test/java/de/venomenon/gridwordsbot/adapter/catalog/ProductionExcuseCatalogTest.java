package de.venomenon.gridwordsbot.adapter.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalog;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalogCoverage;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseCatalogValidator;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseReason;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ProductionExcuseCatalogTest {

    private static final String CATALOG_RESOURCE = "excuses/catalog.json";

    @Test
    void loadsTheCompleteReviewedProductionCatalog() {
        JsonExcuseCatalogLoader loader = new JsonExcuseCatalogLoader(
                JsonMapper.builder().build(),
                new ExcuseCatalogValidator(),
                ExcuseCatalogCoverage.production());

        ExcuseCatalog catalog = loader.loadResource(getClass().getClassLoader(), CATALOG_RESOURCE);

        assertThat(catalog.version()).isEqualTo("2026.08.04.1");
        assertThat(catalog.templates()).hasSize(564);
        assertThat(catalog.templates()).extracting(template -> template.id()).doesNotHaveDuplicates();
        assertThat(catalog.templates()).extracting(template -> template.text()).doesNotHaveDuplicates();
        assertThat(catalog.templates()).allSatisfy(template -> {
            assertThat(template.text()).doesNotContain("Raster", "@everyone", "@here");
            assertThat(template.selectable()).isTrue();
            assertThat(template.weight()).isEqualTo(100);
        });
    }

    @Test
    void preservesTheAcceptedFamilyAndStyleCoverage() {
        ExcuseCatalog catalog = new JsonExcuseCatalogLoader(
                JsonMapper.builder().build(),
                new ExcuseCatalogValidator(),
                ExcuseCatalogCoverage.production())
                .loadResource(getClass().getClassLoader(), CATALOG_RESOURCE);

        Map<String, Long> families = catalog.templates().stream()
                .map(template -> family(template.id()))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        assertThat(families).containsExactlyInAnyOrderEntriesOf(Map.of(
                "general", 144L,
                "not-solved", 64L,
                "very-late-submission", 58L,
                "gridwords-last-attempt", 56L,
                "gridwords-very-slow", 56L,
                "quadwords-very-slow", 56L,
                "quadwords-single-board-collapse", 72L,
                "clear-current-daily-outlier", 58L));

        for (ExcuseStyle style : ExcuseStyle.values()) {
            assertThat(catalog.templates().stream()
                    .filter(template -> template.id().startsWith("general."))
                    .filter(template -> template.style() == style))
                    .as("general templates for %s", style)
                    .hasSize(18);
        }

        for (ExcuseReason reason : ExcuseReason.values()) {
            for (ExcuseStyle style : ExcuseStyle.values()) {
                assertThat(catalog.templates().stream()
                        .filter(template -> template.style() == style)
                        .filter(template -> template.requiresAll().contains(reason)))
                        .as("templates for %s and %s", reason, style)
                        .hasSizeGreaterThanOrEqualTo(6);
            }
        }
    }

    @Test
    void containsTheReviewedExcuseOrJustificationRewrites() {
        ExcuseCatalog catalog = new JsonExcuseCatalogLoader().loadResource(
                getClass().getClassLoader(), CATALOG_RESOURCE);
        Map<String, String> texts = catalog.templates().stream()
                .collect(Collectors.toMap(template -> template.id(), template -> template.text()));

        assertThat(texts).containsEntry(
                "general.tactical.09",
                "Ich habe das Grid heute gezielt zu einer falschen Analyse meiner Muster verleitet.");
        assertThat(texts).containsEntry(
                "general.sporting.15",
                "Die heutige Leistung war offenbar Teil einer unangekündigten Regenerationseinheit.");
        assertThat(texts.get("clear-current-daily-outlier.technical.04"))
                .contains("Das spricht statistisch mindestens ebenso gegen das Sample wie gegen mich.");
        assertThat(texts.get("gridwords-very-slow.sporting.04"))
                .contains("weil ein überhasteter Angriff");
    }

    private static String family(String id) {
        return Arrays.stream(new String[] {
                    "quadwords-single-board-collapse",
                    "clear-current-daily-outlier",
                    "very-late-submission",
                    "gridwords-last-attempt",
                    "gridwords-very-slow",
                    "quadwords-very-slow",
                    "not-solved",
                    "general"
                })
                .filter(prefix -> id.startsWith(prefix + "."))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown catalog family: " + id));
    }
}
