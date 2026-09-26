package controller.student;

import model.service.quran.QuranLibraryChapterRecitationService;
import model.service.quran.QuranLibraryUpstreamErrors;
import model.service.quran.QuranLibraryVersesService;
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

@WebServlet(name = "StudentQuranLibraryChapterAudioServlet",
        urlPatterns = {"/student/api/quran-library/audio/chapter-file"})
public class StudentQuranLibraryChapterAudioServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(StudentQuranLibraryChapterAudioServlet.class.getName());

    private final QuranLibraryChapterRecitationService recitationService =
            new QuranLibraryChapterRecitationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "private, max-age=300");
        response.setContentType("application/json;charset=UTF-8");

        int chapterNumber;
        try {
            chapterNumber = Integer.parseInt(trimOrEmpty(request.getParameter("chapter")));
        } catch (Exception ex) {
            badRequest(response, "chapter_required");
            return;
        }

        try {
            QuranLibraryVersesService.validateChapter(chapterNumber);
        } catch (IOException ex) {
            badRequest(response, "invalid_chapter");
            return;
        }

        long reciterId;
        try {
            reciterId = Long.parseLong(trimOrEmpty(request.getParameter("reciter_id")));
        } catch (Exception ex) {
            badRequest(response, "reciter_id_required");
            return;
        }

        try {
            HttpResponse<String> upstream = recitationService.fetchChapterReciterMp3Payload(chapterNumber,
                    reciterId);
            int code = upstream.statusCode();
            if (code < 200 || code >= 300) {
                LOGGER.log(Level.WARNING, "Quran Library chapter mp3 upstream HTTP {0}", code);
                String errCode =
                        code == HttpServletResponse.SC_FORBIDDEN
                                ? "upstream_forbidden"
                                : code == HttpServletResponse.SC_NOT_FOUND ? "upstream_not_found" : "upstream_error";
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("ok", Boolean.FALSE);
                err.put("error", errCode);
                err.put("message",
                        QuranLibraryUpstreamErrors.contextualMessage(errCode, "chapter_audio"));
                err.put("detail",
                        QuranLibraryUpstreamErrors.audioEntitlementHint(code));
                err.put("upstreamStatus", Integer.valueOf(code));
                int httpOut =
                        code == HttpServletResponse.SC_FORBIDDEN
                                ? HttpServletResponse.SC_FORBIDDEN
                                : HttpServletResponse.SC_BAD_GATEWAY;
                response.setStatus(httpOut);
                response.getWriter().write(JsonUtil.obj(err));
                return;
            }
            Map<String, Object> parsed = QuranLibraryChapterRecitationService.parseAudioPublicMap(upstream);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ok", Boolean.TRUE);
            payload.put("chapterNumber", chapterNumber);
            payload.put("reciterId", reciterId);
            payload.putAll(parsed);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(JsonUtil.obj(payload));
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Quran Library chapter mp3 parse failed", ex);
            String ec = QuranLibraryUpstreamErrors.classify(ex);
            response.setStatus(QuranLibraryUpstreamErrors.servletStatus(ec, -1));
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", Boolean.FALSE);
            err.put("error", ec);
            err.put("message", QuranLibraryUpstreamErrors.contextualMessage(ec, "chapter_audio"));
            response.getWriter().write(JsonUtil.obj(err));
        }
    }

    private static String trimOrEmpty(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static void badRequest(HttpServletResponse response, String code) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("ok", Boolean.FALSE);
        err.put("error", code);
        response.getWriter().write(JsonUtil.obj(err));
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}
