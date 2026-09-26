package util;

/**
 * Configuration for Zoom Meeting SDK (Web).
 * <p><b>Required credentials</b> come from a Zoom Marketplace <b>General App with the Meeting SDK feature
 * enabled</b> (formerly the "Meeting SDK" app type). In the current Marketplace UI they are labelled
 * <b>Client ID</b> and <b>Client Secret</b> — those values act as the SDK Key and SDK Secret.
 * Do <b>not</b> use {@code ZOOM_CLIENT_ID} / {@code ZOOM_CLIENT_SECRET} (Server-to-Server OAuth)
 * here — signing the Web join JWT with the OAuth secret will produce an invalid signature and join will hang or fail.
 *
 * <p>Environment variables (Railway service Variables / local {@code .env}):
 * <ul>
 *   <li>{@code ZOOM_MEETING_SDK_KEY} (alias: {@code ZOOM_MEETING_SDK_CLIENT_ID}) — Meeting SDK app Client ID</li>
 *   <li>{@code ZOOM_MEETING_SDK_SECRET} (alias: {@code ZOOM_MEETING_SDK_CLIENT_SECRET}) — Meeting SDK app Client Secret</li>
 *   <li>{@code ZOOM_EMBED_ENABLED} — optional; embedding defaults to ON when credentials are set.
 *       Set to {@code false} / {@code 0} / {@code no} / {@code off} to force external zoom.us redirects.</li>
 *   <li>{@code ZOOM_WEB_SDK_VERSION} — optional Web SDK version override</li>
 *   <li>{@code ETASMI_ZOOM_WEB_DEBUG} — optional verbose SDK console logging</li>
 * </ul>
 */
public final class ZoomMeetingSdkConfig {
    private ZoomMeetingSdkConfig() {
    }

    public static String getSdkKey() {
        String v = trimToNull(System.getenv("ZOOM_MEETING_SDK_KEY"));
        if (v == null) {
            v = trimToNull(System.getenv("ZOOM_MEETING_SDK_CLIENT_ID"));
        }
        return v;
    }

    public static String getSdkSecret() {
        String v = trimToNull(System.getenv("ZOOM_MEETING_SDK_SECRET"));
        if (v == null) {
            v = trimToNull(System.getenv("ZOOM_MEETING_SDK_CLIENT_SECRET"));
        }
        return v;
    }

    public static boolean isSdkConfigured() {
        return getSdkKey() != null && getSdkSecret() != null;
    }

    /**
     * Embedding is enabled by default once SDK credentials are configured.
     * Only an explicit {@code ZOOM_EMBED_ENABLED=false|0|no|off} disables it.
     */
    public static boolean isEmbedEnabled() {
        String v = trimToNull(System.getenv("ZOOM_EMBED_ENABLED"));
        if (v == null) {
            return true;
        }
        return !("false".equalsIgnoreCase(v)
                || "0".equals(v)
                || "no".equalsIgnoreCase(v)
                || "off".equalsIgnoreCase(v));
    }

    public static boolean isEmbeddingAvailable() {
        return isEmbedEnabled() && isSdkConfigured();
    }

    public static String getWebSdkVersion() {
        String v = trimToNull(System.getenv("ZOOM_WEB_SDK_VERSION"));
        return v != null ? v : "3.11.2";
    }

    /**
     * When true, Zoom Meeting SDK Web enables verbose console logging ({@code ZoomMtg.init({ debug: true })}).
     */
    public static boolean isWebClientDebug() {
        String v = trimToNull(System.getenv("ETASMI_ZOOM_WEB_DEBUG"));
        if (v == null) {
            return false;
        }
        return "true".equalsIgnoreCase(v)
                || "1".equals(v)
                || "yes".equalsIgnoreCase(v)
                || "on".equalsIgnoreCase(v);
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.replace("\uFEFF", "").trim();
        return t.isEmpty() ? null : t;
    }
}
