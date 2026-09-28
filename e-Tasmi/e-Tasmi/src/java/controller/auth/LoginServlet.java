package controller.auth;

import model.service.AuthResult;
import model.service.AuthService;
import util.DevMode;
import util.JsonUtil;
import util.RateLimiter;
import util.SessionUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@WebServlet(name = "LoginServlet", urlPatterns = {"/auth/login"})
public class LoginServlet extends HttpServlet {
    private final AuthService authService = new AuthService();

    private boolean isAjax(HttpServletRequest request) {
        String xrw = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        return "XMLHttpRequest".equalsIgnoreCase(xrw) || (accept != null && accept.contains("application/json"));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Avoid stale login.jsp in browsers and intermediaries after deploys.
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0);

        if ("1".equals(request.getParameter("expired"))) {
            request.setAttribute("error", "Session expired. Please log in again.");
        }
        if ("1".equals(request.getParameter("verified"))) {
            request.setAttribute("success", "Email verified successfully. You can now log in.");
        }
        if ("1".equals(request.getParameter("registered"))) {
            request.setAttribute("success", "Registration successful. You can log in now.");
        }
        if ("1".equals(request.getParameter("pending"))) {
            request.setAttribute("success", "Registration successful. Your instructor account is pending admin approval.");
        }
        request.getRequestDispatcher("/jsp/auth/login.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String email = request.getParameter("email");
        String password = request.getParameter("password");

        String ip = request.getRemoteAddr();
        String normalizedEmail = (email == null) ? "" : email.trim().toLowerCase();
        String key = "login:" + ip + ":" + normalizedEmail;

        // Development mode (localhost or ETASMI_DISABLE_RATE_LIMIT=true): never lock out while
        // testing, and proactively clear any existing lockout so testing cycles aren't blocked.
        boolean devBypass = DevMode.isRateLimitBypassed(request);
        if (devBypass) {
            RateLimiter.clear(key);
        }

        if (!devBypass && !RateLimiter.allow(key, 5, Duration.ofMinutes(5))) {
            if (isAjax(request)) {
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> payload = new HashMap<>();
                payload.put("success", false);
                payload.put("error", "Too many login attempts. Please try again later.");
                response.getWriter().write(JsonUtil.obj(payload));
                return;
            }
            request.setAttribute("emailValue", email);
            request.setAttribute("error", "Too many login attempts. Please try again later.");
            request.getRequestDispatcher("/jsp/auth/login.jsp").forward(request, response);
            return;
        }

        AuthResult result = authService.login(email, password == null ? null : password.toCharArray());
        if (!result.isSuccess()) {
            if (isAjax(request)) {
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> payload = new HashMap<>();
                payload.put("success", false);
                payload.put("error", result.getError());
                payload.put("emailValue", email);
                response.getWriter().write(JsonUtil.obj(payload));
                return;
            }

            request.setAttribute("emailValue", email);
            request.setAttribute("error", result.getError());
            request.getRequestDispatcher("/jsp/auth/login.jsp").forward(request, response);
            return;
        }

        // Successful login: reset the attempt counter so future logins start clean.
        RateLimiter.clear(key);

        String role = result.getUser().getRole() == null ? null : result.getUser().getRole().name();

        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = SessionUtil.createUserSession(
                request,
                result.getUser().getUserId(),
                role,
                result.getUser().getFullName()
        );
        session.setAttribute("email", result.getUser().getEmail());
        if (result.getInstructorVerificationStatus() != null) {
            session.setAttribute("instructorVerificationStatus", result.getInstructorVerificationStatus());
        }

        String ctx = request.getContextPath();
        String redirect = "ADMIN".equalsIgnoreCase(role) ? ctx + "/admin/dashboard" : ctx + "/dashboard";
        if (isAjax(request)) {
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> payload = new HashMap<>();
            payload.put("success", true);
            payload.put("redirect", redirect);
            response.getWriter().write(JsonUtil.obj(payload));
            return;
        }
        response.sendRedirect(redirect);
    }
}
