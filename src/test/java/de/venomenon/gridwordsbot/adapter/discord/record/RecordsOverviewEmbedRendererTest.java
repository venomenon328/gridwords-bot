package de.venomenon.gridwordsbot.adapter.discord.record;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.DurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.StreakRecordMetric;
import de.venomenon.gridwordsbot.domain.record.StreakRecordValue;
import de.venomenon.gridwordsbot.port.in.RecordsQueryUseCase;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecordsOverviewEmbedRendererTest {
    private final RecordsOverviewEmbedRenderer renderer = new RecordsOverviewEmbedRenderer();

    @Test
    void rendersUnavailableForbiddenAndReadyEmptyStatesNeutrally() {
        assertThat(renderer.render(new RecordsQueryUseCase.Unavailable(), "Ada").getFirst().getDescription())
                .contains("noch nicht vollständig initialisiert");
        assertThat(renderer.render(new RecordsQueryUseCase.Forbidden(), "Ada").getFirst().getDescription())
                .contains("nur Administratoren");
        assertThat(renderer.render(new RecordsQueryUseCase.Ready(List.of()), "Ada").getFirst().getDescription())
                .contains("Keine Rekorde");
    }

    @Test
    void rendersSharedStateWithoutIndividualOwnershipAndNeutralizesNames() {
        StreakRecordValue value = new StreakRecordValue(12, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 12));
        RecordsQueryUseCase.Entry shared = new RecordsQueryUseCase.Entry(
                "streak.gridwords-solved.shared", "gridwords-solved", Optional.of(GameType.GRIDWORDS),
                RecordsQueryUseCase.Category.SERIES, RecordsQueryUseCase.Scope.SHARED,
                Optional.empty(), Optional.of(value),
                Optional.of(new RecordSourceReference.StreakRun(StreakRecordMetric.GRIDWORDS_SOLVED,
                        new RecordSourceReference.StreakRunOwner.Shared(), value.startDate())), false);

        var embed = renderer.render(new RecordsQueryUseCase.Ready(List.of(shared)), "<@123> Ada").getFirst();

        assertThat(embed.getDescription()).contains("Halter: gemeinsam", "12 Tage", "01.07.2026–12.07.2026")
                .doesNotContain("<@", "@123");
        assertThat(embed.getFooter().getText()).contains("Ada").doesNotContain("@", "<", ">");
    }

    @Test
    void rendersServerHolderGameDayRunningSeriesAndAttemptsTieBreakContext() {
        RecordsQueryUseCase.Entry result = new RecordsQueryUseCase.Entry(
                "result.quadwords.fewest-attempts.server-individual", "fewest-attempts",
                Optional.of(GameType.QUADWORDS), RecordsQueryUseCase.Category.RESULTS,
                RecordsQueryUseCase.Scope.SERVER_INDIVIDUAL, Optional.of("Georgia"),
                Optional.of(new AttemptsDurationRecordValue(2, Duration.ofSeconds(90))),
                Optional.of(new RecordSourceReference.GameResult(
                        1, 0, 99, GameType.QUADWORDS, LocalDate.of(2026, 8, 6))), false);
        StreakRecordValue streakValue = new StreakRecordValue(
                8, LocalDate.of(2026, 7, 30), LocalDate.of(2026, 8, 6));
        RecordsQueryUseCase.Entry running = new RecordsQueryUseCase.Entry(
                "streak.activity.server-individual", "activity", Optional.empty(),
                RecordsQueryUseCase.Category.SERIES, RecordsQueryUseCase.Scope.SERVER_INDIVIDUAL,
                Optional.of("Georgia"), Optional.of(streakValue),
                Optional.of(new RecordSourceReference.StreakRun(StreakRecordMetric.ACTIVITY,
                        new RecordSourceReference.StreakRunOwner.Player(99), streakValue.startDate())), true);

        String description = renderer.render(new RecordsQueryUseCase.Ready(List.of(result, running)), "Ada")
                .getFirst().getDescription();

        assertThat(description).contains(
                "QuadWords · Wenigste Versuche", "2 Versuche · 1:30", "Halter: Georgia", "Spieltag 06.08.2026",
                "Aktivitätsserie", "30.07.2026–06.08.2026", "läuft");
    }

    @Test
    void largeOutputIsDeterministicallyPagedWithinDiscordDescriptionLimit() {
        List<RecordsQueryUseCase.Entry> entries = new ArrayList<>();
        for (int index = 0; index < 80; index++) {
            entries.add(new RecordsQueryUseCase.Entry(
                    "result.gridwords.fastest-solution.personal." + index, "fastest-solution",
                    Optional.of(GameType.GRIDWORDS), RecordsQueryUseCase.Category.RESULTS,
                    RecordsQueryUseCase.Scope.PERSONAL, Optional.of("Spieler-" + index + "-" + "x".repeat(40)),
                    Optional.of(new DurationRecordValue(Duration.ofSeconds(60 + index))),
                    Optional.of(new RecordSourceReference.GameResult(
                            index + 1L, 0, index + 1L, GameType.GRIDWORDS, LocalDate.of(2026, 8, 1))), false));
        }

        var first = renderer.render(new RecordsQueryUseCase.Ready(entries), "Ada");
        var second = renderer.render(new RecordsQueryUseCase.Ready(entries), "Ada");

        assertThat(first).hasSizeGreaterThan(1);
        assertThat(first).allSatisfy(embed -> assertThat(embed.getDescription()).hasSizeLessThanOrEqualTo(3_800));
        assertThat(first.stream().map(embed -> embed.getTitle() + "\n" + embed.getDescription()).toList())
                .isEqualTo(second.stream().map(embed -> embed.getTitle() + "\n" + embed.getDescription()).toList());
    }
}
