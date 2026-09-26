-- Adds the "evaluation reviewed" workflow flag used by the Instructor
-- Evaluation Dashboard.
--
--   NULL      -> session is shown under "Active Evaluations".
--   non-NULL  -> instructor has marked the evaluations reviewed; the session
--               is shown under "Reviewed Sessions" instead. Reopen = clear.
--
-- Run once on existing databases. Fresh installs already get the column from
-- etasmi_schema.sql.

ALTER TABLE tasmi_session
  ADD COLUMN evaluation_reviewed_at TIMESTAMP NULL DEFAULT NULL AFTER status,
  ADD INDEX idx_tasmi_session_eval_reviewed (instructor_id, evaluation_reviewed_at);
