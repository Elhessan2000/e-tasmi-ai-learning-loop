package model.entity;

/**
 * Tracks automatic AI analysis lifecycle on a recitation row. Terminal analysis outcomes
 * live in {@code recitation_analysis}; this flag only covers queue/running/completed job control.
 */
public enum RecitationAnalysisJobState {
    NONE,
    IN_PROGRESS,
    COMPLETED;

    public static RecitationAnalysisJobState fromDb(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        try {
            return RecitationAnalysisJobState.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            return NONE;
        }
    }
}
