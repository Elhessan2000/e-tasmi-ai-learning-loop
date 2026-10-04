-- =====================================================================
-- e-Tasmi — Railway-ready schema import
-- ---------------------------------------------------------------------
-- Run this ONCE against the Railway MySQL database that your app uses
-- (whatever MYSQLDATABASE is set to — usually "railway").
--
-- How to run:
--   1. Railway dashboard → your MySQL service → "Query" tab
--   2. Paste the full contents of this file → Run
--
-- OR from your machine:
--   mysql -h <MYSQLHOST> -P <MYSQLPORT> -u <MYSQLUSER> -p<MYSQLPASSWORD> <MYSQLDATABASE> < railway_schema.sql
--
-- This file is identical to etasmi_schema.sql but WITHOUT the
-- "CREATE DATABASE etasmi" / "USE etasmi" lines, which fail on Railway
-- (managed MySQL users normally can't create sibling databases).
-- =====================================================================
SET NAMES utf8mb4;
SET time_zone = '+00:00';

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS evaluation;
DROP TABLE IF EXISTS recitation_finding;
DROP TABLE IF EXISTS recitation_analysis;
DROP TABLE IF EXISTS recitation;
DROP TABLE IF EXISTS attendance;
DROP TABLE IF EXISTS session_material;
DROP TABLE IF EXISTS password_reset;
DROP TABLE IF EXISTS email_verification;
DROP TABLE IF EXISTS progress;
DROP TABLE IF EXISTS notifications;
DROP TABLE IF EXISTS payment_verification_history;
DROP TABLE IF EXISTS instructor_earning;
DROP TABLE IF EXISTS payment;
DROP TABLE IF EXISTS enrollment;
DROP TABLE IF EXISTS tasmi_session;
DROP TABLE IF EXISTS admin;
DROP TABLE IF EXISTS instructor_payment_method;
DROP TABLE IF EXISTS instructor_payment_settings;
DROP TABLE IF EXISTS instructor;
DROP TABLE IF EXISTS student;
DROP TABLE IF EXISTS `user`;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE `user` (
  user_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  full_name VARCHAR(150) NOT NULL,
  email VARCHAR(191) NOT NULL,
  phone VARCHAR(30) NOT NULL,
  profile_image_url VARCHAR(500) DEFAULT NULL,
  profile_image_updated_at TIMESTAMP NULL DEFAULT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role ENUM('ADMIN','INSTRUCTOR','STUDENT') NOT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  status ENUM('ACTIVE','INACTIVE','DELETED') NOT NULL DEFAULT 'ACTIVE',
  email_verified TINYINT(1) NOT NULL DEFAULT 0,
  email_verified_at TIMESTAMP NULL DEFAULT NULL,
  email_verification_token_hash VARCHAR(64) DEFAULT NULL,
  email_verification_token_expires_at TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  UNIQUE KEY uq_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE student (
  student_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  registration_number VARCHAR(50) NOT NULL,
  level ENUM('PRIMARY_SCHOOL','SECONDARY_SCHOOL','HIGH_SCHOOL','UNIVERSITY') NOT NULL DEFAULT 'PRIMARY_SCHOOL',
  PRIMARY KEY (student_id),
  UNIQUE KEY uq_student_user (user_id),
  UNIQUE KEY uq_student_regno (registration_number),
  CONSTRAINT fk_student_user
    FOREIGN KEY (user_id) REFERENCES `user` (user_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE instructor (
  instructor_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  title ENUM('SHEIKH','USTADH') DEFAULT NULL,
  qualification VARCHAR(150) DEFAULT NULL,
  qualification_file VARCHAR(255) DEFAULT NULL,
  verification_status ENUM('PENDING','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  bio TEXT DEFAULT NULL,
  zoom_email VARCHAR(200) DEFAULT NULL,
  payment_account_holder VARCHAR(150) DEFAULT NULL,
  payment_method_name VARCHAR(120) DEFAULT NULL,
  payment_account_details VARCHAR(255) DEFAULT NULL,
  payment_qr_url VARCHAR(1024) DEFAULT NULL,
  payment_instructions TEXT DEFAULT NULL,
  PRIMARY KEY (instructor_id),
  UNIQUE KEY uq_instructor_user (user_id),
  CONSTRAINT fk_instructor_user
    FOREIGN KEY (user_id) REFERENCES `user` (user_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE instructor_payment_settings (
  settings_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instructor_id BIGINT UNSIGNED NOT NULL,
  qr_image_url VARCHAR(1024) DEFAULT NULL,
  bank_name VARCHAR(120) DEFAULT NULL,
  account_holder_name VARCHAR(150) DEFAULT NULL,
  account_number VARCHAR(64) DEFAULT NULL,
  payment_notes VARCHAR(1000) DEFAULT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (settings_id),
  UNIQUE KEY uq_instructor_payment_settings (instructor_id),
  CONSTRAINT fk_instructor_payment_settings_instructor
    FOREIGN KEY (instructor_id) REFERENCES instructor (instructor_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin (
  admin_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (admin_id),
  UNIQUE KEY uq_admin_user (user_id),
  CONSTRAINT fk_admin_user
    FOREIGN KEY (user_id) REFERENCES `user` (user_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tasmi_session (
  session_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  instructor_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(150) NOT NULL,
  description TEXT DEFAULT NULL,
  level ENUM('PRIMARY_SCHOOL','SECONDARY_SCHOOL','HIGH_SCHOOL','UNIVERSITY') NOT NULL DEFAULT 'PRIMARY_SCHOOL',
  session_date DATE NOT NULL,
  session_time TIME NOT NULL,
  duration_minutes INT UNSIGNED NOT NULL DEFAULT 60,
  quran_portion VARCHAR(150) DEFAULT NULL,
  surah_number TINYINT UNSIGNED DEFAULT NULL,
  ayah_start SMALLINT UNSIGNED DEFAULT NULL,
  ayah_end SMALLINT UNSIGNED DEFAULT NULL,
  mode ENUM('ONLINE','PHYSICAL') NOT NULL,
  fee DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  capacity INT UNSIGNED NOT NULL DEFAULT 1,
  live_provider VARCHAR(30) DEFAULT NULL,
  meeting_link VARCHAR(1024) DEFAULT NULL,
  meeting_password VARCHAR(120) DEFAULT NULL,
  is_password_visible TINYINT(1) NOT NULL DEFAULT 0,
  live_started_at TIMESTAMP NULL DEFAULT NULL,
  live_ended_at TIMESTAMP NULL DEFAULT NULL,
  recording_status VARCHAR(30) DEFAULT NULL,
  recording_url VARCHAR(1024) DEFAULT NULL,
  recording_synced_at TIMESTAMP NULL DEFAULT NULL,
  zoom_meeting_id BIGINT DEFAULT NULL,
  zoom_start_url VARCHAR(2000) DEFAULT NULL,
  banner_image_url VARCHAR(1024) DEFAULT NULL,
  status ENUM('SCHEDULED','ONGOING','COMPLETED','CANCELLED') NOT NULL DEFAULT 'SCHEDULED',
  evaluation_reviewed_at TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (session_id),
  KEY idx_tasmi_session_instructor (instructor_id),
  KEY idx_tasmi_session_date (session_date),
  KEY idx_tasmi_session_eval_reviewed (instructor_id, evaluation_reviewed_at),
  CONSTRAINT fk_tasmi_session_instructor
    FOREIGN KEY (instructor_id) REFERENCES instructor (instructor_id)
    ON UPDATE CASCADE
    ON DELETE RESTRICT,
  CONSTRAINT chk_tasmi_session_surah
    CHECK (surah_number IS NULL OR (surah_number >= 1 AND surah_number <= 114)),
  CONSTRAINT chk_tasmi_session_ayah_start
    CHECK (ayah_start IS NULL OR ayah_start >= 1),
  CONSTRAINT chk_tasmi_session_ayah_range
    CHECK (ayah_start IS NULL OR ayah_end IS NULL OR ayah_start <= ayah_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE enrollment (
  enrollment_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  student_id BIGINT UNSIGNED NOT NULL,
  session_id BIGINT UNSIGNED NOT NULL,
  enrollment_status ENUM('PENDING','APPROVED','REJECTED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  PRIMARY KEY (enrollment_id),
  UNIQUE KEY uq_enrollment_student_session (student_id, session_id),
  KEY idx_enrollment_session (session_id),
  CONSTRAINT fk_enrollment_student
    FOREIGN KEY (student_id) REFERENCES student (student_id)
    ON UPDATE CASCADE
    ON DELETE RESTRICT,
  CONSTRAINT fk_enrollment_session
    FOREIGN KEY (session_id) REFERENCES tasmi_session (session_id)
    ON UPDATE CASCADE
    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payment (
  payment_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  enrollment_id BIGINT UNSIGNED NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  payment_status ENUM('PENDING','AWAITING_VERIFICATION','APPROVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  payment_date TIMESTAMP NULL DEFAULT NULL,
  verified_by_admin_id BIGINT UNSIGNED DEFAULT NULL,
  receipt_file_path VARCHAR(1024) DEFAULT NULL,
  receipt_submitted_at TIMESTAMP NULL DEFAULT NULL,
  verified_by_instructor_id BIGINT UNSIGNED DEFAULT NULL,
  verification_note VARCHAR(500) DEFAULT NULL,
  currency VARCHAR(3) NOT NULL DEFAULT 'MYR',
  payment_reference VARCHAR(120) DEFAULT NULL,
  student_note VARCHAR(500) DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (payment_id),
  UNIQUE KEY uq_payment_enrollment (enrollment_id),
  KEY idx_payment_status (payment_status),
  KEY idx_payment_verified_by (verified_by_admin_id),
  KEY idx_payment_verified_by_instructor (verified_by_instructor_id),
  CONSTRAINT fk_payment_enrollment
    FOREIGN KEY (enrollment_id) REFERENCES enrollment (enrollment_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE,
  CONSTRAINT fk_payment_verified_by_admin
    FOREIGN KEY (verified_by_admin_id) REFERENCES admin (admin_id)
    ON UPDATE CASCADE
    ON DELETE SET NULL,
  CONSTRAINT fk_payment_verified_by_instructor
    FOREIGN KEY (verified_by_instructor_id) REFERENCES instructor (instructor_id)
    ON UPDATE CASCADE
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE payment_verification_history (
  history_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  payment_id BIGINT UNSIGNED NOT NULL,
  actor_user_id BIGINT UNSIGNED DEFAULT NULL,
  actor_role VARCHAR(20) DEFAULT NULL,
  action VARCHAR(20) NOT NULL,
  from_status VARCHAR(30) DEFAULT NULL,
  to_status VARCHAR(30) DEFAULT NULL,
  reason VARCHAR(500) DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (history_id),
  KEY idx_payment_verification_history_payment (payment_id),
  CONSTRAINT fk_payment_verification_history_payment
    FOREIGN KEY (payment_id) REFERENCES payment (payment_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notifications (
  notification_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  message VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (notification_id),
  KEY idx_notifications_user (user_id),
  CONSTRAINT fk_notifications_user
    FOREIGN KEY (user_id) REFERENCES `user` (user_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE progress (
  student_id BIGINT UNSIGNED NOT NULL,
  completion_rate DECIMAL(5,2) NOT NULL DEFAULT 0.00,
  last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (student_id),
  CONSTRAINT fk_progress_student
    FOREIGN KEY (student_id) REFERENCES student (student_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE,
  CONSTRAINT chk_progress_completion_rate CHECK (completion_rate >= 0 AND completion_rate <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE email_verification (
  user_id BIGINT UNSIGNED NOT NULL,
  code_hash VARCHAR(64) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_email_verification_user
    FOREIGN KEY (user_id) REFERENCES `user` (user_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE password_reset (
  reset_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  token_hash VARCHAR(64) NOT NULL,
  expires_at TIMESTAMP NOT NULL,
  used_at TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (reset_id),
  UNIQUE KEY uq_password_reset_token (token_hash),
  KEY idx_password_reset_user (user_id),
  CONSTRAINT fk_password_reset_user
    FOREIGN KEY (user_id) REFERENCES `user` (user_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE recitation (
  recitation_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  enrollment_id BIGINT UNSIGNED NOT NULL,
  audio_file_path VARCHAR(255) NOT NULL,
  submission_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  parent_recitation_id BIGINT UNSIGNED DEFAULT NULL,
  attempt_number INT UNSIGNED NOT NULL DEFAULT 1,
  analysis_job_state ENUM('NONE','IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'NONE',
  analysis_job_started_at TIMESTAMP NULL DEFAULT NULL,
  PRIMARY KEY (recitation_id),
  KEY idx_recitation_enrollment (enrollment_id),
  KEY idx_recitation_parent (parent_recitation_id),
  CONSTRAINT fk_recitation_enrollment
    FOREIGN KEY (enrollment_id) REFERENCES enrollment (enrollment_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE,
  CONSTRAINT fk_recitation_parent
    FOREIGN KEY (parent_recitation_id) REFERENCES recitation (recitation_id)
    ON UPDATE CASCADE
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Recitation Studio (standalone live-recording / upload workflow).
-- Independent of the enrollment-bound `recitation` table above.
-- ---------------------------------------------------------------------
CREATE TABLE recitation_language (
  language_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  code VARCHAR(12) DEFAULT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (language_id),
  UNIQUE KEY uq_recitation_language_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO recitation_language (name, code, sort_order) VALUES
  ('Arabic', 'ar', 1),
  ('English', 'en', 2),
  ('Malay', 'ms', 3),
  ('Urdu', 'ur', 4),
  ('Turkish', 'tr', 5),
  ('Indonesian', 'id', 6);

CREATE TABLE recitation_submissions (
  recitation_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  student_id BIGINT UNSIGNED NOT NULL,
  file_path VARCHAR(500) DEFAULT NULL,
  topic_text VARCHAR(255) DEFAULT NULL,
  language_id BIGINT UNSIGNED DEFAULT NULL,
  module_label VARCHAR(150) DEFAULT NULL,
  status ENUM('PENDING','EVALUATED') NOT NULL DEFAULT 'PENDING',
  duration_seconds INT DEFAULT NULL,
  submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (recitation_id),
  KEY idx_recsub_student (student_id),
  KEY idx_recsub_language (language_id),
  CONSTRAINT fk_recsub_user
    FOREIGN KEY (student_id) REFERENCES `user` (user_id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_recsub_language
    FOREIGN KEY (language_id) REFERENCES recitation_language (language_id)
    ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE attendance (
  attendance_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  session_id BIGINT UNSIGNED NOT NULL,
  student_id BIGINT UNSIGNED NOT NULL,
  marked_by_instructor_id BIGINT UNSIGNED NOT NULL,
  attendance_status ENUM('PRESENT','ABSENT') NOT NULL,
  notes VARCHAR(255) DEFAULT NULL,
  marked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (attendance_id),
  UNIQUE KEY uq_attendance_session_student (session_id, student_id),
  KEY idx_attendance_instructor (marked_by_instructor_id),
  CONSTRAINT fk_attendance_session
    FOREIGN KEY (session_id) REFERENCES tasmi_session (session_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE,
  CONSTRAINT fk_attendance_student
    FOREIGN KEY (student_id) REFERENCES student (student_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE,
  CONSTRAINT fk_attendance_instructor
    FOREIGN KEY (marked_by_instructor_id) REFERENCES instructor (instructor_id)
    ON UPDATE CASCADE
    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE session_material (
  material_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  session_id BIGINT UNSIGNED NOT NULL,
  uploaded_by_instructor_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(150) NOT NULL,
  file_path VARCHAR(255) NOT NULL,
  file_type VARCHAR(120) DEFAULT NULL,
  uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (material_id),
  KEY idx_material_session (session_id),
  KEY idx_material_instructor (uploaded_by_instructor_id),
  CONSTRAINT fk_material_session
    FOREIGN KEY (session_id) REFERENCES tasmi_session (session_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE,
  CONSTRAINT fk_material_instructor
    FOREIGN KEY (uploaded_by_instructor_id) REFERENCES instructor (instructor_id)
    ON UPDATE CASCADE
    ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- AI Learning Loop: persisted recitation analysis and structured findings.
-- One `recitation_analysis` row per analysis run (history is kept, rows are
-- never overwritten). Findings belong to the run that produced them.
-- ---------------------------------------------------------------------
CREATE TABLE recitation_analysis (
  analysis_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  recitation_id BIGINT UNSIGNED NOT NULL,
  status ENUM('OK','REJECTED','CANNOT_EVALUATE','REFERENCE_UNAVAILABLE','FAILED') NOT NULL,
  status_reason VARCHAR(500) DEFAULT NULL,
  stt_provider VARCHAR(60) DEFAULT NULL,
  stt_model VARCHAR(60) DEFAULT NULL,
  analysis_model VARCHAR(60) DEFAULT NULL,
  transcript TEXT DEFAULT NULL,
  reference_source VARCHAR(60) DEFAULT NULL,
  reference_verse_keys VARCHAR(255) DEFAULT NULL,
  reference_text TEXT DEFAULT NULL,
  matches_expected_passage TINYINT(1) DEFAULT NULL,
  accuracy_percent DECIMAL(5,2) DEFAULT NULL,
  ai_suggested_score INT DEFAULT NULL,
  ai_summary TEXT DEFAULT NULL,
  ai_feedback TEXT DEFAULT NULL,
  matched_passage_note TEXT DEFAULT NULL,
  correct_word_count SMALLINT UNSIGNED DEFAULT NULL,
  is_quran_confidence DECIMAL(4,3) DEFAULT NULL,
  mixed_passages TINYINT(1) DEFAULT NULL,
  detected_passages VARCHAR(255) DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (analysis_id),
  KEY idx_recitation_analysis_recitation (recitation_id, created_at),
  CONSTRAINT fk_recitation_analysis_recitation
    FOREIGN KEY (recitation_id) REFERENCES recitation (recitation_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE,
  CONSTRAINT chk_recitation_analysis_score
    CHECK (ai_suggested_score IS NULL OR (ai_suggested_score >= 0 AND ai_suggested_score <= 100)),
  CONSTRAINT chk_recitation_analysis_accuracy
    CHECK (accuracy_percent IS NULL OR (accuracy_percent >= 0 AND accuracy_percent <= 100)),
  CONSTRAINT chk_recitation_analysis_quran_confidence
    CHECK (is_quran_confidence IS NULL OR (is_quran_confidence >= 0 AND is_quran_confidence <= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- AI findings are advisory until an instructor decides. `instructor_status`
-- is the verification gate: only ACCEPTED / EDITED / INSTRUCTOR_ADDED rows may
-- ever reach a student. The AI columns are never overwritten by an edit; the
-- instructor's version lives in the `instructor_*` columns beside them.
CREATE TABLE recitation_finding (
  finding_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  analysis_id BIGINT UNSIGNED NOT NULL,
  recitation_id BIGINT UNSIGNED NOT NULL,
  finding_type ENUM('MISSING_WORD','INCORRECT_WORD','EXTRA_WORD','PASSAGE_MISMATCH','PRONUNCIATION_OBSERVATION','OTHER') NOT NULL,
  verse_key VARCHAR(12) DEFAULT NULL,
  word_position SMALLINT UNSIGNED DEFAULT NULL,
  expected_text VARCHAR(255) DEFAULT NULL,
  heard_text VARCHAR(255) DEFAULT NULL,
  explanation TEXT DEFAULT NULL,
  ai_status ENUM('PROPOSED','NOT_APPLICABLE') NOT NULL DEFAULT 'PROPOSED',
  ai_confidence DECIMAL(4,3) DEFAULT NULL,
  instructor_status ENUM('PENDING','ACCEPTED','EDITED','REJECTED','INSTRUCTOR_ADDED') NOT NULL DEFAULT 'PENDING',
  instructor_expected_text VARCHAR(255) DEFAULT NULL,
  instructor_heard_text VARCHAR(255) DEFAULT NULL,
  instructor_explanation TEXT DEFAULT NULL,
  instructor_note TEXT DEFAULT NULL,
  decided_by_instructor_id BIGINT UNSIGNED DEFAULT NULL,
  decided_at TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (finding_id),
  KEY idx_finding_analysis (analysis_id),
  KEY idx_finding_recitation_status (recitation_id, instructor_status),
  KEY idx_finding_instructor (decided_by_instructor_id),
  CONSTRAINT fk_finding_analysis
    FOREIGN KEY (analysis_id) REFERENCES recitation_analysis (analysis_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE,
  CONSTRAINT fk_finding_recitation
    FOREIGN KEY (recitation_id) REFERENCES recitation (recitation_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE,
  CONSTRAINT fk_finding_instructor
    FOREIGN KEY (decided_by_instructor_id) REFERENCES instructor (instructor_id)
    ON UPDATE CASCADE
    ON DELETE SET NULL,
  CONSTRAINT chk_finding_confidence
    CHECK (ai_confidence IS NULL OR (ai_confidence >= 0 AND ai_confidence <= 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE evaluation (
  evaluation_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  recitation_id BIGINT UNSIGNED NOT NULL,
  instructor_id BIGINT UNSIGNED NOT NULL,
  score INT NOT NULL,
  feedback TEXT DEFAULT NULL,
  analysis_id BIGINT UNSIGNED DEFAULT NULL,
  published_at TIMESTAMP NULL DEFAULT NULL,
  created_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (evaluation_id),
  UNIQUE KEY uq_evaluation_recitation (recitation_id),
  KEY idx_evaluation_instructor (instructor_id),
  KEY idx_evaluation_analysis (analysis_id),
  CONSTRAINT fk_evaluation_recitation
    FOREIGN KEY (recitation_id) REFERENCES recitation (recitation_id)
    ON UPDATE CASCADE
    ON DELETE CASCADE,
  CONSTRAINT fk_evaluation_instructor
    FOREIGN KEY (instructor_id) REFERENCES instructor (instructor_id)
    ON UPDATE CASCADE
    ON DELETE RESTRICT,
  CONSTRAINT fk_evaluation_analysis
    FOREIGN KEY (analysis_id) REFERENCES recitation_analysis (analysis_id)
    ON UPDATE CASCADE
    ON DELETE SET NULL,
  CONSTRAINT chk_evaluation_score CHECK (score >= 0 AND score <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_log (
  log_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  actor_user_id BIGINT UNSIGNED DEFAULT NULL,
  actor_role VARCHAR(20) DEFAULT NULL,
  action VARCHAR(100) DEFAULT NULL,
  entity_type VARCHAR(50) DEFAULT NULL,
  entity_id VARCHAR(50) DEFAULT NULL,
  detail TEXT DEFAULT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (log_id),
  KEY idx_audit_actor (actor_user_id),
  CONSTRAINT fk_audit_actor
    FOREIGN KEY (actor_user_id) REFERENCES `user` (user_id)
    ON UPDATE CASCADE
    ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
