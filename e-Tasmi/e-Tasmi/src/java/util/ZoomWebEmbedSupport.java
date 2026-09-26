package util;

import javax.servlet.http.HttpServletRequest;

/**
 * Zoom Meeting SDK for Web requires a <a href="https://developer.mozilla.org/en-US/docs/Web/Security/Secure_Contexts">secure context</a>
 * (HTTPS or localhost). Plain HTTP on a LAN IP (e.g. {@code http://192.168.0.104}) is not secure — in-browser join usually fails;
 * use the participant join URL in the Zoom app instead.
 */
public final class ZoomWebEmbedSupport {
    private ZoomWebEmbedSupport() {
    }

    public static boolean requestAllowsInBrowserSdk(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        if (request.isSecure()) {
            return true;
        }
        String xfp = request.getHeader("X-Forwarded-Proto");
        if (xfp != null && !xfp.isBlank()) {
            String first = xfp.split(",")[0].trim();
            if ("https".equalsIgnoreCase(first)) {
                return true;
            }
        }
        String host = request.getServerName();
        if (host == null) {
            return false;
        }
        String h = host.trim();
        return "localhost".equalsIgnoreCase(h)
                || "127.0.0.1".equals(h)
                || "[::1]".equals(h);
    }
}
