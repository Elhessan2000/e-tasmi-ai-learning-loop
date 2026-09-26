package util;

import model.entity.TasmiSession;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Builds a participant join URL for live sessions, preferring the stored Zoom join link
 * and falling back to a generic Zoom web join URL when only the meeting id is available.
 */
public final class ZoomJoinLinkUtil {
    private ZoomJoinLinkUtil() {
    }

    public static String resolveParticipantJoinUrl(TasmiSession session) {
        if (session == null) {
            return null;
        }
        String fromStored = MeetingLinkUtil.toStudentJoinLink(session.getMeetingLink());
        if (fromStored != null) {
            String trimmed = fromStored.trim();
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return trimmed;
            }
        }
        Long zoomId = session.getZoomMeetingId();
        if (zoomId == null || zoomId <= 0) {
            return null;
        }
        String provider = session.getLiveProvider();
        if (provider == null || !"ZOOM".equalsIgnoreCase(provider.trim())) {
            return null;
        }
        return buildZoomPublicJoinLink(zoomId, session.getMeetingPassword());
    }

    private static String buildZoomPublicJoinLink(long meetingId, String passcode) {
        String base = "https://zoom.us/j/" + meetingId;
        if (passcode == null || passcode.isBlank()) {
            return base;
        }
        return base + "?pwd=" + URLEncoder.encode(passcode.trim(), StandardCharsets.UTF_8);
    }

    /**
     * Zoom participant links often include {@code pwd=} in the query; use when {@code meeting_password} was not persisted.
     */
    /**
     * True when the join URL query string includes a Zoom passcode parameter but parsing may still fail.
     */
    public static boolean joinUrlQueryImpliesPasscode(String meetingLink) {
        if (meetingLink == null || meetingLink.isBlank()) {
            return false;
        }
        try {
            String q = new URI(meetingLink.trim()).getRawQuery();
            if (q == null || q.isBlank()) {
                return false;
            }
            String lower = q.toLowerCase();
            return lower.contains("pwd=") || lower.contains("password=");
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String extractPwdFromJoinUrl(String meetingLink) {
        if (meetingLink == null || meetingLink.isBlank()) {
            return "";
        }
        try {
            URI uri = new URI(meetingLink.trim());
            String q = uri.getRawQuery();
            if (q == null || q.isBlank()) {
                return "";
            }
            for (String pair : q.split("&")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = pair.substring(0, eq);
                if ("pwd".equalsIgnoreCase(key)) {
                    String raw = pair.substring(eq + 1);
                    return URLDecoder.decode(raw, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
