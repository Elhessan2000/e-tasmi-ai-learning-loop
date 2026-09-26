package model.service;

public class InstructorVerificationUpdateResult {
    private final boolean success;
    private final String message;

    private InstructorVerificationUpdateResult(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static InstructorVerificationUpdateResult success(String message) {
        return new InstructorVerificationUpdateResult(true, message);
    }

    public static InstructorVerificationUpdateResult failure(String message) {
        return new InstructorVerificationUpdateResult(false, message);
    }

    public boolean isSuccess() { return success; }
    public String getMessage() { return message; }
}
