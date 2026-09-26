package util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

/**
 * Meeting SDK JWT for Zoom Web client (participant or host). Signed with the Meeting SDK secret.
 *
 * @see <a href="https://developers.zoom.us/docs/meeting-sdk/auth/">Meeting SDK authorization</a>
 */
public final class ZoomMeetingSdkJwt {
    private static final int MIN_TTL_SECONDS = 1800;
    private static final int DEFAULT_TTL_SECONDS = 7200;

    private ZoomMeetingSdkJwt() {
    }

    /**
     * @param sdkKey    Meeting SDK Client ID ({@code ZOOM_MEETING_SDK_KEY})
     * @param sdkSecret Meeting SDK Client Secret ({@code ZOOM_MEETING_SDK_SECRET})
     * @param meetingNumber numeric meeting id as string
     * @param role          0 = participant, 1 = host
     */
    public static String sign(String sdkKey, String sdkSecret, String meetingNumber, int role)
            throws NoSuchAlgorithmException, InvalidKeyException {
        if (sdkKey == null || sdkKey.isBlank() || sdkSecret == null || sdkSecret.isBlank()) {
            throw new IllegalArgumentException("SDK key and secret are required.");
        }
        if (meetingNumber == null || meetingNumber.isBlank()) {
            throw new IllegalArgumentException("Meeting number is required.");
        }
        if (role != 0 && role != 1) {
            throw new IllegalArgumentException("Role must be 0 (participant) or 1 (host).");
        }

        long iat = Instant.now().getEpochSecond() - 30;
        long exp = iat + DEFAULT_TTL_SECONDS;
        if (exp < iat + MIN_TTL_SECONDS) {
            exp = iat + MIN_TTL_SECONDS;
        }

        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        /* Web: include video_webrtc_mode per https://developers.zoom.us/docs/meeting-sdk/auth/ (optional; helps WebRTC path). */
        String payloadJson = "{"
                + "\"appKey\":\"" + jsonEscape(sdkKey) + "\","
                + "\"sdkKey\":\"" + jsonEscape(sdkKey) + "\","
                + "\"mn\":\"" + jsonEscape(meetingNumber) + "\","
                + "\"role\":" + role + ","
                + "\"iat\":" + iat + ","
                + "\"exp\":" + exp + ","
                + "\"tokenExp\":" + exp + ","
                + "\"video_webrtc_mode\":1"
                + "}";

        String header = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(sdkSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] sig = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        String signature = base64UrlEncode(sig);
        return signingInput + "." + signature;
    }

    private static String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
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

    private static String base64UrlEncode(byte[] data) {
        return Base64.getEncoder().encodeToString(data)
                .replace('+', '-')
                .replace('/', '_')
                .replace("=", "");
    }
}
