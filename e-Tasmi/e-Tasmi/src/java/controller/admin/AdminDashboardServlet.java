package controller.admin;

import model.dao.UserDao;
import model.dao.impl.UserDaoJdbc;
import model.entity.ReportSummary;
import model.service.InstructorVerificationService;
import model.service.ReportService;
import util.Db;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;

@WebServlet(name = "AdminDashboardServlet", urlPatterns = {"/admin/dashboard"})
public class AdminDashboardServlet extends HttpServlet {
    private final ReportService reportService = new ReportService();
    private final InstructorVerificationService instructorVerificationService = new InstructorVerificationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try (Connection conn = Db.getConnection()) {
            UserDao userDao = new UserDaoJdbc();
            int totalUsers = userDao.countUsersByRole(conn, null);
            int totalStudents = userDao.countUsersByRole(conn, "STUDENT");
            int totalInstructors = userDao.countUsersByRole(conn, "INSTRUCTOR");
            int pendingInstructors = instructorVerificationService.listPending().size();

            request.setAttribute("activeMenu", "overview");
            request.setAttribute("totalUsers", totalUsers);
            request.setAttribute("totalStudents", totalStudents);
            request.setAttribute("totalInstructors", totalInstructors);
            request.setAttribute("pendingInstructors", pendingInstructors);
        } catch (Exception ex) {
            request.setAttribute("activeMenu", "overview");
            request.setAttribute("error", "Failed to load dashboard stats.");
        }

        ReportSummary summary = reportService.loadSummary();
        request.setAttribute("summary", summary);
        long totalSessions = summary.getSessionsScheduled() + summary.getSessionsOngoing()
                + summary.getSessionsCompleted() + summary.getSessionsCancelled();
        long totalPayments = summary.getPaymentsPending() + summary.getPaymentsSuccess() + summary.getPaymentsFailed();
        long totalEnrollments = summary.getEnrollmentsPending() + summary.getEnrollmentsApproved()
                + summary.getEnrollmentsRejected() + summary.getEnrollmentsCancelled();
        request.setAttribute("totalSessions", totalSessions);
        request.setAttribute("totalPayments", totalPayments);
        request.setAttribute("totalEnrollments", totalEnrollments);

        request.getRequestDispatcher("/jsp/admin/dashboard.jsp").forward(request, response);
    }
}
