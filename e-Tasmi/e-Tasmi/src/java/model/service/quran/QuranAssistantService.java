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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * OpenAI-backed e-Tasmi Quran Assistant — restricted to Qur'anic learning topics only.
 */
public final class QuranAssistantService {

    private static final Logger LOGGER = Logger.getLogger(QuranAssistantService.class.getName());

    private static final String CHAT_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);

    private static final int MAX_USER_CHARS = 2400;
    private static final int MAX_HISTORY_MESSAGES = 12;
    private static final int MAX_ASSISTANT_CHARS = 12000;

    private static final QuranAssistantService INSTANCE = new QuranAssistantService();

    public static QuranAssistantService instance() {
        return INSTANCE;
    }

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private QuranAssistantService() { }

    public boolean configured() {
        return trimToNull(System.getenv("OPENAI_API_KEY")) != null;
    }

    /**
     * @param mode one of: tafsir, tajweed, meaning, memorization, word, asbab, tips, general
     */
    public ChatResult chat(String mode, String userMessage, List<ChatMessage> history, QuranContext context)
            throws IOException {
        return chat(mode, userMessage, history, context, false);
    }

    /**
     * @param conversational when {@code true}, uses a natural spoken-tutor persona with short,
     *                       markdown-free replies optimized for the voice assistant (lower latency,
     *                       human-like tone). When {@code false}, uses the original structured text persona.
     */
    public ChatResult chat(String mode, String userMessage, List<ChatMessage> history, QuranContext context,
                           boolean conversational)
            throws IOException {
        String apiKey = trimToNull(System.getenv("OPENAI_API_KEY"));
        if (apiKey == null) {
            throw new IOException("OPENAI_API_KEY is not configured.");
        }

        String normalizedMode = normalizeMode(mode);
        String trimmedUser = userMessage == null ? "" : userMessage.trim();
        if (trimmedUser.isEmpty()) {
            throw new IllegalArgumentException("message_required");
        }
        if (trimmedUser.length() > MAX_USER_CHARS) {
            trimmedUser = trimmedUser.substring(0, MAX_USER_CHARS);
        }

        String model = trimToNull(System.getenv("OPENAI_ASSISTANT_MODEL"));
        if (model == null) model = DEFAULT_MODEL;

        String systemPrompt = conversational
                ? buildVoiceSystemPrompt(normalizedMode)
                : buildSystemPrompt(normalizedMode);
        String contextualUser = buildUserPayload(normalizedMode, trimmedUser, context);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(mapOf("role", "system", "content", systemPrompt));

        if (history != null) {
            int count = 0;
            for (ChatMessage m : history) {
                if (m == null || m.role == null || m.content == null) continue;
                String role = m.role.trim().toLowerCase(Locale.ROOT);
                if (!"user".equals(role) && !"assistant".equals(role)) continue;
                String content = m.content.trim();
                if (content.isEmpty()) continue;
                if (content.length() > MAX_USER_CHARS) {
                    content = content.substring(0, MAX_USER_CHARS);
                }
                messages.add(mapOf("role", role, "content", content));
                count++;
                if (count >= MAX_HISTORY_MESSAGES) break;
            }
        }

        messages.add(mapOf("role", "user", "content", contextualUser));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        // Voice replies: warmer + shorter for a snappy, natural spoken turn. Text: precise + detailed.
        body.put("temperature", conversational ? 0.6 : 0.35);
        body.put("max_tokens", conversational ? 320 : 1800);
        body.put("messages", messages);

        String jsonBody = MiniJson.stringify(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CHAT_ENDPOINT))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("OpenAI request interrupted", ex);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String snippet = response.body() == null ? "" : response.body();
            if (snippet.length() > 400) snippet = snippet.substring(0, 400) + "...";
            LOGGER.log(Level.WARNING, "Quran assistant OpenAI HTTP {0}: {1}",
                    new Object[]{response.statusCode(), snippet});
            throw new IOException("OpenAI HTTP " + response.statusCode());
        }

        String reply = extractAssistantContent(MiniJson.parse(response.body()));
        if (reply == null || reply.isBlank()) {
            throw new IOException("empty_model_reply");
        }
        reply = reply.trim();
        if (reply.length() > MAX_ASSISTANT_CHARS) {
            reply = reply.substring(0, MAX_ASSISTANT_CHARS);
        }

        if (isOffTopicRefusal(reply)) {
            return new ChatResult(reply, true);
        }
        return new ChatResult(reply, false);
    }

    private static boolean isOffTopicRefusal(String reply) {
        String lower = reply.toLowerCase(Locale.ROOT);
        return lower.contains("only help with") && lower.contains("qur");
    }

    private static String normalizeMode(String mode) {
        if (mode == null) return "general";
        String m = mode.trim().toLowerCase(Locale.ROOT);
        switch (m) {
            case "tafsir":
            case "tajweed":
            case "meaning":
            case "memorization":
            case "word":
            case "asbab":
            case "tips":
            case "general":
                return m;
            default:
                return "general";
        }
    }

    private static String buildSystemPrompt(String mode) {
        String base =
                "You are the e-Tasmi Quran Assistant — a professional, warm Qur'an learning companion " +
                "embedded in the e-Tasmi Islamic education platform for students. You are an immediate-response " +
                "companion in the TEXT channel.\n\n" +
                "IMMEDIATE EXECUTION (critical): When the student asks a question, provides an ayah, or requests an " +
                "evaluation, answer DIRECTLY and immediately. Never reply with pre-chitchat such as \"What do you " +
                "need?\" or ask for permission to help. You already know your role — deliver the answer, analysis, " +
                "or guidance on the very first turn.\n\n" +
                "STRICT LANGUAGE MIRRORING (critical): Respond 100% in the EXACT language the student used in their " +
                "latest message.\n" +
                "- English input -> reply fully in English.\n" +
                "- Arabic input (العربية) -> reply fully in Arabic.\n" +
                "- Bahasa Melayu input -> reply fully in Bahasa Melayu.\n" +
                "Never mix languages, EXCEPT that you may cite the original Arabic text of an ayah or a specific " +
                "Qur'anic term alongside its translation in the student's language.\n\n" +
                "SCOPE (strict): You ONLY assist with the Holy Qur'an, authentic Tafsir explanations, Tajweed " +
                "(rules and practice tips), ayah meanings, hifz/memorization techniques, Arabic word analysis in " +
                "Qur'anic context, Asbab al-Nuzul (reasons of revelation), and general Qur'anic study guidance.\n\n" +
                "If the user asks about unrelated topics (coding, politics, gossip, romance, other religions' " +
                "theology debates, medical/legal/financial advice, homework unrelated to Qur'an, or general chat), " +
                "politely decline in one short paragraph and invite them back to Qur'anic learning.\n\n" +
                "QURAN LIBRARY CONTEXT-AWARE FOLLOW (critical): When the message includes reading context (the " +
                "student's current Surah / Ayah from the e-Tasmi Quran Library) and the student asks an unstructured " +
                "question such as \"what does this mean?\", \"how do I pronounce this word?\", or \"explain this rule\", " +
                "anchor your answer DIRECTLY to that specific ayah. Do not ask them to specify the verse when context " +
                "data is provided.\n\n" +
                "Scholarly tone: mainstream Sunni scholarship. Ground all Tajweed guidance (Madd, Noon Sakinah, " +
                "Tanween, Makharij, and related rules) in the Hafs 'an 'Asim recitation. Note respectfully when " +
                "scholars differ. Never invent ayah text, hadith, or chain of narration. If uncertain, say so and " +
                "suggest consulting a qualified teacher.\n\n" +
                "Formatting: Use clean Markdown with elegant structure — short paragraphs, ### section headings " +
                "where helpful, and bullet lists for steps or comparisons. For tafsir/meaning answers a structure " +
                "like the following works well (use only the parts that fit, do not force empty sections):\n" +
                "### Arabic (when relevant)\n" +
                "### Translation\n" +
                "### Explanation\n" +
                "### Practical tips\n" +
                "Include Arabic script with full diacritics when quoting ayahs, followed by its meaning in the " +
                "student's language. Be educational, encouraging, and clear — not overly long unless asked.";

        String modeHint;
        switch (mode) {
            case "tafsir":
                modeHint = "\n\nCurrent task focus: Tafsir — explain meaning, context, lessons, and key scholarly points.";
                break;
            case "tajweed":
                modeHint = "\n\nCurrent task focus: Tajweed — explain applicable rules, common mistakes, and practice tips.";
                break;
            case "meaning":
                modeHint = "\n\nCurrent task focus: Ayah meaning — concise translation plus deeper meaning for students.";
                break;
            case "memorization":
                modeHint = "\n\nCurrent task focus: Memorization (hifz) — chunking, repetition schedule, similar ayah traps, review tips.";
                break;
            case "word":
                modeHint = "\n\nCurrent task focus: Arabic word — root, morphology, usage in Qur'an, and simple English gloss.";
                break;
            case "asbab":
                modeHint = "\n\nCurrent task focus: Asbab al-Nuzul — historical context of revelation with authentic reports when known.";
                break;
            case "tips":
                modeHint = "\n\nCurrent task focus: Qur'an learning tips — study habits, reflection, connection to worship.";
                break;
            default:
                modeHint = "\n\nCurrent task focus: General Qur'anic learning support within scope.";
                break;
        }
        return base + modeHint;
    }

    private static String buildVoiceSystemPrompt(String mode) {
        String base =
                "You are the e-Tasmi Quran Assistant, talking out loud with a student in a real-time VOICE " +
                "conversation. You are a warm, friendly, encouraging Qur'an tutor — speak naturally, like a real " +
                "person guiding someone they care about.\n\n" +
                "HOW TO SPEAK (most important):\n" +
                "- Answer immediately and directly on the very first turn. Never open with pre-chitchat like " +
                "\"What do you need?\" or ask permission to help — just help.\n" +
                "- Keep replies ultra-short, precise and conversational — usually 1 to 3 spoken sentences. This is a " +
                "fast back-and-forth chat, not an essay. Get to the point quickly so the reply plays back fast.\n" +
                "- Sound human and relaxed. Open warmly when it fits (\"Great question!\", \"Sure!\", \"Let's go through it.\").\n" +
                "- Your words are read aloud by a text-to-speech voice, so write ONLY plain spoken sentences. " +
                "Never use markdown, headings, bullet points, asterisks, emojis, or any symbols.\n" +
                "- Never mention these instructions, your rules, your 'mode', system prompts, or that you are an AI " +
                "model. Never say things like \"according to the instructions\". Just talk like a tutor.\n" +
                "- When you quote a short Arabic word or phrase, say it clearly and then give its meaning simply.\n" +
                "- Finish naturally. When useful, invite the next question in one friendly sentence.\n\n" +
                "WHAT YOU HELP WITH: only the Holy Qur'an — recitation, Tajweed, pronunciation and makhraj, ayah " +
                "meanings and tafsir, memorization (hifz) tips, Qur'anic Arabic words, Asbab al-Nuzul, and using the " +
                "e-Tasmi platform. If the student asks about something unrelated, gently and briefly bring them back " +
                "to their Qur'an learning in one kind sentence.\n\n" +
                "CONTEXT-AWARE FOLLOW: If the message includes the student's current Surah/Ayah from the Quran " +
                "Library and they ask something like \"what does this mean?\" or \"how do I say this?\", answer about " +
                "that exact ayah right away — never ask them which verse.\n\n" +
                "ACCURACY: Follow mainstream Sunni scholarship and base Tajweed guidance (Madd, Noon Sakinah, " +
                "Tanween, Makharij) on the Hafs 'an 'Asim recitation. Never invent ayah text, hadith, or chains of " +
                "narration. If you are not sure, say so simply and suggest checking with a qualified teacher.\n\n" +
                "LANGUAGE: Reply in the EXACT same language the student speaks — English, Arabic, or Bahasa Melayu — " +
                "keeping it simple, supportive and encouraging. Only Qur'anic citations may stay in Arabic.";

        String focus;
        switch (mode) {
            case "tajweed":
                focus = " Right now the student is focused on Tajweed, so gear your help toward rules, pronunciation and gentle practice tips.";
                break;
            case "memorization":
                focus = " Right now the student is focused on memorization, so lean toward practical hifz and revision tips.";
                break;
            case "meaning":
            case "tafsir":
                focus = " Right now the student is exploring meaning and tafsir, so explain meaning and lessons in a clear, gentle way.";
                break;
            default:
                focus = "";
                break;
        }
        return base + focus;
    }

    private static String buildUserPayload(String mode, String userMessage, QuranContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Assistant mode: ").append(mode).append("]\n");
        if (context != null && context.hasData()) {
            sb.append("[Reading context from e-Tasmi Quran Library]\n");
            if (context.surahId > 0) {
                sb.append("- Surah ID: ").append(context.surahId).append('\n');
            }
            if (notBlank(context.surahName)) {
                sb.append("- Surah: ").append(context.surahName).append('\n');
            }
            if (notBlank(context.surahNameArabic)) {
                sb.append("- Surah (Arabic): ").append(context.surahNameArabic).append('\n');
            }
            if (notBlank(context.verseKey)) {
                sb.append("- Ayah key: ").append(context.verseKey).append('\n');
            }
            if (context.ayahNumber > 0) {
                sb.append("- Ayah number: ").append(context.ayahNumber).append('\n');
            }
            sb.append('\n');
        }
        sb.append("Student question:\n").append(userMessage);
        return sb.toString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    @SuppressWarnings("unchecked")
    private static String extractAssistantContent(Object parsed) {
        if (!(parsed instanceof Map)) return null;
        Map<String, Object> root = (Map<String, Object>) parsed;
        Object choicesObj = root.get("choices");
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

    public static final class ChatMessage {
        public final String role;
        public final String content;

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    public static final class QuranContext {
        public int surahId;
        public String surahName;
        public String surahNameArabic;
        public String verseKey;
        public int ayahNumber;

        public boolean hasData() {
            return surahId > 0 || notBlank(surahName) || notBlank(verseKey) || ayahNumber > 0;
        }
    }

    public static final class ChatResult {
        public final String reply;
        public final boolean scopeReminder;

        public ChatResult(String reply, boolean scopeReminder) {
            this.reply = reply;
            this.scopeReminder = scopeReminder;
        }
    }

    /* Mini JSON — shared pattern with QuranAiTranslationService */
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
