package model.service;

import model.dao.ReportDao;
import model.dao.impl.ReportDaoJdbc;
import model.entity.AdminReportResult;
import model.entity.ReportSummary;
import util.Db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReportService {
    private static final Logger LOGGER = Logger.getLogger(ReportService.class.getName());

    private final ReportDao reportDao;

    public ReportService() {
        this.reportDao = new ReportDaoJdbc();
    }

    public ReportSummary loadSummary() {
        try (Connection connection = Db.getConnection()) {
            return reportDao.loadSummary(connection);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load report summary", ex);
            return new ReportSummary();
        }
    }

    public AdminReportResult loadAdminReport(String type, String dateFrom, String dateTo, String status, String role, String sessionMode) {
        String normalizedType = normalizeReportType(type);
        LocalDate from = parseDate(dateFrom);
        LocalDate to = parseDate(dateTo);
        String normalizedStatus = normalize(status);
        String normalizedRole = normalize(role);
        String normalizedMode = normalize(sessionMode);

        try (Connection connection = Db.getConnection()) {
            switch (normalizedType) {
                case "enrollments":
                    return buildEnrollmentsReport(connection, from, to, normalizedStatus, normalizedMode);
                case "payments":
                    return buildPaymentsReport(connection, from, to, normalizedStatus, normalizedMode);
                case "verification":
                    return buildVerificationReport(connection, from, to, normalizedStatus);
                case "evaluations":
                    return buildEvaluationsReport(connection, from, to, normalizedStatus, normalizedMode);
                case "users":
                    return buildUsersReport(connection, from, to, normalizedStatus, normalizedRole);
                case "sessions":
                default:
                    return buildSessionsReport(connection, from, to, normalizedStatus, normalizedMode);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load admin report", ex);
            AdminReportResult fallback = new AdminReportResult();
            fallback.setType(normalizedType);
            fallback.setTitle("Report unavailable");
            fallback.setSubtitle("The report could not be generated due to a server error.");
            return fallback;
        }
    }

    private AdminReportResult buildSessionsReport(Connection connection, LocalDate from, LocalDate to, String status, String mode) throws SQLException {
        AdminReportResult result = baseResult("sessions", "Sessions Report", "Scheduled, ongoing, completed, and cancelled session records.", from, to, status, null, mode);
        result.setColumns(List.of("ID", "Session", "Instructor", "Date", "Time", "Type", "Status", "Fee", "Enrollments"));

        StringBuilder sql = new StringBuilder()
                .append("SELECT ts.session_id, ts.title, iu.full_name AS instructor_name, ts.session_date, ts.session_time, ")
                .append("ts.mode, ts.status, ts.fee, COUNT(e.enrollment_id) AS enrollment_count ")
                .append("FROM tasmi_session ts ")
                .append("JOIN instructor i ON i.instructor_id = ts.instructor_id ")
                .append("JOIN `user` iu ON iu.user_id = i.user_id ")
                .append("LEFT JOIN enrollment e ON e.session_id = ts.session_id ")
                .append("WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendDateFilter(sql, params, "ts.session_date", from, to);
        appendExactFilter(sql, params, "ts.status", status);
        appendExactFilter(sql, params, "ts.mode", mode);
        sql.append("GROUP BY ts.session_id, ts.title, iu.full_name, ts.session_date, ts.session_time, ts.mode, ts.status, ts.fee ")
                .append("ORDER BY ts.session_date DESC, ts.session_time DESC, ts.session_id DESC");

        try (PreparedStatement ps = prepare(connection, sql.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.addRow(List.of(
                        String.valueOf(rs.getLong("session_id")),
                        text(rs.getString("title")),
                        text(rs.getString("instructor_name")),
                        text(String.valueOf(rs.getDate("session_date"))),
                        text(String.valueOf(rs.getTime("session_time"))),
                        text(rs.getString("mode")),
                        text(rs.getString("status")),
                        money(rs.getBigDecimal("fee")),
                        String.valueOf(rs.getLong("enrollment_count"))
                ));
            }
        }

        result.addMetric("Total Sessions", String.valueOf(result.getTotalRows()), "Matching session records");
        result.addMetric("Scheduled", String.valueOf(countRows(result, 6, "SCHEDULED")), "Upcoming sessions");
        result.addMetric("Ongoing", String.valueOf(countRows(result, 6, "ONGOING")), "Currently live");
        result.addMetric("Completed", String.valueOf(countRows(result, 6, "COMPLETED")), "Finished sessions");
        return result;
    }

    private AdminReportResult buildEnrollmentsReport(Connection connection, LocalDate from, LocalDate to, String status, String mode) throws SQLException {
        AdminReportResult result = baseResult("enrollments", "Enrollments Report", "Student enrollment records connected to real sessions and payment status.", from, to, status, null, mode);
        result.setColumns(List.of("ID", "Student", "Session", "Session Date", "Enrollment Status", "Payment Status", "Amount"));

        StringBuilder sql = new StringBuilder()
                .append("SELECT e.enrollment_id, su.full_name AS student_name, ts.title, ts.session_date, ")
                .append("e.enrollment_status, COALESCE(p.payment_status, '-') AS payment_status, p.amount, ts.mode ")
                .append("FROM enrollment e ")
                .append("JOIN student st ON st.student_id = e.student_id ")
                .append("JOIN `user` su ON su.user_id = st.user_id ")
                .append("JOIN tasmi_session ts ON ts.session_id = e.session_id ")
                .append("LEFT JOIN payment p ON p.enrollment_id = e.enrollment_id ")
                .append("WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendDateFilter(sql, params, "ts.session_date", from, to);
        appendExactFilter(sql, params, "e.enrollment_status", status);
        appendExactFilter(sql, params, "ts.mode", mode);
        sql.append("ORDER BY ts.session_date DESC, e.enrollment_id DESC");

        try (PreparedStatement ps = prepare(connection, sql.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.addRow(List.of(
                        String.valueOf(rs.getLong("enrollment_id")),
                        text(rs.getString("student_name")),
                        text(rs.getString("title")),
                        text(String.valueOf(rs.getDate("session_date"))),
                        text(rs.getString("enrollment_status")),
                        text(rs.getString("payment_status")),
                        money(rs.getBigDecimal("amount"))
                ));
            }
        }

        result.addMetric("Total Enrollments", String.valueOf(result.getTotalRows()), "Matching enrollment records");
        result.addMetric("Pending", String.valueOf(countRows(result, 4, "PENDING")), "Awaiting confirmation");
        result.addMetric("Approved", String.valueOf(countRows(result, 4, "APPROVED")), "Confirmed enrollments");
        result.addMetric("Cancelled", String.valueOf(countRows(result, 4, "CANCELLED")), "Cancelled records");
        return result;
    }

    private AdminReportResult buildPaymentsReport(Connection connection, LocalDate from, LocalDate to, String status, String mode) throws SQLException {
        AdminReportResult result = baseResult("payments", "Payments Report", "Payment records linked to enrollments, sessions, and students.", from, to, status, null, mode);
        result.setColumns(List.of("ID", "Student", "Session", "Amount", "Payment Status", "Payment Date", "Enrollment Status"));

        StringBuilder sql = new StringBuilder()
                .append("SELECT p.payment_id, su.full_name AS student_name, ts.title, p.amount, p.payment_status, p.payment_date, e.enrollment_status, ts.mode ")
                .append("FROM payment p ")
                .append("JOIN enrollment e ON e.enrollment_id = p.enrollment_id ")
                .append("JOIN student st ON st.student_id = e.student_id ")
                .append("JOIN `user` su ON su.user_id = st.user_id ")
                .append("JOIN tasmi_session ts ON ts.session_id = e.session_id ")
                .append("WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendTimestampDateFilter(sql, params, "p.payment_date", from, to);
        appendExactFilter(sql, params, "p.payment_status", status);
        appendExactFilter(sql, params, "ts.mode", mode);
        sql.append("ORDER BY p.payment_date DESC, p.payment_id DESC");

        try (PreparedStatement ps = prepare(connection, sql.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.addRow(List.of(
                        String.valueOf(rs.getLong("payment_id")),
                        text(rs.getString("student_name")),
                        text(rs.getString("title")),
                        money(rs.getBigDecimal("amount")),
                        text(rs.getString("payment_status")),
                        formatTimestamp(rs.getTimestamp("payment_date")),
                        text(rs.getString("enrollment_status"))
                ));
            }
        }

        result.addMetric("Total Payments", String.valueOf(result.getTotalRows()), "Matching payment records");
        result.addMetric("Approved", String.valueOf(countRows(result, 4, "APPROVED")), "Verified payments");
        result.addMetric("Awaiting Verification", String.valueOf(countRows(result, 4, "AWAITING_VERIFICATION")), "Pending instructor review");
        result.addMetric("Rejected", String.valueOf(countRows(result, 4, "REJECTED")), "Rejected receipts");
        return result;
    }

    private AdminReportResult buildVerificationReport(Connection connection, LocalDate from, LocalDate to, String status) throws SQLException {
        AdminReportResult result = baseResult("verification", "Instructor Verification Report", "Instructor review states connected to instructor account records.", from, to, status, null, null);
        result.setColumns(List.of("Instructor ID", "Name", "Email", "Verification Status", "Account Status", "Joined"));

        StringBuilder sql = new StringBuilder()
                .append("SELECT i.instructor_id, u.full_name, u.email, i.verification_status, u.status, u.created_at ")
                .append("FROM instructor i JOIN `user` u ON u.user_id = i.user_id WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendTimestampDateFilter(sql, params, "u.created_at", from, to);
        appendExactFilter(sql, params, "i.verification_status", status);
        sql.append("ORDER BY u.created_at DESC, i.instructor_id DESC");

        try (PreparedStatement ps = prepare(connection, sql.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.addRow(List.of(
                        String.valueOf(rs.getLong("instructor_id")),
                        text(rs.getString("full_name")),
                        text(rs.getString("email")),
                        text(rs.getString("verification_status")),
                        text(rs.getString("status")),
                        formatTimestamp(rs.getTimestamp("created_at"))
                ));
            }
        }

        result.addMetric("Total Instructors", String.valueOf(result.getTotalRows()), "Matching instructor records");
        result.addMetric("Pending", String.valueOf(countRows(result, 3, "PENDING")), "Awaiting admin review");
        result.addMetric("Approved", String.valueOf(countRows(result, 3, "APPROVED")), "Approved instructors");
        result.addMetric("Rejected", String.valueOf(countRows(result, 3, "REJECTED")), "Rejected applications");
        return result;
    }

    private AdminReportResult buildEvaluationsReport(Connection connection, LocalDate from, LocalDate to, String status, String mode) throws SQLException {
        AdminReportResult result = baseResult("evaluations", "Evaluations & Reviews Report", "Student submissions with completed or pending instructor reviews.", from, to, status, null, mode);
        result.setColumns(List.of("Recitation ID", "Student", "Session", "Submitted", "Review Status", "Score", "Instructor"));

        StringBuilder sql = new StringBuilder()
                .append("SELECT r.recitation_id, su.full_name AS student_name, ts.title, r.submission_date, ")
                .append("CASE WHEN ev.evaluation_id IS NULL THEN 'PENDING' ELSE 'COMPLETED' END AS review_status, ")
                .append("ev.score, COALESCE(iu.full_name, '-') AS instructor_name, ts.mode ")
                .append("FROM recitation r ")
                .append("JOIN enrollment en ON en.enrollment_id = r.enrollment_id ")
                .append("JOIN student st ON st.student_id = en.student_id ")
                .append("JOIN `user` su ON su.user_id = st.user_id ")
                .append("JOIN tasmi_session ts ON ts.session_id = en.session_id ")
                .append("LEFT JOIN evaluation ev ON ev.recitation_id = r.recitation_id ")
                .append("LEFT JOIN instructor ins ON ins.instructor_id = ev.instructor_id ")
                .append("LEFT JOIN `user` iu ON iu.user_id = ins.user_id ")
                .append("WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendTimestampDateFilter(sql, params, "r.submission_date", from, to);
        appendExactFilter(sql, params, "ts.mode", mode);
        if ("PENDING".equals(status)) {
            sql.append("AND ev.evaluation_id IS NULL ");
        } else if ("COMPLETED".equals(status)) {
            sql.append("AND ev.evaluation_id IS NOT NULL ");
        }
        sql.append("ORDER BY r.submission_date DESC, r.recitation_id DESC");

        int scoreTotal = 0;
        int scoreCount = 0;
        try (PreparedStatement ps = prepare(connection, sql.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Integer score = null;
                int rawScore = rs.getInt("score");
                if (!rs.wasNull()) {
                    score = rawScore;
                    scoreTotal += rawScore;
                    scoreCount++;
                }
                result.addRow(List.of(
                        String.valueOf(rs.getLong("recitation_id")),
                        text(rs.getString("student_name")),
                        text(rs.getString("title")),
                        formatTimestamp(rs.getTimestamp("submission_date")),
                        text(rs.getString("review_status")),
                        score == null ? "-" : String.valueOf(score),
                        text(rs.getString("instructor_name"))
                ));
            }
        }

        result.addMetric("Total Submissions", String.valueOf(result.getTotalRows()), "Matching recitation submissions");
        result.addMetric("Reviewed", String.valueOf(countRows(result, 4, "COMPLETED")), "Completed evaluations");
        result.addMetric("Pending Review", String.valueOf(countRows(result, 4, "PENDING")), "Awaiting feedback");
        result.addMetric("Average Score", scoreCount == 0 ? "N/A" : String.format("%.1f", (double) scoreTotal / scoreCount), "Reviewed submissions only");
        return result;
    }

    private AdminReportResult buildUsersReport(Connection connection, LocalDate from, LocalDate to, String status, String role) throws SQLException {
        AdminReportResult result = baseResult("users", "Users Report", "User accounts by role, status, activation, and email verification.", from, to, status, role, null);
        result.setColumns(List.of("ID", "Name", "Email", "Role", "Account Status", "Active", "Email Verified", "Joined"));

        StringBuilder sql = new StringBuilder()
                .append("SELECT user_id, full_name, email, role, status, is_active, email_verified, created_at ")
                .append("FROM `user` WHERE 1=1 ");
        List<Object> params = new ArrayList<>();
        appendTimestampDateFilter(sql, params, "created_at", from, to);
        appendExactFilter(sql, params, "status", status);
        appendExactFilter(sql, params, "role", role);
        sql.append("ORDER BY created_at DESC, user_id DESC");

        try (PreparedStatement ps = prepare(connection, sql.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.addRow(List.of(
                        String.valueOf(rs.getLong("user_id")),
                        text(rs.getString("full_name")),
                        text(rs.getString("email")),
                        text(rs.getString("role")),
                        text(rs.getString("status")),
                        rs.getBoolean("is_active") ? "Yes" : "No",
                        rs.getBoolean("email_verified") ? "Yes" : "No",
                        formatTimestamp(rs.getTimestamp("created_at"))
                ));
            }
        }

        result.addMetric("Total Users", String.valueOf(result.getTotalRows()), "Matching user records");
        result.addMetric("Students", String.valueOf(countRows(result, 3, "STUDENT")), "Student accounts");
        result.addMetric("Instructors", String.valueOf(countRows(result, 3, "INSTRUCTOR")), "Instructor accounts");
        result.addMetric("Active", String.valueOf(countRows(result, 5, "Yes")), "Active user rows");
        return result;
    }

    private AdminReportResult baseResult(String type, String title, String subtitle, LocalDate from, LocalDate to, String status, String role, String mode) {
        AdminReportResult result = new AdminReportResult();
        result.setType(type);
        result.setTitle(title);
        result.setSubtitle(subtitle);
        result.setDateFrom(from == null ? "" : from.toString());
        result.setDateTo(to == null ? "" : to.toString());
        result.setStatus(status == null ? "" : status);
        result.setRole(role == null ? "" : role);
        result.setSessionMode(mode == null ? "" : mode);
        return result;
    }

    private PreparedStatement prepare(Connection connection, String sql, List<Object> params) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(sql);
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            if (value instanceof LocalDate) {
                ps.setDate(i + 1, Date.valueOf((LocalDate) value));
            } else {
                ps.setObject(i + 1, value);
            }
        }
        return ps;
    }

    private void appendExactFilter(StringBuilder sql, List<Object> params, String column, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append("AND ").append(column).append(" = ? ");
        params.add(value);
    }

    private void appendDateFilter(StringBuilder sql, List<Object> params, String column, LocalDate from, LocalDate to) {
        if (from != null) {
            sql.append("AND ").append(column).append(" >= ? ");
            params.add(from);
        }
        if (to != null) {
            sql.append("AND ").append(column).append(" <= ? ");
            params.add(to);
        }
    }

    private void appendTimestampDateFilter(StringBuilder sql, List<Object> params, String column, LocalDate from, LocalDate to) {
        if (from != null) {
            sql.append("AND DATE(").append(column).append(") >= ? ");
            params.add(from);
        }
        if (to != null) {
            sql.append("AND DATE(").append(column).append(") <= ? ");
            params.add(to);
        }
    }

    private String normalizeReportType(String type) {
        String value = normalize(type);
        if (value == null) {
            return "sessions";
        }
        switch (value) {
            case "ENROLLMENTS": return "enrollments";
            case "PAYMENTS": return "payments";
            case "VERIFICATION":
            case "INSTRUCTOR_VERIFICATION": return "verification";
            case "EVALUATIONS":
            case "REVIEWS": return "evaluations";
            case "USERS": return "users";
            case "SESSIONS":
            default: return "sessions";
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "ALL".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed.toUpperCase();
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private long countRows(AdminReportResult result, int columnIndex, String expected) {
        if (result == null || expected == null) {
            return 0;
        }
        long count = 0;
        for (List<String> row : result.getRows()) {
            if (row.size() > columnIndex && expected.equalsIgnoreCase(row.get(columnIndex))) {
                count++;
            }
        }
        return count;
    }

    private String text(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String money(BigDecimal amount) {
        if (amount == null) {
            return "-";
        }
        return "RM " + amount.stripTrailingZeros().toPlainString();
    }

    private String formatTimestamp(Timestamp timestamp) {
        if (timestamp == null) {
            return "-";
        }
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(timestamp.toLocalDateTime());
    }
}
