package util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class MeetingLinkUtil {
    private MeetingLinkUtil() {
    }

    public static String normalize(String link) {
        if (link == null) {
            return null;
        }
        String trimmed = link.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String toStudentJoinLink(String link) {
        String normalized = normalize(link);
        if (normalized == null) {
            return null;
        }

        try {
            URI uri = new URI(normalized);
            String host = uri.getHost();
            if (host == null || !host.toLowerCase(Locale.ROOT).contains("zoom.us")) {
                return normalized;
            }

            String path = uri.getPath() == null ? "" : uri.getPath();
            String meetingId = extractMeetingId(path);
            if (meetingId == null) {
                return stripQueryParameter(normalized, "zak");
            }

            Map<String, String> query = parseQuery(uri.getRawQuery());
            query.remove("zak");

            if (path.contains("/j/") || path.contains("/wc/join/")) {
                return rebuild(uri.getScheme(), host, path, query);
            }

            if (path.contains("/s/") || path.contains("/start") || path.contains("/wc/")) {
                String joinPath = "/j/" + meetingId;
                return rebuild(uri.getScheme(), host, joinPath, query);
            }

            return stripQueryParameter(normalized, "zak");
        } catch (URISyntaxException ex) {
            return normalized;
        }
    }

    private static String extractMeetingId(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String[] parts = path.split("/");
        for (String part : parts) {
            if (part != null && part.matches("\\d{9,12}")) {
                return part;
            }
        }
        return null;
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return params;
        }
        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            if (idx < 0) {
                params.put(pair, "");
            } else {
                params.put(pair.substring(0, idx), pair.substring(idx + 1));
            }
        }
        return params;
    }

    private static String rebuild(String scheme, String host, String path, Map<String, String> query) {
        StringBuilder sb = new StringBuilder();
        sb.append((scheme == null || scheme.isBlank()) ? "https" : scheme)
                .append("://")
                .append(host)
                .append(path);
        if (query != null && !query.isEmpty()) {
            sb.append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : query.entrySet()) {
                if (!first) {
                    sb.append('&');
                }
                sb.append(entry.getKey());
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    sb.append('=').append(entry.getValue());
                }
                first = false;
            }
        }
        return sb.toString();
    }

    private static String stripQueryParameter(String link, String parameter) {
        try {
            URI uri = new URI(link);
            Map<String, String> query = parseQuery(uri.getRawQuery());
            query.remove(parameter);
            return rebuild(uri.getScheme(), uri.getHost(), uri.getPath(), query);
        } catch (URISyntaxException ex) {
            return link;
        }
    }
}
