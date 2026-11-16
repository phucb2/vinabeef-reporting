package com.vinabeef.reporting.kernel.core;

import com.vinabeef.reporting.kernel.model.*;
import com.vinabeef.reporting.kernel.port.DashboardSnapshotCache;
import com.vinabeef.reporting.kernel.port.ReportDataGateway;
import com.vinabeef.reporting.kernel.spi.ColumnEnricher;
import com.vinabeef.reporting.kernel.spi.ReportDefinition;
import com.vinabeef.reporting.kernel.spi.ReportExporterPlugin;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/** Kernel is tested with in-memory fakes only: no Spring, no DB, no POI. */
class ReportAggregationServiceTest {

    static final LocalDate D = LocalDate.of(2026, 11, 1);
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-16T00:00:00Z"), ZoneOffset.UTC);

    static class FakeDefinition implements ReportDefinition {
        public String reportType() { return "fake"; }
        public String title() { return "Fake"; }
        public List<ColumnDef> columns() { return List.of(ColumnDef.of("n", "N", ColumnType.INTEGER)); }
        public String sql() { return "SELECT 1 AS n"; }
        public Map<String, Object> params(ReportFilter f) { return Map.of(); }
    }
    static class DoubleEnricher implements ColumnEnricher {
        public String appliesTo() { return "fake"; }
        public ColumnDef column() { return ColumnDef.of("n2", "N x2", ColumnType.INTEGER); }
        public Object enrich(Row r, ReportFilter f) { return ((Long) r.get("n")) * 2; }
    }
    static class TextExporter implements ReportExporterPlugin {
        public String format() { return "txt"; }
        public String contentType() { return "text/plain"; }
        public void export(ReportDataSet d, OutputStream out) throws java.io.IOException {
            out.write(d.rows().toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    ReportDataGateway gateway = (def, f) -> {
        Row r = new Row(); r.put("n", 21L);
        return new ReportDataSet(def.reportType(), def.title(), def.columns(), List.of(r), CLOCK.instant());
    };

    ReportAggregationService service(ReportDataGateway gw, DashboardSnapshotCache cache) {
        PluginRegistry registry = new PluginRegistry(List.of(new FakeDefinition()), List.of(new DoubleEnricher()),
                List.of(new TextExporter()), List.of());
        return new ReportAggregationService(registry, gw, cache, (s, d) -> {});
    }

    @Test
    void exportRunsDefinitionEnricherAndExporter() {
        ExportResult result = service(gateway, null).export(new ReportFilter("fake", null, D, D), "TXT");
        assertThat(result.fileName()).isEqualTo("fake_20261101-20261101.txt");
        assertThat(new String(result.content(), StandardCharsets.UTF_8)).contains("n=21", "n2=42");
    }

    @Test
    void unknownFormatFailsBeforeQuerying() {
        assertThatThrownBy(() -> service((d, f) -> { throw new AssertionError("must not query"); }, null)
                .export(new ReportFilter("fake", null, D, D), "docx"))
                .isInstanceOf(PluginNotFoundException.class)
                .hasMessageContaining("txt");
    }
}
