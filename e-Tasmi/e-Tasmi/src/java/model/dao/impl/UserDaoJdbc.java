package model.dao.impl;

import model.dao.UserDao;
import model.entity.User;
import model.entity.UserRole;
import model.entity.UserStatus;

import java.sql.*;
import java.time.Instant;
import java.util.Optional;

/**
 * IMPORTANT: SQL/table/column names must match your finalized schema.
 * If your ERD uses different names, update constants here (and the other *Jdbc classes).
 */
public class UserDaoJdbc implements UserDao {
    // MySQL: `user` can be problematic as an identifier; always escape it.
    private static final String TABLE = "`user`";

    private static final String COL_USER_ID = "user_id";
    private static final String COL_FULL_NAME = "full_name";
    private static final String COL_EMAIL = "email";
    private static final String COL_PHONE = "phone";
    private static final String COL_PROFILE_IMAGE_URL = "profile_image_url";
    private static final String COL_PROFILE_IMAGE_UPDATED_AT = "profile_image_updated_at";
    private static final String COL_PASSWORD_HASH = "password_hash";
    private static final String COL_IS_ACTIVE = "is_active";
    private static final String COL_ROLE = "role";
    private static final String COL_STATUS = "status";
    private static final String COL_CREATED_AT = "created_at";
    private static final String COL_EMAIL_VERIFIED = "email_verified";
    private static final String COL_EMAIL_VERIFIED_AT = "email_verified_at";
    private static final String COL_EMAIL_VERIFICATION_TOKEN_HASH = "email_verification_token_hash";
    private static final String COL_EMAIL_VERIFICATION_TOKEN_EXPIRES_AT = "email_verification_token_expires_at";

    private static final String SELECT_COLUMNS =
            COL_USER_ID + "," + COL_FULL_NAME + "," + COL_EMAIL + "," + COL_PHONE + "," +
            COL_PROFILE_IMAGE_URL + "," + COL_PROFILE_IMAGE_UPDATED_AT + "," + COL_PASSWORD_HASH + "," +
            COL_IS_ACTIVE + "," + COL_ROLE + "," + COL_STATUS + "," + COL_EMAIL_VERIFIED + "," +
            COL_EMAIL_VERIFIED_AT + "," + COL_EMAIL_VERIFICATION_TOKEN_HASH + "," +
            COL_EMAIL_VERIFICATION_TOKEN_EXPIRES_AT + "," + COL_CREATED_AT;

    @Override
    public Optional<User> findByEmail(Connection connection, String email) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM " + TABLE
                + " WHERE " + COL_EMAIL + " = ? AND " + COL_STATUS + " <> ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, UserStatus.DELETED.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    @Override
    public Optional<User> findById(Connection connection, long userId) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM " + TABLE
                + " WHERE " + COL_USER_ID + " = ? AND " + COL_STATUS + " <> ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, UserStatus.DELETED.name());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    @Override
    public Optional<User> findAnyById(Connection connection, long userId) throws SQLException {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM " + TABLE + " WHERE " + COL_USER_ID + " = ?";
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

    @Override
    public long insert(Connection connection, User user) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (" +
                COL_FULL_NAME + "," + COL_EMAIL + "," + COL_PHONE + "," + COL_PASSWORD_HASH + "," +
                COL_IS_ACTIVE + "," + COL_ROLE + "," + COL_STATUS + "," + COL_EMAIL_VERIFIED + "," +
                COL_EMAIL_VERIFIED_AT + "," + COL_EMAIL_VERIFICATION_TOKEN_HASH + "," +
                COL_EMAIL_VERIFICATION_TOKEN_EXPIRES_AT + "," + COL_CREATED_AT +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            ps.setString(4, user.getPasswordHash());
            ps.setBoolean(5, user.isActive());
            ps.setString(6, user.getRole() == null ? null : user.getRole().name());
            ps.setString(7, user.getStatus() == null ? UserStatus.ACTIVE.name() : user.getStatus().name());
            ps.setBoolean(8, user.isEmailVerified());
            setTimestampOrNull(ps, 9, user.getEmailVerifiedAt());
            ps.setString(10, user.getEmailVerificationTokenHash());
            setTimestampOrNull(ps, 11, user.getEmailVerificationTokenExpiresAt());
            ps.setTimestamp(12, Timestamp.from(user.getCreatedAt() == null ? Instant.now() : user.getCreatedAt()));

            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Insert user affected " + updated + " rows");
            }

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for user insert");
                }
                return keys.getLong(1);
            }
        }
    }

    @Override
    public boolean updateProfile(Connection connection, long userId, String fullName, String phone) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET " + COL_FULL_NAME + " = ?, " + COL_PHONE + " = ?"
                + " WHERE " + COL_USER_ID + " = ? AND " + COL_STATUS + " <> ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, phone);
            ps.setLong(3, userId);
            ps.setString(4, UserStatus.DELETED.name());
            int updated = ps.executeUpdate();
            return updated == 1;
        }
    }

    @Override
    public boolean updatePasswordHash(Connection connection, long userId, String passwordHash) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET " + COL_PASSWORD_HASH + " = ?"
                + " WHERE " + COL_USER_ID + " = ? AND " + COL_STATUS + " <> ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setLong(2, userId);
            ps.setString(3, UserStatus.DELETED.name());
            int updated = ps.executeUpdate();
            return updated == 1;
        }
    }

    @Override
    public boolean updateProfileImage(Connection connection, long userId, String profileImageUrl) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET " + COL_PROFILE_IMAGE_URL + " = ?, "
                + COL_PROFILE_IMAGE_UPDATED_AT + " = CURRENT_TIMESTAMP"
                + " WHERE " + COL_USER_ID + " = ? AND " + COL_STATUS + " <> ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, profileImageUrl);
            ps.setLong(2, userId);
            ps.setString(3, UserStatus.DELETED.name());
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean update(Connection connection, User user) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET "
                + COL_FULL_NAME + " = ?, "
                + COL_EMAIL + " = ?, "
                + COL_PHONE + " = ?, "
                + COL_ROLE + " = ?, "
                + COL_STATUS + " = ?"
                + " WHERE " + COL_USER_ID + " = ? AND " + COL_STATUS + " <> ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            ps.setString(4, user.getRole() == null ? null : user.getRole().name());
            ps.setString(5, user.getStatus() == null ? UserStatus.ACTIVE.name() : user.getStatus().name());
            ps.setLong(6, user.getUserId());
            ps.setString(7, UserStatus.DELETED.name());
            int updated = ps.executeUpdate();
            return updated == 1;
        }
    }

    @Override
    public java.util.List<User> listUsers(Connection connection, String search, String role) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT " + SELECT_COLUMNS + " FROM " + TABLE
                + " WHERE " + COL_STATUS + " <> ?");
        java.util.List<Object> params = new java.util.ArrayList<>();
        params.add(UserStatus.DELETED.name());

        if (search != null && !search.isBlank()) {
            sql.append(" AND (").append(COL_FULL_NAME).append(" LIKE ? OR ").append(COL_EMAIL).append(" LIKE ?)");
            params.add("%" + search + "%");
            params.add("%" + search + "%");
        }
        if (role != null && !role.isBlank()) {
            sql.append(" AND ").append(COL_ROLE).append(" = ?");
            params.add(role);
        }

        sql.append(" ORDER BY ").append(COL_CREATED_AT).append(" DESC LIMIT 100");
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                java.util.List<User> users = new java.util.ArrayList<>();
                while (rs.next()) {
                    users.add(map(rs));
                }
                return users;
            }
        }
    }

    @Override
    public int countUsersByRole(Connection connection, String role) throws SQLException {
        if (role == null || role.isBlank()) {
            String sql = "SELECT COUNT(*) FROM " + TABLE + " WHERE " + COL_STATUS + " <> ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, UserStatus.DELETED.name());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        }

        String sql = "SELECT COUNT(*) FROM " + TABLE
                + " WHERE " + COL_ROLE + " = ? AND " + COL_STATUS + " <> ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, role);
            ps.setString(2, UserStatus.DELETED.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    @Override
    public boolean toggleActive(Connection connection, long userId, boolean active) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET " + COL_IS_ACTIVE + " = ?, " + COL_STATUS + " = ?"
                + " WHERE " + COL_USER_ID + " = ? AND " + COL_STATUS + " <> ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setString(2, active ? UserStatus.ACTIVE.name() : UserStatus.INACTIVE.name());
            ps.setLong(3, userId);
            ps.setString(4, UserStatus.DELETED.name());
            int updated = ps.executeUpdate();
            return updated == 1;
        }
    }

    @Override
    public boolean softDelete(Connection connection, long userId) throws SQLException {
        String sql = "UPDATE " + TABLE
                + " SET " + COL_IS_ACTIVE + " = 0, "
                + COL_STATUS + " = ?, "
                + COL_EMAIL + " = CONCAT('deleted+', " + COL_USER_ID + ", '+', UNIX_TIMESTAMP(), '@deleted.etasmi.local'), "
                + COL_EMAIL_VERIFIED + " = 0, "
                + COL_EMAIL_VERIFIED_AT + " = NULL, "
                + COL_EMAIL_VERIFICATION_TOKEN_HASH + " = NULL, "
                + COL_EMAIL_VERIFICATION_TOKEN_EXPIRES_AT + " = NULL "
                + "WHERE " + COL_USER_ID + " = ? AND " + COL_STATUS + " <> ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, UserStatus.DELETED.name());
            ps.setLong(2, userId);
            ps.setString(3, UserStatus.DELETED.name());
            int updated = ps.executeUpdate();
            return updated == 1;
        }
    }

    @Override
    public boolean existsByEmailExcludingUserId(Connection connection, String email, Long excludeUserId) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM " + TABLE
                + " WHERE LOWER(" + COL_EMAIL + ") = LOWER(?) AND " + COL_STATUS + " <> ?");
        if (excludeUserId != null && excludeUserId > 0) {
            sql.append(" AND ").append(COL_USER_ID).append(" <> ?");
        }
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            ps.setString(1, email);
            ps.setString(2, UserStatus.DELETED.name());
            if (excludeUserId != null && excludeUserId > 0) {
                ps.setLong(3, excludeUserId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    @Override
    public int countActiveUsersByRole(Connection connection, String role) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + TABLE
                + " WHERE " + COL_ROLE + " = ? AND " + COL_IS_ACTIVE + " = 1 AND " + COL_STATUS + " <> ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, role);
            ps.setString(2, UserStatus.DELETED.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private User map(ResultSet rs) throws SQLException {
        User u = new User();
        u.setUserId(rs.getLong(COL_USER_ID));
        u.setFullName(rs.getString(COL_FULL_NAME));
        u.setEmail(rs.getString(COL_EMAIL));
        u.setPhone(rs.getString(COL_PHONE));
        u.setProfileImageUrl(safeGetString(rs, COL_PROFILE_IMAGE_URL));
        Timestamp profileImageUpdatedAt = safeGetTimestamp(rs, COL_PROFILE_IMAGE_UPDATED_AT);
        if (profileImageUpdatedAt != null) {
            u.setProfileImageUpdatedAt(profileImageUpdatedAt.toInstant());
        }
        u.setPasswordHash(rs.getString(COL_PASSWORD_HASH));
        Boolean active = safeGetBoolean(rs, COL_IS_ACTIVE);
        u.setActive(active == null || active);
        u.setRole(UserRole.fromString(rs.getString(COL_ROLE)));
        u.setStatus(UserStatus.fromString(rs.getString(COL_STATUS)));
        Timestamp created = safeGetTimestamp(rs, COL_CREATED_AT);
        if (created != null) {
            u.setCreatedAt(created.toInstant());
        }
        Boolean emailVerified = safeGetBoolean(rs, COL_EMAIL_VERIFIED);
        if (emailVerified != null) {
            u.setEmailVerified(emailVerified);
        }
        u.setEmailVerificationTokenHash(safeGetString(rs, COL_EMAIL_VERIFICATION_TOKEN_HASH));

        Timestamp verifiedAt = safeGetTimestamp(rs, COL_EMAIL_VERIFIED_AT);
        if (verifiedAt != null) {
            u.setEmailVerifiedAt(verifiedAt.toInstant());
        }

        Timestamp tokenExpiresAt = safeGetTimestamp(rs, COL_EMAIL_VERIFICATION_TOKEN_EXPIRES_AT);
        if (tokenExpiresAt != null) {
            u.setEmailVerificationTokenExpiresAt(tokenExpiresAt.toInstant());
        }
        return u;
    }

    private void setTimestampOrNull(PreparedStatement ps, int index, Instant value) throws SQLException {
        if (value == null) {
            ps.setTimestamp(index, null);
        } else {
            ps.setTimestamp(index, Timestamp.from(value));
        }
    }

    private String safeGetString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException ex) {
            return null;
        }
    }

    private Boolean safeGetBoolean(ResultSet rs, String column) {
        try {
            boolean value = rs.getBoolean(column);
            return rs.wasNull() ? null : value;
        } catch (SQLException ex) {
            return null;
        }
    }

    private Timestamp safeGetTimestamp(ResultSet rs, String column) {
        try {
            return rs.getTimestamp(column);
        } catch (SQLException ex) {
            return null;
        }
    }
}
