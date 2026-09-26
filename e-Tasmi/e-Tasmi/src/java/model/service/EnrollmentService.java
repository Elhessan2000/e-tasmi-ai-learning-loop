package model.service;

import model.dao.EnrollmentDao;
import model.dao.NotificationDao;
import model.dao.StudentDao;
import model.dao.TasmiSessionDao;
import model.dao.impl.EnrollmentDaoJdbc;
import model.dao.impl.NotificationDaoJdbc;
import model.dao.impl.StudentDaoJdbc;
import model.dao.impl.TasmiSessionDaoJdbc;
import model.entity.Enrollment;
import model.entity.EnrollmentStatus;
import model.entity.Notification;
import model.entity.Student;
import model.entity.TasmiSession;
import model.entity.TasmiSessionStatus;
import util.Db;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EnrollmentService {
    private static final Logger LOGGER = Logger.getLogger(EnrollmentService.class.getName());

    private final StudentDao studentDao;
    private final TasmiSessionDao tasmiSessionDao;
    private final EnrollmentDao enrollmentDao;
    private final NotificationDao notificationDao;

    public EnrollmentService() {
        this.studentDao = new StudentDaoJdbc();
        this.tasmiSessionDao = new TasmiSessionDaoJdbc();
        this.enrollmentDao = new EnrollmentDaoJdbc();
        this.notificationDao = new NotificationDaoJdbc();
    }

    /**
     * Allows a student to withdraw from an enrollment (set status to CANCELLED).
     * Only allowed if enrollment is PENDING or APPROVED and session is not completed/cancelled.
     */
    public boolean withdrawEnrollment(long studentUserId, long enrollmentId) {
        if (studentUserId <= 0 || enrollmentId <= 0) return false;
        try (Connection connection = Db.getConnection()) {
            Optional<Enrollment> enrollmentOpt = enrollmentDao.findById(connection, enrollmentId);
            if (enrollmentOpt.isEmpty()) return false;
            Enrollment e = enrollmentOpt.get();
            if (e.getEnrollmentStatus() == EnrollmentStatus.CANCELLED || e.getEnrollmentStatus() == EnrollmentStatus.REJECTED) return false;
            // Check student owns this enrollment
            Optional<Student> studentOpt = studentDao.findByUserId(connection, studentUserId);
            if (studentOpt.isEmpty() || studentOpt.get().getStudentId() != e.getStudentId()) return false;
            // Check session status
            Optional<TasmiSession> sessionOpt = tasmiSessionDao.findById(connection, e.getSessionId());
            if (sessionOpt.isEmpty()) return false;
            TasmiSession s = sessionOpt.get();
            if (s.getStatus() == model.entity.TasmiSessionStatus.COMPLETED || s.getStatus() == model.entity.TasmiSessionStatus.CANCELLED) return false;
            // Allow withdrawal if enrollment is PENDING or APPROVED
            return enrollmentDao.updateStatus(connection, enrollmentId, EnrollmentStatus.CANCELLED);
        } catch (SQLException ex) {
            Logger.getLogger(EnrollmentService.class.getName()).log(Level.SEVERE, "Failed to withdraw enrollment", ex);
            return false;
        }
    }

    public EnrollmentResult enroll(long studentUserId, long sessionId) {
        if (sessionId <= 0) {
            return EnrollmentResult.failure("Invalid session.");
        }

        try (Connection connection = Db.getConnection()) {
            connection.setAutoCommit(false);

            Optional<Student> studentOpt = studentDao.findByUserId(connection, studentUserId);
            if (studentOpt.isEmpty()) {
                connection.rollback();
                return EnrollmentResult.failure("Student profile not found.");
            }

            Optional<TasmiSession> sessionOpt = tasmiSessionDao.findById(connection, sessionId);
            if (sessionOpt.isEmpty()) {
                connection.rollback();
                return EnrollmentResult.failure("Session not found.");
            }

            TasmiSession s = sessionOpt.get();
            if (s.getStatus() != TasmiSessionStatus.SCHEDULED) {
                connection.rollback();
                return EnrollmentResult.failure("This session is not available for enrollment.");
            }

            Student student = studentOpt.get();
            if (student.getLevel() != null && s.getLevel() != null && student.getLevel() != s.getLevel()) {
                connection.rollback();
                return EnrollmentResult.failure("You can only enroll in sessions that match your level.");
            }

            long studentId = student.getStudentId();
            boolean paymentRequired = s.getFee() != null && s.getFee().compareTo(BigDecimal.ZERO) > 0;
            String sessionLabel = sessionTitle(s);
            Optional<Enrollment> existing = enrollmentDao.findByStudentAndSession(connection, studentId, sessionId);
            if (existing.isPresent()) {
                Enrollment existingEnrollment = existing.get();
                if (existingEnrollment.getEnrollmentStatus() == EnrollmentStatus.CANCELLED
                        || existingEnrollment.getEnrollmentStatus() == EnrollmentStatus.REJECTED) {
                    int activeEnrollments = enrollmentDao.countActiveBySessionId(connection, sessionId);
                    if (s.getCapacity() > 0 && activeEnrollments >= s.getCapacity()) {
                        connection.rollback();
                        return EnrollmentResult.failure("This session has already reached its capacity.");
                    }
                    boolean reactivated = enrollmentDao.updateStatus(connection, existingEnrollment.getEnrollmentId(), EnrollmentStatus.PENDING);
                    if (!reactivated) {
                        connection.rollback();
                        return EnrollmentResult.failure("We could not reactivate this enrollment right now.");
                    }
                    notifyStudent(connection,
                            studentOpt.get().getUserId(),
                            paymentRequired
                                    ? "Enrollment reopened for " + sessionLabel + ". Review the session and continue to checkout to confirm your seat."
                                    : "Enrollment reopened for " + sessionLabel + ". This free session will be confirmed automatically.");
                    connection.commit();
                    return EnrollmentResult.success(
                            existingEnrollment.getEnrollmentId(),
                            false,
                            paymentRequired,
                            paymentRequired ? "reactivated_payment_required" : "reactivated_free_session",
                            paymentRequired
                                    ? "Your enrollment was reopened. Continue to payment review when you are ready."
                                    : "Your enrollment was reopened for this free session."
                    );
                }
                connection.rollback();
                return EnrollmentResult.success(
                        existingEnrollment.getEnrollmentId(),
                        true,
                        paymentRequired,
                        paymentRequired ? "already_enrolled_payment_required" : "already_enrolled_free_session",
                        paymentRequired
                                ? "You already have an enrollment for this session. Open payment review to continue toward checkout."
                                : "You are already enrolled in this free session."
                );
            }

            int activeEnrollments = enrollmentDao.countActiveBySessionId(connection, sessionId);
            if (s.getCapacity() > 0 && activeEnrollments >= s.getCapacity()) {
                connection.rollback();
                return EnrollmentResult.failure("This session has already reached its capacity.");
            }

            Enrollment e = new Enrollment();
            e.setStudentId(studentId);
            e.setSessionId(sessionId);
            e.setEnrollmentStatus(EnrollmentStatus.PENDING);

            long enrollmentId = enrollmentDao.insert(connection, e);
            notifyStudent(connection,
                    studentOpt.get().getUserId(),
                    paymentRequired
                            ? "Enrollment created for " + sessionLabel + ". Review the session and continue to checkout to confirm your seat."
                            : "Enrollment created for " + sessionLabel + ". This free session will be confirmed automatically.");
            connection.commit();
            return EnrollmentResult.success(
                    enrollmentId,
                    false,
                    paymentRequired,
                    paymentRequired ? "payment_required" : "free_session",
                    paymentRequired
                            ? "Enrollment created. Review the session and continue to checkout to confirm your seat."
                            : "Enrollment created for a free session."
            );
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to enroll student", ex);
            return EnrollmentResult.failure("Enrollment failed due to a server error.");
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

    private String sessionTitle(TasmiSession session) {
        if (session == null || session.getTitle() == null || session.getTitle().trim().isEmpty()) {
            return "Session #" + (session == null ? "-" : session.getSessionId());
        }
        return session.getTitle().trim();
    }
}
