-- Cleanup generated audit/test data introduced during assistant verification
-- Date: 2026-04-09
-- Scope:
--   - remove known audit/test/demo users created for verification
--   - remove known audit/test/demo tasmi sessions and all linked records
--   - remove related notifications and audit log activity generated during testing

START TRANSACTION;

-- Known audit/test/demo users
-- 2,3,5,6,7,8,10,11,12,13,17,18,21

-- Known audit/test/demo sessions
-- 1,6,7,8,13,16,17

-- Remove evaluation rows tied to recitations from test/demo enrollments
DELETE FROM evaluation
WHERE recitation_id IN (
    SELECT recitation_id FROM (
        SELECT r.recitation_id
        FROM recitation r
        JOIN enrollment e ON e.enrollment_id = r.enrollment_id
        WHERE e.session_id IN (1,6,7,8,13,16,17)
           OR e.student_id IN (1,3,4,5,6,8,9,10)
    ) AS q
);

-- Remove recitations tied to test/demo enrollments
DELETE FROM recitation
WHERE enrollment_id IN (
    SELECT enrollment_id FROM (
        SELECT e.enrollment_id
        FROM enrollment e
        WHERE e.session_id IN (1,6,7,8,13,16,17)
           OR e.student_id IN (1,3,4,5,6,8,9,10)
    ) AS q
);

-- Remove payments tied to test/demo enrollments
DELETE FROM payment
WHERE enrollment_id IN (
    SELECT enrollment_id FROM (
        SELECT e.enrollment_id
        FROM enrollment e
        WHERE e.session_id IN (1,6,7,8,13,16,17)
           OR e.student_id IN (1,3,4,5,6,8,9,10)
    ) AS q
);

-- Remove attendance tied to test/demo sessions or students
DELETE FROM attendance
WHERE session_id IN (1,6,7,8,13,16,17)
   OR student_id IN (1,3,4,5,6,8,9,10);

-- Remove uploaded materials tied to test/demo sessions
DELETE FROM session_material
WHERE session_id IN (1,6,7,8,13,16,17);

-- Remove notifications for deleted audit/test/demo users
DELETE FROM notifications
WHERE user_id IN (2,3,5,6,7,8,10,11,12,13,17,18,21);

-- Remove notifications generated from deleted sessions or test flows on remaining real users
DELETE FROM notifications
WHERE message LIKE '%Demo Tasmi Session%'
   OR message LIKE '%Instructor Backend Validation Session%'
   OR message LIKE '%Instructor Material Attendance Validation%'
   OR message LIKE '%Sync Test 20260404111710%'
   OR message LIKE '%Sync Test 20260404111723%'
   OR message LIKE '%Ongoing Sync Test 20260404111751%'
   OR message LIKE '%Tagweed%'
   OR message LIKE '%first live session%'
   OR message LIKE '%new class%'
   OR message LIKE '%Enrollment created for Tasmi.%'
   OR message LIKE '%Payment flow created for Tasmi.%'
   OR message LIKE '%Payment confirmed for Tasmi.%'
   OR message LIKE '%Enrollment created for class.%'
   OR message LIKE '%Payment flow created for class.%'
   OR message LIKE '%Payment confirmed for class.%'
   OR message = 'Your Tasmi session is now live. You can join from your enrollments page.'
   OR message = 'Your Tasmi session has been completed. Check for evaluations and feedback.'
   OR message = 'A Tasmi session you were enrolled in has been cancelled by the instructor.';

-- Remove all current audit logs generated during test/audit work
DELETE FROM audit_log;

-- Remove enrollments tied to test/demo sessions or students
DELETE FROM enrollment
WHERE session_id IN (1,6,7,8,13,16,17)
   OR student_id IN (1,3,4,5,6,8,9,10);

-- Remove test/demo sessions
DELETE FROM tasmi_session
WHERE session_id IN (1,6,7,8,13,16,17);

-- Remove progress rows for test/demo students
DELETE FROM progress
WHERE student_id IN (1,3,4,5,6,8,9,10);

-- Remove auth helper rows for test/demo users
DELETE FROM email_verification
WHERE user_id IN (2,3,5,6,7,8,10,11,12,13,17,18,21);

DELETE FROM password_reset
WHERE user_id IN (2,3,5,6,7,8,10,11,12,13,17,18,21);

-- Remove role/profile rows for test/demo users
DELETE FROM admin
WHERE user_id IN (12);

DELETE FROM instructor
WHERE user_id IN (3,13,21);

DELETE FROM student
WHERE user_id IN (2,5,6,7,8,10,17,18);

-- Remove test/demo users themselves
DELETE FROM user
WHERE user_id IN (2,3,5,6,7,8,10,11,12,13,17,18,21);

COMMIT;

SELECT 'remaining_users' AS metric, COUNT(*) AS value FROM user
UNION ALL SELECT 'remaining_sessions', COUNT(*) FROM tasmi_session
UNION ALL SELECT 'remaining_enrollments', COUNT(*) FROM enrollment
UNION ALL SELECT 'remaining_payments', COUNT(*) FROM payment
UNION ALL SELECT 'remaining_notifications', COUNT(*) FROM notifications
UNION ALL SELECT 'remaining_audit_logs', COUNT(*) FROM audit_log;
