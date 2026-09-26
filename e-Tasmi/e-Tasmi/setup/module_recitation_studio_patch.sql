-- =====================================================================
-- Module patch: Recitation Studio
-- Adds the standalone live-recording / upload workflow tables.
--
-- Safe to run on an existing e-Tasmi database. These tables are also
-- created lazily at runtime by RecitationSubmissionService (ensureSchema),
-- so this patch is optional but recommended for explicit migrations.
--
-- Usage (MySQL):
--   USE etasmi;
--   SOURCE module_recitation_studio_patch.sql;
-- =====================================================================

CREATE TABLE IF NOT EXISTS recitation_language (
  language_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  name VARCHAR(80) NOT NULL,
  code VARCHAR(12) DEFAULT NULL,
  is_active TINYINT(1) NOT NULL DEFAULT 1,
  sort_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (language_id),
  UNIQUE KEY uq_recitation_language_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO recitation_language (name, code, sort_order) VALUES
  ('Arabic', 'ar', 1),
  ('English', 'en', 2),
  ('Malay', 'ms', 3),
  ('Urdu', 'ur', 4),
  ('Turkish', 'tr', 5),
  ('Indonesian', 'id', 6);

CREATE TABLE IF NOT EXISTS recitation_submissions (
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
