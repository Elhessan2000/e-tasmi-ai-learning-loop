package model.dao;

import model.entity.RecitationAnalysis;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface RecitationAnalysisDao {
    long insert(Connection connection, RecitationAnalysis analysis) throws SQLException;

    Optional<RecitationAnalysis> findById(Connection connection, long analysisId) throws SQLException;

    Optional<RecitationAnalysis> findLatestByRecitationId(Connection connection, long recitationId) throws SQLException;

    /** Newest first. Headers only; findings are loaded separately. */
    List<RecitationAnalysis> listByRecitationId(Connection connection, long recitationId) throws SQLException;

    /**
     * The newest analysis for each of the given recitations, in one query.
     * Recitations with no analysis are absent from the map.
     */
    Map<Long, RecitationAnalysis> findLatestByRecitationIds(Connection connection, Collection<Long> recitationIds)
            throws SQLException;
}
