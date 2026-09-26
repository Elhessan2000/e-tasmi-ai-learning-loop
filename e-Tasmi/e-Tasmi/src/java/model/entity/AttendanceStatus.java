package model.entity;

public enum AttendanceStatus {
    PRESENT,
    ABSENT;

    public static AttendanceStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        return AttendanceStatus.valueOf(value.trim().toUpperCase());
    }
}
