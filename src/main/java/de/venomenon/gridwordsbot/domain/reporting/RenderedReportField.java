package de.venomenon.gridwordsbot.domain.reporting;

import java.util.Objects;

/** One visible field of a transport-neutral Discord-compatible report page. */
public record RenderedReportField(String name, String value) {
    public static final int MAX_NAME_LENGTH = 256;
    public static final int MAX_VALUE_LENGTH = 1_024;

    public RenderedReportField {
        name = Objects.requireNonNull(name, "name");
        value = Objects.requireNonNull(value, "value");
        if (name.length() > MAX_NAME_LENGTH) throw new ReportRenderingException("report field name exceeds Discord limit");
        if (value.length() > MAX_VALUE_LENGTH) throw new ReportRenderingException("report field value exceeds Discord limit");
    }
}
