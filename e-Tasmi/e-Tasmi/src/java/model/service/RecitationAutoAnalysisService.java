package model.service;

import model.dao.EvaluationDao;
import model.dao.InstructorDao;
import model.dao.RecitationAnalysisDao;
import model.dao.RecitationDao;
import model.dao.impl.EvaluationDaoJdbc;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.RecitationAnalysisDaoJdbc;
import model.dao.impl.RecitationDaoJdbc;
import model.entity.Instructor;
import model.entity.RecitationAnalysis;
import model.entity.RecitationAnalysisJobState;
import util.Db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Queues automatic AI analysis after student submission and handles instructor retries.
 */
public class RecitationAutoAnalysisService {
    private static final Logger LOGGER = Logger.getLogger(RecitationAutoAnalysisService.class.getName());

    private final RecitationDao recitationDao = new RecitationDaoJdbc();
    private final RecitationAnalysisDao analysisDao = new RecitationAnalysisDaoJdbc();
    private final EvaluationDao evaluationDao = new EvaluationDaoJdbc();
    private final InstructorDao instructorDao = new InstructorDaoJdbc();
    private final RecitationAnalysisPipeline pipeline = new RecitationAnalysisPipeline();
    private final RecitationAnalysisJobRecovery recovery = new RecitationAnalysisJobRecovery();

    /** Called after a recitation row is committed. Idempotent. */
    public void scheduleAfterSubmission(long recitationId) {
        recovery.reconcileRecitation(recitationId);
        if (!tryClaimInitial(recitationId)) {
            return;
        }
        enqueueAfterClaim(recitationId);
    }

    /**
     * After startup reconciliation: reclaim stale leases. Recitations with no analysis
     * are queued as an initial run. Recitations whose latest analysis is retryable are
     * queued as a retry. Other stale leases are abandoned so they do not block the UI.
     */
    void scheduleRecoveryRequeue(long recitationId) {
        try (Connection connection = Db.getConnection()) {
            Timestamp staleCutoff = Timestamp.from(RecitationAnalysisJobConfig.staleCutoff());
            if (evaluationDao.findByRecitationId(connection, recitationId).isPresent()) {
                recitationDao.abandonStaleInProgress(connection, recitationId, staleCutoff);
                return;
            }
            Optional<RecitationAnalysis> latest = analysisDao.findLatestByRecitationId(connection, recitationId);
            if (latest.isEmpty()) {
                if (tryClaimInitial(recitationId)) {
                    enqueueAfterClaim(recitationId);
                }
                return;
            }
            if (!isRetryableStatus(latest.get().getStatus())) {
                recitationDao.abandonStaleInProgress(connection, recitationId, staleCutoff);
                return;
            }
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (!recitationDao.tryClaimRetryAnalysisJob(connection, recitationId, staleCutoff)) {
                    connection.rollback();
                    return;
                }
                Instant leaseStartedAt = recitationDao.findAnalysisJobStartedAt(connection, recitationId);
                connection.commit();
                enqueue(recitationId, leaseStartedAt);
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Could not requeue stale analysis for recitation " + recitationId, ex);
        }
    }

    /**
     * Ensures analysis is queued when a recitation has none yet (legacy submissions).
     * Safe to call from instructor page loads; uses the same claim rules as submission.
     */
    public void ensureQueuedIfMissing(long recitationId) {
        if (recitationId <= 0) {
            return;
        }
        try (Connection connection = Db.getConnection()) {
            recovery.reconcileRecitation(recitationId);
            if (analysisDao.findLatestByRecitationId(connection, recitationId).isPresent()) {
                return;
            }
            if (recitationDao.isAnalysisJobActivelyInProgress(connection, recitationId,
                    RecitationAnalysisJobConfig.staleCutoff())) {
                return;
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Could not inspect analysis queue for recitation " + recitationId, ex);
            return;
        }
        scheduleAfterSubmission(recitationId);
    }

    /**
     * Instructor retry after a safe failure, or to recover a missing analysis on legacy rows.
     *
     * @return {@code null} on success, otherwise a user-facing error message
     */
    public String scheduleInstructorRetry(long instructorUserId, long recitationId) {
        if (recitationId <= 0) {
            return "Invalid recitation.";
        }
        try (Connection connection = Db.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                recovery.reconcileRecitation(recitationId);
                Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, instructorUserId);
                if (instructorOpt.isEmpty()) {
                    connection.rollback();
                    return "Instructor profile not found.";
                }
                boolean owns = recitationDao.listForInstructor(connection, instructorOpt.get().getInstructorId())
                        .stream().anyMatch(r -> r != null && r.getRecitationId() == recitationId);
                if (!owns) {
                    connection.rollback();
                    return "You cannot analyze this recitation.";
                }
                if (evaluationDao.findByRecitationId(connection, recitationId).isPresent()) {
                    connection.rollback();
                    return "This evaluation is already saved, so its findings are final.";
                }
                if (recitationDao.isAnalysisJobActivelyInProgress(connection, recitationId,
                        RecitationAnalysisJobConfig.staleCutoff())) {
                    connection.rollback();
                    return "AI analysis is already in progress for this recitation.";
                }
                Optional<RecitationAnalysis> latest = analysisDao.findLatestByRecitationId(connection, recitationId);
                Timestamp staleCutoff = Timestamp.from(RecitationAnalysisJobConfig.staleCutoff());
                if (latest.isEmpty()) {
                    if (!recitationDao.tryClaimInitialAnalysisJob(connection, recitationId, staleCutoff)) {
                        connection.rollback();
                        return "AI analysis is already in progress for this recitation.";
                    }
                    Instant leaseStartedAt = recitationDao.findAnalysisJobStartedAt(connection, recitationId);
                    connection.commit();
                    enqueue(recitationId, leaseStartedAt);
                    return null;
                }
                String status = latest.get().getStatus();
                if (!isRetryableStatus(status)) {
                    connection.rollback();
                    return "The latest analysis cannot be retried. Review the existing findings or save your evaluation.";
                }
                if (!recitationDao.tryClaimRetryAnalysisJob(connection, recitationId, staleCutoff)) {
                    connection.rollback();
                    return "AI analysis is already in progress for this recitation.";
                }
                Instant leaseStartedAt = recitationDao.findAnalysisJobStartedAt(connection, recitationId);
                connection.commit();
                enqueue(recitationId, leaseStartedAt);
                return null;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to schedule analysis retry for recitation " + recitationId, ex);
            return "Could not start analysis retry due to a server error.";
        }
    }

    private boolean tryClaimInitial(long recitationId) {
        try (Connection connection = Db.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Timestamp staleCutoff = Timestamp.from(RecitationAnalysisJobConfig.staleCutoff());
                if (!recitationDao.tryClaimInitialAnalysisJob(connection, recitationId, staleCutoff)) {
                    connection.rollback();
                    return false;
                }
                connection.commit();
                return true;
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to claim initial analysis job for recitation " + recitationId, ex);
            return false;
        }
    }

    private void enqueueAfterClaim(long recitationId) {
        try (Connection connection = Db.getConnection()) {
            Instant leaseStartedAt = recitationDao.findAnalysisJobStartedAt(connection, recitationId);
            enqueue(recitationId, leaseStartedAt);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to read analysis lease for recitation " + recitationId, ex);
        }
    }

    private void enqueue(long recitationId, Instant leaseStartedAt) {
        Instant lease = leaseStartedAt;
        RecitationAutoAnalysisExecutor.submit(recitationId, () -> {
            try {
                pipeline.runForRecitation(recitationId);
            } finally {
                markJobCompleted(recitationId, lease);
            }
        });
        LOGGER.info("Queued auto analysis recitation_id=" + recitationId);
    }

    private void markJobCompleted(long recitationId, Instant leaseStartedAt) {
        try (Connection connection = Db.getConnection()) {
            recitationDao.markAnalysisJobCompleted(connection, recitationId, leaseStartedAt);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to mark analysis job completed for recitation " + recitationId, ex);
        }
    }

    private static boolean isRetryableStatus(String status) {
        if (status == null) {
            return false;
        }
        switch (status.trim()) {
            case "FAILED":
            case "CANNOT_EVALUATE":
            case "REFERENCE_UNAVAILABLE":
                return true;
            default:
                return false;
        }
    }
}
