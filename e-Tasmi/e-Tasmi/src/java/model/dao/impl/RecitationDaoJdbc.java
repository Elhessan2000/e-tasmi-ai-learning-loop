package model.dao.impl;

import model.dao.RecitationDao;
import model.entity.PublishedRecitationRecord;
import model.entity.Recitation;
import model.entity.RecitationAnalysisJobState;

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
    private static final String COL_PARENT = "parent_recitation_id";
    private static final String COL_ATTEMPT = "attempt_number";
    private static final String COL_JOB_STATE = "analysis_job_state";
    private static final String COL_JOB_STARTED = "analysis_job_started_at";

    private static final String SELECT_COLUMNS =
            COL_RECITATION_ID + "," + COL_ENROLLMENT_ID + "," + COL_AUDIO_FILE_PATH + ","
                    + COL_SUBMISSION_DATE + "," + COL_PARENT + "," + COL_ATTEMPT + "," + COL_JOB_STATE
                    + "," + COL_JOB_STARTED;

    @Override
    public long insert(Connection connection, Recitation recitation) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (" + COL_ENROLLMENT_ID + "," + COL_AUDIO_FILE_PATH
                + "," + COL_PARENT + "," + COL_ATTEMPT + ") VALUES (?,?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, recitation.getEnrollmentId());
            ps.setString(2, recitation.getAudioFilePath());
            if (recitation.getParentRecitationId() == null) {
                ps.setNull(3, Types.BIGINT);
            } else {
                ps.setLong(3, recitation.getParentRecitationId());
            }
            ps.setInt(4, Math.max(1, recitation.getAttemptNumber()));

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
        String sql = "SELECT " + SELECT_COLUMNS +
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
        String sql = "SELECT " + SELECT_COLUMNS +
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
        String sql = "SELECT r." + COL_RECITATION_ID + ", r." + COL_ENROLLMENT_ID + ", r." + COL_AUDIO_FILE_PATH + ", r." + COL_SUBMISSION_DATE
                + ", r." + COL_PARENT + ", r." + COL_ATTEMPT + ", r." + COL_JOB_STATE + ", r." + COL_JOB_STARTED +
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

    private static final String PUBLISHED_FROM =
            " FROM " + TABLE + " r"
                    + " JOIN enrollment e ON e.enrollment_id = r.enrollment_id"
                    + " JOIN tasmi_session s ON s.session_id = e.session_id"
                    + " JOIN evaluation ev ON ev.recitation_id = r.recitation_id AND ev.published_at IS NOT NULL"
                    + " WHERE e.student_id = ?";

    @Override
    public List<PublishedRecitationRecord> listPublishedForStudent(Connection connection, long studentId, int limit)
            throws SQLException {
        String sql = "SELECT r.recitation_id, r.enrollment_id, r.parent_recitation_id, r.attempt_number,"
                + " r.submission_date, ev.score, ev.published_at,"
                + " s.title, s.surah_number, s.ayah_start, s.ayah_end, s.quran_portion,"
                + " (SELECT COUNT(*) FROM recitation_analysis ra"
                + "    JOIN recitation_finding f ON f.analysis_id = ra.analysis_id"
                + "   WHERE ra.analysis_id = ev.analysis_id AND ra.recitation_id = r.recitation_id"
                + "     AND f.instructor_status IN ('ACCEPTED','EDITED','INSTRUCTOR_ADDED')) AS focus_count"
                + PUBLISHED_FROM
                + " ORDER BY r.submission_date DESC, r.recitation_id DESC"
                + " LIMIT ?";
        List<PublishedRecitationRecord> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PublishedRecitationRecord row = new PublishedRecitationRecord();
                    row.setRecitationId(rs.getLong("recitation_id"));
                    row.setEnrollmentId(rs.getLong("enrollment_id"));
                    long parentId = rs.getLong("parent_recitation_id");
                    row.setParentRecitationId(rs.wasNull() ? null : parentId);
                    row.setAttemptNumber(Math.max(1, rs.getInt("attempt_number")));
                    Timestamp submitted = rs.getTimestamp("submission_date");
                    row.setSubmittedAt(submitted == null ? null : submitted.toInstant());
                    Timestamp published = rs.getTimestamp("published_at");
                    row.setPublishedAt(published == null ? null : published.toInstant());
                    row.setScore(rs.getInt("score"));
                    row.setSessionTitle(rs.getString("title"));
                    row.setSurahNumber(nullableInt(rs, "surah_number"));
                    row.setAyahStart(nullableInt(rs, "ayah_start"));
                    row.setAyahEnd(nullableInt(rs, "ayah_end"));
                    row.setQuranPortion(rs.getString("quran_portion"));
                    row.setFocusCount(rs.getInt("focus_count"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    @Override
    public int countPublishedForStudent(Connection connection, long studentId) throws SQLException {
        String sql = "SELECT COUNT(*)" + PUBLISHED_FROM;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    @Override
    public int maxAttemptNumber(Connection connection, long enrollmentId) throws SQLException {
        String sql = "SELECT COALESCE(MAX(" + COL_ATTEMPT + "), 0) FROM " + TABLE
                + " WHERE " + COL_ENROLLMENT_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, enrollmentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
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
        long parentId = rs.getLong(COL_PARENT);
        if (!rs.wasNull()) {
            r.setParentRecitationId(parentId);
        }
        r.setAttemptNumber(rs.getInt(COL_ATTEMPT));
        try {
            r.setAnalysisJobState(RecitationAnalysisJobState.fromDb(rs.getString(COL_JOB_STATE)));
            Timestamp jobStarted = rs.getTimestamp(COL_JOB_STARTED);
            if (jobStarted != null) {
                r.setAnalysisJobStartedAt(jobStarted.toInstant());
            }
        } catch (SQLException ignored) {
            r.setAnalysisJobState(RecitationAnalysisJobState.NONE);
        }
        return r;
    }

    @Override
    public RecitationAnalysisJobState findAnalysisJobState(Connection connection, long recitationId) throws SQLException {
        String sql = "SELECT " + COL_JOB_STATE + " FROM " + TABLE + " WHERE " + COL_RECITATION_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, recitationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return RecitationAnalysisJobState.NONE;
                }
                return RecitationAnalysisJobState.fromDb(rs.getString(1));
            }
        }
    }

    @Override
    public boolean tryClaimInitialAnalysisJob(Connection connection, long recitationId, Timestamp staleCutoff)
            throws SQLException {
        String sql = "UPDATE " + TABLE + " SET " + COL_JOB_STATE + " = 'IN_PROGRESS', " + COL_JOB_STARTED
                + " = CURRENT_TIMESTAMP WHERE " + COL_RECITATION_ID + " = ?"
                + " AND NOT EXISTS (SELECT 1 FROM recitation_analysis ra WHERE ra.recitation_id = " + TABLE + "."
                + COL_RECITATION_ID + ")"
                + " AND ("
                + COL_JOB_STATE + " = 'NONE'"
                + " OR " + COL_JOB_STATE + " = 'COMPLETED'"
                + " OR (" + COL_JOB_STATE + " = 'IN_PROGRESS' AND ("
                + COL_JOB_STARTED + " IS NULL OR " + COL_JOB_STARTED + " < ?)))";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, recitationId);
            ps.setTimestamp(2, staleCutoff);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean tryClaimRetryAnalysisJob(Connection connection, long recitationId, Timestamp staleCutoff)
            throws SQLException {
        String sql = "UPDATE " + TABLE + " SET " + COL_JOB_STATE + " = 'IN_PROGRESS', " + COL_JOB_STARTED
                + " = CURRENT_TIMESTAMP WHERE " + COL_RECITATION_ID + " = ?"
                + " AND NOT EXISTS (SELECT 1 FROM evaluation e WHERE e.recitation_id = " + TABLE + "." + COL_RECITATION_ID + ")"
                + " AND EXISTS ("
                + "   SELECT 1 FROM recitation_analysis ra"
                + "   WHERE ra.recitation_id = " + TABLE + "." + COL_RECITATION_ID
                + "     AND ra.analysis_id = (SELECT MAX(ra2.analysis_id) FROM recitation_analysis ra2"
                + "                           WHERE ra2.recitation_id = " + TABLE + "." + COL_RECITATION_ID + ")"
                + "     AND ra.status IN ('FAILED','CANNOT_EVALUATE','REFERENCE_UNAVAILABLE')"
                + " )"
                + " AND ("
                + COL_JOB_STATE + " = 'COMPLETED'"
                + " OR (" + COL_JOB_STATE + " = 'IN_PROGRESS' AND ("
                + COL_JOB_STARTED + " IS NULL OR " + COL_JOB_STARTED + " < ?)))";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, recitationId);
            ps.setTimestamp(2, staleCutoff);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public void markAnalysisJobCompleted(Connection connection, long recitationId, Instant leaseStartedAt)
            throws SQLException {
        String sql = "UPDATE " + TABLE + " SET " + COL_JOB_STATE + " = 'COMPLETED', " + COL_JOB_STARTED + " = NULL"
                + " WHERE " + COL_RECITATION_ID + " = ? AND " + COL_JOB_STATE + " = 'IN_PROGRESS'"
                + " AND " + COL_JOB_STARTED + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, recitationId);
            if (leaseStartedAt == null) {
                ps.setNull(2, Types.TIMESTAMP);
            } else {
                ps.setTimestamp(2, Timestamp.from(leaseStartedAt));
            }
            ps.executeUpdate();
        }
    }

    @Override
    public boolean abandonStaleInProgress(Connection connection, long recitationId, Timestamp staleCutoff)
            throws SQLException {
        String sql = "UPDATE " + TABLE + " SET " + COL_JOB_STATE + " = 'COMPLETED', " + COL_JOB_STARTED + " = NULL"
                + " WHERE " + COL_RECITATION_ID + " = ? AND " + COL_JOB_STATE + " = 'IN_PROGRESS'"
                + " AND (" + COL_JOB_STARTED + " IS NULL OR " + COL_JOB_STARTED + " < ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, recitationId);
            ps.setTimestamp(2, staleCutoff);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean healInProgressWhenAnalysisExists(long recitationId, Timestamp staleCutoff) throws SQLException {
        try (Connection connection = util.Db.getConnection()) {
            return healInProgressWhenAnalysisExists(connection, recitationId, staleCutoff);
        }
    }

    @Override
    public boolean healInProgressWhenAnalysisExists(Connection connection, long recitationId, Timestamp staleCutoff)
            throws SQLException {
        String sql = "UPDATE " + TABLE + " r SET r." + COL_JOB_STATE + " = 'COMPLETED', r." + COL_JOB_STARTED
                + " = NULL"
                + " WHERE r." + COL_RECITATION_ID + " = ? AND r." + COL_JOB_STATE + " = 'IN_PROGRESS'"
                + " AND r." + COL_JOB_STARTED + " IS NOT NULL AND r." + COL_JOB_STARTED + " < ?"
                + " AND EXISTS (SELECT 1 FROM recitation_analysis ra WHERE ra.recitation_id = r." + COL_RECITATION_ID
                + " AND ra.created_at >= r." + COL_JOB_STARTED + ")";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, recitationId);
            ps.setTimestamp(2, staleCutoff);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public Instant findAnalysisJobStartedAt(Connection connection, long recitationId) throws SQLException {
        String sql = "SELECT " + COL_JOB_STARTED + " FROM " + TABLE + " WHERE " + COL_RECITATION_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, recitationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Timestamp started = rs.getTimestamp(COL_JOB_STARTED);
                return started == null ? null : started.toInstant();
            }
        }
    }

    @Override
    public boolean isAnalysisJobActivelyInProgress(long recitationId, Instant staleCutoff) throws SQLException {
        try (Connection connection = util.Db.getConnection()) {
            return isAnalysisJobActivelyInProgress(connection, recitationId, staleCutoff);
        }
    }

    @Override
    public boolean isAnalysisJobActivelyInProgress(Connection connection, long recitationId, Instant staleCutoff)
            throws SQLException {
        String sql = "SELECT " + COL_JOB_STATE + ", " + COL_JOB_STARTED + " FROM " + TABLE + " WHERE "
                + COL_RECITATION_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, recitationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                if (!"IN_PROGRESS".equalsIgnoreCase(rs.getString(COL_JOB_STATE))) {
                    return false;
                }
                Timestamp started = rs.getTimestamp(COL_JOB_STARTED);
                if (started == null) {
                    return false;
                }
                return !started.toInstant().isBefore(staleCutoff);
            }
        }
    }

    @Override
    public List<Long> listRecitationIdsByJobState(String jobState) throws SQLException {
        String sql = "SELECT " + COL_RECITATION_ID + " FROM " + TABLE + " WHERE " + COL_JOB_STATE + " = ?";
        List<Long> ids = new ArrayList<>();
        try (Connection connection = util.Db.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, jobState);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        return ids;
    }

    @Override
    public List<Long> listCompletedJobIdsWithoutAnalysis() throws SQLException {
        String sql = "SELECT r." + COL_RECITATION_ID + " FROM " + TABLE + " r"
                + " WHERE r." + COL_JOB_STATE + " = 'COMPLETED'"
                + " AND NOT EXISTS (SELECT 1 FROM recitation_analysis ra WHERE ra.recitation_id = r."
                + COL_RECITATION_ID + ")";
        List<Long> ids = new ArrayList<>();
        try (Connection connection = util.Db.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
        }
        return ids;
    }
}
