package controller.student;

import model.dao.StudentDao;
import model.dao.UserDao;
import model.dao.impl.StudentDaoJdbc;
import model.dao.impl.UserDaoJdbc;
import model.entity.Student;
import model.entity.User;
import util.Db;
import util.PasswordUtil;
import util.CloudinaryUtil;
import util.LocalFileUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "StudentProfileServlet", urlPatterns = {"/student/profile"})
@MultipartConfig(
		fileSizeThreshold = 1024 * 1024,
		maxFileSize = 2L * 1024 * 1024,
		maxRequestSize = 3L * 1024 * 1024
)
public class StudentProfileServlet extends HttpServlet {
	private static final Logger LOGGER = Logger.getLogger(StudentProfileServlet.class.getName());

	private final UserDao userDao = new UserDaoJdbc();
	private final StudentDao studentDao = new StudentDaoJdbc();

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		long userId = readUserId(session);

		if (userId > 0) {
			try (Connection connection = Db.getConnection()) {
				userDao.findById(connection, userId).ifPresent(u -> {
					request.setAttribute("user", u);
					if (u.getProfileImageUrl() != null && !u.getProfileImageUrl().isBlank()) {
						request.setAttribute("profileImageUrl", u.getProfileImageUrl());
					}
				});
				studentDao.findByUserId(connection, userId).ifPresent(s -> request.setAttribute("student", s));
			} catch (SQLException ex) {
				LOGGER.log(Level.SEVERE, "Failed to load profile", ex);
			}
		}

		request.getRequestDispatcher("/jsp/student/profile.jsp").forward(request, response);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		long userId = readUserId(session);
		if (userId <= 0) {
			response.sendRedirect(request.getContextPath() + "/auth/login?expired=1");
			return;
		}

		String action = safe(request.getParameter("action"));
		if ("profile".equals(action)) {
			handleProfileUpdate(request, response, session, userId);
			return;
		}
		if ("password".equals(action)) {
			handlePasswordChange(request, response, userId);
			return;
		}
		if ("photo".equals(action)) {
			handlePhotoUpload(request, response, userId);
			return;
		}

		response.sendRedirect(request.getContextPath() + "/student/profile");
	}

	private void handleProfileUpdate(HttpServletRequest request, HttpServletResponse response, HttpSession session, long userId) throws IOException, ServletException {
		String fullName = safe(request.getParameter("fullName")).trim();
		String phone = safe(request.getParameter("phone")).trim();

		if (fullName.isEmpty() || phone.isEmpty()) {
			request.setAttribute("error", "Full name and phone are required.");
			doGet(request, response);
			return;
		}

		try (Connection connection = Db.getConnection()) {
			boolean ok = userDao.updateProfile(connection, userId, fullName, phone);
			if (!ok) {
				request.setAttribute("error", "Profile update failed.");
				doGet(request, response);
				return;
			}
			session.setAttribute("displayName", fullName);
			request.setAttribute("success", "Profile updated successfully.");
		} catch (SQLException ex) {
			LOGGER.log(Level.SEVERE, "Failed to update profile", ex);
			request.setAttribute("error", "Profile update failed due to a server error.");
		}

		doGet(request, response);
	}

	private void handlePasswordChange(HttpServletRequest request, HttpServletResponse response, long userId) throws IOException, ServletException {
		String currentPassword = safe(request.getParameter("currentPassword"));
		String newPassword = safe(request.getParameter("newPassword"));
		String confirmPassword = safe(request.getParameter("confirmPassword"));

		if (currentPassword.trim().isEmpty() || newPassword.trim().isEmpty() || confirmPassword.trim().isEmpty()) {
			request.setAttribute("error", "All password fields are required.");
			doGet(request, response);
			return;
		}

		if (!newPassword.equals(confirmPassword)) {
			request.setAttribute("error", "New password and confirm password do not match.");
			doGet(request, response);
			return;
		}

		if (!isStrongPassword(newPassword)) {
			request.setAttribute("error", "Password must be at least 8 characters and include uppercase, lowercase, and a number.");
			doGet(request, response);
			return;
		}

		try (Connection connection = Db.getConnection()) {
			Optional<User> userOpt = userDao.findById(connection, userId);
			if (userOpt.isEmpty()) {
				request.setAttribute("error", "Account not found.");
				doGet(request, response);
				return;
			}

			User user = userOpt.get();
			if (!PasswordUtil.verifyPassword(currentPassword.toCharArray(), user.getPasswordHash())) {
				request.setAttribute("error", "Current password is incorrect.");
				doGet(request, response);
				return;
			}

			String newHash = PasswordUtil.hashPassword(newPassword.toCharArray());
			boolean ok = userDao.updatePasswordHash(connection, userId, newHash);
			if (!ok) {
				request.setAttribute("error", "Password update failed.");
				doGet(request, response);
				return;
			}
			request.setAttribute("success", "Password updated successfully.");
		} catch (SQLException ex) {
			LOGGER.log(Level.SEVERE, "Failed to update password", ex);
			request.setAttribute("error", "Password update failed due to a server error.");
		}

		doGet(request, response);
	}

	private void handlePhotoUpload(HttpServletRequest request, HttpServletResponse response, long userId) throws IOException, ServletException {
		Part photo;
		try {
			photo = request.getPart("photo");
		} catch (Exception ex) {
			LOGGER.log(Level.WARNING, "Failed to read photo part", ex);
			request.setAttribute("error", "Please choose an image file.");
			doGet(request, response);
			return;
		}

		if (photo == null || photo.getSize() <= 0) {
			request.setAttribute("error", "Please choose an image file.");
			doGet(request, response);
			return;
		}

		String contentType = safe(photo.getContentType()).toLowerCase(Locale.ROOT);
		if (!contentType.startsWith("image/")) {
			request.setAttribute("error", "Only image files are allowed.");
			doGet(request, response);
			return;
		}

		String submittedName = photo.getSubmittedFileName();
		String ext = extractExt(submittedName);
		if (!isAllowedImageExt(ext)) {
			request.setAttribute("error", "Allowed formats: JPG, PNG, WEBP.");
			doGet(request, response);
			return;
		}

		try (var in = photo.getInputStream(); Connection connection = Db.getConnection()) {
			String imageUrl;
			if (CloudinaryUtil.isConfigured()) {
				LOGGER.info("Uploading profile photo to Cloudinary for user " + userId);
				imageUrl = CloudinaryUtil.uploadImageAsJpg(
						in,
						"profile/user_" + userId,
						"profile_user_" + userId + "." + ext,
						photo.getContentType()
				);
			} else {
				LOGGER.info("Cloudinary not configured, saving profile photo locally for user " + userId);
				imageUrl = LocalFileUtil.saveImage(in, "profile", "user_" + userId + "." + ext);
			}
			LOGGER.info("Profile photo saved: " + imageUrl);
			boolean saved = userDao.updateProfileImage(connection, userId, imageUrl);
			if (!saved) {
				request.setAttribute("error", "Profile photo uploaded, but the account record could not be updated.");
				doGet(request, response);
				return;
			}
			request.setAttribute("success", "Profile photo updated.");
		} catch (SQLException ex) {
			LOGGER.log(Level.SEVERE, "Failed to store profile image metadata", ex);
			request.setAttribute("error", "Profile photo uploaded, but its record could not be saved.");
		} catch (Exception ex) {
			LOGGER.log(Level.SEVERE, "Profile photo upload failed", ex);
			request.setAttribute("error", "Photo upload failed. Please try again.");
		}

		doGet(request, response);
	}

	private boolean isStrongPassword(String password) {
		if (password == null || password.length() < 8) {
			return false;
		}
		boolean hasUpper = false;
		boolean hasLower = false;
		boolean hasDigit = false;
		for (int i = 0; i < password.length(); i++) {
			char c = password.charAt(i);
			if (Character.isUpperCase(c)) hasUpper = true;
			else if (Character.isLowerCase(c)) hasLower = true;
			else if (Character.isDigit(c)) hasDigit = true;
		}
		return hasUpper && hasLower && hasDigit;
	}

	private String extractExt(String submittedName) {
		if (submittedName == null) {
			return "";
		}
		int dot = submittedName.lastIndexOf('.');
		if (dot < 0 || dot >= submittedName.length() - 1) {
			return "";
		}
		return submittedName.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private boolean isAllowedImageExt(String ext) {
		if (ext == null) {
			return false;
		}
		switch (ext.toLowerCase(Locale.ROOT)) {
			case "jpg":
			case "jpeg":
			case "png":
			case "webp":
				return true;
			default:
				return false;
		}
	}

	private String safe(String s) {
		return s == null ? "" : s;
	}

	private long readUserId(HttpSession session) {
		if (session == null) {
			return 0;
		}
		Object userIdObj = session.getAttribute("userId");
		if (userIdObj instanceof Long) {
			return (Long) userIdObj;
		}
		if (userIdObj instanceof Integer) {
			return ((Integer) userIdObj).longValue();
		}
		return 0;
	}
}

