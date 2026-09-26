package model.dao.impl;

import model.dao.NotificationDao;
import model.entity.Notification;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * IMPORTANT: Update table/column names to match your finalized schema.
 */
public class NotificationDaoJdbc implements NotificationDao {
    private static final String TABLE = "notifications";

    private static final String COL_NOTIFICATION_ID = "notification_id";
    private static final String COL_USER_ID = "user_id";
    private static final String COL_MESSAGE = "message";
    private static final String COL_CREATED_AT = "created_at";

    @Override
    public long insert(Connection connection, Notification notification) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (" + COL_USER_ID + "," + COL_MESSAGE + "," + COL_CREATED_AT + ") VALUES (?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, notification.getUserId());
            ps.setString(2, notification.getMessage());
            ps.setTimestamp(3, Timestamp.from(notification.getCreatedAt() == null ? Instant.now() : notification.getCreatedAt()));

            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Insert notification affected " + updated + " rows");
            }

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for notification insert");
                }
                return keys.getLong(1);
            }
        }
    }

    @Override
    public List<Notification> listByUserId(Connection connection, long userId) throws SQLException {
        String sql = "SELECT " + COL_NOTIFICATION_ID + "," + COL_USER_ID + "," + COL_MESSAGE + "," + COL_CREATED_AT +
                " FROM " + TABLE + " WHERE " + COL_USER_ID + " = ? ORDER BY " + COL_CREATED_AT + " DESC";

        List<Notification> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Notification n = new Notification();
                    n.setNotificationId(rs.getLong(COL_NOTIFICATION_ID));
                    n.setUserId(rs.getLong(COL_USER_ID));
                    n.setMessage(rs.getString(COL_MESSAGE));
                    Timestamp ts = rs.getTimestamp(COL_CREATED_AT);
                    if (ts != null) {
                        n.setCreatedAt(ts.toInstant());
                    }
                    results.add(n);
                }
            }
        }
        return results;
    }
}
