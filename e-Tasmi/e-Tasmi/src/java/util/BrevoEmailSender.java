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
 * Sends transactional email via the Brevo (ex-Sendinblue) HTTPS API.
 * <p>
 * Brevo's free tier (300 emails/day) only requires verifying a single
 * sender email address — no domain ownership needed — which makes it
 * the right fit for hosts that block SMTP (Railway) when you don't
 * own a verifiable domain.
 * <p>
 * Activated when {@code BREVO_API_KEY} is set. Takes priority over
 * Resend/SMTP in {@link EmailUtil}.
 */
public final class BrevoEmailSender {
    private static final Logger LOGGER = Logger.getLogger(BrevoEmailSender.class.getName());
    private static final String ENDPOINT = "https://api.brevo.com/v3/smtp/email";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private BrevoEmailSender() {
    }

    public static void send(String apiKey,
                            String fromAddress,
                            String fromName,
                            String toEmail,
                            String subject,
                            String bodyText,
                            String bodyHtml) throws MessagingException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new MessagingException("BREVO_API_KEY is empty");
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            throw new MessagingException("Sender address (SMTP_FROM) is required when using Brevo");
        }
        if (toEmail == null || toEmail.isBlank()) {
            throw new MessagingException("Recipient email is required");
        }

        String effectiveFromName = (fromName == null || fromName.isBlank()) ? "e-Tasmi" : fromName;

        StringBuilder json = new StringBuilder(768);
        json.append('{');
        json.append("\"sender\":{");
        json.append("\"name\":\"").append(escape(effectiveFromName)).append("\",");
        json.append("\"email\":\"").append(escape(fromAddress)).append("\"");
        json.append("},");
        json.append("\"to\":[{\"email\":\"").append(escape(toEmail)).append("\"}],");
        json.append("\"subject\":\"").append(escape(subject == null ? "" : subject)).append("\"");
        if (bodyHtml != null && !bodyHtml.isEmpty()) {
            json.append(",\"htmlContent\":\"").append(escape(bodyHtml)).append("\"");
        }
        if (bodyText != null && !bodyText.isEmpty()) {
            json.append(",\"textContent\":\"").append(escape(bodyText)).append("\"");
        }
        json.append('}');

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(15))
                .header("api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.io.IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new MessagingException("Brevo HTTPS call failed: "
                    + ex.getClass().getName() + ": " + ex.getMessage(), ex);
        }

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            LOGGER.info("Brevo accepted email for " + toEmail + " (status=" + status + ")");
            return;
        }

        String body = response.body();
        if (body == null) body = "";
        if (body.length() > 500) body = body.substring(0, 500) + "...";
        throw new MessagingException("Brevo API rejected request (status=" + status + "): " + body);
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
