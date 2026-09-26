package model.service.quran;

import javax.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Offline Quran catalogue bundled under {@code /assets/quran/} for environments where
 * Quran Foundation credentials are not configured (local dev, staging without QF keys).
 */
public final class QuranBundledCatalog {

    private static final Logger LOGGER = Logger.getLogger(QuranBundledCatalog.class.getName());

    private static final String META_RESOURCE = "/assets/quran/quran-meta.json";
    private static final String SAMPLE_RESOURCE = "/assets/quran/quran-sample.json";

    private static final Pattern STRING_FIELD = Pattern.compile(
            "\"([a-zA-Z0-9_]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern INT_FIELD = Pattern.compile(
            "\"([a-zA-Z0-9_]+)\"\\s*:\\s*(-?\\d+)");

    private static volatile List<Map<String, Object>> chaptersCache;
    private static volatile Map<Integer, List<Map<String, Object>>> sampleVersesCache;

    private QuranBundledCatalog() {
    }

    /** Load bundled JSON once at application startup. */
    public static void init(ServletContext ctx) {
        if (ctx == null) {
            return;
        }
        try {
            chaptersCache = parseMetaChapters(readResource(ctx, META_RESOURCE));
            sampleVersesCache = parseSampleVerses(readResource(ctx, SAMPLE_RESOURCE));
            LOGGER.info("[QuranBundledCatalog] Loaded "
                    + chaptersCache.size() + " chapters, "
                    + sampleVersesCache.size() + " sample surahs with ayahs.");
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "[QuranBundledCatalog] Failed to load bundled Quran assets", ex);
            chaptersCache = List.of();
            sampleVersesCache = Map.of();
        }
    }

    public static boolean isReady() {
        return chaptersCache != null && !chaptersCache.isEmpty();
    }

    public static List<Map<String, Object>> chapters() throws IOException {
        List<Map<String, Object>> cached = chaptersCache;
        if (cached == null || cached.isEmpty()) {
            throw new IOException("Bundled Quran chapter catalogue is unavailable.");
        }
        return cached;
    }

    public static boolean hasSampleChapter(int chapterNumber) {
        Map<Integer, List<Map<String, Object>>> sample = sampleVersesCache;
        return sample != null && sample.containsKey(chapterNumber);
    }

    public static QuranVersesParse.VersesSlice versesForChapter(int chapterNumber) throws IOException {
        Map<Integer, List<Map<String, Object>>> sample = sampleVersesCache;
        if (sample == null) {
            throw new IOException("Bundled Quran verse catalogue is unavailable.");
        }
        List<Map<String, Object>> rows = sample.get(chapterNumber);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        Map<String, Object> pagination = new LinkedHashMap<>();
        pagination.put("currentPage", 1L);
        pagination.put("perPage", rows.size());
        pagination.put("totalPages", 1L);
        pagination.put("totalRecords", rows.size());
        return new QuranVersesParse.VersesSlice(List.copyOf(rows), Map.copyOf(pagination));
    }

    private static String readResource(ServletContext ctx, String path) throws IOException {
        try (InputStream in = ctx.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Missing bundled resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<Map<String, Object>> parseMetaChapters(String json) throws IOException {
        if (json == null || json.isBlank()) {
            throw new IOException("Empty meta catalogue");
        }
        int keyIdx = json.indexOf("\"surahs\"");
        if (keyIdx < 0) {
            throw new IOException("Missing surahs array in meta catalogue");
        }
        int bracket = json.indexOf('[', keyIdx);
        if (bracket < 0) {
            throw new IOException("Invalid surahs array in meta catalogue");
        }
        String inner = extractBracketContents(json, bracket);
        if (inner == null) {
            throw new IOException("Unterminated surahs array in meta catalogue");
        }
        List<String> objects = splitTopLevelJsonObjects(inner);
        List<Map<String, Object>> rows = new ArrayList<>(objects.size());
        for (String obj : objects) {
            Map<String, Object> row = metaSurahToPublicRow(obj);
            if (row != null && !row.isEmpty()) {
                rows.add(row);
            }
        }
        if (rows.isEmpty()) {
            throw new IOException("No chapters parsed from meta catalogue");
        }
        return List.copyOf(rows);
    }

    private static Map<String, Object> metaSurahToPublicRow(String obj) {
        Long number = firstLong(obj, "number");
        if (number == null || number < 1 || number > 114) {
            return null;
        }
        String nameArabic = unescapeJsonString(firstString(obj, "name"));
        String nameSimple = unescapeJsonString(firstString(obj, "transliteration"));
        String translatedName = unescapeJsonString(firstString(obj, "english"));
        Long ayahs = firstLong(obj, "ayahs");
        String revelation = unescapeJsonString(firstString(obj, "revelation"));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", number);
        m.put("nameArabic", nullToEmpty(nameArabic));
        m.put("nameSimple", nullToEmpty(nameSimple));
        m.put("nameComplex", nullToEmpty(nameSimple));
        if (translatedName != null && !translatedName.isBlank()) {
            m.put("translatedName", translatedName);
        }
        if (ayahs != null) {
            m.put("versesCount", ayahs);
        }
        if (revelation != null && !revelation.isBlank()) {
            m.put("revelationPlace", revelation.trim().toLowerCase());
        }
        return m;
    }

    private static Map<Integer, List<Map<String, Object>>> parseSampleVerses(String json) throws IOException {
        if (json == null || json.isBlank()) {
            throw new IOException("Empty sample catalogue");
        }
        int surahsIdx = json.indexOf("\"surahs\"");
        if (surahsIdx < 0) {
            throw new IOException("Missing surahs object in sample catalogue");
        }
        int brace = json.indexOf('{', surahsIdx);
        if (brace < 0) {
            throw new IOException("Invalid surahs object in sample catalogue");
        }
        String surahsInner = extractBraceContents(json, brace);
        if (surahsInner == null) {
            throw new IOException("Unterminated surahs object in sample catalogue");
        }

        Map<Integer, List<Map<String, Object>>> out = new LinkedHashMap<>();
        Matcher keyMatcher = Pattern.compile("\"(\\d{1,3})\"\\s*:\\s*\\{").matcher(surahsInner);
        while (keyMatcher.find()) {
            int chapterNumber = Integer.parseInt(keyMatcher.group(1));
            int objStart = keyMatcher.end() - 1;
            String block = extractBraceContents(surahsInner, objStart);
            if (block == null) {
                continue;
            }
            List<Map<String, Object>> verses = parseSampleAyahs(chapterNumber, block);
            if (!verses.isEmpty()) {
                out.put(chapterNumber, List.copyOf(verses));
            }
        }
        return Map.copyOf(out);
    }

    private static List<Map<String, Object>> parseSampleAyahs(int chapterNumber, String surahBlock) {
        int ayahIdx = surahBlock.indexOf("\"ayahs\"");
        if (ayahIdx < 0) {
            return List.of();
        }
        int bracket = surahBlock.indexOf('[', ayahIdx);
        if (bracket < 0) {
            return List.of();
        }
        String inner = extractBracketContents(surahBlock, bracket);
        if (inner == null) {
            return List.of();
        }
        List<String> objects = splitTopLevelJsonObjects(inner);
        List<Map<String, Object>> rows = new ArrayList<>(objects.size());
        for (String obj : objects) {
            Long ayahNumber = firstLong(obj, "number");
            String text = unescapeJsonString(firstString(obj, "text"));
            if (ayahNumber == null || ayahNumber < 1 || text == null || text.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("chapterId", (long) chapterNumber);
            row.put("verseNumber", ayahNumber);
            row.put("verseKey", chapterNumber + ":" + ayahNumber);
            row.put("textArabic", text);
            rows.add(row);
        }
        return rows;
    }

    private static String extractBracketContents(String s, int openIdx) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return s.substring(openIdx + 1, i);
                }
            }
        }
        return null;
    }

    private static String extractBraceContents(String s, int openIdx) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return s.substring(openIdx + 1, i);
                }
            }
        }
        return null;
    }

    private static List<String> splitTopLevelJsonObjects(String arrInnerTrimmed) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < arrInnerTrimmed.length(); i++) {
            char c = arrInnerTrimmed.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(arrInnerTrimmed.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return out;
    }

    private static String firstString(String obj, String key) {
        Matcher m = STRING_FIELD.matcher(obj);
        while (m.find()) {
            if (key.equals(m.group(1))) {
                return m.group(2);
            }
        }
        return null;
    }

    private static Long firstLong(String obj, String key) {
        Matcher m = INT_FIELD.matcher(obj);
        while (m.find()) {
            if (key.equals(m.group(1))) {
                try {
                    return Long.parseLong(m.group(2));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String unescapeJsonString(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                char n = raw.charAt(i + 1);
                switch (n) {
                    case '"', '\\', '/' -> {
                        sb.append(n);
                        i++;
                    }
                    case 'n' -> {
                        sb.append('\n');
                        i++;
                    }
                    case 'r' -> {
                        sb.append('\r');
                        i++;
                    }
                    case 't' -> {
                        sb.append('\t');
                        i++;
                    }
                    case 'u' -> {
                        if (i + 5 < raw.length()) {
                            try {
                                int cp = Integer.parseInt(raw.substring(i + 2, i + 6), 16);
                                sb.append((char) cp);
                                i += 5;
                            } catch (NumberFormatException ignored) {
                                sb.append(c);
                            }
                        } else {
                            sb.append(c);
                        }
                    }
                    default -> sb.append(c);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
