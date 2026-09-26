package model.dao;

import model.entity.Notification;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface NotificationDao {
    long insert(Connection connection, Notification notification) throws SQLException;

    List<Notification> listByUserId(Connection connection, long userId) throws SQLException;
}
