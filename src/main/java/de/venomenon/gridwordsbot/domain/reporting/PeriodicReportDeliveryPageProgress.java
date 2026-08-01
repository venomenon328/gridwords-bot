package de.venomenon.gridwordsbot.domain.reporting;

/** One durably confirmed page in the visible order of a logical report delivery. */
public record PeriodicReportDeliveryPageProgress(int pageIndex, long messageId) {
    public PeriodicReportDeliveryPageProgress {
        if (pageIndex < 0 || messageId <= 0) {
            throw new IllegalArgumentException("pageIndex must be non-negative and messageId must be positive");
        }
    }
}
