package com.vinabeef.reporting.kernel.model;

import java.time.Instant;

/** KPIs for the executive dashboard (FR-13). */
public record DashboardSummary(long totalCattle, long treatedInPeriod, long movementsToday,
                               Instant asOf, boolean fromSnapshot) {

    /** Report type the kernel asks for when building the dashboard. Provided by a definition plugin. */
    public static final String REPORT_TYPE = "dashboard-kpis";

    public static DashboardSummary from(ReportDataSet data) {
        if (data.isEmpty()) return new DashboardSummary(0, 0, 0, data.generatedAt(), false);
        Row r = data.rows().get(0);
        return new DashboardSummary(asLong(r.get("total_cattle")), asLong(r.get("treated_count")),
                                    asLong(r.get("movements_today")), data.generatedAt(), false);
    }

    public DashboardSummary asSnapshot() {
        return new DashboardSummary(totalCattle, treatedInPeriod, movementsToday, asOf, true);
    }

    private static long asLong(Object v) { return v instanceof Number n ? n.longValue() : 0L; }
}
