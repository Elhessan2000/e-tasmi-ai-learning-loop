package util;

import java.security.SecureRandom;

public final class CodeUtil {
    private static final SecureRandom RANDOM = new SecureRandom();

    private CodeUtil() {
    }

    /**
     * Generates a 6-digit numeric code, zero-padded.
     */
    public static String generateSixDigitCode() {
        int value = RANDOM.nextInt(1_000_000);
        return String.format("%06d", value);
    }
}
