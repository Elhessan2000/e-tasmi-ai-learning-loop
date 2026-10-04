package setup;

import model.dao.UserDao;
import model.dao.impl.UserDaoJdbc;
import model.entity.User;
import model.entity.UserRole;
import model.entity.UserStatus;
import util.PasswordUtil;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.Optional;

@WebListener
public class DBSeeder implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            UserDao userDao = new UserDaoJdbc();
            try (Connection conn = util.Db.getConnection()) {
                // Old DB dumps may have `user` without auth/profile columns. SchemaBootstrap only
                // runs when the table is missing entirely, so add missing columns before any query.
                ensureUserProfileAndAuthColumns(conn);
                ensureStudentLevelColumn(conn);
                ensureEmailVerificationTable(conn);
                ensureInstructorModuleSchema(conn);
                ensureAiLearningLoopSchema(conn);
                repairAllAdminUsers(conn);
                Optional<User> adminOpt = userDao.findByEmail(conn, "admin@etasmi.com");
                if (adminOpt.isEmpty()) {
                    User admin = new User();
                    admin.setFullName("Admin User");
                    admin.setEmail("admin@etasmi.com");
                    admin.setPhone("1234567890");
                    admin.setPasswordHash(PasswordUtil.hashPassword("Admin123!".toCharArray()));
                    admin.setActive(true);
                    admin.setEmailVerified(true);
                    admin.setEmailVerifiedAt(Instant.now());
                    admin.setRole(UserRole.ADMIN);
                    admin.setStatus(UserStatus.ACTIVE);
                    admin.setCreatedAt(Instant.now());
                    long adminUserId = userDao.insert(conn, admin);
                    ensureAdminProfile(conn, adminUserId);
                    System.out.println("Seeded default admin account: admin@etasmi.com");
                } else {
                    long adminUserId = adminOpt.get().getUserId();
                    ensureAdminProfile(conn, adminUserId);
                    repairBootstrapAdminAccount(conn, adminUserId);
                }
            }
        } catch (Exception ex) {
            System.err.println("DBSeeder failed: " + ex.getMessage());
        }
    }

    private void ensureUserProfileAndAuthColumns(Connection connection) throws Exception {
        if (!tableExists(connection, "user")) {
            return;
        }
        ensureColumn(connection, "user", "profile_image_url",
                "ALTER TABLE `user` ADD COLUMN profile_image_url VARCHAR(500) DEFAULT NULL AFTER phone");
        ensureColumn(connection, "user", "profile_image_updated_at",
                "ALTER TABLE `user` ADD COLUMN profile_image_updated_at TIMESTAMP NULL DEFAULT NULL AFTER profile_image_url");
        ensureColumn(connection, "user", "email_verified",
                "ALTER TABLE `user` ADD COLUMN email_verified TINYINT(1) NOT NULL DEFAULT 0 AFTER password_hash");
        ensureColumn(connection, "user", "email_verified_at",
                "ALTER TABLE `user` ADD COLUMN email_verified_at TIMESTAMP NULL DEFAULT NULL AFTER email_verified");
        ensureColumn(connection, "user", "email_verification_token_hash",
                "ALTER TABLE `user` ADD COLUMN email_verification_token_hash VARCHAR(64) DEFAULT NULL AFTER email_verified_at");
        ensureColumn(connection, "user", "email_verification_token_expires_at",
                "ALTER TABLE `user` ADD COLUMN email_verification_token_expires_at TIMESTAMP NULL DEFAULT NULL "
                        + "AFTER email_verification_token_hash");
    }

    private void ensureStudentLevelColumn(Connection connection) throws Exception {
        if (!tableExists(connection, "student")) {
            return;
        }
        ensureColumn(connection, "student", "level",
                "ALTER TABLE student ADD COLUMN level ENUM("
                        + "'PRIMARY_SCHOOL','SECONDARY_SCHOOL','HIGH_SCHOOL','UNIVERSITY') "
                        + "NOT NULL DEFAULT 'PRIMARY_SCHOOL' AFTER registration_number");
    }

    private void ensureEmailVerificationTable(Connection connection) throws Exception {
        if (!tableExists(connection, "user")) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS email_verification ("
                    + "user_id BIGINT UNSIGNED NOT NULL,"
                    + "code_hash VARCHAR(64) NOT NULL,"
                    + "expires_at TIMESTAMP NOT NULL,"
                    + "attempts INT NOT NULL DEFAULT 0,"
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "PRIMARY KEY (user_id),"
                    + "CONSTRAINT fk_email_verification_user FOREIGN KEY (user_id) REFERENCES `user` (user_id) "
                    + "ON DELETE CASCADE ON UPDATE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        }
    }

    private void ensureInstructorModuleSchema(Connection connection) throws Exception {
        ensureColumn(connection, "tasmi_session", "duration_minutes",
                "ALTER TABLE tasmi_session ADD COLUMN duration_minutes INT UNSIGNED NOT NULL DEFAULT 60 AFTER session_time");
        ensureColumn(connection, "tasmi_session", "quran_portion",
                "ALTER TABLE tasmi_session ADD COLUMN quran_portion VARCHAR(150) DEFAULT NULL AFTER duration_minutes");
        ensureColumn(connection, "tasmi_session", "live_provider",
                "ALTER TABLE tasmi_session ADD COLUMN live_provider VARCHAR(30) DEFAULT NULL AFTER capacity");
        ensureColumn(connection, "tasmi_session", "meeting_link",
                "ALTER TABLE tasmi_session ADD COLUMN meeting_link VARCHAR(1024) DEFAULT NULL AFTER live_provider");
        ensureColumn(connection, "tasmi_session", "meeting_password",
                "ALTER TABLE tasmi_session ADD COLUMN meeting_password VARCHAR(120) DEFAULT NULL AFTER meeting_link");
        ensureColumn(connection, "tasmi_session", "is_password_visible",
                "ALTER TABLE tasmi_session ADD COLUMN is_password_visible TINYINT(1) NOT NULL DEFAULT 0 AFTER meeting_password");
        ensureColumn(connection, "tasmi_session", "live_started_at",
                "ALTER TABLE tasmi_session ADD COLUMN live_started_at TIMESTAMP NULL DEFAULT NULL AFTER is_password_visible");
        ensureColumn(connection, "tasmi_session", "live_ended_at",
                "ALTER TABLE tasmi_session ADD COLUMN live_ended_at TIMESTAMP NULL DEFAULT NULL AFTER live_started_at");
        ensureColumn(connection, "tasmi_session", "recording_status",
                "ALTER TABLE tasmi_session ADD COLUMN recording_status VARCHAR(30) DEFAULT NULL AFTER live_ended_at");
        ensureColumn(connection, "tasmi_session", "recording_url",
                "ALTER TABLE tasmi_session ADD COLUMN recording_url VARCHAR(1024) DEFAULT NULL AFTER recording_status");
        ensureColumn(connection, "tasmi_session", "recording_synced_at",
                "ALTER TABLE tasmi_session ADD COLUMN recording_synced_at TIMESTAMP NULL DEFAULT NULL AFTER recording_url");
        ensureColumn(connection, "tasmi_session", "zoom_meeting_id",
                "ALTER TABLE tasmi_session ADD COLUMN zoom_meeting_id BIGINT DEFAULT NULL AFTER recording_synced_at");
        ensureColumn(connection, "tasmi_session", "zoom_start_url",
                "ALTER TABLE tasmi_session ADD COLUMN zoom_start_url VARCHAR(2000) DEFAULT NULL AFTER zoom_meeting_id");
        ensureColumn(connection, "tasmi_session", "evaluation_reviewed_at",
                "ALTER TABLE tasmi_session ADD COLUMN evaluation_reviewed_at TIMESTAMP NULL DEFAULT NULL AFTER status");
        ensureIndex(connection, "tasmi_session", "idx_tasmi_session_eval_reviewed",
                "ALTER TABLE tasmi_session ADD INDEX idx_tasmi_session_eval_reviewed (instructor_id, evaluation_reviewed_at)");
        ensureColumn(connection, "instructor", "zoom_email",
                "ALTER TABLE instructor ADD COLUMN zoom_email VARCHAR(200) DEFAULT NULL AFTER bio");
        ensureColumn(connection, "instructor", "payment_account_holder",
                "ALTER TABLE instructor ADD COLUMN payment_account_holder VARCHAR(150) DEFAULT NULL AFTER zoom_email");
        ensureColumn(connection, "instructor", "payment_method_name",
                "ALTER TABLE instructor ADD COLUMN payment_method_name VARCHAR(120) DEFAULT NULL AFTER payment_account_holder");
        ensureColumn(connection, "instructor", "payment_account_details",
                "ALTER TABLE instructor ADD COLUMN payment_account_details VARCHAR(255) DEFAULT NULL AFTER payment_method_name");
        ensureColumn(connection, "instructor", "payment_qr_url",
                "ALTER TABLE instructor ADD COLUMN payment_qr_url VARCHAR(1024) DEFAULT NULL AFTER payment_account_details");
        ensureColumn(connection, "instructor", "payment_instructions",
                "ALTER TABLE instructor ADD COLUMN payment_instructions TEXT DEFAULT NULL AFTER payment_qr_url");

        ensureColumn(connection, "payment", "receipt_file_path",
                "ALTER TABLE payment ADD COLUMN receipt_file_path VARCHAR(1024) DEFAULT NULL AFTER verified_by_admin_id");
        ensureColumn(connection, "payment", "receipt_submitted_at",
                "ALTER TABLE payment ADD COLUMN receipt_submitted_at TIMESTAMP NULL DEFAULT NULL AFTER receipt_file_path");
        ensureColumn(connection, "payment", "verified_by_instructor_id",
                "ALTER TABLE payment ADD COLUMN verified_by_instructor_id BIGINT UNSIGNED DEFAULT NULL AFTER receipt_submitted_at");
        ensureColumn(connection, "payment", "verification_note",
                "ALTER TABLE payment ADD COLUMN verification_note VARCHAR(500) DEFAULT NULL AFTER verified_by_instructor_id");
        ensureColumn(connection, "payment", "currency",
                "ALTER TABLE payment ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'MYR' AFTER verification_note");
        ensureColumn(connection, "payment", "payment_reference",
                "ALTER TABLE payment ADD COLUMN payment_reference VARCHAR(120) DEFAULT NULL AFTER currency");
        ensureColumn(connection, "payment", "student_note",
                "ALTER TABLE payment ADD COLUMN student_note VARCHAR(500) DEFAULT NULL AFTER payment_reference");
        ensureColumn(connection, "payment", "created_at",
                "ALTER TABLE payment ADD COLUMN created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP AFTER student_note");

        ensurePaymentStatusEnum(connection);

        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS instructor_payment_settings ("
                    + "settings_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,"
                    + "instructor_id BIGINT UNSIGNED NOT NULL,"
                    + "qr_image_url VARCHAR(1024) DEFAULT NULL,"
                    + "bank_name VARCHAR(120) DEFAULT NULL,"
                    + "account_holder_name VARCHAR(150) DEFAULT NULL,"
                    + "account_number VARCHAR(64) DEFAULT NULL,"
                    + "payment_notes VARCHAR(1000) DEFAULT NULL,"
                    + "is_active TINYINT(1) NOT NULL DEFAULT 1,"
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + "PRIMARY KEY (settings_id),"
                    + "UNIQUE KEY uq_instructor_payment_settings (instructor_id),"
                    + "CONSTRAINT fk_instructor_payment_settings_instructor FOREIGN KEY (instructor_id) REFERENCES instructor (instructor_id) ON UPDATE CASCADE ON DELETE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            st.execute("CREATE TABLE IF NOT EXISTS payment_verification_history ("
                    + "history_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,"
                    + "payment_id BIGINT UNSIGNED NOT NULL,"
                    + "actor_user_id BIGINT UNSIGNED DEFAULT NULL,"
                    + "actor_role VARCHAR(20) DEFAULT NULL,"
                    + "action VARCHAR(20) NOT NULL,"
                    + "from_status VARCHAR(30) DEFAULT NULL,"
                    + "to_status VARCHAR(30) DEFAULT NULL,"
                    + "reason VARCHAR(500) DEFAULT NULL,"
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "PRIMARY KEY (history_id),"
                    + "KEY idx_payment_verification_history_payment (payment_id),"
                    + "CONSTRAINT fk_payment_verification_history_payment FOREIGN KEY (payment_id) REFERENCES payment (payment_id) ON UPDATE CASCADE ON DELETE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
            st.execute("CREATE TABLE IF NOT EXISTS attendance (" +
                    "attendance_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT," +
                    "session_id BIGINT UNSIGNED NOT NULL," +
                    "student_id BIGINT UNSIGNED NOT NULL," +
                    "marked_by_instructor_id BIGINT UNSIGNED NOT NULL," +
                    "attendance_status ENUM('PRESENT','ABSENT') NOT NULL," +
                    "notes VARCHAR(255) DEFAULT NULL," +
                    "marked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (attendance_id)," +
                    "UNIQUE KEY uq_attendance_session_student (session_id, student_id)," +
                    "KEY idx_attendance_instructor (marked_by_instructor_id)," +
                    "CONSTRAINT fk_attendance_session FOREIGN KEY (session_id) REFERENCES tasmi_session (session_id) ON UPDATE CASCADE ON DELETE CASCADE," +
                    "CONSTRAINT fk_attendance_student FOREIGN KEY (student_id) REFERENCES student (student_id) ON UPDATE CASCADE ON DELETE CASCADE," +
                    "CONSTRAINT fk_attendance_instructor FOREIGN KEY (marked_by_instructor_id) REFERENCES instructor (instructor_id) ON UPDATE CASCADE ON DELETE RESTRICT" +
                    ")");
            st.execute("CREATE TABLE IF NOT EXISTS session_material (" +
                    "material_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT," +
                    "session_id BIGINT UNSIGNED NOT NULL," +
                    "uploaded_by_instructor_id BIGINT UNSIGNED NOT NULL," +
                    "title VARCHAR(150) NOT NULL," +
                    "file_path VARCHAR(255) NOT NULL," +
                    "file_type VARCHAR(120) DEFAULT NULL," +
                    "uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                    "PRIMARY KEY (material_id)," +
                    "KEY idx_material_session (session_id)," +
                    "KEY idx_material_instructor (uploaded_by_instructor_id)," +
                    "CONSTRAINT fk_material_session FOREIGN KEY (session_id) REFERENCES tasmi_session (session_id) ON UPDATE CASCADE ON DELETE CASCADE," +
                    "CONSTRAINT fk_material_instructor FOREIGN KEY (uploaded_by_instructor_id) REFERENCES instructor (instructor_id) ON UPDATE CASCADE ON DELETE RESTRICT" +
                    ")");
        }
    }

    /**
     * AI Learning Loop foundation: structured Qur'an passage on the session,
     * persisted analysis runs, persisted structured findings with per-finding
     * instructor verification columns, evaluation publication state, and
     * recitation attempt lineage.
     *
     * <p>Mirrors {@code setup/module_ai_learning_loop_patch.sql}. Each foreign
     * key is added in the same ALTER as the column it belongs to, so the
     * existing single column-existence guard covers the constraint too.</p>
     *
     * <p>Analysis rows and findings are written by {@code RecitationAnalysisService}.
     * Instructor verification and publication stay unimplemented.</p>
     */
    private void ensureAiLearningLoopSchema(Connection connection) throws Exception {
        if (!tableExists(connection, "recitation") || !tableExists(connection, "instructor")) {
            return;
        }

        // 1) Analysis runs. History is kept: a re-analysis inserts a new row.
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS recitation_analysis ("
                    + "analysis_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,"
                    + "recitation_id BIGINT UNSIGNED NOT NULL,"
                    + "status ENUM('OK','REJECTED','CANNOT_EVALUATE','REFERENCE_UNAVAILABLE','FAILED') NOT NULL,"
                    + "status_reason VARCHAR(500) DEFAULT NULL,"
                    + "stt_provider VARCHAR(60) DEFAULT NULL,"
                    + "stt_model VARCHAR(60) DEFAULT NULL,"
                    + "analysis_model VARCHAR(60) DEFAULT NULL,"
                    + "transcript TEXT DEFAULT NULL,"
                    + "reference_source VARCHAR(60) DEFAULT NULL,"
                    + "reference_verse_keys VARCHAR(255) DEFAULT NULL,"
                    + "reference_text TEXT DEFAULT NULL,"
                    + "matches_expected_passage TINYINT(1) DEFAULT NULL,"
                    + "accuracy_percent DECIMAL(5,2) DEFAULT NULL,"
                    + "ai_suggested_score INT DEFAULT NULL,"
                    + "ai_summary TEXT DEFAULT NULL,"
                    + "ai_feedback TEXT DEFAULT NULL,"
                    + "matched_passage_note TEXT DEFAULT NULL,"
                    + "correct_word_count SMALLINT UNSIGNED DEFAULT NULL,"
                    + "is_quran_confidence DECIMAL(4,3) DEFAULT NULL,"
                    + "mixed_passages TINYINT(1) DEFAULT NULL,"
                    + "detected_passages VARCHAR(255) DEFAULT NULL,"
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "PRIMARY KEY (analysis_id),"
                    + "KEY idx_recitation_analysis_recitation (recitation_id, created_at),"
                    + "CONSTRAINT fk_recitation_analysis_recitation FOREIGN KEY (recitation_id) "
                    + "REFERENCES recitation (recitation_id) ON UPDATE CASCADE ON DELETE CASCADE,"
                    + "CONSTRAINT chk_recitation_analysis_score "
                    + "CHECK (ai_suggested_score IS NULL OR (ai_suggested_score >= 0 AND ai_suggested_score <= 100)),"
                    + "CONSTRAINT chk_recitation_analysis_accuracy "
                    + "CHECK (accuracy_percent IS NULL OR (accuracy_percent >= 0 AND accuracy_percent <= 100)),"
                    + "CONSTRAINT chk_recitation_analysis_quran_confidence "
                    + "CHECK (is_quran_confidence IS NULL OR (is_quran_confidence >= 0 AND is_quran_confidence <= 1))"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");

            // 2) Structured findings. AI columns are immutable; an instructor
            // edit is stored in the instructor_* columns beside them.
            st.execute("CREATE TABLE IF NOT EXISTS recitation_finding ("
                    + "finding_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,"
                    + "analysis_id BIGINT UNSIGNED NOT NULL,"
                    + "recitation_id BIGINT UNSIGNED NOT NULL,"
                    + "finding_type ENUM('MISSING_WORD','INCORRECT_WORD','EXTRA_WORD','PASSAGE_MISMATCH',"
                    + "'PRONUNCIATION_OBSERVATION','OTHER') NOT NULL,"
                    + "verse_key VARCHAR(12) DEFAULT NULL,"
                    + "word_position SMALLINT UNSIGNED DEFAULT NULL,"
                    + "expected_text VARCHAR(255) DEFAULT NULL,"
                    + "heard_text VARCHAR(255) DEFAULT NULL,"
                    + "explanation TEXT DEFAULT NULL,"
                    + "ai_status ENUM('PROPOSED','NOT_APPLICABLE') NOT NULL DEFAULT 'PROPOSED',"
                    + "ai_confidence DECIMAL(4,3) DEFAULT NULL,"
                    + "instructor_status ENUM('PENDING','ACCEPTED','EDITED','REJECTED','INSTRUCTOR_ADDED') "
                    + "NOT NULL DEFAULT 'PENDING',"
                    + "instructor_expected_text VARCHAR(255) DEFAULT NULL,"
                    + "instructor_heard_text VARCHAR(255) DEFAULT NULL,"
                    + "instructor_explanation TEXT DEFAULT NULL,"
                    + "instructor_note TEXT DEFAULT NULL,"
                    + "decided_by_instructor_id BIGINT UNSIGNED DEFAULT NULL,"
                    + "decided_at TIMESTAMP NULL DEFAULT NULL,"
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + "PRIMARY KEY (finding_id),"
                    + "KEY idx_finding_analysis (analysis_id),"
                    + "KEY idx_finding_recitation_status (recitation_id, instructor_status),"
                    + "KEY idx_finding_instructor (decided_by_instructor_id),"
                    + "CONSTRAINT fk_finding_analysis FOREIGN KEY (analysis_id) "
                    + "REFERENCES recitation_analysis (analysis_id) ON UPDATE CASCADE ON DELETE CASCADE,"
                    + "CONSTRAINT fk_finding_recitation FOREIGN KEY (recitation_id) "
                    + "REFERENCES recitation (recitation_id) ON UPDATE CASCADE ON DELETE CASCADE,"
                    + "CONSTRAINT fk_finding_instructor FOREIGN KEY (decided_by_instructor_id) "
                    + "REFERENCES instructor (instructor_id) ON UPDATE CASCADE ON DELETE SET NULL,"
                    + "CONSTRAINT chk_finding_confidence "
                    + "CHECK (ai_confidence IS NULL OR (ai_confidence >= 0 AND ai_confidence <= 1))"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci");
        }

        // Phase 3 reload columns. CREATE TABLE IF NOT EXISTS does not alter a table
        // that already exists, so databases created in Phase 0 pick these up here.
        ensureColumn(connection, "recitation_analysis", "ai_feedback",
                "ALTER TABLE recitation_analysis ADD COLUMN ai_feedback TEXT DEFAULT NULL AFTER ai_summary");
        ensureColumn(connection, "recitation_analysis", "matched_passage_note",
                "ALTER TABLE recitation_analysis ADD COLUMN matched_passage_note TEXT DEFAULT NULL AFTER ai_feedback");
        ensureColumn(connection, "recitation_analysis", "correct_word_count",
                "ALTER TABLE recitation_analysis ADD COLUMN correct_word_count SMALLINT UNSIGNED DEFAULT NULL"
                        + " AFTER matched_passage_note");
        ensureColumn(connection, "recitation_analysis", "is_quran_confidence",
                "ALTER TABLE recitation_analysis ADD COLUMN is_quran_confidence DECIMAL(4,3) DEFAULT NULL"
                        + " AFTER correct_word_count,"
                        + " ADD CONSTRAINT chk_recitation_analysis_quran_confidence"
                        + " CHECK (is_quran_confidence IS NULL OR (is_quran_confidence >= 0 AND is_quran_confidence <= 1))");
        ensureColumn(connection, "recitation_analysis", "mixed_passages",
                "ALTER TABLE recitation_analysis ADD COLUMN mixed_passages TINYINT(1) DEFAULT NULL"
                        + " AFTER is_quran_confidence");
        ensureColumn(connection, "recitation_analysis", "detected_passages",
                "ALTER TABLE recitation_analysis ADD COLUMN detected_passages VARCHAR(255) DEFAULT NULL"
                        + " AFTER mixed_passages");

        // 3) Structured passage on the session. `quran_portion` stays as-is and
        // remains the display label; these columns are the machine-readable form.
        ensureColumn(connection, "tasmi_session", "surah_number",
                "ALTER TABLE tasmi_session ADD COLUMN surah_number TINYINT UNSIGNED DEFAULT NULL AFTER quran_portion,"
                        + " ADD CONSTRAINT chk_tasmi_session_surah"
                        + " CHECK (surah_number IS NULL OR (surah_number >= 1 AND surah_number <= 114))");
        ensureColumn(connection, "tasmi_session", "ayah_start",
                "ALTER TABLE tasmi_session ADD COLUMN ayah_start SMALLINT UNSIGNED DEFAULT NULL AFTER surah_number,"
                        + " ADD CONSTRAINT chk_tasmi_session_ayah_start"
                        + " CHECK (ayah_start IS NULL OR ayah_start >= 1)");
        ensureColumn(connection, "tasmi_session", "ayah_end",
                "ALTER TABLE tasmi_session ADD COLUMN ayah_end SMALLINT UNSIGNED DEFAULT NULL AFTER ayah_start,"
                        + " ADD CONSTRAINT chk_tasmi_session_ayah_range"
                        + " CHECK (ayah_start IS NULL OR ayah_end IS NULL OR ayah_start <= ayah_end)");

        // 4) Attempt lineage for the practice loop.
        ensureColumn(connection, "recitation", "parent_recitation_id",
                "ALTER TABLE recitation ADD COLUMN parent_recitation_id BIGINT UNSIGNED DEFAULT NULL"
                        + " AFTER submission_date,"
                        + " ADD CONSTRAINT fk_recitation_parent FOREIGN KEY (parent_recitation_id)"
                        + " REFERENCES recitation (recitation_id) ON UPDATE CASCADE ON DELETE SET NULL");
        ensureColumn(connection, "recitation", "attempt_number",
                "ALTER TABLE recitation ADD COLUMN attempt_number INT UNSIGNED NOT NULL DEFAULT 1"
                        + " AFTER parent_recitation_id");
        ensureIndex(connection, "recitation", "idx_recitation_parent",
                "ALTER TABLE recitation ADD INDEX idx_recitation_parent (parent_recitation_id)");
        ensureColumn(connection, "recitation", "analysis_job_state",
                "ALTER TABLE recitation ADD COLUMN analysis_job_state"
                        + " ENUM('NONE','IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'NONE'"
                        + " AFTER attempt_number");
        ensureColumn(connection, "recitation", "analysis_job_started_at",
                "ALTER TABLE recitation ADD COLUMN analysis_job_started_at TIMESTAMP NULL DEFAULT NULL"
                        + " AFTER analysis_job_state");
        try (Statement st = connection.createStatement()) {
            st.executeUpdate("UPDATE recitation r SET analysis_job_state = 'COMPLETED'"
                    + " WHERE r.analysis_job_state = 'NONE'"
                    + " AND EXISTS (SELECT 1 FROM recitation_analysis a WHERE a.recitation_id = r.recitation_id)");
        } catch (Exception ignored) {
            // Column may not exist yet on a very old schema; ensureColumn above is authoritative.
        }

        // 5) Evaluation publication state. `created_at` is deliberately left
        // NULL for rows that already exist so no historical evaluation is given
        // an invented timestamp; new rows are stamped by EvaluationDaoJdbc.
        ensureColumn(connection, "evaluation", "analysis_id",
                "ALTER TABLE evaluation ADD COLUMN analysis_id BIGINT UNSIGNED DEFAULT NULL AFTER feedback,"
                        + " ADD CONSTRAINT fk_evaluation_analysis FOREIGN KEY (analysis_id)"
                        + " REFERENCES recitation_analysis (analysis_id) ON UPDATE CASCADE ON DELETE SET NULL");
        ensureColumn(connection, "evaluation", "published_at",
                "ALTER TABLE evaluation ADD COLUMN published_at TIMESTAMP NULL DEFAULT NULL AFTER analysis_id");
        ensureColumn(connection, "evaluation", "created_at",
                "ALTER TABLE evaluation ADD COLUMN created_at TIMESTAMP NULL DEFAULT NULL AFTER published_at");
        ensureIndex(connection, "evaluation", "idx_evaluation_analysis",
                "ALTER TABLE evaluation ADD INDEX idx_evaluation_analysis (analysis_id)");
    }

    /**
     * Migrates the payment_status ENUM from the legacy PayPal-era values
     * ({@code PENDING, SUCCESS, FAILED}) to the QR-transfer lifecycle
     * ({@code PENDING, AWAITING_VERIFICATION, APPROVED, REJECTED}) and rewrites
     * existing rows. Runs idempotently on every startup.
     */
    private void ensurePaymentStatusEnum(Connection connection) {
        if (!safeTableExists(connection, "payment")) {
            return;
        }
        try (Statement st = connection.createStatement()) {
            // 1) Widen the enum to a superset so both old + new values are valid.
            st.execute("ALTER TABLE payment MODIFY COLUMN payment_status "
                    + "ENUM('PENDING','SUCCESS','FAILED','AWAITING_VERIFICATION','APPROVED','REJECTED') "
                    + "NOT NULL DEFAULT 'PENDING'");
            // 2) Rewrite legacy values onto the new lifecycle.
            st.execute("UPDATE payment SET payment_status = 'APPROVED' WHERE payment_status = 'SUCCESS'");
            st.execute("UPDATE payment SET payment_status = 'REJECTED' WHERE payment_status = 'FAILED'");
            // 3) Narrow the enum down to the final set.
            st.execute("ALTER TABLE payment MODIFY COLUMN payment_status "
                    + "ENUM('PENDING','AWAITING_VERIFICATION','APPROVED','REJECTED') "
                    + "NOT NULL DEFAULT 'PENDING'");
            System.out.println("[DBSeeder] payment_status enum migrated to QR-transfer lifecycle.");
        } catch (Exception ex) {
            System.err.println("[DBSeeder] payment_status enum migration skipped: " + ex.getMessage());
        }
    }

    private static boolean safeTableExists(Connection connection, String table) {
        try {
            return tableExists(connection, table);
        } catch (Exception ex) {
            return false;
        }
    }

    private void ensureColumn(Connection connection, String table, String column, String alterSql) throws Exception {
        String sql = "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }
        try (Statement st = connection.createStatement()) {
            st.execute(alterSql);
        }
    }

    private void ensureIndex(Connection connection, String table, String indexName, String alterSql) throws Exception {
        String sql = "SELECT COUNT(*) FROM information_schema.STATISTICS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, table);
            ps.setString(2, indexName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }
        try (Statement st = connection.createStatement()) {
            st.execute(alterSql);
        }
    }

    private static boolean tableExists(Connection connection, String table) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND LOWER(table_name) = LOWER(?)")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * Any row in {@code admin} is a back-office account: ensure email is treated as verified and status ACTIVE
     * so logins are not blocked after email-verification is enforced for self-registered users.
     */
    private void repairAllAdminUsers(Connection connection) throws Exception {
        if (!tableExists(connection, "user") || !tableExists(connection, "admin")) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE `user` u INNER JOIN admin a ON a.user_id = u.user_id "
                        + "SET u.email_verified = 1, "
                        + "u.email_verified_at = COALESCE(u.email_verified_at, CURRENT_TIMESTAMP), "
                        + "u.is_active = 1, u.status = 'ACTIVE' "
                        + "WHERE u.role = 'ADMIN'")) {
            int n = ps.executeUpdate();
            if (n > 0) {
                System.out.println("[DBSeeder] Repaired " + n + " admin user row(s) (email_verified, ACTIVE).");
            }
        }
    }

    /**
     * Keeps the documented bootstrap admin ({@code admin@etasmi.com}) able to sign in after DB restores
     * or schema drift (email_verified / is_active / status).
     * Optional: set env {@code ETASMI_BOOTSTRAP_ADMIN_PASSWORD} to force-reset that account's password on startup.
     */
    private void repairBootstrapAdminAccount(Connection connection, long adminUserId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE `user` SET email_verified = 1, "
                        + "email_verified_at = COALESCE(email_verified_at, CURRENT_TIMESTAMP), "
                        + "is_active = 1, status = 'ACTIVE' "
                        + "WHERE user_id = ? AND role = 'ADMIN' AND email = 'admin@etasmi.com'")) {
            ps.setLong(1, adminUserId);
            ps.executeUpdate();
        }
        String bootstrapPwd = System.getenv("ETASMI_BOOTSTRAP_ADMIN_PASSWORD");
        if (bootstrapPwd != null && !bootstrapPwd.isBlank()) {
            String hash = PasswordUtil.hashPassword(bootstrapPwd.toCharArray());
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE `user` SET password_hash = ? WHERE user_id = ? AND role = 'ADMIN'")) {
                ps.setString(1, hash);
                ps.setLong(2, adminUserId);
                int n = ps.executeUpdate();
                if (n == 1) {
                    System.out.println("ETASMI_BOOTSTRAP_ADMIN_PASSWORD: updated password for admin@etasmi.com");
                }
            }
        }
    }

    private void ensureAdminProfile(Connection connection, long userId) throws Exception {
        if (userId <= 0) {
            return;
        }

        try (PreparedStatement ps = connection.prepareStatement("SELECT COUNT(*) FROM admin WHERE user_id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            }
        }

        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO admin (user_id) VALUES (?)")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // No-op
    }
}
