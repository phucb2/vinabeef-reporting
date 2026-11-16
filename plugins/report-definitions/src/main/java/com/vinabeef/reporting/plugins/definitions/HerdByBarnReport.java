package com.vinabeef.reporting.plugins.definitions;

import com.vinabeef.reporting.kernel.model.ColumnDef;
import com.vinabeef.reporting.kernel.model.ColumnType;

import java.util.List;

/** Active head count per barn and how many animals were treated in the period. */
public class HerdByBarnReport extends AbstractSqlReportDefinition {

    @Override public String reportType() { return "herd-by-barn"; }
    @Override public String title()      { return "Herd by barn"; }

    @Override
    public List<ColumnDef> columns() {
        return List.of(
                ColumnDef.of("barn",              "Barn",              ColumnType.STRING),
                ColumnDef.of("total_head",        "Total head",        ColumnType.INTEGER, "#,##0"),
                ColumnDef.of("treated_in_period", "Treated in period", ColumnType.INTEGER, "#,##0"));
    }

    @Override
    public String sql() {
        return """
            SELECT c.expected_barn_id              AS barn,
                   COUNT(DISTINCT c.animal_id)     AS total_head,
                   COUNT(DISTINCT h.animal_id)     AS treated_in_period
            FROM cattle_tag.cattle c
            LEFT JOIN health.health_record h
                   ON h.animal_id = c.animal_id
                  AND h.kind = 'TREATMENT' AND h.status = 'ACTIVE'
                  AND h.performed_at >= :from AND h.performed_at < :toExclusive
            WHERE c.status = 'ACTIVE' AND {barnFilter}
            GROUP BY c.expected_barn_id
            ORDER BY c.expected_barn_id
            """.replace("{barnFilter}", barnFilter("c.expected_barn_id"));
    }
}
