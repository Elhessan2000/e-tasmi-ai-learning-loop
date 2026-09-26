package model.dao;

import model.entity.PasswordReset;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public interface PasswordResetDao {
    long insert(Connection connection, long userId, String tokenHash, Instant expiresAt) throws SQLException;

    Optional<PasswordReset> findActiveByTokenHash(Connection connection, String tokenHash) throws SQLException;

    boolean existsActiveByTokenHash(Connection connection, String tokenHash) throws SQLException;

    boolean markUsed(Connection connection, long resetId) throws SQLException;

    boolean invalidateByUserId(Connection connection, long userId) throws SQLException;
}
