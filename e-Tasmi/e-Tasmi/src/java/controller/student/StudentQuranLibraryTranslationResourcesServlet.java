package controller.student;

import model.service.quran.QuranLibraryUpstreamErrors;
import model.service.quran.QuranLibraryTranslationResourcesService;
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
 * Student JSON proxy for translation edition catalogue ({@link QuranLibraryTranslationResourcesService}).
 */
@WebServlet(name = "StudentQuranLibraryTranslationResourcesServlet",
        urlPatterns = {"/student/api/quran-library/resources/translations"})
public class StudentQuranLibraryTranslationResourcesServlet extends HttpServlet {

    private static final Logger LOGGER =
            Logger.getLogger(StudentQuranLibraryTranslationResourcesServlet.class.getName());

    private final QuranLibraryTranslationResourcesService resourcesService =
            new QuranLibraryTranslationResourcesService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "private, max-age=600");

        String languageParam = request.getParameter("language");
        try {
            QuranLibraryTranslationResourcesService.sanitizeLanguage(languageParam);
        } catch (IOException ex) {
            badRequest(response, "invalid_language_param");
            return;
        }

        try {
            List<Map<String, Object>> translationResources =
                    resourcesService.fetchTranslationResources(languageParam);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ok", Boolean.TRUE);
            payload.put("translations", translationResources);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(JsonUtil.obj(payload));
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Quran Library translation resources unavailable", ex);
            String code = QuranLibraryUpstreamErrors.classify(ex);
            int sc = QuranLibraryUpstreamErrors.servletStatus(code, -1);
            response.setStatus(sc);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", Boolean.FALSE);
            err.put("error", code);
            err.put("message", QuranLibraryUpstreamErrors.contextualMessage(code, "translations"));
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
