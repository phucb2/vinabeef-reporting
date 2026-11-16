package com.vinabeef.reporting.kernel.spi;

import com.vinabeef.reporting.kernel.model.DateRange;
import com.vinabeef.reporting.kernel.model.ReportDataSet;
import com.vinabeef.reporting.kernel.model.SyncResult;

/** Extension point: WHERE data is sent (NADCS, VietGAP…). */
public interface IntegrationSinkPlugin extends Plugin {
    String sinkId();

    /** The ReportDefinition whose data this sink transmits, e.g. "outbreak-cases". */
    String sourceReportType();

    /** Map the generic data set to the external schema and send it. Must not throw for remote errors. */
    SyncResult transmit(ReportDataSet data, DateRange range);

    @Override default String key() { return sinkId(); }
}
