package model.entity;

import util.MeetingLinkUtil;
import util.ZoomJoinLinkUtil;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public class TasmiSession {
    private long sessionId;
    private long instructorId;
    private String title;
    private String description;
    private StudentLevel level;
    private LocalDate sessionDate;
    private LocalTime sessionTime;
    private SessionMode mode;
    private Integer durationMinutes;
    private String quranPortion;
    private BigDecimal fee;
    private int capacity;
    private TasmiSessionStatus status;
    private String liveProvider;
    private String meetingLink;
    private String meetingPassword;
    private boolean passwordVisible;
    private Instant liveStartedAt;
    private Instant liveEndedAt;
    private SessionRecordingStatus recordingStatus;
    private String recordingUrl;
    private Instant recordingSyncedAt;
    private Long zoomMeetingId;
    private String zoomStartUrl;
    private String bannerImageUrl;

    /**
     * Timestamp at which the instructor marked this session's evaluations as
     * "Reviewed" via the Instructor Evaluation Dashboard.
     *
     * <p>Independent from {@link #status} (which tracks the live-session
     * lifecycle):
     * <ul>
     *   <li>{@code null} &rarr; the session belongs to the
     *       <strong>Active Evaluations</strong> tab.</li>
     *   <li>non-{@code null} &rarr; the session has been moved to
     *       <strong>Reviewed Sessions</strong>; the value records when.</li>
     * </ul>
     * Reopen for review = clear back to {@code null}.
     */
    private Instant evaluationReviewedAt;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public StudentLevel getLevel() {
        return level;
    }

    public void setLevel(StudentLevel level) {
        this.level = level;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }


    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public long getInstructorId() {
        return instructorId;
    }

    public void setInstructorId(long instructorId) {
        this.instructorId = instructorId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
    }

    public LocalTime getSessionTime() {
        return sessionTime;
    }

    public void setSessionTime(LocalTime sessionTime) {
        this.sessionTime = sessionTime;
    }

    public SessionMode getMode() {
        return mode;
    }

    public void setMode(SessionMode mode) {
        this.mode = mode;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public String getQuranPortion() {
        return quranPortion;
    }

    public void setQuranPortion(String quranPortion) {
        this.quranPortion = quranPortion;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public TasmiSessionStatus getStatus() {
        return status;
    }

    public void setStatus(TasmiSessionStatus status) {
        this.status = status;
    }

    public String getLiveProvider() {
        return liveProvider;
    }

    public void setLiveProvider(String liveProvider) {
        this.liveProvider = liveProvider;
    }

    public String getMeetingLink() {
        return meetingLink;
    }

    public void setMeetingLink(String meetingLink) {
        this.meetingLink = meetingLink;
    }

    public String getStudentMeetingLink() {
        return MeetingLinkUtil.toStudentJoinLink(meetingLink);
    }

    /**
     * Join URL for students (stored Zoom link or synthesized {@code zoom.us/j/...} when applicable).
     */
    public String getResolvableParticipantJoinUrl() {
        return ZoomJoinLinkUtil.resolveParticipantJoinUrl(this);
    }

    public String getMeetingPassword() {
        return meetingPassword;
    }

    public void setMeetingPassword(String meetingPassword) {
        this.meetingPassword = meetingPassword;
    }

    public boolean isPasswordVisible() {
        return passwordVisible;
    }

    public void setPasswordVisible(boolean passwordVisible) {
        this.passwordVisible = passwordVisible;
    }

    public Instant getLiveStartedAt() {
        return liveStartedAt;
    }

    public void setLiveStartedAt(Instant liveStartedAt) {
        this.liveStartedAt = liveStartedAt;
    }

    public Instant getLiveEndedAt() {
        return liveEndedAt;
    }

    public void setLiveEndedAt(Instant liveEndedAt) {
        this.liveEndedAt = liveEndedAt;
    }

    public SessionRecordingStatus getRecordingStatus() {
        return recordingStatus;
    }

    public void setRecordingStatus(SessionRecordingStatus recordingStatus) {
        this.recordingStatus = recordingStatus;
    }

    public String getRecordingUrl() {
        return recordingUrl;
    }

    public void setRecordingUrl(String recordingUrl) {
        this.recordingUrl = recordingUrl;
    }

    public Instant getRecordingSyncedAt() {
        return recordingSyncedAt;
    }

    public void setRecordingSyncedAt(Instant recordingSyncedAt) {
        this.recordingSyncedAt = recordingSyncedAt;
    }

    public Long getZoomMeetingId() {
        return zoomMeetingId;
    }

    public void setZoomMeetingId(Long zoomMeetingId) {
        this.zoomMeetingId = zoomMeetingId;
    }

    public String getZoomStartUrl() {
        return zoomStartUrl;
    }

    public void setZoomStartUrl(String zoomStartUrl) {
        this.zoomStartUrl = zoomStartUrl;
    }

    public String getBannerImageUrl() {
        return bannerImageUrl;
    }

    public void setBannerImageUrl(String bannerImageUrl) {
        this.bannerImageUrl = bannerImageUrl;
    }

    public Instant getEvaluationReviewedAt() {
        return evaluationReviewedAt;
    }

    public void setEvaluationReviewedAt(Instant evaluationReviewedAt) {
        this.evaluationReviewedAt = evaluationReviewedAt;
    }

    /**
     * Convenience predicate used by the dashboard to bucket a session into
     * "Active Evaluations" vs "Reviewed Sessions".
     */
    public boolean isEvaluationReviewed() {
        return evaluationReviewedAt != null;
    }
}
