package model.dao.impl;

import model.dao.SessionAttendanceDao;
import model.entity.AttendanceStatus;
import model.entity.SessionAttendance;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class SessionAttendanceDaoJdbc implements SessionAttendanceDao {
    private static final String TABLE = "attendance";

    @Override
    public long insert(Connection connection, SessionAttendance attendance) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (session_id, student_id, marked_by_instructor_id, attendance_status, notes, marked_at) VALUES (?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, attendance.getSessionId());
            ps.setLong(2, attendance.getStudentId());
            ps.setLong(3, attendance.getMarkedByInstructorId());
            ps.setString(4, attendance.getAttendanceStatus() == null ? null : attendance.getAttendanceStatus().name());
            ps.setString(5, attendance.getNotes());
            if (attendance.getMarkedAt() == null) {
                ps.setTimestamp(6, Timestamp.from(Instant.now()));
            } else {
                ps.setTimestamp(6, Timestamp.from(attendance.getMarkedAt()));
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to insert attendance");
    }

    @Override
    public boolean update(Connection connection, SessionAttendance attendance) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET marked_by_instructor_id = ?, attendance_status = ?, notes = ?, marked_at = ? WHERE attendance_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, attendance.getMarkedByInstructorId());
            ps.setString(2, attendance.getAttendanceStatus() == null ? null : attendance.getAttendanceStatus().name());
            ps.setString(3, attendance.getNotes());
            ps.setTimestamp(4, Timestamp.from(attendance.getMarkedAt() == null ? Instant.now() : attendance.getMarkedAt()));
            ps.setLong(5, attendance.getAttendanceId());
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public Optional<SessionAttendance> findBySessionIdAndStudentId(Connection connection, long sessionId, long studentId) throws SQLException {
        String sql = "SELECT * FROM " + TABLE + " WHERE session_id = ? AND student_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            ps.setLong(2, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    @Override
    public List<SessionAttendance> listBySessionId(Connection connection, long sessionId) throws SQLException {
        String sql = "SELECT * FROM " + TABLE + " WHERE session_id = ? ORDER BY student_id ASC";
        List<SessionAttendance> results = new ArrayList<>();
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
    public Set<Long> findSessionIdsWithPresentAttendance(Connection connection, long studentId) throws SQLException {
        String sql = "SELECT session_id FROM " + TABLE + " WHERE student_id = ? AND attendance_status = ?";
        Set<Long> set = new HashSet<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setString(2, AttendanceStatus.PRESENT.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    set.add(rs.getLong("session_id"));
                }
            }
        }
        return set;
    }

    private SessionAttendance map(ResultSet rs) throws SQLException {
        SessionAttendance attendance = new SessionAttendance();
        attendance.setAttendanceId(rs.getLong("attendance_id"));
        attendance.setSessionId(rs.getLong("session_id"));
        attendance.setStudentId(rs.getLong("student_id"));
        attendance.setMarkedByInstructorId(rs.getLong("marked_by_instructor_id"));
        attendance.setAttendanceStatus(AttendanceStatus.fromString(rs.getString("attendance_status")));
        attendance.setNotes(rs.getString("notes"));
        Timestamp markedAt = rs.getTimestamp("marked_at");
        if (markedAt != null) {
            attendance.setMarkedAt(markedAt.toInstant());
        }
        return attendance;
    }
}
