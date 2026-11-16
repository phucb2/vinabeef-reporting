package com.vinabeef.reporting.kernel.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Self-describing result of a report query. Exporters and sinks only read
 * {@link #columns()} and {@link #rows()}, so adding a column never changes them.
 */
public final class ReportDataSet {
    private final String reportType;
    private final String title;
    private final List<ColumnDef> columns;
    private final List<Row> rows;
    private final Map<String, Number> metrics = new LinkedHashMap<>();
    private final Instant generatedAt;
    private ReportFilter filter;

    public ReportDataSet(String reportType, String title, List<ColumnDef> columns, List<Row> rows, Instant generatedAt) {
        this.reportType = reportType;
        this.title = title;
        this.columns = new ArrayList<>(columns);
        this.rows = new ArrayList<>(rows);
        this.generatedAt = generatedAt;
    }

    /** Used by enrichers to append a derived column. */
    public void addColumn(ColumnDef column) {
        addColumn(column, null);
    }

    /** Insert a derived column after the column with key {@code afterKey} (null or unknown = at the end). */
    public void addColumn(ColumnDef column, String afterKey) {
        boolean exists = columns.stream().anyMatch(c -> c.key().equals(column.key()));
        if (exists) throw new IllegalStateException("Duplicate column key: " + column.key());
        int index = -1;
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).key().equals(afterKey)) index = i + 1;
        }
        if (index < 0) columns.add(column); else columns.add(index, column);
    }

    public void putMetric(String name, Number value) { metrics.put(name, value); }

    /** The request this data answers (period, barn, granularity); exporters may print it as report context. */
    public void attachFilter(ReportFilter filter) { this.filter = filter; }

    public String reportType()            { return reportType; }
    public String title()                 { return title; }
    public List<ColumnDef> columns()      { return Collections.unmodifiableList(columns); }
    public List<Row> rows()               { return Collections.unmodifiableList(rows); }
    public Map<String, Number> metrics()  { return Collections.unmodifiableMap(metrics); }
    public Instant generatedAt()          { return generatedAt; }
    public Optional<ReportFilter> filter() { return Optional.ofNullable(filter); }
    public boolean isEmpty()              { return rows.isEmpty(); }
}
