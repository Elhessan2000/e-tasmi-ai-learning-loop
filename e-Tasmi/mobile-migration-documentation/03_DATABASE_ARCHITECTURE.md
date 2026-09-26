# 03 — DATABASE ARCHITECTURE

**Authoritative schema file:** `e-Tasmi/setup/etasmi_schema.sql` (20 tables).
**Engine:** MySQL 8.x, InnoDB, `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci` on every table.
**Rule:** the mobile migration must preserve this schema. Table names, column names, and column meanings must not change.

---

## 1. Connection Strategy

`util/Db.getConnection()` resolves a connection in this exact precedence order. The **first** strategy that yields a usable connection wins.

| # | Strategy | Variables | Notes |
|---|---|---|---|
| 1 | Railway discrete variables | `MYSQLHOST`, `MYSQLPORT` (default `3306`), `MYSQLDATABASE`, `MYSQLUSER`, `MYSQLPASSWORD` | An empty password is treated as valid. Builds the JDBC URL dynamically. |
| 2 | Combined Railway URL | `MYSQL_URL` (`mysql://user:pass@host:port/db`) | Parsed into JDBC form |
| 3 | Explicit JDBC | env `ETASMI_JDBC_URL` / `ETASMI_JDBC_USER` / `ETASMI_JDBC_PASSWORD`, or system properties `etasmi.jdbc.url` / `.user` / `.password` | Used by local Docker Compose |
| 4 | JNDI DataSource | `java:comp/env/jdbc/ETasmiDS` declared in `web.xml` `<resource-ref>` and `META-INF/context.xml` | Backed by `util/RailwayDataSourceFactory`, which itself reads the `MYSQL*` variables at runtime |

**Connection pooling:** only strategy 4 pools (Tomcat JDBC pool via `context.xml`). Strategies 1–3 open a **new `DriverManager` connection per call**, closed by try-with-resources in the DAO/service. Since the Railway path takes precedence, **the production deployment is effectively unpooled.** This is a meaningful scalability consideration for the migration.

**TLS:** the compose JDBC URL uses `useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`. Encryption to the database is not enforced.

---

## 2. Schema Creation and Migration at Runtime

Three separate mechanisms create or alter schema. Understanding the split is essential, because a database's actual shape depends on which path it took.

| Mechanism | Trigger | Behavior |
|---|---|---|
| **`util/SchemaBootstrap`** (`@WebListener`) | Every application start | Checks whether the table `user` exists. If **missing**, executes `WEB-INF/db/railway_schema.sql` in full (all 20 tables). If **present**, logs and exits — **it performs no incremental migration whatsoever.** Also calls `QuranBundledCatalog.init(ctx)`. |
| **`setup/DBSeeder`** (`@WebListener`) | Every application start | Performs the **incremental** work: adds missing columns, creates missing tables, repairs admin rows, seeds `admin@etasmi.com` (password `Admin123!`) when no admin exists, and — if `ETASMI_BOOTSTRAP_ADMIN_PASSWORD` is set — resets that admin's password on **every** boot. |
| **Recitation Studio DAO `ensureSchema`** | First use of the Recitation Studio DAOs | Lazily creates `recitation_language` / `recitation_submissions` if absent |
| **Docker Compose init** | First creation of the `db` volume only | Mounts `e-Tasmi/setup/etasmi_schema.sql` as `/docker-entrypoint-initdb.d/01_schema.sql` |

### ⚠ Migration-critical consequence

`DBSeeder` does **not** add every column present in `etasmi_schema.sql`. Notably absent from its incremental patches are `tasmi_session.description`, `tasmi_session.level`, `tasmi_session.capacity`, and `tasmi_session.banner_image_url`. A long-lived database that was created before those columns existed and has only ever been migrated by `DBSeeder` **may be missing them**. Before migrating, run a live `SHOW COLUMNS` comparison against `etasmi_schema.sql` rather than assuming the file matches production.

Additionally, `SchemaBootstrap`'s SQL runner is naive: it strips full-line `--` comments and splits statements on a line-terminating `;`. It cannot handle stored procedures or `DELIMITER` changes. The current schema contains neither, so it works — but any future schema addition must respect that constraint.

---

## 3. Table Reference

### 3.1 `user` — root identity for all three roles

| Column | Type | Null | Default | Key | Meaning |
|---|---|---|---|---|---|
| `user_id` | BIGINT UNSIGNED AI | NO | — | **PK** | Identity |
| `full_name` | VARCHAR(150) | NO | — | | Display name |
| `email` | VARCHAR(191) | NO | — | **UNIQUE** `uq_user_email` | Login identifier, always stored lowercased |
| `phone` | VARCHAR(30) | NO | — | | Contact number |
| `profile_image_url` | VARCHAR(500) | YES | NULL | | Cloudinary secure URL or `/uploads/profile/...` |
| `profile_image_updated_at` | TIMESTAMP | YES | NULL | | Used for cache-busting the photo redirect |
| `password_hash` | VARCHAR(255) | NO | — | | BCrypt cost 12, or legacy PBKDF2 |
| `role` | ENUM('ADMIN','INSTRUCTOR','STUDENT') | NO | — | | Authorization role |
| `is_active` | TINYINT(1) | NO | 1 | | Admin activate/deactivate switch |
| `status` | ENUM('ACTIVE','INACTIVE','DELETED') | NO | 'ACTIVE' | | Lifecycle. `DELETED` = soft-deleted; every query filters `status <> 'DELETED'` |
| `email_verified` | TINYINT(1) | NO | 0 | | Verification gate for login |
| `email_verified_at` | TIMESTAMP | YES | NULL | | |
| `email_verification_token_hash` | VARCHAR(64) | YES | NULL | | SHA-256 of the 24-hour link token |
| `email_verification_token_expires_at` | TIMESTAMP | YES | NULL | | |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | Registration date; used in reports |

*Note:* registration inserts `status = 'INACTIVE'` explicitly, overriding the column default.

---

### 3.2 `student`

| Column | Type | Null | Default | Key | Meaning |
|---|---|---|---|---|---|
| `student_id` | BIGINT UNSIGNED AI | NO | — | **PK** | |
| `user_id` | BIGINT UNSIGNED | NO | — | **UNIQUE** `uq_student_user`, **FK** → `user.user_id` | 1:1 with user |
| `registration_number` | VARCHAR(50) | NO | — | **UNIQUE** `uq_student_regno` | Generated as `"STD-" + userId` |
| `level` | ENUM('PRIMARY_SCHOOL','SECONDARY_SCHOOL','HIGH_SCHOOL','UNIVERSITY') | NO | 'PRIMARY_SCHOOL' | | Drives session visibility and enrollment eligibility |

**FK:** `fk_student_user` → `user(user_id)` **ON UPDATE CASCADE ON DELETE CASCADE**

---

### 3.3 `instructor`

| Column | Type | Null | Default | Key | Meaning |
|---|---|---|---|---|---|
| `instructor_id` | BIGINT UNSIGNED AI | NO | — | **PK** | |
| `user_id` | BIGINT UNSIGNED | NO | — | **UNIQUE** `uq_instructor_user`, **FK** → `user.user_id` | 1:1 with user |
| `title` | ENUM('SHEIKH','USTADH') | YES | NULL | | Honorific. ⚠ The profile UI offers "Austaz"/"Astazh" as a `namePrefix`, which does not map to these enum values |
| `qualification` | VARCHAR(150) | YES | NULL | | Free-text credential, editable in the profile |
| `qualification_file` | VARCHAR(255) | YES | NULL | | Local absolute path or Cloudinary URL to the PDF |
| `verification_status` | ENUM('PENDING','APPROVED','REJECTED') | NO | 'PENDING' | | **The instructor access gate** |
| `bio` | TEXT | YES | NULL | | Required at registration |
| `zoom_email` | VARCHAR(200) | YES | NULL | | Preferred Zoom host; falls back to `ZOOM_DEFAULT_HOST_EMAIL` |
| `payment_account_holder` | VARCHAR(150) | YES | NULL | | **LEGACY** — superseded by `instructor_payment_settings` |
| `payment_method_name` | VARCHAR(120) | YES | NULL | | **LEGACY** |
| `payment_account_details` | VARCHAR(255) | YES | NULL | | **LEGACY** |
| `payment_qr_url` | VARCHAR(1024) | YES | NULL | | **LEGACY** |
| `payment_instructions` | TEXT | YES | NULL | | **LEGACY** |

**FK:** `fk_instructor_user` → `user(user_id)` **ON UPDATE CASCADE ON DELETE CASCADE**

---

### 3.4 `instructor_payment_settings` — the live payment configuration

| Column | Type | Null | Default | Key | Meaning |
|---|---|---|---|---|---|
| `settings_id` | BIGINT UNSIGNED AI | NO | — | **PK** | |
| `instructor_id` | BIGINT UNSIGNED | NO | — | **UNIQUE** `uq_instructor_payment_settings`, **FK** | One settings row per instructor |
| `qr_image_url` | VARCHAR(1024) | YES | NULL | | DuitNow/bank QR image shown to students |
| `bank_name` | VARCHAR(120) | YES | NULL | | |
| `account_holder_name` | VARCHAR(150) | YES | NULL | | |
| `account_number` | VARCHAR(64) | YES | NULL | | |
| `payment_notes` | VARCHAR(1000) | YES | NULL | | |
| `is_active` | TINYINT(1) | NO | 1 | | |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |
| `updated_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | | |

**Business rule (`isUsable()`):** active **and** (a QR image **or** both bank name and account holder). If not usable, students cannot upload a receipt for that instructor's sessions.

**FK:** → `instructor(instructor_id)` **ON UPDATE CASCADE ON DELETE CASCADE**

---

### 3.5 `admin`

| Column | Type | Null | Default | Key | Meaning |
|---|---|---|---|---|---|
| `admin_id` | BIGINT UNSIGNED AI | NO | — | **PK** | |
| `user_id` | BIGINT UNSIGNED | NO | — | **UNIQUE** `uq_admin_user`, **FK** | 1:1 with user |

Deliberately minimal — all admin identity lives in `user`.
**FK:** → `user(user_id)` **ON UPDATE CASCADE ON DELETE CASCADE**

---

### 3.6 `tasmi_session` — the central business entity

| Column | Type | Null | Default | Key | Meaning |
|---|---|---|---|---|---|
| `session_id` | BIGINT UNSIGNED AI | NO | — | **PK** | |
| `instructor_id` | BIGINT UNSIGNED | NO | — | **FK**, idx `idx_tasmi_session_instructor` | Owner |
| `title` | VARCHAR(150) | NO | — | | |
| `description` | TEXT | YES | NULL | | ⚠ Not added by `DBSeeder` |
| `level` | ENUM(4 values) | NO | 'PRIMARY_SCHOOL' | | Target student level; must match the student's level for visibility. ⚠ Not added by `DBSeeder` |
| `session_date` | DATE | NO | — | idx `idx_tasmi_session_date` | |
| `session_time` | TIME | NO | — | | Interpreted in `APP_ZONE` (default `Asia/Kuala_Lumpur`) |
| `duration_minutes` | INT UNSIGNED | NO | 60 | | UI range 15–360 |
| `quran_portion` | VARCHAR(150) | YES | NULL | | **Expected text for AI evaluation.** AI analysis is disabled when this is null |
| `mode` | ENUM('ONLINE','PHYSICAL') | NO | — | | Always written as `ONLINE`; students only see `ONLINE` |
| `fee` | DECIMAL(10,2) | NO | 0.00 | | `0` means free → enrollment auto-approves |
| `capacity` | INT UNSIGNED | NO | 1 | | `0` disables the capacity check. ⚠ Not added by `DBSeeder` |
| `live_provider` | VARCHAR(30) | YES | NULL | | `'ZOOM'` or `'MANUAL'` |
| `meeting_link` | VARCHAR(1024) | YES | NULL | | Participant join URL |
| `meeting_password` | VARCHAR(120) | YES | NULL | | Zoom passcode |
| `is_password_visible` | TINYINT(1) | NO | 0 | | Toggled by the UI-less `togglepassword` action |
| `live_started_at` | TIMESTAMP | YES | NULL | | Set on `start` |
| `live_ended_at` | TIMESTAMP | YES | NULL | | Set on `complete` |
| `recording_status` | VARCHAR(30) | YES | NULL | | **UNUSED** — meetings are created with `auto_recording="none"` |
| `recording_url` | VARCHAR(1024) | YES | NULL | | **UNUSED** |
| `recording_synced_at` | TIMESTAMP | YES | NULL | | **UNUSED** |
| `zoom_meeting_id` | BIGINT | YES | NULL | | Required for SDK embed |
| `zoom_start_url` | VARCHAR(2000) | YES | NULL | | Host start URL (sensitive — grants host rights) |
| `banner_image_url` | VARCHAR(1024) | YES | NULL | | ⚠ Not added by `DBSeeder` |
| `status` | ENUM('SCHEDULED','ONGOING','COMPLETED','CANCELLED') | NO | 'SCHEDULED' | | Lifecycle |
| `evaluation_reviewed_at` | TIMESTAMP | YES | NULL | idx `idx_tasmi_session_eval_reviewed (instructor_id, evaluation_reviewed_at)` | **Independent of `status`.** NULL → "Active Evaluations" tab; non-NULL → "Reviewed Sessions" tab. Reopening sets it back to NULL |

**FK:** `fk_tasmi_session_instructor` → `instructor(instructor_id)` **ON UPDATE CASCADE ON DELETE RESTRICT** — an instructor row cannot be removed while sessions exist.

---

### 3.7 `enrollment`

| Column | Type | Null | Default | Key | Meaning |
|---|---|---|---|---|---|
| `enrollment_id` | BIGINT UNSIGNED AI | NO | — | **PK** | |
| `student_id` | BIGINT UNSIGNED | NO | — | **FK**, part of unique | |
| `session_id` | BIGINT UNSIGNED | NO | — | **FK**, idx `idx_enrollment_session` | |
| `enrollment_status` | ENUM('PENDING','APPROVED','REJECTED','CANCELLED') | NO | 'PENDING' | | |

**UNIQUE:** `uq_enrollment_student_session (student_id, session_id)` — **this is what prevents duplicate enrollment.** A student who re-enrolls in a session they previously cancelled has their existing row reactivated to `PENDING` rather than a new row inserted.

**FKs:** → `student(student_id)` **ON DELETE RESTRICT**; → `tasmi_session(session_id)` **ON DELETE RESTRICT**.
The RESTRICT on `session_id` is why deleting a session that has any enrollment fails at the database level.

---

### 3.8 `payment`

| Column | Type | Null | Default | Key | Meaning |
|---|---|---|---|---|---|
| `payment_id` | BIGINT UNSIGNED AI | NO | — | **PK** | |
| `enrollment_id` | BIGINT UNSIGNED | NO | — | **UNIQUE** `uq_payment_enrollment`, **FK** | **One payment per enrollment** |
| `amount` | DECIMAL(10,2) | NO | — | | Copied from `tasmi_session.fee` at creation |
| `payment_status` | ENUM('PENDING','AWAITING_VERIFICATION','APPROVED','REJECTED') | NO | 'PENDING' | idx `idx_payment_status` | |
| `payment_date` | TIMESTAMP | YES | NULL | | Set on approval; the `payments` report filters on `DATE(payment_date)` |
| `verified_by_admin_id` | BIGINT UNSIGNED | YES | NULL | **FK** → `admin`, idx | **NEVER WRITTEN** — no admin verification path exists |
| `receipt_file_path` | VARCHAR(1024) | YES | NULL | | Cloudinary URL or `/uploads/receipts/...` |
| `receipt_submitted_at` | TIMESTAMP | YES | NULL | | |
| `verified_by_instructor_id` | BIGINT UNSIGNED | YES | NULL | **FK** → `instructor`, idx | The actual verifier |
| `verification_note` | VARCHAR(500) | YES | NULL | | Rejection reason (required on reject) |
| `currency` | VARCHAR(3) | NO | 'MYR' | | Malaysian Ringgit; displayed as `"RM "` |
| `payment_reference` | VARCHAR(120) | YES | NULL | | Student-entered reference — accepted by the servlet, no UI field |
| `student_note` | VARCHAR(500) | YES | NULL | | Same |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |

**FKs:** → `enrollment` **ON DELETE CASCADE**; → `admin(admin_id)` **ON DELETE SET NULL**; → `instructor(instructor_id)` **ON DELETE SET NULL**.

---

### 3.9 `payment_verification_history` — payment audit trail

| Column | Type | Null | Default | Key | Meaning |
|---|---|---|---|---|---|
| `history_id` | BIGINT UNSIGNED AI | NO | — | **PK** | |
| `payment_id` | BIGINT UNSIGNED | NO | — | **FK**, idx | |
| `actor_user_id` | BIGINT UNSIGNED | YES | NULL | | Who acted (no FK constraint) |
| `actor_role` | VARCHAR(20) | YES | NULL | | `STUDENT` or `INSTRUCTOR` |
| `action` | VARCHAR(20) | NO | — | | `SUBMITTED`, `RESUBMITTED`, `APPROVED`, `REJECTED` |
| `from_status` | VARCHAR(30) | YES | NULL | | |
| `to_status` | VARCHAR(30) | YES | NULL | | |
| `reason` | VARCHAR(500) | YES | NULL | | |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |

Rendered as the student's payment timeline and in the admin payment detail drawer.
**FK:** → `payment(payment_id)` **ON DELETE CASCADE**

---

### 3.10 `notifications`

| Column | Type | Null | Default | Key | Meaning |
|---|---|---|---|---|---|
| `notification_id` | BIGINT UNSIGNED AI | NO | — | **PK** | |
| `user_id` | BIGINT UNSIGNED | NO | — | **FK**, idx `idx_notifications_user` | Recipient |
| `message` | VARCHAR(255) | NO | — | | Plain text, pre-rendered in English |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |

**There is no `is_read` column, no type/category column, and no deep-link column.** Notifications cannot be marked read, filtered by type, or navigated from. Any mobile notification feature must either accept this or add columns (an additive, non-breaking change).
**FK:** → `user(user_id)` **ON DELETE CASCADE**

---

### 3.11 `progress`

| Column | Type | Null | Default | Key | Meaning |
|---|---|---|---|---|---|
| `student_id` | BIGINT UNSIGNED | NO | — | **PK** + **FK** | One row per student; the student ID *is* the primary key |
| `completion_rate` | DECIMAL(5,2) | NO | 0.00 | | `AVG(evaluation.score)` over all the student's evaluated recitations |
| `last_updated` | TIMESTAMP | NO | CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP | | |

**CHECK:** `chk_progress_completion_rate` — `completion_rate >= 0 AND completion_rate <= 100`
**FK:** → `student(student_id)` **ON DELETE CASCADE**

Despite the name, this table holds a single averaged score. All other "progress" numbers on the student progress page are computed live per request and are not stored.

---

### 3.12 `email_verification`

| Column | Type | Null | Default | Key | Meaning |
|---|---|---|---|---|---|
| `user_id` | BIGINT UNSIGNED | NO | — | **PK** + **FK** | One pending code per user |
| `code_hash` | VARCHAR(64) | NO | — | | SHA-256 of `userId + ":" + code` |
| `expires_at` | TIMESTAMP | NO | — | | 10 minutes after creation |
| `attempts` | INT | NO | 0 | | Max 5, then the code is refused |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |

The row is deleted on successful verification.
**FK:** → `user(user_id)` **ON DELETE CASCADE**

---

### 3.13 `password_reset`

| Column | Type | Null | Default | Key | Meaning |
|---|---|---|---|---|---|
| `reset_id` | BIGINT UNSIGNED AI | NO | — | **PK** | |
| `user_id` | BIGINT UNSIGNED | NO | — | **FK**, idx `idx_password_reset_user` | |
| `token_hash` | VARCHAR(64) | NO | — | **UNIQUE** `uq_password_reset_token` | SHA-256 of a 32-byte random token |
| `expires_at` | TIMESTAMP | NO | — | | 30 minutes |
| `used_at` | TIMESTAMP | YES | NULL | | Non-NULL = consumed (single-use) |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |

Active-token predicate: `used_at IS NULL AND expires_at > CURRENT_TIMESTAMP`.
**FK:** → `user(user_id)` **ON DELETE CASCADE**

---

### 3.14 `recitation` — the LIVE recitation table

| Column | Type | Null | Default | Key | Meaning |
|---|---|---|---|---|---|
| `recitation_id` | BIGINT UNSIGNED AI | NO | — | **PK** | |
| `enrollment_id` | BIGINT UNSIGNED | NO | — | **FK**, idx `idx_recitation_enrollment` | **Enrollment-bound** — this is how a recitation is tied to a session and instructor |
| `audio_file_path` | VARCHAR(255) | NO | — | | Cloudinary URL or `/uploads/recitations/...`. ⚠ 255 chars is tight for long Cloudinary URLs |
| `submission_date` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |

A student may submit **multiple** recitations per enrollment (no unique constraint).
**FK:** → `enrollment(enrollment_id)` **ON DELETE CASCADE**

---

### 3.15 `recitation_language` — Recitation Studio v2 (not wired to the UI)

| Column | Type | Null | Default | Key |
|---|---|---|---|---|
| `language_id` | BIGINT UNSIGNED AI | NO | — | **PK** |
| `name` | VARCHAR(80) | NO | — | **UNIQUE** `uq_recitation_language_name` |
| `code` | VARCHAR(12) | YES | NULL | |
| `is_active` | TINYINT(1) | NO | 1 | |
| `sort_order` | INT | NO | 0 | |

Seeded by the schema with: Arabic (`ar`, 1), English (`en`, 2), Malay (`ms`, 3), Urdu (`ur`, 4), Turkish (`tr`, 5), Indonesian (`id`, 6).

---

### 3.16 `recitation_submissions` — Recitation Studio v2 (not wired to the UI)

| Column | Type | Null | Default | Key | Meaning |
|---|---|---|---|---|---|
| `recitation_id` | BIGINT UNSIGNED AI | NO | — | **PK** | |
| `student_id` | BIGINT UNSIGNED | NO | — | **FK** → **`user.user_id`**, idx | ⚠ **References `user`, not `student`** — inconsistent with every other table |
| `file_path` | VARCHAR(500) | YES | NULL | | |
| `topic_text` | VARCHAR(255) | YES | NULL | | |
| `language_id` | BIGINT UNSIGNED | YES | NULL | **FK**, idx | |
| `module_label` | VARCHAR(150) | YES | NULL | | |
| `status` | ENUM('PENDING','EVALUATED') | NO | 'PENDING' | | |
| `duration_seconds` | INT | YES | NULL | | |
| `submitted_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |

**Not connected to any live UI.** The student Recitation Studio writes to `recitation` (3.14). Keep the table; do not build against it without a product decision.
**FKs:** → `user(user_id)` **ON DELETE CASCADE**; → `recitation_language(language_id)` **ON DELETE SET NULL**

---

### 3.17 `attendance`

| Column | Type | Null | Default | Key | Meaning |
|---|---|---|---|---|---|
| `attendance_id` | BIGINT UNSIGNED AI | NO | — | **PK** | |
| `session_id` | BIGINT UNSIGNED | NO | — | **FK**, part of unique | |
| `student_id` | BIGINT UNSIGNED | NO | — | **FK**, part of unique | |
| `marked_by_instructor_id` | BIGINT UNSIGNED | NO | — | **FK**, idx | Always the session's instructor, even though the mark is automatic |
| `attendance_status` | ENUM('PRESENT','ABSENT') | NO | — | | Only `PRESENT` is ever written |
| `notes` | VARCHAR(255) | YES | NULL | | Always *"Auto-recorded when the student opened the live session join link."* |
| `marked_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |

**UNIQUE:** `uq_attendance_session_student (session_id, student_id)` — enables the UPSERT on repeated joins.
**FKs:** → `tasmi_session` **ON DELETE CASCADE**; → `student` **ON DELETE CASCADE**; → `instructor` **ON DELETE RESTRICT**

---

### 3.18 `session_material` — schema present, upload not implemented

| Column | Type | Null | Default | Key |
|---|---|---|---|---|
| `material_id` | BIGINT UNSIGNED AI | NO | — | **PK** |
| `session_id` | BIGINT UNSIGNED | NO | — | **FK**, idx `idx_material_session` |
| `uploaded_by_instructor_id` | BIGINT UNSIGNED | NO | — | **FK**, idx `idx_material_instructor` |
| `title` | VARCHAR(150) | NO | — | |
| `file_path` | VARCHAR(255) | NO | — | |
| `file_type` | VARCHAR(120) | YES | NULL | |
| `uploaded_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | |

`SessionMaterialDaoJdbc.insert()` has **zero call sites**. Only `/instructor/materials/open` reads the table.
**FKs:** → `tasmi_session` **ON DELETE CASCADE**; → `instructor` **ON DELETE RESTRICT**

---

### 3.19 `evaluation`

| Column | Type | Null | Default | Key | Meaning |
|---|---|---|---|---|---|
| `evaluation_id` | BIGINT UNSIGNED AI | NO | — | **PK** | |
| `recitation_id` | BIGINT UNSIGNED | NO | — | **UNIQUE** `uq_evaluation_recitation`, **FK** | **One evaluation per recitation — enforced by the database** |
| `instructor_id` | BIGINT UNSIGNED | NO | — | **FK**, idx `idx_evaluation_instructor` | Grader |
| `score` | INT | NO | — | | **0–100, enforced by CHECK** |
| `feedback` | TEXT | YES | NULL | | Free text |

**CHECK:** `chk_evaluation_score` — `score >= 0 AND score <= 100`
**FKs:** → `recitation(recitation_id)` **ON DELETE CASCADE**; → `instructor(instructor_id)` **ON DELETE RESTRICT**

**No AI output is stored here.** There is no column for a transcript, word-level diff, tajwīd notes, or an AI-suggested score. The AI report lives only in the HTTP session and is discarded.

---

### 3.20 `audit_log`

| Column | Type | Null | Default | Key | Meaning |
|---|---|---|---|---|---|
| `log_id` | BIGINT UNSIGNED AI | NO | — | **PK** | |
| `actor_user_id` | BIGINT UNSIGNED | YES | NULL | **FK**, idx `idx_audit_actor` | |
| `actor_role` | VARCHAR(20) | YES | NULL | | |
| `action` | VARCHAR(100) | YES | NULL | | One of 7 values (see below) |
| `entity_type` | VARCHAR(50) | YES | NULL | | `USER` or `INSTRUCTOR` |
| `entity_id` | VARCHAR(50) | YES | NULL | | |
| `detail` | TEXT | YES | NULL | | |
| `created_at` | TIMESTAMP | NO | CURRENT_TIMESTAMP | | |

**Only 7 actions are ever written:** `USER_CREATED`, `USER_UPDATED`, `USER_ACTIVATED`, `USER_DEACTIVATED`, `USER_DELETED`, `INSTRUCTOR_APPROVED`, `INSTRUCTOR_REJECTED`. Logins, logouts, failed authentication, password resets, session CRUD, payments, and evaluations are **not** audited.

**FK:** → `user(user_id)` **ON DELETE SET NULL**

---

## 4. Enumerations

### MySQL column enums

| Column | Values |
|---|---|
| `user.role` | `ADMIN`, `INSTRUCTOR`, `STUDENT` |
| `user.status` | `ACTIVE`, `INACTIVE`, `DELETED` |
| `student.level`, `tasmi_session.level` | `PRIMARY_SCHOOL`, `SECONDARY_SCHOOL`, `HIGH_SCHOOL`, `UNIVERSITY` |
| `instructor.title` | `SHEIKH`, `USTADH` |
| `instructor.verification_status` | `PENDING`, `APPROVED`, `REJECTED` |
| `tasmi_session.mode` | `ONLINE`, `PHYSICAL` |
| `tasmi_session.status` | `SCHEDULED`, `ONGOING`, `COMPLETED`, `CANCELLED` |
| `enrollment.enrollment_status` | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` |
| `payment.payment_status` | `PENDING`, `AWAITING_VERIFICATION`, `APPROVED`, `REJECTED` |
| `attendance.attendance_status` | `PRESENT`, `ABSENT` |
| `recitation_submissions.status` | `PENDING`, `EVALUATED` |

### Java-only enums (no dedicated column)

| Enum | Values | Where used |
|---|---|---|
| `SessionRecordingStatus` | recording lifecycle states | Maps to the unused `tasmi_session.recording_status` VARCHAR |
| `RecitationSubmissionStatus` | `PENDING`, `EVALUATED` | Mirrors `recitation_submissions.status` |

### Legacy value tolerance

`PaymentStatus.fromString()` maps historical values so old rows still parse:

| Legacy value in DB | Interpreted as |
|---|---|
| `SUCCESS` | `APPROVED` |
| `FAILED` | `REJECTED` |

This is read-side only. New writes always use the current four values. **The admin dashboard payment chips still query the legacy names directly in SQL and therefore report 0** — see doc 02, A2.

---

## 5. Relationships

| Relationship | Cardinality | FK column | Cascade | Business meaning |
|---|---|---|---|---|
| `user` → `student` | 1 : 0..1 | `student.user_id` (UNIQUE) | CASCADE | Student profile extension |
| `user` → `instructor` | 1 : 0..1 | `instructor.user_id` (UNIQUE) | CASCADE | Instructor profile extension |
| `user` → `admin` | 1 : 0..1 | `admin.user_id` (UNIQUE) | CASCADE | Admin profile extension |
| `user` → `notifications` | 1 : N | `notifications.user_id` | CASCADE | Inbox |
| `user` → `email_verification` | 1 : 0..1 | PK/FK | CASCADE | One live code per user |
| `user` → `password_reset` | 1 : N | `password_reset.user_id` | CASCADE | Historic + active tokens |
| `user` → `audit_log` | 1 : N | `audit_log.actor_user_id` | SET NULL | Actions survive user deletion |
| `user` → `recitation_submissions` | 1 : N | `recitation_submissions.student_id` | CASCADE | ⚠ Studio v2 links to `user`, not `student` |
| `instructor` → `instructor_payment_settings` | 1 : 0..1 | UNIQUE FK | CASCADE | Payment configuration |
| `instructor` → `tasmi_session` | 1 : N | `tasmi_session.instructor_id` | **RESTRICT** | An instructor with sessions cannot be hard-deleted |
| `instructor` → `evaluation` | 1 : N | `evaluation.instructor_id` | **RESTRICT** | Grading history is protected |
| `instructor` → `payment` (verifier) | 1 : N | `payment.verified_by_instructor_id` | SET NULL | Who approved |
| `admin` → `payment` (verifier) | 1 : N | `payment.verified_by_admin_id` | SET NULL | **Never populated** |
| `student` ↔ `tasmi_session` | **M : N via `enrollment`** | `student_id` + `session_id`, UNIQUE pair | RESTRICT both sides | The junction table; the unique pair prevents duplicate enrollment |
| `enrollment` → `payment` | 1 : 0..1 | `payment.enrollment_id` (UNIQUE) | CASCADE | Exactly one payment per enrollment |
| `payment` → `payment_verification_history` | 1 : N | `history.payment_id` | CASCADE | Audit timeline |
| `enrollment` → `recitation` | 1 : N | `recitation.enrollment_id` | CASCADE | Multiple submissions allowed |
| `recitation` → `evaluation` | 1 : 0..1 | `evaluation.recitation_id` (UNIQUE) | CASCADE | One grade per recitation |
| `student` → `progress` | 1 : 0..1 | PK/FK | CASCADE | Aggregate completion rate |
| `tasmi_session` ↔ `student` (attendance) | **M : N via `attendance`** | UNIQUE `(session_id, student_id)` | CASCADE (both), RESTRICT on instructor | Auto-recorded presence |
| `tasmi_session` → `session_material` | 1 : N | `material.session_id` | CASCADE | Materials (no upload UI) |

### Delete-behavior summary

**CASCADE (data disappears with the parent):** all `user`→profile links, notifications, verification/reset tokens, payment↔enrollment, recitation↔enrollment, evaluation↔recitation, attendance, materials, payment history.

**RESTRICT (parent cannot be deleted while children exist):** `instructor`→`tasmi_session`, `instructor`→`evaluation`, `instructor`→`attendance`, `instructor`→`session_material`, `student`→`enrollment`, `tasmi_session`→`enrollment`.

**Practical consequence:** because `user`→`student`→`enrollment` mixes CASCADE then RESTRICT, a hard `DELETE FROM user` for a student who has enrolled in anything will **fail**. This is exactly why admin deletion is implemented as a soft delete (status flip + email anonymization) rather than a real `DELETE`. Any mobile/backend rewrite must preserve the soft-delete approach.

---

## 6. Entity Relationship Diagram

```
                                  ┌──────────────────────┐
                                  │        user          │
                                  │──────────────────────│
                                  │ PK user_id           │
                                  │ UQ email             │
                                  │    role   (3 values) │
                                  │    status (3 values)  │
                                  │    email_verified    │
                                  └───┬────┬────┬────┬───┘
                 1:0..1 ┌─────────────┘    │    │    └──────────────┐ 1:N
                        ▼             1:0..1│    │1:0..1            ▼
                 ┌─────────────┐            ▼    ▼        ┌──────────────────┐
                 │   student   │      ┌──────────┐ ┌──────┐│  notifications   │
                 │─────────────│      │instructor│ │admin ││  email_verif.    │
                 │PK student_id│      │──────────│ │──────││  password_reset  │
                 │UQ user_id   │      │PK instr_ │ │PK ad-││  audit_log       │
                 │UQ reg_number│      │   id     │ │min_id││  recitation_     │
                 │   level     │      │UQ user_id│ │UQ    ││    submissions   │
                 └──┬───────┬──┘      │ verific- │ │user_ │└──────────────────┘
                    │       │         │ ation_   │ │id    │
              1:0..1│       │         │ status   │ └───┬──┘
                    ▼       │         └──┬────┬──┘     │
            ┌────────────┐  │      1:0..1│    │1:N     │ SET NULL
            │  progress  │  │            ▼    │        │
            │────────────│  │  ┌──────────────────┐    │
            │PK student_id│ │  │instructor_payment│    │
            │completion_ │  │  │    _settings     │    │
            │  rate      │  │  │ UQ instructor_id │    │
            │ CHECK 0-100│  │  │ qr_image_url     │    │
            └────────────┘  │  │ bank_name        │    │
                            │  └──────────────────┘    │
                            │                          │
                            │            ┌─────────────▼──────────────┐
                            │            │      tasmi_session         │
                            │            │────────────────────────────│
                            │            │ PK session_id              │
                            │            │ FK instructor_id  RESTRICT │
                            │            │    level / mode / status   │
                            │            │    fee / capacity          │
                            │            │    quran_portion  ◄── AI   │
                            │            │    zoom_meeting_id         │
                            │            │    meeting_link/password   │
                            │            │    evaluation_reviewed_at  │
                            │            └──┬──────┬──────────┬───────┘
                            │               │      │          │
                    M:N     │        1:N    │      │1:N       │1:N
              ┌─────────────┴───────────────▼──┐   ▼          ▼
              │          enrollment            │ ┌──────────┐ ┌──────────────┐
              │────────────────────────────────│ │attendance│ │session_      │
              │ PK enrollment_id               │ │──────────│ │  material    │
              │ FK student_id      RESTRICT    │ │UQ(session│ │──────────────│
              │ FK session_id      RESTRICT    │ │ ,student)│ │(no upload UI)│
              │ UQ (student_id, session_id) ◄──┼─┤ PRESENT/ │ └──────────────┘
              │    enrollment_status           │ │  ABSENT  │
              └───────┬────────────────┬───────┘ └──────────┘
                      │                │
               1:0..1 │                │ 1:N
                      ▼                ▼
        ┌───────────────────┐   ┌──────────────────┐
        │      payment      │   │    recitation    │
        │───────────────────│   │──────────────────│
        │ PK payment_id     │   │ PK recitation_id │
        │ UQ enrollment_id  │   │ FK enrollment_id │
        │    amount         │   │    audio_file_   │
        │    payment_status │   │      path        │
        │    receipt_file_  │   │    submission_   │
        │      path         │   │      date        │
        │ FK verified_by_   │   └────────┬─────────┘
        │    instructor_id  │            │ 1:0..1
        │ FK verified_by_   │            ▼
        │    admin_id (NEVER│   ┌──────────────────┐
        │    WRITTEN)       │   │    evaluation    │
        └─────────┬─────────┘   │──────────────────│
                  │ 1:N         │ PK evaluation_id │
                  ▼             │ UQ recitation_id │
      ┌────────────────────────┐│ FK instructor_id │
      │payment_verification_   ││    score 0-100   │
      │      history           ││      CHECK       │
      │────────────────────────││    feedback      │
      │ SUBMITTED/RESUBMITTED/ │└──────────────────┘
      │ APPROVED/REJECTED      │
      └────────────────────────┘

  Standalone (Recitation Studio v2 — present but not wired to any UI):
      recitation_language (6 seeded rows) ──1:N──▶ recitation_submissions ──FK──▶ user
```

---

## 7. Key SQL Operations

Rather than reproducing every statement, these are the operations that carry business meaning and must be reproduced faithfully.

### Authentication
```sql
-- Login lookup (parameterized; excludes soft-deleted users)
SELECT <cols> FROM user WHERE email = ? AND status <> 'DELETED';

-- Instructor gate, read after successful password check
SELECT verification_status FROM instructor WHERE user_id = ?;
```

### Registration (single transaction)
```sql
INSERT INTO user (full_name, email, phone, password_hash, role, is_active,
                  status, email_verified, created_at)
       VALUES (?, ?, ?, ?, ?, 1, 'INACTIVE', 0, ?);
-- then ONE of:
INSERT INTO student  (user_id, registration_number, level) VALUES (?, CONCAT('STD-', ?), ?);
INSERT INTO instructor (user_id, qualification_file, bio, verification_status)
       VALUES (?, ?, ?, 'PENDING');
-- always:
INSERT INTO notifications (user_id, message, created_at) VALUES (?, ?, ?);
```

### Session visibility for a student
Conceptually:
```sql
SELECT s.* FROM tasmi_session s
  JOIN instructor i ON i.instructor_id = s.instructor_id
  JOIN user       u ON u.user_id       = i.user_id
 WHERE s.status = 'SCHEDULED'
   AND s.mode   = 'ONLINE'
   AND s.session_date >= CURRENT_DATE
   AND i.verification_status = 'APPROVED'
   AND u.is_active = 1
   AND u.status <> 'DELETED';
-- then filtered in Java: session.level IS NULL OR session.level = student.level
```

### Capacity check
```sql
SELECT COUNT(*) FROM enrollment
 WHERE session_id = ? AND enrollment_status IN ('PENDING','APPROVED');
-- blocked when capacity > 0 AND count >= capacity
```

### Payment verification (single transaction)
```sql
UPDATE payment SET payment_status = ?, verified_by_instructor_id = ?,
                   verification_note = ?, payment_date = ?
 WHERE payment_id = ?;
UPDATE enrollment SET enrollment_status = ?   -- APPROVED on approve, PENDING on reject
 WHERE enrollment_id = ?;
INSERT INTO payment_verification_history
       (payment_id, actor_user_id, actor_role, action, from_status, to_status, reason)
       VALUES (?, ?, 'INSTRUCTOR', ?, ?, ?, ?);
INSERT INTO notifications (user_id, message, created_at) VALUES (?, ?, ?);
```

### Evaluation + progress recomputation
```sql
INSERT INTO evaluation (recitation_id, instructor_id, score, feedback)
       VALUES (?, ?, ?, ?);   -- UNIQUE(recitation_id) enforces one-shot

-- Progress recompute
SELECT AVG(ev.score) FROM evaluation ev
  JOIN recitation r  ON r.recitation_id = ev.recitation_id
  JOIN enrollment e  ON e.enrollment_id = r.enrollment_id
 WHERE e.student_id = ?;
INSERT INTO progress (student_id, completion_rate) VALUES (?, ?)
  ON DUPLICATE KEY UPDATE completion_rate = VALUES(completion_rate);
```

### Instructor recitation queue (ownership by join)
```sql
SELECT r.* FROM recitation r
  JOIN enrollment    e ON e.enrollment_id = r.enrollment_id
  JOIN tasmi_session s ON s.session_id    = e.session_id
 WHERE s.instructor_id = ?;
```
This join **is** the ownership check for evaluation — there is no `instructor_id` column on `recitation`.

### Admin soft delete
See doc 02, A7 — the full `UPDATE` with email anonymization.

### Admin aggregates
`SELECT status, COUNT(*) … GROUP BY status` over `tasmi_session`, `enrollment`, and `payment`; plain `COUNT(*)` over `recitation` and `evaluation`; `ROUND(AVG(evaluation.score))` for the landing-page completion percentage.

---

## 8. Schema File Discrepancies

| File | Tables | Status | Use for migration? |
|---|---|---|---|
| `e-Tasmi/setup/etasmi_schema.sql` | **20** | **Canonical.** Mounted as the Docker Compose DB init script. | **YES — this is the target** |
| `e-Tasmi/web/WEB-INF/db/railway_schema.sql` | 20 | Identical table set; this is what `SchemaBootstrap` executes on an empty DB | Yes (equivalent) |
| `e-Tasmi/setup/railway_schema.sql` | 20 | Duplicate copy of the above | Reference only |
| `db/etasmi.sql` | **10** | **Legacy dump.** Contains an `instructor_profile` table that does not exist in the canonical schema, and is missing 11 current tables (`instructor_payment_settings`, `payment_verification_history`, `progress`, `email_verification`, `password_reset`, `recitation`, `recitation_language`, `recitation_submissions`, `attendance`, `session_material`, `evaluation`). | **NO — do not use** |
| `e-Tasmi/setup/*_patch.sql` | — | Historical patches, already folded into the canonical schema | Reference only |
| `DBSeeder` runtime migrations | — | Incremental; **incomplete** vs canonical (see §2) | Must be verified against the live DB |

Other files in `setup/`: `sample_data.sql`, `seed_qademo_student.sql`, `remove_demo_session.sql`, `reset_operational_data_for_testing.sql`, `reset_recitation_evaluation_demo.sql`, `cleanup_generated_audit_data_20260409.sql`, `drop_evaluation_pdf_column.sql`. These are seed/reset utilities, not schema definitions. **`drop_evaluation_pdf_column.sql` confirms that a previously added `evaluation` PDF column was deliberately removed** — do not reintroduce it.

---

## 9. Findings Relevant to the Migration

1. **Every table is used** by the running code. There are no orphan tables. However, four have no live write path from the UI: `session_material` (no upload), `recitation_submissions` + `recitation_language` (Studio v2 not wired), and the `ABSENT` half of `attendance`.
2. **No table is queried that is missing from the schema.** The schema is complete relative to the code.
3. **`recitation_submissions.student_id` references `user`, not `student`** — the only place in the schema with this inconsistency. Preserve it verbatim if the table is retained.
4. **`recitation.audio_file_path` is VARCHAR(255)** while every other media column is VARCHAR(500)–(1024). Long Cloudinary URLs risk truncation. Widening it would be a safe, additive fix, but it is a schema change and therefore out of scope for this phase.
5. **`notifications` has no read state** — a mobile notification centre with unread badges requires an additive column.
6. **No soft-delete flag exists on any table except `user`.** Sessions are hard-deleted; enrollments are status-flipped to `CANCELLED`.
7. **No `created_at`/`updated_at` on several core tables** — `enrollment`, `evaluation`, and `student` have no timestamps at all. Enrollment date must be inferred from the related payment's `created_at`. This limits any "recently enrolled" mobile feature.
8. **All queries use `PreparedStatement`.** Dynamic SQL (e.g. `UserDaoJdbc.listUsers`, `AdminPaymentsServlet` filters) concatenates only constant column names and binds all user input. No SQL injection vector was found.
