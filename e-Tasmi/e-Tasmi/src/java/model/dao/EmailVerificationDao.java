package model.dao;

import model.entity.EmailVerification;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

public interface EmailVerificationDao {
    boolean upsert(Connection connection, long userId, String codeHash, Instant expiresAt) throws SQLException;

    Optional<EmailVerification> findActiveByUserId(Connection connection, long userId) throws SQLException;

    boolean incrementAttempts(Connection connection, long userId) throws SQLException;

    boolean deleteByUserId(Connection connection, long userId) throws SQLException;
}
