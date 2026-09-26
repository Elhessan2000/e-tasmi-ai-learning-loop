package model.dao.impl;

import model.dao.ProgressDao;
import model.entity.Progress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;

public class ProgressDaoJdbc implements ProgressDao {
    private static final String TABLE = "progress";

    private static final String COL_STUDENT_ID = "student_id";
    private static final String COL_COMPLETION_RATE = "completion_rate";
    private static final String COL_LAST_UPDATED = "last_updated";

    @Override
    public Optional<Progress> findByStudentId(Connection connection, long studentId) throws SQLException {
        String sql = "SELECT " + COL_STUDENT_ID + "," + COL_COMPLETION_RATE + "," + COL_LAST_UPDATED +
                " FROM " + TABLE + " WHERE " + COL_STUDENT_ID + " = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                Progress p = new Progress();
                p.setStudentId(rs.getLong(COL_STUDENT_ID));
                p.setCompletionRate(rs.getBigDecimal(COL_COMPLETION_RATE));
                Timestamp ts = rs.getTimestamp(COL_LAST_UPDATED);
                if (ts != null) {
                    p.setLastUpdated(ts.toInstant());
                }
                return Optional.of(p);
            }
        }
    }

    @Override
    public BigDecimal computeCompletionRate(Connection connection, long studentId) throws SQLException {
        // Average evaluation score (0..100) across all evaluations for this student's recitations
        String sql = "SELECT AVG(ev.score) AS avg_score" +
                " FROM evaluation ev" +
                " JOIN recitation r ON ev.recitation_id = r.recitation_id" +
                " JOIN enrollment e ON r.enrollment_id = e.enrollment_id" +
                " WHERE e.student_id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return BigDecimal.ZERO;
                }

                BigDecimal avg = rs.getBigDecimal("avg_score");
                if (avg == null) {
                    return BigDecimal.ZERO;
                }

                if (avg.compareTo(BigDecimal.ZERO) < 0) {
                    avg = BigDecimal.ZERO;
                }
                if (avg.compareTo(new BigDecimal("100")) > 0) {
                    avg = new BigDecimal("100");
                }
                return avg.setScale(2, RoundingMode.HALF_UP);
            }
        }
    }

    @Override
    public void upsert(Connection connection, long studentId, BigDecimal completionRate) throws SQLException {
        if (completionRate == null) {
            completionRate = BigDecimal.ZERO;
        }

        // MySQL upsert
        String sql = "INSERT INTO " + TABLE + " (" + COL_STUDENT_ID + "," + COL_COMPLETION_RATE + "," + COL_LAST_UPDATED + ")" +
                " VALUES (?,?,?)" +
                " ON DUPLICATE KEY UPDATE " + COL_COMPLETION_RATE + " = VALUES(" + COL_COMPLETION_RATE + "), " +
                COL_LAST_UPDATED + " = VALUES(" + COL_LAST_UPDATED + ")";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setBigDecimal(2, completionRate);
            ps.setTimestamp(3, Timestamp.from(Instant.now()));
            ps.executeUpdate();
        }
    }
}
