package model.service.quran;

/**
 * Validation for the structured Qur'an passage stored on a Tasmi session
 * ({@code tasmi_session.surah_number}, {@code ayah_start}, {@code ayah_end}).
 *
 * <p>The database CHECK constraints can only enforce the surah bound (1..114)
 * and {@code ayah_start <= ayah_end}. The number of ayāt differs per surah, so
 * the upper bound has to be checked here.</p>
 *
 * <p>Deliberately self-contained: unlike {@link QuranBundledCatalog} this class
 * needs no {@code ServletContext}, no bundled resource, and no network, so it
 * can be used from any service, filter, or test.</p>
 *
 * <p>Ayah counts follow the Hafs ʿan ʿĀsim numbering used by the Qur'an
 * Foundation content API (6236 ayāt in total, basmala counted as an ayah only
 * in Surah Al-Fatiha).</p>
 */
public final class QuranPassageRange {

    /** Number of surahs in the Qur'an. */
    public static final int SURAH_COUNT = 114;

    /** Ayah count per surah; index 0 holds Surah 1. */
    private static final int[] AYAH_COUNTS = {
            7, 286, 200, 176, 120, 165, 206, 75, 129, 109,
            123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
            112, 78, 118, 64, 77, 227, 93, 88, 69, 60,
            34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
            54, 53, 89, 59, 37, 35, 38, 29, 18, 45,
            60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
            14, 11, 11, 18, 12, 12, 30, 52, 52, 44,
            28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
            29, 19, 36, 25, 22, 17, 19, 26, 30, 20,
            15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
            11, 8, 3, 9, 5, 4, 7, 3, 6, 3,
            5, 4, 5, 6
    };

    private QuranPassageRange() {
    }

    /**
     * @param surahNumber 1..114
     * @return number of ayāt in that surah
     * @throws IllegalArgumentException if the surah number is out of range
     */
    public static int ayahCount(int surahNumber) {
        if (surahNumber < 1 || surahNumber > SURAH_COUNT) {
            throw new IllegalArgumentException("Surah number must be between 1 and " + SURAH_COUNT + ".");
        }
        return AYAH_COUNTS[surahNumber - 1];
    }

    /**
     * Validates a structured passage.
     *
     * <p>A session is allowed to carry no structured passage at all — every
     * session created before this feature existed is in that state — so three
     * nulls are valid. Supplying only part of the triple is not.</p>
     *
     * @return {@code null} when the passage is valid, otherwise a message
     *         suitable for display to the instructor
     */
    public static String validate(Integer surahNumber, Integer ayahStart, Integer ayahEnd) {
        if (surahNumber == null && ayahStart == null && ayahEnd == null) {
            return null;
        }
        if (surahNumber == null || ayahStart == null || ayahEnd == null) {
            return "Select a surah and both the first and last ayah, or leave all three empty.";
        }
        if (surahNumber < 1 || surahNumber > SURAH_COUNT) {
            return "Surah number must be between 1 and " + SURAH_COUNT + ".";
        }
        int total = AYAH_COUNTS[surahNumber - 1];
        if (ayahStart < 1) {
            return "The first ayah must be 1 or greater.";
        }
        if (ayahEnd > total) {
            return "Surah " + surahNumber + " has " + total + " ayat, so the last ayah cannot be " + ayahEnd + ".";
        }
        if (ayahStart > ayahEnd) {
            return "The first ayah cannot come after the last ayah.";
        }
        return null;
    }
}
