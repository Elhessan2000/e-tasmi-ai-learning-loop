package controller.auth;

import model.dao.UserDao;
import model.dao.impl.UserDaoJdbc;
import model.entity.StudentLevel;
import model.entity.UserRole;
import model.service.EmailVerificationService;
import model.service.RegistrationRequest;
import model.service.RegistrationResult;
import model.service.RegistrationService;
import model.service.ServiceResult;
import util.EmailVerificationConfig;
import util.JsonUtil;
import util.QualificationFileUtil;
import util.RateLimiter;
import util.UrlUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Part;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@WebServlet(name = "RegisterServlet", urlPatterns = {"/auth/register"})
@MultipartConfig
public class RegisterServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(RegisterServlet.class.getName());

    private final RegistrationService registrationService = new RegistrationService();
    private final EmailVerificationService emailVerificationService = new EmailVerificationService();
    private final UserDao userDao = new UserDaoJdbc();

    private boolean isAjax(HttpServletRequest request) {
        String xrw = request.getHeader("X-Requested-With");
        String accept = request.getHeader("Accept");
        return "XMLHttpRequest".equalsIgnoreCase(xrw) || (accept != null && accept.contains("application/json"));
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String roleParam = request.getParameter("role");
        UserRole role = UserRole.fromString(roleParam);

        if (role == null || (role != UserRole.STUDENT && role != UserRole.INSTRUCTOR)) {
            role = UserRole.STUDENT;
        }

        request.setAttribute("roleValue", role.name());
        request.getRequestDispatcher("/jsp/auth/register.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String fullName = request.getParameter("fullName");
        String email = request.getParameter("email");
        String phone = request.getParameter("phone");
        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");
        String role = request.getParameter("role");
        String studentLevelStr = request.getParameter("studentLevel");

        String qualificationPath = null;
        String bio = request.getParameter("bio");

        UserRole parsedRole = UserRole.fromString(role);
        if (parsedRole == null || (parsedRole != UserRole.STUDENT && parsedRole != UserRole.INSTRUCTOR)) {
            setStickyValues(request, fullName, email, phone, parsedRole, bio);
            String msg = "Please choose Student or Instructor registration.";
            if (isAjax(request)) {
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> payload = new HashMap<>();
                payload.put("success", false);
                payload.put("error", msg);
                response.getWriter().write(JsonUtil.obj(payload));
                return;
            }
            request.setAttribute("roleError", msg);
            request.setAttribute("error", msg);
            request.getRequestDispatcher("/jsp/auth/register.jsp").forward(request, response);
            return;
        }

        if (password != null && confirmPassword != null && !password.equals(confirmPassword)) {
            setStickyValues(request, fullName, email, phone, parsedRole, bio);
            String msg = "Passwords do not match";
            if (isAjax(request)) {
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> fieldErrors = new HashMap<>();
                fieldErrors.put("confirmPassword", msg);
                Map<String, Object> payload = new HashMap<>();
                payload.put("success", false);
                payload.put("error", msg);
                payload.put("fieldErrors", fieldErrors);
                response.getWriter().write(JsonUtil.obj(payload));
                return;
            }
            request.setAttribute("confirmPasswordError", msg);
            request.setAttribute("error", msg);
            request.getRequestDispatcher("/jsp/auth/register.jsp").forward(request, response);
            return;
        }

        // Rate limit by IP to reduce abuse.
        String ip = request.getRemoteAddr();
        if (!RateLimiter.allow("register:" + ip, 10, Duration.ofMinutes(10))) {
            String msg = "Too many registration attempts. Please try again later.";
            if (isAjax(request)) {
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> payload = new HashMap<>();
                payload.put("success", false);
                payload.put("error", msg);
                response.getWriter().write(JsonUtil.obj(payload));
                return;
            }
            setStickyValues(request, fullName, email, phone, parsedRole, bio);
            request.setAttribute("error", msg);
            request.getRequestDispatcher("/jsp/auth/register.jsp").forward(request, response);
            return;
        }

        if (parsedRole == UserRole.INSTRUCTOR) {
            try {
                if (isEmailAlreadyRegistered(email)) {
                    setStickyValues(request, fullName, email, phone, parsedRole, bio);
                    String msg = "Email is already registered.";
                    if (isAjax(request)) {
                        response.setContentType("application/json;charset=UTF-8");
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("success", false);
                        payload.put("error", msg);
                        response.getWriter().write(JsonUtil.obj(payload));
                        return;
                    }
                    request.setAttribute("emailError", msg);
                    request.setAttribute("error", msg);
                    request.getRequestDispatcher("/jsp/auth/register.jsp").forward(request, response);
                    return;
                }
                Part qualification = request.getPart("qualification");
                qualificationPath = QualificationFileUtil.savePdf(getServletContext(), qualification);
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Qualification upload failed", ex);
                setStickyValues(request, fullName, email, phone, parsedRole, bio);
                String msg = (ex.getMessage() == null || ex.getMessage().isBlank())
                        ? "Qualification upload failed. Please try again."
                        : ex.getMessage();
                if (isAjax(request)) {
                    response.setContentType("application/json;charset=UTF-8");
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("success", false);
                    payload.put("error", msg);
                    response.getWriter().write(JsonUtil.obj(payload));
                    return;
                }
                request.setAttribute("qualificationError", msg);
                request.setAttribute("error", msg);
                request.getRequestDispatcher("/jsp/auth/register.jsp").forward(request, response);
                return;
            }
        }

        StudentLevel parsedLevel = null;
        if (parsedRole == UserRole.STUDENT) {
            parsedLevel = StudentLevel.fromString(studentLevelStr);
            if (parsedLevel == null) {
                setStickyValues(request, fullName, email, phone, parsedRole, bio);
                request.setAttribute("studentLevelValue", studentLevelStr);
                String msg = "Please select your education level.";
                if (isAjax(request)) {
                    response.setContentType("application/json;charset=UTF-8");
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("success", false);
                    payload.put("error", msg);
                    response.getWriter().write(JsonUtil.obj(payload));
                    return;
                }
                request.setAttribute("studentLevelError", msg);
                request.setAttribute("error", msg);
                request.getRequestDispatcher("/jsp/auth/register.jsp").forward(request, response);
                return;
            }
        }

        RegistrationRequest rr = new RegistrationRequest();
        rr.setFullName(fullName);
        rr.setEmail(email);
        rr.setPhone(phone);
        rr.setPassword(password == null ? null : password.toCharArray());
        rr.setRole(parsedRole);
        rr.setStudentLevel(parsedLevel);
        rr.setQualificationFilePath(qualificationPath);
        rr.setInstructorBio(bio);

        RegistrationResult result = registrationService.register(rr);
        if (!result.isSuccess() && !result.isExistingUnverified()) {
            cleanupQualificationIfNeeded(qualificationPath);
            setStickyValues(request, fullName, email, phone, parsedRole, bio);
            if (isAjax(request)) {
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> payload = new HashMap<>();
                payload.put("success", false);
                payload.put("error", result.getError());
                response.getWriter().write(JsonUtil.obj(payload));
                return;
            }
            request.setAttribute("error", result.getError());
            attachInlineError(request, result.getError());
            request.getRequestDispatcher("/jsp/auth/register.jsp").forward(request, response);
            return;
        }

        if (!EmailVerificationConfig.isEnabled()) {
            String redirect = request.getContextPath() + "/auth/login"
                    + (parsedRole == UserRole.INSTRUCTOR ? "?pending=1" : "?registered=1");
            if (isAjax(request)) {
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> payload = new HashMap<>();
                payload.put("success", true);
                payload.put("redirect", redirect);
                response.getWriter().write(JsonUtil.obj(payload));
                return;
            }
            response.sendRedirect(redirect);
            return;
        }

        String pendingEmail = result.getEmail();
        String baseUrl = UrlUtil.getBaseUrl(request);
        ServiceResult verificationResult = emailVerificationService.createAndSendCode(
                getServletContext(),
                result.getUserId() == null ? 0L : result.getUserId(),
                pendingEmail,
                fullName,
                baseUrl
        );

        // Build the base "account was created" message tailored to the user's role.
        String baseMsg;
        if (result.isExistingUnverified()) {
            baseMsg = "Your account already exists but is not verified yet.";
        } else if (parsedRole == UserRole.INSTRUCTOR) {
            baseMsg = "Registration received. Your instructor account will remain pending admin review until approval.";
        } else {
            baseMsg = "Registration successful. Your account was created.";
        }

        // Decide a SINGLE message tone. We never show success + error together:
        //   - email delivered OK   → one success message (tone=success)
        //   - email send failed    → one warning message explaining what happened
        //                            and how to retry via the resend button
        String successMsg = null;
        String warningMsg = null;

        if (verificationResult.isSuccess()) {
            successMsg = baseMsg + " " + verificationResult.getMessage()
                    + " Enter the 6-digit code below to verify your email.";
        } else {
            LOGGER.log(Level.WARNING,
                    "Verification email could not be sent for {0}: {1}",
                    new Object[]{pendingEmail, verificationResult.getError()});
            warningMsg = baseMsg
                    + " However, we could not deliver the verification email right now. "
                    + "Please click \"Resend Verification Code\" below to try again.";
        }

        String redirect = request.getContextPath() + "/auth/verify-email" +
                "?email=" + java.net.URLEncoder.encode(pendingEmail == null ? "" : pendingEmail, java.nio.charset.StandardCharsets.UTF_8);
        if (successMsg != null) {
            redirect += "&msg=" + java.net.URLEncoder.encode(successMsg, java.nio.charset.StandardCharsets.UTF_8);
        } else if (warningMsg != null) {
            redirect += "&warn=" + java.net.URLEncoder.encode(warningMsg, java.nio.charset.StandardCharsets.UTF_8);
        }

        if (isAjax(request)) {
            response.setContentType("application/json;charset=UTF-8");
            Map<String, Object> payload = new HashMap<>();
            payload.put("success", true);
            payload.put("redirect", redirect);
            response.getWriter().write(JsonUtil.obj(payload));
            return;
        }
        request.setAttribute("pendingEmail", pendingEmail);
        if (successMsg != null) {
            request.setAttribute("success", successMsg);
        } else if (warningMsg != null) {
            request.setAttribute("warning", warningMsg);
        }
        request.getRequestDispatcher("/jsp/auth/verify_email.jsp").forward(request, response);
    }

    private boolean isAdminSession(HttpSession session) {
        if (session == null) {
            return false;
        }
        Object role = session.getAttribute("role");
        return role != null && "ADMIN".equalsIgnoreCase(String.valueOf(role));
    }

    private void setStickyValues(HttpServletRequest request,
                                 String fullName,
                                 String email,
                                 String phone,
                                 UserRole role,
                                 String bio) {
        request.setAttribute("fullNameValue", fullName);
        request.setAttribute("emailValue", email);
        request.setAttribute("phoneValue", phone);
        request.setAttribute("roleValue", role == null ? null : role.name());
        request.setAttribute("bioValue", bio);
        String level = request.getParameter("studentLevel");
        if (level != null) {
            request.setAttribute("studentLevelValue", level);
        }
    }

    private void attachInlineError(HttpServletRequest request, String message) {
        if (message == null) {
            return;
        }
        String m = message.toLowerCase();
        if (m.contains("full name")) {
            request.setAttribute("fullNameError", message);
        } else if (m.contains("email")) {
            request.setAttribute("emailError", message);
        } else if (m.contains("phone")) {
            request.setAttribute("phoneError", message);
        } else if (m.contains("password")) {
            request.setAttribute("passwordError", message);
        } else if (m.contains("qualification")) {
            request.setAttribute("qualificationError", message);
        } else if (m.contains("bio")) {
            request.setAttribute("bioError", message);
        } else if (m.contains("level")) {
            request.setAttribute("studentLevelError", message);
        } else if (m.contains("role")) {
            request.setAttribute("roleError", message);
        }
    }

    private boolean isEmailAlreadyRegistered(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        try (Connection connection = util.Db.getConnection()) {
            return userDao.findByEmail(connection, email.trim().toLowerCase()).isPresent();
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Could not pre-check email availability", ex);
            return false;
        }
    }

    private void cleanupQualificationIfNeeded(String qualificationPath) {
        if (qualificationPath == null || qualificationPath.isBlank()) {
            return;
        }
        try {
            if (qualificationPath.startsWith("http://") || qualificationPath.startsWith("https://")) {
                util.CloudinaryUtil.deleteByUrl(qualificationPath);
                return;
            }
            java.nio.file.Files.deleteIfExists(Path.of(qualificationPath));
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to clean up orphaned qualification upload", ex);
        }
    }
}
