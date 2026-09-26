package controller.auth;

import model.dao.UserDao;
import model.dao.impl.UserDaoJdbc;
import model.entity.User;
import model.service.EmailVerificationService;
import model.service.ServiceResult;
import util.Db;
import util.JsonUtil;
import util.RateLimiter;
import util.UrlUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@WebServlet(name = "ResendVerificationServlet", urlPatterns = {"/auth/resend-verification"})
public class ResendVerificationServlet extends HttpServlet {
    private final UserDao userDao = new UserDaoJdbc();
    private final EmailVerificationService emailVerificationService = new EmailVerificationService();

    private boolean isAjax(HttpServletRequest request) {
        String xrw = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        return "XMLHttpRequest".equalsIgnoreCase(xrw) || (accept != null && accept.contains("application/json"));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String ip = request.getRemoteAddr();
        String email = request.getParameter("email");

        if (!RateLimiter.allow("resend:" + ip, 5, Duration.ofMinutes(10))) {
            String msg = "Too many requests. Please try again later.";
            if (isAjax(request)) {
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> payload = new HashMap<>();
                payload.put("success", false);
                payload.put("error", msg);
                response.getWriter().write(JsonUtil.obj(payload));
                return;
            }
            request.setAttribute("error", msg);
            request.getRequestDispatcher("/jsp/auth/verify_email.jsp").forward(request, response);
            return;
        }

        // Generic success message to avoid leaking whether an email exists.
        String genericSuccess = "If the email exists and is not verified, a new verification code has been sent.";

        if (email == null || email.isBlank()) {
            if (isAjax(request)) {
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> payload = new HashMap<>();
                payload.put("success", true);
                payload.put("message", genericSuccess);
                response.getWriter().write(JsonUtil.obj(payload));
                return;
            }
            request.setAttribute("success", genericSuccess);
            request.getRequestDispatcher("/jsp/auth/verify_email.jsp").forward(request, response);
            return;
        }

        String normalizedEmail = email.trim().toLowerCase();

        ServiceResult sendRes = null;
        boolean knownUnverified = false;
        try (Connection connection = Db.getConnection()) {
            Optional<User> userOpt = userDao.findByEmail(connection, normalizedEmail);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (!user.isEmailVerified()) {
                    knownUnverified = true;
                    String baseUrl = UrlUtil.getBaseUrl(request);
                    sendRes = emailVerificationService.createAndSendCode(
                            getServletContext(),
                            user.getUserId(),
                            user.getEmail(),
                            user.getFullName(),
                            baseUrl
                    );
                }
            }
        } catch (SQLException ex) {
            // Still respond generically.
        }

        // If we KNOW this email exists + is unverified, and sending failed, return the error.
        if (knownUnverified && sendRes != null && !sendRes.isSuccess()) {
            String msg = sendRes.getError() == null ? "Could not send verification email. Please try again." : sendRes.getError();
            if (isAjax(request)) {
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> payload = new HashMap<>();
                payload.put("success", false);
                payload.put("error", msg);
                response.getWriter().write(JsonUtil.obj(payload));
                return;
            }
            request.setAttribute("pendingEmail", normalizedEmail);
            request.setAttribute("error", msg);
            request.getRequestDispatcher("/jsp/auth/verify_email.jsp").forward(request, response);
            return;
        }

        if (isAjax(request)) {
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> payload = new HashMap<>();
            payload.put("success", true);
            payload.put("message", genericSuccess);
            payload.put("redirect", buildVerifyRedirect(request, normalizedEmail, genericSuccess));
            response.getWriter().write(JsonUtil.obj(payload));
            return;
        }

        request.setAttribute("pendingEmail", normalizedEmail);
        request.setAttribute("success", genericSuccess);
        request.getRequestDispatcher("/jsp/auth/verify_email.jsp").forward(request, response);
    }

    private String buildVerifyRedirect(HttpServletRequest request, String email, String message) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase();
        String safeMessage = message == null ? "" : message;
        return request.getContextPath()
                + "/auth/verify-email?email=" + URLEncoder.encode(normalizedEmail, StandardCharsets.UTF_8)
                + "&msg=" + URLEncoder.encode(safeMessage, StandardCharsets.UTF_8);
    }
}
