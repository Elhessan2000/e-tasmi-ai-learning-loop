package controller.student;

import model.service.quran.QuranLibraryVersesService;
import util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Student JSON proxy for a single verse by Quran Foundation verse key ({@code chapter:verse}), used to lazy-load a
 * longer tafsir excerpt without merging full commentary into merged-chapter payloads.
 */
@WebServlet(name = "StudentQuranLibraryVerseByKeyServlet",
        urlPatterns = {"/student/api/quran-library/verses/by-key"})
public class StudentQuranLibraryVerseByKeyServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(StudentQuranLibraryVerseByKeyServlet.class.getName());

    private final QuranLibraryVersesService versesService = new QuranLibraryVersesService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "private, max-age=300");

        String verseKeyRaw = request.getParameter("verse_key");
        if (verseKeyRaw == null || verseKeyRaw.isBlank()) {
            verseKeyRaw = request.getParameter("verseKey");
        }

        String verseKeySanitized;
        try {
            verseKeySanitized = QuranLibraryVersesService.sanitizeVerseKey(verseKeyRaw);
        } catch (IOException ex) {
            mapVerseKeySanitize(response, ex);
            return;
        }

        String translationsCsv;
        try {
            translationsCsv = resolveTranslationsCsv(request);
        } catch (IOException ex) {
            mapResourceCsvSanitizeError(response, ex, "translations");
            return;
        }

        String tafsirsCsv;
        try {
            tafsirsCsv = QuranLibraryVersesService.sanitizeTafsirsCsv(request.getParameter("tafsirs"));
        } catch (IOException ex) {
            mapResourceCsvSanitizeError(response, ex, "tafsirs");
            return;
        }
        if (tafsirsCsv == null) {
            badRequest(response, "tafsirs_required");
            return;
        }

        Map<String, Object> verseRow;
        try {
            verseRow = versesService.fetchVerseByKey(verseKeySanitized, translationsCsv, tafsirsCsv);
        } catch (IOException bad) {
            if ("chapter out of range".equals(bad.getMessage())) {
                badRequest(response, "invalid_chapter");
                return;
            }
            LOGGER.log(Level.WARNING, "Quran Library verse by key unavailable", bad);
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", Boolean.FALSE);
            err.put("error", "load_failed");
            response.getWriter().write(JsonUtil.obj(err));
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", Boolean.TRUE);
        payload.put("verseKey", verseKeySanitized);
        payload.put("verse", verseRow);
        if (translationsCsv != null) {
            payload.put("translationResourceIds", translationsCsv);
        }
        payload.put("tafsirResourceIds", tafsirsCsv);

        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(JsonUtil.obj(payload));
    }

    private static void mapVerseKeySanitize(HttpServletResponse response, IOException ex) throws IOException {
        String msg = ex.getMessage();
        if ("verse_key required".equals(msg)) {
            badRequest(response, "verse_key_required");
            return;
        }
        if ("invalid verse_key".equals(msg)) {
            badRequest(response, "invalid_verse_key");
            return;
        }
        if ("verse_key out of range".equals(msg)) {
            badRequest(response, "verse_key_out_of_range");
            return;
        }
        badRequest(response, "invalid_request");
    }

    private static String resolveTranslationsCsv(HttpServletRequest request) throws IOException {
        String tr = request.getParameter("translations");
        if ((tr == null || tr.isBlank()) && translationFlag(request.getParameter("translate"))) {
            tr = "131";
        }
        return QuranLibraryVersesService.sanitizeTranslationsCsv(tr);
    }

    private static boolean translationFlag(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String v = raw.trim().toLowerCase();
        return "1".equals(v) || "true".equals(v) || "yes".equals(v);
    }

    private static void mapResourceCsvSanitizeError(HttpServletResponse response, IOException ex, String kind)
            throws IOException {
        String msg = ex.getMessage();
        if ("translations".equals(kind)) {
            if ("translations param too long".equals(msg)) {
                badRequest(response, "translations_param_too_long");
                return;
            }
            if ("invalid translations param".equals(msg)) {
                badRequest(response, "invalid_translations_param");
                return;
            }
        }
        if ("tafsirs".equals(kind)) {
            if ("tafsirs param too long".equals(msg)) {
                badRequest(response, "tafsirs_param_too_long");
                return;
            }
            if ("invalid tafsirs param".equals(msg)) {
                badRequest(response, "invalid_tafsirs_param");
                return;
            }
        }
        badRequest(response, "invalid_request");
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
