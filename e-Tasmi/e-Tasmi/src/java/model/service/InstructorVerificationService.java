package model.service;

import model.dao.InstructorDao;
import model.dao.NotificationDao;
import model.dao.UserDao;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.NotificationDaoJdbc;
import model.dao.impl.UserDaoJdbc;
import model.entity.Instructor;
import model.entity.InstructorVerificationStatus;
import model.entity.Notification;
import model.entity.User;
import model.entity.UserStatus;
import util.Db;
import util.QualificationFileUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InstructorVerificationService {
    private static final Logger LOGGER = Logger.getLogger(InstructorVerificationService.class.getName());

    private final InstructorDao instructorDao;
    private final UserDao userDao;
    private final NotificationDao notificationDao;
    private final AuditLogService auditLogService;

    public InstructorVerificationService() {
        this.instructorDao = new InstructorDaoJdbc();
        this.userDao = new UserDaoJdbc();
        this.notificationDao = new NotificationDaoJdbc();
        this.auditLogService = new AuditLogService();
    }

    public List<InstructorVerificationItem> listPending() {
        return listByStatus(InstructorVerificationStatus.PENDING);
    }

    public List<InstructorVerificationItem> listByStatus(InstructorVerificationStatus status) {
        try (Connection connection = Db.getConnection()) {
            List<Instructor> instructors = instructorDao.listByVerificationStatus(connection, status);
            List<InstructorVerificationItem> items = new ArrayList<>();

            for (Instructor i : instructors) {
                Optional<User> u = userDao.findById(connection, i.getUserId());
                if (u.isPresent()) {
                    User user = u.get();
                    if (!user.isActive() || user.getStatus() == UserStatus.DELETED) {
                        continue;
                    }
                    items.add(new InstructorVerificationItem(
                            i,
                            user,
                            QualificationFileUtil.isAvailable(i.getQualificationFile())
                    ));
                }
            }

            return items;
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to list instructors", ex);
            return List.of();
        }
    }

    public InstructorVerificationUpdateResult approve(long actorUserId, long instructorId) {
        return updateStatus(actorUserId, instructorId, InstructorVerificationStatus.APPROVED);
    }

    public InstructorVerificationUpdateResult reject(long actorUserId, long instructorId) {
        return updateStatus(actorUserId, instructorId, InstructorVerificationStatus.REJECTED);
    }

    private InstructorVerificationUpdateResult updateStatus(long actorUserId, long instructorId, InstructorVerificationStatus newStatus) {
        try (Connection connection = Db.getConnection()) {
            connection.setAutoCommit(false);

            Optional<Instructor> instructorOpt = instructorDao.findById(connection, instructorId);
            if (instructorOpt.isEmpty()) {
                connection.rollback();
                return InstructorVerificationUpdateResult.failure("Instructor not found.");
            }

            Instructor current = instructorOpt.get();
            if (current.getVerificationStatus() != InstructorVerificationStatus.PENDING) {
                connection.rollback();
                return InstructorVerificationUpdateResult.failure("Only pending instructors can be updated.");
            }

            boolean updated = instructorDao.updateVerificationStatus(connection, instructorId, newStatus);
            if (!updated) {
                connection.rollback();
                return InstructorVerificationUpdateResult.failure("Failed to update the instructor verification record.");
            }

            Optional<User> userOpt = userDao.findById(connection, current.getUserId());
            if (userOpt.isEmpty()) {
                connection.rollback();
                return InstructorVerificationUpdateResult.failure("Linked user account was not found.");
            }

            User user = userOpt.get();
            if (newStatus == InstructorVerificationStatus.APPROVED && !user.isEmailVerified()) {
                connection.rollback();
                return InstructorVerificationUpdateResult.failure("This instructor must verify their email before approval.");
            }
            user.setStatus(newStatus == InstructorVerificationStatus.APPROVED ? UserStatus.ACTIVE : UserStatus.INACTIVE);
            if (!userDao.update(connection, user)) {
                connection.rollback();
                return InstructorVerificationUpdateResult.failure("Failed to update the linked user status.");
            }

            if (current.getUserId() > 0) {
                Notification n = new Notification();
                n.setUserId(current.getUserId());
                n.setCreatedAt(Instant.now());
                if (newStatus == InstructorVerificationStatus.APPROVED) {
                    n.setMessage("Your instructor account has been approved. You now have full instructor access.");
                } else if (newStatus == InstructorVerificationStatus.REJECTED) {
                    n.setMessage("Your instructor account verification was rejected. Please contact admin.");
                } else {
                    n.setMessage("Your instructor verification status is now: " + newStatus.name());
                }
                notificationDao.insert(connection, n);
            }

            auditLogService.log(
                    connection,
                    actorUserId > 0 ? actorUserId : null,
                    "ADMIN",
                    newStatus == InstructorVerificationStatus.APPROVED ? "INSTRUCTOR_APPROVED" : "INSTRUCTOR_REJECTED",
                    "INSTRUCTOR",
                    String.valueOf(instructorId),
                    "User #" + current.getUserId() + " verification changed to " + newStatus.name()
            );

            connection.commit();
            return InstructorVerificationUpdateResult.success(
                    newStatus == InstructorVerificationStatus.APPROVED
                            ? "Instructor approved successfully."
                            : "Instructor rejected successfully."
            );
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to update instructor verification", ex);
            return InstructorVerificationUpdateResult.failure("A server error prevented the verification update.");
        }
    }
}
