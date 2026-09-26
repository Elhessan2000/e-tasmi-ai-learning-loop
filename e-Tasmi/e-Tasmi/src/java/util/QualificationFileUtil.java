package util;

import javax.servlet.ServletContext;
import javax.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

public final class QualificationFileUtil {
    private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L;

    private QualificationFileUtil() {
    }

    public static String savePdf(ServletContext servletContext, Part qualification) throws IOException {
        if (qualification == null || qualification.getSize() <= 0) {
            return null;
        }
        if (qualification.getSize() > MAX_FILE_SIZE) {
            throw new IOException("Qualification PDF exceeds 10 MB.");
        }

        String original = qualification.getSubmittedFileName();
        String safeName = safeFileName(original == null ? "qualification.pdf" : original);
        if (!safeName.toLowerCase().endsWith(".pdf")) {
            throw new IOException("Only PDF qualification files are allowed.");
        }

        String contentType = qualification.getContentType();
        String normalizedType = contentType == null ? "" : contentType.trim().toLowerCase();
        if (!normalizedType.isBlank() && !"application/pdf".equals(normalizedType) && !"application/octet-stream".equals(normalizedType)) {
            throw new IOException("Only PDF qualification files are allowed.");
        }

        String fileName = UUID.randomUUID() + "_" + safeName;
        try (InputStream in = qualification.getInputStream()) {
            Path directory = qualificationDirectory(servletContext);
            Path target = directory.resolve(fileName).normalize();
            if (!target.startsWith(directory)) {
                throw new IOException("Invalid qualification file name.");
            }
            Files.copy(in, target);
            return target.toAbsolutePath().normalize().toString();
        }
    }

    public static boolean isHttpUrl(String value) {
        String v = value == null ? "" : value.trim().toLowerCase();
        return v.startsWith("http://") || v.startsWith("https://");
    }

    public static boolean isAllowedQualificationPath(Path filePath) {
        if (filePath == null) {
            return false;
        }

        String base = System.getProperty("catalina.base");
        String tmp = System.getProperty("java.io.tmpdir");

        Path allowed1 = null;
        if (base != null && !base.isBlank()) {
            allowed1 = Paths.get(base, "etasmi_uploads", "qualifications").toAbsolutePath().normalize();
        }
        Path allowed2 = null;
        if (tmp != null && !tmp.isBlank()) {
            allowed2 = Paths.get(tmp, "etasmi_uploads", "qualifications").toAbsolutePath().normalize();
        }

        if (allowed1 != null && filePath.startsWith(allowed1)) {
            return true;
        }
        return allowed2 != null && filePath.startsWith(allowed2);
    }

    public static boolean isAvailable(String qualificationFile) {
        String q = qualificationFile == null ? "" : qualificationFile.trim();
        if (q.isEmpty()) {
            return false;
        }
        if (isHttpUrl(q)) {
            return remoteFileAvailable(q) || CloudinaryUtil.buildSignedDownloadUrl(q, false) != null;
        }

        try {
            Path filePath = Paths.get(q).toAbsolutePath().normalize();
            return isAllowedQualificationPath(filePath) && Files.exists(filePath) && Files.isRegularFile(filePath);
        } catch (Exception ex) {
            return false;
        }
    }

    public static boolean remoteFileAvailable(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(5000);
            connection.setInstanceFollowRedirects(true);
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_BAD_METHOD) {
                close(connection);
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Range", "bytes=0-0");
                connection.setConnectTimeout(4000);
                connection.setReadTimeout(5000);
                connection.setInstanceFollowRedirects(true);
                status = connection.getResponseCode();
            }
            return status >= 200 && status < 400;
        } catch (Exception ex) {
            return false;
        } finally {
            close(connection);
        }
    }

    public static String safeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "qualification.pdf";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public static String headerSafeFileName(String name) {
        String safe = safeFileName(name);
        return safe.replace("\"", "").replace("\r", "").replace("\n", "");
    }

    private static Path qualificationDirectory(ServletContext servletContext) throws IOException {
        String base = System.getProperty("catalina.base");
        Path root;
        if (base != null && !base.isBlank()) {
            root = Paths.get(base, "etasmi_uploads", "qualifications");
        } else {
            root = Paths.get(System.getProperty("java.io.tmpdir"), "etasmi_uploads", "qualifications");
        }
        Path normalized = root.toAbsolutePath().normalize();
        Files.createDirectories(normalized);
        return normalized;
    }

    private static void close(HttpURLConnection connection) {
        if (connection != null) {
            connection.disconnect();
        }
    }
}
