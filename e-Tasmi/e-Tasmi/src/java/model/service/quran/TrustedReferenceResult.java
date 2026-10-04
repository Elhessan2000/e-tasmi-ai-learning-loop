package model.service.quran;

import java.util.Objects;

/**
 * Outcome of a trusted-reference fetch. On failure the result carries a reason
 * and no Qur'an text — callers must not invent a substitute.
 */
public final class TrustedReferenceResult {

    private final TrustedReference reference;
    private final String reason;

    private TrustedReferenceResult(TrustedReference reference, String reason) {
        this.reference = reference;
        this.reason = reason;
    }

    public static TrustedReferenceResult ok(TrustedReference reference) {
        return new TrustedReferenceResult(Objects.requireNonNull(reference, "reference"), null);
    }

    public static TrustedReferenceResult unavailable(String reason) {
        String message = (reason == null || reason.isBlank())
                ? "Trusted Qur'an reference is unavailable."
                : reason.trim();
        return new TrustedReferenceResult(null, message);
    }

    public boolean isOk() {
        return reference != null;
    }

    public boolean isUnavailable() {
        return reference == null;
    }

    public TrustedReference getReference() {
        return reference;
    }

    public String getReason() {
        return reason;
    }
}
