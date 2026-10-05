package controller.student;

import model.entity.Progress;
import model.entity.StudentProgressSummary;
import model.service.ProgressService;
import model.service.StudentProgressHistoryService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;

@WebServlet(name = "StudentProgressServlet", urlPatterns = {"/student/progress"})
public class StudentProgressServlet extends HttpServlet {
    private final ProgressService progressService = new ProgressService();
    private final StudentProgressHistoryService historyService = new StudentProgressHistoryService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = readUserId(session);

        Optional<Progress> progressOpt = progressService.getOrComputeForStudentUser(userId);
        progressOpt.ifPresent(p -> request.setAttribute("progress", p));
        StudentProgressSummary summary = progressService.buildSummaryForStudentUser(userId);
        request.setAttribute("progressSummary", summary);
        request.setAttribute("progressHistory", historyService.loadForStudentUser(userId));

        request.getRequestDispatcher("/jsp/student/progress.jsp").forward(request, response);
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
