package setup;

import model.service.analysis.ComparisonOutcome;
import model.service.analysis.FindingType;
import model.service.analysis.RecitationComparisonEngine;
import model.service.analysis.RecitationFinding;
import model.service.quran.TrustedReference;

import java.util.ArrayList;
import java.util.List;

/**
 * Reproducible check of the deterministic recitation comparison.
 *
 * <p>Runs with no network, no API key, and no database, so the correctness of the Arabic
 * normalisation and the finding generation can be verified at any time and recorded as
 * evidence. Fixtures are Surah Al-Fatiha in Uthmani orthography and hand-written transcripts.</p>
 *
 * Usage inside the app container:
 * java -cp "/usr/local/tomcat/webapps/ROOT/WEB-INF/classes:/usr/local/tomcat/webapps/ROOT/WEB-INF/lib/*" setup.ArabicComparisonSelfCheck
 */
public final class ArabicComparisonSelfCheck {

    // Surah Al-Fatiha 1:1-1:7, Uthmani orthography (alef wasla, dagger alef, full harakat).
    private static final String[] FATIHA_UTHMANI = {
            "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
            "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ",
            "ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
            "مَٰلِكِ يَوْمِ ٱلدِّينِ",
            "إِيَّاكَ نَعْبُدُ وَإِيَّاكَ نَسْتَعِينُ",
            "ٱهْدِنَا ٱلصِّرَٰطَ ٱلْمُسْتَقِيمَ",
            "صِرَٰطَ ٱلَّذِينَ أَنْعَمْتَ عَلَيْهِمْ غَيْرِ ٱلْمَغْضُوبِ عَلَيْهِمْ وَلَا ٱلضَّآلِّينَ"
    };

    private static int failures = 0;
    private static int checks = 0;

    private ArabicComparisonSelfCheck() {
    }

    public static void main(String[] args) {
        TrustedReference fatiha = reference(1, FATIHA_UTHMANI);

        caseIdenticalText(fatiha);
        caseOrthographicVariantsOnly(fatiha);
        caseOneWordRemoved(fatiha);
        caseOneWordSubstituted(fatiha);
        caseOneWordDuplicated(fatiha);
        caseWrongSurah(fatiha);
        casePassageMismatchSuppressesWordFindings(fatiha);
        caseReferenceUnavailable();
        caseVerificationDefaults(fatiha);

        System.out.println();
        System.out.println("Checks run: " + checks + ", failures: " + failures);
        if (failures > 0) {
            System.out.println("RESULT: FAIL");
            System.exit(1);
        }
        System.out.println("RESULT: PASS");
    }

    // --- Cases -----------------------------------------------------------------------------

    /** Identical text must produce no findings at all. */
    private static void caseIdenticalText(TrustedReference reference) {
        ComparisonOutcome outcome = RecitationComparisonEngine.compare(reference, join(FATIHA_UTHMANI));
        header("1. Identical recitation", outcome);
        expect("no findings", outcome.getFindings().isEmpty());
        expect("accuracy is 100%", Math.abs(outcome.getAccuracyPercent() - 100.0) < 0.001);
        expect("every reference word counted correct",
                outcome.getCorrectCount() == outcome.getReferenceWordCount());
    }

    /**
     * The decisive case. Transcript uses plain modern spelling: no alef wasla, no dagger alef,
     * no harakat, plus a tatweel. None of that is a recitation error.
     */
    private static void caseOrthographicVariantsOnly(TrustedReference reference) {
        String[] plain = {
                "بسم الله الرحمن الرحيم",
                "الحمد لله رب العالمين",
                "الرحمن الرحيم",
                "مالك يوم الديـن",                 // contains a tatweel
                "اياك نعبد واياك نستعين",
                "اهدنا الصراط المستقيم",            // dagger alef written as a full alef
                "صراط الذين انعمت عليهم غير المغضوب عليهم ولا الضالين"
        };
        ComparisonOutcome outcome = RecitationComparisonEngine.compare(reference, join(plain));
        header("2. Orthographic variants only", outcome);
        expect("zero findings for orthography-only differences", outcome.getFindings().isEmpty());
        if (!outcome.getFindings().isEmpty()) {
            for (RecitationFinding finding : outcome.getFindings()) {
                System.out.println("      unexpected " + finding.getType() + " at " + finding.locationLabel());
            }
        }
    }

    /** Dropping "رَبِّ" from 1:2 must yield exactly one MISSING_WORD at 1:2 position 3. */
    private static void caseOneWordRemoved(TrustedReference reference) {
        String[] transcript = FATIHA_UTHMANI.clone();
        transcript[1] = "ٱلْحَمْدُ لِلَّهِ ٱلْعَٰلَمِينَ";
        ComparisonOutcome outcome = RecitationComparisonEngine.compare(reference, join(transcript));
        header("3. One word removed", outcome);
        expect("exactly one finding", outcome.getFindings().size() == 1);
        RecitationFinding finding = first(outcome);
        expect("type is MISSING_WORD", finding != null && finding.getType() == FindingType.MISSING_WORD);
        expect("located at 1:2 word 3", finding != null && "1:2#3".equals(finding.locationLabel()));
        expect("heard text is absent", finding != null && finding.getHeardText() == null);
        expect("expected text is the original surface form",
                finding != null && "رَبِّ".equals(finding.getExpectedText()));
    }

    /** Substituting a word must yield exactly one INCORRECT_WORD carrying both surfaces. */
    private static void caseOneWordSubstituted(TrustedReference reference) {
        String[] transcript = FATIHA_UTHMANI.clone();
        transcript[3] = "مَٰلِكِ يَوْمِ ٱلْقِيَامَةِ";
        ComparisonOutcome outcome = RecitationComparisonEngine.compare(reference, join(transcript));
        header("4. One word substituted", outcome);
        expect("exactly one finding", outcome.getFindings().size() == 1);
        RecitationFinding finding = first(outcome);
        expect("type is INCORRECT_WORD", finding != null && finding.getType() == FindingType.INCORRECT_WORD);
        expect("located at 1:4 word 3", finding != null && "1:4#3".equals(finding.locationLabel()));
        expect("expected surface preserved", finding != null && "ٱلدِّينِ".equals(finding.getExpectedText()));
        expect("heard surface preserved", finding != null && "ٱلْقِيَامَةِ".equals(finding.getHeardText()));
    }

    /** Repeating a word must yield exactly one EXTRA_WORD anchored to a reference position. */
    private static void caseOneWordDuplicated(TrustedReference reference) {
        String[] transcript = FATIHA_UTHMANI.clone();
        transcript[2] = "ٱلرَّحْمَٰنِ ٱلرَّحِيمِ ٱلرَّحِيمِ";
        ComparisonOutcome outcome = RecitationComparisonEngine.compare(reference, join(transcript));
        header("5. One word duplicated", outcome);
        expect("exactly one finding", outcome.getFindings().size() == 1);
        RecitationFinding finding = first(outcome);
        expect("type is EXTRA_WORD", finding != null && finding.getType() == FindingType.EXTRA_WORD);
        expect("expected text is absent", finding != null && finding.getExpectedText() == null);
        expect("heard surface preserved", finding != null && "ٱلرَّحِيمِ".equals(finding.getHeardText()));
        expect("anchored to a reference verse", finding != null && finding.getVerseKey() != null);
    }

    /** A different surah must not be silently accepted as correct. */
    private static void caseWrongSurah(TrustedReference reference) {
        String ikhlas = "قُلْ هُوَ ٱللَّهُ أَحَدٌ ٱللَّهُ ٱلصَّمَدُ لَمْ يَلِدْ وَلَمْ يُولَدْ "
                + "وَلَمْ يَكُن لَّهُۥ كُفُوًا أَحَدٌ";
        ComparisonOutcome outcome = RecitationComparisonEngine.compare(reference, ikhlas);
        header("6. Wrong surah entirely", outcome);
        expect("many findings are reported", outcome.getFindings().size() >= 10);
        expect("accuracy is below 50%", outcome.getAccuracyPercent() < 50.0);
    }

    /** With a passage mismatch, word-level noise must be replaced by one PASSAGE_MISMATCH. */
    private static void casePassageMismatchSuppressesWordFindings(TrustedReference reference) {
        String ikhlas = "قُلْ هُوَ ٱللَّهُ أَحَدٌ ٱللَّهُ ٱلصَّمَدُ";
        ComparisonOutcome mismatch = RecitationComparisonEngine.compare(reference, ikhlas)
                .asPassageMismatch("The recitation is from a different surah.");
        header("7. Passage mismatch", mismatch);
        expect("exactly one finding", mismatch.getFindings().size() == 1);
        RecitationFinding finding = first(mismatch);
        expect("type is PASSAGE_MISMATCH", finding != null && finding.getType() == FindingType.PASSAGE_MISMATCH);
        expect("no word-level findings remain", mismatch.getFindings().stream().noneMatch(f ->
                f.getType() == FindingType.MISSING_WORD
                        || f.getType() == FindingType.INCORRECT_WORD
                        || f.getType() == FindingType.EXTRA_WORD));
        expect("explanation carries no Arabic reference text",
                finding != null && finding.getExpectedText() == null && finding.getHeardText() == null);
        expect("per-word counts are withheld so no partial credit is implied",
                mismatch.getCorrectWords().isEmpty()
                        && mismatch.getMissingCount() == 0
                        && mismatch.getIncorrectCount() == 0
                        && mismatch.getExtraCount() == 0);
    }

    /** No trusted reference means no comparison and no invented text. */
    private static void caseReferenceUnavailable() {
        ComparisonOutcome outcome = RecitationComparisonEngine.compare(null, join(FATIHA_UTHMANI));
        System.out.println();
        System.out.println("8. Trusted reference unavailable");
        expect("outcome is marked unavailable", !outcome.isAvailable());
        expect("no findings", outcome.getFindings().isEmpty());
        expect("no correct words claimed", outcome.getCorrectWords().isEmpty());
        expect("no reference words claimed", outcome.getReferenceWordCount() == 0);
    }

    /** Nothing in the pipeline may produce a pre-verified finding. */
    private static void caseVerificationDefaults(TrustedReference reference) {
        String[] transcript = FATIHA_UTHMANI.clone();
        transcript[1] = "ٱلْحَمْدُ لِلَّهِ ٱلْعَٰلَمِينَ";
        ComparisonOutcome outcome = RecitationComparisonEngine.compare(reference, join(transcript));
        System.out.println();
        System.out.println("9. Human verification defaults");
        expect("every finding is PROPOSED by AI", outcome.getFindings().stream()
                .allMatch(f -> f.getAiStatus().name().equals("PROPOSED")));
        expect("every finding is PENDING instructor review", outcome.getFindings().stream()
                .allMatch(f -> f.getInstructorStatus().name().equals("PENDING")));
    }

    // --- Harness ---------------------------------------------------------------------------

    private static TrustedReference reference(int surah, String[] verses) {
        List<TrustedReference.Verse> list = new ArrayList<>(verses.length);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < verses.length; i++) {
            String verseKey = surah + ":" + (i + 1);
            list.add(new TrustedReference.Verse(verseKey, verses[i]));
            if (i > 0) {
                text.append(' ');
            }
            text.append(verses[i]);
        }
        String verseKeys = surah + ":1-" + surah + ":" + verses.length;
        return new TrustedReference(TrustedReference.SOURCE_QURAN_FOUNDATION, verseKeys, text.toString(), list);
    }

    private static String join(String[] verses) {
        return String.join(" ", verses);
    }

    private static RecitationFinding first(ComparisonOutcome outcome) {
        return outcome.getFindings().isEmpty() ? null : outcome.getFindings().get(0);
    }

    private static void header(String title, ComparisonOutcome outcome) {
        System.out.println();
        System.out.println(title + " [" + outcome.countsLabel()
                + " accuracy=" + String.format("%.1f", outcome.getAccuracyPercent()) + "%]");
    }

    private static void expect(String description, boolean condition) {
        checks++;
        if (condition) {
            System.out.println("   PASS  " + description);
        } else {
            failures++;
            System.out.println("   FAIL  " + description);
        }
    }
}
