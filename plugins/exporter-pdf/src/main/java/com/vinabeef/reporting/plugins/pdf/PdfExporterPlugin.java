package com.vinabeef.reporting.plugins.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import com.vinabeef.reporting.kernel.model.ColumnDef;
import com.vinabeef.reporting.kernel.model.ColumnType;
import com.vinabeef.reporting.kernel.model.Granularity;
import com.vinabeef.reporting.kernel.model.ReportDataSet;
import com.vinabeef.reporting.kernel.model.ReportFilter;
import com.vinabeef.reporting.kernel.model.Row;
import com.vinabeef.reporting.kernel.spi.PluginContext;
import com.vinabeef.reporting.kernel.spi.ReportExporterPlugin;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Generic .pdf renderer (OpenPDF) laid out like a management report:
 * branded header/footer with "Page X of Y", a context band (period, barn, breakdown), KPI tiles,
 * a breakdown by category for detail reports, the data table (grouped with subtotals and a grand
 * total for aggregate reports) and a sign-off block.
 *
 * <p>It stays generic: it only reads the dataset's columns, rows and filter. Conventions it relies on:
 * <ul>
 *   <li>INTEGER/DECIMAL columns <b>with a format pattern</b> are measures: they are summed in totals.
 *       Numeric columns without a pattern (e.g. "days since") are shown but never summed.</li>
 *   <li>Columns keyed {@code rfid} and {@code barn} give the "Animals" and "Barns" tiles.</li>
 * </ul>
 * Fonts: {@code reporting.plugins.pdf.font} / {@code pdf.font-bold} (TTF path or classpath resource).
 * A Unicode font is needed for Vietnamese diacritics; without one it falls back to Helvetica.
 */
public class PdfExporterPlugin implements ReportExporterPlugin {

    private static final String DEFAULT_FONT = "/fonts/NotoSans-Regular.ttf";
    private static final String DEFAULT_BOLD_FONT = "/fonts/NotoSans-Bold.ttf";
    private static final ZoneId FARM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Color BRAND = new Color(0x1F, 0x5E, 0x3B);
    private static final Color BRAND_TINT = new Color(0xE8, 0xF1, 0xEB);
    private static final Color INK = new Color(0x1F, 0x29, 0x33);
    private static final Color MUTED = new Color(0x6B, 0x72, 0x80);
    private static final Color RULE = new Color(0xD9, 0xDD, 0xE3);
    private static final Color ZEBRA = new Color(0xF6, 0xF7, 0xF9);
    private static final Color TRACK = new Color(0xE5, 0xE7, 0xEB);

    private static final int BREAKDOWN_MAX_VALUES = 10;
    private static final Set<String> ID_COLUMNS = Set.of("rfid", "record_id");

    private BaseFont regular;          // never null after init
    private BaseFont bold;             // null = simulate bold from the regular font
    private String organisation = "VinaBeef";
    private String systemName = "Livestock Farm Management System";

    @Override public String format()      { return "pdf"; }
    @Override public String contentType() { return "application/pdf"; }

    @Override
    public void init(PluginContext context) {
        regular = load(context.config("pdf.font").orElse(DEFAULT_FONT));
        bold = load(context.config("pdf.font-bold").orElse(DEFAULT_BOLD_FONT));
        if (regular == null) {          // no Unicode font: Helvetica (no Vietnamese diacritics)
            regular = standard(BaseFont.HELVETICA);
            bold = standard(BaseFont.HELVETICA_BOLD);
        }
        organisation = context.config("pdf.organisation").orElse(organisation);
        systemName = context.config("pdf.system-name").orElse(systemName);
    }

    @Override
    public void export(ReportDataSet data, OutputStream out) throws IOException {
        List<ColumnDef> cols = data.columns();
        float[] widths = columnWidths(data);
        float totalWidth = 0;
        for (float w : widths) totalWidth += w;
        Rectangle pageSize = cols.size() >= 6 || totalWidth > 95 ? PageSize.A4.rotate() : PageSize.A4;

        Document doc = new Document(pageSize, 36, 36, 80, 56);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setCloseStream(false);
            writer.setPageEvent(new PageDecorator(data));
            doc.addTitle(data.title());
            doc.addAuthor(organisation);
            doc.addCreator(organisation + " Reporting");
            doc.open();

            addTitleBlock(doc, data);
            addKpiTiles(doc, data);
            if (data.isEmpty()) {
                Paragraph none = new Paragraph("No records match the selected period and barn.", font(10, false, MUTED));
                none.setSpacingBefore(18);
                doc.add(none);
            } else {
                if (measures(cols).isEmpty() && data.rows().size() >= 5) addBreakdown(doc, data);
                addDetailTable(doc, data, widths);
            }
            addSignOff(doc);
        } catch (DocumentException e) {
            throw new IOException("PDF rendering failed", e);
        } finally {
            if (doc.isOpen()) doc.close();
        }
    }

    // ------------------------------------------------------------------ sections

    private void addTitleBlock(Document doc, ReportDataSet data) throws DocumentException {
        Paragraph title = new Paragraph(data.title(), font(18, true, INK));
        title.setSpacingAfter(2);
        doc.add(title);
        Paragraph sub = new Paragraph("Report " + data.reportType() + "  ·  Source: reporting read replica"
                + "  ·  Times in farm local time (ICT, UTC+7)", font(8, false, MUTED));
        sub.setSpacingAfter(10);
        doc.add(sub);

        Map<String, String> context = new LinkedHashMap<>();
        data.filter().ifPresent(f -> {
            context.put("Period", period(f));
            context.put("Barn", f.barnId() == null ? "All barns" : f.barnId());
            if (f.granularity() != Granularity.NONE) context.put("Breakdown", granularityLabel(f.granularity()));
        });
        context.put("Generated", STAMP.format(data.generatedAt().atZone(FARM_ZONE)));

        float[] bandWidths = new float[context.size()];
        int i = 0;
        for (String key : context.keySet()) bandWidths[i++] = key.equals("Period") ? 2.2f : 1f;   // period text is the longest
        PdfPTable band = new PdfPTable(bandWidths);
        band.setWidthPercentage(100);
        band.setSpacingAfter(12);
        for (Map.Entry<String, String> e : context.entrySet()) {
            PdfPCell c = new PdfPCell();
            c.setBorder(Rectangle.NO_BORDER);
            c.setBackgroundColor(BRAND_TINT);
            c.setPadding(7);
            c.setPaddingBottom(9);
            c.addElement(new Paragraph(e.getKey().toUpperCase(), font(6.5f, true, BRAND)));
            c.addElement(new Paragraph(e.getValue(), font(9.5f, true, INK)));
            band.addCell(c);
        }
        doc.add(band);
    }

    private void addKpiTiles(Document doc, ReportDataSet data) throws DocumentException {
        List<ColumnDef> cols = data.columns();
        List<String[]> tiles = new ArrayList<>();
        // detail reports: record count; aggregate reports: their totals say more than a row count
        if (measures(cols).isEmpty()) tiles.add(new String[]{"Records", String.format("%,d", data.rows().size())});
        if (has(cols, "rfid")) tiles.add(new String[]{"Animals", String.format("%,d", distinct(data, "rfid").size())});
        if (has(cols, "barn")) tiles.add(new String[]{"Barns", String.format("%,d", distinct(data, "barn").size())});
        for (ColumnDef m : measures(cols)) {
            String label = m.label().toLowerCase().startsWith("total") ? m.label() : "Total " + m.label().toLowerCase();
            tiles.add(new String[]{label, formatValue(sum(data.rows(), m), m)});
        }
        cols.stream().filter(c -> c.type() == ColumnType.DATETIME).findFirst().ifPresent(c -> {
            data.rows().stream().map(r -> r.get(c.key())).filter(LocalDateTime.class::isInstance)
                    .map(LocalDateTime.class::cast).max(Comparator.naturalOrder())
                    .ifPresent(last -> tiles.add(new String[]{"Latest entry", STAMP.format(last)}));
        });
        List<String[]> shown = tiles.subList(0, Math.min(5, tiles.size()));

        PdfPTable table = new PdfPTable(shown.size());
        table.setWidthPercentage(100);
        table.setSpacingAfter(14);
        for (String[] t : shown) {
            PdfPCell c = new PdfPCell();
            c.setBorder(Rectangle.LEFT | Rectangle.RIGHT);   // white right border = gap between tiles
            c.setBorderColorLeft(BRAND);
            c.setBorderWidthLeft(3);
            c.setBorderColorRight(Color.WHITE);
            c.setBorderWidthRight(6);
            c.setBackgroundColor(ZEBRA);
            c.setPadding(8);
            c.setPaddingBottom(10);
            c.addElement(new Paragraph(t[0], font(7.5f, false, MUTED)));
            c.addElement(new Paragraph(t[1], font(t[1].length() > 12 ? 12 : 17, true, INK)));
            table.addCell(c);
        }
        doc.add(table);
    }

    /** Detail reports: counts and shares for up to two low-cardinality text columns (barn first). */
    private void addBreakdown(Document doc, ReportDataSet data) throws DocumentException {
        List<ColumnDef> candidates = new ArrayList<>();
        for (ColumnDef c : data.columns()) {
            if (c.type() != ColumnType.STRING || ID_COLUMNS.contains(c.key())) continue;
            int n = distinct(data, c.key()).size();
            if (n >= 2 && n <= BREAKDOWN_MAX_VALUES) candidates.add(c);
        }
        if (candidates.isEmpty()) return;
        List<ColumnDef> chosen = new ArrayList<>();
        candidates.stream().filter(c -> c.key().equals("barn")).findFirst().ifPresent(chosen::add);
        candidates.stream().filter(c -> !chosen.contains(c))
                .max(Comparator.comparingInt((ColumnDef c) -> distinct(data, c.key()).size())
                        .thenComparing(c -> -candidates.indexOf(c)))
                .ifPresent(chosen::add);

        doc.add(sectionHeading("Breakdown"));
        PdfPTable outer = new PdfPTable(2);
        outer.setWidthPercentage(100);
        outer.setSpacingAfter(14);
        for (int i = 0; i < 2; i++) {
            PdfPCell holder = new PdfPCell();
            holder.setBorder(Rectangle.NO_BORDER);
            holder.setPaddingRight(i == 0 ? 10 : 0);
            holder.setPaddingLeft(i == 1 ? 10 : 0);
            if (i < chosen.size()) holder.addElement(breakdownTable(data, chosen.get(i)));
            outer.addCell(holder);
        }
        doc.add(outer);
    }

    private PdfPTable breakdownTable(ReportDataSet data, ColumnDef col) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Row r : data.rows()) counts.merge(Objects.toString(r.get(col.key()), "(blank)"), 1, Integer::sum);
        int total = data.rows().size();

        PdfPTable t = new PdfPTable(new float[]{44, 12, 30, 14});
        t.setWidthPercentage(100);
        t.addCell(headerCell("By " + col.label().toLowerCase(), Element.ALIGN_LEFT));
        t.addCell(headerCell("Count", Element.ALIGN_RIGHT));
        t.addCell(headerCell("", Element.ALIGN_LEFT));
        t.addCell(headerCell("Share", Element.ALIGN_RIGHT));
        counts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).forEach(e -> {
            float share = (float) e.getValue() / total;
            t.addCell(bodyCell(e.getKey(), Element.ALIGN_LEFT, null));
            t.addCell(bodyCell(String.format("%,d", e.getValue()), Element.ALIGN_RIGHT, null));
            PdfPCell bar = bodyCell("", Element.ALIGN_LEFT, null);
            bar.setCellEvent(new ShareBar(share));
            t.addCell(bar);
            t.addCell(bodyCell(String.format("%.0f%%", share * 100), Element.ALIGN_RIGHT, null));
        });
        return t;
    }

    private void addDetailTable(Document doc, ReportDataSet data, float[] widths) throws DocumentException {
        List<ColumnDef> cols = data.columns();
        List<ColumnDef> measures = measures(cols);
        boolean aggregate = !measures.isEmpty();

        doc.add(sectionHeading(aggregate ? "Figures" : "Details"));
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (ColumnDef c : cols) table.addCell(headerCell(c.label(), align(c)));

        List<Row> rows = data.rows();
        // Aggregate reports are grouped on the first column: repeated leading values are blanked and
        // each group of more than one row gets a subtotal.
        boolean grouped = aggregate && distinctFormatted(rows, cols.get(0)) < rows.size();
        List<Row> group = new ArrayList<>();
        int stripe = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            Row prev = i > 0 ? rows.get(i - 1) : null;
            if (grouped && prev != null && !sameKey(row, prev, cols.get(0))) {
                if (group.size() > 1) addTotalRow(table, cols, group, "Subtotal", false);
                group.clear();
            }
            Color bg = stripe++ % 2 == 1 ? ZEBRA : null;
            boolean leading = grouped && !group.isEmpty();
            for (ColumnDef c : cols) {
                boolean blank = leading && !measures.contains(c) && prev != null && sameKey(row, prev, c);
                if (!blank) leading = false;
                table.addCell(bodyCell(blank ? "" : formatValue(row.get(c.key()), c), align(c), bg));
            }
            group.add(row);
        }
        if (grouped && group.size() > 1) addTotalRow(table, cols, group, "Subtotal", false);
        if (aggregate) addTotalRow(table, cols, rows, "Total", true);
        doc.add(table);
    }

    private void addTotalRow(PdfPTable table, List<ColumnDef> cols, List<Row> rows, String label, boolean grand) {
        List<ColumnDef> measures = measures(cols);
        for (int i = 0; i < cols.size(); i++) {
            ColumnDef c = cols.get(i);
            String text = measures.contains(c) ? formatValue(sum(rows, c), c) : i == 0 ? label : "";
            PdfPCell cell = new PdfPCell(new Phrase(text, font(grand ? 8.5f : 8, true, grand ? BRAND : INK)));
            cell.setHorizontalAlignment(measures.contains(c) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(4);
            cell.setPaddingBottom(5);
            cell.setBackgroundColor(BRAND_TINT);
            cell.setBorder(grand ? Rectangle.TOP | Rectangle.BOTTOM : Rectangle.BOTTOM);
            cell.setBorderColor(grand ? BRAND : RULE);
            cell.setBorderWidth(grand ? 1f : 0.5f);
            table.addCell(cell);
        }
    }

    private void addSignOff(Document doc) throws DocumentException {
        PdfPTable wrapper = new PdfPTable(1);
        wrapper.setWidthPercentage(100);
        wrapper.setSpacingBefore(22);
        wrapper.setKeepTogether(true);

        PdfPCell note = new PdfPCell(new Phrase(
                "Figures exclude corrected and voided health records. This report is generated automatically; "
                        + "verify against source records before submitting to authorities.", font(7.5f, false, MUTED)));
        note.setBorder(Rectangle.NO_BORDER);
        note.setPaddingBottom(14);
        wrapper.addCell(note);

        PdfPTable signs = new PdfPTable(3);
        signs.setWidthPercentage(100);
        for (String role : List.of("Prepared by", "Checked by (Veterinarian)", "Approved by (Farm manager)")) {
            PdfPCell c = new PdfPCell();
            c.setBorder(Rectangle.NO_BORDER);
            c.setPaddingRight(18);
            Paragraph p = new Paragraph(role, font(8.5f, true, INK));
            p.setAlignment(Element.ALIGN_CENTER);
            c.addElement(p);
            Paragraph space = new Paragraph(" ", font(30, false, INK));
            c.addElement(space);
            Paragraph line = new Paragraph("(Signature, full name)      Date: ___/___/______", font(7, false, MUTED));
            line.setAlignment(Element.ALIGN_CENTER);
            c.addElement(line);
            signs.addCell(c);
        }
        PdfPCell holder = new PdfPCell(signs);
        holder.setBorder(Rectangle.NO_BORDER);
        wrapper.addCell(holder);
        doc.add(wrapper);
    }

    // ------------------------------------------------------------------ page header / footer

    /** Draws the header, footer and "Page X of Y" on every page. */
    private final class PageDecorator extends PdfPageEventHelper {
        private final ReportDataSet data;
        private PdfTemplate totalPages;

        PageDecorator(ReportDataSet data) { this.data = data; }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            totalPages = writer.getDirectContent().createTemplate(30, 10);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float left = document.left();
            float right = document.right();
            float top = document.getPageSize().getHeight() - 26;

            // header: brand mark, organisation, report title, rule
            cb.saveState();
            cb.setColorFill(BRAND);
            cb.roundRectangle(left, top - 24, 24, 24, 4);
            cb.fill();
            cb.restoreState();
            text(cb, "VB", font(9.5f, true, Color.WHITE), Element.ALIGN_CENTER, left + 12, top - 15.5f);
            text(cb, organisation.toUpperCase(), font(12, true, BRAND), Element.ALIGN_LEFT, left + 32, top - 10);
            text(cb, systemName, font(7.5f, false, MUTED), Element.ALIGN_LEFT, left + 32, top - 21);
            text(cb, data.title(), font(9, true, INK), Element.ALIGN_RIGHT, right, top - 10);
            text(cb, "Internal · Confidential", font(7.5f, false, MUTED), Element.ALIGN_RIGHT, right, top - 21);
            rule(cb, left, right, top - 31, BRAND, 1.2f);

            // footer: generation stamp left, page X of Y right
            float y = 30;
            rule(cb, left, right, y + 12, RULE, 0.5f);
            String stamp = "Generated " + STAMP.format(data.generatedAt().atZone(FARM_ZONE)) + " (ICT) by "
                    + organisation + " Reporting  ·  " + data.reportType();
            text(cb, stamp, font(7, false, MUTED), Element.ALIGN_LEFT, left, y);
            // "Page n of" right-aligned, then the total (filled in on close) after a space-width gap
            String page = "Page " + writer.getPageNumber() + " of";
            float x = right - 14;
            text(cb, page, font(7, false, MUTED), Element.ALIGN_RIGHT, x, y);
            cb.addTemplate(totalPages, x + regular.getWidthPoint(" ", 7), y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            totalPages.beginText();
            totalPages.setFontAndSize(regular, 7);
            totalPages.setColorFill(MUTED);
            totalPages.setTextMatrix(0, 0);
            totalPages.showText(String.valueOf(writer.getPageNumber() - 1));
            totalPages.endText();
        }

        private void text(PdfContentByte cb, String s, Font f, int align, float x, float y) {
            ColumnText.showTextAligned(cb, align, new Phrase(s, f), x, y, 0);
        }

        private void rule(PdfContentByte cb, float x1, float x2, float y, Color color, float width) {
            cb.saveState();
            cb.setColorStroke(color);
            cb.setLineWidth(width);
            cb.moveTo(x1, y);
            cb.lineTo(x2, y);
            cb.stroke();
            cb.restoreState();
        }
    }

    /** Horizontal share bar drawn behind a table cell. */
    private record ShareBar(float fraction) implements PdfPCellEvent {
        @Override
        public void cellLayout(PdfPCell cell, Rectangle pos, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[PdfPTable.BACKGROUNDCANVAS];
            float h = 5;
            float x = pos.getLeft() + 3;
            float w = pos.getWidth() - 6;
            float y = (pos.getTop() + pos.getBottom()) / 2 - h / 2;
            cb.saveState();
            cb.setColorFill(TRACK);
            cb.rectangle(x, y, w, h);
            cb.fill();
            cb.setColorFill(BRAND);
            cb.rectangle(x, y, Math.max(1, w * fraction), h);
            cb.fill();
            cb.restoreState();
        }
    }

    // ------------------------------------------------------------------ cells & fonts

    private Paragraph sectionHeading(String text) {
        Paragraph p = new Paragraph(text, font(10.5f, true, BRAND));
        p.setSpacingAfter(5);
        return p;
    }

    private PdfPCell headerCell(String text, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font(8, true, Color.WHITE)));
        c.setBackgroundColor(BRAND);
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(5);
        c.setPaddingBottom(6);
        return c;
    }

    private PdfPCell bodyCell(String text, int align, Color bg) {
        PdfPCell c = new PdfPCell(new Phrase(text, font(8, false, INK)));
        c.setHorizontalAlignment(align);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(4);
        c.setPaddingBottom(5);
        c.setBorder(Rectangle.BOTTOM);
        c.setBorderColor(RULE);
        c.setBorderWidth(0.5f);
        if (bg != null) c.setBackgroundColor(bg);
        return c;
    }

    private Font font(float size, boolean isBold, Color color) {
        if (isBold && bold != null) return new Font(bold, size, Font.NORMAL, color);
        return new Font(regular, size, isBold ? Font.BOLD : Font.NORMAL, color);
    }

    private static BaseFont load(String path) {
        try {
            return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static BaseFont standard(String name) {
        try {
            return BaseFont.createFont(name, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("Standard PDF font unavailable: " + name, e);
        }
    }

    // ------------------------------------------------------------------ data helpers

    private static List<ColumnDef> measures(List<ColumnDef> cols) {
        return cols.stream()
                .filter(c -> (c.type() == ColumnType.INTEGER || c.type() == ColumnType.DECIMAL) && c.format() != null)
                .toList();
    }

    private static int align(ColumnDef c) {
        return switch (c.type()) {
            case INTEGER, DECIMAL -> Element.ALIGN_RIGHT;
            case DATE, DATETIME, BOOLEAN -> Element.ALIGN_CENTER;
            case STRING -> Element.ALIGN_LEFT;
        };
    }

    private static boolean has(List<ColumnDef> cols, String key) {
        return cols.stream().anyMatch(c -> c.key().equals(key));
    }

    private static Set<Object> distinct(ReportDataSet data, String key) {
        Set<Object> values = new LinkedHashSet<>();
        for (Row r : data.rows()) if (r.get(key) != null) values.add(r.get(key));
        return values;
    }

    private static long distinctFormatted(List<Row> rows, ColumnDef c) {
        return rows.stream().map(r -> formatValue(r.get(c.key()), c)).distinct().count();
    }

    private static boolean sameKey(Row a, Row b, ColumnDef c) {
        return Objects.equals(a.get(c.key()), b.get(c.key()));
    }

    private static Object sum(List<Row> rows, ColumnDef c) {
        if (c.type() == ColumnType.DECIMAL) {
            BigDecimal total = BigDecimal.ZERO;
            for (Row r : rows) if (r.get(c.key()) instanceof Number n) total = total.add(new BigDecimal(n.toString()));
            return total;
        }
        long total = 0;
        for (Row r : rows) if (r.get(c.key()) instanceof Number n) total += n.longValue();
        return total;
    }

    /** Relative column widths from label and value lengths, so long text columns get the room. */
    private static float[] columnWidths(ReportDataSet data) {
        List<ColumnDef> cols = data.columns();
        float[] widths = new float[cols.size()];
        List<Row> sample = data.rows().subList(0, Math.min(300, data.rows().size()));
        for (int i = 0; i < cols.size(); i++) {
            ColumnDef c = cols.get(i);
            int max = c.label().length();
            for (Row r : sample) max = Math.max(max, formatValue(r.get(c.key()), c).length());
            widths[i] = Math.max(5, Math.min(40, max)) + 2;
        }
        return widths;
    }

    private static String period(ReportFilter f) {
        if (f.from().equals(f.to())) return DAY.format(f.from());
        long days = ChronoUnit.DAYS.between(f.from(), f.to()) + 1;
        return DAY.format(f.from()) + " – " + DAY.format(f.to()) + "  (" + days + " days)";
    }

    private static String granularityLabel(Granularity g) {
        return switch (g) {
            case DAY -> "Daily";
            case WEEK -> "Weekly (ISO weeks)";
            case MONTH -> "Monthly";
            case QUARTER -> "Quarterly";
            case YEAR -> "Yearly";
            case NONE -> "None";
        };
    }

    private static String formatValue(Object v, ColumnDef c) {
        if (v == null) return "";
        if (c.format() != null) {
            if (v instanceof TemporalAccessor t) return DateTimeFormatter.ofPattern(c.format()).format(t);
            if (v instanceof Number n)           return new DecimalFormat(c.format()).format(n);
        }
        if (v instanceof Boolean b) return b ? "Yes" : "No";
        return v.toString();
    }
}
