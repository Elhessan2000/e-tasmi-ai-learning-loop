package controller.student;

import model.service.quran.QuranFoundationClient;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Quran Foundation diagnostics: env snapshot (no secrets). Optional {@code ?live=1} runs token + chapters probe.
 */
@WebServlet(name = "StudentQuranFoundationHealthServlet", urlPatterns = {"/student/api/quran-foundation/health"})
public class StudentQuranFoundationHealthServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        String live = request.getParameter("live");
        boolean runLive = "1".equals(live) || "true".equalsIgnoreCase(trim(live));

        response.getWriter().write(QuranFoundationClient.healthAndLiveProbeJson(runLive));
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
