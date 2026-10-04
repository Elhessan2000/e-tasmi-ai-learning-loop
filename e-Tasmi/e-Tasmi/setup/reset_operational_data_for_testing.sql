-- e-Tasmi: wipe operational / demo data — database rows only (schema unchanged).
--
-- Removes:
--   sessions (all statuses), enrollments, payments, recitations, evaluations (feedback),
--   attendance, session materials, notifications, student progress aggregates,
--   audit log, pending password-reset and email-verification rows.
--
-- Preserves:
--   user, student, instructor, admin, instructor_payment_settings (accounts and QR payment config).
--
-- After run: dashboards and history should be empty; instructors can create new sessions.
--
-- Optional: old uploaded files (audio, receipts, PDFs) may still exist under the app
-- upload volume; delete that folder if you need zero files on disk.
--
-- Usage (Docker Compose):
--   Get-Content e-Tasmi/setup/reset_operational_data_for_testing.sql -Raw |
--     docker exec -i etasmi-db mysql -uetasmi -p etasmi
-- The database password is the local Compose value in docker-compose.yml. It is not printed here.

USE etasmi;

SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;

-- Child / dependent tables first; TRUNCATE clears rows and resets AUTO_INCREMENT.
TRUNCATE TABLE evaluation;
TRUNCATE TABLE recitation;
TRUNCATE TABLE payment;
TRUNCATE TABLE attendance;
TRUNCATE TABLE session_material;
TRUNCATE TABLE enrollment;
TRUNCATE TABLE tasmi_session;

TRUNCATE TABLE notifications;
TRUNCATE TABLE progress;
TRUNCATE TABLE audit_log;

TRUNCATE TABLE password_reset;
TRUNCATE TABLE email_verification;

SET FOREIGN_KEY_CHECKS = 1;

-- End of reset
