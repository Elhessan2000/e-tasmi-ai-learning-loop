package model.service;

public class RecitationSubmitResult {
    private final boolean success;
    private final String error;
    private final long recitationId;

    private RecitationSubmitResult(boolean success, String error, long recitationId) {
        this.success = success;
        this.error = error;
        this.recitationId = recitationId;
    }

    public static RecitationSubmitResult success(long recitationId) {
        return new RecitationSubmitResult(true, null, recitationId);
    }

    public static RecitationSubmitResult failure(String error) {
        return new RecitationSubmitResult(false, error, 0);
    }

    public long getRecitationId() {
        return recitationId;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }
}
