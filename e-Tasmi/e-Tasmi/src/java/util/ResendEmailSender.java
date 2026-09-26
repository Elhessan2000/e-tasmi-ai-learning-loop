package util;

import javax.mail.MessagingException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Sends transactional email via the Resend HTTPS API.
 * <p>
 * Used on hosts that block outbound SMTP (e.g. Railway). When
 * {@code RESEND_API_KEY} is set, {@link EmailUtil} routes all outbound
 * email through this class instead of {@code javax.mail.Transport}.
 * The HTTP call goes out on port 443, which is never blocked.
 */
public final class ResendEmailSender {
    private static final Logger LOGGER = Logger.getLogger(ResendEmailSender.class.getName());
    private static final String ENDPOINT = "https://api.resend.com/emails";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private ResendEmailSender() {
    }

    /**
     * Posts a single email to Resend. Throws {@link MessagingException} on
     * any failure so callers can reuse the same catch path as the SMTP flow.
     */
    public static void send(String apiKey,
                            String fromAddress,
                            String toEmail,
                            String subject,
                            String bodyText,
                            String bodyHtml) throws MessagingException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new MessagingException("RESEND_API_KEY is empty");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new MessagingException("Sender address (SMTP_FROM) is required when using Resend");
        }
        if (toEmail == null || toEmail.isBlank()) {
            throw new MessagingException("Recipient email is required");
        }

        StringBuilder json = new StringBuilder(512);
        json.append('{');
        json.append("\"from\":\"").append(escape(fromAddress)).append("\",");
        json.append("\"to\":[\"").append(escape(toEmail)).append("\"],");
        json.append("\"subject\":\"").append(escape(subject == null ? "" : subject)).append("\"");
        if (bodyText != null && !bodyText.isEmpty()) {
            json.append(",\"text\":\"").append(escape(bodyText)).append("\"");
        }
        if (bodyHtml != null && !bodyHtml.isEmpty()) {
            json.append(",\"html\":\"").append(escape(bodyHtml)).append("\"");
        }
        json.append('}');

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.io.IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new MessagingException("Resend HTTPS call failed: "
                    + ex.getClass().getName() + ": " + ex.getMessage(), ex);
        }

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            LOGGER.info("Resend accepted email for " + toEmail + " (status=" + status + ")");
            return;
        }

        String body = response.body();
        if (body == null) body = "";
        if (body.length() > 500) body = body.substring(0, 500) + "...";
        throw new MessagingException("Resend API rejected request (status=" + status + "): " + body);
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
