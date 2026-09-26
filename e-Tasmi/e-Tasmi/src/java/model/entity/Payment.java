package model.entity;

import java.math.BigDecimal;
import java.time.Instant;

public class Payment {
    private long paymentId;
    private long enrollmentId;
    private BigDecimal amount;
    private PaymentStatus paymentStatus;
    private Instant paymentDate;
    private Long verifiedByAdminId;
    private String receiptFilePath;
    private Instant receiptSubmittedAt;
    private Long verifiedByInstructorId;
    private String verificationNote;
    private String paymentReference;
    private String studentNote;
    private String currency;
    private Instant createdAt;

    public long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(long paymentId) {
        this.paymentId = paymentId;
    }

    public long getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(long enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public Instant getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(Instant paymentDate) {
        this.paymentDate = paymentDate;
    }

    public Long getVerifiedByAdminId() {
        return verifiedByAdminId;
    }

    public void setVerifiedByAdminId(Long verifiedByAdminId) {
        this.verifiedByAdminId = verifiedByAdminId;
    }

    public String getReceiptFilePath() {
        return receiptFilePath;
    }

    public void setReceiptFilePath(String receiptFilePath) {
        this.receiptFilePath = receiptFilePath;
    }

    public Instant getReceiptSubmittedAt() {
        return receiptSubmittedAt;
    }

    public void setReceiptSubmittedAt(Instant receiptSubmittedAt) {
        this.receiptSubmittedAt = receiptSubmittedAt;
    }

    public Long getVerifiedByInstructorId() {
        return verifiedByInstructorId;
    }

    public void setVerifiedByInstructorId(Long verifiedByInstructorId) {
        this.verifiedByInstructorId = verifiedByInstructorId;
    }

    public String getVerificationNote() {
        return verificationNote;
    }

    public void setVerificationNote(String verificationNote) {
        this.verificationNote = verificationNote;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public String getStudentNote() {
        return studentNote;
    }

    public void setStudentNote(String studentNote) {
        this.studentNote = studentNote;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
