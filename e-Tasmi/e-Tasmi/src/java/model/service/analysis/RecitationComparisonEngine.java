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

        return align(referenceTokens, heardSurfaces, heardNormalized);
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

        List<RecitationFinding> findings = new ArrayList<>();
        List<String> correctWords = new ArrayList<>();
        int missing = 0;
        int incorrect = 0;
        int extra = 0;

        int i = m;
        int j = n;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0
                    && referenceTokens.get(i - 1).matches(heardNormalized.get(j - 1))
                    && dp[i][j] == dp[i - 1][j - 1]) {
                correctWords.add(referenceTokens.get(i - 1).getSurface());
                i--;
                j--;
                continue;
            }
            if (i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + 1) {
                ReferenceToken token = referenceTokens.get(i - 1);
                findings.add(RecitationFinding.incorrectWord(
                        token.getVerseKey(), token.getWordPosition(),
                        token.getSurface(), heardSurfaces.get(j - 1)));
                incorrect++;
                i--;
                j--;
                continue;
            }
            if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) {
                ReferenceToken token = referenceTokens.get(i - 1);
                findings.add(RecitationFinding.missingWord(
                        token.getVerseKey(), token.getWordPosition(), token.getSurface()));
                missing++;
                i--;
                continue;
            }
            if (j > 0 && dp[i][j] == dp[i][j - 1] + 1) {
                findings.add(extraWordAt(referenceTokens, i, heardSurfaces.get(j - 1)));
                extra++;
                j--;
                continue;
            }
            // Defensive: the edit path is always one of the four cases above, but never loop.
            if (i > 0) {
                ReferenceToken token = referenceTokens.get(i - 1);
                findings.add(RecitationFinding.missingWord(
                        token.getVerseKey(), token.getWordPosition(), token.getSurface()));
                missing++;
                i--;
            } else if (j > 0) {
                findings.add(extraWordAt(referenceTokens, i, heardSurfaces.get(j - 1)));
                extra++;
                j--;
            }
        }

        Collections.reverse(findings);
        Collections.reverse(correctWords);
        return ComparisonOutcome.of(findings, correctWords, m, missing, incorrect, extra);
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
