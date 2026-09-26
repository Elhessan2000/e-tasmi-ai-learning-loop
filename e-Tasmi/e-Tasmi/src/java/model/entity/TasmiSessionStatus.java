package model.entity;

public enum TasmiSessionStatus {
    SCHEDULED,
    ONGOING,
    COMPLETED,
    CANCELLED;

    public static TasmiSessionStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        return TasmiSessionStatus.valueOf(value.trim().toUpperCase());
    }
}
