package model.service;

import model.entity.Evaluation;
import model.entity.RecitationAnalysis;
import model.entity.RecitationAnalysisJobState;

/**
 * Student-safe coarse phase for recitation history (no provider names, transcripts, or analysis ids).
 */
public enum StudentRecitationAnalysisPhase {
    ANALYSIS_IN_PROGRESS,
    SUBMITTED,
    AWAITING_INSTRUCTOR,
    REFERENCE_UNAVAILABLE,
    ANALYSIS_FAILED,
    CANNOT_EVALUATE,
    REJECTED;

    public static StudentRecitationAnalysisPhase resolve(
            Evaluation evaluation,
            RecitationAnalysisJobState jobState,
            RecitationAnalysis latestAnalysis,
            java.time.Instant recitationJobStartedAt) {
        if (jobState == RecitationAnalysisJobState.IN_PROGRESS) {
            if (recitationJobStartedAt != null
                    && recitationJobStartedAt.isBefore(RecitationAnalysisJobConfig.staleCutoff())) {
                return SUBMITTED;
            }
            return ANALYSIS_IN_PROGRESS;
        }
        if (latestAnalysis == null || latestAnalysis.getStatus() == null) {
            return SUBMITTED;
        }
        switch (latestAnalysis.getStatus().trim()) {
            case "OK":
                return AWAITING_INSTRUCTOR;
            case "REFERENCE_UNAVAILABLE":
                return REFERENCE_UNAVAILABLE;
            case "FAILED":
                return ANALYSIS_FAILED;
            case "CANNOT_EVALUATE":
                return CANNOT_EVALUATE;
            case "REJECTED":
                return REJECTED;
            default:
                return AWAITING_INSTRUCTOR;
        }
    }
}
