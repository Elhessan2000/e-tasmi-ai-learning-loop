package model.entity;

public class StudentProgressSummary {
    private int totalEnrollments;
    private int approvedEnrollments;
    private int completedSessions;
    private int pendingPayments;
    private int successfulPayments;
    private int recitationsSubmitted;
    private int evaluatedRecitations;
    private int attendanceMarked;
    private int attendancePresent;

    public int getTotalEnrollments() {
        return totalEnrollments;
    }

    public void setTotalEnrollments(int totalEnrollments) {
        this.totalEnrollments = totalEnrollments;
    }

    public int getApprovedEnrollments() {
        return approvedEnrollments;
    }

    public void setApprovedEnrollments(int approvedEnrollments) {
        this.approvedEnrollments = approvedEnrollments;
    }

    public int getCompletedSessions() {
        return completedSessions;
    }

    public void setCompletedSessions(int completedSessions) {
        this.completedSessions = completedSessions;
    }

    public int getPendingPayments() {
        return pendingPayments;
    }

    public void setPendingPayments(int pendingPayments) {
        this.pendingPayments = pendingPayments;
    }

    public int getSuccessfulPayments() {
        return successfulPayments;
    }

    public void setSuccessfulPayments(int successfulPayments) {
        this.successfulPayments = successfulPayments;
    }

    public int getRecitationsSubmitted() {
        return recitationsSubmitted;
    }

    public void setRecitationsSubmitted(int recitationsSubmitted) {
        this.recitationsSubmitted = recitationsSubmitted;
    }

    public int getEvaluatedRecitations() {
        return evaluatedRecitations;
    }

    public void setEvaluatedRecitations(int evaluatedRecitations) {
        this.evaluatedRecitations = evaluatedRecitations;
    }

    public int getAttendanceMarked() {
        return attendanceMarked;
    }

    public void setAttendanceMarked(int attendanceMarked) {
        this.attendanceMarked = attendanceMarked;
    }

    public int getAttendancePresent() {
        return attendancePresent;
    }

    public void setAttendancePresent(int attendancePresent) {
        this.attendancePresent = attendancePresent;
    }
}
