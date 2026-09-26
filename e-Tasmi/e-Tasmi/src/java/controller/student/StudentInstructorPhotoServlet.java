package controller.student;

import model.dao.InstructorDao;
import model.dao.UserDao;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.UserDaoJdbc;
import model.entity.Instructor;
import model.entity.User;
import util.Db;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

@WebServlet(name = "StudentInstructorPhotoServlet", urlPatterns = {"/student/instructor-photo"})
public class StudentInstructorPhotoServlet extends HttpServlet {
    private final InstructorDao instructorDao = new InstructorDaoJdbc();
    private final UserDao userDao = new UserDaoJdbc();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long viewerUserId = readUserId(session);
        if (viewerUserId <= 0) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        long instructorId = parseLong(request.getParameter("instructorId"));
        if (instructorId <= 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        long instructorUserId;
        try (Connection connection = Db.getConnection()) {
            Optional<Instructor> instructorOpt = instructorDao.findById(connection, instructorId);
            if (instructorOpt.isEmpty() || instructorOpt.get().getUserId() <= 0) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            instructorUserId = instructorOpt.get().getUserId();
            Optional<User> userOpt = userDao.findById(connection, instructorUserId);
            if (userOpt.isEmpty() || userOpt.get().getProfileImageUrl() == null || userOpt.get().getProfileImageUrl().isBlank()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            response.setHeader("Cache-Control", "no-store, max-age=0, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
            response.sendRedirect(userOpt.get().getProfileImageUrl() + "?t=" + System.currentTimeMillis());
        } catch (SQLException ex) {
            throw new ServletException("Unable to load instructor photo", ex);
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

    private long parseLong(String raw) {
        if (raw == null) {
            return 0;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
