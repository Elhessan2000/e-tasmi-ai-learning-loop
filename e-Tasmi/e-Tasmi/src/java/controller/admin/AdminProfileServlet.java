package controller.admin;

import model.dao.UserDao;
import model.dao.impl.UserDaoJdbc;
import model.entity.User;
import util.Db;
import util.PasswordUtil;

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
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "AdminProfileServlet", urlPatterns = {"/admin/profile"})
public class AdminProfileServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(AdminProfileServlet.class.getName());
    private final UserDao userDao = new UserDaoJdbc();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = readUserId(session);

        if (userId > 0) {
            try (Connection connection = Db.getConnection()) {
                userDao.findById(connection, userId).ifPresent(u -> request.setAttribute("user", u));
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Failed to load admin profile", ex);
            }
        }

        request.setAttribute("activeMenu", "profile");
        request.getRequestDispatcher("/jsp/admin/profile.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = readUserId(session);
        if (userId <= 0) {
            response.sendRedirect(request.getContextPath() + "/auth/login?expired=1");
            return;
        }

        String action = safe(request.getParameter("action"));
        if ("profile".equals(action)) {
            handleProfileUpdate(request, response, session, userId);
            return;
        }
        if ("password".equals(action)) {
            handlePasswordChange(request, response, userId);
            return;
        }

        response.sendRedirect(request.getContextPath() + "/admin/profile");
    }

    private void handleProfileUpdate(HttpServletRequest request, HttpServletResponse response, HttpSession session, long userId) throws IOException, ServletException {
        String fullName = safe(request.getParameter("fullName")).trim();
        String phone = safe(request.getParameter("phone")).trim();

        if (fullName.isEmpty() || phone.isEmpty()) {
            request.setAttribute("error", "Full name and phone are required.");
            doGet(request, response);
            return;
        }

        try (Connection connection = Db.getConnection()) {
            boolean ok = userDao.updateProfile(connection, userId, fullName, phone);
            if (!ok) {
                request.setAttribute("error", "Profile update failed.");
                doGet(request, response);
                return;
            }
            session.setAttribute("displayName", fullName);
            request.setAttribute("success", "Profile updated successfully.");
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to update admin profile", ex);
            request.setAttribute("error", "Profile update failed due to a server error.");
        }

        doGet(request, response);
    }

    private void handlePasswordChange(HttpServletRequest request, HttpServletResponse response, long userId) throws IOException, ServletException {
        String currentPassword = safe(request.getParameter("currentPassword"));
        String newPassword = safe(request.getParameter("newPassword"));
        String confirmPassword = safe(request.getParameter("confirmPassword"));

        if (currentPassword.trim().isEmpty() || newPassword.trim().isEmpty() || confirmPassword.trim().isEmpty()) {
            request.setAttribute("error", "All password fields are required.");
            doGet(request, response);
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            request.setAttribute("error", "New password and confirm password do not match.");
            doGet(request, response);
            return;
        }

        if (!isStrongPassword(newPassword)) {
            request.setAttribute("error", "Password must be at least 8 characters and include uppercase, lowercase, and a number.");
            doGet(request, response);
            return;
        }

        try (Connection connection = Db.getConnection()) {
            Optional<User> userOpt = userDao.findById(connection, userId);
            if (userOpt.isEmpty()) {
                request.setAttribute("error", "Account not found.");
                doGet(request, response);
                return;
            }

            User user = userOpt.get();
            if (!PasswordUtil.verifyPassword(currentPassword.toCharArray(), user.getPasswordHash())) {
                request.setAttribute("error", "Current password is incorrect.");
                doGet(request, response);
                return;
            }

            String newHash = PasswordUtil.hashPassword(newPassword.toCharArray());
            boolean ok = userDao.updatePasswordHash(connection, userId, newHash);
            if (!ok) {
                request.setAttribute("error", "Password update failed.");
                doGet(request, response);
                return;
            }
            request.setAttribute("success", "Password updated successfully.");
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to update admin password", ex);
            request.setAttribute("error", "Password update failed due to a server error.");
        }

        doGet(request, response);
    }

    private boolean isStrongPassword(String password) {
        if (password == null || password.length() < 8) return false;
        boolean hasUpper = false, hasLower = false, hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
        }
        return hasUpper && hasLower && hasDigit;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private long readUserId(HttpSession session) {
        if (session == null) return 0;
        Object userIdObj = session.getAttribute("userId");
        if (userIdObj instanceof Long) return (Long) userIdObj;
        if (userIdObj instanceof Integer) return ((Integer) userIdObj).longValue();
        return 0;
    }
}
