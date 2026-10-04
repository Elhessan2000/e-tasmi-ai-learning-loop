-- e-Tasmi: clear recitation & evaluation demo data only (schema unchanged).
--
-- Removes:
--   - All evaluations (scores & feedback)
--   - All submitted recitations
--   - All student progress rows (completion_rate is derived from evaluations; avoids stale dashboards)
--
-- Preserves:
--   - Users, students, instructors, admins, sessions, enrollments, payments, attendance, etc.
--
-- Optional: delete orphaned audio under your uploads volume (e.g. uploads/recitations/) if you need zero files on disk.
--
-- Docker Compose:
--   Get-Content e-Tasmi/setup/reset_recitation_evaluation_demo.sql -Raw |
--     docker exec -i etasmi-db mysql -uetasmi -p etasmi
-- The database password is the local Compose value in docker-compose.yml. It is not printed here.

USE etasmi;

SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE evaluation;
TRUNCATE TABLE recitation;
TRUNCATE TABLE progress;

SET FOREIGN_KEY_CHECKS = 1;
