package controller;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;

@WebServlet(name = "DashboardRouterServlet", urlPatterns = {"/dashboard"})
public class DashboardRouterServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            response.sendRedirect(request.getContextPath() + "/auth/login");
            return;
        }

        String role = (String) session.getAttribute("role");
        if (role == null) {
            response.sendRedirect(request.getContextPath() + "/auth/login");
            return;
        }

        switch (role.toUpperCase()) {
            case "STUDENT":
                // Use servlet so dashboard can load real data (counts, progress, etc.).
                request.getRequestDispatcher("/student/dashboard").forward(request, response);
                return;
            case "INSTRUCTOR":
                request.getRequestDispatcher("/instructor/dashboard").forward(request, response);
                return;
            case "ADMIN":
                request.getRequestDispatcher("/admin/dashboard").forward(request, response);
                return;
            default:
                response.sendRedirect(request.getContextPath() + "/auth/login");
        }
    }
}
