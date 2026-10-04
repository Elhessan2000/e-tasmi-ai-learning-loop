package model.service.quranpedia;

import util.JsonUtil;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Quranpedia catalogue metadata and ayah translations.
 * Learning Loop comparison text is read separately by {@link QuranpediaReferenceProvider}.
 */
public final class QuranpediaService {

    private static final Logger LOGGER = Logger.getLogger(QuranpediaService.class.getName());

    private final QuranpediaClient client = new QuranpediaClient();

    public List<MushafSummary> listMushafs() throws IOException {
        HttpResponse<String> response = client.get("/mushafs");
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Quranpedia mushafs HTTP " + response.statusCode());
        }
        Object parsed = parseBody(response.body());
        if (!(parsed instanceof List<?>)) {
            throw new IOException("Quranpedia mushafs response was not a list.");
        }
        List<MushafSummary> rows = new ArrayList<>();
        for (Object item : (List<?>) parsed) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) item;
            Integer id = asInt(row.get("id"));
            String name = asString(row.get("name"));
            String rawiName = null;
            Object rawi = row.get("rawi");
            if (rawi instanceof Map<?, ?>) {
                rawiName = asString(((Map<?, ?>) rawi).get("name"));
            }
            if (id != null && name != null && !name.isBlank()) {
                rows.add(new MushafSummary(id.intValue(), name, rawiName));
            }
        }
        return rows;
    }

    /**
     * {@code GET /translations/{surah}/{ayah}/{language}}.
     * Consumes {@code book.id}, {@code book.name}, and {@code translation-content}.
     */
    public List<AyahTranslation> translations(int surah, int ayah, String language) throws IOException {
        if (surah < 1 || surah > 114 || ayah < 1 || ayah > 286) {
            throw new IOException("surah or ayah is out of range");
        }
        if (language == null || !language.matches("[a-z]{2}")) {
            throw new IOException("language code is not a two-letter code");
        }
        String path = "/translations/" + surah + "/" + ayah + "/" + language;
        HttpResponse<String> response = client.get(path);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Quranpedia translations HTTP " + response.statusCode());
        }
        Object parsed = parseBody(response.body());
        if (!(parsed instanceof List<?>)) {
            throw new IOException("Quranpedia translations response was not a list.");
        }
        List<AyahTranslation> rows = new ArrayList<>();
        for (Object item : (List<?>) parsed) {
            if (!(item instanceof Map<?, ?>)) {
                continue;
            }
            Map<?, ?> row = (Map<?, ?>) item;
            Object book = row.get("book");
            if (!(book instanceof Map<?, ?>)) {
                continue;
            }
            Map<?, ?> bookMap = (Map<?, ?>) book;
            Integer bookId = asInt(bookMap.get("id"));
            String bookName = asString(bookMap.get("name"));
            String content = asString(row.get("translation-content"));
            if (bookId != null && content != null && !content.isBlank()) {
                rows.add(new AyahTranslation(bookId.intValue(), bookName == null ? "" : bookName, content));
            }
        }
        return rows;
    }

    public String healthJson(boolean live) {
        QuranpediaConfig cfg = QuranpediaConfig.load();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", Boolean.valueOf(cfg.isConfigured()));
        body.put("configured", Boolean.valueOf(cfg.isConfigured()));
        body.put("endpoint", cfg.isConfigured() ? cfg.getEndpoint() : "");
        body.put("authentication", "none");
        body.put("comparisonSource", "quranpedia_hafs_text");
        if (live) {
            body.put("liveProbe", liveProbe());
        }
        return JsonUtil.obj(body);
    }

    private Map<String, Object> liveProbe() {
        Map<String, Object> probe = new LinkedHashMap<>();
        if (!QuranpediaConfig.load().isConfigured()) {
            probe.put("liveProbeRan", Boolean.FALSE);
            probe.put("liveSkippedReason", "not_configured");
            return probe;
        }
        probe.put("liveProbeRan", Boolean.TRUE);
        try {
            List<MushafSummary> mushafs = listMushafs();
            probe.put("mushafsHttpStatus", Integer.valueOf(200));
            probe.put("mushafCount", Integer.valueOf(mushafs.size()));
            boolean hafs = false;
            int nameChars = 0;
            int rawiChars = 0;
            for (MushafSummary row : mushafs) {
                if (row.getId() == 1) {
                    hafs = true;
                    nameChars = row.getName().length();
                    rawiChars = row.getRawiName() == null ? 0 : row.getRawiName().length();
                }
            }
            probe.put("hafsMushafIdPresent", Boolean.valueOf(hafs));
            probe.put("hafsNameChars", Integer.valueOf(nameChars));
            probe.put("hafsRawiNameChars", Integer.valueOf(rawiChars));
            probe.put("consumedMushafFields", List.of("id", "name", "rawi.name"));
            LOGGER.info("Quranpedia mushafs count=" + mushafs.size() + " hafsPresent=" + hafs);
        } catch (IOException ex) {
            probe.put("mushafsHttpStatus", Integer.valueOf(0));
            probe.put("mushafError", "request_failed");
            LOGGER.log(Level.WARNING, "Quranpedia mushaf probe failed ({0})", ex.getClass().getSimpleName());
        }
        try {
            List<AyahTranslation> rows = translations(1, 1, "en");
            probe.put("translationHttpStatus", Integer.valueOf(200));
            probe.put("translationCount", Integer.valueOf(rows.size()));
            if (!rows.isEmpty()) {
                AyahTranslation first = rows.get(0);
                probe.put("firstBookId", Integer.valueOf(first.getBookId()));
                probe.put("firstBookNameChars", Integer.valueOf(first.getBookName().length()));
                probe.put("translationContentChars", Integer.valueOf(first.getContent().length()));
            }
            probe.put("consumedTranslationFields", List.of("book.id", "book.name", "translation-content"));
            LOGGER.info("Quranpedia translations/1/1/en count=" + rows.size());
        } catch (IOException ex) {
            probe.put("translationHttpStatus", Integer.valueOf(0));
            probe.put("translationError", "request_failed");
            LOGGER.log(Level.WARNING, "Quranpedia translation probe failed ({0})", ex.getClass().getSimpleName());
        }
        return probe;
    }

    private static Object parseBody(String body) throws IOException {
        try {
            return QuranpediaJson.parse(body);
        } catch (RuntimeException ex) {
            throw new IOException("Quranpedia response could not be parsed.");
        }
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        return null;
    }

    private static String asString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    public static final class MushafSummary {
        private final int id;
        private final String name;
        private final String rawiName;

        MushafSummary(int id, String name, String rawiName) {
            this.id = id;
            this.name = name;
            this.rawiName = rawiName;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getRawiName() {
            return rawiName;
        }
    }

    public static final class AyahTranslation {
        private final int bookId;
        private final String bookName;
        private final String content;

        AyahTranslation(int bookId, String bookName, String content) {
            this.bookId = bookId;
            this.bookName = bookName;
            this.content = content;
        }

        public int getBookId() {
            return bookId;
        }

        public String getBookName() {
            return bookName;
        }

        public String getContent() {
            return content;
        }
    }
}
