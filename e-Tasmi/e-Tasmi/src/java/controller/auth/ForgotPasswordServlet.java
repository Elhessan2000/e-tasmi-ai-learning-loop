package controller.auth;

import model.service.PasswordResetService;
import model.service.ServiceResult;
import util.JsonUtil;
import util.RateLimiter;
import util.UrlUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@WebServlet(name = "ForgotPasswordServlet", urlPatterns = {"/auth/forgot-password"})
public class ForgotPasswordServlet extends HttpServlet {
    private final PasswordResetService passwordResetService = new PasswordResetService();

    private boolean isAjax(HttpServletRequest request) {
        String xrw = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        return "XMLHttpRequest".equalsIgnoreCase(xrw) || (accept != null && accept.contains("application/json"));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.getRequestDispatcher("/jsp/auth/forgot_password.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String email = request.getParameter("email");
        if (email != null) {
            email = email.trim();
        }
        String ip = request.getRemoteAddr();
        if (!RateLimiter.allow("forgot:" + ip, 10, Duration.ofMinutes(10))) {
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
            request.getRequestDispatcher("/jsp/auth/forgot_password.jsp").forward(request, response);
            return;
        }

        ServiceResult sr = passwordResetService.requestReset(getServletContext(), email, UrlUtil.getBaseUrl(request));
        if (isAjax(request)) {
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> payload = new HashMap<>();
            payload.put("success", sr.isSuccess());
            if (sr.isSuccess()) {
                payload.put("message", sr.getMessage());
            } else {
                payload.put("error", sr.getError());
            }
            response.getWriter().write(JsonUtil.obj(payload));
            return;
        }

        if (sr.isSuccess()) {
            request.setAttribute("success", sr.getMessage());
        } else {
            request.setAttribute("error", sr.getError());
        }
        request.getRequestDispatcher("/jsp/auth/forgot_password.jsp").forward(request, response);
    }
}
