package model.entity;

public enum UserStatus {
    ACTIVE,
    INACTIVE,
    DELETED;

    public static UserStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UserStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
