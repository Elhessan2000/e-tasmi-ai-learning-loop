package model.service.quranpedia;

/**
 * Quranpedia content API. No authentication is required by the published API.
 * An empty {@code QURANPEDIA_API_ENDPOINT} disables the client.
 * When the variable is unset, the documented base URL is used.
 */
public final class QuranpediaConfig {

    public static final String ENV_ENDPOINT = "QURANPEDIA_API_ENDPOINT";
    public static final String DOCUMENTED_ENDPOINT = "https://api.quranpedia.net/v1";

    private final String endpoint;
    private final boolean configured;

    private QuranpediaConfig(String endpoint, boolean configured) {
        this.endpoint = endpoint;
        this.configured = configured;
    }

    public static QuranpediaConfig load() {
        String raw = System.getenv(ENV_ENDPOINT);
        if (raw == null) {
            return new QuranpediaConfig(DOCUMENTED_ENDPOINT, true);
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return new QuranpediaConfig("", false);
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        boolean https = trimmed.startsWith("https://");
        return new QuranpediaConfig(trimmed, https);
    }

    public boolean isConfigured() {
        return configured;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
