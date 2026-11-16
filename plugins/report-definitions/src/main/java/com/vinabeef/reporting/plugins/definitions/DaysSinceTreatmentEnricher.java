package com.vinabeef.reporting.plugins.definitions;

import com.vinabeef.reporting.kernel.model.ColumnDef;
import com.vinabeef.reporting.kernel.model.ColumnType;
import com.vinabeef.reporting.kernel.model.ReportFilter;
import com.vinabeef.reporting.kernel.model.Row;
import com.vinabeef.reporting.kernel.spi.ColumnEnricher;
import com.vinabeef.reporting.kernel.spi.PluginContext;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/** DERIVED FIELD EXAMPLE: adds a column to treatment-summary without changing its SQL. */
public class DaysSinceTreatmentEnricher implements ColumnEnricher {

    private Clock clock = Clock.systemDefaultZone();

    @Override public void init(PluginContext context) { this.clock = context.clock(); }

    @Override public String appliesTo() { return TreatmentSummaryReport.TYPE; }

    @Override public String insertAfter() { return "treated_at"; }

    @Override
    public ColumnDef column() {
        return ColumnDef.of("days_since_treatment", "Days since treatment", ColumnType.INTEGER);
    }

    @Override
    public Object enrich(Row row, ReportFilter filter) {
        if (!(row.get("treated_at") instanceof LocalDateTime treatedAt)) return null;
        return ChronoUnit.DAYS.between(treatedAt.toLocalDate(), LocalDate.now(clock));
    }
}
