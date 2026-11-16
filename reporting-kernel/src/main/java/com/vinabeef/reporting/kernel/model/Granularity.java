package com.vinabeef.reporting.kernel.model;

/**
 * How rows are bucketed in time. NONE = one figure for the whole range.
 * sqlUnit() is the PostgreSQL date_trunc unit.
 */
public enum Granularity {
    NONE(null), DAY("day"), WEEK("week"), MONTH("month"), QUARTER("quarter"), YEAR("year");

    private final String sqlUnit;

    Granularity(String sqlUnit) { this.sqlUnit = sqlUnit; }

    public String sqlUnit() { return sqlUnit; }

    public static Granularity parse(String value) {
        if (value == null || value.isBlank()) return NONE;
        try {
            return Granularity.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown granularity '" + value + "'. Use DAY, WEEK, MONTH, QUARTER or YEAR");
        }
    }
}
