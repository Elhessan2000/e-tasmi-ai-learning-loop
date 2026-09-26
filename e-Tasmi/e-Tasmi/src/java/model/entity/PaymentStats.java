package model.entity;

import java.math.BigDecimal;

/** Aggregate counters + revenue for the admin payment monitoring dashboard. */
public class PaymentStats {
    private long totalTransactions;
    private long pendingCount;
    private long awaitingCount;
    private long approvedCount;
    private long rejectedCount;
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    private String currency = "MYR";

    public long getTotalTransactions() { return totalTransactions; }
    public void setTotalTransactions(long totalTransactions) { this.totalTransactions = totalTransactions; }
    public long getPendingCount() { return pendingCount; }
    public void setPendingCount(long pendingCount) { this.pendingCount = pendingCount; }
    public long getAwaitingCount() { return awaitingCount; }
    public void setAwaitingCount(long awaitingCount) { this.awaitingCount = awaitingCount; }
    public long getApprovedCount() { return approvedCount; }
    public void setApprovedCount(long approvedCount) { this.approvedCount = approvedCount; }
    public long getRejectedCount() { return rejectedCount; }
    public void setRejectedCount(long rejectedCount) { this.rejectedCount = rejectedCount; }
    public BigDecimal getTotalRevenue() { return totalRevenue == null ? BigDecimal.ZERO : totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    /** Pending + awaiting verification combined (everything not yet resolved). */
    public long getOpenCount() {
        return pendingCount + awaitingCount;
    }
}
