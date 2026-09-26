package model.service.quran;

import javax.servlet.http.HttpServletResponse;

/**
 * Stable error codes/messages for student Quran Library JSON (no secrets, no OAuth tokens).
 */
public final class QuranLibraryUpstreamErrors {

    private QuranLibraryUpstreamErrors() {
    }

    /**
     * Classifies downstream failures without exposing Quran Foundation payloads.
     */
    public static String classify(Throwable ex) {
        if (ex == null) {
            return "load_failed";
        }
        String msg = safeMsg(ex.getMessage());
        if (msg.contains("API is not configured") || msg.contains("not configured (missing")) {
            return "not_configured";
        }
        if (msg.contains("token request failed") || msg.contains("token response missing")) {
            return "oauth_token_failed";
        }
        if (msg.contains("HTTP 403")) {
            return "upstream_forbidden";
        }
        if (msg.contains("HTTP 404")) {
            return "upstream_not_found";
        }
        if (msg.contains("HTTP 401")) {
            return "upstream_unauthorized";
        }
        if (msg.contains("HTTP ")) {
            return "upstream_error";
        }
        return "load_failed";
    }

    /** HTTP status suitable for servlet response (still JSON body with {@code ok:false}). */
    public static int servletStatus(String errorCode, int upstreamHttpIfKnown) {
        if ("not_configured".equals(errorCode) || "oauth_token_failed".equals(errorCode)) {
            return HttpServletResponse.SC_BAD_GATEWAY;
        }
        if ("upstream_forbidden".equals(errorCode)) {
            if (upstreamHttpIfKnown >= 400 && upstreamHttpIfKnown < 600) {
                return upstreamHttpIfKnown;
            }
            return HttpServletResponse.SC_FORBIDDEN;
        }
        if ("upstream_not_found".equals(errorCode)) {
            return HttpServletResponse.SC_BAD_GATEWAY;
        }
        return HttpServletResponse.SC_BAD_GATEWAY;
    }

    /** Student-safe sentence (English) for UI. */
    public static String studentMessage(String errorCode) {
        return switch (errorCode != null ? errorCode : "") {
            case "not_configured" ->
                    "The Quran Library is not fully configured on the server yet. Ask your administrator.";
            case "oauth_token_failed" ->
                    "The server could not sign in with Quran Foundation (OAuth). Check QF_ENV, credentials, "
                            + "and that the OAuth scope matches your account.";
            case "upstream_forbidden" ->
                    "Quran Foundation rejected this request (forbidden). This API account may not include that dataset.";
            case "upstream_unauthorized" ->
                    "Quran Foundation rejected the access token for this request.";
            case "upstream_not_found" ->
                    "Quran Foundation could not find the requested resource for this deployment.";
            case "upstream_error" ->
                    "Quran Foundation returned an error while loading content.";
            default -> "We could not load this right now through e-Tasmi. Try again in a moment.";
        };
    }

    /**
     * Clearer entitlement messaging per area (still no outbound payload echo).
     */
    public static String contextualMessage(String errorCode, String feature) {
        String code = errorCode == null ? "" : errorCode;
        if ("upstream_forbidden".equals(code)) {
            if ("translations".equals(feature)) {
                return "Translation resources are not enabled for this Quran Foundation API account.";
            }
            if ("tafsirs".equals(feature)) {
                return "Tafsir resources are not enabled for this Quran Foundation API account.";
            }
            if ("reciter_catalog".equals(feature)) {
                return "This Quran Foundation account does not have reciter catalogue access.";
            }
            if ("chapter_audio".equals(feature)) {
                return "This Quran Foundation account may not include chapter audio for this reciter.";
            }
            if ("chapters".equals(feature)) {
                return "The Quran chapter list could not be loaded from Quran Foundation with this API account.";
            }
            if ("verses".equals(feature)) {
                return "Ayahs could not be loaded from Quran Foundation for this surah.";
            }
        }
        return studentMessage(code);
    }

    public static String audioEntitlementHint(int upstreamStatus) {
        if (upstreamStatus == 403) {
            return "This Quran Foundation account may not include audio / chapter-reciter access.";
        }
        if (upstreamStatus == 404) {
            return "That chapter or reciter was not found upstream.";
        }
        return "Chapter audio could not be loaded.";
    }

    private static String safeMsg(String s) {
        return s == null ? "" : s;
    }
}
