package model.service;

import model.dao.EvaluationDao;
import model.dao.InstructorDao;
import model.dao.EnrollmentDao;
import model.dao.ProgressDao;
import model.dao.RecitationDao;
import model.dao.StudentDao;
import model.dao.TasmiSessionDao;
import model.dao.UserDao;
import model.dao.impl.EvaluationDaoJdbc;
import model.dao.impl.InstructorDaoJdbc;
import model.dao.impl.EnrollmentDaoJdbc;
import model.dao.impl.ProgressDaoJdbc;
import model.dao.impl.RecitationDaoJdbc;
import model.dao.impl.StudentDaoJdbc;
import model.dao.impl.TasmiSessionDaoJdbc;
import model.dao.impl.UserDaoJdbc;
import model.entity.Evaluation;
import model.entity.Instructor;
import model.entity.InstructorVerificationStatus;
import model.entity.Recitation;
import model.entity.Enrollment;
import model.entity.TasmiSession;
import util.Db;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EvaluationService {
    private static final Logger LOGGER = Logger.getLogger(EvaluationService.class.getName());

    private final InstructorDao instructorDao;
    private final RecitationDao recitationDao;
    private final EvaluationDao evaluationDao;
    private final EnrollmentDao enrollmentDao;
    private final ProgressDao progressDao;
    private final TasmiSessionDao tasmiSessionDao;

    public EvaluationService() {
        this.instructorDao = new InstructorDaoJdbc();
        this.recitationDao = new RecitationDaoJdbc();
        this.evaluationDao = new EvaluationDaoJdbc();
        this.enrollmentDao = new EnrollmentDaoJdbc();
        this.progressDao = new ProgressDaoJdbc();
        this.tasmiSessionDao = new TasmiSessionDaoJdbc();
    }

    public List<Recitation> listRecitationsForInstructor(long instructorUserId) {
        try (Connection connection = Db.getConnection()) {
            Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, instructorUserId);
            if (instructorOpt.isEmpty()) {
                return List.of();
            }
            return recitationDao.listForInstructor(connection, instructorOpt.get().getInstructorId());
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to list recitations", ex);
            return List.of();
        }
    }

    public EvaluationResult evaluate(long instructorUserId, long recitationId, int score, String feedback) {
        if (recitationId <= 0) {
            return EvaluationResult.failure("Invalid recitation.");
        }
        if (score < 0 || score > 100) {
            return EvaluationResult.failure("Score must be between 0 and 100.");
        }

        try (Connection connection = Db.getConnection()) {
            connection.setAutoCommit(false);

            Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, instructorUserId);
            if (instructorOpt.isEmpty()) {
                connection.rollback();
                return EvaluationResult.failure("Instructor profile not found.");
            }

            Instructor instructor = instructorOpt.get();
            if (instructor.getVerificationStatus() != InstructorVerificationStatus.APPROVED) {
                connection.rollback();
                return EvaluationResult.failure("Your instructor account is not approved.");
            }

            boolean owns = recitationDao.listForInstructor(connection, instructor.getInstructorId())
                    .stream().anyMatch(r -> r.getRecitationId() == recitationId);
            if (!owns) {
                connection.rollback();
                return EvaluationResult.failure("You cannot evaluate this recitation.");
            }

            Optional<Evaluation> existing = evaluationDao.findByRecitationId(connection, recitationId);
            if (existing.isPresent()) {
                connection.rollback();
                return EvaluationResult.failure("This recitation has already been evaluated.");
            }

            Evaluation e = new Evaluation();
            e.setRecitationId(recitationId);
            e.setInstructorId(instructor.getInstructorId());
            e.setScore(score);
            e.setFeedback(feedback);

            evaluationDao.insert(connection, e);

            Optional<Recitation> recitationOpt = recitationDao.findById(connection, recitationId);
            if (recitationOpt.isPresent()) {
                Optional<Enrollment> enrollmentOpt = enrollmentDao.findById(connection, recitationOpt.get().getEnrollmentId());
                if (enrollmentOpt.isPresent()) {
                    long studentId = enrollmentOpt.get().getStudentId();
                    progressDao.upsert(connection, studentId, progressDao.computeCompletionRate(connection, studentId));
                }
            }

            connection.commit();
            return EvaluationResult.success();
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to evaluate recitation", ex);
            return EvaluationResult.failure("Evaluation failed due to a server error.");
        }
    }

    /**
     * Marks the session's evaluations as <em>Reviewed</em> for the calling
     * instructor. The session moves out of the Active Evaluations grid and
     * into the Reviewed Sessions grid on the dashboard.
     *
     * <p>By design this never blocks the instructor based on whether all
     * recitations are evaluated or whether students are still pending — the
     * UI's confirmation modal warns the instructor first. The decision to
     * archive is the instructor's prerogative.</p>
     *
     * @param instructorUserId the user id from the http session
     * @param sessionId        the {@code tasmi_session.session_id} to mark
     * @return success / failure result
     */
    public EvaluationResult markSessionReviewed(long instructorUserId, long sessionId) {
        return updateSessionReviewState(instructorUserId, sessionId, Instant.now());
    }

    /**
     * Clears the {@code evaluation_reviewed_at} timestamp, returning the
     * session to the Active Evaluations bucket.
     */
    public EvaluationResult reopenSessionReview(long instructorUserId, long sessionId) {
        return updateSessionReviewState(instructorUserId, sessionId, null);
    }

    private EvaluationResult updateSessionReviewState(long instructorUserId, long sessionId, Instant reviewedAt) {
        if (sessionId <= 0) {
            return EvaluationResult.failure("Invalid session.");
        }

        try (Connection connection = Db.getConnection()) {
            connection.setAutoCommit(false);

            Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, instructorUserId);
            if (instructorOpt.isEmpty()) {
                connection.rollback();
                return EvaluationResult.failure("Instructor profile not found.");
            }

            Instructor instructor = instructorOpt.get();
            if (instructor.getVerificationStatus() != InstructorVerificationStatus.APPROVED) {
                connection.rollback();
                return EvaluationResult.failure("Your instructor account is not approved.");
            }

            Optional<TasmiSession> sessionOpt = tasmiSessionDao.findById(connection, sessionId);
            if (sessionOpt.isEmpty() || sessionOpt.get().getInstructorId() != instructor.getInstructorId()) {
                // We deliberately conflate "missing" and "not yours" so we
                // don't leak information about session existence.
                connection.rollback();
                return EvaluationResult.failure("You cannot modify this session.");
            }

            boolean updated = (reviewedAt == null)
                    ? tasmiSessionDao.clearEvaluationReviewed(connection, sessionId, instructor.getInstructorId())
                    : tasmiSessionDao.markEvaluationReviewed(connection, sessionId, instructor.getInstructorId(), reviewedAt);

            if (!updated) {
                connection.rollback();
                return EvaluationResult.failure("Could not update the session review status.");
            }

            connection.commit();
            return EvaluationResult.success();
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to update session review state", ex);
            return EvaluationResult.failure("Server error while updating the session review status.");
        }
    }
}
