package model.dao;

import model.entity.RecitationLanguage;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface RecitationLanguageDao {
    /**
     * Creates the {@code recitation_language} table if it does not exist and
     * seeds a small set of default languages on first use. Safe to call
     * repeatedly.
     */
    void ensureSchema(Connection connection) throws SQLException;

    List<RecitationLanguage> listActive(Connection connection) throws SQLException;

    Optional<RecitationLanguage> findById(Connection connection, long languageId) throws SQLException;
}
