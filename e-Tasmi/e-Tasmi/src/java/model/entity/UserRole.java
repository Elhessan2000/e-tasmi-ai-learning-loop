package model.entity;

public enum UserRole {
    STUDENT,
    INSTRUCTOR,
    ADMIN;

    public static UserRole fromString(String value) {
        if (value == null) {
            return null;
        }
        return UserRole.valueOf(value.trim().toUpperCase());
    }
}
