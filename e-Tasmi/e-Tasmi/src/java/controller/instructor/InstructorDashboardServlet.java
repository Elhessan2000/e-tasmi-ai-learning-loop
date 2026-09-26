package controller.instructor;

import model.dao.InstructorDao;
import model.dao.impl.InstructorDaoJdbc;
import model.entity.InstructorDashboardStats;
import model.entity.Instructor;
import model.service.InstructorWorkspaceService;

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
import util.Db;

@WebServlet(name = "InstructorDashboardServlet", urlPatterns = {"/instructor/dashboard"})
public class InstructorDashboardServlet extends HttpServlet {
    private final InstructorDao instructorDao = new InstructorDaoJdbc();
    private final InstructorWorkspaceService workspaceService = new InstructorWorkspaceService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object userIdObj = session.getAttribute("userId");
            if (userIdObj instanceof Long) {
                long userId = (Long) userIdObj;
                refreshStatus(session, userId);
                request.setAttribute("dashboardStats", workspaceService.buildDashboard(userId));
            } else if (userIdObj instanceof Integer) {
                long userId = ((Integer) userIdObj).longValue();
                refreshStatus(session, userId);
                request.setAttribute("dashboardStats", workspaceService.buildDashboard(userId));
            }
        }

        request.getRequestDispatcher("/jsp/instructor/dashboard.jsp").forward(request, response);
    }

    private void refreshStatus(HttpSession session, long userId) {
        try (Connection connection = Db.getConnection()) {
            Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, userId);
            if (instructorOpt.isPresent() && instructorOpt.get().getVerificationStatus() != null) {
                session.setAttribute("instructorVerificationStatus", instructorOpt.get().getVerificationStatus().name());
            }
        } catch (SQLException ignored) {
            // keep existing session value
        }
    }
}
