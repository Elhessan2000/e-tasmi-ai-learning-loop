package model.dao.impl;

import model.dao.EmailVerificationDao;
import model.entity.EmailVerification;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;

public class EmailVerificationDaoJdbc implements EmailVerificationDao {
    private static final String TABLE = "email_verification";

    @Override
    public boolean upsert(Connection connection, long userId, String codeHash, Instant expiresAt) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (user_id, code_hash, expires_at, attempts, created_at) " +
                "VALUES (?,?,?,0,CURRENT_TIMESTAMP) " +
                "ON DUPLICATE KEY UPDATE code_hash=VALUES(code_hash), expires_at=VALUES(expires_at), attempts=0";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, codeHash);
            ps.setTimestamp(3, expiresAt == null ? null : Timestamp.from(expiresAt));
            int updated = ps.executeUpdate();
            return updated >= 1;
        }
    }

    @Override
    public Optional<EmailVerification> findActiveByUserId(Connection connection, long userId) throws SQLException {
        String sql = "SELECT user_id, code_hash, expires_at, attempts, created_at " +
                "FROM " + TABLE + " WHERE user_id = ? AND expires_at > CURRENT_TIMESTAMP LIMIT 1";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    @Override
    public boolean incrementAttempts(Connection connection, long userId) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET attempts = attempts + 1 WHERE user_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            int updated = ps.executeUpdate();
            return updated == 1;
        }
    }

    @Override
    public boolean deleteByUserId(Connection connection, long userId) throws SQLException {
        String sql = "DELETE FROM " + TABLE + " WHERE user_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
            return true;
        }
    }

    private EmailVerification map(ResultSet rs) throws SQLException {
        EmailVerification ev = new EmailVerification();
        ev.setUserId(rs.getLong("user_id"));
        ev.setCodeHash(rs.getString("code_hash"));

        Timestamp expires = rs.getTimestamp("expires_at");
        if (expires != null) {
            ev.setExpiresAt(expires.toInstant());
        }
        ev.setAttempts(rs.getInt("attempts"));

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            ev.setCreatedAt(created.toInstant());
        }
        return ev;
    }
}
