package util;

import javax.servlet.http.HttpServletRequest;

/**
 * Small helper to detect "local development" so security throttling (rate limiting / lockouts)
 * can be safely bypassed while testing, without weakening production behaviour.
 *
 * <p>Bypass is granted when either:</p>
 * <ul>
 *   <li>the explicit flag is set — env {@code ETASMI_DISABLE_RATE_LIMIT=true} or system property
 *       {@code etasmi.rateLimit.disabled=true} (works everywhere, the safest production-off switch); or</li>
 *   <li>the request is clearly served on localhost — the Host header resolves to
 *       {@code localhost}/{@code 127.0.0.1}/{@code ::1}, or the client address is a loopback
 *       address. A real production deployment is reached via its public domain, so this never
 *       triggers there.</li>
 * </ul>
 */
public final class DevMode {

    private static final boolean FORCE_BYPASS = readForceBypass();

    private DevMode() {
    }

    private static boolean readForceBypass() {
        String value = System.getenv("ETASMI_DISABLE_RATE_LIMIT");
        if (value == null || value.isBlank()) {
            value = System.getProperty("etasmi.rateLimit.disabled");
        }
        if (value == null) {
            return false;
        }
        value = value.trim();
        return value.equalsIgnoreCase("true")
                || value.equals("1")
                || value.equalsIgnoreCase("yes")
                || value.equalsIgnoreCase("on");
    }

    /** @return true when the explicit development flag is enabled (regardless of request). */
    public static boolean isForced() {
        return FORCE_BYPASS;
    }

    /**
     * @return true when rate limiting / lockouts should be bypassed for this request,
     *         i.e. the dev flag is set or the request is running on localhost.
     */
    public static boolean isRateLimitBypassed(HttpServletRequest request) {
        return FORCE_BYPASS || isLocalRequest(request);
    }

    /** @return true if the request is being served on/from the local machine. */
    public static boolean isLocalRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        if (isLoopbackHost(request.getServerName())) {
            return true;
        }
        return isLoopbackHost(request.getRemoteAddr());
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        host = host.trim();
        return host.equalsIgnoreCase("localhost")
                || host.equals("127.0.0.1")
                || host.startsWith("127.")
                || host.equals("::1")
                || host.equals("0:0:0:0:0:0:0:1");
    }
}
