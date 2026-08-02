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
import org.junit.jupiter.api.Test;

class DailyResultDetailsServiceTest {
    private static final LocalDate DATE = LocalDate.of(2026, 8, 3);
    @Test void returnsCurrentResultOnlyAfterPageValidation() {
        var resultQuery = (DailyResultDetailsQuery) (player, type, date) -> Optional.of(grid());
        var service = service(List.of(new DailyStatusInteractionContextQuery.Participant(2L, "B"), new DailyStatusInteractionContextQuery.Participant(1L, "a")), resultQuery);
        var result = service.get(request(0, 1));
        assertThat(result).isInstanceOf(DailyResultDetailsUseCase.Found.class);
        assertThat(((DailyResultDetailsUseCase.Found) result).playerDisplayName()).isEqualTo("a");
    }
    @Test void rejectsOldStatusAndCopiedOptionFromAnotherPageWithoutReadingResult() {
        var never = (DailyResultDetailsQuery) (player, type, date) -> { throw new AssertionError("must stay read-only and unqueried"); };
        var old = new DailyResultDetailsService((g, c, m, d) -> Optional.empty(), never).get(request(0, 1));
        var players = java.util.stream.LongStream.rangeClosed(1, 26).mapToObj(id -> new DailyStatusInteractionContextQuery.Participant(id, String.format("P%02d", id))).toList();
        var copied = service(players, never).get(request(0, 26));
        assertThat(old).isEqualTo(new DailyResultDetailsUseCase.Rejected(DailyResultDetailsUseCase.Reason.STATUS_NOT_CURRENT));
        assertThat(copied).isEqualTo(new DailyResultDetailsUseCase.Rejected(DailyResultDetailsUseCase.Reason.TARGET_NOT_ON_PAGE));
    }
    @Test void representsMissingResultWithoutMutation() {
        var result = service(List.of(new DailyStatusInteractionContextQuery.Participant(1, "A")), (p,t,d) -> Optional.empty()).get(request(0, 1));
        assertThat(result).isEqualTo(new DailyResultDetailsUseCase.Missing("A", GameType.GRIDWORDS, DATE));
    }
    private static DailyResultDetailsService service(List<DailyStatusInteractionContextQuery.Participant> players, DailyResultDetailsQuery results) { return new DailyResultDetailsService((g,c,m,d) -> Optional.of(new DailyStatusInteractionContextQuery.Context(players)), results); }
    private static DailyResultDetailsUseCase.Request request(int page, long player) { return new DailyResultDetailsUseCase.Request(1, 2, 3, DATE, GameType.GRIDWORDS, page, player); }
    private static ParsedGameResult grid() { return new ParsedGameResult(GameType.GRIDWORDS, DATE, new ShareOutcome.Solved(1, 6), Duration.ZERO, OptionalInt.empty(), Optional.of(new NormalizedBoard(List.of("⬜⬜⬜⬜⬜")))); }
}