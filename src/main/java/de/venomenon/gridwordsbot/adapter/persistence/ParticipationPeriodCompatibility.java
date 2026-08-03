package de.venomenon.gridwordsbot.adapter.persistence;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Derives the temporary global compatibility history from persisted per-game intervals. */
final class ParticipationPeriodCompatibility {
    private ParticipationPeriodCompatibility() {
    }

    static List<ParticipationPeriod> union(Collection<GameParticipationPeriod> typedPeriods) {
        Objects.requireNonNull(typedPeriods, "typedPeriods");
        List<GameParticipationPeriod> sorted = new ArrayList<>(typedPeriods);
        sorted.sort(Comparator.comparingLong(GameParticipationPeriod::playerId)
                .thenComparing(GameParticipationPeriod::activeFrom)
                .thenComparing(GameParticipationPeriod::inactiveFrom,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        List<ParticipationPeriod> result = new ArrayList<>();
        ParticipationPeriod current = null;
        for (GameParticipationPeriod typed : sorted) {
            ParticipationPeriod candidate = new ParticipationPeriod(
                    typed.playerId(), typed.activeFrom(), typed.inactiveFrom());
            if (current == null || startsAfterGlobalGap(current, candidate)) {
                if (current != null) {
                    result.add(current);
                }
                current = candidate;
                continue;
            }
            current = new ParticipationPeriod(
                    current.playerId(), current.activeFrom(), laterEnd(current.inactiveFrom(), candidate.inactiveFrom()));
        }
        if (current != null) {
            result.add(current);
        }
        return List.copyOf(result);
    }

    private static boolean startsAfterGlobalGap(ParticipationPeriod current, ParticipationPeriod candidate) {
        if (current.playerId() != candidate.playerId()) {
            return true;
        }
        return current.inactiveFrom() != null && candidate.activeFrom().isAfter(current.inactiveFrom());
    }

    private static LocalDate laterEnd(LocalDate first, LocalDate second) {
        if (first == null || second == null) {
            return null;
        }
        return first.isAfter(second) ? first : second;
    }
}
