package de.venomenon.gridwordsbot.domain.reporting;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** One report participant with the historical facts relevant to the selected period. */
public record ReportParticipant(
        long discordUserId,
        String displayName,
        LocalDate firstParticipationStart,
        List<LocalDate> unionParticipationDays,
        List<LocalDate> gridWordsParticipationDays,
        List<LocalDate> quadWordsParticipationDays,
        List<LocalDate> bothGamesParticipationDays) {
    public ReportParticipant {
        if (discordUserId <= 0) throw new IllegalArgumentException("discordUserId must be positive");
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
        Objects.requireNonNull(firstParticipationStart, "firstParticipationStart");
        unionParticipationDays = immutableDistinctDays(unionParticipationDays, "unionParticipationDays");
        gridWordsParticipationDays = immutableDistinctDays(gridWordsParticipationDays, "gridWordsParticipationDays");
        quadWordsParticipationDays = immutableDistinctDays(quadWordsParticipationDays, "quadWordsParticipationDays");
        bothGamesParticipationDays = immutableDistinctDays(bothGamesParticipationDays, "bothGamesParticipationDays");
        if (unionParticipationDays.isEmpty()) throw new IllegalArgumentException("participant needs union participation days");

        Set<LocalDate> expectedUnion = new HashSet<>(gridWordsParticipationDays);
        expectedUnion.addAll(quadWordsParticipationDays);
        Set<LocalDate> expectedBoth = new HashSet<>(gridWordsParticipationDays);
        expectedBoth.retainAll(quadWordsParticipationDays);
        if (!expectedUnion.equals(Set.copyOf(unionParticipationDays))) {
            throw new IllegalArgumentException("union participation days must match both game histories");
        }
        if (!expectedBoth.equals(Set.copyOf(bothGamesParticipationDays))) {
            throw new IllegalArgumentException("both-games participation days must match the game-history intersection");
        }
    }

    private static List<LocalDate> immutableDistinctDays(List<LocalDate> days, String name) {
        List<LocalDate> copy = List.copyOf(Objects.requireNonNull(days, name));
        if (copy.stream().anyMatch(Objects::isNull) || Set.copyOf(copy).size() != copy.size()) {
            throw new IllegalArgumentException(name + " must contain distinct non-null dates");
        }
        return copy;
    }
}
