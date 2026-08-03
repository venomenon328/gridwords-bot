package de.venomenon.gridwordsbot.application.reporting;

import de.venomenon.gridwordsbot.domain.model.GameType;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
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
