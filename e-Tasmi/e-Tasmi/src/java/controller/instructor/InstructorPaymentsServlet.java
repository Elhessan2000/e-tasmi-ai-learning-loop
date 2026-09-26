package controller.instructor;

import model.dao.InstructorDao;
import model.dao.InstructorPaymentSettingsDao;
import model.dao.PaymentDao;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.InstructorPaymentSettingsDaoJdbc;
import model.dao.impl.PaymentDaoJdbc;
import model.entity.Instructor;
import model.entity.InstructorPaymentSettings;
import model.entity.PaymentQueryFilter;
import model.entity.PaymentStats;
import model.entity.PaymentStatus;
import model.entity.PaymentTransactionRow;
import model.entity.RevenueBucket;
import model.service.PaymentResult;
import model.service.PaymentService;
import util.CloudinaryUtil;
import util.Db;
import util.LocalFileUtil;
import util.LocaleSupport;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Unified instructor payments dashboard — configuration, stats, and verification in one page.
 */
@WebServlet(name = "InstructorPaymentsServlet", urlPatterns = {"/instructor/payments"})
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = 8L * 1024L * 1024L,
        maxRequestSize = 10L * 1024L * 1024L
)
public class InstructorPaymentsServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(InstructorPaymentsServlet.class.getName());
    private static final long MAX_QR_BYTES = 8L * 1024L * 1024L;

    private final InstructorDao instructorDao = new InstructorDaoJdbc();
    private final InstructorPaymentSettingsDao settingsDao = new InstructorPaymentSettingsDaoJdbc();
    private final PaymentDao paymentDao = new PaymentDaoJdbc();
    private final PaymentService paymentService = new PaymentService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        long userId = readUserId(request.getSession(false));
        renderPage(request, response, userId);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        long userId = readUserId(request.getSession(false));
        String action = trimToNull(request.getParameter("action"));
        long sessionId = readLong(request.getParameter("sessionId"));

        if ("saveSettings".equalsIgnoreCase(action)) {
            handleSaveSettings(request, response, userId, sessionId);
            return;
        }

        if (!"approve".equalsIgnoreCase(action) && !"reject".equalsIgnoreCase(action)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        long paymentId = readLong(request.getParameter("paymentId"));
        boolean approve = "approve".equalsIgnoreCase(action);
        String reason = trimToNull(request.getParameter("reason"));
        PaymentResult result = paymentService.verifyInstructorPayment(userId, paymentId, approve, reason);

        String notice = result.isSuccess() ? result.getCode() : "verify_error";
        StringBuilder redirect = new StringBuilder("/instructor/payments?notice=").append(safe(notice));
        if (sessionId > 0) {
            redirect.append("&sessionId=").append(sessionId);
        }
        if (!result.isSuccess()) {
            redirect.append("&errorMessage=").append(urlEncode(result.getError()));
        }
        response.sendRedirect(LocaleSupport.localizedUrl(request, redirect.toString()));
    }

    private void handleSaveSettings(HttpServletRequest request, HttpServletResponse response, long userId, long sessionId)
            throws ServletException, IOException {
        Optional<Instructor> instructorOpt = loadInstructor(userId);
        if (instructorOpt.isEmpty()) {
            response.sendRedirect(LocaleSupport.localizedUrl(request, buildPaymentsUrl(sessionId, "settings_error",
                    urlEncode("Instructor profile not found."))));
            return;
        }
        Instructor instructor = instructorOpt.get();

        String bankName = trimToNull(request.getParameter("bankName"));
        String accountHolder = trimToNull(request.getParameter("accountHolderName"));

        InstructorPaymentSettings existing;
        try (Connection connection = Db.getConnection()) {
            existing = settingsDao.findByInstructorId(connection, instructor.getInstructorId()).orElse(null);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load payment settings", ex);
            response.sendRedirect(LocaleSupport.localizedUrl(request, buildPaymentsUrl(sessionId, "settings_error",
                    urlEncode("Could not load your current settings."))));
            return;
        }

        String existingQr = existing == null ? null : existing.getQrImageUrl();
        boolean removeQr = "1".equals(request.getParameter("removeQr"));

        Part qrPart = null;
        try {
            qrPart = request.getPart("qrImage");
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to read QR image part", ex);
        }

        String qrUrl = existingQr;
        String newlyUploaded = null;
        if (qrPart != null && qrPart.getSize() > 0) {
            String validationError = validateQrPart(qrPart);
            if (validationError != null) {
                response.sendRedirect(LocaleSupport.localizedUrl(request, buildPaymentsUrl(sessionId, "settings_error",
                        urlEncode(validationError))));
                return;
            }
            try {
                newlyUploaded = saveQr(qrPart);
                qrUrl = newlyUploaded;
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "Failed to store QR image", ex);
                response.sendRedirect(LocaleSupport.localizedUrl(request, buildPaymentsUrl(sessionId, "settings_error",
                        urlEncode("We could not save your QR image."))));
                return;
            }
        } else if (removeQr) {
            qrUrl = null;
        }

        boolean hasBank = bankName != null && accountHolder != null;
        boolean hasQr = trimToNull(qrUrl) != null;
        if (!hasBank && !hasQr) {
            if (newlyUploaded != null) {
                deleteStoredUploadQuietly(request, newlyUploaded);
            }
            response.sendRedirect(LocaleSupport.localizedUrl(request, buildPaymentsUrl(sessionId, "settings_error",
                    urlEncode("Add a QR image or provide bank name and account holder name."))));
            return;
        }

        InstructorPaymentSettings settings = new InstructorPaymentSettings();
        settings.setInstructorId(instructor.getInstructorId());
        settings.setQrImageUrl(qrUrl);
        settings.setBankName(bankName);
        settings.setAccountHolderName(accountHolder);
        settings.setAccountNumber(existing == null ? null : existing.getAccountNumber());
        settings.setPaymentNotes(existing == null ? null : existing.getPaymentNotes());
        settings.setActive(true);

        try (Connection connection = Db.getConnection()) {
            settingsDao.upsert(connection, settings);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to save payment settings", ex);
            if (newlyUploaded != null) {
                deleteStoredUploadQuietly(request, newlyUploaded);
            }
            response.sendRedirect(LocaleSupport.localizedUrl(request, buildPaymentsUrl(sessionId, "settings_error",
                    urlEncode("Your configuration could not be saved."))));
            return;
        }

        if (newlyUploaded != null && existingQr != null && !existingQr.equals(newlyUploaded)) {
            deleteStoredUploadQuietly(request, existingQr);
        }

        response.sendRedirect(LocaleSupport.localizedUrl(request, buildPaymentsUrl(sessionId, "settings_saved", null)));
    }

    private String buildPaymentsUrl(long sessionId, String notice, String errorMessage) {
        StringBuilder url = new StringBuilder("/instructor/payments?notice=").append(notice);
        if (sessionId > 0) {
            url.append("&sessionId=").append(sessionId);
        }
        if (errorMessage != null && !errorMessage.isEmpty()) {
            url.append("&errorMessage=").append(errorMessage);
        }
        return url.toString();
    }

    private void renderPage(HttpServletRequest request, HttpServletResponse response, long userId)
            throws ServletException, IOException {
        request.setAttribute("activeMenu", "payments");
        applyNotice(request);

        Optional<Instructor> instructorOpt = loadInstructor(userId);
        if (instructorOpt.isEmpty()) {
            request.setAttribute("error", "Instructor profile not found.");
            request.getRequestDispatcher("/jsp/instructor/payments.jsp").forward(request, response);
            return;
        }
        Instructor instructor = instructorOpt.get();
        request.setAttribute("instructor", instructor);

        Long sessionId = readOptionalLong(request.getParameter("sessionId"));
        request.setAttribute("selectedSessionId", sessionId == null ? 0L : sessionId);

        try (Connection connection = Db.getConnection()) {
            InstructorPaymentSettings settings =
                    settingsDao.findByInstructorId(connection, instructor.getInstructorId()).orElse(null);
            request.setAttribute("settings", settings);
            if (settings != null && trimToNull(settings.getQrImageUrl()) != null) {
                request.setAttribute("qrPreviewUrl", resolveUrl(request.getContextPath(), settings.getQrImageUrl()));
            }

            List<RevenueBucket> sessions = paymentDao.listInstructorSessions(connection, instructor.getInstructorId());
            request.setAttribute("sessionOptions", sessions);

            List<PaymentTransactionRow> queue = paymentDao.listInstructorQueue(
                    connection, instructor.getInstructorId(), sessionId, null);
            request.setAttribute("queue", queue);

            PaymentQueryFilter statsFilter = new PaymentQueryFilter();
            statsFilter.setInstructorId(instructor.getInstructorId());
            if (sessionId != null) {
                statsFilter.setSessionId(sessionId);
            }
            PaymentStats stats = paymentDao.loadStats(connection, statsFilter);
            request.setAttribute("stats", stats);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load instructor payments dashboard", ex);
            request.setAttribute("error", "We could not load your payments right now. Please try again.");
            request.setAttribute("sessionOptions", Collections.emptyList());
            request.setAttribute("queue", Collections.emptyList());
        }

        request.getRequestDispatcher("/jsp/instructor/payments.jsp").forward(request, response);
    }

    private void applyNotice(HttpServletRequest request) {
        String notice = trimToNull(request.getParameter("notice"));
        if (notice == null) {
            return;
        }
        switch (notice) {
            case "payment_approved":
                request.setAttribute("success", "Payment approved. The student's enrollment is now confirmed.");
                break;
            case "payment_rejected":
                request.setAttribute("success", "Payment rejected. The student has been asked to resubmit.");
                break;
            case "settings_saved":
                request.setAttribute("success", "Payment configuration saved successfully.");
                break;
            case "verify_error":
            case "settings_error":
                String message = trimToNull(request.getParameter("errorMessage"));
                request.setAttribute("error", message != null ? message : "Something went wrong. Please try again.");
                break;
            default:
                break;
        }
    }

    private String validateQrPart(Part part) {
        if (part == null || part.getSize() <= 0) {
            return null;
        }
        if (part.getSize() > MAX_QR_BYTES) {
            return "QR images must be 8 MB or smaller.";
        }
        String submittedName = trimToNull(part.getSubmittedFileName());
        String lowerName = submittedName == null ? "" : submittedName.toLowerCase();
        boolean allowedExtension = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".png") || lowerName.endsWith(".webp");
        String contentType = trimToNull(part.getContentType());
        boolean allowedMime = contentType != null && contentType.toLowerCase().startsWith("image/");
        if (!allowedExtension && !allowedMime) {
            return "Please upload your QR as an image (JPG, PNG, or WEBP).";
        }
        return null;
    }

    private String saveQr(Part part) throws IOException {
        String submittedName = sanitizeFileName(part.getSubmittedFileName());
        if (submittedName == null || submittedName.isBlank()) {
            submittedName = "payment-qr.jpg";
        }
        if (CloudinaryUtil.isConfigured()) {
            String publicId = "payment-qr/" + Instant.now().toEpochMilli() + "_" + UUID.randomUUID() + "_" + submittedName;
            try (InputStream in = part.getInputStream()) {
                return CloudinaryUtil.uploadImageAsJpg(in, publicId, submittedName, part.getContentType());
            }
        }
        try (InputStream in = part.getInputStream()) {
            return LocalFileUtil.saveImage(in, "payment-qr", submittedName);
        }
    }

    private void deleteStoredUploadQuietly(HttpServletRequest request, String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }
        try {
            if (storedPath.startsWith("http://") || storedPath.startsWith("https://")) {
                CloudinaryUtil.deleteByUrl(storedPath);
                return;
            }
            java.io.File target;
            if (storedPath.startsWith("/")) {
                String realPath = request.getServletContext().getRealPath(storedPath);
                target = realPath == null ? null : new java.io.File(realPath);
            } else {
                target = new java.io.File(storedPath);
            }
            if (target != null && target.exists() && target.isFile()) {
                java.nio.file.Files.deleteIfExists(target.toPath());
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to clean up QR upload " + storedPath, ex);
        }
    }

    private String resolveUrl(String ctx, String storedPath) {
        String path = trimToNull(storedPath);
        if (path == null) {
            return null;
        }
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        if (path.startsWith("/")) {
            return ctx + path;
        }
        return ctx + "/" + path;
    }

    private String sanitizeFileName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private Optional<Instructor> loadInstructor(long userId) {
        if (userId <= 0) {
            return Optional.empty();
        }
        try (Connection connection = Db.getConnection()) {
            return instructorDao.findByUserId(connection, userId);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load instructor profile", ex);
            return Optional.empty();
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
            return 0L;
        }
    }

    private Long readOptionalLong(String raw) {
        long value = readLong(raw);
        return value > 0 ? value : null;
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

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception ex) {
            return "";
        }
    }
}
