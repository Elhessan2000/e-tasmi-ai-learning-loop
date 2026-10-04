package model.service.quran;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanitized verse rows from Quran Foundation {@code GET /content/api/v4/verses/by_chapter/:id}.
 * {@code translations} may yield {@code translationText}, optional secondary fields when two editions load, and optional
 * {@code translationFormatted} (sanitized HTML overlays when markup is present).
 * Optional {@code tafsirs}: {@code tafsirName}, {@code tafsirLanguageName}, {@code tafsirEditionResourceId},
 * {@code tafsirPreview} (plain, capped), {@code tafsirHtmlSafe} on single-verse fetches when enabled,
 * {@code tafsirPreviewTruncated}, {@code tafsirSnippetId}.
 * Regex-based — matches {@link QuranChaptersParse} style.
 */
public final class QuranVersesParse {

    private static final Logger LOGGER = Logger.getLogger(QuranVersesParse.class.getName());

    private static final Pattern STRING_FIELD = Pattern.compile(
            "\"([a-zA-Z0-9_]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern INT_FIELD = Pattern.compile(
            "\"([a-zA-Z0-9_]+)\"\\s*:\\s*(-?\\d+)");
    private static final Pattern NEXT_PAGE = Pattern.compile(
            "\"next_page\"\\s*:\\s*(null|-?\\d+)");
    /** Some gateways normalize JSON numbers to strings; camelCase appears in alternate shapes. */
    private static final Pattern NEXT_PAGE_QUOTED = Pattern.compile(
            "\"next_page\"\\s*:\\s*\"(-?\\d+)\"");
    private static final Pattern NEXT_PAGE_CAMEL = Pattern.compile(
            "\"nextPage\"\\s*:\\s*(null|-?\\d+)");
    private static final Pattern NEXT_PAGE_CAMEL_QUOTED = Pattern.compile(
            "\"nextPage\"\\s*:\\s*\"(-?\\d+)\"");
    private static final Pattern PAT_HTML_TAGS = Pattern.compile("<[^>]+>");

    /** Inline list / merged-chapter payloads: keep excerpts short. */
    private static final int TAFSIR_PLAIN_PREVIEW_MAX = 520;

    /** Single-verse (by-key) detail responses: larger plain excerpt after stripping HTML. */
    public static final int TAFSIR_PLAIN_FULL_MAX = 98_304;

    private static final int TRANSLATION_PLAIN_MAX = 24_576;

    private QuranVersesParse() {
    }

    public record VersesSlice(List<Map<String, Object>> verses, Map<String, Object> pagination) {
    }

    public static VersesSlice toSlice(String apiJsonBody) throws IOException {
        if (apiJsonBody == null || apiJsonBody.isBlank()) {
            throw new IOException("Empty verses response");
        }
        int keyIdx = apiJsonBody.indexOf("\"verses\"");
        if (keyIdx < 0) {
            throw new IOException("Missing verses array");
        }
        int bracket = apiJsonBody.indexOf('[', keyIdx);
        if (bracket < 0) {
            throw new IOException("Invalid verses array");
        }
        String inner = extractBracketContents(apiJsonBody, bracket);
        if (inner == null) {
            throw new IOException("Unterminated verses array");
        }
        List<String> objects = splitTopLevelJsonObjects(inner);
        List<Map<String, Object>> rows = new ArrayList<>(objects.size());
        for (String obj : objects) {
            Map<String, Object> pub = verseToPublicRow(obj, TAFSIR_PLAIN_PREVIEW_MAX, false);
            if (pub != null && !pub.isEmpty()) {
                rows.add(pub);
            }
        }
        Map<String, Object> pagination = paginationToPublicMap(apiJsonBody);
        return new VersesSlice(rows, pagination);
    }

    /**
     * Maps {@code GET /verses/by_key/...} style JSON ({@code "verse":\s*\{...\}}) into the same verse map shape
     * as merged-chapter rows, using a generous tafsir plain cap.
     */
    public static Map<String, Object> verseByKeyResponseToPublicRow(String apiJsonBody) throws IOException {
        if (apiJsonBody == null || apiJsonBody.isBlank()) {
            throw new IOException("Empty verse-by-key response");
        }
        String verseObj = extractJsonObjectAfterField(apiJsonBody, "\"verse\"");
        Map<String, Object> row = verseToPublicRow(verseObj, TAFSIR_PLAIN_FULL_MAX, true);
        if (row == null || row.isEmpty()) {
            throw new IOException("No verse parsed");
        }
        return row;
    }

    private static String extractJsonObjectAfterField(String apiJsonBody, String fieldQuoted) throws IOException {
        int keyIdx = apiJsonBody.indexOf(fieldQuoted);
        if (keyIdx < 0) {
            throw new IOException("Missing " + fieldQuoted);
        }
        int colon = apiJsonBody.indexOf(':', keyIdx + fieldQuoted.length());
        if (colon < 0) {
            throw new IOException("Invalid " + fieldQuoted);
        }
        int braceOpen = apiJsonBody.indexOf('{', colon);
        if (braceOpen < 0) {
            throw new IOException("Missing verse object");
        }
        int end = closingBraceIndex(apiJsonBody, braceOpen);
        if (end < 0) {
            throw new IOException("Unterminated verse object");
        }
        return apiJsonBody.substring(braceOpen, end + 1);
    }

    private static int closingBraceIndex(String s, int openBraceIdx) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = openBraceIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String extractBracketContents(String s, int openIdx) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return s.substring(openIdx + 1, i);
                }
            }
        }
        return null;
    }

    private static List<String> splitTopLevelJsonObjects(String arrInnerTrimmed) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < arrInnerTrimmed.length(); i++) {
            char c = arrInnerTrimmed.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(arrInnerTrimmed.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return out;
    }

    /**
     * Structural verse fields ({@code id}, keys, numbering) must be read from a slice that excludes nested arrays
     * ({@code words}, optional {@code translations}, {@code tafsirs}), otherwise {@link #firstLong} may match
     * snippet row ids ({@code tafsirs[].id}) or unrelated ids before the verse block ends.
     */
    private static String verseStructuralHead(String verseObject) {
        if (verseObject == null || verseObject.isEmpty()) {
            return "";
        }
        int cut = verseObject.length();
        String[] keys = {"\"translations\"", "\"tafsirs\"", "\"words\""};
        for (String key : keys) {
            int idx = verseObject.indexOf(key);
            if (idx >= 0 && idx < cut) {
                cut = idx;
            }
        }
        return verseObject.substring(0, cut);
    }

    /**
     * Slice before the {@code words} array so {@link #firstString} resolves verse-level {@code text_*} keys,
     * not duplicates inside {@code words[]}.
     */
    private static String verseScriptPrefix(String verseObject) {
        if (verseObject == null || verseObject.isEmpty()) {
            return "";
        }
        int w = verseObject.indexOf("\"words\"");
        if (w < 0) {
            return verseObject;
        }
        return verseObject.substring(0, w);
    }

    private static String joinWordGlyphsFromWordsArray(String verseObject) {
        if (verseObject == null || verseObject.isBlank()) {
            return null;
        }
        int idx = verseObject.indexOf("\"words\"");
        if (idx < 0) {
            return null;
        }
        int openBracket = verseObject.indexOf('[', idx);
        if (openBracket < 0) {
            return null;
        }
        String inner = extractBracketContents(verseObject, openBracket);
        if (inner == null) {
            return null;
        }
        List<String> objs = splitTopLevelJsonObjects(inner.trim());
        if (objs.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (String wo : objs) {
            String g = firstNonBlank(
                    unescapeJsonString(firstString(wo, "text_uthmani")),
                    unescapeJsonString(firstString(wo, "text_uthmani_simple")),
                    unescapeJsonString(firstString(wo, "text_imlaei")),
                    unescapeJsonString(firstString(wo, "text_imlaei_simple")),
                    unescapeJsonString(firstString(wo, "code_v1")),
                    unescapeJsonString(firstString(wo, "code_v2")),
                    unescapeJsonString(firstString(wo, "text_qpc_hafs")));
            if (g == null || g.isBlank()) {
                continue;
            }
            g = g.trim();
            if (sb.length() > 0) {
                sb.append('\u0020');
            }
            sb.append(g);
        }
        return sb.length() == 0 ? null : sb.toString().trim().replaceAll("\\s+", "\u0020");
    }

    private static PlainExcerpt htmlFragmentToArabicReadable(String htmlRaw, int maxLen) {
        if (htmlRaw == null || htmlRaw.isBlank()) {
            return null;
        }
        return compactHtmlToPlain(htmlRaw, maxLen);
    }

    private static Map<String, Object> verseToPublicRow(String verseObject, int tafsirPlainMax,
                                                        boolean sanitizeTafsirHtml) {
        String meta = verseStructuralHead(verseObject);
        Long globalVersePk = firstLong(meta, "id");
        Long verseNumber = firstLong(meta, "verse_number");
        Long rowId = (globalVersePk != null && globalVersePk >= 1) ? globalVersePk : verseNumber;
        if (rowId == null || rowId < 1) {
            return null;
        }
        Long chapterId = firstLong(meta, "chapter_id");
        Long verseIndex = firstLong(meta, "verse_index");
        String verseKey = unescapeJsonString(firstString(meta, "verse_key"));
        String script = verseScriptPrefix(verseObject);
        String uthmani = unescapeJsonString(firstString(script, "text_uthmani"));
        String uthmaniSimple = unescapeJsonString(firstString(script, "text_uthmani_simple"));
        String imlaeiSimple = unescapeJsonString(firstString(script, "text_imlaei_simple"));
        String textImlaei = unescapeJsonString(firstString(script, "text_imlaei"));
        String textIndopak = unescapeJsonString(firstString(script, "text_indopak"));
        String textQpc = unescapeJsonString(firstString(script, "text_qpc_hafs"));
        String codeV1 = unescapeJsonString(firstString(script, "code_v1"));
        String uthmaniTajweedRaw = firstString(script, "text_uthmani_tajweed");
        PlainExcerpt tajweedPlain = htmlFragmentToArabicReadable(
                uthmaniTajweedRaw == null ? null : unescapeJsonString(uthmaniTajweedRaw), 16_384);

        Long pageNumber = firstLong(meta, "page_number");
        Long juzNumber = firstLong(meta, "juz_number");

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", rowId);
        if (chapterId != null) {
            m.put("chapterId", chapterId);
        }
        if (verseNumber != null) {
            m.put("verseNumber", verseNumber);
        }
        if (verseKey != null && !verseKey.isBlank()) {
            m.put("verseKey", verseKey);
        }
        if (verseIndex != null) {
            m.put("verseIndex", verseIndex);
        }

        String fromTajweed = tajweedPlain == null ? null : tajweedPlain.text;
        String displayArabic = firstNonBlank(
                uthmani,
                uthmaniSimple,
                imlaeiSimple,
                textImlaei,
                textIndopak,
                textQpc,
                codeV1,
                fromTajweed);
        if (displayArabic == null || displayArabic.isBlank()) {
            displayArabic = joinWordGlyphsFromWordsArray(verseObject);
        }
        m.put("textArabic", nullToEmpty(displayArabic));
        m.put("textUthmani", nullToEmpty(uthmani));
        if ((displayArabic == null || displayArabic.isBlank()) && LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE,
                    "Unresolved Arabic after parse: verseKey={0}, hasWordsArray={1}",
                    new Object[]{verseKey, Boolean.valueOf(verseObject.contains("\"words\""))});
        }

        List<TranslationPick> trRows = ayahTranslationsOrdered(verseObject);
        TranslationPick primary = choosePrimaryTranslation(trRows);
        if (primary != null && primary.plainText != null && !primary.plainText.isBlank()) {
            m.put("translationText", primary.plainText);
            if (primary.resourceId != null) {
                m.put("translationResourceId", primary.resourceId);
            }
        }
        TranslationPick secondary = chooseSecondaryTranslation(trRows, primary);
        if (secondary != null && secondary.plainText != null && !secondary.plainText.isBlank()) {
            m.put("translationSecondaryText", secondary.plainText);
            if (secondary.resourceId != null) {
                m.put("translationSecondaryResourceId", secondary.resourceId);
            }
        }
        putTranslationFormatted(m, trRows);

        TafsirPick taf = firstAyahTafsir(verseObject, tafsirPlainMax);
        if (taf != null && taf.previewPlain != null && !taf.previewPlain.isBlank()) {
            m.put("tafsirPreview", taf.previewPlain);
        }
        if (taf != null && taf.resourceName != null && !taf.resourceName.isBlank()) {
            m.put("tafsirName", taf.resourceName);
        }
        if (taf != null && taf.languageName != null && !taf.languageName.isBlank()) {
            m.put("tafsirLanguageName", taf.languageName);
        }
        if (taf != null && taf.resourceEditionId != null) {
            m.put("tafsirEditionResourceId", taf.resourceEditionId);
        }
        if (taf != null && Boolean.TRUE.equals(taf.previewTruncated)) {
            m.put("tafsirPreviewTruncated", Boolean.TRUE);
        }
        if (taf != null && taf.snippetRowId != null) {
            m.put("tafsirSnippetId", taf.snippetRowId);
        }
        if (sanitizeTafsirHtml && taf != null && taf.htmlRaw != null && !taf.htmlRaw.isBlank()) {
            String safe = QuranTafsirHtmlSanitize.sanitize(taf.htmlRaw);
            if (safe != null && !safe.isBlank()) {
                m.put("tafsirHtmlSafe", safe);
            }
        }

        if (pageNumber != null) {
            m.put("pageNumber", pageNumber);
        }
        if (juzNumber != null) {
            m.put("juzNumber", juzNumber);
        }
        return m;
    }

    private static TranslationPick choosePrimaryTranslation(List<TranslationPick> rows) {
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        for (TranslationPick p : rows) {
            if (p.plainText != null && !p.plainText.isBlank()) {
                return p;
            }
        }
        return rows.get(0);
    }

    private static TranslationPick chooseSecondaryTranslation(List<TranslationPick> rows,
                                                              TranslationPick primary) {
        if (rows == null || rows.size() < 2) {
            return null;
        }
        for (TranslationPick p : rows) {
            if (primary != null && Objects.equals(primary.resourceId, p.resourceId)) {
                continue;
            }
            if (p.plainText != null && !p.plainText.isBlank()) {
                return p;
            }
        }
        return null;
    }

    private static void putTranslationFormatted(Map<String, Object> m, List<TranslationPick> rows) {
        List<Map<String, Object>> overlays = new ArrayList<>();
        for (TranslationPick p : rows) {
            if (p.rawHtml == null || !p.rawHtml.contains("<")) {
                continue;
            }
            String safe = QuranTafsirHtmlSanitize.sanitize(p.rawHtml);
            if (safe == null || safe.isBlank()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            if (p.resourceId != null) {
                row.put("resourceId", p.resourceId);
            }
            row.put("htmlSafe", safe);
            overlays.add(row);
        }
        if (!overlays.isEmpty()) {
            m.put("translationFormatted", overlays);
        }
    }

    private static List<TranslationPick> ayahTranslationsOrdered(String verseObject) {
        List<TranslationPick> out = new ArrayList<>();
        int idx = verseObject.indexOf("\"translations\"");
        if (idx < 0) {
            return out;
        }
        int openBracket = verseObject.indexOf('[', idx);
        if (openBracket < 0) {
            return out;
        }
        String inner = extractBracketContents(verseObject, openBracket);
        if (inner == null) {
            return out;
        }
        String trimmedInner = inner.trim();
        if (trimmedInner.isEmpty()) {
            return out;
        }
        List<String> objs = splitTopLevelJsonObjects(trimmedInner);
        for (String obj : objs) {
            String rawHtml = unescapeJsonString(firstString(obj, "text"));
            Long resourceId = firstLong(obj, "resource_id");
            PlainExcerpt excerpt = compactHtmlToPlain(rawHtml, TRANSLATION_PLAIN_MAX);
            String plain = excerpt == null ? null : excerpt.text;
            if ((plain == null || plain.isBlank()) && (resourceId == null || rawHtml == null || rawHtml.isBlank())) {
                continue;
            }
            if (!out.isEmpty()) {
                TranslationPick prev = out.get(out.size() - 1);
                if (Objects.equals(prev.resourceId, resourceId)) {
                    continue;
                }
            }
            out.add(new TranslationPick(resourceId, rawHtml != null ? rawHtml : "", plain));
        }
        return out;
    }

    private static final class TranslationPick {
        final Long resourceId;
        /** Original HTML excerpt from Quran Foundation when present (may contain footnote markup). */
        final String rawHtml;
        final String plainText;

        TranslationPick(Long resourceId, String rawHtml, String plainText) {
            this.resourceId = resourceId;
            this.rawHtml = rawHtml;
            this.plainText = plainText;
        }
    }

    private static final class TafsirPick {
        final Long snippetRowId;
        final Long resourceEditionId;
        final String resourceName;
        final String languageName;
        final String htmlRaw;
        final String previewPlain;
        final Boolean previewTruncated;

        TafsirPick(Long snippetRowId, Long resourceEditionId, String resourceName, String languageName,
                String htmlRaw, String previewPlain, Boolean previewTruncated) {
            this.snippetRowId = snippetRowId;
            this.resourceEditionId = resourceEditionId;
            this.resourceName = resourceName;
            this.languageName = languageName;
            this.htmlRaw = htmlRaw;
            this.previewPlain = previewPlain;
            this.previewTruncated = previewTruncated;
        }
    }

    private static TafsirPick firstAyahTafsir(String verseObject, int tafsirPlainMax) {
        int idx = verseObject.indexOf("\"tafsirs\"");
        if (idx < 0) {
            return null;
        }
        int openBracket = verseObject.indexOf('[', idx);
        if (openBracket < 0) {
            return null;
        }
        String inner = extractBracketContents(verseObject, openBracket);
        if (inner == null) {
            return null;
        }
        String trimmedInner = inner.trim();
        if (trimmedInner.isEmpty()) {
            return null;
        }
        List<String> objs = splitTopLevelJsonObjects(trimmedInner);
        if (objs.isEmpty()) {
            return null;
        }
        String first = objs.get(0);
        String name = unescapeJsonString(firstString(first, "name"));
        String lang = unescapeJsonString(firstString(first, "language_name"));
        Long editionId = firstLong(first, "resource_id");
        String html = unescapeJsonString(firstString(first, "text"));
        Long snippetRowId = firstLong(first, "id");
        PlainExcerpt preview = compactHtmlToPlain(html, tafsirPlainMax);
        boolean hasName = name != null && !name.isBlank();
        boolean hasLang = lang != null && !lang.isBlank();
        boolean hasPreview = preview != null && preview.text != null && !preview.text.isBlank();
        boolean hasHtmlBody = html != null && !html.isBlank();
        if (!hasName && !hasLang && !hasPreview && !hasHtmlBody && snippetRowId == null && editionId == null) {
            return null;
        }
        Boolean trunc = preview != null && preview.truncated ? Boolean.TRUE : null;
        return new TafsirPick(snippetRowId, editionId, name, lang,
                html, preview == null ? null : preview.text, trunc);
    }

    private static final class PlainExcerpt {
        final String text;
        final boolean truncated;

        PlainExcerpt(String text, boolean truncated) {
            this.text = text;
            this.truncated = truncated;
        }
    }

    /** Strip markup and collapse whitespace for a student-facing excerpt (HTML fragments from QF). */
    private static PlainExcerpt compactHtmlToPlain(String raw, int maxLen) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = PAT_HTML_TAGS.matcher(raw).replaceAll(" ");
        s = s.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#160;", " ");
        s = s.replaceAll("\\s+", " ").trim();
        if (s.isEmpty()) {
            return null;
        }
        if (maxLen > 16 && s.length() > maxLen) {
            return new PlainExcerpt(s.substring(0, maxLen - 1).trim() + '\u2026', true);
        }
        return new PlainExcerpt(s, false);
    }

    private static String firstNonBlank(String... parts) {
        if (parts == null) {
            return null;
        }
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                return p;
            }
        }
        return null;
    }

    private static Long paginationLong(String paginationObjectBlock, String snakeKey, String camelKey) {
        Long v = firstLong(paginationObjectBlock, snakeKey);
        if (v != null) {
            return v;
        }
        return firstLong(paginationObjectBlock, camelKey);
    }

    /**
     * Resolves {@code next_page} / {@code nextPage} whether numeric, null, or string-encoded.
     */
    private static Long extractNextPage(String paginationObjectBlock) {
        Matcher nm = NEXT_PAGE.matcher(paginationObjectBlock);
        if (nm.find()) {
            String raw = nm.group(1);
            if (!"null".equals(raw)) {
                try {
                    return Long.parseLong(raw);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
        Matcher nq = NEXT_PAGE_QUOTED.matcher(paginationObjectBlock);
        if (nq.find()) {
            try {
                return Long.parseLong(nq.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Matcher nc = NEXT_PAGE_CAMEL.matcher(paginationObjectBlock);
        if (nc.find()) {
            String raw = nc.group(1);
            if (!"null".equals(raw)) {
                try {
                    return Long.parseLong(raw);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
            return null;
        }
        Matcher ncq = NEXT_PAGE_CAMEL_QUOTED.matcher(paginationObjectBlock);
        if (ncq.find()) {
            try {
                return Long.parseLong(ncq.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Map<String, Object> paginationToPublicMap(String apiJsonBody) throws IOException {
        int keyIdx = apiJsonBody.indexOf("\"pagination\"");
        if (keyIdx < 0) {
            return Map.of();
        }
        int brace = apiJsonBody.indexOf('{', keyIdx);
        if (brace < 0) {
            throw new IOException("Invalid pagination object");
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        int end = -1;
        for (int i = brace; i < apiJsonBody.length(); i++) {
            char c = apiJsonBody.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    end = i;
                    break;
                }
            }
        }
        if (end < 0) {
            throw new IOException("Unterminated pagination object");
        }
        String block = apiJsonBody.substring(brace, end + 1);
        Long perPage = paginationLong(block, "per_page", "perPage");
        Long current = paginationLong(block, "current_page", "currentPage");
        Long totalPages = paginationLong(block, "total_pages", "totalPages");
        Long totalRecords = paginationLong(block, "total_records", "totalRecords");
        Long nextPage = extractNextPage(block);

        Map<String, Object> m = new LinkedHashMap<>();
        if (current != null) {
            m.put("currentPage", current);
        }
        if (perPage != null) {
            m.put("perPage", perPage);
        }
        if (totalPages != null) {
            m.put("totalPages", totalPages);
        }
        if (totalRecords != null) {
            m.put("totalRecords", totalRecords);
        }
        if (nextPage != null) {
            m.put("nextPage", nextPage);
        }
        return m;
    }

    private static String firstString(String obj, String key) {
        Matcher m = STRING_FIELD.matcher(obj);
        while (m.find()) {
            if (key.equals(m.group(1))) {
                return m.group(2);
            }
        }
        return null;
    }

    private static Long firstLong(String obj, String key) {
        Matcher m = INT_FIELD.matcher(obj);
        while (m.find()) {
            if (key.equals(m.group(1))) {
                try {
                    return Long.parseLong(m.group(2));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    static String unescapeJsonString(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' && i + 1 < raw.length()) {
                char n = raw.charAt(i + 1);
                switch (n) {
                    case '"':
                    case '\\':
                    case '/':
                        sb.append(n);
                        i++;
                        continue;
                    case 'n':
                        sb.append('\n');
                        i++;
                        continue;
                    case 'r':
                        sb.append('\r');
                        i++;
                        continue;
                    case 't':
                        sb.append('\t');
                        i++;
                        continue;
                    case 'u':
                        if (i + 5 < raw.length()) {
                            try {
                                int cp = Integer.parseInt(raw.substring(i + 2, i + 6), 16);
                                sb.append((char) cp);
                                i += 5;
                                continue;
                            } catch (NumberFormatException ignored) {
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
