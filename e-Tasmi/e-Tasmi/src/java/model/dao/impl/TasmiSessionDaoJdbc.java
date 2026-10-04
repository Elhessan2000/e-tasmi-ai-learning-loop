
package model.dao.impl;

import model.dao.TasmiSessionDao;
import model.entity.SessionMode;
import model.entity.SessionRecordingStatus;
import model.entity.StudentLevel;
import model.entity.TasmiSession;
import model.entity.TasmiSessionStatus;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class TasmiSessionDaoJdbc implements TasmiSessionDao {
    private static final String TABLE = "tasmi_session";

    private static final String COL_SESSION_ID = "session_id";
    private static final String COL_INSTRUCTOR_ID = "instructor_id";
    private static final String COL_TITLE = "title";
    private static final String COL_SESSION_DATE = "session_date";
    private static final String COL_SESSION_TIME = "session_time";
    private static final String COL_DURATION_MINUTES = "duration_minutes";
    private static final String COL_QURAN_PORTION = "quran_portion";
    private static final String COL_SURAH_NUMBER = "surah_number";
    private static final String COL_AYAH_START = "ayah_start";
    private static final String COL_AYAH_END = "ayah_end";
    private static final String COL_MODE = "mode";
    private static final String COL_FEE = "fee";
    private static final String COL_DESCRIPTION = "description";
    private static final String COL_LEVEL = "level";
    private static final String COL_CAPACITY = "capacity";
    private static final String COL_STATUS = "status";
    private static final String COL_LIVE_PROVIDER = "live_provider";
    private static final String COL_MEETING_LINK = "meeting_link";
    private static final String COL_MEETING_PASSWORD = "meeting_password";
    private static final String COL_PASSWORD_VISIBLE = "is_password_visible";
    private static final String COL_LIVE_STARTED_AT = "live_started_at";
    private static final String COL_LIVE_ENDED_AT = "live_ended_at";
    private static final String COL_RECORDING_STATUS = "recording_status";
    private static final String COL_RECORDING_URL = "recording_url";
    private static final String COL_RECORDING_SYNCED_AT = "recording_synced_at";
    private static final String COL_ZOOM_MEETING_ID = "zoom_meeting_id";
    private static final String COL_ZOOM_START_URL = "zoom_start_url";
    private static final String COL_BANNER_IMAGE_URL = "banner_image_url";

    /**
     * Added by the Evaluation Review workflow. NULL = active evaluations,
     * non-NULL = the instructor has marked the session as reviewed.
     * Treated as optional in the schema so older databases that haven't run
     * {@code module_evaluation_reviewed_patch.sql} yet still load.
     */
    private static final String COL_EVALUATION_REVIEWED_AT = "evaluation_reviewed_at";

    @Override
    public boolean updateStatusByIdAndInstructorId(Connection connection, long sessionId, long instructorId, TasmiSessionStatus newStatus) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET " + COL_STATUS + " = ? WHERE " + COL_SESSION_ID + " = ? AND " + COL_INSTRUCTOR_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newStatus == null ? null : newStatus.name());
            ps.setLong(2, sessionId);
            ps.setLong(3, instructorId);
            return ps.executeUpdate() == 1;
        }
    }


    @Override
    public long insert(Connection connection, TasmiSession session) throws SQLException {
        String titleColumn = resolveTitleColumn(connection);
        String sql = "INSERT INTO " + TABLE + " (" +
            COL_INSTRUCTOR_ID + "," + titleColumn + "," + COL_DESCRIPTION + "," + COL_LEVEL + "," + COL_SESSION_DATE + "," + COL_SESSION_TIME + "," +
                COL_DURATION_MINUTES + "," + COL_QURAN_PORTION + "," +
                COL_SURAH_NUMBER + "," + COL_AYAH_START + "," + COL_AYAH_END + "," +
                COL_MODE + "," + COL_FEE + "," + COL_CAPACITY + "," +
                COL_LIVE_PROVIDER + "," + COL_MEETING_LINK + "," + COL_MEETING_PASSWORD + "," + COL_PASSWORD_VISIBLE + "," +
                COL_LIVE_STARTED_AT + "," + COL_LIVE_ENDED_AT + "," + COL_RECORDING_STATUS + "," + COL_RECORDING_URL + "," + COL_RECORDING_SYNCED_AT + "," +
                COL_ZOOM_MEETING_ID + "," + COL_ZOOM_START_URL + "," +
                COL_BANNER_IMAGE_URL + "," +
                COL_STATUS +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, session.getInstructorId());
            ps.setString(2, session.getTitle());
            ps.setString(3, session.getDescription());
            ps.setString(4, session.getLevel() == null ? StudentLevel.PRIMARY_SCHOOL.name() : session.getLevel().name());
            ps.setDate(5, Date.valueOf(session.getSessionDate()));
            ps.setTime(6, Time.valueOf(session.getSessionTime()));
            ps.setInt(7, session.getDurationMinutes() == null || session.getDurationMinutes() <= 0 ? 60 : session.getDurationMinutes());
            ps.setString(8, session.getQuranPortion());
            setNullableInteger(ps, 9, session.getSurahNumber());
            setNullableInteger(ps, 10, session.getAyahStart());
            setNullableInteger(ps, 11, session.getAyahEnd());
            ps.setString(12, session.getMode() == null ? null : session.getMode().name());
            ps.setBigDecimal(13, session.getFee() == null ? BigDecimal.ZERO : session.getFee());
            ps.setInt(14, session.getCapacity());
            ps.setString(15, session.getLiveProvider());
            ps.setString(16, session.getMeetingLink());
            ps.setString(17, session.getMeetingPassword());
            ps.setBoolean(18, session.isPasswordVisible());
            setTimestamp(ps, 19, session.getLiveStartedAt());
            setTimestamp(ps, 20, session.getLiveEndedAt());
            ps.setString(21, session.getRecordingStatus() == null ? null : session.getRecordingStatus().name());
            ps.setString(22, session.getRecordingUrl());
            setTimestamp(ps, 23, session.getRecordingSyncedAt());
            setNullableLong(ps, 24, session.getZoomMeetingId());
            ps.setString(25, session.getZoomStartUrl());
            ps.setString(26, session.getBannerImageUrl());
            ps.setString(27, session.getStatus() == null ? null : session.getStatus().name());

            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Insert tasmi_session affected " + updated + " rows");
            }

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for tasmi_session insert");
                }
                return keys.getLong(1);
            }
        }
    }

    @Override
    public Optional<TasmiSession> findById(Connection connection, long sessionId) throws SQLException {
        String titleColumn = resolveTitleColumn(connection);
        String sql = "SELECT " + buildSelectColumns(connection, titleColumn) +
                " FROM " + TABLE + " WHERE " + COL_SESSION_ID + " = ?";

        if (!COL_TITLE.equalsIgnoreCase(titleColumn)) {
            sql = "SELECT " + buildSelectColumns(connection, titleColumn) +
                    " FROM " + TABLE + " WHERE " + COL_SESSION_ID + " = ?";
        }

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    @Override
    public List<TasmiSession> listByInstructorId(Connection connection, long instructorId) throws SQLException {
        String titleColumn = resolveTitleColumn(connection);
        String sql = "SELECT " + buildSelectColumns(connection, titleColumn) +
                " FROM " + TABLE + " WHERE " + COL_INSTRUCTOR_ID + " = ? ORDER BY " + COL_SESSION_DATE + " DESC, " + COL_SESSION_TIME + " DESC";

        if (!COL_TITLE.equalsIgnoreCase(titleColumn)) {
            sql = "SELECT " + buildSelectColumns(connection, titleColumn) +
                " FROM " + TABLE + " WHERE " + COL_INSTRUCTOR_ID + " = ? ORDER BY " + COL_SESSION_DATE + " DESC, " + COL_SESSION_TIME + " DESC";
        }

        List<TasmiSession> results = new ArrayList<>();
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

    @Override
    public List<TasmiSession> listByStatus(Connection connection, TasmiSessionStatus status) throws SQLException {
        String titleColumn = resolveTitleColumn(connection);
        String sql = "SELECT " + buildSelectColumns(connection, titleColumn) +
                " FROM " + TABLE + " WHERE " + COL_STATUS + " = ? ORDER BY " + COL_SESSION_DATE + " ASC, " + COL_SESSION_TIME + " ASC";

        if (!COL_TITLE.equalsIgnoreCase(titleColumn)) {
            sql = "SELECT " + buildSelectColumns(connection, titleColumn) +
                " FROM " + TABLE + " WHERE " + COL_STATUS + " = ? ORDER BY " + COL_SESSION_DATE + " ASC, " + COL_SESSION_TIME + " ASC";
        }

        List<TasmiSession> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status == null ? null : status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        }
        return results;
    }

    @Override
    public boolean updateByIdAndInstructorId(Connection connection, TasmiSession session) throws SQLException {
        String titleColumn = resolveTitleColumn(connection);
        String sql = "UPDATE " + TABLE + " SET " +
            titleColumn + " = ?," +
            COL_DESCRIPTION + " = ?," +
            COL_LEVEL + " = ?," +
            COL_SESSION_DATE + " = ?," +
            COL_SESSION_TIME + " = ?," +
            COL_DURATION_MINUTES + " = ?," +
            COL_QURAN_PORTION + " = ?," +
            COL_SURAH_NUMBER + " = ?," +
            COL_AYAH_START + " = ?," +
            COL_AYAH_END + " = ?," +
            COL_MODE + " = ?," +
            COL_FEE + " = ?," +
            COL_CAPACITY + " = ?," +
            COL_LIVE_PROVIDER + " = ?," +
            COL_MEETING_LINK + " = ?," +
            COL_MEETING_PASSWORD + " = ?," +
            COL_PASSWORD_VISIBLE + " = ?," +
            COL_LIVE_STARTED_AT + " = ?," +
            COL_LIVE_ENDED_AT + " = ?," +
            COL_RECORDING_STATUS + " = ?," +
            COL_RECORDING_URL + " = ?," +
            COL_RECORDING_SYNCED_AT + " = ?," +
            COL_ZOOM_MEETING_ID + " = ?," +
            COL_ZOOM_START_URL + " = ?," +
            COL_BANNER_IMAGE_URL + " = ?" +
            " WHERE " + COL_SESSION_ID + " = ? AND " + COL_INSTRUCTOR_ID + " = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, session.getTitle());
            ps.setString(2, session.getDescription());
            ps.setString(3, session.getLevel() == null ? StudentLevel.PRIMARY_SCHOOL.name() : session.getLevel().name());
            ps.setDate(4, Date.valueOf(session.getSessionDate()));
            ps.setTime(5, Time.valueOf(session.getSessionTime()));
            ps.setInt(6, session.getDurationMinutes() == null || session.getDurationMinutes() <= 0 ? 60 : session.getDurationMinutes());
            ps.setString(7, session.getQuranPortion());
            setNullableInteger(ps, 8, session.getSurahNumber());
            setNullableInteger(ps, 9, session.getAyahStart());
            setNullableInteger(ps, 10, session.getAyahEnd());
            ps.setString(11, session.getMode() == null ? null : session.getMode().name());
            ps.setBigDecimal(12, session.getFee() == null ? BigDecimal.ZERO : session.getFee());
            ps.setInt(13, session.getCapacity());
            ps.setString(14, session.getLiveProvider());
            ps.setString(15, session.getMeetingLink());
            ps.setString(16, session.getMeetingPassword());
            ps.setBoolean(17, session.isPasswordVisible());
            setTimestamp(ps, 18, session.getLiveStartedAt());
            setTimestamp(ps, 19, session.getLiveEndedAt());
            ps.setString(20, session.getRecordingStatus() == null ? null : session.getRecordingStatus().name());
            ps.setString(21, session.getRecordingUrl());
            setTimestamp(ps, 22, session.getRecordingSyncedAt());
            setNullableLong(ps, 23, session.getZoomMeetingId());
            ps.setString(24, session.getZoomStartUrl());
            ps.setString(25, session.getBannerImageUrl());
            ps.setLong(26, session.getSessionId());
            ps.setLong(27, session.getInstructorId());
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean deleteByIdAndInstructorId(Connection connection, long sessionId, long instructorId) throws SQLException {
        String sql = "DELETE FROM " + TABLE + " WHERE " + COL_SESSION_ID + " = ? AND " + COL_INSTRUCTOR_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            ps.setLong(2, instructorId);
            return ps.executeUpdate() == 1;
        }
    }


    private TasmiSession map(ResultSet rs) throws SQLException {
        TasmiSession s = new TasmiSession();
        s.setSessionId(rs.getLong(COL_SESSION_ID));
        s.setInstructorId(rs.getLong(COL_INSTRUCTOR_ID));
        s.setTitle(rs.getString(COL_TITLE));
        s.setDescription(rs.getString(COL_DESCRIPTION));
        s.setLevel(StudentLevel.fromString(safeGetString(rs, COL_LEVEL)));

        Date d = rs.getDate(COL_SESSION_DATE);
        if (d != null) {
            s.setSessionDate(d.toLocalDate());
        }

        Time t = rs.getTime(COL_SESSION_TIME);
        if (t != null) {
            s.setSessionTime(t.toLocalTime());
        }

        int durationMinutes = rs.getInt(COL_DURATION_MINUTES);
        if (!rs.wasNull()) {
            s.setDurationMinutes(durationMinutes);
        }
        s.setQuranPortion(safeGetString(rs, COL_QURAN_PORTION));
        s.setSurahNumber(safeGetNullableInt(rs, COL_SURAH_NUMBER));
        s.setAyahStart(safeGetNullableInt(rs, COL_AYAH_START));
        s.setAyahEnd(safeGetNullableInt(rs, COL_AYAH_END));
        s.setMode(SessionMode.fromString(rs.getString(COL_MODE)));
        s.setFee(rs.getBigDecimal(COL_FEE));
        s.setCapacity(rs.getInt(COL_CAPACITY));
        s.setStatus(TasmiSessionStatus.fromString(rs.getString(COL_STATUS)));
        s.setLiveProvider(safeGetString(rs, COL_LIVE_PROVIDER));
        s.setMeetingLink(safeGetString(rs, COL_MEETING_LINK));
        s.setMeetingPassword(safeGetString(rs, COL_MEETING_PASSWORD));
        s.setPasswordVisible(safeGetBoolean(rs, COL_PASSWORD_VISIBLE));
        s.setLiveStartedAt(safeGetInstant(rs, COL_LIVE_STARTED_AT));
        s.setLiveEndedAt(safeGetInstant(rs, COL_LIVE_ENDED_AT));
        s.setRecordingStatus(SessionRecordingStatus.fromString(safeGetString(rs, COL_RECORDING_STATUS)));
        s.setRecordingUrl(safeGetString(rs, COL_RECORDING_URL));
        s.setRecordingSyncedAt(safeGetInstant(rs, COL_RECORDING_SYNCED_AT));
        s.setZoomMeetingId(safeGetNullableLong(rs, COL_ZOOM_MEETING_ID));
        s.setZoomStartUrl(safeGetString(rs, COL_ZOOM_START_URL));
        s.setBannerImageUrl(safeGetString(rs, COL_BANNER_IMAGE_URL));
        // Returns null when the column isn't present (pre-patch DB) or is NULL.
        s.setEvaluationReviewedAt(safeGetInstant(rs, COL_EVALUATION_REVIEWED_AT));

        return s;
    }

    private String safeGetString(ResultSet rs, String col) {
        try {
            return rs.getString(col);
        } catch (SQLException ignored) {
            return null;
        }
    }

    private boolean safeGetBoolean(ResultSet rs, String col) {
        try {
            return rs.getBoolean(col);
        } catch (SQLException ignored) {
            return false;
        }
    }

    private Instant safeGetInstant(ResultSet rs, String col) {
        try {
            Timestamp ts = rs.getTimestamp(col);
            return ts == null ? null : ts.toInstant();
        } catch (SQLException ignored) {
            return null;
        }
    }

    private Integer safeGetNullableInt(ResultSet rs, String col) {
        try {
            int val = rs.getInt(col);
            return rs.wasNull() ? null : val;
        } catch (SQLException ignored) {
            return null;
        }
    }

    private void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private Long safeGetNullableLong(ResultSet rs, String col) {
        try {
            long val = rs.getLong(col);
            return rs.wasNull() ? null : val;
        } catch (SQLException ignored) {
            return null;
        }
    }

    private void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private void setTimestamp(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) {
            ps.setTimestamp(index, null);
            return;
        }
        ps.setTimestamp(index, Timestamp.from(value));
    }

    private String buildSelectColumns(Connection connection, String titleColumn) throws SQLException {
        // Base columns
        String titleSel = COL_TITLE.equalsIgnoreCase(titleColumn) ? COL_TITLE : (titleColumn + " AS " + COL_TITLE);
        StringBuilder sb = new StringBuilder();
        sb.append(COL_SESSION_ID).append(',')
            .append(COL_INSTRUCTOR_ID).append(',')
            .append(titleSel).append(',')
            .append(COL_DESCRIPTION).append(',')
            .append(COL_LEVEL).append(',')
            .append(COL_SESSION_DATE).append(',')
            .append(COL_SESSION_TIME).append(',')
            .append(COL_DURATION_MINUTES).append(',')
            .append(COL_QURAN_PORTION).append(',');
        if (hasColumn(connection, TABLE, COL_SURAH_NUMBER)) {
            sb.append(COL_SURAH_NUMBER).append(',')
                .append(COL_AYAH_START).append(',')
                .append(COL_AYAH_END).append(',');
        }
        sb.append(COL_MODE).append(',')
            .append(COL_FEE).append(',')
            .append(COL_CAPACITY).append(',')
            .append(COL_STATUS).append(',')
            .append(COL_LIVE_PROVIDER).append(',')
            .append(COL_MEETING_LINK).append(',')
            .append(COL_MEETING_PASSWORD).append(',')
            .append(COL_PASSWORD_VISIBLE).append(',')
            .append(COL_LIVE_STARTED_AT).append(',')
            .append(COL_LIVE_ENDED_AT).append(',')
            .append(COL_RECORDING_STATUS).append(',')
            .append(COL_RECORDING_URL).append(',')
            .append(COL_RECORDING_SYNCED_AT).append(',')
            .append(COL_ZOOM_MEETING_ID).append(',')
            .append(COL_ZOOM_START_URL).append(',')
            .append(COL_BANNER_IMAGE_URL);

        // Only include evaluation_reviewed_at if the migration has been applied;
        // pre-patch databases will simply load with a null evaluationReviewedAt.
        if (hasColumn(connection, TABLE, COL_EVALUATION_REVIEWED_AT)) {
            sb.append(',').append(COL_EVALUATION_REVIEWED_AT);
        }

        return sb.toString();
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        for (String c : getTableColumns(connection, table)) {
            if (c != null && c.equalsIgnoreCase(column)) {
                return true;
            }
        }
        return false;
    }

    private String resolveOptionalExistingColumn(List<String> existingLower, List<String> existingColumns, String... candidates) {
        if (candidates == null || candidates.length == 0) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && existingLower.contains(candidate.toLowerCase())) {
                for (String existing : existingColumns) {
                    if (existing != null && existing.equalsIgnoreCase(candidate)) {
                        return existing;
                    }
                }
                return candidate;
            }
        }
        return null;
    }

    private String resolveTitleColumn(Connection connection) throws SQLException {
        return resolveExistingColumn(connection, TABLE, "title", "session_title", "session_name", "name", "topic");
    }

    private String resolveExistingColumn(Connection connection, String table, String... candidates) throws SQLException {
        if (candidates == null || candidates.length == 0) {
            throw new SQLException("No candidate columns provided for table " + table);
        }

        List<String> existingColumns = getTableColumns(connection, table);
        List<String> existingLower = new ArrayList<>();
        for (String c : existingColumns) {
            existingLower.add(c.toLowerCase());
        }

        for (String candidate : candidates) {
            if (candidate != null && existingLower.contains(candidate.toLowerCase())) {
                return candidate;
            }
        }

        // Heuristic fallback: handle camelCase or unexpected naming.
        // Prefer anything containing "title" first, then "name".
        for (String existing : existingColumns) {
            String lower = existing.toLowerCase();
            if (lower.contains("title")) {
                return existing;
            }
        }
        for (String existing : existingColumns) {
            String lower = existing.toLowerCase();
            if (lower.contains("name")) {
                return existing;
            }
        }

        throw new SQLException(
                "Database schema mismatch: none of these columns exist in " + table + ": " + String.join(", ", candidates) +
                        ". Existing columns: " + String.join(", ", existingColumns)
        );
    }

    private List<String> getTableColumns(Connection connection, String table) throws SQLException {
        if (connection == null) {
            throw new SQLException("No database connection available to inspect table " + table);
        }

        // 1) Try DatabaseMetaData first.
        List<String> cols = new ArrayList<>();
        try {
            DatabaseMetaData meta = connection.getMetaData();
            List<String[]> attempts = Arrays.asList(
                    new String[]{connection.getCatalog(), null, table},
                    new String[]{null, null, table},
                    new String[]{connection.getCatalog(), null, table.toUpperCase()},
                    new String[]{null, null, table.toUpperCase()},
                    new String[]{connection.getCatalog(), null, table.toLowerCase()},
                    new String[]{null, null, table.toLowerCase()}
            );

            for (String[] a : attempts) {
                cols.clear();
                try (ResultSet rs = meta.getColumns(a[0], a[1], a[2], null)) {
                    while (rs.next()) {
                        String col = rs.getString("COLUMN_NAME");
                        if (col != null && !col.isBlank()) {
                            cols.add(col);
                        }
                    }
                }
                if (!cols.isEmpty()) {
                    return cols;
                }
            }
        } catch (SQLException ignored) {
            // fallback below
        }

        // 2) Fallback: query ResultSetMetaData (works even when meta.getColumns is unreliable).
        String sql = "SELECT * FROM " + table + " WHERE 1=0";
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                String col = md.getColumnLabel(i);
                if (col == null || col.isBlank()) {
                    col = md.getColumnName(i);
                }
                if (col != null && !col.isBlank()) {
                    cols.add(col);
                }
            }
        }

        if (cols.isEmpty()) {
            throw new SQLException("Could not determine columns for table " + table);
        }
        return cols;
    }

    @Override
    public boolean updateLiveDetailsAndStatusByIdAndInstructorId(Connection connection,
                                                                 long sessionId,
                                                                 long instructorId,
                                                                 TasmiSession session) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET " +
                COL_STATUS + " = ?, " +
                COL_LIVE_PROVIDER + " = ?, " +
                COL_MEETING_LINK + " = ?, " +
                COL_MEETING_PASSWORD + " = ?, " +
                COL_PASSWORD_VISIBLE + " = ?, " +
                COL_LIVE_STARTED_AT + " = ?, " +
                COL_LIVE_ENDED_AT + " = ?, " +
                COL_RECORDING_STATUS + " = ?, " +
                COL_RECORDING_URL + " = ?, " +
                COL_RECORDING_SYNCED_AT + " = ?, " +
                COL_ZOOM_MEETING_ID + " = ?, " +
                COL_ZOOM_START_URL + " = ? " +
                "WHERE " + COL_SESSION_ID + " = ? AND " + COL_INSTRUCTOR_ID + " = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, session.getStatus() == null ? null : session.getStatus().name());
            ps.setString(2, session.getLiveProvider());
            ps.setString(3, session.getMeetingLink());
            ps.setString(4, session.getMeetingPassword());
            ps.setBoolean(5, session.isPasswordVisible());
            setTimestamp(ps, 6, session.getLiveStartedAt());
            setTimestamp(ps, 7, session.getLiveEndedAt());
            ps.setString(8, session.getRecordingStatus() == null ? null : session.getRecordingStatus().name());
            ps.setString(9, session.getRecordingUrl());
            setTimestamp(ps, 10, session.getRecordingSyncedAt());
            setNullableLong(ps, 11, session.getZoomMeetingId());
            ps.setString(12, session.getZoomStartUrl());
            ps.setLong(13, sessionId);
            ps.setLong(14, instructorId);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean markEvaluationReviewed(Connection connection,
                                          long sessionId,
                                          long instructorId,
                                          Instant reviewedAt) throws SQLException {
        String sql = "UPDATE " + TABLE +
                " SET " + COL_EVALUATION_REVIEWED_AT + " = ?" +
                " WHERE " + COL_SESSION_ID + " = ? AND " + COL_INSTRUCTOR_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            setTimestamp(ps, 1, reviewedAt == null ? Instant.now() : reviewedAt);
            ps.setLong(2, sessionId);
            ps.setLong(3, instructorId);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean clearEvaluationReviewed(Connection connection,
                                           long sessionId,
                                           long instructorId) throws SQLException {
        String sql = "UPDATE " + TABLE +
                " SET " + COL_EVALUATION_REVIEWED_AT + " = NULL" +
                " WHERE " + COL_SESSION_ID + " = ? AND " + COL_INSTRUCTOR_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            ps.setLong(2, instructorId);
            return ps.executeUpdate() == 1;
        }
    }
}
