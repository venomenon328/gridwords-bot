package de.venomenon.gridwordsbot.port.in;

import de.venomenon.gridwordsbot.domain.excuse.ExcuseOption;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseRound;
import de.venomenon.gridwordsbot.domain.excuse.ExcuseStyle;
import java.util.List;
import java.util.Objects;

/** Authorized follow-up actions for the ephemeral excuse flow. */
public interface ExcuseInteractionUseCase {

    Result openStyleMenu(ActionRequest request);

    Result selectStyle(StyleRequest request);

    Result pick(PickRequest request);

    Result decline(ActionRequest request);

    record ActionRequest(
            long guildId, long channelId, long canonicalMessageId, long gameResultId, long actorId, int contextGeneration) {
        public ActionRequest {
            if (guildId <= 0 || channelId <= 0 || canonicalMessageId <= 0 || gameResultId <= 0 || actorId <= 0
                    || contextGeneration < 1) {
                throw new IllegalArgumentException("Discord IDs, result ID, and context generation must be positive");
            }
        }
    }

    record PickRequest(ActionRequest action, ExcuseRound round, int position) {
        public PickRequest {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(round, "round");
            if (position < 1 || position > 3) {
                throw new IllegalArgumentException("position must be between 1 and 3");
            }
        }
    }

    record StyleRequest(ActionRequest action, ExcuseStyle style) {
        public StyleRequest {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(style, "style");
        }
    }

    sealed interface Result permits Options, StyleMenu, Selected, Declined, Rejected {
    }

    record Options(List<ExcuseOption> options, List<ExcuseStyle> availableRerollStyles) implements Result {
        public Options {
            options = List.copyOf(Objects.requireNonNull(options, "options"));
            availableRerollStyles = List.copyOf(Objects.requireNonNull(availableRerollStyles, "availableRerollStyles"));
            if (options.size() != 3) {
                throw new IllegalArgumentException("exactly three options are required");
            }
        }
    }

    record StyleMenu(List<ExcuseStyle> styles) implements Result {
        public StyleMenu {
            styles = List.copyOf(Objects.requireNonNull(styles, "styles"));
            if (styles.isEmpty()) {
                throw new IllegalArgumentException("at least one reroll style is required");
            }
        }
    }

    enum Selected implements Result { INSTANCE }

    enum Declined implements Result { INSTANCE }

    record Rejected(Reason reason) implements Result {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }
    }

    enum Reason {
        FEATURE_DISABLED,
        CONTEXT_MISMATCH,
        NOT_RESULT_AUTHOR,
        OFFER_UNAVAILABLE,
        REROLL_UNAVAILABLE,
        OPTION_UNAVAILABLE
    }
}
