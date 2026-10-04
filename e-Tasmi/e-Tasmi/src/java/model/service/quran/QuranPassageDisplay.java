package model.service.quran;

import model.entity.TasmiSession;

/**
 * Display-only passage labels. Never used as comparison text.
 */
public final class QuranPassageDisplay {

    private QuranPassageDisplay() {
    }

    public static String format(TasmiSession session) {
        if (session == null) {
            return "";
        }
        return format(session.getSurahNumber(), session.getAyahStart(), session.getAyahEnd(),
                session.getQuranPortion());
    }

    public static String format(Integer surah, Integer ayahStart, Integer ayahEnd, String fallback) {
        if (surah != null && ayahStart != null && ayahEnd != null
                && surah >= 1 && surah <= 114 && ayahStart >= 1 && ayahEnd >= ayahStart) {
            String ayahs = ayahStart.equals(ayahEnd)
                    ? String.valueOf(ayahStart)
                    : ayahStart + "\u2013" + ayahEnd;
            String name = QuranBundledCatalog.chapterNameSimple(surah);
            if (name != null) {
                return "Surah " + name + " \u00b7 " + ayahs;
            }
            return surah + ":" + ayahStart + "\u2013" + ayahEnd;
        }
        return fallback == null ? "" : fallback.trim();
    }
}
