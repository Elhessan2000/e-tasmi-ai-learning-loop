package controller.student;

import model.service.quran.QuranAssistantService;
import util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Student Quran Assistant chat API.
 * POST {@code {"mode":"tafsir","message":"2:255","history":[...],"context":{...}}}
 */
@WebServlet(
        name = "StudentQuranAssistantServlet",
        urlPatterns = {"/student/api/quran-assistant/chat"})
public class StudentQuranAssistantServlet extends HttpServlet {

    private static final int MAX_BODY_BYTES = 64 * 1024;
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

        String mode;
        String message;
        List<QuranAssistantService.ChatMessage> history;
        QuranAssistantService.QuranContext context;
        try {
            mode = extractStringField(raw, "mode");
            message = extractStringField(raw, "message");
            history = parseHistory(raw);
            context = parseContext(raw);
        } catch (IllegalArgumentException ex) {
            badRequest(response, ex.getMessage());
            return;
        }

        if (message == null || message.isBlank()) {
            badRequest(response, "message_required");
            return;
        }

        QuranAssistantService svc = QuranAssistantService.instance();
        if (!svc.configured()) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", Boolean.FALSE);
            err.put("error", "ai_not_configured");
            err.put("message", "Quran Assistant is not configured on this server. Please contact your administrator.");
            response.getWriter().write(JsonUtil.obj(err));
            return;
        }

        try {
            QuranAssistantService.ChatResult result = svc.chat(mode, message, history, context);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ok", Boolean.TRUE);
            payload.put("reply", result.reply);
            payload.put("mode", mode == null ? "general" : mode.trim().toLowerCase());
            payload.put("scopeReminder", Boolean.valueOf(result.scopeReminder));
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(JsonUtil.obj(payload));
        } catch (IllegalArgumentException ex) {
            badRequest(response, ex.getMessage());
        } catch (IOException ex) {
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", Boolean.FALSE);
            err.put("error", "ai_upstream_failed");
            err.put("message", "Could not reach the Quran Assistant service. Please try again shortly.");
            response.getWriter().write(JsonUtil.obj(err));
        }
    }

    private static List<QuranAssistantService.ChatMessage> parseHistory(String body) {
        List<QuranAssistantService.ChatMessage> out = new ArrayList<>();
        int idx = body.indexOf("\"history\"");
        if (idx < 0) return out;
        int open = body.indexOf('[', idx);
        if (open < 0) return out;
        String inner = extractBracket(body, open);
        if (inner == null) return out;
        List<String> objects = splitTopLevel(inner);
        for (String obj : objects) {
            String role = extractStringField(obj, "role");
            String content = extractStringField(obj, "content");
            if (role == null || content == null) continue;
            out.add(new QuranAssistantService.ChatMessage(role, content));
        }
        return out;
    }

    private static QuranAssistantService.QuranContext parseContext(String body) {
        int idx = body.indexOf("\"context\"");
        if (idx < 0) return null;
        int open = body.indexOf('{', idx);
        if (open < 0) return null;
        String obj = extractBrace(body, open);
        if (obj == null) return null;

        QuranAssistantService.QuranContext ctx = new QuranAssistantService.QuranContext();
        ctx.surahName = extractStringField(obj, "surahName");
        if (ctx.surahName == null) ctx.surahName = extractStringField(obj, "surah_name");
        ctx.surahNameArabic = extractStringField(obj, "surahNameArabic");
        if (ctx.surahNameArabic == null) ctx.surahNameArabic = extractStringField(obj, "surah_name_arabic");
        ctx.verseKey = extractStringField(obj, "verseKey");
        if (ctx.verseKey == null) ctx.verseKey = extractStringField(obj, "verse_key");

        String surahIdStr = extractStringField(obj, "surahId");
        if (surahIdStr == null) surahIdStr = extractStringField(obj, "surah_id");
        if (surahIdStr != null) {
            try { ctx.surahId = (int) Math.round(Double.parseDouble(surahIdStr.trim())); } catch (NumberFormatException ignored) { }
        }

        String ayahStr = extractStringField(obj, "ayahNumber");
        if (ayahStr == null) ayahStr = extractStringField(obj, "ayah_number");
        if (ayahStr != null) {
            try { ctx.ayahNumber = (int) Math.round(Double.parseDouble(ayahStr.trim())); } catch (NumberFormatException ignored) { }
        }

        if (ctx.verseKey != null && VERSE_KEY.matcher(ctx.verseKey.trim()).matches()) {
            String[] parts = ctx.verseKey.split(":");
            if (ctx.surahId <= 0) {
                try { ctx.surahId = Integer.parseInt(parts[0]); } catch (NumberFormatException ignored) { }
            }
            if (ctx.ayahNumber <= 0) {
                try { ctx.ayahNumber = Integer.parseInt(parts[1]); } catch (NumberFormatException ignored) { }
            }
        }

        return ctx.hasData() ? ctx : null;
    }

    private static String readBody(HttpServletRequest request) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = request.getReader()) {
            char[] buf = new char[4096];
            int total = 0;
            int read;
            while ((read = r.read(buf)) != -1) {
                total += read;
                if (total > MAX_BODY_BYTES) throw new IOException("body too large");
                sb.append(buf, 0, read);
            }
        }
        return sb.toString();
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

    private static String extractBrace(String s, int openIdx) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return s.substring(openIdx, i + 1);
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
                    case '"': sb.append('"'); i++; continue;
                    case '\\': sb.append('\\'); i++; continue;
                    case '/': sb.append('/'); i++; continue;
                    case 'n': sb.append('\n'); i++; continue;
                    case 'r': sb.append('\r'); i++; continue;
                    case 't': sb.append('\t'); i++; continue;
                    case 'b': sb.append('\b'); i++; continue;
                    case 'f': sb.append('\f'); i++; continue;
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
