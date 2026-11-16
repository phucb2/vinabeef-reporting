package com.vinabeef.reporting.infrastructure.cache;

import com.vinabeef.reporting.kernel.model.DashboardSummary;
import com.vinabeef.reporting.kernel.model.ReportFilter;
import com.vinabeef.reporting.kernel.port.DashboardSnapshotCache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Last good dashboard per filter. Swap for a Redis adapter when running several pods. */
public class InMemorySnapshotCache implements DashboardSnapshotCache {

    private final Map<ReportFilter, DashboardSummary> snapshots = new ConcurrentHashMap<>();

    @Override public Optional<DashboardSummary> get(ReportFilter f)       { return Optional.ofNullable(snapshots.get(f)); }
    @Override public void put(ReportFilter f, DashboardSummary summary)   { snapshots.put(f, summary); }
}
