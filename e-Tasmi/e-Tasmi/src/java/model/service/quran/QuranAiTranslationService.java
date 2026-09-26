package model.service.quran;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OpenAI-backed Quran ayah translation fallback.
 * <p>
 * Used when the Quran Foundation translation payload is missing (empty entitlement, network blip,
 * or restricted resource). Calls {@code https://api.openai.com/v1/chat/completions} with a strict
 * JSON-mode prompt so we get one English translation per ayah in a single round-trip.
 * </p>
 * Results are memoised process-wide by {@code verseKey} (chapter:verse) so subsequent toggles or
 * page loads reuse the same translation without spending another OpenAI call.
 *
 * <p>The translations produced by this service are clearly labelled in the JSON payload as
 * {@code source = "openai"} so the front-end can display a small pill ("AI translation") under
 * those ayahs if desired.</p>
 */
public final class QuranAiTranslationService {

    private static final Logger LOGGER = Logger.getLogger(QuranAiTranslationService.class.getName());

    private static final String CHAT_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final int MAX_BATCH = 30;
    private static final int MAX_ARABIC_CHARS = 1500;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    private static final QuranAiTranslationService INSTANCE = new QuranAiTranslationService();

    public static QuranAiTranslationService instance() {
        return INSTANCE;
    }

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    /** Process-wide cache: verseKey -> English translation. */
    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>(2048);

    private QuranAiTranslationService() { }

    public boolean configured() {
        return trimToNull(System.getenv("OPENAI_API_KEY")) != null;
    }

    /** Look up a single verse from cache without contacting OpenAI. */
    public String cached(String verseKey) {
        return verseKey == null ? null : cache.get(verseKey);
    }

    /**
     * Translate a list of ayahs into English. Re-uses cached values, calls OpenAI for the rest.
     * Each entry must contain {@code verseKey} (e.g. {@code 2:255}) and the Arabic text.
     *
     * @return a map keyed by verseKey with the English translation. Verses that could not be
     *         translated are omitted (the caller can show a friendly empty state).
     */
    public Map<String, String> translate(List<Ayah> ayahs) throws IOException {
        if (ayahs == null || ayahs.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> out = new LinkedHashMap<>();
        List<Ayah> missing = new ArrayList<>();
        for (Ayah a : ayahs) {
            if (a == null || a.verseKey == null || a.verseKey.isBlank()) continue;
            String hit = cache.get(a.verseKey);
            if (hit != null) {
                out.put(a.verseKey, hit);
                continue;
            }
            if (a.arabic == null || a.arabic.isBlank()) continue;
            missing.add(a);
        }
        if (missing.isEmpty()) {
            return out;
        }

        String apiKey = trimToNull(System.getenv("OPENAI_API_KEY"));
        if (apiKey == null) {
            throw new IOException("OPENAI_API_KEY is not configured for fallback translation.");
        }

        // Chunk so each call stays small + predictable.
        for (int i = 0; i < missing.size(); i += MAX_BATCH) {
            List<Ayah> chunk = missing.subList(i, Math.min(i + MAX_BATCH, missing.size()));
            try {
                Map<String, String> chunkResults = callOpenAi(apiKey, chunk);
                chunkResults.forEach((k, v) -> {
                    if (v == null || v.isBlank()) return;
                    cache.put(k, v);
                    out.put(k, v);
                });
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "OpenAI ayah translation failed for batch starting at " + i, ex);
                if (out.isEmpty() && i == 0) {
                    throw new IOException("AI translation upstream failed: " + ex.getMessage(), ex);
                }
                // Otherwise keep what we have so far; caller still gets useful output.
                break;
            }
        }
        return out;
    }

    private Map<String, String> callOpenAi(String apiKey, List<Ayah> chunk) throws Exception {
        String model = trimToNull(System.getenv("OPENAI_TRANSLATION_MODEL"));
        if (model == null) model = DEFAULT_MODEL;

        StringBuilder ayahBlock = new StringBuilder();
        for (Ayah a : chunk) {
            String arabic = a.arabic == null ? "" : a.arabic;
            if (arabic.length() > MAX_ARABIC_CHARS) {
                arabic = arabic.substring(0, MAX_ARABIC_CHARS);
            }
            ayahBlock.append('[').append(a.verseKey).append("] ").append(arabic).append('\n');
        }

        String systemPrompt =
                "You are a careful translator of the Holy Quran. Translate each Arabic ayah into clear, " +
                "readable English suitable for a student reading app. Stay close to the Sahih " +
                "International style: faithful, simple, neutral. Do NOT add commentary, footnotes, or " +
                "transliteration. Do NOT skip any verse. Respond with strict JSON only.";

        String userPrompt =
                "For each ayah below, return its English translation. Each line is formatted as " +
                "\"[verseKey] arabic\" where verseKey looks like \"chapter:verse\" (e.g. 2:255).\n\n" +
                "Ayahs:\n" + ayahBlock +
                "\nReturn STRICT JSON with this exact shape (no markdown, no prose):\n" +
                "{ \"translations\": { \"<verseKey>\": \"<english translation>\", ... } }\n" +
                "Use only the verseKeys provided above as JSON keys. Translation values must be a single " +
                "line of plain English, no markdown.";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0);
        body.put("response_format", Collections.singletonMap("type", "json_object"));
        body.put("messages", Arrays.asList(
                mapOf("role", "system", "content", systemPrompt),
                mapOf("role", "user", "content", userPrompt)
        ));
        String jsonBody = MiniJson.stringify(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CHAT_ENDPOINT))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String snippet = response.body() == null ? "" : response.body();
            if (snippet.length() > 400) snippet = snippet.substring(0, 400) + "...";
            LOGGER.log(Level.WARNING, "OpenAI translation HTTP {0}: {1}",
                    new Object[]{response.statusCode(), snippet});
            throw new IOException("OpenAI HTTP " + response.statusCode());
        }

        Object parsed = MiniJson.parse(response.body());
        if (!(parsed instanceof Map)) return Collections.emptyMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;
        String content = extractAssistantContent(root);
        if (content == null) return Collections.emptyMap();

        Object payload = MiniJson.parse(content);
        if (!(payload instanceof Map)) return Collections.emptyMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) payload;
        Object trObj = map.get("translations");
        if (!(trObj instanceof Map)) return Collections.emptyMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> rawTr = (Map<String, Object>) trObj;

        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : rawTr.entrySet()) {
            String k = e.getKey();
            Object v = e.getValue();
            if (k == null || v == null) continue;
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) continue;
            result.put(k, s);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static String extractAssistantContent(Map<String, Object> chatResponse) {
        Object choicesObj = chatResponse.get("choices");
        if (!(choicesObj instanceof List) || ((List<?>) choicesObj).isEmpty()) return null;
        Object first = ((List<?>) choicesObj).get(0);
        if (!(first instanceof Map)) return null;
        Object messageObj = ((Map<String, Object>) first).get("message");
        if (!(messageObj instanceof Map)) return null;
        Object contentObj = ((Map<String, Object>) messageObj).get("content");
        return contentObj == null ? null : String.valueOf(contentObj);
    }

    private static Map<String, Object> mapOf(String k1, Object v1, String k2, Object v2) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k1, v1);
        m.put(k2, v2);
        return m;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** Lightweight DTO used by callers. */
    public static final class Ayah {
        public final String verseKey;
        public final String arabic;
        public Ayah(String verseKey, String arabic) {
            this.verseKey = verseKey;
            this.arabic = arabic;
        }
    }

    /* ===========================================================================
     * Inline mini-JSON (avoids pulling a dependency for one endpoint)
     * =========================================================================== */
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
                Object value = readValue();
                map.put(key, value);
                skipWs();
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == '}') { pos++; return map; }
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
                char c = peek();
                if (c == ',') { pos++; continue; }
                if (c == ']') { pos++; return list; }
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
                        case '"':  sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/':  sb.append('/'); break;
                        case 'b':  sb.append('\b'); break;
                        case 'f':  sb.append('\f'); break;
                        case 'n':  sb.append('\n'); break;
                        case 'r':  sb.append('\r'); break;
                        case 't':  sb.append('\t'); break;
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
                    case '"':  sb.append("\\\""); break;
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
