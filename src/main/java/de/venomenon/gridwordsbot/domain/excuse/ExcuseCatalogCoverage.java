package de.venomenon.gridwordsbot.domain.excuse;

/** Optional catalog-size requirements, separated from structural validation for incremental development. */
public record ExcuseCatalogCoverage(int minimumGeneralPerStyle, int minimumBaselineRenderablePerStyle) {

    public ExcuseCatalogCoverage {
        if (minimumGeneralPerStyle < 0 || minimumBaselineRenderablePerStyle < 0) {
            throw new IllegalArgumentException("coverage limits must not be negative");
        }
        if (minimumBaselineRenderablePerStyle > minimumGeneralPerStyle) {
            throw new IllegalArgumentException("baseline renderable minimum cannot exceed the general minimum");
        }
    }

    public static ExcuseCatalogCoverage structuralOnly() {
        return new ExcuseCatalogCoverage(0, 0);
    }

    public static ExcuseCatalogCoverage production() {
        return new ExcuseCatalogCoverage(6, 3);
    }
}
