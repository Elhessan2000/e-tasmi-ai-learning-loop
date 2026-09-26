package model.entity;

import java.time.Instant;

public class SessionMaterial {
    private long materialId;
    private long sessionId;
    private long uploadedByInstructorId;
    private String title;
    private String filePath;
    private String fileType;
    private Instant uploadedAt;

    public long getMaterialId() {
        return materialId;
    }

    public void setMaterialId(long materialId) {
        this.materialId = materialId;
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public long getUploadedByInstructorId() {
        return uploadedByInstructorId;
    }

    public void setUploadedByInstructorId(long uploadedByInstructorId) {
        this.uploadedByInstructorId = uploadedByInstructorId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
