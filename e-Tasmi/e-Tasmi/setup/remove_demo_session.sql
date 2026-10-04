-- Remove the local "Demo Tasmi Session" and all linked student-facing records.
-- Safe to re-run: targets only sessions titled exactly "Demo Tasmi Session".
--
-- Docker:
--   Get-Content e-Tasmi\setup\remove_demo_session.sql | docker exec -i etasmi-db mysql -uetasmi -p etasmi
-- The database password is the local Compose value in docker-compose.yml. It is not printed here.

USE etasmi;

START TRANSACTION;

SET @session_id = (
  SELECT session_id FROM tasmi_session WHERE title = 'Demo Tasmi Session' LIMIT 1
);

DELETE ev FROM evaluation ev
  INNER JOIN recitation r ON ev.recitation_id = r.recitation_id
  INNER JOIN enrollment e ON r.enrollment_id = e.enrollment_id
  WHERE e.session_id = @session_id;

DELETE r FROM recitation r
  INNER JOIN enrollment e ON r.enrollment_id = e.enrollment_id
  WHERE e.session_id = @session_id;

DELETE p FROM payment p
  INNER JOIN enrollment e ON p.enrollment_id = e.enrollment_id
  WHERE e.session_id = @session_id;

DELETE FROM attendance WHERE session_id = @session_id;
DELETE FROM session_material WHERE session_id = @session_id;
DELETE FROM enrollment WHERE session_id = @session_id;

DELETE FROM notifications
WHERE message LIKE 'Demo:%'
   OR message LIKE '%Demo Tasmi Session%';

DELETE FROM tasmi_session WHERE session_id = @session_id;

COMMIT;

SELECT COUNT(*) AS remaining_demo_sessions
FROM tasmi_session
WHERE title = 'Demo Tasmi Session';
