package de.venomenon.gridwordsbot.domain.status;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Transport-neutral, complete projection of one business day's state. */
public record DailyStatus(
        LocalDate gameDate,
        List<PlayerLine> players,
        int sharedGridWordsSolved,
        int sharedQuadWordsSolved,
        int sharedComplete,
        int sharedPerfect) {

    public DailyStatus(LocalDate gameDate, List<PlayerLine> players, int sharedComplete, int sharedPerfect) {
        this(gameDate, players, 0, 0, sharedComplete, sharedPerfect);
    }

    public DailyStatus {
        Objects.requireNonNull(gameDate, "gameDate");
        players = List.copyOf(Objects.requireNonNull(players, "players"));
        if (sharedGridWordsSolved < 0
                || sharedQuadWordsSolved < 0
                || sharedComplete < 0
                || sharedPerfect < 0) {
            throw new IllegalArgumentException("negative shared streak");
        }
    }

    public record PlayerLine(
            long discordUserId,
            String displayName,
            GameState gridWordsState,
            GameState quadWordsState,
            StreakSummary streaks) {

        public PlayerLine {
            if (discordUserId <= 0) {
                throw new IllegalArgumentException("discordUserId must be positive");
            }
            Objects.requireNonNull(displayName, "displayName");
            gridWordsState = checkedState(gridWordsState, GameType.GRIDWORDS);
            quadWordsState = checkedState(quadWordsState, GameType.QUADWORDS);
            Objects.requireNonNull(streaks, "streaks");
        }

        /** Compatibility constructor for the pre-10.6 two-game projection. */
        public PlayerLine(
                long discordUserId,
                String displayName,
                Optional<ParsedGameResult> gridWords,
                Optional<ParsedGameResult> quadWords,
                StreakSummary streaks) {
            this(
                    discordUserId,
                    displayName,
                    new GameState(GameType.GRIDWORDS, true, gridWords),
                    new GameState(GameType.QUADWORDS, true, quadWords),
                    streaks);
        }

        public Optional<ParsedGameResult> gridWords() {
            return gridWordsState.result();
        }

        public Optional<ParsedGameResult> quadWords() {
            return quadWordsState.result();
        }

        public GameState game(GameType type) {
            return type == GameType.GRIDWORDS ? gridWordsState : quadWordsState;
        }

        public boolean participates(GameType type) {
            return game(type).participating();
        }

        public Optional<ParsedGameResult> result(GameType type) {
            return game(type).result();
        }

        private static GameState checkedState(GameState state, GameType expectedType) {
            Objects.requireNonNull(state, expectedType + " state");
            if (state.gameType() != expectedType) {
                throw new IllegalArgumentException("state must belong to " + expectedType);
            }
            return state;
        }
    }

    /** Explicit transport-neutral state of one game: participation is independent from result presence. */
    public record GameState(GameType gameType, boolean participating, Optional<ParsedGameResult> result) {
        public GameState {
            Objects.requireNonNull(gameType, "gameType");
            result = Objects.requireNonNull(result, "result");
            if (result.isPresent() && result.get().gameType() != gameType) {
                throw new IllegalArgumentException("result game type must match state game type");
            }
            if (!participating && result.isPresent()) {
                throw new IllegalArgumentException("a non-participating game cannot have a result");
            }
        }
    }
}