package model.service;

public class RegistrationResult {
    private final boolean success;
    private final String error;
    private final Long userId;
    private final String email;
    private final boolean existingUnverified;

    private RegistrationResult(boolean success, String error, Long userId, String email, boolean existingUnverified) {
        this.success = success;
        this.error = error;
        this.userId = userId;
        this.email = email;
        this.existingUnverified = existingUnverified;
    }

    public static RegistrationResult success(long userId, String email) {
        return new RegistrationResult(true, null, userId, email, false);
    }

    public static RegistrationResult failure(String error) {
        return new RegistrationResult(false, error, null, null, false);
    }

    public static RegistrationResult existingUnverified(long userId, String email) {
        return new RegistrationResult(false,
                "This email is already registered but not verified yet.",
                userId,
                email,
                true);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public boolean isExistingUnverified() {
        return existingUnverified;
    }
}
