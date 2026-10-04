-- Adds the AI Learning Loop foundation: a structured Qur'an passage on the
-- session, persisted recitation analysis runs, persisted structured AI
-- findings with per-finding instructor verification columns, evaluation
-- publication state, and recitation attempt lineage.
--
-- Run once on existing databases. Fresh installs already get everything from
-- etasmi_schema.sql / railway_schema.sql, and the application applies the same
-- changes automatically on startup via setup.DBSeeder.
--
-- Statements are ordered by dependency: new tables first, then the foreign key
-- on evaluation that points at recitation_analysis.

-- 1. Structured passage on the session. `quran_portion` is kept unchanged and
--    remains the display label; these columns are the machine-readable form.
ALTER TABLE tasmi_session
  ADD COLUMN surah_number TINYINT UNSIGNED DEFAULT NULL AFTER quran_portion,
  ADD COLUMN ayah_start SMALLINT UNSIGNED DEFAULT NULL AFTER surah_number,
  ADD COLUMN ayah_end SMALLINT UNSIGNED DEFAULT NULL AFTER ayah_start,
  ADD CONSTRAINT chk_tasmi_session_surah
    CHECK (surah_number IS NULL OR (surah_number >= 1 AND surah_number <= 114)),
  ADD CONSTRAINT chk_tasmi_session_ayah_start
    CHECK (ayah_start IS NULL OR ayah_start >= 1),
  ADD CONSTRAINT chk_tasmi_session_ayah_range
    CHECK (ayah_start IS NULL OR ayah_end IS NULL OR ayah_start <= ayah_end);

-- 2. Recitation attempt lineage. Practice attempts point at the attempt they
--    were practised from. Behaviour is implemented in a later phase.
ALTER TABLE recitation
  ADD COLUMN parent_recitation_id BIGINT UNSIGNED DEFAULT NULL AFTER submission_date,
  ADD COLUMN attempt_number INT UNSIGNED NOT NULL DEFAULT 1 AFTER parent_recitation_id,
  ADD INDEX idx_recitation_parent (parent_recitation_id),
  ADD CONSTRAINT fk_recitation_parent
    FOREIGN KEY (parent_recitation_id) REFERENCES recitation (recitation_id)
    ON UPDATE CASCADE
    ON DELETE SET NULL;

-- 3. One row per analysis run. History is kept; rows are never overwritten.
CREATE TABLE IF NOT EXISTS recitation_analysis (
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

-- 4. Structured findings. AI columns are immutable; an instructor edit is
--    stored in the instructor_* columns beside them. `instructor_status` is
--    the verification gate: only ACCEPTED / EDITED / INSTRUCTOR_ADDED rows may
--    ever reach a student.
CREATE TABLE IF NOT EXISTS recitation_finding (
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

-- 5. Evaluation publication state. `published_at` is the authority boundary
--    used from a later phase; the gate itself is not implemented yet.
--    `created_at` is intentionally left NULL for rows that already exist, so
--    no historical evaluation is given an invented timestamp. New rows are
--    stamped by the application.
ALTER TABLE evaluation
  ADD COLUMN analysis_id BIGINT UNSIGNED DEFAULT NULL AFTER feedback,
  ADD COLUMN published_at TIMESTAMP NULL DEFAULT NULL AFTER analysis_id,
  ADD COLUMN created_at TIMESTAMP NULL DEFAULT NULL AFTER published_at,
  ADD INDEX idx_evaluation_analysis (analysis_id),
  ADD CONSTRAINT fk_evaluation_analysis
    FOREIGN KEY (analysis_id) REFERENCES recitation_analysis (analysis_id)
    ON UPDATE CASCADE
    ON DELETE SET NULL;
