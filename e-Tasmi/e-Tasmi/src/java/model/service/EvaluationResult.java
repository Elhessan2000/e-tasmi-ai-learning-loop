package model.service;

public class EvaluationResult {
    private final boolean success;
    private final String error;

    private EvaluationResult(boolean success, String error) {
        this.success = success;
        this.error = error;
    }

    public static EvaluationResult success() {
        return new EvaluationResult(true, null);
    }

    public static EvaluationResult failure(String error) {
        return new EvaluationResult(false, error);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }
}
