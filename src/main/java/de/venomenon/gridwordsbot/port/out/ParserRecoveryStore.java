package de.venomenon.gridwordsbot.port.out;

import de.venomenon.gridwordsbot.domain.parsing.ParseErrorCode;
import java.util.List;
import java.util.Objects;

/** Narrow persistence boundary for explicitly approved parser-repair maintenance work. */
public interface ParserRecoveryStore {

    List<Candidate> findCandidates(long guildId, long channelId, ParseErrorCode errorCode);

    /**
     * Atomically prepares a parser rejection for maintenance processing.
     * The original parser error remains stored as the durable recovery marker.
     */
    boolean prepare(long sourceMessageId, ParseErrorCode errorCode);

    /** Clears a recovery marker only after the submission reached a durable post-parse state. */
    boolean complete(long sourceMessageId, ParseErrorCode errorCode);

    record Candidate(
            long sourceMessageId,
            String rawMessageContent,
            SubmissionStore.SubmissionState state) {

        public Candidate {
            if (sourceMessageId <= 0) {
                throw new IllegalArgumentException("sourceMessageId must be positive");
            }
            Objects.requireNonNull(rawMessageContent, "rawMessageContent");
            Objects.requireNonNull(state, "state");
        }
    }
}
