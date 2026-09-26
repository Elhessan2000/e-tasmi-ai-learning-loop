package model.dao;

import model.entity.Admin;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public interface AdminDao {
    long insert(Connection connection, Admin admin) throws SQLException;

    Optional<Admin> findByUserId(Connection connection, long userId) throws SQLException;
}
