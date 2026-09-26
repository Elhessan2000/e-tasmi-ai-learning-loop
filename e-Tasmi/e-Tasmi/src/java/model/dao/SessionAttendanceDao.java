package model.dao;

import model.entity.SessionAttendance;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SessionAttendanceDao {
    long insert(Connection connection, SessionAttendance attendance) throws SQLException;
    boolean update(Connection connection, SessionAttendance attendance) throws SQLException;
    Optional<SessionAttendance> findBySessionIdAndStudentId(Connection connection, long sessionId, long studentId) throws SQLException;
    List<SessionAttendance> listBySessionId(Connection connection, long sessionId) throws SQLException;

    /**
     * Session IDs where this student has a PRESENT attendance row (joined live session).
     */
    Set<Long> findSessionIdsWithPresentAttendance(Connection connection, long studentId) throws SQLException;
}
