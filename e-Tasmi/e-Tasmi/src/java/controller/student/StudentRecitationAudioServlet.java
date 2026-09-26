package controller.student;

import model.dao.EnrollmentDao;
import model.dao.RecitationDao;
import model.dao.StudentDao;
import model.dao.impl.EnrollmentDaoJdbc;
import model.dao.impl.RecitationDaoJdbc;
import model.dao.impl.StudentDaoJdbc;
import model.entity.Enrollment;
import model.entity.Recitation;
import model.entity.Student;
import util.Db;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "StudentRecitationAudioServlet", urlPatterns = {"/student/recitation-audio"})
public class StudentRecitationAudioServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(StudentRecitationAudioServlet.class.getName());

    private final StudentDao studentDao = new StudentDaoJdbc();
    private final EnrollmentDao enrollmentDao = new EnrollmentDaoJdbc();
    private final RecitationDao recitationDao = new RecitationDaoJdbc();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = readUserId(session);
        long recitationId = readLong(request.getParameter("id"));

        if (userId <= 0 || recitationId <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        try (Connection connection = Db.getConnection()) {
            Optional<Student> studentOpt = studentDao.findByUserId(connection, userId);
            Optional<Recitation> recitationOpt = recitationDao.findById(connection, recitationId);
            if (studentOpt.isEmpty() || recitationOpt.isEmpty()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            Optional<Enrollment> enrollmentOpt = enrollmentDao.findById(connection, recitationOpt.get().getEnrollmentId());
            if (enrollmentOpt.isEmpty() || enrollmentOpt.get().getStudentId() != studentOpt.get().getStudentId()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }

            String path = recitationOpt.get().getAudioFilePath();
            if (path == null || path.trim().isEmpty()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            if (path.startsWith("http://") || path.startsWith("https://")) {
                response.sendRedirect(path);
                return;
            }

            File source;
            if (path.startsWith("/")) {
                String realPath = request.getServletContext().getRealPath(path);
                if (realPath == null) {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
                source = new File(realPath);
            } else {
                source = new File(path);
            }

            if (!source.exists() || !source.isFile()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            String mime = resolveMimeType(source.toPath(), path);
            response.setContentType(mime);
            response.setHeader("Content-Length", String.valueOf(source.length()));
            Files.copy(source.toPath(), response.getOutputStream());
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to serve recitation audio", ex);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private long readLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String resolveMimeType(java.nio.file.Path source, String originalPath) throws IOException {
        String mime = Files.probeContentType(source);
        if (mime != null && !mime.isBlank()) {
            return mime;
        }

        String lower = originalPath == null ? "" : originalPath.toLowerCase();
        if (lower.endsWith(".mp4")) {
            return "video/mp4";
        }
        if (lower.endsWith(".webm")) {
            return "video/webm";
        }
        if (lower.endsWith(".mov")) {
            return "video/quicktime";
        }
        if (lower.endsWith(".wav")) {
            return "audio/wav";
        }
        if (lower.endsWith(".m4a")) {
            return "audio/mp4";
        }
        if (lower.endsWith(".ogg")) {
            return "audio/ogg";
        }
        if (lower.endsWith(".aac")) {
            return "audio/aac";
        }
        return "audio/mpeg";
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
