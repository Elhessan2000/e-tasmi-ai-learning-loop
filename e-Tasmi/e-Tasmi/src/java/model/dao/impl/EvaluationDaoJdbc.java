package model.dao.impl;

import model.dao.EvaluationDao;
import model.entity.Evaluation;

import java.sql.*;
import java.util.Optional;

public class EvaluationDaoJdbc implements EvaluationDao {
    private static final String TABLE = "evaluation";

    private static final String COL_EVALUATION_ID = "evaluation_id";
    private static final String COL_RECITATION_ID = "recitation_id";
    private static final String COL_INSTRUCTOR_ID = "instructor_id";
    private static final String COL_SCORE = "score";
    private static final String COL_FEEDBACK = "feedback";
    private static final String COL_ANALYSIS_ID = "analysis_id";
    private static final String COL_PUBLISHED_AT = "published_at";
    private static final String COL_CREATED_AT = "created_at";

    @Override
    public long insert(Connection connection, Evaluation evaluation) throws SQLException {
        // created_at is stamped here rather than by a column default so that
        // evaluations recorded before the column existed stay NULL instead of
        // being given the migration timestamp.
        // published_at is stamped only for new Phase 4 publications. Historical rows
        // created before this gate stay NULL and are never rewritten.
        String sql = "INSERT INTO " + TABLE + " (" + COL_RECITATION_ID + "," + COL_INSTRUCTOR_ID + "," + COL_SCORE + "," + COL_FEEDBACK
                + "," + COL_ANALYSIS_ID + "," + COL_PUBLISHED_AT + "," + COL_CREATED_AT
                + ") VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, evaluation.getRecitationId());
            ps.setLong(2, evaluation.getInstructorId());
            ps.setInt(3, evaluation.getScore());
            ps.setString(4, evaluation.getFeedback());
            if (evaluation.getAnalysisId() == null) {
                ps.setNull(5, Types.BIGINT);
            } else {
                ps.setLong(5, evaluation.getAnalysisId());
            }

            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Insert evaluation affected " + updated + " rows");
            }

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for evaluation insert");
                }
                return keys.getLong(1);
            }
        }
    }

    @Override
    public boolean update(Connection connection, Evaluation evaluation) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET " + COL_SCORE + " = ?, " + COL_FEEDBACK + " = ? WHERE " + COL_RECITATION_ID + " = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, evaluation.getScore());
            ps.setString(2, evaluation.getFeedback());
            ps.setLong(3, evaluation.getRecitationId());

            int updated = ps.executeUpdate();
            return updated == 1;
        }
    }

    @Override
    public Optional<Evaluation> findByRecitationId(Connection connection, long recitationId) throws SQLException {
        String sql = "SELECT " + COL_EVALUATION_ID + "," + COL_RECITATION_ID + "," + COL_INSTRUCTOR_ID + "," + COL_SCORE + "," + COL_FEEDBACK
                + "," + COL_ANALYSIS_ID + "," + COL_PUBLISHED_AT
                + " FROM " + TABLE + " WHERE " + COL_RECITATION_ID + " = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, recitationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                Evaluation e = new Evaluation();
                e.setEvaluationId(rs.getLong(COL_EVALUATION_ID));
                e.setRecitationId(rs.getLong(COL_RECITATION_ID));
                e.setInstructorId(rs.getLong(COL_INSTRUCTOR_ID));
                e.setScore(rs.getInt(COL_SCORE));
                e.setFeedback(rs.getString(COL_FEEDBACK));
                long analysisId = rs.getLong(COL_ANALYSIS_ID);
                if (!rs.wasNull()) {
                    e.setAnalysisId(analysisId);
                }
                Timestamp published = rs.getTimestamp(COL_PUBLISHED_AT);
                if (published != null) {
                    e.setPublishedAt(published.toInstant());
                }
                return Optional.of(e);
            }
        }
    }
}
