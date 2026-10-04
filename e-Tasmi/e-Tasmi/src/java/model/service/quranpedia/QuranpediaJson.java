package model.service.quranpedia;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses the JSON subset returned by the Quranpedia content API. */
final class QuranpediaJson {

    private final String src;
    private int pos;

    private QuranpediaJson(String src) {
        this.src = src;
        this.pos = 0;
    }

    static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("empty json");
        }
        QuranpediaJson parser = new QuranpediaJson(text.trim());
        Object value = parser.readValue();
        parser.skip();
        if (parser.pos != parser.src.length()) {
            throw new IllegalArgumentException("trailing json");
        }
        return value;
    }

    private Object readValue() {
        skip();
        if (pos >= src.length()) {
            throw new IllegalArgumentException("unexpected end");
        }
        char c = src.charAt(pos);
        if (c == '{') {
            return readObject();
        }
        if (c == '[') {
            return readArray();
        }
        if (c == '"') {
            return readString();
        }
        if (c == 't' || c == 'f') {
            return readBoolean();
        }
        if (c == 'n') {
            return readNull();
        }
        return readNumber();
    }

    private Map<String, Object> readObject() {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<>();
        skip();
        if (peek('}')) {
            pos++;
            return map;
        }
        while (true) {
            skip();
            String key = readString();
            skip();
            expect(':');
            map.put(key, readValue());
            skip();
            if (peek('}')) {
                pos++;
                return map;
            }
            expect(',');
        }
    }

    private List<Object> readArray() {
        expect('[');
        List<Object> list = new ArrayList<>();
        skip();
        if (peek(']')) {
            pos++;
            return list;
        }
        while (true) {
            list.add(readValue());
            skip();
            if (peek(']')) {
                pos++;
                return list;
            }
            expect(',');
        }
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= src.length()) {
                break;
            }
            char n = src.charAt(pos++);
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
                    if (pos + 4 > src.length()) {
                        throw new IllegalArgumentException("bad unicode escape");
                    }
                    int cp = Integer.parseInt(src.substring(pos, pos + 4), 16);
                    sb.append((char) cp);
                    pos += 4;
                    break;
                default:
                    sb.append(n);
                    break;
            }
        }
        throw new IllegalArgumentException("unterminated string");
    }

    private Boolean readBoolean() {
        if (src.startsWith("true", pos)) {
            pos += 4;
            return Boolean.TRUE;
        }
        if (src.startsWith("false", pos)) {
            pos += 5;
            return Boolean.FALSE;
        }
        throw new IllegalArgumentException("bad boolean");
    }

    private Object readNull() {
        if (!src.startsWith("null", pos)) {
            throw new IllegalArgumentException("bad null");
        }
        pos += 4;
        return null;
    }

    private Number readNumber() {
        int start = pos;
        if (peek('-')) {
            pos++;
        }
        while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
            pos++;
        }
        String token = src.substring(start, pos);
        if (token.indexOf('.') >= 0) {
            return Double.valueOf(token);
        }
        return Long.valueOf(token);
    }

    private void skip() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private boolean peek(char c) {
        return pos < src.length() && src.charAt(pos) == c;
    }

    private void expect(char c) {
        skip();
        if (!peek(c)) {
            throw new IllegalArgumentException("expected " + c);
        }
        pos++;
    }
}
