package controller.admin;

import model.dao.PaymentDao;
import model.dao.PaymentVerificationHistoryDao;
import model.dao.impl.PaymentDaoJdbc;
import model.dao.impl.PaymentVerificationHistoryDaoJdbc;
import model.entity.PaymentQueryFilter;
import model.entity.PaymentStats;
import model.entity.PaymentStatus;
import model.entity.PaymentTransactionRow;
import model.entity.PaymentVerificationHistory;
import model.entity.RevenueBucket;
import model.service.PaymentReportService;
import util.Db;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Admin platform-wide payment monitoring dashboard.
 *
 * <ul>
 *   <li>GET /admin/payments → stat cards, revenue charts, filtered + paginated transaction table.</li>
 *   <li>GET /admin/payments?ajax=detail&paymentId=.. → JSON transaction detail + verification history.</li>
 *   <li>GET /admin/payments?export=.. → PDF reports (delegated to {@link PaymentReportService}).</li>
 * </ul>
 *
 * The admin is a pure monitor — money moves directly between students and instructors.
 */
@WebServlet(name = "AdminPaymentsServlet", urlPatterns = {"/admin/payments"})
public class AdminPaymentsServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(AdminPaymentsServlet.class.getName());
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final int PAGE_SIZE = 25;

    private final PaymentDao paymentDao = new PaymentDaoJdbc();
    private final PaymentVerificationHistoryDao historyDao = new PaymentVerificationHistoryDaoJdbc();
    private final PaymentReportService reportService = new PaymentReportService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String ajax = trim(request.getParameter("ajax"));
        if ("detail".equalsIgnoreCase(ajax)) {
            writeDetailJson(request, response);
            return;
        }

        String export = trim(request.getParameter("export"));
        if (export != null) {
            reportService.export(request, response, export, baseFilter(request));
            return;
        }

        request.setAttribute("activeMenu", "payments");

        int page = Math.max(1, (int) parseLong(request.getParameter("page")));
        PaymentQueryFilter filter = baseFilter(request);
        filter.setLimit(PAGE_SIZE);
        filter.setOffset((page - 1) * PAGE_SIZE);
        filter.setSortBy(trim(request.getParameter("sort")) == null ? "created_at" : trim(request.getParameter("sort")));
        filter.setSortAsc("asc".equalsIgnoreCase(trim(request.getParameter("dir"))));

        try (java.sql.Connection connection = Db.getConnection()) {
            PaymentStats stats = paymentDao.loadStats(connection, filter);
            List<PaymentTransactionRow> rows = paymentDao.search(connection, filter);
            int total = paymentDao.count(connection, filter);
            List<RevenueBucket> bySession = paymentDao.revenueBySession(connection, filter, 8);
            List<RevenueBucket> byInstructor = paymentDao.revenueByInstructor(connection, filter, 8);

            int totalPages = (int) Math.ceil(total / (double) PAGE_SIZE);

            request.setAttribute("stats", stats);
            request.setAttribute("rows", rows);
            request.setAttribute("totalCount", total);
            request.setAttribute("page", page);
            request.setAttribute("totalPages", Math.max(1, totalPages));
            request.setAttribute("revenueBySession", bySession);
            request.setAttribute("revenueByInstructor", byInstructor);
            request.setAttribute("sortBy", toSortUiKey(filter.getSortBy()));
            request.setAttribute("sortDir", filter.isSortAsc() ? "asc" : "desc");
            request.setAttribute("sessionChartJson", toChartJson(bySession));
            request.setAttribute("instructorChartJson", toChartJson(byInstructor));
            request.setAttribute("filterStatus", filter.getStatus() == null ? "" : filter.getStatus().name());
            request.setAttribute("filterSearch", filter.getSearch() == null ? "" : filter.getSearch());
            request.setAttribute("filterFromDate", filter.getFromDate() == null ? "" : filter.getFromDate().toString());
            request.setAttribute("filterToDate", filter.getToDate() == null ? "" : filter.getToDate().toString());
            request.setAttribute("filterMinAmount", filter.getMinAmount() == null ? "" : filter.getMinAmount().toPlainString());
            request.setAttribute("filterMaxAmount", filter.getMaxAmount() == null ? "" : filter.getMaxAmount().toPlainString());
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to build admin payments dashboard", ex);
            request.setAttribute("error", "We could not load the payments dashboard. Please try again.");
            request.setAttribute("stats", new PaymentStats());
            request.setAttribute("rows", Collections.emptyList());
            request.setAttribute("revenueBySession", Collections.emptyList());
            request.setAttribute("revenueByInstructor", Collections.emptyList());
            request.setAttribute("sessionChartJson", "[]");
            request.setAttribute("instructorChartJson", "[]");
            request.setAttribute("page", 1);
            request.setAttribute("totalPages", 1);
            request.setAttribute("totalCount", 0L);
        }

        request.getRequestDispatcher("/jsp/admin/payments.jsp").forward(request, response);
    }

    private PaymentQueryFilter baseFilter(HttpServletRequest request) {
        PaymentQueryFilter filter = new PaymentQueryFilter();
        filter.setStudentId(parseLongOrNull(request.getParameter("studentId")));
        filter.setInstructorId(parseLongOrNull(request.getParameter("instructorId")));
        filter.setSessionId(parseLongOrNull(request.getParameter("sessionId")));
        filter.setStatus(PaymentStatus.fromString(request.getParameter("status")));
        filter.setFromDate(parseDate(request.getParameter("fromDate")));
        filter.setToDate(parseDate(request.getParameter("toDate")));
        filter.setMinAmount(parseAmount(request.getParameter("minAmount")));
        filter.setMaxAmount(parseAmount(request.getParameter("maxAmount")));
        filter.setSearch(trim(request.getParameter("search")));
        return filter;
    }

    private void writeDetailJson(HttpServletRequest request, HttpServletResponse response) throws IOException {
        long paymentId = parseLong(request.getParameter("paymentId"));
        response.setContentType("application/json;charset=UTF-8");
        if (paymentId <= 0) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\":false,\"error\":\"Invalid payment.\"}");
            return;
        }

        try (java.sql.Connection connection = Db.getConnection()) {
            Optional<PaymentTransactionRow> rowOpt = paymentDao.findTransactionById(connection, paymentId);
            if (rowOpt.isEmpty()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.getWriter().write("{\"success\":false,\"error\":\"Transaction not found.\"}");
                return;
            }
            PaymentTransactionRow r = rowOpt.get();
            List<PaymentVerificationHistory> history = historyDao.listByPayment(connection, paymentId);

            String receipt = r.getReceiptFilePath();
            String receiptUrl = "";
            if (receipt != null && !receipt.isBlank()) {
                receiptUrl = (receipt.startsWith("http://") || receipt.startsWith("https://")) ? receipt
                        : (receipt.startsWith("/") ? request.getContextPath() + receipt : request.getContextPath() + "/" + receipt);
            }

            StringBuilder json = new StringBuilder();
            json.append("{\"success\":true,\"transaction\":{");
            json.append("\"paymentId\":").append(r.getPaymentId()).append(',');
            json.append("\"studentName\":\"").append(jsonEscape(r.getStudentName())).append("\",");
            json.append("\"studentEmail\":\"").append(jsonEscape(r.getStudentEmail())).append("\",");
            json.append("\"instructorName\":\"").append(jsonEscape(r.getInstructorName())).append("\",");
            json.append("\"instructorEmail\":\"").append(jsonEscape(r.getInstructorEmail())).append("\",");
            json.append("\"sessionTitle\":\"").append(jsonEscape(r.getSessionTitle())).append("\",");
            json.append("\"amount\":\"").append(formatMoney(r.getAmount())).append("\",");
            json.append("\"currency\":\"").append(jsonEscape(r.getCurrency())).append("\",");
            json.append("\"status\":\"").append(r.getStatus() == null ? "" : r.getStatus().name()).append("\",");
            json.append("\"statusLabel\":\"").append(jsonEscape(r.getStatus() == null ? "" : r.getStatus().displayLabel())).append("\",");
            json.append("\"reference\":\"").append(jsonEscape(r.getPaymentReference())).append("\",");
            json.append("\"studentNote\":\"").append(jsonEscape(r.getStudentNote())).append("\",");
            json.append("\"verificationNote\":\"").append(jsonEscape(r.getVerificationNote())).append("\",");
            json.append("\"createdAt\":\"").append(jsonEscape(formatInstant(r.getCreatedAt()))).append("\",");
            json.append("\"paymentDate\":\"").append(jsonEscape(formatInstant(r.getPaymentDate()))).append("\",");
            json.append("\"receiptUrl\":\"").append(jsonEscape(receiptUrl)).append("\"");
            json.append("},\"history\":[");
            for (int i = 0; i < history.size(); i++) {
                PaymentVerificationHistory h = history.get(i);
                if (i > 0) json.append(',');
                json.append('{');
                json.append("\"action\":\"").append(jsonEscape(h.getAction())).append("\",");
                json.append("\"actor\":\"").append(jsonEscape(h.getActorName())).append("\",");
                json.append("\"actorRole\":\"").append(jsonEscape(h.getActorRole())).append("\",");
                json.append("\"reason\":\"").append(jsonEscape(h.getReason())).append("\",");
                json.append("\"at\":\"").append(jsonEscape(formatInstant(h.getCreatedAt()))).append("\"");
                json.append('}');
            }
            json.append("]}");
            response.getWriter().write(json.toString());
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to load payment detail", ex);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"success\":false,\"error\":\"Server error.\"}");
        }
    }

    private Long parseLongOrNull(String raw) {
        long value = parseLong(raw);
        return value > 0 ? value : null;
    }

    private long parseLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private BigDecimal parseAmount(String raw) {
        String value = trim(raw);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDate parseDate(String raw) {
        String value = trim(raw);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private String formatMoney(BigDecimal amount) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatInstant(Instant instant) {
        return instant == null ? "-" : DISPLAY_DATE.format(instant);
    }

    /** Maps DAO sort columns to UI column keys used in the JSP. */
    private String toSortUiKey(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return "date";
        }
        switch (sortBy.trim().toLowerCase()) {
            case "created_at":
            case "payment_date":
            case "p.created_at":
            case "p.payment_date":
                return "date";
            case "p.amount":
                return "amount";
            case "su.full_name":
                return "student";
            case "ts.title":
                return "session";
            case "iu.full_name":
                return "instructor";
            case "p.payment_status":
                return "status";
            default:
                return sortBy.trim().toLowerCase();
        }
    }

    private String toChartJson(List<RevenueBucket> buckets) {
        if (buckets == null || buckets.isEmpty()) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < buckets.size(); i++) {
            RevenueBucket bucket = buckets.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"label\":\"").append(jsonEscape(bucket.getLabel())).append("\",\"value\":");
            BigDecimal revenue = bucket.getRevenue();
            json.append(revenue == null ? "0" : revenue.toPlainString());
            json.append('}');
        }
        json.append(']');
        return json.toString();
    }
}
