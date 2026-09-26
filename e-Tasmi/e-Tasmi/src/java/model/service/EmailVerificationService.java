package model.service;

import model.dao.EmailVerificationDao;
import model.dao.InstructorDao;
import model.dao.UserDao;
import model.dao.impl.EmailVerificationDaoJdbc;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.UserDaoJdbc;
import model.entity.EmailVerification;
import model.entity.Instructor;
import model.entity.InstructorVerificationStatus;
import model.entity.User;
import model.entity.UserRole;
import model.entity.UserStatus;
import util.CodeUtil;
import util.Db;
import util.EmailUtil;
import util.TokenUtil;

import javax.mail.MessagingException;
import javax.servlet.ServletContext;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EmailVerificationService {
    private static final Logger LOGGER = Logger.getLogger(EmailVerificationService.class.getName());

    public static final Duration CODE_TTL = Duration.ofMinutes(10);
    public static final int MAX_ATTEMPTS = 5;

    // Token-based verification (link)
    public static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final UserDao userDao;
    private final EmailVerificationDao emailVerificationDao;
    private final InstructorDao instructorDao;

    public EmailVerificationService() {
        this.userDao = new UserDaoJdbc();
        this.emailVerificationDao = new EmailVerificationDaoJdbc();
        this.instructorDao = new InstructorDaoJdbc();
    }

    public ServiceResult createAndSendLink(ServletContext context, long userId, String email, String baseUrl) {
        if (userId <= 0 || email == null || email.isBlank() || baseUrl == null || baseUrl.isBlank()) {
            return ServiceResult.failure("Invalid request.");
        }

        // Industry approach: generate raw token, store only a hash in DB.
        String rawToken = java.util.UUID.randomUUID().toString();
        String tokenHash = TokenUtil.sha256Hex(rawToken);
        Instant expiresAt = Instant.now().plus(TOKEN_TTL);

        try (Connection connection = Db.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            try (java.sql.PreparedStatement ps = connection.prepareStatement(
                    "UPDATE `user` SET email_verification_token_hash = ?, email_verification_token_expires_at = ?, email_verified = 0 WHERE user_id = ?")) {
                ps.setString(1, tokenHash);
                ps.setTimestamp(2, java.sql.Timestamp.from(expiresAt));
                ps.setLong(3, userId);
                int updated = ps.executeUpdate();
                if (updated != 1) {
                    return ServiceResult.failure("Failed to generate verification token.");
                }
            }
            commitIfNeeded(connection, autoCommit);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to store verification token", ex);
            return ServiceResult.failure("Failed to generate verification token.");
        }

        String verifyUrl = baseUrl + "/auth/verify-email?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        System.out.println("Verification link generated: " + verifyUrl);
        try {
            EmailUtil.sendVerificationEmail(context, email, verifyUrl);
            return ServiceResult.success("Verification link sent.");
        } catch (MessagingException ex) {
            LOGGER.log(Level.SEVERE, "Failed to send verification email", ex);
            return ServiceResult.failure("Could not send verification email. Please try resending the link.");
        } catch (RuntimeException ex) {
            // Common in student setups when SMTP isn't configured yet (EmailUtil throws IllegalStateException).
            LOGGER.log(Level.SEVERE, "Failed to send verification email", ex);
            return ServiceResult.failure("SMTP is not configured. For development, you can verify using this link: " + verifyUrl);
        }
    }

    public ServiceResult verifyToken(String token) {
        if (token == null || token.isBlank()) {
            return ServiceResult.failure("Invalid verification link.");
        }
        String tokenHash = TokenUtil.sha256Hex(token.trim());

        try (Connection connection = Db.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            Optional<User> userOpt = findPendingUserByTokenHash(connection, tokenHash);
            if (userOpt.isPresent()) {
                ServiceResult updateResult = markVerified(connection, userOpt.get());
                if (updateResult.isSuccess()) {
                    emailVerificationDao.deleteByUserId(connection, userOpt.get().getUserId());
                    commitIfNeeded(connection, autoCommit);
                    return updateResult;
                }
            }

            // Give a clearer message for already-verified accounts.
            try (java.sql.PreparedStatement ps = connection.prepareStatement(
                    "SELECT email_verified FROM `user` WHERE email_verification_token_hash = ?")) {
                ps.setString(1, tokenHash);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getBoolean(1)) {
                        return ServiceResult.success("Email is already verified. You can log in.");
                    }
                }
            }

            return ServiceResult.failure("Verification link is invalid or expired. Please request a new one.");
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Verify token failed", ex);
            return ServiceResult.failure("Verification failed due to a server error.");
        }
    }

    public ServiceResult createAndSendCode(ServletContext context, long userId, String email, String fullName, String baseUrl) {
        if (userId <= 0 || email == null || email.isBlank()) {
            return ServiceResult.failure("Invalid request.");
        }

        String code = CodeUtil.generateSixDigitCode();
        String codeHash = TokenUtil.sha256Hex(userId + ":" + code);
        Instant codeExpiresAt = Instant.now().plus(CODE_TTL);
        String rawToken = java.util.UUID.randomUUID().toString();
        String tokenHash = TokenUtil.sha256Hex(rawToken);
        Instant tokenExpiresAt = Instant.now().plus(TOKEN_TTL);
        String verifyUrl = (baseUrl == null || baseUrl.isBlank())
                ? null
                : baseUrl + "/auth/verify-email?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        System.out.println("Verification link generated: " + verifyUrl);

        try (Connection connection = Db.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            boolean ok = emailVerificationDao.upsert(connection, userId, codeHash, codeExpiresAt);
            if (!ok) {
                LOGGER.warning("Email verification code upsert returned false for userId=" + userId);
                return ServiceResult.failure("Failed to generate verification code. Please try again.");
            }
            try (java.sql.PreparedStatement ps = connection.prepareStatement(
                    "UPDATE `user` SET email_verification_token_hash = ?, email_verification_token_expires_at = ?, email_verified = 0 WHERE user_id = ?")) {
                ps.setString(1, tokenHash);
                ps.setTimestamp(2, java.sql.Timestamp.from(tokenExpiresAt));
                ps.setLong(3, userId);
                int updated = ps.executeUpdate();
                if (updated != 1) {
                    return ServiceResult.failure("Failed to generate verification link. Please try again.");
                }
            }
            commitIfNeeded(connection, autoCommit);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to store verification code", ex);
            return ServiceResult.failure(friendlyDbError(ex));
        }

        try {
            EmailUtil.sendVerificationCodeEmail(context, email, code, fullName, verifyUrl);
            return ServiceResult.success("Verification code sent.");
        } catch (MessagingException | RuntimeException ex) {
            // Log full chain of causes on a single line so it is easy to spot in
            // hosted-container log streams (e.g. Railway) where multi-line stack
            // traces can be filtered out by search tools.
            LOGGER.log(Level.SEVERE, "Failed to send verification email to " + email + ": " + describeExceptionChain(ex), ex);
            // User remains inactive; allow resend.
            return ServiceResult.failure(friendlyEmailError(ex));
        }
    }

    private static String describeExceptionChain(Throwable ex) {
        StringBuilder chain = new StringBuilder();
        Throwable cur = ex;
        int depth = 0;
        while (cur != null && depth < 6) {
            if (depth > 0) chain.append(" <- ");
            chain.append(cur.getClass().getName()).append(": ").append(cur.getMessage());
            cur = cur.getCause();
            depth++;
        }
        return chain.toString();
    }

    public ServiceResult verifyCode(String email, String code) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            return ServiceResult.failure("Verification code is required.");
        }
        String normalizedEmail = email.trim().toLowerCase();
        String cleanedCode = code.trim();
        if (!cleanedCode.matches("^[0-9]{6}$")) {
            return ServiceResult.failure("Incorrect verification code.");
        }

        try (Connection connection = Db.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            Optional<User> userOpt = userDao.findByEmail(connection, normalizedEmail);
            if (userOpt.isEmpty()) {
                return ServiceResult.failure("Incorrect verification code.");
            }
            User user = userOpt.get();
            if (user.isEmailVerified()) {
                return ServiceResult.success("Email is already verified.");
            }

            Optional<EmailVerification> evOpt = emailVerificationDao.findActiveByUserId(connection, user.getUserId());
            if (evOpt.isEmpty()) {
                return ServiceResult.failure("Verification code expired. Please resend a new code.");
            }

            EmailVerification ev = evOpt.get();
            if (ev.getAttempts() >= MAX_ATTEMPTS) {
                return ServiceResult.failure("Too many attempts. Please resend a new code.");
            }

            String expectedHash = ev.getCodeHash();
            String actualHash = TokenUtil.sha256Hex(user.getUserId() + ":" + cleanedCode);
            if (expectedHash == null || !expectedHash.equalsIgnoreCase(actualHash)) {
                emailVerificationDao.incrementAttempts(connection, user.getUserId());
                return ServiceResult.failure("Incorrect verification code.");
            }

            ServiceResult updateResult = markVerified(connection, user);
            if (!updateResult.isSuccess()) {
                return updateResult;
            }

            emailVerificationDao.deleteByUserId(connection, user.getUserId());
            commitIfNeeded(connection, autoCommit);
            return updateResult;
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Verify code failed", ex);
            return ServiceResult.failure("Verification failed due to a server error.");
        }
    }

    public Optional<Boolean> isEmailVerified(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        try (Connection connection = Db.getConnection()) {
            Optional<User> userOpt = userDao.findByEmail(connection, email.trim().toLowerCase());
            return userOpt.map(User::isEmailVerified);
        } catch (SQLException ex) {
            return Optional.empty();
        }
    }

    private String friendlyDbError(SQLException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        String lower = message.toLowerCase();

        // Optional developer diagnostics (avoid enabling in production).
        // Env var: DEBUG_ERRORS=true
        String debug = System.getenv("DEBUG_ERRORS");
        boolean debugOn = debug != null && ("1".equals(debug) || "true".equalsIgnoreCase(debug) || "yes".equalsIgnoreCase(debug));

        if (lower.contains("communications link failure") || lower.contains("connection refused") || lower.contains("could not connect")) {
            return "Database connection failed. Make sure the MySQL Docker container is running and try again.";
        }
        if (lower.contains("access denied") || lower.contains("authentication") || lower.contains("password")) {
            return "Database login failed. Check MySQL username/password in Tomcat DataSource config.";
        }
        if (lower.contains("doesn't exist") || lower.contains("unknown table") || (lower.contains("table") && lower.contains("does not exist"))) {
            return "Database schema is missing. Rebuild the containers or import setup/etasmi_schema.sql so the email_verification table exists.";
        }

        if (lower.contains("foreign key constraint fails") || lower.contains("cannot add or update a child row")) {
            return "Database constraint error. Make sure the user exists and email_verification.user_id references user.user_id (same type/signedness) in the same database.";
        }
        if (ex.getSQLState() != null && ex.getSQLState().startsWith("42")) {
            // SQL syntax / table not found etc.
            return "Database schema error. Import setup/etasmi_schema.sql and redeploy.";
        }

        if (debugOn) {
            return "Failed to generate verification code (SQLState=" + ex.getSQLState() + ", errorCode=" + ex.getErrorCode() + "): " + message;
        }

        return "Failed to generate verification code due to a database error. Check Tomcat logs.";
    }

    private void commitIfNeeded(Connection connection, boolean autoCommit) throws SQLException {
        if (!autoCommit) {
            connection.commit();
        }
    }

    private Optional<User> findPendingUserByTokenHash(Connection connection, String tokenHash) throws SQLException {
        try (java.sql.PreparedStatement ps = connection.prepareStatement(
                "SELECT user_id, full_name, email, phone, password_hash, role, status, is_active, email_verified, " +
                        "email_verified_at, email_verification_token_hash, email_verification_token_expires_at, created_at " +
                        "FROM `user` WHERE email_verified = 0 AND email_verification_token_hash = ? " +
                        "AND email_verification_token_expires_at IS NOT NULL AND email_verification_token_expires_at > CURRENT_TIMESTAMP")) {
            ps.setString(1, tokenHash);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                User user = new User();
                user.setUserId(rs.getLong("user_id"));
                user.setFullName(rs.getString("full_name"));
                user.setEmail(rs.getString("email"));
                user.setPhone(rs.getString("phone"));
                user.setPasswordHash(rs.getString("password_hash"));
                user.setRole(UserRole.fromString(rs.getString("role")));
                user.setStatus(UserStatus.fromString(rs.getString("status")));
                user.setActive(rs.getBoolean("is_active"));
                user.setEmailVerified(rs.getBoolean("email_verified"));
                java.sql.Timestamp verifiedAt = rs.getTimestamp("email_verified_at");
                if (verifiedAt != null) {
                    user.setEmailVerifiedAt(verifiedAt.toInstant());
                }
                user.setEmailVerificationTokenHash(rs.getString("email_verification_token_hash"));
                java.sql.Timestamp tokenExpires = rs.getTimestamp("email_verification_token_expires_at");
                if (tokenExpires != null) {
                    user.setEmailVerificationTokenExpiresAt(tokenExpires.toInstant());
                }
                java.sql.Timestamp createdAt = rs.getTimestamp("created_at");
                if (createdAt != null) {
                    user.setCreatedAt(createdAt.toInstant());
                }
                return Optional.of(user);
            }
        }
    }

    private ServiceResult markVerified(Connection connection, User user) throws SQLException {
        UserStatus nextStatus = user.getStatus();
        String successMessage = "Email verified successfully. You can now log in.";

        if (user.getRole() == UserRole.STUDENT) {
            nextStatus = UserStatus.ACTIVE;
        } else if (user.getRole() == UserRole.INSTRUCTOR) {
            Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, user.getUserId());
            InstructorVerificationStatus verificationStatus = instructorOpt
                    .map(Instructor::getVerificationStatus)
                    .orElse(null);

            if (verificationStatus == InstructorVerificationStatus.APPROVED) {
                nextStatus = UserStatus.ACTIVE;
                successMessage = "Email verified successfully. Your instructor account is active.";
            } else if (verificationStatus == InstructorVerificationStatus.REJECTED) {
                nextStatus = UserStatus.INACTIVE;
                successMessage = "Email verified successfully, but your instructor request is rejected. Please contact the administrator.";
            } else {
                nextStatus = UserStatus.INACTIVE;
                successMessage = "Email verified successfully. Your instructor account is still pending admin approval.";
            }
        }

        try (java.sql.PreparedStatement ps = connection.prepareStatement(
                "UPDATE `user` SET email_verified = 1, email_verified_at = CURRENT_TIMESTAMP, status = ?, " +
                        "email_verification_token_hash = NULL, email_verification_token_expires_at = NULL WHERE user_id = ?")) {
            ps.setString(1, nextStatus == null ? null : nextStatus.name());
            ps.setLong(2, user.getUserId());
            int updated = ps.executeUpdate();
            if (updated != 1) {
                return ServiceResult.failure("Verification failed. Please try again.");
            }
        }

        return ServiceResult.success(successMessage);
    }

    private String friendlyEmailError(Exception ex) {
        // Flatten the entire cause chain so we can match the root cause message
        // even when it is nested inside a MessagingException wrapper. Without
        // this, hosted deployments (e.g. Railway) would often fall through to
        // the generic fallback below because the top-level message only says
        // "Couldn't connect to host" or similar, while the actionable detail
        // ("Connection refused", "535-5.7.8", "PKIX path building failed"...)
        // lives on an inner cause.
        String combined = describeExceptionChain(ex);
        String lower = combined.toLowerCase();

        if (lower.contains("smtp is not configured")) {
            return "Email sending is not configured yet. Set SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS, SMTP_FROM, and APP_BASE_URL in your deployment (Railway Variables, or local env), then use the resend button.";
        }
        if (lower.contains("smtp or app_base_url environment variables are missing")) {
            return "Email sending needs APP_BASE_URL and full SMTP settings. In Railway, set APP_BASE_URL (e.g. https://your-app.up.railway.app), SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASS, SMTP_FROM, then redeploy.";
        }
        if (lower.contains("app_base_url is not set")) {
            return "APP_BASE_URL is not set. In Railway variables, set APP_BASE_URL to your public URL, e.g. https://e-tasmi-production.up.railway.app, then redeploy.";
        }
        if (lower.contains("gmail smtp requires")) {
            return "Gmail SMTP needs SMTP_USER and SMTP_PASS (or SMTP_USERNAME and SMTP_PASSWORD) with a real Gmail account and a Google App Password. On Railway, add them in the Variables tab and redeploy.";
        }
        if (lower.contains("smtp_from must be")) {
            return "SMTP_FROM must be a real email address (for Gmail, use the same address as SMTP_USERNAME).";
        }

        // Brevo (HTTPS) specific errors.
        if (lower.contains("brevo api rejected") || lower.contains("brevo https call failed")) {
            if (lower.contains("status=401")) {
                return "Brevo rejected the request (invalid BREVO_API_KEY). Regenerate a key at https://app.brevo.com/settings/keys/api and update the Railway variable.";
            }
            if (lower.contains("status=400") && (lower.contains("sender") || lower.contains("from") || lower.contains("not valid"))) {
                return "Brevo rejected the sender address. SMTP_FROM must be an email you have verified in Brevo (Senders & IP > Senders > Add a Sender), then click the verification link Brevo emails you.";
            }
            return "Brevo could not send the email. Details: " + combined;
        }
        if (lower.contains("brevo_api_key is empty")) {
            return "Email sending is not configured. Set BREVO_API_KEY and SMTP_FROM in Railway Variables, then redeploy.";
        }

        // Resend (HTTPS) specific errors — surfaced verbatim so the admin can
        // act on them (usually "sender domain not verified" or bad API key).
        if (lower.contains("resend api rejected") || lower.contains("resend https call failed")) {
            if (lower.contains("status=401") || lower.contains("status=403")) {
                return "Resend rejected the API key. If you use Brevo instead: in Railway delete RESEND_API_KEY, add BREVO_API_KEY (same service as the app), set SMTP_FROM to your verified Brevo sender email, then Redeploy. Otherwise regenerate the key at https://resend.com.";
            }
            if (lower.contains("status=422") && (lower.contains("from") || lower.contains("domain") || lower.contains("verified"))) {
                return "Resend rejected the sender address. Either use SMTP_FROM=onboarding@resend.dev for testing, or verify your own domain in Resend and use an address from that domain.";
            }
            return "Resend could not send the email. Details: " + combined;
        }
        if (lower.contains("resend_api_key is empty")) {
            return "Email sending is not configured. Set RESEND_API_KEY and SMTP_FROM in Railway Variables, then redeploy.";
        }

        // Authentication failures (Gmail 535, AuthenticationFailedException, etc.).
        // These commonly happen on first connection from a new cloud IP (Railway)
        // when the App Password is wrong/revoked, 2FA was disabled, or Google
        // blocked the sign-in attempt as suspicious.
        if (lower.contains("authenticationfailedexception")
                || lower.contains("authentication failed")
                || lower.contains("535")
                || lower.contains("username and password not accepted")
                || lower.contains("badcredentials")
                || lower.contains("invalid credentials")
                || lower.contains("application-specific password required")) {
            return "Gmail rejected the login (authentication failed). Regenerate a Google App Password for this account (2-Step Verification must be ON), then update SMTP_PASS (or SMTP_PASSWORD) in Railway Variables and redeploy. If the account has never sent from this IP before, also visit https://accounts.google.com/DisplayUnlockCaptcha while signed in and retry.";
        }

        // Outbound port blocked or host unreachable. "Connection refused" is the
        // typical signature when the hosting provider blocks outbound SMTP.
        if (lower.contains("connection refused")
                || lower.contains("no route to host")
                || lower.contains("network is unreachable")) {
            return "The app could not open a connection to the SMTP server (connection refused). Your hosting provider may be blocking outbound SMTP on port " + "587" + ". On Railway, verify outbound SMTP is allowed for your plan, or switch to an HTTP email API (e.g. Resend, SendGrid, Mailgun).";
        }
        if (lower.contains("unknownhostexception") || lower.contains("unknown host")) {
            return "The SMTP host name could not be resolved. Check the SMTP_HOST value in Railway Variables (should be 'smtp.gmail.com' for Gmail).";
        }
        if (lower.contains("could not connect") || lower.contains("connection timed out") || lower.contains("read timed out") || lower.contains("connect timed out")) {
            return "The app could not reach the email server within the timeout. Your hosting provider may be blocking outbound SMTP, or the SMTP host/port is wrong. Check SMTP_HOST and SMTP_PORT in Railway Variables.";
        }

        // TLS / certificate issues.
        if (lower.contains("sslhandshakeexception")
                || lower.contains("pkix path building failed")
                || lower.contains("certificate")
                || lower.contains("handshake_failure")) {
            return "SSL/TLS handshake with the SMTP server failed. Verify SMTP_STARTTLS is 'true' for Gmail on port 587, or try port 465 with implicit SSL.";
        }

        return "Could not send verification email. Please try resending the code.";
    }
}
