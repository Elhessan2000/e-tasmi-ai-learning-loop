package controller.student;

import model.service.quran.QuranLibraryTafsirResourcesService;
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
 * Student JSON proxy for tafsir edition catalogue ({@link QuranLibraryTafsirResourcesService}).
 */
@WebServlet(name = "StudentQuranLibraryTafsirResourcesServlet",
        urlPatterns = {"/student/api/quran-library/resources/tafsirs"})
public class StudentQuranLibraryTafsirResourcesServlet extends HttpServlet {

    private static final Logger LOGGER =
            Logger.getLogger(StudentQuranLibraryTafsirResourcesServlet.class.getName());

    private final QuranLibraryTafsirResourcesService resourcesService =
            new QuranLibraryTafsirResourcesService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "private, max-age=600");

        String languageParam = request.getParameter("language");
        try {
            QuranLibraryTafsirResourcesService.sanitizeLanguage(languageParam);
        } catch (IOException ex) {
            badRequest(response, "invalid_language_param");
            return;
        }

        try {
            List<Map<String, Object>> tafsirs = resourcesService.fetchTafsirResources(languageParam);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ok", Boolean.TRUE);
            payload.put("tafsirs", tafsirs);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(JsonUtil.obj(payload));
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Quran Library tafsir resources unavailable", ex);
            String code = QuranLibraryUpstreamErrors.classify(ex);
            int sc = QuranLibraryUpstreamErrors.servletStatus(code, -1);
            response.setStatus(sc);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", Boolean.FALSE);
            err.put("error", code);
            err.put("message", QuranLibraryUpstreamErrors.contextualMessage(code, "tafsirs"));
            response.getWriter().write(JsonUtil.obj(err));
        }
    }

    private static void badRequest(HttpServletResponse response, String code) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("ok", Boolean.FALSE);
        err.put("error", code);
        response.getWriter().write(JsonUtil.obj(err));
    }
}
