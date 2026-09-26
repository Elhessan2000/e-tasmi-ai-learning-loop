package model.service.quran;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanitized tafsir resource rows from {@code GET /content/api/v4/resources/tafsirs}.
 */
public final class QuranTafsirResourcesParse {

    private static final Pattern STRING_FIELD = Pattern.compile(
            "\"([a-zA-Z0-9_]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern INT_FIELD = Pattern.compile(
            "\"([a-zA-Z0-9_]+)\"\\s*:\\s*(-?\\d+)");

    private QuranTafsirResourcesParse() {
    }

    public static List<Map<String, Object>> toPublicMaps(String apiJsonBody) throws IOException {
        if (apiJsonBody == null || apiJsonBody.isBlank()) {
            throw new IOException("Empty tafsirs catalogue response");
        }
        int keyIdx = apiJsonBody.indexOf("\"tafsirs\"");
        if (keyIdx < 0) {
            throw new IOException("Missing tafsirs array");
        }
        int bracket = apiJsonBody.indexOf('[', keyIdx);
        if (bracket < 0) {
            throw new IOException("Invalid tafsirs array");
        }
        String inner = extractBracketContents(apiJsonBody, bracket);
        if (inner == null) {
            throw new IOException("Unterminated tafsirs array");
        }
        List<String> objects = splitTopLevelJsonObjects(inner.trim());
        List<Map<String, Object>> rows = new ArrayList<>(objects.size());
        for (String obj : objects) {
            Map<String, Object> pub = resourceToPublicRow(obj);
            if (pub != null && !pub.isEmpty()) {
                rows.add(pub);
            }
        }
        if (rows.isEmpty()) {
            throw new IOException("No tafsir resources parsed");
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

    private static Map<String, Object> resourceToPublicRow(String obj) {
        Long id = firstLong(obj, "id");
        if (id == null || id < 1) {
            return null;
        }
        String name = unescapeJsonString(firstString(obj, "name"));
        String authorName = unescapeJsonString(firstString(obj, "author_name"));
        String slug = unescapeJsonString(firstString(obj, "slug"));
        String languageName = unescapeJsonString(firstString(obj, "language_name"));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        if (name != null && !name.isBlank()) {
            m.put("name", name);
        }
        if (authorName != null && !authorName.isBlank()) {
            m.put("authorName", authorName);
        }
        if (slug != null && !slug.isBlank()) {
            m.put("slug", slug);
        }
        if (languageName != null && !languageName.isBlank()) {
            m.put("languageName", languageName);
        }
        return m;
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
