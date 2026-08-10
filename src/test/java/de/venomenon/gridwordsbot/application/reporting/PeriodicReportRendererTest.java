package de.venomenon.gridwordsbot.application.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionKey;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionVersion;
import de.venomenon.gridwordsbot.domain.record.RecordEventDraft;
import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordEventType;
import de.venomenon.gridwordsbot.domain.record.RecordEventValidity;
import de.venomenon.gridwordsbot.domain.record.RecordProcessingOrigin;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordStateKey;
import de.venomenon.gridwordsbot.domain.record.StreakRecordMetric;
import de.venomenon.gridwordsbot.domain.record.StreakRecordValue;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReport;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportParticipantSection;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportSharedSection;
import de.venomenon.gridwordsbot.domain.reporting.RenderedPeriodicReport;
import de.venomenon.gridwordsbot.domain.reporting.RenderedReportField;
import de.venomenon.gridwordsbot.domain.reporting.RenderedReportPage;
import de.venomenon.gridwordsbot.domain.reporting.ReportGameStatistics;
import de.venomenon.gridwordsbot.domain.reporting.ReportHighlightFacts;
import de.venomenon.gridwordsbot.domain.reporting.ReportParticipant;
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
import de.venomenon.gridwordsbot.domain.reporting.ReportPersonalDayCounts;
import de.venomenon.gridwordsbot.domain.reporting.ReportPersonalStreaks;
import de.venomenon.gridwordsbot.domain.reporting.ReportPlayerGameStatistics;
import de.venomenon.gridwordsbot.domain.reporting.ReportRatio;
import de.venomenon.gridwordsbot.domain.reporting.ReportRenderingException;
import de.venomenon.gridwordsbot.domain.reporting.ReportSharedDayCounts;
import de.venomenon.gridwordsbot.domain.reporting.ReportSharedStreaks;
import de.venomenon.gridwordsbot.domain.reporting.ReportStreakSnapshot;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PeriodicReportRendererTest {
    private final PeriodicReportRenderer renderer = new PeriodicReportRenderer();

    @Test
    void rendersCompactWeeklyStatisticsAsClearPlayerFields() {
        var period = new ReportPeriod(date(2026, 7, 27), date(2026, 8, 2));
        var rendered = renderer.render(report(ReportType.WEEKLY, period,
                player(1, "Anna", 7, 6, 5, 4, 6, 5, 1, 18, 5, 455, 75),
                player(2, "Bernd", 7, 7, 7, 7, 7, 7, 0, 21, 7, 420, 60)));

        assertThat(rendered.pages()).hasSize(1);
        assertThat(rendered.pages().getFirst().title()).isEqualTo("📊 Wochenbericht · 27. Juli–2. August 2026");
        assertThat(rendered.pages().getFirst().footer()).isEmpty();
        assertThat(rendered.pages().getFirst().fields()).extracting(RenderedReportField::name)
                .containsExactly("👤 Anna", "👤 Bernd", "🤝 Gemeinsam");
        assertThat(rendered.pages().getFirst().fields().getFirst().value()).isEqualTo("""
                📅 Teilnahme 7/7 · Aktiv 6 · Komplett 5 · Perfekt 4
                🟩 GW 6/7 · 5✅ 1❌ 1⬜ · 83,3 % · ØVers. 3,6 · ØZeit 1:31 · Best 1:15
                🟦 QW 6/7 · 5✅ 1❌ 1⬜ · 83,3 % · ØVers. 3,6 · ØZeit 1:31 · Best 1:15
                🔥 Serien (Stand/Rekord)
                ↳ Aktiv 2/8 · Komplett 1/7 · GW 3/6 · QW 4/5 · Perfekt 1/4""");
        assertThat(rendered.pages().getFirst().fields().getLast().value()).isEqualTo("""
                📅 Möglich 7 · Komplett 5 · Perfekt 4
                🔥 Serien (Stand/Rekord)
                ↳ Komplett 1/7 · Perfekt 1/4""");
        assertThat(rendered.contentFingerprint()).matches("[0-9a-f]{64}");
    }

    @Test
    void compactsDateRangesWithinMonthsAcrossMonthsAndAcrossYears() {
        assertThat(renderer.render(report(ReportType.WEEKLY,
                new ReportPeriod(date(2026, 8, 3), date(2026, 8, 9)), player(1))).pages().getFirst().title())
                .isEqualTo("📊 Wochenbericht · 3.–9. August 2026");
        assertThat(renderer.render(report(ReportType.WEEKLY,
                new ReportPeriod(date(2026, 7, 27), date(2026, 8, 2)), player(1))).pages().getFirst().title())
                .isEqualTo("📊 Wochenbericht · 27. Juli–2. August 2026");
        assertThat(renderer.render(report(ReportType.WEEKLY,
                new ReportPeriod(date(2025, 12, 29), date(2026, 1, 4)), player(1))).pages().getFirst().title())
                .isEqualTo("📊 Wochenbericht · 29. Dezember 2025–4. Januar 2026");
    }

    @Test
    void rendersMonthlyTitleWithGermanLocaleIndependentOfDefaults() {
        Locale previousLocale = Locale.getDefault();
        TimeZone previousTimeZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            var rendered = renderer.render(report(ReportType.MONTHLY,
                    new ReportPeriod(date(2026, 8, 1), date(2026, 8, 31)), player(1)));
            assertThat(rendered.pages().getFirst().title()).isEqualTo("📊 Monatsbericht · 1.–31. August 2026");
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousTimeZone);
        }
    }

    @Test
    void rendersFullSuccessFailuresMissingAndUndefinedSolvedAggregatesWithoutNoise() {
        ReportGameStatistics grid = statistics(GameType.GRIDWORDS, 7, 7, 7, 0, 28, 7, 350, 42);
        ReportGameStatistics quad = statistics(GameType.QUADWORDS, 7, 7, 6, 1, 48, 6, 1_110, 121);
        String value = renderer.render(report(ReportType.WEEKLY,
                new ReportPeriod(date(2026, 8, 3), date(2026, 8, 9)),
                playerWithGames(1, "Anna", grid, quad, 7, 7, 7, 5)))
                .pages().getFirst().fields().getFirst().value();

        assertThat(value).contains(
                "🟩 GW 7/7 ✅ · 100 % · ØVers. 4,0 · ØZeit 0:50 · Best 0:42",
                "🟦 QW 7/7 · 6✅ 1❌ · 85,7 % · ØVers. 8,0 · ØZeit 3:05 · Best 2:01");
        assertThat(value).doesNotContain("100,0 %", "Nicht gelöst:", "Fehlend:");
    }

    @Test
    void rendersMissingSubmissionsNoSolvedResultsAndGameNonParticipationCompactly() {
        ReportGameStatistics grid = statistics(GameType.GRIDWORDS, 7, 6, 6, 0, 27, 6, 558, 52);
        ReportGameStatistics quad = statistics(GameType.QUADWORDS, 7, 3, 0, 3, 0, 0, 0, 0);
        String mixed = renderer.render(report(ReportType.WEEKLY,
                new ReportPeriod(date(2026, 8, 3), date(2026, 8, 9)),
                playerWithGames(1, "Anna", grid, quad, 7, 6, 0, 0)))
                .pages().getFirst().fields().getFirst().value();

        assertThat(mixed).contains(
                "🟩 GW 6/7 · 6✅ 1⬜ · 100 % · ØVers. 4,5 · ØZeit 1:33 · Best 0:52",
                "🟦 QW 3/7 · 3❌ 4⬜ · 0 %");
        assertThat(mixed).doesNotContain("🟦 QW 3/7 · 3❌ 4⬜ · 0 % · ØVers.");

        LocalDate day = date(2026, 8, 3);
        ReportGameStatistics noSubmission = statistics(GameType.GRIDWORDS, 1, 0, 0, 0, 0, 0, 0, 0);
        ReportGameStatistics noParticipation = statistics(GameType.QUADWORDS, 0, 0, 0, 0, 0, 0, 0, 0);
        String sparse = renderer.render(report(ReportType.WEEKLY, new ReportPeriod(day, day),
                playerWithGames(2, "Grid only", noSubmission, noParticipation, 1, 0, 0, 0)))
                .pages().getFirst().fields().getFirst().value();
        assertThat(sparse).contains("🟩 GW 0/1 · 1⬜ · keine Einreichung", "🟦 QW — keine Teilnahme");
    }

    @Test
    void usesDeterministicHalfUpRoundingIncludingDurationsAboveOneHour() {
        ReportGameStatistics grid = statistics(GameType.GRIDWORDS, 3, 3, 2, 1, 5, 2, 7_321, 3_660);
        String value = renderer.render(report(ReportType.WEEKLY,
                new ReportPeriod(date(2026, 8, 3), date(2026, 8, 9)),
                playerWithGames(1, "Anna", grid, grid, 3, 3, 2, 1)))
                .pages().getFirst().fields().getFirst().value();

        assertThat(value).contains("66,7 % · ØVers. 2,5 · ØZeit 1:01:01 · Best 1:01:00");
    }

    @Test
    void neutralizesMarkdownMentionsAndKeepsPrefixedFieldNamesWithinDiscordLimit() {
        String unsafe = "@everyone <@123> **bold** _under_\n# title" + "x".repeat(256);
        var rendered = renderer.render(report(ReportType.WEEKLY,
                new ReportPeriod(date(2026, 8, 3), date(2026, 8, 9)),
                player(1, unsafe, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));

        String name = rendered.pages().getFirst().fields().getFirst().name();
        assertThat(name).startsWith("👤 ").hasSizeLessThanOrEqualTo(RenderedReportField.MAX_NAME_LENGTH);
        assertThat(name).doesNotContain("@", "<", ">", "*", "_", "\n", "#");
        assertThat(name).contains("＠everyone", "‹＠123›", "＊＊bold＊＊");
    }

    @Test
    void keepsSharedHistoricalStreaksVisibleWhenThereAreNoSharedPossibleDays() {
        PeriodicReport base = report(ReportType.WEEKLY,
                new ReportPeriod(date(2026, 8, 3), date(2026, 8, 9)), player(1));
        PeriodicReport report = new PeriodicReport(base.reportType(), base.period(), base.participants(),
                new PeriodicReportSharedSection(new ReportSharedDayCounts(0, 0, 0),
                        new ReportSharedStreaks(new ReportStreakSnapshot(0, 7), new ReportStreakSnapshot(0, 3))));

        assertThat(renderer.render(report).pages().getFirst().fields().getLast().value()).isEqualTo("""
                📅 Keine gemeinsam möglichen Tage
                🔥 Serien (Stand/Rekord)
                ↳ Komplett 0/7 · Perfekt 0/3""");
    }

    @Test
    void startsHighlightsOnANewPageWithItsOwnTitleAndCompactFields() {
        PeriodicReport base = report(ReportType.WEEKLY,
                new ReportPeriod(date(2026, 8, 3), date(2026, 8, 9)), player(1, "Anna", 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        PeriodicReport highlighted = new PeriodicReport(base.reportType(), base.period(), base.participants(), base.shared(),
                new ReportHighlightFacts(Map.of(1L, 2), List.of(resultRecord(1, 1, date(2026, 8, 9)))));

        var rendered = renderer.render(highlighted);

        assertThat(rendered.pages()).hasSize(2);
        assertThat(rendered.pages()).extracting(RenderedReportPage::title).containsExactly(
                "📊 Wochenbericht · 3.–9. August 2026",
                "✨ Highlights der Woche · 3.–9. August 2026");
        assertThat(rendered.pages()).extracting(RenderedReportPage::footer).containsExactly(
                Optional.of("Seite 1/2"), Optional.of("Seite 2/2"));
        assertThat(rendered.pages().getLast().fields()).extracting(RenderedReportField::name)
                .containsExactly("🏅 Achievements", "🏆 Rekorde");
        assertThat(rendered.pages().getLast().fields().getFirst().value())
                .isEqualTo("Anna · **2** freigeschaltet");
        assertThat(rendered.pages().getLast().fields().getLast().value())
                .isEqualTo("**Anna** · persönlicher GridWords-Rekord\n↳ wenigste Versuche · **4 Versuche · 1:38**");
        assertThat(rendered.contentFingerprint()).isNotEqualTo(renderer.render(base).contentFingerprint());
    }

    @Test
    void omitsHighlightPageCompletelyWhenThereAreNoHighlights() {
        var rendered = renderer.render(report(ReportType.MONTHLY,
                new ReportPeriod(date(2026, 8, 1), date(2026, 8, 31)), player(1)));

        assertThat(rendered.pages()).singleElement().satisfies(page -> {
            assertThat(page.title()).startsWith("📊 Monatsbericht");
            assertThat(page.fields()).extracting(RenderedReportField::name).doesNotContain("🏅 Achievements", "🏆 Rekorde");
        });
    }

    @Test
    void reducesCrossingAndFinishToOneAtomicTwoLineRecordBlock() {
        PeriodicReport base = report(ReportType.WEEKLY,
                new ReportPeriod(date(2026, 8, 3), date(2026, 8, 9)), player(1, "Anna", 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        PeriodicReport highlighted = new PeriodicReport(base.reportType(), base.period(), base.participants(), base.shared(),
                new ReportHighlightFacts(Map.of(), List.of(
                        streakRecord("00000000-0000-0000-0000-000000000201", RecordEventType.SERIES_RECORD_CROSSED, 7),
                        streakRecord("00000000-0000-0000-0000-000000000202", RecordEventType.RECORD_SERIES_FINISHED, 8))));

        RenderedReportField records = renderer.render(highlighted).pages().getLast().fields().getFirst();
        assertThat(records.name()).isEqualTo("🏆 Rekorde");
        assertThat(records.value()).isEqualTo("**Anna** · persönlicher Rekord\n↳ Perfektserie · **8 Tage**");
    }

    @Test
    void keepsEveryRecordBlockAtomicWhenSplittingManyRecordsAcrossFields() {
        PeriodicReport base = report(ReportType.WEEKLY,
                new ReportPeriod(date(2026, 8, 3), date(2026, 8, 9)), player(1, "Anna", 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        List<RecordEventSnapshot> events = java.util.stream.IntStream.rangeClosed(1, 30)
                .mapToObj(index -> resultRecord(index, 1, date(2026, 8, 9))).toList();
        PeriodicReport highlighted = new PeriodicReport(base.reportType(), base.period(), base.participants(), base.shared(),
                new ReportHighlightFacts(Map.of(), events));

        List<RenderedReportField> recordFields = renderer.render(highlighted).pages().stream()
                .flatMap(page -> page.fields().stream())
                .filter(field -> field.name().startsWith("🏆 Rekorde"))
                .toList();

        assertThat(recordFields).hasSizeGreaterThan(1);
        assertThat(recordFields).allSatisfy(field -> {
            for (String block : field.value().split("\\n\\n")) {
                assertThat(block.lines().count()).isEqualTo(2);
            }
        });
        long renderedRecords = recordFields.stream()
                .mapToLong(field -> occurrences(field.value(), "**Anna**"))
                .sum();
        assertThat(renderedRecords).isEqualTo(30);
    }

    @Test
    void usesGlobalPageNumbersAcrossMultipleStatisticsPagesAndHighlights() {
        List<PeriodicReportParticipantSection> players = java.util.stream.IntStream.rangeClosed(1, 26)
                .mapToObj(index -> player(index, "P" + index, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).toList();
        PeriodicReport base = report(ReportType.WEEKLY,
                new ReportPeriod(date(2026, 8, 3), date(2026, 8, 9)), players);
        PeriodicReport highlighted = new PeriodicReport(base.reportType(), base.period(), base.participants(), base.shared(),
                new ReportHighlightFacts(Map.of(1L, 1), List.of()));

        var rendered = renderer.render(highlighted);

        assertThat(rendered.pages()).hasSize(3);
        assertThat(rendered.pages()).extracting(RenderedReportPage::title).containsExactly(
                "📊 Wochenbericht · 3.–9. August 2026",
                "📊 Wochenbericht · 3.–9. August 2026",
                "✨ Highlights der Woche · 3.–9. August 2026");
        assertThat(rendered.pages()).extracting(RenderedReportPage::footer).containsExactly(
                Optional.of("Seite 1/3"), Optional.of("Seite 2/3"), Optional.of("Seite 3/3"));
        assertThat(rendered.pages().get(1).fields().getLast().name()).isEqualTo("🤝 Gemeinsam");
    }

    @Test
    void paginatesBecauseOfFieldCountAndVisibleCharacterLimits() {
        List<RenderedReportField> fields = java.util.stream.IntStream.rangeClosed(1, 26)
                .mapToObj(index -> new RenderedReportField("F" + index, "v"))
                .toList();
        List<RenderedReportPage> pages = PeriodicReportRenderer.paginate("t", fields);
        assertThat(pages).hasSize(2);
        assertThat(pages.getFirst().fields()).hasSize(RenderedReportPage.MAX_FIELDS);
        assertThat(pages.getLast().fields()).containsExactly(fields.getLast());
        assertThat(pages).extracting(RenderedReportPage::footer)
                .containsExactly(Optional.of("Seite 1/2"), Optional.of("Seite 2/2"));

        RenderedReportField wide = new RenderedReportField("n".repeat(256), "v".repeat(1_024));
        List<RenderedReportField> wideFields = List.of(wide, wide, wide, wide, wide, wide);
        assertThat(PeriodicReportRenderer.paginate("t", wideFields))
                .hasSizeGreaterThan(1)
                .allSatisfy(page -> assertThat(page.visibleLength()).isLessThanOrEqualTo(6_000));
    }

    @Test
    void acceptsExactDiscordLimitsAndRejectsLimitOverruns() {
        RenderedReportField field = new RenderedReportField("n".repeat(256), "v".repeat(1_024));
        RenderedReportPage page = new RenderedReportPage("t".repeat(256), List.of(field, field,
                new RenderedReportField("n".repeat(256), "v".repeat(880))), Optional.of("f".repeat(2_048)));
        assertThat(page.visibleLength()).isEqualTo(6_000);
        assertThatThrownBy(() -> new RenderedReportField("n".repeat(257), "v"))
                .isInstanceOf(ReportRenderingException.class);
        assertThatThrownBy(() -> new RenderedReportField("n", "v".repeat(1_025)))
                .isInstanceOf(ReportRenderingException.class);
        assertThatThrownBy(() -> new RenderedReportPage("t".repeat(257), List.of(), Optional.empty()))
                .isInstanceOf(ReportRenderingException.class);
    }

    @Test
    void producesStableFingerprintsAndIncludesTitlesPaginationAndOrder() {
        PeriodicReport one = report(ReportType.WEEKLY, new ReportPeriod(date(2026, 8, 3), date(2026, 8, 9)),
                player(1), player(2));
        PeriodicReport changed = report(ReportType.WEEKLY, one.period(),
                player(1, "P1", 1, 1, 1, 1, 1, 1, 0, 1, 1, 30, 30), player(2));
        PeriodicReport reversed = report(ReportType.WEEKLY, one.period(), player(2), player(1));

        assertThat(renderer.render(one)).isEqualTo(renderer.render(one));
        assertThat(renderer.render(changed).contentFingerprint()).isNotEqualTo(renderer.render(one).contentFingerprint());
        assertThat(renderer.render(reversed).contentFingerprint()).isNotEqualTo(renderer.render(one).contentFingerprint());

        RenderedReportField first = new RenderedReportField("A", "eins");
        RenderedReportField second = new RenderedReportField("B", "zwei");
        List<RenderedReportPage> onePage = List.of(new RenderedReportPage("Titel", List.of(first, second), Optional.empty()));
        List<RenderedReportPage> twoPages = List.of(
                new RenderedReportPage("Titel", List.of(first), Optional.of("Seite 1/2")),
                new RenderedReportPage("Highlights", List.of(second), Optional.of("Seite 2/2")));
        assertThat(PeriodicReportRenderer.fingerprint(twoPages))
                .isNotEqualTo(PeriodicReportRenderer.fingerprint(onePage));
    }

    private static long occurrences(String value, String needle) {
        return (value.length() - value.replace(needle, "").length()) / needle.length();
    }

    private static PeriodicReport report(
            ReportType type,
            ReportPeriod period,
            PeriodicReportParticipantSection... participants) {
        return report(type, period, List.of(participants));
    }

    private static PeriodicReport report(
            ReportType type,
            ReportPeriod period,
            List<PeriodicReportParticipantSection> participants) {
        int calendarDays = Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(period.startDate(), period.endDate()) + 1);
        int sharedPossible = participants.size() < 2 ? 0 : calendarDays;
        int complete = Math.min(5, sharedPossible);
        int perfect = Math.min(4, complete);
        return new PeriodicReport(type, period, participants, new PeriodicReportSharedSection(
                new ReportSharedDayCounts(sharedPossible, complete, perfect),
                new ReportSharedStreaks(
                        new ReportStreakSnapshot(sharedPossible == 0 ? 0 : 1, sharedPossible == 0 ? 0 : 7),
                        new ReportStreakSnapshot(sharedPossible == 0 ? 0 : 1, sharedPossible == 0 ? 0 : 4))));
    }

    private static PeriodicReportParticipantSection player(int index) {
        return player(index, "P" + index, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    private static PeriodicReportParticipantSection player(
            long id,
            String name,
            int possible,
            int activity,
            int complete,
            int perfect,
            int submitted,
            int solved,
            int unsolved,
            long attempts,
            int solvedCount,
            long durationSeconds,
            long bestSeconds) {
        ReportGameStatistics grid = statistics(
                GameType.GRIDWORDS, possible, submitted, solved, unsolved,
                attempts, solvedCount, durationSeconds, bestSeconds);
        ReportGameStatistics quad = statistics(
                GameType.QUADWORDS, possible, submitted, solved, unsolved,
                attempts, solvedCount, durationSeconds, bestSeconds);
        return playerWithGames(id, name, grid, quad, possible, activity, complete, perfect);
    }

    private static PeriodicReportParticipantSection playerWithGames(
            long id,
            String name,
            ReportGameStatistics grid,
            ReportGameStatistics quad,
            int participation,
            int activity,
            int complete,
            int perfect) {
        LocalDate first = date(2026, 7, 27);
        List<LocalDate> gridDays = java.util.stream.IntStream.range(0, grid.possibleDays())
                .mapToObj(first::plusDays).toList();
        List<LocalDate> quadDays = java.util.stream.IntStream.range(0, quad.possibleDays())
                .mapToObj(first::plusDays).toList();
        LinkedHashSet<LocalDate> unionSet = new LinkedHashSet<>(gridDays);
        unionSet.addAll(quadDays);
        List<LocalDate> union = List.copyOf(unionSet);
        List<LocalDate> both = gridDays.stream().filter(quadDays::contains).toList();
        assertThat(union).hasSize(participation);
        return new PeriodicReportParticipantSection(
                new ReportParticipant(id, name, first, union, gridDays, quadDays, both),
                new ReportPlayerGameStatistics(id, grid, quad),
                new ReportPersonalDayCounts(participation, activity, complete, perfect),
                new ReportPersonalStreaks(
                        new ReportStreakSnapshot(2, 8),
                        new ReportStreakSnapshot(1, 7),
                        new ReportStreakSnapshot(3, 6),
                        new ReportStreakSnapshot(4, 5),
                        new ReportStreakSnapshot(1, 4)));
    }

    private static ReportGameStatistics statistics(
            GameType game,
            int possible,
            int submitted,
            int solved,
            int unsolved,
            long attempts,
            int solvedCount,
            long durationSeconds,
            long bestSeconds) {
        return new ReportGameStatistics(
                game,
                possible,
                submitted,
                solved,
                unsolved,
                possible - submitted,
                submitted == 0 ? Optional.empty() : Optional.of(new ReportRatio(solved, submitted)),
                attempts,
                solvedCount,
                Duration.ofSeconds(durationSeconds),
                solvedCount,
                solvedCount == 0 ? Optional.empty() : Optional.of(Duration.ofSeconds(bestSeconds)));
    }

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private static RecordEventSnapshot resultRecord(int sequence, long playerId, LocalDate gameDate) {
        var source = new RecordSourceReference.GameResult(90L + sequence, 0, playerId, GameType.GRIDWORDS, gameDate);
        UUID eventId = new UUID(0, sequence);
        var draft = new RecordEventDraft(eventId, "report-result-" + sequence,
                new RecordStateKey(1, new RecordDefinitionKey("result.gridwords.fewest-attempts.personal"),
                        RecordDefinitionVersion.RECORDS_V1, new RecordScope.Personal(playerId)),
                RecordEventType.RESULT_RECORD_BROKEN,
                Optional.empty(),
                new AttemptsDurationRecordValue(4, Duration.ofSeconds(98 + sequence - 1L)),
                Optional.empty(),
                Optional.of(playerId),
                Optional.empty(),
                source,
                "report-result-" + sequence,
                RecordProcessingOrigin.LIVE_SUBMISSION,
                Instant.parse("2026-08-09T12:00:00Z").plusSeconds(sequence));
        Instant detected = Instant.parse("2026-08-09T12:00:00Z").plusSeconds(sequence);
        return new RecordEventSnapshot(draft, RecordEventValidity.VALID, Optional.empty(), Optional.empty(), detected, detected);
    }

    private static RecordEventSnapshot streakRecord(String eventId, RecordEventType type, int length) {
        LocalDate start = date(2026, 8, 2);
        var source = new RecordSourceReference.StreakRun(
                StreakRecordMetric.PERFECT,
                new RecordSourceReference.StreakRunOwner.Player(1),
                start);
        Instant detected = Instant.parse("2026-08-09T12:00:00Z");
        var draft = new RecordEventDraft(
                UUID.fromString(eventId),
                "report-" + eventId,
                new RecordStateKey(1, new RecordDefinitionKey("streak.perfect.personal"),
                        RecordDefinitionVersion.RECORDS_V1, new RecordScope.Personal(1)),
                type,
                Optional.empty(),
                new StreakRecordValue(length, start, start.plusDays(length - 1L)),
                Optional.empty(),
                Optional.of(1L),
                Optional.empty(),
                source,
                "report-" + eventId,
                RecordProcessingOrigin.DAY_CLOSE,
                detected);
        return new RecordEventSnapshot(draft, RecordEventValidity.VALID, Optional.empty(), Optional.empty(), detected, detected);
    }
}
