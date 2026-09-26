package model.service;

import model.dao.EnrollmentDao;
import model.dao.InstructorDao;
import model.dao.NotificationDao;
import model.dao.RecitationDao;
import model.dao.StudentDao;
import model.dao.PaymentDao;
import model.dao.TasmiSessionDao;
import model.dao.impl.PaymentDaoJdbc;
import model.dao.impl.EnrollmentDaoJdbc;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.NotificationDaoJdbc;
import model.dao.impl.RecitationDaoJdbc;
import model.dao.impl.StudentDaoJdbc;
import model.dao.impl.TasmiSessionDaoJdbc;
import model.entity.Enrollment;
import model.entity.EnrollmentStatus;
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

    public RecitationService() {
        this.studentDao = new StudentDaoJdbc();
        this.enrollmentDao = new EnrollmentDaoJdbc();
        this.recitationDao = new RecitationDaoJdbc();
        this.paymentDao = new PaymentDaoJdbc();
        this.tasmiSessionDao = new TasmiSessionDaoJdbc();
        this.instructorDao = new InstructorDaoJdbc();
        this.notificationDao = new NotificationDaoJdbc();
    }

    public RecitationSubmitResult submit(long studentUserId, long enrollmentId, String audioFilePath) {
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

            recitationDao.insert(connection, r);
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
            return RecitationSubmitResult.success();
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to submit recitation", ex);
            return RecitationSubmitResult.failure("Recitation submission failed due to a server error.");
        }
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
