package model.service.quranpedia;

import model.service.quran.QuranPassageRange;
import model.service.quran.TrustedReference;
import model.service.quran.TrustedReferenceProvider;
import model.service.quran.TrustedReferenceResult;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Learning Loop trusted reference from Quranpedia Hafs ({@code GET /mushafs/1/{surah}}).
 * Copies the documented {@code text} field verbatim. Does not call Quran Foundation
 * and does not fill gaps from the bundled catalogue.
 */
public final class QuranpediaReferenceProvider implements TrustedReferenceProvider {

    private static final Logger LOGGER = Logger.getLogger(QuranpediaReferenceProvider.class.getName());
    /** Documented Hafs mushaf id. */
    static final int HAFS_MUSHAF_ID = 1;

    private final QuranpediaClient client;

    public QuranpediaReferenceProvider() {
        this(new QuranpediaClient());
    }

    QuranpediaReferenceProvider(QuranpediaClient client) {
        this.client = client;
    }

    @Override
    public TrustedReferenceResult fetch(int surah, int ayahStart, int ayahEnd) {
        String rangeError = QuranPassageRange.validate(surah, ayahStart, ayahEnd);
        if (rangeError != null) {
            return TrustedReferenceResult.unavailable(rangeError);
        }
        if (!QuranpediaConfig.load().isConfigured()) {
            return TrustedReferenceResult.unavailable("Quranpedia is not configured.");
        }

        try {
            HttpResponse<String> response = client.get("/mushafs/" + HAFS_MUSHAF_ID + "/" + surah);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return TrustedReferenceResult.unavailable("Trusted Qur'an reference could not be retrieved.");
            }
            Object parsed = QuranpediaJson.parse(response.body());
            if (!(parsed instanceof List<?>)) {
                return TrustedReferenceResult.unavailable("Trusted Qur'an reference could not be retrieved.");
            }

            TreeMap<Integer, TrustedReference.Verse> byAyah = new TreeMap<>();
            for (Object item : (List<?>) parsed) {
                if (!(item instanceof Map<?, ?>)) {
                    continue;
                }
                Map<?, ?> row = (Map<?, ?>) item;
                Integer number = asInt(row.get("number"));
                if (number == null || number < ayahStart || number > ayahEnd || byAyah.containsKey(number)) {
                    continue;
                }
                String text = asString(row.get("text"));
                if (text == null || text.isBlank()) {
                    return TrustedReferenceResult.unavailable(
                            "Trusted reference is missing ayah text for " + surah + ":" + number + ".");
                }
                byAyah.put(number, new TrustedReference.Verse(surah + ":" + number, text));
            }

            int expected = ayahEnd - ayahStart + 1;
            if (byAyah.size() != expected) {
                return TrustedReferenceResult.unavailable(
                        "Trusted reference returned fewer verses than requested.");
            }

            List<TrustedReference.Verse> verses = new ArrayList<>(byAyah.values());
            StringBuilder joined = new StringBuilder();
            for (int i = 0; i < verses.size(); i++) {
                if (i > 0) {
                    joined.append(' ');
                }
                joined.append(verses.get(i).getUthmaniText());
            }
            String verseKeys = surah + ":" + ayahStart + "-" + surah + ":" + ayahEnd;
            return TrustedReferenceResult.ok(new TrustedReference(
                    TrustedReference.SOURCE_QURANPEDIA,
                    verseKeys,
                    joined.toString(),
                    verses));
        } catch (IOException | RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Quranpedia trusted-reference fetch failed: surah={0} {1}-{2} ({3})",
                    new Object[]{surah, ayahStart, ayahEnd, ex.getClass().getSimpleName()});
            return TrustedReferenceResult.unavailable("Trusted Qur'an reference could not be retrieved.");
        }
    }

    private static Integer asInt(Object value) {
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        return null;
    }

    private static String asString(Object value) {
        return value instanceof String ? (String) value : null;
    }
}
