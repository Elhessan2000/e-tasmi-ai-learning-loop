package model.dao.impl;

import model.dao.PasswordResetDao;
import model.entity.PasswordReset;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;

public class PasswordResetDaoJdbc implements PasswordResetDao {
    private static final String TABLE = "password_reset";

    @Override
    public long insert(Connection connection, long userId, String tokenHash, Instant expiresAt) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (user_id, token_hash, expires_at, created_at) VALUES (?,?,?,CURRENT_TIMESTAMP)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, tokenHash);
            ps.setTimestamp(3, expiresAt == null ? null : Timestamp.from(expiresAt));
            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Insert password reset affected " + updated + " rows");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for password reset");
                }
                return keys.getLong(1);
            }
        }
    }

    @Override
    public Optional<PasswordReset> findActiveByTokenHash(Connection connection, String tokenHash) throws SQLException {
        String sql = "SELECT reset_id, user_id, token_hash, expires_at, used_at, created_at " +
                "FROM " + TABLE + " WHERE token_hash = ? AND used_at IS NULL AND expires_at > CURRENT_TIMESTAMP LIMIT 1";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    @Override
    public boolean existsActiveByTokenHash(Connection connection, String tokenHash) throws SQLException {
        String sql = "SELECT 1 FROM " + TABLE + " WHERE token_hash = ? AND used_at IS NULL AND expires_at > CURRENT_TIMESTAMP LIMIT 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tokenHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean markUsed(Connection connection, long resetId) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET used_at = CURRENT_TIMESTAMP WHERE reset_id = ? AND used_at IS NULL";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, resetId);
            int updated = ps.executeUpdate();
            return updated == 1;
        }
    }

    @Override
    public boolean invalidateByUserId(Connection connection, long userId) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET used_at = CURRENT_TIMESTAMP WHERE user_id = ? AND used_at IS NULL";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
            return true;
        }
    }

    private PasswordReset map(ResultSet rs) throws SQLException {
        PasswordReset pr = new PasswordReset();
        pr.setResetId(rs.getLong("reset_id"));
        pr.setUserId(rs.getLong("user_id"));
        pr.setTokenHash(rs.getString("token_hash"));

        Timestamp expires = rs.getTimestamp("expires_at");
        if (expires != null) {
            pr.setExpiresAt(expires.toInstant());
        }
        Timestamp used = rs.getTimestamp("used_at");
        if (used != null) {
            pr.setUsedAt(used.toInstant());
        }
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            pr.setCreatedAt(created.toInstant());
        }
        return pr;
    }
}
