package model.service.stt;

/**
 * Reads the first object-level string field of the given name.
 * Nested objects, such as a words array, are skipped.
 */
final class JsonText {

    private JsonText() {
    }

    static String topLevelString(String json, String field) {
        if (json == null || field == null) {
            return null;
        }
        int i = 0;
        int n = json.length();
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        while (i < n) {
            char c = json.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                i++;
                continue;
            }
            if (c == '"') {
                int end = endOfString(json, i + 1);
                if (end < 0) {
                    return null;
                }
                String key = unescape(json.substring(i + 1, end));
                int after = skipSpace(json, end + 1);
                if (depth == 1 && field.equals(key) && after < n && json.charAt(after) == ':') {
                    int valueAt = skipSpace(json, after + 1);
                    if (valueAt < n && json.charAt(valueAt) == '"') {
                        int valueEnd = endOfString(json, valueAt + 1);
                        if (valueEnd < 0) {
                            return null;
                        }
                        return unescape(json.substring(valueAt + 1, valueEnd));
                    }
                    return null;
                }
                i = end + 1;
                continue;
            }
            if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            }
            i++;
        }
        return null;
    }

    private static int endOfString(String json, int start) {
        boolean escape = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }

    private static int skipSpace(String json, int i) {
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        return i;
    }

    private static String unescape(String raw) {
        if (raw.indexOf('\\') < 0) {
            return raw;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c != '\\' || i + 1 >= raw.length()) {
                sb.append(c);
                continue;
            }
            char n = raw.charAt(++i);
            switch (n) {
                case '"':
                case '\\':
                case '/':
                    sb.append(n);
                    break;
                case 'b':
                    sb.append('\b');
                    break;
                case 'f':
                    sb.append('\f');
                    break;
                case 'n':
                    sb.append('\n');
                    break;
                case 'r':
                    sb.append('\r');
                    break;
                case 't':
                    sb.append('\t');
                    break;
                case 'u':
                    if (i + 4 < raw.length()) {
                        String hex = raw.substring(i + 1, i + 5);
                        sb.append((char) Integer.parseInt(hex, 16));
                        i += 4;
                    }
                    break;
                default:
                    sb.append(n);
                    break;
            }
        }
        return sb.toString();
    }
}
