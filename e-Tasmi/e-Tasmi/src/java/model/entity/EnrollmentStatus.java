package model.entity;

public enum EnrollmentStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    public static EnrollmentStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        return EnrollmentStatus.valueOf(value.trim().toUpperCase());
    }
}
