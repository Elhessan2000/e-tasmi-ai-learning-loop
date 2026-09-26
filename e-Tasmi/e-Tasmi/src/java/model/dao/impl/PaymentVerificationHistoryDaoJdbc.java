package model.dao.impl;

import model.dao.PaymentVerificationHistoryDao;
import model.entity.PaymentStatus;
import model.entity.PaymentVerificationHistory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class PaymentVerificationHistoryDaoJdbc implements PaymentVerificationHistoryDao {
    private static final String TABLE = "payment_verification_history";

    @Override
    public long insert(Connection connection, PaymentVerificationHistory history) throws SQLException {
        String sql = "INSERT INTO " + TABLE
                + " (payment_id, actor_user_id, actor_role, action, from_status, to_status, reason) "
                + "VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, history.getPaymentId());
            if (history.getActorUserId() == null) {
                ps.setNull(2, Types.BIGINT);
            } else {
                ps.setLong(2, history.getActorUserId());
            }
            ps.setString(3, history.getActorRole());
            ps.setString(4, history.getAction());
            ps.setString(5, history.getFromStatus() == null ? null : history.getFromStatus().name());
            ps.setString(6, history.getToStatus() == null ? null : history.getToStatus().name());
            ps.setString(7, history.getReason());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : 0L;
            }
        }
    }

    @Override
    public List<PaymentVerificationHistory> listByPayment(Connection connection, long paymentId) throws SQLException {
        String sql = "SELECT h.history_id, h.payment_id, h.actor_user_id, h.actor_role, h.action, "
                + "h.from_status, h.to_status, h.reason, h.created_at, u.full_name AS actor_name "
                + "FROM " + TABLE + " h "
                + "LEFT JOIN `user` u ON u.user_id = h.actor_user_id "
                + "WHERE h.payment_id = ? ORDER BY h.history_id ASC";
        List<PaymentVerificationHistory> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PaymentVerificationHistory h = new PaymentVerificationHistory();
                    h.setHistoryId(rs.getLong("history_id"));
                    h.setPaymentId(rs.getLong("payment_id"));
                    long actorId = rs.getLong("actor_user_id");
                    if (!rs.wasNull()) {
                        h.setActorUserId(actorId);
                    }
                    h.setActorRole(rs.getString("actor_role"));
                    h.setAction(rs.getString("action"));
                    h.setFromStatus(PaymentStatus.fromString(rs.getString("from_status")));
                    h.setToStatus(PaymentStatus.fromString(rs.getString("to_status")));
                    h.setReason(rs.getString("reason"));
                    Timestamp created = rs.getTimestamp("created_at");
                    if (created != null) {
                        h.setCreatedAt(created.toInstant());
                    }
                    h.setActorName(rs.getString("actor_name"));
                    rows.add(h);
                }
            }
        }
        return rows;
    }
}
