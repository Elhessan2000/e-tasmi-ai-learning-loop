package setup;

import model.entity.RecitationFindingRecord;
import model.service.analysis.ComparisonOutcome;
import model.service.analysis.FindingGroups;
import model.service.analysis.FindingType;
import model.service.analysis.RecitationBoundaryPolicy;
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
        caseFatihaBasmalaStaysInReference(fatiha);
        caseOpeningBeforeLaterAyahs();
        caseInsideExtraStays();
        caseMissingAndIncorrectStay();
        caseTrailingContinuation();
        caseAnNasOpeningThenContinuation();
        caseLongFindingListGroupsWithoutMerging();
        caseBoundaryNotesStayOutOfTheLearningLoop(fatiha);

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

    private static final String ISTIADHAH = "أعوذ بالله من الشيطان الرجيم";
    private static final String BASMALA = "بسم الله الرحمن الرحيم";

    /** Al-Fatihah 1:1 is the basmala, so it stays in the comparison. */
    private static void caseFatihaBasmalaStaysInReference(TrustedReference reference) {
        ComparisonOutcome plain = RecitationComparisonEngine.compare(reference, join(FATIHA_UTHMANI));
        header("10. Al-Fatihah 1:1 basmala is reference text", plain);
        expect("no opening note when the basmala is the passage", plain.getOpeningNote() == null);
        expect("no extra findings", plain.getExtraCount() == 0 && plain.getFindings().isEmpty());

        ComparisonOutcome withSeekingRefuge = RecitationComparisonEngine.compare(
                reference, ISTIADHAH + " " + join(FATIHA_UTHMANI));
        header("11. Isti'adhah before Al-Fatihah 1:1", withSeekingRefuge);
        expect("isti'adhah is an opening note", withSeekingRefuge.getOpeningNote() != null
                && withSeekingRefuge.getOpeningNote().contains("Isti'adhah"));
        expect("basmala was not removed", withSeekingRefuge.getOpeningNote() == null
                || !withSeekingRefuge.getOpeningNote().contains("basmala"));
        expect("no extra findings for the opening", withSeekingRefuge.getExtraCount() == 0);
        expect("assigned words stay correct", withSeekingRefuge.getCorrectCount() == withSeekingRefuge.getReferenceWordCount());
        expect("accuracy stays 100", Math.abs(withSeekingRefuge.getAccuracyPercent() - 100.0) < 0.001);
        expect("opening is not a finding", withSeekingRefuge.getFindings().stream()
                .noneMatch(f -> f.getType() == FindingType.EXTRA_WORD));
    }

    /** A passage that does not begin with the basmala can have both formulas removed. */
    private static void caseOpeningBeforeLaterAyahs() {
        TrustedReference fromSecondAyah = referenceFrom(1, 2, java.util.Arrays.copyOfRange(FATIHA_UTHMANI, 1, 5));
        String assigned = join(java.util.Arrays.copyOfRange(FATIHA_UTHMANI, 1, 5));
        ComparisonOutcome both = RecitationComparisonEngine.compare(
                fromSecondAyah, ISTIADHAH + " " + BASMALA + " " + assigned);
        header("12. Isti'adhah and basmala before Al-Fatihah 1:2-1:5", both);
        expect("both formulas noted", both.getOpeningNote() != null
                && both.getOpeningNote().contains("Isti'adhah")
                && both.getOpeningNote().contains("basmala"));
        expect("no extra findings", both.getExtraCount() == 0 && both.getFindings().isEmpty());
        expect("accuracy stays 100", Math.abs(both.getAccuracyPercent() - 100.0) < 0.001);

        ComparisonOutcome basmalaOnly = RecitationComparisonEngine.compare(fromSecondAyah, BASMALA + " " + assigned);
        expect("basmala alone is an opening", basmalaOnly.getOpeningNote() != null
                && basmalaOnly.getOpeningNote().contains("basmala")
                && basmalaOnly.getExtraCount() == 0);
    }

    /** A repeated word inside the passage remains one atomic extra-word finding. */
    private static void caseInsideExtraStays() {
        String[] transcript = FATIHA_UTHMANI.clone();
        transcript[2] = "ٱلرَّحْمَٰنِ ٱلرَّحِيمِ ٱلرَّحِيمِ";
        ComparisonOutcome outcome = RecitationComparisonEngine.compare(reference(1, FATIHA_UTHMANI), join(transcript));
        header("13. Extra word inside the passage", outcome);
        expect("exactly one extra", outcome.getExtraCount() == 1);
        expect("type is EXTRA_WORD", first(outcome) != null && first(outcome).getType() == FindingType.EXTRA_WORD);
        expect("not a continuation", outcome.getContinuationNote() == null);
    }

    private static void caseMissingAndIncorrectStay() {
        TrustedReference reference = reference(1, FATIHA_UTHMANI);
        String[] missing = FATIHA_UTHMANI.clone();
        missing[1] = "ٱلْحَمْدُ لِلَّهِ ٱلْعَٰلَمِينَ";
        ComparisonOutcome missingOutcome = RecitationComparisonEngine.compare(
                reference, ISTIADHAH + " " + join(missing));
        header("14. Missing word after an opening", missingOutcome);
        expect("opening noted", missingOutcome.getOpeningNote() != null);
        expect("one missing finding", missingOutcome.getMissingCount() == 1
                && firstOf(missingOutcome, FindingType.MISSING_WORD) != null);
        expect("missing is not suppressed", missingOutcome.getFindings().stream()
                .anyMatch(f -> f.getType() == FindingType.MISSING_WORD));

        String[] swapped = FATIHA_UTHMANI.clone();
        swapped[3] = "مَٰلِكِ يَوْمِ ٱلْقِيَامَةِ";
        ComparisonOutcome incorrect = RecitationComparisonEngine.compare(reference, join(swapped));
        header("15. Incorrect word inside the passage", incorrect);
        expect("one incorrect finding", incorrect.getIncorrectCount() == 1
                && firstOf(incorrect, FindingType.INCORRECT_WORD) != null);
    }

    private static void caseTrailingContinuation() {
        TrustedReference start = referenceFrom(1, 1, java.util.Arrays.copyOfRange(FATIHA_UTHMANI, 0, 2));
        String heard = join(java.util.Arrays.copyOfRange(FATIHA_UTHMANI, 0, 2)) + " زيد عمر بكر";
        ComparisonOutcome outcome = RecitationComparisonEngine.compare(start, heard);
        header("16. Continuation after the assigned ayahs", outcome);
        expect("continuation note", RecitationBoundaryPolicy.CONTINUATION_NOTE.equals(outcome.getContinuationNote()));
        expect("trailing words are not extra findings", outcome.getExtraCount() == 0);
        expect("no extra-word findings", outcome.getFindings().stream()
                .noneMatch(f -> f.getType() == FindingType.EXTRA_WORD));
        expect("assigned words remain correct", outcome.getCorrectCount() == outcome.getReferenceWordCount());
    }

    /**
     * The instructor screenshot: An-Nas 114:1 is four words, the student begins with the
     * basmala, recites those four words, then continues. A later "الناس" must not pull the
     * alignment to the end and turn the continuation into extra-word findings.
     */
    private static void caseAnNasOpeningThenContinuation() {
        TrustedReference anNas = referenceFrom(114, 1, new String[]{"قُلْ أَعُوذُ بِرَبِّ ٱلنَّاسِ"});
        String heard = BASMALA + " قل أعوذ برب الناس من شر ما خلقوا الذي يوسوس في صدور الناس من الجنة والناس";
        ComparisonOutcome outcome = RecitationComparisonEngine.compare(anNas, heard);
        header("16b. An-Nas 114:1 with basmala and continuation", outcome);
        expect("four reference words", outcome.getReferenceWordCount() == 4);
        expect("all four correct", outcome.getCorrectCount() == 4);
        expect("nothing missing", outcome.getMissingCount() == 0);
        expect("nothing incorrect", outcome.getIncorrectCount() == 0);
        expect("opening and continuation are not extras", outcome.getExtraCount() == 0);
        expect("no findings", outcome.getFindings().isEmpty());
        expect("basmala noted as opening", outcome.getOpeningNote() != null
                && outcome.getOpeningNote().contains("basmala"));
        expect("continuation noted", outcome.getContinuationNote() != null);
        expect("accuracy stays 100", Math.abs(outcome.getAccuracyPercent() - 100.0) < 0.001);
    }

    /** Many atomic extras stay individual records inside one visual group. */
    private static void caseLongFindingListGroupsWithoutMerging() {
        java.util.List<RecitationFindingRecord> rows = new java.util.ArrayList<>();
        int id = 1;
        for (int ayah = 1; ayah <= 3; ayah++) {
            int perAyah = ayah == 1 ? 4 : ayah == 2 ? 5 : 7;
            for (int n = 0; n < perAyah; n++) {
                RecitationFindingRecord row = new RecitationFindingRecord();
                row.setFindingId(id++);
                row.setFindingType(FindingType.EXTRA_WORD);
                row.setVerseKey("2:" + ayah);
                row.setHeardText("word");
                rows.add(row);
            }
        }
        RecitationFindingRecord missing = new RecitationFindingRecord();
        missing.setFindingId(id);
        missing.setFindingType(FindingType.MISSING_WORD);
        missing.setVerseKey("2:1");
        rows.add(missing);

        java.util.List<FindingGroups.TypeGroup> groups = FindingGroups.group(rows);
        System.out.println();
        System.out.println("17. Grouped presentation keeps atomic findings");
        expect("two type groups", groups.size() == 2);
        FindingGroups.TypeGroup extras = groups.get(1);
        expect("missing words stay their own group", groups.get(0).getType() == FindingType.MISSING_WORD
                && groups.get(0).getCount() == 1);
        expect("extras stay one group of 16", extras.getType() == FindingType.EXTRA_WORD && extras.getCount() == 16);
        expect("three ayah groups", extras.getAyahs().size() == 3);
        int preserved = 0;
        for (FindingGroups.AyahGroup ayah : extras.getAyahs()) {
            preserved += ayah.getFindings().size();
        }
        expect("all 16 atomic extras are still present", preserved == 16);
    }

    /**
     * Boundary notes are analysis text, not findings, so they cannot be verified, published,
     * or copied into the student learning focus or Practice Again.
     */
    private static void caseBoundaryNotesStayOutOfTheLearningLoop(TrustedReference reference) {
        TrustedReference later = referenceFrom(1, 2, java.util.Arrays.copyOfRange(FATIHA_UTHMANI, 1, 3));
        String assigned = join(java.util.Arrays.copyOfRange(FATIHA_UTHMANI, 1, 3));
        ComparisonOutcome outcome = RecitationComparisonEngine.compare(
                later, ISTIADHAH + " " + BASMALA + " " + assigned + " زيد عمر");
        String stored = RecitationBoundaryPolicy.mergeIntoPassageNote("The assigned passage matches.", outcome);
        System.out.println();
        System.out.println("18. Boundary notes stay outside findings, publication, and Practice Again");
        expect("opening text is recoverable from the passage note",
                RecitationBoundaryPolicy.openingText(stored) != null
                        && RecitationBoundaryPolicy.openingText(stored).contains("Isti'adhah"));
        expect("continuation text is recoverable from the passage note",
                RecitationBoundaryPolicy.CONTINUATION_NOTE.equals(
                        RecitationBoundaryPolicy.continuationText(stored)));
        expect("notes created no findings to verify or publish", outcome.getFindings().isEmpty());
        expect("counts describe only the assigned passage",
                outcome.getExtraCount() == 0 && outcome.getCorrectCount() == outcome.getReferenceWordCount());

        String[] transcript = FATIHA_UTHMANI.clone();
        transcript[1] = "ٱلْحَمْدُ لِلَّهِ ٱلْعَٰلَمِينَ";
        ComparisonOutcome realFinding = RecitationComparisonEngine.compare(reference, join(transcript));
        expect("a real finding still starts PENDING", !realFinding.getFindings().isEmpty()
                && realFinding.getFindings().stream()
                .allMatch(f -> f.getInstructorStatus().name().equals("PENDING")));
        expect("PENDING is not a student-facing status", realFinding.getFindings().stream()
                .noneMatch(f -> f.getInstructorStatus().name().equals("ACCEPTED")
                        || f.getInstructorStatus().name().equals("EDITED")
                        || f.getInstructorStatus().name().equals("INSTRUCTOR_ADDED")));
    }

    // --- Harness ---------------------------------------------------------------------------

    private static TrustedReference reference(int surah, String[] verses) {
        return referenceFrom(surah, 1, verses);
    }

    private static TrustedReference referenceFrom(int surah, int firstAyah, String[] verses) {
        List<TrustedReference.Verse> list = new ArrayList<>(verses.length);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < verses.length; i++) {
            String verseKey = surah + ":" + (firstAyah + i);
            list.add(new TrustedReference.Verse(verseKey, verses[i]));
            if (i > 0) {
                text.append(' ');
            }
            text.append(verses[i]);
        }
        String verseKeys = surah + ":" + firstAyah + "-" + surah + ":" + (firstAyah + verses.length - 1);
        return new TrustedReference(TrustedReference.SOURCE_QURAN_FOUNDATION, verseKeys, text.toString(), list);
    }

    private static String join(String[] verses) {
        return String.join(" ", verses);
    }

    private static RecitationFinding first(ComparisonOutcome outcome) {
        return outcome.getFindings().isEmpty() ? null : outcome.getFindings().get(0);
    }

    private static RecitationFinding firstOf(ComparisonOutcome outcome, FindingType type) {
        for (RecitationFinding finding : outcome.getFindings()) {
            if (finding.getType() == type) {
                return finding;
            }
        }
        return null;
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
