package model.dao.impl;

import model.dao.SessionMaterialDao;
import model.entity.SessionMaterial;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SessionMaterialDaoJdbc implements SessionMaterialDao {
    private static final String TABLE = "session_material";

    @Override
    public long insert(Connection connection, SessionMaterial material) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (session_id, uploaded_by_instructor_id, title, file_path, file_type) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, material.getSessionId());
            ps.setLong(2, material.getUploadedByInstructorId());
            ps.setString(3, material.getTitle());
            ps.setString(4, material.getFilePath());
            ps.setString(5, material.getFileType());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to insert session material");
    }

    @Override
    public List<SessionMaterial> listBySessionId(Connection connection, long sessionId) throws SQLException {
        String sql = "SELECT * FROM " + TABLE + " WHERE session_id = ? ORDER BY uploaded_at DESC";
        List<SessionMaterial> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SessionMaterial material = new SessionMaterial();
                    material.setMaterialId(rs.getLong("material_id"));
                    material.setSessionId(rs.getLong("session_id"));
                    material.setUploadedByInstructorId(rs.getLong("uploaded_by_instructor_id"));
                    material.setTitle(rs.getString("title"));
                    material.setFilePath(rs.getString("file_path"));
                    material.setFileType(rs.getString("file_type"));
                    Timestamp uploadedAt = rs.getTimestamp("uploaded_at");
                    if (uploadedAt != null) {
                        material.setUploadedAt(uploadedAt.toInstant());
                    }
                    results.add(material);
                }
            }
        }
        return results;
    }

    @Override
    public Optional<SessionMaterial> findByIdAndSessionId(Connection connection, long materialId, long sessionId) throws SQLException {
        String sql = "SELECT * FROM " + TABLE + " WHERE material_id = ? AND session_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, materialId);
            ps.setLong(2, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                SessionMaterial material = new SessionMaterial();
                material.setMaterialId(rs.getLong("material_id"));
                material.setSessionId(rs.getLong("session_id"));
                material.setUploadedByInstructorId(rs.getLong("uploaded_by_instructor_id"));
                material.setTitle(rs.getString("title"));
                material.setFilePath(rs.getString("file_path"));
                material.setFileType(rs.getString("file_type"));
                Timestamp uploadedAt = rs.getTimestamp("uploaded_at");
                if (uploadedAt != null) {
                    material.setUploadedAt(uploadedAt.toInstant());
                }
                return Optional.of(material);
            }
        }
    }

    @Override
    public boolean deleteByIdAndSessionId(Connection connection, long materialId, long sessionId) throws SQLException {
        String sql = "DELETE FROM " + TABLE + " WHERE material_id = ? AND session_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, materialId);
            ps.setLong(2, sessionId);
            return ps.executeUpdate() == 1;
        }
    }
}
