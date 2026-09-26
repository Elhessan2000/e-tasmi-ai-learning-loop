package controller.student;

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
import model.entity.Payment;
import model.entity.Student;
import model.entity.TasmiSession;
import model.entity.User;
import util.Db;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "StudentEnrollmentsServlet", urlPatterns = {"/student/enrollments"})
public class StudentEnrollmentsServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(StudentEnrollmentsServlet.class.getName());

    private final StudentDao studentDao = new StudentDaoJdbc();
    private final EnrollmentDao enrollmentDao = new EnrollmentDaoJdbc();
    private final PaymentDao paymentDao = new PaymentDaoJdbc();
    private final TasmiSessionDao sessionDao = new TasmiSessionDaoJdbc();
    private final InstructorDao instructorDao = new InstructorDaoJdbc();
    private final UserDao userDao = new UserDaoJdbc();
    private final model.service.EnrollmentService enrollmentService = new model.service.EnrollmentService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = readUserId(session);

        request.setAttribute("enrollmentCards", loadEnrollmentCards(userId));
        applyNotice(request);
        request.getRequestDispatcher("/jsp/student/enrollments.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String action = request.getParameter("action");
        if (!"withdraw".equals(action)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        long enrollmentId = 0;
        try {
            enrollmentId = Long.parseLong(request.getParameter("enrollmentId"));
        } catch (Exception ignored) {
        }

        HttpSession session = request.getSession(false);
        long userId = readUserId(session);
        boolean success = enrollmentService.withdrawEnrollment(userId, enrollmentId);

        request.setAttribute("enrollmentCards", loadEnrollmentCards(userId));
        if (success) {
            request.setAttribute("success", "Enrollment withdrawn successfully.");
        } else {
            request.setAttribute("error", "Unable to withdraw this enrollment right now.");
        }
        request.getRequestDispatcher("/jsp/student/enrollments.jsp").forward(request, response);
    }

    private void applyNotice(HttpServletRequest request) {
        String notice = trimToNull(request.getParameter("notice"));
        if (notice == null) {
            return;
        }

        switch (notice) {
            case "free_confirmed":
                request.setAttribute("success", "Free session confirmed. You can now manage it from your schedule.");
                break;
            case "already_paid":
                request.setAttribute("success", "This session is already paid and confirmed.");
                break;
            case "checkout_ready":
            case "checkout_retry_ready":
            case "checkout_pending":
                request.setAttribute("success", "Payment status updated. Open the payment page to continue.");
                break;
            default:
                break;
        }
    }

    private List<Map<String, Object>> loadEnrollmentCards(long studentUserId) {
        if (studentUserId <= 0) {
            return Collections.emptyList();
        }

        try (Connection connection = Db.getConnection()) {
            Optional<Student> studentOpt = studentDao.findByUserId(connection, studentUserId);
            if (studentOpt.isEmpty()) {
                return Collections.emptyList();
            }

            Map<Long, String> instructorNames = new HashMap<>();
            List<Enrollment> enrollments = enrollmentDao.listByStudentId(connection, studentOpt.get().getStudentId());
            List<Map<String, Object>> cards = new ArrayList<>();

            for (Enrollment enrollment : enrollments) {
                if (enrollment == null) {
                    continue;
                }

                Optional<TasmiSession> sessionOpt = sessionDao.findById(connection, enrollment.getSessionId());
                if (sessionOpt.isEmpty()) {
                    continue;
                }

                TasmiSession session = sessionOpt.get();
                if (!isActiveInstructorSession(connection, session)) {
                    continue;
                }
                Payment payment = paymentDao.findByEnrollmentId(connection, enrollment.getEnrollmentId()).orElse(null);
                Map<String, Object> card = new LinkedHashMap<>();
                card.put("enrollment", enrollment);
                card.put("session", session);
                card.put("payment", payment);
                card.put("instructorName", resolveInstructorName(connection, instructorNames, session));
                card.put("descriptionSnippet", shorten(session.getDescription(), 140));
                card.put("paymentRequired", requiresPayment(session));
                card.put("joinReady", isJoinReady(enrollment, session, payment));
                if (isVisibleEnrollment(enrollment, session, payment)) {
                    cards.add(card);
                }
            }

            cards.sort(Comparator
                    .comparing((Map<String, Object> card) -> {
                        TasmiSession session = (TasmiSession) card.get("session");
                        return session == null ? null : session.getSessionDate();
                    }, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(card -> {
                        TasmiSession session = (TasmiSession) card.get("session");
                        return session == null ? null : session.getSessionTime();
                    }, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(card -> {
                        TasmiSession session = (TasmiSession) card.get("session");
                        return session == null ? 0L : session.getSessionId();
                    }));
            return cards;
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load enrollment cards", ex);
            return Collections.emptyList();
        }
    }

    private boolean isVisibleEnrollment(Enrollment enrollment, TasmiSession session, Payment payment) {
        if (enrollment == null || session == null) {
            return false;
        }
        if (session.getStatus() == model.entity.TasmiSessionStatus.CANCELLED) {
            return false;
        }
        if (enrollment.getEnrollmentStatus() != model.entity.EnrollmentStatus.APPROVED) {
            return false;
        }
        if (!requiresPayment(session)) {
            return true;
        }
        return payment != null && payment.getPaymentStatus() == model.entity.PaymentStatus.APPROVED;
    }

    private boolean isJoinReady(Enrollment enrollment, TasmiSession session, Payment payment) {
        if (enrollment == null || session == null) {
            return false;
        }

        if (enrollment.getEnrollmentStatus() != model.entity.EnrollmentStatus.APPROVED) {
            return false;
        }

        boolean paymentRequired = requiresPayment(session);
        if (paymentRequired && (payment == null || payment.getPaymentStatus() != model.entity.PaymentStatus.APPROVED)) {
            return false;
        }

        return hasUsableLiveBundle(session);
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
                String verificationStatus = trimToNull(rs.getString("verification_status"));
                String userStatus = trimToNull(rs.getString("status"));
                return rs.getBoolean("is_active")
                        && "APPROVED".equalsIgnoreCase(verificationStatus)
                        && !"DELETED".equalsIgnoreCase(userStatus);
            }
        }
    }

    private boolean hasUsableLiveBundle(TasmiSession session) {
        if (session == null) {
            return false;
        }
        String provider = trimToNull(session.getLiveProvider());
        boolean validProvider = "MANUAL".equalsIgnoreCase(provider)
                || "ZOOM".equalsIgnoreCase(provider);
        String joinUrl = trimToNull(session.getResolvableParticipantJoinUrl());
        return validProvider
                && joinUrl != null
                && (joinUrl.startsWith("http://") || joinUrl.startsWith("https://"));
    }

    private String resolveInstructorName(Connection connection, Map<Long, String> cache, TasmiSession session) throws SQLException {
        if (session == null || session.getInstructorId() <= 0) {
            return "Instructor";
        }

        String cached = cache.get(session.getInstructorId());
        if (cached != null) {
            return cached;
        }

        String resolved = "Instructor";
        Optional<model.entity.Instructor> instructorOpt = instructorDao.findById(connection, session.getInstructorId());
        if (instructorOpt.isPresent()) {
            Optional<User> userOpt = userDao.findById(connection, instructorOpt.get().getUserId());
            if (userOpt.isPresent() && trimToNull(userOpt.get().getFullName()) != null) {
                resolved = userOpt.get().getFullName().trim();
            } else {
                resolved = "Instructor #" + session.getInstructorId();
            }
        }

        cache.put(session.getInstructorId(), resolved);
        return resolved;
    }

    private boolean requiresPayment(TasmiSession session) {
        return session != null
                && session.getFee() != null
                && session.getFee().compareTo(BigDecimal.ZERO) > 0;
    }

    private String shorten(String text, int max) {
        String normalized = trimToNull(text);
        if (normalized == null || normalized.length() <= max) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, max - 3)).trim() + "...";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
}
