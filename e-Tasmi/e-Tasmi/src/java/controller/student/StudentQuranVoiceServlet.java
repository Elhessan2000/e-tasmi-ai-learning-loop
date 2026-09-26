package controller.student;

import model.service.quran.QuranAssistantService;
import model.service.quran.QuranVoiceService;
import util.JsonUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Student Quran Assistant voice API (turn-based STT -> GPT -> TTS pipeline).
 *
 * POST /student/api/quran-assistant/voice/turn  (multipart/form-data)
 *   fields: audio (blob), mode, history (JSON string), context (JSON string), lang
 *   -> { ok, transcript, reply, mode, scopeReminder, audio (base64 mp3), audioMime }
 *
 * POST /student/api/quran-assistant/voice/speak (application/json)
 *   body: { "text": "...", "lang": "en|ar" }
 *   -> { ok, audio (base64 mp3), audioMime }
 */
@WebServlet(
        name = "StudentQuranVoiceServlet",
        urlPatterns = {
                "/student/api/quran-assistant/voice/turn",
                "/student/api/quran-assistant/voice/speak"
        })
@MultipartConfig(
        fileSizeThreshold = 512 * 1024,
        maxFileSize = 25L * 1024L * 1024L,
        maxRequestSize = 26L * 1024L * 1024L
)
public class StudentQuranVoiceServlet extends HttpServlet {

    private static final int MAX_BODY_BYTES = 64 * 1024;
    private static final int MAX_AUDIO_BYTES = 25 * 1024 * 1024;
    private static final Pattern VERSE_KEY = Pattern.compile("^\\d{1,3}:\\d{1,3}$");

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        String path = request.getServletPath();
        if (path != null && path.endsWith("/voice/speak")) {
            handleSpeak(request, response);
        } else {
            handleTurn(request, response);
        }
    }

    // ---------------------------------------------------------------------
    // /voice/turn — full pipeline
    // ---------------------------------------------------------------------
    private void handleTurn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");

        QuranVoiceService voice = QuranVoiceService.instance();
        QuranAssistantService chat = QuranAssistantService.instance();
        if (!voice.configured()) {
            notConfigured(response);
            return;
        }

        byte[] audioBytes;
        String fileName;
        try {
            Part audioPart = request.getPart("audio");
            if (audioPart == null) {
                badRequest(response, "audio_required");
                return;
            }
            fileName = sanitizeFileName(audioPart.getSubmittedFileName());
            audioBytes = readPart(audioPart);
        } catch (IllegalArgumentException ex) {
            badRequest(response, ex.getMessage());
            return;
        } catch (Exception ex) {
            badRequest(response, "audio_unreadable");
            return;
        }
        if (audioBytes == null || audioBytes.length == 0) {
            badRequest(response, "audio_required");
            return;
        }

        String mode = formValue(request, "mode");
        String lang = formValue(request, "lang");
        String historyJson = formValue(request, "history");
        String contextJson = formValue(request, "context");
        List<QuranAssistantService.ChatMessage> history = parseHistory(historyJson);
        QuranAssistantService.QuranContext context = parseContext(contextJson);

        String transcript;
        try {
            transcript = voice.transcribe(audioBytes, fileName, lang);
        } catch (IllegalArgumentException ex) {
            badRequest(response, ex.getMessage());
            return;
        } catch (IOException ex) {
            upstreamFailed(response);
            return;
        }

        if (transcript == null || transcript.isBlank()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ok", Boolean.TRUE);
            payload.put("transcript", "");
            payload.put("reply", "");
            payload.put("noSpeech", Boolean.TRUE);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(JsonUtil.obj(payload));
            return;
        }

        QuranAssistantService.ChatResult result;
        try {
            result = chat.chat(mode, transcript, history, context, true);
        } catch (IllegalArgumentException ex) {
            badRequest(response, ex.getMessage());
            return;
        } catch (IOException ex) {
            upstreamFailed(response);
            return;
        }

        String audioBase64 = null;
        try {
            byte[] speech = voice.synthesize(result.reply, lang);
            audioBase64 = Base64.getEncoder().encodeToString(speech);
        } catch (Exception ex) {
            // Voice synthesis is best-effort: still return the text reply if TTS fails.
            audioBase64 = null;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", Boolean.TRUE);
        payload.put("transcript", transcript);
        payload.put("reply", result.reply);
        payload.put("mode", mode == null ? "general" : mode.trim().toLowerCase());
        payload.put("scopeReminder", Boolean.valueOf(result.scopeReminder));
        if (audioBase64 != null) {
            payload.put("audio", audioBase64);
            payload.put("audioMime", "audio/mpeg");
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(JsonUtil.obj(payload));
    }

    // ---------------------------------------------------------------------
    // /voice/speak — TTS only (for replaying a reply)
    // ---------------------------------------------------------------------
    private void handleSpeak(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");

        QuranVoiceService voice = QuranVoiceService.instance();
        if (!voice.configured()) {
            notConfigured(response);
            return;
        }

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

        String text = extractStringField(raw, "text");
        String lang = extractStringField(raw, "lang");
        if (text == null || text.isBlank()) {
            badRequest(response, "text_required");
            return;
        }

        try {
            byte[] speech = voice.synthesize(text, lang);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("ok", Boolean.TRUE);
            payload.put("audio", Base64.getEncoder().encodeToString(speech));
            payload.put("audioMime", "audio/mpeg");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(JsonUtil.obj(payload));
        } catch (IllegalArgumentException ex) {
            badRequest(response, ex.getMessage());
        } catch (IOException ex) {
            upstreamFailed(response);
        }
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------
    private static byte[] readPart(Part part) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = part.getInputStream()) {
            byte[] buf = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > MAX_AUDIO_BYTES) throw new IOException("audio too large");
                out.write(buf, 0, read);
            }
        }
        return out.toByteArray();
    }

    private static String sanitizeFileName(String submitted) {
        String name = submitted == null ? null : submitted.trim();
        if (name == null || name.isEmpty()) return "speech.webm";
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) name = name.substring(slash + 1);
        return name.isEmpty() ? "speech.webm" : name;
    }

    private static String formValue(HttpServletRequest request, String name) {
        String v = request.getParameter(name);
        return (v == null || v.isBlank()) ? null : v;
    }

    private static List<QuranAssistantService.ChatMessage> parseHistory(String json) {
        List<QuranAssistantService.ChatMessage> out = new ArrayList<>();
        if (json == null) return out;
        int open = json.indexOf('[');
        if (open < 0) return out;
        String inner = extractBracket(json, open);
        if (inner == null) return out;
        for (String obj : splitTopLevel(inner)) {
            String role = extractStringField(obj, "role");
            String content = extractStringField(obj, "content");
            if (role == null || content == null) continue;
            out.add(new QuranAssistantService.ChatMessage(role, content));
        }
        return out;
    }

    private static QuranAssistantService.QuranContext parseContext(String json) {
        if (json == null) return null;
        int open = json.indexOf('{');
        if (open < 0) return null;
        String obj = extractBrace(json, open);
        if (obj == null) return null;

        QuranAssistantService.QuranContext ctx = new QuranAssistantService.QuranContext();
        ctx.surahName = firstNonNull(extractStringField(obj, "surahName"), extractStringField(obj, "surah_name"));
        ctx.surahNameArabic = firstNonNull(extractStringField(obj, "surahNameArabic"),
                extractStringField(obj, "surah_name_arabic"));
        ctx.verseKey = firstNonNull(extractStringField(obj, "verseKey"), extractStringField(obj, "verse_key"));

        String surahIdStr = firstNonNull(extractStringField(obj, "surahId"), extractStringField(obj, "surah_id"));
        if (surahIdStr == null) surahIdStr = extractNumberField(obj, "surahId");
        if (surahIdStr == null) surahIdStr = extractNumberField(obj, "surah_id");
        if (surahIdStr != null) {
            try { ctx.surahId = (int) Math.round(Double.parseDouble(surahIdStr.trim())); } catch (NumberFormatException ignored) { }
        }

        String ayahStr = firstNonNull(extractStringField(obj, "ayahNumber"), extractStringField(obj, "ayah_number"));
        if (ayahStr == null) ayahStr = extractNumberField(obj, "ayahNumber");
        if (ayahStr == null) ayahStr = extractNumberField(obj, "ayah_number");
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

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
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
        if (obj == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher m = p.matcher(obj);
        if (!m.find()) return null;
        return unescape(m.group(1));
    }

    private static String extractNumberField(String obj, String key) {
        if (obj == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
        Matcher m = p.matcher(obj);
        if (!m.find()) return null;
        return m.group(1);
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

    private static void notConfigured(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("ok", Boolean.FALSE);
        err.put("error", "ai_not_configured");
        err.put("message", "Voice Assistant is not configured on this server. Please contact your administrator.");
        response.getWriter().write(JsonUtil.obj(err));
    }

    private static void upstreamFailed(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("ok", Boolean.FALSE);
        err.put("error", "ai_upstream_failed");
        err.put("message", "Could not reach the Voice Assistant service. Please try again shortly.");
        response.getWriter().write(JsonUtil.obj(err));
    }

    private static void badRequest(HttpServletResponse response, String code) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("ok", Boolean.FALSE);
        err.put("error", code == null ? "bad_request" : code);
        response.getWriter().write(JsonUtil.obj(err));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}
