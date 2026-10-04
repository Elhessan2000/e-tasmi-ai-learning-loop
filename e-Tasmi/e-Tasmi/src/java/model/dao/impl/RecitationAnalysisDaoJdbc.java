package model.dao.impl;

import model.dao.RecitationAnalysisDao;
import model.entity.RecitationAnalysis;

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

public class RecitationAnalysisDaoJdbc implements RecitationAnalysisDao {
    private static final String TABLE = "recitation_analysis";

    private static final String COLUMNS =
            "analysis_id, recitation_id, status, status_reason, stt_provider, stt_model, analysis_model, "
                    + "transcript, reference_source, reference_verse_keys, reference_text, matches_expected_passage, "
                    + "accuracy_percent, ai_suggested_score, ai_summary, ai_feedback, matched_passage_note, "
                    + "correct_word_count, is_quran_confidence, mixed_passages, detected_passages, created_at";

    @Override
    public long insert(Connection connection, RecitationAnalysis analysis) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " ("
                + "recitation_id, status, status_reason, stt_provider, stt_model, analysis_model, "
                + "transcript, reference_source, reference_verse_keys, reference_text, matches_expected_passage, "
                + "accuracy_percent, ai_suggested_score, ai_summary, ai_feedback, matched_passage_note, "
                + "correct_word_count, is_quran_confidence, mixed_passages, detected_passages"
                + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, analysis.getRecitationId());
            ps.setString(2, analysis.getStatus());
            setString(ps, 3, limit(analysis.getStatusReason(), 500));
            setString(ps, 4, limit(analysis.getSttProvider(), 60));
            setString(ps, 5, limit(analysis.getSttModel(), 60));
            setString(ps, 6, limit(analysis.getAnalysisModel(), 60));
            setString(ps, 7, analysis.getTranscript());
            setString(ps, 8, limit(analysis.getReferenceSource(), 60));
            setString(ps, 9, limit(analysis.getReferenceVerseKeys(), 255));
            setString(ps, 10, analysis.getReferenceText());
            setBoolean(ps, 11, analysis.getMatchesExpectedPassage());
            setDouble(ps, 12, analysis.getAccuracyPercent());
            setInteger(ps, 13, analysis.getAiSuggestedScore());
            setString(ps, 14, analysis.getAiSummary());
            setString(ps, 15, analysis.getAiFeedback());
            setString(ps, 16, analysis.getMatchedPassageNote());
            setInteger(ps, 17, analysis.getCorrectWordCount());
            setDouble(ps, 18, analysis.getIsQuranConfidence());
            setBoolean(ps, 19, analysis.getMixedPassages());
            setString(ps, 20, limit(analysis.getDetectedPassages(), 255));

            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Insert recitation_analysis affected " + updated + " rows");
            }
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for recitation_analysis insert");
                }
                return keys.getLong(1);
            }
        }
    }

    @Override
    public Optional<RecitationAnalysis> findById(Connection connection, long analysisId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE analysis_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, analysisId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    @Override
    public Optional<RecitationAnalysis> findLatestByRecitationId(Connection connection, long recitationId)
            throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE recitation_id = ? ORDER BY created_at DESC, analysis_id DESC LIMIT 1";
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
    public List<RecitationAnalysis> listByRecitationId(Connection connection, long recitationId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM " + TABLE
                + " WHERE recitation_id = ? ORDER BY created_at DESC, analysis_id DESC";
        List<RecitationAnalysis> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, recitationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(map(rs));
                }
            }
        }
        return rows;
    }

    @Override
    public Map<Long, RecitationAnalysis> findLatestByRecitationIds(Connection connection, Collection<Long> recitationIds)
            throws SQLException {
        if (recitationIds == null || recitationIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String placeholders = String.join(",", Collections.nCopies(recitationIds.size(), "?"));
        String sql = "SELECT " + qualify(COLUMNS, "a") + " FROM " + TABLE + " a"
                + " INNER JOIN ("
                + "   SELECT recitation_id, MAX(analysis_id) AS analysis_id"
                + "   FROM " + TABLE
                + "   WHERE recitation_id IN (" + placeholders + ")"
                + "   GROUP BY recitation_id"
                + " ) latest ON a.analysis_id = latest.analysis_id";

        Map<Long, RecitationAnalysis> byRecitation = new LinkedHashMap<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 1;
            for (Long recitationId : recitationIds) {
                ps.setLong(index++, recitationId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RecitationAnalysis row = map(rs);
                    byRecitation.put(row.getRecitationId(), row);
                }
            }
        }
        return byRecitation;
    }

    private RecitationAnalysis map(ResultSet rs) throws SQLException {
        RecitationAnalysis row = new RecitationAnalysis();
        row.setAnalysisId(rs.getLong("analysis_id"));
        row.setRecitationId(rs.getLong("recitation_id"));
        row.setStatus(rs.getString("status"));
        row.setStatusReason(rs.getString("status_reason"));
        row.setSttProvider(rs.getString("stt_provider"));
        row.setSttModel(rs.getString("stt_model"));
        row.setAnalysisModel(rs.getString("analysis_model"));
        row.setTranscript(rs.getString("transcript"));
        row.setReferenceSource(rs.getString("reference_source"));
        row.setReferenceVerseKeys(rs.getString("reference_verse_keys"));
        row.setReferenceText(rs.getString("reference_text"));
        row.setMatchesExpectedPassage(readBoolean(rs, "matches_expected_passage"));
        row.setAccuracyPercent(readDouble(rs, "accuracy_percent"));
        row.setAiSuggestedScore(readInteger(rs, "ai_suggested_score"));
        row.setAiSummary(rs.getString("ai_summary"));
        row.setAiFeedback(rs.getString("ai_feedback"));
        row.setMatchedPassageNote(rs.getString("matched_passage_note"));
        row.setCorrectWordCount(readInteger(rs, "correct_word_count"));
        row.setIsQuranConfidence(readDouble(rs, "is_quran_confidence"));
        row.setMixedPassages(readBoolean(rs, "mixed_passages"));
        row.setDetectedPassages(rs.getString("detected_passages"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            row.setCreatedAt(created.toInstant());
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

    private static void setInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static void setDouble(PreparedStatement ps, int index, Double value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.DECIMAL);
        } else {
            ps.setDouble(index, value);
        }
    }

    private static void setBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.TINYINT);
        } else {
            ps.setBoolean(index, value);
        }
    }

    private static Integer readInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Double readDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean readBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    private static String qualify(String columns, String alias) {
        String[] parts = columns.split(",\\s*");
        StringBuilder qualified = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                qualified.append(", ");
            }
            qualified.append(alias).append('.').append(parts[i].trim());
        }
        return qualified.toString();
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
