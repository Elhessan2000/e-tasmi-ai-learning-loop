package model.service;

import model.dao.RecitationDao;
import model.dao.impl.RecitationDaoJdbc;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Heals stale analysis job rows after crashes: completes only when the lease expired and an
 * analysis row was persisted during that lease. Fresh {@code IN_PROGRESS} leases are never cleared here.
 */
public class RecitationAnalysisJobRecovery {
    private static final Logger LOGGER = Logger.getLogger(RecitationAnalysisJobRecovery.class.getName());

    private final RecitationDao recitationDao = new RecitationDaoJdbc();

    /** Run once at application startup. Retries if MySQL is not accepting connections yet. */
    public void reconcileAllOnStartup() {
        SQLException last = null;
        for (int attempt = 1; attempt <= 8; attempt++) {
            try {
                List<Long> inProgress = recitationDao.listRecitationIdsByJobState("IN_PROGRESS");
                List<Long> completedWithoutAnalysis = recitationDao.listCompletedJobIdsWithoutAnalysis();
                if (inProgress.isEmpty() && completedWithoutAnalysis.isEmpty()) {
                    if (attempt > 1) {
                        LOGGER.info("Startup analysis reconciliation connected on attempt " + attempt + ".");
                    }
                    return;
                }
                LOGGER.info("Reconciling " + inProgress.size() + " in-progress analysis job(s) and "
                        + completedWithoutAnalysis.size() + " completed-without-analysis job(s) after startup.");
                RecitationAutoAnalysisService analysisService = new RecitationAutoAnalysisService();
                for (Long recitationId : inProgress) {
                    if (recitationId == null || recitationId <= 0) {
                        continue;
                    }
                    reconcileRecitation(recitationId);
                    analysisService.scheduleRecoveryRequeue(recitationId);
                }
                for (Long recitationId : completedWithoutAnalysis) {
                    if (recitationId == null || recitationId <= 0) {
                        continue;
                    }
                    analysisService.scheduleRecoveryRequeue(recitationId);
                }
                return;
            } catch (SQLException ex) {
                last = ex;
                LOGGER.log(Level.WARNING, "Startup analysis reconciliation attempt " + attempt + " failed: "
                        + ex.getClass().getSimpleName());
                try {
                    Thread.sleep(2000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOGGER.log(Level.SEVERE, "Interrupted during startup analysis job reconciliation", last);
                    return;
                }
            }
        }
        LOGGER.log(Level.SEVERE, "Failed startup analysis job reconciliation", last);
    }

    void reconcileRecitation(long recitationId) {
        try {
            Timestamp staleCutoff = Timestamp.from(RecitationAnalysisJobConfig.staleCutoff());
            if (recitationDao.healInProgressWhenAnalysisExists(recitationId, staleCutoff)) {
                LOGGER.info("Healed stale analysis job to COMPLETED recitation_id=" + recitationId
                        + " (analysis persisted during expired lease).");
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to heal analysis job for recitation " + recitationId, ex);
        }
    }

    boolean isBlockingInProgress(long recitationId) throws SQLException {
        Instant cutoff = RecitationAnalysisJobConfig.staleCutoff();
        return recitationDao.isAnalysisJobActivelyInProgress(recitationId, cutoff);
    }
}
