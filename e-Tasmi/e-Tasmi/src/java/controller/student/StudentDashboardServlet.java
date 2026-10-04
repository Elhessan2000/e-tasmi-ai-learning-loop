package controller.student;

import model.dao.*;
import model.service.TasmiSessionService;
import model.entity.*;
import model.service.ProgressService;
import util.DashboardGreeting;
import util.Db;
import util.LocaleSupport;
import util.MeetingLinkUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "StudentDashboardServlet", urlPatterns = {"/student/dashboard"})
public class StudentDashboardServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(StudentDashboardServlet.class.getName());

    private final StudentDao studentDao = new model.dao.impl.StudentDaoJdbc();
    private final EnrollmentDao enrollmentDao = new model.dao.impl.EnrollmentDaoJdbc();
    private final PaymentDao paymentDao = new model.dao.impl.PaymentDaoJdbc();
    private final RecitationDao recitationDao = new model.dao.impl.RecitationDaoJdbc();
    private final EvaluationDao evaluationDao = new model.dao.impl.EvaluationDaoJdbc();
    private final NotificationDao notificationDao = new model.dao.impl.NotificationDaoJdbc();
    private final InstructorDao instructorDao = new model.dao.impl.InstructorDaoJdbc();
    private final UserDao userDao = new model.dao.impl.UserDaoJdbc();
    private final TasmiSessionDao tasmiSessionDao = new model.dao.impl.TasmiSessionDaoJdbc();
    private final ProgressService progressService = new ProgressService();
    private final TasmiSessionService tasmiSessionService = new TasmiSessionService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = readUserId(session);

        request.setAttribute("enrollmentsCount", 0);
        request.setAttribute("pendingPaymentsCount", 0);
        request.setAttribute("pendingEvaluationsCount", 0);
        request.setAttribute("notificationsCount", 0);
        request.setAttribute("upcomingSessions", Collections.emptyList());
        request.setAttribute("upcomingSessionCards", Collections.emptyList());
        request.setAttribute("recentNotifications", Collections.emptyList());

        progressService.getOrComputeForStudentUser(userId)
                .ifPresent(p -> request.setAttribute("progress", p));

        if (userId > 0) {
            try (Connection connection = Db.getConnection()) {
                Optional<Student> studentOpt = studentDao.findByUserId(connection, userId);
                if (studentOpt.isPresent()) {
                    request.setAttribute("student", studentOpt.get());

                    List<Enrollment> enrollments = enrollmentDao.listByStudentId(connection, studentOpt.get().getStudentId());
                    request.setAttribute("enrollmentsCount", enrollments.size());
                    Map<Long, Enrollment> enrollmentsBySessionId = new HashMap<>();
                    Map<Long, Payment> paymentsByEnrollmentId = new HashMap<>();
                    Student student = studentOpt.get();
                    for (Enrollment enrollment : enrollments) {
                        if (enrollment != null) {
                            enrollmentsBySessionId.put(enrollment.getSessionId(), enrollment);
                            paymentDao.findByEnrollmentId(connection, enrollment.getEnrollmentId())
                                    .ifPresent(payment -> paymentsByEnrollmentId.put(enrollment.getEnrollmentId(), payment));
                        }
                    }

                    int pendingPayments = 0;
                    int pendingEvaluations = 0;

                    List<TasmiSession> visibleSessions = new ArrayList<>(tasmiSessionService.listStudentVisibleSessions());
                    Map<Long, TasmiSession> visibleById = new HashMap<>();
                    for (TasmiSession visibleSession : visibleSessions) {
                        if (visibleSession != null) {
                            visibleById.put(visibleSession.getSessionId(), visibleSession);
                        }
                    }

                    Map<Long, TasmiSession> upcomingById = new LinkedHashMap<>();
                    java.time.Instant now = java.time.Instant.now();

                    for (Enrollment e : enrollments) {
                        Payment payment = paymentsByEnrollmentId.get(e.getEnrollmentId());
                        TasmiSession linkedSession = visibleById.get(e.getSessionId());
                        if (linkedSession == null) {
                            linkedSession = tasmiSessionDao.findById(connection, e.getSessionId()).orElse(null);
                        }
                        if (!isActiveInstructorSession(connection, linkedSession)) {
                            continue;
                        }
                        BigDecimal fee = linkedSession == null ? BigDecimal.ZERO : (linkedSession.getFee() == null ? BigDecimal.ZERO : linkedSession.getFee());
                        if (linkedSession != null
                                && linkedSession.getStatus() != TasmiSessionStatus.CANCELLED
                                && fee.compareTo(BigDecimal.ZERO) > 0
                                && (payment == null || payment.getPaymentStatus() != PaymentStatus.APPROVED)) {
                            pendingPayments++;
                        }

                        if (matchesStudentLevel(student, linkedSession)
                                && isConfirmedEnrollment(linkedSession, e, payment)
                                && isDashboardUpcoming(linkedSession, now)) {
                            upcomingById.put(linkedSession.getSessionId(), linkedSession);
                        }

                        List<Recitation> recitations = recitationDao.listByEnrollmentId(connection, e.getEnrollmentId());
                        for (Recitation recitation : recitations) {
                            if (recitation != null && evaluationDao.findByRecitationId(connection, recitation.getRecitationId()).isEmpty()) {
                                pendingEvaluations++;
                            }
                        }
                    }

                    List<TasmiSession> upcoming = new ArrayList<>(upcomingById.values());

                    upcoming.sort(Comparator
                            .comparing(TasmiSession::getSessionDate, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(TasmiSession::getSessionTime, Comparator.nullsLast(Comparator.naturalOrder())));

                    if (upcoming.size() > 6) {
                        upcoming = upcoming.subList(0, 6);
                    }

                    request.setAttribute("pendingPaymentsCount", pendingPayments);
                    request.setAttribute("pendingEvaluationsCount", pendingEvaluations);
                    request.setAttribute("upcomingSessions", upcoming);

                    List<Map<String, Object>> cards = new ArrayList<>();
                    for (TasmiSession s : upcoming) {
                        cards.add(buildSessionCard(request, connection, s, enrollmentsBySessionId, paymentsByEnrollmentId));
                    }

                    request.setAttribute("upcomingSessionCards", cards);
                }

                List<Notification> allNotifications = filterStudentNotifications(notificationDao.listByUserId(connection, userId));
                request.setAttribute("notificationsCount", allNotifications.size());
                if (allNotifications.size() > 5) {
                    request.setAttribute("recentNotifications", allNotifications.subList(0, 5));
                } else {
                    request.setAttribute("recentNotifications", allNotifications);
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Failed to load student dashboard", ex);
            }
        }

        prepareDashboardGreeting(request);

        request.getRequestDispatcher("/jsp/student/dashboard.jsp").forward(request, response);
    }

    private void prepareDashboardGreeting(HttpServletRequest request) {
        HttpSession httpSession = request.getSession(false);
        String displayName = null;
        if (httpSession != null) {
            Object nameObj = httpSession.getAttribute("displayName");
            if (nameObj != null) {
                displayName = String.valueOf(nameObj);
            }
        }

        String localeCode = LocaleSupport.getLocale(request);
        request.setAttribute("jstlLocale", Locale.forLanguageTag(localeCode));
        request.setAttribute("timeOfDay", DashboardGreeting.resolveTimeOfDay(LocalTime.now()));
        request.setAttribute("heroFirstName", DashboardGreeting.firstName(displayName, "Student"));
    }

    private long readUserId(HttpSession session) {
        if (session == null) {
            return 0;
        }
        Object userIdObj = session.getAttribute("userId");
        if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        }
        if (userIdObj instanceof Integer) {
            return ((Integer) userIdObj).longValue();
        }
        return 0;
    }

    private String shorten(String text, int max) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.isEmpty() || normalized.length() <= max) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, max - 3)).trim() + "...";
    }

    private String initials(String value) {
        if (value == null) {
            return "I";
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return "I";
        }
        String[] parts = normalized.split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, 1).toUpperCase(Locale.ROOT);
        }
        String first = parts[0].isEmpty() ? "" : parts[0].substring(0, 1).toUpperCase(Locale.ROOT);
        String last = parts[parts.length - 1].isEmpty() ? "" : parts[parts.length - 1].substring(0, 1).toUpperCase(Locale.ROOT);
        String combined = (first + last).trim();
        return combined.isEmpty() ? "I" : combined;
    }

    private String feeLabel(BigDecimal fee) {
        if (fee == null || fee.compareTo(BigDecimal.ZERO) <= 0) {
            return "Free session";
        }
        return "RM " + fee.stripTrailingZeros().toPlainString();
    }

    private Map<String, Object> buildSessionCard(HttpServletRequest request,
                                                 Connection connection,
                                                 TasmiSession session,
                                                 Map<Long, Enrollment> enrollmentsBySessionId,
                                                 Map<Long, Payment> paymentsByEnrollmentId) {
        Map<String, Object> card = new HashMap<>();
        card.put("session", session);

        int enrolledCount = 0;
        try {
            enrolledCount = enrollmentDao.countActiveBySessionId(connection, session.getSessionId());
        } catch (SQLException ignored) {
        }
        card.put("enrolledCount", enrolledCount);
        card.put("availableSeats", session.getCapacity() > 0 ? Math.max(0, session.getCapacity() - enrolledCount) : 0);

        Enrollment linkedEnrollment = enrollmentsBySessionId.get(session.getSessionId());
        Payment linkedPayment = null;
        if (linkedEnrollment != null) {
            card.put("enrollment", linkedEnrollment);
            linkedPayment = paymentsByEnrollmentId.get(linkedEnrollment.getEnrollmentId());
            if (linkedPayment != null) {
                card.put("payment", linkedPayment);
            }
        }

        card.put("paymentRequired", requiresPayment(session));
        card.put("joinReady", isJoinReady(session, linkedEnrollment, linkedPayment));
        card.put("descriptionSnippet", shorten(session.getDescription(), 140));

        String instructorName = "Instructor";
        String instructorTitle = "Teaching instructor";
        String instructorPhotoUrl = null;
        try {
            if (session.getInstructorId() > 0) {
                Optional<Instructor> instOpt = instructorDao.findById(connection, session.getInstructorId());
                if (instOpt.isPresent()) {
                    Instructor instructor = instOpt.get();
                    Optional<User> uOpt = userDao.findById(connection, instructor.getUserId());
                    if (uOpt.isPresent() && uOpt.get().getFullName() != null && !uOpt.get().getFullName().trim().isEmpty()) {
                        instructorName = uOpt.get().getFullName().trim();
                    } else {
                        instructorName = "Instructor #" + session.getInstructorId();
                    }
                    if (instructor.getTitle() != null && !instructor.getTitle().trim().isEmpty()) {
                        instructorTitle = instructor.getTitle().trim();
                    }
                    if (instructor.getUserId() > 0) {
                        instructorPhotoUrl = request.getContextPath() + "/student/instructor-photo?instructorId=" + session.getInstructorId();
                    }
                } else {
                    instructorName = "Instructor #" + session.getInstructorId();
                }
            }
        } catch (SQLException ignored) {
        }

        card.put("instructorName", instructorName);
        card.put("instructorTitle", instructorTitle);
        card.put("instructorPhotoUrl", instructorPhotoUrl);
        card.put("instructorInitials", initials(instructorName));
        card.put("sessionModeLabel", modeLabel(session));
        card.put("capacityLabel", session.getCapacity() > 0 ? Math.max(0, session.getCapacity() - enrolledCount) + " seats left" : "Flexible capacity");
        card.put("feeLabel", feeLabel(session.getFee()));

        return card;
    }

    private boolean requiresPayment(TasmiSession session) {
        return session != null
                && session.getFee() != null
                && session.getFee().compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean matchesStudentLevel(Student student, TasmiSession session) {
        if (student == null || session == null) {
            return false;
        }
        if (student.getLevel() == null || session.getLevel() == null) {
            return true;
        }
        return student.getLevel() == session.getLevel();
    }

    private List<Notification> filterStudentNotifications(List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) {
            return Collections.emptyList();
        }
        List<Notification> filtered = new ArrayList<>();
        for (Notification notification : notifications) {
            if (notification == null) {
                continue;
            }
            String message = notification.getMessage() == null ? "" : notification.getMessage().toLowerCase(Locale.ROOT);
            if (message.contains("demo") || message.contains("sandbox") || message.contains("mock")) {
                continue;
            }
            filtered.add(notification);
        }
        return filtered;
    }

    private boolean isJoinReady(TasmiSession session, Enrollment enrollment, Payment payment) {
        if (session == null || enrollment == null || enrollment.getEnrollmentStatus() != EnrollmentStatus.APPROVED) {
            return false;
        }
        if (requiresPayment(session) && (payment == null || payment.getPaymentStatus() != PaymentStatus.APPROVED)) {
            return false;
        }
        return hasUsableLiveBundle(session);
    }

    private boolean isConfirmedEnrollment(TasmiSession session, Enrollment enrollment, Payment payment) {
        if (session == null || enrollment == null || enrollment.getEnrollmentStatus() != EnrollmentStatus.APPROVED) {
            return false;
        }
        return !requiresPayment(session)
                || (payment != null && payment.getPaymentStatus() == PaymentStatus.APPROVED);
    }

    private boolean isDashboardUpcoming(TasmiSession session, java.time.Instant now) {
        if (session == null || session.getStatus() == null) {
            return false;
        }
        if (session.getStatus() == TasmiSessionStatus.ONGOING) {
            return true;
        }
        return TasmiSessionService.isUpcoming(session, now);
    }

    private boolean isActiveInstructorSession(Connection connection, TasmiSession session) throws SQLException {
        if (connection == null || session == null || session.getInstructorId() <= 0) {
            return false;
        }
        String sql = "SELECT i.verification_status, u.status, u.is_active "
                + "FROM instructor i "
                + "JOIN `user` u ON u.user_id = i.user_id "
                + "WHERE i.instructor_id = ?";
        try (java.sql.PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, session.getInstructorId());
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                String verificationStatus = rs.getString("verification_status");
                String userStatus = rs.getString("status");
                return rs.getBoolean("is_active")
                        && "APPROVED".equalsIgnoreCase(verificationStatus == null ? "" : verificationStatus.trim())
                        && !"DELETED".equalsIgnoreCase(userStatus == null ? "" : userStatus.trim());
            }
        }
    }

    private boolean hasUsableLiveBundle(TasmiSession session) {
        if (session == null) {
            return false;
        }
        String provider = session.getLiveProvider();
        String meetingLink = MeetingLinkUtil.toStudentJoinLink(session.getMeetingLink());
        if (!"MANUAL".equalsIgnoreCase(provider == null ? "" : provider.trim())) {
            return false;
        }
        if (meetingLink == null || meetingLink.trim().isEmpty()) {
            return false;
        }
        String normalized = meetingLink.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private String modeLabel(TasmiSession session) {
        if (session == null || session.getMode() == null) {
            return "Guided session";
        }
        return session.getMode() == SessionMode.PHYSICAL ? "Physical class" : "Online class";
    }
}
