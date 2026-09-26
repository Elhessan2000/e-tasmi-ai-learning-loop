package model.dao;

import model.entity.SessionMaterial;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface SessionMaterialDao {
    long insert(Connection connection, SessionMaterial material) throws SQLException;
    List<SessionMaterial> listBySessionId(Connection connection, long sessionId) throws SQLException;
    Optional<SessionMaterial> findByIdAndSessionId(Connection connection, long materialId, long sessionId) throws SQLException;
    boolean deleteByIdAndSessionId(Connection connection, long materialId, long sessionId) throws SQLException;
}
