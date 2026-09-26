package model.service;

import model.dao.InstructorDao;
import model.dao.NotificationDao;
import model.dao.EnrollmentDao;
import model.dao.TasmiSessionDao;
import model.dao.impl.EnrollmentDaoJdbc;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.NotificationDaoJdbc;
import model.dao.impl.TasmiSessionDaoJdbc;
import model.entity.EnrollmentStatus;
import model.entity.Instructor;
import model.entity.InstructorVerificationStatus;
import model.entity.SessionMode;
import model.entity.SessionRecordingStatus;
import model.entity.StudentLevel;
import model.entity.TasmiSession;
import model.entity.TasmiSessionStatus;
import util.Db;
import util.ZoomApiClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TasmiSessionService {
    private static final Logger LOGGER = Logger.getLogger(TasmiSessionService.class.getName());
    private static final String LIVE_PROVIDER_MANUAL = "MANUAL";
    private static final String LIVE_PROVIDER_ZOOM = "ZOOM";

    private final InstructorDao instructorDao;
    private final TasmiSessionDao tasmiSessionDao;
    private final EnrollmentDao enrollmentDao;
    private final NotificationDao notificationDao;

    public TasmiSessionService() {
        this.instructorDao = new InstructorDaoJdbc();
        this.tasmiSessionDao = new TasmiSessionDaoJdbc();
        this.enrollmentDao = new EnrollmentDaoJdbc();
        this.notificationDao = new NotificationDaoJdbc();
    }

    public List<TasmiSession> listInstructorSessions(long instructorId) {
        if (instructorId <= 0) {
            return List.of();
        }
        try (Connection connection = Db.getConnection()) {
            autoCompleteExpiredOngoingSessions(connection, instructorId);
            return tasmiSessionDao.listByInstructorId(connection, instructorId);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to list instructor sessions", ex);
            return List.of();
        }
    }

    public List<TasmiSession> listScheduledSessions() {
        try (Connection connection = Db.getConnection()) {
            return tasmiSessionDao.listByStatus(connection, TasmiSessionStatus.SCHEDULED)
                    .stream()
                    .filter(s -> s != null && s.getMode() == SessionMode.ONLINE)
                    .toList();
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to list scheduled sessions", ex);
            return List.of();
        }
    }

    public List<TasmiSession> listStudentScheduledSessions() {
        try (Connection connection = Db.getConnection()) {
            LocalDate today = LocalDate.now(util.DateTimeFormats.appZone());
            List<TasmiSession> visibleSessions = new ArrayList<>();
            for (TasmiSession session : tasmiSessionDao.listByStatus(connection, TasmiSessionStatus.SCHEDULED)) {
                if (session == null || !isVisibleToStudents(session) || !isActiveApprovedInstructor(connection, session)) {
                    continue;
                }
                if (session.getSessionDate() != null && session.getSessionDate().isBefore(today)) {
                    continue;
                }
                visibleSessions.add(session);
            }

            return visibleSessions.stream()
                    .sorted(Comparator
                            .comparing(TasmiSession::getSessionDate, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(TasmiSession::getSessionTime, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(TasmiSession::getSessionId, Comparator.reverseOrder()))
                    .toList();
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to list student scheduled sessions", ex);
            return List.of();
        }
    }

    public List<TasmiSession> listStudentVisibleSessions() {
        try (Connection connection = Db.getConnection()) {
            List<TasmiSession> combined = new ArrayList<>();
            combined.addAll(tasmiSessionDao.listByStatus(connection, TasmiSessionStatus.ONGOING));
            combined.addAll(tasmiSessionDao.listByStatus(connection, TasmiSessionStatus.SCHEDULED));

            Map<Long, TasmiSession> uniqueById = new LinkedHashMap<>();
            for (TasmiSession session : combined) {
                if (session == null || !isVisibleToStudents(session) || !isActiveApprovedInstructor(connection, session)) {
                    continue;
                }
                uniqueById.put(session.getSessionId(), session);
            }

            return uniqueById.values()
                    .stream()
                    .sorted(Comparator
                            .comparingInt(this::studentVisibilityPriority)
                            .thenComparing(TasmiSession::getSessionDate, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(TasmiSession::getSessionTime, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(TasmiSession::getSessionId, Comparator.reverseOrder()))
                    .toList();
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to list student-visible sessions", ex);
            return List.of();
        }
    }

    public TasmiSessionCreateResult createSession(long instructorUserId,
                                                  String title,
                                                  String description,
                                                  StudentLevel level,
                                                  LocalDate date,
                                                  LocalTime time,
                                                  Integer durationMinutes,
                                                  String quranPortion,
                                                  BigDecimal fee,
                                                  int capacity) {
        if (level == null) {
            return TasmiSessionCreateResult.failure("Target student level is required.");
        }
        ValidationResult validation = validateSessionInput(title, date, time, durationMinutes, fee, capacity);
        if (!validation.valid) {
            return TasmiSessionCreateResult.failure(validation.message);
        }

        try (Connection connection = Db.getConnection()) {
            Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, instructorUserId);
            if (instructorOpt.isEmpty()) {
                return TasmiSessionCreateResult.failure("Instructor profile not found.");
            }

            Instructor instructor = instructorOpt.get();
            if (instructor.getVerificationStatus() != InstructorVerificationStatus.APPROVED) {
                return TasmiSessionCreateResult.failure("Your account is not approved yet. You cannot create sessions.");
            }

            if (!ZoomApiClient.isConfigured()) {
                return TasmiSessionCreateResult.failure("Zoom integration is not configured. Please contact the administrator.");
            }

            String hostEmail = resolveZoomHostEmail(instructor);
            if (hostEmail == null || hostEmail.isBlank()) {
                return TasmiSessionCreateResult.failure("No Zoom host email available. Please set your Zoom email in your profile, or contact the administrator.");
            }

            int duration = durationMinutes == null || durationMinutes <= 0 ? 60 : durationMinutes;
            ZoomMeetingInfo zoomInfo;
            try {
                ZoomApiClient zoomClient = new ZoomApiClient();
                zoomInfo = zoomClient.createMeeting(
                        hostEmail,
                        title.trim(),
                        date,
                        time,
                        duration,
                        ZoomApiClient.getTimezone()
                );
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Zoom API error during session creation", ex);
                return TasmiSessionCreateResult.failure("Failed to create Zoom meeting: " + ex.getMessage());
            }

            TasmiSession session = new TasmiSession();
            session.setInstructorId(instructor.getInstructorId());
            session.setTitle(title.trim());
            session.setDescription(normalizeText(description));
            session.setLevel(level);
            session.setSessionDate(date);
            session.setSessionTime(time);
            session.setDurationMinutes(duration);
            session.setQuranPortion(normalizeText(quranPortion));
            session.setMode(SessionMode.ONLINE);
            session.setFee(fee == null ? BigDecimal.ZERO : fee);
            session.setCapacity(capacity);
            clearLegacyMeetingDetails(session);
            session.setMeetingLink(zoomInfo.getJoinUrl());
            session.setMeetingPassword(zoomInfo.getPassword());
            session.setPasswordVisible(false);
            session.setLiveProvider(LIVE_PROVIDER_ZOOM);
            session.setZoomMeetingId(zoomInfo.getMeetingId());
            session.setZoomStartUrl(zoomInfo.getStartUrl());
            session.setStatus(TasmiSessionStatus.SCHEDULED);

            long sessionId = tasmiSessionDao.insert(connection, session);
            return TasmiSessionCreateResult.success(sessionId);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to create tasmi session", ex);
            return TasmiSessionCreateResult.failure(dbErrorToUserMessage(ex));
        }
    }

    public ServiceResult updateSession(long instructorUserId,
                                       long sessionId,
                                       String title,
                                       String description,
                                       StudentLevel level,
                                       LocalDate date,
                                       LocalTime time,
                                       Integer durationMinutes,
                                       String quranPortion,
                                       BigDecimal fee,
                                       int capacity) {
        if (sessionId <= 0) {
            return ServiceResult.fail("Invalid session.");
        }
        if (level == null) {
            return ServiceResult.fail("Target student level is required.");
        }
        ValidationResult validation = validateSessionInput(title, date, time, durationMinutes, fee, capacity);
        if (!validation.valid) {
            return ServiceResult.fail(validation.message);
        }

        try (Connection connection = Db.getConnection()) {
            Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, instructorUserId);
            if (instructorOpt.isEmpty()) {
                return ServiceResult.fail("Instructor profile not found.");
            }

            Instructor instructor = instructorOpt.get();
            if (instructor.getVerificationStatus() != InstructorVerificationStatus.APPROVED) {
                return ServiceResult.fail("Your account is not approved yet. You cannot update sessions.");
            }

            Optional<TasmiSession> existingOpt = tasmiSessionDao.findById(connection, sessionId);
            if (existingOpt.isEmpty() || existingOpt.get().getInstructorId() != instructor.getInstructorId()) {
                return ServiceResult.fail("Session not found for your account.");
            }

            TasmiSession session = existingOpt.get();
            if (session.getStatus() != TasmiSessionStatus.SCHEDULED) {
                return ServiceResult.fail("Only scheduled sessions can be edited. Complete or reschedule the live class instead.");
            }

            int duration = durationMinutes == null || durationMinutes <= 0 ? 60 : durationMinutes;

            if (session.getZoomMeetingId() != null && ZoomApiClient.isConfigured()) {
                try {
                    ZoomApiClient zoomClient = new ZoomApiClient();
                    zoomClient.updateMeeting(
                            session.getZoomMeetingId(),
                            title.trim(),
                            date,
                            time,
                            duration,
                            ZoomApiClient.getTimezone()
                    );
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "Failed to update Zoom meeting " + session.getZoomMeetingId(), ex);
                    return ServiceResult.fail("Failed to update Zoom meeting: " + ex.getMessage());
                }
            }

            session.setTitle(title.trim());
            session.setDescription(normalizeText(description));
            session.setLevel(level);
            session.setSessionDate(date);
            session.setSessionTime(time);
            session.setDurationMinutes(duration);
            session.setQuranPortion(normalizeText(quranPortion));
            session.setMode(SessionMode.ONLINE);
            session.setFee(fee == null ? BigDecimal.ZERO : fee);
            session.setCapacity(capacity);
            session.setStatus(TasmiSessionStatus.SCHEDULED);
            session.setLiveStartedAt(null);
            session.setLiveEndedAt(null);
            session.setRecordingStatus(null);
            session.setRecordingUrl(null);
            session.setRecordingSyncedAt(null);

            boolean ok = tasmiSessionDao.updateByIdAndInstructorId(connection, session);
            if (!ok) {
                return ServiceResult.fail("Session update failed.");
            }

            notifyEnrolledStudents(connection, sessionId, "A Tasmi session you are enrolled in has been updated. Please check the latest session details.");
            return ServiceResult.ok();
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to update tasmi session", ex);
            return ServiceResult.fail(dbErrorToUserMessage(ex));
        }
    }

    public void updateBannerImage(long instructorUserId, long sessionId, String bannerUrl) {
        if (instructorUserId <= 0 || sessionId <= 0 || bannerUrl == null) return;
        try (Connection connection = Db.getConnection()) {
            Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, instructorUserId);
            if (instructorOpt.isEmpty()) return;
            Instructor instructor = instructorOpt.get();
            Optional<TasmiSession> sessionOpt = tasmiSessionDao.findById(connection, sessionId);
            if (sessionOpt.isEmpty() || sessionOpt.get().getInstructorId() != instructor.getInstructorId()) return;
            TasmiSession session = sessionOpt.get();
            session.setBannerImageUrl(bannerUrl);
            tasmiSessionDao.updateByIdAndInstructorId(connection, session);
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to update session banner image", ex);
        }
    }

    public ServiceResult deleteSession(long instructorUserId, long sessionId) throws SQLException {
        if (sessionId <= 0) {
            return ServiceResult.fail("Invalid session.");
        }
        try (Connection connection = Db.getConnection()) {
            connection.setAutoCommit(false);
            Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, instructorUserId);
            if (instructorOpt.isEmpty()) {
                connection.rollback();
                return ServiceResult.fail("Instructor profile not found.");
            }
            Instructor instructor = instructorOpt.get();
            if (instructor.getVerificationStatus() != InstructorVerificationStatus.APPROVED) {
                connection.rollback();
                return ServiceResult.fail("Your account is not approved yet. You cannot delete sessions.");
            }

            Optional<TasmiSession> existingOpt = tasmiSessionDao.findById(connection, sessionId);
            if (existingOpt.isEmpty() || existingOpt.get().getInstructorId() != instructor.getInstructorId()) {
                connection.rollback();
                return ServiceResult.fail("Session not found for your account.");
            }

            TasmiSession existing = existingOpt.get();
            if (existing.getZoomMeetingId() != null && ZoomApiClient.isConfigured()) {
                try {
                    new ZoomApiClient().deleteMeeting(existing.getZoomMeetingId());
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "Best-effort Zoom meeting deletion failed for " + existing.getZoomMeetingId(), ex);
                }
            }

            notifyEnrolledStudents(connection, sessionId, "A Tasmi session you were enrolled in has been cancelled by the instructor.");
            boolean ok = tasmiSessionDao.deleteByIdAndInstructorId(connection, sessionId, instructor.getInstructorId());
            if (!ok) {
                connection.rollback();
                return ServiceResult.fail("Session delete failed.");
            }
            connection.commit();
            return ServiceResult.ok();
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to delete tasmi session", ex);
            return ServiceResult.fail(dbErrorToUserMessage(ex));
        }
    }

    public ServiceResult startSession(long instructorUserId, long sessionId) {
        if (instructorUserId <= 0 || sessionId <= 0) {
            return ServiceResult.failure("Invalid request.");
        }

        try (Connection connection = Db.getConnection()) {
            connection.setAutoCommit(false);
            Instructor instructor = requireApprovedInstructor(connection, instructorUserId);
            if (instructor == null) {
                connection.rollback();
                return ServiceResult.failure("Instructor profile not found or not approved.");
            }

            Optional<TasmiSession> sessionOpt = tasmiSessionDao.findById(connection, sessionId);
            if (sessionOpt.isEmpty()) {
                connection.rollback();
                return ServiceResult.failure("Session not found.");
            }

            TasmiSession session = sessionOpt.get();
            if (session.getInstructorId() != instructor.getInstructorId()) {
                connection.rollback();
                return ServiceResult.failure("You do not have access to this session.");
            }
            if (session.getMode() != SessionMode.ONLINE) {
                connection.rollback();
                return ServiceResult.failure("Only live online Tasmi sessions can be started.");
            }
            if (session.getStatus() != TasmiSessionStatus.SCHEDULED) {
                connection.rollback();
                return ServiceResult.failure("Only scheduled sessions can be started.");
            }
            if (!autoCompleteOtherOngoingSessions(connection, instructor.getInstructorId(), sessionId)) {
                connection.rollback();
                return ServiceResult.failure("Unable to close the previous live session. Please try again.");
            }
            int approvedParticipants = countApprovedParticipants(connection, sessionId);
            if (approvedParticipants <= 0) {
                connection.rollback();
                return ServiceResult.failure("At least one approved student must be registered before you can start this live session.");
            }

            if (!hasUsableLiveMeetingBundle(session)) {
                connection.rollback();
                return ServiceResult.failure("This session does not have a valid meeting link yet.");
            }

            if (session.getZoomMeetingId() != null && ZoomApiClient.isConfigured()) {
                try {
                    ZoomMeetingInfo freshInfo = new ZoomApiClient().getMeeting(session.getZoomMeetingId());
                    if (freshInfo.getStartUrl() != null) {
                        session.setZoomStartUrl(freshInfo.getStartUrl());
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, "Failed to refresh Zoom start_url for meeting " + session.getZoomMeetingId(), ex);
                }
            }

            session.setStatus(TasmiSessionStatus.ONGOING);
            session.setLiveStartedAt(Instant.now());
            session.setLiveEndedAt(null);
            session.setRecordingUrl(null);
            session.setRecordingSyncedAt(null);
            session.setRecordingStatus(SessionRecordingStatus.UNAVAILABLE);
            boolean updated = tasmiSessionDao.updateLiveDetailsAndStatusByIdAndInstructorId(connection, session.getSessionId(), session.getInstructorId(), session);
            if (!updated) {
                connection.rollback();
                return ServiceResult.failure("Failed to start session.");
            }

            notifyStudentsByEnrollmentStatus(
                    connection,
                    sessionId,
                    "Your Tasmi session is now live. You can join from your enrollments page.",
                    EnrollmentStatus.APPROVED
            );
            connection.commit();
            return ServiceResult.success("Session started.");
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to start session", ex);
            return ServiceResult.failure(dbErrorToUserMessage(ex));
        }
    }

    public ServiceResult setPasswordVisibility(long instructorUserId, long sessionId, boolean visible) {
        if (instructorUserId <= 0 || sessionId <= 0) {
            return ServiceResult.failure("Invalid request.");
        }

        try (Connection connection = Db.getConnection()) {
            connection.setAutoCommit(false);
            Instructor instructor = requireApprovedInstructor(connection, instructorUserId);
            if (instructor == null) {
                connection.rollback();
                return ServiceResult.failure("Instructor profile not found or not approved.");
            }

            Optional<TasmiSession> sessionOpt = tasmiSessionDao.findById(connection, sessionId);
            if (sessionOpt.isEmpty()) {
                connection.rollback();
                return ServiceResult.failure("Session not found.");
            }

            TasmiSession session = sessionOpt.get();
            if (session.getInstructorId() != instructor.getInstructorId()) {
                connection.rollback();
                return ServiceResult.failure("You do not have access to this session.");
            }
            if (session.getStatus() != TasmiSessionStatus.ONGOING
                    && session.getStatus() != TasmiSessionStatus.SCHEDULED) {
                connection.rollback();
                return ServiceResult.failure("Password visibility can only be changed for scheduled or ongoing sessions.");
            }
            if (!hasUsableLiveMeetingBundle(session)) {
                connection.rollback();
                return ServiceResult.failure("This session does not have a valid meeting link yet.");
            }

            session.setPasswordVisible(visible);
            boolean updated = tasmiSessionDao.updateLiveDetailsAndStatusByIdAndInstructorId(connection, session.getSessionId(), session.getInstructorId(), session);
            if (!updated) {
                connection.rollback();
                return ServiceResult.failure("Unable to update password visibility.");
            }

            connection.commit();
            return ServiceResult.success(visible ? "Meeting password is now visible to paid students." : "Meeting password is now hidden from students.");
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to update meeting password visibility", ex);
            return ServiceResult.failure(dbErrorToUserMessage(ex));
        }
    }

    public ServiceResult completeSession(long instructorUserId, long sessionId) {
        if (instructorUserId <= 0 || sessionId <= 0) {
            return ServiceResult.failure("Invalid request.");
        }

        try (Connection connection = Db.getConnection()) {
            connection.setAutoCommit(false);
            Instructor instructor = requireApprovedInstructor(connection, instructorUserId);
            if (instructor == null) {
                connection.rollback();
                return ServiceResult.failure("Instructor profile not found or not approved.");
            }

            Optional<TasmiSession> sessionOpt = tasmiSessionDao.findById(connection, sessionId);
            if (sessionOpt.isEmpty()) {
                connection.rollback();
                return ServiceResult.failure("Session not found.");
            }

            TasmiSession session = sessionOpt.get();
            if (session.getInstructorId() != instructor.getInstructorId()) {
                connection.rollback();
                return ServiceResult.failure("You do not have access to this session.");
            }
            if (session.getMode() != SessionMode.ONLINE) {
                connection.rollback();
                return ServiceResult.failure("Only live online Tasmi sessions can be completed here.");
            }
            if (session.getStatus() == TasmiSessionStatus.COMPLETED) {
                connection.rollback();
                return ServiceResult.failure("Session is already completed.");
            }
            if (session.getStatus() == TasmiSessionStatus.CANCELLED) {
                connection.rollback();
                return ServiceResult.failure("Cancelled sessions cannot be completed.");
            }
            if (session.getStatus() != TasmiSessionStatus.ONGOING) {
                connection.rollback();
                return ServiceResult.failure("Only active live sessions can be completed.");
            }

            if (!markSessionCompleted(connection, session, Instant.now())) {
                connection.rollback();
                return ServiceResult.failure("Failed to complete session.");
            }

            notifyStudentsByEnrollmentStatus(
                    connection,
                    sessionId,
                    "Your Tasmi session has been completed. Check for evaluations and feedback.",
                    EnrollmentStatus.APPROVED
            );
            connection.commit();
            return ServiceResult.success("Session completed.");
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to complete session", ex);
            return ServiceResult.failure(dbErrorToUserMessage(ex));
        }
    }

    private String resolveZoomHostEmail(Instructor instructor) {
        String instructorZoom = instructor.getZoomEmail();
        if (instructorZoom != null && !instructorZoom.isBlank()) {
            return instructorZoom.trim();
        }
        String defaultEmail = ZoomApiClient.getDefaultHostEmail();
        return (defaultEmail != null && !defaultEmail.isBlank()) ? defaultEmail.trim() : null;
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

    private int countApprovedParticipants(Connection connection, long sessionId) throws SQLException {
        return (int) enrollmentDao.listBySessionId(connection, sessionId)
                .stream()
                .filter(enrollment -> enrollment != null && enrollment.getEnrollmentStatus() == EnrollmentStatus.APPROVED)
                .count();
    }

    private void updateRecordingDetailsAfterCompletion(TasmiSession session) {
        if (session == null) {
            return;
        }

        session.setRecordingUrl(null);
        session.setRecordingSyncedAt(Instant.now());

        session.setRecordingStatus(SessionRecordingStatus.UNAVAILABLE);
    }

    private boolean hasUsableLiveMeetingBundle(TasmiSession session) {
        if (session == null) {
            return false;
        }
        String provider = normalizeText(session.getLiveProvider());
        String meetingLink = normalizeText(session.getMeetingLink());
        boolean validProvider = LIVE_PROVIDER_MANUAL.equalsIgnoreCase(provider)
                || LIVE_PROVIDER_ZOOM.equalsIgnoreCase(provider);
        return validProvider && isValidMeetingLink(meetingLink);
    }

    private boolean hasParticipantJoinBundle(TasmiSession session) {
        if (session == null) {
            return false;
        }
        return hasUsableLiveMeetingBundle(session);
    }

    private void clearLegacyMeetingDetails(TasmiSession session) {
        if (session == null) {
            return;
        }
        session.setMeetingLink(null);
        session.setMeetingPassword(null);
        session.setPasswordVisible(false);
        session.setLiveStartedAt(null);
        session.setLiveEndedAt(null);
        session.setRecordingStatus(null);
        session.setRecordingUrl(null);
        session.setRecordingSyncedAt(null);
    }

    private boolean hasInstructorHostMechanism(TasmiSession session) {
        return session != null
                && hasUsableLiveMeetingBundle(session)
                && isValidMeetingLink(session.getMeetingLink());
    }

    private boolean isVisibleToStudents(TasmiSession session) {
        if (session == null || session.getMode() != SessionMode.ONLINE) {
            return false;
        }
        if (session.getStatus() == TasmiSessionStatus.SCHEDULED) {
            return true;
        }
        return session.getStatus() == TasmiSessionStatus.ONGOING
                && hasParticipantJoinBundle(session);
    }

    private boolean isActiveApprovedInstructor(Connection connection, TasmiSession session) throws SQLException {
        if (connection == null || session == null || session.getInstructorId() <= 0) {
            return false;
        }

        String sql = "SELECT i.verification_status, u.status, u.is_active "
                + "FROM instructor i "
                + "JOIN `user` u ON u.user_id = i.user_id "
                + "WHERE i.instructor_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, session.getInstructorId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                String verificationStatus = normalizeText(rs.getString("verification_status"));
                String userStatus = normalizeText(rs.getString("status"));
                boolean active = rs.getBoolean("is_active");
                return active
                        && "APPROVED".equalsIgnoreCase(verificationStatus)
                        && !"DELETED".equalsIgnoreCase(userStatus);
            }
        }
    }

    private int studentVisibilityPriority(TasmiSession session) {
        if (session == null || session.getStatus() == null) {
            return 99;
        }
        if (session.getStatus() == TasmiSessionStatus.ONGOING) {
            return 0;
        }
        if (session.getStatus() == TasmiSessionStatus.SCHEDULED) {
            return 1;
        }
        return 99;
    }

    private boolean isValidMeetingLink(String meetingLink) {
        String normalized = normalizeText(meetingLink);
        if (normalized == null) {
            return false;
        }
        String lower = normalized.toLowerCase();
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    private void autoCompleteExpiredOngoingSessions(Connection connection, long instructorId) throws SQLException {
        if (connection == null || instructorId <= 0) {
            return;
        }
        Instant now = Instant.now();
        for (TasmiSession session : tasmiSessionDao.listByInstructorId(connection, instructorId)) {
            if (session == null || session.getStatus() != TasmiSessionStatus.ONGOING) {
                continue;
            }
            if (!hasPlannedLiveWindowEnded(session, now)) {
                continue;
            }
            if (markSessionCompleted(connection, session, now)) {
                notifyStudentsByEnrollmentStatus(
                        connection,
                        session.getSessionId(),
                        "Your Tasmi session has been completed. Check for evaluations and feedback.",
                        EnrollmentStatus.APPROVED
                );
            }
        }
    }

    private boolean autoCompleteOtherOngoingSessions(Connection connection, long instructorId, long currentSessionId) throws SQLException {
        List<TasmiSession> sessions = tasmiSessionDao.listByInstructorId(connection, instructorId);
        for (TasmiSession session : sessions) {
            if (session == null || session.getSessionId() == currentSessionId) {
                continue;
            }
            if (session.getStatus() == TasmiSessionStatus.ONGOING) {
                if (!markSessionCompleted(connection, session, Instant.now())) {
                    return false;
                }
                notifyStudentsByEnrollmentStatus(
                        connection,
                        session.getSessionId(),
                        "Your Tasmi session has been completed. Check for evaluations and feedback.",
                        EnrollmentStatus.APPROVED
                );
            }
        }
        return true;
    }

    private boolean hasPlannedLiveWindowEnded(TasmiSession session, Instant now) {
        if (session == null || session.getLiveStartedAt() == null || now == null) {
            return false;
        }
        int durationMinutes = session.getDurationMinutes() == null || session.getDurationMinutes() <= 0
                ? 60
                : session.getDurationMinutes();
        Instant plannedEnd = session.getLiveStartedAt().plus(Duration.ofMinutes(durationMinutes));
        return !plannedEnd.isAfter(now);
    }

    private boolean markSessionCompleted(Connection connection, TasmiSession session, Instant endedAt) throws SQLException {
        if (session == null) {
            return false;
        }
        session.setStatus(TasmiSessionStatus.COMPLETED);
        session.setLiveEndedAt(endedAt == null ? Instant.now() : endedAt);
        updateRecordingDetailsAfterCompletion(session);
        return tasmiSessionDao.updateLiveDetailsAndStatusByIdAndInstructorId(
                connection,
                session.getSessionId(),
                session.getInstructorId(),
                session
        );
    }

    private void notifyEnrolledStudents(Connection connection, long sessionId, String message) {
        notifyStudentsByEnrollmentStatus(connection, sessionId, message, EnrollmentStatus.APPROVED, EnrollmentStatus.PENDING);
    }

    private void notifyStudentsByEnrollmentStatus(Connection connection,
                                                  long sessionId,
                                                  String message,
                                                  EnrollmentStatus... statuses) {
        try {
            List<String> normalizedStatuses = new ArrayList<>();
            if (statuses != null) {
                for (EnrollmentStatus status : statuses) {
                    if (status != null) {
                        normalizedStatuses.add(status.name());
                    }
                }
            }
            if (normalizedStatuses.isEmpty()) {
                normalizedStatuses.add(EnrollmentStatus.APPROVED.name());
            }

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT st.user_id ")
                    .append("FROM enrollment e ")
                    .append("JOIN student st ON st.student_id = e.student_id ")
                    .append("WHERE e.session_id = ? AND e.enrollment_status IN (");
            for (int i = 0; i < normalizedStatuses.size(); i++) {
                if (i > 0) {
                    sql.append(',');
                }
                sql.append('?');
            }
            sql.append(')');

            try (PreparedStatement ps = connection.prepareStatement(sql.toString())) {
                ps.setLong(1, sessionId);
                for (int i = 0; i < normalizedStatuses.size(); i++) {
                    ps.setString(i + 2, normalizedStatuses.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long userId = rs.getLong("user_id");
                        model.entity.Notification n = new model.entity.Notification();
                        n.setUserId(userId);
                        n.setMessage(message);
                        n.setCreatedAt(java.time.Instant.now());
                        notificationDao.insert(connection, n);
                    }
                }
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to notify enrolled students for session " + sessionId, ex);
        }
    }

    private ValidationResult validateSessionInput(String title,
                                                  LocalDate date,
                                                  LocalTime time,
                                                  Integer durationMinutes,
                                                  BigDecimal fee,
                                                  int capacity) {
        if (title == null || title.isBlank() || date == null || time == null) {
            return ValidationResult.fail("Please fill all required session details.");
        }
        if (capacity <= 0) {
            return ValidationResult.fail("Capacity must be greater than zero.");
        }
        if (durationMinutes != null && durationMinutes <= 0) {
            return ValidationResult.fail("Duration must be greater than zero.");
        }
        if (fee != null && fee.compareTo(BigDecimal.ZERO) < 0) {
            return ValidationResult.fail("Fee cannot be negative.");
        }
        return ValidationResult.ok();
    }

    private String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String dbErrorToUserMessage(SQLException ex) {
        String message = ex == null || ex.getMessage() == null ? "" : ex.getMessage();
        String lower = message.toLowerCase();

        if (lower.contains("unknown column")) {
            return "Database schema mismatch (unknown column). " + message;
        }
        if (lower.contains("doesn't exist") && lower.contains("tasmi_session")) {
            return "Database table tasmi_session not found. " + message;
        }
        if (lower.contains("foreign key constraint fails")) {
            return "This session cannot be deleted because it is linked to enrollments or related records. " + message;
        }
        if (lower.contains("data truncated")) {
            return "Database rejected an invalid session value. " + message;
        }
        if (lower.contains("cannot be null")) {
            return "Database rejected a missing required value. " + message;
        }

        return "Database error while saving session. " + message;
    }

    private static class ValidationResult {
        final boolean valid;
        final String message;

        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        static ValidationResult fail(String message) {
            return new ValidationResult(false, message);
        }
    }
}
