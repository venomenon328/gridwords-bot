package de.venomenon.gridwordsbot.port.in;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseOption;
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

    record Options(List<ExcuseOption> options) implements Result {
        public Options {
            options = List.copyOf(Objects.requireNonNull(options, "options"));
            if (options.size() != 3) {
                throw new IllegalArgumentException("exactly three initial options are required");
            }
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
