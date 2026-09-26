package model.service.quran;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * Conservative HTML allowlist for tafsir excerpts from Quran Foundation (defence in depth alongside CSP).
 */
public final class QuranTafsirHtmlSanitize {

    private static final int MAX_HTML_CH = 380_000;

    private QuranTafsirHtmlSanitize() {
    }

    public static String sanitize(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return "";
        }
        String s = rawHtml.length() > MAX_HTML_CH ? rawHtml.substring(0, MAX_HTML_CH) : rawHtml;
        return Jsoup.clean(s, Safelist.relaxed());
    }
}
