package model.service.quran;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts sanitized chapter rows from Quran Foundation {@code GET /content/api/v4/chapters} JSON body.
 * No external JSON dependency; escapes in string values other than quotes are tolerated via regex fallback.
 */
public final class QuranChaptersParse {

    private static final Pattern STRING_FIELD = Pattern.compile(
            "\"([a-zA-Z0-9_]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern INT_FIELD = Pattern.compile(
            "\"([a-zA-Z0-9_]+)\"\\s*:\\s*(-?\\d+)");

    private QuranChaptersParse() {
    }

    public static List<Map<String, Object>> toPublicChapterMaps(String apiJsonBody) throws IOException {
        if (apiJsonBody == null || apiJsonBody.isBlank()) {
            throw new IOException("Empty chapters response");
        }
        int keyIdx = apiJsonBody.indexOf("\"chapters\"");
        if (keyIdx < 0) {
            throw new IOException("Missing chapters array");
        }
        int bracket = apiJsonBody.indexOf('[', keyIdx);
        if (bracket < 0) {
            throw new IOException("Invalid chapters array");
        }
        String inner = extractBracketContents(apiJsonBody, bracket);
        if (inner == null) {
            throw new IOException("Unterminated chapters array");
        }
        List<String> objects = splitTopLevelJsonObjects(inner);
        List<Map<String, Object>> rows = new ArrayList<>(objects.size());
        for (String obj : objects) {
            Map<String, Object> pub = chapterToPublicRow(obj);
            if (pub != null && !pub.isEmpty()) {
                rows.add(pub);
            }
        }
        if (rows.isEmpty()) {
            throw new IOException("No chapters parsed");
        }
        return rows;
    }

    /** Array inner between first [ (at openIdx) and its matching ]. */
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

    private static Map<String, Object> chapterToPublicRow(String chapterObject) {
        Long id = firstLong(chapterObject, "id");
        if (id == null || id < 1) {
            return null;
        }
        String nameArabic = unescapeJsonString(firstString(chapterObject, "name_arabic"));
        String nameSimple = unescapeJsonString(firstString(chapterObject, "name_simple"));
        String nameComplex = unescapeJsonString(firstString(chapterObject, "name_complex"));
        Long versesCount = firstLong(chapterObject, "verses_count");
        String revelation = unescapeJsonString(firstString(chapterObject, "revelation_place"));
        String translatedTitle = translatedNameField(chapterObject);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("nameArabic", nullToEmpty(nameArabic));
        m.put("nameSimple", nullToEmpty(nameSimple));
        m.put("nameComplex", nullToEmpty(nameComplex));
        if (translatedTitle != null && !translatedTitle.isBlank()) {
            m.put("translatedName", translatedTitle);
        }
        if (versesCount != null) {
            m.put("versesCount", versesCount);
        }
        if (revelation != null && !revelation.isBlank()) {
            m.put("revelationPlace", revelation.toLowerCase());
        }
        return m;
    }

    private static String translatedNameField(String chapterObject) {
        int idx = chapterObject.indexOf("\"translated_name\"");
        if (idx < 0) {
            return null;
        }
        int brace = chapterObject.indexOf('{', idx);
        if (brace < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        int end = -1;
        for (int i = brace; i < chapterObject.length(); i++) {
            char c = chapterObject.charAt(i);
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
                    end = i;
                    break;
                }
            }
        }
        if (end < 0) {
            return null;
        }
        String block = chapterObject.substring(brace, end + 1);
        Matcher m = STRING_FIELD.matcher(block);
        while (m.find()) {
            if ("name".equals(m.group(1))) {
                return unescapeJsonString(m.group(2));
            }
        }
        return null;
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
                    case '"':
                    case '\\':
                    case '/':
                        sb.append(n);
                        i++;
                        continue;
                    case 'n':
                        sb.append('\n');
                        i++;
                        continue;
                    case 'r':
                        sb.append('\r');
                        i++;
                        continue;
                    case 't':
                        sb.append('\t');
                        i++;
                        continue;
                    case 'u':
                        if (i + 5 < raw.length()) {
                            try {
                                int cp = Integer.parseInt(raw.substring(i + 2, i + 6), 16);
                                sb.append((char) cp);
                                i += 5;
                                continue;
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
