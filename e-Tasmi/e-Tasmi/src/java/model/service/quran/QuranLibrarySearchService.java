package model.service.quran;

import java.io.IOException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Proxies Quran Foundation Search API ({@code /search/api/v1}) using OAuth scope {@code search} (combined with {@code content} via {@link QuranFoundationConfig#oauthScopeForTokenRequest()} unless overridden).
 */
public final class QuranLibrarySearchService {

    private static final Logger LOGGER = Logger.getLogger(QuranLibrarySearchService.class.getName());

    /** Quick command-bar style search (mixed navigation + verses). */
    private static final int MAX_QUERY_LEN = 180;
    /** Total JSON body cap returned to browsers. */
    public static final int MAX_RESPONSE_BODY_CHARS = 400_000;

    private static final Pattern SAFE_QUERY_CHAR = Pattern.compile("^[\\p{L}\\p{M}\\p{N}\\s\\-_'´`.,!?;:()]+$");

    private final QuranFoundationClient client = new QuranFoundationClient();

    /** {@code GET /search?mode=quick&query=…} returning upstream status + capped body. */
    public HttpResponse<String> fetchQuickRaw(String rawQuery) throws IOException {
        String sanitized = sanitizeQuickQuery(rawQuery);
        QuranFoundationConfig cfg = QuranFoundationConfig.load();
        String base = cfg.resolveSearchApiBaseNormalized();
        if (base == null || base.isBlank()) {
            throw new IOException("search api base unresolved; set QF_SEARCH_API_BASE or use a canonical QF_API_ENDPOINT ending in /content/api/v4.");
        }

        String path = "/search?mode=quick&query=" + URLEncoder.encode(sanitized, StandardCharsets.UTF_8)
                + "&navigationalResultsNumber=10&versesResultsNumber=25";
        HttpResponse<String> resp = client.getAlternateBaseJson(base, path);
        int code = resp.statusCode();
        if (code < 200 || code >= 300) {
            LOGGER.log(Level.WARNING, "Quran Foundation quick search upstream HTTP {0}", Integer.valueOf(code));
        }
        return resp;
    }

    /** Allow letters (incl. Arabic), marks, digits, whitespace, conservative punctuation — no markup. */
    public static String sanitizeQuickQuery(String raw) throws IOException {
        if (raw == null || raw.isBlank()) {
            throw new IOException("query required");
        }
        String t = raw.trim();
        if (t.length() > MAX_QUERY_LEN) {
            throw new IOException("query too long");
        }
        if (!SAFE_QUERY_CHAR.matcher(t).matches()) {
            throw new IOException("invalid query characters");
        }
        return t;
    }

    public static String cappedBody(HttpResponse<String> resp) {
        String b = resp.body();
        if (b == null) {
            return "";
        }
        return b.length() > MAX_RESPONSE_BODY_CHARS ? b.substring(0, MAX_RESPONSE_BODY_CHARS) : b;
    }
}
