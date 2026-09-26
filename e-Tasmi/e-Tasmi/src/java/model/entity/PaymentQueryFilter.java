package model.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Mutable filter/sort/paging criteria for searching payment transactions. */
public class PaymentQueryFilter {
    private Long studentId;
    private Long instructorId;
    private Long sessionId;
    private PaymentStatus status;
    private LocalDate fromDate;
    private LocalDate toDate;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private String search;

    private String sortBy = "created_at";
    private boolean sortAsc = false;
    private int offset = 0;
    private int limit = 25;

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getInstructorId() { return instructorId; }
    public void setInstructorId(Long instructorId) { this.instructorId = instructorId; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public LocalDate getFromDate() { return fromDate; }
    public void setFromDate(LocalDate fromDate) { this.fromDate = fromDate; }
    public LocalDate getToDate() { return toDate; }
    public void setToDate(LocalDate toDate) { this.toDate = toDate; }
    public BigDecimal getMinAmount() { return minAmount; }
    public void setMinAmount(BigDecimal minAmount) { this.minAmount = minAmount; }
    public BigDecimal getMaxAmount() { return maxAmount; }
    public void setMaxAmount(BigDecimal maxAmount) { this.maxAmount = maxAmount; }
    public String getSearch() { return search; }
    public void setSearch(String search) { this.search = search; }
    public String getSortBy() { return sortBy; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public boolean isSortAsc() { return sortAsc; }
    public void setSortAsc(boolean sortAsc) { this.sortAsc = sortAsc; }
    public int getOffset() { return offset; }
    public void setOffset(int offset) { this.offset = Math.max(0, offset); }
    public int getLimit() { return limit; }
    public void setLimit(int limit) { this.limit = Math.max(1, Math.min(200, limit)); }
}
