package de.venomenon.gridwordsbot.application.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.application.status.DailyStatusProjector;
import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.domain.streak.StreakSummary;
import de.venomenon.gridwordsbot.port.in.PersonalStatusUseCase.PlayerIdentity;
import de.venomenon.gridwordsbot.port.out.LatestValidSubmissionQuery;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class PersonalStatusServiceTest {
    private static final long PLAYER = 42L;
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 29);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC);
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void projectsTodayStreaksParticipationReminderAndLatestResultsWithoutProfileWrites() {
        PlayerStore players = mock(PlayerStore.class);
        LatestValidSubmissionQuery submissions = mock(LatestValidSubmissionQuery.class);
        DailyStatusProjector dailyStatus = mock(DailyStatusProjector.class);
        when(players.findAllPlayers()).thenReturn(List.of(player(true)));
        when(players.findGameParticipationPeriod(PLAYER, GameType.GRIDWORDS, TODAY)).thenReturn(Optional.of(
                new GameParticipationPeriod(PLAYER, GameType.GRIDWORDS, LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 31))));
        when(players.findGameParticipationPeriod(PLAYER, GameType.QUADWORDS, TODAY)).thenReturn(Optional.of(
                new GameParticipationPeriod(PLAYER, GameType.QUADWORDS, LocalDate.of(2026, 7, 28), null)));
        ParsedGameResult gridToday = grid(new ShareOutcome.Solved(3, 6), Duration.ofSeconds(85));
        when(dailyStatus.project(TODAY, TODAY)).thenReturn(new DailyStatus(
                TODAY,
                List.of(new DailyStatus.PlayerLine(
                        PLAYER,
                        "Stored Player",
                        new DailyStatus.GameState(GameType.GRIDWORDS, true, Optional.of(gridToday)),
                        new DailyStatus.GameState(GameType.QUADWORDS, true, Optional.empty()),
                        new StreakSummary(23, 7, 11, 4, 3, 0, 0))),
                0,
                0));
        when(submissions.findLatestValidSubmissions(PLAYER)).thenReturn(List.of(
                latest(GameType.GRIDWORDS, new ShareOutcome.Solved(4, 6), "2026-07-28T21:15:00Z"),
                latest(GameType.QUADWORDS, new ShareOutcome.Unsolved(9), "2026-07-29T07:30:00Z")));

        var status = service(players, submissions, dailyStatus).status(new PlayerIdentity(PLAYER, "Renamed in Discord"));

        assertThat(status.known()).isTrue();
        assertThat(status.gridWordsToday().outcome()).contains(new ShareOutcome.Solved(3, 6));
        assertThat(status.gridWordsToday().duration()).contains(Duration.ofSeconds(85));
        assertThat(status.quadWordsToday().participating()).isTrue();
        assertThat(status.quadWordsToday().outcome()).isEmpty();
        assertThat(status.streaks().activity()).hasValue(23);
        assertThat(status.streaks().complete()).hasValue(7);
        assertThat(status.streaks().gridWordsSolved()).hasValue(11);
        assertThat(status.streaks().quadWordsSolved()).hasValue(4);
        assertThat(status.streaks().perfect()).hasValue(3);
        assertThat(status.gridWordsParticipation().activeUntil()).contains(LocalDate.of(2026, 7, 30));
        assertThat(status.reminderOptIn()).isTrue();
        assertThat(status.latestQuadWordsSubmission()).hasValueSatisfying(result ->
                assertThat(result.outcome()).isEqualTo(new ShareOutcome.Unsolved(9)));
        verify(players, never()).synchronizeProfile(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void masksNonApplicableSeriesForSingleGameParticipation() {
        PlayerStore players = mock(PlayerStore.class);
        LatestValidSubmissionQuery submissions = mock(LatestValidSubmissionQuery.class);
        DailyStatusProjector dailyStatus = mock(DailyStatusProjector.class);
        when(players.findAllPlayers()).thenReturn(List.of(player(false)));
        when(players.findGameParticipationPeriod(PLAYER, GameType.GRIDWORDS, TODAY)).thenReturn(Optional.of(
                new GameParticipationPeriod(PLAYER, GameType.GRIDWORDS, TODAY.minusDays(3), null)));
        when(dailyStatus.project(TODAY, TODAY)).thenReturn(new DailyStatus(
                TODAY,
                List.of(new DailyStatus.PlayerLine(
                        PLAYER,
                        "Player",
                        new DailyStatus.GameState(GameType.GRIDWORDS, true, Optional.empty()),
                        new DailyStatus.GameState(GameType.QUADWORDS, false, Optional.empty()),
                        new StreakSummary(4, 0, 2, 0, 0, 0, 0))),
                0,
                0));
        when(submissions.findLatestValidSubmissions(PLAYER)).thenReturn(List.of());

        var status = service(players, submissions, dailyStatus).status(new PlayerIdentity(PLAYER, "Player"));

        assertThat(status.gridWordsToday().participating()).isTrue();
        assertThat(status.quadWordsToday().participating()).isFalse();
        assertThat(status.streaks().activity()).hasValue(4);
        assertThat(status.streaks().gridWordsSolved()).hasValue(2);
        assertThat(status.streaks().quadWordsSolved()).isEmpty();
        assertThat(status.streaks().complete()).isEmpty();
        assertThat(status.streaks().perfect()).isEmpty();
    }

    @Test
    void unknownCallerIsNotRegisteredAndDoesNotTriggerHistoryQueries() {
        PlayerStore players = mock(PlayerStore.class);
        LatestValidSubmissionQuery submissions = mock(LatestValidSubmissionQuery.class);
        DailyStatusProjector dailyStatus = mock(DailyStatusProjector.class);
        when(players.findAllPlayers()).thenReturn(List.of());

        var status = service(players, submissions, dailyStatus).status(new PlayerIdentity(PLAYER, "Unknown"));

        assertThat(status.known()).isFalse();
        assertThat(status.gridWordsToday().participating()).isFalse();
        assertThat(status.streaks().activity()).isEmpty();
        verify(players, never()).synchronizeProfile(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(submissions, dailyStatus);
    }

    @Test
    void rejectsAnInvalidQueryThatReturnsMultipleLatestResultsForOneGame() {
        PlayerStore players = mock(PlayerStore.class);
        LatestValidSubmissionQuery submissions = mock(LatestValidSubmissionQuery.class);
        DailyStatusProjector dailyStatus = mock(DailyStatusProjector.class);
        when(players.findAllPlayers()).thenReturn(List.of(player(false)));
        when(dailyStatus.project(TODAY, TODAY)).thenReturn(new DailyStatus(TODAY, List.of(), 0, 0));
        when(submissions.findLatestValidSubmissions(PLAYER)).thenReturn(List.of(
                latest(GameType.GRIDWORDS, new ShareOutcome.Solved(4, 6), "2026-07-29T08:00:00Z"),
                latest(GameType.GRIDWORDS, new ShareOutcome.Solved(5, 6), "2026-07-29T08:01:00Z")));

        assertThatThrownBy(() -> service(players, submissions, dailyStatus).status(new PlayerIdentity(PLAYER, "Player")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate game type GRIDWORDS");
    }

    private static PersonalStatusService service(
            PlayerStore players,
            LatestValidSubmissionQuery submissions,
            DailyStatusProjector dailyStatus) {
        return new PersonalStatusService(players, submissions, dailyStatus, CLOCK, BERLIN);
    }

    private static PlayerStore.StoredPlayer player(boolean reminderOptIn) {
        return new PlayerStore.StoredPlayer(
                PLAYER, "Stored Player", true, false, reminderOptIn, Instant.EPOCH, Instant.EPOCH);
    }

    private static ParsedGameResult grid(ShareOutcome outcome, Duration duration) {
        int rows = outcome instanceof ShareOutcome.Solved solved ? solved.attemptsUsed() : 6;
        return new ParsedGameResult(
                GameType.GRIDWORDS,
                TODAY,
                outcome,
                duration,
                OptionalInt.empty(),
                Optional.of(new NormalizedBoard(java.util.Collections.nCopies(rows, "⬜⬜⬜⬜⬜"))));
    }

    private static LatestValidSubmissionQuery.LatestValidSubmission latest(
            GameType gameType, ShareOutcome outcome, String receivedAt) {
        return new LatestValidSubmissionQuery.LatestValidSubmission(
                gameType, outcome, Duration.ofSeconds(125), LocalDate.parse(receivedAt.substring(0, 10)),
                Instant.parse(receivedAt));
    }
}
