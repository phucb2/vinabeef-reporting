package com.vinabeef.reporting.kernel.spi;

import com.vinabeef.reporting.kernel.model.ReportDataSet;

import java.io.IOException;
import java.io.OutputStream;

/** Extension point: HOW a report is rendered (xlsx, pdf, csv…). */
public interface ReportExporterPlugin extends Plugin {
    String format();
    String contentType();
    default String fileExtension() { return format(); }

    void export(ReportDataSet data, OutputStream out) throws IOException;

    @Override default String key() { return format(); }
}
