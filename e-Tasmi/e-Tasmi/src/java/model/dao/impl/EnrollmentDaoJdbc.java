package model.dao.impl;

import model.dao.EnrollmentDao;
import model.entity.Enrollment;
import model.entity.EnrollmentStatus;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class EnrollmentDaoJdbc implements EnrollmentDao {
    private static final String TABLE = "enrollment";

    private static final String COL_ENROLLMENT_ID = "enrollment_id";
    private static final String COL_STUDENT_ID = "student_id";
    private static final String COL_SESSION_ID = "session_id";
    private static final String COL_ENROLLMENT_STATUS = "enrollment_status";

    @Override
    public long insert(Connection connection, Enrollment enrollment) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (" + COL_STUDENT_ID + "," + COL_SESSION_ID + "," + COL_ENROLLMENT_STATUS + ") VALUES (?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, enrollment.getStudentId());
            ps.setLong(2, enrollment.getSessionId());
            ps.setString(3, enrollment.getEnrollmentStatus() == null ? null : enrollment.getEnrollmentStatus().name());

            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Insert enrollment affected " + updated + " rows");
            }

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for enrollment insert");
                }
                return keys.getLong(1);
            }
        }
    }

    @Override
    public Optional<Enrollment> findById(Connection connection, long enrollmentId) throws SQLException {
        String sql = "SELECT " + COL_ENROLLMENT_ID + "," + COL_STUDENT_ID + "," + COL_SESSION_ID + "," + COL_ENROLLMENT_STATUS +
                " FROM " + TABLE + " WHERE " + COL_ENROLLMENT_ID + " = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, enrollmentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    @Override
    public Optional<Enrollment> findByStudentAndSession(Connection connection, long studentId, long sessionId) throws SQLException {
        String sql = "SELECT " + COL_ENROLLMENT_ID + "," + COL_STUDENT_ID + "," + COL_SESSION_ID + "," + COL_ENROLLMENT_STATUS +
                " FROM " + TABLE + " WHERE " + COL_STUDENT_ID + " = ? AND " + COL_SESSION_ID + " = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setLong(2, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    @Override
    public List<Enrollment> listByStudentId(Connection connection, long studentId) throws SQLException {
        String sql = "SELECT " + COL_ENROLLMENT_ID + "," + COL_STUDENT_ID + "," + COL_SESSION_ID + "," + COL_ENROLLMENT_STATUS +
                " FROM " + TABLE + " WHERE " + COL_STUDENT_ID + " = ? ORDER BY " + COL_ENROLLMENT_ID + " DESC";

        List<Enrollment> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        }
        return results;
    }

    @Override
    public List<Enrollment> listBySessionId(Connection connection, long sessionId) throws SQLException {
        String sql = "SELECT " + COL_ENROLLMENT_ID + "," + COL_STUDENT_ID + "," + COL_SESSION_ID + "," + COL_ENROLLMENT_STATUS +
                " FROM " + TABLE + " WHERE " + COL_SESSION_ID + " = ? ORDER BY " + COL_ENROLLMENT_ID + " DESC";

        List<Enrollment> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        }
        return results;
    }

    @Override
    public boolean updateStatus(Connection connection, long enrollmentId, EnrollmentStatus newStatus) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET " + COL_ENROLLMENT_STATUS + " = ? WHERE " + COL_ENROLLMENT_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newStatus == null ? null : newStatus.name());
            ps.setLong(2, enrollmentId);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public int countActiveBySessionId(Connection connection, long sessionId) throws SQLException {
        String sql = "SELECT COUNT(*) AS c FROM " + TABLE + " WHERE " + COL_SESSION_ID + " = ? AND " + COL_ENROLLMENT_STATUS + " IN ('PENDING','APPROVED')";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                return rs.getInt("c");
            }
        }
    }

    @Override
    public int countOccupyingBySessionId(Connection connection, long sessionId) throws SQLException {
        String sql = "SELECT COUNT(*) AS c FROM " + TABLE + " e "
                + "JOIN student st ON st.student_id = e.student_id "
                + "LEFT JOIN `user` u ON u.user_id = st.user_id "
                + "WHERE e.session_id = ? "
                + "AND e.enrollment_status IN ('PENDING','APPROVED') "
                + "AND ((u.user_id IS NOT NULL AND u.status <> 'DELETED') "
                + "OR EXISTS (SELECT 1 FROM recitation r WHERE r.enrollment_id = e.enrollment_id))";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                return rs.getInt("c");
            }
        }
    }

    @Override
    public Set<Long> enrollmentIdsWithRecitations(Connection connection, long sessionId) throws SQLException {
        String sql = "SELECT DISTINCT r.enrollment_id FROM recitation r "
                + "JOIN " + TABLE + " e ON e.enrollment_id = r.enrollment_id "
                + "WHERE e.session_id = ?";
        Set<Long> ids = new HashSet<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong("enrollment_id"));
                }
            }
        }
        return ids;
    }

    private Enrollment map(ResultSet rs) throws SQLException {
        Enrollment e = new Enrollment();
        e.setEnrollmentId(rs.getLong(COL_ENROLLMENT_ID));
        e.setStudentId(rs.getLong(COL_STUDENT_ID));
        e.setSessionId(rs.getLong(COL_SESSION_ID));
        e.setEnrollmentStatus(EnrollmentStatus.fromString(rs.getString(COL_ENROLLMENT_STATUS)));
        return e;
    }
}
