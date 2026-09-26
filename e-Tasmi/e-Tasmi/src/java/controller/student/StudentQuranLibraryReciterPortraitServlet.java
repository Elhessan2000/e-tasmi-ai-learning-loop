package controller.student;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Proxies Quran CDN reciter portrait files ({@code images/reciters/&lt;n&gt;/…}) through the app origin.
 * <p>
 * Browsers were failing to load portraits when using guessed public hostnames ({@code assets.quran.com},
 * {@code cdn.quran.com}) that often do not resolve or return 404, and some networks block direct CDN
 * access. The Quran Data API only returns a <strong>relative</strong> {@code profile_picture} path;
 * this servlet tries several known bases server-side and streams the first successful image.
 */
@WebServlet(name = "StudentQuranLibraryReciterPortraitServlet",
        urlPatterns = {"/student/api/quran-library/reciter-portrait"})
public class StudentQuranLibraryReciterPortraitServlet extends HttpServlet {

    private static final int MAX_REL_LEN = 220;
    /** CDN portraits are small; cap bytes to limit proxy DoS / accidental huge responses. */
    private static final int MAX_PORTRAIT_BYTES = 2 * 1024 * 1024;
    /** Relative paths returned by api.qurancdn.com (no query/hash). */
    private static final Pattern ALLOWED_REL =
            Pattern.compile("^images/reciters/[0-9]+/[a-zA-Z0-9._\\-]+(?i:\\.(jpe?g|png|webp))$");

    /**
     * Bases tried in order. {@code static.qurancdn.com} is the only host
     * currently serving image bytes — verified inside the Tomcat container.
     * The others are kept as defensive fallbacks in case a CDN edge flips,
     * but each NXDOMAIN / HTML response wastes 1–5s, so the working host is
     * intentionally listed first to keep first-byte latency low.
     */
    private static final String[] UPSTREAM_BASES = {
            "https://static.qurancdn.com/",
            "https://assets.quran.com/",
            "https://cdn.quran.com/",
            "https://images.qurancdn.com/",
            "https://legacy.quran.com/",
            "https://api.qurancdn.com/"
    };

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String rel = request.getParameter("rel");
        if (rel == null || rel.length() > MAX_REL_LEN) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        rel = rel.trim();
        if (!ALLOWED_REL.matcher(rel).matches()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        for (String base : UPSTREAM_BASES) {
            URI uri = URI.create(base + rel);
            try {
                HttpRequest req = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(14))
                        .header("User-Agent", "eTasmi-QuranLibrary/1.0")
                        .header("Accept", "image/jpeg,image/png,image/webp,image/*;q=0.8,*/*;q=0.5")
                        .GET()
                        .build();
                HttpResponse<byte[]> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
                int code = resp.statusCode();
                byte[] body = resp.body();
                if (code < 200 || code >= 300 || body == null || body.length == 0 || body.length > MAX_PORTRAIT_BYTES) {
                    continue;
                }
                String safeCt = detectImageContentType(body);
                if (safeCt == null) {
                    /* Upstream returned non-image (HTML error page, JSON, etc.) — do not relay to the browser. */
                    continue;
                }
                response.setContentType(safeCt);
                response.setHeader("Cache-Control", "public, max-age=86400");
                response.getOutputStream().write(body);
                return;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                /* try next base */
            }
        }
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    /**
     * Trust file magic only — avoids relaying HTML/JSON from a misconfigured CDN or bad redirect
     * when the upstream {@code Content-Type} lies.
     */
    private static String detectImageContentType(byte[] body) {
        if (body.length >= 3 && (body[0] & 0xFF) == 0xFF && (body[1] & 0xFF) == 0xD8 && (body[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (body.length >= 8
                && (body[0] & 0xFF) == 0x89
                && body[1] == 0x50 && body[2] == 0x4E && body[3] == 0x47
                && body[4] == 0x0D && body[5] == 0x0A && body[6] == 0x1A && body[7] == 0x0A) {
            return "image/png";
        }
        if (body.length >= 12
                && body[0] == 0x52 && body[1] == 0x49 && body[2] == 0x46 && body[3] == 0x46
                && body[8] == 0x57 && body[9] == 0x45 && body[10] == 0x42 && body[11] == 0x50) {
            return "image/webp";
        }
        return null;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
    }
}
