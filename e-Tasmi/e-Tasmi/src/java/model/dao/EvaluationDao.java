package model.dao;

import model.entity.Evaluation;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public interface EvaluationDao {
    long insert(Connection connection, Evaluation evaluation) throws SQLException;
    boolean update(Connection connection, Evaluation evaluation) throws SQLException;

    Optional<Evaluation> findByRecitationId(Connection connection, long recitationId) throws SQLException;
}
