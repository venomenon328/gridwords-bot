package de.venomenon.gridwordsbot.application.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.venomenon.gridwordsbot.domain.model.GameParticipationPeriod;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.status.DailyStatus;
import de.venomenon.gridwordsbot.port.out.GameResultStore;
import de.venomenon.gridwordsbot.port.out.PlayerStore;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class DailyStatusProjectorTest {
    private static final LocalDate DATE = LocalDate.of(2026, 7, 30);

    @Test
    void currentDayKeepsPendingSharedStreaksButHistoricalFinalizationEndsThem() {
        Fixtures fixtures = fixtures(List.of(1L, 2L), DATE.minusDays(10));
        fixtures.complete(1L, DATE.minusDays(1), true);
        fixtures.complete(2L, DATE.minusDays(1), true);

        DailyStatus provisional = fixtures.projector().project(DATE, DATE);
        DailyStatus historical = fixtures.projector().project(DATE, DATE.plusDays(1));

        assertThat(provisional.players().getFirst().streaks().personalComplete()).isEqualTo(1);
        assertThat(provisional.sharedGridWordsSolved()).isEqualTo(1);
        assertThat(provisional.sharedQuadWordsSolved()).isEqualTo(1);
        assertThat(provisional.sharedComplete()).isEqualTo(1);
        assertThat(provisional.sharedPerfect()).isEqualTo(1);
        assertThat(historical.players().getFirst().streaks().personalActivity()).isZero();
        assertThat(historical.players().getFirst().streaks().personalComplete()).isZero();
        assertThat(historical.players().getFirst().streaks().personalGridWordsSolved()).isZero();
        assertThat(historical.players().getFirst().streaks().personalQuadWordsSolved()).isZero();
        assertThat(historical.players().getFirst().streaks().personalPerfect()).isZero();
        assertThat(historical.sharedGridWordsSolved()).isZero();
        assertThat(historical.sharedQuadWordsSolved()).isZero();
        assertThat(historical.sharedComplete()).isZero();
        assertThat(historical.sharedPerfect()).isZero();
    }

    @Test
    void historicalProjectionUsesTheDisplayedDateAndIgnoresLaterResults() {
        Fixtures fixtures = fixtures(List.of(1L, 2L), DATE.minusDays(10));
        fixtures.complete(1L, DATE, true);
        fixtures.complete(2L, DATE, true);
        fixtures.add(1L, DATE.plusDays(1), GameType.GRIDWORDS, false);

        DailyStatus status = fixtures.projector().project(DATE, DATE.plusDays(1));

        assertThat(status.players().getFirst().streaks().personalComplete()).isEqualTo(1);
        assertThat(status.players().getFirst().streaks().personalPerfect()).isEqualTo(1);
        assertThat(status.sharedGridWordsSolved()).isEqualTo(1);
        assertThat(status.sharedQuadWordsSolved()).isEqualTo(1);
        assertThat(status.sharedComplete()).isEqualTo(1);
        assertThat(status.sharedPerfect()).isEqualTo(1);
    }

    @Test
    void showsOnlyDateParticipantsAndSortsUnicodeNamesThenIds() {
        Fixtures fixtures = fixtures(List.of(), DATE);
        fixtures.player(3L, "zoe", both(3L, DATE, null));
        fixtures.player(2L, "Änne", both(2L, DATE, null));
        fixtures.player(1L, "änne", both(1L, DATE, null));
        fixtures.player(4L, "Inactive", both(4L, DATE.minusDays(4), DATE));

        DailyStatus status = fixtures.projector().project(DATE, DATE);

        assertThat(status.players())
                .extracting(DailyStatus.PlayerLine::discordUserId)
                .containsExactly(3L, 1L, 2L);
    }

    @Test
    void yesterdayBackfillReconstructsPersonalAndAllSharedStreaks() {
        Fixtures fixtures = fixtures(List.of(1L, 2L, 3L), DATE.minusDays(20));
        for (long player : List.of(1L, 2L, 3L)) {
            fixtures.complete(player, DATE.minusDays(1), true);
            fixtures.complete(player, DATE, true);
        }

        DailyStatus status = fixtures.projector().project(DATE, DATE.plusDays(1));

        assertThat(status.players()).allSatisfy(player -> {
            assertThat(player.streaks().personalActivity()).isEqualTo(2);
            assertThat(player.streaks().personalComplete()).isEqualTo(2);
            assertThat(player.streaks().personalGridWordsSolved()).isEqualTo(2);
            assertThat(player.streaks().personalQuadWordsSolved()).isEqualTo(2);
            assertThat(player.streaks().personalPerfect()).isEqualTo(2);
        });
        assertThat(status.sharedGridWordsSolved()).isEqualTo(2);
        assertThat(status.sharedQuadWordsSolved()).isEqualTo(2);
        assertThat(status.sharedComplete()).isEqualTo(2);
        assertThat(status.sharedPerfect()).isEqualTo(2);
    }

    @Test
    void oneGamesUnsolvedResultChangesOnlyItsSharedSolvedStreakAndSharedPerfect() {
        Fixtures fixtures = fixtures(List.of(1L, 2L), DATE.minusDays(10));
        fixtures.complete(1L, DATE.minusDays(1), true);
        fixtures.complete(2L, DATE.minusDays(1), true);
        fixtures.complete(1L, DATE, true);
        fixtures.add(2L, DATE, GameType.GRIDWORDS, false);
        fixtures.add(2L, DATE, GameType.QUADWORDS, true);

        DailyStatus status = fixtures.projector().project(DATE, DATE);

        assertThat(status.sharedGridWordsSolved()).isZero();
        assertThat(status.sharedQuadWordsSolved()).isEqualTo(2);
        assertThat(status.sharedComplete()).isEqualTo(2);
        assertThat(status.sharedPerfect()).isZero();
    }

    @Test
    void projectsUnionParticipantsAndCalculatesStreaksFromTheirGameSpecificHistory() {
        Fixtures fixtures = fixtures(List.of(), DATE);
        fixtures.player(1L, "Grid switcher",
                new GameParticipationPeriod(1L, GameType.GRIDWORDS, DATE.minusDays(1), null),
                new GameParticipationPeriod(1L, GameType.QUADWORDS, DATE.minusDays(1), DATE));
        fixtures.player(2L, "Grid only",
                new GameParticipationPeriod(2L, GameType.GRIDWORDS, DATE.minusDays(1), null));
        fixtures.player(3L, "Quad only",
                new GameParticipationPeriod(3L, GameType.QUADWORDS, DATE, null));
        fixtures.complete(1L, DATE.minusDays(1), true);
        fixtures.add(1L, DATE, GameType.GRIDWORDS, true);
        fixtures.add(2L, DATE, GameType.GRIDWORDS, true);
        fixtures.add(3L, DATE, GameType.QUADWORDS, true);

        DailyStatus status = fixtures.projector().project(DATE, DATE);

        assertThat(status.players()).extracting(DailyStatus.PlayerLine::discordUserId)
                .containsExactly(2L, 1L, 3L);
        DailyStatus.PlayerLine switcher = status.players().stream()
                .filter(player -> player.discordUserId() == 1L)
                .findFirst()
                .orElseThrow();
        assertThat(switcher.participates(GameType.GRIDWORDS)).isTrue();
        assertThat(switcher.participates(GameType.QUADWORDS)).isFalse();
        assertThat(switcher.gridWords()).isPresent();
        assertThat(switcher.quadWords()).isEmpty();
        assertThat(switcher.streaks().personalActivity()).isEqualTo(2);
        assertThat(switcher.streaks().personalGridWordsSolved()).isEqualTo(2);
        assertThat(switcher.streaks().personalQuadWordsSolved()).isZero();
        assertThat(switcher.streaks().personalComplete()).isZero();
        assertThat(switcher.streaks().personalPerfect()).isZero();
        assertThat(status.sharedGridWordsSolved()).isEqualTo(1);
        assertThat(status.sharedQuadWordsSolved()).isZero();
        assertThat(status.sharedComplete()).isZero();
        assertThat(status.sharedPerfect()).isZero();
    }

    @Test
    void emptyParticipantProjectionUsesZeroForEverySharedStreak() {
        DailyStatus status = fixtures(List.of(), DATE).projector().project(DATE, DATE);

        assertThat(status.players()).isEmpty();
        assertThat(status.sharedGridWordsSolved()).isZero();
        assertThat(status.sharedQuadWordsSolved()).isZero();
        assertThat(status.sharedComplete()).isZero();
        assertThat(status.sharedPerfect()).isZero();
    }

    private static Fixtures fixtures(List<Long> ids, LocalDate activeFrom) {
        Fixtures fixtures = new Fixtures();
        ids.forEach(id -> fixtures.player(id, "Player " + id, both(id, activeFrom, null)));
        return fixtures;
    }

    private static GameParticipationPeriod[] both(long id, LocalDate activeFrom, LocalDate inactiveFrom) {
        return new GameParticipationPeriod[] {
                new GameParticipationPeriod(id, GameType.GRIDWORDS, activeFrom, inactiveFrom),
                new GameParticipationPeriod(id, GameType.QUADWORDS, activeFrom, inactiveFrom)
        };
    }

    private static final class Fixtures {
        private final List<GameResultStore.StoredGameResult> results = new ArrayList<>();
        private final List<PlayerStore.StoredPlayer> players = new ArrayList<>();
        private final List<GameParticipationPeriod> periods = new ArrayList<>();

        void player(long id, String name, GameParticipationPeriod... participationPeriods) {
            players.add(new PlayerStore.StoredPlayer(
                    id, name, true, false, false, Instant.EPOCH, Instant.EPOCH));
            periods.addAll(List.of(participationPeriods));
        }

        void complete(long id, LocalDate date, boolean solved) {
            add(id, date, GameType.GRIDWORDS, solved);
            add(id, date, GameType.QUADWORDS, solved);
        }

        void add(long id, LocalDate date, GameType type, boolean solved) {
            int maximum = type == GameType.GRIDWORDS ? 6 : 9;
            ShareOutcome outcome = solved
                    ? new ShareOutcome.Solved(1, maximum)
                    : new ShareOutcome.Unsolved(maximum);
            Optional<NormalizedBoard> board = type == GameType.GRIDWORDS
                    ? Optional.of(new NormalizedBoard(
                            Collections.nCopies(solved ? 1 : 6, "⬜⬜⬜⬜⬜")))
                    : Optional.empty();
            ParsedGameResult parsed = new ParsedGameResult(
                    type, date, outcome, Duration.ofSeconds(61), OptionalInt.empty(), board);
            results.add(new GameResultStore.StoredGameResult(
                    results.size() + 1L,
                    id,
                    parsed,
                    "share",
                    "parser",
                    OptionalLong.empty(),
                    Instant.EPOCH,
                    Instant.EPOCH));
        }

        DailyStatusProjector projector() {
            GameResultStore resultStore = mock(GameResultStore.class);
            PlayerStore playerStore = mock(PlayerStore.class);
            when(resultStore.findAll()).thenReturn(List.copyOf(results));
            when(playerStore.findAllPlayers()).thenReturn(List.copyOf(players));
            when(playerStore.findGameParticipationPeriods()).thenReturn(List.copyOf(periods));
            return new DailyStatusProjector(resultStore, playerStore);
        }
    }
}
