package model.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AdminReportResult {
    private String type;
    private String title;
    private String subtitle;
    private String dateFrom;
    private String dateTo;
    private String status;
    private String role;
    private String sessionMode;
    private final List<AdminReportMetric> metrics = new ArrayList<>();
    private final List<String> columns = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(String dateFrom) {
        this.dateFrom = dateFrom;
    }

    public String getDateTo() {
        return dateTo;
    }

    public void setDateTo(String dateTo) {
        this.dateTo = dateTo;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getSessionMode() {
        return sessionMode;
    }

    public void setSessionMode(String sessionMode) {
        this.sessionMode = sessionMode;
    }

    public List<AdminReportMetric> getMetrics() {
        return Collections.unmodifiableList(metrics);
    }

    public void addMetric(String label, String value, String note) {
        metrics.add(new AdminReportMetric(label, value, note));
    }

    public List<String> getColumns() {
        return Collections.unmodifiableList(columns);
    }

    public void setColumns(List<String> values) {
        columns.clear();
        if (values != null) {
            columns.addAll(values);
        }
    }

    public List<List<String>> getRows() {
        return Collections.unmodifiableList(rows);
    }

    public void addRow(List<String> values) {
        rows.add(values == null ? List.of() : List.copyOf(values));
    }

    public int getTotalRows() {
        return rows.size();
    }
}
