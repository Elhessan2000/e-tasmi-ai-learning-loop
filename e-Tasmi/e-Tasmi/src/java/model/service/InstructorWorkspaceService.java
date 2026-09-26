package model.service;

import model.dao.EnrollmentDao;
import model.dao.EvaluationDao;
import model.dao.InstructorDao;
import model.dao.NotificationDao;
import model.dao.RecitationDao;
import model.dao.StudentDao;
import model.dao.UserDao;
import model.dao.impl.EnrollmentDaoJdbc;
import model.dao.impl.EvaluationDaoJdbc;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.NotificationDaoJdbc;
import model.dao.impl.RecitationDaoJdbc;
import model.dao.impl.StudentDaoJdbc;
import model.dao.impl.UserDaoJdbc;
import model.entity.Enrollment;
import model.entity.Evaluation;
import model.entity.Instructor;
import model.entity.InstructorActivityItem;
import model.entity.InstructorDashboardStats;
import model.entity.InstructorVerificationStatus;
import model.entity.Notification;
import model.entity.Payment;
import model.entity.PaymentStatus;
import model.entity.Recitation;
import model.entity.SessionParticipantView;
import model.entity.Student;
import model.entity.TasmiSession;
import model.entity.TasmiSessionStatus;
import model.entity.User;
import util.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InstructorWorkspaceService {
    private static final Logger LOGGER = Logger.getLogger(InstructorWorkspaceService.class.getName());

    private final InstructorDao instructorDao = new InstructorDaoJdbc();
    private final EnrollmentDao enrollmentDao = new EnrollmentDaoJdbc();
    private final StudentDao studentDao = new StudentDaoJdbc();
    private final UserDao userDao = new UserDaoJdbc();
    private final RecitationDao recitationDao = new RecitationDaoJdbc();
    private final EvaluationDao evaluationDao = new EvaluationDaoJdbc();
    private final NotificationDao notificationDao = new NotificationDaoJdbc();

    public InstructorDashboardStats buildDashboard(long instructorUserId) {
        InstructorDashboardStats stats = new InstructorDashboardStats();
        stats.setRecentSessions(List.of());
        // Anchor all "Next Up" / upcoming / today math to the stable application zone so
        // production (Railway defaults to UTC) behaves identically to localhost.
        ZoneId zone = util.DateTimeFormats.appZone();
        stats.setServerNowEpochMs(Instant.now().toEpochMilli());
        stats.setServerZoneId(zone.getId());

        try (Connection connection = Db.getConnection()) {
            Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, instructorUserId);
            if (instructorOpt.isEmpty()) {
                return stats;
            }

            long instructorId = instructorOpt.get().getInstructorId();
            List<TasmiSession> sessions = new TasmiSessionService().listInstructorSessions(instructorId);
            stats.setTotalSessions(sessions.size());

            int upcoming = 0;
            int ongoing = 0;
            int completed = 0;
            int students = 0;
            Instant now = Instant.now();
            LocalDate today = LocalDate.now(zone);
            LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate weekEnd = weekStart.plusDays(6);

            List<TasmiSession> todayList = new ArrayList<>();
            List<TasmiSession> weekList = new ArrayList<>();
            List<TasmiSession> upcomingList = new ArrayList<>();

            for (TasmiSession session : sessions) {
                if (session == null) {
                    continue;
                }
                LocalDate date = session.getSessionDate();

                if (session.getStatus() == TasmiSessionStatus.ONGOING) {
                    ongoing++;
                } else if (session.getStatus() == TasmiSessionStatus.COMPLETED) {
                    completed++;
                } else if (session.getStatus() == TasmiSessionStatus.SCHEDULED
                        && isSessionStillUpcoming(session, zone, now)) {
                    upcoming++;
                }
                students += enrollmentDao.countActiveBySessionId(connection, session.getSessionId());

                if (date != null) {
                    if (date.equals(today)) {
                        todayList.add(session);
                    }
                    if (!date.isBefore(weekStart) && !date.isAfter(weekEnd)) {
                        weekList.add(session);
                    }
                    // "Upcoming" must mean: not yet finished. We compare end-of-session to NOW
                    // (not just the date), so a 3 PM session that the instructor never started
                    // does not stay on the dashboard at 9 PM.
                    if (session.getStatus() == TasmiSessionStatus.ONGOING
                            || (session.getStatus() == TasmiSessionStatus.SCHEDULED
                                    && isSessionStillUpcoming(session, zone, now))) {
                        upcomingList.add(session);
                    }
                }
            }

            Comparator<TasmiSession> chronoSort = Comparator
                    .comparing(TasmiSession::getSessionDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(TasmiSession::getSessionTime, Comparator.nullsLast(Comparator.naturalOrder()));
            todayList.sort(chronoSort);
            weekList.sort(chronoSort);
            upcomingList.sort(chronoSort);

            stats.setTodaySessions(todayList);
            stats.setWeekSessions(weekList);
            stats.setUpcomingSessionsList(upcomingList);

            stats.setUpcomingSessions(upcoming);
            stats.setOngoingSessions(ongoing);
            stats.setCompletedSessions(completed);
            stats.setEnrolledStudentCount(students);

            List<Recitation> instructorRecitations = recitationDao.listForInstructor(connection, instructorId);
            stats.setSubmittedRecitationCount(instructorRecitations.size());
            int pendingEval = 0;
            for (Recitation recitation : instructorRecitations) {
                if (recitation == null) {
                    continue;
                }
                Optional<Evaluation> existing = evaluationDao.findByRecitationId(connection, recitation.getRecitationId());
                if (existing.isEmpty()) {
                    pendingEval++;
                }
            }
            stats.setPendingEvaluationCount(pendingEval);

            // Cockpit selection: prefer a LIVE session that is still within its window;
            // otherwise pick the next chronological session whose end-time is still in the
            // future. This is what makes the countdown automatically roll forward to the
            // next session once the current one ends.
            TasmiSession nextSession = sessions.stream()
                    .filter(s -> s != null && s.getStatus() == TasmiSessionStatus.ONGOING
                            && isSessionStillUpcoming(s, zone, now))
                    .min(chronoSort)
                    .orElseGet(() -> upcomingList.stream().findFirst().orElse(null));
            stats.setNextSession(nextSession);

            List<TasmiSession> activeSessions = sessions.stream()
                    .filter(session -> session != null
                            && session.getStatus() != TasmiSessionStatus.COMPLETED
                            && session.getStatus() != TasmiSessionStatus.CANCELLED)
                    .limit(5)
                    .toList();
            stats.setRecentSessions(activeSessions);

            stats.setRecentActivity(buildActivityFeed(connection, instructorUserId, nextSession,
                    instructorRecitations, pendingEval, zone));
            return stats;
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to build instructor dashboard", ex);
            return stats;
        }
    }

    private List<InstructorActivityItem> buildActivityFeed(Connection connection,
                                                           long instructorUserId,
                                                           TasmiSession nextSession,
                                                           List<Recitation> instructorRecitations,
                                                           int pendingEvaluationCount,
                                                           ZoneId zone) {
        List<InstructorActivityItem> items = new ArrayList<>();
        Instant now = Instant.now();

        // 1. Next session reminder (rank 0 = highest)
        if (nextSession != null && nextSession.getSessionDate() != null && nextSession.getSessionTime() != null) {
            LocalDateTime startLocal = LocalDateTime.of(nextSession.getSessionDate(), nextSession.getSessionTime());
            Instant startAt = startLocal.atZone(zone).toInstant();
            String title;
            String body;
            if (nextSession.getStatus() == TasmiSessionStatus.ONGOING) {
                title = "Live now";
                body = (nextSession.getTitle() == null ? "Session" : nextSession.getTitle())
                        + " is currently live.";
            } else {
                Duration delta = Duration.between(now, startAt);
                long mins = delta.toMinutes();
                if (mins < 0) {
                    title = "Session is starting";
                    body = (nextSession.getTitle() == null ? "Your next session" : nextSession.getTitle())
                            + " is ready to start.";
                } else if (mins < 60) {
                    title = "Session starts in " + mins + " min";
                    body = nextSession.getTitle() == null ? "Get ready to teach." : nextSession.getTitle();
                } else if (mins < 60 * 24) {
                    long h = mins / 60;
                    title = "Session in " + h + " h";
                    body = nextSession.getTitle() == null ? "Upcoming on your schedule." : nextSession.getTitle();
                } else {
                    title = "Next session " + nextSession.getSessionDate();
                    body = nextSession.getTitle() == null ? "Upcoming on your schedule." : nextSession.getTitle();
                }
            }
            InstructorActivityItem reminder = new InstructorActivityItem(
                    nextSession.getStatus() == TasmiSessionStatus.ONGOING
                            ? InstructorActivityItem.Kind.SESSION_LIVE
                            : InstructorActivityItem.Kind.SESSION_REMINDER,
                    title,
                    body,
                    startAt,
                    0
            );
            reminder.setActionLabel(nextSession.getStatus() == TasmiSessionStatus.ONGOING ? "Open" : "Manage");
            reminder.setActionHref("/instructor/sessions");
            items.add(reminder);
        }

        // 2. Pending evaluations rollup (rank 1)
        if (pendingEvaluationCount > 0) {
            InstructorActivityItem pending = new InstructorActivityItem(
                    InstructorActivityItem.Kind.EVALUATIONS_PENDING,
                    pendingEvaluationCount + " evaluation" + (pendingEvaluationCount == 1 ? "" : "s") + " pending",
                    "Review submitted recitations and post feedback.",
                    now,
                    1
            );
            pending.setActionLabel("Review");
            pending.setActionHref("/instructor/evaluations");
            items.add(pending);
        }

        // 3. Most recent recitation submissions (rank 2)
        try {
            List<Recitation> recent = new ArrayList<>(instructorRecitations);
            recent.sort(Comparator.comparing(
                    Recitation::getSubmissionDate,
                    Comparator.nullsLast(Comparator.reverseOrder())
            ));
            int added = 0;
            Set<Long> seenEnrollments = new HashSet<>();
            for (Recitation r : recent) {
                if (added >= 4) {
                    break;
                }
                if (r == null || r.getSubmissionDate() == null) {
                    continue;
                }
                if (!seenEnrollments.add(r.getEnrollmentId())) {
                    continue;
                }
                String studentName = resolveStudentNameForRecitation(connection, r);
                InstructorActivityItem rec = new InstructorActivityItem(
                        InstructorActivityItem.Kind.RECITATION_SUBMITTED,
                        (studentName == null ? "A student" : studentName) + " submitted a recitation",
                        "Tap to listen and evaluate.",
                        r.getSubmissionDate(),
                        2
                );
                rec.setActionLabel("Listen");
                rec.setActionHref("/instructor/evaluations");
                items.add(rec);
                added++;
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to enrich activity feed with recitations", ex);
        }

        // 4. General notifications (rank 3)
        try {
            List<Notification> notifications = notificationDao.listByUserId(connection, instructorUserId);
            int added = 0;
            for (Notification n : notifications) {
                if (added >= 3 || n == null) {
                    continue;
                }
                InstructorActivityItem item = new InstructorActivityItem(
                        InstructorActivityItem.Kind.NOTIFICATION,
                        n.getMessage() == null ? "Notification" : n.getMessage(),
                        null,
                        n.getCreatedAt(),
                        3
                );
                items.add(item);
                added++;
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to load notifications for activity feed", ex);
        }

        items.sort(Comparator
                .comparingInt(InstructorActivityItem::getRank)
                .thenComparing(InstructorActivityItem::getOccurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder())));
        if (items.size() > 8) {
            return new ArrayList<>(items.subList(0, 8));
        }
        return items;
    }

    /**
     * True when the session's scheduled end-time is still in the future.
     *
     * <p>Sessions without a date or time (legacy/free-form drafts) are treated as
     * still-upcoming so we don't silently drop them. For sessions with both fields
     * we add a small grace window (60s) so a session that just-now ticked past its
     * end-time isn't flagged as stale on the very same heartbeat that would also
     * naturally promote it to COMPLETED.</p>
     */
    private static boolean isSessionStillUpcoming(TasmiSession session, ZoneId zone, Instant now) {
        if (session == null) {
            return false;
        }
        LocalDate date = session.getSessionDate();
        LocalTime time = session.getSessionTime();
        if (date == null || time == null) {
            return true;
        }
        int durationMinutes = session.getDurationMinutes() == null ? 60 : session.getDurationMinutes();
        Instant endAt = LocalDateTime.of(date, time)
                .atZone(zone)
                .toInstant()
                .plusSeconds((long) durationMinutes * 60L);
        return endAt.isAfter(now.minusSeconds(60));
    }

    private String resolveStudentNameForRecitation(Connection connection, Recitation recitation) throws SQLException {
        if (recitation == null) {
            return null;
        }
        Optional<Enrollment> enrollmentOpt = enrollmentDao.findById(connection, recitation.getEnrollmentId());
        if (enrollmentOpt.isEmpty()) {
            return null;
        }
        Optional<Student> studentOpt = studentDao.findById(connection, enrollmentOpt.get().getStudentId());
        if (studentOpt.isEmpty()) {
            return null;
        }
        Optional<User> userOpt = userDao.findById(connection, studentOpt.get().getUserId());
        return userOpt.map(User::getFullName).orElse(null);
    }

    public Map<Long, List<SessionParticipantView>> participantsBySession(long instructorUserId, List<TasmiSession> sessions) {
        Map<Long, List<SessionParticipantView>> map = new HashMap<>();
        if (sessions == null || sessions.isEmpty()) {
            return map;
        }

        try (Connection connection = Db.getConnection()) {
            Instructor instructor = requireApprovedInstructor(connection, instructorUserId);
            if (instructor == null) {
                return map;
            }

            for (TasmiSession session : sessions) {
                if (session == null || session.getInstructorId() != instructor.getInstructorId()) {
                    continue;
                }
                map.put(session.getSessionId(), listParticipants(connection, session.getSessionId()));
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load session participants", ex);
        }
        return map;
    }

    private Instructor requireApprovedInstructor(Connection connection, long instructorUserId) throws SQLException {
        Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, instructorUserId);
        if (instructorOpt.isEmpty()) {
            return null;
        }
        Instructor instructor = instructorOpt.get();
        if (instructor.getVerificationStatus() != InstructorVerificationStatus.APPROVED) {
            return null;
        }
        return instructor;
    }

    private List<SessionParticipantView> listParticipants(Connection connection, long sessionId) throws SQLException {
        List<Enrollment> enrollments = enrollmentDao.listBySessionId(connection, sessionId);
        Map<Long, Payment> paymentByEnrollment = loadPaymentsBySession(connection, sessionId);
        List<SessionParticipantView> participants = new ArrayList<>();
        for (Enrollment enrollment : enrollments) {
            if (enrollment == null || enrollment.getEnrollmentStatus() != model.entity.EnrollmentStatus.APPROVED) {
                continue;
            }
            Optional<Student> studentOpt = studentDao.findById(connection, enrollment.getStudentId());
            if (studentOpt.isEmpty()) {
                continue;
            }
            Optional<User> userOpt = userDao.findById(connection, studentOpt.get().getUserId());
            if (userOpt.isEmpty()) {
                continue;
            }

            SessionParticipantView view = new SessionParticipantView();
            view.setSessionId(sessionId);
            view.setEnrollmentId(enrollment.getEnrollmentId());
            view.setStudentId(studentOpt.get().getStudentId());
            view.setUserId(userOpt.get().getUserId());
            view.setFullName(userOpt.get().getFullName());
            view.setEmail(userOpt.get().getEmail());
            view.setPhone(userOpt.get().getPhone());
            view.setRegistrationNumber(studentOpt.get().getRegistrationNumber());
            view.setEnrollmentStatus(enrollment.getEnrollmentStatus());

            Payment payment = paymentByEnrollment.get(enrollment.getEnrollmentId());
            view.setPaymentStatus(payment == null ? null : payment.getPaymentStatus());

            participants.add(view);
        }
        return participants;
    }

    private Map<Long, Payment> loadPaymentsBySession(Connection connection, long sessionId) throws SQLException {
        Map<Long, Payment> map = new HashMap<>();
        String sql = "SELECT p.* FROM payment p JOIN enrollment e ON e.enrollment_id = p.enrollment_id WHERE e.session_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Payment payment = new Payment();
                    payment.setPaymentId(rs.getLong("payment_id"));
                    payment.setEnrollmentId(rs.getLong("enrollment_id"));
                    payment.setAmount(rs.getBigDecimal("amount"));
                    payment.setPaymentStatus(PaymentStatus.fromString(rs.getString("payment_status")));
                    map.put(payment.getEnrollmentId(), payment);
                }
            }
        }
        return map;
    }
}
