package model.entity;

import java.time.Instant;

/**
 * Instructor-published payment destination shown to students on the QR payment page.
 * Money is transferred directly to the instructor; the platform never holds funds.
 */
public class InstructorPaymentSettings {
    private long settingsId;
    private long instructorId;
    private String qrImageUrl;
    private String bankName;
    private String accountHolderName;
    private String accountNumber;
    private String paymentNotes;
    private boolean active;
    private Instant createdAt;
    private Instant updatedAt;

    public long getSettingsId() {
        return settingsId;
    }

    public void setSettingsId(long settingsId) {
        this.settingsId = settingsId;
    }

    public long getInstructorId() {
        return instructorId;
    }

    public void setInstructorId(long instructorId) {
        this.instructorId = instructorId;
    }

    public String getQrImageUrl() {
        return qrImageUrl;
    }

    public void setQrImageUrl(String qrImageUrl) {
        this.qrImageUrl = qrImageUrl;
    }

    public String getBankName() {
        return bankName;
    }

    public void setBankName(String bankName) {
        this.bankName = bankName;
    }

    public String getAccountHolderName() {
        return accountHolderName;
    }

    public void setAccountHolderName(String accountHolderName) {
        this.accountHolderName = accountHolderName;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getPaymentNotes() {
        return paymentNotes;
    }

    public void setPaymentNotes(String paymentNotes) {
        this.paymentNotes = paymentNotes;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /** True when the instructor has provided enough detail for a student to pay. */
    public boolean isUsable() {
        boolean hasBank = bankName != null && !bankName.isBlank()
                && accountHolderName != null && !accountHolderName.isBlank();
        boolean hasQr = qrImageUrl != null && !qrImageUrl.isBlank();
        return active && (hasBank || hasQr);
    }
}
