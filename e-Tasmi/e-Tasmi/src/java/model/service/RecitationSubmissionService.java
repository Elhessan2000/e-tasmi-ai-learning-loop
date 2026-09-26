package model.service;

import model.dao.RecitationLanguageDao;
import model.dao.RecitationSubmissionDao;
import model.dao.impl.RecitationLanguageDaoJdbc;
import model.dao.impl.RecitationSubmissionDaoJdbc;
import model.entity.RecitationLanguage;
import model.entity.RecitationSubmission;
import model.entity.RecitationSubmissionStatus;
import util.Db;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Business logic for the student "Recitation Studio" — the modern live-recording
 * and upload workflow. This is a self-contained feature that writes to the
 * standalone {@code recitation_submissions} table and is independent of the
 * legacy enrollment-bound {@link RecitationService}.
 */
public class RecitationSubmissionService {
    private static final Logger LOGGER = Logger.getLogger(RecitationSubmissionService.class.getName());

    private static final int MAX_TOPIC_LENGTH = 255;
    private static final int MAX_MODULE_LENGTH = 150;

    private final RecitationSubmissionDao submissionDao;
    private final RecitationLanguageDao languageDao;

    public RecitationSubmissionService() {
        this.submissionDao = new RecitationSubmissionDaoJdbc();
        this.languageDao = new RecitationLanguageDaoJdbc();
    }

    /**
     * Ensures both backing tables exist (language first, then submissions, so the
     * foreign key target is present) and that default languages are seeded.
     */
    private void ensureSchema(Connection connection) throws SQLException {
        languageDao.ensureSchema(connection);
        submissionDao.ensureSchema(connection);
    }

    /** Lists the active recitation languages for the bottom-sheet dropdown. */
    public List<RecitationLanguage> listLanguages() {
        try (Connection connection = Db.getConnection()) {
            ensureSchema(connection);
            return languageDao.listActive(connection);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to list recitation languages", ex);
            return Collections.emptyList();
        }
    }

    /** Lists the student's recitations, most recent first, for the dashboard list. */
    public List<RecitationSubmission> listRecent(long studentUserId) {
        if (studentUserId <= 0) {
            return Collections.emptyList();
        }
        try (Connection connection = Db.getConnection()) {
            ensureSchema(connection);
            return submissionDao.listByStudentId(connection, studentUserId);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to list recitation submissions", ex);
            return Collections.emptyList();
        }
    }

    /**
     * Creates a PENDING placeholder row when a live recording is started, before
     * any audio is available. The media path is attached later via
     * {@link #finalizeRecording}.
     */
    public RecitationSubmissionResult startPlaceholder(long studentUserId, String topic, long languageId, String moduleLabel) {
        if (studentUserId <= 0) {
            return RecitationSubmissionResult.failure("Your session has expired. Please sign in again.");
        }
        try (Connection connection = Db.getConnection()) {
            ensureSchema(connection);

            RecitationSubmission submission = new RecitationSubmission();
            submission.setStudentId(studentUserId);
            submission.setTopicText(clean(topic, MAX_TOPIC_LENGTH));
            submission.setLanguageId(resolveLanguageId(connection, languageId));
            submission.setModuleLabel(clean(moduleLabel, MAX_MODULE_LENGTH));
            submission.setStatus(RecitationSubmissionStatus.PENDING);

            long id = submissionDao.insert(connection, submission);
            return RecitationSubmissionResult.success(id);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to create recitation placeholder", ex);
            return RecitationSubmissionResult.failure("We could not start your recitation. Please try again.");
        }
    }

    /**
     * Attaches the compiled audio to an existing placeholder created by
     * {@link #startPlaceholder}.
     */
    public RecitationSubmissionResult finalizeRecording(long studentUserId, long recitationId, String filePath, Long durationSeconds) {
        if (studentUserId <= 0) {
            return RecitationSubmissionResult.failure("Your session has expired. Please sign in again.");
        }
        if (recitationId <= 0) {
            return RecitationSubmissionResult.failure("This recitation could not be found.");
        }
        if (filePath == null || filePath.isBlank()) {
            return RecitationSubmissionResult.failure("The recorded audio file is required.");
        }
        try (Connection connection = Db.getConnection()) {
            ensureSchema(connection);
            boolean updated = submissionDao.updateFilePath(connection, recitationId, studentUserId, filePath, durationSeconds);
            if (!updated) {
                return RecitationSubmissionResult.failure("We could not match this recitation to your account.");
            }
            return RecitationSubmissionResult.success(recitationId);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to finalize recitation recording", ex);
            return RecitationSubmissionResult.failure("Recitation submission failed due to a server error.");
        }
    }

    /**
     * Inserts a fully-formed submission in a single step. Used by the
     * "upload a previous recitation" path and as a fallback when no placeholder
     * was created beforehand.
     */
    public RecitationSubmissionResult submitDirect(long studentUserId, String topic, long languageId,
                                                   String moduleLabel, String filePath, Long durationSeconds) {
        if (studentUserId <= 0) {
            return RecitationSubmissionResult.failure("Your session has expired. Please sign in again.");
        }
        if (filePath == null || filePath.isBlank()) {
            return RecitationSubmissionResult.failure("An audio file is required.");
        }
        try (Connection connection = Db.getConnection()) {
            ensureSchema(connection);

            RecitationSubmission submission = new RecitationSubmission();
            submission.setStudentId(studentUserId);
            submission.setFilePath(filePath);
            submission.setTopicText(clean(topic, MAX_TOPIC_LENGTH));
            submission.setLanguageId(resolveLanguageId(connection, languageId));
            submission.setModuleLabel(clean(moduleLabel, MAX_MODULE_LENGTH));
            submission.setStatus(RecitationSubmissionStatus.PENDING);
            submission.setDurationSeconds(durationSeconds);

            long id = submissionDao.insert(connection, submission);
            return RecitationSubmissionResult.success(id);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to submit recitation", ex);
            return RecitationSubmissionResult.failure("Recitation submission failed due to a server error.");
        }
    }

    private Long resolveLanguageId(Connection connection, long languageId) throws SQLException {
        if (languageId <= 0) {
            return null;
        }
        Optional<RecitationLanguage> language = languageDao.findById(connection, languageId);
        return language.map(RecitationLanguage::getLanguageId).orElse(null);
    }

    private String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}
