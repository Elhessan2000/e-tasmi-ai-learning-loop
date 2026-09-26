package controller.student;

import model.service.PaymentResult;
import model.service.PaymentService;
import util.CloudinaryUtil;
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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Student QR-transfer payment gateway.
 *
 * <ul>
 *   <li>GET  /student/payments/qr     → renders the QR payment page (instructor QR + bank +
 *       instructions + receipt upload + verification timeline) for one enrollment.</li>
 *   <li>POST /student/payments/qr     → handles the receipt upload (+ optional reference and note)
 *       and moves the payment to AWAITING_VERIFICATION.</li>
 * </ul>
 *
 * The student transfers funds directly to the instructor; the platform never holds money.
 */
@WebServlet(name = "StudentQrPaymentServlet", urlPatterns = {"/student/payments/qr", "/student/payments/submit"})
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = 10L * 1024L * 1024L,
        maxRequestSize = 12L * 1024L * 1024L
)
public class StudentQrPaymentServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(StudentQrPaymentServlet.class.getName());
    private static final long MAX_RECEIPT_BYTES = 10L * 1024L * 1024L;

    private final PaymentService paymentService = new PaymentService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        long userId = readUserId(request.getSession(false));
        long enrollmentId = readLong(request.getParameter("enrollmentId"));

        applyNotice(request, trimToNull(request.getParameter("notice")));
        renderPage(request, response, userId, enrollmentId);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        long userId = readUserId(request.getSession(false));
        long enrollmentId = readLong(request.getParameter("enrollmentId"));

        if (enrollmentId <= 0) {
            request.setAttribute("error", "We could not identify the enrollment you are paying for.");
            renderPage(request, response, userId, enrollmentId);
            return;
        }

        Part receiptPart;
        try {
            receiptPart = request.getPart("receipt");
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to read receipt part", ex);
            request.setAttribute("error", "We could not read the uploaded receipt. The file may be too large.");
            request.setAttribute("receiptFormOpen", Boolean.TRUE);
            renderPage(request, response, userId, enrollmentId);
            return;
        }

        String validationError = validateReceiptPart(receiptPart);
        if (validationError != null) {
            request.setAttribute("error", validationError);
            request.setAttribute("receiptFormOpen", Boolean.TRUE);
            renderPage(request, response, userId, enrollmentId);
            return;
        }

        String storedPath;
        try {
            storedPath = saveReceipt(receiptPart);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to store payment receipt", ex);
            request.setAttribute("error", "We could not save your receipt. Please try again.");
            request.setAttribute("receiptFormOpen", Boolean.TRUE);
            renderPage(request, response, userId, enrollmentId);
            return;
        }

        String reference = trimToNull(request.getParameter("reference"));
        String note = trimToNull(request.getParameter("note"));

        PaymentResult result = paymentService.submitReceipt(userId, enrollmentId, storedPath, reference, note);
        if (result.isSuccess()) {
            String notice = "receipt_resubmitted".equals(result.getCode()) ? "receipt_resubmitted" : "receipt_submitted";
            response.sendRedirect(LocaleSupport.localizedUrl(request,
                    "/student/payments/qr?enrollmentId=" + enrollmentId + "&notice=" + notice));
            return;
        }

        deleteStoredUploadQuietly(request, storedPath);
        request.setAttribute("error", result.getError());
        request.setAttribute("receiptFormOpen", Boolean.TRUE);
        renderPage(request, response, userId, enrollmentId);
    }

    private void renderPage(HttpServletRequest request, HttpServletResponse response, long userId, long enrollmentId)
            throws ServletException, IOException {
        request.setAttribute("activeMenu", "payments");

        Optional<PaymentService.QrPaymentCheckout> checkoutOpt =
                userId > 0 && enrollmentId > 0
                        ? paymentService.loadQrCheckout(userId, enrollmentId)
                        : Optional.empty();

        if (checkoutOpt.isPresent()) {
            PaymentService.QrPaymentCheckout checkout = checkoutOpt.get();
            request.setAttribute("checkout", checkout);

            String ctx = request.getContextPath();
            String qrUrl = checkout.getSettings() == null ? null : checkout.getSettings().getQrImageUrl();
            request.setAttribute("qrUrl", resolveUrl(ctx, qrUrl));

            String receiptPath = checkout.getPayment() == null ? null : checkout.getPayment().getReceiptFilePath();
            request.setAttribute("receiptUrl", resolveUrl(ctx, receiptPath));
        }

        request.getRequestDispatcher("/jsp/student/qr_payment.jsp").forward(request, response);
    }

    private void applyNotice(HttpServletRequest request, String notice) {
        if (notice == null) {
            return;
        }
        switch (notice) {
            case "checkout_ready":
            case "checkout_retry_ready":
                request.setAttribute("success", "Your payment page is ready. Transfer the fee and upload your receipt below.");
                break;
            case "checkout_pending":
                request.setAttribute("success", "Your receipt was already submitted and is awaiting instructor verification.");
                break;
            case "receipt_submitted":
                request.setAttribute("success", "Payment receipt submitted. Please wait for instructor verification.");
                break;
            case "receipt_resubmitted":
                request.setAttribute("success", "New payment receipt submitted. Please wait for instructor verification.");
                break;
            default:
                break;
        }
    }

    private String validateReceiptPart(Part part) {
        if (part == null || part.getSize() <= 0) {
            return "Please choose your payment receipt before submitting.";
        }
        if (part.getSize() > MAX_RECEIPT_BYTES) {
            return "Receipt files must be 10 MB or smaller.";
        }
        String submittedName = trimToNull(part.getSubmittedFileName());
        String lowerName = submittedName == null ? "" : submittedName.toLowerCase();
        boolean allowedExtension = lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")
                || lowerName.endsWith(".png") || lowerName.endsWith(".webp") || lowerName.endsWith(".gif")
                || lowerName.endsWith(".pdf");
        String contentType = trimToNull(part.getContentType());
        String lowerContentType = contentType == null ? "" : contentType.toLowerCase();
        boolean allowedMime = lowerContentType.startsWith("image/")
                || "application/pdf".equals(lowerContentType)
                || "application/octet-stream".equals(lowerContentType);
        if (!allowedExtension && !allowedMime) {
            return "Please upload a clear image (JPG, PNG, WEBP) or PDF of your transfer receipt.";
        }
        return null;
    }

    private String saveReceipt(Part part) throws IOException {
        String submittedName = sanitizeFileName(part.getSubmittedFileName());
        if (submittedName == null || submittedName.isBlank()) {
            submittedName = "receipt.jpg";
        }
        boolean isPdf = submittedName.toLowerCase().endsWith(".pdf")
                || "application/pdf".equalsIgnoreCase(trimToNull(part.getContentType()));
        if (CloudinaryUtil.isConfigured()) {
            String publicId = "receipts/" + Instant.now().toEpochMilli() + "_" + UUID.randomUUID() + "_" + submittedName;
            try (InputStream in = part.getInputStream()) {
                if (isPdf) {
                    return CloudinaryUtil.uploadRaw(in, publicId, submittedName, part.getContentType());
                }
                return CloudinaryUtil.uploadImageAsJpg(in, publicId, submittedName, part.getContentType());
            }
        }
        try (InputStream in = part.getInputStream()) {
            return LocalFileUtil.saveFile(in, "receipts", submittedName, isPdf ? "pdf" : "jpg");
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
            File target;
            if (storedPath.startsWith("/")) {
                String realPath = request.getServletContext().getRealPath(storedPath);
                target = realPath == null ? null : new File(realPath);
            } else {
                target = new File(storedPath);
            }
            if (target != null && target.exists() && target.isFile()) {
                Files.deleteIfExists(target.toPath());
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to clean up receipt upload " + storedPath, ex);
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private long readLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (Exception ignored) {
            return 0;
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
}
