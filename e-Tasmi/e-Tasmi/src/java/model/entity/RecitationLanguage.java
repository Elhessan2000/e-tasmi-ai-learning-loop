package model.entity;

/**
 * A recitation language option offered in the Recitation Studio bottom-sheet
 * (e.g. Arabic, English, Malay). Backed by the {@code recitation_language} table.
 */
public class RecitationLanguage {
    private long languageId;
    private String name;
    private String code;
    private boolean active = true;
    private int sortOrder;

    public long getLanguageId() {
        return languageId;
    }

    public void setLanguageId(long languageId) {
        this.languageId = languageId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
