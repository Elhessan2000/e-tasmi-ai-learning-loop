package model.dao;

import model.entity.Payment;
import model.entity.PaymentQueryFilter;
import model.entity.PaymentStats;
import model.entity.PaymentStatus;
import model.entity.PaymentTransactionRow;
import model.entity.RevenueBucket;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentDao {
    long insert(Connection connection, Payment payment) throws SQLException;

    Optional<Payment> findById(Connection connection, long paymentId) throws SQLException;

    Optional<Payment> findByEnrollmentId(Connection connection, long enrollmentId) throws SQLException;

    boolean updateStatus(Connection connection, long paymentId, PaymentStatus newStatus, Instant paymentDate, Long verifiedByAdminId) throws SQLException;

    /** Stores an uploaded receipt (+ reference/note) and moves the payment to AWAITING_VERIFICATION. */
    boolean submitReceipt(Connection connection, long paymentId, String receiptFilePath, String reference, String note, Instant submittedAt) throws SQLException;

    boolean updateInstructorVerification(Connection connection, long paymentId, PaymentStatus newStatus, Instant paymentDate, Long instructorId, String note) throws SQLException;

    // --- Monitoring / verification queries ---

    List<PaymentTransactionRow> search(Connection connection, PaymentQueryFilter filter) throws SQLException;

    int count(Connection connection, PaymentQueryFilter filter) throws SQLException;

    PaymentStats loadStats(Connection connection, PaymentQueryFilter filter) throws SQLException;

    List<RevenueBucket> revenueBySession(Connection connection, PaymentQueryFilter filter, int limit) throws SQLException;

    List<RevenueBucket> revenueByInstructor(Connection connection, PaymentQueryFilter filter, int limit) throws SQLException;

    Optional<PaymentTransactionRow> findTransactionById(Connection connection, long paymentId) throws SQLException;

    /** Verification queue for one instructor, optionally narrowed to a session and/or status. */
    List<PaymentTransactionRow> listInstructorQueue(Connection connection, long instructorId, Long sessionId, PaymentStatus status) throws SQLException;

    /** Per-session summary (count + approved revenue) for an instructor's session dropdown. */
    List<RevenueBucket> listInstructorSessions(Connection connection, long instructorId) throws SQLException;
}
