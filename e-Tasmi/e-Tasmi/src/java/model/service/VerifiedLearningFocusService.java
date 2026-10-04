package model.service;

import model.dao.EnrollmentDao;
import model.dao.EvaluationDao;
import model.dao.InstructorDao;
import model.dao.RecitationAnalysisDao;
import model.dao.RecitationDao;
import model.dao.RecitationFindingDao;
import model.dao.StudentDao;
import model.dao.TasmiSessionDao;
import model.dao.UserDao;
import model.dao.impl.EnrollmentDaoJdbc;
import model.dao.impl.EvaluationDaoJdbc;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.RecitationAnalysisDaoJdbc;
import model.dao.impl.RecitationDaoJdbc;
import model.dao.impl.RecitationFindingDaoJdbc;
import model.dao.impl.StudentDaoJdbc;
import model.dao.impl.TasmiSessionDaoJdbc;
import model.dao.impl.UserDaoJdbc;
import model.entity.Enrollment;
import model.entity.Evaluation;
import model.entity.Instructor;
import model.entity.Recitation;
import model.entity.RecitationAnalysis;
import model.entity.RecitationFindingRecord;
import model.entity.Student;
import model.entity.TasmiSession;
import model.entity.User;
import model.service.analysis.FindingInstructorStatus;
import model.service.analysis.FindingType;
import model.service.quran.QuranPassageDisplay;
import model.service.quran.RecitationReferenceService;
import model.service.quran.TrustedReference;
import model.service.quran.TrustedReferenceResult;
import util.Db;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Builds the learner-facing result. Students see score, feedback, and focus only after
 * {@code evaluation.published_at} is set. Unpublished or missing evaluations appear as
 * awaiting instructor review with no score or feedback in the projection.
 */
public class VerifiedLearningFocusService {
    private static final Logger LOGGER = Logger.getLogger(VerifiedLearningFocusService.class.getName());
    private static final DateTimeFormatter SUBMITTED_DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private final StudentDao studentDao;
    private final RecitationDao recitationDao;
    private final EnrollmentDao enrollmentDao;
    private final EvaluationDao evaluationDao;
    private final RecitationAnalysisDao analysisDao;
    private final RecitationFindingDao findingDao;
    private final TasmiSessionDao tasmiSessionDao;
    private final InstructorDao instructorDao;
    private final UserDao userDao;

    public VerifiedLearningFocusService() {
        this.studentDao = new StudentDaoJdbc();
        this.recitationDao = new RecitationDaoJdbc();
        this.enrollmentDao = new EnrollmentDaoJdbc();
        this.evaluationDao = new EvaluationDaoJdbc();
        this.analysisDao = new RecitationAnalysisDaoJdbc();
        this.findingDao = new RecitationFindingDaoJdbc();
        this.tasmiSessionDao = new TasmiSessionDaoJdbc();
        this.instructorDao = new InstructorDaoJdbc();
        this.userDao = new UserDaoJdbc();
    }

    /**
     * Empty when the recitation does not exist or does not belong to this student.
     * Callers must not distinguish those two cases.
     */
    public Optional<VerifiedRecitationView> loadForStudent(long studentUserId, long recitationId) {
        if (studentUserId <= 0 || recitationId <= 0) {
            return Optional.empty();
        }
        try (Connection connection = Db.getConnection()) {
            Optional<Student> studentOpt = studentDao.findByUserId(connection, studentUserId);
            Optional<Recitation> recitationOpt = recitationDao.findById(connection, recitationId);
            if (studentOpt.isEmpty() || recitationOpt.isEmpty()) {
                return Optional.empty();
            }
            Recitation recitation = recitationOpt.get();
            Optional<Enrollment> enrollmentOpt = enrollmentDao.findById(connection, recitation.getEnrollmentId());
            if (enrollmentOpt.isEmpty()
                    || enrollmentOpt.get().getStudentId() != studentOpt.get().getStudentId()) {
                return Optional.empty();
            }
            Enrollment enrollment = enrollmentOpt.get();
            TasmiSession session = tasmiSessionDao.findById(connection, enrollment.getSessionId()).orElse(null);
            Evaluation evaluation = evaluationDao.findByRecitationId(connection, recitationId).orElse(null);

            boolean published = evaluation != null && evaluation.getPublishedAt() != null;
            Integer score = published ? evaluation.getScore() : null;
            String feedback = published ? blankToNull(evaluation.getFeedback()) : null;
            List<VerifiedFocusItem> focus = published
                    ? focusFor(connection, recitationId, evaluation.getAnalysisId())
                    : List.of();

            LOGGER.info("Student result loaded recitation_id=" + recitationId
                    + " published=" + published
                    + " focus_items=" + focus.size());

            return Optional.of(new VerifiedRecitationView(
                    recitationId,
                    enrollment.getEnrollmentId(),
                    published,
                    published,
                    score,
                    feedback,
                    sessionTitle(session, enrollment.getSessionId()),
                    session == null ? "" : QuranPassageDisplay.format(session),
                    instructorName(connection, session),
                    statusKind(score),
                    Math.max(1, recitation.getAttemptNumber()),
                    focus));
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load student result for recitation " + recitationId, ex);
            return Optional.empty();
        }
    }

    /**
     * Same access rules as {@link #loadForStudent}; adds submission date, journey phase, and QF verses.
     */
    public Optional<StudentRecitationResultPage> loadResultPage(long studentUserId, long recitationId) {
        Optional<VerifiedRecitationView> viewOpt = loadForStudent(studentUserId, recitationId);
        if (viewOpt.isEmpty()) {
            return Optional.empty();
        }
        VerifiedRecitationView view = viewOpt.get();
        try (Connection connection = Db.getConnection()) {
            Optional<Recitation> recitationOpt = recitationDao.findById(connection, recitationId);
            if (recitationOpt.isEmpty()) {
                return Optional.empty();
            }
            Recitation recitation = recitationOpt.get();
            Optional<Enrollment> enrollmentOpt = enrollmentDao.findById(connection, recitation.getEnrollmentId());
            TasmiSession session = enrollmentOpt.isEmpty()
                    ? null
                    : tasmiSessionDao.findById(connection, enrollmentOpt.get().getSessionId()).orElse(null);
            Evaluation evaluation = evaluationDao.findByRecitationId(connection, recitationId).orElse(null);
            boolean published = view.isPublished();
            RecitationAnalysis latest = analysisDao.findLatestByRecitationId(connection, recitationId).orElse(null);
            StudentRecitationAnalysisPhase phase = null;
            if (!published) {
                phase = StudentRecitationAnalysisPhase.resolve(
                        evaluation,
                        recitation.getAnalysisJobState(),
                        latest,
                        recitation.getAnalysisJobStartedAt());
            }
            String submittedDate = recitation.getSubmissionDate() == null
                    ? ""
                    : SUBMITTED_DATE_FMT.format(recitation.getSubmissionDate());
            List<TrustedReference.Verse> verses = loadDisplayVerses(session, latest);
            String storedReferenceText = latest == null ? "" : blankToNull(latest.getReferenceText());
            int journeyStep = journeyActiveStep(published, phase);
            return Optional.of(new StudentRecitationResultPage(
                    view, submittedDate, phase, journeyStep, verses, storedReferenceText));
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load student result page for recitation " + recitationId, ex);
            return Optional.empty();
        }
    }

    private static List<TrustedReference.Verse> loadDisplayVerses(TasmiSession session, RecitationAnalysis latest) {
        if (session == null) {
            return List.of();
        }
        if (latest != null && !TrustedReference.SOURCE_QURANPEDIA.equals(latest.getReferenceSource())) {
            return List.of();
        }
        Integer surah = session.getSurahNumber();
        Integer ayahStart = session.getAyahStart();
        Integer ayahEnd = session.getAyahEnd();
        if (surah == null || ayahStart == null || ayahEnd == null || ayahStart < 1 || ayahEnd < ayahStart) {
            return List.of();
        }
        try {
            TrustedReferenceResult referenceResult = RecitationReferenceService.getInstance()
                    .fetch(surah, ayahStart, ayahEnd);
            if (referenceResult != null && referenceResult.isOk() && referenceResult.getReference() != null) {
                return referenceResult.getReference().getVerses();
            }
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Student display verses unavailable: {0}", ex.getClass().getSimpleName());
        }
        return List.of();
    }

    private static int journeyActiveStep(boolean published, StudentRecitationAnalysisPhase phase) {
        if (published) {
            return 4;
        }
        if (phase == null) {
            return 2;
        }
        switch (phase) {
            case ANALYSIS_IN_PROGRESS:
            case SUBMITTED:
            case REFERENCE_UNAVAILABLE:
            case ANALYSIS_FAILED:
            case CANNOT_EVALUATE:
                return 2;
            case AWAITING_INSTRUCTOR:
            case REJECTED:
            default:
                return 3;
        }
    }

    private List<VerifiedFocusItem> focusFor(Connection connection, long recitationId, Long analysisId)
            throws SQLException {
        if (analysisId == null || analysisId <= 0) {
            return List.of();
        }
        Optional<RecitationAnalysis> analysisOpt = analysisDao.findById(connection, analysisId);
        if (analysisOpt.isEmpty() || analysisOpt.get().getRecitationId() != recitationId) {
            return List.of();
        }
        List<RecitationFindingRecord> rows = new ArrayList<>();
        for (RecitationFindingRecord row : findingDao.listByAnalysisId(connection, analysisId)) {
            if (row != null && isStudentFacing(row.getInstructorStatus())) {
                rows.add(row);
            }
        }
        rows.sort(FOCUS_ORDER);
        List<VerifiedFocusItem> items = new ArrayList<>();
        for (RecitationFindingRecord row : rows) {
            VerifiedFocusItem item = toItem(row);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private static boolean isStudentFacing(FindingInstructorStatus status) {
        return status == FindingInstructorStatus.ACCEPTED
                || status == FindingInstructorStatus.EDITED
                || status == FindingInstructorStatus.INSTRUCTOR_ADDED;
    }

    private static VerifiedFocusItem toItem(RecitationFindingRecord row) {
        FindingType type = row.getFindingType();
        FindingInstructorStatus status = row.getInstructorStatus();
        if (type == null || !isStudentFacing(status)) {
            return null;
        }
        boolean instructorOnly = status == FindingInstructorStatus.INSTRUCTOR_ADDED;
        boolean acceptedOriginal = status == FindingInstructorStatus.ACCEPTED;
        String expected = chooseText(acceptedOriginal, instructorOnly,
                row.getExpectedText(), row.getInstructorExpectedText());
        String heard = chooseText(acceptedOriginal, instructorOnly,
                row.getHeardText(), row.getInstructorHeardText());
        String guidance = chooseText(acceptedOriginal, instructorOnly,
                row.getExplanation(), row.getInstructorExplanation());
        String hint = blankToNull(row.getInstructorNote());
        String typeKey = typeKey(type);
        String verseLabel = blankToNull(row.getVerseKey());
        return new VerifiedFocusItem(typeKey, verseLabel, row.getWordPosition(),
                expected, heard, guidance, hint, typeKey);
    }

    /**
     * Accepted uses the original proposal. Edited prefers the instructor field and falls back
     * to the original for that field only. Instructor-added never reads the AI columns.
     */
    private static String chooseText(boolean acceptedOriginal, boolean instructorOnly,
                                     String aiText, String instructorText) {
        if (acceptedOriginal) {
            return blankToNull(aiText);
        }
        if (instructorOnly) {
            return blankToNull(instructorText);
        }
        String edited = blankToNull(instructorText);
        return edited != null ? edited : blankToNull(aiText);
    }

    private static String typeKey(FindingType type) {
        switch (type) {
            case MISSING_WORD:
                return "missingWord";
            case INCORRECT_WORD:
                return "differentWord";
            case EXTRA_WORD:
                return "extraWord";
            case PASSAGE_MISMATCH:
                return "assignedPassage";
            case PRONUNCIATION_OBSERVATION:
                return "listenAgain";
            default:
                return "instructorNote";
        }
    }

    private static final Comparator<RecitationFindingRecord> FOCUS_ORDER = Comparator
            .comparingInt((RecitationFindingRecord row) ->
                    row.getFindingType() == FindingType.PASSAGE_MISMATCH ? 0 : 1)
            .thenComparing(VerifiedLearningFocusService::surahNumber, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(VerifiedLearningFocusService::ayahNumber, Comparator.nullsLast(Integer::compareTo))
            .thenComparing(RecitationFindingRecord::getWordPosition, Comparator.nullsLast(Integer::compareTo));

    private static Integer surahNumber(RecitationFindingRecord row) {
        int[] parts = verseParts(row.getVerseKey());
        return parts == null ? null : parts[0];
    }

    private static Integer ayahNumber(RecitationFindingRecord row) {
        int[] parts = verseParts(row.getVerseKey());
        return parts == null ? null : parts[1];
    }

    private static int[] verseParts(String verseKey) {
        if (verseKey == null || !verseKey.matches("\\d{1,3}:\\d{1,3}")) {
            return null;
        }
        String[] pieces = verseKey.split(":");
        return new int[]{Integer.parseInt(pieces[0]), Integer.parseInt(pieces[1])};
    }

    private String instructorName(Connection connection, TasmiSession session) throws SQLException {
        if (session == null) {
            return "";
        }
        Optional<Instructor> instructorOpt = instructorDao.findById(connection, session.getInstructorId());
        if (instructorOpt.isEmpty()) {
            return "";
        }
        Optional<User> userOpt = userDao.findById(connection, instructorOpt.get().getUserId());
        if (userOpt.isEmpty() || userOpt.get().getFullName() == null) {
            return "";
        }
        return userOpt.get().getFullName().trim();
    }

    private static String sessionTitle(TasmiSession session, long sessionId) {
        if (session != null && session.getTitle() != null && !session.getTitle().isBlank()) {
            return session.getTitle().trim();
        }
        return "Session #" + sessionId;
    }

    private static String statusKind(Integer score) {
        if (score == null) {
            return "pending";
        }
        if (score >= 85) {
            return "excellent";
        }
        if (score >= 60) {
            return "reviewed";
        }
        return "improve";
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
