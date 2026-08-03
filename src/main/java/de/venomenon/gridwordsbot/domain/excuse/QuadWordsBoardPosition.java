package de.venomenon.gridwordsbot.domain.excuse;

/** Stable, name-free position of one QuadWords board. */
public enum QuadWordsBoardPosition {
    TOP_LEFT("oben links", ExcuseFact.TOP_LEFT_WORST),
    TOP_RIGHT("oben rechts", ExcuseFact.TOP_RIGHT_WORST),
    BOTTOM_LEFT("unten links", ExcuseFact.BOTTOM_LEFT_WORST),
    BOTTOM_RIGHT("unten rechts", ExcuseFact.BOTTOM_RIGHT_WORST);

    private final String displayName;
    private final ExcuseFact worstPositionFact;

    QuadWordsBoardPosition(String displayName, ExcuseFact worstPositionFact) {
        this.displayName = displayName;
        this.worstPositionFact = worstPositionFact;
    }

    public String displayName() {
        return displayName;
    }

    public ExcuseFact worstPositionFact() {
        return worstPositionFact;
    }
}
