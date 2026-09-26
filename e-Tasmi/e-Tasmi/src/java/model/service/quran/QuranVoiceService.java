package model.service.quran;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OpenAI-backed voice helpers for the e-Tasmi Quran Assistant voice mode.
 *
 * Provides the two ends of the turn-based voice pipeline:
 *   transcribe()  -> speech-to-text (Whisper / gpt-4o-transcribe)
 *   synthesize()  -> text-to-speech (gpt-4o-mini-tts / tts-1)
 *
 * The GPT reasoning step in between is delegated to {@link QuranAssistantService} so the
 * strict Qur'anic scope, modes, history and context handling are shared with the text chat.
 */
public final class QuranVoiceService {

    private static final Logger LOGGER = Logger.getLogger(QuranVoiceService.class.getName());

    private static final String TRANSCRIPTION_ENDPOINT = "https://api.openai.com/v1/audio/transcriptions";
    private static final String SPEECH_ENDPOINT = "https://api.openai.com/v1/audio/speech";

    private static final String DEFAULT_STT_MODEL = "gpt-4o-transcribe";
    private static final String DEFAULT_TTS_MODEL = "gpt-4o-mini-tts";
    private static final String DEFAULT_VOICE = "alloy";

    /** OpenAI TTS rejects inputs longer than 4096 characters. */
    private static final int MAX_TTS_CHARS = 4000;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    private static final QuranVoiceService INSTANCE = new QuranVoiceService();

    public static QuranVoiceService instance() {
        return INSTANCE;
    }

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private QuranVoiceService() { }

    public boolean configured() {
        return trimToNull(System.getenv("OPENAI_API_KEY")) != null;
    }

    /**
     * Speech-to-text. Returns the plain transcript (possibly empty) or throws on transport failure.
     *
     * @param langHint optional ISO-639-1 hint ("en", "ar"); null/blank lets the model auto-detect.
     */
    public String transcribe(byte[] audioBytes, String fileName, String langHint) throws IOException {
        String apiKey = trimToNull(System.getenv("OPENAI_API_KEY"));
        if (apiKey == null) {
            throw new IOException("OPENAI_API_KEY is not configured.");
        }
        if (audioBytes == null || audioBytes.length == 0) {
            throw new IllegalArgumentException("audio_required");
        }

        String model = trimToNull(System.getenv("OPENAI_VOICE_STT_MODEL"));
        if (model == null) model = DEFAULT_STT_MODEL;

        String safeFileName = trimToNull(fileName);
        if (safeFileName == null) safeFileName = "speech.webm";

        String boundary = "----eTasmiVoiceBoundary" + System.nanoTime();
        byte[] payload = buildTranscriptionPayload(boundary, model, safeFileName, audioBytes, normalizeLang(langHint));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TRANSCRIPTION_ENDPOINT))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Transcription request interrupted", ex);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            LOGGER.log(Level.WARNING, "Voice STT HTTP {0}: {1}",
                    new Object[]{response.statusCode(), truncate(response.body(), 400)});
            throw new IOException("OpenAI STT HTTP " + response.statusCode());
        }

        Object parsed = MiniJson.parse(response.body());
        if (!(parsed instanceof Map)) {
            throw new IOException("invalid_stt_response");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;
        String text = root.get("text") == null ? null : String.valueOf(root.get("text"));
        return text == null ? "" : text.trim();
    }

    /**
     * Text-to-speech. Returns raw MP3 bytes for the supplied text.
     *
     * @param langHint optional ISO-639-1 hint used to tailor delivery instructions.
     */
    public byte[] synthesize(String text, String langHint) throws IOException {
        String apiKey = trimToNull(System.getenv("OPENAI_API_KEY"));
        if (apiKey == null) {
            throw new IOException("OPENAI_API_KEY is not configured.");
        }
        String speech = trimToNull(text);
        if (speech == null) {
            throw new IllegalArgumentException("text_required");
        }
        if (speech.length() > MAX_TTS_CHARS) {
            speech = speech.substring(0, MAX_TTS_CHARS);
        }

        String model = trimToNull(System.getenv("OPENAI_VOICE_TTS_MODEL"));
        if (model == null) model = DEFAULT_TTS_MODEL;
        String voice = trimToNull(System.getenv("OPENAI_VOICE_NAME"));
        if (voice == null) voice = DEFAULT_VOICE;

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("voice", voice);
        body.put("input", speech);
        body.put("response_format", "mp3");
        // gpt-4o-mini-tts honours a free-form delivery instruction; older tts-1 models ignore it.
        body.put("instructions", deliveryInstructions(normalizeLang(langHint)));

        String jsonBody = MiniJson.stringify(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SPEECH_ENDPOINT))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Speech request interrupted", ex);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String snippet = response.body() == null ? "" : new String(response.body(), StandardCharsets.UTF_8);
            LOGGER.log(Level.WARNING, "Voice TTS HTTP {0}: {1}",
                    new Object[]{response.statusCode(), truncate(snippet, 400)});
            throw new IOException("OpenAI TTS HTTP " + response.statusCode());
        }

        byte[] audio = response.body();
        if (audio == null || audio.length == 0) {
            throw new IOException("empty_tts_audio");
        }
        return audio;
    }

    private static String deliveryInstructions(String lang) {
        String base = "Speak as a calm, warm and professional Qur'an learning tutor. "
                + "Keep a measured, encouraging and respectful pace suitable for an Islamic educational platform. "
                + "Pronounce Arabic terms and Qur'anic words clearly and reverently.";
        if ("ar".equals(lang)) {
            return base + " The learner is interacting in Arabic; speak natural, clear Modern Standard Arabic.";
        }
        return base;
    }

    private static String normalizeLang(String lang) {
        String l = trimToNull(lang);
        if (l == null) return null;
        l = l.toLowerCase(Locale.ROOT);
        if (l.startsWith("ar")) return "ar";
        if (l.startsWith("en")) return "en";
        if (l.startsWith("ms")) return "ms";
        return null;
    }

    private static byte[] buildTranscriptionPayload(String boundary, String model, String fileName,
                                                    byte[] audioBytes, String langHint) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeFormField(out, boundary, "model", model);
        if (langHint != null) {
            writeFormField(out, boundary, "language", langHint);
        }
        writeFormField(out, boundary, "response_format", "json");
        writeFormField(out, boundary, "temperature", "0");
        writeFormField(out, boundary, "prompt",
                "Conversational question for a Qur'an learning assistant. Transcribe faithfully.");
        writeFileField(out, boundary, "file", fileName, audioBytes);
        writeAscii(out, "--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private static void writeFormField(ByteArrayOutputStream out, String boundary, String name, String value) {
        writeAscii(out, "--" + boundary + "\r\n");
        writeAscii(out, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        writeAscii(out, value == null ? "" : value);
        writeAscii(out, "\r\n");
    }

    private static void writeFileField(ByteArrayOutputStream out, String boundary, String name,
                                       String filename, byte[] bytes) {
        writeAscii(out, "--" + boundary + "\r\n");
        writeAscii(out, "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"\r\n");
        writeAscii(out, "Content-Type: application/octet-stream\r\n\r\n");
        out.write(bytes, 0, bytes.length);
        writeAscii(out, "\r\n");
    }

    private static void writeAscii(ByteArrayOutputStream out, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.write(bytes, 0, bytes.length);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    /* Minimal JSON helper (same pattern shared across the quran services). */
    private static final class MiniJson {
        private final String src;
        private int pos;

        private MiniJson(String src) { this.src = src; }

        static Object parse(String text) {
            if (text == null) throw new IllegalArgumentException("null json");
            MiniJson p = new MiniJson(text);
            p.skipWs();
            Object v = p.readValue();
            p.skipWs();
            return v;
        }

        private Object readValue() {
            skipWs();
            if (pos >= src.length()) throw new IllegalStateException("unexpected end");
            char c = src.charAt(pos);
            if (c == '{') return readObject();
            if (c == '[') return readArray();
            if (c == '"') return readString();
            if (c == 't' || c == 'f') return readBoolean();
            if (c == 'n') { expectLiteral("null"); return null; }
            return readNumber();
        }

        private Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            expect('{'); skipWs();
            if (peek() == '}') { pos++; return map; }
            while (true) {
                skipWs();
                String key = readString();
                skipWs(); expect(':'); skipWs();
                map.put(key, readValue());
                skipWs();
                char ch = peek();
                if (ch == ',') { pos++; continue; }
                if (ch == '}') { pos++; return map; }
                throw new IllegalStateException("expected , or } at " + pos);
            }
        }

        private List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            expect('['); skipWs();
            if (peek() == ']') { pos++; return list; }
            while (true) {
                skipWs();
                list.add(readValue());
                skipWs();
                char ch = peek();
                if (ch == ',') { pos++; continue; }
                if (ch == ']') { pos++; return list; }
                throw new IllegalStateException("expected , or ] at " + pos);
            }
        }

        private String readString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (pos >= src.length()) throw new IllegalStateException("bad escape");
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            if (pos + 4 > src.length()) throw new IllegalStateException("bad unicode escape");
                            int cp = Integer.parseInt(src.substring(pos, pos + 4), 16);
                            pos += 4;
                            sb.append((char) cp);
                            break;
                        default: throw new IllegalStateException("bad escape: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalStateException("unterminated string");
        }

        private Object readNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') pos++;
                else break;
            }
            String num = src.substring(start, pos);
            try { return Double.parseDouble(num); }
            catch (NumberFormatException ex) { throw new IllegalStateException("bad number: " + num); }
        }

        private Boolean readBoolean() {
            if (src.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            if (src.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
            throw new IllegalStateException("bad boolean at " + pos);
        }

        private void expectLiteral(String lit) {
            if (!src.startsWith(lit, pos)) throw new IllegalStateException("expected " + lit);
            pos += lit.length();
        }

        private void expect(char c) {
            if (pos >= src.length() || src.charAt(pos) != c) {
                throw new IllegalStateException("expected '" + c + "' at " + pos);
            }
            pos++;
        }

        private char peek() {
            if (pos >= src.length()) throw new IllegalStateException("unexpected end");
            return src.charAt(pos);
        }

        private void skipWs() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
        }

        static String stringify(Object value) {
            StringBuilder sb = new StringBuilder();
            write(sb, value);
            return sb.toString();
        }

        @SuppressWarnings("unchecked")
        private static void write(StringBuilder sb, Object value) {
            if (value == null) { sb.append("null"); return; }
            if (value instanceof Boolean) { sb.append(((Boolean) value) ? "true" : "false"); return; }
            if (value instanceof Number) { sb.append(value.toString()); return; }
            if (value instanceof CharSequence) { writeString(sb, value.toString()); return; }
            if (value instanceof Map) {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<String, Object> e : ((Map<String, Object>) value).entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    writeString(sb, e.getKey());
                    sb.append(':');
                    write(sb, e.getValue());
                }
                sb.append('}');
                return;
            }
            if (value instanceof Iterable) {
                sb.append('[');
                boolean first = true;
                for (Object item : (Iterable<?>) value) {
                    if (!first) sb.append(',');
                    first = false;
                    write(sb, item);
                }
                sb.append(']');
                return;
            }
            writeString(sb, String.valueOf(value));
        }

        private static void writeString(StringBuilder sb, String s) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    case '\b': sb.append("\\b"); break;
                    case '\f': sb.append("\\f"); break;
                    default:
                        if (c < 0x20) sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                        else sb.append(c);
                }
            }
            sb.append('"');
        }
    }
}
