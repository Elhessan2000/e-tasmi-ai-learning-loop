package util;

import java.time.LocalTime;

/**
 * Time-of-day greeting helpers for the student dashboard hero.
 */
public final class DashboardGreeting {

    private DashboardGreeting() {
    }

    /** Returns {@code morning}, {@code afternoon}, or {@code evening}. */
    public static String resolveTimeOfDay(LocalTime time) {
        int hour = time.getHour();
        if (hour < 12) {
            return "morning";
        }
        if (hour < 18) {
            return "afternoon";
        }
        return "evening";
    }

    /** First token of the session display name for the hero greeting. */
    public static String firstName(String displayName, String fallback) {
        if (displayName == null) {
            return fallback;
        }
        String normalized = displayName.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return fallback;
        }
        int space = normalized.indexOf(' ');
        return space > 0 ? normalized.substring(0, space) : normalized;
    }
}
