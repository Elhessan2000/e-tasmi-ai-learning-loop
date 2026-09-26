package controller.auth;

import model.service.EmailVerificationService;
import model.service.ServiceResult;
import util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet(name = "VerifyEmailServlet", urlPatterns = {"/auth/verify-email"})
public class VerifyEmailServlet extends HttpServlet {
    private final EmailVerificationService emailVerificationService = new EmailVerificationService();

    private boolean isAjax(HttpServletRequest request) {
        String xrw = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        return "XMLHttpRequest".equalsIgnoreCase(xrw) || (accept != null && accept.contains("application/json"));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String token = request.getParameter("token");
        if (token != null && !token.isBlank()) {
            ServiceResult res = emailVerificationService.verifyToken(token);
            if (res.isSuccess()) {
                request.setAttribute("message", res.getMessage());
                request.getRequestDispatcher("/jsp/auth/verify_success.jsp").forward(request, response);
                return;
            }
            request.setAttribute("error", res.getError());
            request.getRequestDispatcher("/jsp/auth/verify_failed.jsp").forward(request, response);
            return;
        }

        // Code-based verification page
        String email = request.getParameter("email");
        if (email != null && !email.isBlank()) {
            request.setAttribute("pendingEmail", email);
        }
        String msg = request.getParameter("msg");
        if (msg != null && !msg.isBlank()) {
            request.setAttribute("success", msg);
        }
        String err = request.getParameter("error");
        if (err != null && !err.isBlank()) {
            request.setAttribute("error", err);
        }
        request.getRequestDispatcher("/jsp/auth/verify_email.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String email = request.getParameter("email");
        String code = request.getParameter("code");

        ServiceResult res = emailVerificationService.verifyCode(email, code);
        if (res.isSuccess()) {
            if (isAjax(request)) {
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> payload = new HashMap<>();
                payload.put("success", true);
                payload.put("message", res.getMessage());
                payload.put("redirect", request.getContextPath() + "/auth/login?verified=1");
                response.getWriter().write(JsonUtil.obj(payload));
                return;
            }
            request.setAttribute("message", res.getMessage());
            request.getRequestDispatcher("/jsp/auth/verify_success.jsp").forward(request, response);
            return;
        }

        if (isAjax(request)) {
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> payload = new HashMap<>();
            payload.put("success", false);
            payload.put("error", res.getError());
            response.getWriter().write(JsonUtil.obj(payload));
            return;
        }

        request.setAttribute("pendingEmail", email);
        request.setAttribute("error", res.getError());
        request.getRequestDispatcher("/jsp/auth/verify_email.jsp").forward(request, response);
    }
}
