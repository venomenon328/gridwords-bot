package de.venomenon.gridwordsbot.application.status;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.domain.streak.StreakCalculator;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import de.venomenon.gridwordsbot.domain.status.DailyStatus;
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
        List<GameResultStore.StoredGameResult> all = results.findAll();
        List<ParticipationPeriod> periods = players.findParticipationPeriods();
        Map<Long, PlayerStore.StoredPlayer> profiles = players.findAllPlayers().stream()
                .collect(Collectors.toMap(PlayerStore.StoredPlayer::discordUserId, player -> player));
        List<StreakCalculator.PlayerResult> streakResults = all.stream()
                .map(result -> new StreakCalculator.PlayerResult(result.playerId(), result.parsedResult())).toList();
        List<DailyStatus.PlayerLine> lines = periods.stream().filter(period -> period.contains(date))
                .map(ParticipationPeriod::playerId).distinct().map(id -> line(id, date, today, all, periods, profiles, streakResults))
                .sorted(Comparator.comparing((DailyStatus.PlayerLine line) -> line.displayName().toLowerCase(Locale.ROOT))
                        .thenComparingLong(DailyStatus.PlayerLine::discordUserId)).toList();
        int sharedComplete = lines.isEmpty() ? 0 : lines.getFirst().streaks().sharedComplete();
        int sharedPerfect = lines.isEmpty() ? 0 : lines.getFirst().streaks().sharedPerfect();
        return new DailyStatus(date, lines, sharedComplete, sharedPerfect);
    }

    private DailyStatus.PlayerLine line(long id, LocalDate date, LocalDate today,
            List<GameResultStore.StoredGameResult> all, List<ParticipationPeriod> periods,
            Map<Long, PlayerStore.StoredPlayer> profiles, List<StreakCalculator.PlayerResult> streakResults) {
        PlayerStore.StoredPlayer profile = profiles.get(id);
        if (profile == null) throw new IllegalStateException("participation period without profile: " + id);
        StreakSummary summary = calculator.calculateWithParticipation(streakResults, periods, id, today);
        return new DailyStatus.PlayerLine(id, profile.displayName(),
                all.stream().filter(r -> r.playerId() == id && r.parsedResult().gameType() == GameType.GRIDWORDS
                        && r.parsedResult().gameDate().equals(date)).findFirst().map(GameResultStore.StoredGameResult::parsedResult),
                all.stream().filter(r -> r.playerId() == id && r.parsedResult().gameType() == GameType.QUADWORDS
                        && r.parsedResult().gameDate().equals(date)).findFirst().map(GameResultStore.StoredGameResult::parsedResult), summary);
    }
}
