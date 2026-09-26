package controller.instructor;

import model.dao.InstructorDao;
import model.dao.UserDao;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.UserDaoJdbc;
import model.entity.Instructor;
import model.entity.User;
import util.CloudinaryUtil;
import util.Db;
import util.LocalFileUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Part;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "InstructorProfileServlet", urlPatterns = {"/instructor/profile"})
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,
        maxFileSize = 2L * 1024 * 1024,
        maxRequestSize = 3L * 1024 * 1024
)
public class InstructorProfileServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(InstructorProfileServlet.class.getName());

    private final UserDao userDao = new UserDaoJdbc();
    private final InstructorDao instructorDao = new InstructorDaoJdbc();

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
                instructorDao.findByUserId(connection, userId).ifPresent(i -> request.setAttribute("instructor", i));
            } catch (SQLException ex) {
                LOGGER.log(Level.SEVERE, "Failed to load instructor profile", ex);
            }
        }

        request.getRequestDispatcher("/jsp/instructor/profile.jsp").forward(request, response);
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
        if ("photo".equals(action)) {
            handlePhotoUpload(request, response, userId);
            return;
        }

        response.sendRedirect(request.getContextPath() + "/instructor/profile");
    }

    private void handleProfileUpdate(HttpServletRequest request, HttpServletResponse response, HttpSession session, long userId) throws IOException, ServletException {
        String namePrefix = safe(request.getParameter("namePrefix")).trim();
        String baseName = safe(request.getParameter("fullName")).trim();
        String phone = safe(request.getParameter("phone")).trim();
        String qualification = safe(request.getParameter("qualification")).trim();
        String zoomEmail = safe(request.getParameter("zoomEmail")).trim();

        if (baseName.isEmpty() || phone.isEmpty()) {
            request.setAttribute("error", "Full name and phone are required.");
            doGet(request, response);
            return;
        }

        String fullName = buildDisplayName(namePrefix, baseName);

        try (Connection connection = Db.getConnection()) {
            Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, userId);
            if (instructorOpt.isEmpty()) {
                request.setAttribute("error", "Instructor profile not found.");
                doGet(request, response);
                return;
            }

            Instructor instructor = instructorOpt.get();

            boolean okUser = userDao.updateProfile(connection, userId, fullName, phone);
            boolean okInstructor = instructorDao.updateProfile(
                    connection,
                    instructor.getInstructorId(),
                    null,
                    qualification.isEmpty() ? null : qualification
            );
            instructorDao.updateZoomEmail(
                    connection,
                    instructor.getInstructorId(),
                    zoomEmail.isEmpty() ? null : zoomEmail
            );

            if (!okUser || !okInstructor) {
                request.setAttribute("error", "Profile update failed.");
                doGet(request, response);
                return;
            }

            session.setAttribute("displayName", fullName);
            request.setAttribute("success", "Profile updated successfully.");
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to update instructor profile", ex);
            request.setAttribute("error", "Profile update failed due to a server error.");
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

        try (var validationStream = photo.getInputStream()) {
            BufferedImage image = ImageIO.read(validationStream);
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                request.setAttribute("error", "The uploaded file is not a valid image.");
                doGet(request, response);
                return;
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to validate uploaded image content", ex);
            request.setAttribute("error", "The uploaded file is not a valid image.");
            doGet(request, response);
            return;
        }

        try (var in = photo.getInputStream(); Connection connection = Db.getConnection()) {
            String imageUrl;
            if (CloudinaryUtil.isConfigured()) {
                LOGGER.info("Uploading instructor profile photo to Cloudinary for user " + userId);
                imageUrl = CloudinaryUtil.uploadImageAsJpg(
                        in,
                        "profile/user_" + userId,
                        "profile_user_" + userId + "." + ext,
                        photo.getContentType()
                );
            } else {
                LOGGER.info("Cloudinary not configured, saving instructor profile photo locally for user " + userId);
                imageUrl = LocalFileUtil.saveImage(in, "profile", "user_" + userId + "." + ext);
            }
            LOGGER.info("Instructor profile photo saved: " + imageUrl);
            boolean saved = userDao.updateProfileImage(connection, userId, imageUrl);
            if (!saved) {
                request.setAttribute("error", "Profile photo uploaded, but the account record could not be updated.");
                doGet(request, response);
                return;
            }
            request.setAttribute("success", "Profile photo updated.");
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to store instructor profile image metadata", ex);
            request.setAttribute("error", "Profile photo uploaded, but its record could not be saved.");
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Profile photo upload failed", ex);
            request.setAttribute("error", "Photo upload failed. Please try again.");
        }

        doGet(request, response);
    }

    private String buildDisplayName(String prefix, String baseName) {
        String p = prefix == null ? "" : prefix.trim();
        String n = baseName == null ? "" : baseName.trim();

        if (n.isEmpty()) {
            return "";
        }

        if ("Austaz".equalsIgnoreCase(p)) {
            return "Austaz " + n;
        }
        if ("Astazh".equalsIgnoreCase(p)) {
            return "Astazh " + n;
        }
        return n;
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
