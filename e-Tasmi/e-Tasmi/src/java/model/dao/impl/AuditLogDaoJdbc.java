package model.dao.impl;

import model.dao.AuditLogDao;
import model.entity.AuditLog;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public class AuditLogDaoJdbc implements AuditLogDao {
    @Override
    public void insertLog(Connection connection, AuditLog log) throws SQLException {
        String sql = "INSERT INTO audit_log (actor_user_id, actor_role, action, entity_type, entity_id, detail) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setObject(1, log.getActorUserId(), Types.BIGINT);
            ps.setString(2, log.getActorRole());
            ps.setString(3, log.getAction());
            ps.setString(4, log.getEntityType());
            ps.setString(5, log.getEntityId());
            ps.setString(6, log.getDetail());
            ps.executeUpdate();
        }
    }

    @Override
    public List<AuditLog> listLogs(Connection connection, String action, String role, String dateFrom, String dateTo) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_log WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (action != null && !action.isBlank()) {
            // `action` is a reserved word in some SQL modes; always qualify/quote.
            sql.append(" AND `action` = ?");
            params.add(action);
        }
        if (role != null && !role.isBlank()) {
            sql.append(" AND `actor_role` = ?");
            params.add(role);
        }
        // Calendar-day range in UTC: TIMESTAMP is stored in UTC; JDBC uses serverTimezone=UTC in Docker.
        // Inclusive of both ends: [from 00:00, to+1 00:00) so the whole of `dateTo` is included.
        if (dateFrom != null && !dateFrom.isBlank()) {
            Instant start = LocalDate.parse(dateFrom).atStartOfDay(ZoneOffset.UTC).toInstant();
            sql.append(" AND `created_at` >= ?");
            params.add(Timestamp.from(start));
        }
        if (dateTo != null && !dateTo.isBlank()) {
            Instant endExclusive = LocalDate.parse(dateTo).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            sql.append(" AND `created_at` < ?");
            params.add(Timestamp.from(endExclusive));
        }
        sql.append(" ORDER BY `created_at` DESC LIMIT 100");
        try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                List<AuditLog> logs = new ArrayList<>();
                while (rs.next()) {
                    AuditLog log = new AuditLog();
                    log.setLogId(rs.getInt("log_id"));
                    log.setActorUserId(rs.getObject("actor_user_id") == null ? null : rs.getLong("actor_user_id"));
                    log.setActorRole(rs.getString("actor_role"));
                    log.setAction(rs.getString("action"));
                    log.setEntityType(rs.getString("entity_type"));
                    log.setEntityId(rs.getString("entity_id"));
                    log.setDetail(rs.getString("detail"));
                    log.setCreatedAt(rs.getTimestamp("created_at"));
                    logs.add(log);
                }
                return logs;
            }
        }
    }
}
