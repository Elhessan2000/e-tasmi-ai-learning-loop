package util;

/**
 * Challenge-copy switch for registration email verification.
 *
 * <p>Reads {@code EMAIL_VERIFICATION_ENABLED}. {@code 1}/{@code true}/{@code yes}
 * keep the original verify-email flow. Unset or any other value disables
 * registration verification in this isolated copy. The original
 * {@link model.service.EmailVerificationService} remains in the codebase so
 * the flow can be restored by setting the flag to {@code true}.</p>
 */
public final class EmailVerificationConfig {

    private EmailVerificationConfig() {
    }

    public static boolean isEnabled() {
        String value = System.getenv("EMAIL_VERIFICATION_ENABLED");
        if (value == null || value.isBlank()) {
            value = System.getProperty("etasmi.emailVerification.enabled");
        }
        if (value == null || value.isBlank()) {
            return false;
        }
        value = value.trim();
        return "1".equals(value)
                || "true".equalsIgnoreCase(value)
                || "yes".equalsIgnoreCase(value);
    }
}
