package model.dao;

import model.entity.InstructorPaymentSettings;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public interface InstructorPaymentSettingsDao {
    Optional<InstructorPaymentSettings> findByInstructorId(Connection connection, long instructorId) throws SQLException;

    /** Insert or update the single settings row for an instructor. */
    void upsert(Connection connection, InstructorPaymentSettings settings) throws SQLException;
}
