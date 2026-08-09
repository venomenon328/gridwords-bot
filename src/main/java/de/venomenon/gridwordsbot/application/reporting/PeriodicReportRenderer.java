package de.venomenon.gridwordsbot.application.reporting;

import de.venomenon.gridwordsbot.domain.model.GameType;
import de.venomenon.gridwordsbot.domain.record.AttemptsDurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.DurationRecordValue;
import de.venomenon.gridwordsbot.domain.record.RecordDefinition;
import de.venomenon.gridwordsbot.domain.record.RecordDefinitionCatalog;
import de.venomenon.gridwordsbot.domain.record.RecordEventSnapshot;
import de.venomenon.gridwordsbot.domain.record.RecordEventType;
import de.venomenon.gridwordsbot.domain.record.RecordScope;
import de.venomenon.gridwordsbot.domain.record.RecordSourceReference;
import de.venomenon.gridwordsbot.domain.record.RecordValue;
import de.venomenon.gridwordsbot.domain.record.ResultRecordMetric;
import de.venomenon.gridwordsbot.domain.record.StreakRecordMetric;
import de.venomenon.gridwordsbot.domain.record.StreakRecordValue;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReport;
import de.venomenon.gridwordsbot.domain.reporting.PeriodicReportParticipantSection;
import de.venomenon.gridwordsbot.domain.reporting.RenderedPeriodicReport;
import de.venomenon.gridwordsbot.domain.reporting.RenderedReportField;
import de.venomenon.gridwordsbot.domain.reporting.RenderedReportPage;
import de.venomenon.gridwordsbot.domain.reporting.ReportGameStatistics;
import de.venomenon.gridwordsbot.domain.reporting.ReportRenderingException;
import de.venomenon.gridwordsbot.domain.reporting.ReportStreakSnapshot;
import de.venomenon.gridwordsbot.domain.reporting.ReportType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Renders complete reports into deterministic, Discord-safe transport-neutral pages. */
public final class PeriodicReportRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d. MMMM uuuu", Locale.GERMAN);
    private static final String UNDEFINED = "—";

    public RenderedPeriodicReport render(PeriodicReport report) {
        Objects.requireNonNull(report, "report");
        String title = title(report.reportType(), report.period().startDate().format(DATE), report.period().endDate().format(DATE));
        List<RenderedReportField> fields = new ArrayList<>();
        report.participants().forEach(section -> fields.add(personalField(section)));
        fields.add(sharedField(report));
        fields.addAll(highlightFields(report));
        List<RenderedReportPage> pages = paginate(title, fields);
        return new RenderedPeriodicReport(pages, fingerprint(pages));
    }

    private static String title(ReportType type, String start, String end) {
        String label = type == ReportType.WEEKLY ? "Wochenbericht" : "Monatsbericht";
        return label + " · " + start + " bis " + end;
    }

    private static RenderedReportField personalField(PeriodicReportParticipantSection section) {
        var days = section.dayCounts();
        var streaks = section.streaks();
        return new RenderedReportField(safeDisplayName(section.participant().displayName()),
                "Teilnahme: " + days.participationDays() + " · Aktivität: " + days.activityDays()
                        + " · Komplett: " + days.completeDays() + " · Perfekt: " + days.perfectDays()
                        + "\nGridWords\n" + gameStatistics(section.gameStatistics().gridWords())
                        + "\nQuadWords\n" + gameStatistics(section.gameStatistics().quadWords())
                        + "\nSerien (Stand/Rekord)\nAktivität: " + streak(streaks.activity())
                        + " · Komplett: " + streak(streaks.complete())
                        + "\nGridWords gelöst: " + streak(streaks.gridWordsSolved())
                        + " · QuadWords gelöst: " + streak(streaks.quadWordsSolved())
                        + " · Perfekt: " + streak(streaks.perfect()));
    }

    private static String gameStatistics(ReportGameStatistics statistics) {
        if (statistics.possibleDays() == 0) return "Nicht teilgenommen";
        return "Eingereicht: " + statistics.submitted() + "/" + statistics.possibleDays()
                + " · Gelöst: " + statistics.solved() + " · Nicht gelöst: " + statistics.unsolved()
                + " · Fehlend: " + statistics.missing()
                + "\nQuote: " + percentage(statistics.solutionRate().map(rate -> rate.numerator()).orElse(0), statistics.submitted())
                + " · Ø Versuche: " + average(statistics.solvedAttemptsTotal(), statistics.solvedAttemptsCount())
                + " · Ø Zeit: " + averageDuration(statistics.solvedDurationTotal(), statistics.solvedDurationCount())
                + " · Bestzeit: " + statistics.bestSolvedDuration().map(PeriodicReportRenderer::duration).orElse(UNDEFINED);
    }

    private static RenderedReportField sharedField(PeriodicReport report) {
        var days = report.shared().dayCounts();
        var streaks = report.shared().streaks();
        return new RenderedReportField("Gemeinsam",
                "Mögliche Tage: " + days.sharedPossibleDays() + " · Komplette Tage: " + days.completeDays()
                        + " · Perfekte Tage: " + days.perfectDays()
                        + "\nKomplettserie (Stand/Rekord): " + streak(streaks.complete())
                        + " · Perfektserie (Stand/Rekord): " + streak(streaks.perfect()));
    }

    private static List<RenderedReportField> highlightFields(PeriodicReport report) {
        List<RenderedReportField> fields = new ArrayList<>();
        List<String> awards = new ArrayList<>();
        for (PeriodicReportParticipantSection section : report.participants()) {
            Integer count = report.highlights().activeAwardsByParticipant().get(section.participant().discordUserId());
            if (count != null) {
                awards.add(safeDisplayName(section.participant().displayName()) + ": " + count
                        + (count == 1 ? " Achievement freigeschaltet" : " Achievements freigeschaltet"));
            }
        }
        addSplitFields(fields, "🏅 Achievements", awards);

        Map<Long, String> names = new LinkedHashMap<>();
        report.participants().forEach(section -> names.put(section.participant().discordUserId(),
                safeDisplayName(section.participant().displayName())));
        List<String> records = deduplicatedRecordEvents(report.highlights().recordEvents()).stream()
                .map(event -> recordLine(event, names))
                .toList();
        addSplitFields(fields, "🏆 Rekorde", records);
        return List.copyOf(fields);
    }

    private static void addSplitFields(List<RenderedReportField> fields, String title, List<String> lines) {
        if (lines.isEmpty()) return;
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            if (line.length() > RenderedReportField.MAX_VALUE_LENGTH) {
                throw new ReportRenderingException("report highlight line exceeds Discord field limit");
            }
            int separator = current.isEmpty() ? 0 : 1;
            if (current.length() + separator + line.length() > RenderedReportField.MAX_VALUE_LENGTH) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append('\n');
            current.append(line);
        }
        if (!current.isEmpty()) chunks.add(current.toString());
        for (int index = 0; index < chunks.size(); index++) {
            fields.add(new RenderedReportField(index == 0 ? title : title + " (Fortsetzung)", chunks.get(index)));
        }
    }

    private static List<RecordEventSnapshot> deduplicatedRecordEvents(List<RecordEventSnapshot> events) {
        Map<StreakRecordIdentity, RecordEventSnapshot> streaks = new LinkedHashMap<>();
        List<RecordEventSnapshot> results = new ArrayList<>();
        for (RecordEventSnapshot event : events) {
            if (event.draft().newSource() instanceof RecordSourceReference.StreakRun source) {
                StreakRecordIdentity key = new StreakRecordIdentity(event.draft().stateKey(), source);
                RecordEventSnapshot existing = streaks.get(key);
                if (existing == null || preferredStreakEvent(event, existing)) streaks.put(key, event);
            } else {
                results.add(event);
            }
        }
        results.addAll(streaks.values());
        return results.stream().sorted(Comparator
                .comparing(PeriodicReportRenderer::businessDate)
                .thenComparing(event -> event.draft().stateKey().definitionKey().value())
                .thenComparing(event -> event.draft().stateKey().scopeKey())
                .thenComparing(event -> event.draft().newSource().toString())
                .thenComparing(event -> event.draft().detectedAt())
                .thenComparing(event -> event.draft().eventId())).toList();
    }

    private static boolean preferredStreakEvent(RecordEventSnapshot candidate, RecordEventSnapshot current) {
        boolean candidateFinish = candidate.draft().type() == RecordEventType.RECORD_SERIES_FINISHED;
        boolean currentFinish = current.draft().type() == RecordEventType.RECORD_SERIES_FINISHED;
        if (candidateFinish != currentFinish) return candidateFinish;
        int dateComparison = businessDate(candidate).compareTo(businessDate(current));
        if (dateComparison != 0) return dateComparison > 0;
        return candidate.draft().detectedAt().isAfter(current.draft().detectedAt())
                || (candidate.draft().detectedAt().equals(current.draft().detectedAt())
                && candidate.draft().eventId().compareTo(current.draft().eventId()) > 0);
    }

    private static LocalDate businessDate(RecordEventSnapshot event) {
        return switch (event.draft().newSource()) {
            case RecordSourceReference.GameResult result -> result.gameDate();
            case RecordSourceReference.StreakRun ignored -> ((StreakRecordValue) event.draft().newValue()).endDate();
        };
    }

    private static String recordLine(RecordEventSnapshot event, Map<Long, String> names) {
        RecordDefinition<?> definition = RecordDefinitionCatalog.recordsV1().find(event.draft().stateKey().definitionKey())
                .orElseThrow(() -> new IllegalStateException("record event references an unknown definition"));
        String subject = switch (event.draft().stateKey().scope()) {
            case RecordScope.Shared ignored -> "Gemeinsam";
            case RecordScope.Personal personal -> playerName(personal.playerId(), event, names);
            case RecordScope.ServerIndividual ignored -> playerName(event.draft().newHolderPlayerId()
                    .orElseGet(() -> playerId(event.draft().newSource())), event, names);
        };
        String scope = switch (event.draft().stateKey().scope()) {
            case RecordScope.Personal ignored -> "persönlicher";
            case RecordScope.ServerIndividual ignored -> "serverweiter";
            case RecordScope.Shared ignored -> "gemeinsamer";
        };
        String game = definition.game().map(value -> value == GameType.GRIDWORDS ? "GridWords " : "QuadWords ").orElse("");
        return subject + ": neuer " + scope + " " + game + "Rekord · " + metric(definition) + " · "
                + value(event.draft().newValue());
    }

    private static long playerId(RecordSourceReference source) {
        if (source instanceof RecordSourceReference.GameResult result) return result.playerId();
        if (source instanceof RecordSourceReference.StreakRun streak
                && streak.owner() instanceof RecordSourceReference.StreakRunOwner.Player player) return player.playerId();
        throw new IllegalStateException("individual record event has no player source");
    }

    private static String playerName(long playerId, RecordEventSnapshot event, Map<Long, String> names) {
        String name = names.get(playerId);
        if (name == null) throw new IllegalStateException("record event player is not a report participant: " + event.draft().eventId());
        return name;
    }

    private static String metric(RecordDefinition<?> definition) {
        return switch (definition.metric()) {
            case ResultRecordMetric.FEWEST_ATTEMPTS -> "wenigste Versuche";
            case ResultRecordMetric.FASTEST_SOLUTION -> "Bestzeit";
            case ResultRecordMetric.SLOWEST_SUCCESSFUL_SOLUTION -> "langsamste erfolgreiche Lösung";
            case StreakRecordMetric.ACTIVITY -> "Aktivitätsserie";
            case StreakRecordMetric.COMPLETE -> "Komplettserie";
            case StreakRecordMetric.GRIDWORDS_SOLVED -> "GridWords-Lösungsserie";
            case StreakRecordMetric.QUADWORDS_SOLVED -> "QuadWords-Lösungsserie";
            case StreakRecordMetric.PERFECT -> "Perfektserie";
            case StreakRecordMetric.GRIDWORDS_DROUGHT -> "GridWords-Durststrecke";
            case StreakRecordMetric.QUADWORDS_DROUGHT -> "QuadWords-Durststrecke";
            case StreakRecordMetric.WITHOUT_PERFECT_DAY -> "Tage ohne perfekten Tag";
        };
    }

    private static String value(RecordValue value) {
        return switch (value) {
            case AttemptsDurationRecordValue attempts -> attempts.attempts() + " Versuche · " + duration(attempts.duration());
            case DurationRecordValue duration -> duration(duration.duration());
            case StreakRecordValue streak -> streak.length() + (streak.length() == 1 ? " Tag" : " Tage");
        };
    }

    private record StreakRecordIdentity(
            de.venomenon.gridwordsbot.domain.record.RecordStateKey stateKey,
            RecordSourceReference.StreakRun source) { }

    private static String percentage(int solved, int submitted) {
        return submitted == 0 ? UNDEFINED : decimal(BigDecimal.valueOf(solved).multiply(BigDecimal.valueOf(100)), submitted) + " %";
    }

    private static String average(long total, int count) {
        return count == 0 ? UNDEFINED : decimal(BigDecimal.valueOf(total), count);
    }

    private static String decimal(BigDecimal numerator, int denominator) {
        return numerator.divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static String averageDuration(Duration total, int count) {
        if (count == 0) return UNDEFINED;
        BigDecimal seconds = BigDecimal.valueOf(total.getSeconds())
                .add(BigDecimal.valueOf(total.getNano()).movePointLeft(9));
        return duration(seconds.divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP).longValueExact());
    }

    private static String duration(Duration value) {
        return duration(BigDecimal.valueOf(value.getSeconds()).add(BigDecimal.valueOf(value.getNano()).movePointLeft(9))
                .setScale(0, RoundingMode.HALF_UP).longValueExact());
    }

    private static String duration(long seconds) {
        long hours = seconds / 3_600;
        long minutes = (seconds % 3_600) / 60;
        long remainingSeconds = seconds % 60;
        return hours == 0 ? "%d:%02d".formatted(minutes, remainingSeconds)
                : "%d:%02d:%02d".formatted(hours, minutes, remainingSeconds);
    }

    private static String streak(ReportStreakSnapshot streak) {
        return streak.currentAtPeriodEnd() + "/" + streak.allTimeRecordThroughPeriodEnd();
    }

    private static String safeDisplayName(String displayName) {
        StringBuilder safe = new StringBuilder();
        for (int index = 0; index < displayName.length(); index++) {
            char value = displayName.charAt(index);
            safe.append(switch (value) {
                case '@' -> '＠'; case '<' -> '‹'; case '>' -> '›'; case '*' -> '＊'; case '_' -> '＿';
                case '~' -> '～'; case '`' -> '｀'; case '|' -> '｜'; case '[' -> '［'; case ']' -> '］';
                case '(' -> '（'; case ')' -> '）'; case '#' -> '＃'; case '\\' -> '＼';
                default -> Character.isISOControl(value) ? ' ' : value;
            });
        }
        String normalized = safe.toString().trim().replaceAll(" +", " ");
        return normalized.isEmpty() ? UNDEFINED : normalized;
    }

    static List<RenderedReportPage> paginate(String title, List<RenderedReportField> fields) {
        List<RenderedReportPage> pages = List.of();
        int assumedPageCount = 1;
        for (int attempt = 0; attempt <= fields.size() + 1; attempt++) {
            pages = paginate(title, fields, assumedPageCount);
            if (pages.size() == assumedPageCount) return pages;
            assumedPageCount = pages.size();
        }
        throw new ReportRenderingException("report page count did not stabilize");
    }

    private static List<RenderedReportPage> paginate(String title, List<RenderedReportField> fields, int pageCount) {
        List<List<RenderedReportField>> pageFields = new ArrayList<>();
        List<RenderedReportField> current = new ArrayList<>();
        for (RenderedReportField field : fields) {
            if (!fits(title, current, field, pageFields.size() + 1, pageCount)) {
                if (current.isEmpty()) throw new ReportRenderingException("report section cannot fit on a Discord page");
                pageFields.add(List.copyOf(current));
                current.clear();
                if (!fits(title, current, field, pageFields.size() + 1, pageCount)) {
                    throw new ReportRenderingException("report section cannot fit on a Discord page");
                }
            }
            current.add(field);
        }
        pageFields.add(List.copyOf(current));
        List<RenderedReportPage> pages = new ArrayList<>();
        for (int index = 0; index < pageFields.size(); index++) {
            Optional<String> footer = pageCount == 1 ? Optional.empty() : Optional.of("Seite " + (index + 1) + "/" + pageCount);
            pages.add(new RenderedReportPage(title, pageFields.get(index), footer));
        }
        return List.copyOf(pages);
    }

    private static boolean fits(String title, List<RenderedReportField> current, RenderedReportField next, int page, int pageCount) {
        if (current.size() == RenderedReportPage.MAX_FIELDS) return false;
        Optional<String> footer = pageCount == 1 ? Optional.empty() : Optional.of("Seite " + page + "/" + pageCount);
        try {
            List<RenderedReportField> candidate = new ArrayList<>(current);
            candidate.add(next);
            new RenderedReportPage(title, candidate, footer);
            return true;
        } catch (ReportRenderingException exception) {
            return false;
        }
    }

    static String fingerprint(List<RenderedReportPage> pages) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, "pages", Integer.toString(pages.size()));
        for (RenderedReportPage page : pages) {
            append(canonical, "title", page.title());
            append(canonical, "fields", Integer.toString(page.fields().size()));
            for (RenderedReportField field : page.fields()) {
                append(canonical, "name", field.name());
                append(canonical, "value", field.value());
            }
            append(canonical, "footer", page.footer().orElse(""));
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void append(StringBuilder target, String label, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        target.append(label).append(':').append(bytes.length).append(':').append(value);
    }
}
