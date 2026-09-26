package util;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Best-effort in-memory rate limiter (per JVM). This is not a substitute for a shared store
 * but provides basic brute-force resistance.
 *
 * <p>The whole limiter can be turned off for local development by setting the environment
 * variable {@code ETASMI_DISABLE_RATE_LIMIT=true} (or the system property
 * {@code etasmi.rateLimit.disabled=true}). This is OFF by default, so production behaviour
 * is unchanged unless the flag is explicitly enabled.</p>
 */
public final class RateLimiter {
    private static final ConcurrentHashMap<String, Deque<Long>> BUCKETS = new ConcurrentHashMap<>();

    /** Global kill switch for local development. Read once at class load; defaults to false (enabled). */
    private static final boolean GLOBALLY_DISABLED = readGloballyDisabled();

    private RateLimiter() {
    }

    private static boolean readGloballyDisabled() {
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

    /** @return true when the limiter has been globally disabled (development mode). */
    public static boolean isGloballyDisabled() {
        return GLOBALLY_DISABLED;
    }

    public static boolean allow(String key, int maxAttempts, Duration window) {
        if (GLOBALLY_DISABLED || key == null || key.isBlank()) {
            return true;
        }
        long now = System.currentTimeMillis();
        long cutoff = now - window.toMillis();

        Deque<Long> deque = BUCKETS.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
                deque.removeFirst();
            }
            if (deque.size() >= maxAttempts) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    /**
     * Immediately clears the recorded attempts for a single key, lifting any active lockout.
     * Call this after a successful login (to reset the counter) or when bypassing in dev mode.
     */
    public static void clear(String key) {
        if (key != null && !key.isBlank()) {
            BUCKETS.remove(key);
        }
    }

    /** Clears all tracked buckets (e.g. for tests or an admin "reset limits" action). */
    public static void clearAll() {
        BUCKETS.clear();
    }
}
