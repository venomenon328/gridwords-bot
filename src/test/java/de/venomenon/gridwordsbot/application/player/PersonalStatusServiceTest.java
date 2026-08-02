package de.venomenon.gridwordsbot.application.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.ParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.in.PersonalStatusUseCase.ParticipationStatus;
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
import java.util.Set;
import org.junit.jupiter.api.Test;

class PersonalStatusServiceTest {
    private static final long PLAYER = 42L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-29T10:00:00Z"), ZoneOffset.UTC);
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void projectsTheCurrentPeriodReminderPreferenceAndBothLatestResultsInOneQuery() {
        PlayerStore players = mock(PlayerStore.class);
        LatestValidSubmissionQuery submissions = mock(LatestValidSubmissionQuery.class);
        when(players.synchronizeProfile(any())).thenReturn(player(true, true));
        when(players.findParticipationPeriod(PLAYER, LocalDate.of(2026, 7, 29))).thenReturn(Optional.of(
                new ParticipationPeriod(PLAYER, LocalDate.of(2026, 7, 27), LocalDate.of(2026, 7, 31))));
        when(submissions.findLatestValidSubmissions(PLAYER)).thenReturn(List.of(
                latest(GameType.GRIDWORDS, new ShareOutcome.Solved(4, 6), "2026-07-28T21:15:00Z"),
                latest(GameType.QUADWORDS, new ShareOutcome.Unsolved(9), "2026-07-29T07:30:00Z")));

        var status = service(players, submissions).status(new PlayerIdentity(PLAYER, "Player"));

        assertThat(status.participation().active()).isTrue();
        assertThat(status.participation().activeFrom()).contains(LocalDate.of(2026, 7, 27));
        assertThat(status.participation().activeUntil()).contains(LocalDate.of(2026, 7, 30));
        assertThat(status.reminderOptIn()).isTrue();
        assertThat(status.latestGridWordsSubmission()).hasValueSatisfying(result -> {
            assertThat(result.outcome()).isEqualTo(new ShareOutcome.Solved(4, 6));
            assertThat(result.gameDate()).isEqualTo(LocalDate.of(2026, 7, 28));
            assertThat(result.receivedAt()).isEqualTo(Instant.parse("2026-07-28T21:15:00Z"));
        });
        assertThat(status.latestQuadWordsSubmission()).hasValueSatisfying(result ->
                assertThat(result.outcome()).isEqualTo(new ShareOutcome.Unsolved(9)));
        verify(players).synchronizeProfile(new PlayerStore.ProfileUpdate(PLAYER, "Player", false));
        verify(submissions, times(1)).findLatestValidSubmissions(PLAYER);
    }

    @Test
    void projectsAnInactiveProfileWithoutCurrentParticipationOrSubmissions() {
        PlayerStore players = mock(PlayerStore.class);
        LatestValidSubmissionQuery submissions = mock(LatestValidSubmissionQuery.class);
        when(players.synchronizeProfile(any())).thenReturn(player(false, false));
        when(players.findParticipationPeriod(PLAYER, LocalDate.of(2026, 7, 29))).thenReturn(Optional.empty());
        when(submissions.findLatestValidSubmissions(PLAYER)).thenReturn(List.of());

        var status = service(players, submissions).status(new PlayerIdentity(PLAYER, "Player"));

        assertThat(status.participation()).isEqualTo(
                new ParticipationStatus(false, Optional.empty(), Optional.empty()));
        assertThat(status.reminderOptIn()).isFalse();
        assertThat(status.latestGridWordsSubmission()).isEmpty();
        assertThat(status.latestQuadWordsSubmission()).isEmpty();
    }

    @Test
    void rejectsAnInvalidQueryThatReturnsMultipleLatestResultsForOneGame() {
        PlayerStore players = mock(PlayerStore.class);
        LatestValidSubmissionQuery submissions = mock(LatestValidSubmissionQuery.class);
        when(players.synchronizeProfile(any())).thenReturn(player(true, false));
        when(players.findParticipationPeriod(PLAYER, LocalDate.of(2026, 7, 29))).thenReturn(Optional.of(
                new ParticipationPeriod(PLAYER, LocalDate.of(2026, 7, 29), null)));
        when(submissions.findLatestValidSubmissions(PLAYER)).thenReturn(List.of(
                latest(GameType.GRIDWORDS, new ShareOutcome.Solved(4, 6), "2026-07-29T08:00:00Z"),
                latest(GameType.GRIDWORDS, new ShareOutcome.Solved(5, 6), "2026-07-29T08:01:00Z")));

        assertThatThrownBy(() -> service(players, submissions).status(new PlayerIdentity(PLAYER, "Player")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate game type GRIDWORDS");
    }

    private static PersonalStatusService service(PlayerStore players, LatestValidSubmissionQuery submissions) {
        return new PersonalStatusService(players, submissions, CLOCK, BERLIN, Set.of(7L));
    }

    private static PlayerStore.StoredPlayer player(boolean active, boolean reminderOptIn) {
        return new PlayerStore.StoredPlayer(
                PLAYER, "Player", active, false, reminderOptIn, Instant.EPOCH, Instant.EPOCH);
    }

    private static LatestValidSubmissionQuery.LatestValidSubmission latest(
            GameType gameType, ShareOutcome outcome, String receivedAt) {
        return new LatestValidSubmissionQuery.LatestValidSubmission(
                gameType,
                outcome,
                Duration.ofSeconds(125),
                LocalDate.parse(receivedAt.substring(0, 10)),
                Instant.parse(receivedAt));
    }
}
