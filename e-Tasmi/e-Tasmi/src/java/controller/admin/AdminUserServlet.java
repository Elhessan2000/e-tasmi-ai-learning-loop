package controller.admin;

import model.dao.AdminDao;
import model.dao.InstructorDao;
import model.dao.StudentDao;
import model.dao.UserDao;
import model.dao.impl.AdminDaoJdbc;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.StudentDaoJdbc;
import model.dao.impl.UserDaoJdbc;
import model.entity.Admin;
import model.entity.Instructor;
import model.entity.InstructorVerificationStatus;
import model.entity.Student;
import model.entity.User;
import model.entity.UserRole;
import model.entity.UserStatus;
import model.service.AuditLogService;
import util.PasswordUtil;
import util.Db;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@WebServlet(name = "AdminUserServlet", urlPatterns = {"/admin/users", "/admin/users/create", "/admin/users/edit", "/admin/users/update", "/admin/users/toggle", "/admin/users/delete"})
public class AdminUserServlet extends HttpServlet {
    private static final Pattern SIMPLE_EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private final AuditLogService auditLogService = new AuditLogService();
    private final UserDao userDao = new UserDaoJdbc();
    private final StudentDao studentDao = new StudentDaoJdbc();
    private final InstructorDao instructorDao = new InstructorDaoJdbc();
    private final AdminDao adminDao = new AdminDaoJdbc();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getServletPath();
        request.setAttribute("activeMenu", "users");
        if (request.getParameter("success") != null) {
            request.setAttribute("success", request.getParameter("success").replace('+', ' '));
        }
        if (request.getParameter("error") != null) {
            request.setAttribute("error", request.getParameter("error").replace('+', ' '));
        }
        try (Connection conn = Db.getConnection()) {
            if ("/admin/users".equals(path)) {
                String search = request.getParameter("search");
                String role = request.getParameter("role");
                List<User> users = userDao.listUsers(conn, search, role);
                request.setAttribute("users", users);
                request.setAttribute("search", search);
                request.setAttribute("role", role);
                request.getRequestDispatcher("/jsp/admin/users/list.jsp").forward(request, response);
            } else if ("/admin/users/create".equals(path)) {
                request.getRequestDispatcher("/jsp/admin/users/create.jsp").forward(request, response);
            } else if ("/admin/users/edit".equals(path)) {
                long userId = parseLong(request.getParameter("id"));
                if (userId <= 0) {
                    response.sendRedirect(request.getContextPath() + "/admin/users?error=User+not+found");
                    return;
                }
                Optional<User> userOpt = userDao.findById(conn, userId);
                if (userOpt.isPresent()) {
                    request.setAttribute("user", userOpt.get());
                    request.getRequestDispatcher("/jsp/admin/users/edit.jsp").forward(request, response);
                } else {
                    response.sendRedirect(request.getContextPath() + "/admin/users?error=User+not+found");
                }
            }
        } catch (Exception ex) {
            request.setAttribute("error", "Failed to load user management.");
            request.getRequestDispatcher("/jsp/admin/users/list.jsp").forward(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String path = request.getServletPath();
        Long actorUserId = readUserId(request);
        Connection conn = null;
        try {
            conn = Db.getConnection();
            conn.setAutoCommit(false);
            if ("/admin/users/create".equals(path)) {
                String fullName = safeTrim(request.getParameter("fullName"));
                String email = normalizeEmail(request.getParameter("email"));
                String phone = safeTrim(request.getParameter("phone"));
                String password = request.getParameter("password");
                UserRole role = parseRole(request.getParameter("role"));
                String validationError = validateCreateInput(fullName, email, phone, password, role);
                if (validationError != null) {
                    conn.rollback();
                    response.sendRedirect(request.getContextPath() + "/admin/users/create?error=" + encodeMessage(validationError));
                    return;
                }
                if (userDao.existsByEmailExcludingUserId(conn, email, null)) {
                    conn.rollback();
                    response.sendRedirect(request.getContextPath() + "/admin/users/create?error=Email+address+is+already+in+use");
                    return;
                }
                User user = new User();
                user.setFullName(fullName);
                user.setEmail(email);
                user.setPhone(phone);
                user.setPasswordHash(PasswordUtil.hashPassword(password.toCharArray()));
                user.setActive(true);
                user.setEmailVerified(true);
                user.setEmailVerifiedAt(java.time.Instant.now());
                user.setRole(role);
                user.setStatus(UserStatus.ACTIVE);
                user.setCreatedAt(java.time.Instant.now());
                long createdUserId = userDao.insert(conn, user);
                createRoleProfile(conn, createdUserId, role);
                auditLogService.log(conn, actorUserId, "ADMIN", "USER_CREATED", "USER", String.valueOf(createdUserId),
                        "Created user " + email + " with role " + user.getRole());
                conn.commit();
                response.sendRedirect(request.getContextPath() + "/admin/users?success=User+created");
            } else if ("/admin/users/update".equals(path)) {
                long userId = parseLong(request.getParameter("id"));
                String fullName = safeTrim(request.getParameter("fullName"));
                String email = normalizeEmail(request.getParameter("email"));
                String phone = safeTrim(request.getParameter("phone"));
                UserRole requestedRole = parseRole(request.getParameter("role"));
                if (userId <= 0) {
                    conn.rollback();
                    response.sendRedirect(request.getContextPath() + "/admin/users?error=User+not+found");
                    return;
                }
                Optional<User> existingOpt = userDao.findById(conn, userId);
                if (existingOpt.isEmpty()) {
                    conn.rollback();
                    response.sendRedirect(request.getContextPath() + "/admin/users?error=User+not+found");
                    return;
                }
                User user = existingOpt.get();
                String validationError = validateUpdateInput(fullName, email, phone, requestedRole);
                if (validationError != null) {
                    conn.rollback();
                    response.sendRedirect(request.getContextPath() + "/admin/users/edit?id=" + userId + "&error=" + encodeMessage(validationError));
                    return;
                }
                if (user.getRole() != requestedRole) {
                    conn.rollback();
                    response.sendRedirect(request.getContextPath() + "/admin/users/edit?id=" + userId + "&error=Changing+role+for+existing+users+is+not+supported+yet");
                    return;
                }
                if (userDao.existsByEmailExcludingUserId(conn, email, userId)) {
                    conn.rollback();
                    response.sendRedirect(request.getContextPath() + "/admin/users/edit?id=" + userId + "&error=Email+address+is+already+in+use");
                    return;
                }
                user.setFullName(fullName);
                user.setEmail(email);
                user.setPhone(phone);
                user.setRole(requestedRole);
                userDao.update(conn, user);
                auditLogService.log(conn, actorUserId, "ADMIN", "USER_UPDATED", "USER", String.valueOf(userId),
                        "Updated user " + email + " with role " + user.getRole());
                conn.commit();
                response.sendRedirect(request.getContextPath() + "/admin/users?success=User+updated");
            } else if ("/admin/users/toggle".equals(path)) {
                long userId = parseLong(request.getParameter("id"));
                boolean active = Boolean.parseBoolean(request.getParameter("active"));
                Optional<User> userOpt = userDao.findById(conn, userId);
                if (userOpt.isEmpty()) {
                    conn.rollback();
                    response.sendRedirect(request.getContextPath() + "/admin/users?error=User+not+found");
                    return;
                }
                User target = userOpt.get();
                if (actorUserId != null && actorUserId == userId && !active) {
                    conn.rollback();
                    response.sendRedirect(request.getContextPath() + "/admin/users?error=You+cannot+deactivate+your+own+account");
                    return;
                }
                if (target.getRole() == UserRole.ADMIN && target.isActive() && !active && userDao.countActiveUsersByRole(conn, "ADMIN") <= 1) {
                    conn.rollback();
                    response.sendRedirect(request.getContextPath() + "/admin/users?error=You+cannot+deactivate+the+last+active+admin");
                    return;
                }
                boolean toggled = userDao.toggleActive(conn, userId, active);
                if (!toggled) {
                    conn.rollback();
                    response.sendRedirect(request.getContextPath() + "/admin/users?error=Unable+to+update+user+status");
                    return;
                }
                auditLogService.log(conn, actorUserId, "ADMIN",
                        active ? "USER_ACTIVATED" : "USER_DEACTIVATED",
                        "USER", String.valueOf(userId),
                        "Changed active state to " + active);
                conn.commit();
                response.sendRedirect(request.getContextPath() + "/admin/users?success=User+status+changed");
            } else if ("/admin/users/delete".equals(path)) {
                long userId = parseLong(request.getParameter("id"));
                Optional<User> userOpt = userDao.findById(conn, userId);
                if (userOpt.isEmpty()) {
                    conn.rollback();
                    response.sendRedirect(request.getContextPath() + "/admin/users?error=User+not+found");
                    return;
                }
                User target = userOpt.get();
                if (actorUserId != null && actorUserId == userId) {
                    conn.rollback();
                    response.sendRedirect(request.getContextPath() + "/admin/users?error=You+cannot+delete+your+own+account");
                    return;
                }
                if (target.getRole() == UserRole.ADMIN && target.isActive() && userDao.countActiveUsersByRole(conn, "ADMIN") <= 1) {
                    conn.rollback();
                    response.sendRedirect(request.getContextPath() + "/admin/users?error=You+cannot+delete+the+last+active+admin");
                    return;
                }
                boolean deleted = userDao.softDelete(conn, userId);
                if (!deleted) {
                    conn.rollback();
                    response.sendRedirect(request.getContextPath() + "/admin/users?error=Unable+to+delete+user");
                    return;
                }
                auditLogService.log(conn, actorUserId, "ADMIN", "USER_DELETED", "USER", String.valueOf(userId),
                        "Deleted user access for " + target.getEmail());
                conn.commit();
                response.sendRedirect(request.getContextPath() + "/admin/users?success=User+deleted");
            }
        } catch (Exception ex) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (Exception ignored) {
                }
            }
            response.sendRedirect(request.getContextPath() + "/admin/users?error=Operation+failed");
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Long readUserId(HttpServletRequest request) {
        Object userIdObj = request.getSession(false) == null ? null : request.getSession(false).getAttribute("userId");
        if (userIdObj instanceof Number) {
            return ((Number) userIdObj).longValue();
        }
        if (userIdObj != null) {
            try {
                return Long.parseLong(String.valueOf(userIdObj));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

    private void createRoleProfile(Connection conn, long userId, UserRole role) throws Exception {
        if (role == UserRole.STUDENT) {
            Student student = new Student();
            student.setUserId(userId);
            student.setRegistrationNumber("STD-" + userId);
            studentDao.insert(conn, student);
            return;
        }
        if (role == UserRole.INSTRUCTOR) {
            Instructor instructor = new Instructor();
            instructor.setUserId(userId);
            instructor.setVerificationStatus(InstructorVerificationStatus.APPROVED);
            instructorDao.insert(conn, instructor);
            return;
        }
        if (role == UserRole.ADMIN) {
            Admin admin = new Admin();
            admin.setUserId(userId);
            adminDao.insert(conn, admin);
        }
    }

    private UserRole parseRole(String rawRole) {
        try {
            return UserRole.fromString(rawRole);
        } catch (Exception ex) {
            return null;
        }
    }

    private String validateCreateInput(String fullName, String email, String phone, String password, UserRole role) {
        String common = validateCommonInput(fullName, email, phone, role);
        if (common != null) {
            return common;
        }
        if (password == null || password.length() < 8) {
            return "Password must be at least 8 characters long";
        }
        return null;
    }

    private String validateUpdateInput(String fullName, String email, String phone, UserRole role) {
        return validateCommonInput(fullName, email, phone, role);
    }

    private String validateCommonInput(String fullName, String email, String phone, UserRole role) {
        if (fullName == null || fullName.length() < 3 || fullName.length() > 150) {
            return "Full name must be between 3 and 150 characters";
        }
        if (email == null || email.length() > 191 || !SIMPLE_EMAIL_PATTERN.matcher(email).matches()) {
            return "Enter a valid email address";
        }
        if (phone == null || phone.length() < 7 || phone.length() > 30) {
            return "Phone number must be between 7 and 30 characters";
        }
        if (role == null) {
            return "Select a valid role";
        }
        return null;
    }

    private String safeTrim(String value) {
        return value == null ? null : value.trim();
    }

    private String normalizeEmail(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    private String encodeMessage(String value) {
        return value == null ? "" : value.trim().replace(" ", "+");
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }
}
