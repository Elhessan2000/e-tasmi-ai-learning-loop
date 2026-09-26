USE etasmi;

SET @student_email = 'student.demo@etasmi.local';
SET @instructor_email = 'instructor.demo@etasmi.local';
SET @session_title = 'Demo Tasmi Session';
SET @demo_password_hash = (SELECT password_hash FROM `user` WHERE email = 'admin@etasmi.com' LIMIT 1);
SET @admin_id = (SELECT admin_id FROM admin ORDER BY admin_id ASC LIMIT 1);

SET @student_user_id = (SELECT user_id FROM `user` WHERE email = @student_email LIMIT 1);
SET @instructor_user_id = (SELECT user_id FROM `user` WHERE email = @instructor_email LIMIT 1);
SET @student_id = (SELECT student_id FROM student WHERE user_id = @student_user_id LIMIT 1);
SET @instructor_id = (SELECT instructor_id FROM instructor WHERE user_id = @instructor_user_id LIMIT 1);
SET @session_id = (SELECT session_id FROM tasmi_session WHERE instructor_id = @instructor_id AND title = @session_title LIMIT 1);
SET @enrollment_id = (SELECT enrollment_id FROM enrollment WHERE student_id = @student_id AND session_id = @session_id LIMIT 1);
SET @recitation_id = (SELECT recitation_id FROM recitation WHERE enrollment_id = @enrollment_id ORDER BY recitation_id DESC LIMIT 1);

DELETE FROM evaluation WHERE recitation_id = @recitation_id;
DELETE FROM recitation WHERE enrollment_id = @enrollment_id;
DELETE FROM payment WHERE enrollment_id = @enrollment_id;
DELETE FROM notifications WHERE user_id IN (@student_user_id, @instructor_user_id) AND message LIKE 'Demo:%';
DELETE FROM progress WHERE student_id = @student_id;
DELETE FROM enrollment WHERE enrollment_id = @enrollment_id;
DELETE FROM tasmi_session WHERE session_id = @session_id;
DELETE FROM instructor WHERE user_id = @instructor_user_id;
DELETE FROM student WHERE user_id = @student_user_id;
DELETE FROM `user` WHERE email IN (@student_email, @instructor_email);

INSERT INTO `user` (
  full_name, email, phone, password_hash, role, is_active, status,
  email_verified, email_verified_at, created_at
)
VALUES
(
  'Demo Student', @student_email, '0111111111', @demo_password_hash, 'STUDENT', 1, 'ACTIVE',
  1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
),
(
  'Demo Instructor', @instructor_email, '0222222222', @demo_password_hash, 'INSTRUCTOR', 1, 'ACTIVE',
  1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);

SET @student_user_id = (SELECT user_id FROM `user` WHERE email = @student_email LIMIT 1);
SET @instructor_user_id = (SELECT user_id FROM `user` WHERE email = @instructor_email LIMIT 1);

INSERT INTO student (user_id, registration_number)
VALUES (@student_user_id, CONCAT('STD-DEMO-', @student_user_id));

INSERT INTO instructor (user_id, title, qualification, qualification_file, verification_status, bio)
VALUES (
  @instructor_user_id,
  'USTADH',
  'B.A. Islamic Studies',
  '/demo/qualifications/demo-instructor.pdf',
  'APPROVED',
  'Demo instructor account for local development and phpMyAdmin verification.'
);

SET @student_id = (SELECT student_id FROM student WHERE user_id = @student_user_id LIMIT 1);
SET @instructor_id = (SELECT instructor_id FROM instructor WHERE user_id = @instructor_user_id LIMIT 1);

-- Demo users only — no sample session, enrollment, or payment rows.
-- Use remove_demo_session.sql to purge any legacy "Demo Tasmi Session" rows.
