package model.service;

import model.dao.EnrollmentDao;
import model.dao.InstructorDao;
import model.dao.PaymentDao;
import model.dao.StudentDao;
import model.dao.TasmiSessionDao;
import model.dao.UserDao;
import model.dao.impl.EnrollmentDaoJdbc;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.PaymentDaoJdbc;
import model.dao.impl.StudentDaoJdbc;
import model.dao.impl.TasmiSessionDaoJdbc;
import model.dao.impl.UserDaoJdbc;
import model.entity.Enrollment;
import model.entity.EnrollmentStatus;
import model.entity.Instructor;
import model.entity.Payment;
import model.entity.PaymentStatus;
import model.entity.Student;
import model.entity.TasmiSession;
import model.entity.TasmiSessionStatus;
import model.entity.User;
import util.Db;
import util.ZoomJoinLinkUtil;
import util.ZoomMeetingSdkConfig;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public class LiveSessionAccessService {
    private static final String LIVE_PROVIDER_MANUAL = "MANUAL";
    private static final String LIVE_PROVIDER_ZOOM = "ZOOM";

    private final StudentDao studentDao = new StudentDaoJdbc();
    private final InstructorDao instructorDao = new InstructorDaoJdbc();
    private final EnrollmentDao enrollmentDao = new EnrollmentDaoJdbc();
    private final PaymentDao paymentDao = new PaymentDaoJdbc();
    private final TasmiSessionDao tasmiSessionDao = new TasmiSessionDaoJdbc();
    private final UserDao userDao = new UserDaoJdbc();

    public LiveSessionAccess validateStudentJoin(long userId, long sessionId) throws SQLException {
        try (Connection connection = Db.getConnection()) {
            return validateStudentJoin(connection, userId, sessionId);
        }
    }

    public LiveSessionAccess validateInstructorHost(long userId, long sessionId) throws SQLException {
        try (Connection connection = Db.getConnection()) {
            return validateInstructorHost(connection, userId, sessionId);
        }
    }

    public LiveSessionAccess validateStudentJoin(Connection connection, long userId, long sessionId) throws SQLException {
        if (userId <= 0 || sessionId <= 0) {
            return LiveSessionAccess.denied("Invalid live-session request.");
        }

        Optional<User> userOpt = userDao.findById(connection, userId);
        if (userOpt.isEmpty()) {
            return LiveSessionAccess.denied("Your account could not be loaded.");
        }

        Optional<Student> studentOpt = studentDao.findByUserId(connection, userId);
        if (studentOpt.isEmpty()) {
            return LiveSessionAccess.denied("Student profile not found.");
        }

        Optional<Enrollment> enrollmentOpt = enrollmentDao.findByStudentAndSession(connection, studentOpt.get().getStudentId(), sessionId);
        if (enrollmentOpt.isEmpty()) {
            return LiveSessionAccess.denied("You are not enrolled in this session.");
        }

        Enrollment enrollment = enrollmentOpt.get();
        if (enrollment.getEnrollmentStatus() != EnrollmentStatus.APPROVED) {
            return LiveSessionAccess.denied("Your enrollment is not approved yet.");
        }

        Optional<TasmiSession> sessionOpt = tasmiSessionDao.findById(connection, sessionId);
        if (sessionOpt.isEmpty()) {
            return LiveSessionAccess.denied("Live session not found.");
        }

        TasmiSession tasmiSession = sessionOpt.get();
        boolean paymentRequired = tasmiSession.getFee() != null && tasmiSession.getFee().compareTo(java.math.BigDecimal.ZERO) > 0;
        Optional<Payment> paymentOpt = paymentDao.findByEnrollmentId(connection, enrollment.getEnrollmentId());
        if (paymentRequired && (paymentOpt.isEmpty() || paymentOpt.get().getPaymentStatus() != PaymentStatus.APPROVED)) {
            return LiveSessionAccess.denied("An approved payment is required before joining this live session.");
        }
        if (!hasParticipantJoinAccess(tasmiSession)) {
            return LiveSessionAccess.denied("This live session does not have a participant join link yet.");
        }

        return LiveSessionAccess.allowed(
                tasmiSession,
                defaultDisplayName(userOpt.get()),
                userOpt.get().getEmail(),
                false,
                ZoomJoinLinkUtil.resolveParticipantJoinUrl(tasmiSession)
        );
    }

    public LiveSessionAccess validateInstructorHost(Connection connection, long userId, long sessionId) throws SQLException {
        if (userId <= 0 || sessionId <= 0) {
            return LiveSessionAccess.denied("Invalid live-session request.");
        }

        Optional<User> userOpt = userDao.findById(connection, userId);
        if (userOpt.isEmpty()) {
            return LiveSessionAccess.denied("Your account could not be loaded.");
        }

        Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, userId);
        if (instructorOpt.isEmpty()) {
            return LiveSessionAccess.denied("Instructor profile not found.");
        }

        Optional<TasmiSession> sessionOpt = tasmiSessionDao.findById(connection, sessionId);
        if (sessionOpt.isEmpty()) {
            return LiveSessionAccess.denied("Live session not found.");
        }

        TasmiSession tasmiSession = sessionOpt.get();
        if (tasmiSession.getInstructorId() != instructorOpt.get().getInstructorId()) {
            return LiveSessionAccess.denied("You do not have access to host this session.");
        }
        if (!hasParticipantJoinAccess(tasmiSession)) {
            return LiveSessionAccess.denied("This live session does not have a valid meeting link yet.");
        }

        String hostUrl = tasmiSession.getZoomStartUrl();
        if (hostUrl == null || hostUrl.isBlank()) {
            hostUrl = tasmiSession.getMeetingLink();
        }

        return LiveSessionAccess.allowed(
                tasmiSession,
                defaultDisplayName(userOpt.get()),
                userOpt.get().getEmail(),
                true,
                hostUrl
        );
    }

    public boolean canEmbed(TasmiSession tasmiSession) {
        if (tasmiSession == null || !ZoomMeetingSdkConfig.isEmbeddingAvailable()) {
            return false;
        }
        String provider = normalize(tasmiSession.getLiveProvider());
        if (!LIVE_PROVIDER_ZOOM.equalsIgnoreCase(provider)) {
            return false;
        }
        Long zid = tasmiSession.getZoomMeetingId();
        return zid != null && zid > 0;
    }

    private boolean hasParticipantJoinAccess(TasmiSession tasmiSession) {
        if (tasmiSession == null) {
            return false;
        }
        String provider = normalize(tasmiSession.getLiveProvider());
        boolean validProvider = LIVE_PROVIDER_MANUAL.equalsIgnoreCase(provider)
                || LIVE_PROVIDER_ZOOM.equalsIgnoreCase(provider);
        String joinUrl = normalize(ZoomJoinLinkUtil.resolveParticipantJoinUrl(tasmiSession));
        return validProvider
                && joinUrl != null
                && (joinUrl.startsWith("http://") || joinUrl.startsWith("https://"));
    }

    private String defaultDisplayName(User user) {
        if (user == null || user.getFullName() == null || user.getFullName().isBlank()) {
            return "e-Tasmi User";
        }
        return user.getFullName().trim();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
