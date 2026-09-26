package model.service;

import model.dao.EnrollmentDao;
import model.dao.InstructorDao;
import model.dao.InstructorPaymentSettingsDao;
import model.dao.NotificationDao;
import model.dao.PaymentDao;
import model.dao.PaymentVerificationHistoryDao;
import model.dao.StudentDao;
import model.dao.TasmiSessionDao;
import model.dao.UserDao;
import model.dao.impl.EnrollmentDaoJdbc;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.InstructorPaymentSettingsDaoJdbc;
import model.dao.impl.NotificationDaoJdbc;
import model.dao.impl.PaymentDaoJdbc;
import model.dao.impl.PaymentVerificationHistoryDaoJdbc;
import model.dao.impl.StudentDaoJdbc;
import model.dao.impl.TasmiSessionDaoJdbc;
import model.dao.impl.UserDaoJdbc;
import model.entity.Enrollment;
import model.entity.EnrollmentStatus;
import model.entity.Instructor;
import model.entity.InstructorPaymentSettings;
import model.entity.Notification;
import model.entity.Payment;
import model.entity.PaymentStatus;
import model.entity.PaymentVerificationHistory;
import model.entity.Student;
import model.entity.TasmiSession;
import model.entity.User;
import util.Db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PaymentService {
    private static final Logger LOGGER = Logger.getLogger(PaymentService.class.getName());

    private final StudentDao studentDao;
    private final EnrollmentDao enrollmentDao;
    private final InstructorDao instructorDao;
    private final TasmiSessionDao tasmiSessionDao;
    private final PaymentDao paymentDao;
    private final NotificationDao notificationDao;
    private final UserDao userDao;
    private final InstructorPaymentSettingsDao paymentSettingsDao;
    private final PaymentVerificationHistoryDao historyDao;

    public PaymentService() {
        this.studentDao = new StudentDaoJdbc();
        this.enrollmentDao = new EnrollmentDaoJdbc();
        this.instructorDao = new InstructorDaoJdbc();
        this.tasmiSessionDao = new TasmiSessionDaoJdbc();
        this.paymentDao = new PaymentDaoJdbc();
        this.notificationDao = new NotificationDaoJdbc();
        this.userDao = new UserDaoJdbc();
        this.paymentSettingsDao = new InstructorPaymentSettingsDaoJdbc();
        this.historyDao = new PaymentVerificationHistoryDaoJdbc();
    }

    public Optional<Payment> findPaymentByEnrollment(long enrollmentId) {
        try (Connection connection = Db.getConnection()) {
            return paymentDao.findByEnrollmentId(connection, enrollmentId);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to find payment", ex);
            return Optional.empty();
        }
    }

    public PaymentResult beginPaymentFlow(long studentUserId, long enrollmentId) {
        if (enrollmentId <= 0) {
            return PaymentResult.failure("Invalid enrollment.");
        }

        try (Connection connection = Db.getConnection()) {
            connection.setAutoCommit(false);
            PaymentContext context = resolveContext(connection, studentUserId, enrollmentId);

            if (context.fee.compareTo(BigDecimal.ZERO) <= 0) {
                PaymentResult freeResult = confirmFreeEnrollment(connection, context);
                if (!freeResult.isSuccess()) {
                    connection.rollback();
                    return freeResult;
                }
                connection.commit();
                return freeResult;
            }

            if (context.existingPayment != null) {
                PaymentStatus status = context.existingPayment.getPaymentStatus();
                if (status == PaymentStatus.APPROVED) {
                    connection.rollback();
                    return PaymentResult.success("already_paid", "This session is already paid and confirmed.");
                }
                if (context.enrollment.getEnrollmentStatus() != EnrollmentStatus.PENDING) {
                    enrollmentDao.updateStatus(connection, context.enrollment.getEnrollmentId(), EnrollmentStatus.PENDING);
                }
                connection.commit();
                String code = status == PaymentStatus.AWAITING_VERIFICATION ? "checkout_pending" : "checkout_ready";
                return PaymentResult.success(code, "Open the payment page to continue.");
            }

            Payment payment = new Payment();
            payment.setEnrollmentId(enrollmentId);
            payment.setAmount(context.fee);
            payment.setPaymentStatus(PaymentStatus.PENDING);
            paymentDao.insert(connection, payment);

            if (context.enrollment.getEnrollmentStatus() != EnrollmentStatus.PENDING) {
                enrollmentDao.updateStatus(connection, context.enrollment.getEnrollmentId(), EnrollmentStatus.PENDING);
            }
            notifyUser(connection, context.student.getUserId(),
                    "Payment record created for " + context.sessionLabel + ". Transfer the fee and upload your receipt to complete verification.");
            connection.commit();
            return PaymentResult.success("checkout_ready", "Payment page prepared successfully.");
        } catch (PaymentFlowException ex) {
            return PaymentResult.failure(ex.getMessage());
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to begin payment flow", ex);
            return PaymentResult.failure("Payment could not be prepared due to a server error.");
        }
    }

    public PaymentResult submitReceipt(long studentUserId, long enrollmentId, String receiptFilePath, String reference, String note) {
        if (enrollmentId <= 0) {
            return PaymentResult.failure("Invalid enrollment.");
        }
        String normalizedReceipt = normalizeText(receiptFilePath);
        if (normalizedReceipt == null) {
            return PaymentResult.failure("Please upload a valid payment receipt.");
        }

        try (Connection connection = Db.getConnection()) {
            connection.setAutoCommit(false);
            PaymentContext context = resolveContext(connection, studentUserId, enrollmentId);
            if (context.fee.compareTo(BigDecimal.ZERO) <= 0) {
                connection.rollback();
                return PaymentResult.failure("This session does not require payment.");
            }

            Payment payment = context.existingPayment;
            long paymentId;
            PaymentStatus previousStatus;
            if (payment == null) {
                Payment created = new Payment();
                created.setEnrollmentId(enrollmentId);
                created.setAmount(context.fee);
                created.setPaymentStatus(PaymentStatus.PENDING);
                paymentId = paymentDao.insert(connection, created);
                previousStatus = PaymentStatus.PENDING;
            } else {
                if (payment.getPaymentStatus() == PaymentStatus.APPROVED) {
                    connection.rollback();
                    return PaymentResult.failure("This payment has already been approved.");
                }
                if (payment.getPaymentStatus() == PaymentStatus.AWAITING_VERIFICATION) {
                    connection.rollback();
                    return PaymentResult.failure("Your receipt is already submitted and pending verification. "
                            + "Please wait for the instructor to review it before submitting again.");
                }
                paymentId = payment.getPaymentId();
                previousStatus = payment.getPaymentStatus();
            }

            boolean updated = paymentDao.submitReceipt(connection, paymentId, normalizedReceipt,
                    shorten(reference, 120), shorten(note, 500), Instant.now());
            if (!updated) {
                connection.rollback();
                return PaymentResult.failure("Payment receipt could not be saved right now.");
            }
            if (context.enrollment.getEnrollmentStatus() != EnrollmentStatus.PENDING) {
                enrollmentDao.updateStatus(connection, enrollmentId, EnrollmentStatus.PENDING);
            }

            boolean resubmission = previousStatus == PaymentStatus.REJECTED;
            recordHistory(connection, paymentId, context.student.getUserId(), "STUDENT",
                    resubmission ? "RESUBMITTED" : "SUBMITTED", previousStatus, PaymentStatus.AWAITING_VERIFICATION,
                    normalizeText(note));

            notifyUser(connection, context.student.getUserId(),
                    "Your payment receipt for " + context.sessionLabel + " was submitted and is awaiting instructor verification.");
            Optional<Instructor> instructorOpt = instructorDao.findById(connection, context.session.getInstructorId());
            if (instructorOpt.isPresent()) {
                notifyUser(connection, instructorOpt.get().getUserId(),
                        "New payment receipt submitted for " + context.sessionLabel + ". Please review it from Payment Verification.");
            }
            connection.commit();
            return PaymentResult.success(resubmission ? "receipt_resubmitted" : "receipt_submitted",
                    "Payment receipt submitted for verification.");
        } catch (PaymentFlowException ex) {
            return PaymentResult.failure(ex.getMessage());
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to submit payment receipt", ex);
            return PaymentResult.failure("Payment receipt could not be submitted due to a server error.");
        }
    }

    public PaymentResult verifyInstructorPayment(long instructorUserId, long paymentId, boolean approve, String reason) {
        if (instructorUserId <= 0 || paymentId <= 0) {
            return PaymentResult.failure("Invalid payment verification request.");
        }
        String normalizedReason = normalizeText(reason);
        if (!approve && normalizedReason == null) {
            return PaymentResult.failure("A rejection reason is required.");
        }

        try (Connection connection = Db.getConnection()) {
            connection.setAutoCommit(false);
            Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, instructorUserId);
            if (instructorOpt.isEmpty()) {
                connection.rollback();
                return PaymentResult.failure("Instructor profile not found.");
            }
            Instructor instructor = instructorOpt.get();

            Optional<Payment> paymentOpt = paymentDao.findById(connection, paymentId);
            if (paymentOpt.isEmpty()) {
                connection.rollback();
                return PaymentResult.failure("Payment record not found.");
            }
            Payment payment = paymentOpt.get();
            Optional<Enrollment> enrollmentOpt = enrollmentDao.findById(connection, payment.getEnrollmentId());
            if (enrollmentOpt.isEmpty()) {
                connection.rollback();
                return PaymentResult.failure("Enrollment record not found.");
            }
            Enrollment enrollment = enrollmentOpt.get();
            Optional<TasmiSession> sessionOpt = tasmiSessionDao.findById(connection, enrollment.getSessionId());
            if (sessionOpt.isEmpty()) {
                connection.rollback();
                return PaymentResult.failure("Session record not found.");
            }
            TasmiSession session = sessionOpt.get();
            if (session.getInstructorId() != instructor.getInstructorId()) {
                connection.rollback();
                return PaymentResult.failure("You can only verify receipts for your own sessions.");
            }
            if (payment.getPaymentStatus() == PaymentStatus.APPROVED && approve) {
                connection.rollback();
                return PaymentResult.failure("This payment is already approved.");
            }
            if (normalizeText(payment.getReceiptFilePath()) == null) {
                connection.rollback();
                return PaymentResult.failure("This payment has no receipt to verify.");
            }

            PaymentStatus previousStatus = payment.getPaymentStatus();
            PaymentStatus newStatus = approve ? PaymentStatus.APPROVED : PaymentStatus.REJECTED;
            boolean updated = paymentDao.updateInstructorVerification(connection, paymentId, newStatus,
                    Instant.now(), instructor.getInstructorId(), approve ? null : shorten(normalizedReason, 500));
            if (!updated) {
                connection.rollback();
                return PaymentResult.failure("Payment status could not be updated.");
            }

            EnrollmentStatus newEnrollmentStatus = approve ? EnrollmentStatus.APPROVED : EnrollmentStatus.PENDING;
            if (enrollment.getEnrollmentStatus() != newEnrollmentStatus) {
                enrollmentDao.updateStatus(connection, enrollment.getEnrollmentId(), newEnrollmentStatus);
            }

            recordHistory(connection, paymentId, instructor.getUserId(), "INSTRUCTOR",
                    approve ? "APPROVED" : "REJECTED", previousStatus, newStatus,
                    approve ? null : normalizedReason);

            Optional<Student> studentOpt = studentDao.findById(connection, enrollment.getStudentId());
            if (studentOpt.isPresent()) {
                String message = approve
                        ? "Your payment for " + sessionTitle(session) + " was approved. Your enrollment is confirmed."
                        : "Your payment for " + sessionTitle(session) + " was rejected: " + normalizedReason
                          + ". You can upload a new receipt.";
                notifyUser(connection, studentOpt.get().getUserId(), message);
            }
            connection.commit();
            return PaymentResult.success(approve ? "payment_approved" : "payment_rejected",
                    approve ? "Payment approved successfully." : "Payment rejected successfully.");
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to verify instructor payment", ex);
            return PaymentResult.failure("Payment verification failed due to a server error.");
        }
    }

    public PaymentResult confirmFreeEnrollment(long studentUserId, long enrollmentId) {
        if (enrollmentId <= 0) {
            return PaymentResult.failure("Invalid enrollment.");
        }
        try (Connection connection = Db.getConnection()) {
            connection.setAutoCommit(false);
            PaymentContext context = resolveContext(connection, studentUserId, enrollmentId);
            PaymentResult result = confirmFreeEnrollment(connection, context);
            if (!result.isSuccess()) {
                connection.rollback();
                return result;
            }
            connection.commit();
            return result;
        } catch (PaymentFlowException ex) {
            return PaymentResult.failure(ex.getMessage());
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to confirm free enrollment", ex);
            return PaymentResult.failure("Free session confirmation failed due to a server error.");
        }
    }

    public Optional<QrPaymentCheckout> loadQrCheckout(long studentUserId, long enrollmentId) {
        if (enrollmentId <= 0) {
            return Optional.empty();
        }
        try (Connection connection = Db.getConnection()) {
            PaymentContext context = resolveContext(connection, studentUserId, enrollmentId);
            if (context.fee.compareTo(BigDecimal.ZERO) <= 0 || context.existingPayment == null) {
                return Optional.empty();
            }
            Instructor instructor = instructorDao.findById(connection, context.session.getInstructorId()).orElse(null);
            InstructorPaymentSettings settings = null;
            String instructorName = "Instructor";
            if (instructor != null) {
                settings = paymentSettingsDao.findByInstructorId(connection, instructor.getInstructorId()).orElse(null);
                Optional<User> userOpt = userDao.findById(connection, instructor.getUserId());
                if (userOpt.isPresent() && normalizeText(userOpt.get().getFullName()) != null) {
                    instructorName = userOpt.get().getFullName().trim();
                }
            }
            List<PaymentVerificationHistory> timeline =
                    historyDao.listByPayment(connection, context.existingPayment.getPaymentId());
            return Optional.of(new QrPaymentCheckout(context.enrollment, context.session, context.existingPayment,
                    context.fee, context.sessionLabel, instructor, settings, instructorName, timeline));
        } catch (PaymentFlowException ex) {
            return Optional.empty();
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load QR checkout", ex);
            return Optional.empty();
        }
    }

    private PaymentResult confirmFreeEnrollment(Connection connection, PaymentContext context) throws SQLException {
        if (context.fee.compareTo(BigDecimal.ZERO) > 0) {
            return PaymentResult.failure("This session requires payment before it can be confirmed.");
        }
        if (context.existingPayment != null) {
            if (context.existingPayment.getPaymentStatus() != PaymentStatus.APPROVED) {
                paymentDao.updateStatus(connection, context.existingPayment.getPaymentId(), PaymentStatus.APPROVED, Instant.now(), null);
            }
        } else {
            Payment payment = new Payment();
            payment.setEnrollmentId(context.enrollment.getEnrollmentId());
            payment.setAmount(BigDecimal.ZERO);
            payment.setPaymentStatus(PaymentStatus.APPROVED);
            long paymentId = paymentDao.insert(connection, payment);
            paymentDao.updateStatus(connection, paymentId, PaymentStatus.APPROVED, Instant.now(), null);
        }
        if (context.enrollment.getEnrollmentStatus() != EnrollmentStatus.APPROVED) {
            enrollmentDao.updateStatus(connection, context.enrollment.getEnrollmentId(), EnrollmentStatus.APPROVED);
        }
        notifyUser(connection, context.student.getUserId(),
                "Your free session " + context.sessionLabel + " is confirmed. You can track it from My Sessions.");
        return PaymentResult.success("free_confirmed", "Free session confirmed successfully.");
    }

    private void recordHistory(Connection connection, long paymentId, long actorUserId, String actorRole,
                               String action, PaymentStatus from, PaymentStatus to, String reason) throws SQLException {
        PaymentVerificationHistory history = new PaymentVerificationHistory();
        history.setPaymentId(paymentId);
        history.setActorUserId(actorUserId > 0 ? actorUserId : null);
        history.setActorRole(actorRole);
        history.setAction(action);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setReason(reason);
        historyDao.insert(connection, history);
    }

    private PaymentContext resolveContext(Connection connection, long studentUserId, long enrollmentId) throws SQLException, PaymentFlowException {
        Optional<Student> studentOpt = studentDao.findByUserId(connection, studentUserId);
        if (studentOpt.isEmpty()) {
            throw new PaymentFlowException("Student profile not found.");
        }
        long studentId = studentOpt.get().getStudentId();
        List<Enrollment> enrollments = enrollmentDao.listByStudentId(connection, studentId);
        Optional<Enrollment> enrollmentOpt = enrollments.stream().filter(e -> e.getEnrollmentId() == enrollmentId).findFirst();
        if (enrollmentOpt.isEmpty()) {
            throw new PaymentFlowException("You cannot pay for this enrollment.");
        }
        Enrollment enrollment = enrollmentOpt.get();
        if (enrollment.getEnrollmentStatus() == EnrollmentStatus.CANCELLED
                || enrollment.getEnrollmentStatus() == EnrollmentStatus.REJECTED) {
            throw new PaymentFlowException("This enrollment is no longer eligible for payment.");
        }
        Optional<TasmiSession> sessionOpt = tasmiSessionDao.findById(connection, enrollment.getSessionId());
        if (sessionOpt.isEmpty()) {
            throw new PaymentFlowException("Session not found.");
        }
        TasmiSession session = sessionOpt.get();
        if (!isActiveInstructorSession(connection, session)) {
            throw new PaymentFlowException("This session is no longer available for payment.");
        }
        if (session.getStatus() == model.entity.TasmiSessionStatus.CANCELLED
                || session.getStatus() == model.entity.TasmiSessionStatus.COMPLETED) {
            throw new PaymentFlowException("This session is no longer accepting payments.");
        }

        PaymentContext context = new PaymentContext();
        context.student = studentOpt.get();
        context.enrollment = enrollment;
        context.session = session;
        context.fee = session.getFee() == null ? BigDecimal.ZERO : session.getFee();
        context.existingPayment = paymentDao.findByEnrollmentId(connection, enrollmentId).orElse(null);
        context.sessionLabel = sessionTitle(session);
        return context;
    }

    private boolean isActiveInstructorSession(Connection connection, TasmiSession session) throws SQLException {
        if (connection == null || session == null || session.getInstructorId() <= 0) {
            return false;
        }
        String sql = "SELECT i.verification_status, u.status, u.is_active "
                + "FROM instructor i JOIN `user` u ON u.user_id = i.user_id WHERE i.instructor_id = ?";
        try (java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, session.getInstructorId());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                String verificationStatus = normalizeText(rs.getString("verification_status"));
                String userStatus = normalizeText(rs.getString("status"));
                return rs.getBoolean("is_active")
                        && "APPROVED".equalsIgnoreCase(verificationStatus)
                        && !"DELETED".equalsIgnoreCase(userStatus);
            }
        }
    }

    private void notifyUser(Connection connection, long userId, String message) throws SQLException {
        if (userId <= 0 || message == null || message.isBlank()) {
            return;
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setMessage(message);
        notification.setCreatedAt(Instant.now());
        notificationDao.insert(connection, notification);
    }

    private String sessionTitle(TasmiSession session) {
        if (session == null || session.getTitle() == null || session.getTitle().trim().isEmpty()) {
            return "Session #" + (session == null ? "-" : session.getSessionId());
        }
        return session.getTitle().trim();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String shorten(String text, int max) {
        String normalized = normalizeText(text);
        if (normalized == null || normalized.length() <= max) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, max - 3)).trim() + "...";
    }

    private static final class PaymentContext {
        private Student student;
        private Enrollment enrollment;
        private TasmiSession session;
        private Payment existingPayment;
        private BigDecimal fee;
        private String sessionLabel;
    }

    public static final class QrPaymentCheckout {
        private final Enrollment enrollment;
        private final TasmiSession session;
        private final Payment payment;
        private final BigDecimal amount;
        private final String sessionLabel;
        private final Instructor instructor;
        private final InstructorPaymentSettings settings;
        private final String instructorName;
        private final List<PaymentVerificationHistory> timeline;

        public QrPaymentCheckout(Enrollment enrollment, TasmiSession session, Payment payment, BigDecimal amount,
                                 String sessionLabel, Instructor instructor, InstructorPaymentSettings settings,
                                 String instructorName, List<PaymentVerificationHistory> timeline) {
            this.enrollment = enrollment;
            this.session = session;
            this.payment = payment;
            this.amount = amount;
            this.sessionLabel = sessionLabel;
            this.instructor = instructor;
            this.settings = settings;
            this.instructorName = instructorName;
            this.timeline = timeline;
        }

        public Enrollment getEnrollment() { return enrollment; }
        public TasmiSession getSession() { return session; }
        public Payment getPayment() { return payment; }
        public BigDecimal getAmount() { return amount; }
        public String getSessionLabel() { return sessionLabel; }
        public Instructor getInstructor() { return instructor; }
        public InstructorPaymentSettings getSettings() { return settings; }
        public String getInstructorName() { return instructorName; }
        public List<PaymentVerificationHistory> getTimeline() { return timeline; }
    }

    private static final class PaymentFlowException extends Exception {
        private PaymentFlowException(String message) {
            super(message);
        }
    }
}
