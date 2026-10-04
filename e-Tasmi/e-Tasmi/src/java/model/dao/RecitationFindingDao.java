package model.dao;

import model.entity.RecitationFindingRecord;
import model.service.analysis.FindingType;
import model.service.analysis.RecitationFinding;

import model.service.analysis.FindingInstructorStatus;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface RecitationFindingDao {
    /**
     * Inserts findings for one analysis. Always stores {@code ai_status=PROPOSED} and
     * {@code instructor_status=PENDING}. Instructor decision columns are left null.
     */
    void insertAll(Connection connection, long analysisId, long recitationId, List<RecitationFinding> findings)
            throws SQLException;

    List<RecitationFindingRecord> listByAnalysisId(Connection connection, long analysisId) throws SQLException;

    /** Findings grouped by analysis id, in insertion order within each analysis. */
    Map<Long, List<RecitationFindingRecord>> listByAnalysisIds(Connection connection, Collection<Long> analysisIds)
            throws SQLException;

    Optional<RecitationFindingRecord> findById(Connection connection, long findingId) throws SQLException;

    /**
     * Records an instructor decision. Only {@code instructor_status}, the four {@code instructor_*}
     * columns and the decision stamp are written: the AI columns that describe the original
     * proposal are never part of this statement, so an edit cannot overwrite what the AI said.
     *
     * <p>Passing null for a text field clears it, which is how Accept-after-Edit drops an override
     * instead of hiding it under an {@code ACCEPTED} status.</p>
     */
    boolean updateDecision(Connection connection,
                           long findingId,
                           FindingInstructorStatus status,
                           String instructorExpectedText,
                           String instructorHeardText,
                           String instructorExplanation,
                           String instructorNote,
                           long decidedByInstructorId) throws SQLException;

    /**
     * Inserts a finding the instructor wrote themselves: {@code ai_status=NOT_APPLICABLE} and
     * {@code instructor_status=INSTRUCTOR_ADDED}, with every AI text column left null so it can
     * never be mistaken for an AI proposal.
     */
    long insertInstructorAdded(Connection connection,
                               long analysisId,
                               long recitationId,
                               FindingType findingType,
                               String verseKey,
                               Integer wordPosition,
                               String instructorExpectedText,
                               String instructorHeardText,
                               String instructorExplanation,
                               String instructorNote,
                               long decidedByInstructorId) throws SQLException;

    /** Number of findings on this analysis still awaiting an instructor decision. */
    int countPendingByAnalysisId(Connection connection, long analysisId) throws SQLException;
}
