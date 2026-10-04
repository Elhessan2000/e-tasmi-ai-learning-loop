package model.service.analysis;

/** Mirrors {@code recitation_finding.ai_status}. */
public enum FindingAiStatus {
    /** The finding was proposed by the analysis pipeline. */
    PROPOSED,
    /** The finding was added by an instructor, so there is no AI proposal behind it. */
    NOT_APPLICABLE
}
