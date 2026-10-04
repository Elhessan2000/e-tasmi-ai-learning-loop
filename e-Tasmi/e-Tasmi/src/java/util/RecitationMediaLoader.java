package util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Reads recitation audio bytes from Cloudinary URLs or local {@code /uploads/} paths. */
public final class RecitationMediaLoader {
    private static final Logger LOGGER = Logger.getLogger(RecitationMediaLoader.class.getName());

    private RecitationMediaLoader() {
    }

    public static byte[] readBytes(String mediaPath) {
        String normalized = trimToNull(mediaPath);
        if (normalized == null) {
            return null;
        }
        try {
            if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(normalized))
                        .timeout(Duration.ofSeconds(40))
                        .GET()
                        .build();
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body();
                }
                return null;
            }
            if (normalized.startsWith("/uploads/")) {
                String safePath = normalized.substring("/uploads/".length()).replace("..", "").replace("\\", "/");
                Path base = Paths.get(LocalFileUtil.getUploadsDir()).toAbsolutePath().normalize();
                Path target = base.resolve(safePath).normalize();
                if (!target.startsWith(base) || !Files.exists(target) || !Files.isRegularFile(target)) {
                    return null;
                }
                return Files.readAllBytes(target);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to read recitation media", ex);
            return null;
        }
        return null;
    }

    public static String fileName(String mediaPath, long recitationId) {
        String normalized = trimToNull(mediaPath);
        if (normalized == null) {
            return "recitation-" + recitationId + ".webm";
        }
        int slash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        String filename = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        filename = trimToNull(filename);
        return filename == null ? "recitation-" + recitationId + ".webm" : filename;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
