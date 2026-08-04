package de.venomenon.gridwordsbot.port.in;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseOption;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle;
import java.util.List;
import java.util.Objects;

/** Server-side authorization and initial-option preparation for one canonical excuse button. */
public interface ExcuseOpenUseCase {

    Result open(Request request);

    record Request(long guildId, long channelId, long canonicalMessageId, long gameResultId, long actorId) {
        public Request {
            if (guildId <= 0 || channelId <= 0 || canonicalMessageId <= 0 || gameResultId <= 0 || actorId <= 0) {
                throw new IllegalArgumentException("Discord and result IDs must be positive");
            }
        }
    }

    sealed interface Result permits Options, Rejected {
    }

    record Options(int contextGeneration, List<ExcuseOption> options, List<ExcuseStyle> availableRerollStyles) implements Result {
        public Options {
            if (contextGeneration < 1) {
                throw new IllegalArgumentException("contextGeneration must be positive");
            }
            options = List.copyOf(Objects.requireNonNull(options, "options"));
            availableRerollStyles = List.copyOf(Objects.requireNonNull(availableRerollStyles, "availableRerollStyles"));
            if (options.size() != 3) {
                throw new IllegalArgumentException("exactly three initial options are required");
            }
        }

        public Options(List<ExcuseOption> options) {
            this(1, options, List.of());
        }

        public Options(List<ExcuseOption> options, List<ExcuseStyle> availableRerollStyles) {
            this(1, options, availableRerollStyles);
        }
    }

    record Rejected(Reason reason) implements Result {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    enum Reason {
        FEATURE_DISABLED,
        CONTEXT_MISMATCH,
        NOT_RESULT_AUTHOR,
        OFFER_UNAVAILABLE
    }
}
