package util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;

/**
 * Fallback local file storage when Cloudinary is not configured.
 * Files are stored under a configurable uploads directory and served
 * via a servlet-mapped path.
 */
public final class LocalFileUtil {

    private static final String UPLOADS_DIR_ENV = "ETASMI_UPLOADS_DIR";
    private static final String DEFAULT_DIR = "/usr/local/tomcat/etasmi_uploads";

    private LocalFileUtil() {}

    public static String getUploadsDir() {
        String dir = System.getenv(UPLOADS_DIR_ENV);
        return (dir != null && !dir.isBlank()) ? dir.trim() : DEFAULT_DIR;
    }

    /**
     * Save an uploaded image to local storage.
     * @return the relative URL path (e.g. "/uploads/profile/abc123.jpg")
     */
    public static String saveImage(InputStream input, String subfolder, String originalFileName) throws IOException {
        return saveFile(input, subfolder, originalFileName, "jpg");
    }

    public static String saveFile(InputStream input, String subfolder, String originalFileName, String defaultExtension) throws IOException {
        Path baseDir = Paths.get(getUploadsDir());
        Path targetDir = baseDir.resolve(subfolder);
        Files.createDirectories(targetDir);

        String ext = extractExt(originalFileName);
        if (ext.isEmpty()) ext = defaultExtension == null || defaultExtension.isBlank() ? "bin" : defaultExtension;
        String uniqueName = UUID.randomUUID().toString().replace("-", "").substring(0, 16) + "." + ext;

        Path targetFile = targetDir.resolve(uniqueName);
        Files.copy(input, targetFile, StandardCopyOption.REPLACE_EXISTING);

        return "/uploads/" + subfolder + "/" + uniqueName;
    }

    private static String extractExt(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot >= name.length() - 1) return "";
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
