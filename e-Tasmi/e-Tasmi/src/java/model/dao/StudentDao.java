package model.dao;

import model.entity.Student;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public interface StudentDao {
    long insert(Connection connection, Student student) throws SQLException;
    Optional<Student> findById(Connection connection, long studentId) throws SQLException;
    Optional<Student> findByUserId(Connection connection, long userId) throws SQLException;
}
