package setup;

import util.CloudinaryUtil;
import util.Db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Clears stale local media references that no longer point to real files.
 *
 * Usage inside the app container:
 * java -cp "/usr/local/tomcat/webapps/ROOT/WEB-INF/classes:/usr/local/tomcat/webapps/ROOT/WEB-INF/lib/*" setup.LegacyMediaReferenceCleanup
 */
public final class LegacyMediaReferenceCleanup {
    private static final String WEBAPP_ROOT = "/usr/local/tomcat/webapps/ROOT";

    private LegacyMediaReferenceCleanup() {
    }

    public static void main(String[] args) throws Exception {
        Summary summary = new Summary();
        try (Connection connection = Db.getConnection()) {
            cleanupRecitations(connection, summary);
            cleanupMaterials(connection, summary);
            cleanupQualifications(connection, summary);
            cleanupProfiles(connection, summary);
        }

        System.out.println("Legacy media reference cleanup finished.");
        System.out.println("Cleared stale references: " + summary.cleared);
        System.out.println("Kept existing local references: " + summary.keptExisting);
        System.out.println("Skipped cloud references: " + summary.skippedCloud);
        if (!summary.details.isEmpty()) {
            System.out.println("Details:");
            for (String detail : summary.details) {
                System.out.println(" - " + detail);
            }
        }
    }

    private static void cleanupRecitations(Connection connection, Summary summary) throws Exception {
        cleanupSingleField(connection,
                "SELECT recitation_id AS id, audio_file_path AS path FROM recitation WHERE audio_file_path IS NOT NULL AND audio_file_path <> ''",
                "UPDATE recitation SET audio_file_path = '' WHERE recitation_id = ?",
                "Recitation",
                summary);
    }

    private static void cleanupMaterials(Connection connection, Summary summary) throws Exception {
        cleanupSingleField(connection,
                "SELECT material_id AS id, file_path AS path FROM session_material WHERE file_path IS NOT NULL AND file_path <> ''",
                "UPDATE session_material SET file_path = '' WHERE material_id = ?",
                "Material",
                summary);
    }

    private static void cleanupQualifications(Connection connection, Summary summary) throws Exception {
        cleanupSingleField(connection,
                "SELECT instructor_id AS id, qualification_file AS path FROM instructor WHERE qualification_file IS NOT NULL AND qualification_file <> ''",
                "UPDATE instructor SET qualification_file = NULL WHERE instructor_id = ?",
                "Qualification",
                summary);
    }

    private static void cleanupProfiles(Connection connection, Summary summary) throws Exception {
        cleanupSingleField(connection,
                "SELECT user_id AS id, profile_image_url AS path FROM user WHERE profile_image_url IS NOT NULL AND profile_image_url <> ''",
                "UPDATE user SET profile_image_url = NULL, profile_image_updated_at = NULL WHERE user_id = ?",
                "Profile image",
                summary);
    }

    private static void cleanupSingleField(Connection connection,
                                           String selectSql,
                                           String clearSql,
                                           String label,
                                           Summary summary) throws Exception {
        try (PreparedStatement select = connection.prepareStatement(selectSql);
             ResultSet rs = select.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong("id");
                String rawPath = rs.getString("path");
                if (rawPath == null || rawPath.isBlank()) {
                    continue;
                }
                if (CloudinaryUtil.isCloudinaryUrl(rawPath)) {
                    summary.skippedCloud++;
                    continue;
                }

                Path resolved = resolveLegacyPath(rawPath);
                if (resolved != null && Files.isRegularFile(resolved)) {
                    summary.keptExisting++;
                    continue;
                }

                try (PreparedStatement clear = connection.prepareStatement(clearSql)) {
                    clear.setLong(1, id);
                    clear.executeUpdate();
                }
                summary.cleared++;
                summary.details.add(label + " #" + id + " cleared because the local file is missing: " + rawPath);
            }
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
            if (trimmed.startsWith("/uploads/") || trimmed.startsWith("/demo/")) {
                return Paths.get(WEBAPP_ROOT, trimmed.substring(1)).toAbsolutePath().normalize();
            }
            return Paths.get(trimmed).toAbsolutePath().normalize();
        } catch (Exception ex) {
            return null;
        }
    }

    private static final class Summary {
        private int cleared;
        private int keptExisting;
        private int skippedCloud;
        private final List<String> details = new ArrayList<>();
    }
}
