# 05 — BUSINESS RULES

Every rule below was read out of the code, the SQL schema, or the JSP/JavaScript. Each carries an enforcement point so it can be reproduced exactly in PHP.

**Enforcement legend:** `DB` = database constraint · `SVC` = Java service layer · `SRV` = servlet · `DAO` = SQL predicate · `UI` = JSP/JavaScript only (**client-side only, therefore bypassable**)

---

## 1. Identity, Registration, and Account Lifecycle

| # | Rule | Enforcement |
|---|---|---|
| BR-1 | Email is globally unique across all roles and always stored lowercased. | DB `uq_user_email` + SVC |
| BR-2 | Self-registration is limited to `STUDENT` and `INSTRUCTOR`. **An ADMIN account can never be created through `/auth/register`** — rejected at both the servlet and the service. | SRV + SVC |
| BR-3 | Password must be ≥8 characters and contain an uppercase letter, a lowercase letter, and a digit. Applies to registration, password reset, and profile password change. | SVC |
| BR-3a | Admin-created users are validated at ≥8 characters server-side, but `admin/user_form.jsp` declares `minlength="6"`. **The server is stricter than the form** — the UI can submit a 6-character password that the server rejects. | SRV vs UI |
| BR-4 | Phone must be 7–30 characters (admin path) or 8–15 digits (self-registration path). The two paths use different rules. | SVC |
| BR-5 | Every new self-registered user is created `status = 'INACTIVE'`, `email_verified = 0`, `is_active = 1`, explicitly overriding the column default of `'ACTIVE'`. | SVC |
| BR-6 | Registration creates the `user` row, the matching `student`/`instructor` profile row, and a welcome `notifications` row **in a single transaction**. Any failure rolls back all three. | SVC |
| BR-7 | A student's registration number is generated as `"STD-" + userId`, so it can only be assigned after the user insert. | SVC |
| BR-8 | An instructor must supply a bio and a qualification PDF (≤10 MB, PDF only) at registration, and is created `verification_status = 'PENDING'`. | SRV + SVC |
| BR-9 | Admin-created users are immediately `ACTIVE` and `email_verified = 1` — they skip email verification entirely. An admin-created instructor is inserted **already `APPROVED`**. | SRV |
| BR-10 | Deletion is always a soft delete: `status='DELETED'`, `is_active=0`, and the email is anonymized to `deleted_{id}_{originalEmail}` so the address can be reused. **No row is ever physically removed.** | SRV |
| BR-11 | An admin cannot deactivate or delete their own account. | SRV |
| BR-12 | An admin cannot deactivate or delete the **last active admin**. | SRV |
| BR-13 | A user's role can never be changed after creation — `/admin/users/update` explicitly rejects it. | SRV |
| BR-14 | Every query that reads users filters `status <> 'DELETED'`. Soft-deleted users are invisible platform-wide. | DAO |

---

## 2. Email Verification

| # | Rule | Enforcement |
|---|---|---|
| BR-15 | Two independent verification mechanisms exist: a 6-digit code (10-minute TTL, stored as SHA-256 of `userId + ":" + code`) and a link token (24-hour TTL, SHA-256 in `user.email_verification_token_hash`). Either one verifies the account. | SVC |
| BR-16 | The 6-digit code allows a **maximum of 5 attempts**; the 6th is refused even if correct. | SVC |
| BR-17 | On successful verification the `email_verification` row is deleted, so a code is single-use. | SVC |
| BR-18 | Verification promotes a **student** to `status='ACTIVE'`. An **instructor** is promoted to `ACTIVE` only if `verification_status` is already `APPROVED`; otherwise the account stays `INACTIVE`. | SVC |
| BR-19 | Verifying an unknown email returns `"Incorrect verification code."` — the same message as a wrong code, to prevent account enumeration. | SVC |
| BR-20 | Resend always reports generic success regardless of whether the address exists. | SRV |
| BR-21 | **Exception:** `GET /auth/verify-status?email=` returns `{"known":true|false}` without authentication, which *does* leak whether an address is registered. This contradicts BR-19/BR-20. | SRV |

---

## 3. Login and Session

| # | Rule | Enforcement |
|---|---|---|
| BR-22 | Login requires the account to be `is_active = 1`, `status <> 'DELETED'`, and `email_verified = 1`. | SVC |
| BR-23 | An unverified user is refused login with a message directing them to verify. | SVC |
| BR-24 | Wrong password and unknown email both yield `"Invalid email or password."` | SVC |
| BR-25 | An instructor with `verification_status = 'PENDING'` **can log in** but every instructor page forwards to `pendingApproval.jsp`. A `REJECTED` instructor is blocked the same way. Approval is an authorization gate, not an authentication gate. | AuthFilter |
| BR-26 | Login rate limit: 5 failures per 5 minutes per `{ip}:{email}`, cleared on success. Registration/forgot-password/resend: 10 per 10 minutes per IP (resend is 5/10min). | SVC |
| BR-27 | Rate limiting is skipped entirely on localhost or when `ETASMI_DISABLE_RATE_LIMIT=true`. | SVC |
| BR-28 | The previous session is invalidated and a new one created on every successful login (session-fixation defence). | SRV |
| BR-29 | Session inactivity timeout is **900 seconds (15 minutes)**, with no refresh, no remember-me, and no persistent token. | SRV |
| BR-30 | Passwords are hashed with **BCrypt cost 12**. Legacy PBKDF2 hashes still verify, but are **not** re-hashed on successful login. | SVC |

---

## 4. Password Reset

| # | Rule | Enforcement |
|---|---|---|
| BR-31 | The reset token is 32 random bytes, URL-safe Base64, stored only as SHA-256. | SVC |
| BR-32 | TTL 30 minutes; single-use (`used_at` stamped on consumption). | DB + SVC |
| BR-33 | Requesting a new reset invalidates all of the user's prior outstanding tokens. | DAO |
| BR-34 | The request endpoint always reports success, whether or not the email exists. | SRV |

---

## 5. Instructor Verification

| # | Rule | Enforcement |
|---|---|---|
| BR-35 | Only an ADMIN can approve or reject an instructor. | AuthFilter |
| BR-36 | **Approval requires `user.email_verified = true`.** If not verified, the transaction is rolled back and approval fails. | SVC |
| BR-37 | Approve sets `verification_status='APPROVED'` **and** `user.status='ACTIVE'` in one transaction. | SVC |
| BR-38 | Reject sets `verification_status='REJECTED'` **and** `user.status='INACTIVE'`. | SVC |
| BR-39 | Both actions write an in-app notification and an `audit_log` row (`INSTRUCTOR_APPROVED` / `INSTRUCTOR_REJECTED`). **No email is sent.** | SVC |
| BR-40 | **No rejection reason is captured or stored anywhere.** The rejected instructor is told only that the application was rejected. | — |
| BR-41 | A rejected instructor can be re-approved later; there is no resubmission workflow for a new qualification document. | SVC |
| BR-42 | Only an `APPROVED` instructor's sessions are visible to students. | DAO |

---

## 6. Session Creation and Management

| # | Rule | Enforcement |
|---|---|---|
| BR-43 | Only an `APPROVED` instructor can create a session. Checked again in the servlet even though `AuthFilter` already gates it. | SRV + SVC |
| BR-44 | A session is always created with `mode = 'ONLINE'`. Although the enum allows `PHYSICAL`, no code path writes it. | SVC |
| BR-45 | **Session creation requires a working Zoom integration.** If Zoom is unconfigured or the API call fails, creation is aborted and no row is inserted. There is no manual fallback in the create path. | SVC |
| BR-46 | A Zoom host email is required: `instructor.zoom_email`, else `ZOOM_DEFAULT_HOST_EMAIL`. With neither, creation fails. | SVC |
| BR-47 | Required fields: title, level, date, time, duration, fee, capacity. `capacity > 0`, `duration > 0`, `fee ≥ 0`. | SRV |
| BR-48 | Duration defaults to 60 when unparseable; the UI constrains it to 15–360. | SRV / UI |
| BR-49 | A session may be **edited only while `status = 'SCHEDULED'`**. | SVC |
| BR-50 | Editing patches the existing Zoom meeting (it does not create a new one), clears `live_started_at`/`live_ended_at`/recording fields, and notifies every enrolled student. | SVC |
| BR-51 | Deleting a session attempts a best-effort Zoom delete, notifies enrolled students, then hard-deletes the row. **The FK `RESTRICT` on `enrollment.session_id` means deletion fails at the database level if any enrollment exists** — including cancelled ones. | SVC + DB |
| BR-52 | Starting a session requires **at least one `APPROVED` enrollment** and a valid join link. | SVC |
| BR-53 | Starting a session **auto-completes any other session the same instructor currently has `ONGOING`.** An instructor can host only one live session at a time. | SVC |
| BR-54 | `start` sets `status='ONGOING'` + `live_started_at`; `complete` sets `status='COMPLETED'` + `live_ended_at`. | SVC |
| BR-55 | `quran_portion` is the expected text for AI evaluation. When it is null, AI recitation analysis is unavailable for that session's recitations. | SVC |
| BR-56 | Sessions do not auto-transition by time. A past-dated session stays `SCHEDULED` until the instructor acts. There is no scheduler or cron anywhere in the codebase. | — |

---

## 7. Session Visibility to Students

A session appears in a student's browse list only when **all** of these hold:

| # | Condition | Enforcement |
|---|---|---|
| BR-57 | `tasmi_session.status = 'SCHEDULED'` | DAO |
| BR-58 | `tasmi_session.mode = 'ONLINE'` | DAO |
| BR-59 | `session_date >= CURRENT_DATE` | DAO |
| BR-60 | The owning instructor's `verification_status = 'APPROVED'` | DAO |
| BR-61 | The owning instructor's `user.is_active = 1` and `status <> 'DELETED'` | DAO |
| BR-62 | `session.level IS NULL` **or** `session.level = student.level` — applied **in Java after the query**, not in SQL | SVC |

BR-62 is the single most important visibility rule: a UNIVERSITY student never sees a PRIMARY_SCHOOL session.

---

## 8. Enrollment

| # | Rule | Enforcement |
|---|---|---|
| BR-63 | A student may hold **only one enrollment row per session**, enforced by `uq_enrollment_student_session`. | DB |
| BR-64 | Re-enrolling in a previously `CANCELLED` session **reactivates the existing row to `PENDING`** rather than inserting a new one. | SVC |
| BR-65 | The student's level must match the session's level at enrollment time, re-checked server-side. | SVC |
| BR-66 | Capacity check: blocked when `capacity > 0 AND COUNT(enrollments IN ('PENDING','APPROVED')) >= capacity`. **`capacity = 0` disables the check entirely.** Rejected and cancelled enrollments free a seat. | SVC |
| BR-67 | **Free session (`fee = 0`): the enrollment is created directly as `APPROVED`. No payment row is created.** Immediate access. | SVC |
| BR-68 | **Paid session (`fee > 0`): the enrollment is created `PENDING` and a `payment` row is created `PENDING` with `amount` copied from `session.fee`.** | SVC |
| BR-69 | Exactly one payment per enrollment, enforced by `uq_payment_enrollment`. | DB |
| BR-70 | Withdrawal sets `enrollment_status='CANCELLED'`. The endpoint exists but **no UI control invokes it** — see doc 02. | SVC |
| BR-71 | There is no enrollment deadline, no cut-off relative to the session date, and no cap on how many sessions a student may join. | — |

---

## 9. Payment

**The system has no payment gateway.** Payment is manual bank/QR transfer with instructor verification. ToyyibPay, Stripe, Billplz, and PayPal appear nowhere in the codebase.

| # | Rule | Enforcement |
|---|---|---|
| BR-72 | The payment amount is copied from `tasmi_session.fee` at creation and is never recalculated. A later fee change does not affect existing payments. | SVC |
| BR-73 | Currency is always `MYR`, displayed as `"RM "`. There is no multi-currency support. | DB default |
| BR-74 | Status machine: `PENDING` → `AWAITING_VERIFICATION` → (`APPROVED` \| `REJECTED`). `REJECTED` → `AWAITING_VERIFICATION` on resubmission. | SVC |
| BR-75 | A student can upload a receipt only when the instructor's `instructor_payment_settings.isUsable()` is true — active **and** (a QR image **or** both bank name and account holder). | SVC |
| BR-76 | Receipt files: JPG, PNG, WEBP, GIF, PDF, **≤10 MB**. | SRV |
| BR-77 | Receipt upload is blocked when the payment is already `APPROVED` or already `AWAITING_VERIFICATION`. Resubmission is allowed **only** from `REJECTED`. | SVC |
| BR-78 | **Only the owning instructor can verify a payment** — the payment's session must belong to them. Admins can view every payment but have **no approve/reject capability at all**. `payment.verified_by_admin_id` exists in the schema and is never written. | SVC |
| BR-79 | Approval sets `payment_status='APPROVED'`, `payment_date=now()`, `verified_by_instructor_id`, **and sets `enrollment_status='APPROVED'`** — this is the moment access is granted. | SVC |
| BR-80 | Rejection **requires a reason** (≤500 chars), sets `payment_status='REJECTED'`, and reverts `enrollment_status` to `'PENDING'` (not `REJECTED`), leaving the student able to resubmit. | SVC |
| BR-81 | Every transition writes a `payment_verification_history` row (`SUBMITTED`/`RESUBMITTED`/`APPROVED`/`REJECTED`) with actor, from-status, to-status, and reason. | SVC |
| BR-82 | Every transition writes an in-app notification. Approval notifies the student; submission notifies both student and instructor. | SVC |
| BR-83 | There is **no refund, no cancellation of an approved payment, and no partial payment**. | — |
| BR-84 | There is **no payment expiry or timeout**. A `PENDING` payment stays pending indefinitely. | — |
| BR-85 | Receipt download (`/student/payments/receipt`) requires `APPROVED` status and `amount > 0`, else `404`. | SRV |
| BR-86 | All payment listings and reports constrain `amount > 0`, so free enrollments never appear as transactions. | DAO |
| BR-87 | Legacy statuses `SUCCESS`/`FAILED` are read as `APPROVED`/`REJECTED` by `PaymentStatus.fromString()`, but the admin dashboard chips query those legacy names directly in SQL and therefore always report **0**. Known defect — see doc 02 A2. | SVC vs DAO |

---

## 10. Live Session Access

| # | Rule | Enforcement |
|---|---|---|
| BR-88 | A student may join only when: enrolled, `enrollment_status='APPROVED'`, payment `APPROVED` when `fee > 0`, a join URL exists, provider is `ZOOM` or `MANUAL`, and the URL is valid http(s). | SVC |
| BR-89 | **An instructor may host only their own session** (`session.instructor_id` must match). | SVC |
| BR-90 | Attendance is marked **automatically** when the student opens the join link — `PRESENT`, with the note *"Auto-recorded when the student opened the live session join link."* **`ABSENT` is never written by any code path**, and there is no manual attendance UI. | SVC |
| BR-91 | Attendance uses an UPSERT on `uq_attendance_session_student`, so repeated joins do not duplicate rows. | DB + DAO |
| BR-92 | `marked_by_instructor_id` is always set to the session's instructor even though the mark is automatic. | SVC |
| BR-93 | Zoom SDK signatures are minted server-side per request; students receive `role=0` and instructors `role=1` plus a ZAK token. The SDK secret never leaves the server. | SVC |
| BR-94 | Meeting passcodes are shown to students only when `is_password_visible = 1`; the toggle endpoint exists but has no UI. | SRV |
| BR-95 | Zoom meetings are created with `auto_recording="none"`. **No recording is ever produced**, so `recording_url`, `recording_status`, and `recording_synced_at` are permanently null. | SVC |

---

## 11. Recitation Submission

| # | Rule | Enforcement |
|---|---|---|
| BR-96 | A recitation is attached to an **enrollment**, not directly to a session or student. | DB |
| BR-97 | Submission requires: enrollment `APPROVED`, session not `CANCELLED`, and payment `APPROVED` when `fee > 0`. | SVC |
| BR-98 | Accepted formats: webm, mp3, wav, m4a, ogg, aac, mp4, mov, mpeg, mpga; MIME `audio/*` or `video/*`. Max **200 MB**. | SRV |
| BR-99 | A student may submit **multiple recitations per enrollment** — there is no unique constraint and no cap. | DB |
| BR-100 | Cloudinary uploads for recitations use the **video** endpoint (`resource_type=video`), which is Cloudinary's audio/video pipeline. | SVC |
| BR-101 | **AI analysis is not triggered on submission.** Nothing happens automatically; the instructor must explicitly request it. | SVC |
| BR-102 | Audio playback is ownership-checked through `enrollment.student_id`; instructors reach it through the session-ownership join. | SRV |
| BR-103 | A student cannot delete or replace a submitted recitation. | — |

---

## 12. Evaluation

| # | Rule | Enforcement |
|---|---|---|
| BR-104 | **One evaluation per recitation, enforced by `uq_evaluation_recitation`.** A second attempt fails with `"This recitation has already been evaluated."` | DB + SVC |
| BR-105 | An evaluation **cannot be edited or deleted once saved**. Grading is one-shot. | — |
| BR-106 | Score must be an integer 0–100, enforced by `chk_evaluation_score` and by the service. | DB + SVC |
| BR-107 | **Only the owning instructor may evaluate**, established by the join `recitation → enrollment → tasmi_session.instructor_id`. There is no `instructor_id` column on `recitation`. | DAO |
| BR-108 | Saving an evaluation recomputes `progress.completion_rate` as `AVG(evaluation.score)` across **all** of that student's evaluated recitations, then upserts `progress`. | SVC |
| BR-109 | Feedback text is optional. | — |
| BR-110 | `evaluation_reviewed_at` on `tasmi_session` is **independent of `status`**. NULL puts the session in the "Active Evaluations" tab; a timestamp moves it to "Reviewed Sessions". Reopening sets it back to NULL. | SVC |
| BR-111 | **No AI output is ever persisted.** The transcript, diff, tajwīd notes, and suggested score live only in the `HttpSession` under `etasmi.aiAnalysis.{recitationId}` and are discarded on logout, timeout, or restart. The instructor's saved score is entirely manual. | SVC |
| BR-112 | Students cannot respond to feedback; there is no reply, dispute, or re-submission-for-regrade path. | — |

---

## 13. AI Rules

| # | Rule | Enforcement |
|---|---|---|
| BR-113 | Recitation analysis requires the session's `quran_portion` to be non-empty; without it, analysis returns `FAILED`. | SVC |
| BR-114 | Analysis is instructor-initiated only, one recitation at a time. | SRV |
| BR-115 | Four result statuses: `OK`, `REJECTED` (high-confidence non-Quran audio), `CANNOT_EVALUATE` (empty or failed transcription), `FAILED` (no API key, file too large/small, missing expected text). | SVC |
| BR-116 | **AI failure never blocks the workflow.** The instructor can always grade manually. | SVC |
| BR-117 | The AI suggested score is advisory. It is displayed but is not written to `evaluation.score` unless the instructor types it. | SRV |
| BR-118 | The Quran Assistant is scope-limited to Quran/Islamic-studies topics by the system prompt; off-topic questions produce a `scopeReminder` flag. | SVC |
| BR-119 | Assistant conversation history is capped at **12 messages**; the user message at 2,400 characters; the reply at 12,000. History lives in the browser and is resent each turn — **nothing is persisted server-side.** | SRV |
| BR-120 | AI translation accepts a maximum of **320 verses per request**, processed in batches of 30, cached in a JVM-local map that is lost on restart. | SRV |
| BR-121 | Voice input is capped at 25 MB per turn. When TTS fails, the text reply is still returned without audio. | SRV |
| BR-122 | **All AI features are student-only.** Instructors have no assistant; admins have no AI features at all. | AuthFilter |

---

## 14. Notifications

| # | Rule | Enforcement |
|---|---|---|
| BR-123 | Notifications are in-app rows only. **There is no push, no SMS, and no email notification** other than verification and password-reset mail. | — |
| BR-124 | Message text is pre-rendered **in English at write time** and stored as a string. It is **not translated** even for Arabic or Malay users — a genuine i18n gap. | SVC |
| BR-125 | There is **no read/unread state, no type/category, and no deep link.** Notifications cannot be marked read, filtered, or navigated from. | DB |
| BR-126 | Notifications are generated on: registration, enrollment, payment submission, payment approval, payment rejection, session update, session deletion, instructor approval, and instructor rejection. | SVC |

---

## 15. File Handling

| # | Rule | Enforcement |
|---|---|---|
| BR-127 | Storage is dual-mode: Cloudinary when configured, otherwise the local `ETASMI_UPLOADS_DIR`. The decision is per-upload at runtime. | SVC |
| BR-128 | Size limits by type: profile photo **2 MB**, session banner **3 MB**, payment QR **8 MB**, payment receipt **10 MB**, qualification PDF **10 MB**, recitation audio **200 MB**, voice input **25 MB**. | SRV |
| BR-129 | Images are validated by decoding with `ImageIO`, not by trusting the declared MIME type. | SVC |
| BR-130 | Local paths are containment-checked against the uploads root, with `..` and `\` stripped. | SRV |
| BR-131 | Qualification PDFs are served to admins via a **signed 300-second** Cloudinary URL. This is the only signed-URL use in the system. | SVC |
| BR-132 | **`/uploads/*` has no authentication.** Receipts, recitation audio, and profile photos are readable by anyone holding the URL. | SRV |
| BR-133 | Replacing a file does not delete the previous one — **no Cloudinary destroy call exists anywhere.** Orphaned assets accumulate. | — |

---

## 16. Authorization

| # | Rule | Enforcement |
|---|---|---|
| BR-134 | Authorization is prefix-based in a single filter: `/admin/*` → ADMIN, `/instructor/*` → INSTRUCTOR, `/student/*` → STUDENT. | AuthFilter |
| BR-135 | Instructor routes additionally require `verification_status='APPROVED'`, re-read from the database on each request (not trusted from the session). | AuthFilter |
| BR-136 | Resource-level ownership is checked inside each servlet/service, not by the filter. The filter only proves role. | SRV + SVC |
| BR-137 | There is **no cross-role access**: an admin cannot open a student page, and an instructor cannot open an admin page. Everything is redirected to `/dashboard?forbidden=1`. | AuthFilter |
| BR-138 | There are no sub-roles, no per-permission grants, and no admin tiers. Role is a single enum value. | DB |

---

## 17. Reporting

| # | Rule | Enforcement |
|---|---|---|
| BR-139 | Reports are admin-only, read-only, and generated live on each request — nothing is cached or precomputed. | SRV |
| BR-140 | Each report type filters on a different date column: sessions → `session_date`; payments → `DATE(payment_date)`; users → `created_at`; evaluations → the related session date. | DAO |
| BR-141 | Because `enrollment` has no timestamp column, enrollment dates are inferred from the related payment's `created_at`. | DAO |
| BR-142 | Admin user listing and audit logs are capped at `LIMIT 100` with **no pagination**. Only `/admin/payments` paginates (25/page). | DAO |
| BR-143 | Only 7 actions are audited: `USER_CREATED`, `USER_UPDATED`, `USER_ACTIVATED`, `USER_DEACTIVATED`, `USER_DELETED`, `INSTRUCTOR_APPROVED`, `INSTRUCTOR_REJECTED`. **Logins, logouts, failed authentication, password resets, session CRUD, payments, and evaluations are not audited.** | SVC |

---

## 18. Localization

| # | Rule | Enforcement |
|---|---|---|
| BR-144 | Three locales: `en`, `ar`, `ms`. Every GET is redirected to a locale-prefixed URL. | Filter |
| BR-145 | Arabic switches the document to `dir="rtl"`. | UI |
| BR-146 | All server timestamps are interpreted in `APP_ZONE`, default `Asia/Kuala_Lumpur`. | SVC |
| BR-147 | Notification text and most server-side validation messages are English-only regardless of locale. | SVC |

---

## 19. Rules That Exist in the Code but Have No User Interface

These are real, reachable server behaviors with no way to trigger them from the web UI. They must be classified as *keep*, *expose*, or *remove* before the mobile build.

| Capability | Endpoint | Status |
|---|---|---|
| Withdraw an enrollment | `POST /student/enrollments` `action=withdraw` | Implemented, no button |
| Cancel a payment | `POST /student/payments` `action=cancel` | Implemented, no button |
| Toggle meeting-password visibility | `POST /instructor/sessions` `action=togglepassword` | Implemented, no button |
| Payment reference / student note | `POST /student/payments/qr` (`reference`, `note`) | Accepted, no form field |
| Instructor Zoom email | `POST /instructor/profile` (`zoomEmail`) | Accepted, no form field |
| Session material upload | `SessionMaterialDaoJdbc.insert()` | **Zero call sites** — only the read path is wired |
| Recitation Studio v2 | `recitation_submissions` + `recitation_language` | Tables and DAOs exist, no UI |
| Admin payment verification | `payment.verified_by_admin_id` | Column exists, never written |
| Attendance `ABSENT` | `attendance.attendance_status` | Enum value never written |
| Meeting recording | `recording_*` columns | Disabled at meeting creation |

---

## 20. Rules Deliberately Absent

Confirmed by exhaustive search — the mobile app must **not** assume these exist.

- No payment gateway, no online card/FPX payment, no refunds.
- No scheduler, cron, or background job of any kind. Every state change is user-initiated.
- No email notifications beyond verification and password reset.
- No push notifications.
- No chat or messaging between students and instructors.
- No session waitlist.
- No recurring or multi-part sessions.
- No certificates or completion badges.
- No ratings or reviews of instructors.
- No instructor payout or commission tracking.
- No student-to-student features.
- No content moderation of recitation audio.
- No data export for students (GDPR-style).
- No account self-deletion.
