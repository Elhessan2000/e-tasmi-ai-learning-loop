package model.entity;

import java.time.Instant;

public class Recitation {
    private long recitationId;
    private long enrollmentId;
    private String audioFilePath;
    private Instant submissionDate;
    private Long parentRecitationId;
    private int attemptNumber = 1;
    private RecitationAnalysisJobState analysisJobState = RecitationAnalysisJobState.NONE;
    private Instant analysisJobStartedAt;

    public long getRecitationId() {
        return recitationId;
    }

    public void setRecitationId(long recitationId) {
        this.recitationId = recitationId;
    }

    public long getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(long enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public String getAudioFilePath() {
        return audioFilePath;
    }

    public void setAudioFilePath(String audioFilePath) {
        this.audioFilePath = audioFilePath;
    }

    public Instant getSubmissionDate() {
        return submissionDate;
    }

    public void setSubmissionDate(Instant submissionDate) {
        this.submissionDate = submissionDate;
    }

    public Long getParentRecitationId() {
        return parentRecitationId;
    }

    public void setParentRecitationId(Long parentRecitationId) {
        this.parentRecitationId = parentRecitationId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber < 1 ? 1 : attemptNumber;
    }

    public RecitationAnalysisJobState getAnalysisJobState() {
        return analysisJobState == null ? RecitationAnalysisJobState.NONE : analysisJobState;
    }

    public void setAnalysisJobState(RecitationAnalysisJobState analysisJobState) {
        this.analysisJobState = analysisJobState == null ? RecitationAnalysisJobState.NONE : analysisJobState;
    }

    public Instant getAnalysisJobStartedAt() {
        return analysisJobStartedAt;
    }

    public void setAnalysisJobStartedAt(Instant analysisJobStartedAt) {
        this.analysisJobStartedAt = analysisJobStartedAt;
    }
}
