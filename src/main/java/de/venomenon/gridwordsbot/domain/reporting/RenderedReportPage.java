package de.venomenon.gridwordsbot.domain.reporting;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One complete, limit-checked visible embed page without Discord SDK types. */
public record RenderedReportPage(String title, List<RenderedReportField> fields, Optional<String> footer) {
    public static final int MAX_TITLE_LENGTH = 256;
    public static final int MAX_FIELDS = 25;
    public static final int MAX_FOOTER_LENGTH = 2_048;
    public static final int MAX_VISIBLE_LENGTH = 6_000;

    public RenderedReportPage {
        title = Objects.requireNonNull(title, "title");
        fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
        footer = Objects.requireNonNull(footer, "footer");
        if (title.length() > MAX_TITLE_LENGTH) throw new ReportRenderingException("report title exceeds Discord limit");
        if (fields.size() > MAX_FIELDS) throw new ReportRenderingException("report page exceeds Discord field limit");
        footer.ifPresent(value -> {
            if (value.length() > MAX_FOOTER_LENGTH) {
                throw new ReportRenderingException("report footer exceeds Discord limit");
            }
        });
        if (visibleLength(title, fields, footer) > MAX_VISIBLE_LENGTH) {
            throw new ReportRenderingException("report page exceeds Discord visible character limit");
        }
    }

    public int visibleLength() {
        return visibleLength(title, fields, footer);
    }

    private static int visibleLength(String title, List<RenderedReportField> fields, Optional<String> footer) {
        return title.length()
                + fields.stream().mapToInt(field -> field.name().length() + field.value().length()).sum()
                + footer.map(String::length).orElse(0);
    }
}
