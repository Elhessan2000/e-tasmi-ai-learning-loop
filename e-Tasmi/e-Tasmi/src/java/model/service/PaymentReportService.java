package model.service;

import model.dao.PaymentDao;
import model.dao.impl.PaymentDaoJdbc;
import model.entity.AdminReportResult;
import model.entity.PaymentQueryFilter;
import model.entity.PaymentStats;
import model.entity.PaymentStatus;
import model.entity.PaymentTransactionRow;
import model.entity.RevenueBucket;
import util.AdminReportPdfUtil;
import util.Db;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generates filtered payment PDF exports for the admin monitoring dashboard.
 */
public class PaymentReportService {
    private static final Logger LOGGER = Logger.getLogger(PaymentReportService.class.getName());
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final int EXPORT_LIMIT = 5000;

    private final PaymentDao paymentDao = new PaymentDaoJdbc();

    public void export(HttpServletRequest request, HttpServletResponse response,
                       String exportType, PaymentQueryFilter filter) throws IOException {
        String type = normalizeExportType(exportType);
        AdminReportResult report = buildReport(type, filter, request);
        String fileName = "etasmi-payments-" + type + "-" + LocalDate.now() + ".pdf";
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setHeader("Cache-Control", "private, no-store");
        try {
            AdminReportPdfUtil.writePdf(report, response.getOutputStream(),
                    "Finance - Payment Reports", "Payment export", true);
            response.getOutputStream().flush();
        } catch (IOException ex) {
            throw ex;
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to write payment PDF export", ex);
            response.reset();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not generate PDF.");
        }
    }

    private AdminReportResult buildReport(String type, PaymentQueryFilter filter, HttpServletRequest request) {
        PaymentQueryFilter exportFilter = copyFilter(filter);
        exportFilter.setOffset(0);
        exportFilter.setLimit(EXPORT_LIMIT);
        exportFilter.setSortBy("created_at");
        exportFilter.setSortAsc(false);

        try (Connection connection = Db.getConnection()) {
            switch (type) {
                case "session":
                    return buildSessionReport(connection, exportFilter, request);
                case "student":
                    return buildStudentReport(connection, exportFilter, request);
                case "instructor":
                    return buildInstructorReport(connection, exportFilter, request);
                case "revenue-session":
                    return buildRevenueBySessionReport(connection, exportFilter);
                case "revenue-instructor":
                    return buildRevenueByInstructorReport(connection, exportFilter);
                case "financial":
                    return buildFinancialSummaryReport(connection, exportFilter);
                case "pdf":
                default:
                    return buildTransactionsReport(connection, exportFilter);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to build payment report", ex);
            AdminReportResult fallback = new AdminReportResult();
            fallback.setType(type);
            fallback.setTitle("Payment report unavailable");
            fallback.setSubtitle("The report could not be generated due to a server error.");
            applyFilterMeta(fallback, exportFilter);
            return fallback;
        }
    }

    private AdminReportResult buildTransactionsReport(Connection connection, PaymentQueryFilter filter) throws SQLException {
        AdminReportResult result = new AdminReportResult();
        result.setType("payments");
        result.setTitle("Platform Payment Transactions");
        result.setSubtitle("All QR-transfer payment records matching the selected filters.");
        applyFilterMeta(result, filter);
        result.setColumns(List.of(
                "ID", "Date", "Student", "Email", "Instructor", "Session", "Amount", "Currency", "Status", "Reference"));

        List<PaymentTransactionRow> rows = paymentDao.search(connection, filter);
        PaymentStats stats = paymentDao.loadStats(connection, filter);
        for (PaymentTransactionRow row : rows) {
            result.addRow(rowToTransactionCells(row));
        }

        result.addMetric("Total transactions", String.valueOf(stats.getTotalTransactions()), "Matching filter");
        result.addMetric("Approved", String.valueOf(stats.getApprovedCount()), "Verified payments");
        result.addMetric("Awaiting verification", String.valueOf(stats.getAwaitingCount()), "Pending instructor review");
        result.addMetric("Rejected", String.valueOf(stats.getRejectedCount()), "Rejected receipts");
        result.addMetric("Total revenue", money(stats.getTotalRevenue(), stats.getCurrency()), "Sum of approved payments");
        return result;
    }

    private AdminReportResult buildSessionReport(Connection connection, PaymentQueryFilter filter, HttpServletRequest request) throws SQLException {
        Long sessionId = filter.getSessionId();
        if (sessionId == null || sessionId <= 0) {
            sessionId = parseLong(request.getParameter("sessionId"));
        }
        if (sessionId == null || sessionId <= 0) {
            return emptyReport("session", "Session Payment Report", "A sessionId is required for this report.", filter);
        }
        filter.setSessionId(sessionId);
        AdminReportResult result = buildTransactionsReport(connection, filter);
        result.setType("session");
        result.setTitle("Session Payment Report");
        result.setSubtitle("Transactions for session #" + sessionId + ".");
        return result;
    }

    private AdminReportResult buildStudentReport(Connection connection, PaymentQueryFilter filter, HttpServletRequest request) throws SQLException {
        Long studentId = filter.getStudentId();
        if (studentId == null || studentId <= 0) {
            studentId = parseLong(request.getParameter("studentId"));
        }
        if (studentId == null || studentId <= 0) {
            return emptyReport("student", "Student Payment Report", "A studentId is required for this report.", filter);
        }
        filter.setStudentId(studentId);
        AdminReportResult result = buildTransactionsReport(connection, filter);
        result.setType("student");
        result.setTitle("Student Payment Report");
        result.setSubtitle("All payment transactions for student #" + studentId + ".");
        return result;
    }

    private AdminReportResult buildInstructorReport(Connection connection, PaymentQueryFilter filter, HttpServletRequest request) throws SQLException {
        Long instructorId = filter.getInstructorId();
        if (instructorId == null || instructorId <= 0) {
            instructorId = parseLong(request.getParameter("instructorId"));
        }
        if (instructorId == null || instructorId <= 0) {
            return emptyReport("instructor", "Instructor Payment Report", "An instructorId is required for this report.", filter);
        }
        filter.setInstructorId(instructorId);
        AdminReportResult result = buildTransactionsReport(connection, filter);
        result.setType("instructor");
        result.setTitle("Instructor Earnings Report");
        result.setSubtitle("Payment history and approved revenue for instructor #" + instructorId + ".");
        return result;
    }

    private AdminReportResult buildRevenueBySessionReport(Connection connection, PaymentQueryFilter filter) throws SQLException {
        AdminReportResult result = new AdminReportResult();
        result.setType("revenue-session");
        result.setTitle("Revenue by Session");
        result.setSubtitle("Approved payment totals grouped by session.");
        applyFilterMeta(result, filter);
        result.setColumns(List.of("Session ID", "Session", "Transactions", "Approved Revenue"));

        List<RevenueBucket> buckets = paymentDao.revenueBySession(connection, filter, 200);
        BigDecimal total = BigDecimal.ZERO;
        for (RevenueBucket bucket : buckets) {
            result.addRow(List.of(
                    String.valueOf(bucket.getId()),
                    text(bucket.getLabel()),
                    String.valueOf(bucket.getTransactionCount()),
                    money(bucket.getRevenue(), "MYR")));
            total = total.add(bucket.getRevenue());
        }
        result.addMetric("Sessions", String.valueOf(buckets.size()), "With approved revenue");
        result.addMetric("Total revenue", money(total, "MYR"), "Sum across sessions");
        return result;
    }

    private AdminReportResult buildRevenueByInstructorReport(Connection connection, PaymentQueryFilter filter) throws SQLException {
        AdminReportResult result = new AdminReportResult();
        result.setType("revenue-instructor");
        result.setTitle("Revenue by Instructor");
        result.setSubtitle("Approved payment totals grouped by instructor.");
        applyFilterMeta(result, filter);
        result.setColumns(List.of("Instructor ID", "Instructor", "Transactions", "Approved Revenue"));

        List<RevenueBucket> buckets = paymentDao.revenueByInstructor(connection, filter, 200);
        BigDecimal total = BigDecimal.ZERO;
        for (RevenueBucket bucket : buckets) {
            result.addRow(List.of(
                    String.valueOf(bucket.getId()),
                    text(bucket.getLabel()),
                    String.valueOf(bucket.getTransactionCount()),
                    money(bucket.getRevenue(), "MYR")));
            total = total.add(bucket.getRevenue());
        }
        result.addMetric("Instructors", String.valueOf(buckets.size()), "With approved revenue");
        result.addMetric("Total revenue", money(total, "MYR"), "Sum across instructors");
        return result;
    }

    private AdminReportResult buildFinancialSummaryReport(Connection connection, PaymentQueryFilter filter) throws SQLException {
        AdminReportResult result = new AdminReportResult();
        result.setType("financial");
        result.setTitle("Financial Summary");
        result.setSubtitle("Platform-wide payment counters and approved revenue.");
        applyFilterMeta(result, filter);
        result.setColumns(List.of("Metric", "Value", "Notes"));

        PaymentStats stats = paymentDao.loadStats(connection, filter);
        result.addRow(List.of("Total transactions", String.valueOf(stats.getTotalTransactions()), "All statuses"));
        result.addRow(List.of("Awaiting payment", String.valueOf(stats.getPendingCount()), "No receipt uploaded yet"));
        result.addRow(List.of("Awaiting verification", String.valueOf(stats.getAwaitingCount()), "Receipt submitted"));
        result.addRow(List.of("Approved", String.valueOf(stats.getApprovedCount()), "Instructor verified"));
        result.addRow(List.of("Rejected", String.valueOf(stats.getRejectedCount()), "Needs resubmission"));
        result.addRow(List.of("Total approved revenue", money(stats.getTotalRevenue(), stats.getCurrency()), "Direct to instructors"));

        List<RevenueBucket> topSessions = paymentDao.revenueBySession(connection, filter, 5);
        List<RevenueBucket> topInstructors = paymentDao.revenueByInstructor(connection, filter, 5);
        result.addMetric("Top sessions", String.valueOf(topSessions.size()), "By approved revenue");
        result.addMetric("Top instructors", String.valueOf(topInstructors.size()), "By approved revenue");
        result.addMetric("Currency", stats.getCurrency(), "Display currency");
        return result;
    }

    private AdminReportResult emptyReport(String type, String title, String subtitle, PaymentQueryFilter filter) {
        AdminReportResult result = new AdminReportResult();
        result.setType(type);
        result.setTitle(title);
        result.setSubtitle(subtitle);
        applyFilterMeta(result, filter);
        result.setColumns(List.of("Message"));
        result.addRow(List.of(subtitle));
        return result;
    }

    private List<String> rowToTransactionCells(PaymentTransactionRow row) {
        List<String> cells = new ArrayList<>();
        cells.add(String.valueOf(row.getPaymentId()));
        cells.add(formatInstant(row.getCreatedAt()));
        cells.add(text(row.getStudentName()));
        cells.add(text(row.getStudentEmail()));
        cells.add(text(row.getInstructorName()));
        cells.add(text(row.getSessionTitle()));
        cells.add(money(row.getAmount(), null));
        cells.add(text(row.getCurrency()));
        cells.add(row.getStatus() == null ? "-" : row.getStatus().displayLabel());
        cells.add(text(row.getPaymentReference()));
        return cells;
    }

    private void applyFilterMeta(AdminReportResult result, PaymentQueryFilter filter) {
        if (filter.getStatus() != null) {
            result.setStatus(filter.getStatus().name());
        }
        if (filter.getFromDate() != null) {
            result.setDateFrom(filter.getFromDate().toString());
        }
        if (filter.getToDate() != null) {
            result.setDateTo(filter.getToDate().toString());
        }
        if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
            result.setRole("search:" + filter.getSearch().trim());
        }
    }

    private PaymentQueryFilter copyFilter(PaymentQueryFilter source) {
        PaymentQueryFilter copy = new PaymentQueryFilter();
        if (source == null) {
            return copy;
        }
        copy.setStudentId(source.getStudentId());
        copy.setInstructorId(source.getInstructorId());
        copy.setSessionId(source.getSessionId());
        copy.setStatus(source.getStatus());
        copy.setFromDate(source.getFromDate());
        copy.setToDate(source.getToDate());
        copy.setMinAmount(source.getMinAmount());
        copy.setMaxAmount(source.getMaxAmount());
        copy.setSearch(source.getSearch());
        copy.setSortBy(source.getSortBy());
        copy.setSortAsc(source.isSortAsc());
        copy.setOffset(source.getOffset());
        copy.setLimit(source.getLimit());
        return copy;
    }

    private String normalizeExportType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "pdf";
        }
        String normalized = raw.trim().toLowerCase();
        switch (normalized) {
            case "sessionpdf":
            case "session":
                return "session";
            case "studentpdf":
            case "student":
                return "student";
            case "instructorpdf":
            case "instructor":
                return "instructor";
            case "revenue-session":
            case "revenue_by_session":
                return "revenue-session";
            case "revenue-instructor":
            case "revenue_by_instructor":
                return "revenue-instructor";
            case "financial":
            case "financial-summary":
            case "summary":
                return "financial";
            default:
                return "pdf";
        }
    }

    private Long parseLong(String raw) {
        try {
            long value = Long.parseLong(raw);
            return value > 0 ? value : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String text(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private String money(BigDecimal amount, String currency) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        String cur = currency == null || currency.isBlank() ? "MYR" : currency.trim().toUpperCase();
        return cur + " " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "-" : DISPLAY_DATE.format(instant);
    }
}
