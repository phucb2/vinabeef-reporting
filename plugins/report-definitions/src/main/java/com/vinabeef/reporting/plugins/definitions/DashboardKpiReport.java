package com.vinabeef.reporting.plugins.definitions;

import com.vinabeef.reporting.kernel.model.ColumnDef;
import com.vinabeef.reporting.kernel.model.ColumnType;
import com.vinabeef.reporting.kernel.model.DashboardSummary;

import java.util.List;

/** Single-row KPI query used by the dashboard (FR-13). */
public class DashboardKpiReport extends AbstractSqlReportDefinition {

    @Override public String reportType() { return DashboardSummary.REPORT_TYPE; }
    @Override public String title()      { return "Dashboard KPIs"; }

    @Override
    public List<ColumnDef> columns() {
        return List.of(
                ColumnDef.of("total_cattle",    "Total cattle",    ColumnType.INTEGER),
                ColumnDef.of("treated_count",   "Treated",         ColumnType.INTEGER),
                ColumnDef.of("movements_today", "Movements today", ColumnType.INTEGER));
    }

    @Override
    public String sql() {
        return """
            SELECT
              (SELECT COUNT(*) FROM cattle_tag.cattle c
                WHERE c.status = 'ACTIVE' AND {cattleBarn})                         AS total_cattle,
              (SELECT COUNT(DISTINCT h.animal_id) FROM health.health_record h
                WHERE h.kind = 'TREATMENT' AND h.status = 'ACTIVE'
                  AND h.performed_at >= :from AND h.performed_at < :toExclusive
                  AND {healthBarn})                                                 AS treated_count,
              (SELECT COUNT(*) FROM movement.movement m
                WHERE m.occurred_at >= date_trunc('day', now() AT TIME ZONE 'Asia/Ho_Chi_Minh')
                                       AT TIME ZONE 'Asia/Ho_Chi_Minh'
                  AND {movementBarn})                                               AS movements_today
            """.replace("{cattleBarn}", barnFilter("c.expected_barn_id"))
                 .replace("{healthBarn}", barnFilter("h.barn_id"))
                 .replace("{movementBarn}", barnFilter("m.barn_id"));
    }
}
