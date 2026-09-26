package controller.instructor;

import model.dao.InstructorDao;
import model.dao.SessionMaterialDao;
import model.dao.TasmiSessionDao;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.SessionMaterialDaoJdbc;
import model.dao.impl.TasmiSessionDaoJdbc;
import model.entity.Instructor;
import model.entity.SessionMaterial;
import model.entity.TasmiSession;
import util.CloudinaryUtil;
import util.Db;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.util.Optional;

@WebServlet(name = "InstructorMaterialServlet", urlPatterns = {"/instructor/materials/open"})
public class InstructorMaterialServlet extends HttpServlet {
    private final InstructorDao instructorDao = new InstructorDaoJdbc();
    private final TasmiSessionDao sessionDao = new TasmiSessionDaoJdbc();
    private final SessionMaterialDao materialDao = new SessionMaterialDaoJdbc();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        long userId = readUserId(request.getSession(false));
        long sessionId = parseLong(request.getParameter("sessionId"));
        long materialId = parseLong(request.getParameter("materialId"));
        boolean download = "1".equals(request.getParameter("download"));

        if (userId <= 0 || sessionId <= 0 || materialId <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid material request.");
            return;
        }

        try (Connection connection = Db.getConnection()) {
            Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, userId);
            if (instructorOpt.isEmpty()) {
                redirectBack(request, response, "Instructor profile not found.");
                return;
            }

            Optional<TasmiSession> sessionOpt = sessionDao.findById(connection, sessionId);
            if (sessionOpt.isEmpty() || sessionOpt.get().getInstructorId() != instructorOpt.get().getInstructorId()) {
                redirectBack(request, response, "Session not found for your account.");
                return;
            }

            Optional<SessionMaterial> materialOpt = materialDao.findByIdAndSessionId(connection, materialId, sessionId);
            if (materialOpt.isEmpty()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Material not found.");
                return;
            }

            SessionMaterial material = materialOpt.get();
            String fileRef = material.getFilePath();
            if (fileRef == null || fileRef.isBlank()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Material file not found.");
                return;
            }

            String trimmed = fileRef.trim();
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                response.sendRedirect(download ? CloudinaryUtil.toAttachmentUrl(trimmed) : trimmed);
                return;
            }

            Path filePath = resolvePath(trimmed);
            if (filePath == null || !isAllowedMaterialPath(filePath) || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Material file not found on server.");
                return;
            }

            String contentType = Files.probeContentType(filePath);
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }

            response.setContentType(contentType);
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Content-Disposition", (download ? "attachment" : "inline") + "; filename=\"" + safeFileName(filePath.getFileName().toString()) + "\"");
            response.setContentLengthLong(Files.size(filePath));

            try (InputStream in = Files.newInputStream(filePath); OutputStream out = response.getOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        } catch (Exception ex) {
            throw new ServletException("Failed to open teaching material", ex);
        }
    }

    private void redirectBack(HttpServletRequest request, HttpServletResponse response, String message) throws IOException {
        String encoded = java.net.URLEncoder.encode(message == null ? "Access denied." : message, java.nio.charset.StandardCharsets.UTF_8);
        response.sendRedirect(request.getContextPath() + "/instructor/sessions?errorMessage=" + encoded);
    }

    private Path resolvePath(String fileRef) {
        try {
            if (fileRef.startsWith("/")) {
                String root = getServletContext().getRealPath("/");
                if (root == null || root.isBlank()) {
                    String base = System.getProperty("catalina.base");
                    if (base == null || base.isBlank()) {
                        return null;
                    }
                    return Paths.get(base, "webapps", "ROOT", fileRef.substring(1)).toAbsolutePath().normalize();
                }
                return new java.io.File(root, fileRef.substring(1)).toPath().toAbsolutePath().normalize();
            }
            return Paths.get(fileRef).toAbsolutePath().normalize();
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isAllowedMaterialPath(Path filePath) {
        String root = getServletContext().getRealPath("/uploads/materials");
        if (root != null && !root.isBlank()) {
            Path allowed = Paths.get(root).toAbsolutePath().normalize();
            if (filePath.startsWith(allowed)) {
                return true;
            }
        }
        String tmp = System.getProperty("java.io.tmpdir");
        if (tmp != null && !tmp.isBlank()) {
            Path allowedTmp = Paths.get(tmp, "etasmi_uploads", "materials").toAbsolutePath().normalize();
            if (filePath.startsWith(allowedTmp)) {
                return true;
            }
        }
        String base = System.getProperty("catalina.base");
        if (base != null && !base.isBlank()) {
            Path allowedBase = Paths.get(base, "webapps", "ROOT", "uploads", "materials").toAbsolutePath().normalize();
            return filePath.startsWith(allowedBase);
        }
        return false;
    }

    private String safeFileName(String name) {
        return name == null ? "material" : name.replace("\"", "").replace("\r", "").replace("\n", "");
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private long readUserId(HttpSession session) {
        if (session == null) {
            return 0L;
        }
        Object userIdObj = session.getAttribute("userId");
        if (userIdObj instanceof Number) {
            return ((Number) userIdObj).longValue();
        }
        if (userIdObj != null) {
            try {
                return Long.parseLong(String.valueOf(userIdObj));
            } catch (NumberFormatException ex) {
                return 0L;
            }
        }
        return 0L;
    }
}
