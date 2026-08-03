package de.venomenon.gridwordsbot.application.status;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.port.in.DailyResultDetailsUseCase;
import de.venomenon.gridwordsbot.port.out.DailyResultDetailsQuery;
import de.venomenon.gridwordsbot.port.out.DailyStatusInteractionContextQuery;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class DailyResultDetailsServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 8, 3);

    @Test
    void returnsCurrentResultOnlyAfterGameSpecificPageValidation() {
        var resultQuery = (DailyResultDetailsQuery) (player, type, date) -> Optional.of(grid());
        var service = service(
                List.of(participant(2L, "B"), participant(1L, "a")),
                List.of(participant(1L, "a")),
                resultQuery);

        var result = service.get(request(GameType.GRIDWORDS, 0, 1));

        assertThat(result).isInstanceOf(DailyResultDetailsUseCase.Found.class);
        assertThat(((DailyResultDetailsUseCase.Found) result).playerDisplayName()).isEqualTo("a");
    }

    @Test
    void rejectsATargetWhoParticipatesOnlyInTheOtherGameWithoutReadingTheResult() {
        DailyResultDetailsQuery never = neverRead();
        var service = service(
                List.of(participant(1L, "Grid only")),
                List.of(participant(2L, "Quad only")),
                never);

        var result = service.get(request(GameType.GRIDWORDS, 0, 2));

        assertThat(result).isEqualTo(new DailyResultDetailsUseCase.Rejected(
                DailyResultDetailsUseCase.Reason.TARGET_NOT_PARTICIPATING));
    }

    @Test
    void rejectsOldStatusAndCopiedOptionFromAnotherPageWithoutReadingResult() {
        DailyResultDetailsQuery never = neverRead();
        var old = new DailyResultDetailsService((g, c, m, d) -> Optional.empty(), never)
                .get(request(GameType.GRIDWORDS, 0, 1));
        var copied = service(players(26), List.of(), never)
                .get(request(GameType.GRIDWORDS, 0, 26));

        assertThat(old).isEqualTo(new DailyResultDetailsUseCase.Rejected(
                DailyResultDetailsUseCase.Reason.STATUS_NOT_CURRENT));
        assertThat(copied).isEqualTo(new DailyResultDetailsUseCase.Rejected(
                DailyResultDetailsUseCase.Reason.TARGET_NOT_ON_PAGE));
    }

    @Test
    void rejectsUnknownTargetAndManipulatedPageBeforeReadingResult() {
        DailyResultDetailsQuery never = neverRead();
        var service = service(players(26), List.of(), never);

        assertThat(service.get(request(GameType.GRIDWORDS, 0, 99))).isEqualTo(new DailyResultDetailsUseCase.Rejected(
                DailyResultDetailsUseCase.Reason.TARGET_NOT_PARTICIPATING));
        assertThat(service.get(request(GameType.GRIDWORDS, 2, 26))).isEqualTo(new DailyResultDetailsUseCase.Rejected(
                DailyResultDetailsUseCase.Reason.PAGE_NOT_OFFERED));
    }

    @Test
    void rejectsAllPagesWhenOneGameExceedsTheDeliveredLimit() {
        AtomicInteger resultReads = new AtomicInteger();
        DailyResultDetailsQuery query = (player, type, date) -> {
            resultReads.incrementAndGet();
            return Optional.of(grid());
        };
        var service = service(players(51), List.of(participant(99, "Quad only")), query);

        var result = service.get(request(GameType.GRIDWORDS, 0, 1));

        assertThat(result).isEqualTo(new DailyResultDetailsUseCase.Rejected(
                DailyResultDetailsUseCase.Reason.PAGE_NOT_OFFERED));
        assertThat(resultReads).hasValue(0);
    }

    @Test
    void representsMissingResultForAParticipatingPlayerWithoutMutation() {
        var result = service(
                List.of(participant(1L, "A")),
                List.of(),
                (p, t, d) -> Optional.empty())
                .get(request(GameType.GRIDWORDS, 0, 1));

        assertThat(result).isEqualTo(new DailyResultDetailsUseCase.Missing("A", GameType.GRIDWORDS, DATE));
    }

    private static DailyResultDetailsService service(
            List<DailyStatusInteractionContextQuery.Participant> gridParticipants,
            List<DailyStatusInteractionContextQuery.Participant> quadParticipants,
            DailyResultDetailsQuery results) {
        return new DailyResultDetailsService(
                (g, c, m, d) -> Optional.of(new DailyStatusInteractionContextQuery.Context(
                        gridParticipants, quadParticipants)),
                results);
    }

    private static DailyStatusInteractionContextQuery.Participant participant(long id, String name) {
        return new DailyStatusInteractionContextQuery.Participant(id, name);
    }

    private static DailyResultDetailsQuery neverRead() {
        return (player, type, date) -> {
            throw new AssertionError("result query must not be invoked");
        };
    }

    private static List<DailyStatusInteractionContextQuery.Participant> players(int count) {
        return LongStream.rangeClosed(1, count)
                .mapToObj(id -> participant(id, String.format("P%02d", id)))
                .toList();
    }

    private static DailyResultDetailsUseCase.Request request(GameType gameType, int page, long player) {
        return new DailyResultDetailsUseCase.Request(1, 2, 3, DATE, gameType, page, player);
    }

    private static ParsedGameResult grid() {
        return new ParsedGameResult(
                GameType.GRIDWORDS,
                DATE,
                new ShareOutcome.Solved(1, 6),
                Duration.ZERO,
                OptionalInt.empty(),
                Optional.of(new NormalizedBoard(List.of("⬜⬜⬜⬜⬜"))));
    }
}