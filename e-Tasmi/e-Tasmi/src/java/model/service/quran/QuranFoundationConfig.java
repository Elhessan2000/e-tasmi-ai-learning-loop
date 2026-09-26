package model.service.quran;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Quran Foundation Content API — configuration from environment variables only.
 * <p>
 * Required: {@code QF_CLIENT_ID}, {@code QF_CLIENT_SECRET}, {@code QF_API_ENDPOINT} (HTTPS API base).
 * Optional: {@code QF_ENV} — drives OAuth host when {@code QF_AUTH_ENDPOINT} is not set
 * (recognized: {@code prelive}, {@code production}, {@code production_test}, {@code production_live}; see
 * <a href="https://api-docs.quran.foundation/docs/quickstart/manual-authentication/">manual authentication</a>).
 * Optional override: {@code QF_AUTH_ENDPOINT} — OAuth server origin or full {@code .../oauth2/token} URL.
 * <p>
 * Secrets ({@code QF_CLIENT_SECRET}) never leave the server; do not expose via JSP or JSON.
 */
public final class QuranFoundationConfig {

    public static final String ENV_QF_ENV = "QF_ENV";
    public static final String ENV_QF_CLIENT_ID = "QF_CLIENT_ID";
    public static final String ENV_QF_CLIENT_SECRET = "QF_CLIENT_SECRET";
    public static final String ENV_QF_API_ENDPOINT = "QF_API_ENDPOINT";
    /** Optional; when unset, token URL is inferred from {@code QF_ENV} (canonical Quran Foundation OAuth hosts). */
    public static final String ENV_QF_AUTH_ENDPOINT = "QF_AUTH_ENDPOINT";
    /** Optional space-separated OAuth scopes (default {@code content search} for Content + Search APIs). */
    public static final String ENV_QF_OAUTH_SCOPE = "QF_OAUTH_SCOPE";
    /** Optional override when Search API base differs from Content API host (must be HTTPS). */
    public static final String ENV_QF_SEARCH_API_BASE = "QF_SEARCH_API_BASE";

    /** Documented pre-production OAuth base (public). */
    public static final String DEFAULT_AUTH_BASE_PRELIVE = "https://prelive-oauth2.quran.foundation";
    /** Documented production OAuth base (public). */
    public static final String DEFAULT_AUTH_BASE_PRODUCTION = "https://oauth2.quran.foundation";

    private final String envLabel;
    private final String clientId;
    private final String clientSecret;
    private final String apiEndpoint;
    /** Optional explicit OAuth base or token URL from env. */
    private final String authEndpointOverride;
    private final String oauthScope;
    private final String searchApiBaseOverride;

    private QuranFoundationConfig(String envLabel,
                                  String clientId,
                                  String clientSecret,
                                  String apiEndpoint,
                                  String authEndpointOverride,
                                  String oauthScope,
                                  String searchApiBaseOverride) {
        this.envLabel = envLabel;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.apiEndpoint = apiEndpoint;
        this.authEndpointOverride = authEndpointOverride;
        this.oauthScope = oauthScope;
        this.searchApiBaseOverride = searchApiBaseOverride;
    }

    /**
     * Loads current configuration from {@link System#getenv()}.
     */
    public static QuranFoundationConfig load() {
        return new QuranFoundationConfig(
                trimOrNull(System.getenv(ENV_QF_ENV)),
                trimOrNull(System.getenv(ENV_QF_CLIENT_ID)),
                trimOrNull(System.getenv(ENV_QF_CLIENT_SECRET)),
                trimOrNull(System.getenv(ENV_QF_API_ENDPOINT)),
                trimOrNull(System.getenv(ENV_QF_AUTH_ENDPOINT)),
                trimOrNull(System.getenv(ENV_QF_OAUTH_SCOPE)),
                trimOrNull(System.getenv(ENV_QF_SEARCH_API_BASE)));
    }

    private static String trimOrNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Names of required variables that are missing or invalid.
     * When {@code QF_AUTH_ENDPOINT} is unset, {@code QF_ENV} must allow tier resolution (or API host inference).
     */
    public List<String> missingEnvKeys() {
        List<String> missing = new ArrayList<>();
        if (clientId == null) {
            missing.add(ENV_QF_CLIENT_ID);
        }
        if (clientSecret == null) {
            missing.add(ENV_QF_CLIENT_SECRET);
        }
        if (!isHttpUri(apiEndpoint)) {
            missing.add(ENV_QF_API_ENDPOINT);
        }
        if (isHttpUri(apiEndpoint) && looksLikeOAuthHostnameUsedAsContentApi(apiEndpoint)) {
            /* Virtual hint: OAuth/token hosts answer /content/api/… with non-JSON or 404. */
            missing.add("QF_API_ENDPOINT_USE_CONTENT_APIS_HOST");
        }
        if (authEndpointOverride != null && !isHttpUri(trimTrailingSlashes(authEndpointOverride))) {
            missing.add(ENV_QF_AUTH_ENDPOINT);
        }
        if (searchApiBaseOverride != null && !isHttpUri(trimTrailingSlashes(searchApiBaseOverride))) {
            missing.add(ENV_QF_SEARCH_API_BASE);
        }
        if (authEndpointOverride == null && clientId != null && clientSecret != null && isHttpUri(apiEndpoint)) {
            try {
                resolveTier();
            } catch (IllegalStateException ex) {
                missing.add(ENV_QF_ENV);
            }
        }
        return Collections.unmodifiableList(missing);
    }

    /**
     * True when the URL hostname looks like Quran Foundation OAuth (oauth2…) rather than Content API (apis…).
     * Helps catch the common mistake of pasting {@code QF_AUTH_ENDPOINT}'s origin into {@code QF_API_ENDPOINT}.
     */
    static boolean looksLikeOAuthHostnameUsedAsContentApi(String apiEndpointUrl) {
        if (apiEndpointUrl == null) {
            return false;
        }
        try {
            URI u = URI.create(trimTrailingSlashes(apiEndpointUrl));
            String host = u.getHost();
            if (host == null || host.isEmpty()) {
                return false;
            }
            String h = host.toLowerCase(Locale.ROOT);
            boolean qf = h.endsWith("quran.foundation");
            boolean looksApis = h.startsWith("apis") || h.contains(".apis.") || h.contains("apis-prelive");
            return qf && !looksApis && h.contains("oauth");
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * True when credentials and API base are set, URLs are valid, and an OAuth token URL can be resolved.
     */
    public boolean isComplete() {
        return missingEnvKeys().isEmpty();
    }

    private static String trimTrailingSlashes(String s) {
        String u = s;
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        return u;
    }

    /**
     * Canonical OAuth2 token URL for Client Credentials ({@code POST}, Basic auth, {@code scope=content}).
     * Uses {@link #ENV_QF_AUTH_ENDPOINT} when set; otherwise documented hosts per {@link #resolveTier()}.
     */
    public String resolveAuthTokenEndpoint() {
        if (authEndpointOverride != null) {
            return normalizeTokenUrl(authEndpointOverride);
        }
        switch (resolveTier()) {
            case PRELIVE:
                return DEFAULT_AUTH_BASE_PRELIVE + "/oauth2/token";
            case PRODUCTION:
            default:
                return DEFAULT_AUTH_BASE_PRODUCTION + "/oauth2/token";
        }
    }

    private static String normalizeTokenUrl(String raw) {
        String u = raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw;
        if (u.toLowerCase(Locale.ROOT).endsWith("/oauth2/token")) {
            return u;
        }
        return u + "/oauth2/token";
    }

    /**
     * API tier for matching tokens to hosts (do not mix prelive and production).
     */
    public Tier resolveTier() {
        String label = envLabel != null ? envLabel.trim().toLowerCase(Locale.ROOT) : "";
        /*
         * Forgiving: typos such as duplicated env tokens should still infer production when
         * {@code production_live} appears alongside garbage.
         */
        if (!label.isEmpty() && label.contains("production_live")) {
            label = "production_live";
        }
        if ("prelive".equals(label) || "pre-live".equals(label) || "staging".equals(label)) {
            return Tier.PRELIVE;
        }
        if ("production".equals(label) || "prod".equals(label) || "production_test".equals(label)
                || "production_live".equals(label) || label.isEmpty()) {
            if (label.isEmpty() && apiEndpoint != null) {
                String hostHint = apiEndpoint.toLowerCase(Locale.ROOT);
                if (hostHint.contains("apis-prelive") || hostHint.contains("prelive-oauth2")) {
                    return Tier.PRELIVE;
                }
            }
            return Tier.PRODUCTION;
        }
        if (apiEndpoint != null) {
            String hostHint = apiEndpoint.toLowerCase(Locale.ROOT);
            if (hostHint.contains("apis-prelive") || hostHint.contains("prelive")) {
                return Tier.PRELIVE;
            }
        }
        throw new IllegalStateException(
                "QF_ENV must be recognizable (e.g. prelive, production, production_test, production_live) or set QF_AUTH_ENDPOINT.");
    }

    /**
     * Space-separated OAuth scopes for client-credentials token (e.g. {@code content}, {@code content search}).
     */
    public String oauthScopeForTokenRequest() {
        if (oauthScope != null && !oauthScope.isBlank()) {
            return oauthScope.trim();
        }
        /*
         * Quran Foundation manual auth recommends {@code scope=content} for Content APIs.
         * Use QF_OAUTH_SCOPE (e.g. {@code content search}) only when Search API is provisioned — a
         * too-broad scope can make the OAuth server return HTTP 400 (invalid_scope).
         */
        return "content";
    }

    /**
     * Search API base (no trailing slash), derived from Content API host unless {@link #ENV_QF_SEARCH_API_BASE} set.
     */
    public String resolveSearchApiBaseNormalized() {
        if (searchApiBaseOverride != null && isHttpUri(trimTrailingSlashes(searchApiBaseOverride))) {
            return trimTrailingSlashes(searchApiBaseOverride);
        }
        String api = getApiBaseNormalized();
        if (api == null) {
            return null;
        }
        int ix = api.indexOf("/content/api/");
        if (ix > 0) {
            return api.substring(0, ix) + "/search/api/v1";
        }
        if (looksLikeApisContentOrigin(api)) {
            return trimTrailingSlashes(api) + "/search/api/v1";
        }
        return null;
    }

    /** True when {@code api} is the documented Content API origins (bare host, no /content/api path). */
    private static boolean looksLikeApisContentOrigin(String apiNormalized) {
        if (apiNormalized == null || apiNormalized.isBlank()) {
            return false;
        }
        try {
            java.net.URI u = java.net.URI.create(apiNormalized.toLowerCase(Locale.ROOT));
            String host = u.getHost();
            if (host == null) {
                return false;
            }
            boolean qfHost = host.endsWith("quran.foundation");
            return qfHost
                    && ("apis.quran.foundation".equals(host) || host.startsWith("apis-prelive"));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** Normalized API base URL without trailing slash. */
    public String getApiBaseNormalized() {
        String base = apiEndpoint;
        if (base == null) {
            return null;
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static boolean isHttpUri(String candidate) {
        if (candidate == null) {
            return false;
        }
        try {
            URI u = URI.create(candidate);
            String scheme = u.getScheme();
            return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public String getEnvLabel() {
        return envLabel;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getApiEndpoint() {
        return apiEndpoint;
    }

    /**
     * Raw optional override from env; prefer {@link #resolveAuthTokenEndpoint()} for requests.
     */
    public String getAuthEndpointOverride() {
        return authEndpointOverride;
    }

    public enum Tier {
        PRELIVE,
        PRODUCTION
    }
}
