package model.service;

import model.dao.InstructorDao;
import model.dao.RecitationAnalysisDao;
import model.dao.RecitationDao;
import model.dao.RecitationFindingDao;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.RecitationAnalysisDaoJdbc;
import model.dao.impl.RecitationDaoJdbc;
import model.dao.impl.RecitationFindingDaoJdbc;
import model.entity.Instructor;
import model.entity.Recitation;
import model.entity.RecitationAnalysis;
import model.entity.RecitationFindingRecord;
import model.service.RecitationAiAnalysisService.AnalysisResult;
import model.service.analysis.RecitationFinding;
import util.Db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Persists and reloads recitation analyses. History is append-only: {@link #save} never updates
 * or deletes an earlier run. Findings are stored as {@code PROPOSED} / {@code PENDING}.
 */
public class RecitationAnalysisService {
    private static final Logger LOGGER = Logger.getLogger(RecitationAnalysisService.class.getName());

    private final InstructorDao instructorDao;
    private final RecitationDao recitationDao;
    private final RecitationAnalysisDao analysisDao;
    private final RecitationFindingDao findingDao;

    public RecitationAnalysisService() {
        this.instructorDao = new InstructorDaoJdbc();
        this.recitationDao = new RecitationDaoJdbc();
        this.analysisDao = new RecitationAnalysisDaoJdbc();
        this.findingDao = new RecitationFindingDaoJdbc();
    }

    /**
     * Inserts one analysis run and its findings in a single transaction.
     *
     * @return the new {@code analysis_id}
     * @throws IllegalStateException when the instructor does not own the recitation
     */
    public long save(long instructorUserId, long recitationId, AnalysisResult result) throws SQLException {
        if (result == null || result.getStatus() == null) {
            throw new IllegalArgumentException("Analysis result is required.");
        }
        try (Connection connection = Db.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (!owns(connection, instructorUserId, recitationId)) {
                    connection.rollback();
                    throw new IllegalStateException("You cannot save an analysis for this recitation.");
                }
                long analysisId = persistAnalysis(connection, recitationId, result);
                connection.commit();
                // Counts, status, source and verse keys only. No transcript and no Qur'an text.
                LOGGER.info("Persisted recitation analysis id=" + analysisId
                        + " recitation_id=" + recitationId
                        + " status=" + result.getStatus().name()
                        + " findings=" + size(result.getFindings())
                        + " reference_source=" + result.getReferenceSource()
                        + " verse_keys=" + result.getReferenceVerseKeys());
                return analysisId;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    /** Persists an analysis run for automatic post-submission processing (no instructor session). */
    public long saveForRecitation(long recitationId, AnalysisResult result) throws SQLException {
        if (recitationId <= 0) {
            throw new IllegalArgumentException("Invalid recitation.");
        }
        if (result == null || result.getStatus() == null) {
            throw new IllegalArgumentException("Analysis result is required.");
        }
        try (Connection connection = Db.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                if (recitationDao.findById(connection, recitationId).isEmpty()) {
                    connection.rollback();
                    throw new IllegalStateException("Recitation not found.");
                }
                long analysisId = persistAnalysis(connection, recitationId, result);
                connection.commit();
                LOGGER.info("Persisted auto recitation analysis id=" + analysisId
                        + " recitation_id=" + recitationId
                        + " status=" + result.getStatus().name()
                        + " findings=" + size(result.getFindings()));
                return analysisId;
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        }
    }

    private long persistAnalysis(Connection connection, long recitationId, AnalysisResult result) throws SQLException {
        RecitationAnalysis row = toRow(recitationId, result);
        long analysisId = analysisDao.insert(connection, row);
        findingDao.insertAll(connection, analysisId, recitationId, result.getFindings());
        return analysisId;
    }

    public Optional<AnalysisResult> loadLatestReport(long instructorUserId, long recitationId) {
        try (Connection connection = Db.getConnection()) {
            if (!owns(connection, instructorUserId, recitationId)) {
                return Optional.empty();
            }
            Optional<RecitationAnalysis> latest = analysisDao.findLatestByRecitationId(connection, recitationId);
            if (latest.isEmpty()) {
                return Optional.empty();
            }
            RecitationAnalysis row = latest.get();
            row.setFindings(findingDao.listByAnalysisId(connection, row.getAnalysisId()));
            return Optional.of(AnalysisResult.restored(row));
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load latest analysis for recitation " + recitationId, ex);
            return Optional.empty();
        }
    }

    /** Newest analysis header (with findings) for each owned recitation. Unowned ids are ignored. */
    public Map<Long, RecitationAnalysis> loadLatestForRecitations(long instructorUserId, List<Long> recitationIds) {
        if (recitationIds == null || recitationIds.isEmpty()) {
            return Map.of();
        }
        try (Connection connection = Db.getConnection()) {
            List<Long> owned = ownedIds(connection, instructorUserId, recitationIds);
            Map<Long, RecitationAnalysis> latest = analysisDao.findLatestByRecitationIds(connection, owned);
            attachFindings(connection, latest.values());
            return latest;
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load latest analyses", ex);
            return Map.of();
        }
    }

    /**
     * Servlet-facing reload. {@code AnalysisResult.restored} is package-private, so the conversion
     * stays in this package.
     */
    public Map<Long, AnalysisResult> loadLatestReports(long instructorUserId, List<Long> recitationIds) {
        Map<Long, AnalysisResult> reports = new LinkedHashMap<>();
        for (Map.Entry<Long, RecitationAnalysis> entry : loadLatestForRecitations(instructorUserId, recitationIds).entrySet()) {
            reports.put(entry.getKey(), AnalysisResult.restored(entry.getValue()));
        }
        return reports;
    }

    /** Newest first. Empty when the instructor does not own the recitation. */
    public List<RecitationAnalysis> history(long instructorUserId, long recitationId) {
        try (Connection connection = Db.getConnection()) {
            if (!owns(connection, instructorUserId, recitationId)) {
                return List.of();
            }
            List<RecitationAnalysis> rows = analysisDao.listByRecitationId(connection, recitationId);
            attachFindings(connection, rows);
            return rows;
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load analysis history for recitation " + recitationId, ex);
            return List.of();
        }
    }

    private void attachFindings(Connection connection, Collection<RecitationAnalysis> analyses) throws SQLException {
        if (analyses == null || analyses.isEmpty()) {
            return;
        }
        List<Long> analysisIds = new ArrayList<>(analyses.size());
        for (RecitationAnalysis analysis : analyses) {
            if (analysis != null) {
                analysisIds.add(analysis.getAnalysisId());
            }
        }
        Map<Long, List<RecitationFindingRecord>> findings = findingDao.listByAnalysisIds(connection, analysisIds);
        for (RecitationAnalysis analysis : analyses) {
            if (analysis == null) {
                continue;
            }
            List<RecitationFindingRecord> rows = findings.get(analysis.getAnalysisId());
            analysis.setFindings(rows == null ? List.of() : rows);
        }
    }

    private boolean owns(Connection connection, long instructorUserId, long recitationId) throws SQLException {
        return ownedIds(connection, instructorUserId, List.of(recitationId)).contains(recitationId);
    }

    private List<Long> ownedIds(Connection connection, long instructorUserId, List<Long> requested) throws SQLException {
        Optional<Instructor> instructor = instructorDao.findByUserId(connection, instructorUserId);
        if (instructor.isEmpty() || requested == null || requested.isEmpty()) {
            return List.of();
        }
        List<Recitation> owned = recitationDao.listForInstructor(connection, instructor.get().getInstructorId());
        List<Long> ids = new ArrayList<>();
        for (Long requestedId : requested) {
            if (requestedId == null || requestedId <= 0) {
                continue;
            }
            for (Recitation recitation : owned) {
                if (recitation != null && recitation.getRecitationId() == requestedId) {
                    ids.add(requestedId);
                    break;
                }
            }
        }
        return ids;
    }

    private static RecitationAnalysis toRow(long recitationId, AnalysisResult result) {
        RecitationAnalysis row = new RecitationAnalysis();
        row.setRecitationId(recitationId);
        row.setStatus(result.getStatus().name());
        row.setStatusReason(result.getStatus() == RecitationAiAnalysisService.Status.OK ? null : result.getReason());
        row.setSttProvider(result.getSttProvider());
        row.setSttModel(result.getSttModel());
        row.setAnalysisModel(result.getAnalysisModel());
        row.setTranscript(result.getTranscript());
        row.setReferenceSource(result.getReferenceSource());
        row.setReferenceVerseKeys(result.getReferenceVerseKeys());
        row.setReferenceText(result.getReferenceText());
        if (result.getStatus() == RecitationAiAnalysisService.Status.OK) {
            row.setMatchesExpectedPassage(result.isMatchesExpectedPassage());
            row.setAccuracyPercent(clamp(result.getAccuracyPercent(), 0, 100));
            row.setAiSuggestedScore((int) Math.round(clamp(result.getScore(), 0, 100)));
            row.setAiSummary(result.getSummary());
            row.setAiFeedback(result.getFeedback());
            row.setMatchedPassageNote(result.getMatchedPassageNote());
            row.setCorrectWordCount(result.getCorrectWords().size());
            row.setIsQuranConfidence(clamp(result.getIsQuranConfidence(), 0, 1));
            row.setMixedPassages(result.isMixedPassages());
            row.setDetectedPassages(joinPassages(result.getDetectedPassages()));
        }
        return row;
    }

    private static String joinPassages(List<String> passages) {
        if (passages == null || passages.isEmpty()) {
            return null;
        }
        StringBuilder joined = new StringBuilder();
        for (String passage : passages) {
            if (passage == null || passage.isBlank()) {
                continue;
            }
            String piece = joined.length() == 0 ? passage.trim() : ", " + passage.trim();
            if (joined.length() + piece.length() > 255) {
                break;
            }
            joined.append(piece);
        }
        return joined.length() == 0 ? null : joined.toString();
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static int size(List<RecitationFinding> findings) {
        return findings == null ? 0 : findings.size();
    }
}
