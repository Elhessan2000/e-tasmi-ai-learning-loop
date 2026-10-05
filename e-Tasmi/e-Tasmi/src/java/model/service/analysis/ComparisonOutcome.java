package model.service.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of the deterministic comparison between a trusted reference and a transcript.
 *
 * <p>When the trusted reference is unavailable the outcome is {@link #isAvailable() unavailable}
 * and carries no findings, so a missing reference can never produce a claim about what the
 * student recited.</p>
 */
public final class ComparisonOutcome {

    private final boolean available;
    private final List<RecitationFinding> findings;
    private final List<String> correctWords;
    private final int referenceWordCount;
    private final int missingCount;
    private final int incorrectCount;
    private final int extraCount;
    /** Informational only. Never a finding and never part of the verification count. */
    private final String openingNote;
    private final String continuationNote;

    private ComparisonOutcome(boolean available, List<RecitationFinding> findings, List<String> correctWords,
                              int referenceWordCount, int missingCount, int incorrectCount, int extraCount) {
        this(available, findings, correctWords, referenceWordCount, missingCount, incorrectCount, extraCount,
                null, null);
    }

    private ComparisonOutcome(boolean available, List<RecitationFinding> findings, List<String> correctWords,
                              int referenceWordCount, int missingCount, int incorrectCount, int extraCount,
                              String openingNote, String continuationNote) {
        this.available = available;
        this.findings = List.copyOf(findings == null ? List.of() : findings);
        this.correctWords = List.copyOf(correctWords == null ? List.of() : correctWords);
        this.referenceWordCount = referenceWordCount;
        this.missingCount = missingCount;
        this.incorrectCount = incorrectCount;
        this.extraCount = extraCount;
        this.openingNote = openingNote;
        this.continuationNote = continuationNote;
    }

    static ComparisonOutcome of(List<RecitationFinding> findings, List<String> correctWords,
                                int referenceWordCount, int missingCount, int incorrectCount, int extraCount) {
        return new ComparisonOutcome(true, findings, correctWords,
                referenceWordCount, missingCount, incorrectCount, extraCount);
    }

    /** No trusted reference, so no comparison and no findings. */
    public static ComparisonOutcome unavailable() {
        return new ComparisonOutcome(false, List.of(), List.of(), 0, 0, 0, 0);
    }

    /**
     * Replaces every word-level finding with a single {@link FindingType#PASSAGE_MISMATCH}.
     *
     * <p>Diffing against the wrong passage is noise, so the per-word counts are withheld too.
     * Reporting them would imply the comparison was meaningful: a recitation of a different
     * surah can incidentally share a few words and would otherwise be presented as partially
     * correct.</p>
     */
    public ComparisonOutcome asPassageMismatch(String explanation) {
        List<RecitationFinding> only = new ArrayList<>(1);
        only.add(RecitationFinding.passageMismatch(explanation));
        return new ComparisonOutcome(available, only, List.of(), referenceWordCount, 0, 0, 0);
    }

    /** Returns a copy with extra advisory findings appended. */
    public ComparisonOutcome withAdditionalFindings(List<RecitationFinding> extra) {
        if (extra == null || extra.isEmpty()) {
            return this;
        }
        List<RecitationFinding> merged = new ArrayList<>(findings);
        merged.addAll(extra);
        return new ComparisonOutcome(available, merged, correctWords,
                referenceWordCount, missingCount, incorrectCount, extraCount,
                openingNote, continuationNote);
    }

    /** Returns a copy whose findings are replaced wholesale, preserving the counts. */
    public ComparisonOutcome withFindings(List<RecitationFinding> replacement) {
        return new ComparisonOutcome(available, replacement, correctWords,
                referenceWordCount, missingCount, incorrectCount, extraCount,
                openingNote, continuationNote);
    }

    /** Attaches informational boundary notes. They are not findings and do not change counts. */
    public ComparisonOutcome withObservations(String opening, String continuation) {
        return new ComparisonOutcome(available, findings, correctWords,
                referenceWordCount, missingCount, incorrectCount, extraCount,
                opening, continuation);
    }

    public boolean isAvailable() {
        return available;
    }

    public List<RecitationFinding> getFindings() {
        return findings;
    }

    /** Original surface forms of the reference words that were recited correctly. */
    public List<String> getCorrectWords() {
        return correctWords;
    }

    public int getReferenceWordCount() {
        return referenceWordCount;
    }

    public int getCorrectCount() {
        return correctWords.size();
    }

    public int getMissingCount() {
        return missingCount;
    }

    public int getIncorrectCount() {
        return incorrectCount;
    }

    public int getExtraCount() {
        return extraCount;
    }

    /** Leading isti'adhah or basmala excluded from comparison, or null. */
    public String getOpeningNote() {
        return openingNote;
    }

    /** Speech after the assigned passage, excluded from comparison, or null. */
    public String getContinuationNote() {
        return continuationNote;
    }

    /** Share of reference words recited correctly. Extra words do not reduce it. */
    public double getAccuracyPercent() {
        int base = correctWords.size() + missingCount + incorrectCount;
        if (base <= 0) {
            return 0.0;
        }
        double value = 100.0 * correctWords.size() / base;
        return Math.max(0.0, Math.min(100.0, value));
    }

    /** Counts only, safe to log: contains no Qur'an text. */
    public String countsLabel() {
        return "reference_words=" + referenceWordCount
                + " correct=" + correctWords.size()
                + " missing=" + missingCount
                + " incorrect=" + incorrectCount
                + " extra=" + extraCount
                + " findings=" + findings.size();
    }
}
