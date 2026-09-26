package util;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class EmailUtil {
    private static final Logger LOGGER = Logger.getLogger(EmailUtil.class.getName());

    private static volatile Properties FILE_CONFIG;

    private EmailUtil() {
    }

    public static void sendEmail(ServletContext context, String toEmail, String subject, String bodyText) throws MessagingException {
        sendEmail(context, toEmail, subject, bodyText, null);
    }

    private static void sendEmail(ServletContext context, String toEmail, String subject, String bodyText, String bodyHtml) throws MessagingException {
        if (context == null) {
            throw new IllegalArgumentException("ServletContext is required");
        }
        if (toEmail == null || toEmail.isBlank()) {
            throw new IllegalArgumentException("Recipient email is required");
        }

        SmtpSettings settings = loadSmtpSettings(context);

        // Diagnostics (safe): helps confirm what the running app sees after redeploy.
        try {
            LOGGER.info("SMTP config loaded: host=" + settings.host + ", port=" + settings.port + ", from=" + settings.from + ", auth=" + ((settings.username != null && settings.password != null) ? "on" : "off"));
        } catch (Exception ignore) {
        }

        String configurationProblem = getConfigurationProblem(context);
        if (configurationProblem != null) {
            throw new IllegalStateException(configurationProblem);
        }

        // HTTPS transports (preferred on hosts that block outbound SMTP like
        // Railway). Priority order: Brevo > Resend > SMTP. Only SMTP_FROM is
        // required for either HTTPS provider; credentials live in env vars.
        String brevoKey = getenvTransport("BREVO_API_KEY");
        String resendKey = getenvTransport("RESEND_API_KEY");

        // One line to stderr so hosted log UIs always show which path runs.
        // If you see resend=true and brevo=false but wanted Brevo, BREVO_API_KEY
        // is missing on THIS service — add it or delete RESEND_API_KEY.
        System.err.println("[e-Tasmi-email] brevoKeySet=" + (brevoKey != null)
                + " resendKeySet=" + (resendKey != null) + " to=" + toEmail);

        if (brevoKey != null) {
            String fromName = trimToNull(System.getenv("BREVO_SENDER_NAME"));
            LOGGER.info("Using Brevo HTTPS transport (BREVO_API_KEY present). from=" + settings.from + ", to=" + toEmail);
            BrevoEmailSender.send(brevoKey, settings.from, fromName, toEmail, subject, bodyText, bodyHtml);
            return;
        }

        if (resendKey != null) {
            LOGGER.info("Using Resend HTTPS transport (RESEND_API_KEY present). from=" + settings.from + ", to=" + toEmail);
            ResendEmailSender.send(resendKey, settings.from, toEmail, subject, bodyText, bodyHtml);
            return;
        }

        ensureSmtpAndPublicBaseEnv(settings);

        String smtpHost = settings.host;
        String smtpPort = settings.port;

        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        if (smtpHost != null) {
            props.put("mail.smtp.ssl.trust", smtpHost);
        }
        // Gmail 587: STARTTLS (required); matches Railway / production troubleshooting needs.
        props.put("mail.smtp.starttls.required", (settings.startTls == null ? "true" : settings.startTls));

        // Optional troubleshooting only — off by default in production.
        String debug = trimToNull(System.getenv("SMTP_DEBUG"));
        if (debug != null && !("0".equals(debug) || "false".equalsIgnoreCase(debug) || "no".equalsIgnoreCase(debug))) {
            props.put("mail.debug", "true");
        }

        // Reliability: avoid hanging requests if SMTP is blocked.
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        Session session;
        if (settings.username != null && settings.password != null) {
            session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(settings.username, settings.password);
                }
            });
        } else {
            session = Session.getInstance(props);
        }

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(settings.from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail, false));
        message.setSubject(subject, StandardCharsets.UTF_8.name());
        if (bodyHtml == null || bodyHtml.isBlank()) {
            message.setText(bodyText == null ? "" : bodyText, StandardCharsets.UTF_8.name());
        } else {
            MimeBodyPart textPart = new MimeBodyPart();
            textPart.setText(bodyText == null ? "" : bodyText, StandardCharsets.UTF_8.name());

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(bodyHtml, "text/html; charset=UTF-8");

            Multipart multipart = new MimeMultipart("alternative");
            multipart.addBodyPart(textPart);
            multipart.addBodyPart(htmlPart);
            message.setContent(multipart);
        }

        try {
            LOGGER.info("Sending email: to=" + toEmail + ", subject=" + (subject == null ? "" : subject));
        } catch (Exception ignore) {
        }

        try {
            Transport.send(message);
        } catch (MessagingException ex) {
            String hostLog = smtpHost == null ? "" : smtpHost;
            String portLog = smtpPort == null ? "" : smtpPort;
            LOGGER.log(Level.SEVERE,
                    "SMTP send failed: recipient=" + toEmail
                            + ", smtpHost=" + hostLog
                            + ", smtpPort=" + portLog
                            + " — " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()),
                    ex);
            System.err.println("SMTP send failed: recipient=" + toEmail
                    + ", smtpHost=" + hostLog
                    + ", smtpPort=" + portLog
                    + " — " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
            throw ex;
        }

        try {
            LOGGER.info("Email sent OK: to=" + toEmail);
        } catch (Exception ignore) {
        }
    }

    public static void sendPasswordResetEmail(ServletContext context, String toEmail, String resetUrl, String fullName) throws MessagingException {
        String subject = "Reset your password - e-Tasmi";
        String greet = (fullName == null || fullName.isBlank()) ? "Assalamu alaikum" : ("Assalamu alaikum " + fullName.trim());
        String body = greet + ",\n\n" +
                "We received a request to reset your password.\n\n" +
                "Reset link:\n" + resetUrl + "\n\n" +
                "This link expires in 30 minutes. If you did not request this, you can ignore this email.";
        String html = buildPasswordResetEmailHtml(fullName, resetUrl);
        sendEmail(context, toEmail, subject, body, html);
    }

    private static String buildPasswordResetEmailHtml(String fullName, String resetUrl) {
        String safeName = escapeHtml(fullName == null ? "" : fullName.trim());
        String safeUrl = escapeHtml(resetUrl == null ? "" : resetUrl.trim());
        String greeting = safeName.isBlank() ? "Assalamu alaikum," : "Assalamu alaikum " + safeName + ",";

        return "<!doctype html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>Password Reset - e-Tasmi</title></head>"
                + "<body style=\"margin:0;padding:0;background:#F8FAFC;font-family:Inter,Arial,Helvetica,sans-serif;color:#0F172A;\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"background:#F8FAFC;margin:0;padding:32px 16px;\">"
                + "<tr><td align=\"center\">"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"max-width:560px;background:#FFFFFF;border:1px solid #E5E7EB;border-radius:16px;overflow:hidden;\">"
                + "<tr><td style=\"padding:32px;\">"
                + "<h1 style=\"margin:0;font-size:24px;color:#0F172A;\">Reset your password</h1>"
                + "<p style=\"margin:16px 0 0 0;font-size:15px;line-height:1.7;color:#64748B;\">"
                + greeting + " We received a request to reset your e-Tasmi password. Use the button below within <strong>30 minutes</strong>.</p>"
                + "<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" align=\"center\" style=\"margin:24px auto 0 auto;\">"
                + "<tr><td align=\"center\" bgcolor=\"#22C55E\" style=\"border-radius:10px;\">"
                + "<a href=\"" + safeUrl + "\" style=\"display:inline-block;padding:13px 22px;font-size:14px;font-weight:800;color:#FFFFFF;text-decoration:none;\">Reset password</a>"
                + "</td></tr></table>"
                + "<p style=\"margin:16px 0 0 0;font-size:12px;line-height:1.6;color:#64748B;word-break:break-word;\">"
                + "If the button does not work, open this link:<br><a href=\"" + safeUrl + "\" style=\"color:#16A34A;\">" + safeUrl + "</a></p>"
                + "<p style=\"margin:24px 0 0 0;font-size:13px;line-height:1.6;color:#64748B;\">If you did not request this, you can safely ignore this email.</p>"
                + "</td></tr></table></td></tr></table></body></html>";
    }

    public static void sendVerificationEmail(ServletContext context, String toEmail, String verifyUrl) throws MessagingException {
        String subject = "Verify your email - e-Tasmi";
        String body = "Assalamu alaikum,\n\n" +
                "Please verify your email address by opening the link below:\n\n" +
                verifyUrl + "\n\n" +
                "If you did not create an account, you can ignore this email.";
        String html = buildVerificationEmailHtml(null, null, verifyUrl);
        sendEmail(context, toEmail, subject, body, html);
    }

    public static void sendVerificationCodeEmail(ServletContext context, String toEmail, String code, String fullName) throws MessagingException {
        sendVerificationCodeEmail(context, toEmail, code, fullName, null);
    }

    public static void sendVerificationCodeEmail(ServletContext context, String toEmail, String code, String fullName, String verifyUrl) throws MessagingException {
        String subject = "Your verification code - e-Tasmi";
        String greet = (fullName == null || fullName.isBlank()) ? "Assalamu alaikum" : ("Assalamu alaikum " + fullName.trim());
        String body = greet + ",\n\n" +
                "Your verification code is:\n\n" +
                (code == null ? "" : code) + "\n\n";
        if (verifyUrl != null && !verifyUrl.isBlank()) {
            body += "You can also verify directly using this secure link:\n" +
                    verifyUrl + "\n\n";
        }
        body += "This code expires in 10 minutes. If you did not request this, you can ignore this email.";
        String html = buildVerificationEmailHtml(fullName, code, verifyUrl);
        sendEmail(context, toEmail, subject, body, html);
    }

    private static String buildVerificationEmailHtml(String fullName, String code, String verifyUrl) {
        String safeName = escapeHtml(fullName == null ? "" : fullName.trim());
        String safeCode = escapeHtml(code == null ? "" : code.trim());
        String safeUrl = escapeHtml(verifyUrl == null ? "" : verifyUrl.trim());
        String greeting = safeName.isBlank() ? "Assalamu alaikum," : "Assalamu alaikum " + safeName + ",";
        boolean hasCode = !safeCode.isBlank();
        boolean hasUrl = !safeUrl.isBlank();

        StringBuilder html = new StringBuilder(9000);
        html.append("<!doctype html>")
                .append("<html lang=\"en\">")
                .append("<head>")
                .append("<meta charset=\"UTF-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
                .append("<meta name=\"x-apple-disable-message-reformatting\">")
                .append("<title>Email Verification - eTasmi</title>")
                .append("</head>")
                .append("<body style=\"margin:0;padding:0;background:#F8FAFC;font-family:Inter,Arial,Helvetica,sans-serif;color:#0F172A;\">")
                .append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;\">")
                .append(hasCode ? "Use your eTasmi verification code to activate your account." : "Verify your eTasmi email address to activate your account.")
                .append("</div>")
                .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"background:#F8FAFC;margin:0;padding:32px 16px;\">")
                .append("<tr><td align=\"center\">")
                .append("<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" style=\"max-width:560px;background:#FFFFFF;border:1px solid #E5E7EB;border-radius:16px;overflow:hidden;box-shadow:0 16px 40px rgba(15,23,42,0.08);\">")
                .append("<tr><td align=\"center\" style=\"padding:34px 32px 18px 32px;\">")
                .append("<div style=\"width:64px;height:64px;border-radius:16px;background:#22C55E;display:inline-block;text-align:center;line-height:64px;color:#FFFFFF;font-size:34px;font-weight:800;letter-spacing:0;font-family:Inter,Arial,Helvetica,sans-serif;\">e</div>")
                .append("<div style=\"font-size:24px;font-weight:800;color:#0F172A;margin-top:14px;letter-spacing:0;\">eTasmi</div>")
                .append("<div style=\"font-size:13px;font-weight:600;color:#22C55E;margin-top:4px;letter-spacing:0;\">Quran recitation learning system</div>")
                .append("</td></tr>")
                .append("<tr><td style=\"padding:0 32px 32px 32px;\">")
                .append("<div style=\"height:1px;background:#E5E7EB;margin-bottom:28px;\"></div>")
                .append("<h1 style=\"margin:0;text-align:center;font-size:28px;line-height:1.25;color:#0F172A;font-weight:800;letter-spacing:0;\">Verify Your Email</h1>")
                .append("<p style=\"margin:18px 0 0 0;font-size:15px;line-height:1.7;color:#64748B;text-align:center;\">")
                .append(greeting)
                .append(" Thank you for registering with eTasmi. Verify your email address to activate your account and continue securely.")
                .append("</p>");

        if (hasCode) {
            html.append("<div style=\"margin:28px 0 8px 0;padding:22px 20px;border-radius:14px;background:#F0FDF4;border:1px solid #BBF7D0;text-align:center;\">")
                    .append("<div style=\"font-size:12px;line-height:1.4;text-transform:uppercase;color:#16A34A;font-weight:800;letter-spacing:1.5px;\">Verification Code</div>")
                    .append("<div style=\"margin-top:12px;font-size:34px;line-height:1.15;font-weight:800;color:#0F172A;letter-spacing:6px;font-family:Arial,Helvetica,sans-serif;\">")
                    .append(safeCode)
                    .append("</div>")
                    .append("</div>")
                    .append("<p style=\"margin:14px 0 0 0;font-size:14px;line-height:1.6;color:#64748B;text-align:center;\">This code expires in <strong style=\"color:#0F172A;\">10 minutes</strong>.</p>");
        }

        if (hasUrl) {
            html.append("<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" align=\"center\" style=\"margin:26px auto 0 auto;\">")
                    .append("<tr><td align=\"center\" bgcolor=\"#22C55E\" style=\"border-radius:10px;\">")
                    .append("<a href=\"")
                    .append(safeUrl)
                    .append("\" style=\"display:inline-block;padding:13px 22px;font-size:14px;font-weight:800;color:#FFFFFF;text-decoration:none;border-radius:10px;background:#22C55E;\">Verify email</a>")
                    .append("</td></tr>")
                    .append("</table>")
                    .append("<p style=\"margin:16px 0 0 0;font-size:12px;line-height:1.6;color:#64748B;text-align:center;word-break:break-word;\">If the button does not work, open this link:<br>")
                    .append("<a href=\"")
                    .append(safeUrl)
                    .append("\" style=\"color:#16A34A;text-decoration:underline;\">")
                    .append(safeUrl)
                    .append("</a></p>");
        }

        html.append("<div style=\"margin-top:30px;padding:16px 18px;border-radius:12px;background:#F8FAFC;border:1px solid #E5E7EB;\">")
                .append("<p style=\"margin:0;font-size:13px;line-height:1.6;color:#64748B;text-align:center;\">If you did not create an eTasmi account, you can safely ignore this email.</p>")
                .append("</div>")
                .append("</td></tr>")
                .append("<tr><td style=\"padding:22px 32px;background:#F8FAFC;border-top:1px solid #E5E7EB;text-align:center;\">")
                .append("<p style=\"margin:0;font-size:13px;line-height:1.5;color:#64748B;\">eTasmi</p>")
                .append("<p style=\"margin:6px 0 0 0;font-size:12px;line-height:1.5;color:#94A3B8;\">This is an automated verification email. Please do not reply.</p>")
                .append("</td></tr>")
                .append("</table>")
                .append("</td></tr>")
                .append("</table>")
                .append("</body></html>");
        return html.toString();
    }

    private static String escapeHtml(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&':
                    escaped.append("&amp;");
                    break;
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&#39;");
                    break;
                default:
                    escaped.append(c);
                    break;
            }
        }
        return escaped.toString();
    }

    public static String getConfigurationProblem(ServletContext context) {
        if (context == null) {
            return "SMTP is not configured.";
        }

        SmtpSettings settings = loadSmtpSettings(context);

        // Brevo (HTTPS) mode takes priority when BREVO_API_KEY is set.
        // Only a verified sender address (SMTP_FROM) is required.
        String brevoKey = getenvTransport("BREVO_API_KEY");
        if (brevoKey != null) {
            if (settings.from == null || settings.from.isBlank() || !settings.from.contains("@")) {
                return "BREVO_API_KEY is set but SMTP_FROM is missing or not an email address. Set SMTP_FROM to the sender email you verified in Brevo (Senders & IP > Senders).";
            }
            return null;
        }

        // Resend (HTTPS) mode: SMTP host/port/username/password are ignored.
        // The only thing we need is a verified sender address.
        String resendKey = getenvTransport("RESEND_API_KEY");
        if (resendKey != null) {
            if (settings.from == null || settings.from.isBlank() || !settings.from.contains("@")) {
                return "RESEND_API_KEY is set but SMTP_FROM is missing or not an email address. Set SMTP_FROM to a verified Resend sender (e.g. 'onboarding@resend.dev' for testing, or a verified-domain address in production).";
            }
            return null;
        }

        if (settings.host == null || settings.port == null || settings.from == null) {
            return "SMTP is not configured. Set SMTP_HOST, SMTP_PORT, and SMTP_FROM via environment variables (Railway Variables tab).";
        }
        if (UrlUtil.resolveAppUrlFromEnv() == null) {
            return "APP_URL (or APP_BASE_URL) is not set. Set it to your public Railway URL (e.g. https://your-app.up.railway.app) so reset links are correct.";
        }
        if (settings.username == null || settings.password == null) {
            return "SMTP credentials are missing. Set SMTP_USER and SMTP_PASSWORD (or SMTP_PASS) with your provider's app password.";
        }
        if (!settings.from.contains("@") || settings.from.endsWith(".local")) {
            return "SMTP_FROM must be a real email address (for Gmail, use the same address as SMTP_USER).";
        }
        return null;
    }

    public static boolean canSendEmail(ServletContext context) {
        return getConfigurationProblem(context) == null;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        if (t.startsWith("\uFEFF")) {
            t = t.substring(1).trim();
        }
        return t.isEmpty() ? null : t;
    }

    /** Reads env for API keys; trims and strips UTF-8 BOM from pasted values. */
    private static String getenvTransport(String key) {
        return trimToNull(System.getenv(key));
    }

    private static String getSetting(ServletContext context, String key) {
        String env = trimToNull(System.getenv(key));
        if (env != null) {
            return env;
        }

        String fromContext = trimToNull(context.getInitParameter(key));
        if (fromContext != null) {
            return fromContext;
        }

        // Last resort: load from /email.properties on the classpath.
        // This allows a developer-local file at src/conf/email.properties (copied to the build output).
        String fromFile = trimToNull(getFileConfig().getProperty(key));
        return fromFile;
    }

    private static SmtpSettings loadSmtpSettings(ServletContext context) {
        String host = getSetting(context, "SMTP_HOST");
        String port = getSetting(context, "SMTP_PORT");
        // Railway often uses SMTP_USER / SMTP_PASS; local and older configs use SMTP_USERNAME / SMTP_PASSWORD.
        // getSetting() checks environment variables first, then context-params, then /email.properties.
        String username = firstNonEmpty(getSetting(context, "SMTP_USER"), getSetting(context, "SMTP_USERNAME"));
        String password = firstNonEmpty(getSetting(context, "SMTP_PASS"), getSetting(context, "SMTP_PASSWORD"));
        String from = getSetting(context, "SMTP_FROM");
        String startTls = getSetting(context, "SMTP_STARTTLS");

        if ((from == null || from.isBlank()) && username != null && username.contains("@")) {
            from = username;
        }

        return new SmtpSettings(host, port, username, password, from, startTls);
    }

    /**
     * Required for pure SMTP (not Brevo/Resend). Ensures public verification links
     * can be built via {@code APP_BASE_URL} and that credentials are present.
     */
    private static void ensureSmtpAndPublicBaseEnv(SmtpSettings settings) {
        String smtpHost = settings.host;
        String smtpPort = settings.port;
        String smtpUser = settings.username;
        String smtpPass = settings.password;
        String smtpFrom = settings.from;
        String appBaseUrl = UrlUtil.resolveAppUrlFromEnv();
        if (smtpHost == null || smtpPort == null || smtpUser == null || smtpPass == null || smtpFrom == null || appBaseUrl == null) {
            LOGGER.severe("SMTP or APP_URL environment variables are missing. host=" + smtpHost
                    + ", port=" + smtpPort
                    + ", smtpUserSet=" + (smtpUser != null)
                    + ", smtpPassSet=" + (smtpPass != null)
                    + ", smtpFrom=" + smtpFrom
                    + ", appUrlSet=" + (appBaseUrl != null));
            throw new IllegalStateException("SMTP or APP_URL environment variables are missing.");
        }
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static Properties getFileConfig() {
        Properties existing = FILE_CONFIG;
        if (existing != null) {
            return existing;
        }

        Properties loaded = new Properties();
        try (InputStream in = EmailUtil.class.getResourceAsStream("/email.properties")) {
            if (in != null) {
                loaded.load(in);
                LOGGER.info("Loaded SMTP config from classpath resource: /email.properties");
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Failed to load /email.properties", ex);
        }

        FILE_CONFIG = loaded;
        return loaded;
    }

    private static final class SmtpSettings {
        private final String host;
        private final String port;
        private final String username;
        private final String password;
        private final String from;
        private final String startTls;

        private SmtpSettings(String host, String port, String username, String password, String from, String startTls) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
            this.from = from;
            this.startTls = startTls;
        }
    }
}
