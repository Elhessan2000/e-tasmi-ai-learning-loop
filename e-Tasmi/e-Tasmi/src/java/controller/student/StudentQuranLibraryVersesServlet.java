package controller.student;

import model.service.quran.QuranLibraryUpstreamErrors;
import model.service.quran.QuranLibraryVersesService;
import model.service.quran.QuranVersesParse;
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
 * Student JSON proxy for verses by surah ({@code merged=1}, default), or a single upstream page ({@code page},
 * {@code per_page}). Optional {@code translations} (comma-separated resource IDs) forwards to Quran Foundation.
 * {@code translate=1} with {@code translations} omitted defaults to {@code translations=131} (see QF docs).
 * Optional {@code tafsirs}: comma-separated tafsir resource IDs (QF verse payload may include snippet HTML).
 */
@WebServlet(name = "StudentQuranLibraryVersesServlet", urlPatterns = {"/student/api/quran-library/verses/by-chapter"})
public class StudentQuranLibraryVersesServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(StudentQuranLibraryVersesServlet.class.getName());

    private final QuranLibraryVersesService versesService = new QuranLibraryVersesService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "private, max-age=120");

        int chapterNumber;
        String chapterRaw = request.getParameter("chapter");
        if (chapterRaw == null || chapterRaw.isBlank()) {
            badRequest(response, "chapter_required");
            return;
        }
        try {
            chapterNumber = Integer.parseInt(chapterRaw.trim());
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

        boolean merged = mergeFlag(request.getParameter("merged"));
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
        QuranVersesParse.VersesSlice slice;
        try {
            if (merged) {
                slice = versesService.fetchMergedChapter(chapterNumber, translationsCsv, tafsirsCsv);
            } else {
                int page = parsePage(request.getParameter("page"));
                int perPage = parsePerPage(request.getParameter("per_page"));
                slice = versesService.fetchPage(chapterNumber, page, perPage, translationsCsv, tafsirsCsv);
            }
        } catch (NumberFormatException nfe) {
            badRequest(response, "invalid_numeric_param");
            return;
        } catch (IOException bad) {
            if ("chapter out of range".equals(bad.getMessage())
                    || "page must be >= 1".equals(bad.getMessage())
                    || "per_page out of range".equals(bad.getMessage())
                    || "page_out_of_range".equals(bad.getMessage())) {
                badRequest(response, bad.getMessage().replace(' ', '_'));
                return;
            }
            LOGGER.log(Level.WARNING, "Quran Library verses unavailable", bad);
            String errCode = QuranLibraryUpstreamErrors.classify(bad);
            int sc = QuranLibraryUpstreamErrors.servletStatus(errCode, -1);
            response.setStatus(sc);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", Boolean.FALSE);
            err.put("error", errCode);
            err.put("message", QuranLibraryUpstreamErrors.contextualMessage(errCode, "verses"));
            response.getWriter().write(JsonUtil.obj(err));
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", Boolean.TRUE);
        payload.put("chapterNumber", chapterNumber);
        payload.put("merged", Boolean.valueOf(merged));
        payload.put("verses", slice.verses());
        if (translationsCsv != null) {
            payload.put("translationResourceIds", translationsCsv);
        }
        if (tafsirsCsv != null) {
            payload.put("tafsirResourceIds", tafsirsCsv);
        }
        if (!slice.pagination().isEmpty()) {
            payload.put("pagination", slice.pagination());
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(JsonUtil.obj(payload));
    }

    /** When {@code translate} is truthy and {@code translations} is omitted, default resource id {@code 131}. */
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

    private static boolean mergeFlag(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }
        String v = raw.trim().toLowerCase();
        return !("0".equals(v) || "false".equals(v) || "no".equals(v));
    }

    /** Maps IOException from {@link QuranLibraryVersesService#sanitizeTranslationsCsv(String)} /
     * {@link QuranLibraryVersesService#sanitizeTafsirsCsv(String)} before JSON 400 bodies. */
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

    private static int parsePage(String raw) throws IOException {
        if (raw == null || raw.isBlank()) {
            return 1;
        }
        int n = Integer.parseInt(raw.trim());
        if (n < 1 || n > 10_000) {
            throw new IOException("page_out_of_range");
        }
        return n;
    }

    private static int parsePerPage(String raw) throws IOException {
        if (raw == null || raw.isBlank()) {
            return 50;
        }
        int n = Integer.parseInt(raw.trim());
        if (n < 1 || n > 286) {
            throw new IOException("per_page out of range");
        }
        return n;
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
