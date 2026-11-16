package com.vinabeef.reporting.plugins.excel;

import com.vinabeef.reporting.kernel.model.ColumnDef;
import com.vinabeef.reporting.kernel.model.ReportDataSet;
import com.vinabeef.reporting.kernel.model.Row;
import com.vinabeef.reporting.kernel.spi.ReportExporterPlugin;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Generic .xlsx renderer (Apache POI). Knows nothing about cattle: it only reads ColumnDef metadata. */
public class ExcelExporterPlugin implements ReportExporterPlugin {

    @Override public String format()      { return "xlsx"; }
    @Override public String contentType() { return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; }

    @Override
    public void export(ReportDataSet data, OutputStream out) throws IOException {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(500)) {           // streaming: low memory for big exports
            Sheet sheet = wb.createSheet(safeSheetName(data.title()));
            List<ColumnDef> cols = data.columns();
            Map<String, CellStyle> styles = buildStyles(wb, cols);

            org.apache.poi.ss.usermodel.Row header = sheet.createRow(0);
            for (int i = 0; i < cols.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols.get(i).label());
                cell.setCellStyle(styles.get("__header"));
            }

            int r = 1;
            for (Row row : data.rows()) {
                org.apache.poi.ss.usermodel.Row xRow = sheet.createRow(r++);
                for (int i = 0; i < cols.size(); i++) {
                    ColumnDef col = cols.get(i);
                    Cell cell = xRow.createCell(i);
                    write(cell, row.get(col.key()));
                    CellStyle style = styles.get(col.key());
                    if (style != null) cell.setCellStyle(style);
                }
            }
            sheet.createFreezePane(0, 1);
            wb.write(out);
            wb.dispose();
        }
    }

    private static void write(Cell cell, Object v) {
        if (v == null)                          cell.setBlank();
        else if (v instanceof Number n)         cell.setCellValue(n.doubleValue());
        else if (v instanceof Boolean b)        cell.setCellValue(b);
        else if (v instanceof LocalDateTime dt) cell.setCellValue(dt);
        else if (v instanceof LocalDate d)      cell.setCellValue(d);
        else                                    cell.setCellValue(v.toString());
    }

    private static Map<String, CellStyle> buildStyles(SXSSFWorkbook wb, List<ColumnDef> cols) {
        Map<String, CellStyle> styles = new HashMap<>();
        Font bold = wb.createFont();
        bold.setBold(true);
        CellStyle header = wb.createCellStyle();
        header.setFont(bold);
        styles.put("__header", header);

        var dataFormat = wb.getCreationHelper().createDataFormat();
        for (ColumnDef c : cols) {
            String fmt = c.format() != null ? c.format() : switch (c.type()) {
                case DATE -> "dd/MM/yyyy";
                case DATETIME -> "dd/MM/yyyy HH:mm";
                default -> null;
            };
            if (fmt != null) {
                CellStyle s = wb.createCellStyle();
                s.setDataFormat(dataFormat.getFormat(fmt));
                styles.put(c.key(), s);
            }
        }
        return styles;
    }

    private static String safeSheetName(String title) {
        String s = title == null ? "Report" : title.replaceAll("[\\\\/?*\\[\\]:]", " ");
        return s.length() > 31 ? s.substring(0, 31) : s;
    }
}
