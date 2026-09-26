package model.dao;

import model.entity.TasmiSession;
import model.entity.TasmiSessionStatus;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TasmiSessionDao {
    long insert(Connection connection, TasmiSession session) throws SQLException;
    Optional<TasmiSession> findById(Connection connection, long sessionId) throws SQLException;

    boolean updateByIdAndInstructorId(Connection connection, TasmiSession session) throws SQLException;

    boolean deleteByIdAndInstructorId(Connection connection, long sessionId, long instructorId) throws SQLException;

    List<TasmiSession> listByInstructorId(Connection connection, long instructorId) throws SQLException;
    List<TasmiSession> listByStatus(Connection connection, TasmiSessionStatus status) throws SQLException;

    boolean updateStatusByIdAndInstructorId(Connection connection, long sessionId, long instructorId, TasmiSessionStatus newStatus) throws SQLException;

    boolean updateLiveDetailsAndStatusByIdAndInstructorId(Connection connection,
                                                          long sessionId,
                                                          long instructorId,
                                                          TasmiSession session) throws SQLException;

    /**
     * Sets {@code evaluation_reviewed_at} for a session owned by the given
     * instructor. Used by the Instructor Evaluation Dashboard to move a
     * session from <em>Active Evaluations</em> into <em>Reviewed Sessions</em>.
     */
    boolean markEvaluationReviewed(Connection connection,
                                   long sessionId,
                                   long instructorId,
                                   Instant reviewedAt) throws SQLException;

    /**
     * Clears {@code evaluation_reviewed_at} (reopens the session for review).
     */
    boolean clearEvaluationReviewed(Connection connection,
                                    long sessionId,
                                    long instructorId) throws SQLException;
}
