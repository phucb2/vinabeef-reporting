package com.vinabeef.reporting.kernel.port;

import com.vinabeef.reporting.kernel.model.ReportDataSet;
import com.vinabeef.reporting.kernel.model.ReportFilter;
import com.vinabeef.reporting.kernel.spi.ReportDefinition;

/** Output port. Generic on purpose: one method for every report, so it never changes. */
public interface ReportDataGateway {
    ReportDataSet execute(ReportDefinition definition, ReportFilter filter);
}
