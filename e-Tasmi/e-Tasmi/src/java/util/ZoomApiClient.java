package util;

import model.service.ZoomMeetingInfo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal Zoom Server-to-Server OAuth client using JDK 17 HttpClient.
 *
 * Reads credentials from environment variables:
 *   ZOOM_ACCOUNT_ID, ZOOM_CLIENT_ID, ZOOM_CLIENT_SECRET,
 *   ZOOM_DEFAULT_HOST_EMAIL, ZOOM_TIMEZONE
 */
public final class ZoomApiClient {
    private static final Logger LOGGER = Logger.getLogger(ZoomApiClient.class.getName());

    private static final String TOKEN_URL = "https://zoom.us/oauth/token";
    private static final String API_BASE = "https://api.zoom.us/v2";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);

    private static final Pattern PAT_ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PAT_EXPIRES_IN = Pattern.compile("\"expires_in\"\\s*:\\s*(\\d+)");
    private static final Pattern PAT_ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern PAT_JOIN_URL = Pattern.compile("\"join_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PAT_START_URL = Pattern.compile("\"start_url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PAT_PASSWORD = Pattern.compile("\"password\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PAT_CODE = Pattern.compile("\"code\"\\s*:\\s*(\\d+)");
    private static final Pattern PAT_MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern PAT_USER_TOKEN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    private static String cachedToken;
    private static long tokenExpiresAtMillis;

    private final HttpClient httpClient;

    public ZoomApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }

    public static boolean isConfigured() {
        return notBlank(env("ZOOM_ACCOUNT_ID"))
                && notBlank(env("ZOOM_CLIENT_ID"))
                && notBlank(env("ZOOM_CLIENT_SECRET"));
    }

    public static String getDefaultHostEmail() {
        return env("ZOOM_DEFAULT_HOST_EMAIL");
    }

    public static String getTimezone() {
        String tz = env("ZOOM_TIMEZONE");
        return notBlank(tz) ? tz : "UTC";
    }

    /**
     * Creates a Zoom meeting under the given host email.
     */
    public ZoomMeetingInfo createMeeting(String hostEmail,
                                         String topic,
                                         LocalDate date,
                                         LocalTime time,
                                         int durationMinutes,
                                         String timezone) throws IOException {
        String token = getAccessToken();
        String startTime = date.toString() + "T" + time.toString() + ":00";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("topic", topic);
        body.put("type", 2); // scheduled meeting
        body.put("start_time", startTime);
        body.put("duration", durationMinutes);
        body.put("timezone", timezone);

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("join_before_host", false);
        settings.put("waiting_room", true);
        settings.put("auto_recording", "none");
        settings.put("mute_upon_entry", true);
        body.put("settings", settings);

        String json = JsonUtil.obj(castToStringKeys(body));
        String url = API_BASE + "/users/" + urlEncode(hostEmail) + "/meetings";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = send(request);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Zoom create meeting failed (" + resp.statusCode() + "): " + extractErrorMessage(resp.body()));
        }

        return parseMeetingResponse(resp.body());
    }

    /**
     * Updates an existing Zoom meeting.
     */
    public void updateMeeting(long meetingId,
                              String topic,
                              LocalDate date,
                              LocalTime time,
                              int durationMinutes,
                              String timezone) throws IOException {
        String token = getAccessToken();
        String startTime = date.toString() + "T" + time.toString() + ":00";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("topic", topic);
        body.put("start_time", startTime);
        body.put("duration", durationMinutes);
        body.put("timezone", timezone);

        String json = JsonUtil.obj(castToStringKeys(body));
        String url = API_BASE + "/meetings/" + meetingId;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> resp = send(request);
        if (resp.statusCode() != 204 && (resp.statusCode() < 200 || resp.statusCode() >= 300)) {
            throw new IOException("Zoom update meeting failed (" + resp.statusCode() + "): " + extractErrorMessage(resp.body()));
        }
    }

    /**
     * Deletes a Zoom meeting. Best-effort; logs but does not throw on 404.
     */
    public void deleteMeeting(long meetingId) throws IOException {
        String token = getAccessToken();
        String url = API_BASE + "/meetings/" + meetingId;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .DELETE()
                .build();

        HttpResponse<String> resp = send(request);
        if (resp.statusCode() == 404) {
            LOGGER.info("Zoom meeting " + meetingId + " already deleted or not found.");
            return;
        }
        if (resp.statusCode() != 204 && (resp.statusCode() < 200 || resp.statusCode() >= 300)) {
            throw new IOException("Zoom delete meeting failed (" + resp.statusCode() + "): " + extractErrorMessage(resp.body()));
        }
    }

    /**
     * Fetches meeting details including a fresh start_url.
     */
    public ZoomMeetingInfo getMeeting(long meetingId) throws IOException {
        String token = getAccessToken();
        String url = API_BASE + "/meetings/" + meetingId;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> resp = send(request);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Zoom get meeting failed (" + resp.statusCode() + "): " + extractErrorMessage(resp.body()));
        }

        return parseMeetingResponse(resp.body());
    }

    /**
     * Fetches the host's ZAK (Zoom Access Key). Required by the Web Meeting SDK to start a meeting
     * as host (role 1) when the meeting has not started yet.
     *
     * @param hostEmailOrUserId Zoom user email (or user id) of the meeting host
     */
    public String getUserZakToken(String hostEmailOrUserId) throws IOException {
        String token = getAccessToken();
        String url = API_BASE + "/users/" + urlEncode(hostEmailOrUserId) + "/token?type=zak";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> resp = send(request);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Zoom ZAK token request failed (" + resp.statusCode() + "): " + extractErrorMessage(resp.body()));
        }
        String zak = extractPattern(PAT_USER_TOKEN, resp.body());
        if (!notBlank(zak)) {
            throw new IOException("Zoom ZAK response missing token.");
        }
        return zak;
    }

    private synchronized String getAccessToken() throws IOException {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiresAtMillis) {
            return cachedToken;
        }

        String accountId = env("ZOOM_ACCOUNT_ID");
        String clientId = env("ZOOM_CLIENT_ID");
        String clientSecret = env("ZOOM_CLIENT_SECRET");

        if (!notBlank(accountId) || !notBlank(clientId) || !notBlank(clientSecret)) {
            throw new IOException("Zoom API credentials are not configured.");
        }

        String credentials = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));

        String tokenUrl = TOKEN_URL + "?grant_type=account_credentials&account_id=" + urlEncode(accountId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .timeout(HTTP_TIMEOUT)
                .header("Authorization", "Basic " + credentials)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> resp = send(request);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("Zoom OAuth token request failed (" + resp.statusCode() + "): " + truncate(resp.body(), 300));
        }

        String body = resp.body();
        String token = extractPattern(PAT_ACCESS_TOKEN, body);
        if (!notBlank(token)) {
            throw new IOException("Zoom OAuth response missing access_token.");
        }

        long expiresIn = 3600;
        String expiresStr = extractPattern(PAT_EXPIRES_IN, body);
        if (notBlank(expiresStr)) {
            try {
                expiresIn = Long.parseLong(expiresStr);
            } catch (NumberFormatException ignored) {
            }
        }

        cachedToken = token;
        tokenExpiresAtMillis = System.currentTimeMillis() + (expiresIn - 60) * 1000;
        return cachedToken;
    }

    private ZoomMeetingInfo parseMeetingResponse(String body) throws IOException {
        String idStr = extractPattern(PAT_ID, body);
        String joinUrl = extractPattern(PAT_JOIN_URL, body);
        String startUrl = extractPattern(PAT_START_URL, body);
        String password = extractPattern(PAT_PASSWORD, body);

        if (!notBlank(idStr) || !notBlank(joinUrl)) {
            throw new IOException("Zoom response missing meeting id or join_url: " + truncate(body, 500));
        }

        long meetingId;
        try {
            meetingId = Long.parseLong(idStr);
        } catch (NumberFormatException ex) {
            throw new IOException("Zoom response has invalid meeting id: " + idStr);
        }

        if (joinUrl != null) {
            joinUrl = joinUrl.replace("\\/", "/");
        }
        if (startUrl != null) {
            startUrl = startUrl.replace("\\/", "/");
        }

        return new ZoomMeetingInfo(meetingId, joinUrl, startUrl, password);
    }

    private HttpResponse<String> send(HttpRequest request) throws IOException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("Zoom API request interrupted", ex);
        } catch (IOException ex) {
            throw new IOException("Could not reach Zoom API: " + ex.getMessage(), ex);
        }
    }

    private static String extractErrorMessage(String body) {
        if (!notBlank(body)) {
            return "(no response body)";
        }
        String message = extractPattern(PAT_MESSAGE, body);
        if (notBlank(message)) {
            return message;
        }
        return truncate(body, 300);
    }

    private static String extractPattern(Pattern pattern, String text) {
        if (text == null) {
            return null;
        }
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    private static String urlEncode(String value) {
        if (value == null) {
            return "";
        }
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String env(String key) {
        String v = System.getenv(key);
        return v == null ? null : v.trim();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ?> castToStringKeys(Map<String, Object> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getValue() instanceof Map) {
                result.put(e.getKey(), castToStringKeys((Map<String, Object>) e.getValue()));
            } else {
                result.put(e.getKey(), e.getValue());
            }
        }
        return result;
    }
}
