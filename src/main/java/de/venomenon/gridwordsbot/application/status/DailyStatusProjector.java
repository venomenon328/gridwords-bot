package de.venomenon.gridwordsbot.application.status;

import de.venomenon.gridwordsbot.domain.model.DailyGameParticipation;
import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.domain.streak.StreakCalculator;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Builds a complete status from persisted facts; no Discord or Spring types cross this boundary. */
public final class DailyStatusProjector {
    private final GameResultStore results;
    private final PlayerStore players;
    private final StreakCalculator calculator = new StreakCalculator();

    public DailyStatusProjector(GameResultStore results, PlayerStore players) {
        this.results = results;
        this.players = players;
    }

    public DailyStatus project(LocalDate date, LocalDate today) {
        if (date.isAfter(today)) {
            throw new IllegalArgumentException("status date must not be in the future");
        }
        List<GameResultStore.StoredGameResult> all = results.findAll();
        List<GameParticipationPeriod> periods = players.findGameParticipationPeriods();
        DailyGameParticipation participation = DailyGameParticipation.fromPeriods(date, periods);
        Map<Long, PlayerStore.StoredPlayer> profiles = players.findAllPlayers().stream()
                .collect(Collectors.toMap(PlayerStore.StoredPlayer::discordUserId, player -> player));
        List<StreakCalculator.PlayerResult> streakResults = all.stream()
                .map(result -> new StreakCalculator.PlayerResult(result.playerId(), result.parsedResult()))
                .toList();
        List<DailyStatus.PlayerLine> lines = participation.participatingPlayers().stream()
                .map(id -> line(id, date, date.equals(today), all, periods, participation, profiles, streakResults))
                .sorted(Comparator
                        .comparing((DailyStatus.PlayerLine line) -> line.displayName().toLowerCase(Locale.ROOT))
                        .thenComparingLong(DailyStatus.PlayerLine::discordUserId))
                .toList();
        StreakSummary shared = lines.isEmpty() ? null : lines.getFirst().streaks();
        return new DailyStatus(
                date,
                lines,
                shared == null ? 0 : shared.sharedGridWordsSolved(),
                shared == null ? 0 : shared.sharedQuadWordsSolved(),
                shared == null ? 0 : shared.sharedComplete(),
                shared == null ? 0 : shared.sharedPerfect());
    }

    private DailyStatus.PlayerLine line(
            long id,
            LocalDate date,
            boolean provisionalCurrentDay,
            List<GameResultStore.StoredGameResult> all,
            List<GameParticipationPeriod> periods,
            DailyGameParticipation participation,
            Map<Long, PlayerStore.StoredPlayer> profiles,
            List<StreakCalculator.PlayerResult> streakResults) {
        PlayerStore.StoredPlayer profile = profiles.get(id);
        if (profile == null) {
            throw new IllegalStateException("participation period without profile: " + id);
        }
        StreakSummary summary = calculator.calculateWithGameParticipation(
                streakResults, periods, id, date, provisionalCurrentDay);
        return new DailyStatus.PlayerLine(
                id,
                profile.displayName(),
                state(GameType.GRIDWORDS, id, date, participation, all),
                state(GameType.QUADWORDS, id, date, participation, all),
                summary);
    }

    private static DailyStatus.GameState state(
            GameType gameType,
            long playerId,
            LocalDate date,
            DailyGameParticipation participation,
            List<GameResultStore.StoredGameResult> all) {
        return new DailyStatus.GameState(
                gameType,
                participation.playersFor(gameType).contains(playerId),
                all.stream()
                        .filter(result -> result.playerId() == playerId
                                && result.parsedResult().gameType() == gameType
                                && result.parsedResult().gameDate().equals(date))
                        .findFirst()
                        .map(GameResultStore.StoredGameResult::parsedResult));
    }
}