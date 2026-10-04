package model.dao.impl;

import model.dao.RecitationFindingDao;
import model.entity.RecitationFindingRecord;
import model.service.analysis.FindingAiStatus;
import model.service.analysis.FindingInstructorStatus;
import model.service.analysis.FindingType;
import model.service.analysis.RecitationFinding;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RecitationFindingDaoJdbc implements RecitationFindingDao {
    private static final String TABLE = "recitation_finding";

    private static final String COLUMNS =
            "finding_id, analysis_id, recitation_id, finding_type, verse_key, word_position, "
                    + "expected_text, heard_text, explanation, ai_status, ai_confidence, instructor_status, "
                    + "instructor_expected_text, instructor_heard_text, instructor_explanation, instructor_note, "
                    + "decided_by_instructor_id, decided_at, created_at";

    @Override
    public void insertAll(Connection connection, long analysisId, long recitationId, List<RecitationFinding> findings)
            throws SQLException {
        if (findings == null || findings.isEmpty()) {
            return;
        }
        String sql = "INSERT INTO " + TABLE + " ("
                + "analysis_id, recitation_id, finding_type, verse_key, word_position, "
                + "expected_text, heard_text, explanation, ai_status, instructor_status"
                + ") VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (RecitationFinding finding : findings) {
                if (finding == null || finding.getType() == null) {
                    continue;
                }
                ps.setLong(1, analysisId);
                ps.setLong(2, recitationId);
                ps.setString(3, finding.getType().name());
                setString(ps, 4, limit(finding.getVerseKey(), 12));
                if (finding.getWordPosition() == null) {
                    ps.setNull(5, Types.SMALLINT);
                } else {
                    ps.setInt(5, finding.getWordPosition());
                }
                setString(ps, 6, limit(finding.getExpectedText(), 255));
                setString(ps, 7, limit(finding.getHeardText(), 255));
                setString(ps, 8, finding.getExplanation());
                // Phase 3 never records an instructor decision. These two values are fixed.
                ps.setString(9, FindingAiStatus.PROPOSED.name());
                ps.setString(10, FindingInstructorStatus.PENDING.name());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public List<RecitationFindingRecord> listByAnalysisId(Connection connection, long analysisId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE analysis_id = ? ORDER BY finding_id";
        List<RecitationFindingRecord> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, analysisId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(map(rs));
                }
            }
        }
        return rows;
    }

    @Override
    public Map<Long, List<RecitationFindingRecord>> listByAnalysisIds(Connection connection, Collection<Long> analysisIds)
            throws SQLException {
        if (analysisIds == null || analysisIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = String.join(",", Collections.nCopies(analysisIds.size(), "?"));
        String sql = "SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE analysis_id IN (" + placeholders + ") ORDER BY analysis_id, finding_id";
        Map<Long, List<RecitationFindingRecord>> grouped = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 1;
            for (Long analysisId : analysisIds) {
                ps.setLong(index++, analysisId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RecitationFindingRecord row = map(rs);
                    grouped.computeIfAbsent(row.getAnalysisId(), key -> new ArrayList<>()).add(row);
                }
            }
        }
        return grouped;
    }

    @Override
    public Optional<RecitationFindingRecord> findById(Connection connection, long findingId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE finding_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, findingId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public boolean updateDecision(Connection connection,
                                  long findingId,
                                  FindingInstructorStatus status,
                                  String instructorExpectedText,
                                  String instructorHeardText,
                                  String instructorExplanation,
                                  String instructorNote,
                                  long decidedByInstructorId) throws SQLException {
        // finding_type, verse_key, word_position, expected_text, heard_text, explanation and
        // ai_status are deliberately absent: the original AI proposal is immutable.
        String sql = "UPDATE " + TABLE + " SET instructor_status = ?, instructor_expected_text = ?, "
                + "instructor_heard_text = ?, instructor_explanation = ?, instructor_note = ?, "
                + "decided_by_instructor_id = ?, decided_at = CURRENT_TIMESTAMP WHERE finding_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status.name());
            setString(ps, 2, limit(instructorExpectedText, 255));
            setString(ps, 3, limit(instructorHeardText, 255));
            setString(ps, 4, instructorExplanation);
            setString(ps, 5, instructorNote);
            ps.setLong(6, decidedByInstructorId);
            ps.setLong(7, findingId);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public long insertInstructorAdded(Connection connection,
                                      long analysisId,
                                      long recitationId,
                                      FindingType findingType,
                                      String verseKey,
                                      Integer wordPosition,
                                      String instructorExpectedText,
                                      String instructorHeardText,
                                      String instructorExplanation,
                                      String instructorNote,
                                      long decidedByInstructorId) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " ("
                + "analysis_id, recitation_id, finding_type, verse_key, word_position, "
                + "ai_status, instructor_status, instructor_expected_text, instructor_heard_text, "
                + "instructor_explanation, instructor_note, decided_by_instructor_id, decided_at"
                + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, analysisId);
            ps.setLong(2, recitationId);
            ps.setString(3, findingType.name());
            setString(ps, 4, limit(verseKey, 12));
            if (wordPosition == null) {
                ps.setNull(5, Types.SMALLINT);
            } else {
                ps.setInt(5, wordPosition);
            }
            // No AI proposed this, so there is no AI text and no AI status to claim.
            ps.setString(6, FindingAiStatus.NOT_APPLICABLE.name());
            ps.setString(7, FindingInstructorStatus.INSTRUCTOR_ADDED.name());
            setString(ps, 8, limit(instructorExpectedText, 255));
            setString(ps, 9, limit(instructorHeardText, 255));
            setString(ps, 10, instructorExplanation);
            setString(ps, 11, instructorNote);
            ps.setLong(12, decidedByInstructorId);

            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Insert instructor finding affected " + updated + " rows");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for instructor finding insert");
                }
                return keys.getLong(1);
            }
        }
    }

    @Override
    public int countPendingByAnalysisId(Connection connection, long analysisId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + TABLE
                + " WHERE analysis_id = ? AND instructor_status = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, analysisId);
            ps.setString(2, FindingInstructorStatus.PENDING.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private RecitationFindingRecord map(ResultSet rs) throws SQLException {
        RecitationFindingRecord row = new RecitationFindingRecord();
        row.setFindingId(rs.getLong("finding_id"));
        row.setAnalysisId(rs.getLong("analysis_id"));
        row.setRecitationId(rs.getLong("recitation_id"));
        row.setFindingType(FindingType.valueOf(rs.getString("finding_type")));
        row.setVerseKey(rs.getString("verse_key"));
        int position = rs.getInt("word_position");
        row.setWordPosition(rs.wasNull() ? null : position);
        row.setExpectedText(rs.getString("expected_text"));
        row.setHeardText(rs.getString("heard_text"));
        row.setExplanation(rs.getString("explanation"));
        row.setAiStatus(FindingAiStatus.valueOf(rs.getString("ai_status")));
        double confidence = rs.getDouble("ai_confidence");
        row.setAiConfidence(rs.wasNull() ? null : confidence);
        row.setInstructorStatus(FindingInstructorStatus.valueOf(rs.getString("instructor_status")));
        row.setInstructorExpectedText(rs.getString("instructor_expected_text"));
        row.setInstructorHeardText(rs.getString("instructor_heard_text"));
        row.setInstructorExplanation(rs.getString("instructor_explanation"));
        row.setInstructorNote(rs.getString("instructor_note"));
        long instructorId = rs.getLong("decided_by_instructor_id");
        row.setDecidedByInstructorId(rs.wasNull() ? null : instructorId);
        Timestamp decidedAt = rs.getTimestamp("decided_at");
        if (decidedAt != null) {
            row.setDecidedAt(decidedAt.toInstant());
        }
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            row.setCreatedAt(createdAt.toInstant());
        }
        return row;
    }

    private static void setString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
