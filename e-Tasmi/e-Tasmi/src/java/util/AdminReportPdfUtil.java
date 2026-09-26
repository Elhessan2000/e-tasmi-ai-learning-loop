package util;

import model.entity.AdminReportResult;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.IOException;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders an {@link AdminReportResult} as a printable PDF (Apache PDFBox).
 */
public final class AdminReportPdfUtil {

    private static final float MARGIN = 40f;
    private static final float FILTER_FONT = 8f;
    private static final float TABLE_HEADER = 8f;
    private static final float TABLE_BODY = 7.5f;
    private static final float TABLE_LINE = 8.5f;
    private static final float TABLE_CELL_PAD = 3f;
    private static final float FOOTER_H = 22f;
    private static final float TABLE_HEAD_FILL = 0.93f;
    private static final float STROKE_COL = 0.82f;

    private AdminReportPdfUtil() {
    }

    public static void writePdf(AdminReportResult report, OutputStream out) throws IOException {
        writePdf(report, out, "Administration - Report", "Admin export", true);
    }

    /**
     * @param sectionLabel sub-header shown under the eTasmi wordmark
     * @param footerLabel  label used in the page footer ("eTasmi - {label} - Page x of y")
     * @param showFilters  whether to render the admin "Filters:" summary line
     */
    public static void writePdf(AdminReportResult report, OutputStream out,
                                String sectionLabel, String footerLabel, boolean showFilters) throws IOException {
        AdminReportResult r = report == null ? new AdminReportResult() : report;
        int numCols = r.getColumns() == null ? 0 : r.getColumns().size();
        boolean landscape = numCols > 6;
        PDRectangle media = landscape
                ? new PDRectangle(PDRectangle.LETTER.getHeight(), PDRectangle.LETTER.getWidth())
                : PDRectangle.LETTER;
        try (PDDocument document = new PDDocument()) {
            PDDocumentInformation info = new PDDocumentInformation();
            String title = r.getTitle() == null || r.getTitle().isBlank() ? "Admin report" : r.getTitle();
            info.setTitle("eTasmi - " + title);
            info.setAuthor("eTasmi");
            info.setSubject("Administrator report export");
            document.setDocumentInformation(info);

            PdfPageWriter pw = new PdfPageWriter(document, media);
            PDFont reg = PDType1Font.HELVETICA;
            PDFont bold = PDType1Font.HELVETICA_BOLD;

            pw.newPage();
            float y = pw.getTopY();
            y = pw.drawTextBlock(reg, bold, 16f, 11f, "eTasmi", y, 1f, 0.2f, 0.2f, false);
            y -= 4f;
            y = pw.drawTextBlock(reg, bold, 9f, 11f, sectionLabel, y, 0.25f, 0.25f, 0.28f, false);
            y -= 10f;
            y = pw.drawTextBlock(reg, bold, 13f, 12f, title, y, 0.09f, 0.1f, 0.12f, true);
            if (r.getSubtitle() != null && !r.getSubtitle().isBlank()) {
                y -= 2f;
                y = pw.drawTextBlock(reg, reg, 10f, 11.5f, r.getSubtitle(), y, 0.35f, 0.38f, 0.42f, false);
            }
            y -= 8f;
            y = pw.drawTextBlock(reg, reg, 8f, 10f, "Generated: "
                    + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(java.time.LocalDateTime.now()), y, 0.4f, 0.4f, 0.45f, false);
            y -= 10f;

            if (showFilters) {
                StringBuilder filterLine = new StringBuilder("Filters: ");
                filterLine.append("Category: ").append(nvl(r.getType(), "all"));
                if (r.getStatus() != null && !r.getStatus().isBlank()) {
                    filterLine.append(" - Status: ").append(r.getStatus());
                }
                if (r.getRole() != null && !r.getRole().isBlank()) {
                    filterLine.append(" - Role: ").append(r.getRole());
                }
                if (r.getDateFrom() != null && !r.getDateFrom().isBlank()) {
                    filterLine.append(" - From: ").append(r.getDateFrom());
                }
                if (r.getDateTo() != null && !r.getDateTo().isBlank()) {
                    filterLine.append(" - To: ").append(r.getDateTo());
                }
                y = pw.drawWrappedLine(reg, FILTER_FONT, filterLine.toString(), y, 0.35f, 0.35f, 0.4f, TABLE_LINE);
                y -= 4f;
            }
            y = pw.drawTextBlock(reg, reg, 9f, 10.5f, "Records: " + r.getTotalRows(), y, 0.2f, 0.2f, 0.25f, true);
            y -= 12f;

            if (numCols == 0) {
                y = pw.drawTextBlock(reg, reg, 10f, 12f, "No tabular data for this report.", y, 0.3f, 0.3f, 0.35f, false);
            } else {
                y = drawTable(pw, r, reg, bold, y);
            }
            pw.addFooters(footerLabel);
            document.save(out);
        }
    }

    private static float drawTable(PdfPageWriter pw, AdminReportResult r, PDFont reg, PDFont bold, float y) throws IOException {
        List<String> cols = r.getColumns();
        int n = cols.size();
        List<List<String>> rows = r.getRows();
        float tableWidth = pw.getContentWidth();
        float colW = tableWidth / n;
        y = ensureSpace(pw, y, 40f);

        if (rows == null || rows.isEmpty()) {
            pw.drawHLine(y + 4f, 0.75f, 0.75f, 0.78f);
            y -= 6f;
            y = pw.drawTextBlock(reg, reg, 9.5f, 11.5f, "No records match the selected report criteria.", y, 0.3f, 0.3f, 0.35f, false);
            y -= 8f;
            pw.drawHLine(y, 0.75f, 0.75f, 0.78f);
            y -= 10f;
            return y;
        }

        y = drawTableHeaderRow(pw, cols, n, colW, bold, y, tableWidth);
        y -= 4f;
        for (int ri = 0; ri < rows.size(); ri++) {
            y = drawDataRow(pw, reg, n, colW, y, tableWidth, rows.get(ri), ri);
        }
        return y;
    }

    private static float drawTableHeaderRow(PdfPageWriter pw, List<String> cols, int n, float colW,
            PDFont bold, float y, float tableWidth) throws IOException {
        float x0 = MARGIN;
        float maxBlock = 0f;
        List<List<String>> hLines = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String h = toPdfString(cols.get(i));
            List<String> lines = wrapText(bold, TABLE_HEADER, h, colW - 2 * TABLE_CELL_PAD);
            hLines.add(lines);
            maxBlock = Math.max(maxBlock, lines.size() * TABLE_LINE + 2 * TABLE_CELL_PAD);
        }
        y = ensureSpace(pw, y, maxBlock + 8f);
        float headerH = maxBlock;
        float fillTop = y;
        float fillBottom = y - headerH;
        try (PDPageContentStream s = pw.openStream()) {
            s.setNonStrokingColor(TABLE_HEAD_FILL, TABLE_HEAD_FILL, TABLE_HEAD_FILL);
            s.addRect(x0, fillBottom, tableWidth, headerH);
            s.fill();
        }
        try (PDPageContentStream s = pw.openStream()) {
            s.setStrokingColor(STROKE_COL, STROKE_COL, STROKE_COL);
            s.setLineWidth(0.5f);
            s.moveTo(x0, fillTop);
            s.lineTo(x0 + tableWidth, fillTop);
            s.lineTo(x0 + tableWidth, fillBottom);
            s.lineTo(x0, fillBottom);
            s.closePath();
            s.stroke();
        }
        for (int i = 1; i < n; i++) {
            float x = x0 + i * colW;
            try (PDPageContentStream s = pw.openStream()) {
                s.setStrokingColor(0.88f, 0.88f, 0.88f);
                s.setLineWidth(0.3f);
                s.moveTo(x, fillTop);
                s.lineTo(x, fillBottom);
                s.stroke();
            }
        }
        for (int i = 0; i < n; i++) {
            float x = x0 + i * colW;
            List<String> lines = hLines.get(i);
            float lineY = y - TABLE_CELL_PAD;
            for (String line : lines) {
                try (PDPageContentStream s = pw.openStream()) {
                    s.beginText();
                    s.setFont(bold, TABLE_HEADER);
                    s.setNonStrokingColor(0.1f, 0.1f, 0.12f);
                    s.newLineAtOffset(x + TABLE_CELL_PAD, lineY);
                    s.showText(line);
                    s.endText();
                }
                lineY -= TABLE_LINE;
            }
        }
        return fillBottom;
    }

    private static float drawDataRow(PdfPageWriter pw, PDFont reg, int n, float colW, float y, float tableWidth,
            List<String> row, int rowIndex) throws IOException {
        float x0 = MARGIN;
        List<List<String>> cellLines = new ArrayList<>();
        int maxL = 1;
        for (int i = 0; i < n; i++) {
            String val = i < row.size() ? row.get(i) : "";
            String text = toPdfString(val);
            List<String> lines = wrapText(reg, TABLE_BODY, text, colW - 2 * TABLE_CELL_PAD);
            cellLines.add(lines);
            maxL = Math.max(maxL, lines.size());
        }
        float rowH = maxL * TABLE_LINE + 2 * TABLE_CELL_PAD;
        y = ensureSpace(pw, y, rowH + 6f);
        float lineYStart = y;
        float fillBottom = y - rowH;
        if (rowIndex % 2 == 0) {
            try (PDPageContentStream s = pw.openStream()) {
                s.setNonStrokingColor(0.97f, 0.98f, 0.99f);
                s.addRect(x0, fillBottom, tableWidth, rowH);
                s.fill();
            }
        }
        try (PDPageContentStream s = pw.openStream()) {
            s.setStrokingColor(0.9f, 0.9f, 0.92f);
            s.setLineWidth(0.2f);
            s.moveTo(x0, lineYStart);
            s.lineTo(x0 + tableWidth, lineYStart);
            s.lineTo(x0 + tableWidth, fillBottom);
            s.lineTo(x0, fillBottom);
            s.closePath();
            s.stroke();
        }
        for (int i = 1; i < n; i++) {
            float x = x0 + i * colW;
            try (PDPageContentStream s = pw.openStream()) {
                s.setStrokingColor(0.9f, 0.9f, 0.92f);
                s.setLineWidth(0.2f);
                s.moveTo(x, lineYStart);
                s.lineTo(x, fillBottom);
                s.stroke();
            }
        }
        for (int i = 0; i < n; i++) {
            float x = x0 + i * colW;
            List<String> lines = cellLines.get(i);
            float lineY = lineYStart - TABLE_CELL_PAD;
            for (String line : lines) {
                try (PDPageContentStream s = pw.openStream()) {
                    s.beginText();
                    s.setFont(reg, TABLE_BODY);
                    s.setNonStrokingColor(0.12f, 0.12f, 0.16f);
                    s.newLineAtOffset(x + TABLE_CELL_PAD, lineY);
                    s.showText(line);
                    s.endText();
                }
                lineY -= TABLE_LINE;
            }
        }
        return fillBottom - 2f;
    }

    private static float ensureSpace(PdfPageWriter pw, float y, float needHeight) throws IOException {
        if (y - needHeight < MARGIN + FOOTER_H) {
            pw.newPage();
            return pw.getTopY() - 8f;
        }
        return y;
    }

    private static String nvl(String a, String d) {
        return a == null || a.isBlank() ? d : a;
    }

    private static String toPdfString(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 32 && c <= 126) {
                b.append(c);
            } else if (Character.isWhitespace(c)) {
                b.append(' ');
            } else {
                b.append('?');
            }
        }
        return b.toString();
    }

    private static List<String> wrapText(PDFont font, float fontSize, String text, float maxW) throws IOException {
        List<String> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            out.add("");
            return out;
        }
        String t = text.trim();
        if (t.isEmpty()) {
            out.add("");
            return out;
        }
        String[] words = t.split("\\s+");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (lineWidth(font, fontSize, w) > maxW) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                out.addAll(breakOversizedWord(font, fontSize, w, maxW));
                continue;
            }
            String tryLine = cur.length() == 0 ? w : cur + " " + w;
            if (lineWidth(font, fontSize, tryLine) <= maxW) {
                if (cur.length() > 0) {
                    cur.append(' ');
                }
                cur.append(w);
            } else {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                }
                cur = new StringBuilder(w);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        if (out.isEmpty()) {
            out.add("");
        }
        return out;
    }

    private static List<String> breakOversizedWord(PDFont font, float fontSize, String w, float maxW) throws IOException {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < w.length(); i++) {
            char ch = w.charAt(i);
            if (ch < 32 || ch > 126) {
                ch = '?';
            }
            String next = cur.length() == 0 ? String.valueOf(ch) : cur + String.valueOf(ch);
            if (lineWidth(font, fontSize, next) <= maxW) {
                cur.append(ch);
            } else {
                if (cur.length() > 0) {
                    parts.add(cur.toString());
                }
                cur = new StringBuilder(String.valueOf(ch));
            }
        }
        if (cur.length() > 0) {
            parts.add(cur.toString());
        }
        if (parts.isEmpty()) {
            parts.add(w.length() > 0 ? w.substring(0, 1) : "");
        }
        return parts;
    }

    private static float lineWidth(PDFont font, float size, String s) throws IOException {
        return font.getStringWidth(s) / 1000f * size;
    }

    private static final class PdfPageWriter {
        private final PDDocument document;
        private final PDRectangle media;
        private PDPage currentPage;
        private boolean firstOnPage = true;

        private PdfPageWriter(PDDocument document, PDRectangle media) {
            this.document = document;
            this.media = media;
        }

        float getContentWidth() {
            return media.getWidth() - 2 * MARGIN;
        }

        float getTopY() {
            return media.getHeight() - MARGIN;
        }

        void newPage() throws IOException {
            currentPage = new PDPage(media);
            document.addPage(currentPage);
            firstOnPage = true;
        }

        PDPageContentStream openStream() throws IOException {
            PDPageContentStream.AppendMode mode = firstOnPage
                    ? PDPageContentStream.AppendMode.OVERWRITE
                    : PDPageContentStream.AppendMode.APPEND;
            firstOnPage = false;
            return new PDPageContentStream(document, currentPage, mode, true, true);
        }

        void addFooters(String footerLabel) throws IOException {
            String label = footerLabel == null || footerLabel.isBlank() ? "export" : footerLabel;
            int total = document.getNumberOfPages();
            for (int i = 0; i < total; i++) {
                PDPage p = document.getPage(i);
                try (PDPageContentStream s = new PDPageContentStream(
                        document, p, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    s.beginText();
                    s.setFont(PDType1Font.HELVETICA, 7.5f);
                    s.setNonStrokingColor(0.45f, 0.45f, 0.5f);
                    String foot = "eTasmi - " + label + " - Page " + (i + 1) + " of " + total;
                    s.newLineAtOffset(MARGIN, MARGIN);
                    s.showText(foot);
                    s.endText();
                }
            }
        }

        void drawHLine(float y, float r, float g, float b) throws IOException {
            try (PDPageContentStream s = openStream()) {
                s.setStrokingColor(r, g, b);
                s.setLineWidth(0.4f);
                s.moveTo(MARGIN, y);
                s.lineTo(media.getWidth() - MARGIN, y);
                s.stroke();
            }
        }

        float drawTextBlock(PDFont reg, PDFont useFont, float fontSize, float lineSpacing, String text,
                float y, float cr, float cg, float cb, boolean bold) throws IOException {
            PDFont f = bold ? useFont : reg;
            List<String> lines = wrapText(f, fontSize, toPdfString(text), getContentWidth());
            for (String line : lines) {
                y = ensureSpaceForLine(y, lineSpacing + 2f);
                try (PDPageContentStream s = openStream()) {
                    s.beginText();
                    s.setFont(f, fontSize);
                    s.setNonStrokingColor(cr, cg, cb);
                    s.newLineAtOffset(MARGIN, y);
                    s.showText(line);
                    s.endText();
                }
                y -= lineSpacing;
            }
            return y;
        }

        float drawWrappedLine(PDFont f, float fontSize, String text, float y, float cr, float cg, float cb, float lineHeight) throws IOException {
            List<String> lines = wrapText(f, fontSize, toPdfString(text), getContentWidth());
            for (String line : lines) {
                y = ensureSpaceForLine(y, lineHeight);
                try (PDPageContentStream s = openStream()) {
                    s.beginText();
                    s.setFont(f, fontSize);
                    s.setNonStrokingColor(cr, cg, cb);
                    s.newLineAtOffset(MARGIN, y);
                    s.showText(line);
                    s.endText();
                }
                y -= lineHeight;
            }
            return y;
        }

        private float ensureSpaceForLine(float y, float need) throws IOException {
            if (y - need < MARGIN + FOOTER_H) {
                newPage();
                return getTopY() - 8f;
            }
            return y;
        }
    }
}
