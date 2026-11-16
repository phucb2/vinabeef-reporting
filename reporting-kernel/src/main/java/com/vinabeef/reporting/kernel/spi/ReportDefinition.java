package com.vinabeef.reporting.kernel.spi;

import com.vinabeef.reporting.kernel.model.ColumnDef;
import com.vinabeef.reporting.kernel.model.ReportFilter;

import java.util.List;
import java.util.Map;

/**
 * Extension point: WHAT data a report contains.
 * Add a field  -> edit this report's columns() + sql() only.
 * Add a report -> add a new implementation.
 */
public interface ReportDefinition extends Plugin {
    String reportType();
    String title();

    /** Column keys must match the SQL aliases. */
    List<ColumnDef> columns();

    /** Named-parameter SQL (":from", ":toExclusive", ":barnId"…) run against the read replica. */
    String sql();

    Map<String, Object> params(ReportFilter filter);

    @Override default String key() { return reportType(); }
}
