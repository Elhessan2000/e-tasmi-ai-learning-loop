package model.service;

import model.dao.EnrollmentDao;
import model.dao.PaymentDao;
import model.dao.SessionAttendanceDao;
import model.dao.StudentDao;
import model.dao.TasmiSessionDao;
import model.dao.impl.EnrollmentDaoJdbc;
import model.dao.impl.PaymentDaoJdbc;
import model.dao.impl.SessionAttendanceDaoJdbc;
import model.dao.impl.StudentDaoJdbc;
import model.dao.impl.TasmiSessionDaoJdbc;
import model.entity.AttendanceStatus;
import model.entity.Enrollment;
import model.entity.EnrollmentStatus;
import model.entity.Payment;
import model.entity.PaymentStatus;
import model.entity.SessionAttendance;
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

public class AttendanceService {
    private static final Logger LOGGER = Logger.getLogger(AttendanceService.class.getName());

    private final StudentDao studentDao = new StudentDaoJdbc();
    private final EnrollmentDao enrollmentDao = new EnrollmentDaoJdbc();
    private final PaymentDao paymentDao = new PaymentDaoJdbc();
    private final TasmiSessionDao tasmiSessionDao = new TasmiSessionDaoJdbc();
    private final SessionAttendanceDao attendanceDao = new SessionAttendanceDaoJdbc();

    public void markStudentJoined(long studentUserId, long sessionId) {
        if (studentUserId <= 0 || sessionId <= 0) {
            return;
        }

        try (Connection connection = Db.getConnection()) {
            connection.setAutoCommit(false);

            Optional<Student> studentOpt = studentDao.findByUserId(connection, studentUserId);
            if (studentOpt.isEmpty()) {
                connection.rollback();
                return;
            }

            Optional<Enrollment> enrollmentOpt = enrollmentDao.findByStudentAndSession(connection, studentOpt.get().getStudentId(), sessionId);
            if (enrollmentOpt.isEmpty()) {
                connection.rollback();
                return;
            }

            Enrollment enrollment = enrollmentOpt.get();
            if (enrollment.getEnrollmentStatus() != EnrollmentStatus.APPROVED) {
                connection.rollback();
                return;
            }

            Optional<TasmiSession> sessionOpt = tasmiSessionDao.findById(connection, sessionId);
            if (sessionOpt.isEmpty()) {
                connection.rollback();
                return;
            }

            TasmiSession tasmiSession = sessionOpt.get();
            if (tasmiSession.getStatus() != TasmiSessionStatus.ONGOING) {
                connection.rollback();
                return;
            }

            boolean paymentRequired = tasmiSession.getFee() != null && tasmiSession.getFee().compareTo(BigDecimal.ZERO) > 0;
            Optional<Payment> paymentOpt = paymentDao.findByEnrollmentId(connection, enrollment.getEnrollmentId());
            if (paymentRequired && (paymentOpt.isEmpty() || paymentOpt.get().getPaymentStatus() != PaymentStatus.APPROVED)) {
                connection.rollback();
                return;
            }

            Optional<SessionAttendance> existingOpt = attendanceDao.findBySessionIdAndStudentId(connection, sessionId, studentOpt.get().getStudentId());
            SessionAttendance attendance = existingOpt.orElseGet(SessionAttendance::new);
            attendance.setSessionId(sessionId);
            attendance.setStudentId(studentOpt.get().getStudentId());
            attendance.setMarkedByInstructorId(tasmiSession.getInstructorId());
            attendance.setAttendanceStatus(AttendanceStatus.PRESENT);
            attendance.setNotes("Auto-recorded when the student opened the live session join link.");
            attendance.setMarkedAt(Instant.now());

            if (existingOpt.isPresent()) {
                attendanceDao.update(connection, attendance);
            } else {
                attendanceDao.insert(connection, attendance);
            }

            connection.commit();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to auto-record live-session attendance for session " + sessionId, ex);
        }
    }
}
