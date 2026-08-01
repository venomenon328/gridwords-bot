package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.reporting.RenderedReportPage;
import java.util.List;
import java.util.Objects;

/** Transport-neutral boundary for ordered report pages and later Discord-side reconciliation. */
public interface PeriodicReportMessageGateway {

    long create(long channelId, ReportPage page);

    void edit(long channelId, long messageId, ReportPage page);

    /** Loads a known bot message or throws {@link MissingMessageException} when it no longer exists. */
    PublishedReportPage load(long channelId, long messageId);

    /** Returns every exact rendered-page match in deterministic ascending message-id order. */
    List<PublishedReportPage> findExactMatches(long channelId, ReportPage page);

    void delete(long channelId, long messageId);

    /** A rendered page paired with its zero-based visible order inside one report. */
    record ReportPage(int pageIndex, RenderedReportPage renderedPage) {
        public ReportPage {
            if (pageIndex < 0) {
                throw new IllegalArgumentException("pageIndex must not be negative");
            }
            Objects.requireNonNull(renderedPage, "renderedPage");
        }
    }

    /** A bot message read from the channel, retaining its transport-neutral visible page. */
    record PublishedReportPage(long messageId, ReportPage page) {
        public PublishedReportPage {
            if (messageId <= 0) {
                throw new IllegalArgumentException("messageId must be positive");
            }
            Objects.requireNonNull(page, "page");
        }
    }

    /** Base type for a classified gateway outcome safe for delivery orchestration. */
    sealed class MessageGatewayException extends RuntimeException
            permits RetryableMessageException, PermanentMessageException, UnknownMessageException, MissingMessageException {
        protected MessageGatewayException(String safeMessage, Throwable cause) {
            super(requireSafeMessage(safeMessage), cause);
        }

        private static String requireSafeMessage(String safeMessage) {
            if (safeMessage == null || safeMessage.isBlank() || safeMessage.indexOf('\n') >= 0 || safeMessage.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("safeMessage must be a non-blank single-line value");
            }
            return safeMessage;
        }
    }

    final class RetryableMessageException extends MessageGatewayException {
        public RetryableMessageException(String safeMessage, Throwable cause) { super(safeMessage, cause); }
    }

    final class PermanentMessageException extends MessageGatewayException {
        public PermanentMessageException(String safeMessage, Throwable cause) { super(safeMessage, cause); }
    }

    /** The external operation outcome is unknown and must be reconciled rather than blindly repeated. */
    final class UnknownMessageException extends MessageGatewayException {
        public UnknownMessageException(String safeMessage, Throwable cause) { super(safeMessage, cause); }
    }

    /** A previously known Discord message no longer exists. */
    final class MissingMessageException extends MessageGatewayException {
        public MissingMessageException(String safeMessage) { super(safeMessage, null); }
    }
}
