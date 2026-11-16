package com.vinabeef.reporting.kernel.api;

import com.vinabeef.reporting.kernel.model.DashboardSummary;
import com.vinabeef.reporting.kernel.model.DateRange;
import com.vinabeef.reporting.kernel.model.ExportResult;
import com.vinabeef.reporting.kernel.model.ReportFilter;
import com.vinabeef.reporting.kernel.model.SyncResult;

import java.util.Set;

/** The only entry point of the kernel. Controllers and schedulers depend on this, nothing else. */
public interface ReportingFacade {
    DashboardSummary getSummary(ReportFilter filter);                 // FR-13
    ExportResult export(ReportFilter filter, String format);          // FR-11
    SyncResult sync(String sinkId, DateRange range);                  // FR-12

    Set<String> reportTypes();
    Set<String> supportedFormats();
}
