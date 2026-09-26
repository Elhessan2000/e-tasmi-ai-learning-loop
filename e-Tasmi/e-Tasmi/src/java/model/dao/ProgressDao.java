package model.dao;

import model.entity.Progress;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public interface ProgressDao {
    Optional<Progress> findByStudentId(Connection connection, long studentId) throws SQLException;

    /**
     * Computes a progress rate (0..100) based on average evaluation score for the student.
     */
    BigDecimal computeCompletionRate(Connection connection, long studentId) throws SQLException;

    /**
     * Inserts or updates progress for the student.
     */
    void upsert(Connection connection, long studentId, BigDecimal completionRate) throws SQLException;
}
