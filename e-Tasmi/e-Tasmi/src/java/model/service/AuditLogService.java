package model.service;

import model.dao.AuditLogDao;
import model.dao.impl.AuditLogDaoJdbc;
import model.entity.AuditLog;
import util.Db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AuditLogService {
    private static final Logger LOGGER = Logger.getLogger(AuditLogService.class.getName());
    private final AuditLogDao auditLogDao;

    public AuditLogService() {
        this.auditLogDao = new AuditLogDaoJdbc();
    }

    public void log(Connection connection, Long actorUserId, String actorRole,
                    String action, String entityType, String entityId, String detail) throws SQLException {
        AuditLog log = new AuditLog();
        log.setActorUserId(actorUserId);
        log.setActorRole(actorRole);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetail(detail);
        auditLogDao.insertLog(connection, log);
    }

    public List<AuditLog> listLogs(String action, String role, String dateFrom, String dateTo) {
        try (Connection connection = Db.getConnection()) {
            return auditLogDao.listLogs(connection, action, role, dateFrom, dateTo);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load audit logs", ex);
            return List.of();
        }
    }
}
