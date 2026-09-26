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
import model.entity.PaymentStatus;
import model.entity.Student;
import model.entity.TasmiSession;
import model.entity.User;
import model.service.PaymentResult;
import model.service.PaymentService;
import model.service.EnrollmentService;
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

@WebServlet(name = "StudentPaymentsServlet", urlPatterns = {"/student/payments"})
public class StudentPaymentsServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(StudentPaymentsServlet.class.getName());

    private final PaymentService paymentService = new PaymentService();
    private final EnrollmentService enrollmentService = new EnrollmentService();
    private final StudentDao studentDao = new StudentDaoJdbc();
    private final EnrollmentDao enrollmentDao = new EnrollmentDaoJdbc();
    private final PaymentDao paymentDao = new PaymentDaoJdbc();
    private final TasmiSessionDao sessionDao = new TasmiSessionDaoJdbc();
    private final InstructorDao instructorDao = new InstructorDaoJdbc();
    private final UserDao userDao = new UserDaoJdbc();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = readUserId(session);

        applyPaymentHistory(request, userId);
        applyNotice(request);

        request.getRequestDispatcher("/jsp/student/payments.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = readUserId(session);
        long enrollmentId = readLong(request.getParameter("enrollmentId"));
        String action = trimToNull(request.getParameter("action"));

        if ("cancel".equalsIgnoreCase(action)) {
            boolean cancelled = enrollmentService.withdrawEnrollment(userId, enrollmentId);
            if (cancelled) {
                response.sendRedirect(request.getContextPath() + "/student/sessions?notice=payment_cancelled");
                return;
            }

            applyPaymentHistory(request, userId);
            request.setAttribute("error", "Unable to cancel this enrollment right now.");
            request.getRequestDispatcher("/jsp/student/payments.jsp").forward(request, response);
            return;
        }

        PaymentResult result = paymentService.beginPaymentFlow(userId, enrollmentId);
        if (result.isSuccess()) {
            if ("checkout_ready".equals(result.getCode())
                    || "checkout_retry_ready".equals(result.getCode())
                    || "checkout_pending".equals(result.getCode())) {
                response.sendRedirect(request.getContextPath()
                        + "/student/payments/qr?notice=" + safe(result.getCode())
                        + "&enrollmentId=" + enrollmentId);
                return;
            }
            response.sendRedirect(request.getContextPath()
                    + "/student/payments?notice=" + safe(result.getCode()));
            return;
        }

        applyPaymentHistory(request, userId);
        request.setAttribute("error", result.getError());
        request.getRequestDispatcher("/jsp/student/payments.jsp").forward(request, response);
    }

    private void applyNotice(HttpServletRequest request) {
        String notice = trimToNull(request.getParameter("notice"));
        if (notice == null) {
            return;
        }

        switch (notice) {
            case "already_paid":
                request.setAttribute("success", "This session is already paid and recorded in your history.");
                break;
            case "receipt_submitted":
            case "receipt_resubmitted":
                request.setAttribute("success", "Payment receipt submitted. Please wait for instructor verification.");
                break;
            case "payment_approved":
                request.setAttribute("success", "Payment approved. Your session enrollment is confirmed.");
                break;
            case "payment_rejected":
                request.setAttribute("error", "Payment receipt rejected. You can submit a new receipt from the payment page.");
                break;
            case "qr_checkout_unavailable":
            case "payment_setup_missing":
                request.setAttribute("error", "This instructor has not set up their QR payment yet. Please try again later.");
                break;
            default:
                break;
        }
    }

    private void applyPaymentHistory(HttpServletRequest request, long studentUserId) {
        request.setAttribute("historyCards", buildPaymentHistoryCards(studentUserId));
    }

    private List<Map<String, Object>> buildPaymentHistoryCards(long studentUserId) {
        if (studentUserId <= 0) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> cards = new ArrayList<>();
        try (Connection connection = Db.getConnection()) {
            Optional<Student> studentOpt = studentDao.findByUserId(connection, studentUserId);
            if (studentOpt.isEmpty()) {
                return Collections.emptyList();
            }

            Map<Long, String> instructorNames = new HashMap<>();
            List<Enrollment> enrollments = enrollmentDao.listByStudentId(connection, studentOpt.get().getStudentId());
            for (Enrollment enrollment : enrollments) {
                if (enrollment == null) {
                    continue;
                }

                Optional<TasmiSession> sessionOpt = sessionDao.findById(connection, enrollment.getSessionId());
                if (sessionOpt.isEmpty()) {
                    continue;
                }

                TasmiSession tasmiSession = sessionOpt.get();
                if (!isActiveInstructorSession(connection, tasmiSession)) {
                    continue;
                }
                Payment payment = paymentDao.findByEnrollmentId(connection, enrollment.getEnrollmentId()).orElse(null);
                if (!isPaymentRecord(payment, tasmiSession)) {
                    continue;
                }

                Map<String, Object> card = new LinkedHashMap<>();
                card.put("enrollment", enrollment);
                card.put("session", tasmiSession);
                card.put("payment", payment);
                card.put("instructorName", resolveInstructorName(connection, instructorNames, tasmiSession));
                card.put("amountPaid", payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount());
                card.put("referenceNumber", buildReferenceNumber(payment));
                cards.add(card);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to build payment history", ex);
        }

        cards.sort((left, right) -> {
            Payment leftPayment = (Payment) left.get("payment");
            Payment rightPayment = (Payment) right.get("payment");
            if (leftPayment == null && rightPayment == null) {
                return 0;
            }
            if (leftPayment == null) {
                return 1;
            }
            if (rightPayment == null) {
                return -1;
            }

            if (leftPayment.getPaymentDate() != null && rightPayment.getPaymentDate() != null) {
                int dateCompare = rightPayment.getPaymentDate().compareTo(leftPayment.getPaymentDate());
                if (dateCompare != 0) {
                    return dateCompare;
                }
            } else if (leftPayment.getPaymentDate() == null && rightPayment.getPaymentDate() != null) {
                return 1;
            } else if (leftPayment.getPaymentDate() != null) {
                return -1;
            }

            return Long.compare(rightPayment.getPaymentId(), leftPayment.getPaymentId());
        });
        return cards;
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

    private boolean isPaymentRecord(Payment payment, TasmiSession session) {
        if (payment == null) {
            return false;
        }
        BigDecimal amount = payment.getAmount();
        if (amount == null && session != null) {
            amount = session.getFee();
        }
        return amount != null && amount.compareTo(BigDecimal.ZERO) > 0;
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

    private String buildReferenceNumber(Payment payment) {
        return payment == null ? null : ("ETP-" + payment.getPaymentId());
    }

    private long readLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String shorten(String text, int max) {
        String normalized = trimToNull(text);
        if (normalized == null || normalized.length() <= max) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, max - 3)).trim() + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
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
