package model.service;

public class ServiceResult {
    private final boolean success;
    private final String message;

    private ServiceResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    // Preferred naming
    public static ServiceResult success(String message) {
        return new ServiceResult(true, message);
    }

    public static ServiceResult failure(String message) {
        return new ServiceResult(false, message);
    }

    // Backwards-compatible naming used by other services
    public static ServiceResult ok() {
        return new ServiceResult(true, null);
    }

    public static ServiceResult fail(String error) {
        return new ServiceResult(false, error);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Alias for older call sites that expect getError().
     */
    public String getError() {
        return message;
    }
}
