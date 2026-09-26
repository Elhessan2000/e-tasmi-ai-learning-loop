package model.dao;

import model.entity.Recitation;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface RecitationDao {
    long insert(Connection connection, Recitation recitation) throws SQLException;

    Optional<Recitation> findById(Connection connection, long recitationId) throws SQLException;

    List<Recitation> listByEnrollmentId(Connection connection, long enrollmentId) throws SQLException;

    /**
     * Lists recitations for a given instructor by joining: recitation -> enrollment -> tasmi_session.
     */
    List<Recitation> listForInstructor(Connection connection, long instructorId) throws SQLException;
}
