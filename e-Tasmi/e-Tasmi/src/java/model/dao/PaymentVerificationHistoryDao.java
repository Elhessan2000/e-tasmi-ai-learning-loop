package model.dao;

import model.entity.PaymentVerificationHistory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface PaymentVerificationHistoryDao {
    long insert(Connection connection, PaymentVerificationHistory history) throws SQLException;

    /** Chronological (oldest first) timeline for a payment, with actor display names joined. */
    List<PaymentVerificationHistory> listByPayment(Connection connection, long paymentId) throws SQLException;
}
