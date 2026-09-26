package model.service.quran;

import util.JsonUtil;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Backend-only client for Quran Foundation Content APIs.
 * <p>
 * OAuth2 Client Credentials per Quran Foundation docs:
 * {@code POST {authBase}/oauth2/token} with HTTP Basic ({@code client_id:client_secret}),
 * body {@code grant_type=client_credentials}; scope defaults to {@code content} unless {@code QF_OAUTH_SCOPE}
 * is set ({@link QuranFoundationConfig#oauthScopeForTokenRequest()} — use {@code content search} only when Search API is provisioned).
 * Content requests use headers {@code x-auth-token} and {@code x-client-id} (not {@code Authorization: Bearer}).
 *
 * @see <a href="https://api-docs.quran.foundation/docs/quickstart/manual-authentication/">Manual authentication</a>
 */
public final class QuranFoundationClient {

    private static final Logger LOGGER = Logger.getLogger(QuranFoundationClient.class.getName());

    /** Guards static token cache (all instances share one cache). */
    private static final Object TOKEN_LOCK = new Object();

    /** Large surahs with translations/tafsir payloads can exceed quick responses over WAN. */
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(55);
    private static final Pattern PAT_ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PAT_EXPIRES_IN = Pattern.compile("\"expires_in\"\\s*:\\s*(\\d+)");
    /** OAuth failure JSON {@code error} field (RFC 6749-style); never logged to clients. */
    private static final Pattern PAT_OAUTH_ERR = Pattern.compile("\"error\"\\s*:\\s*\"([^\"]+)\"");

    private static volatile String cachedAccessToken;
    private static volatile long tokenExpiresAtEpochMillis;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();

    private static String tokenRequestBody(QuranFoundationConfig cfg) {
        return "grant_type=client_credentials&scope="
                + URLEncoder.encode(cfg.oauthScopeForTokenRequest(), StandardCharsets.UTF_8);
    }

    /**
     * Safe diagnostics (no outbound HTTP). No secrets, tokens, or raw credential values.
     */
    public static Map<String, Object> healthSnapshot() {
        QuranFoundationConfig cfg = QuranFoundationConfig.load();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", Boolean.TRUE);
        m.put("module", "quran_foundation");
        m.put("configured", cfg.isComplete());
        m.put("qfEnv", cfg.getEnvLabel());
        m.put("tier", tierName(cfg));
        m.put("missingEnv", cfg.missingEnvKeys());
        m.put("varsPresent", varsPresence(cfg));
        boolean cached = cachedAccessToken != null && System.currentTimeMillis() < tokenExpiresAtEpochMillis;
        m.put("tokenCached", cached);
        if (cfg.isComplete()) {
            m.put("authTokenEndpointResolved", cfg.resolveAuthTokenEndpoint());
            m.put("apiBaseNormalized", cfg.getApiBaseNormalized());
            String sb = cfg.resolveSearchApiBaseNormalized();
            if (sb != null) {
                m.put("searchApiBaseNormalized", sb);
            }
        }
        m.put("note", "Add ?live=1 for token + Content API reachability (still no secrets in response).");
        return m;
    }

    /**
     * Presence-only flags so operators can confirm JVM env without exposing values.
     */
    private static Map<String, Boolean> varsPresence(QuranFoundationConfig cfg) {
        Map<String, Boolean> v = new LinkedHashMap<>();
        v.put(QuranFoundationConfig.ENV_QF_CLIENT_ID, present(cfg.getClientId()));
        v.put(QuranFoundationConfig.ENV_QF_CLIENT_SECRET, present(cfg.getClientSecret()));
        v.put(QuranFoundationConfig.ENV_QF_API_ENDPOINT, present(cfg.getApiEndpoint()));
        v.put(QuranFoundationConfig.ENV_QF_ENV, present(cfg.getEnvLabel()));
        return v;
    }

    private static boolean present(String s) {
        return s != null && !s.isBlank();
    }

    private static String tierName(QuranFoundationConfig cfg) {
        try {
            return cfg.resolveTier().name().toLowerCase();
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    /**
     * Optional live check: OAuth token exchange + GET {@code /content/api/v4/chapters}.
     * Logs HTTP status codes only — never client_secret or access_token.
     */
    public static Map<String, Object> liveConnectivityProbe() {
        Map<String, Object> out = new LinkedHashMap<>();
        QuranFoundationConfig cfg = QuranFoundationConfig.load();
        if (!cfg.isComplete()) {
            out.put("liveProbeRan", Boolean.FALSE);
            out.put("liveSkippedReason", "not_configured");
            return out;
        }
        out.put("liveProbeRan", Boolean.TRUE);
        try {
            QuranFoundationClient client = new QuranFoundationClient();
            client.getAccessToken();
            out.put("tokenExchangeOk", Boolean.TRUE);
            LOGGER.info("Quran Foundation probe: token exchange succeeded");

            HttpResponse<String> chapters = client.getJson("/content/api/v4/chapters");
            int code = chapters.statusCode();
            out.put("chaptersHttpStatus", Integer.valueOf(code));
            out.put("contentApiOk", Boolean.valueOf(code >= 200 && code < 300));
            LOGGER.log(Level.INFO, "Quran Foundation probe: chapters HTTP {0}", code);
        } catch (IOException ex) {
            out.put("tokenExchangeOk", Boolean.FALSE);
            out.put("contentApiOk", Boolean.FALSE);
            out.put("liveProbeError", "io_failure");
            LOGGER.log(Level.WARNING, "Quran Foundation probe failed ({0})", ex.getClass().getSimpleName());
        }
        return out;
    }

    /**
     * Returns a valid access token, refreshing when needed.
     */
    public String getAccessToken() throws IOException {
        synchronized (TOKEN_LOCK) {
            QuranFoundationConfig cfg = QuranFoundationConfig.load();
            if (!cfg.isComplete()) {
                throw new IOException("Quran Foundation API is not configured (missing or invalid env).");
            }
            if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiresAtEpochMillis) {
                return cachedAccessToken;
            }
            return refreshAccessTokenLocked(cfg);
        }
    }

    public static void clearCachedToken() {
        synchronized (TOKEN_LOCK) {
            cachedAccessToken = null;
            tokenExpiresAtEpochMillis = 0L;
        }
    }

    /** Caller must hold {@link #TOKEN_LOCK}. */
    private String refreshAccessTokenLocked(QuranFoundationConfig cfg) throws IOException {
        String tokenUrl = cfg.resolveAuthTokenEndpoint();
        String basic = Base64.getEncoder().encodeToString(
                (cfg.getClientId() + ":" + cfg.getClientSecret()).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(tokenRequestBody(cfg), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = send(request);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            String oauthErrCode = oauthErrorSnippet(resp.body());
            if (oauthErrCode != null) {
                LOGGER.log(Level.WARNING, "Quran Foundation token request failed: HTTP {0} oauth_error={1}",
                        new Object[]{Integer.valueOf(resp.statusCode()), oauthErrCode});
            } else {
                LOGGER.log(Level.WARNING, "Quran Foundation token request failed: HTTP {0}", resp.statusCode());
            }
            throw new IOException("Quran Foundation token request failed (HTTP " + resp.statusCode() + ").");
        }

        String body = resp.body();
        String token = extractFirst(PAT_ACCESS_TOKEN, body);
        if (token == null || token.isEmpty()) {
            LOGGER.warning("Quran Foundation token response missing access_token.");
            throw new IOException("Quran Foundation token response missing access_token.");
        }

        long expiresIn = 3600L;
        String expiresRaw = extractFirst(PAT_EXPIRES_IN, body);
        if (expiresRaw != null) {
            try {
                expiresIn = Long.parseLong(expiresRaw);
            } catch (NumberFormatException ignored) {
            }
        }

        long skewMillis = 60_000L;
        cachedAccessToken = token;
        tokenExpiresAtEpochMillis = System.currentTimeMillis() + Math.max(30_000L, expiresIn * 1000L - skewMillis);
        return cachedAccessToken;
    }

    /**
     * GET JSON against another HTTPS base under the same org (e.g. Search API) using the cached Content token.
     */
    public HttpResponse<String> getAlternateBaseJson(String alternateBaseNormalized, String relativePathStartingWithSlash)
            throws IOException {
        String base = normalizeNoTrailingSlash(alternateBaseNormalized);
        if (base == null || base.isBlank()) {
            throw new IOException("alternate base missing");
        }
        String path = relativePathStartingWithSlash == null ? "" : relativePathStartingWithSlash;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return getAgainstAbsoluteUrlLocked(base + path, true);
    }

    static String normalizeNoTrailingSlash(String s) {
        if (s == null) {
            return null;
        }
        String b = s.trim();
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        return b;
    }

    private HttpResponse<String> getAgainstAbsoluteUrlLocked(String fullUrlHttps, boolean allow401Retry)
            throws IOException {
        QuranFoundationConfig cfg = QuranFoundationConfig.load();
        if (!cfg.isComplete()) {
            throw new IOException("Quran Foundation API is not configured.");
        }
        String token = getAccessToken();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fullUrlHttps))
                .timeout(HTTP_TIMEOUT)
                .header("x-auth-token", token)
                .header("x-client-id", cfg.getClientId())
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> resp = send(request);
        if (resp.statusCode() == 401 && allow401Retry) {
            clearCachedToken();
            token = getAccessToken();
            request = HttpRequest.newBuilder()
                    .uri(URI.create(fullUrlHttps))
                    .timeout(HTTP_TIMEOUT)
                    .header("x-auth-token", token)
                    .header("x-client-id", cfg.getClientId())
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            resp = send(request);
        }
        return resp;
    }

    /**
     * GET JSON from the Content API (path relative to {@code QF_API_ENDPOINT}, or absolute URL).
     * On HTTP 401, clears the cached token and retries once.
     */
    public HttpResponse<String> getJson(String relativeOrAbsolutePath) throws IOException {
        return getJson(relativeOrAbsolutePath, true);
    }

    private HttpResponse<String> getJson(String relativeOrAbsolutePath, boolean allow401Retry) throws IOException {
        QuranFoundationConfig cfg = QuranFoundationConfig.load();
        if (!cfg.isComplete()) {
            throw new IOException("Quran Foundation API is not configured.");
        }
        String base = cfg.getApiBaseNormalized();
        String path = relativeOrAbsolutePath.startsWith("/") ? relativeOrAbsolutePath : "/" + relativeOrAbsolutePath;
        String url = relativeOrAbsolutePath.startsWith("http://") || relativeOrAbsolutePath.startsWith("https://")
                ? relativeOrAbsolutePath
                : base + path;

        String token = getAccessToken();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .header("x-auth-token", token)
                .header("x-client-id", cfg.getClientId())
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> resp = send(request);
        if (resp.statusCode() == 401 && allow401Retry) {
            clearCachedToken();
            return getJson(relativeOrAbsolutePath, false);
        }
        return resp;
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Quran Foundation request interrupted", ex);
        }
    }

    private static String extractFirst(Pattern pattern, String body) {
        if (body == null) {
            return null;
        }
        var m = pattern.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    /** Logs only; trims and caps length — no bearer tokens included. */
    private static String oauthErrorSnippet(String body) {
        String e = extractFirst(PAT_OAUTH_ERR, body);
        if (e == null || e.isBlank()) {
            return null;
        }
        e = e.trim();
        return e.length() > 96 ? e.substring(0, 96) + "…" : e;
    }

    public static String healthSnapshotJson() {
        return JsonUtil.obj(healthSnapshot());
    }

    public static String healthAndLiveProbeJson(boolean runLiveProbe) {
        Map<String, Object> body = new LinkedHashMap<>(healthSnapshot());
        if (runLiveProbe) {
            body.put("liveProbe", liveConnectivityProbe());
        }
        return JsonUtil.obj(body);
    }
}
