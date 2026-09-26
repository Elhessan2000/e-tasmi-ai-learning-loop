package model.entity;

public enum StudentLevel {
    PRIMARY_SCHOOL("Primary School"),
    SECONDARY_SCHOOL("Secondary School"),
    HIGH_SCHOOL("High School"),
    UNIVERSITY("University");

    private final String displayName;

    StudentLevel(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static StudentLevel fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        for (StudentLevel level : values()) {
            if (level.name().equalsIgnoreCase(trimmed) || level.displayName.equalsIgnoreCase(trimmed)) {
                return level;
            }
        }
        return null;
    }
}
