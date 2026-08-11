package de.venomenon.gridwordsbot.port.in;

import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;

/** Application boundary for one already filtered, transport-neutral shared result message. */
public interface ProcessSharedResultUseCase {
    ProcessingResult process(InboundSharedMessage message);

    /**
     * Explicit maintenance path for a previously parser-rejected submission prepared for recovery.
     * Implementations may bypass ordinary admission rules only after verifying the persisted recovery marker.
     */
    default ProcessingResult processMaintenanceRecovery(
            InboundSharedMessage message, ParseErrorCode recoveredErrorCode) {
        throw new UnsupportedOperationException("maintenance recovery is not available");
    }
}
