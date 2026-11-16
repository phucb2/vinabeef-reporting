package com.vinabeef.reporting.plugins.definitions;

import com.vinabeef.reporting.kernel.model.ColumnDef;
import com.vinabeef.reporting.kernel.model.ColumnType;

import java.util.List;

/** Suspected outbreak cases. Also the data source of the NADCS sink. */
public class OutbreakCasesReport extends AbstractSqlReportDefinition {

    public static final String TYPE = "outbreak-cases";

    @Override public String reportType() { return TYPE; }
    @Override public String title()      { return "Suspected outbreak cases"; }

    @Override
    public List<ColumnDef> columns() {
        return List.of(
                ColumnDef.of("record_id",    "Record",       ColumnType.STRING),
                ColumnDef.of("rfid",         "RFID",         ColumnType.STRING),
                ColumnDef.of("barn",         "Barn",         ColumnType.STRING),
                ColumnDef.of("disease_code", "Disease code", ColumnType.STRING),
                ColumnDef.of("diagnosis",    "Diagnosis",    ColumnType.STRING),
                ColumnDef.of("detected_at",  "Detected at",  ColumnType.DATETIME, "dd/MM/yyyy HH:mm"));
    }

    @Override
    public String sql() {
        return """
            SELECT h.record_id::text                             AS record_id,
                   t.rfid_tag                                    AS rfid,
                   h.barn_id                                     AS barn,
                   h.disease_code                                AS disease_code,
                   h.diagnosis                                   AS diagnosis,
                   h.performed_at AT TIME ZONE 'Asia/Ho_Chi_Minh' AS detected_at
            FROM health.health_record h
            LEFT JOIN cattle_tag.tag_assignment t
                   ON t.animal_id = h.animal_id AND t.unassigned_at IS NULL
            WHERE h.outbreak_suspected AND h.status = 'ACTIVE'
              AND h.performed_at >= :from AND h.performed_at < :toExclusive
              AND {barnFilter}
            ORDER BY h.performed_at
            """.replace("{barnFilter}", barnFilter("h.barn_id"));
    }
}
