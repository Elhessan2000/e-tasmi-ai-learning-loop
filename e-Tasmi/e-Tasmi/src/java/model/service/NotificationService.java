package model.service;

import model.dao.NotificationDao;
import model.dao.impl.NotificationDaoJdbc;
import model.entity.Notification;
import util.Db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NotificationService {
    private static final Logger LOGGER = Logger.getLogger(NotificationService.class.getName());

    private final NotificationDao notificationDao;

    public NotificationService() {
        this.notificationDao = new NotificationDaoJdbc();
    }

    public List<Notification> listForUser(long userId) {
        if (userId <= 0) {
            return List.of();
        }

        try (Connection connection = Db.getConnection()) {
            return notificationDao.listByUserId(connection, userId);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to list notifications", ex);
            return List.of();
        }
    }

    /**
     * Delivers a one-way notification to a single user's inbox. Used by the admin
     * payout cockpit to contact instructors about settlements/transfers.
     *
     * @return true when the notification was persisted.
     */
    public boolean createForUser(long userId, String message) {
        if (userId <= 0 || message == null || message.trim().isEmpty()) {
            return false;
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setMessage(message.trim());
        notification.setCreatedAt(java.time.Instant.now());

        try (Connection connection = Db.getConnection()) {
            notificationDao.insert(connection, notification);
            return true;
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to create notification for user " + userId, ex);
            return false;
        }
    }
}
