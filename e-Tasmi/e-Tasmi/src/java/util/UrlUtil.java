package util;

import javax.servlet.http.HttpServletRequest;

public final class UrlUtil {
    private UrlUtil() {
    }

    /**
     * Public origin for building verification and password-reset links.
     * <ol>
     *   <li>{@code APP_BASE_URL} (Railway / production) — if unset, no scheme defaults to https</li>
     *   <li>Reverse-proxy headers {@code X-Forwarded-Proto} / {@code X-Forwarded-Host} when present</li>
     *   <li>Request URL (local dev)</li>
     * </ol>
     */
    public static String getBaseUrl(HttpServletRequest request) {
        String fromEnv = resolveAppUrlFromEnv();
        if (fromEnv != null) {
            return normalizeAppBaseUrl(fromEnv) + nullSafeContextPath(request);
        }

        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        } else {
            scheme = scheme.split(",")[0].trim();
        }
        String serverName = request.getHeader("X-Forwarded-Host");
        if (serverName == null || serverName.isBlank()) {
            serverName = request.getServerName();
        } else {
            serverName = serverName.split(",")[0].trim();
        }
        int serverPort = request.getServerPort();
        String forwardedPort = request.getHeader("X-Forwarded-Port");
        if (forwardedPort != null && !forwardedPort.isBlank()) {
            try {
                serverPort = Integer.parseInt(forwardedPort.split(",")[0].trim());
            } catch (NumberFormatException ignored) {
            }
        }

        boolean isDefaultPort = ("http".equalsIgnoreCase(scheme) && serverPort == 80)
                || ("https".equalsIgnoreCase(scheme) && serverPort == 443);

        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(serverName);
        if (!isDefaultPort) {
            sb.append(":").append(serverPort);
        }
        sb.append(nullSafeContextPath(request));
        return sb.toString();
    }

    private static String nullSafeContextPath(HttpServletRequest request) {
        String ctx = request.getContextPath();
        return ctx == null ? "" : ctx;
    }

    /**
     * Public site origin from env. Checks {@code APP_URL} first, then {@code APP_BASE_URL}.
     */
    public static String resolveAppUrlFromEnv() {
        return firstNonEmpty(trimToNull(System.getenv("APP_URL")), trimToNull(System.getenv("APP_BASE_URL")));
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.startsWith("\uFEFF")) {
            t = t.substring(1).trim();
        }
        return t.isEmpty() ? null : t;
    }

    private static String normalizeAppBaseUrl(String raw) {
        String t = trimToNull(raw);
        if (t == null) {
            return "";
        }
        while (t.endsWith("/")) {
            t = t.substring(0, t.length() - 1);
        }
        if (!(t.startsWith("http://") || t.startsWith("https://"))) {
            t = "https://" + t;
        }
        return t;
    }
}
