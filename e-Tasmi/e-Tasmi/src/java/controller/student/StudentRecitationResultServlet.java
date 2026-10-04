package controller.student;

import model.service.StudentRecitationResultPage;
import model.service.VerifiedLearningFocusService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;

/**
 * Student result for one recitation. Missing and unowned ids both return 404.
 * Focus and Practice Again are rendered only when the view says the evaluation is published.
 */
@WebServlet(name = "StudentRecitationResultServlet", urlPatterns = {"/student/recitation-result"})
public class StudentRecitationResultServlet extends HttpServlet {
    private final VerifiedLearningFocusService focusService = new VerifiedLearningFocusService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        long userId = readUserId(request.getSession(false));
        long recitationId = readLong(request.getParameter("id"));
        Optional<StudentRecitationResultPage> page = focusService.loadResultPage(userId, recitationId);
        if (page.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        request.setAttribute("activeMenu", "recitations");
        request.setAttribute("resultPage", page.get());
        request.setAttribute("result", page.get().getView());
        request.getRequestDispatcher("/jsp/student/recitation-result.jsp").forward(request, response);
    }

    private long readLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (Exception ignored) {
            return 0;
        }
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
