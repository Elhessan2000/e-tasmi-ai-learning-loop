package model.dao;

import model.entity.PublishedRecitationRecord;
import model.entity.Recitation;
import model.entity.RecitationAnalysisJobState;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

public interface RecitationDao {
    long insert(Connection connection, Recitation recitation) throws SQLException;

    Optional<Recitation> findById(Connection connection, long recitationId) throws SQLException;

    List<Recitation> listByEnrollmentId(Connection connection, long enrollmentId) throws SQLException;

    /**
     * Lists recitations for a given instructor by joining: recitation -> enrollment -> tasmi_session.
     */
    List<Recitation> listForInstructor(Connection connection, long instructorId) throws SQLException;

    /**
     * Published recitations for one student, newest submission first. Rows without
     * {@code evaluation.published_at} are never returned. The focus count uses the same
     * student-facing statuses as the verified learning focus.
     */
    List<PublishedRecitationRecord> listPublishedForStudent(Connection connection, long studentId, int limit)
            throws SQLException;

    int countPublishedForStudent(Connection connection, long studentId) throws SQLException;

    /** Highest attempt number already stored for this enrollment, or 0 when none exist. */
    int maxAttemptNumber(Connection connection, long enrollmentId) throws SQLException;

    RecitationAnalysisJobState findAnalysisJobState(Connection connection, long recitationId) throws SQLException;

    /**
     * @return true when this caller won the race to start the first analysis for a recitation
     */
    boolean tryClaimInitialAnalysisJob(Connection connection, long recitationId, Timestamp staleCutoff) throws SQLException;

    /**
     * @return true when a retry run may start (terminal failure analysis exists, no saved evaluation)
     */
    boolean tryClaimRetryAnalysisJob(Connection connection, long recitationId, Timestamp staleCutoff) throws SQLException;

    /**
     * Marks the job completed only when still {@code IN_PROGRESS} on the given lease
     * ({@code analysis_job_started_at}), so a late worker cannot clear a newer claim.
     */
    void markAnalysisJobCompleted(Connection connection, long recitationId, java.time.Instant leaseStartedAt)
            throws SQLException;

    /**
     * Stale {@code IN_PROGRESS} with an analysis row created during the current lease
     * → {@code COMPLETED} (worker died after persist). Fresh leases are never healed.
     */
    boolean healInProgressWhenAnalysisExists(long recitationId, Timestamp staleCutoff) throws SQLException;

    boolean healInProgressWhenAnalysisExists(Connection connection, long recitationId, Timestamp staleCutoff)
            throws SQLException;

    /**
     * Clears a crashed lease that is past {@code staleCutoff} so a retry or a later submit can proceed.
     * Fresh {@code IN_PROGRESS} rows are not touched.
     */
    boolean abandonStaleInProgress(Connection connection, long recitationId, Timestamp staleCutoff) throws SQLException;

    java.time.Instant findAnalysisJobStartedAt(Connection connection, long recitationId) throws SQLException;

    /** True when state is {@code IN_PROGRESS} and the lease is still fresh. */
    boolean isAnalysisJobActivelyInProgress(long recitationId, java.time.Instant staleCutoff) throws SQLException;

    boolean isAnalysisJobActivelyInProgress(Connection connection, long recitationId, java.time.Instant staleCutoff)
            throws SQLException;

    List<Long> listRecitationIdsByJobState(String jobState) throws SQLException;

    /** {@code COMPLETED} jobs that never persisted an analysis row (persist failed after work). */
    List<Long> listCompletedJobIdsWithoutAnalysis() throws SQLException;
}
