package controller.student;

import model.dao.EnrollmentDao;
import model.dao.EvaluationDao;
import model.dao.InstructorDao;
import model.dao.PaymentDao;
import model.dao.RecitationAnalysisDao;
import model.dao.RecitationDao;
import model.dao.StudentDao;
import model.dao.TasmiSessionDao;
import model.dao.UserDao;
import model.dao.impl.EnrollmentDaoJdbc;
import model.dao.impl.EvaluationDaoJdbc;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.PaymentDaoJdbc;
import model.dao.impl.RecitationAnalysisDaoJdbc;
import model.dao.impl.RecitationDaoJdbc;
import model.dao.impl.StudentDaoJdbc;
import model.dao.impl.TasmiSessionDaoJdbc;
import model.dao.impl.UserDaoJdbc;
import model.entity.Enrollment;
import model.entity.EnrollmentStatus;
import model.entity.Evaluation;
import model.entity.Instructor;
import model.entity.Payment;
import model.entity.PaymentStatus;
import model.entity.Recitation;
import model.entity.RecitationAnalysis;
import model.entity.Student;
import model.entity.TasmiSession;
import model.entity.User;
import model.service.RecitationService;
import model.service.RecitationSubmitResult;
import model.service.StudentRecitationAnalysisPhase;
import model.service.VerifiedLearningFocusService;
import model.service.VerifiedRecitationView;
import util.CloudinaryUtil;
import util.Db;
import util.LocalFileUtil;

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
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Student "Recitation Studio" — a session-first workflow:
 *   1) pick one of the sessions you are enrolled in,
 *   2) record live audio OR upload a previous file,
 * and the submission is linked to that session's enrollment so the
 * instructor can evaluate it (legacy enrollment-bound recitation flow).
 *
 * <ul>
 *   <li>GET  /student/recitations             → dashboard (eligible sessions + history)</li>
 *   <li>POST /student/recitations (multipart) → submit a recording/upload for an enrollment</li>
 * </ul>
 */
@WebServlet(name = "StudentRecitationServlet", urlPatterns = {"/student/recitations"})
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = 200L * 1024L * 1024L,
        maxRequestSize = 210L * 1024L * 1024L
)
public class StudentRecitationServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(StudentRecitationServlet.class.getName());
    private static final long MAX_MEDIA_SIZE_BYTES = 200L * 1024L * 1024L;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private final RecitationService recitationService = new RecitationService();
    private final StudentDao studentDao = new StudentDaoJdbc();
    private final EnrollmentDao enrollmentDao = new EnrollmentDaoJdbc();
    private final PaymentDao paymentDao = new PaymentDaoJdbc();
    private final TasmiSessionDao tasmiSessionDao = new TasmiSessionDaoJdbc();
    private final EvaluationDao evaluationDao = new EvaluationDaoJdbc();
    private final InstructorDao instructorDao = new InstructorDaoJdbc();
    private final UserDao userDao = new UserDaoJdbc();
    private final RecitationDao recitationDao = new RecitationDaoJdbc();
    private final RecitationAnalysisDao recitationAnalysisDao = new RecitationAnalysisDaoJdbc();
    private final VerifiedLearningFocusService focusService = new VerifiedLearningFocusService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = readUserId(session);

        if ("1".equals(request.getParameter("submitted"))) {
            request.setAttribute("successKey", "student.recitations.submittedSuccess");
            request.setAttribute("success", "Recitation submitted. You can track progress on your result page.");
        }
        attachPracticeAgain(request, userId);

        populatePage(request, userId);
        request.getRequestDispatcher("/jsp/student/recitations.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = readUserId(session);
        boolean wantsJson = "1".equals(request.getParameter("ajax"));

        long enrollmentId = readLong(request.getParameter("enrollmentId"));
        long parentRecitationId = readLong(request.getParameter("parentRecitationId"));
        if (enrollmentId <= 0) {
            respond(request, response, wantsJson, false, "Please choose the session you want to submit for.", 0);
            return;
        }

        Part audioPart;
        try {
            audioPart = request.getPart("audio");
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to read recitation media part", ex);
            respond(request, response, wantsJson, false, "We could not read the uploaded audio. The file may be too large.", 0);
            return;
        }

        String validationError = validateMediaPart(audioPart);
        if (validationError != null) {
            respond(request, response, wantsJson, false, validationError, 0);
            return;
        }

        String storedPath;
        try {
            storedPath = saveUpload(audioPart);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Failed to store recitation media", ex);
            respond(request, response, wantsJson, false, "We could not save your recitation audio. Please try again.", 0);
            return;
        }

        RecitationSubmitResult result = recitationService.submit(userId, enrollmentId, storedPath, parentRecitationId);
        if (result.isSuccess()) {
            respond(request, response, wantsJson, true, null, result.getRecitationId());
            return;
        }

        deleteStoredUploadQuietly(request, storedPath);
        respond(request, response, wantsJson, false, result.getError(), 0);
    }

    /* ===================================================================
     * Page data
     * ================================================================= */
    private void populatePage(HttpServletRequest request, long studentUserId) {
        request.setAttribute("activeMenu", "recitations");
        request.setAttribute("studentFirstName", resolveFirstName(request.getSession(false)));

        List<Map<String, Object>> eligibleSessions = new ArrayList<>();
        List<Map<String, Object>> historyItems = new ArrayList<>();

        if (studentUserId > 0) {
            try (Connection connection = Db.getConnection()) {
                Optional<Student> studentOpt = studentDao.findByUserId(connection, studentUserId);
                if (studentOpt.isPresent()) {
                    long studentId = studentOpt.get().getStudentId();
                    List<Enrollment> enrollments = enrollmentDao.listByStudentId(connection, studentId);
                    Map<Long, String> instructorNames = new HashMap<>();

                    for (Enrollment enrollment : enrollments) {
                        if (enrollment == null) {
                            continue;
                        }
                        TasmiSession tasmiSession = tasmiSessionDao.findById(connection, enrollment.getSessionId()).orElse(null);
                        String title = (tasmiSession != null && trimToNull(tasmiSession.getTitle()) != null)
                                ? tasmiSession.getTitle().trim()
                                : ("Session #" + enrollment.getSessionId());
                        String instructorName = resolveInstructorName(connection, instructorNames, tasmiSession);

                        if (isEligible(connection, enrollment, tasmiSession)) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            row.put("enrollmentId", enrollment.getEnrollmentId());
                            row.put("sessionId", enrollment.getSessionId());
                            row.put("title", title);
                            row.put("instructor", instructorName);
                            row.put("schedule", formatSessionSchedule(tasmiSession));
                            row.put("portion", tasmiSession == null ? "" : model.service.quran.QuranPassageDisplay.format(tasmiSession));
                            row.put("mode", modeLabel(tasmiSession));
                            row.put("fee", feeLabel(tasmiSession == null ? null : tasmiSession.getFee()));
                            eligibleSessions.add(row);
                        }

                        for (Recitation recitation : recitationDao.listByEnrollmentId(connection, enrollment.getEnrollmentId())) {
                            if (recitation == null) {
                                continue;
                            }
                            Evaluation evaluation = evaluationDao.findByRecitationId(connection, recitation.getRecitationId()).orElse(null);
                            boolean published = evaluation != null && evaluation.getPublishedAt() != null;
                            Integer score = published ? evaluation.getScore() : null;
                            String statusKind;
                            String statusLabel;
                            String statusI18nKey;
                            RecitationAnalysis latestAnalysis = recitationAnalysisDao
                                    .findLatestByRecitationId(connection, recitation.getRecitationId()).orElse(null);
                            StudentRecitationAnalysisPhase analysisPhase = published
                                    ? null
                                    : StudentRecitationAnalysisPhase.resolve(
                                    evaluation, recitation.getAnalysisJobState(), latestAnalysis,
                                    recitation.getAnalysisJobStartedAt());
                            if (!published) {
                                statusKind = "pending";
                                statusLabel = studentPhaseLabel(analysisPhase);
                                statusI18nKey = studentPhaseI18nKey(analysisPhase);
                            } else if (score != null && score >= 85) {
                                statusKind = "excellent";
                                statusLabel = "Excellent";
                                statusI18nKey = "student.recitations.instructorVerified";
                            } else if (score != null && score >= 60) {
                                statusKind = "reviewed";
                                statusLabel = "Reviewed";
                                statusI18nKey = "student.recitations.instructorVerified";
                            } else {
                                statusKind = "improve";
                                statusLabel = "Needs improvement";
                                statusI18nKey = "student.recitations.instructorVerified";
                            }

                            Map<String, Object> item = new LinkedHashMap<>();
                            item.put("recitationId", recitation.getRecitationId());
                            item.put("sessionTitle", title);
                            item.put("instructor", instructorName);
                            item.put("schedule", formatSessionSchedule(tasmiSession));
                            item.put("portion", tasmiSession == null ? "" : model.service.quran.QuranPassageDisplay.format(tasmiSession));
                            item.put("mode", modeLabel(tasmiSession));
                            item.put("date", recitation.getSubmissionDate() == null ? "" : DATE_FMT.format(recitation.getSubmissionDate()));
                            item.put("sortTs", recitation.getSubmissionDate() == null ? 0L : recitation.getSubmissionDate().toEpochMilli());
                            item.put("evaluated", published);
                            item.put("published", published);
                            item.put("score", score);
                            item.put("feedback", published ? nullToEmpty(evaluation.getFeedback()) : "");
                            item.put("statusKind", statusKind);
                            item.put("statusLabel", statusLabel);
                            item.put("statusI18nKey", statusI18nKey);
                            if (analysisPhase != null) {
                                item.put("analysisPhase", analysisPhase.name());
                            }
                            item.put("audioUrl", request.getContextPath() + "/student/recitation-audio?id=" + recitation.getRecitationId());
                            item.put("resultUrl", request.getContextPath()
                                    + "/student/recitation-result?id=" + recitation.getRecitationId());
                            historyItems.add(item);
                        }
                    }
                }
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Failed to prepare recitation page", ex);
            }
        }

        historyItems.sort((a, b) -> Long.compare(
                ((Number) b.getOrDefault("sortTs", 0L)).longValue(),
                ((Number) a.getOrDefault("sortTs", 0L)).longValue()));

        int total = historyItems.size();
        int reviewed = 0;
        for (Map<String, Object> item : historyItems) {
            if (Boolean.TRUE.equals(item.get("published"))) {
                reviewed++;
            }
        }

        request.setAttribute("eligibleSessions", eligibleSessions);
        request.setAttribute("historyItems", historyItems);
        request.setAttribute("statTotal", total);
        request.setAttribute("statReviewed", reviewed);
        request.setAttribute("statPending", total - reviewed);
    }

    /**
     * Prepares Practice Again only for a published recitation this student owns,
     * on an enrollment that can still accept a submission.
     */
    private void attachPracticeAgain(HttpServletRequest request, long studentUserId) {
        long practiceId = readLong(request.getParameter("practice"));
        if (practiceId <= 0 || studentUserId <= 0) {
            return;
        }
        Optional<VerifiedRecitationView> viewOpt = focusService.loadForStudent(studentUserId, practiceId);
        if (viewOpt.isEmpty() || !viewOpt.get().isPublished()) {
            return;
        }
        VerifiedRecitationView view = viewOpt.get();
        try (Connection connection = Db.getConnection()) {
            Optional<Enrollment> enrollmentOpt = enrollmentDao.findById(connection, view.getEnrollmentId());
            if (enrollmentOpt.isEmpty()) {
                return;
            }
            Enrollment enrollment = enrollmentOpt.get();
            TasmiSession session = tasmiSessionDao.findById(connection, enrollment.getSessionId()).orElse(null);
            if (!isEligible(connection, enrollment, session)) {
                request.setAttribute("error", "This session is not open for another attempt.");
                return;
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Could not prepare Practice Again for recitation " + practiceId, ex);
            return;
        }
        request.setAttribute("practiceParentId", Long.valueOf(practiceId));
        request.setAttribute("practiceEnrollmentId", Long.valueOf(view.getEnrollmentId()));
        request.setAttribute("practiceFocus", view.getFocusItems());
    }

    private boolean isEligible(Connection connection, Enrollment enrollment, TasmiSession tasmiSession) throws SQLException {
        if (enrollment.getEnrollmentStatus() != EnrollmentStatus.APPROVED) {
            return false;
        }
        boolean paymentSatisfied = tasmiSession != null
                && tasmiSession.getFee() != null
                && tasmiSession.getFee().compareTo(BigDecimal.ZERO) <= 0;
        if (!paymentSatisfied) {
            Optional<Payment> paymentOpt = paymentDao.findByEnrollmentId(connection, enrollment.getEnrollmentId());
            paymentSatisfied = paymentOpt.isPresent() && paymentOpt.get().getPaymentStatus() == PaymentStatus.APPROVED;
        }
        return paymentSatisfied;
    }

    /* ===================================================================
     * Response helpers
     * ================================================================= */
    private void respond(HttpServletRequest request, HttpServletResponse response,
                         boolean wantsJson, boolean ok, String error, long recitationId) throws IOException, ServletException {
        String redirect = recitationId > 0
                ? request.getContextPath() + "/student/recitation-result?id=" + recitationId
                : request.getContextPath() + "/student/recitations?submitted=1";
        if (wantsJson) {
            if (ok) {
                writeJson(response, HttpServletResponse.SC_OK, "{\"ok\":true,\"redirect\":\"" + jsonEscape(redirect) + "\"}");
            } else {
                writeJson(response, HttpServletResponse.SC_BAD_REQUEST, "{\"ok\":false,\"error\":\"" + jsonEscape(error) + "\"}");
            }
            return;
        }
        if (ok) {
            response.sendRedirect(redirect);
            return;
        }
        request.setAttribute("error", error);
        populatePage(request, readUserId(request.getSession(false)));
        request.getRequestDispatcher("/jsp/student/recitations.jsp").forward(request, response);
    }

    /* ===================================================================
     * File storage
     * ================================================================= */
    private String validateMediaPart(Part audioPart) {
        if (audioPart == null || audioPart.getSize() <= 0) {
            return "Please record or choose a recitation audio file first.";
        }
        if (audioPart.getSize() > MAX_MEDIA_SIZE_BYTES) {
            return "Recitation files must be 200 MB or smaller.";
        }
        String submittedName = trimToNull(audioPart.getSubmittedFileName());
        String lowerName = submittedName == null ? "" : submittedName.toLowerCase();
        boolean allowedExtension = lowerName.endsWith(".webm") || lowerName.endsWith(".mp3")
                || lowerName.endsWith(".wav") || lowerName.endsWith(".m4a") || lowerName.endsWith(".ogg")
                || lowerName.endsWith(".oga") || lowerName.endsWith(".aac") || lowerName.endsWith(".mp4")
                || lowerName.endsWith(".mov") || lowerName.endsWith(".mpeg") || lowerName.endsWith(".mpga");
        String contentType = trimToNull(audioPart.getContentType());
        String lowerContentType = contentType == null ? "" : contentType.toLowerCase();
        boolean allowedMime = lowerContentType.startsWith("audio/") || lowerContentType.startsWith("video/")
                || "application/octet-stream".equals(lowerContentType);
        if (!allowedExtension && !allowedMime) {
            return "Please use a supported audio format (webm, mp3, wav, m4a, ogg, aac).";
        }
        return null;
    }

    private String saveUpload(Part part) throws IOException {
        String submittedName = sanitizeFileName(part.getSubmittedFileName());
        if (submittedName == null || submittedName.isBlank()) {
            submittedName = "recitation.webm";
        }
        if (CloudinaryUtil.isConfigured()) {
            String publicId = "recitations/" + Instant.now().toEpochMilli() + "_" + UUID.randomUUID() + "_" + submittedName;
            try (InputStream in = part.getInputStream()) {
                return CloudinaryUtil.uploadVideo(in, publicId, submittedName, part.getContentType());
            }
        }
        try (InputStream in = part.getInputStream()) {
            return LocalFileUtil.saveFile(in, "recitations", submittedName, "webm");
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
            LOGGER.log(Level.WARNING, "Failed to clean up recitation upload " + storedPath, ex);
        }
    }

    /* ===================================================================
     * Formatting / utility
     * ================================================================= */
    private String resolveInstructorName(Connection connection, Map<Long, String> cache, TasmiSession tasmiSession) throws SQLException {
        if (tasmiSession == null || tasmiSession.getInstructorId() <= 0) {
            return "Instructor";
        }
        String cached = cache.get(tasmiSession.getInstructorId());
        if (cached != null) {
            return cached;
        }
        String resolved = "Instructor";
        Optional<Instructor> instructorOpt = instructorDao.findById(connection, tasmiSession.getInstructorId());
        if (instructorOpt.isPresent()) {
            Optional<User> userOpt = userDao.findById(connection, instructorOpt.get().getUserId());
            if (userOpt.isPresent() && trimToNull(userOpt.get().getFullName()) != null) {
                resolved = userOpt.get().getFullName().trim();
            } else {
                resolved = "Instructor #" + tasmiSession.getInstructorId();
            }
        }
        cache.put(tasmiSession.getInstructorId(), resolved);
        return resolved;
    }

    private String formatSessionSchedule(TasmiSession tasmiSession) {
        if (tasmiSession == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (tasmiSession.getSessionDate() != null) {
            sb.append(tasmiSession.getSessionDate().toString());
        }
        if (tasmiSession.getSessionTime() != null) {
            if (sb.length() > 0) {
                sb.append(" \u00b7 ");
            }
            sb.append(tasmiSession.getSessionTime().toString());
        }
        return sb.toString();
    }

    private static String studentPhaseLabel(StudentRecitationAnalysisPhase phase) {
        if (phase == null) {
            return "Pending review";
        }
        switch (phase) {
            case ANALYSIS_IN_PROGRESS:
                return "AI analysis in progress";
            case SUBMITTED:
                return "Submitted";
            case AWAITING_INSTRUCTOR:
                return "Awaiting instructor review";
            case REFERENCE_UNAVAILABLE:
                return "Reference temporarily unavailable";
            case ANALYSIS_FAILED:
                return "Analysis could not be completed";
            case CANNOT_EVALUATE:
                return "Could not evaluate submission";
            case REJECTED:
                return "Awaiting instructor review";
            default:
                return "Pending review";
        }
    }

    private static String studentPhaseI18nKey(StudentRecitationAnalysisPhase phase) {
        if (phase == null) {
            return "student.recitations.statusPending";
        }
        switch (phase) {
            case ANALYSIS_IN_PROGRESS:
                return "student.recitations.phaseAnalysisTitle";
            case SUBMITTED:
                return "student.recitations.phaseSubmittedTitle";
            case AWAITING_INSTRUCTOR:
            case REJECTED:
                return "student.recitations.awaitingReview";
            case REFERENCE_UNAVAILABLE:
                return "student.recitations.phaseReferenceTitle";
            case ANALYSIS_FAILED:
                return "student.recitations.phaseFailedTitle";
            case CANNOT_EVALUATE:
                return "student.recitations.phaseCannotEvaluateTitle";
            default:
                return "student.recitations.statusPending";
        }
    }

    private String modeLabel(TasmiSession tasmiSession) {
        if (tasmiSession == null || tasmiSession.getMode() == null) {
            return "Guided session";
        }
        return "PHYSICAL".equalsIgnoreCase(tasmiSession.getMode().name()) ? "Physical class" : "Online class";
    }

    private String feeLabel(BigDecimal fee) {
        if (fee == null || fee.compareTo(BigDecimal.ZERO) <= 0) {
            return "Free session";
        }
        return "RM " + fee.stripTrailingZeros().toPlainString();
    }

    private void writeJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        try (PrintWriter writer = response.getWriter()) {
            writer.write(body);
        }
    }

    private String resolveFirstName(HttpSession session) {
        if (session == null) {
            return "there";
        }
        Object displayName = session.getAttribute("displayName");
        String name = displayName == null ? "" : String.valueOf(displayName).trim();
        if (name.isEmpty()) {
            return "there";
        }
        String[] parts = name.split("\\s+");
        return parts.length > 0 && !parts[0].isEmpty() ? parts[0] : "there";
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

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private long readLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
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
