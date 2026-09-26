package model.service;

public class RecitationSubmitResult {
    private final boolean success;
    private final String error;

    private RecitationSubmitResult(boolean success, String error) {
        this.success = success;
        this.error = error;
    }

    public static RecitationSubmitResult success() {
        return new RecitationSubmitResult(true, null);
    }

    public static RecitationSubmitResult failure(String error) {
        return new RecitationSubmitResult(false, error);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }
}
