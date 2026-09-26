package model.dao;

import model.entity.Instructor;
import model.entity.InstructorVerificationStatus;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface InstructorDao {
    long insert(Connection connection, Instructor instructor) throws SQLException;
    Optional<Instructor> findByUserId(Connection connection, long userId) throws SQLException;
    Optional<Instructor> findById(Connection connection, long instructorId) throws SQLException;

    List<Instructor> listByVerificationStatus(Connection connection, InstructorVerificationStatus status) throws SQLException;
    boolean updateVerificationStatus(Connection connection, long instructorId, InstructorVerificationStatus newStatus) throws SQLException;

    boolean updateProfile(Connection connection,
                          long instructorId,
                          String title,
                          String qualification) throws SQLException;

    boolean updateZoomEmail(Connection connection,
                            long instructorId,
                            String zoomEmail) throws SQLException;

    boolean updatePaymentDetails(Connection connection,
                                 long instructorId,
                                 String accountHolder,
                                 String methodName,
                                 String accountDetails,
                                 String qrUrl,
                                 String instructions) throws SQLException;
}
