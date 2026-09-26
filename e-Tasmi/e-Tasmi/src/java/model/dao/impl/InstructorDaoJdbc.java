package model.dao.impl;

import model.dao.InstructorDao;
import model.entity.Instructor;
import model.entity.InstructorVerificationStatus;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InstructorDaoJdbc implements InstructorDao {
    private static final String TABLE = "instructor";

    private static final String COL_INSTRUCTOR_ID = "instructor_id";
    private static final String COL_USER_ID = "user_id";
    private static final String COL_TITLE = "title";
    private static final String COL_QUALIFICATION = "qualification";
    private static final String COL_QUALIFICATION_FILE = "qualification_file";
    private static final String COL_BIO = "bio";
    private static final String COL_VERIFICATION_STATUS = "verification_status";

    @Override
    public long insert(Connection connection, Instructor instructor) throws SQLException {
        // Try the newest schema first; fallback to legacy schema when title/qualification columns don't exist yet.
        try {
            return insertWithOptionalColumns(connection, instructor);
        } catch (SQLException ex) {
            if (isUnknownColumn(ex)) {
                return insertLegacy(connection, instructor);
            }
            throw ex;
        }
    }

    private long insertWithOptionalColumns(Connection connection, Instructor instructor) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (" + COL_USER_ID + "," + COL_TITLE + "," + COL_QUALIFICATION + "," + COL_QUALIFICATION_FILE + "," + COL_BIO + "," + COL_VERIFICATION_STATUS + ") VALUES (?,?,?,?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, instructor.getUserId());
            ps.setString(2, instructor.getTitle());
            ps.setString(3, instructor.getQualification());
            ps.setString(4, instructor.getQualificationFile());
            ps.setString(5, instructor.getBio());
            ps.setString(6, instructor.getVerificationStatus() == null ? null : instructor.getVerificationStatus().name());

            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Insert instructor affected " + updated + " rows");
            }

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for instructor insert");
                }
                return keys.getLong(1);
            }
        }
    }

    private long insertLegacy(Connection connection, Instructor instructor) throws SQLException {
        String sql = "INSERT INTO " + TABLE + " (" + COL_USER_ID + "," + COL_QUALIFICATION_FILE + "," + COL_BIO + "," + COL_VERIFICATION_STATUS + ") VALUES (?,?,?,?)";

        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, instructor.getUserId());
            ps.setString(2, instructor.getQualificationFile());
            ps.setString(3, instructor.getBio());
            ps.setString(4, instructor.getVerificationStatus() == null ? null : instructor.getVerificationStatus().name());

            int updated = ps.executeUpdate();
            if (updated != 1) {
                throw new SQLException("Insert instructor affected " + updated + " rows");
            }

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("No generated key returned for instructor insert");
                }
                return keys.getLong(1);
            }
        }
    }

    @Override
    public Optional<Instructor> findByUserId(Connection connection, long userId) throws SQLException {
        // SELECT * keeps this method compatible with older DB schemas.
        String sql = "SELECT * FROM " + TABLE + " WHERE " + COL_USER_ID + " = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(map(rs));
            }
        }
    }

    @Override
    public Optional<Instructor> findById(Connection connection, long instructorId) throws SQLException {
        // SELECT * keeps this method compatible with older DB schemas.
        String sql = "SELECT * FROM " + TABLE + " WHERE " + COL_INSTRUCTOR_ID + " = ?";

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
    public List<Instructor> listByVerificationStatus(Connection connection, InstructorVerificationStatus status) throws SQLException {
        // SELECT * keeps this method compatible with older DB schemas.
        String sql = "SELECT * FROM " + TABLE + " WHERE " + COL_VERIFICATION_STATUS + " = ? ORDER BY " + COL_INSTRUCTOR_ID + " DESC";

        List<Instructor> results = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, status == null ? null : status.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(map(rs));
                }
            }
        }
        return results;
    }

    @Override
    public boolean updateVerificationStatus(Connection connection, long instructorId, InstructorVerificationStatus newStatus) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET " + COL_VERIFICATION_STATUS + " = ? WHERE " + COL_INSTRUCTOR_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, newStatus == null ? null : newStatus.name());
            ps.setLong(2, instructorId);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean updateProfile(Connection connection,
                                 long instructorId,
                                 String title,
                                 String qualification) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET "
                + COL_TITLE + " = ?, "
                + COL_QUALIFICATION + " = ? "
                + "WHERE " + COL_INSTRUCTOR_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, qualification);
            ps.setLong(3, instructorId);
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            // If columns don't exist yet, let the caller proceed (name/phone can still update).
            if (isUnknownColumn(ex)) {
                return true;
            }
            throw ex;
        }
    }

    private Instructor map(ResultSet rs) throws SQLException {
        Instructor i = new Instructor();
        i.setInstructorId(rs.getLong(COL_INSTRUCTOR_ID));
        i.setUserId(rs.getLong(COL_USER_ID));
        i.setQualificationFile(rs.getString(COL_QUALIFICATION_FILE));
        i.setBio(rs.getString(COL_BIO));
        i.setVerificationStatus(InstructorVerificationStatus.fromString(rs.getString(COL_VERIFICATION_STATUS)));

        // Optional columns (present only after DB migration)
        i.setTitle(safeGetString(rs, COL_TITLE));
        i.setQualification(safeGetString(rs, COL_QUALIFICATION));
        i.setZoomEmail(safeGetString(rs, "zoom_email"));
        i.setPaymentAccountHolder(safeGetString(rs, "payment_account_holder"));
        i.setPaymentMethodName(safeGetString(rs, "payment_method_name"));
        i.setPaymentAccountDetails(safeGetString(rs, "payment_account_details"));
        i.setPaymentQrUrl(safeGetString(rs, "payment_qr_url"));
        i.setPaymentInstructions(safeGetString(rs, "payment_instructions"));
        return i;
    }

    private String safeGetString(ResultSet rs, String col) {
        try {
            return rs.getString(col);
        } catch (SQLException ignored) {
            return null;
        }
    }

    @Override
    public boolean updateZoomEmail(Connection connection, long instructorId, String zoomEmail) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET zoom_email = ? WHERE " + COL_INSTRUCTOR_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, zoomEmail);
            ps.setLong(2, instructorId);
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            if (isUnknownColumn(ex)) {
                return true;
            }
            throw ex;
        }
    }

    @Override
    public boolean updatePaymentDetails(Connection connection,
                                        long instructorId,
                                        String accountHolder,
                                        String methodName,
                                        String accountDetails,
                                        String qrUrl,
                                        String instructions) throws SQLException {
        String sql = "UPDATE " + TABLE + " SET "
                + "payment_account_holder = ?, "
                + "payment_method_name = ?, "
                + "payment_account_details = ?, "
                + "payment_qr_url = ?, "
                + "payment_instructions = ? "
                + "WHERE " + COL_INSTRUCTOR_ID + " = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, accountHolder);
            ps.setString(2, methodName);
            ps.setString(3, accountDetails);
            ps.setString(4, qrUrl);
            ps.setString(5, instructions);
            ps.setLong(7, instructorId);
            return ps.executeUpdate() == 1;
        } catch (SQLException ex) {
            if (isUnknownColumn(ex)) {
                return true;
            }
            throw ex;
        }
    }

    private boolean isUnknownColumn(SQLException ex) {
        if (ex == null) {
            return false;
        }
        // MySQL unknown column is SQLState 42S22
        if ("42S22".equals(ex.getSQLState())) {
            return true;
        }
        String msg = ex.getMessage();
        return msg != null && msg.toLowerCase().contains("unknown column");
    }
}
