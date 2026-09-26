package model.entity;

public enum SessionRecordingStatus {
    PENDING,
    AVAILABLE,
    UNAVAILABLE,
    SYNC_FAILED;

    public static SessionRecordingStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SessionRecordingStatus.valueOf(value.trim().toUpperCase());
        } catch (Exception ex) {
            return null;
        }
    }
}
