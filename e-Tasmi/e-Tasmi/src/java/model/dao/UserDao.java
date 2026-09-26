package model.dao;

import model.entity.User;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public interface UserDao {
    Optional<User> findByEmail(Connection connection, String email) throws SQLException;
    Optional<User> findById(Connection connection, long userId) throws SQLException;

    /**
     * Inserts a new user and returns the generated userId.
     */
    long insert(Connection connection, User user) throws SQLException;

    /**
     * Updates editable profile fields (currently: full name, phone).
     */
    boolean updateProfile(Connection connection, long userId, String fullName, String phone) throws SQLException;

    /**
     * Updates password hash for a user.
     */
    boolean updatePasswordHash(Connection connection, long userId, String passwordHash) throws SQLException;

    /**
     * Saves the latest profile image URL for a user account.
     */
    boolean updateProfileImage(Connection connection, long userId, String profileImageUrl) throws SQLException;

    /**
     * Updates all user fields (admin CRUD).
     */
    boolean update(Connection connection, User user) throws SQLException;

    /**
     * Lists users by search and role.
     */
    java.util.List<User> listUsers(Connection connection, String search, String role) throws SQLException;

    /**
     * Counts users by role.
     */
    int countUsersByRole(Connection connection, String role) throws SQLException;

    /**
     * Activates/deactivates user.
     */
    boolean toggleActive(Connection connection, long userId, boolean active) throws SQLException;

    /**
     * Soft deletes user (set is_active=0, status=DELETED, and release the email).
     */
    boolean softDelete(Connection connection, long userId) throws SQLException;

    /**
     * Returns true when another user already uses the same email.
     */
    boolean existsByEmailExcludingUserId(Connection connection, String email, Long excludeUserId) throws SQLException;

    /**
     * Counts active users for the given role.
     */
    int countActiveUsersByRole(Connection connection, String role) throws SQLException;
}
