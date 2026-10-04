package model.service;

import model.dao.EnrollmentDao;
import model.dao.EvaluationDao;
import model.dao.InstructorDao;
import model.dao.NotificationDao;
import model.dao.RecitationDao;
import model.dao.StudentDao;
import model.dao.PaymentDao;
import model.dao.TasmiSessionDao;
import model.dao.impl.PaymentDaoJdbc;
import model.dao.impl.EnrollmentDaoJdbc;
import model.dao.impl.EvaluationDaoJdbc;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.NotificationDaoJdbc;
import model.dao.impl.RecitationDaoJdbc;
import model.dao.impl.StudentDaoJdbc;
import model.dao.impl.TasmiSessionDaoJdbc;
import model.entity.Enrollment;
import model.entity.EnrollmentStatus;
import model.entity.Evaluation;
import model.entity.Notification;
import model.entity.Payment;
import model.entity.PaymentStatus;
import model.entity.Recitation;
import model.entity.Student;
import model.entity.TasmiSession;
import model.entity.TasmiSessionStatus;
import util.Db;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RecitationService {
    private static final Logger LOGGER = Logger.getLogger(RecitationService.class.getName());

    private final StudentDao studentDao;
    private final EnrollmentDao enrollmentDao;
    private final RecitationDao recitationDao;
    private final PaymentDao paymentDao;
    private final TasmiSessionDao tasmiSessionDao;
    private final InstructorDao instructorDao;
    private final NotificationDao notificationDao;
    private final EvaluationDao evaluationDao;
    private final AuditLogService auditLogService;
    private final RecitationAutoAnalysisService autoAnalysisService;

    public RecitationService() {
        this.studentDao = new StudentDaoJdbc();
        this.enrollmentDao = new EnrollmentDaoJdbc();
        this.recitationDao = new RecitationDaoJdbc();
        this.paymentDao = new PaymentDaoJdbc();
        this.tasmiSessionDao = new TasmiSessionDaoJdbc();
        this.instructorDao = new InstructorDaoJdbc();
        this.notificationDao = new NotificationDaoJdbc();
        this.evaluationDao = new EvaluationDaoJdbc();
        this.auditLogService = new AuditLogService();
        this.autoAnalysisService = new RecitationAutoAnalysisService();
    }

    public RecitationSubmitResult submit(long studentUserId, long enrollmentId, String audioFilePath) {
        return submit(studentUserId, enrollmentId, audioFilePath, 0L);
    }

    /**
     * @param parentRecitationId a published recitation on the same enrollment, or {@code 0}
     *                           for an ordinary first attempt
     */
    public RecitationSubmitResult submit(long studentUserId, long enrollmentId, String audioFilePath,
                                         long parentRecitationId) {
        if (enrollmentId <= 0) {
            return RecitationSubmitResult.failure("Please select an enrollment.");
        }
        if (audioFilePath == null || audioFilePath.isBlank()) {
            return RecitationSubmitResult.failure("Audio file is required.");
        }

        try (Connection connection = Db.getConnection()) {
            connection.setAutoCommit(false);

            Optional<Student> studentOpt = studentDao.findByUserId(connection, studentUserId);
            if (studentOpt.isEmpty()) {
                connection.rollback();
                return RecitationSubmitResult.failure("Student profile not found.");
            }

            long studentId = studentOpt.get().getStudentId();

            Optional<Enrollment> enrollmentOpt = enrollmentDao.findById(connection, enrollmentId);
            if (enrollmentOpt.isEmpty() || enrollmentOpt.get().getStudentId() != studentId) {
                connection.rollback();
                return RecitationSubmitResult.failure("Invalid enrollment.");
            }
            Enrollment enrollment = enrollmentOpt.get();
            if (enrollment.getEnrollmentStatus() != EnrollmentStatus.APPROVED) {
                connection.rollback();
                return RecitationSubmitResult.failure("Your enrollment is not approved yet.");
            }
            Optional<TasmiSession> sessionOpt = tasmiSessionDao.findById(connection, enrollment.getSessionId());
            if (sessionOpt.isEmpty()) {
                connection.rollback();
                return RecitationSubmitResult.failure("Session not found for this recitation.");
            }

            TasmiSession session = sessionOpt.get();
            if (session.getStatus() == TasmiSessionStatus.CANCELLED) {
                connection.rollback();
                return RecitationSubmitResult.failure("This session is no longer accepting recitations.");
            }

            boolean paymentRequired = session.getFee() != null && session.getFee().compareTo(java.math.BigDecimal.ZERO) > 0;
            if (paymentRequired) {
                Optional<Payment> paymentOpt = paymentDao.findByEnrollmentId(connection, enrollmentId);
                if (paymentOpt.isEmpty() || paymentOpt.get().getPaymentStatus() != PaymentStatus.APPROVED) {
                    connection.rollback();
                    return RecitationSubmitResult.failure("Payment for this enrollment is not verified yet.");
                }
            }

            Recitation r = new Recitation();
            r.setEnrollmentId(enrollmentId);
            r.setAudioFilePath(audioFilePath);
            if (parentRecitationId > 0) {
                RecitationSubmitResult lineage = applyPracticeAgain(connection, studentId, enrollmentId, parentRecitationId, r);
                if (lineage != null) {
                    connection.rollback();
                    return lineage;
                }
            } else {
                r.setParentRecitationId(null);
                r.setAttemptNumber(1);
            }

            long recitationId = recitationDao.insert(connection, r);
            if (parentRecitationId > 0) {
                auditLogService.log(connection, studentUserId, "STUDENT",
                        "PRACTICE_AGAIN", "recitation", String.valueOf(recitationId),
                        "recitation_id=" + recitationId
                                + " parent_recitation_id=" + parentRecitationId
                                + " enrollment_id=" + enrollmentId
                                + " attempt_number=" + r.getAttemptNumber());
            }
            try {
                notifyStudent(connection,
                        studentOpt.get().getUserId(),
                        "Recitation submitted for " + sessionLabel(session) + ". Your instructor will review it soon.");
                Optional<model.entity.Instructor> instructorOpt = instructorDao.findById(connection, session.getInstructorId());
                if (instructorOpt.isPresent()) {
                    notifyStudent(connection,
                            instructorOpt.get().getUserId(),
                            "A new recitation has been submitted for " + sessionLabel(session) + ". Review it when you are available.");
                }
            } catch (SQLException notifyEx) {
                LOGGER.log(Level.WARNING, "Recitation saved, but notification dispatch failed", notifyEx);
            }
            connection.commit();
            autoAnalysisService.scheduleAfterSubmission(recitationId);
            return RecitationSubmitResult.success(recitationId);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to submit recitation", ex);
            return RecitationSubmitResult.failure("Recitation submission failed due to a server error.");
        }
    }

    /**
     * @return a failure when the parent cannot be used, or null when {@code recitation} is ready to insert
     */
    private RecitationSubmitResult applyPracticeAgain(Connection connection, long studentId, long enrollmentId,
                                                      long parentRecitationId, Recitation recitation)
            throws SQLException {
        Optional<Recitation> parentOpt = recitationDao.findById(connection, parentRecitationId);
        if (parentOpt.isEmpty()) {
            return RecitationSubmitResult.failure("You cannot practise from this recitation.");
        }
        Recitation parent = parentOpt.get();
        Optional<Enrollment> parentEnrollment = enrollmentDao.findById(connection, parent.getEnrollmentId());
        if (parentEnrollment.isEmpty() || parentEnrollment.get().getStudentId() != studentId) {
            return RecitationSubmitResult.failure("You cannot practise from this recitation.");
        }
        if (parent.getEnrollmentId() != enrollmentId) {
            return RecitationSubmitResult.failure("Practice Again stays on the same session.");
        }
        Optional<Evaluation> evaluation = evaluationDao.findByRecitationId(connection, parentRecitationId);
        if (evaluation.isEmpty() || evaluation.get().getPublishedAt() == null) {
            return RecitationSubmitResult.failure("Practice Again is available after your instructor publishes the review.");
        }
        recitation.setParentRecitationId(parentRecitationId);
        recitation.setAttemptNumber(1 + recitationDao.maxAttemptNumber(connection, enrollmentId));
        return null;
    }

    private void notifyStudent(Connection connection, long userId, String message) throws SQLException {
        if (userId <= 0 || message == null || message.isBlank()) {
            return;
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setMessage(message);
        notification.setCreatedAt(Instant.now());
        notificationDao.insert(connection, notification);
    }

    private String sessionLabel(TasmiSession session) {
        if (session == null || session.getTitle() == null || session.getTitle().trim().isEmpty()) {
            return "Session #" + (session == null ? "-" : session.getSessionId());
        }
        return session.getTitle().trim();
    }
}
