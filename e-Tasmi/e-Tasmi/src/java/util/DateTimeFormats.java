package util;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Single source of truth for how e-Tasmi interprets and presents session dates/times.
 *
 * <p><strong>Storage model.</strong> Session schedule values are stored as a
 * timezone-agnostic wall-clock ({@code session_date DATE}, {@code session_time TIME}
 * &rarr; {@link java.time.LocalDate}/{@link LocalTime}). They are NOT instants, so no
 * UTC conversion is applied to them, and they round-trip through MySQL identically on
 * every host.</p>
 *
 * <p><strong>Display.</strong> Every human-facing surface renders the time via
 * {@link #sessionTime(LocalTime)} ({@code "hh:mm a"} &rarr; e.g. {@code 10:00 PM}).
 * Machine-readable contexts intentionally keep 24-hour {@code HH:mm} (the
 * {@code <input type="time">} control and the calendar JSON feeds, which the browser /
 * calendar JS parse for layout math).</p>
 *
 * <p><strong>Timezone.</strong> Instant-to-local math (the dashboard "Next Up"
 * countdown, "still upcoming" filtering, today's-sessions bucketing, calendar
 * positioning, timestamp display) is anchored to {@link #APP_ZONE} rather than the
 * ambient JVM default. On localhost the JVM default is {@code Asia/Kuala_Lumpur}, but
 * managed hosts (e.g. Railway) default to UTC, which previously shifted all session math
 * by 8 hours and surfaced the wrong session in "Next Up". Anchoring to a stable zone
 * makes every environment behave identically.</p>
 */
public final class DateTimeFormats {

    /** Fallback zone used when no timezone is configured via the environment. */
    public static final String DEFAULT_ZONE_ID = "Asia/Kuala_Lumpur";

    /**
     * The single application timezone, resolved once at class load.
     *
     * <p>Resolution order: {@code APP_TIME_ZONE} env/property &rarr; {@code TZ}
     * env/property &rarr; {@link #DEFAULT_ZONE_ID}. An unset or invalid value falls back
     * to the default, so a forgotten Railway variable can no longer break the dashboard.</p>
     */
    public static final ZoneId APP_ZONE = resolveAppZone();

    /** Canonical human-facing session time, e.g. {@code 10:00 PM}. */
    private static final DateTimeFormatter SESSION_TIME =
            DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    private DateTimeFormats() {
    }

    /**
     * @return the stable application zone (see {@link #APP_ZONE}). Prefer this over
     * {@link ZoneId#systemDefault()} for any session date/time math or display.
     */
    public static ZoneId appZone() {
        return APP_ZONE;
    }

    /**
     * Formats a session time for display in 12-hour form with an AM/PM marker.
     *
     * @param time the wall-clock session time (may be {@code null})
     * @return e.g. {@code "10:00 PM"}, or an empty string when {@code time} is {@code null}
     */
    public static String sessionTime(LocalTime time) {
        return time == null ? "" : time.format(SESSION_TIME);
    }

    private static ZoneId resolveAppZone() {
        for (String key : new String[]{"APP_TIME_ZONE", "TZ"}) {
            String configured = System.getProperty(key);
            if (configured == null || configured.isBlank()) {
                configured = System.getenv(key);
            }
            if (configured != null && !configured.isBlank()) {
                try {
                    return ZoneId.of(configured.trim());
                } catch (Exception ignored) {
                    // fall through to the next candidate / default
                }
            }
        }
        return ZoneId.of(DEFAULT_ZONE_ID);
    }
}
