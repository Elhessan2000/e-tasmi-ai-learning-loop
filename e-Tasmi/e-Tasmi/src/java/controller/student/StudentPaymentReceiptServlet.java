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
import model.entity.Instructor;
import model.entity.Payment;
import model.entity.PaymentStatus;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "StudentPaymentReceiptServlet", urlPatterns = {"/student/payments/receipt"})
public class StudentPaymentReceiptServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(StudentPaymentReceiptServlet.class.getName());
    private static final DateTimeFormatter RECEIPT_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a").withZone(ZoneId.systemDefault());

    private final StudentDao studentDao = new StudentDaoJdbc();
    private final UserDao userDao = new UserDaoJdbc();
    private final PaymentDao paymentDao = new PaymentDaoJdbc();
    private final EnrollmentDao enrollmentDao = new EnrollmentDaoJdbc();
    private final TasmiSessionDao sessionDao = new TasmiSessionDaoJdbc();
    private final InstructorDao instructorDao = new InstructorDaoJdbc();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        long userId = readUserId(request.getSession(false));
        long paymentId = readLong(request.getParameter("paymentId"));
        boolean download = "1".equals(request.getParameter("download"))
                || "true".equalsIgnoreCase(request.getParameter("download"));

        Optional<Map<String, Object>> receiptOpt = loadReceipt(userId, paymentId);
        if (receiptOpt.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Map<String, Object> receipt = receiptOpt.get();
        if (download) {
            writeReceiptPdf(response, receipt);
            return;
        }

        request.setAttribute("receipt", receipt);
        request.setAttribute("downloadMode", false);
        request.getRequestDispatcher("/jsp/student/payment_receipt.jsp").forward(request, response);
    }

    private void writeReceiptPdf(HttpServletResponse response, Map<String, Object> receipt) throws IOException {
        String referenceNumber = String.valueOf(receipt.get("referenceNumber"));
        byte[] pdf = buildReceiptPdf(receipt);
        String fileName = "e-tasmi-receipt-" + sanitizeForFilename(referenceNumber) + ".pdf";

        response.reset();
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        response.setContentLength(pdf.length);
        response.getOutputStream().write(pdf);
    }

    private byte[] buildReceiptPdf(Map<String, Object> receipt) throws IOException {
        Payment payment = (Payment) receipt.get("payment");
        TasmiSession tasmiSession = (TasmiSession) receipt.get("session");

        String referenceNumber = textValue(receipt.get("referenceNumber"), "-");
        String studentName = textValue(receipt.get("studentName"), "Student");
        String sessionName = tasmiSession == null || trimToNull(tasmiSession.getTitle()) == null
                ? "Session"
                : tasmiSession.getTitle().trim();
        String instructorName = textValue(receipt.get("instructorName"), "Instructor");
        String amount = amountText(payment == null ? null : payment.getAmount());
        String paymentDate = payment == null ? "-" : formatInstant(payment.getPaymentDate());
        String status = payment == null || payment.getPaymentStatus() == null
                ? "-"
                : paymentStatusLabel(payment.getPaymentStatus());

        StringBuilder stream = new StringBuilder();
        stream.append("q\n");
        stream.append("0.133 0.773 0.369 rg\n");
        stream.append("36 760 523 46 re f\n");
        stream.append("0.133 0.773 0.369 rg\n");
        stream.append("36 704 523 34 re f\n");
        stream.append("1 1 1 rg\n");
        stream.append("52 773 22 22 re f\n");
        stream.append("Q\n");

        pdfText(stream, "F2", 18, 58, 779, "e", "0.133 0.773 0.369");
        pdfText(stream, "F2", 24, 88, 779, "eTasmi", "1 1 1");
        pdfText(stream, "F1", 11, 88, 764, "Official Payment Receipt", "1 1 1");
        pdfText(stream, "F2", 18, 52, 714, "Payment Receipt", "1 1 1");
        pdfText(stream, "F1", 10, 400, 717, "Reference: " + referenceNumber, "1 1 1");

        pdfText(stream, "F1", 10, 52, 676, "Generated from real eTasmi payment records.", "0.392 0.455 0.545");

        drawSection(stream, 52, 578, "Student", new String[][]{
                {"Name", studentName},
                {"Payment Status", status}
        });
        drawSection(stream, 310, 578, "Payment", new String[][]{
                {"Amount", amount},
                {"Payment Date", paymentDate}
        });
        drawSection(stream, 52, 448, "Session", new String[][]{
                {"Session Name", sessionName},
                {"Instructor", instructorName},
                {"Session Date", tasmiSession == null || tasmiSession.getSessionDate() == null ? "-" : String.valueOf(tasmiSession.getSessionDate())}
        });

        stream.append("q\n");
        stream.append("0.949 0.984 0.961 rg\n");
        stream.append("52 278 491 58 re f\n");
        stream.append("0.898 0.922 0.965 RG\n");
        stream.append("52 278 491 58 re S\n");
        stream.append("Q\n");
        pdfText(stream, "F2", 13, 70, 313, "Total Paid", "0.392 0.455 0.545");
        pdfText(stream, "F2", 24, 70, 290, amount, "0.063 0.090 0.165");
        pdfText(stream, "F1", 10, 70, 252, "Thank you. This PDF confirms the payment record stored in your eTasmi account.", "0.392 0.455 0.545");

        return createSinglePagePdf(stream.toString());
    }

    private void drawSection(StringBuilder stream, int x, int y, String title, String[][] rows) {
        int width = x > 100 ? 233 : 491;
        int height = 42 + (rows.length * 28);
        stream.append("q\n");
        stream.append("1 1 1 rg\n");
        stream.append(x).append(' ').append(y).append(' ').append(width).append(' ').append(height).append(" re f\n");
        stream.append("0.898 0.922 0.965 RG\n");
        stream.append(x).append(' ').append(y).append(' ').append(width).append(' ').append(height).append(" re S\n");
        stream.append("Q\n");
        pdfText(stream, "F2", 13, x + 16, y + height - 25, title, "0.063 0.090 0.165");
        int rowY = y + height - 53;
        for (String[] row : rows) {
            pdfText(stream, "F1", 9, x + 16, rowY, row[0], "0.392 0.455 0.545");
            pdfText(stream, "F2", 10, x + 92, rowY, row[1], "0.063 0.090 0.165");
            rowY -= 28;
        }
    }

    private void pdfText(StringBuilder stream, String font, int size, int x, int y, String text, String rgb) {
        stream.append("BT\n");
        stream.append(rgb).append(" rg\n");
        stream.append('/').append(font).append(' ').append(size).append(" Tf\n");
        stream.append(x).append(' ').append(y).append(" Td\n");
        stream.append('(').append(escapePdfText(limitPdfText(text, 48))).append(") Tj\n");
        stream.append("ET\n");
    }

    private byte[] createSinglePagePdf(String content) throws IOException {
        byte[] contentBytes = content.getBytes(StandardCharsets.ISO_8859_1);
        List<byte[]> objects = new ArrayList<>();
        objects.add("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
        objects.add("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
        objects.add("3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R /F2 5 0 R >> >> /Contents 6 0 R >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
        objects.add("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
        objects.add("5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold >>\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
        objects.add(("6 0 obj\n<< /Length " + contentBytes.length + " >>\nstream\n" + content + "endstream\nendobj\n").getBytes(StandardCharsets.ISO_8859_1));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("%PDF-1.4\n".getBytes(StandardCharsets.ISO_8859_1));
        List<Integer> offsets = new ArrayList<>();
        for (byte[] object : objects) {
            offsets.add(out.size());
            out.write(object);
        }
        int xrefOffset = out.size();
        out.write(("xref\n0 " + (objects.size() + 1) + "\n").getBytes(StandardCharsets.ISO_8859_1));
        out.write("0000000000 65535 f \n".getBytes(StandardCharsets.ISO_8859_1));
        for (Integer offset : offsets) {
            out.write(String.format("%010d 00000 n \n", offset).getBytes(StandardCharsets.ISO_8859_1));
        }
        out.write(("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF").getBytes(StandardCharsets.ISO_8859_1));
        return out.toByteArray();
    }

    private Optional<Map<String, Object>> loadReceipt(long studentUserId, long paymentId) {
        if (studentUserId <= 0 || paymentId <= 0) {
            return Optional.empty();
        }

        try (Connection connection = Db.getConnection()) {
            Optional<Student> studentOpt = studentDao.findByUserId(connection, studentUserId);
            if (studentOpt.isEmpty()) {
                return Optional.empty();
            }

            Optional<Payment> paymentOpt = paymentDao.findById(connection, paymentId);
            if (paymentOpt.isEmpty()) {
                return Optional.empty();
            }

            Payment payment = paymentOpt.get();
            if (payment.getPaymentStatus() != PaymentStatus.APPROVED) {
                return Optional.empty();
            }

            BigDecimal amountPaid = payment.getAmount();
            if (amountPaid == null || amountPaid.compareTo(BigDecimal.ZERO) <= 0) {
                return Optional.empty();
            }

            Optional<Enrollment> enrollmentOpt = enrollmentDao.findById(connection, payment.getEnrollmentId());
            if (enrollmentOpt.isEmpty()) {
                return Optional.empty();
            }

            Enrollment enrollment = enrollmentOpt.get();
            Student student = studentOpt.get();
            if (enrollment.getStudentId() != student.getStudentId()) {
                return Optional.empty();
            }

            Optional<TasmiSession> sessionOpt = sessionDao.findById(connection, enrollment.getSessionId());
            if (sessionOpt.isEmpty()) {
                return Optional.empty();
            }

            Optional<User> studentUserOpt = userDao.findById(connection, student.getUserId());
            TasmiSession tasmiSession = sessionOpt.get();
            if (!isActiveInstructorSession(connection, tasmiSession)) {
                return Optional.empty();
            }

            String instructorName = "Instructor";
            if (tasmiSession.getInstructorId() > 0) {
                Optional<Instructor> instructorOpt = instructorDao.findById(connection, tasmiSession.getInstructorId());
                if (instructorOpt.isPresent()) {
                    Optional<User> instructorUserOpt = userDao.findById(connection, instructorOpt.get().getUserId());
                    if (instructorUserOpt.isPresent() && trimToNull(instructorUserOpt.get().getFullName()) != null) {
                        instructorName = instructorUserOpt.get().getFullName().trim();
                    } else {
                        instructorName = "Instructor #" + tasmiSession.getInstructorId();
                    }
                }
            }

            Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("payment", payment);
            receipt.put("enrollment", enrollment);
            receipt.put("session", tasmiSession);
            receipt.put("studentName", studentUserOpt.isPresent() && trimToNull(studentUserOpt.get().getFullName()) != null
                    ? studentUserOpt.get().getFullName().trim()
                    : "Student");
            receipt.put("studentEmail", studentUserOpt.isPresent() ? trimToNull(studentUserOpt.get().getEmail()) : null);
            receipt.put("instructorName", instructorName);
            receipt.put("referenceNumber", "ETP-" + payment.getPaymentId());
            receipt.put("amountPaid", amountPaid);
            receipt.put("sessionType", modeLabel(tasmiSession));
            return Optional.of(receipt);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to build student receipt", ex);
            return Optional.empty();
        }
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

    private String textValue(Object value, String fallback) {
        String text = value == null ? null : trimToNull(String.valueOf(value));
        return text == null ? fallback : text;
    }

    private String amountText(BigDecimal amount) {
        if (amount == null) {
            return "RM 0.00";
        }
        return "RM " + amount.stripTrailingZeros().toPlainString();
    }

    private String formatInstant(java.time.Instant instant) {
        if (instant == null) {
            return "-";
        }
        return RECEIPT_DATE_FORMAT.format(instant);
    }

    private String paymentStatusLabel(PaymentStatus status) {
        if (status == null) {
            return "-";
        }
        return status.displayLabel();
    }

    private String limitPdfText(String value, int maxLength) {
        String text = textValue(value, "-").replaceAll("[\\r\\n\\t]+", " ").trim();
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 3)).trim() + "...";
    }

    private String escapePdfText(String value) {
        StringBuilder escaped = new StringBuilder();
        String text = value == null ? "" : value;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '(' || ch == ')' || ch == '\\') {
                escaped.append('\\').append(ch);
            } else if (ch >= 32 && ch <= 126) {
                escaped.append(ch);
            } else {
                escaped.append('?');
            }
        }
        return escaped.toString();
    }

    private String sanitizeForFilename(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "receipt";
        }
        return normalized.replaceAll("[^A-Za-z0-9._-]+", "-");
    }

    private String modeLabel(TasmiSession tasmiSession) {
        if (tasmiSession == null || tasmiSession.getMode() == null) {
            return "Guided session";
        }
        return tasmiSession.getMode() == model.entity.SessionMode.PHYSICAL ? "Physical" : "Online";
    }
}
