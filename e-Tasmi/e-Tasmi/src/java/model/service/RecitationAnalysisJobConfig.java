package model.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Configuration for automatic recitation analysis job leases. */
public final class RecitationAnalysisJobConfig {

    private static final int DEFAULT_STALE_MINUTES = 15;
    private static final int MIN_STALE_MINUTES = 5;
    private static final int MAX_STALE_MINUTES = 120;

    private RecitationAnalysisJobConfig() {
    }

    /**
     * How long an {@code IN_PROGRESS} job may run before it is treated as stale (crash/restart recovery).
     * Default 15 minutes — long enough for QF + ElevenLabs + comparison + OpenAI under normal latency.
     */
    public static int staleLeaseMinutes() {
        String raw = System.getenv("ETASMI_ANALYSIS_STALE_MINUTES");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_STALE_MINUTES;
        }
        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(MIN_STALE_MINUTES, Math.min(MAX_STALE_MINUTES, value));
        } catch (NumberFormatException ex) {
            return DEFAULT_STALE_MINUTES;
        }
    }

    public static Instant staleCutoff() {
        return Instant.now().minus(staleLeaseMinutes(), ChronoUnit.MINUTES);
    }
}
