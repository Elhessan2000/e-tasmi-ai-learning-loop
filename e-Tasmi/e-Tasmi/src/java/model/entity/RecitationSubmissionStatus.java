package model.entity;

/**
 * Lifecycle of a self-recorded / uploaded recitation made through the
 * student "Recitation Studio" workflow.
 *
 * PENDING   - submitted (or in-progress placeholder) and awaiting instructor review.
 * EVALUATED - an instructor has reviewed the recitation and left feedback.
 */
public enum RecitationSubmissionStatus {
    PENDING,
    EVALUATED;

    public static RecitationSubmissionStatus fromString(String raw) {
        if (raw == null) {
            return PENDING;
        }
        String normalized = raw.trim().toUpperCase();
        for (RecitationSubmissionStatus status : values()) {
            if (status.name().equals(normalized)) {
                return status;
            }
        }
        return PENDING;
    }
}
