package model.service;

import java.util.Collections;
import java.util.List;

/**
 * Student-safe view of one recitation. Score, feedback, and focus are present only when
 * the evaluation has been published ({@code published_at} set).
 */
public final class VerifiedRecitationView {
    private final long recitationId;
    private final long enrollmentId;
    private final boolean evaluated;
    private final boolean published;
    private final Integer score;
    private final String feedback;
    private final String sessionTitle;
    private final String portion;
    private final String instructorName;
    private final String statusKind;
    private final int attemptNumber;
    private final List<VerifiedFocusItem> focusItems;

    public VerifiedRecitationView(long recitationId, long enrollmentId, boolean evaluated, boolean published,
                                  Integer score, String feedback, String sessionTitle, String portion,
                                  String instructorName, String statusKind, int attemptNumber,
                                  List<VerifiedFocusItem> focusItems) {
        this.recitationId = recitationId;
        this.enrollmentId = enrollmentId;
        this.evaluated = evaluated;
        this.published = published;
        this.score = score;
        this.feedback = feedback;
        this.sessionTitle = sessionTitle;
        this.portion = portion;
        this.instructorName = instructorName;
        this.statusKind = statusKind;
        this.attemptNumber = attemptNumber;
        this.focusItems = focusItems == null ? List.of() : List.copyOf(focusItems);
    }

    public long getRecitationId() {
        return recitationId;
    }

    public long getEnrollmentId() {
        return enrollmentId;
    }

    public boolean isEvaluated() {
        return evaluated;
    }

    public boolean isPublished() {
        return published;
    }

    public Integer getScore() {
        return score;
    }

    public String getFeedback() {
        return feedback;
    }

    public String getSessionTitle() {
        return sessionTitle;
    }

    public String getPortion() {
        return portion;
    }

    public String getInstructorName() {
        return instructorName;
    }

    public String getStatusKind() {
        return statusKind;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public List<VerifiedFocusItem> getFocusItems() {
        return Collections.unmodifiableList(focusItems);
    }
}
