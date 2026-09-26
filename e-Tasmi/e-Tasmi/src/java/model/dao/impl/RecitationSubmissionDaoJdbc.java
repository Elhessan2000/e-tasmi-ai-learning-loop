package model.dao.impl;

import model.dao.RecitationSubmissionDao;
import model.entity.RecitationSubmission;
import model.entity.RecitationSubmissionStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecitationSubmissionDaoJdbc implements RecitationSubmissionDao {
    private static final String TABLE = "recitation_submissions";
    private static final String LANGUAGE_TABLE = "recitation_language";

    private static final String COL_RECITATION_ID = "recitation_id";
    private static final String COL_STUDENT_ID = "student_id";
    private static final String COL_FILE_PATH = "file_path";
    private static final String COL_TOPIC_TEXT = "topic_text";
    private static final String COL_LANGUAGE_ID = "language_id";
    private static final String COL_MODULE_LABEL = "module_label";
    private static final String COL_STATUS = "status";
    private static final String COL_DURATION_SECONDS = "duration_seconds";
    private static final String COL_SUBMITTED_AT = "submitted_at";

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS " + TABLE + " (" +
            COL_RECITATION_ID + " BIGINT UNSIGNED NOT NULL AUTO_INCREMENT," +
            COL_STUDENT_ID + " BIGINT UNSIGNED NOT NULL," +
            COL_FILE_PATH + " VARCHAR(500) DEFAULT NULL," +
            COL_TOPIC_TEXT + " VARCHAR(255) DEFAULT NULL," +
            COL_LANGUAGE_ID + " BIGINT UNSIGNED DEFAULT NULL," +
            COL_MODULE_LABEL + " VARCHAR(150) DEFAULT NULL," +
            COL_STATUS + " ENUM('PENDING','EVALUATED') NOT NULL DEFAULT 'PENDING'," +
            COL_DURATION_SECONDS + " INT DEFAULT NULL," +
            COL_SUBMITTED_AT + " TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
            "PRIMARY KEY (" + COL_RECITATION_ID + ")," +
            "KEY idx_recsub_student (" + COL_STUDENT_ID + ")," +
            "KEY idx_recsub_language (" + COL_LANGUAGE_ID + ")," +
            "CONSTRAINT fk_recsub_user FOREIGN KEY (" + COL_STUDENT_ID + ") REFERENCES `user` (user_id) ON UPDATE CASCADE ON DELETE CASCADE," +
            "CONSTRAINT fk_recsub_language FOREIGN KEY (" + COL_LANGUAGE_ID + ") REFERENCES " + LANGUAGE_TABLE + " (language_id) ON UPDATE CASCADE ON DELETE SET NULL" +
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";

    private static final String SELECT_COLUMNS =
            "s." + COL_RECITATION_ID + ", s." + COL_STUDENT_ID + ", s." + COL_FILE_PATH + ", s." + COL_TOPIC_TEXT +
            ", s." + COL_LANGUAGE_ID + ", s." + COL_MODULE_LABEL + ", s." + COL_STATUS + ", s." + COL_DURATION_SECONDS +
            ", s." + COL_SUBMITTED_AT + ", l.name AS language_name";

    @Override
    public void ensureSchema(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.executeUpdate(CREATE_TABLE_SQL);
        }
    }

    @Override
    public long insert(Connection connection, RecitationSubmission submission) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (" +
                COL_STUDENT_ID + "," + COL_FILE_PATH + "," + COL_TOPIC_TEXT + "," + COL_LANGUAGE_ID + "," +
                COL_MODULE_LABEL + "," + COL_STATUS + "," + COL_DURATION_SECONDS +
                ") VALUES (?,?,?,?,?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, submission.getStudentId());
            ps.setString(2, submission.getFilePath());
            ps.setString(3, submission.getTopicText());
            if (submission.getLanguageId() == null) {
                ps.setNull(4, java.sql.Types.BIGINT);
            } else {
                ps.setLong(4, submission.getLanguageId());
            }
            ps.setString(5, submission.getModuleLabel());
            ps.setString(6, submission.getStatus() == null
                    ? RecitationSubmissionStatus.PENDING.name()
                    : submission.getStatus().name());
            if (submission.getDurationSeconds() == null) {
                ps.setNull(7, java.sql.Types.INTEGER);
            } else {
                ps.setLong(7, submission.getDurationSeconds());
            }

            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Insert recitation submission affected " + updated + " rows");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for recitation submission insert");
                }
                return keys.getLong(1);
            }
        }
    }

    @Override
    public Optional<RecitationSubmission> findById(Connection connection, long recitationId) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS +
                " FROM " + TABLE + " s" +
                " LEFT JOIN " + LANGUAGE_TABLE + " l ON s." + COL_LANGUAGE_ID + " = l.language_id" +
                " WHERE s." + COL_RECITATION_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, recitationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    @Override
    public boolean updateFilePath(Connection connection, long recitationId, long studentId,
                                  String filePath, Long durationSeconds) throws SQLException {
        String sql = "UPDATE " + TABLE +
                " SET " + COL_FILE_PATH + " = ?, " + COL_DURATION_SECONDS + " = ?" +
                " WHERE " + COL_RECITATION_ID + " = ? AND " + COL_STUDENT_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, filePath);
            if (durationSeconds == null) {
                ps.setNull(2, java.sql.Types.INTEGER);
            } else {
                ps.setLong(2, durationSeconds);
            }
            ps.setLong(3, recitationId);
            ps.setLong(4, studentId);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public List<RecitationSubmission> listByStudentId(Connection connection, long studentId) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS +
                " FROM " + TABLE + " s" +
                " LEFT JOIN " + LANGUAGE_TABLE + " l ON s." + COL_LANGUAGE_ID + " = l.language_id" +
                " WHERE s." + COL_STUDENT_ID + " = ?" +
                " ORDER BY s." + COL_SUBMITTED_AT + " DESC, s." + COL_RECITATION_ID + " DESC";

        List<RecitationSubmission> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        }
        return results;
    }

    private RecitationSubmission map(ResultSet rs) throws SQLException {
        RecitationSubmission submission = new RecitationSubmission();
        submission.setRecitationId(rs.getLong(COL_RECITATION_ID));
        submission.setStudentId(rs.getLong(COL_STUDENT_ID));
        submission.setFilePath(rs.getString(COL_FILE_PATH));
        submission.setTopicText(rs.getString(COL_TOPIC_TEXT));

        long languageId = rs.getLong(COL_LANGUAGE_ID);
        if (!rs.wasNull()) {
            submission.setLanguageId(languageId);
        }
        submission.setLanguageName(rs.getString("language_name"));
        submission.setModuleLabel(rs.getString(COL_MODULE_LABEL));
        submission.setStatus(RecitationSubmissionStatus.fromString(rs.getString(COL_STATUS)));

        int duration = rs.getInt(COL_DURATION_SECONDS);
        if (!rs.wasNull()) {
            submission.setDurationSeconds((long) duration);
        }

        Timestamp ts = rs.getTimestamp(COL_SUBMITTED_AT);
        submission.setSubmittedAt(ts != null ? ts.toInstant() : Instant.now());
        return submission;
    }
}
