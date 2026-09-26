-- ---------------------------------------------------------------------------
-- One-off extra demo student (no UI registration; uses admin password hash)
-- Prerequisite: an admin user admin@etasmi.com must exist (DBSeeder).
--
-- From host (Docker):
--   docker compose exec -T db mysql -u etasmi -petasmi123 etasmi < e-Tasmi/setup/seed_qademo_student.sql
-- Or: paste into phpMyAdmin / MySQL client.
--
-- Login after run:
--   Email:    qademo.student@etasmi.local
--   Password: Admin123!
-- (Same password as admin@etasmi.com and the other demo users in sample_data.sql.)
-- ---------------------------------------------------------------------------

USE etasmi;

SET @qademo_email = 'qademo.student@etasmi.local';
SET @q_uid = (SELECT user_id FROM `user` WHERE email = @qademo_email LIMIT 1);
SET @q_sid = (SELECT student_id FROM student WHERE user_id = @q_uid LIMIT 1);
SET @admin_hash = (SELECT password_hash FROM `user` WHERE email = 'admin@etasmi.com' LIMIT 1);

-- Tear down this email only (re-runnable)
DELETE ev FROM evaluation ev
  INNER JOIN recitation r ON ev.recitation_id = r.recitation_id
  INNER JOIN enrollment e ON r.enrollment_id = e.enrollment_id
  INNER JOIN student s ON e.student_id = s.student_id
  WHERE s.user_id = @q_uid;

DELETE r FROM recitation r
  INNER JOIN enrollment e ON r.enrollment_id = e.enrollment_id
  INNER JOIN student s ON e.student_id = s.student_id
  WHERE s.user_id = @q_uid;

DELETE p FROM payment p
  INNER JOIN enrollment e ON p.enrollment_id = e.enrollment_id
  INNER JOIN student s ON e.student_id = s.student_id
  WHERE s.user_id = @q_uid;

DELETE FROM progress WHERE student_id = @q_sid;
DELETE FROM attendance WHERE student_id = @q_sid;
DELETE FROM notifications WHERE user_id = @q_uid;
DELETE FROM email_verification WHERE user_id = @q_uid;
DELETE FROM password_reset WHERE user_id = @q_uid;
DELETE FROM enrollment WHERE student_id = @q_sid;
DELETE FROM student WHERE user_id = @q_uid;
DELETE FROM `user` WHERE user_id = @q_uid;

-- Insert (fails with empty error if @admin_hash is NULL — run after first app startup)
INSERT INTO `user` (
  full_name, email, phone, password_hash, role, is_active, status,
  email_verified, email_verified_at, created_at
) VALUES (
  'QA Demo Student', @qademo_email, '0100000000', @admin_hash, 'STUDENT', 1, 'ACTIVE',
  1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

SET @q_uid = LAST_INSERT_ID();

INSERT INTO student (user_id, registration_number)
VALUES (@q_uid, CONCAT('STD-QA-', @q_uid));
