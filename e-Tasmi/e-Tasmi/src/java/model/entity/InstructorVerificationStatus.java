package model.entity;

public enum InstructorVerificationStatus {
    PENDING,
    APPROVED,
    REJECTED;

    public static InstructorVerificationStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        return InstructorVerificationStatus.valueOf(value.trim().toUpperCase());
    }
}
