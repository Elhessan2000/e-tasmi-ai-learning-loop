package model.service;

import model.dao.EnrollmentDao;
import model.dao.EvaluationDao;
import model.dao.PaymentDao;
import model.dao.ProgressDao;
import model.dao.RecitationDao;
import model.dao.SessionAttendanceDao;
import model.dao.StudentDao;
import model.dao.TasmiSessionDao;
import model.dao.impl.EnrollmentDaoJdbc;
import model.dao.impl.EvaluationDaoJdbc;
import model.dao.impl.PaymentDaoJdbc;
import model.dao.impl.ProgressDaoJdbc;
import model.dao.impl.RecitationDaoJdbc;
import model.dao.impl.SessionAttendanceDaoJdbc;
import model.dao.impl.StudentDaoJdbc;
import model.dao.impl.TasmiSessionDaoJdbc;
import model.entity.AttendanceStatus;
import model.entity.Enrollment;
import model.entity.EnrollmentStatus;
import model.entity.Payment;
import model.entity.PaymentStatus;
import model.entity.Progress;
import model.entity.Recitation;
import model.entity.SessionAttendance;
import model.entity.Student;
import model.entity.StudentProgressSummary;
import model.entity.TasmiSession;
import model.entity.TasmiSessionStatus;
import util.Db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ProgressService {
    private static final Logger LOGGER = Logger.getLogger(ProgressService.class.getName());

    private final StudentDao studentDao;
    private final ProgressDao progressDao;
    private final EnrollmentDao enrollmentDao;
    private final TasmiSessionDao tasmiSessionDao;
    private final PaymentDao paymentDao;
    private final RecitationDao recitationDao;
    private final EvaluationDao evaluationDao;
    private final SessionAttendanceDao attendanceDao;

    public ProgressService() {
        this.studentDao = new StudentDaoJdbc();
        this.progressDao = new ProgressDaoJdbc();
        this.enrollmentDao = new EnrollmentDaoJdbc();
        this.tasmiSessionDao = new TasmiSessionDaoJdbc();
        this.paymentDao = new PaymentDaoJdbc();
        this.recitationDao = new RecitationDaoJdbc();
        this.evaluationDao = new EvaluationDaoJdbc();
        this.attendanceDao = new SessionAttendanceDaoJdbc();
    }

    public Optional<Progress> getOrComputeForStudentUser(long studentUserId) {
        if (studentUserId <= 0) {
            return Optional.empty();
        }

        try (Connection connection = Db.getConnection()) {
            Optional<Student> studentOpt = studentDao.findByUserId(connection, studentUserId);
            if (studentOpt.isEmpty()) {
                return Optional.empty();
            }

            long studentId = studentOpt.get().getStudentId();
            Optional<Progress> existing = progressDao.findByStudentId(connection, studentId);
            if (existing.isPresent()) {
                return existing;
            }

            // If no row yet, compute based on evaluations and insert
            progressDao.upsert(connection, studentId, progressDao.computeCompletionRate(connection, studentId));
            return progressDao.findByStudentId(connection, studentId);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load progress", ex);
            return Optional.empty();
        }
    }

    public StudentProgressSummary buildSummaryForStudentUser(long studentUserId) {
        StudentProgressSummary summary = new StudentProgressSummary();
        if (studentUserId <= 0) {
            return summary;
        }

        try (Connection connection = Db.getConnection()) {
            Optional<Student> studentOpt = studentDao.findByUserId(connection, studentUserId);
            if (studentOpt.isEmpty()) {
                return summary;
            }

            long studentId = studentOpt.get().getStudentId();
            List<Enrollment> enrollments = enrollmentDao.listByStudentId(connection, studentId);
            summary.setTotalEnrollments(enrollments.size());

            for (Enrollment enrollment : enrollments) {
                if (enrollment == null) {
                    continue;
                }

                if (enrollment.getEnrollmentStatus() == EnrollmentStatus.APPROVED) {
                    summary.setApprovedEnrollments(summary.getApprovedEnrollments() + 1);
                }

                Optional<TasmiSession> sessionOpt = tasmiSessionDao.findById(connection, enrollment.getSessionId());
                TasmiSession session = sessionOpt.orElse(null);
                if (session != null && session.getStatus() == TasmiSessionStatus.COMPLETED) {
                    summary.setCompletedSessions(summary.getCompletedSessions() + 1);
                }

                Optional<Payment> paymentOpt = paymentDao.findByEnrollmentId(connection, enrollment.getEnrollmentId());
                if (session != null && session.getFee() != null && session.getFee().compareTo(java.math.BigDecimal.ZERO) > 0) {
                    if (paymentOpt.isPresent() && paymentOpt.get().getPaymentStatus() == PaymentStatus.APPROVED) {
                        summary.setSuccessfulPayments(summary.getSuccessfulPayments() + 1);
                    } else {
                        summary.setPendingPayments(summary.getPendingPayments() + 1);
                    }
                }

                List<Recitation> recitations = recitationDao.listByEnrollmentId(connection, enrollment.getEnrollmentId());
                summary.setRecitationsSubmitted(summary.getRecitationsSubmitted() + recitations.size());
                for (Recitation recitation : recitations) {
                    if (recitation != null && evaluationDao.findByRecitationId(connection, recitation.getRecitationId()).isPresent()) {
                        summary.setEvaluatedRecitations(summary.getEvaluatedRecitations() + 1);
                    }
                }

                List<SessionAttendance> attendanceList = attendanceDao.listBySessionId(connection, enrollment.getSessionId());
                for (SessionAttendance attendance : attendanceList) {
                    if (attendance == null || attendance.getStudentId() != studentId) {
                        continue;
                    }
                    summary.setAttendanceMarked(summary.getAttendanceMarked() + 1);
                    if (attendance.getAttendanceStatus() == AttendanceStatus.PRESENT) {
                        summary.setAttendancePresent(summary.getAttendancePresent() + 1);
                    }
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to build student progress summary", ex);
        }

        return summary;
    }
}
