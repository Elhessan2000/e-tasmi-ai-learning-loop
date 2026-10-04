package model.service;

import model.entity.RecitationAnalysis;
import model.entity.TasmiSession;
import model.service.quran.TrustedReference;

/**
 * Server-side rules for publishing an instructor evaluation in the AI Learning Loop.
 * Does not apply to legacy rows that already exist in the database.
 */
public final class LearningLoopPublicationPolicy {

    private LearningLoopPublicationPolicy() {
    }

    /**
     * @return {@code null} when publication may proceed, otherwise a user-facing refusal message
     */
    public static String refusalReason(RecitationAnalysis latestAnalysis, TasmiSession session, int pendingFindings) {
        if (latestAnalysis == null) {
            return "Run AI analysis before publishing this recitation.";
        }
        if (pendingFindings > 0) {
            return "Publish is blocked: " + pendingFindings
                    + (pendingFindings == 1 ? " finding is" : " findings are")
                    + " still pending. Accept, edit, or reject each one first.";
        }

        String status = latestAnalysis.getStatus() == null ? "" : latestAnalysis.getStatus().trim();
        switch (status) {
            case "OK":
                if (!hasStructuredSessionRange(session)) {
                    return "Structured Qur'an passage (surah and ayah range) is required before publication.";
                }
                if (!hasStructuredAnalysisReference(latestAnalysis)) {
                    return "Publication requires a structured AI Learning Loop analysis with a trusted Qur'an reference.";
                }
                return null;
            case "REFERENCE_UNAVAILABLE":
            case "REJECTED":
                // REJECTED is a finished judgment (too short, or confident non-Qur'an).
                // The instructor closes it with a score and feedback. It is not retried,
                // and it does not carry a Quranpedia reference source.
                if (!hasStructuredSessionRange(session)) {
                    return "Structured Qur'an passage (surah and ayah range) is required before publication.";
                }
                return null;
            case "FAILED":
                return "Analysis could not be completed. Run Analyze again before publishing.";
            case "CANNOT_EVALUATE":
                return "The recitation could not be evaluated automatically. Run Analyze again before publishing.";
            default:
                return "The latest analysis is not in a publishable state.";
        }
    }

    public static boolean hasStructuredSessionRange(TasmiSession session) {
        if (session == null) {
            return false;
        }
        Integer surah = session.getSurahNumber();
        Integer start = session.getAyahStart();
        Integer end = session.getAyahEnd();
        return surah != null && surah >= 1 && surah <= 114
                && start != null && start >= 1
                && end != null && end >= start;
    }

    private static boolean hasStructuredAnalysisReference(RecitationAnalysis analysis) {
        String keys = analysis.getReferenceVerseKeys();
        if (keys == null || keys.isBlank()) {
            return false;
        }
        String source = analysis.getReferenceSource();
        return TrustedReference.SOURCE_QURANPEDIA.equals(source);
    }
}
