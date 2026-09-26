package model.dao.impl;

import model.dao.PaymentDao;
import model.entity.EnrollmentStatus;
import model.entity.Payment;
import model.entity.PaymentQueryFilter;
import model.entity.PaymentStats;
import model.entity.PaymentStatus;
import model.entity.PaymentTransactionRow;
import model.entity.RevenueBucket;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PaymentDaoJdbc implements PaymentDao {
    private static final String TABLE = "payment";

    private static final String COL_PAYMENT_ID = "payment_id";
    private static final String COL_ENROLLMENT_ID = "enrollment_id";
    private static final String COL_AMOUNT = "amount";
    private static final String COL_PAYMENT_STATUS = "payment_status";
    private static final String COL_PAYMENT_DATE = "payment_date";
    private static final String COL_VERIFIED_BY_ADMIN_ID = "verified_by_admin_id";
    private static final String COL_RECEIPT_FILE_PATH = "receipt_file_path";
    private static final String COL_RECEIPT_SUBMITTED_AT = "receipt_submitted_at";
    private static final String COL_VERIFIED_BY_INSTRUCTOR_ID = "verified_by_instructor_id";
    private static final String COL_VERIFICATION_NOTE = "verification_note";
    private static final String COL_PAYMENT_REFERENCE = "payment_reference";
    private static final String COL_STUDENT_NOTE = "student_note";
    private static final String COL_CURRENCY = "currency";
    private static final String COL_CREATED_AT = "created_at";

    private static final String PAYMENT_COLUMNS = String.join(",",
            COL_PAYMENT_ID, COL_ENROLLMENT_ID, COL_AMOUNT, COL_PAYMENT_STATUS, COL_PAYMENT_DATE,
            COL_VERIFIED_BY_ADMIN_ID, COL_RECEIPT_FILE_PATH, COL_RECEIPT_SUBMITTED_AT,
            COL_VERIFIED_BY_INSTRUCTOR_ID, COL_VERIFICATION_NOTE, COL_PAYMENT_REFERENCE,
            COL_STUDENT_NOTE, COL_CURRENCY, COL_CREATED_AT);

    private static final String JOINED_FROM =
            " FROM payment p "
                    + "JOIN enrollment e ON e.enrollment_id = p.enrollment_id "
                    + "JOIN student s ON s.student_id = e.student_id "
                    + "JOIN `user` su ON su.user_id = s.user_id "
                    + "JOIN tasmi_session ts ON ts.session_id = e.session_id "
                    + "JOIN instructor i ON i.instructor_id = ts.instructor_id "
                    + "JOIN `user` iu ON iu.user_id = i.user_id ";

    @Override
    public long insert(Connection connection, Payment payment) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (" + COL_ENROLLMENT_ID + "," + COL_AMOUNT + "," + COL_PAYMENT_STATUS + ") VALUES (?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, payment.getEnrollmentId());
            ps.setBigDecimal(2, payment.getAmount());
            ps.setString(3, payment.getPaymentStatus() == null ? null : payment.getPaymentStatus().name());
            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Insert payment affected " + updated + " rows");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for payment insert");
                }
                return keys.getLong(1);
            }
        }
    }

    @Override
    public Optional<Payment> findById(Connection connection, long paymentId) throws SQLException {
        String sql = "SELECT " + PAYMENT_COLUMNS + " FROM " + TABLE + " WHERE " + COL_PAYMENT_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<Payment> findByEnrollmentId(Connection connection, long enrollmentId) throws SQLException {
        String sql = "SELECT " + PAYMENT_COLUMNS + " FROM " + TABLE + " WHERE " + COL_ENROLLMENT_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, enrollmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public boolean updateStatus(Connection connection, long paymentId, PaymentStatus newStatus, Instant paymentDate, Long verifiedByAdminId) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET " + COL_PAYMENT_STATUS + " = ?, " + COL_PAYMENT_DATE + " = ?, " + COL_VERIFIED_BY_ADMIN_ID + " = ?"
                + " WHERE " + COL_PAYMENT_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newStatus == null ? null : newStatus.name());
            setTimestamp(ps, 2, paymentDate);
            if (verifiedByAdminId == null) {
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(3, verifiedByAdminId);
            }
            ps.setLong(4, paymentId);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean submitReceipt(Connection connection, long paymentId, String receiptFilePath, String reference, String note, Instant submittedAt) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET "
                + COL_RECEIPT_FILE_PATH + " = ?, "
                + COL_RECEIPT_SUBMITTED_AT + " = ?, "
                + COL_PAYMENT_REFERENCE + " = ?, "
                + COL_STUDENT_NOTE + " = ?, "
                + COL_PAYMENT_STATUS + " = ?, "
                + COL_PAYMENT_DATE + " = NULL, "
                + COL_VERIFIED_BY_ADMIN_ID + " = NULL, "
                + COL_VERIFIED_BY_INSTRUCTOR_ID + " = NULL, "
                + COL_VERIFICATION_NOTE + " = NULL "
                + "WHERE " + COL_PAYMENT_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, receiptFilePath);
            setTimestamp(ps, 2, submittedAt);
            ps.setString(3, reference);
            ps.setString(4, note);
            ps.setString(5, PaymentStatus.AWAITING_VERIFICATION.name());
            ps.setLong(6, paymentId);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean updateInstructorVerification(Connection connection, long paymentId, PaymentStatus newStatus, Instant paymentDate, Long instructorId, String note) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET "
                + COL_PAYMENT_STATUS + " = ?, "
                + COL_PAYMENT_DATE + " = ?, "
                + COL_VERIFIED_BY_INSTRUCTOR_ID + " = ?, "
                + COL_VERIFICATION_NOTE + " = ? "
                + "WHERE " + COL_PAYMENT_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newStatus == null ? null : newStatus.name());
            setTimestamp(ps, 2, paymentDate);
            if (instructorId == null) {
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(3, instructorId);
            }
            ps.setString(4, note);
            ps.setLong(5, paymentId);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public List<PaymentTransactionRow> search(Connection connection, PaymentQueryFilter filter) throws SQLException {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ").append(rowSelectColumns()).append(JOINED_FROM)
                .append("WHERE p.amount > 0 ");
        appendFilters(sql, params, filter, true);
        sql.append("ORDER BY ").append(resolveSort(filter)).append(' ').append(filter.isSortAsc() ? "ASC" : "DESC")
                .append(", p.payment_id DESC ");
        sql.append("LIMIT ? OFFSET ?");
        params.add(filter.getLimit());
        params.add(filter.getOffset());

        List<PaymentTransactionRow> rows = new ArrayList<>();
        try (PreparedStatement ps = prepare(connection, sql.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(mapRow(rs));
            }
        }
        return rows;
    }

    @Override
    public int count(Connection connection, PaymentQueryFilter filter) throws SQLException {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*)").append(JOINED_FROM).append("WHERE p.amount > 0 ");
        appendFilters(sql, params, filter, true);
        try (PreparedStatement ps = prepare(connection, sql.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    @Override
    public PaymentStats loadStats(Connection connection, PaymentQueryFilter filter) throws SQLException {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) AS total, "
                + "SUM(p.payment_status = 'PENDING') AS pending, "
                + "SUM(p.payment_status = 'AWAITING_VERIFICATION') AS awaiting, "
                + "SUM(p.payment_status = 'APPROVED') AS approved, "
                + "SUM(p.payment_status = 'REJECTED') AS rejected, "
                + "COALESCE(SUM(CASE WHEN p.payment_status = 'APPROVED' THEN p.amount ELSE 0 END), 0) AS revenue, "
                + "MAX(p.currency) AS currency")
                .append(JOINED_FROM).append("WHERE p.amount > 0 ");
        appendFilters(sql, params, filter, false);

        PaymentStats stats = new PaymentStats();
        try (PreparedStatement ps = prepare(connection, sql.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                stats.setTotalTransactions(rs.getLong("total"));
                stats.setPendingCount(rs.getLong("pending"));
                stats.setAwaitingCount(rs.getLong("awaiting"));
                stats.setApprovedCount(rs.getLong("approved"));
                stats.setRejectedCount(rs.getLong("rejected"));
                BigDecimal revenue = rs.getBigDecimal("revenue");
                stats.setTotalRevenue(revenue == null ? BigDecimal.ZERO : revenue);
                String currency = rs.getString("currency");
                if (currency != null && !currency.isBlank()) {
                    stats.setCurrency(currency);
                }
            }
        }
        return stats;
    }

    @Override
    public List<RevenueBucket> revenueBySession(Connection connection, PaymentQueryFilter filter, int limit) throws SQLException {
        return revenueGrouped(connection, filter, limit, "ts.session_id", "ts.title");
    }

    @Override
    public List<RevenueBucket> revenueByInstructor(Connection connection, PaymentQueryFilter filter, int limit) throws SQLException {
        return revenueGrouped(connection, filter, limit, "i.instructor_id", "iu.full_name");
    }

    private List<RevenueBucket> revenueGrouped(Connection connection, PaymentQueryFilter filter, int limit,
                                               String idColumn, String labelColumn) throws SQLException {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ").append(idColumn).append(" AS bucket_id, ")
                .append(labelColumn).append(" AS bucket_label, COUNT(*) AS txn_count, "
                        + "COALESCE(SUM(p.amount), 0) AS revenue")
                .append(JOINED_FROM)
                .append("WHERE p.amount > 0 AND p.payment_status = 'APPROVED' ");
        appendFilters(sql, params, filter, false);
        sql.append("GROUP BY ").append(idColumn).append(", ").append(labelColumn)
                .append(" ORDER BY revenue DESC LIMIT ?");
        params.add(Math.max(1, limit));

        List<RevenueBucket> buckets = new ArrayList<>();
        try (PreparedStatement ps = prepare(connection, sql.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                buckets.add(new RevenueBucket(
                        rs.getLong("bucket_id"),
                        rs.getString("bucket_label"),
                        rs.getLong("txn_count"),
                        rs.getBigDecimal("revenue")));
            }
        }
        return buckets;
    }

    @Override
    public Optional<PaymentTransactionRow> findTransactionById(Connection connection, long paymentId) throws SQLException {
        String sql = "SELECT " + rowSelectColumns() + JOINED_FROM + "WHERE p.payment_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public List<PaymentTransactionRow> listInstructorQueue(Connection connection, long instructorId, Long sessionId, PaymentStatus status) throws SQLException {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ").append(rowSelectColumns()).append(JOINED_FROM)
                .append("WHERE p.amount > 0 AND ts.instructor_id = ? ");
        params.add(instructorId);
        if (sessionId != null && sessionId > 0) {
            sql.append("AND ts.session_id = ? ");
            params.add(sessionId);
        }
        if (status != null) {
            sql.append("AND p.payment_status = ? ");
            params.add(status.name());
        }
        sql.append("ORDER BY (p.payment_status = 'AWAITING_VERIFICATION') DESC, p.created_at DESC, p.payment_id DESC");
        List<PaymentTransactionRow> rows = new ArrayList<>();
        try (PreparedStatement ps = prepare(connection, sql.toString(), params);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(mapRow(rs));
            }
        }
        return rows;
    }

    @Override
    public List<RevenueBucket> listInstructorSessions(Connection connection, long instructorId) throws SQLException {
        String sql = "SELECT ts.session_id AS bucket_id, ts.title AS bucket_label, "
                + "COUNT(p.payment_id) AS txn_count, "
                + "COALESCE(SUM(CASE WHEN p.payment_status = 'APPROVED' THEN p.amount ELSE 0 END), 0) AS revenue "
                + "FROM tasmi_session ts "
                + "LEFT JOIN enrollment e ON e.session_id = ts.session_id "
                + "LEFT JOIN payment p ON p.enrollment_id = e.enrollment_id AND p.amount > 0 "
                + "WHERE ts.instructor_id = ? AND ts.fee > 0 "
                + "GROUP BY ts.session_id, ts.title "
                + "ORDER BY ts.session_date DESC, ts.session_id DESC";
        List<RevenueBucket> buckets = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, instructorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    buckets.add(new RevenueBucket(
                            rs.getLong("bucket_id"),
                            rs.getString("bucket_label"),
                            rs.getLong("txn_count"),
                            rs.getBigDecimal("revenue")));
                }
            }
        }
        return buckets;
    }

    private String rowSelectColumns() {
        return "p.payment_id, p.enrollment_id, p.amount, p.payment_status, p.payment_date, p.created_at, "
                + "p.receipt_file_path, p.receipt_submitted_at, p.payment_reference, p.student_note, "
                + "p.verification_note, p.currency, "
                + "e.student_id, e.session_id, e.enrollment_status, "
                + "su.user_id AS student_user_id, su.full_name AS student_name, su.email AS student_email, "
                + "ts.title AS session_title, ts.fee AS session_fee, ts.instructor_id, "
                + "iu.user_id AS instructor_user_id, iu.full_name AS instructor_name, iu.email AS instructor_email";
    }

    private void appendFilters(StringBuilder sql, List<Object> params, PaymentQueryFilter f, boolean includeStatus) {
        if (f == null) {
            return;
        }
        if (f.getStudentId() != null) {
            sql.append("AND e.student_id = ? ");
            params.add(f.getStudentId());
        }
        if (f.getInstructorId() != null) {
            sql.append("AND ts.instructor_id = ? ");
            params.add(f.getInstructorId());
        }
        if (f.getSessionId() != null) {
            sql.append("AND ts.session_id = ? ");
            params.add(f.getSessionId());
        }
        if (includeStatus && f.getStatus() != null) {
            sql.append("AND p.payment_status = ? ");
            params.add(f.getStatus().name());
        }
        if (f.getFromDate() != null) {
            sql.append("AND DATE(p.created_at) >= ? ");
            params.add(f.getFromDate());
        }
        if (f.getToDate() != null) {
            sql.append("AND DATE(p.created_at) <= ? ");
            params.add(f.getToDate());
        }
        if (f.getMinAmount() != null) {
            sql.append("AND p.amount >= ? ");
            params.add(f.getMinAmount());
        }
        if (f.getMaxAmount() != null) {
            sql.append("AND p.amount <= ? ");
            params.add(f.getMaxAmount());
        }
        if (f.getSearch() != null && !f.getSearch().isBlank()) {
            String like = "%" + f.getSearch().trim() + "%";
            sql.append("AND (su.full_name LIKE ? OR su.email LIKE ? OR ts.title LIKE ? OR iu.full_name LIKE ? OR p.payment_reference LIKE ?) ");
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
    }

    private String resolveSort(PaymentQueryFilter f) {
        String key = f == null || f.getSortBy() == null ? "created_at" : f.getSortBy().trim().toLowerCase();
        switch (key) {
            case "amount":
                return "p.amount";
            case "status":
                return "p.payment_status";
            case "student":
                return "su.full_name";
            case "session":
                return "ts.title";
            case "instructor":
                return "iu.full_name";
            case "date":
            case "payment_date":
                return "p.payment_date";
            case "created_at":
            default:
                return "p.created_at";
        }
    }

    private PreparedStatement prepare(Connection connection, String sql, List<Object> params) throws SQLException {
        PreparedStatement ps = connection.prepareStatement(sql);
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            if (value instanceof LocalDate) {
                ps.setDate(i + 1, Date.valueOf((LocalDate) value));
            } else if (value instanceof BigDecimal) {
                ps.setBigDecimal(i + 1, (BigDecimal) value);
            } else if (value instanceof Integer) {
                ps.setInt(i + 1, (Integer) value);
            } else if (value instanceof Long) {
                ps.setLong(i + 1, (Long) value);
            } else {
                ps.setObject(i + 1, value);
            }
        }
        return ps;
    }

    private void setTimestamp(PreparedStatement ps, int index, Instant instant) throws SQLException {
        if (instant == null) {
            ps.setTimestamp(index, null);
        } else {
            ps.setTimestamp(index, Timestamp.from(instant));
        }
    }

    private Payment map(ResultSet rs) throws SQLException {
        Payment p = new Payment();
        p.setPaymentId(rs.getLong(COL_PAYMENT_ID));
        p.setEnrollmentId(rs.getLong(COL_ENROLLMENT_ID));
        p.setAmount(rs.getBigDecimal(COL_AMOUNT));
        p.setPaymentStatus(PaymentStatus.fromString(rs.getString(COL_PAYMENT_STATUS)));
        Timestamp ts = rs.getTimestamp(COL_PAYMENT_DATE);
        if (ts != null) {
            p.setPaymentDate(ts.toInstant());
        }
        long adminId = rs.getLong(COL_VERIFIED_BY_ADMIN_ID);
        if (!rs.wasNull()) {
            p.setVerifiedByAdminId(adminId);
        }
        p.setReceiptFilePath(rs.getString(COL_RECEIPT_FILE_PATH));
        Timestamp receiptTs = rs.getTimestamp(COL_RECEIPT_SUBMITTED_AT);
        if (receiptTs != null) {
            p.setReceiptSubmittedAt(receiptTs.toInstant());
        }
        long instructorId = rs.getLong(COL_VERIFIED_BY_INSTRUCTOR_ID);
        if (!rs.wasNull()) {
            p.setVerifiedByInstructorId(instructorId);
        }
        p.setVerificationNote(rs.getString(COL_VERIFICATION_NOTE));
        p.setPaymentReference(rs.getString(COL_PAYMENT_REFERENCE));
        p.setStudentNote(rs.getString(COL_STUDENT_NOTE));
        p.setCurrency(rs.getString(COL_CURRENCY));
        Timestamp createdTs = rs.getTimestamp(COL_CREATED_AT);
        if (createdTs != null) {
            p.setCreatedAt(createdTs.toInstant());
        }
        return p;
    }

    private PaymentTransactionRow mapRow(ResultSet rs) throws SQLException {
        PaymentTransactionRow r = new PaymentTransactionRow();
        r.setPaymentId(rs.getLong("payment_id"));
        r.setEnrollmentId(rs.getLong("enrollment_id"));
        r.setAmount(rs.getBigDecimal("amount"));
        r.setStatus(PaymentStatus.fromString(rs.getString("payment_status")));
        Timestamp paymentDate = rs.getTimestamp("payment_date");
        if (paymentDate != null) {
            r.setPaymentDate(paymentDate.toInstant());
        }
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            r.setCreatedAt(createdAt.toInstant());
        }
        r.setReceiptFilePath(rs.getString("receipt_file_path"));
        Timestamp submittedAt = rs.getTimestamp("receipt_submitted_at");
        if (submittedAt != null) {
            r.setReceiptSubmittedAt(submittedAt.toInstant());
        }
        r.setPaymentReference(rs.getString("payment_reference"));
        r.setStudentNote(rs.getString("student_note"));
        r.setVerificationNote(rs.getString("verification_note"));
        r.setCurrency(rs.getString("currency"));
        r.setStudentId(rs.getLong("student_id"));
        r.setSessionId(rs.getLong("session_id"));
        r.setEnrollmentStatus(EnrollmentStatus.fromString(rs.getString("enrollment_status")));
        r.setStudentUserId(rs.getLong("student_user_id"));
        r.setStudentName(rs.getString("student_name"));
        r.setStudentEmail(rs.getString("student_email"));
        r.setSessionTitle(rs.getString("session_title"));
        r.setSessionFee(rs.getBigDecimal("session_fee"));
        r.setInstructorId(rs.getLong("instructor_id"));
        r.setInstructorUserId(rs.getLong("instructor_user_id"));
        r.setInstructorName(rs.getString("instructor_name"));
        r.setInstructorEmail(rs.getString("instructor_email"));
        return r;
    }
}
