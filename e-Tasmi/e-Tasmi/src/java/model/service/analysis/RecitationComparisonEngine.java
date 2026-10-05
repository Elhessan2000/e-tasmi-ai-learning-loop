package model.service.analysis;

import model.service.quran.TrustedReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Deterministic word-level comparison of a trusted Qur'an reference against an ASR transcript.
 *
 * <p>This is the sole source of word-level findings. It runs entirely in Java with no network
 * call and no API key, so it is reproducible and testable: the same reference and transcript
 * always yield the same findings. The language model never produces the diff; it only explains
 * findings that were computed here.</p>
 *
 * <p>Alignment is a Levenshtein edit path over word tokens, where equality is decided by
 * {@link ReferenceToken#matches(String)} so that mushaf orthography is not mistaken for a
 * recitation error.</p>
 */
public final class RecitationComparisonEngine {

    private RecitationComparisonEngine() {
    }

    /**
     * @param reference  trusted passage, or {@code null} when it could not be retrieved
     * @param transcript ASR transcript of the student's recitation
     * @return the findings and counts, or {@link ComparisonOutcome#unavailable()} when there is
     *         nothing trustworthy to compare against
     */
    public static ComparisonOutcome compare(TrustedReference reference, String transcript) {
        if (reference == null) {
            return ComparisonOutcome.unavailable();
        }
        List<ReferenceToken> referenceTokens = tokenizeReference(reference);
        if (referenceTokens.isEmpty()) {
            return ComparisonOutcome.unavailable();
        }

        List<String> heardSurfaces = new ArrayList<>();
        List<String> heardNormalized = new ArrayList<>();
        for (String word : ArabicTextNormalizer.splitWords(transcript)) {
            String normalized = ArabicTextNormalizer.normalize(word);
            if (normalized.isEmpty()) {
                continue;
            }
            heardSurfaces.add(word);
            heardNormalized.add(normalized);
        }

        RecitationBoundaryPolicy.Split split = RecitationBoundaryPolicy.stripLeading(
                referenceTokens, heardSurfaces, heardNormalized);
        ComparisonOutcome aligned = align(referenceTokens, split.surfaces, split.normalized);
        if (split.openingNote == null) {
            return aligned;
        }
        return aligned.withObservations(split.openingNote, aligned.getContinuationNote());
    }

    /** Flattens the reference into positioned tokens, numbering words within each verse. */
    public static List<ReferenceToken> tokenizeReference(TrustedReference reference) {
        if (reference == null) {
            return List.of();
        }
        List<ReferenceToken> tokens = new ArrayList<>();
        for (TrustedReference.Verse verse : reference.getVerses()) {
            if (verse == null) {
                continue;
            }
            int position = 0;
            for (String word : ArabicTextNormalizer.splitWords(verse.getUthmaniText())) {
                ReferenceToken candidate = new ReferenceToken(verse.getVerseKey(), position + 1, word);
                // Standalone pause marks normalise to nothing; they are not words and must not
                // consume a position or become a finding.
                if (candidate.isEmpty()) {
                    continue;
                }
                position++;
                tokens.add(candidate);
            }
        }
        return tokens;
    }

    private static ComparisonOutcome align(List<ReferenceToken> referenceTokens,
                                           List<String> heardSurfaces,
                                           List<String> heardNormalized) {
        int m = referenceTokens.size();
        int n = heardNormalized.size();

        int[][] dp = new int[m + 1][n + 1];
        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                int substitution = dp[i - 1][j - 1]
                        + (referenceTokens.get(i - 1).matches(heardNormalized.get(j - 1)) ? 0 : 1);
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), substitution);
            }
        }

        List<Step> backward = new ArrayList<>();
        int i = m;
        int j = n;
        while (i > 0 || j > 0) {
            boolean match = i > 0 && j > 0
                    && referenceTokens.get(i - 1).matches(heardNormalized.get(j - 1))
                    && dp[i][j] == dp[i - 1][j - 1];
            boolean trailingCopy = match && j > 0 && dp[i][j] == dp[i][j - 1] + 1;
            // A later copy of a reference word has the same cost as aligning that word
            // earlier and treating the rest as extra. Keep the earlier alignment so speech
            // after the assigned passage stays a trailing run.
            if (trailingCopy) {
                backward.add(Step.extra(extraWordAt(referenceTokens, i, heardSurfaces.get(j - 1))));
                j--;
                continue;
            }
            if (match) {
                backward.add(Step.match(referenceTokens.get(i - 1).getSurface()));
                i--;
                j--;
                continue;
            }
            if (i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1) {
                ReferenceToken token = referenceTokens.get(i - 1);
                backward.add(Step.incorrect(RecitationFinding.incorrectWord(
                        token.getVerseKey(), token.getWordPosition(),
                        token.getSurface(), heardSurfaces.get(j - 1))));
                i--;
                j--;
                continue;
            }
            if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
                ReferenceToken token = referenceTokens.get(i - 1);
                backward.add(Step.missing(RecitationFinding.missingWord(
                        token.getVerseKey(), token.getWordPosition(), token.getSurface())));
                i--;
                continue;
            }
            if (j > 0 && dp[i][j] == dp[i][j - 1] + 1) {
                backward.add(Step.extra(extraWordAt(referenceTokens, i, heardSurfaces.get(j - 1))));
                j--;
                continue;
            }
            // Defensive: the edit path is always one of the four cases above, but never loop.
            if (i > 0) {
                ReferenceToken token = referenceTokens.get(i - 1);
                backward.add(Step.missing(RecitationFinding.missingWord(
                        token.getVerseKey(), token.getWordPosition(), token.getSurface())));
                i--;
            } else if (j > 0) {
                backward.add(Step.extra(extraWordAt(referenceTokens, i, heardSurfaces.get(j - 1))));
                j--;
            }
        }

        Collections.reverse(backward);
        int lastReferenceStep = -1;
        for (int step = 0; step < backward.size(); step++) {
            if (backward.get(step).kind != StepKind.EXTRA) {
                lastReferenceStep = step;
            }
        }

        List<RecitationFinding> findings = new ArrayList<>();
        List<String> correctWords = new ArrayList<>();
        int missing = 0;
        int incorrect = 0;
        int extra = 0;
        int continuationWords = 0;
        for (int step = 0; step < backward.size(); step++) {
            Step current = backward.get(step);
            boolean trailing = current.kind == StepKind.EXTRA && step > lastReferenceStep;
            if (trailing) {
                continuationWords++;
                continue;
            }
            switch (current.kind) {
                case MATCH:
                    correctWords.add(current.surface);
                    break;
                case INCORRECT:
                    findings.add(current.finding);
                    incorrect++;
                    break;
                case MISSING:
                    findings.add(current.finding);
                    missing++;
                    break;
                case EXTRA:
                    findings.add(current.finding);
                    extra++;
                    break;
                default:
                    break;
            }
        }

        ComparisonOutcome outcome = ComparisonOutcome.of(findings, correctWords, m, missing, incorrect, extra);
        if (continuationWords > 0) {
            return outcome.withObservations(null, RecitationBoundaryPolicy.CONTINUATION_NOTE);
        }
        return outcome;
    }

    private enum StepKind { MATCH, INCORRECT, MISSING, EXTRA }

    private static final class Step {
        private final StepKind kind;
        private final RecitationFinding finding;
        private final String surface;

        private Step(StepKind kind, RecitationFinding finding, String surface) {
            this.kind = kind;
            this.finding = finding;
            this.surface = surface;
        }

        private static Step match(String surface) {
            return new Step(StepKind.MATCH, null, surface);
        }

        private static Step incorrect(RecitationFinding finding) {
            return new Step(StepKind.INCORRECT, finding, null);
        }

        private static Step missing(RecitationFinding finding) {
            return new Step(StepKind.MISSING, finding, null);
        }

        private static Step extra(RecitationFinding finding) {
            return new Step(StepKind.EXTRA, finding, null);
        }
    }

    /** Anchors an extra word to the reference word it follows, so it stays addressable. */
    private static RecitationFinding extraWordAt(List<ReferenceToken> referenceTokens,
                                                 int consumedReferenceCount,
                                                 String heardSurface) {
        if (consumedReferenceCount <= 0) {
            return RecitationFinding.extraWord(null, null, heardSurface);
        }
        ReferenceToken anchor = referenceTokens.get(consumedReferenceCount - 1);
        return RecitationFinding.extraWord(anchor.getVerseKey(), anchor.getWordPosition(), heardSurface);
    }
}
