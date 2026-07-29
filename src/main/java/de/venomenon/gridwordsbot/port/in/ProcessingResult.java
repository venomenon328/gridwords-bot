package de.venomenon.gridwordsbot.port.in;

import de.venomenon.gridwordsbot.domain.model.GameType;
import java.util.Objects;

/** Transport-neutral result used by the Discord adapter to decide whether to react. */
public sealed interface ProcessingResult permits ProcessingResult.Ignored, ProcessingResult.Accepted, ProcessingResult.Rejected {

    record Ignored() implements ProcessingResult {
    }

    record Accepted(GameType gameType) implements ProcessingResult {
        public Accepted() {
            this(GameType.GRIDWORDS);
        }

        public Accepted {
            Objects.requireNonNull(gameType, "gameType");
        }
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
