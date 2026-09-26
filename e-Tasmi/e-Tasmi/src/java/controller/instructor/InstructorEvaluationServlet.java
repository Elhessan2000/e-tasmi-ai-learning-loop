package controller.instructor;

import model.dao.EvaluationDao;
import model.dao.EnrollmentDao;
import model.dao.InstructorDao;
import model.dao.StudentDao;
import model.dao.TasmiSessionDao;
import model.dao.UserDao;
import model.dao.impl.EvaluationDaoJdbc;
import model.dao.impl.EnrollmentDaoJdbc;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.StudentDaoJdbc;
import model.dao.impl.TasmiSessionDaoJdbc;
import model.dao.impl.UserDaoJdbc;
import model.entity.Enrollment;
import model.entity.EnrollmentStatus;
import model.entity.Evaluation;
import model.entity.Instructor;
import model.entity.Recitation;
import model.entity.Student;
import model.entity.TasmiSession;
import model.entity.User;
import model.service.EvaluationResult;
import model.service.EvaluationService;
import model.service.RecitationAiAnalysisService;
import util.Db;
import util.LocalFileUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "InstructorEvaluationServlet", urlPatterns = {"/instructor/evaluations"})
public class InstructorEvaluationServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(InstructorEvaluationServlet.class.getName());

    /** HTTP session attribute key for the latest AI analysis result per recitation (shown after Analyze). */
    public static String aiAnalysisSessionKey(long recitationId) {
        return "etasmi.aiAnalysis." + recitationId;
    }

    private final EvaluationService evaluationService = new EvaluationService();
    private final EvaluationDao evaluationDao = new EvaluationDaoJdbc();
    private final EnrollmentDao enrollmentDao = new EnrollmentDaoJdbc();
    private final InstructorDao instructorDao = new InstructorDaoJdbc();
    private final StudentDao studentDao = new StudentDaoJdbc();
    private final TasmiSessionDao tasmiSessionDao = new TasmiSessionDaoJdbc();
    private final UserDao userDao = new UserDaoJdbc();
    private final RecitationAiAnalysisService recitationAiAnalysisService = new RecitationAiAnalysisService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = readUserId(session);

        // recitationRows still drives the per-recitation evaluation/AI forms in
        // the drawer. It is the union of every recitation owned by this instructor
        // and is independent of the active/reviewed split.
        List<Map<String, Object>> rows = loadRecitationRows(userId);
        request.setAttribute("recitationRows", rows);

        // Load every session this instructor owns (including ones with zero
        // submissions) and bucket them into Active / Reviewed.
        List<Map<String, Object>> allGroups = buildSessionGroupsForAllSessions(userId, rows);
        List<Map<String, Object>> activeGroups = new ArrayList<>();
        List<Map<String, Object>> reviewedGroups = new ArrayList<>();
        for (Map<String, Object> group : allGroups) {
            Object reviewedAt = group.get("evaluationReviewedAt");
            if (reviewedAt != null) {
                reviewedGroups.add(group);
            } else {
                activeGroups.add(group);
            }
        }

        request.setAttribute("activeSessionGroups", activeGroups);
        request.setAttribute("reviewedSessionGroups", reviewedGroups);
        // Preserved for any consumers that still read the legacy attribute name
        // (e.g. AI analyze auto-open). The union is non-empty whenever either
        // tab is non-empty, which is what they care about.
        request.setAttribute("sessionGroups", allGroups);

        // Flash messages from POST redirects.
        String saved = request.getParameter("saved");
        if ("1".equals(saved)) {
            request.setAttribute("success", "Evaluation saved successfully.");
        }
        String reviewedFlash = request.getParameter("reviewed");
        if ("1".equals(reviewedFlash)) {
            request.setAttribute("success", "Session moved to Reviewed Sessions.");
        } else if ("0".equals(reviewedFlash)) {
            request.setAttribute("success", "Session reopened for review.");
        }

        request.getRequestDispatcher("/jsp/instructor/evaluations.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = readUserId(session);
        String action = trimToNull(request.getParameter("action"));

        // Evaluation Review workflow — mark a whole session as reviewed
        // (or reopen one). These actions live alongside the existing
        // analyze/save action handlers.
        if ("mark_reviewed".equalsIgnoreCase(action) || "reopen".equalsIgnoreCase(action)) {
            handleSessionReviewState(request, response, userId, action);
            return;
        }

        long recitationId = 0;
        try {
            recitationId = Long.parseLong(request.getParameter("recitationId"));
        } catch (Exception ignored) {
        }

        if ("analyze".equalsIgnoreCase(action)) {
            handleAnalyze(request, response, userId, recitationId);
            return;
        }

        int score = -1;
        try {
            score = Integer.parseInt(request.getParameter("score"));
        } catch (Exception ignored) {
        }

        String feedback = request.getParameter("feedback");

        HttpSession httpSession = request.getSession(false);
        EvaluationResult result = evaluationService.evaluate(userId, recitationId, score, feedback);
        if (result.isSuccess()) {
            if (httpSession != null) {
                httpSession.removeAttribute(aiAnalysisSessionKey(recitationId));
            }
            response.sendRedirect(request.getContextPath() + "/instructor/evaluations?saved=1");
            return;
        }

        request.setAttribute("error", result.getError());
        forwardWithFreshGroups(request, response, userId);
    }

    /**
     * Handles {@code action=mark_reviewed} (move session to Reviewed Sessions)
     * and {@code action=reopen} (move it back to Active). Both actions take
     * {@code sessionId} as input and redirect on success so the dashboard
     * shows the session in its new tab.
     */
    private void handleSessionReviewState(HttpServletRequest request,
                                          HttpServletResponse response,
                                          long userId,
                                          String action) throws ServletException, IOException {
        long sessionId = 0;
        try {
            sessionId = Long.parseLong(request.getParameter("sessionId"));
        } catch (Exception ignored) {
        }

        boolean reopening = "reopen".equalsIgnoreCase(action);
        EvaluationResult result = reopening
                ? evaluationService.reopenSessionReview(userId, sessionId)
                : evaluationService.markSessionReviewed(userId, sessionId);

        if (result.isSuccess()) {
            // ?reviewed=1 -> "marked reviewed" toast,
            // ?reviewed=0 -> "reopened" toast. Keep ?session=ID so the JSP
            // can re-open the same roster the instructor was looking at.
            String redirect = request.getContextPath()
                    + "/instructor/evaluations?reviewed=" + (reopening ? "0" : "1")
                    + "&session=" + sessionId;
            response.sendRedirect(redirect);
            return;
        }

        request.setAttribute("error", result.getError());
        forwardWithFreshGroups(request, response, userId);
    }

    /**
     * Re-renders the dashboard with a fresh dataset after a failed POST. Keeps
     * doPost branches readable.
     */
    private void forwardWithFreshGroups(HttpServletRequest request,
                                        HttpServletResponse response,
                                        long userId) throws ServletException, IOException {
        List<Map<String, Object>> rows = loadRecitationRows(userId);
        List<Map<String, Object>> allGroups = buildSessionGroupsForAllSessions(userId, rows);
        List<Map<String, Object>> activeGroups = new ArrayList<>();
        List<Map<String, Object>> reviewedGroups = new ArrayList<>();
        for (Map<String, Object> group : allGroups) {
            if (group.get("evaluationReviewedAt") != null) {
                reviewedGroups.add(group);
            } else {
                activeGroups.add(group);
            }
        }
        request.setAttribute("recitationRows", rows);
        request.setAttribute("activeSessionGroups", activeGroups);
        request.setAttribute("reviewedSessionGroups", reviewedGroups);
        request.setAttribute("sessionGroups", allGroups);
        request.getRequestDispatcher("/jsp/instructor/evaluations.jsp").forward(request, response);
    }

    private void handleAnalyze(HttpServletRequest request, HttpServletResponse response, long userId, long recitationId) throws ServletException, IOException {
        if (recitationId <= 0) {
            request.setAttribute("error", "Invalid recitation selected for AI analysis.");
            forwardWithFreshGroups(request, response, userId);
            return;
        }

        List<Map<String, Object>> rows = loadRecitationRows(userId);
        Recitation target = null;
        String expectedText = null;
        for (Map<String, Object> row : rows) {
            Recitation recitation = row == null ? null : (Recitation) row.get("recitation");
            if (recitation != null && recitation.getRecitationId() == recitationId) {
                target = recitation;
                Object portion = row.get("quranPortion");
                expectedText = portion == null ? null : trimToNull(String.valueOf(portion));
                break;
            }
        }

        if (target == null) {
            request.setAttribute("error", "You cannot analyze this recitation.");
            forwardWithFreshGroups(request, response, userId);
            return;
        }

        if (expectedText == null) {
            request.setAttribute("error", "This session has no expected Quran portion defined. Update the session before running AI analysis.");
            forwardWithFreshGroups(request, response, userId);
            return;
        }

        byte[] mediaBytes = readRecitationMedia(target.getAudioFilePath());
        if (mediaBytes == null || mediaBytes.length == 0) {
            request.setAttribute("error", "Recitation media is unavailable for AI analysis. Use a valid upload URL or local /uploads file.");
            forwardWithFreshGroups(request, response, userId);
            return;
        }

        String fileName = extractFileName(target.getAudioFilePath(), target.getRecitationId());
        RecitationAiAnalysisService.AnalysisResult analysisResult =
                recitationAiAnalysisService.analyze(expectedText, mediaBytes, fileName);

        HttpSession analyzeSession = request.getSession(true);
        analyzeSession.setAttribute(aiAnalysisSessionKey(recitationId), analysisResult);

        RecitationAiAnalysisService.Status status = analysisResult.getStatus();
        if (status == RecitationAiAnalysisService.Status.OK) {
            request.setAttribute("success", "AI analysis generated. Review the report before confirming your final score.");
        } else if (status == RecitationAiAnalysisService.Status.CANNOT_EVALUATE
                || status == RecitationAiAnalysisService.Status.FAILED) {
            // REJECTED already renders a dedicated status card in the JSP, so skip the top banner there.
            request.setAttribute("error", analysisResult.getError());
        }

        Map<Long, RecitationAiAnalysisService.AnalysisResult> analysisByRecitationId = new HashMap<>();
        analysisByRecitationId.put(recitationId, analysisResult);
        request.setAttribute("analysisByRecitationId", analysisByRecitationId);

        Map<Long, String> expectedTextByRecitationId = new HashMap<>();
        expectedTextByRecitationId.put(recitationId, expectedText);
        request.setAttribute("expectedTextByRecitationId", expectedTextByRecitationId);

        // Reuse the helper but with the same recitationRows we already loaded.
        List<Map<String, Object>> allGroups = buildSessionGroupsForAllSessions(userId, rows);
        List<Map<String, Object>> activeGroups = new ArrayList<>();
        List<Map<String, Object>> reviewedGroups = new ArrayList<>();
        for (Map<String, Object> group : allGroups) {
            if (group.get("evaluationReviewedAt") != null) {
                reviewedGroups.add(group);
            } else {
                activeGroups.add(group);
            }
        }
        request.setAttribute("recitationRows", rows);
        request.setAttribute("activeSessionGroups", activeGroups);
        request.setAttribute("reviewedSessionGroups", reviewedGroups);
        request.setAttribute("sessionGroups", allGroups);
        request.getRequestDispatcher("/jsp/instructor/evaluations.jsp").forward(request, response);
    }

    private List<Map<String, Object>> loadRecitationRows(long instructorUserId) {
        List<Recitation> recitations = evaluationService.listRecitationsForInstructor(instructorUserId);
        if (recitations == null || recitations.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = Db.getConnection()) {
            Map<Long, Enrollment> enrollmentCache = new HashMap<>();
            Map<Long, Student> studentCache = new HashMap<>();
            Map<Long, User> userCache = new HashMap<>();
            Map<Long, TasmiSession> sessionCache = new HashMap<>();

            for (Recitation recitation : recitations) {
                if (recitation == null) {
                    continue;
                }

                Enrollment enrollment = loadEnrollment(connection, enrollmentCache, recitation.getEnrollmentId());
                Student student = enrollment == null ? null : loadStudent(connection, studentCache, enrollment.getStudentId());
                User user = student == null ? null : loadUser(connection, userCache, student.getUserId());
                TasmiSession tasmiSession = enrollment == null ? null : loadSession(connection, sessionCache, enrollment.getSessionId());
                Evaluation evaluation = evaluationDao.findByRecitationId(connection, recitation.getRecitationId()).orElse(null);

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("recitation", recitation);
                row.put("evaluation", evaluation);
                row.put("studentName", displayStudentName(user));
                row.put("studentIdentifier", displayStudentIdentifier(student, enrollment));
                row.put("studentPhotoUrl", user == null ? null : trimToNull(user.getProfileImageUrl()));
                row.put("studentInitials", initials(displayStudentName(user)));
                row.put("studentId", student == null ? null : Long.valueOf(student.getStudentId()));
                row.put("sessionTitle", tasmiSession == null ? null : trimToNull(tasmiSession.getTitle()));
                row.put("quranPortion", tasmiSession == null ? null : trimToNull(tasmiSession.getQuranPortion()));
                row.put("sessionId", tasmiSession == null ? null : Long.valueOf(tasmiSession.getSessionId()));
                row.put("sessionDate", tasmiSession == null || tasmiSession.getSessionDate() == null
                        ? null
                        : String.valueOf(tasmiSession.getSessionDate()));
                row.put("sessionTime", tasmiSession == null || tasmiSession.getSessionTime() == null
                        ? null
                        : String.valueOf(tasmiSession.getSessionTime()));
                rows.add(row);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load instructor recitation rows", ex);
            for (Recitation recitation : recitations) {
                if (recitation == null) {
                    continue;
                }

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("recitation", recitation);
                row.put("studentName", "Student");
                row.put("studentIdentifier", "-");
                row.put("studentPhotoUrl", null);
                row.put("studentInitials", "ST");
                row.put("sessionTitle", null);
                row.put("quranPortion", null);
                rows.add(row);
            }
        }

        return rows;
    }

    /**
     * Builds a session-grouped view that contains <em>every</em> session owned
     * by the instructor — including sessions with zero recitations — and merges
     * in the recitation rows that already exist.
     *
     * <p>Each group has:
     * <ul>
     *   <li>{@code sessionId}, {@code sessionTitle}, {@code sessionDate},
     *       {@code sessionTime}, {@code sessionStatus} (string),
     *       {@code quranPortion}</li>
     *   <li>{@code evaluationReviewedAt} ({@link Instant} or {@code null}) —
     *       drives the Active vs Reviewed split on the dashboard.</li>
     *   <li>{@code submittedRows} — recitation rows for this session.</li>
     *   <li>{@code submittedCount}, {@code pendingCount}, {@code totalCount},
     *       {@code evaluatedCount}.</li>
     *   <li>{@code pendingStudents} — APPROVED enrollments without a
     *       submission yet.</li>
     * </ul>
     *
     * <p>This replaces the old recitation-derived grouping that hid sessions
     * with zero submissions.</p>
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildSessionGroupsForAllSessions(long userId,
                                                                       List<Map<String, Object>> rows) {
        // Bucket existing recitation rows by session for O(1) merging below.
        Map<Long, List<Map<String, Object>>> rowsBySession = new HashMap<>();
        Map<Long, Set<Long>> submittedStudentsBySession = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                Object sessionIdObj = row.get("sessionId");
                if (!(sessionIdObj instanceof Long)) continue;
                Long sessionId = (Long) sessionIdObj;
                rowsBySession.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(row);
                Object studentIdObj = row.get("studentId");
                if (studentIdObj instanceof Long) {
                    submittedStudentsBySession.computeIfAbsent(sessionId, k -> new HashSet<>())
                            .add((Long) studentIdObj);
                }
            }
        }

        List<Map<String, Object>> groups = new ArrayList<>();
        try (Connection connection = Db.getConnection()) {
            // Resolve the instructor row once so we can pull the canonical
            // session list. If the user isn't an instructor, we have nothing
            // to render.
            Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, userId);
            if (instructorOpt.isEmpty()) {
                return Collections.emptyList();
            }

            List<TasmiSession> sessions = tasmiSessionDao.listByInstructorId(
                    connection, instructorOpt.get().getInstructorId());
            if (sessions == null || sessions.isEmpty()) {
                return Collections.emptyList();
            }

            Map<Long, Student> studentCache = new HashMap<>();
            Map<Long, User> userCache = new HashMap<>();

            for (TasmiSession tasmiSession : sessions) {
                if (tasmiSession == null) continue;
                long sessionId = tasmiSession.getSessionId();

                Map<String, Object> group = new LinkedHashMap<>();
                group.put("sessionId", Long.valueOf(sessionId));
                group.put("sessionTitle", trimToNull(tasmiSession.getTitle()));
                group.put("sessionDate", tasmiSession.getSessionDate() == null ? null
                        : String.valueOf(tasmiSession.getSessionDate()));
                group.put("sessionTime", tasmiSession.getSessionTime() == null ? null
                        : String.valueOf(tasmiSession.getSessionTime()));
                group.put("sessionStatus", tasmiSession.getStatus() == null ? null
                        : tasmiSession.getStatus().name());
                group.put("quranPortion", trimToNull(tasmiSession.getQuranPortion()));
                group.put("evaluationReviewedAt", tasmiSession.getEvaluationReviewedAt());

                List<Map<String, Object>> submittedRows = rowsBySession.getOrDefault(
                        sessionId, new ArrayList<>());
                Set<Long> submittedIds = submittedStudentsBySession.getOrDefault(
                        sessionId, Collections.emptySet());

                int evaluatedCount = 0;
                for (Map<String, Object> row : submittedRows) {
                    if (row.get("evaluation") != null) {
                        evaluatedCount++;
                    }
                }

                // Pending = approved enrollments without a submission yet.
                List<Map<String, Object>> pending = new ArrayList<>();
                int totalApproved = 0;
                List<Enrollment> enrollments = enrollmentDao.listBySessionId(connection, sessionId);
                if (enrollments != null) {
                    for (Enrollment enrollment : enrollments) {
                        if (enrollment == null) continue;
                        if (enrollment.getEnrollmentStatus() != EnrollmentStatus.APPROVED) continue;
                        totalApproved++;
                        if (submittedIds.contains(enrollment.getStudentId())) continue;

                        Student student = loadStudent(connection, studentCache, enrollment.getStudentId());
                        User user = student == null ? null : loadUser(connection, userCache, student.getUserId());

                        Map<String, Object> p = new LinkedHashMap<>();
                        String pendingName = displayStudentName(user);
                        p.put("name", pendingName);
                        p.put("identifier", displayStudentIdentifier(student, enrollment));
                        p.put("photoUrl", user == null ? null : trimToNull(user.getProfileImageUrl()));
                        p.put("initials", initials(pendingName));
                        pending.add(p);
                    }
                }

                group.put("submittedRows", submittedRows);
                group.put("pendingStudents", pending);
                group.put("submittedCount", submittedRows.size());
                group.put("evaluatedCount", evaluatedCount);
                group.put("pendingCount", pending.size());
                // totalCount = approved enrollments. If there are submissions
                // from non-approved enrollments (edge case) we bias toward the
                // larger of the two so the math never reads as "5/3 submitted".
                int total = Math.max(totalApproved, submittedRows.size());
                group.put("totalCount", total);

                groups.add(group);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, "Failed to build session groups for evaluations view", ex);
            return Collections.emptyList();
        }

        return groups;
    }

    private Enrollment loadEnrollment(Connection connection, Map<Long, Enrollment> cache, long enrollmentId) throws SQLException {
        if (cache.containsKey(enrollmentId)) {
            return cache.get(enrollmentId);
        }
        Enrollment enrollment = enrollmentDao.findById(connection, enrollmentId).orElse(null);
        cache.put(enrollmentId, enrollment);
        return enrollment;
    }

    private Student loadStudent(Connection connection, Map<Long, Student> cache, long studentId) throws SQLException {
        if (cache.containsKey(studentId)) {
            return cache.get(studentId);
        }
        Student student = studentDao.findById(connection, studentId).orElse(null);
        cache.put(studentId, student);
        return student;
    }

    private TasmiSession loadSession(Connection connection, Map<Long, TasmiSession> cache, long sessionId) throws SQLException {
        if (cache.containsKey(sessionId)) {
            return cache.get(sessionId);
        }
        TasmiSession tasmiSession = tasmiSessionDao.findById(connection, sessionId).orElse(null);
        cache.put(sessionId, tasmiSession);
        return tasmiSession;
    }

    private User loadUser(Connection connection, Map<Long, User> cache, long userId) throws SQLException {
        if (cache.containsKey(userId)) {
            return cache.get(userId);
        }
        User user = userDao.findById(connection, userId).orElse(null);
        cache.put(userId, user);
        return user;
    }

    private String displayStudentName(User user) {
        String fullName = user == null ? null : trimToNull(user.getFullName());
        return fullName == null ? "Student" : fullName;
    }

    private String displayStudentIdentifier(Student student, Enrollment enrollment) {
        String registrationNumber = student == null ? null : trimToNull(student.getRegistrationNumber());
        if (registrationNumber != null) {
            return registrationNumber;
        }
        if (enrollment != null && enrollment.getStudentId() > 0) {
            return String.valueOf(enrollment.getStudentId());
        }
        return "-";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String initials(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return "ST";
        }
        String[] parts = normalized.split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        String first = parts[0].substring(0, 1).toUpperCase();
        String last = parts[parts.length - 1].substring(0, 1).toUpperCase();
        return first + last;
    }

    private byte[] readRecitationMedia(String mediaPath) {
        String normalized = trimToNull(mediaPath);
        if (normalized == null) {
            return null;
        }

        try {
            if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(normalized))
                        .timeout(Duration.ofSeconds(40))
                        .GET()
                        .build();
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body();
                }
                return null;
            }

            if (normalized.startsWith("/uploads/")) {
                String safePath = normalized.substring("/uploads/".length()).replace("..", "").replace("\\", "/");
                Path base = Paths.get(LocalFileUtil.getUploadsDir()).toAbsolutePath().normalize();
                Path target = base.resolve(safePath).normalize();
                if (!target.startsWith(base) || !Files.exists(target) || !Files.isRegularFile(target)) {
                    return null;
                }
                return Files.readAllBytes(target);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to read recitation media for AI analysis", ex);
            return null;
        }

        return null;
    }

    private String extractFileName(String mediaPath, long recitationId) {
        String normalized = trimToNull(mediaPath);
        if (normalized == null) {
            return "recitation-" + recitationId + ".webm";
        }
        int slash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        String filename = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        filename = trimToNull(filename);
        return filename == null ? "recitation-" + recitationId + ".webm" : filename;
    }

    private long readUserId(HttpSession session) {
        if (session == null) {
            return 0;
        }
        Object userIdObj = session.getAttribute("userId");
        if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        }
        if (userIdObj instanceof Integer) {
            return ((Integer) userIdObj).longValue();
        }
        return 0;
    }
}
