package com.vinabeef.reporting.plugins.definitions;

import com.vinabeef.reporting.kernel.model.ColumnDef;
import com.vinabeef.reporting.kernel.model.ColumnType;
import com.vinabeef.reporting.kernel.model.Granularity;
import com.vinabeef.reporting.kernel.model.ReportFilter;
import com.vinabeef.reporting.kernel.model.Row;
import com.vinabeef.reporting.kernel.spi.ColumnEnricher;

import java.time.LocalDate;
import java.time.temporal.IsoFields;

/**
 * ENRICHER EXAMPLE: turns period_start into a readable label that depends on the request's
 * granularity ("2026-Q1", "2026-03", "2026-W05"). Pure formatting, so it stays out of the SQL.
 */
public class PeriodLabelEnricher implements ColumnEnricher {

    @Override public String appliesTo()   { return TreatmentTrendReport.TYPE; }
    @Override public String insertAfter() { return "period_start"; }

    @Override
    public ColumnDef column() { return ColumnDef.of("period_label", "Period", ColumnType.STRING); }

    @Override
    public Object enrich(Row row, ReportFilter filter) {
        if (!(row.get("period_start") instanceof LocalDate d)) return null;
        return label(d, filter.granularityOr(Granularity.MONTH));
    }

    static String label(LocalDate d, Granularity g) {
        return switch (g) {
            case YEAR    -> String.valueOf(d.getYear());
            case QUARTER -> d.getYear() + "-Q" + d.get(IsoFields.QUARTER_OF_YEAR);
            case MONTH   -> String.format("%d-%02d", d.getYear(), d.getMonthValue());
            case WEEK    -> String.format("%d-W%02d", d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
            case DAY, NONE -> d.toString();
        };
    }
}
