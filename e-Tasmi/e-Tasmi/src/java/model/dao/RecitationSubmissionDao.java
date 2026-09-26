package model.dao;

import model.entity.RecitationSubmission;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface RecitationSubmissionDao {
    /**
     * Creates the {@code recitation_submissions} table if it does not exist.
     * Safe to call repeatedly.
     */
    void ensureSchema(Connection connection) throws SQLException;

    long insert(Connection connection, RecitationSubmission submission) throws SQLException;

    Optional<RecitationSubmission> findById(Connection connection, long recitationId) throws SQLException;

    /**
     * Attaches the stored media path to an existing (placeholder) submission,
     * scoped to its owner. Returns {@code true} when a row was updated.
     */
    boolean updateFilePath(Connection connection, long recitationId, long studentId,
                           String filePath, Long durationSeconds) throws SQLException;

    List<RecitationSubmission> listByStudentId(Connection connection, long studentId) throws SQLException;
}
