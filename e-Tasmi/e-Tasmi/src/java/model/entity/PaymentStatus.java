package model.entity;

/**
 * Lifecycle of a manual QR-transfer payment.
 *
 * <ul>
 *   <li>{@code PENDING} – payment record created, student has not uploaded proof yet.</li>
 *   <li>{@code AWAITING_VERIFICATION} – student uploaded a receipt; instructor must review.</li>
 *   <li>{@code APPROVED} – instructor confirmed the transfer; enrollment is granted.</li>
 *   <li>{@code REJECTED} – instructor rejected the receipt; student may resubmit.</li>
 * </ul>
 */
public enum PaymentStatus {
    PENDING,
    AWAITING_VERIFICATION,
    APPROVED,
    REJECTED;

    public static PaymentStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase();
        if (normalized.isEmpty()) {
            return null;
        }
        // Backward compatibility with the legacy PayPal-era status names.
        switch (normalized) {
            case "SUCCESS":
            case "COMPLETED":
            case "PAID":
                return APPROVED;
            case "FAILED":
            case "REJECT":
            case "REJECTED":
                return REJECTED;
            case "SUBMITTED":
            case "AWAITING":
            case "AWAITING_VERIFICATION":
                return AWAITING_VERIFICATION;
            default:
                try {
                    return PaymentStatus.valueOf(normalized);
                } catch (IllegalArgumentException ex) {
                    return null;
                }
        }
    }

    /** Display-friendly label for badges and tables. */
    public String displayLabel() {
        switch (this) {
            case PENDING:
                return "Awaiting Payment";
            case AWAITING_VERIFICATION:
                return "Pending Verification";
            case APPROVED:
                return "Approved";
            case REJECTED:
                return "Rejected";
            default:
                return name();
        }
    }
}
