package controller.student;

import model.service.quran.QuranLibraryChaptersService;
import model.service.quran.QuranLibraryUpstreamErrors;
import util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Student-facing JSON proxy for Quran Foundation chapters list only.
 * No secrets or bearer tokens returned.
 */
@WebServlet(name = "StudentQuranLibraryChaptersServlet", urlPatterns = {"/student/api/quran-library/chapters"})
public class StudentQuranLibraryChaptersServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(StudentQuranLibraryChaptersServlet.class.getName());

    private final QuranLibraryChaptersService chaptersService = new QuranLibraryChaptersService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "private, max-age=300");

        try {
            List<Map<String, Object>> chapters = chaptersService.fetchChapters();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ok", Boolean.TRUE);
            payload.put("chapters", chapters);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(JsonUtil.obj(payload));
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Quran Library chapters unavailable", ex);
            String code = QuranLibraryUpstreamErrors.classify(ex);
            int sc = QuranLibraryUpstreamErrors.servletStatus(code, -1);
            response.setStatus(sc);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", Boolean.FALSE);
            err.put("error", code);
            err.put("message", QuranLibraryUpstreamErrors.contextualMessage(code, "chapters"));
            response.getWriter().write(JsonUtil.obj(err));
        }
    }
}
