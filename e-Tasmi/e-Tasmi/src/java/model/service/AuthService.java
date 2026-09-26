package model.service;

import model.dao.InstructorDao;
import model.dao.UserDao;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.UserDaoJdbc;
import model.entity.Instructor;
import model.entity.User;
import model.entity.UserRole;
import util.Db;
import util.PasswordUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AuthService {
    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());

    /**
     * When true (env {@code ETASMI_ALLOW_LOGIN_WITHOUT_EMAIL_VERIFICATION} or
     * system property {@code etasmi.allowLoginWithoutEmailVerification}),
     * unverified users can sign in. Use only for hosting smoke-tests; turn off
     * when email verification is ready.
     */
    private static boolean isLoginWithoutEmailVerificationEnabled() {
        String p = firstNonBlank(System.getenv("ETASMI_ALLOW_LOGIN_WITHOUT_EMAIL_VERIFICATION"),
                System.getProperty("etasmi.allowLoginWithoutEmailVerification"));
        return p != null && ("1".equals(p) || "true".equalsIgnoreCase(p) || "yes".equalsIgnoreCase(p));
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a.trim();
        }
        if (b != null && !b.isBlank()) {
            return b.trim();
        }
        return null;
    }

    private final UserDao userDao;
    private final InstructorDao instructorDao;

    public AuthService() {
        this.userDao = new UserDaoJdbc();
        this.instructorDao = new InstructorDaoJdbc();
    }

    public AuthResult login(String email, char[] password) {
        if (email == null || email.isBlank() || password == null || password.length == 0) {
            return AuthResult.failure("Email and password are required.");
        }

        try (Connection connection = Db.getConnection()) {
            Optional<User> userOpt = userDao.findByEmail(connection, email.trim().toLowerCase());
            if (userOpt.isEmpty()) {
                return AuthResult.failure("Invalid email or password.");
            }

            User user = userOpt.get();

            if (!PasswordUtil.verifyPassword(password, user.getPasswordHash())) {
                return AuthResult.failure("Invalid email or password.");
            }

            if (!user.isActive()) {
                return AuthResult.failure("Your account has been deactivated. Please contact the administrator.");
            }

            // Admins are not self-registered; they must be able to sign in for operations even if
            // email_verified drifted to 0 after a DB restore. Students/instructors must verify (unless env bypass).
            boolean emailBypass = isLoginWithoutEmailVerificationEnabled();
            boolean adminSkipsEmailGate = user.getRole() == UserRole.ADMIN;
            if (!user.isEmailVerified() && !emailBypass && !adminSkipsEmailGate) {
                return AuthResult.failure("Please verify your email before logging in.");
            }
            if (!user.isEmailVerified() && emailBypass) {
                LOGGER.info("Login: allowing unverified user (ETASMI_ALLOW_LOGIN_WITHOUT_EMAIL_VERIFICATION is enabled). userId=" + user.getUserId());
            }

            String instructorStatus = null;
            if (user.getRole() != null && user.getRole().name().equals("INSTRUCTOR")) {
                Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, user.getUserId());
                if (instructorOpt.isPresent() && instructorOpt.get().getVerificationStatus() != null) {
                    instructorStatus = instructorOpt.get().getVerificationStatus().name();
                }
            }

            reconcileLegacyAuthState(connection, user, instructorStatus);

            if (user.getStatus() == null || !user.getStatus().name().equals("ACTIVE")) {
                if (user.getRole() != null && user.getRole().name().equals("INSTRUCTOR")) {
                    if ("PENDING".equalsIgnoreCase(instructorStatus) || "REJECTED".equalsIgnoreCase(instructorStatus)) {
                        return AuthResult.success(user, instructorStatus);
                    }
                    return AuthResult.failure("Your instructor account is inactive. Please contact the administrator.");
                }
                return AuthResult.failure("Your account is inactive. Please contact the administrator.");
            }

            return AuthResult.success(user, instructorStatus);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Login failed", ex);
            return AuthResult.failure(friendlyLoginDbError(ex));
        }
    }

    private void reconcileLegacyAuthState(Connection connection, User user, String instructorStatus) throws SQLException {
        if (user == null || user.getRole() == null) {
            return;
        }

        boolean changed = false;
        boolean allowUnverified = isLoginWithoutEmailVerificationEnabled();
        if (user.getRole().name().equals("STUDENT")) {
            if (allowUnverified && !user.isEmailVerified() && user.isActive()
                    && user.getStatus() == model.entity.UserStatus.INACTIVE) {
                user.setStatus(model.entity.UserStatus.ACTIVE);
                changed = true;
            } else if (user.isEmailVerified() && user.isActive() && user.getStatus() != model.entity.UserStatus.ACTIVE) {
                user.setStatus(model.entity.UserStatus.ACTIVE);
                changed = true;
            } else if (!user.isEmailVerified() && user.getStatus() == model.entity.UserStatus.ACTIVE) {
                if (!allowUnverified) {
                    user.setStatus(model.entity.UserStatus.INACTIVE);
                    changed = true;
                }
            }
        } else if (user.getRole().name().equals("INSTRUCTOR")) {
            if ("APPROVED".equalsIgnoreCase(instructorStatus) && user.isEmailVerified() && user.isActive()
                    && user.getStatus() != model.entity.UserStatus.ACTIVE) {
                user.setStatus(model.entity.UserStatus.ACTIVE);
                changed = true;
            } else if (!user.isEmailVerified() && user.getStatus() == model.entity.UserStatus.ACTIVE) {
                if (!allowUnverified) {
                    user.setStatus(model.entity.UserStatus.INACTIVE);
                    changed = true;
                }
            }
        } else if ("ADMIN".equals(user.getRole().name())) {
            if (user.isActive() && user.getStatus() != model.entity.UserStatus.ACTIVE) {
                user.setStatus(model.entity.UserStatus.ACTIVE);
                changed = true;
            }
        }

        if (changed) {
            userDao.update(connection, user);
        }
    }

    private String friendlyLoginDbError(SQLException ex) {
        String message = ex.getMessage() == null ? "" : ex.getMessage();
        String lower = message.toLowerCase();

        // Helpful, safe messages for common local setup problems.
        if (message.contains("No DataSource found") || message.contains("JNDI DataSource lookup failed")) {
            return "Database is not configured. Configure Tomcat DataSource 'jdbc/ETasmiDS' or set ETASMI_JDBC_URL.";
        }
        if (lower.contains("communications link failure") || lower.contains("connection refused") || lower.contains("could not connect")) {
            return "Cannot connect to the database. Make sure MySQL is running and credentials are correct.";
        }
        if (lower.contains("access denied") || lower.contains("authentication") || lower.contains("password")) {
            return "Database login failed. Check the MySQL username/password in your DataSource config.";
        }
        if (lower.contains("doesn't exist") || lower.contains("unknown column") || lower.contains("unknown table") || lower.contains("table") && lower.contains("does not exist")) {
            return "Database schema mismatch. Import setup/etasmi_schema.sql into your MySQL database.";
        }
        if (lower.contains("no suitable driver") || lower.contains("mysql jdbc driver not found")) {
            return "Database driver is not loaded. Redeploy the app; if this persists, ensure mysql-connector-j is in WEB-INF/lib.";
        }

        return "Login failed due to a server error. Check server logs for details.";
    }
}
