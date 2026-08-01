package de.venomenon.gridwordsbot.domain.reporting;

import java.util.List;
import java.util.Objects;

/** Final ordered report pages and their stable content fingerprint. */
public record RenderedPeriodicReport(List<RenderedReportPage> pages, String contentFingerprint) {
    public RenderedPeriodicReport {
        pages = List.copyOf(Objects.requireNonNull(pages, "pages"));
        contentFingerprint = Objects.requireNonNull(contentFingerprint, "contentFingerprint");
        if (pages.isEmpty()) throw new IllegalArgumentException("rendered report needs pages");
        if (!contentFingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("content fingerprint must be a lowercase SHA-256 hex value");
        }
    }
}
