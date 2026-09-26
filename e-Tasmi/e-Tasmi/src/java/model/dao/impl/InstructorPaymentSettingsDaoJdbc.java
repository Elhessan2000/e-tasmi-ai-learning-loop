package model.dao.impl;

import model.dao.InstructorPaymentSettingsDao;
import model.entity.InstructorPaymentSettings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

public class InstructorPaymentSettingsDaoJdbc implements InstructorPaymentSettingsDao {
    private static final String TABLE = "instructor_payment_settings";
    private static final String COLUMNS = "settings_id, instructor_id, qr_image_url, bank_name, account_holder_name, "
            + "account_number, payment_notes, is_active, created_at, updated_at";

    @Override
    public Optional<InstructorPaymentSettings> findByInstructorId(Connection connection, long instructorId) throws SQLException {
        String sql = "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE instructor_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, instructorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    @Override
    public void upsert(Connection connection, InstructorPaymentSettings settings) throws SQLException {
        String sql = "INSERT INTO " + TABLE
                + " (instructor_id, qr_image_url, bank_name, account_holder_name, account_number, payment_notes, is_active) "
                + "VALUES (?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE "
                + "qr_image_url = VALUES(qr_image_url), "
                + "bank_name = VALUES(bank_name), "
                + "account_holder_name = VALUES(account_holder_name), "
                + "account_number = VALUES(account_number), "
                + "payment_notes = VALUES(payment_notes), "
                + "is_active = VALUES(is_active)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, settings.getInstructorId());
            ps.setString(2, settings.getQrImageUrl());
            ps.setString(3, settings.getBankName());
            ps.setString(4, settings.getAccountHolderName());
            ps.setString(5, settings.getAccountNumber());
            ps.setString(6, settings.getPaymentNotes());
            ps.setBoolean(7, settings.isActive());
            ps.executeUpdate();
        }
    }

    private InstructorPaymentSettings map(ResultSet rs) throws SQLException {
        InstructorPaymentSettings s = new InstructorPaymentSettings();
        s.setSettingsId(rs.getLong("settings_id"));
        s.setInstructorId(rs.getLong("instructor_id"));
        s.setQrImageUrl(rs.getString("qr_image_url"));
        s.setBankName(rs.getString("bank_name"));
        s.setAccountHolderName(rs.getString("account_holder_name"));
        s.setAccountNumber(rs.getString("account_number"));
        s.setPaymentNotes(rs.getString("payment_notes"));
        s.setActive(rs.getBoolean("is_active"));
        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) {
            s.setCreatedAt(created.toInstant());
        }
        Timestamp updated = rs.getTimestamp("updated_at");
        if (updated != null) {
            s.setUpdatedAt(updated.toInstant());
        }
        return s;
    }
}
