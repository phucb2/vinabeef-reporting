package com.vinabeef.reporting.plugins.definitions;

import com.vinabeef.reporting.kernel.model.ColumnDef;
import com.vinabeef.reporting.kernel.model.ColumnType;

import java.util.List;

/**
 * TIMEFRAME EXAMPLE: treatments per period and barn.
 * The caller chooses the bucket with granularity=DAY|WEEK|MONTH|QUARTER|YEAR (default MONTH),
 * e.g. period=2026&granularity=QUARTER → one row per quarter and barn.
 * Buckets are cut in farm local time, so a treatment at 23:30 on 31 March stays in Q1.
 */
public class TreatmentTrendReport extends AbstractSqlReportDefinition {

    public static final String TYPE = "treatment-trend";

    @Override public String reportType() { return TYPE; }
    @Override public String title()      { return "Treatment trend"; }

    @Override
    public List<ColumnDef> columns() {
        return List.of(
                ColumnDef.of("period_start",    "Period start",    ColumnType.DATE, "dd/MM/yyyy"),
                ColumnDef.of("barn",            "Barn",            ColumnType.STRING),
                ColumnDef.of("treatments",      "Treatments",      ColumnType.INTEGER, "#,##0"),
                ColumnDef.of("animals_treated", "Animals treated", ColumnType.INTEGER, "#,##0"));
    }

    @Override
    public String sql() {
        return """
            SELECT date_trunc(:bucket, h.performed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS period_start,
                   h.barn_id                   AS barn,
                   COUNT(*)                    AS treatments,
                   COUNT(DISTINCT h.animal_id) AS animals_treated
            FROM health.health_record h
            WHERE h.kind = 'TREATMENT' AND h.status = 'ACTIVE'
              AND h.performed_at >= :from AND h.performed_at < :toExclusive
              AND {barnFilter}
            GROUP BY 1, 2
            ORDER BY 1, 2
            """.replace("{barnFilter}", barnFilter("h.barn_id"));
    }
}
