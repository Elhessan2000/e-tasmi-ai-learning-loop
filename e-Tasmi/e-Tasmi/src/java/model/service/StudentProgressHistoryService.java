package model.service;

import model.dao.RecitationDao;
import model.dao.StudentDao;
import model.dao.impl.RecitationDaoJdbc;
import model.dao.impl.StudentDaoJdbc;
import model.entity.PublishedRecitationRecord;
import model.entity.Student;
import model.service.quran.QuranBundledCatalog;
import model.service.quran.QuranPassageDisplay;
import util.Db;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Published recitation history for the authenticated student. The student is resolved from the
 * session user id only; no browser-supplied student, recitation or evaluation id is read here.
 */
public class StudentProgressHistoryService {
    private static final Logger LOGGER = Logger.getLogger(StudentProgressHistoryService.class.getName());
    static final int HISTORY_LIMIT = 60;
    private static final DateTimeFormatter DATE_LABEL_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_ISO_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.systemDefault());

    private final StudentDao studentDao;
    private final RecitationDao recitationDao;
    private final VerifiedLearningFocusService focusService;

    public StudentProgressHistoryService() {
        this.studentDao = new StudentDaoJdbc();
        this.recitationDao = new RecitationDaoJdbc();
        this.focusService = new VerifiedLearningFocusService();
    }

    public StudentProgressHistory loadForStudentUser(long studentUserId) {
        if (studentUserId <= 0) {
            return StudentProgressHistory.empty();
        }
        List<PublishedRecitationRecord> rows;
        int total;
        try (Connection connection = Db.getConnection()) {
            Optional<Student> studentOpt = studentDao.findByUserId(connection, studentUserId);
            if (studentOpt.isEmpty()) {
                return StudentProgressHistory.empty();
            }
            long studentId = studentOpt.get().getStudentId();
            rows = recitationDao.listPublishedForStudent(connection, studentId, HISTORY_LIMIT);
            total = recitationDao.countPublishedForStudent(connection, studentId);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load published recitation history", ex);
            return StudentProgressHistory.empty();
        }
        if (rows.isEmpty()) {
            return StudentProgressHistory.empty();
        }

        List<StudentProgressHistory.Entry> entries = new ArrayList<>(rows.size());
        for (PublishedRecitationRecord row : rows) {
            entries.add(toEntry(row));
        }

        StudentProgressHistory.Entry latest = entries.get(0);
        int previousIndex = previousAttemptIndex(rows);
        StudentProgressHistory.Comparison comparison = new StudentProgressHistory.Comparison(
                latest, previousIndex < 0 ? null : entries.get(previousIndex));

        return new StudentProgressHistory(entries, total, latestFocus(studentUserId, rows.get(0), latest), comparison);
    }

    /**
     * The attempt the latest result was practised from when it is published; otherwise the most
     * recent earlier published attempt on the same enrollment. -1 when there is none.
     */
    private static int previousAttemptIndex(List<PublishedRecitationRecord> rows) {
        PublishedRecitationRecord latest = rows.get(0);
        Long parentId = latest.getParentRecitationId();
        if (parentId != null) {
            for (int i = 1; i < rows.size(); i++) {
                if (rows.get(i).getRecitationId() == parentId) {
                    return i;
                }
            }
        }
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).getEnrollmentId() == latest.getEnrollmentId()) {
                return i;
            }
        }
        return -1;
    }

    private StudentProgressHistory.LatestFocus latestFocus(long studentUserId, PublishedRecitationRecord row,
                                                            StudentProgressHistory.Entry entry) {
        Optional<VerifiedRecitationView> viewOpt = focusService.loadForStudent(studentUserId, row.getRecitationId());
        if (viewOpt.isEmpty() || !viewOpt.get().isPublished()) {
            return null;
        }
        List<VerifiedFocusItem> items = viewOpt.get().getFocusItems();
        if (items == null || items.isEmpty()) {
            return new StudentProgressHistory.LatestFocus(entry, null, entry.getPassage(), null, 0);
        }
        VerifiedFocusItem primary = items.get(0);
        String verseKey = primary.getVerseLabel();
        if (verseKey != null && verseKey.matches("\\d{1,3}:\\d{1,3}")) {
            int surah = Integer.parseInt(verseKey.substring(0, verseKey.indexOf(':')));
            int ayah = Integer.parseInt(verseKey.substring(verseKey.indexOf(':') + 1));
            String name = surah >= 1 && surah <= 114 ? QuranBundledCatalog.chapterNameSimple(surah) : null;
            return new StudentProgressHistory.LatestFocus(entry, primary,
                    name == null ? "Surah " + surah : name, ayah, items.size());
        }
        return new StudentProgressHistory.LatestFocus(entry, primary, passage(row), null, items.size());
    }

    private static StudentProgressHistory.Entry toEntry(PublishedRecitationRecord row) {
        String title = row.getSessionTitle() == null || row.getSessionTitle().isBlank()
                ? "Session"
                : row.getSessionTitle().trim();
        return new StudentProgressHistory.Entry(
                row.getRecitationId(),
                row.getSubmittedAt() == null ? "" : DATE_ISO_FMT.format(row.getSubmittedAt()),
                row.getSubmittedAt() == null ? "" : DATE_LABEL_FMT.format(row.getSubmittedAt()),
                title,
                passage(row),
                row.getAttemptNumber(),
                Math.max(0, Math.min(100, row.getScore())),
                Math.max(0, row.getFocusCount()));
    }

    private static String passage(PublishedRecitationRecord row) {
        String label = QuranPassageDisplay.format(row.getSurahNumber(), row.getAyahStart(), row.getAyahEnd(),
                row.getQuranPortion());
        return label.startsWith("Surah ") ? label.substring("Surah ".length()) : label;
    }
}
