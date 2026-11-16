package com.vinabeef.reporting.plugins.definitions;

import com.vinabeef.reporting.kernel.model.ColumnDef;
import com.vinabeef.reporting.kernel.model.ColumnType;

import java.util.List;

/**
 * Treatments in the period, one row per treatment.
 * EXTRA FIELD EXAMPLE: "breed" was added by editing ONLY this class
 * (one ColumnDef + the JOIN on cattle). Kernel, gateway and exporters are unchanged.
 */
public class TreatmentSummaryReport extends AbstractSqlReportDefinition {

    public static final String TYPE = "treatment-summary";

    @Override public String reportType() { return TYPE; }
    @Override public String title()      { return "Treatment summary"; }

    @Override
    public List<ColumnDef> columns() {
        return List.of(
                ColumnDef.of("rfid",       "RFID",       ColumnType.STRING),
                ColumnDef.of("barn",       "Barn",       ColumnType.STRING),
                ColumnDef.of("breed",      "Breed",      ColumnType.STRING),                 // <- added field
                ColumnDef.of("diagnosis",  "Diagnosis",  ColumnType.STRING),
                ColumnDef.of("treatment",  "Treatment",  ColumnType.STRING),
                ColumnDef.of("treated_at", "Treated at", ColumnType.DATETIME, "dd/MM/yyyy HH:mm"));
    }

    @Override
    public String sql() {
        // rfid = the tag active today (an animal can have had several tags)
        return """
            SELECT t.rfid_tag                                    AS rfid,
                   h.barn_id                                     AS barn,
                   c.breed                                       AS breed,
                   h.diagnosis                                   AS diagnosis,
                   h.vaccine_or_drug                             AS treatment,
                   h.performed_at AT TIME ZONE 'Asia/Ho_Chi_Minh' AS treated_at
            FROM health.health_record h
            JOIN cattle_tag.cattle c ON c.animal_id = h.animal_id
            LEFT JOIN cattle_tag.tag_assignment t
                   ON t.animal_id = h.animal_id AND t.unassigned_at IS NULL
            WHERE h.kind = 'TREATMENT' AND h.status = 'ACTIVE'
              AND h.performed_at >= :from AND h.performed_at < :toExclusive
              AND {barnFilter}
            ORDER BY h.performed_at
            """.replace("{barnFilter}", barnFilter("h.barn_id"));
    }
}
