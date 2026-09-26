package model.service;

/**
 * Outcome of a Recitation Studio operation (start placeholder / finalize / upload).
 * Mirrors the {@link AuthResult}/{@link RecitationSubmitResult} convention used
 * elsewhere in the service layer.
 */
public class RecitationSubmissionResult {
    private final boolean success;
    private final String error;
    private final long recitationId;

    private RecitationSubmissionResult(boolean success, String error, long recitationId) {
        this.success = success;
        this.error = error;
        this.recitationId = recitationId;
    }

    public static RecitationSubmissionResult success(long recitationId) {
        return new RecitationSubmissionResult(true, null, recitationId);
    }

    public static RecitationSubmissionResult failure(String error) {
        return new RecitationSubmissionResult(false, error, 0L);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }

    public long getRecitationId() {
        return recitationId;
    }
}
