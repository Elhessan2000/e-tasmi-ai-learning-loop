package model.dao.impl;

import model.dao.AdminDao;
import model.entity.Admin;

import java.sql.*;
import java.util.Optional;

public class AdminDaoJdbc implements AdminDao {
    private static final String TABLE = "admin";

    private static final String COL_ADMIN_ID = "admin_id";
    private static final String COL_USER_ID = "user_id";

    @Override
    public long insert(Connection connection, Admin admin) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (" + COL_USER_ID + ") VALUES (?)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, admin.getUserId());

            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Insert admin affected " + updated + " rows");
            }

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for admin insert");
                }
                return keys.getLong(1);
            }
        }
    }

    @Override
    public Optional<Admin> findByUserId(Connection connection, long userId) throws SQLException {
        String sql = "SELECT " + COL_ADMIN_ID + "," + COL_USER_ID + " FROM " + TABLE + " WHERE " + COL_USER_ID + " = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }

                Admin a = new Admin();
                a.setAdminId(rs.getLong(COL_ADMIN_ID));
                a.setUserId(rs.getLong(COL_USER_ID));
                return Optional.of(a);
            }
        }
    }
}
