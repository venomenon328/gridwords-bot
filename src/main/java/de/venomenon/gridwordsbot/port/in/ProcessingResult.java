package de.venomenon.gridwordsbot.port.in;

import java.util.Objects;

/** Transport-neutral result used by the Discord adapter to decide whether to react. */
public sealed interface ProcessingResult permits ProcessingResult.Ignored, ProcessingResult.Accepted, ProcessingResult.Rejected {

    record Ignored() implements ProcessingResult {
    }

    record Accepted() implements ProcessingResult {
    }

    record Rejected(String errorCode) implements ProcessingResult {
        public Rejected {
            Objects.requireNonNull(errorCode, "errorCode");
            if (errorCode.isBlank()) {
                throw new IllegalArgumentException("errorCode must not be blank");
            }
        }
    }
}
