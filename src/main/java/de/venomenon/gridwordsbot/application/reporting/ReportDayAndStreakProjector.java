package de.venomenon.gridwordsbot.application.reporting;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.reporting.ReportDayAndStreakProjection;
import de.venomenon.gridwordsbot.domain.reporting.ReportGameResult;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipant;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipantBasis;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipantDayAndStreakSnapshot;
import de.venomenon.gridwordsbot.domain.reporting.ReportPersonalDayCounts;
import de.venomenon.gridwordsbot.domain.reporting.ReportPersonalStreaks;
import de.venomenon.gridwordsbot.domain.reporting.ReportSharedDayCounts;
import de.venomenon.gridwordsbot.domain.reporting.ReportSharedStreaks;
import de.venomenon.gridwordsbot.domain.reporting.ReportStreakHistory;
import de.venomenon.gridwordsbot.domain.reporting.ReportStreakSnapshot;
import de.venomenon.gridwordsbot.domain.streak.CalendarStreakCalculator;
import de.venomenon.gridwordsbot.port.out.ReportStreakHistoryQuery;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Derives final report-day features and the seven report streak snapshots without formatting or storage. */
public final class ReportDayAndStreakProjector {
    private final ReportStreakHistoryQuery historyQuery;
    private final CalendarStreakCalculator streaks = new CalendarStreakCalculator();

    public ReportDayAndStreakProjector(ReportStreakHistoryQuery historyQuery) {
        this.historyQuery = Objects.requireNonNull(historyQuery, "historyQuery");
    }

    public ReportDayAndStreakProjection project(ReportParticipantBasis basis) {
        Objects.requireNonNull(basis, "basis");
        LocalDate cutoff = basis.period().statisticsAndStreakCutoff();
        ReportStreakHistory history = Objects.requireNonNull(historyQuery.findThrough(cutoff), "streak history");
        HistoricalDays days = HistoricalDays.from(history, cutoff);

        List<ReportParticipantDayAndStreakSnapshot> participants = basis.participants().stream()
                .map(participant -> projectParticipant(participant, basis, days))
                .toList();
        return new ReportDayAndStreakProjection(
                participants,
                sharedDayCounts(basis, days),
                sharedStreaks(days, cutoff));
    }

    private ReportParticipantDayAndStreakSnapshot projectParticipant(
            ReportParticipant participant, ReportParticipantBasis basis, HistoricalDays days) {
        long playerId = participant.discordUserId();
        Set<LocalDate> unionParticipationDays = Set.copyOf(participant.unionParticipationDays());
        Set<LocalDate> bothGamesParticipationDays = Set.copyOf(participant.bothGamesParticipationDays());
        int activityDays = count(unionParticipationDays, day -> days.activity(playerId, day));
        int completeDays = count(bothGamesParticipationDays, day -> days.complete(playerId, day));
        int perfectDays = count(bothGamesParticipationDays, day -> days.perfect(playerId, day));
        LocalDate firstRelevantDay = days.firstParticipationDay(playerId).orElse(participant.firstParticipationStart());
        LocalDate cutoff = basis.period().statisticsAndStreakCutoff();

        return new ReportParticipantDayAndStreakSnapshot(
                playerId,
                new ReportPersonalDayCounts(unionParticipationDays.size(), activityDays, completeDays, perfectDays),
                new ReportPersonalStreaks(
                        snapshot(firstRelevantDay, cutoff, day -> days.activity(playerId, day)),
                        snapshot(firstRelevantDay, cutoff, day -> days.complete(playerId, day)),
                        snapshot(firstRelevantDay, cutoff, day -> days.solved(playerId, day, GameType.GRIDWORDS)),
                        snapshot(firstRelevantDay, cutoff, day -> days.solved(playerId, day, GameType.QUADWORDS)),
                        snapshot(firstRelevantDay, cutoff, day -> days.perfect(playerId, day))));
    }

    private ReportSharedDayCounts sharedDayCounts(ReportParticipantBasis basis, HistoricalDays days) {
        int complete = count(basis.sharedPossibleDays(), days::sharedComplete);
        int perfect = count(basis.sharedPossibleDays(), days::sharedPerfect);
        return new ReportSharedDayCounts(basis.sharedPossibleDays().size(), complete, perfect);
    }

    private ReportSharedStreaks sharedStreaks(HistoricalDays days, LocalDate cutoff) {
        LocalDate firstRelevantDay = days.firstParticipationDay().orElse(cutoff);
        return new ReportSharedStreaks(
                snapshot(firstRelevantDay, cutoff, days::sharedComplete),
                snapshot(firstRelevantDay, cutoff, days::sharedPerfect));
    }

    private ReportStreakSnapshot snapshot(
            LocalDate firstRelevantDay, LocalDate cutoff, java.util.function.Predicate<LocalDate> condition) {
        CalendarStreakCalculator.StreakValues values = streaks.through(firstRelevantDay, cutoff, condition);
        return new ReportStreakSnapshot(values.current(), values.record());
    }

    private static int count(Set<LocalDate> days, java.util.function.Predicate<LocalDate> condition) {
        return Math.toIntExact(days.stream().filter(condition).count());
    }

    private static final class HistoricalDays {
        private final Map<Long, List<GameParticipationPeriod>> periodsByPlayer;
        private final Map<Long, Map<LocalDate, Map<GameType, ReportGameResult>>> resultsByPlayer;

        private HistoricalDays(
                Map<Long, List<GameParticipationPeriod>> periodsByPlayer,
                Map<Long, Map<LocalDate, Map<GameType, ReportGameResult>>> resultsByPlayer) {
            this.periodsByPlayer = periodsByPlayer;
            this.resultsByPlayer = resultsByPlayer;
        }

        static HistoricalDays from(ReportStreakHistory history, LocalDate cutoff) {
            Map<Long, List<GameParticipationPeriod>> periods = new HashMap<>();
            history.participationPeriods().stream()
                    .filter(period -> !period.activeFrom().isAfter(cutoff))
                    .forEach(period -> periods.computeIfAbsent(period.playerId(), ignored -> new ArrayList<>()).add(period));
            periods.values().forEach(playerPeriods -> playerPeriods.sort(
                    Comparator.comparing(GameParticipationPeriod::activeFrom)
                            .thenComparing(GameParticipationPeriod::gameType)));

            Map<Long, Map<LocalDate, Map<GameType, ReportGameResult>>> results = new HashMap<>();
            history.results().stream()
                    .filter(result -> !result.gameDate().isAfter(cutoff))
                    .forEach(result -> results
                            .computeIfAbsent(result.playerId(), ignored -> new HashMap<>())
                            .computeIfAbsent(result.gameDate(), ignored -> new HashMap<>())
                            .put(result.gameType(), result));
            return new HistoricalDays(periods, results);
        }

        java.util.Optional<LocalDate> firstParticipationDay(long playerId) {
            return periodsByPlayer.getOrDefault(playerId, List.of()).stream()
                    .map(GameParticipationPeriod::activeFrom)
                    .min(LocalDate::compareTo);
        }

        java.util.Optional<LocalDate> firstParticipationDay() {
            return periodsByPlayer.values().stream()
                    .flatMap(List::stream)
                    .map(GameParticipationPeriod::activeFrom)
                    .min(LocalDate::compareTo);
        }

        boolean activity(long playerId, LocalDate day) {
            return participatesInAnyGame(playerId, day) && games(playerId, day).keySet().stream()
                    .anyMatch(gameType -> participates(playerId, gameType, day));
        }

        boolean complete(long playerId, LocalDate day) {
            Map<GameType, ReportGameResult> games = games(playerId, day);
            return participatesInBothGames(playerId, day)
                    && games.containsKey(GameType.GRIDWORDS)
                    && games.containsKey(GameType.QUADWORDS);
        }

        boolean solved(long playerId, LocalDate day, GameType gameType) {
            ReportGameResult result = games(playerId, day).get(gameType);
            return participates(playerId, gameType, day)
                    && result != null
                    && result.outcome() instanceof ShareOutcome.Solved;
        }

        boolean perfect(long playerId, LocalDate day) {
            return complete(playerId, day) && games(playerId, day).values().stream()
                    .allMatch(result -> result.outcome() instanceof ShareOutcome.Solved);
        }

        boolean sharedComplete(LocalDate day) {
            List<Long> bothGamesPlayerIds = bothGamesPlayerIds(day);
            return bothGamesPlayerIds.size() >= 2
                    && bothGamesPlayerIds.stream().allMatch(playerId -> complete(playerId, day));
        }

        boolean sharedPerfect(LocalDate day) {
            List<Long> bothGamesPlayerIds = bothGamesPlayerIds(day);
            return bothGamesPlayerIds.size() >= 2
                    && bothGamesPlayerIds.stream().allMatch(playerId -> perfect(playerId, day));
        }

        private boolean participates(long playerId, GameType gameType, LocalDate day) {
            return periodsByPlayer.getOrDefault(playerId, List.of()).stream()
                    .anyMatch(period -> period.gameType() == gameType && period.contains(day));
        }

        private boolean participatesInAnyGame(long playerId, LocalDate day) {
            return participates(playerId, GameType.GRIDWORDS, day)
                    || participates(playerId, GameType.QUADWORDS, day);
        }

        private boolean participatesInBothGames(long playerId, LocalDate day) {
            return participates(playerId, GameType.GRIDWORDS, day)
                    && participates(playerId, GameType.QUADWORDS, day);
        }

        private List<Long> bothGamesPlayerIds(LocalDate day) {
            return periodsByPlayer.entrySet().stream()
                    .filter(entry -> participatesInBothGames(entry.getKey(), day))
                    .map(Map.Entry::getKey)
                    .toList();
        }

        private Map<GameType, ReportGameResult> games(long playerId, LocalDate day) {
            return resultsByPlayer.getOrDefault(playerId, Map.of()).getOrDefault(day, Map.of());
        }
    }
}
