package model.entity;

import java.math.BigDecimal;

/** A single revenue grouping (by session or by instructor) for analytics cards/charts. */
public class RevenueBucket {
    private long id;
    private String label;
    private long transactionCount;
    private BigDecimal revenue = BigDecimal.ZERO;

    public RevenueBucket() {
    }

    public RevenueBucket(long id, String label, long transactionCount, BigDecimal revenue) {
        this.id = id;
        this.label = label;
        this.transactionCount = transactionCount;
        this.revenue = revenue == null ? BigDecimal.ZERO : revenue;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public long getTransactionCount() { return transactionCount; }
    public void setTransactionCount(long transactionCount) { this.transactionCount = transactionCount; }
    public BigDecimal getRevenue() { return revenue == null ? BigDecimal.ZERO : revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
}
