package com.vinabeef.reporting.kernel.model;

import java.util.Objects;

/**
 * Metadata for one column of a report.
 * key    = SQL alias / Row key (lower snake_case, e.g. "treated_at")
 * label  = header shown in Excel/PDF
 * format = optional display pattern (e.g. "dd/MM/yyyy HH:mm", "#,##0.00")
 */
public record ColumnDef(String key, String label, ColumnType type, String format) {
    public ColumnDef {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(type, "type");
    }
    public static ColumnDef of(String key, String label, ColumnType type) {
        return new ColumnDef(key, label, type, null);
    }
    public static ColumnDef of(String key, String label, ColumnType type, String format) {
        return new ColumnDef(key, label, type, format);
    }
}
