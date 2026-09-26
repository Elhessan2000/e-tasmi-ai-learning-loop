package util;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

public final class SessionUtil {
    // Security requirement: auto-logout after inactivity.
    // Adjust to match your SRS if it specifies a different value.
    public static final int SESSION_TIMEOUT_SECONDS = 15 * 60;

    private SessionUtil() {}

    public static HttpSession createUserSession(HttpServletRequest request, long userId, String role, String displayName) {
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(SESSION_TIMEOUT_SECONDS);
        session.setAttribute("userId", userId);
        session.setAttribute("role", role);
        session.setAttribute("displayName", displayName);
        return session;
    }

    /**
     * Reads {@code userId} from the session, tolerating {@link Long}, {@link Integer}, other {@link Number}, or numeric string.
     */
    public static long readUserId(HttpSession session) {
        if (session == null) {
            return 0L;
        }
        Object o = session.getAttribute("userId");
        if (o == null) {
            return 0L;
        }
        if (o instanceof Long) {
            return (Long) o;
        }
        if (o instanceof Integer) {
            return ((Integer) o).longValue();
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o).trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
