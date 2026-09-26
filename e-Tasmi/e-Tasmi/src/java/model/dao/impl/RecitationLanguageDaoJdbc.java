package model.dao.impl;

import model.dao.RecitationLanguageDao;
import model.entity.RecitationLanguage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecitationLanguageDaoJdbc implements RecitationLanguageDao {
    private static final String TABLE = "recitation_language";

    private static final String COL_LANGUAGE_ID = "language_id";
    private static final String COL_NAME = "name";
    private static final String COL_CODE = "code";
    private static final String COL_IS_ACTIVE = "is_active";
    private static final String COL_SORT_ORDER = "sort_order";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
            COL_LANGUAGE_ID + " BIGINT UNSIGNED NOT NULL AUTO_INCREMENT," +
            COL_NAME + " VARCHAR(80) NOT NULL," +
            COL_CODE + " VARCHAR(12) DEFAULT NULL," +
            COL_IS_ACTIVE + " TINYINT(1) NOT NULL DEFAULT 1," +
            COL_SORT_ORDER + " INT NOT NULL DEFAULT 0," +
            "PRIMARY KEY (" + COL_LANGUAGE_ID + ")," +
            "UNIQUE KEY uq_recitation_language_name (" + COL_NAME + ")" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

    /** Default languages seeded once when the table is first created/empty. */
    private static final String[][] DEFAULT_LANGUAGES = {
            {"Arabic", "ar", "1"},
            {"English", "en", "2"},
            {"Malay", "ms", "3"},
            {"Urdu", "ur", "4"},
            {"Turkish", "tr", "5"},
            {"Indonesian", "id", "6"}
    };

    @Override
    public void ensureSchema(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate(CREATE_TABLE_SQL);
        }
        seedDefaultsIfEmpty(connection);
    }

    private void seedDefaultsIfEmpty(Connection connection) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM " + TABLE;
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(countSql)) {
            if (rs.next() && rs.getLong(1) > 0) {
                return;
            }
        }

        String insertSql = "INSERT INTO " + TABLE + " (" + COL_NAME + "," + COL_CODE + "," + COL_SORT_ORDER + ") VALUES (?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(insertSql)) {
            for (String[] row : DEFAULT_LANGUAGES) {
                ps.setString(1, row[0]);
                ps.setString(2, row[1]);
                ps.setInt(3, Integer.parseInt(row[2]));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public List<RecitationLanguage> listActive(Connection connection) throws SQLException {
        String sql = "SELECT " + COL_LANGUAGE_ID + "," + COL_NAME + "," + COL_CODE + "," + COL_IS_ACTIVE + "," + COL_SORT_ORDER +
                " FROM " + TABLE +
                " WHERE " + COL_IS_ACTIVE + " = 1" +
                " ORDER BY " + COL_SORT_ORDER + " ASC, " + COL_NAME + " ASC";

        List<RecitationLanguage> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(map(rs));
            }
        }
        return results;
    }

    @Override
    public Optional<RecitationLanguage> findById(Connection connection, long languageId) throws SQLException {
        String sql = "SELECT " + COL_LANGUAGE_ID + "," + COL_NAME + "," + COL_CODE + "," + COL_IS_ACTIVE + "," + COL_SORT_ORDER +
                " FROM " + TABLE + " WHERE " + COL_LANGUAGE_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, languageId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    private RecitationLanguage map(ResultSet rs) throws SQLException {
        RecitationLanguage language = new RecitationLanguage();
        language.setLanguageId(rs.getLong(COL_LANGUAGE_ID));
        language.setName(rs.getString(COL_NAME));
        language.setCode(rs.getString(COL_CODE));
        language.setActive(rs.getBoolean(COL_IS_ACTIVE));
        language.setSortOrder(rs.getInt(COL_SORT_ORDER));
        return language;
    }
}
