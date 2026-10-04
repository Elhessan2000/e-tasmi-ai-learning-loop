package controller.student;

import model.service.quranpedia.QuranpediaService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Quranpedia diagnostics. {@code ?live=1} calls the documented catalogue and
 * one-ayah translation endpoints. The response contains counts and field names, not passage text.
 */
@WebServlet(name = "StudentQuranpediaHealthServlet", urlPatterns = {"/student/api/quranpedia/health"})
public class StudentQuranpediaHealthServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        String live = request.getParameter("live");
        boolean runLive = "1".equals(live) || "true".equalsIgnoreCase(live == null ? "" : live.trim());
        response.getWriter().write(new QuranpediaService().healthJson(runLive));
    }
}
