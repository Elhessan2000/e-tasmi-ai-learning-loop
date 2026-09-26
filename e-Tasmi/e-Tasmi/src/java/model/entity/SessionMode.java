package model.entity;

public enum SessionMode {
    ONLINE,
    PHYSICAL;

    public static SessionMode fromString(String value) {
        if (value == null) {
            return null;
        }
        return SessionMode.valueOf(value.trim().toUpperCase());
    }
}
