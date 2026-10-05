package model.entity;

import java.time.Instant;

/**
 * One recitation whose evaluation has {@code published_at} set, with the count of
 * student-facing findings (accepted, edited or instructor-added) on the evaluated analysis.
 */
public class PublishedRecitationRecord {
    private long recitationId;
    private long enrollmentId;
    private Long parentRecitationId;
    private int attemptNumber;
    private Instant submittedAt;
    private Instant publishedAt;
    private int score;
    private String sessionTitle;
    private Integer surahNumber;
    private Integer ayahStart;
    private Integer ayahEnd;
    private String quranPortion;
    private int focusCount;

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
        this.attemptNumber = attemptNumber;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getSessionTitle() {
        return sessionTitle;
    }

    public void setSessionTitle(String sessionTitle) {
        this.sessionTitle = sessionTitle;
    }

    public Integer getSurahNumber() {
        return surahNumber;
    }

    public void setSurahNumber(Integer surahNumber) {
        this.surahNumber = surahNumber;
    }

    public Integer getAyahStart() {
        return ayahStart;
    }

    public void setAyahStart(Integer ayahStart) {
        this.ayahStart = ayahStart;
    }

    public Integer getAyahEnd() {
        return ayahEnd;
    }

    public void setAyahEnd(Integer ayahEnd) {
        this.ayahEnd = ayahEnd;
    }

    public String getQuranPortion() {
        return quranPortion;
    }

    public void setQuranPortion(String quranPortion) {
        this.quranPortion = quranPortion;
    }

    public int getFocusCount() {
        return focusCount;
    }

    public void setFocusCount(int focusCount) {
        this.focusCount = focusCount;
    }
}
