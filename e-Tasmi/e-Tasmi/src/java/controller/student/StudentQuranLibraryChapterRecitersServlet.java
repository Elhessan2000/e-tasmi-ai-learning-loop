package controller.student;

import model.service.quran.QuranLibraryChapterRecitationService;
import model.service.quran.QuranLibraryUpstreamErrors;
import util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "StudentQuranLibraryChapterRecitersServlet",
        urlPatterns = {"/student/api/quran-library/audio/chapter-reciters"})
public class StudentQuranLibraryChapterRecitersServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(StudentQuranLibraryChapterRecitersServlet.class.getName());

    private final QuranLibraryChapterRecitationService recitationService =
            new QuranLibraryChapterRecitationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "private, max-age=600");
        try {
            HttpResponse<String> upstream = recitationService.fetchChapterRecitersCatalogRaw();
            int code = upstream.statusCode();
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            if (code >= HttpServletResponse.SC_OK && code < HttpServletResponse.SC_MULTIPLE_CHOICES) {
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write(QuranLibraryChapterRecitationService.cappedRelayBody(upstream));
                return;
            }

            LOGGER.log(Level.WARNING, "Quran Library chapter_reciters upstream HTTP {0}", code);
            String errCode =
                    code == HttpServletResponse.SC_FORBIDDEN
                            ? "upstream_forbidden"
                            : code == HttpServletResponse.SC_NOT_FOUND ? "upstream_not_found" : "upstream_error";
            int outStatus =
                    code == HttpServletResponse.SC_FORBIDDEN
                            ? HttpServletResponse.SC_FORBIDDEN
                            : HttpServletResponse.SC_BAD_GATEWAY;
            response.setStatus(outStatus);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", Boolean.FALSE);
            err.put("error", errCode);
            err.put(
                    "message",
                    QuranLibraryUpstreamErrors.contextualMessage(errCode, "reciter_catalog"));
            err.put("upstreamStatus", Integer.valueOf(code));
            response.getWriter().write(JsonUtil.obj(err));
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Quran Library chapter_reciters proxy failed", ex);
            String ec = QuranLibraryUpstreamErrors.classify(ex);
            response.setStatus(QuranLibraryUpstreamErrors.servletStatus(ec, -1));
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", Boolean.FALSE);
            err.put("error", ec);
            err.put(
                    "message",
                    QuranLibraryUpstreamErrors.contextualMessage(ec, "reciter_catalog"));
            response.getWriter().write(JsonUtil.obj(err));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}
