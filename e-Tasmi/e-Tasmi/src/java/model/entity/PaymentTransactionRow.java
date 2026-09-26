package model.entity;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Denormalized payment row joining payment + enrollment + student + session + instructor.
 * Powers the admin monitoring table, instructor verification queue, and PDF reports.
 */
public class PaymentTransactionRow {
    private long paymentId;
    private long enrollmentId;

    private long studentId;
    private long studentUserId;
    private String studentName;
    private String studentEmail;

    private long sessionId;
    private String sessionTitle;
    private BigDecimal sessionFee;

    private long instructorId;
    private long instructorUserId;
    private String instructorName;
    private String instructorEmail;

    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private EnrollmentStatus enrollmentStatus;

    private String paymentReference;
    private String studentNote;
    private String verificationNote;
    private String receiptFilePath;
    private Instant receiptSubmittedAt;
    private Instant paymentDate;
    private Instant createdAt;

    public long getPaymentId() { return paymentId; }
    public void setPaymentId(long paymentId) { this.paymentId = paymentId; }
    public long getEnrollmentId() { return enrollmentId; }
    public void setEnrollmentId(long enrollmentId) { this.enrollmentId = enrollmentId; }
    public long getStudentId() { return studentId; }
    public void setStudentId(long studentId) { this.studentId = studentId; }
    public long getStudentUserId() { return studentUserId; }
    public void setStudentUserId(long studentUserId) { this.studentUserId = studentUserId; }
    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public String getStudentEmail() { return studentEmail; }
    public void setStudentEmail(String studentEmail) { this.studentEmail = studentEmail; }
    public long getSessionId() { return sessionId; }
    public void setSessionId(long sessionId) { this.sessionId = sessionId; }
    public String getSessionTitle() { return sessionTitle; }
    public void setSessionTitle(String sessionTitle) { this.sessionTitle = sessionTitle; }
    public BigDecimal getSessionFee() { return sessionFee; }
    public void setSessionFee(BigDecimal sessionFee) { this.sessionFee = sessionFee; }
    public long getInstructorId() { return instructorId; }
    public void setInstructorId(long instructorId) { this.instructorId = instructorId; }
    public long getInstructorUserId() { return instructorUserId; }
    public void setInstructorUserId(long instructorUserId) { this.instructorUserId = instructorUserId; }
    public String getInstructorName() { return instructorName; }
    public void setInstructorName(String instructorName) { this.instructorName = instructorName; }
    public String getInstructorEmail() { return instructorEmail; }
    public void setInstructorEmail(String instructorEmail) { this.instructorEmail = instructorEmail; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public EnrollmentStatus getEnrollmentStatus() { return enrollmentStatus; }
    public void setEnrollmentStatus(EnrollmentStatus enrollmentStatus) { this.enrollmentStatus = enrollmentStatus; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public String getStudentNote() { return studentNote; }
    public void setStudentNote(String studentNote) { this.studentNote = studentNote; }
    public String getVerificationNote() { return verificationNote; }
    public void setVerificationNote(String verificationNote) { this.verificationNote = verificationNote; }
    public String getReceiptFilePath() { return receiptFilePath; }
    public void setReceiptFilePath(String receiptFilePath) { this.receiptFilePath = receiptFilePath; }
    public Instant getReceiptSubmittedAt() { return receiptSubmittedAt; }
    public void setReceiptSubmittedAt(Instant receiptSubmittedAt) { this.receiptSubmittedAt = receiptSubmittedAt; }
    public Instant getPaymentDate() { return paymentDate; }
    public void setPaymentDate(Instant paymentDate) { this.paymentDate = paymentDate; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
