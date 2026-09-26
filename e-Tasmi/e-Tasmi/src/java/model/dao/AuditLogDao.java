package model.dao;

import model.entity.AuditLog;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface AuditLogDao {
    void insertLog(Connection connection, AuditLog log) throws SQLException;
    List<AuditLog> listLogs(Connection connection, String action, String role, String dateFrom, String dateTo) throws SQLException;
}
