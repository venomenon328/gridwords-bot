package de.venomenon.gridwordsbot.domain.excuse;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Exactly three distinct rendered options for one interaction round. */
public record ExcuseSelection(ExcuseRound round, List<ExcuseOption> options) {

    public ExcuseSelection {
        Objects.requireNonNull(round, "round");
        options = List.copyOf(Objects.requireNonNull(options, "options"));
        if (options.size() != 3) {
            throw new IllegalArgumentException("an excuse selection must contain exactly three options");
        }
        Set<String> ids = new HashSet<>();
        for (int index = 0; index < options.size(); index++) {
            ExcuseOption option = options.get(index);
            if (option.round() != round || option.position() != index + 1) {
                throw new IllegalArgumentException("option round and positions must match the selection");
            }
            if (!ids.add(option.templateId())) {
                throw new IllegalArgumentException("template ids must be unique within a selection");
            }
        }
    }
}
