package model.service;

import model.entity.User;

public class AuthResult {
    private final boolean success;
    private final String error;
    private final User user;
    private final String instructorVerificationStatus;

    private AuthResult(boolean success, String error, User user, String instructorVerificationStatus) {
        this.success = success;
        this.error = error;
        this.user = user;
        this.instructorVerificationStatus = instructorVerificationStatus;
    }

    public static AuthResult success(User user, String instructorVerificationStatus) {
        return new AuthResult(true, null, user, instructorVerificationStatus);
    }

    public static AuthResult failure(String error) {
        return new AuthResult(false, error, null, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }

    public User getUser() {
        return user;
    }

    public String getInstructorVerificationStatus() {
        return instructorVerificationStatus;
    }
}
