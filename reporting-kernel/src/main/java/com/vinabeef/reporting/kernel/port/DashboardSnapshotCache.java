package com.vinabeef.reporting.kernel.port;

import com.vinabeef.reporting.kernel.model.DashboardSummary;
import com.vinabeef.reporting.kernel.model.ReportFilter;

import java.util.Optional;

public interface DashboardSnapshotCache {
    Optional<DashboardSummary> get(ReportFilter filter);
    void put(ReportFilter filter, DashboardSummary summary);
}
