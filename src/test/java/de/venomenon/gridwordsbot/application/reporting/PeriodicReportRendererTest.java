package de.venomenon.gridwordsbot.application.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReport;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportParticipantSection;
import de.venomenon.gridwordsbot.domain.reporting.RenderedPeriodicReport;
import de.venomenon.gridwordsbot.domain.reporting.RenderedReportField;
import de.venomenon.gridwordsbot.domain.reporting.RenderedReportPage;
import de.venomenon.gridwordsbot.domain.reporting.ReportGameStatistics;
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
import de.venomenon.gridwordsbot.domain.reporting.ReportHighlightFacts;
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
import java.time.Duration;
import java.time.LocalDate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class PeriodicReportRendererTest {
    private final PeriodicReportRenderer renderer = new PeriodicReportRenderer();

    @Test
    void rendersCompleteWeeklyReportWithTwoPlayersAsAGoldenPage() {
        var rendered = renderer.render(report(ReportType.WEEKLY, new ReportPeriod(date(2026, 7, 27), date(2026, 8, 2)),
                player(1, "Anna", 7, 6, 5, 4, 6, 5, 1, 18, 5, 455, 75),
                player(2, "Bernd", 7, 7, 7, 7, 7, 7, 0, 21, 7, 420, 60)));

        assertThat(rendered.pages()).hasSize(1);
        assertThat(rendered.pages().getFirst().title()).isEqualTo("Wochenbericht · 27. Juli 2026 bis 2. August 2026");
        assertThat(rendered.pages().getFirst().footer()).isEmpty();
        assertThat(rendered.pages().getFirst().fields()).extracting(RenderedReportField::name)
                .containsExactly("Anna", "Bernd", "Gemeinsam");
        assertThat(rendered.pages().getFirst().fields().getFirst().value()).isEqualTo("""
                Teilnahme: 7 · Aktivität: 6 · Komplett: 5 · Perfekt: 4
                GridWords
                Eingereicht: 6/7 · Gelöst: 5 · Nicht gelöst: 1 · Fehlend: 1
                Quote: 83,3 % · Ø Versuche: 3,6 · Ø Zeit: 1:31 · Bestzeit: 1:15
                QuadWords
                Eingereicht: 6/7 · Gelöst: 5 · Nicht gelöst: 1 · Fehlend: 1
                Quote: 83,3 % · Ø Versuche: 3,6 · Ø Zeit: 1:31 · Bestzeit: 1:15
                Serien (Stand/Rekord)
                Aktivität: 2/8 · Komplett: 1/7
                GridWords gelöst: 3/6 · QuadWords gelöst: 4/5 · Perfekt: 1/4""");
        assertThat(rendered.pages().getFirst().fields().get(2).value()).isEqualTo(
                "Mögliche Tage: 7 · Komplette Tage: 5 · Perfekte Tage: 4\n"
                        + "Komplettserie (Stand/Rekord): 1/7 · Perfektserie (Stand/Rekord): 1/4");
        assertThat(rendered.contentFingerprint()).matches("[0-9a-f]{64}");
    }

    @Test
    void rendersMonthlyTitleWithGermanLocaleIndependentOfDefaults() {
        Locale previousLocale = Locale.getDefault();
        TimeZone previousTimeZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            var rendered = renderer.render(report(ReportType.MONTHLY, new ReportPeriod(date(2026, 2, 1), date(2026, 2, 28)),
                    player(1, "Anna", 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));
            assertThat(rendered.pages()).hasSize(1);
            assertThat(rendered.pages().getFirst().title()).isEqualTo("Monatsbericht · 1. Februar 2026 bis 28. Februar 2026");
            assertThat(rendered.pages().getFirst().fields()).extracting(RenderedReportField::name, RenderedReportField::value)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("Anna", """
                                    Teilnahme: 1 · Aktivität: 0 · Komplett: 0 · Perfekt: 0
                                    GridWords
                                    Eingereicht: 0/1 · Gelöst: 0 · Nicht gelöst: 0 · Fehlend: 1
                                    Quote: — · Ø Versuche: — · Ø Zeit: — · Bestzeit: —
                                    QuadWords
                                    Eingereicht: 0/1 · Gelöst: 0 · Nicht gelöst: 0 · Fehlend: 1
                                    Quote: — · Ø Versuche: — · Ø Zeit: — · Bestzeit: —
                                    Serien (Stand/Rekord)
                                    Aktivität: 2/8 · Komplett: 1/7
                                    GridWords gelöst: 3/6 · QuadWords gelöst: 4/5 · Perfekt: 1/4"""),
                            org.assertj.core.groups.Tuple.tuple("Gemeinsam",
                                    "Mögliche Tage: 0 · Komplette Tage: 0 · Perfekte Tage: 0\n"
                                            + "Komplettserie (Stand/Rekord): 0/0 · Perfektserie (Stand/Rekord): 0/0"));
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousTimeZone);
        }
    }

    @Test
    void rendersUndefinedStatisticsAsNeutralPlaceholdersAndKeepsSharedZerosVisible() {
        var rendered = renderer.render(report(ReportType.WEEKLY, new ReportPeriod(date(2026, 7, 27), date(2026, 8, 2)),
                player(1, "Anna", 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));

        assertThat(rendered.pages().getFirst().fields().getFirst().value()).contains(
                "Eingereicht: 0/7 · Gelöst: 0 · Nicht gelöst: 0 · Fehlend: 7",
                "Quote: — · Ø Versuche: — · Ø Zeit: — · Bestzeit: —");
        assertThat(rendered.pages().get(0).fields().getLast().value()).contains("Mögliche Tage: 0 · Komplette Tage: 0 · Perfekte Tage: 0");
    }

    @Test
    void rendersZeroGameParticipationAsNeutralNonParticipation() {
        LocalDate day = date(2026, 7, 27);
        ReportParticipant participant = new ReportParticipant(
                1L, "Grid only", day, List.of(day), List.of(day), List.of(), List.of());
        ReportGameStatistics gridWords = new ReportGameStatistics(
                GameType.GRIDWORDS, 1, 0, 0, 0, 1, Optional.empty(),
                0, 0, Duration.ZERO, 0, Optional.empty());
        ReportGameStatistics quadWords = new ReportGameStatistics(
                GameType.QUADWORDS, 0, 0, 0, 0, 0, Optional.empty(),
                0, 0, Duration.ZERO, 0, Optional.empty());
        PeriodicReportParticipantSection section = new PeriodicReportParticipantSection(
                participant,
                new ReportPlayerGameStatistics(1L, gridWords, quadWords),
                new ReportPersonalDayCounts(1, 0, 0, 0),
                new ReportPersonalStreaks(zero(), zero(), zero(), zero(), zero()));

        String value = renderer.render(report(
                ReportType.WEEKLY,
                new ReportPeriod(day, day),
                section)).pages().getFirst().fields().getFirst().value();

        assertThat(value).contains("GridWords\nEingereicht: 0/1", "QuadWords\nNicht teilgenommen");
        assertThat(value).doesNotContain("QuadWords\nEingereicht", "QuadWords\nNicht teilgenommen\nQuote");
    }

    @Test
    void usesDeterministicHalfUpRoundingAtMeaningfulBoundariesIncludingDurationsAboveOneHour() {
        var rendered = renderer.render(report(ReportType.WEEKLY, new ReportPeriod(date(2026, 7, 27), date(2026, 8, 2)),
                player(1, "Anna", 3, 3, 2, 1, 3, 2, 1, 5, 2, 7_321, 3_660)));

        assertThat(rendered.pages().getFirst().fields().getFirst().value())
                .contains("Quote: 66,7 % · Ø Versuche: 2,5 · Ø Zeit: 1:01:01 · Bestzeit: 1:01:00");
    }

    @Test
    void neutralizesMentionsMarkdownAndControlCharactersInDisplayNames() {
        var rendered = renderer.render(report(ReportType.WEEKLY, new ReportPeriod(date(2026, 7, 27), date(2026, 8, 2)),
                player(1, "@everyone <@123> <@&4> **bold** _under_\n# title", 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)));

        String name = rendered.pages().getFirst().fields().getFirst().name();
        assertThat(name).doesNotContain("@", "<", ">", "*", "_", "\n", "#");
        assertThat(name).contains("＠everyone", "‹＠123›", "＊＊bold＊＊");
    }

    @Test
    void rendersOnlyAwardCountsAndCompleteRecordFactsAsOptionalHighlights() {
        PeriodicReport base = report(ReportType.WEEKLY, new ReportPeriod(date(2026, 7, 27), date(2026, 8, 2)),
                player(1, "Anna", 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                player(2, "Bernd", 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        PeriodicReport highlighted = new PeriodicReport(base.reportType(), base.period(), base.participants(), base.shared(),
                new ReportHighlightFacts(Map.of(1L, 2), List.of(resultRecord(1, date(2026, 8, 1)))));

        var rendered = renderer.render(highlighted);

        List<RenderedReportField> fields = rendered.pages().stream().flatMap(page -> page.fields().stream()).toList();
        assertThat(fields).extracting(RenderedReportField::name).contains("🏅 Achievements", "🏆 Rekorde");
        assertThat(fields.stream().filter(field -> field.name().equals("🏅 Achievements")).findFirst().orElseThrow().value())
                .isEqualTo("Anna: 2 Achievements freigeschaltet");
        assertThat(fields.stream().filter(field -> field.name().equals("🏆 Rekorde")).findFirst().orElseThrow().value())
                .isEqualTo("Anna: neuer persönlicher GridWords Rekord · wenigste Versuche · 4 Versuche · 1:38");
        assertThat(rendered.contentFingerprint()).isNotEqualTo(renderer.render(base).contentFingerprint());
    }

    @Test
    void reducesACrossingAndFinishOfTheSameStreakRunInOnePeriodToTheFinish() {
        PeriodicReport base = report(ReportType.WEEKLY, new ReportPeriod(date(2026, 7, 27), date(2026, 8, 2)),
                player(1, "Anna", 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        PeriodicReport highlighted = new PeriodicReport(base.reportType(), base.period(), base.participants(), base.shared(),
                new ReportHighlightFacts(Map.of(), List.of(
                        streakRecord("00000000-0000-0000-0000-000000000201", RecordEventType.SERIES_RECORD_CROSSED, 7),
                        streakRecord("00000000-0000-0000-0000-000000000202", RecordEventType.RECORD_SERIES_FINISHED, 8))));

        List<RenderedReportField> fields = renderer.render(highlighted).pages().stream()
                .flatMap(page -> page.fields().stream()).filter(field -> field.name().startsWith("🏆 Rekorde")).toList();

        assertThat(fields).singleElement().extracting(RenderedReportField::value)
                .isEqualTo("Anna: neuer persönlicher Rekord · Perfektserie · 8 Tage");
    }

    @Test
    void paginatesAtomicallyForVisibleCharacterLimitsAndPlacesSharedLast() {
        List<PeriodicReportParticipantSection> players = java.util.stream.IntStream.rangeClosed(1, 26)
                .mapToObj(index -> player(index, "P" + index, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).toList();
        var rendered = renderer.render(report(ReportType.WEEKLY, new ReportPeriod(date(2026, 7, 27), date(2026, 8, 2)), players));

        assertThat(rendered.pages()).hasSize(2);
        assertThat(rendered.pages().getFirst().fields()).hasSize(14);
        assertThat(rendered.pages().getFirst().footer()).contains("Seite 1/2");
        assertThat(rendered.pages().getLast().fields()).extracting(RenderedReportField::name).contains("P26", "Gemeinsam");
        assertThat(rendered.pages().stream().flatMap(page -> page.fields().stream()).map(RenderedReportField::name))
                .containsExactlyElementsOf(java.util.stream.Stream.concat(
                        java.util.stream.IntStream.rangeClosed(1, 26).mapToObj(index -> "P" + index),
                        java.util.stream.Stream.of("Gemeinsam")).toList());

        List<PeriodicReportParticipantSection> longNamed = java.util.stream.IntStream.rangeClosed(1, 10)
                .mapToObj(index -> player(index, "x".repeat(256), 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)).toList();
        var characterLimited = renderer.render(report(ReportType.WEEKLY,
                new ReportPeriod(date(2026, 7, 27), date(2026, 8, 2)), longNamed));
        assertThat(characterLimited.pages()).hasSizeGreaterThan(1);
        assertThat(characterLimited.pages()).allSatisfy(page -> assertThat(page.visibleLength()).isLessThanOrEqualTo(6_000));
    }

    @Test
    void paginatesBecauseOfTheFieldCountLimit() {
        List<RenderedReportField> fields = java.util.stream.IntStream.rangeClosed(1, 26)
                .mapToObj(index -> new RenderedReportField("F" + index, "v"))
                .toList();

        List<RenderedReportPage> pages = PeriodicReportRenderer.paginate("t", fields);

        assertThat(pages).hasSize(2);
        assertThat(pages.getFirst().fields()).hasSize(RenderedReportPage.MAX_FIELDS);
        assertThat(pages.getLast().fields()).containsExactly(fields.getLast());
        assertThat(pages).extracting(RenderedReportPage::footer)
                .containsExactly(Optional.of("Seite 1/2"), Optional.of("Seite 2/2"));
    }

    @Test
    void keepsSharedSectionOnTheLastPageWhenItFitsAndMovesItAtomicallyWhenItDoesNot() {
        RenderedReportField shared = new RenderedReportField("Gemeinsam", "s".repeat(50));
        List<RenderedReportField> fitting = wideFields(160, shared);
        List<RenderedReportField> overflowing = wideFields(170, shared);

        List<RenderedReportPage> fittingPages = PeriodicReportRenderer.paginate("t", fitting);
        List<RenderedReportPage> overflowingPages = PeriodicReportRenderer.paginate("t", overflowing);

        assertThat(fittingPages).singleElement().satisfies(page -> {
            assertThat(page.fields()).hasSize(6);
            assertThat(page.fields().getLast()).isEqualTo(shared);
        });
        assertThat(overflowingPages).hasSize(2);
        assertThat(overflowingPages.getFirst().fields()).hasSize(5).doesNotContain(shared);
        assertThat(overflowingPages.getLast().fields()).containsExactly(shared);
    }

    @Test
    void acceptsExactLimitsAndRejectsEveryLimitExceededByOneCharacter() {
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
        assertThatThrownBy(() -> new RenderedReportPage("t", List.of(), Optional.of("f".repeat(2_049))))
                .isInstanceOf(ReportRenderingException.class);
        assertThatThrownBy(() -> new RenderedReportPage("t".repeat(256), List.of(field, field,
                new RenderedReportField("n".repeat(256), "v".repeat(881))), Optional.of("f".repeat(2_048))))
                .isInstanceOf(ReportRenderingException.class);
    }

    @Test
    void producesStableFingerprintsAndChangesThemForVisibleContentAndOrder() {
        PeriodicReport one = report(ReportType.WEEKLY, new ReportPeriod(date(2026, 7, 27), date(2026, 8, 2)),
                player(1, "Anna", 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), player(2, "Bernd", 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        PeriodicReport changed = report(ReportType.WEEKLY, one.period(),
                player(1, "Anna", 1, 1, 1, 1, 1, 1, 0, 1, 1, 30, 30), player(2, "Bernd", 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        PeriodicReport reversed = report(ReportType.WEEKLY, one.period(),
                player(2, "Bernd", 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0), player(1, "Anna", 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        assertThat(renderer.render(one)).isEqualTo(renderer.render(one));
        assertThat(renderer.render(changed).contentFingerprint()).isNotEqualTo(renderer.render(one).contentFingerprint());
        assertThat(renderer.render(reversed).contentFingerprint()).isNotEqualTo(renderer.render(one).contentFingerprint());
    }

    @Test
    void changesFingerprintWhenOnlyThePaginationStructureChanges() {
        RenderedReportField first = new RenderedReportField("A", "eins");
        RenderedReportField second = new RenderedReportField("B", "zwei");
        List<RenderedReportPage> onePage = List.of(new RenderedReportPage("Titel", List.of(first, second), Optional.empty()));
        List<RenderedReportPage> twoPages = List.of(
                new RenderedReportPage("Titel", List.of(first), Optional.of("Seite 1/2")),
                new RenderedReportPage("Titel", List.of(second), Optional.of("Seite 2/2")));

        assertThat(PeriodicReportRenderer.fingerprint(twoPages))
                .isNotEqualTo(PeriodicReportRenderer.fingerprint(onePage));
    }

    @Test
    void renderedModelsDefensivelyCopyNestedCollectionsAndExposeThemAsImmutable() {
        RenderedReportField original = new RenderedReportField("A", "eins");
        List<RenderedReportField> mutableFields = new ArrayList<>();
        mutableFields.add(original);
        RenderedReportPage page = new RenderedReportPage("Titel", mutableFields, Optional.empty());
        List<RenderedReportPage> mutablePages = new ArrayList<>();
        mutablePages.add(page);
        RenderedPeriodicReport rendered = new RenderedPeriodicReport(mutablePages, "0".repeat(64));

        mutableFields.add(new RenderedReportField("B", "zwei"));
        mutablePages.clear();

        assertThat(page.fields()).containsExactly(original);
        assertThat(rendered.pages()).containsExactly(page);
        assertThatThrownBy(() -> page.fields().add(new RenderedReportField("C", "drei")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> rendered.pages().add(page))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static List<RenderedReportField> wideFields(int nameLength, RenderedReportField shared) {
        List<RenderedReportField> fields = new ArrayList<>();
        for (int index = 0; index < 5; index++) {
            fields.add(new RenderedReportField("n".repeat(nameLength), "v".repeat(1_024)));
        }
        fields.add(shared);
        return List.copyOf(fields);
    }

    private static PeriodicReport report(ReportType type, ReportPeriod period, PeriodicReportParticipantSection... participants) {
        return report(type, period, List.of(participants));
    }

    private static PeriodicReport report(ReportType type, ReportPeriod period, List<PeriodicReportParticipantSection> participants) {
        return new PeriodicReport(type, period, participants, new de.venomenon.gridwordsbot.domain.reporting.PeriodicReportSharedSection(
                new ReportSharedDayCounts(participants.size() < 2 ? 0 : 7, participants.size() < 2 ? 0 : 5, participants.size() < 2 ? 0 : 4),
                new ReportSharedStreaks(new ReportStreakSnapshot(participants.size() < 2 ? 0 : 1, participants.size() < 2 ? 0 : 7),
                        new ReportStreakSnapshot(participants.size() < 2 ? 0 : 1, participants.size() < 2 ? 0 : 4))));
    }

    private static PeriodicReportParticipantSection player(long id, String name, int possible, int activity, int complete, int perfect,
            int submitted, int solved, int unsolved, long attempts, int solvedCount, long durationSeconds, long bestSeconds) {
        List<LocalDate> days = java.util.stream.IntStream.range(0, possible).mapToObj(index -> date(2026, 7, 27).plusDays(index)).toList();
        ReportGameStatistics game = new ReportGameStatistics(GameType.GRIDWORDS, possible, submitted, solved, unsolved, possible - submitted,
                submitted == 0 ? Optional.empty() : Optional.of(new ReportRatio(solved, submitted)), attempts, solvedCount,
                Duration.ofSeconds(durationSeconds), solvedCount,
                solvedCount == 0 ? Optional.empty() : Optional.of(Duration.ofSeconds(bestSeconds)));
        ReportGameStatistics quad = new ReportGameStatistics(GameType.QUADWORDS, possible, submitted, solved, unsolved, possible - submitted,
                submitted == 0 ? Optional.empty() : Optional.of(new ReportRatio(solved, submitted)), attempts, solvedCount,
                Duration.ofSeconds(durationSeconds), solvedCount,
                solvedCount == 0 ? Optional.empty() : Optional.of(Duration.ofSeconds(bestSeconds)));
        return new PeriodicReportParticipantSection(new ReportParticipant(
                        id, name, date(2026, 7, 1), days, days, days, days),
                new ReportPlayerGameStatistics(id, game, quad), new ReportPersonalDayCounts(possible, activity, complete, perfect),
                new ReportPersonalStreaks(new ReportStreakSnapshot(2, 8), new ReportStreakSnapshot(1, 7),
                        new ReportStreakSnapshot(3, 6), new ReportStreakSnapshot(4, 5), new ReportStreakSnapshot(1, 4)));
    }

    private static LocalDate date(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private static RecordEventSnapshot resultRecord(long playerId, LocalDate gameDate) {
        var source = new RecordSourceReference.GameResult(91, 0, playerId, GameType.GRIDWORDS, gameDate);
        var draft = new RecordEventDraft(UUID.fromString("00000000-0000-0000-0000-000000000091"), "report-result-91",
                new RecordStateKey(1, new RecordDefinitionKey("result.gridwords.fewest-attempts.personal"),
                        RecordDefinitionVersion.RECORDS_V1, new RecordScope.Personal(playerId)),
                RecordEventType.RESULT_RECORD_BROKEN, Optional.empty(),
                new AttemptsDurationRecordValue(4, Duration.ofSeconds(98)), Optional.empty(), Optional.of(playerId),
                Optional.empty(), source, "report-result-91", RecordProcessingOrigin.LIVE_SUBMISSION,
                Instant.parse("2026-08-09T12:00:00Z"));
        return new RecordEventSnapshot(draft, RecordEventValidity.VALID, Optional.empty(), Optional.empty(),
                Instant.parse("2026-08-09T12:00:00Z"), Instant.parse("2026-08-09T12:00:00Z"));
    }

    private static RecordEventSnapshot streakRecord(String eventId, RecordEventType type, int length) {
        LocalDate start = date(2026, 7, 26);
        var source = new RecordSourceReference.StreakRun(StreakRecordMetric.PERFECT,
                new RecordSourceReference.StreakRunOwner.Player(1), start);
        Instant detected = Instant.parse("2026-08-09T12:00:00Z");
        var draft = new RecordEventDraft(UUID.fromString(eventId), "report-" + eventId,
                new RecordStateKey(1, new RecordDefinitionKey("streak.perfect.personal"),
                        RecordDefinitionVersion.RECORDS_V1, new RecordScope.Personal(1)), type, Optional.empty(),
                new StreakRecordValue(length, start, start.plusDays(length - 1L)), Optional.empty(), Optional.of(1L),
                Optional.empty(), source, "report-" + eventId, RecordProcessingOrigin.DAY_CLOSE, detected);
        return new RecordEventSnapshot(draft, RecordEventValidity.VALID, Optional.empty(), Optional.empty(), detected, detected);
    }

    private static ReportStreakSnapshot zero() {
        return new ReportStreakSnapshot(0, 0);
    }
}
