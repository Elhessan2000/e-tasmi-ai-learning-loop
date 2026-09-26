package controller.student;

import model.dao.UserDao;
import model.dao.impl.UserDaoJdbc;
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

@WebServlet(name = "StudentProfilePhotoServlet", urlPatterns = {"/student/profile-photo"})
public class StudentProfilePhotoServlet extends HttpServlet {
    private final UserDao userDao = new UserDaoJdbc();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = readUserId(session);
        if (userId <= 0) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        try (Connection connection = Db.getConnection()) {
            Optional<User> userOpt = userDao.findById(connection, userId);
            if (userOpt.isEmpty() || userOpt.get().getProfileImageUrl() == null || userOpt.get().getProfileImageUrl().isBlank()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            response.setHeader("Cache-Control", "no-store, max-age=0, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
            String imgUrl = userOpt.get().getProfileImageUrl();
            if (!imgUrl.startsWith("http")) {
                imgUrl = request.getContextPath() + imgUrl;
            }
            response.sendRedirect(imgUrl + "?t=" + System.currentTimeMillis());
        } catch (SQLException ex) {
            throw new ServletException("Unable to load profile photo", ex);
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
