package de.venomenon.gridwordsbot.domain.excuse;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Performs complete structural validation and optional production coverage checks. */
public final class ExcuseCatalogValidator {

    public static final int MAX_TEXT_LENGTH = 500;

    private final ExcuseTemplateRenderer renderer;

    public ExcuseCatalogValidator() {
        this(new ExcuseTemplateRenderer());
    }

    public ExcuseCatalogValidator(ExcuseTemplateRenderer renderer) {
        this.renderer = renderer;
    }

    public ExcuseCatalog validate(ExcuseCatalog catalog) {
        return validate(catalog, ExcuseCatalogCoverage.structuralOnly());
    }

    public ExcuseCatalog validate(ExcuseCatalog catalog, ExcuseCatalogCoverage coverage) {
        List<String> errors = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (ExcuseTemplate template : catalog.templates()) {
            if (!ids.add(template.id())) {
                errors.add("duplicate template id " + template.id());
            }
            if (template.text().length() > MAX_TEXT_LENGTH) {
                errors.add(template.id() + " exceeds " + MAX_TEXT_LENGTH + " characters");
            }
            String lowerText = template.text().toLowerCase(Locale.ROOT);
            if (lowerText.contains("@everyone") || lowerText.contains("@here")) {
                errors.add(template.id() + " contains a forbidden mention");
            }
            ExcuseTemplateRenderer.Analysis analysis = renderer.analyze(template.text());
            if (analysis.malformed()) {
                errors.add(template.id() + " contains malformed braces");
            }
            if (!analysis.unknownTokens().isEmpty()) {
                errors.add(template.id() + " contains unknown placeholders " + analysis.unknownTokens());
            }
            if (!template.requiresAll().isEmpty() && template.specificity() == 0) {
                errors.add(template.id() + " requires context but has zero specificity");
            }
            if (template.isGeneral() && template.specificity() != 0) {
                errors.add(template.id() + " is general but has non-zero specificity");
            }
        }
        validateCoverage(catalog, coverage, errors);
        if (!errors.isEmpty()) {
            throw new ExcuseCatalogValidationException(errors);
        }
        return catalog;
    }

    private void validateCoverage(
            ExcuseCatalog catalog, ExcuseCatalogCoverage coverage, List<String> errors) {
        if (coverage.minimumGeneralPerStyle() == 0 && coverage.minimumBaselineRenderablePerStyle() == 0) {
            return;
        }
        Map<ExcuseStyle, Integer> generalCounts = new EnumMap<>(ExcuseStyle.class);
        Map<ExcuseStyle, Integer> baselineCounts = new EnumMap<>(ExcuseStyle.class);
        for (ExcuseStyle style : ExcuseStyle.values()) {
            generalCounts.put(style, 0);
            baselineCounts.put(style, 0);
        }
        for (ExcuseTemplate template : catalog.templates()) {
            if (!template.selectable() || !template.isGeneral()) {
                continue;
            }
            generalCounts.compute(template.style(), (ignored, count) -> count + 1);
            boolean renderableForBothGames = Set.of(GameType.values()).stream()
                    .allMatch(game -> renderer.render(template, ExcuseContext.forGame(game)).isPresent());
            if (renderableForBothGames) {
                baselineCounts.compute(template.style(), (ignored, count) -> count + 1);
            }
        }
        for (ExcuseStyle style : ExcuseStyle.values()) {
            if (generalCounts.get(style) < coverage.minimumGeneralPerStyle()) {
                errors.add(style + " has only " + generalCounts.get(style) + " general templates");
            }
            if (baselineCounts.get(style) < coverage.minimumBaselineRenderablePerStyle()) {
                errors.add(style + " has only " + baselineCounts.get(style) + " baseline-renderable templates");
            }
        }
    }
}
