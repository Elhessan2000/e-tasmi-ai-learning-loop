package model.entity;

import java.time.Instant;

/**
 * A recitation submitted through the student "Recitation Studio" (live recording
 * or upload of a previous recitation). Backed by the {@code recitation_submissions}
 * table.
 *
 * <p>This is intentionally a standalone, lightweight model (keyed directly to the
 * student's {@code user_id}) and is separate from the legacy enrollment-bound
 * {@link Recitation} entity.</p>
 */
public class RecitationSubmission {
    private long recitationId;
    private long studentId;
    private String filePath;
    private String topicText;
    private Long languageId;
    private String languageName;
    private String moduleLabel;
    private RecitationSubmissionStatus status = RecitationSubmissionStatus.PENDING;
    private Long durationSeconds;
    private Instant submittedAt;

    public long getRecitationId() {
        return recitationId;
    }

    public void setRecitationId(long recitationId) {
        this.recitationId = recitationId;
    }

    public long getStudentId() {
        return studentId;
    }

    public void setStudentId(long studentId) {
        this.studentId = studentId;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getTopicText() {
        return topicText;
    }

    public void setTopicText(String topicText) {
        this.topicText = topicText;
    }

    public Long getLanguageId() {
        return languageId;
    }

    public void setLanguageId(Long languageId) {
        this.languageId = languageId;
    }

    public String getLanguageName() {
        return languageName;
    }

    public void setLanguageName(String languageName) {
        this.languageName = languageName;
    }

    public String getModuleLabel() {
        return moduleLabel;
    }

    public void setModuleLabel(String moduleLabel) {
        this.moduleLabel = moduleLabel;
    }

    public RecitationSubmissionStatus getStatus() {
        return status;
    }

    public void setStatus(RecitationSubmissionStatus status) {
        this.status = status;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public boolean hasFile() {
        return filePath != null && !filePath.trim().isEmpty();
    }
}
