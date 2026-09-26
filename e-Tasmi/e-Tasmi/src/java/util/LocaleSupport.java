package util;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Locale helpers for SEO URL prefixes (/en/, /ar/, /ms/) and JSP link building.
 */
public final class LocaleSupport {

    public static final String LOCALE_REQUEST_ATTR = "locale";
    /** Primary locale attribute for JSP (SEO filter). */
    public static final String CURRENT_LOCALE_ATTR = "currentLocale";
    /** Text direction attribute for JSP ({@code ltr} / {@code rtl}). */
    public static final String CURRENT_DIR_ATTR = "currentDir";
    public static final String COOKIE_NAME = "etasmi.locale";
    public static final String DEFAULT_LOCALE = "en";

    private static final Set<String> SUPPORTED = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList("en", "ar", "ms")));
    private static final Pattern LOCALE_PREFIX = Pattern.compile("^/(en|ar|ms)(?=($|/))");

    private LocaleSupport() {
    }

    public static boolean isSupported(String locale) {
        return locale != null && SUPPORTED.contains(locale);
    }

    public static Set<String> supportedLocales() {
        return SUPPORTED;
    }

    public static String directionFor(String locale) {
        return "ar".equals(locale) ? "rtl" : "ltr";
    }

    public static String getLocale(HttpServletRequest request) {
        Object current = request.getAttribute(CURRENT_LOCALE_ATTR);
        if (current instanceof String && isSupported((String) current)) {
            return (String) current;
        }

        Object attr = request.getAttribute(LOCALE_REQUEST_ATTR);
        if (attr instanceof String && isSupported((String) attr)) {
            return (String) attr;
        }

        String fromCookie = readCookie(request, COOKIE_NAME);
        if (isSupported(fromCookie)) {
            return fromCookie;
        }

        String fromHeader = normalizeLanguageTag(request.getHeader("Accept-Language"));
        if (isSupported(fromHeader)) {
            return fromHeader;
        }

        return DEFAULT_LOCALE;
    }

    public static String getDirection(HttpServletRequest request) {
        Object dir = request.getAttribute(CURRENT_DIR_ATTR);
        if (dir instanceof String && ("ltr".equals(dir) || "rtl".equals(dir))) {
            return (String) dir;
        }
        return directionFor(getLocale(request));
    }

    /** Resolve locale from cookie / Accept-Language before URL prefix is applied. */
    public static String resolvePreferredLocale(HttpServletRequest request) {
        return getLocale(request);
    }

    /** Publish locale + direction on the request and persist cookie. */
    public static void applyRequestLocale(HttpServletRequest request, HttpServletResponse response, String locale) {
        String safe = isSupported(locale) ? locale : DEFAULT_LOCALE;
        String dir = directionFor(safe);
        request.setAttribute(CURRENT_LOCALE_ATTR, safe);
        request.setAttribute(CURRENT_DIR_ATTR, dir);
        request.setAttribute(LOCALE_REQUEST_ATTR, safe);
        persistLocaleCookie(response, safe);
    }

    public static LocaleParseResult parsePrefixedPath(String pathWithinContext) {
        if (pathWithinContext == null || pathWithinContext.isEmpty()) {
            pathWithinContext = "/";
        }

        Matcher matcher = LOCALE_PREFIX.matcher(pathWithinContext);
        if (!matcher.find()) {
            return null;
        }

        String locale = matcher.group(1);
        String stripped = pathWithinContext.substring(matcher.end());
        if (stripped.isEmpty()) {
            stripped = "/home";
        }
        return new LocaleParseResult(locale, stripped);
    }

    public static String stripLocalePrefix(String pathWithinContext) {
        LocaleParseResult parsed = parsePrefixedPath(pathWithinContext);
        if (parsed == null) {
            return pathWithinContext == null || pathWithinContext.isEmpty() ? "/" : pathWithinContext;
        }
        return parsed.strippedPath();
    }

    public static String localizedPath(String locale, String path) {
        String safeLocale = isSupported(locale) ? locale : DEFAULT_LOCALE;
        String normalized = (path == null || path.trim().isEmpty()) ? "/home" : path.trim();
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }

        LocaleParseResult parsed = parsePrefixedPath(normalized);
        if (parsed != null) {
            normalized = parsed.strippedPath();
        }

        return "/" + safeLocale + normalized;
    }

    public static String localizedUrl(HttpServletRequest request, String path) {
        return request.getContextPath() + localizedPath(getLocale(request), path);
    }

    public static void persistLocaleCookie(HttpServletResponse response, String locale) {
        if (!isSupported(locale)) {
            locale = DEFAULT_LOCALE;
        }
        Cookie cookie = new Cookie(COOKIE_NAME, locale);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 24 * 365);
        cookie.setHttpOnly(false);
        response.addCookie(cookie);
    }

    public static boolean shouldBypassLocaleRouting(String pathWithinContext) {
        if (pathWithinContext == null || pathWithinContext.isEmpty() || "/".equals(pathWithinContext)) {
            return false;
        }

        if (pathWithinContext.startsWith("/assets/")
                || pathWithinContext.startsWith("/css/")
                || pathWithinContext.startsWith("/js/")
                || pathWithinContext.startsWith("/learnhub-dist/")
                || pathWithinContext.startsWith("/WEB-INF/")
                || pathWithinContext.startsWith("/jsp/")) {
            return true;
        }

        int dot = pathWithinContext.lastIndexOf('.');
        if (dot > pathWithinContext.lastIndexOf('/')) {
            String ext = pathWithinContext.substring(dot + 1).toLowerCase(Locale.ROOT);
            switch (ext) {
                case "css":
                case "js":
                case "json":
                case "png":
                case "jpg":
                case "jpeg":
                case "gif":
                case "svg":
                case "webp":
                case "ico":
                case "woff":
                case "woff2":
                case "ttf":
                case "map":
                case "mp4":
                case "webmanifest":
                    return true;
                default:
                    break;
            }
        }

        return false;
    }

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String normalizeLanguageTag(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.trim().isEmpty()) {
            return null;
        }
        String first = acceptLanguage.split(",")[0].trim();
        if (first.isEmpty()) {
            return null;
        }
        String base = first.split("-")[0].toLowerCase(Locale.ROOT);
        return isSupported(base) ? base : null;
    }

    public static final class LocaleParseResult {
        private final String locale;
        private final String strippedPath;

        public LocaleParseResult(String locale, String strippedPath) {
            this.locale = locale;
            this.strippedPath = strippedPath;
        }

        public String locale() {
            return locale;
        }

        public String strippedPath() {
            return strippedPath;
        }
    }
}
