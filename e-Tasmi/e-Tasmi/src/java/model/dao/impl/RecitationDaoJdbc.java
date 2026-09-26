package model.dao.impl;

import model.dao.RecitationDao;
import model.entity.Recitation;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecitationDaoJdbc implements RecitationDao {
    private static final String TABLE = "recitation";

    private static final String COL_RECITATION_ID = "recitation_id";
    private static final String COL_ENROLLMENT_ID = "enrollment_id";
    private static final String COL_AUDIO_FILE_PATH = "audio_file_path";
    private static final String COL_SUBMISSION_DATE = "submission_date";

    @Override
    public long insert(Connection connection, Recitation recitation) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (" + COL_ENROLLMENT_ID + "," + COL_AUDIO_FILE_PATH + ") VALUES (?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, recitation.getEnrollmentId());
            ps.setString(2, recitation.getAudioFilePath());

            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Insert recitation affected " + updated + " rows");
            }

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for recitation insert");
                }
                return keys.getLong(1);
            }
        }
    }

    @Override
    public Optional<Recitation> findById(Connection connection, long recitationId) throws SQLException {
        String sql = "SELECT " + COL_RECITATION_ID + "," + COL_ENROLLMENT_ID + "," + COL_AUDIO_FILE_PATH + "," + COL_SUBMISSION_DATE +
                " FROM " + TABLE + " WHERE " + COL_RECITATION_ID + " = ?";

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
    public List<Recitation> listByEnrollmentId(Connection connection, long enrollmentId) throws SQLException {
        String sql = "SELECT " + COL_RECITATION_ID + "," + COL_ENROLLMENT_ID + "," + COL_AUDIO_FILE_PATH + "," + COL_SUBMISSION_DATE +
                " FROM " + TABLE + " WHERE " + COL_ENROLLMENT_ID + " = ? ORDER BY " + COL_SUBMISSION_DATE + " DESC";

        List<Recitation> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, enrollmentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        }
        return results;
    }

    @Override
    public List<Recitation> listForInstructor(Connection connection, long instructorId) throws SQLException {
        String sql = "SELECT r." + COL_RECITATION_ID + ", r." + COL_ENROLLMENT_ID + ", r." + COL_AUDIO_FILE_PATH + ", r." + COL_SUBMISSION_DATE +
                " FROM " + TABLE + " r" +
                " JOIN enrollment e ON r.enrollment_id = e.enrollment_id" +
                " JOIN tasmi_session s ON e.session_id = s.session_id" +
                " WHERE s.instructor_id = ?" +
                " ORDER BY r." + COL_SUBMISSION_DATE + " DESC";

        List<Recitation> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, instructorId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        }
        return results;
    }

    private Recitation map(ResultSet rs) throws SQLException {
        Recitation r = new Recitation();
        r.setRecitationId(rs.getLong(COL_RECITATION_ID));
        r.setEnrollmentId(rs.getLong(COL_ENROLLMENT_ID));
        r.setAudioFilePath(rs.getString(COL_AUDIO_FILE_PATH));

        Timestamp ts = rs.getTimestamp(COL_SUBMISSION_DATE);
        if (ts != null) {
            r.setSubmissionDate(ts.toInstant());
        } else {
            r.setSubmissionDate(Instant.now());
        }
        return r;
    }
}
