package com.vinabeef.reporting.kernel.core;

import com.vinabeef.reporting.kernel.api.ReportingFacade;
import com.vinabeef.reporting.kernel.model.DashboardSummary;
import com.vinabeef.reporting.kernel.model.DateRange;
import com.vinabeef.reporting.kernel.model.ExportResult;
import com.vinabeef.reporting.kernel.model.ReportDataSet;
import com.vinabeef.reporting.kernel.model.ReportFilter;
import com.vinabeef.reporting.kernel.model.Row;
import com.vinabeef.reporting.kernel.model.SyncResult;
import com.vinabeef.reporting.kernel.port.AdminAlertNotifier;
import com.vinabeef.reporting.kernel.port.DashboardSnapshotCache;
import com.vinabeef.reporting.kernel.port.ReportDataGateway;
import com.vinabeef.reporting.kernel.spi.ColumnEnricher;
import com.vinabeef.reporting.kernel.spi.IntegrationSinkPlugin;
import com.vinabeef.reporting.kernel.spi.ReportDefinition;
import com.vinabeef.reporting.kernel.spi.ReportExporterPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Set;

/**
 * Report Aggregation Core (kernel). Knows only SPI interfaces and ports.
 *
 * Flow:  ① facade call → ② registry lookup → ③ gateway.execute(def, filter)
 *        → ④ SQL on read replica → ⑤ enrichers → ⑥ exporter / sink
 */
public final class ReportAggregationService implements ReportingFacade {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final PluginRegistry registry;
    private final ReportDataGateway gateway;
    private final DashboardSnapshotCache cache;
    private final AdminAlertNotifier notifier;

    public ReportAggregationService(PluginRegistry registry, ReportDataGateway gateway,
                                    DashboardSnapshotCache cache, AdminAlertNotifier notifier) {
        this.registry = registry;
        this.gateway = gateway;
        this.cache = cache;
        this.notifier = notifier;
    }

    // ---------------- FR-11: export ----------------
    @Override
    public ExportResult export(ReportFilter filter, String format) {
        ReportExporterPlugin exporter = registry.findExporter(format);      // ② fail fast before querying
        ReportDataSet data = build(filter);                                 // ②③④⑤
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            exporter.export(data, out);                                     // ⑥
        } catch (IOException | RuntimeException e) {
            throw new ReportGenerationException("Rendering " + format + " failed for " + filter.reportType(), e);
        }
        String fileName = filter.reportType() + "_" + FILE_TS.format(filter.from()) + "-"
                + FILE_TS.format(filter.to()) + "." + exporter.fileExtension();
        return new ExportResult(fileName, exporter.contentType(), out.toByteArray());
    }

    // ---------------- FR-13: dashboard ----------------
    @Override
    public DashboardSummary getSummary(ReportFilter filter) {
        ReportFilter kpiFilter = filter.withReportType(DashboardSummary.REPORT_TYPE);
        try {
            DashboardSummary summary = DashboardSummary.from(build(kpiFilter));
            cache.put(kpiFilter, summary);
            return summary;
        } catch (PluginNotFoundException e) {
            throw e;                                                        // configuration error, no fallback
        } catch (RuntimeException e) {
            return cache.get(kpiFilter)                                     // DB down → last snapshot
                    .map(DashboardSummary::asSnapshot)
                    .orElseThrow(() -> new ReportGenerationException("Dashboard unavailable and no snapshot cached", e));
        }
    }

    // ---------------- FR-12: external sync ----------------
    @Override
    public SyncResult sync(String sinkId, DateRange range) {
        IntegrationSinkPlugin sink = registry.findSink(sinkId);
        ReportFilter filter = new ReportFilter(sink.sourceReportType(), null, range.from(), range.to());
        SyncResult result;
        try {
            result = sink.transmit(build(filter), range);                   // ⑥ → ⑦ HTTPS inside plugin
        } catch (RuntimeException e) {
            result = SyncResult.failed(0, e.getMessage());
        }
        if (!result.isSuccess()) {
            notifier.alert("Sync to " + sinkId + " failed",
                    "Range " + range + " after " + result.attempts() + " attempt(s): " + result.message());
        }
        return result;
    }

    @Override public Set<String> reportTypes()      { return registry.reportTypes(); }
    @Override public Set<String> supportedFormats() { return registry.supportedFormats(); }

    // ---------------- shared pipeline ----------------
    private ReportDataSet build(ReportFilter filter) {
        ReportDefinition definition = registry.findDefinition(filter.reportType());   // ②
        ReportDataSet data = gateway.execute(definition, filter);                     // ③④
        data.attachFilter(filter);
        for (ColumnEnricher enricher : registry.enrichersFor(definition.reportType())) {   // ⑤
            data.addColumn(enricher.column(), enricher.insertAfter());
            for (Row row : data.rows()) {
                row.put(enricher.column().key(), enricher.enrich(row, filter));
            }
        }
        data.putMetric("row_count", data.rows().size());
        return data;
    }
}
