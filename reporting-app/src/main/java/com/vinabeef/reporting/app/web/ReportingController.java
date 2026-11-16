package com.vinabeef.reporting.app.web;

import com.vinabeef.reporting.kernel.api.ReportingFacade;
import com.vinabeef.reporting.kernel.model.DashboardSummary;
import com.vinabeef.reporting.kernel.model.DateRange;
import com.vinabeef.reporting.kernel.model.ExportResult;
import com.vinabeef.reporting.kernel.model.Granularity;
import com.vinabeef.reporting.kernel.model.ReportFilter;
import com.vinabeef.reporting.kernel.model.ReportPeriod;
import com.vinabeef.reporting.kernel.model.SyncResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
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

/**
 * Thin adapter: HTTP ⇄ ReportingFacade. No business logic, no plugin knowledge.
 * The OpenAPI annotations only document the API (served by springdoc); they change no behaviour.
 */
@RestController
@RequestMapping("/api/v1")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "Bad input: unknown report type, format, period or granularity, or no time range",
                content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content),
        @ApiResponse(responseCode = "403", description = "JWT lacks the required role", content = @Content)
})
public class ReportingController {

    private static final String PERIOD_DOC =
            "Named period: year `2026`, half `2026-H1`, quarter `2026-Q2`, month `2026-03`, ISO week `2026-W05` or day `2026-03-15`. "
                    + "Takes precedence over `from`/`to`.";
    private static final String BARN_DOC = "Barn id, e.g. `GLI-B02`. Omit for all barns.";

    private final ReportingFacade facade;

    public ReportingController(ReportingFacade facade) { this.facade = facade; }

    @GetMapping("/reports/summary")
    @PreAuthorize("hasAnyRole('MANAGER','EXEC')")
    @Tag(name = "Dashboard", description = "Headline KPIs (FR-13)")
    @Operation(summary = "Dashboard KPIs",
            description = "Active head count, animals treated in the range and gate movements today. "
                    + "If the database is unavailable the last cached figures are returned with `fromSnapshot=true`. "
                    + "Roles: MANAGER, EXEC.")
    @ApiResponse(responseCode = "200", description = "KPIs for the range")
    public DashboardSummary summary(
            @Parameter(description = BARN_DOC, example = "GLI-B02") @RequestParam(required = false) String barnId,
            @Parameter(description = PERIOD_DOC, example = "2026-11") @RequestParam(required = false) String period,
            @Parameter(description = "Range start (inclusive), used when `period` is absent") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Range end (inclusive), used when `period` is absent") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return facade.getSummary(ReportFilter.of(DashboardSummary.REPORT_TYPE, barnId, range(period, from, to), Granularity.NONE));
    }

    @GetMapping("/reports/export")
    @PreAuthorize("hasAnyRole('MANAGER','EXEC')")
    @Tag(name = "Reports", description = "Report catalog and file export (FR-11)")
    @Operation(summary = "Export a report as a file",
            description = "Runs the report for the range and renders it in the requested format; the file name is in "
                    + "`Content-Disposition`. Valid `type` and `format` values are listed by `GET /reports/catalog`. "
                    + "Roles: MANAGER, EXEC.")
    @ApiResponse(responseCode = "200", description = "The report file",
            content = {
                    @Content(mediaType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            schema = @Schema(type = "string", format = "binary")),
                    @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary"))
            })
    @ApiResponse(responseCode = "500", description = "Report query or rendering failed",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<byte[]> export(
            @Parameter(description = "Report type (see catalog)", example = "treatment-summary",
                    schema = @Schema(allowableValues = {"treatment-summary", "treatment-trend", "herd-by-barn", "outbreak-cases", "dashboard-kpis"}))
            @RequestParam String type,
            @Parameter(description = "Output format (see catalog)", example = "pdf", schema = @Schema(allowableValues = {"pdf", "xlsx"}))
            @RequestParam String format,
            @Parameter(description = BARN_DOC, example = "GLI-B02") @RequestParam(required = false) String barnId,
            @Parameter(description = PERIOD_DOC, example = "2026-11") @RequestParam(required = false) String period,
            @Parameter(description = "Time bucket for reports that group by time (e.g. `treatment-trend`, default MONTH); ignored by the others",
                    schema = @Schema(allowableValues = {"DAY", "WEEK", "MONTH", "QUARTER", "YEAR"}))
            @RequestParam(required = false) String granularity,
            @Parameter(description = "Range start (inclusive), used when `period` is absent") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Range end (inclusive), used when `period` is absent") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
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
    @Tag(name = "Reports")
    @Operation(summary = "List report types, formats and granularities",
            description = "What the installed plugins support right now; adding a plugin JAR extends these lists. Roles: MANAGER, EXEC.")
    @ApiResponse(responseCode = "200", description = "Supported values",
            content = @Content(mediaType = "application/json", schema = @Schema(example = """
                    {"reportTypes":["treatment-summary","treatment-trend","herd-by-barn","outbreak-cases","dashboard-kpis"],
                     "formats":["xlsx","pdf"],"granularities":["DAY","WEEK","MONTH","QUARTER","YEAR"]}""")))
    public Map<String, Set<String>> catalog() {
        return Map.of("reportTypes", facade.reportTypes(), "formats", facade.supportedFormats(),
                "granularities", Set.of("DAY", "WEEK", "MONTH", "QUARTER", "YEAR"));
    }

    @PostMapping("/integrations/{sinkId}/sync")
    @PreAuthorize("hasRole('ADMIN')")
    @Tag(name = "Integrations", description = "Pushes to external systems (FR-12)")
    @Operation(summary = "Send a range to an external system now",
            description = "Same as the nightly job, for a chosen range. Retries with exponential backoff; a failure "
                    + "raises an admin alert and is reported in the body (status FAILED), not as an HTTP error. Role: ADMIN.")
    @ApiResponse(responseCode = "200", description = "Outcome of the transfer")
    public SyncResult sync(
            @Parameter(description = "Integration id", example = "NADCS") @PathVariable String sinkId,
            @Parameter(description = "Range start (inclusive)", example = "2026-11-01") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Range end (inclusive)", example = "2026-11-15") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return facade.sync(sinkId, new DateRange(from, to));
    }
}
