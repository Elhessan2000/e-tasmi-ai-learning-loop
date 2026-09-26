package model.service;

public class TasmiSessionCreateResult {
    private final boolean success;
    private final String error;
    private final long sessionId;

    private TasmiSessionCreateResult(boolean success, String error, long sessionId) {
        this.success = success;
        this.error = error;
        this.sessionId = sessionId;
    }

    public static TasmiSessionCreateResult success(long sessionId) {
        return new TasmiSessionCreateResult(true, null, sessionId);
    }

    public static TasmiSessionCreateResult failure(String error) {
        return new TasmiSessionCreateResult(false, error, 0);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }

    public long getSessionId() {
        return sessionId;
    }
}
