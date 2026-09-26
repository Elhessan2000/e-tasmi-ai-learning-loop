package setup;

import util.CloudinaryUtil;
import util.Db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One-off utility to migrate legacy local media references to Cloudinary URLs.
 *
 * Usage inside the app container:
 * java -cp "/usr/local/tomcat/webapps/ROOT/WEB-INF/classes:/usr/local/tomcat/webapps/ROOT/WEB-INF/lib/*" setup.LegacyMediaCloudinaryMigration
 */
public final class LegacyMediaCloudinaryMigration {
    private static final String WEBAPP_ROOT = "/usr/local/tomcat/webapps/ROOT";

    private LegacyMediaCloudinaryMigration() {
    }

    public static void main(String[] args) throws Exception {
        if (!CloudinaryUtil.isConfigured()) {
            throw new IllegalStateException("Cloudinary is not configured. Refusing to run migration.");
        }

        MigrationSummary summary = new MigrationSummary();
        try (Connection connection = Db.getConnection()) {
            migrateRecitations(connection, summary);
            migrateMaterials(connection, summary);
            migrateQualifications(connection, summary);
            migrateProfileUrls(connection, summary);
        }

        System.out.println("Legacy media migration finished.");
        System.out.println("Migrated: " + summary.migrated);
        System.out.println("Skipped missing: " + summary.skippedMissing);
        System.out.println("Skipped already-cloud: " + summary.skippedCloud);
        if (!summary.messages.isEmpty()) {
            System.out.println("Details:");
            for (String message : summary.messages) {
                System.out.println(" - " + message);
            }
        }
    }

    private static void migrateRecitations(Connection connection, MigrationSummary summary) throws Exception {
        String sql = "SELECT recitation_id, audio_file_path FROM recitation WHERE audio_file_path IS NOT NULL AND audio_file_path <> ''";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long recitationId = rs.getLong("recitation_id");
                String currentPath = rs.getString("audio_file_path");
                if (CloudinaryUtil.isCloudinaryUrl(currentPath)) {
                    summary.skippedCloud++;
                    continue;
                }

                Path source = resolveLegacyPath(currentPath);
                if (source == null || !Files.isRegularFile(source)) {
                    summary.skippedMissing++;
                    summary.messages.add("Recitation #" + recitationId + " skipped because file is missing: " + currentPath);
                    continue;
                }

                String fileName = source.getFileName().toString();
                String publicId = "recitations/legacy_" + recitationId + "_" + Instant.now().toEpochMilli();
                String contentType = safeContentType(source, "audio/mpeg");
                String uploadedUrl;
                try (InputStream in = Files.newInputStream(source)) {
                    uploadedUrl = CloudinaryUtil.uploadVideo(in, publicId, fileName, contentType);
                }
                updateSingleField(connection, "UPDATE recitation SET audio_file_path = ? WHERE recitation_id = ?", uploadedUrl, recitationId);
                deleteLegacyFile(source, summary, "Recitation #" + recitationId);
                summary.migrated++;
            }
        }
    }

    private static void migrateMaterials(Connection connection, MigrationSummary summary) throws Exception {
        String sql = "SELECT material_id, file_path FROM session_material WHERE file_path IS NOT NULL AND file_path <> ''";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long materialId = rs.getLong("material_id");
                String currentPath = rs.getString("file_path");
                if (CloudinaryUtil.isCloudinaryUrl(currentPath)) {
                    summary.skippedCloud++;
                    continue;
                }

                Path source = resolveLegacyPath(currentPath);
                if (source == null || !Files.isRegularFile(source)) {
                    summary.skippedMissing++;
                    summary.messages.add("Material #" + materialId + " skipped because file is missing: " + currentPath);
                    continue;
                }

                String fileName = source.getFileName().toString();
                String contentType = safeContentType(source, "application/octet-stream");
                String publicId = "materials/legacy_" + materialId + "_" + Instant.now().toEpochMilli();
                String uploadedUrl;
                try (InputStream in = Files.newInputStream(source)) {
                    if (contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                        uploadedUrl = CloudinaryUtil.uploadImageAsJpg(in, publicId, fileName, contentType);
                    } else {
                        uploadedUrl = CloudinaryUtil.uploadRaw(in, publicId, fileName, contentType);
                    }
                }
                updateSingleField(connection, "UPDATE session_material SET file_path = ? WHERE material_id = ?", uploadedUrl, materialId);
                deleteLegacyFile(source, summary, "Material #" + materialId);
                summary.migrated++;
            }
        }
    }

    private static void migrateQualifications(Connection connection, MigrationSummary summary) throws Exception {
        String sql = "SELECT instructor_id, qualification_file FROM instructor WHERE qualification_file IS NOT NULL AND qualification_file <> ''";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long instructorId = rs.getLong("instructor_id");
                String currentPath = rs.getString("qualification_file");
                if (CloudinaryUtil.isCloudinaryUrl(currentPath)) {
                    summary.skippedCloud++;
                    continue;
                }

                Path source = resolveLegacyPath(currentPath);
                if (source == null || !Files.isRegularFile(source)) {
                    summary.skippedMissing++;
                    summary.messages.add("Instructor qualification #" + instructorId + " skipped because file is missing: " + currentPath);
                    continue;
                }

                String fileName = source.getFileName().toString();
                String contentType = safeContentType(source, "application/octet-stream");
                String publicId = "qualifications/legacy_" + instructorId + "_" + Instant.now().toEpochMilli();
                String uploadedUrl;
                try (InputStream in = Files.newInputStream(source)) {
                    if (contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                        uploadedUrl = CloudinaryUtil.uploadImageAsJpg(in, publicId, fileName, contentType);
                    } else {
                        uploadedUrl = CloudinaryUtil.uploadRaw(in, publicId, fileName, contentType);
                    }
                }
                updateSingleField(connection, "UPDATE instructor SET qualification_file = ? WHERE instructor_id = ?", uploadedUrl, instructorId);
                deleteLegacyFile(source, summary, "Qualification #" + instructorId);
                summary.migrated++;
            }
        }
    }

    private static void migrateProfileUrls(Connection connection, MigrationSummary summary) throws Exception {
        String sql = "SELECT user_id, profile_image_url FROM user WHERE profile_image_url IS NOT NULL AND profile_image_url <> ''";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long userId = rs.getLong("user_id");
                String currentPath = rs.getString("profile_image_url");
                if (CloudinaryUtil.isCloudinaryUrl(currentPath)) {
                    summary.skippedCloud++;
                    continue;
                }

                Path source = resolveLegacyPath(currentPath);
                if (source == null || !Files.isRegularFile(source)) {
                    summary.skippedMissing++;
                    summary.messages.add("Profile image #" + userId + " skipped because file is missing: " + currentPath);
                    continue;
                }

                String fileName = source.getFileName().toString();
                String contentType = safeContentType(source, "image/jpeg");
                String publicId = "profile/user_" + userId;
                String uploadedUrl;
                try (InputStream in = Files.newInputStream(source)) {
                    uploadedUrl = CloudinaryUtil.uploadImageAsJpg(in, publicId, fileName, contentType);
                }
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE user SET profile_image_url = ?, profile_image_updated_at = CURRENT_TIMESTAMP WHERE user_id = ?")) {
                    update.setString(1, uploadedUrl);
                    update.setLong(2, userId);
                    update.executeUpdate();
                }
                deleteLegacyFile(source, summary, "Profile image #" + userId);
                summary.migrated++;
            }
        }
    }

    private static void updateSingleField(Connection connection, String sql, String newUrl, long id) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newUrl);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private static Path resolveLegacyPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        String trimmed = rawPath.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return null;
        }

        try {
            if (trimmed.startsWith("/")) {
                if (trimmed.startsWith("/uploads/") || trimmed.startsWith("/demo/")) {
                    return Paths.get(WEBAPP_ROOT, trimmed.substring(1)).toAbsolutePath().normalize();
                }
                return Paths.get(trimmed).toAbsolutePath().normalize();
            }
            return Paths.get(trimmed).toAbsolutePath().normalize();
        } catch (Exception ex) {
            return null;
        }
    }

    private static String safeContentType(Path source, String fallback) {
        try {
            String contentType = Files.probeContentType(source);
            if (contentType != null && !contentType.isBlank()) {
                return contentType;
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    private static void deleteLegacyFile(Path source, MigrationSummary summary, String label) {
        try {
            Files.deleteIfExists(source);
        } catch (IOException ex) {
            summary.messages.add(label + " migrated, but local cleanup failed: " + source);
        }
    }

    private static final class MigrationSummary {
        private int migrated;
        private int skippedMissing;
        private int skippedCloud;
        private final List<String> messages = new ArrayList<>();
    }
}
