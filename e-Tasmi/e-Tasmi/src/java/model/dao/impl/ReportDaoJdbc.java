package model.dao.impl;

import model.dao.ReportDao;
import model.entity.ReportSummary;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ReportDaoJdbc implements ReportDao {
    @Override
    public ReportSummary loadSummary(Connection connection) throws SQLException {
        ReportSummary s = new ReportSummary();

        // Users by role
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT role, COUNT(*) AS c FROM `user` GROUP BY role")) {
            try (ResultSet rs = ps.executeQuery()) {
                long total = 0;
                while (rs.next()) {
                    String role = rs.getString("role");
                    long c = rs.getLong("c");
                    total += c;
                    if (role != null) {
                        switch (role.trim().toUpperCase()) {
                            case "STUDENT": s.setTotalStudents(c); break;
                            case "INSTRUCTOR": s.setTotalInstructors(c); break;
                            case "ADMIN": s.setTotalAdmins(c); break;
                        }
                    }
                }
                s.setTotalUsers(total);
            }
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT status, COUNT(*) AS c FROM `user` GROUP BY status")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("status");
                    long c = rs.getLong("c");
                    if (status != null) {
                        switch (status.trim().toUpperCase()) {
                            case "ACTIVE": s.setUsersActive(c); break;
                            case "INACTIVE": s.setUsersInactive(c); break;
                            case "DELETED": s.setUsersDeleted(c); break;
                        }
                    }
                }
            }
        }

        // Instructor verification counts
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT i.verification_status, COUNT(*) AS c " +
                        "FROM instructor i JOIN `user` u ON u.user_id = i.user_id " +
                        "WHERE u.is_active = 1 AND u.status <> 'DELETED' " +
                        "GROUP BY i.verification_status")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("verification_status");
                    long c = rs.getLong("c");
                    if (status != null) {
                        switch (status.trim().toUpperCase()) {
                            case "PENDING": s.setInstructorsPending(c); break;
                            case "APPROVED": s.setInstructorsApproved(c); break;
                            case "REJECTED": s.setInstructorsRejected(c); break;
                        }
                    }
                }
            }
        }

        // Sessions by status
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT status, COUNT(*) AS c FROM tasmi_session GROUP BY status")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("status");
                    long c = rs.getLong("c");
                    if (status != null) {
                        switch (status.trim().toUpperCase()) {
                            case "SCHEDULED": s.setSessionsScheduled(c); break;
                            case "ONGOING": s.setSessionsOngoing(c); break;
                            case "COMPLETED": s.setSessionsCompleted(c); break;
                            case "CANCELLED": s.setSessionsCancelled(c); break;
                        }
                    }
                }
            }
        }

        // Enrollments by status
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT enrollment_status, COUNT(*) AS c FROM enrollment GROUP BY enrollment_status")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("enrollment_status");
                    long c = rs.getLong("c");
                    if (status != null) {
                        switch (status.trim().toUpperCase()) {
                            case "PENDING": s.setEnrollmentsPending(c); break;
                            case "APPROVED": s.setEnrollmentsApproved(c); break;
                            case "REJECTED": s.setEnrollmentsRejected(c); break;
                            case "CANCELLED": s.setEnrollmentsCancelled(c); break;
                        }
                    }
                }
            }
        }

        // Payments by status
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT payment_status, COUNT(*) AS c FROM payment GROUP BY payment_status")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String status = rs.getString("payment_status");
                    long c = rs.getLong("c");
                    if (status != null) {
                        switch (status.trim().toUpperCase()) {
                            case "PENDING": s.setPaymentsPending(c); break;
                            case "SUCCESS": s.setPaymentsSuccess(c); break;
                            case "FAILED": s.setPaymentsFailed(c); break;
                        }
                    }
                }
            }
        }

        // Recitations total
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) AS c FROM recitation")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    s.setTotalRecitations(rs.getLong("c"));
                }
            }
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) AS c FROM recitation r LEFT JOIN evaluation e ON e.recitation_id = r.recitation_id WHERE e.evaluation_id IS NULL")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    s.setPendingReviews(rs.getLong("c"));
                }
            }
        }

        // Evaluations total + average score
        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) AS c, AVG(score) AS avg_score FROM evaluation")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    s.setTotalEvaluations(rs.getLong("c"));
                    BigDecimal avg = rs.getBigDecimal("avg_score");
                    s.setAvgEvaluationScore(avg);
                }
            }
        }

        return s;
    }
}
