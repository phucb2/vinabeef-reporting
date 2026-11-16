package com.vinabeef.reporting.kernel.spi;

import com.vinabeef.reporting.kernel.model.ColumnDef;
import com.vinabeef.reporting.kernel.model.ReportFilter;
import com.vinabeef.reporting.kernel.model.Row;

/**
 * Extension point: a derived column added after the query, without touching the report's SQL.
 * Use it for values computed per row (labels, scores, "days since"); never for filtering,
 * sorting or totals, which belong in SQL.
 */
public interface ColumnEnricher extends Plugin {
    /** reportType this enricher applies to. */
    String appliesTo();

    ColumnDef column();

    /** Key of the column to place the new one after; null = append at the end. */
    default String insertAfter() { return null; }

    /**
     * Compute the value for one row. It may read other columns of the row and the request
     * (period, granularity, barn), e.g. to build a "2026-Q1" label.
     */
    Object enrich(Row row, ReportFilter filter);

    @Override default String key() { return appliesTo() + ":" + column().key(); }
}
