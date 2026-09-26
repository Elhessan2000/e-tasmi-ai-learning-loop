package model.entity;

import java.util.Collections;
import java.util.List;

public class InstructorDashboardStats {
    private int totalSessions;
    private int upcomingSessions;
    private int ongoingSessions;
    private int completedSessions;
    private int enrolledStudentCount;
    private int submittedRecitationCount;
    private int pendingEvaluationCount;
    private List<TasmiSession> recentSessions;
    private List<TasmiSession> todaySessions = Collections.emptyList();
    private List<TasmiSession> weekSessions = Collections.emptyList();
    private List<TasmiSession> upcomingSessionsList = Collections.emptyList();
    private List<InstructorActivityItem> recentActivity = Collections.emptyList();
    private TasmiSession nextSession;
    private long serverNowEpochMs;
    private String serverZoneId;

    public int getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(int totalSessions) {
        this.totalSessions = totalSessions;
    }

    public int getUpcomingSessions() {
        return upcomingSessions;
    }

    public void setUpcomingSessions(int upcomingSessions) {
        this.upcomingSessions = upcomingSessions;
    }

    public int getOngoingSessions() {
        return ongoingSessions;
    }

    public void setOngoingSessions(int ongoingSessions) {
        this.ongoingSessions = ongoingSessions;
    }

    public int getCompletedSessions() {
        return completedSessions;
    }

    public void setCompletedSessions(int completedSessions) {
        this.completedSessions = completedSessions;
    }

    public int getEnrolledStudentCount() {
        return enrolledStudentCount;
    }

    public void setEnrolledStudentCount(int enrolledStudentCount) {
        this.enrolledStudentCount = enrolledStudentCount;
    }

    public int getSubmittedRecitationCount() {
        return submittedRecitationCount;
    }

    public void setSubmittedRecitationCount(int submittedRecitationCount) {
        this.submittedRecitationCount = submittedRecitationCount;
    }

    public int getPendingEvaluationCount() {
        return pendingEvaluationCount;
    }

    public void setPendingEvaluationCount(int pendingEvaluationCount) {
        this.pendingEvaluationCount = pendingEvaluationCount;
    }

    public List<TasmiSession> getRecentSessions() {
        return recentSessions;
    }

    public void setRecentSessions(List<TasmiSession> recentSessions) {
        this.recentSessions = recentSessions;
    }

    public List<TasmiSession> getTodaySessions() {
        return todaySessions;
    }

    public void setTodaySessions(List<TasmiSession> todaySessions) {
        this.todaySessions = todaySessions == null ? Collections.emptyList() : todaySessions;
    }

    public List<TasmiSession> getWeekSessions() {
        return weekSessions;
    }

    public void setWeekSessions(List<TasmiSession> weekSessions) {
        this.weekSessions = weekSessions == null ? Collections.emptyList() : weekSessions;
    }

    public List<TasmiSession> getUpcomingSessionsList() {
        return upcomingSessionsList;
    }

    public void setUpcomingSessionsList(List<TasmiSession> upcomingSessionsList) {
        this.upcomingSessionsList = upcomingSessionsList == null ? Collections.emptyList() : upcomingSessionsList;
    }

    public List<InstructorActivityItem> getRecentActivity() {
        return recentActivity;
    }

    public void setRecentActivity(List<InstructorActivityItem> recentActivity) {
        this.recentActivity = recentActivity == null ? Collections.emptyList() : recentActivity;
    }

    public TasmiSession getNextSession() {
        return nextSession;
    }

    public void setNextSession(TasmiSession nextSession) {
        this.nextSession = nextSession;
    }

    public long getServerNowEpochMs() {
        return serverNowEpochMs;
    }

    public void setServerNowEpochMs(long serverNowEpochMs) {
        this.serverNowEpochMs = serverNowEpochMs;
    }

    public String getServerZoneId() {
        return serverZoneId;
    }

    public void setServerZoneId(String serverZoneId) {
        this.serverZoneId = serverZoneId;
    }
}
