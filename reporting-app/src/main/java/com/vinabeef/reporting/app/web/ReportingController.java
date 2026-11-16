package com.vinabeef.reporting.app.web;

import com.vinabeef.reporting.kernel.api.ReportingFacade;
import com.vinabeef.reporting.kernel.model.DashboardSummary;
import com.vinabeef.reporting.kernel.model.DateRange;
import com.vinabeef.reporting.kernel.model.ExportResult;
import com.vinabeef.reporting.kernel.model.Granularity;
import com.vinabeef.reporting.kernel.model.ReportFilter;
import com.vinabeef.reporting.kernel.model.ReportPeriod;
import com.vinabeef.reporting.kernel.model.SyncResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

/** Thin adapter: HTTP ⇄ ReportingFacade. No business logic, no plugin knowledge. */
@RestController
@RequestMapping("/api/v1")
public class ReportingController {

    private final ReportingFacade facade;

    public ReportingController(ReportingFacade facade) { this.facade = facade; }

    /**
     * Time range = either {@code period} (2026, 2026-H1, 2026-Q2, 2026-03, 2026-W05, 2026-03-15)
     * or explicit {@code from}/{@code to}. {@code granularity} buckets rows in time (DAY … YEAR).
     */
    @GetMapping("/reports/summary")
    @PreAuthorize("hasAnyRole('MANAGER','EXEC')")
    public DashboardSummary summary(@RequestParam(required = false) String barnId,
                                    @RequestParam(required = false) String period,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return facade.getSummary(ReportFilter.of(DashboardSummary.REPORT_TYPE, barnId, range(period, from, to), Granularity.NONE));
    }

    /** e.g. /reports/export?type=treatment-trend&period=2026&granularity=QUARTER&format=xlsx */
    @GetMapping("/reports/export")
    @PreAuthorize("hasAnyRole('MANAGER','EXEC')")
    public ResponseEntity<byte[]> export(@RequestParam String type,
                                         @RequestParam String format,
                                         @RequestParam(required = false) String barnId,
                                         @RequestParam(required = false) String period,
                                         @RequestParam(required = false) String granularity,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        ReportFilter filter = ReportFilter.of(type, barnId, range(period, from, to), Granularity.parse(granularity));
        ExportResult result = facade.export(filter, format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(result.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(result.fileName()).build().toString())
                .body(result.content());
    }

    /** A named period wins; otherwise both dates are required. Bad input → IllegalArgumentException → 400. */
    private static ReportPeriod range(String period, LocalDate from, LocalDate to) {
        if (period != null && !period.isBlank()) return ReportPeriod.parse(period);
        if (from == null || to == null) throw new IllegalArgumentException("Give either 'period' or both 'from' and 'to'");
        return new ReportPeriod(from, to);
    }

    @GetMapping("/reports/catalog")
    @PreAuthorize("hasAnyRole('MANAGER','EXEC')")
    public Map<String, Set<String>> catalog() {
        return Map.of("reportTypes", facade.reportTypes(), "formats", facade.supportedFormats(),
                "granularities", Set.of("DAY", "WEEK", "MONTH", "QUARTER", "YEAR"));
    }

    @PostMapping("/integrations/{sinkId}/sync")
    @PreAuthorize("hasRole('ADMIN')")
    public SyncResult sync(@PathVariable String sinkId,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return facade.sync(sinkId, new DateRange(from, to));
    }
}
