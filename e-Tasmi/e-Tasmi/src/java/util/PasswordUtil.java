package util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordUtil {
    // BCrypt is the preferred algorithm for new passwords.
    private static final int BCRYPT_COST = 12;

    // Legacy support: PBKDF2 hashes created before BCrypt migration.
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int PBKDF2_SALT_BYTES = 16;
    private static final int PBKDF2_KEY_BITS = 256;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordUtil() {}

    public static String hashPassword(char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Password is required");
        }

        // Prefer BCrypt when available on the runtime classpath.
        if (BcryptAdapter.isAvailable()) {
            // BCrypt expects String. We rely on the JVM to manage internal Strings; callers should clear the char[].
            return BcryptAdapter.hashpw(new String(password), BcryptAdapter.gensalt(BCRYPT_COST));
        }

        // Fallback for environments where BCrypt jar is not on the build/runtime classpath.
        return legacyHashPasswordPbkdf2(password);
    }

    public static boolean verifyPassword(char[] password, String stored) {
        if (stored == null || stored.isBlank()) {
            return false;
        }

        // BCrypt hashes start with $2a$, $2b$, or $2y$.
        if (stored.startsWith("$2a$") || stored.startsWith("$2b$") || stored.startsWith("$2y$")) {
            if (password == null) {
                return false;
            }
            try {
                if (!BcryptAdapter.isAvailable()) {
                    return false;
                }
                return BcryptAdapter.checkpw(new String(password), stored);
            } catch (Exception ex) {
                return false;
            }
        }

        // Legacy PBKDF2 format: pbkdf2$ALGORITHM$ITERATIONS$SALT$DERIVED
        if (!stored.startsWith("pbkdf2$")) {
            return false;
        }

        String[] parts = stored.split("\\$");
        if (parts.length != 5) {
            return false;
        }
        if (!"pbkdf2".equals(parts[0])) {
            return false;
        }

        String algorithm = parts[1];
        // Currently we only support PBKDF2WithHmacSHA256 for legacy hashes.
        if (algorithm == null || !algorithm.equalsIgnoreCase(PBKDF2_ALGORITHM)) {
            return false;
        }
        int iterations;
        try {
            iterations = Integer.parseInt(parts[2]);
        } catch (NumberFormatException ex) {
            return false;
        }

        byte[] salt;
        byte[] expected;
        try {
            salt = Base64.getDecoder().decode(parts[3]);
            expected = Base64.getDecoder().decode(parts[4]);
        } catch (IllegalArgumentException ex) {
            return false;
        }

        byte[] actual = pbkdf2(password, salt, iterations, expected.length * 8);
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyBits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyBits);
            SecretKeyFactory skf = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            return skf.generateSecret(spec).getEncoded();
        } catch (Exception ex) {
            throw new IllegalStateException("Password hashing failed", ex);
        }
    }

    /**
     * Reflection-based adapter to avoid compile-time dependency on jBCrypt.
     * Uses org.mindrot.jbcrypt.BCrypt when the jar is on the classpath.
     */
    private static final class BcryptAdapter {
        private static final String CLASS_NAME = "org.mindrot.jbcrypt.BCrypt";

        private BcryptAdapter() {
        }

        static boolean isAvailable() {
            try {
                Class.forName(CLASS_NAME);
                return true;
            } catch (ClassNotFoundException ex) {
                return false;
            }
        }

        static String gensalt(int logRounds) {
            try {
                Class<?> c = Class.forName(CLASS_NAME);
                return (String) c.getMethod("gensalt", int.class).invoke(null, logRounds);
            } catch (Exception ex) {
                throw new IllegalStateException("BCrypt not available", ex);
            }
        }

        static String hashpw(String password, String salt) {
            try {
                Class<?> c = Class.forName(CLASS_NAME);
                return (String) c.getMethod("hashpw", String.class, String.class).invoke(null, password, salt);
            } catch (Exception ex) {
                throw new IllegalStateException("BCrypt hashing failed", ex);
            }
        }

        static boolean checkpw(String password, String hashed) {
            try {
                Class<?> c = Class.forName(CLASS_NAME);
                return (Boolean) c.getMethod("checkpw", String.class, String.class).invoke(null, password, hashed);
            } catch (Exception ex) {
                return false;
            }
        }
    }

    // Backward-compat helper (kept private) to avoid unused warnings; can be used in data migrations.
    @SuppressWarnings("unused")
    private static String legacyHashPasswordPbkdf2(char[] password) {
        byte[] salt = new byte[PBKDF2_SALT_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        byte[] derived = pbkdf2(password, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS);
        return "pbkdf2$" + PBKDF2_ALGORITHM + "$" + PBKDF2_ITERATIONS + "$" +
                Base64.getEncoder().encodeToString(salt) + "$" +
                Base64.getEncoder().encodeToString(derived);
    }
}






