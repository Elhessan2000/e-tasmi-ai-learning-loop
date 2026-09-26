package controller.instructor;

import model.dao.InstructorDao;
import model.dao.impl.InstructorDaoJdbc;
import model.entity.Instructor;
import model.entity.StudentLevel;
import model.entity.TasmiSession;
import model.service.InstructorWorkspaceService;
import model.service.ServiceResult;
import model.service.TasmiSessionCreateResult;
import model.service.TasmiSessionService;
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
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "InstructorSessionsServlet", urlPatterns = {"/instructor/sessions"})
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = 3L * 1024 * 1024,
        maxRequestSize = 5L * 1024 * 1024
)
public class InstructorSessionsServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(InstructorSessionsServlet.class.getName());

    private final TasmiSessionService tasmiSessionService = new TasmiSessionService();
    private final InstructorWorkspaceService workspaceService = new InstructorWorkspaceService();
    private final InstructorDao instructorDao = new InstructorDaoJdbc();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = readUserId(session);

        InstructorContext ctx = loadInstructorContext(userId);
        if (session != null && ctx.verificationStatus != null) {
            session.setAttribute("instructorVerificationStatus", ctx.verificationStatus);
        }
        request.setAttribute("verificationStatus", ctx.verificationStatus);

        preparePage(request, ctx);

        String editIdStr = request.getParameter("editId");
        if (editIdStr != null && !editIdStr.isBlank()) {
            long editId = parseLong(editIdStr);
            List<TasmiSession> sessions = (List<TasmiSession>) request.getAttribute("sessions");
            if (sessions != null) {
                for (TasmiSession sessionRow : sessions) {
                    if (sessionRow != null && sessionRow.getSessionId() == editId) {
                        request.setAttribute("editSession", sessionRow);
                        break;
                    }
                }
            }
        }

        setQueryMessages(request);
        request.getRequestDispatcher("/jsp/instructor/sessions.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = readUserId(session);

        String action = safe(request.getParameter("action"));
        if (action.isEmpty()) {
            action = "create";
        }

        switch (action.toLowerCase()) {
            case "delete":
                handleDelete(request, response, userId);
                return;
            case "update":
                handleUpdate(request, response, userId);
                return;
            case "start":
                handleStart(request, response, userId);
                return;
            case "complete":
                handleComplete(request, response, userId);
                return;
            case "togglepassword":
                handleTogglePassword(request, response, userId);
                return;
            default:
                handleCreate(request, response, userId);
        }
    }

    private void preparePage(HttpServletRequest request, InstructorContext ctx) {
        List<TasmiSession> sessions;
        if (ctx.instructorId <= 0) {
            sessions = List.of();
            request.setAttribute("error", "Instructor profile not found for your account.");
        } else {
            sessions = tasmiSessionService.listInstructorSessions(ctx.instructorId);
        }
        request.setAttribute("sessions", sessions);
        request.setAttribute("participantsBySession", workspaceService.participantsBySession(ctx.userId, sessions));
    }

    private void setQueryMessages(HttpServletRequest request) {
        if ("1".equals(request.getParameter("created"))) {
            request.setAttribute("success", "Session created successfully.");
        } else if ("1".equals(request.getParameter("updated"))) {
            request.setAttribute("success", "Session updated successfully.");
        } else if ("1".equals(request.getParameter("deleted"))) {
            request.setAttribute("success", "Session deleted successfully.");
        } else if ("1".equals(request.getParameter("started"))) {
            request.setAttribute("success", "Live session started successfully.");
        } else if ("1".equals(request.getParameter("completed"))) {
            request.setAttribute("success", "Session marked as completed.");
        }

        String error = request.getParameter("errorMessage");
        if (error != null && !error.isBlank()) {
            request.setAttribute("error", error);
        }
    }

    private void handleCreate(HttpServletRequest request, HttpServletResponse response, long userId) throws IOException, ServletException {
        SessionFormData form = parseSessionForm(request);
        if (!form.valid) {
            forwardWithError(request, response, userId, form.error);
            return;
        }

        String bannerUrl = uploadBannerIfPresent(request, userId, 0);

        TasmiSessionCreateResult result = tasmiSessionService.createSession(
                userId,
                form.title,
                form.description,
                form.level,
                form.date,
                form.time,
                form.durationMinutes,
                form.quranPortion,
                form.fee,
                form.capacity
        );
        if (result.isSuccess()) {
            if (bannerUrl != null) {
                tasmiSessionService.updateBannerImage(userId, result.getSessionId(), bannerUrl);
            }
            response.sendRedirect(request.getContextPath() + "/instructor/sessions?created=1");
            return;
        }

        forwardWithError(request, response, userId, result.getError());
    }

    private void handleUpdate(HttpServletRequest request, HttpServletResponse response, long userId) throws IOException, ServletException {
        long sessionId = parseLong(request.getParameter("sessionId"));
        SessionFormData form = parseSessionForm(request);
        if (sessionId <= 0 || !form.valid) {
            forwardWithError(request, response, userId, form.valid ? "Invalid session." : form.error);
            return;
        }

        String bannerUrl = uploadBannerIfPresent(request, userId, sessionId);

        ServiceResult result = tasmiSessionService.updateSession(
                userId,
                sessionId,
                form.title,
                form.description,
                form.level,
                form.date,
                form.time,
                form.durationMinutes,
                form.quranPortion,
                form.fee,
                form.capacity
        );
        if (result.isSuccess()) {
            if (bannerUrl != null) {
                tasmiSessionService.updateBannerImage(userId, sessionId, bannerUrl);
            }
            response.sendRedirect(request.getContextPath() + "/instructor/sessions?updated=1");
            return;
        }

        forwardWithError(request, response, userId, result.getError());
    }

    private void handleDelete(HttpServletRequest request, HttpServletResponse response, long userId) throws IOException, ServletException {
        long sessionId = parseLong(request.getParameter("sessionId"));
        ServiceResult result;
        try {
            result = tasmiSessionService.deleteSession(userId, sessionId);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to delete session", ex);
            result = ServiceResult.fail("Database error while deleting session. " + ex.getMessage());
        }
        if (result.isSuccess()) {
            response.sendRedirect(request.getContextPath() + "/instructor/sessions?deleted=1");
            return;
        }
        forwardWithError(request, response, userId, result.getError());
    }

    private void handleStart(HttpServletRequest request, HttpServletResponse response, long userId) throws IOException, ServletException {
        long sessionId = parseLong(request.getParameter("sessionId"));
        ServiceResult result = tasmiSessionService.startSession(userId, sessionId);
        if (result.isSuccess()) {
            response.sendRedirect(request.getContextPath() + "/instructor/live-session?sessionId=" + sessionId);
            return;
        }
        forwardWithError(request, response, userId, result.getError());
    }

    private void handleComplete(HttpServletRequest request, HttpServletResponse response, long userId) throws IOException, ServletException {
        long sessionId = parseLong(request.getParameter("sessionId"));
        ServiceResult result = tasmiSessionService.completeSession(userId, sessionId);
        if (result.isSuccess()) {
            response.sendRedirect(request.getContextPath() + "/instructor/sessions?completed=1");
            return;
        }
        forwardWithError(request, response, userId, result.getError());
    }

    private void handleTogglePassword(HttpServletRequest request, HttpServletResponse response, long userId) throws IOException, ServletException {
        long sessionId = parseLong(request.getParameter("sessionId"));
        boolean visible = "1".equals(request.getParameter("visible")) || "true".equalsIgnoreCase(safe(request.getParameter("visible")));
        ServiceResult result = tasmiSessionService.setPasswordVisibility(userId, sessionId, visible);
        if (result.isSuccess()) {
            response.sendRedirect(request.getContextPath() + "/instructor/sessions?updated=1");
            return;
        }
        forwardWithError(request, response, userId, result.getError());
    }

    private void forwardWithError(HttpServletRequest request, HttpServletResponse response, long userId, String error) throws IOException, ServletException {
        request.setAttribute("error", error);
        InstructorContext ctx = loadInstructorContext(userId);
        request.setAttribute("verificationStatus", ctx.verificationStatus);
        preparePage(request, ctx);
        request.getRequestDispatcher("/jsp/instructor/sessions.jsp").forward(request, response);
    }

    private String uploadBannerIfPresent(HttpServletRequest request, long userId, long sessionId) {
        Part bannerPart;
        try {
            bannerPart = request.getPart("bannerImage");
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to read bannerImage part", ex);
            return null;
        }
        if (bannerPart == null || bannerPart.getSize() <= 0) {
            LOGGER.info("No banner image uploaded (part is null or empty)");
            return null;
        }
        LOGGER.info("Banner part received: size=" + bannerPart.getSize() + " type=" + bannerPart.getContentType() + " name=" + bannerPart.getSubmittedFileName());
        String contentType = bannerPart.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            LOGGER.warning("Banner rejected: invalid content type: " + contentType);
            return null;
        }
        String submittedName = bannerPart.getSubmittedFileName();
        String ext = extractExt(submittedName);
        if (!isAllowedImageExt(ext)) {
            LOGGER.warning("Banner rejected: disallowed extension: " + ext);
            return null;
        }
        try (var in = bannerPart.getInputStream()) {
            String url;
            if (CloudinaryUtil.isConfigured()) {
                LOGGER.info("Uploading banner to Cloudinary...");
                String publicId = "session_banner/user_" + userId + "_sess_" + (sessionId > 0 ? sessionId : System.currentTimeMillis());
                url = CloudinaryUtil.uploadImageAsJpg(in, publicId, "banner." + ext, contentType);
            } else {
                LOGGER.info("Cloudinary not configured, saving banner locally...");
                url = LocalFileUtil.saveImage(in, "banners", "sess_" + userId + "_" + System.currentTimeMillis() + "." + ext);
            }
            LOGGER.info("Banner saved successfully: " + url);
            return url;
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Banner upload failed", ex);
            return null;
        }
    }

    private String extractExt(String submittedName) {
        if (submittedName == null) return "";
        int dot = submittedName.lastIndexOf('.');
        if (dot < 0 || dot >= submittedName.length() - 1) return "";
        return submittedName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isAllowedImageExt(String ext) {
        if (ext == null) return false;
        return "jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext) || "webp".equals(ext);
    }

    private SessionFormData parseSessionForm(HttpServletRequest request) {
        SessionFormData form = new SessionFormData();
        form.title = safe(request.getParameter("title")).trim();
        form.description = safe(request.getParameter("description")).trim();
        form.level = StudentLevel.fromString(safe(request.getParameter("level")).trim());
        form.quranPortion = safe(request.getParameter("quranPortion")).trim();

        try {
            form.date = LocalDate.parse(safe(request.getParameter("sessionDate")).trim());
        } catch (Exception ignored) {
        }
        try {
            form.time = LocalTime.parse(safe(request.getParameter("sessionTime")).trim());
        } catch (Exception ignored) {
        }
        try {
            form.durationMinutes = Integer.parseInt(safe(request.getParameter("durationMinutes")).trim());
        } catch (Exception ignored) {
            form.durationMinutes = 60;
        }
        try {
            form.capacity = Integer.parseInt(safe(request.getParameter("capacity")).trim());
        } catch (Exception ignored) {
            form.capacity = 0;
        }
        try {
            String fee = safe(request.getParameter("fee")).trim();
            form.fee = fee.isEmpty() ? BigDecimal.ZERO : new BigDecimal(fee);
        } catch (Exception ignored) {
            form.fee = null;
        }

        if (form.title.isEmpty() || form.level == null || form.date == null || form.time == null || form.capacity <= 0 || form.fee == null) {
            form.valid = false;
            form.error = "Please enter a valid title, target student level, date, time, duration, fee, and capacity.";
        } else {
            form.valid = true;
        }
        return form;
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
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

    private InstructorContext loadInstructorContext(long userId) {
        if (userId <= 0) {
            return new InstructorContext(0, userId, null);
        }

        try (Connection connection = Db.getConnection()) {
            Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, userId);
            if (instructorOpt.isEmpty()) {
                return new InstructorContext(0, userId, null);
            }

            Instructor instructor = instructorOpt.get();
            String status = instructor.getVerificationStatus() == null ? null : instructor.getVerificationStatus().name();
            return new InstructorContext(instructor.getInstructorId(), userId, status);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load instructor context", ex);
            return new InstructorContext(0, userId, null);
        }
    }

    private static class InstructorContext {
        final long instructorId;
        final long userId;
        final String verificationStatus;

        private InstructorContext(long instructorId, long userId, String verificationStatus) {
            this.instructorId = instructorId;
            this.userId = userId;
            this.verificationStatus = verificationStatus;
        }
    }

    private static class SessionFormData {
        String title;
        String description;
        StudentLevel level;
        LocalDate date;
        LocalTime time;
        Integer durationMinutes;
        String quranPortion;
        BigDecimal fee;
        int capacity;
        boolean valid;
        String error;
    }
}
