package model.service;

import model.dao.PasswordResetDao;
import model.dao.UserDao;
import model.dao.impl.PasswordResetDaoJdbc;
import model.dao.impl.UserDaoJdbc;
import model.entity.PasswordReset;
import model.entity.User;
import util.Db;
import util.EmailUtil;
import util.PasswordUtil;
import util.TokenUtil;

import javax.mail.MessagingException;
import javax.servlet.ServletContext;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PasswordResetService {
    private static final Logger LOGGER = Logger.getLogger(PasswordResetService.class.getName());

    public static final Duration RESET_TTL = Duration.ofMinutes(30);

    private final UserDao userDao;
    private final PasswordResetDao passwordResetDao;

    public PasswordResetService() {
        this.userDao = new UserDaoJdbc();
        this.passwordResetDao = new PasswordResetDaoJdbc();
    }

    /**
     * Always returns success to avoid account enumeration.
     */
    private static final String GENERIC_SUCCESS =
            "If the email exists in our system, a reset link has been sent.";

    public ServiceResult requestReset(ServletContext context, String email, String baseUrl) {
        String configProblem = EmailUtil.getConfigurationProblem(context);
        if (configProblem != null) {
            LOGGER.severe("Password reset email unavailable: " + configProblem);
            return ServiceResult.failure("Password reset email is temporarily unavailable. Please try again later.");
        }
        if (email == null || email.isBlank()) {
            return ServiceResult.success(GENERIC_SUCCESS);
        }

        String normalizedEmail = email.trim().toLowerCase();

        try (Connection connection = Db.getConnection()) {
            Optional<User> userOpt = userDao.findByEmail(connection, normalizedEmail);
            if (userOpt.isEmpty()) {
                return ServiceResult.success(GENERIC_SUCCESS);
            }

            User user = userOpt.get();
            String token = TokenUtil.generateToken();
            String tokenHash = TokenUtil.sha256Hex(token);
            Instant expiresAt = Instant.now().plus(RESET_TTL);

            // Invalidate any previous tokens for this user.
            passwordResetDao.invalidateByUserId(connection, user.getUserId());
            passwordResetDao.insert(connection, user.getUserId(), tokenHash, expiresAt);

            String link = baseUrl + "/auth/reset-password?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            try {
                EmailUtil.sendPasswordResetEmail(context, normalizedEmail, link, user.getFullName());
            } catch (MessagingException ex) {
                LOGGER.log(Level.SEVERE, "Failed to send reset email", ex);
                passwordResetDao.invalidateByUserId(connection, user.getUserId());
                return ServiceResult.failure("Password reset email could not be sent. Please try again later.");
            } catch (RuntimeException ex) {
                LOGGER.log(Level.SEVERE, "Failed to send reset email", ex);
                passwordResetDao.invalidateByUserId(connection, user.getUserId());
                return ServiceResult.failure("Password reset email is temporarily unavailable. Please try again later.");
            }
            return ServiceResult.success(GENERIC_SUCCESS);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Request reset failed", ex);
            return ServiceResult.failure("Password reset is temporarily unavailable. Please try again later.");
        }
    }

    public ServiceResult validateResetToken(String token) {
        if (token == null || token.isBlank()) {
            return ServiceResult.failure("Invalid or expired reset link.");
        }

        String tokenHash = TokenUtil.sha256Hex(token.trim());
        try (Connection connection = Db.getConnection()) {
            if (passwordResetDao.existsActiveByTokenHash(connection, tokenHash)) {
                return ServiceResult.success("Reset link is valid.");
            }
            return ServiceResult.failure("Invalid or expired reset link.");
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Reset token validation failed", ex);
            return ServiceResult.failure("Password reset is temporarily unavailable. Please try again later.");
        }
    }

    public ServiceResult resetPassword(String token, char[] newPassword) {
        if (token == null || token.isBlank()) {
            return ServiceResult.failure("Invalid or expired reset link.");
        }
        if (newPassword == null || newPassword.length < 8) {
            return ServiceResult.failure("Weak password. Use 8+ chars with upper, lower, and a number.");
        }
        boolean hasUpper = false, hasLower = false, hasDigit = false;
        for (char c : newPassword) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
        }
        if (!(hasUpper && hasLower && hasDigit)) {
            return ServiceResult.failure("Weak password. Use 8+ chars with upper, lower, and a number.");
        }

        String tokenHash = TokenUtil.sha256Hex(token.trim());

        try (Connection connection = Db.getConnection()) {
            Optional<PasswordReset> prOpt = passwordResetDao.findActiveByTokenHash(connection, tokenHash);
            if (prOpt.isEmpty()) {
                return ServiceResult.failure("Invalid or expired reset link.");
            }
            PasswordReset pr = prOpt.get();

            String hash = PasswordUtil.hashPassword(newPassword);
            boolean updated = userDao.updatePasswordHash(connection, pr.getUserId(), hash);
            if (!updated) {
                return ServiceResult.failure("Password reset failed. Please try again.");
            }

            passwordResetDao.markUsed(connection, pr.getResetId());
            return ServiceResult.success("Password updated successfully. You can now log in.");
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Reset password failed", ex);
            return ServiceResult.failure("Password reset failed due to a server error.");
        }
    }
}
