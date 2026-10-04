package model.service.quranpedia;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/** HTTP client for https://api.quranpedia.net/v1. Does not send credentials. */
public final class QuranpediaClient {

    private static final Logger LOGGER = Logger.getLogger(QuranpediaClient.class.getName());
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();

    public HttpResponse<String> get(String path) throws IOException {
        QuranpediaConfig cfg = QuranpediaConfig.load();
        if (!cfg.isConfigured()) {
            throw new IOException("Quranpedia is not configured.");
        }
        String suffix = path.startsWith("/") ? path : "/" + path;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(cfg.getEndpoint() + suffix))
                .timeout(TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "e-Tasmi-quranpedia-client")
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                LOGGER.log(Level.WARNING, "Quranpedia HTTP {0} path={1}",
                        new Object[]{Integer.valueOf(response.statusCode()), suffix});
            }
            return response;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Quranpedia request interrupted.");
        }
    }
}
