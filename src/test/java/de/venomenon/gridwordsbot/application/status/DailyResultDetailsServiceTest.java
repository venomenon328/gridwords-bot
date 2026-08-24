package de.venomenon.gridwordsbot.application.status;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.achievement.AchievementDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.model.NormalizedBoard;
import de.venomenon.gridwordsbot.domain.model.ParsedGameResult;
import de.venomenon.gridwordsbot.domain.model.ShareOutcome;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordScopeType;
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
        var resultQuery = (DailyResultDetailsQuery) (guild, player, type, date, version) -> Optional.of(details());
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
        var old = service((g, c, m, d) -> Optional.empty(), never)
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
        DailyResultDetailsQuery query = (guild, player, type, date, version) -> {
            resultReads.incrementAndGet();
            return Optional.of(details());
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
                (g, p, t, d, version) -> Optional.empty())
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
                results, RecordDefinitionCatalog.recordsV1(), AchievementDefinitionCatalog.achievementsV2(), key -> Optional.empty());
    }

    @Test
    void derivesOptionalDetailBlocksOnlyFromTheProvidedReadProjection() {
        DailyResultDetailsQuery projection = (guild, player, type, date, version) -> Optional.of(
                new DailyResultDetailsQuery.Details(42L, grid(), Optional.of("Genau so gespeichert."), List.of(
                        new DailyResultDetailsQuery.CurrentRecord(
                                "result.gridwords.fewest-attempts.personal", RecordScopeType.PERSONAL),
                        new DailyResultDetailsQuery.CurrentRecord("streak.activity.personal", RecordScopeType.PERSONAL)),
                        List.of("participation.1.gridwords", "situational.crossgame.perfect_double")));
        DailyResultDetailsService service = new DailyResultDetailsService(
                (g, c, m, d) -> Optional.of(new DailyStatusInteractionContextQuery.Context(
                        List.of(participant(1L, "A")), List.of())), projection,
                RecordDefinitionCatalog.recordsV1(), AchievementDefinitionCatalog.achievementsV2(),
                key -> key.value().equals("participation.1.gridwords") ? Optional.of("<:wave:1>") : Optional.empty());

        DailyResultDetailsUseCase.Found found = (DailyResultDetailsUseCase.Found) service.get(request(GameType.GRIDWORDS, 0, 1));

        assertThat(found.selectedExcuse()).contains("Genau so gespeichert.");
        assertThat(found.currentRecords()).containsExactly(new DailyResultDetailsUseCase.CurrentRecord(
                "Wenigste Versuche", "Persönlich"));
        assertThat(found.achievements()).containsExactly(
                new DailyResultDetailsUseCase.Achievement("<:wave:1>", "GW: Dabei!"),
                new DailyResultDetailsUseCase.Achievement("✨", "GW+QW: Perfekter Doppelschlag"));
    }

    private static DailyResultDetailsService service(
            DailyStatusInteractionContextQuery contexts, DailyResultDetailsQuery results) {
        return new DailyResultDetailsService(contexts, results, RecordDefinitionCatalog.recordsV1(),
                AchievementDefinitionCatalog.achievementsV2(), key -> Optional.empty());
    }

    private static DailyStatusInteractionContextQuery.Participant participant(long id, String name) {
        return new DailyStatusInteractionContextQuery.Participant(id, name);
    }

    private static DailyResultDetailsQuery neverRead() {
        return (guild, player, type, date, version) -> {
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

    private static DailyResultDetailsQuery.Details details() {
        return new DailyResultDetailsQuery.Details(1L, grid(), Optional.empty(), List.of(), List.of());
    }
}
