package de.venomenon.gridwordsbot.domain.reporting;

/** An exact, unrounded ratio for later presentation. */
public record ReportRatio(int numerator, int denominator) {
    public ReportRatio {
        if (denominator <= 0) throw new IllegalArgumentException("denominator must be positive");
        if (numerator < 0 || numerator > denominator) {
            throw new IllegalArgumentException("numerator must be between zero and denominator");
        }
    }
}
