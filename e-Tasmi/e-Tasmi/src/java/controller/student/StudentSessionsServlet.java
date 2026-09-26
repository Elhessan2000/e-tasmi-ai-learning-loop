package controller.student;

import model.dao.EnrollmentDao;
import model.dao.InstructorDao;
import model.dao.PaymentDao;
import model.dao.StudentDao;
import model.dao.UserDao;
import model.dao.impl.EnrollmentDaoJdbc;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.PaymentDaoJdbc;
import model.dao.impl.StudentDaoJdbc;
import model.dao.impl.UserDaoJdbc;
import model.entity.Enrollment;
import model.entity.EnrollmentStatus;
import model.entity.Payment;
import model.entity.PaymentStatus;
import model.entity.Student;
import model.entity.TasmiSession;
import model.entity.TasmiSessionStatus;
import model.entity.User;
import model.service.EnrollmentResult;
import model.service.EnrollmentService;
import model.service.PaymentResult;
import model.service.PaymentService;
import model.service.TasmiSessionService;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "StudentSessionsServlet", urlPatterns = {"/student/sessions", "/student/available-sessions", "/student/enroll-confirm"})
public class StudentSessionsServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(StudentSessionsServlet.class.getName());
    private static final String LIST_JSP = "/jsp/student/sessions.jsp";
    private static final String CONFIRM_JSP = "/jsp/student/session_confirm.jsp";
    private static final String LIST_PATH = "/student/available-sessions";
    private static final String CONFIRM_PATH = "/student/enroll-confirm";

    private final TasmiSessionService tasmiSessionService = new TasmiSessionService();
    private final EnrollmentService enrollmentService = new EnrollmentService();
    private final PaymentService paymentService = new PaymentService();

    private final StudentDao studentDao = new StudentDaoJdbc();
    private final EnrollmentDao enrollmentDao = new EnrollmentDaoJdbc();
    private final PaymentDao paymentDao = new PaymentDaoJdbc();
    private final InstructorDao instructorDao = new InstructorDaoJdbc();
    private final UserDao userDao = new UserDaoJdbc();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession httpSession = request.getSession(false);
        long userId = readUserId(httpSession);
        String searchQuery = trimToNull(request.getParameter("q"));
        long focusSessionId = readLong(request.getParameter("sessionId"));
        String servletPath = request.getServletPath();

        if (!CONFIRM_PATH.equals(servletPath) && focusSessionId > 0) {
            response.sendRedirect(request.getContextPath() + buildConfirmLocation(focusSessionId, searchQuery, trimToNull(request.getParameter("from"))));
            return;
        }

        if (CONFIRM_PATH.equals(servletPath)) {
            prepareConfirmPage(request, userId, searchQuery, focusSessionId);
            if (request.getAttribute("selectedSessionCard") == null) {
                response.sendRedirect(request.getContextPath() + LIST_PATH);
                return;
            }
            applyNotice(request);
            // Skip session_confirm.jsp: same path as POST checkout → QR method selection (or enrollments / errors).
            handleCheckout(request, response, userId, focusSessionId);
            return;
        }

        prepareListPage(request, userId, searchQuery);
        applyNotice(request);
        request.getRequestDispatcher(LIST_JSP).forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = readUserId(session);
        String action = trimToNull(request.getParameter("action"));

        long sessionId = 0;
        try {
            sessionId = Long.parseLong(request.getParameter("sessionId"));
        } catch (Exception ignored) {
        }

        if (sessionId <= 0) {
            request.setAttribute("error", "Please select a session first.");
            prepareListPage(request, userId, trimToNull(request.getParameter("q")));
            request.getRequestDispatcher(LIST_JSP).forward(request, response);
            return;
        }

        if (action == null || "checkout".equalsIgnoreCase(action)) {
            handleCheckout(request, response, userId, sessionId);
            return;
        }
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
    }

    private void applyNotice(HttpServletRequest request) {
        String notice = trimToNull(request.getParameter("notice"));
        if (notice == null) {
            return;
        }

        switch (notice) {
            case "payment_required":
                request.setAttribute("success", "Enrollment created. Continue to payment confirmation to complete your registration.");
                break;
            case "reactivated_payment_required":
                request.setAttribute("success", "Your enrollment was reopened. Continue to payment confirmation when you are ready.");
                break;
            case "already_enrolled_payment_required":
                request.setAttribute("success", "You already have a registration for this session. Continue the payment step to finish confirming it.");
                break;
            case "free_session":
                request.setAttribute("success", "Free session reserved. It will be confirmed automatically.");
                break;
            case "payment_cancelled":
                request.setAttribute("success", "Enrollment cancelled before checkout. You can browse available sessions again whenever you are ready.");
                break;
            case "qr_checkout_unavailable":
                request.setAttribute("error", "Online payment is not available right now. Please try again later.");
                break;
            case "payment_setup_missing":
                request.setAttribute("error", "The instructor has not set up payment details yet. Please contact them or try again later.");
                break;
            default:
                break;
        }
    }

    private void handleCheckout(HttpServletRequest request, HttpServletResponse response, long userId, long sessionId) throws ServletException, IOException {
        String searchQuery = trimToNull(request.getParameter("q"));
        String from = trimToNull(request.getParameter("from"));
        EnrollmentResult result = enrollmentService.enroll(userId, sessionId);
        if (!result.isSuccess()) {
            request.setAttribute("error", result.getError());
            prepareConfirmPage(request, userId, searchQuery, sessionId);
            request.setAttribute("from", from);
            request.getRequestDispatcher(CONFIRM_JSP).forward(request, response);
            return;
        }

        if (!result.isPaymentRequired()) {
            PaymentResult freeResult = paymentService.confirmFreeEnrollment(userId, result.getEnrollmentId());
            if (freeResult.isSuccess()) {
                response.sendRedirect(request.getContextPath()
                        + "/student/enrollments?notice=" + safe(freeResult.getCode())
                        + "&enrollmentId=" + result.getEnrollmentId());
                return;
            }

            request.setAttribute("error", freeResult.getError());
            prepareConfirmPage(request, userId, searchQuery, sessionId);
            request.setAttribute("from", from);
            request.getRequestDispatcher(CONFIRM_JSP).forward(request, response);
            return;
        }

        PaymentResult paymentResult = paymentService.beginPaymentFlow(userId, result.getEnrollmentId());
        if (paymentResult.isSuccess()) {
            String code = safe(paymentResult.getCode());
            if ("checkout_ready".equals(code) || "checkout_retry_ready".equals(code) || "checkout_pending".equals(code)) {
                response.sendRedirect(request.getContextPath()
                        + "/student/payments/qr?notice=" + code
                        + "&enrollmentId=" + result.getEnrollmentId());
                return;
            }
            if ("already_paid".equals(code)) {
                response.sendRedirect(request.getContextPath()
                        + "/student/enrollments?notice=" + code
                        + "&enrollmentId=" + result.getEnrollmentId());
                return;
            }
            response.sendRedirect(request.getContextPath()
                    + "/student/payments?notice=" + code
                    + "&enrollmentId=" + result.getEnrollmentId());
            return;
        }

        request.setAttribute("error", paymentResult.getError());
        prepareConfirmPage(request, userId, searchQuery, sessionId);
        request.setAttribute("from", from);
        request.getRequestDispatcher(CONFIRM_JSP).forward(request, response);
    }

    private void prepareListPage(HttpServletRequest request, long studentUserId, String searchQuery) {
        List<TasmiSession> sessions = tasmiSessionService.listStudentScheduledSessions();
        sessions = filterByStudentLevel(studentUserId, sessions);
        List<Map<String, Object>> sessionCards = buildSessionCards(studentUserId, sessions);
        request.setAttribute("sessionCards", filterSessionCards(sessionCards, searchQuery));
        request.setAttribute("searchQuery", searchQuery == null ? "" : searchQuery);
    }

    private void prepareConfirmPage(HttpServletRequest request, long studentUserId, String searchQuery, long focusSessionId) {
        List<TasmiSession> sessions = tasmiSessionService.listStudentScheduledSessions();
        sessions = filterByStudentLevel(studentUserId, sessions);
        List<Map<String, Object>> sessionCards = buildSessionCards(studentUserId, sessions);
        request.setAttribute("selectedSessionCard", findCardBySessionId(sessionCards, focusSessionId));
        request.setAttribute("selectedSessionId", focusSessionId);
        request.setAttribute("searchQuery", searchQuery == null ? "" : searchQuery);
        request.setAttribute("from", trimToNull(request.getParameter("from")));
    }

    /**
     * Available Sessions list. Shows scheduled sessions the student could enroll in, plus sessions where the
     * student already submitted payment but is still waiting for instructor verification (kept visible so the
     * student can retry/track). Fully confirmed sessions (approved + paid) are excluded — they live in My Sessions.
     */
    private List<Map<String, Object>> buildSessionCards(long studentUserId, List<TasmiSession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> cards = new ArrayList<>();
        try (Connection connection = Db.getConnection()) {
            Map<Long, Enrollment> enrollmentsBySessionId = new HashMap<>();
            Map<Long, Payment> paymentsByEnrollmentId = new HashMap<>();
            Map<Long, String> instructorNames = new HashMap<>();

            Optional<Student> studentOpt = studentUserId > 0 ? studentDao.findByUserId(connection, studentUserId) : Optional.empty();
            if (studentOpt.isPresent()) {
                for (Enrollment enrollment : enrollmentDao.listByStudentId(connection, studentOpt.get().getStudentId())) {
                    if (enrollment == null) {
                        continue;
                    }
                    enrollmentsBySessionId.put(enrollment.getSessionId(), enrollment);
                    paymentDao.findByEnrollmentId(connection, enrollment.getEnrollmentId())
                            .ifPresent(payment -> paymentsByEnrollmentId.put(enrollment.getEnrollmentId(), payment));
                }
            }

            for (TasmiSession session : sessions) {
                if (session == null) {
                    continue;
                }

                Enrollment enrollment = enrollmentsBySessionId.get(session.getSessionId());
                Payment payment = enrollment == null ? null : paymentsByEnrollmentId.get(enrollment.getEnrollmentId());

                if (isConfirmedForStudent(session, enrollment, payment)) {
                    continue;
                }

                boolean pendingVerification = isPendingPaymentVerification(session, enrollment, payment);

                Map<String, Object> card = new LinkedHashMap<>();
                int enrolledCount = enrollmentDao.countActiveBySessionId(connection, session.getSessionId());
                int availableSeats = session.getCapacity() > 0 ? Math.max(0, session.getCapacity() - enrolledCount) : 0;

                card.put("session", session);
                card.put("enrollment", enrollment);
                card.put("payment", payment);
                card.put("enrolledCount", enrolledCount);
                card.put("availableSeats", availableSeats);
                card.put("paymentRequired", requiresPayment(session));
                card.put("instructorName", resolveInstructorName(connection, instructorNames, session));
                card.put("descriptionSnippet", shorten(session.getDescription(), 140));
                card.put("sessionModeLabel", modeLabel(session));
                card.put("feeLabel", requiresPayment(session) && session.getFee() != null
                        ? "RM " + session.getFee().stripTrailingZeros().toPlainString()
                        : "Free session");
                card.put("pendingVerification", pendingVerification);

                cards.add(card);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to build student session cards", ex);
        }

        return cards;
    }

    /**
     * Payment receipt submitted and still awaiting instructor verification: requires a real enrollment row.
     */
    private boolean isPendingPaymentVerification(TasmiSession session, Enrollment enrollment, Payment payment) {
        if (session == null || session.getStatus() == TasmiSessionStatus.CANCELLED) {
            return false;
        }
        if (enrollment == null) {
            return false;
        }
        if (enrollment.getEnrollmentStatus() == EnrollmentStatus.CANCELLED
                || enrollment.getEnrollmentStatus() == EnrollmentStatus.REJECTED) {
            return false;
        }
        if (!requiresPayment(session)) {
            return false;
        }
        if (payment == null) {
            return false;
        }
        PaymentStatus paymentStatus = payment.getPaymentStatus();
        // Receipt uploaded and awaiting instructor review → the card must reflect
        // "Pending Verification" and stop offering another payment submission.
        if (paymentStatus == PaymentStatus.AWAITING_VERIFICATION) {
            return true;
        }
        // Defensive: a PENDING record that already carries a receipt also counts as pending.
        if (paymentStatus == PaymentStatus.PENDING) {
            return trimToNull(payment.getReceiptFilePath()) != null;
        }
        return false;
    }

    private List<TasmiSession> filterByStudentLevel(long studentUserId, List<TasmiSession> sessions) {
        if (studentUserId <= 0 || sessions == null || sessions.isEmpty()) {
            return sessions;
        }
        try (Connection connection = Db.getConnection()) {
            Optional<Student> studentOpt = studentDao.findByUserId(connection, studentUserId);
            if (studentOpt.isEmpty() || studentOpt.get().getLevel() == null) {
                return sessions;
            }
            model.entity.StudentLevel studentLevel = studentOpt.get().getLevel();
            List<TasmiSession> filtered = new ArrayList<>();
            for (TasmiSession s : sessions) {
                if (s != null && (s.getLevel() == null || s.getLevel() == studentLevel)) {
                    filtered.add(s);
                }
            }
            return filtered;
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to filter sessions by student level", ex);
            return sessions;
        }
    }

    private List<Map<String, Object>> filterSessionCards(List<Map<String, Object>> sessionCards, String query) {
        if (sessionCards == null || sessionCards.isEmpty()) {
            return Collections.emptyList();
        }
        String normalizedQuery = trimToNull(query);
        if (normalizedQuery == null) {
            return sessionCards;
        }

        String needle = normalizedQuery.toLowerCase();
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> card : sessionCards) {
            if (matchesSearch(card, needle)) {
                filtered.add(card);
            }
        }
        return filtered;
    }

    private Map<String, Object> findCardBySessionId(List<Map<String, Object>> sessionCards, long sessionId) {
        if (sessionCards == null || sessionCards.isEmpty() || sessionId <= 0) {
            return null;
        }
        for (Map<String, Object> card : sessionCards) {
            TasmiSession session = card == null ? null : (TasmiSession) card.get("session");
            if (session != null && session.getSessionId() == sessionId) {
                return card;
            }
        }
        return null;
    }

    private boolean matchesSearch(Map<String, Object> card, String needle) {
        if (card == null || needle == null || needle.isEmpty()) {
            return true;
        }

        TasmiSession session = (TasmiSession) card.get("session");
        return contains(session == null ? null : session.getTitle(), needle)
                || contains(session == null ? null : session.getQuranPortion(), needle)
                || contains(session == null || session.getMode() == null ? null : session.getMode().name(), needle)
                || contains(card.get("instructorName") == null ? null : String.valueOf(card.get("instructorName")), needle)
                || contains(card.get("descriptionSnippet") == null ? null : String.valueOf(card.get("descriptionSnippet")), needle);
    }

    private boolean contains(String value, String needle) {
        String normalized = trimToNull(value);
        return normalized != null && normalized.toLowerCase().contains(needle);
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

    private boolean isConfirmedForStudent(TasmiSession session, Enrollment enrollment, Payment payment) {
        if (session == null || enrollment == null) {
            return false;
        }
        if (enrollment.getEnrollmentStatus() != EnrollmentStatus.APPROVED) {
            return false;
        }
        return !requiresPayment(session)
                || (payment != null && payment.getPaymentStatus() == PaymentStatus.APPROVED);
    }

    private String modeLabel(TasmiSession session) {
        if (session == null || session.getMode() == null) {
            return "Guided session";
        }
        return session.getMode() == model.entity.SessionMode.PHYSICAL ? "Physical" : "Online";
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String buildConfirmLocation(long sessionId, String searchQuery, String from) {
        StringBuilder location = new StringBuilder(CONFIRM_PATH)
                .append("?sessionId=").append(sessionId);

        String normalizedQuery = trimToNull(searchQuery);
        if (normalizedQuery != null) {
            location.append("&q=").append(urlEncode(normalizedQuery));
        }

        String normalizedFrom = trimToNull(from);
        if (normalizedFrom != null) {
            location.append("&from=").append(urlEncode(normalizedFrom));
        }

        return location.toString();
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception ex) {
            return "";
        }
    }

    private long readLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String initials(String value) {
        if (value == null) {
            return "IN";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "IN";
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        String first = parts[0].substring(0, 1).toUpperCase();
        String last = parts[parts.length - 1].substring(0, 1).toUpperCase();
        return (first + last).trim();
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
