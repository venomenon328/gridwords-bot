package de.venomenon.gridwordsbot.application.reporting;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.reporting.ReportGameResult;
import de.venomenon.gridwordsbot.domain.reporting.ReportGameStatistics;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipant;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipantBasis;
import de.venomenon.gridwordsbot.domain.reporting.ReportPlayerGameStatistics;
import de.venomenon.gridwordsbot.domain.reporting.ReportRatio;
import de.venomenon.gridwordsbot.port.out.ReportGameResultQuery;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Calculates independent per-game statistics from valid result facts and individual participation days. */
public final class ReportGameStatisticsProjector {
    private final ReportGameResultQuery results;

    public ReportGameStatisticsProjector(ReportGameResultQuery results) {
        this.results = results;
    }

    public List<ReportPlayerGameStatistics> project(ReportParticipantBasis participants) {
        Set<Long> participantIds = participants.participants().stream()
                .map(ReportParticipant::discordUserId)
                .collect(Collectors.toUnmodifiableSet());
        Map<Long, List<ReportGameResult>> resultsByPlayer = results.findResults(participants.period(), participantIds).stream()
                .filter(result -> participantIds.contains(result.playerId()))
                .collect(Collectors.groupingBy(ReportGameResult::playerId));
        return participants.participants().stream()
                .map(participant -> projectParticipant(participant, resultsByPlayer.getOrDefault(participant.discordUserId(), List.of())))
                .toList();
    }

    private static ReportPlayerGameStatistics projectParticipant(ReportParticipant participant, List<ReportGameResult> results) {
        return new ReportPlayerGameStatistics(participant.discordUserId(),
                gameStatistics(GameType.GRIDWORDS, Set.copyOf(participant.gridWordsParticipationDays()), results),
                gameStatistics(GameType.QUADWORDS, Set.copyOf(participant.quadWordsParticipationDays()), results));
    }

    private static ReportGameStatistics gameStatistics(
            GameType gameType, Set<LocalDate> participationDays, List<ReportGameResult> results) {
        List<ReportGameResult> submittedResults = results.stream()
                .filter(result -> result.gameType() == gameType && participationDays.contains(result.gameDate()))
                .toList();
        List<ReportGameResult> solvedResults = submittedResults.stream()
                .filter(result -> result.outcome() instanceof ShareOutcome.Solved)
                .toList();
        int submitted = submittedResults.size();
        int solved = solvedResults.size();
        int possibleDays = participationDays.size();
        Duration solvedDurationTotal = solvedResults.stream()
                .map(ReportGameResult::duration)
                .reduce(Duration.ZERO, Duration::plus);
        Optional<Duration> bestSolvedDuration = solvedResults.stream()
                .map(ReportGameResult::duration)
                .min(Duration::compareTo);
        long solvedAttemptsTotal = solvedResults.stream()
                .map(ReportGameResult::outcome)
                .map(ShareOutcome.Solved.class::cast)
                .mapToLong(ShareOutcome.Solved::attemptsUsed)
                .sum();
        return new ReportGameStatistics(
                gameType, possibleDays, submitted, solved, submitted - solved, possibleDays - submitted,
                submitted == 0 ? Optional.empty() : Optional.of(new ReportRatio(solved, submitted)),
                solvedAttemptsTotal, solved, solvedDurationTotal, solved, bestSolvedDuration);
    }
}
