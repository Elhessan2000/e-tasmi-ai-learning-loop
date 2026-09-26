package model.dao.impl;

import model.dao.StudentDao;
import model.entity.Student;
import model.entity.StudentLevel;

import java.sql.*;
import java.util.Optional;

public class StudentDaoJdbc implements StudentDao {
    private static final String TABLE = "student";

    private static final String COL_STUDENT_ID = "student_id";
    private static final String COL_USER_ID = "user_id";
    private static final String COL_REGISTRATION_NUMBER = "registration_number";
    private static final String COL_LEVEL = "level";

    private static final String ALL_COLUMNS = COL_STUDENT_ID + "," + COL_USER_ID + "," + COL_REGISTRATION_NUMBER + "," + COL_LEVEL;

    @Override
    public long insert(Connection connection, Student student) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (" + COL_USER_ID + "," + COL_REGISTRATION_NUMBER + "," + COL_LEVEL + ") VALUES (?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, student.getUserId());
            ps.setString(2, student.getRegistrationNumber());
            ps.setString(3, student.getLevel() == null ? StudentLevel.PRIMARY_SCHOOL.name() : student.getLevel().name());

            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Insert student affected " + updated + " rows");
            }

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for student insert");
                }
                return keys.getLong(1);
            }
        }
    }

    @Override
    public Optional<Student> findById(Connection connection, long studentId) throws SQLException {
        String sql = "SELECT " + ALL_COLUMNS +
                " FROM " + TABLE + " WHERE " + COL_STUDENT_ID + " = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    @Override
    public Optional<Student> findByUserId(Connection connection, long userId) throws SQLException {
        String sql = "SELECT " + ALL_COLUMNS +
                " FROM " + TABLE + " WHERE " + COL_USER_ID + " = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    private Student map(ResultSet rs) throws SQLException {
        Student s = new Student();
        s.setStudentId(rs.getLong(COL_STUDENT_ID));
        s.setUserId(rs.getLong(COL_USER_ID));
        s.setRegistrationNumber(rs.getString(COL_REGISTRATION_NUMBER));
        s.setLevel(StudentLevel.fromString(rs.getString(COL_LEVEL)));
        return s;
    }
}
