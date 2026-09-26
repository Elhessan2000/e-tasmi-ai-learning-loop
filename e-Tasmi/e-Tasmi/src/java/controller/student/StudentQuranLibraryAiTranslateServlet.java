package controller.student;

import model.service.quran.QuranAiTranslationService;
import util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Student-facing AI fallback endpoint for ayah translations.
 * <p>
 * POST body: {@code {"verses":[{"verseKey":"1:1","arabic":"..."}, ...]}}<br>
 * Response: {@code {"ok":true,"translations":{"1:1":"In the name of Allah..."}}}
 * <p>
 * Used when Quran Foundation does not return a translation (entitlement / restricted /
 * missing). The front-end calls this lazily when the Translate toggle is ON and any verse
 * has no {@code translationText}. Results are cached server-side in
 * {@link QuranAiTranslationService} so subsequent loads are instant.
 */
@WebServlet(
        name = "StudentQuranLibraryAiTranslateServlet",
        urlPatterns = {"/student/api/quran-library/translate-ai"})
public class StudentQuranLibraryAiTranslateServlet extends HttpServlet {

    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024; // 2 MB hard cap
    private static final int MAX_VERSES_PER_REQUEST = 320;

    private static final Pattern VERSE_KEY = Pattern.compile("^\\d{1,3}:\\d{1,3}$");

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        String raw;
        try {
            raw = readBody(request);
        } catch (IOException ex) {
            badRequest(response, "body_too_large");
            return;
        }
        if (raw == null || raw.isBlank()) {
            badRequest(response, "empty_body");
            return;
        }

        List<QuranAiTranslationService.Ayah> ayahs;
        try {
            ayahs = parseAyahs(raw);
        } catch (IllegalArgumentException ex) {
            badRequest(response, ex.getMessage());
            return;
        }
        if (ayahs.isEmpty()) {
            okEmpty(response);
            return;
        }

        QuranAiTranslationService svc = QuranAiTranslationService.instance();
        if (!svc.configured()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", Boolean.FALSE);
            err.put("error", "ai_not_configured");
            err.put("message", "OpenAI fallback translation is not configured on this server.");
            response.getWriter().write(JsonUtil.obj(err));
            return;
        }

        Map<String, String> translations;
        try {
            translations = svc.translate(ayahs);
        } catch (IOException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", Boolean.FALSE);
            err.put("error", "ai_upstream_failed");
            err.put("message", "Could not reach the AI translation provider.");
            response.getWriter().write(JsonUtil.obj(err));
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", Boolean.TRUE);
        payload.put("source", "openai");
        payload.put("translations", translations);
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(JsonUtil.obj(payload));
    }

    private static void okEmpty(HttpServletResponse response) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", Boolean.TRUE);
        payload.put("translations", new LinkedHashMap<String, String>());
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(JsonUtil.obj(payload));
    }

    private static String readBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = request.getReader()) {
            char[] buf = new char[4096];
            int total = 0;
            int read;
            while ((read = r.read(buf)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) {
                    throw new IOException("body too large");
                }
                sb.append(buf, 0, read);
            }
        }
        return sb.toString();
    }

    /**
     * Naive but tolerant parser for the {@code verses} array in the JSON body.
     * Expects {@code verses:[{verseKey,arabic}, ...]}; allows extra fields and any key order.
     */
    private static List<QuranAiTranslationService.Ayah> parseAyahs(String body) {
        int idx = body.indexOf("\"verses\"");
        if (idx < 0) throw new IllegalArgumentException("verses_missing");
        int open = body.indexOf('[', idx);
        if (open < 0) throw new IllegalArgumentException("verses_missing");
        String inner = extractBracket(body, open);
        if (inner == null) throw new IllegalArgumentException("verses_malformed");
        List<String> objects = splitTopLevel(inner);
        if (objects.size() > MAX_VERSES_PER_REQUEST) {
            throw new IllegalArgumentException("too_many_verses");
        }
        List<QuranAiTranslationService.Ayah> out = new ArrayList<>(objects.size());
        for (String obj : objects) {
            String key = extractStringField(obj, "verseKey");
            if (key == null) key = extractStringField(obj, "verse_key");
            String ar = extractStringField(obj, "arabic");
            if (ar == null) ar = extractStringField(obj, "text");
            if (key == null || ar == null) continue;
            key = key.trim();
            ar = ar.trim();
            if (key.isEmpty() || ar.isEmpty()) continue;
            if (!VERSE_KEY.matcher(key).matches()) continue;
            out.add(new QuranAiTranslationService.Ayah(key, ar));
        }
        return out;
    }

    private static String extractBracket(String s, int openIdx) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return s.substring(openIdx + 1, i);
            }
        }
        return null;
    }

    private static List<String> splitTopLevel(String inner) {
        List<String> out = new ArrayList<>();
        int depth = 0, start = -1;
        boolean inString = false, escape = false;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(inner.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return out;
    }

    private static String extractStringField(String obj, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher m = p.matcher(obj);
        if (!m.find()) return null;
        return unescape(m.group(1));
    }

    private static String unescape(String raw) {
        if (raw == null) return null;
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                char n = raw.charAt(i + 1);
                switch (n) {
                    case '"':  sb.append('"');  i++; continue;
                    case '\\': sb.append('\\'); i++; continue;
                    case '/':  sb.append('/');  i++; continue;
                    case 'n':  sb.append('\n'); i++; continue;
                    case 'r':  sb.append('\r'); i++; continue;
                    case 't':  sb.append('\t'); i++; continue;
                    case 'b':  sb.append('\b'); i++; continue;
                    case 'f':  sb.append('\f'); i++; continue;
                    case 'u':
                        if (i + 5 < raw.length()) {
                            try {
                                int cp = Integer.parseInt(raw.substring(i + 2, i + 6), 16);
                                sb.append((char) cp);
                                i += 5;
                                continue;
                            } catch (NumberFormatException ignored) { /* fall through */ }
                        }
                        break;
                    default: break;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static void badRequest(HttpServletResponse response, String code) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("ok", Boolean.FALSE);
        err.put("error", code);
        response.getWriter().write(JsonUtil.obj(err));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}
