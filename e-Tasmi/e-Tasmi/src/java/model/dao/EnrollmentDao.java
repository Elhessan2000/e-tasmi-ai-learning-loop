package model.dao;

import model.entity.Enrollment;
import model.entity.EnrollmentStatus;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface EnrollmentDao {
    long insert(Connection connection, Enrollment enrollment) throws SQLException;

    Optional<Enrollment> findById(Connection connection, long enrollmentId) throws SQLException;

    Optional<Enrollment> findByStudentAndSession(Connection connection, long studentId, long sessionId) throws SQLException;
    List<Enrollment> listByStudentId(Connection connection, long studentId) throws SQLException;
    List<Enrollment> listBySessionId(Connection connection, long sessionId) throws SQLException;

    boolean updateStatus(Connection connection, long enrollmentId, EnrollmentStatus newStatus) throws SQLException;

    /**
     * Counts enrollments for a session that are still active (PENDING or APPROVED).
     */
    int countActiveBySessionId(Connection connection, long sessionId) throws SQLException;

    /**
     * Pending or approved enrollments the instructor must see, and that block session deletion.
     * A soft-deleted student is included only when the enrollment still has a recitation.
     */
    int countOccupyingBySessionId(Connection connection, long sessionId) throws SQLException;

    /**
     * Enrollment ids in this session that have at least one recitation.
     */
    Set<Long> enrollmentIdsWithRecitations(Connection connection, long sessionId) throws SQLException;
}
