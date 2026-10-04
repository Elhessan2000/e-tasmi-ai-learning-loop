package model.service.analysis;

/**
 * Mirrors {@code recitation_finding.finding_type}.
 *
 * <p>Acoustic claims are deliberately limited to {@link #PRONUNCIATION_OBSERVATION}, which is
 * advisory and requires the instructor to listen. Makhraj and tajwid verdicts are not in this
 * set: a plain text transcript cannot prove them, so the model is never allowed to assert
 * them.</p>
 */
public enum FindingType {
    MISSING_WORD,
    INCORRECT_WORD,
    EXTRA_WORD,
    PASSAGE_MISMATCH,
    PRONUNCIATION_OBSERVATION,
    OTHER
}
