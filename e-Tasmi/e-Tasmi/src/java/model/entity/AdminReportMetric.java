package model.entity;

public class AdminReportMetric {
    private final String label;
    private final String value;
    private final String note;

    public AdminReportMetric(String label, String value, String note) {
        this.label = label;
        this.value = value;
        this.note = note;
    }

    public String getLabel() {
        return label;
    }

    public String getValue() {
        return value;
    }

    public String getNote() {
        return note;
    }
}
