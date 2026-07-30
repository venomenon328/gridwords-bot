package de.venomenon.gridwordsbot.application.reminder;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Transport-neutral boundary for a later Discord reminder sender. */
public record ReminderMentionPlan(Set<Long> allowedUserIds) {
    public ReminderMentionPlan {
        allowedUserIds = Set.copyOf(Objects.requireNonNull(allowedUserIds));
        if (allowedUserIds.stream().anyMatch(id -> id <= 0)) throw new IllegalArgumentException("user IDs must be positive");
    }
    public List<String> mentions() {
        return allowedUserIds.stream().sorted().map(id -> "<@" + id + ">").toList();
    }
}
