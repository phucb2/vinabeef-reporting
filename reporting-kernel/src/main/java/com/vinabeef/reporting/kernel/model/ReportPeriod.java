package com.vinabeef.reporting.kernel.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.IsoFields;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A named reporting period turned into an inclusive date range.
 * Accepted forms: "2026", "2026-H1", "2026-Q2", "2026-03", "2026-W05", "2026-03-15".
 * Quarters follow the calendar year; change {@link #quarter(int, int)} for a fiscal year.
 */
public record ReportPeriod(LocalDate from, LocalDate to) {

    private static final Pattern YEAR    = Pattern.compile("(\\d{4})");
    private static final Pattern HALF    = Pattern.compile("(\\d{4})-H([12])");
    private static final Pattern QUARTER = Pattern.compile("(\\d{4})-Q([1-4])");
    private static final Pattern MONTH   = Pattern.compile("\\d{4}-\\d{2}");
    private static final Pattern WEEK    = Pattern.compile("(\\d{4})-W(\\d{2})");
    private static final Pattern DAY     = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    public ReportPeriod {
        if (to.isBefore(from)) throw new IllegalArgumentException("'to' must not be before 'from'");
    }

    public static ReportPeriod parse(String period) {
        String p = period == null ? "" : period.trim().toUpperCase();
        Matcher m;
        if ((m = YEAR.matcher(p)).matches())    return year(Integer.parseInt(m.group(1)));
        if ((m = HALF.matcher(p)).matches())    return half(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        if ((m = QUARTER.matcher(p)).matches()) return quarter(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        if (MONTH.matcher(p).matches())         return month(YearMonth.parse(p));
        if ((m = WEEK.matcher(p)).matches())    return isoWeek(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
        if (DAY.matcher(p).matches())           { LocalDate d = LocalDate.parse(p); return new ReportPeriod(d, d); }
        throw new IllegalArgumentException("Unknown period '" + period
                + "'. Use 2026, 2026-H1, 2026-Q2, 2026-03, 2026-W05 or 2026-03-15");
    }

    public static ReportPeriod year(int y)           { return new ReportPeriod(LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31)); }
    public static ReportPeriod half(int y, int h)    { return months(LocalDate.of(y, 6 * h - 5, 1), 6); }
    public static ReportPeriod quarter(int y, int q) { return months(LocalDate.of(y, 3 * q - 2, 1), 3); }
    public static ReportPeriod month(YearMonth m)    { return new ReportPeriod(m.atDay(1), m.atEndOfMonth()); }

    public static ReportPeriod isoWeek(int weekYear, int week) {
        LocalDate monday = LocalDate.of(weekYear, 1, 4)
                .with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week)
                .with(DayOfWeek.MONDAY);
        return new ReportPeriod(monday, monday.plusDays(6));
    }

    private static ReportPeriod months(LocalDate start, int n) {
        return new ReportPeriod(start, start.plusMonths(n).minusDays(1));
    }
}
