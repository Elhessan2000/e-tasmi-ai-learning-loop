package model.service.quran;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Fetches Quran chapters from Quran Foundation Content API (backend only).
 */
public final class QuranLibraryChaptersService {

    private static final Logger LOGGER = Logger.getLogger(QuranLibraryChaptersService.class.getName());

    private static final String CHAPTERS_PATH = "/content/api/v4/chapters";

    private final QuranFoundationClient client = new QuranFoundationClient();

    public List<Map<String, Object>> fetchChapters() throws IOException {
        if (!QuranFoundationConfig.load().isComplete()) {
            if (QuranBundledCatalog.isReady()) {
                LOGGER.log(Level.INFO, "Quran Foundation not configured — serving bundled chapter catalogue.");
                return QuranBundledCatalog.chapters();
            }
        }
        HttpResponse<String> resp = client.getJson(CHAPTERS_PATH);
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            LOGGER.log(Level.WARNING, "Quran Foundation chapters rejected: HTTP {0}", code);
            throw new IOException("Chapters HTTP " + code);
        }
        try {
            return QuranChaptersParse.toPublicChapterMaps(resp.body());
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to parse chapters payload");
            throw ex;
        }
    }
}
