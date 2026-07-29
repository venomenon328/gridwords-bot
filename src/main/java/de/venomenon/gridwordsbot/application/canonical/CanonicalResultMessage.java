package de.venomenon.gridwordsbot.application.canonical;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;
import java.util.OptionalInt;

/** Transport-neutral data required for one canonical GridWords embed. */
public record CanonicalResultMessage(
        String playerDisplayName,
        GameType gameType,
        LocalDate gameDate,
        ShareOutcome outcome,
        Duration duration,
        NormalizedBoard board,
        StreakSummary streaks,
        OptionalInt personalComplete,
        OptionalInt personalPerfect,
        OptionalInt sharedComplete,
        OptionalInt sharedPerfect,
        String publicationKey) {

    public CanonicalResultMessage {
        Objects.requireNonNull(playerDisplayName);
        Objects.requireNonNull(gameType);
        Objects.requireNonNull(gameDate);
        Objects.requireNonNull(outcome);
        Objects.requireNonNull(duration);
        Objects.requireNonNull(board);
        Objects.requireNonNull(streaks);
        Objects.requireNonNull(personalComplete);
        Objects.requireNonNull(personalPerfect);
        Objects.requireNonNull(sharedComplete);
        Objects.requireNonNull(sharedPerfect);
        Objects.requireNonNull(publicationKey);
    }
}
