package com.vinabeef.reporting.kernel.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Input of every report.
 * barnId == null means "all barns"; granularity says how rows are bucketed in time (NONE = whole range).
 */
public record ReportFilter(String reportType, String barnId, LocalDate from, LocalDate to, Granularity granularity) {

    public ReportFilter {
        Objects.requireNonNull(reportType, "reportType");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) throw new IllegalArgumentException("'to' must not be before 'from'");
        granularity = granularity == null ? Granularity.NONE : granularity;
    }

    /** Range without time buckets. */
    public ReportFilter(String reportType, String barnId, LocalDate from, LocalDate to) {
        this(reportType, barnId, from, to, Granularity.NONE);
    }

    public static ReportFilter of(String reportType, String barnId, ReportPeriod period, Granularity granularity) {
        return new ReportFilter(reportType, barnId, period.from(), period.to(), granularity);
    }

    public ReportFilter withReportType(String type) { return new ReportFilter(type, barnId, from, to, granularity); }

    /** Half-open upper bound for timestamp columns: [from, to + 1 day). */
    public LocalDate toExclusive() { return to.plusDays(1); }

    /** The granularity to use when the caller gave none. */
    public Granularity granularityOr(Granularity defaultValue) {
        return granularity == Granularity.NONE ? defaultValue : granularity;
    }
}
