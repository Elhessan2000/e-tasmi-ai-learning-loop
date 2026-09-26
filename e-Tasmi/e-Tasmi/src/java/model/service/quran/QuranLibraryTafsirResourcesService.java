package model.service.quran;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tafsir resource catalogue from Quran Foundation (backend only).
 */
public final class QuranLibraryTafsirResourcesService {

    private static final Logger LOGGER = Logger.getLogger(QuranLibraryTafsirResourcesService.class.getName());

    private static final String TAFSIRS_RESOURCES_PATH = "/content/api/v4/resources/tafsirs";

    private final QuranFoundationClient client = new QuranFoundationClient();

    public List<Map<String, Object>> fetchTafsirResources(String languageParam) throws IOException {
        String language = sanitizeLanguage(languageParam);
        String path = TAFSIRS_RESOURCES_PATH;
        if (language != null) {
            path += "?language=" + URLEncoder.encode(language, StandardCharsets.UTF_8);
        }
        HttpResponse<String> resp = client.getJson(path);
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            LOGGER.log(Level.WARNING, "Quran Foundation tafsir resources rejected: HTTP {0}", Integer.valueOf(code));
            throw new IOException("Tafsir resources HTTP " + code);
        }
        try {
            return QuranTafsirResourcesParse.toPublicMaps(resp.body());
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to parse tafsir resources");
            throw ex;
        }
    }

    public static String sanitizeLanguage(String raw) throws IOException {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (!s.matches("[a-z][a-z0-9_-]{1,11}")) {
            throw new IOException("invalid_language_param");
        }
        return s;
    }
}
