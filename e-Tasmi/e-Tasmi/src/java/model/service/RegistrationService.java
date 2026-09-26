package model.service;

import model.dao.InstructorDao;
import model.dao.StudentDao;
import model.dao.UserDao;
import model.dao.NotificationDao;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.NotificationDaoJdbc;
import model.dao.impl.StudentDaoJdbc;
import model.dao.impl.UserDaoJdbc;
import model.entity.*;
import util.Db;
import util.PasswordUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RegistrationService {
    private static final Logger LOGGER = Logger.getLogger(RegistrationService.class.getName());

    private final UserDao userDao;
    private final StudentDao studentDao;
    private final InstructorDao instructorDao;
    private final NotificationDao notificationDao;

    public RegistrationService() {
        this.userDao = new UserDaoJdbc();
        this.studentDao = new StudentDaoJdbc();
        this.instructorDao = new InstructorDaoJdbc();
        this.notificationDao = new NotificationDaoJdbc();
    }

    public RegistrationResult register(RegistrationRequest request) {
        if (request == null) {
            return RegistrationResult.failure("Invalid request.");
        }
        if (request.getFullName() == null || request.getFullName().isBlank()) {
            return RegistrationResult.failure("Full name is required.");
        }
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            return RegistrationResult.failure("Email is required.");
        }
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            return RegistrationResult.failure("Phone is required.");
        }
        if (!isValidPhone(request.getPhone())) {
            return RegistrationResult.failure("Invalid phone number format.");
        }
        if (!isStrongPassword(request.getPassword())) {
            return RegistrationResult.failure("Weak password. Use 8+ chars with upper, lower, and a number.");
        }
        if (request.getRole() == null) {
            return RegistrationResult.failure("Role is required.");
        }
        if (request.getRole() != UserRole.STUDENT && request.getRole() != UserRole.INSTRUCTOR) {
            return RegistrationResult.failure("Please choose Student or Instructor registration.");
        }
        if (request.getRole() == UserRole.STUDENT && request.getStudentLevel() == null) {
            return RegistrationResult.failure("Level is required for students.");
        }
        if (request.getRole() == UserRole.INSTRUCTOR) {
            if (request.getQualificationFilePath() == null || request.getQualificationFilePath().isBlank()) {
                return RegistrationResult.failure("Qualification file is required for instructors.");
            }
            if (request.getInstructorBio() == null || request.getInstructorBio().isBlank()) {
                return RegistrationResult.failure("Bio is required for instructors.");
            }
        }

        String normalizedEmail = request.getEmail().trim().toLowerCase();

        try (Connection connection = Db.getConnection()) {
            connection.setAutoCommit(false);

            Optional<User> existing = userDao.findByEmail(connection, normalizedEmail);
            if (existing.isPresent()) {
                connection.rollback();
                User existingUser = existing.get();
                if (!existingUser.isEmailVerified()) {
                    return RegistrationResult.existingUnverified(existingUser.getUserId(), existingUser.getEmail());
                }
                return RegistrationResult.failure("Email is already registered.");
            }

            User user = new User();

            user.setFullName(request.getFullName().trim());
            user.setEmail(normalizedEmail);
            user.setPhone(request.getPhone().trim());
            user.setPasswordHash(PasswordUtil.hashPassword(request.getPassword()));
            user.setActive(true);
            user.setRole(request.getRole());
            user.setStatus(UserStatus.INACTIVE);
            user.setEmailVerified(false);
            user.setEmailVerifiedAt(null);
            user.setCreatedAt(Instant.now());

            long userId = userDao.insert(connection, user);

            switch (request.getRole()) {
                case STUDENT: {
                    Student s = new Student();
                    s.setUserId(userId);
                    s.setRegistrationNumber("STD-" + userId);
                    s.setLevel(request.getStudentLevel());
                    studentDao.insert(connection, s);

                    Notification n = new Notification();
                    n.setUserId(userId);
                    n.setMessage("Registration successful. Please verify your email to activate your student account.");
                    n.setCreatedAt(Instant.now());
                    notificationDao.insert(connection, n);
                    break;
                }
                case INSTRUCTOR: {
                    Instructor i = new Instructor();
                    i.setUserId(userId);
                    i.setQualificationFile(request.getQualificationFilePath());
                    i.setBio(request.getInstructorBio().trim());
                    i.setVerificationStatus(InstructorVerificationStatus.PENDING);
                    instructorDao.insert(connection, i);

                    Notification n = new Notification();
                    n.setUserId(userId);
                    n.setMessage("Registration received. Please verify your email first. After that, your instructor account will remain pending admin verification.");
                    n.setCreatedAt(Instant.now());
                    notificationDao.insert(connection, n);
                    break;
                }
                default:
                    connection.rollback();
                    return RegistrationResult.failure("Unsupported role.");
            }

            connection.commit();
            return RegistrationResult.success(userId, user.getEmail());
        } catch (SQLException ex) {
            if (isMysqlDuplicateKey(ex)) {
                return RegistrationResult.failure("This email is already registered.");
            }
            if (isLikelyMissingDbColumnOrTable(ex)) {
                LOGGER.log(Level.SEVERE, "Registration failed (check DB schema / restart app for DBSeeder migrations): " + ex.getMessage(), ex);
                return RegistrationResult.failure("Database is missing required columns. Restart the app once so migrations run, or import e-Tasmi/web/WEB-INF/db/railway_schema.sql.");
            }
            LOGGER.log(Level.SEVERE, "Registration failed: " + ex.getMessage(), ex);
            return RegistrationResult.failure("Registration failed due to a server error.");
        }
    }

    /** MySQL duplicate key: SQLState 23xxx, error 1062, or message text. */
    private static boolean isMysqlDuplicateKey(SQLException ex) {
        SQLException cur = ex;
        int depth = 0;
        while (cur != null && depth < 8) {
            if (cur.getErrorCode() == 1062) {
                return true;
            }
            if (cur.getSQLState() != null && cur.getSQLState().startsWith("23")) {
                return true;
            }
            String m = cur.getMessage();
            if (m != null && m.contains("Duplicate entry")) {
                return true;
            }
            cur = cur.getNextException();
            depth++;
        }
        return false;
    }

    private static boolean isLikelyMissingDbColumnOrTable(SQLException ex) {
        String m = ex.getMessage();
        if (m == null) {
            return false;
        }
        String lower = m.toLowerCase();
        if (lower.contains("unknown column")) {
            return true;
        }
        if (lower.contains("doesn't exist") && (lower.contains("table") || lower.contains("column"))) {
            return true;
        }
        return "42S02".equals(ex.getSQLState()) || "42S22".equals(ex.getSQLState());
    }

    private boolean isValidPhone(String phone) {
        String p = phone.trim();
        // Accept digits and common separators; enforce 8-15 digits total.
        String digits = p.replaceAll("[^0-9]", "");
        return digits.length() >= 8 && digits.length() <= 15;
    }

    private boolean isStrongPassword(char[] password) {
        if (password == null || password.length < 8) {
            return false;
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        for (char c : password) {
            if (Character.isUpperCase(c)) hasUpper = true;
            else if (Character.isLowerCase(c)) hasLower = true;
            else if (Character.isDigit(c)) hasDigit = true;
        }
        return hasUpper && hasLower && hasDigit;
    }
}
