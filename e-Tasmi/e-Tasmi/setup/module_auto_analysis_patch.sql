-- Automatic post-submission AI analysis job state on recitation rows.
-- Fresh installs include this column in etasmi_schema.sql; DBSeeder applies it on startup too.

ALTER TABLE recitation
  ADD COLUMN analysis_job_state ENUM('NONE','IN_PROGRESS','COMPLETED') NOT NULL DEFAULT 'NONE'
  AFTER attempt_number;

UPDATE recitation r
SET analysis_job_state = 'COMPLETED'
WHERE r.analysis_job_state = 'NONE'
  AND EXISTS (SELECT 1 FROM recitation_analysis a WHERE a.recitation_id = r.recitation_id);

ALTER TABLE recitation
  ADD COLUMN analysis_job_started_at TIMESTAMP NULL DEFAULT NULL
  AFTER analysis_job_state;
