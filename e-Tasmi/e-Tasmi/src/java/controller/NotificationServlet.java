package controller;

import model.service.NotificationService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebServlet(name = "NotificationServlet", urlPatterns = {"/notifications"})
public class NotificationServlet extends HttpServlet {
    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = readUserId(session);
        String role = session == null || session.getAttribute("role") == null
                ? ""
                : String.valueOf(session.getAttribute("role"));

        if ("STUDENT".equalsIgnoreCase(role)) {
            response.sendRedirect(request.getContextPath() + "/student/notifications");
            return;
        }

        request.setAttribute("activeMenu", "notifications");
        request.setAttribute("role", role);
        request.setAttribute("notifications", notificationService.listForUser(userId));
        request.getRequestDispatcher("/jsp/common/notifications.jsp").forward(request, response);
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
