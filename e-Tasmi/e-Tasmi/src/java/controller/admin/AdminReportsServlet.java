package controller.admin;

import model.entity.AdminReportResult;
import model.entity.ReportSummary;
import model.service.ReportService;
import util.AdminReportPdfUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@WebServlet(name = "AdminReportsServlet", urlPatterns = {"/admin/reports"})
public class AdminReportsServlet extends HttpServlet {
    private final ReportService reportService = new ReportService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        ReportSummary summary = reportService.loadSummary();
        AdminReportResult report = reportService.loadAdminReport(
                request.getParameter("type"),
                request.getParameter("dateFrom"),
                request.getParameter("dateTo"),
                request.getParameter("status"),
                request.getParameter("role"),
                request.getParameter("sessionMode")
        );

        if ("csv".equalsIgnoreCase(request.getParameter("export"))) {
            writeCsv(response, report);
            return;
        }
        if ("pdf".equalsIgnoreCase(request.getParameter("export"))) {
            writePdf(response, report);
            return;
        }

        request.setAttribute("activeMenu", "reports");
        request.setAttribute("summary", summary);
        request.setAttribute("report", report);
        request.getRequestDispatcher("/jsp/admin/reports.jsp").forward(request, response);
    }

    private void writePdf(HttpServletResponse response, AdminReportResult report) throws IOException {
        String fileName = "etasmi-"
                + safeFilePart(report == null ? "report" : report.getType()) + "-"
                + LocalDate.now() + ".pdf";
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setHeader("Cache-Control", "private, no-store");
        AdminReportPdfUtil.writePdf(report, response.getOutputStream());
        response.getOutputStream().flush();
    }

    private void writeCsv(HttpServletResponse response, AdminReportResult report) throws IOException {
        String fileName = "etasmi-" + safeFilePart(report == null ? "report" : report.getType()) + "-" + LocalDate.now() + ".csv";
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

        StringBuilder csv = new StringBuilder();
        if (report != null) {
            appendCsvRow(csv, report.getColumns());
            for (List<String> row : report.getRows()) {
                appendCsvRow(csv, row);
            }
        }
        response.getWriter().write(csv.toString());
    }

    private void appendCsvRow(StringBuilder csv, List<String> values) {
        if (values == null || values.isEmpty()) {
            csv.append('\n');
            return;
        }
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(csvEscape(values.get(i)));
        }
        csv.append('\n');
    }

    private String csvEscape(String value) {
        String v = value == null ? "" : value;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    private String safeFilePart(String value) {
        if (value == null || value.isBlank()) {
            return "report";
        }
        return value.toLowerCase().replaceAll("[^a-z0-9_-]+", "-");
    }
}
