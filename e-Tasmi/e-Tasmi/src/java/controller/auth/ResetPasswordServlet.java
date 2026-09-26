package controller.auth;

import model.service.PasswordResetService;
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

@WebServlet(name = "ResetPasswordServlet", urlPatterns = {"/auth/reset-password"})
public class ResetPasswordServlet extends HttpServlet {
    private final PasswordResetService passwordResetService = new PasswordResetService();

    private boolean isAjax(HttpServletRequest request) {
        String xrw = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        return "XMLHttpRequest".equalsIgnoreCase(xrw) || (accept != null && accept.contains("application/json"));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String token = request.getParameter("token");
        ServiceResult validation = passwordResetService.validateResetToken(token);
        if (validation.isSuccess()) {
            request.setAttribute("token", token);
        } else {
            request.setAttribute("error", validation.getError());
        }
        request.getRequestDispatcher("/jsp/auth/reset_password.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String token = request.getParameter("token");
        String newPassword = request.getParameter("newPassword");
        String confirmPassword = request.getParameter("confirmPassword");

        if (newPassword == null || confirmPassword == null || !newPassword.equals(confirmPassword)) {
            String msg = "Passwords do not match";
            if (isAjax(request)) {
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> payload = new HashMap<>();
                payload.put("success", false);
                payload.put("error", msg);
                response.getWriter().write(JsonUtil.obj(payload));
                return;
            }
            request.setAttribute("error", msg);
            request.setAttribute("token", token);
            request.getRequestDispatcher("/jsp/auth/reset_password.jsp").forward(request, response);
            return;
        }

        ServiceResult sr = passwordResetService.resetPassword(token, newPassword.toCharArray());
        if (isAjax(request)) {
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> payload = new HashMap<>();
            payload.put("success", sr.isSuccess());
            if (sr.isSuccess()) {
                payload.put("message", sr.getMessage());
                payload.put("redirect", request.getContextPath() + "/auth/login");
            } else {
                payload.put("error", sr.getMessage());
            }
            response.getWriter().write(JsonUtil.obj(payload));
            return;
        }

        if (sr.isSuccess()) {
            request.setAttribute("success", sr.getMessage());
        } else {
            request.setAttribute("error", sr.getMessage());
            request.setAttribute("token", token);
        }
        request.getRequestDispatcher("/jsp/auth/reset_password.jsp").forward(request, response);
    }
}
