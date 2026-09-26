# e-Tasmi Reset Notes

## What this reset does
- Moves local development away from XAMPP + NetBeans assumptions.
- Uses Docker containers for MySQL, Tomcat, and phpMyAdmin.
- Defines one clean SQL schema in `e-Tasmi/setup/etasmi_schema.sql`.
- Adds a repeatable sample dataset in `e-Tasmi/setup/sample_data.sql`.
- Keeps the current Java/JSP code as the starting point, but documents the biggest issues before deeper refactoring.

## What I found in the current project

### 1. Source and generated files are mixed together
- `e-Tasmi/src/` contains the real source code.
- `e-Tasmi/build/` contains generated output and compiled classes that should not be treated as source.
- `e-Tasmi/nbproject/` is NetBeans project metadata and includes machine-specific settings.

### 2. The database is one of the main reasons the app feels broken
- `db/etasmi.sql` is a phpMyAdmin dump with old seed data and XAMPP-specific file paths.
- The old schema file was corrupted and duplicated sections.
- Several Java classes expect columns/tables that were missing from the schema.

### 3. Schema and code are currently out of sync
Examples found during the scan:
- `UserDaoJdbc` and `EmailVerificationService` expect `email_verified`, `email_verified_at`, `email_verification_token_hash`, and `email_verification_token_expires_at` in `user`.
- `TasmiSessionDaoJdbc` expects `description` and `capacity` in `tasmi_session`.
- `ProgressDaoJdbc`, `RecitationDaoJdbc`, `EvaluationDaoJdbc`, and `PasswordResetDaoJdbc` expect `progress`, `recitation`, `evaluation`, `password_reset`, and `email_verification` tables.
- The old SQL baseline did not consistently provide those structures.

### 4. Core fixes already applied
- Fixed session notification writes so `notifications.user_id` now stores the real `user.user_id`, not `student.student_id`.
- Implemented the previously unimplemented `TasmiSessionDaoJdbc.updateStatusAndZoomByIdAndInstructorId(...)` method.
- Fixed auth-state mapping so `user.is_active` is now loaded into the Java model and enforced during login.
- Added login blocking for deactivated accounts and unverified-email accounts.
- Added clean sample records to verify the main relationships in phpMyAdmin.

## Current auth behavior
- Students now register as unverified users and must verify email before login.
- Instructors now register as unverified users, then remain `status=INACTIVE` with `verification_status=PENDING` until admin approval even after email verification.
- Pending or rejected instructors can still reach the pending-approval experience.
- Users with `is_active = 0` are blocked from logging in.
- Users with `email_verified = 0` are blocked from logging in.

## Email verification flow
- Registration now sends a 6-digit verification code by email.
- The user is taken directly to the verification page after signup.
- Resend verification continues to work for existing unverified accounts.
- Real delivery requires SMTP credentials in Docker environment variables or a local `.env` file.

## New local baseline

### Containers
- `docker-compose.yml` starts:
  - `db`: MySQL 8.4
  - `app`: Tomcat 9 with the app built from the current source
  - `phpmyadmin`: browser-based DB UI
- `Dockerfile` builds the app and deploys the generated WAR into Tomcat.

### Database source of truth
- Use `e-Tasmi/setup/etasmi_schema.sql` as the only baseline schema going forward.
- Use `e-Tasmi/setup/sample_data.sql` for repeatable local demo data.
- Treat `db/etasmi.sql` as an old dump for reference only, not as the setup file.

## Sample data now loaded
The local MySQL container now contains demo records for:
- one admin user
- one demo student user + student profile
- one demo instructor user + instructor profile
- one demo session
- one approved enrollment
- one successful payment
- one notification for the student user
- one recitation
- one evaluation
- one progress row

Demo accounts use the same password as the seeded admin account:
- `Admin123!`

Demo emails:
- `student.demo@etasmi.local`
- `instructor.demo@etasmi.local`

## Where to inspect the data
- App: `http://localhost:8080/`
- phpMyAdmin: `http://localhost:8081/`

phpMyAdmin login:
- Host: `db`
- Username: `etasmi`
- Password: `etasmi123`

## Recommended next rebuild order
1. Repair instructor session actions and student enrollment flows end to end in the browser.
2. Validate recitation, evaluation, progress, and payments with real UI testing.
3. Clean admin user management behavior and remaining auth edge cases.
4. Remove dead code and generated artifacts from version control.
5. Add simple test coverage around DAO/service logic after the schema is stable.
