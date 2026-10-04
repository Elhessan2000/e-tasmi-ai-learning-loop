package model.service.quran;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Trusted reference backed by the existing Quran Foundation Content API client.
 * Does not use {@link QuranBundledCatalog}: bundled text is a library fallback,
 * not an attributable Challenge reference.
 */
public final class QuranFoundationReferenceProvider implements TrustedReferenceProvider {

    private static final Logger LOGGER = Logger.getLogger(QuranFoundationReferenceProvider.class.getName());

    /** Small enough that 2:45–55 spans two hops, matching the Phase 1 test plan. */
    private static final int PAGE_SIZE = 50;
    private static final int MAX_PAGES = 48;

    private final QuranLibraryVersesService versesService;

    public QuranFoundationReferenceProvider() {
        this(new QuranLibraryVersesService());
    }

    QuranFoundationReferenceProvider(QuranLibraryVersesService versesService) {
        this.versesService = versesService;
    }

    @Override
    public TrustedReferenceResult fetch(int surah, int ayahStart, int ayahEnd) {
        String rangeError = QuranPassageRange.validate(surah, ayahStart, ayahEnd);
        if (rangeError != null) {
            return TrustedReferenceResult.unavailable(rangeError);
        }
        if (!QuranFoundationConfig.load().isComplete()) {
            return TrustedReferenceResult.unavailable("Quran Foundation is not configured.");
        }

        try {
            TreeMap<Integer, TrustedReference.Verse> byAyah = new TreeMap<>();
            collectFromPages(surah, ayahStart, ayahEnd, byAyah);
            fillMissingByKey(surah, ayahStart, ayahEnd, byAyah);

            int expected = ayahEnd - ayahStart + 1;
            if (byAyah.size() != expected) {
                return TrustedReferenceResult.unavailable(
                        "Trusted reference returned fewer verses than requested.");
            }

            List<TrustedReference.Verse> verses = new ArrayList<>(byAyah.values());
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < verses.size(); i++) {
                if (i > 0) {
                    text.append(' ');
                }
                text.append(verses.get(i).getUthmaniText());
            }
            String verseKeys = surah + ":" + ayahStart + "-" + surah + ":" + ayahEnd;
            return TrustedReferenceResult.ok(new TrustedReference(
                    TrustedReference.SOURCE_QURAN_FOUNDATION,
                    verseKeys,
                    text.toString(),
                    verses));
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Quran Foundation trusted-reference fetch failed: surah={0} {1}-{2}",
                    new Object[]{surah, ayahStart, ayahEnd});
            return TrustedReferenceResult.unavailable("Trusted Qur'an reference could not be retrieved.");
        }
    }

    private void collectFromPages(int surah, int ayahStart, int ayahEnd,
                                  TreeMap<Integer, TrustedReference.Verse> byAyah) throws IOException {
        Integer page = 1;
        for (int hop = 0; hop < MAX_PAGES && page != null && page > 0; hop++) {
            QuranVersesParse.VersesSlice slice = versesService.fetchPage(surah, page, PAGE_SIZE, null, null);
            int highestOnPage = 0;
            for (Map<String, Object> row : slice.verses()) {
                Integer number = verseNumber(row);
                if (number == null) {
                    continue;
                }
                highestOnPage = Math.max(highestOnPage, number);
                if (number < ayahStart || number > ayahEnd || byAyah.containsKey(number)) {
                    continue;
                }
                TrustedReference.Verse verse = toVerse(surah, number, row);
                if (verse == null) {
                    throw new IOException("Missing Uthmani text for " + surah + ":" + number);
                }
                byAyah.put(number, verse);
            }
            if (highestOnPage >= ayahEnd && byAyah.size() >= (ayahEnd - ayahStart + 1)) {
                return;
            }
            page = nextPage(slice);
        }
    }

    private void fillMissingByKey(int surah, int ayahStart, int ayahEnd,
                                  TreeMap<Integer, TrustedReference.Verse> byAyah) throws IOException {
        for (int n = ayahStart; n <= ayahEnd; n++) {
            if (byAyah.containsKey(n)) {
                continue;
            }
            Map<String, Object> row = versesService.fetchVerseByKey(surah + ":" + n, null, null);
            TrustedReference.Verse verse = toVerse(surah, n, row);
            if (verse == null) {
                throw new IOException("Missing Uthmani text for " + surah + ":" + n);
            }
            byAyah.put(n, verse);
        }
    }

    private static TrustedReference.Verse toVerse(int surah, int ayah, Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        Object raw = row.get("textUthmani");
        String text = raw == null ? null : raw.toString().trim();
        if (text == null || text.isEmpty()) {
            return null;
        }
        Object keyRaw = row.get("verseKey");
        String key = keyRaw == null || keyRaw.toString().isBlank()
                ? (surah + ":" + ayah)
                : keyRaw.toString().trim();
        return new TrustedReference.Verse(key, text);
    }

    private static Integer verseNumber(Map<String, Object> row) {
        Object raw = row.get("verseNumber");
        if (raw instanceof Number) {
            return ((Number) raw).intValue();
        }
        if (raw != null) {
            try {
                return Integer.parseInt(raw.toString().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Object keyRaw = row.get("verseKey");
        if (keyRaw == null) {
            return null;
        }
        String key = keyRaw.toString();
        int colon = key.lastIndexOf(':');
        if (colon < 0 || colon == key.length() - 1) {
            return null;
        }
        try {
            return Integer.parseInt(key.substring(colon + 1).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer nextPage(QuranVersesParse.VersesSlice slice) {
        if (slice == null || slice.pagination() == null) {
            return null;
        }
        Object raw = slice.pagination().get("nextPage");
        if (raw instanceof Number) {
            int next = ((Number) raw).intValue();
            return next > 0 ? next : null;
        }
        return null;
    }
}
