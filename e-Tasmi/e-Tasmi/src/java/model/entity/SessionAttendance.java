package model.entity;

import java.time.Instant;

public class SessionAttendance {
    private long attendanceId;
    private long sessionId;
    private long studentId;
    private long markedByInstructorId;
    private AttendanceStatus attendanceStatus;
    private String notes;
    private Instant markedAt;

    public long getAttendanceId() {
        return attendanceId;
    }

    public void setAttendanceId(long attendanceId) {
        this.attendanceId = attendanceId;
    }

    public long getSessionId() {
        return sessionId;
    }

    public void setSessionId(long sessionId) {
        this.sessionId = sessionId;
    }

    public long getStudentId() {
        return studentId;
    }

    public void setStudentId(long studentId) {
        this.studentId = studentId;
    }

    public long getMarkedByInstructorId() {
        return markedByInstructorId;
    }

    public void setMarkedByInstructorId(long markedByInstructorId) {
        this.markedByInstructorId = markedByInstructorId;
    }

    public AttendanceStatus getAttendanceStatus() {
        return attendanceStatus;
    }

    public void setAttendanceStatus(AttendanceStatus attendanceStatus) {
        this.attendanceStatus = attendanceStatus;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getMarkedAt() {
        return markedAt;
    }

    public void setMarkedAt(Instant markedAt) {
        this.markedAt = markedAt;
    }
}
