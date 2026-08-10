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
import de.venomenon.gridwordsbot.domain.reporting.ReportPeriod;
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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Renders complete reports into deterministic, Discord-safe transport-neutral pages. */
public final class PeriodicReportRenderer {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM", Locale.GERMAN);
    private static final String UNDEFINED = "—";

    public RenderedPeriodicReport render(PeriodicReport report) {
        Objects.requireNonNull(report, "report");
        String range = dateRange(report.period());
        String statisticsTitle = statisticsTitle(report.reportType(), range);
        int calendarDays = Math.toIntExact(ChronoUnit.DAYS.between(
                report.period().startDate(), report.period().endDate()) + 1);

        List<RenderedReportField> statistics = new ArrayList<>();
        report.participants().forEach(section -> statistics.add(personalField(section, calendarDays)));
        statistics.add(sharedField(report));

        List<PageSection> sections = new ArrayList<>();
        sections.add(new PageSection(statisticsTitle, statistics));
        List<RenderedReportField> highlights = highlightFields(report);
        if (!highlights.isEmpty()) {
            sections.add(new PageSection(highlightTitle(report.reportType(), range), highlights));
        }

        List<RenderedReportPage> pages = paginateSections(sections);
        return new RenderedPeriodicReport(pages, fingerprint(pages));
    }

    private static String statisticsTitle(ReportType type, String range) {
        String label = type == ReportType.WEEKLY ? "Wochenbericht" : "Monatsbericht";
        return "📊 " + label + " · " + range;
    }

    private static String highlightTitle(ReportType type, String range) {
        return (type == ReportType.WEEKLY ? "✨ Highlights der Woche · " : "✨ Highlights des Monats · ") + range;
    }

    private static String dateRange(ReportPeriod period) {
        LocalDate start = period.startDate();
        LocalDate end = period.endDate();
        if (start.equals(end)) {
            return start.getDayOfMonth() + ". " + start.format(MONTH) + " " + start.getYear();
        }
        if (start.getYear() == end.getYear() && start.getMonth() == end.getMonth()) {
            return start.getDayOfMonth() + ".–" + end.getDayOfMonth() + ". "
                    + end.format(MONTH) + " " + end.getYear();
        }
        if (start.getYear() == end.getYear()) {
            return start.getDayOfMonth() + ". " + start.format(MONTH) + "–"
                    + end.getDayOfMonth() + ". " + end.format(MONTH) + " " + end.getYear();
        }
        return start.getDayOfMonth() + ". " + start.format(MONTH) + " " + start.getYear() + "–"
                + end.getDayOfMonth() + ". " + end.format(MONTH) + " " + end.getYear();
    }

    private static RenderedReportField personalField(
            PeriodicReportParticipantSection section,
            int calendarDays) {
        var days = section.dayCounts();
        var streaks = section.streaks();
        String name = fieldName("👤 ", section.participant().displayName());
        String value = "📅 Teilnahme " + days.participationDays() + "/" + calendarDays
                + " · Aktiv " + days.activityDays()
                + " · Komplett " + days.completeDays()
                + " · Perfekt " + days.perfectDays()
                + "\n" + gameStatistics(section.gameStatistics().gridWords())
                + "\n" + gameStatistics(section.gameStatistics().quadWords())
                + "\n🔥 Serien (Stand/Rekord)"
                + "\n↳ Aktiv " + streak(streaks.activity())
                + " · Komplett " + streak(streaks.complete())
                + " · GW " + streak(streaks.gridWordsSolved())
                + " · QW " + streak(streaks.quadWordsSolved())
                + " · Perfekt " + streak(streaks.perfect());
        return new RenderedReportField(name, value);
    }

    private static String gameStatistics(ReportGameStatistics statistics) {
        String game = statistics.gameType() == GameType.GRIDWORDS ? "🟩 GW" : "🟦 QW";
        if (statistics.possibleDays() == 0) {
            return game + " — keine Teilnahme";
        }
        if (statistics.submitted() == 0) {
            return game + " 0/" + statistics.possibleDays() + " · " + statistics.missing()
                    + "⬜ · keine Einreichung";
        }

        StringBuilder line = new StringBuilder(game)
                .append(' ').append(statistics.submitted()).append('/').append(statistics.possibleDays());
        if (statistics.solved() == statistics.submitted() && statistics.missing() == 0) {
            line.append(" ✅");
        } else {
            List<String> outcomes = new ArrayList<>();
            if (statistics.solved() > 0) outcomes.add(statistics.solved() + "✅");
            if (statistics.unsolved() > 0) outcomes.add(statistics.unsolved() + "❌");
            if (statistics.missing() > 0) outcomes.add(statistics.missing() + "⬜");
            line.append(" · ").append(String.join(" ", outcomes));
        }
        line.append(" · ").append(percentage(statistics.solved(), statistics.submitted()));
        if (statistics.solved() > 0) {
            line.append(" · ØVers. ").append(average(statistics.solvedAttemptsTotal(), statistics.solvedAttemptsCount()))
                    .append(" · ØZeit ").append(averageDuration(
                            statistics.solvedDurationTotal(), statistics.solvedDurationCount()))
                    .append(" · Best ").append(statistics.bestSolvedDuration()
                            .map(PeriodicReportRenderer::duration).orElseThrow());
        }
        return line.toString();
    }

    private static RenderedReportField sharedField(PeriodicReport report) {
        var days = report.shared().dayCounts();
        var streaks = report.shared().streaks();
        String dayLine = days.sharedPossibleDays() == 0
                ? "📅 Keine gemeinsam möglichen Tage"
                : "📅 Möglich " + days.sharedPossibleDays()
                        + " · Komplett " + days.completeDays()
                        + " · Perfekt " + days.perfectDays();
        return new RenderedReportField("🤝 Gemeinsam",
                dayLine
                        + "\n🔥 Serien (Stand/Rekord)"
                        + "\n↳ Komplett " + streak(streaks.complete())
                        + " · Perfekt " + streak(streaks.perfect()));
    }

    private static List<RenderedReportField> highlightFields(PeriodicReport report) {
        List<RenderedReportField> fields = new ArrayList<>();
        List<String> awards = new ArrayList<>();
        for (PeriodicReportParticipantSection section : report.participants()) {
            Integer count = report.highlights().activeAwardsByParticipant().get(section.participant().discordUserId());
            if (count != null) {
                awards.add(safeDisplayName(section.participant().displayName()) + " · **" + count + "** freigeschaltet");
            }
        }
        addSplitFields(fields, "🏅 Achievements", awards, "\n");

        Map<Long, String> names = new LinkedHashMap<>();
        report.participants().forEach(section -> names.put(section.participant().discordUserId(),
                safeDisplayName(section.participant().displayName())));
        List<String> records = deduplicatedRecordEvents(report.highlights().recordEvents()).stream()
                .map(event -> recordBlock(event, names))
                .toList();
        addSplitFields(fields, "🏆 Rekorde", records, "\n\n");
        return List.copyOf(fields);
    }

    private static void addSplitFields(
            List<RenderedReportField> fields,
            String title,
            List<String> blocks,
            String separator) {
        if (blocks.isEmpty()) return;
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String block : blocks) {
            if (block.length() > RenderedReportField.MAX_VALUE_LENGTH) {
                throw new ReportRenderingException("report highlight block exceeds Discord field limit");
            }
            int separatorLength = current.isEmpty() ? 0 : separator.length();
            if (current.length() + separatorLength + block.length() > RenderedReportField.MAX_VALUE_LENGTH) {
                chunks.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append(separator);
            current.append(block);
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

    private static String recordBlock(RecordEventSnapshot event, Map<Long, String> names) {
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
        String game = definition.game().map(value -> value == GameType.GRIDWORDS ? " GridWords" : " QuadWords").orElse("");
        return "**" + subject + "** · " + scope + game + "-Rekord\n↳ "
                + metric(definition) + " · **" + value(event.draft().newValue()) + "**";
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
        String value = decimal(BigDecimal.valueOf(solved).multiply(BigDecimal.valueOf(100)), submitted);
        return (value.endsWith(",0") ? value.substring(0, value.length() - 2) : value) + " %";
    }

    private static String average(long total, int count) {
        return decimal(BigDecimal.valueOf(total), count);
    }

    private static String decimal(BigDecimal numerator, int denominator) {
        return numerator.divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP)
                .toPlainString().replace('.', ',');
    }

    private static String averageDuration(Duration total, int count) {
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

    private static String fieldName(String prefix, String displayName) {
        String value = prefix + safeDisplayName(displayName);
        return truncateUtf16(value, RenderedReportField.MAX_NAME_LENGTH);
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

    private static String truncateUtf16(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        int end = maxLength;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))
                && end < value.length() && Character.isLowSurrogate(value.charAt(end))) {
            end--;
        }
        return value.substring(0, end);
    }

    static List<RenderedReportPage> paginate(String title, List<RenderedReportField> fields) {
        return paginateSections(List.of(new PageSection(title, fields)));
    }

    private static List<RenderedReportPage> paginateSections(List<PageSection> sections) {
        int fieldCount = sections.stream().mapToInt(section -> section.fields().size()).sum();
        int assumedPageCount = 1;
        List<RenderedReportPage> pages = List.of();
        for (int attempt = 0; attempt <= fieldCount + sections.size() + 1; attempt++) {
            pages = paginateSections(sections, assumedPageCount);
            if (pages.size() == assumedPageCount) return pages;
            assumedPageCount = pages.size();
        }
        throw new ReportRenderingException("report page count did not stabilize");
    }

    private static List<RenderedReportPage> paginateSections(List<PageSection> sections, int pageCount) {
        List<PageDraft> drafts = new ArrayList<>();
        for (PageSection section : sections) {
            List<RenderedReportField> current = new ArrayList<>();
            for (RenderedReportField field : section.fields()) {
                int pageNumber = drafts.size() + 1;
                if (!fits(section.title(), current, field, pageNumber, pageCount)) {
                    if (current.isEmpty()) {
                        throw new ReportRenderingException("report section cannot fit on a Discord page");
                    }
                    drafts.add(new PageDraft(section.title(), List.copyOf(current)));
                    current.clear();
                    pageNumber = drafts.size() + 1;
                    if (!fits(section.title(), current, field, pageNumber, pageCount)) {
                        throw new ReportRenderingException("report section cannot fit on a Discord page");
                    }
                }
                current.add(field);
            }
            if (!current.isEmpty()) {
                drafts.add(new PageDraft(section.title(), List.copyOf(current)));
            }
        }

        List<RenderedReportPage> pages = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            PageDraft draft = drafts.get(index);
            Optional<String> footer = pageCount == 1
                    ? Optional.empty()
                    : Optional.of("Seite " + (index + 1) + "/" + pageCount);
            pages.add(new RenderedReportPage(draft.title(), draft.fields(), footer));
        }
        return List.copyOf(pages);
    }

    private static boolean fits(
            String title,
            List<RenderedReportField> current,
            RenderedReportField next,
            int page,
            int pageCount) {
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

    private record PageSection(String title, List<RenderedReportField> fields) {
        private PageSection {
            Objects.requireNonNull(title, "title");
            fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
            if (fields.isEmpty()) throw new IllegalArgumentException("report page section needs fields");
        }
    }

    private record PageDraft(String title, List<RenderedReportField> fields) { }
}
