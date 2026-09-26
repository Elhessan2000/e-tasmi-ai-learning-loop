package model.service.quran;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verses-by-chapter from Quran Foundation (backend only). Optionally merges pagination server-side.
 */
public final class QuranLibraryVersesService {

    private static final Logger LOGGER = Logger.getLogger(QuranLibraryVersesService.class.getName());

    /** Upstream route per public Content API docs. */
    static final String VERSES_BY_CHAPTER_PREFIX = "/content/api/v4/verses/by_chapter/";
    /** Single ayah: {@code verse_key} in path (colon URL-encoded per query rules). */
    static final String VERSES_BY_KEY_PREFIX = "/content/api/v4/verses/by_key/";

    private static final int DEFAULT_PAGE_CHUNK = 64;
    /** Enough for longest surah at default chunk even if {@code next_page} were missing from upstream JSON. */
    private static final int MAX_UPSTREAM_PAGES = 48;

    private static final Pattern VERSE_KEY_PATTERN = Pattern.compile("^(\\d{1,3}):(\\d{1,3})$");

    /**
     * Quran Foundation may omit verse-level script when {@code translations} / {@code tafsirs} query params are set.
     * Explicit {@code fields} / {@code word_fields} request verse + word script per Content API field reference.
     */
    private static final String QF_VERSES_FIELDS =
            "id,chapter_id,verse_number,verse_key,verse_index,page_number,juz_number,hizb_number,rub_el_hizb_number,"
                    + "text_uthmani,text_uthmani_simple,text_imlaei,text_imlaei_simple,text_indopak,text_qpc_hafs,"
                    + "text_uthmani_tajweed,code_v1";
    private static final String QF_VERSES_WORD_FIELDS =
            "text_uthmani,text_uthmani_simple,text_imlaei,text_imlaei_simple,code_v1,code_v2";

    private final QuranFoundationClient client = new QuranFoundationClient();

    /**
     * Fetches one upstream page ({@code page} / {@code perPage} forwarded as query {@code page} / {@code per_page}).
     * Optional {@code translationsCsv} forwards as comma-separated translation resource IDs (e.g. {@code 131}).
     * Optional {@code tafsirsCsv} forwards as {@code tafsirs} resource IDs.
     */
    public QuranVersesParse.VersesSlice fetchPage(int chapterNumber, int page, int perPage,
                                                 String translationsCsv, String tafsirsCsv) throws IOException {
        validateChapter(chapterNumber);
        validatePaging(page, perPage);
        String path = upstreamPath(chapterNumber, page, perPage, translationsCsv, tafsirsCsv);
        HttpResponse<String> resp = client.getJson(path);
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            LOGGER.log(Level.WARNING, "Quran Foundation verses rejected: chapter={0} HTTP {1}",
                    new Object[]{Integer.valueOf(chapterNumber), Integer.valueOf(code)});
            throw new IOException("Verses HTTP " + code);
        }
        QuranVersesParse.VersesSlice slice = QuranVersesParse.toSlice(resp.body());
        if (slice.verses().isEmpty()) {
            throw new IOException("No verses parsed");
        }
        return slice;
    }

    /**
     * Fetches all verses for the chapter by following {@code pagination.next_page} until exhausted.
     *
     * @param translationsCsv optional comma-separated translation resource IDs for upstream {@code translations};
     * @param tafsirsCsv optional comma-separated tafsir resource IDs for upstream {@code tafsirs};
     */
    public QuranVersesParse.VersesSlice fetchMergedChapter(int chapterNumber, String translationsCsv,
                                                          String tafsirsCsv)
            throws IOException {
        validateChapter(chapterNumber);
        if (!QuranFoundationConfig.load().isComplete()) {
            QuranVersesParse.VersesSlice bundled = QuranBundledCatalog.versesForChapter(chapterNumber);
            if (bundled != null) {
                LOGGER.log(Level.INFO, "Quran Foundation not configured — serving bundled ayahs for chapter {0}",
                        chapterNumber);
                return bundled;
            }
            throw new IOException("Bundled ayahs unavailable for chapter " + chapterNumber);
        }
        List<Map<String, Object>> all = new ArrayList<>();
        Map<String, Object> lastPagination = new LinkedHashMap<>();
        long pageIdx = 1L;
        for (int hops = 0; hops < MAX_UPSTREAM_PAGES; hops++) {
            String path = upstreamPath(chapterNumber, (int) pageIdx, DEFAULT_PAGE_CHUNK, translationsCsv, tafsirsCsv);
            HttpResponse<String> resp = client.getJson(path);
            int code = resp.statusCode();
            if (code < 200 || code >= 300) {
                LOGGER.log(Level.WARNING, "Quran Foundation verses merge rejected: chapter={0} HTTP {1}",
                        new Object[]{Integer.valueOf(chapterNumber), Integer.valueOf(code)});
                throw new IOException("Verses HTTP " + code);
            }
            QuranVersesParse.VersesSlice slice = QuranVersesParse.toSlice(resp.body());
            all.addAll(slice.verses());
            lastPagination.clear();
            lastPagination.putAll(slice.pagination());

            Number nextRaw = slice.pagination().get("nextPage") instanceof Number
                    ? (Number) slice.pagination().get("nextPage")
                    : null;
            if (nextRaw == null) {
                break;
            }
            long next = nextRaw.longValue();
            if (next <= pageIdx || next > 10_000) {
                break;
            }
            pageIdx = next;
            Number totalRecords = slice.pagination().get("totalRecords") instanceof Number
                    ? (Number) slice.pagination().get("totalRecords")
                    : null;
            if (totalRecords != null && totalRecords.longValue() > 0
                    && all.size() >= totalRecords.longValue()) {
                break;
            }
        }
        if (all.isEmpty()) {
            throw new IOException("No verses parsed");
        }
        fillShortMergedChapter(chapterNumber, translationsCsv, tafsirsCsv, all, lastPagination);
        sortVersesByNumber(all);
        return new QuranVersesParse.VersesSlice(all, Map.copyOf(lastPagination));
    }

    /**
     * If upstream pagination hints {@code total_records} but merge stopped early (parser missed {@code next_page},
     * etc.), fetch additional pages and append missing ayahs by {@code verseKey}.
     */
    private void fillShortMergedChapter(int chapterNumber, String translationsCsv, String tafsirsCsv,
                                       List<Map<String, Object>> all, Map<String, Object> lastPagination)
            throws IOException {
        Object trRaw = lastPagination.get("totalRecords");
        long expected = trRaw instanceof Number ? ((Number) trRaw).longValue() : -1L;
        if (expected <= 0 || all.size() >= expected) {
            return;
        }
        LOGGER.log(Level.WARNING,
                "Quran verses merge shortfall: chapter={0} loaded={1} expected_total_records={2}; fetching remaining pages",
                new Object[]{Integer.valueOf(chapterNumber), Integer.valueOf(all.size()), Long.valueOf(expected)});
        Set<String> seen = verseKeys(all);
        long per = DEFAULT_PAGE_CHUNK;
        Object pp = lastPagination.get("perPage");
        if (pp instanceof Number && ((Number) pp).longValue() >= 1) {
            per = Math.min(286L, ((Number) pp).longValue());
        }
        int perPageInt = (int) per;
        for (int p = 1; p <= MAX_UPSTREAM_PAGES && seen.size() < expected; p++) {
            QuranVersesParse.VersesSlice slice = fetchPage(chapterNumber, p, perPageInt, translationsCsv, tafsirsCsv);
            if (slice.verses().isEmpty()) {
                break;
            }
            for (Map<String, Object> row : slice.verses()) {
                Object vk = row.get("verseKey");
                if (vk == null) {
                    continue;
                }
                String key = String.valueOf(vk);
                if (seen.add(key)) {
                    all.add(row);
                }
            }
            if (slice.verses().size() < perPageInt) {
                break;
            }
        }
    }

    private static Set<String> verseKeys(List<Map<String, Object>> rows) {
        Set<String> seen = new HashSet<>();
        for (Map<String, Object> row : rows) {
            Object vk = row.get("verseKey");
            if (vk != null) {
                seen.add(String.valueOf(vk));
            }
        }
        return seen;
    }

    private static void sortVersesByNumber(List<Map<String, Object>> rows) {
        rows.sort(Comparator.comparingInt(QuranLibraryVersesService::verseRowSortKey));
    }

    private static int verseRowSortKey(Map<String, Object> m) {
        Object vn = m.get("verseNumber");
        if (vn instanceof Number) {
            int i = ((Number) vn).intValue();
            return i > 0 ? i : 99999;
        }
        return 99999;
    }

    /**
     * One verse by {@code verse_key} (e.g. {@code 2:255}). When {@code tafsirsCsv} is set, asks upstream for
     * {@code tafsir_fields=text} so HTML bodies are useful for full plain-text extraction server-side.
     */
    public Map<String, Object> fetchVerseByKey(String verseKey, String translationsCsv, String tafsirsCsv)
            throws IOException {
        String key = sanitizeVerseKey(verseKey);
        StringBuilder sb = new StringBuilder(VERSES_BY_KEY_PREFIX)
                .append(URLEncoder.encode(key, StandardCharsets.UTF_8));
        char sep = '?';
        if (translationsCsv != null && !translationsCsv.isBlank()) {
            sb.append(sep).append("translations=").append(encodeCsvForQuery(translationsCsv));
            sep = '&';
        }
        if (tafsirsCsv != null && !tafsirsCsv.isBlank()) {
            sb.append(sep).append("tafsirs=").append(encodeCsvForQuery(tafsirsCsv))
                    .append("&tafsir_fields=text");
            sep = '&';
        }
        sb.append(sep).append("fields=").append(URLEncoder.encode(QF_VERSES_FIELDS, StandardCharsets.UTF_8));
        sb.append("&word_fields=").append(URLEncoder.encode(QF_VERSES_WORD_FIELDS, StandardCharsets.UTF_8));
        HttpResponse<String> resp = client.getJson(sb.toString());
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            LOGGER.log(Level.WARNING, "Quran Foundation verse-by-key rejected: key={0} HTTP {1}",
                    new Object[]{key, Integer.valueOf(code)});
            throw new IOException("Verse by key HTTP " + code);
        }
        return QuranVersesParse.verseByKeyResponseToPublicRow(resp.body());
    }

    public static void validateChapter(int chapterNumber) throws IOException {
        if (chapterNumber < 1 || chapterNumber > 114) {
            throw new IOException("chapter out of range");
        }
    }

    private static void validatePaging(int page, int perPage) throws IOException {
        if (page < 1) {
            throw new IOException("page must be >= 1");
        }
        if (perPage < 1 || perPage > 286) {
            throw new IOException("per_page out of range");
        }
    }

    private static String upstreamPath(int chapterNumber, int page, int perPage,
                                       String translationsCsv, String tafsirsCsv) {
        StringBuilder sb = new StringBuilder();
        sb.append(VERSES_BY_CHAPTER_PREFIX).append(chapterNumber)
                .append("?page=").append(page).append("&per_page=").append(perPage);
        sb.append("&fields=").append(URLEncoder.encode(QF_VERSES_FIELDS, StandardCharsets.UTF_8));
        sb.append("&word_fields=").append(URLEncoder.encode(QF_VERSES_WORD_FIELDS, StandardCharsets.UTF_8));
        if (translationsCsv != null && !translationsCsv.isBlank()) {
            sb.append("&translations=").append(encodeCsvForQuery(translationsCsv));
        }
        if (tafsirsCsv != null && !tafsirsCsv.isBlank()) {
            sb.append("&tafsirs=").append(encodeCsvForQuery(tafsirsCsv));
        }
        return sb.toString();
    }

    public static String sanitizeTranslationsCsv(String raw) throws IOException {
        return sanitizeCommaSeparatedResourceCsv(raw, "translations");
    }

    /** Comma-separated tafsir resource IDs for upstream verse requests. */
    public static String sanitizeTafsirsCsv(String raw) throws IOException {
        return sanitizeCommaSeparatedResourceCsv(raw, "tafsirs");
    }

    /**
     * Normalizes {@code chapter:verse} (no leading zeros). Chapter 1–114; verse 1–286 (loose upper bound).
     */
    public static String sanitizeVerseKey(String raw) throws IOException {
        if (raw == null || raw.isBlank()) {
            throw new IOException("verse_key required");
        }
        String compact = raw.trim().replaceAll("\\s+", "");
        var m = VERSE_KEY_PATTERN.matcher(compact);
        if (!m.matches()) {
            throw new IOException("invalid verse_key");
        }
        int chapter = Integer.parseInt(m.group(1));
        int verseNum = Integer.parseInt(m.group(2));
        if (chapter < 1 || chapter > 114 || verseNum < 1 || verseNum > 286) {
            throw new IOException("verse_key out of range");
        }
        validateChapter(chapter);
        return chapter + ":" + verseNum;
    }

    private static String sanitizeCommaSeparatedResourceCsv(String raw, String label) throws IOException {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.length() > 64) {
            throw new IOException(label + " param too long");
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || c == ',';
            if (!ok) {
                throw new IOException("invalid " + label + " param");
            }
        }
        return s;
    }

    private static String encodeCsvForQuery(String csvDigitsAndCommas) {
        return URLEncoder.encode(csvDigitsAndCommas, StandardCharsets.UTF_8);
    }
}
