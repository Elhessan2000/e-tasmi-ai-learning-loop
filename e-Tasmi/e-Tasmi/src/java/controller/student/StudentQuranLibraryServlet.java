package controller.student;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Student Quran Library module shell — full UI deferred.
 * Route: {@code /student/quran-library}
 */
@WebServlet(name = "StudentQuranLibraryServlet", urlPatterns = {"/student/quran-library"})
public class StudentQuranLibraryServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setAttribute("activeMenu", "quran-library");
        request.getRequestDispatcher("/jsp/student/quran_library.jsp").forward(request, response);
    }
}
