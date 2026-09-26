package model.entity;

import java.math.BigDecimal;

public class ReportSummary {
    private long totalUsers;
    private long totalStudents;
    private long totalInstructors;
    private long totalAdmins;
    private long usersActive;
    private long usersInactive;
    private long usersDeleted;

    private long instructorsPending;
    private long instructorsApproved;
    private long instructorsRejected;

    private long sessionsScheduled;
    private long sessionsOngoing;
    private long sessionsCompleted;
    private long sessionsCancelled;

    private long enrollmentsPending;
    private long enrollmentsApproved;
    private long enrollmentsRejected;
    private long enrollmentsCancelled;

    private long paymentsPending;
    private long paymentsSuccess;
    private long paymentsFailed;

    private long totalRecitations;
    private long totalEvaluations;
    private long pendingReviews;

    private BigDecimal avgEvaluationScore;

    public long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(long totalUsers) { this.totalUsers = totalUsers; }

    public long getTotalStudents() { return totalStudents; }
    public void setTotalStudents(long totalStudents) { this.totalStudents = totalStudents; }

    public long getTotalInstructors() { return totalInstructors; }
    public void setTotalInstructors(long totalInstructors) { this.totalInstructors = totalInstructors; }

    public long getTotalAdmins() { return totalAdmins; }
    public void setTotalAdmins(long totalAdmins) { this.totalAdmins = totalAdmins; }

    public long getUsersActive() { return usersActive; }
    public void setUsersActive(long usersActive) { this.usersActive = usersActive; }

    public long getUsersInactive() { return usersInactive; }
    public void setUsersInactive(long usersInactive) { this.usersInactive = usersInactive; }

    public long getUsersDeleted() { return usersDeleted; }
    public void setUsersDeleted(long usersDeleted) { this.usersDeleted = usersDeleted; }

    public long getInstructorsPending() { return instructorsPending; }
    public void setInstructorsPending(long instructorsPending) { this.instructorsPending = instructorsPending; }

    public long getInstructorsApproved() { return instructorsApproved; }
    public void setInstructorsApproved(long instructorsApproved) { this.instructorsApproved = instructorsApproved; }

    public long getInstructorsRejected() { return instructorsRejected; }
    public void setInstructorsRejected(long instructorsRejected) { this.instructorsRejected = instructorsRejected; }

    public long getSessionsScheduled() { return sessionsScheduled; }
    public void setSessionsScheduled(long sessionsScheduled) { this.sessionsScheduled = sessionsScheduled; }

    public long getSessionsOngoing() { return sessionsOngoing; }
    public void setSessionsOngoing(long sessionsOngoing) { this.sessionsOngoing = sessionsOngoing; }

    public long getSessionsCompleted() { return sessionsCompleted; }
    public void setSessionsCompleted(long sessionsCompleted) { this.sessionsCompleted = sessionsCompleted; }

    public long getSessionsCancelled() { return sessionsCancelled; }
    public void setSessionsCancelled(long sessionsCancelled) { this.sessionsCancelled = sessionsCancelled; }

    public long getEnrollmentsPending() { return enrollmentsPending; }
    public void setEnrollmentsPending(long enrollmentsPending) { this.enrollmentsPending = enrollmentsPending; }

    public long getEnrollmentsApproved() { return enrollmentsApproved; }
    public void setEnrollmentsApproved(long enrollmentsApproved) { this.enrollmentsApproved = enrollmentsApproved; }

    public long getEnrollmentsRejected() { return enrollmentsRejected; }
    public void setEnrollmentsRejected(long enrollmentsRejected) { this.enrollmentsRejected = enrollmentsRejected; }

    public long getEnrollmentsCancelled() { return enrollmentsCancelled; }
    public void setEnrollmentsCancelled(long enrollmentsCancelled) { this.enrollmentsCancelled = enrollmentsCancelled; }

    public long getPaymentsPending() { return paymentsPending; }
    public void setPaymentsPending(long paymentsPending) { this.paymentsPending = paymentsPending; }

    public long getPaymentsSuccess() { return paymentsSuccess; }
    public void setPaymentsSuccess(long paymentsSuccess) { this.paymentsSuccess = paymentsSuccess; }

    public long getPaymentsFailed() { return paymentsFailed; }
    public void setPaymentsFailed(long paymentsFailed) { this.paymentsFailed = paymentsFailed; }

    public long getTotalRecitations() { return totalRecitations; }
    public void setTotalRecitations(long totalRecitations) { this.totalRecitations = totalRecitations; }

    public long getTotalEvaluations() { return totalEvaluations; }
    public void setTotalEvaluations(long totalEvaluations) { this.totalEvaluations = totalEvaluations; }

    public long getPendingReviews() { return pendingReviews; }
    public void setPendingReviews(long pendingReviews) { this.pendingReviews = pendingReviews; }

    public BigDecimal getAvgEvaluationScore() { return avgEvaluationScore; }
    public void setAvgEvaluationScore(BigDecimal avgEvaluationScore) { this.avgEvaluationScore = avgEvaluationScore; }
}
