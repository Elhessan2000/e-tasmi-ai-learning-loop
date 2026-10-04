package model.service;

import model.dao.EvaluationDao;
import model.dao.InstructorDao;
import model.dao.RecitationAnalysisDao;
import model.dao.RecitationDao;
import model.dao.RecitationFindingDao;
import model.dao.impl.EvaluationDaoJdbc;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.RecitationAnalysisDaoJdbc;
import model.dao.impl.RecitationDaoJdbc;
import model.dao.impl.RecitationFindingDaoJdbc;
import model.entity.Instructor;
import model.entity.InstructorVerificationStatus;
import model.entity.Recitation;
import model.entity.RecitationAnalysis;
import model.entity.RecitationFindingRecord;
import model.service.analysis.FindingInstructorStatus;
import model.service.analysis.FindingType;
import util.Db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The human verification gate over {@code recitation_finding}.
 *
 * <p>Every AI finding is stored {@code PENDING} and leaves that state only through one of the
 * methods here, each of which requires an explicit instructor action. Nothing in this class
 * promotes a finding automatically, and no caller may pass {@code PENDING} in.</p>
 *
 * <p>An instructor edit never touches the AI columns. The original proposal stays readable beside
 * the instructor's version for the lifetime of the row.</p>
 *
 * <p>Three rules are enforced on every call, server-side, regardless of what the browser sends:
 * the caller must be the approved instructor who owns the recitation; the finding must belong to
 * the recitation's newest analysis; and the recitation must not be evaluated yet, because a saved
 * evaluation is already visible to the student.</p>
 */
public class FindingVerificationService {
    private static final Logger LOGGER = Logger.getLogger(FindingVerificationService.class.getName());

    private static final int MAX_SHORT_TEXT = 255;
    private static final int MAX_LONG_TEXT = 4000;

    private final InstructorDao instructorDao;
    private final RecitationDao recitationDao;
    private final RecitationAnalysisDao analysisDao;
    private final RecitationFindingDao findingDao;
    private final EvaluationDao evaluationDao;
    private final AuditLogService auditLogService;

    public FindingVerificationService() {
        this.instructorDao = new InstructorDaoJdbc();
        this.recitationDao = new RecitationDaoJdbc();
        this.analysisDao = new RecitationAnalysisDaoJdbc();
        this.findingDao = new RecitationFindingDaoJdbc();
        this.evaluationDao = new EvaluationDaoJdbc();
        this.auditLogService = new AuditLogService();
    }

    /** Accepts the AI proposal as-is. Any earlier instructor override is cleared, not hidden. */
    public EvaluationResult accept(long instructorUserId, long findingId) {
        return decide(instructorUserId, findingId, FindingInstructorStatus.ACCEPTED,
                null, null, null, null);
    }

    /**
     * Stores the instructor's corrected version beside the untouched AI proposal. At least one
     * field must be supplied, otherwise this would be an Accept wearing the wrong status.
     */
    public EvaluationResult edit(long instructorUserId, long findingId,
                                 String expectedText, String heardText,
                                 String explanation, String note) {
        String safeExpected = trimShort(expectedText);
        String safeHeard = trimShort(heardText);
        String safeExplanation = trimLong(explanation);
        String safeNote = trimLong(note);
        if (safeExpected == null && safeHeard == null && safeExplanation == null && safeNote == null) {
            return EvaluationResult.failure("An edit needs at least one corrected field. Use Accept to keep the AI version.");
        }
        return decide(instructorUserId, findingId, FindingInstructorStatus.EDITED,
                safeExpected, safeHeard, safeExplanation, safeNote);
    }

    /** Rejects the proposal. A rejected finding is never student-facing and never blocks publication. */
    public EvaluationResult reject(long instructorUserId, long findingId, String note) {
        return decide(instructorUserId, findingId, FindingInstructorStatus.REJECTED,
                null, null, null, trimLong(note));
    }

    private EvaluationResult decide(long instructorUserId, long findingId,
                                    FindingInstructorStatus status,
                                    String expectedText, String heardText,
                                    String explanation, String note) {
        if (findingId <= 0) {
            return EvaluationResult.failure("Invalid finding.");
        }
        if (status == FindingInstructorStatus.PENDING) {
            // Defensive: nothing may push a finding back into the unverified state.
            return EvaluationResult.failure("A finding cannot be returned to pending.");
        }

        try (Connection connection = Db.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                Optional<RecitationFindingRecord> findingOpt = findingDao.findById(connection, findingId);
                if (findingOpt.isEmpty()) {
                    connection.rollback();
                    return refusal();
                }
                RecitationFindingRecord finding = findingOpt.get();
                FindingInstructorStatus applied = status;
                String appliedExpected = expectedText;
                String appliedHeard = heardText;
                String appliedExplanation = explanation;
                String appliedNote = note;
                if (status == FindingInstructorStatus.REJECTED) {
                    // Reject only changes the decision and optional note. An edited override or
                    // an instructor-authored finding must keep its own text.
                    appliedExpected = finding.getInstructorExpectedText();
                    appliedHeard = finding.getInstructorHeardText();
                    appliedExplanation = finding.getInstructorExplanation();
                    if (appliedNote == null) {
                        appliedNote = finding.getInstructorNote();
                    }
                }
                if (finding.getInstructorStatus() == FindingInstructorStatus.INSTRUCTOR_ADDED) {
                    if (status == FindingInstructorStatus.ACCEPTED) {
                        connection.rollback();
                        return EvaluationResult.failure(
                                "This is your finding. Use Edit to change it, or Reject to withdraw it.");
                    }
                    if (status == FindingInstructorStatus.EDITED) {
                        // An instructor-authored row stays INSTRUCTOR_ADDED so it is never
                        // mistaken for an edited AI proposal.
                        applied = FindingInstructorStatus.INSTRUCTOR_ADDED;
                    }
                }

                Guard guard = authorize(connection, instructorUserId, finding.getRecitationId(), finding.getAnalysisId());
                if (!guard.allowed) {
                    connection.rollback();
                    return EvaluationResult.failure(guard.error);
                }

                boolean updated = findingDao.updateDecision(connection, findingId, applied,
                        appliedExpected, appliedHeard, appliedExplanation, appliedNote, guard.instructorId);
                if (!updated) {
                    connection.rollback();
                    return refusal();
                }

                // Ids, status and verse key only. No Qur'an text, no instructor prose, no transcript.
                auditLogService.log(connection, instructorUserId, "INSTRUCTOR",
                        "FINDING_" + applied.name(), "recitation_finding", String.valueOf(findingId),
                        "recitation_id=" + finding.getRecitationId()
                                + " analysis_id=" + finding.getAnalysisId()
                                + " type=" + finding.getFindingType()
                                + " location=" + locationLabel(finding)
                                + " from=" + finding.getInstructorStatus()
                                + " to=" + applied);

                connection.commit();
                LOGGER.info("Finding decision recorded: finding_id=" + findingId
                        + " recitation_id=" + finding.getRecitationId()
                        + " analysis_id=" + finding.getAnalysisId()
                        + " status=" + applied);
                return EvaluationResult.success();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to record decision for finding " + findingId, ex);
            return EvaluationResult.failure("Could not record the decision due to a server error.");
        }
    }

    /**
     * Adds a finding the instructor observed themselves. It is created already verified as
     * {@code INSTRUCTOR_ADDED}, so it does not block publication, and it carries no AI text.
     */
    public EvaluationResult addInstructorFinding(long instructorUserId, long recitationId,
                                                 FindingType findingType, String verseKey, Integer wordPosition,
                                                 String expectedText, String heardText,
                                                 String explanation, String note) {
        if (recitationId <= 0) {
            return EvaluationResult.failure("Invalid recitation.");
        }
        if (findingType == null) {
            return EvaluationResult.failure("Choose a finding type.");
        }
        if (findingType == FindingType.PASSAGE_MISMATCH) {
            return EvaluationResult.failure("Passage mismatch is created by the analysis, not added by hand.");
        }
        String safeExplanation = trimLong(explanation);
        String safeExpected = trimShort(expectedText);
        String safeHeard = trimShort(heardText);
        String safeNote = trimLong(note);
        if (safeExplanation == null && safeExpected == null && safeHeard == null && safeNote == null) {
            return EvaluationResult.failure("Describe the finding before adding it.");
        }
        String safeVerseKey = trimShort(verseKey);
        if (safeVerseKey != null && !safeVerseKey.matches("\\d{1,3}:\\d{1,3}")) {
            return EvaluationResult.failure("Verse key must look like 2:255.");
        }
        if (wordPosition != null && wordPosition < 1) {
            return EvaluationResult.failure("Word position must be 1 or greater.");
        }

        try (Connection connection = Db.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                // Ownership first, so an unowned recitation is not distinguished from a missing one
                // by an "analyze first" message.
                Guard owner = authorizeOwnership(connection, instructorUserId, recitationId);
                if (!owner.allowed) {
                    connection.rollback();
                    return EvaluationResult.failure(owner.error);
                }

                Optional<RecitationAnalysis> latest = analysisDao.findLatestByRecitationId(connection, recitationId);
                if (latest.isEmpty()) {
                    connection.rollback();
                    return EvaluationResult.failure("Run the AI analysis before adding findings to this recitation.");
                }
                long analysisId = latest.get().getAnalysisId();

                Guard guard = authorize(connection, instructorUserId, recitationId, analysisId);
                if (!guard.allowed) {
                    connection.rollback();
                    return EvaluationResult.failure(guard.error);
                }

                long findingId = findingDao.insertInstructorAdded(connection, analysisId, recitationId,
                        findingType, safeVerseKey, wordPosition,
                        safeExpected, safeHeard, safeExplanation, safeNote, guard.instructorId);

                auditLogService.log(connection, instructorUserId, "INSTRUCTOR",
                        "FINDING_INSTRUCTOR_ADDED", "recitation_finding", String.valueOf(findingId),
                        "recitation_id=" + recitationId
                                + " analysis_id=" + analysisId
                                + " type=" + findingType
                                + " location=" + (safeVerseKey == null ? "-" : safeVerseKey
                                        + (wordPosition == null ? "" : "#" + wordPosition)));

                connection.commit();
                LOGGER.info("Instructor finding added: finding_id=" + findingId
                        + " recitation_id=" + recitationId
                        + " analysis_id=" + analysisId
                        + " type=" + findingType);
                return EvaluationResult.success();
            } catch (SQLException | RuntimeException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to add instructor finding for recitation " + recitationId, ex);
            return EvaluationResult.failure("Could not add the finding due to a server error.");
        }
    }

    /**
     * Pending count on the newest analysis for each owned recitation, for the review screen.
     * A recitation with no analysis, or with no findings, is absent.
     */
    public int countPending(long instructorUserId, long recitationId) {
        try (Connection connection = Db.getConnection()) {
            Optional<RecitationAnalysis> latest = analysisDao.findLatestByRecitationId(connection, recitationId);
            if (latest.isEmpty()) {
                return 0;
            }
            Guard guard = authorize(connection, instructorUserId, recitationId, latest.get().getAnalysisId());
            if (!guard.allowed && !guard.alreadyEvaluated) {
                return 0;
            }
            return findingDao.countPendingByAnalysisId(connection, latest.get().getAnalysisId());
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to count pending findings for recitation " + recitationId, ex);
            return 0;
        }
    }

    /**
     * Ownership, latest-analysis and freeze checks. A missing recitation and someone else's
     * recitation produce the same message, so this does not confirm that a row exists.
     */
    private Guard authorize(Connection connection, long instructorUserId, long recitationId, long analysisId)
            throws SQLException {
        Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, instructorUserId);
        if (instructorOpt.isEmpty()) {
            return Guard.denied("Instructor profile not found.");
        }
        Instructor instructor = instructorOpt.get();
        if (instructor.getVerificationStatus() != InstructorVerificationStatus.APPROVED) {
            return Guard.denied("Your instructor account is not approved.");
        }

        List<Recitation> owned = recitationDao.listForInstructor(connection, instructor.getInstructorId());
        boolean owns = owned.stream().anyMatch(r -> r != null && r.getRecitationId() == recitationId);
        if (!owns) {
            return Guard.denied("You cannot verify findings for this recitation.");
        }

        Optional<RecitationAnalysis> latest = analysisDao.findLatestByRecitationId(connection, recitationId);
        if (latest.isEmpty() || latest.get().getAnalysisId() != analysisId) {
            return Guard.denied("This finding belongs to an older analysis. Review the latest analysis instead.");
        }

        if (evaluationDao.findByRecitationId(connection, recitationId).isPresent()) {
            Guard frozen = Guard.denied("This evaluation is already saved, so its findings are final.");
            frozen.alreadyEvaluated = true;
            return frozen;
        }

        return Guard.allowed(instructor.getInstructorId());
    }

    /** Recitation ownership only. Used so Add does not leak whether an unowned recitation has an analysis. */
    private Guard authorizeOwnership(Connection connection, long instructorUserId, long recitationId)
            throws SQLException {
        Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, instructorUserId);
        if (instructorOpt.isEmpty()) {
            return Guard.denied("Instructor profile not found.");
        }
        Instructor instructor = instructorOpt.get();
        if (instructor.getVerificationStatus() != InstructorVerificationStatus.APPROVED) {
            return Guard.denied("Your instructor account is not approved.");
        }
        List<Recitation> owned = recitationDao.listForInstructor(connection, instructor.getInstructorId());
        boolean owns = owned.stream().anyMatch(r -> r != null && r.getRecitationId() == recitationId);
        if (!owns) {
            return Guard.denied("You cannot verify findings for this recitation.");
        }
        return Guard.allowed(instructor.getInstructorId());
    }

    private static EvaluationResult refusal() {
        return EvaluationResult.failure("You cannot verify findings for this recitation.");
    }

    private static String locationLabel(RecitationFindingRecord finding) {
        if (finding.getVerseKey() == null) {
            return "-";
        }
        return finding.getWordPosition() == null
                ? finding.getVerseKey()
                : finding.getVerseKey() + "#" + finding.getWordPosition();
    }

    private static String trimShort(String value) {
        return trimToLength(value, MAX_SHORT_TEXT);
    }

    private static String trimLong(String value) {
        return trimToLength(value, MAX_LONG_TEXT);
    }

    private static String trimToLength(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private static final class Guard {
        private final boolean allowed;
        private final String error;
        private final long instructorId;
        private boolean alreadyEvaluated;

        private Guard(boolean allowed, String error, long instructorId) {
            this.allowed = allowed;
            this.error = error;
            this.instructorId = instructorId;
        }

        static Guard allowed(long instructorId) {
            return new Guard(true, null, instructorId);
        }

        static Guard denied(String error) {
            return new Guard(false, error, 0);
        }
    }
}
