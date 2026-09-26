# 02 — COMPLETE FEATURE INVENTORY

**Rule applied throughout:** every feature listed here exists in the current codebase. Nothing has been invented, simplified, or removed. Where a capability exists in the backend but has no UI, it is explicitly flagged as such rather than being described as a working feature.

**Totals:** 73 features — Student 33, Instructor 16, Admin 12, Shared/System 12.

---

## PART A — STUDENT FEATURES (33)

All student routes require an authenticated session with `role = STUDENT`. `AuthFilter` redirects unauthenticated HTML requests to `/auth/login?expired=1` and returns HTTP 401 JSON for `/student/api/*`.

---

### S1. Student Registration

| | |
|---|---|
| **Page / URL** | `jsp/auth/register.jsp` — `GET/POST /auth/register?role=STUDENT` |
| **Servlet** | `controller/auth/RegisterServlet` (`@MultipartConfig`) |
| **Purpose** | Create a student account |
| **Fields** | `fullName`, `email`, `phone`, `password`, `confirmPassword`, `role=STUDENT`, `studentLevel` |
| **Validation** | fullName non-blank; email non-blank (**no server-side format regex on this path**); phone 8–15 digits after stripping non-digits; password ≥8 chars with upper + lower + digit; passwords must match; `studentLevel` required (one of `PRIMARY_SCHOOL`, `SECONDARY_SCHOOL`, `HIGH_SCHOOL`, `UNIVERSITY`) |
| **Rate limit** | 10 attempts / 10 minutes per IP (key `register:{ip}`) |
| **DB writes** (one transaction) | `user` (role `STUDENT`, `status=INACTIVE`, `email_verified=0`, `is_active=1`, BCrypt hash); `student` (`registration_number = "STD-" + userId`, `level`); `notifications` (welcome message) |
| **External** | Email verification code sent via Brevo → Resend → SMTP |
| **Success** | Redirect `/auth/verify-email?email=…&msg=…` |
| **Errors** | `"Email is already registered."`, `"Invalid phone number format."`, `"Weak password. Use 8+ chars with upper, lower, and a number."`, `"Passwords do not match"`, `"Please select your education level."`, `"Too many registration attempts. Please try again later."` |
| **Permissions** | Public |

### S2. Email Verification (6-digit code)

| | |
|---|---|
| **URL** | `GET/POST /auth/verify-email` → `jsp/auth/verify_email.jsp` |
| **Servlet** | `controller/auth/VerifyEmailServlet` |
| **Mechanism** | Two parallel artifacts: a **6-digit code** (SHA-256 of `userId:code` in `email_verification.code_hash`, **10-minute** TTL, **max 5 attempts**) and a **link token** (UUID, SHA-256 in `user.email_verification_token_hash`, **24-hour** TTL) |
| **Validation** | Code must match `^[0-9]{6}$` |
| **On success** | `user.email_verified=1`, `email_verified_at` set, `email_verification` row deleted, `user.status → ACTIVE` for students |
| **Errors** | `"Incorrect verification code."` (also returned for unknown emails, to prevent enumeration), `"Verification code expired. Please resend a new code."` |
| **Permissions** | Public |

### S3. Resend Verification Code

`POST /auth/resend-verification` — `ResendVerificationServlet`. Rate limit **5 / 10 min / IP**. Always returns a generic success message regardless of whether the email exists. Only actually sends when the user exists and `email_verified = false`.

### S4. Verification Status Polling

`GET /auth/verify-status?email=…` — `VerifyStatusServlet`. Returns `{"known":bool,"verified":bool}`. **Unauthenticated** — this is an email-enumeration weakness (see doc 11).

### S5. Login

| | |
|---|---|
| **URL** | `GET/POST /auth/login` → `jsp/auth/login.jsp` |
| **Servlet** | `controller/auth/LoginServlet` → `AuthService.login()` |
| **Lookup** | `SELECT … FROM user WHERE email = ? AND status <> 'DELETED'` (email lowercased/trimmed) |
| **Password** | BCrypt (cost 12) preferred; PBKDF2-HmacSHA256 120,000 iterations accepted for legacy hashes; **no plaintext fallback** |
| **Gates, in order** | blank fields → `is_active=false` → email-verification gate (admins always skip; bypassable with `ETASMI_ALLOW_LOGIN_WITHOUT_EMAIL_VERIFICATION`) → `user.status` must be `ACTIVE`, **except** instructors with `PENDING`/`REJECTED` verification who are allowed to log in |
| **Rate limit** | **5 attempts / 5 minutes** per `{ip}:{email}`; cleared on success; bypassed on localhost or with `ETASMI_DISABLE_RATE_LIMIT` |
| **Session** | Prior session invalidated (session-fixation defence), then sets `userId` (long), `role` (String), `displayName`, `email`, and `instructorVerificationStatus` when applicable. Timeout **900 s**. |
| **Redirect** | `ADMIN` → `/admin/dashboard`; all others → `/dashboard` (role router) |
| **AJAX** | Returns `{success, redirect}` JSON when `X-Requested-With: XMLHttpRequest` |
| **Errors** | `"Invalid email or password."` (generic for both unknown user and bad password), `"Your account has been deactivated…"`, `"Too many login attempts. Please try again later."` |

### S6. Password Reset Request

`GET/POST /auth/forgot-password` — `ForgotPasswordServlet`. Rate limit 10 / 10 min / IP. Token = 32 random bytes, URL-safe Base64 (~256 bits), stored as SHA-256 in `password_reset.token_hash`, **30-minute** TTL, prior tokens invalidated. Always returns a generic success message. Email subject: `"Reset your password - e-Tasmi"`.

### S7. Password Reset Completion

`GET/POST /auth/reset-password?token=…` — `ResetPasswordServlet`. GET validates the token is active (`used_at IS NULL AND expires_at > CURRENT_TIMESTAMP`). POST applies the same password strength rules as registration and marks the token used (single-use).

### S8. Logout

`GET /auth/logout` — `LogoutServlet`. Calls `session.invalidate()`, redirects to `/auth/login`. GET-only; no explicit cookie deletion.

### S9. Student Dashboard

| | |
|---|---|
| **Page / URL** | `jsp/student/dashboard.jsp` — `GET /student/dashboard` |
| **Servlet** | `StudentDashboardServlet` |
| **Displays** | Welcome banner; 4 stat tiles; featured upcoming session with countdown; quick-action grid; upcoming session list (max 6); recitation activity heatmap; pending-payment strip |
| **Tables** | `student`, `enrollment`, `payment`, `tasmi_session`, `recitation`, `evaluation`, `notifications`, `instructor`, `user`, `progress` |
| **Business rules** | "Upcoming" = enrollment `APPROVED` + payment satisfied + student level matches + session `SCHEDULED`/`ONGOING` + instructor active & approved. Notifications containing "demo"/"sandbox"/"mock" are filtered out of the dashboard feed. |
| **Errors** | SQL failures are logged; the page still renders with zeroed counters |

### S10. Browse Available Sessions

| | |
|---|---|
| **Page / URL** | `jsp/student/sessions.jsp` — `GET /student/available-sessions` (alias `/student/sessions`) |
| **Servlet** | `StudentSessionsServlet` |
| **Params** | `q` (search), `notice` (flash code), `sessionId` (redirects to enroll-confirm) |
| **Visibility rules** | Session must be `status=SCHEDULED`, `mode=ONLINE`, `session_date >= today`, instructor `is_active` + `verification_status=APPROVED` + user not `DELETED`; if the student has a level, the session's level must match or be null |
| **Card state** | Excluded when enrollment `APPROVED` **and** (free **or** payment `APPROVED`) — those move to My Sessions. `availableSeats = capacity − COUNT(enrollments in PENDING|APPROVED)` |
| **Notice codes** | `payment_required`, `reactivated_payment_required`, `already_enrolled_payment_required`, `free_session`, `payment_cancelled`, `qr_checkout_unavailable`, `payment_setup_missing` |
| **Client JS** | `browse-sessions.js` — client-side filter chips, sort, detail modal. **No AJAX.** |

### S11. Session Detail Modal

Rendered client-side from data already embedded in the session cards by `browse-sessions.js`. No separate endpoint.

### S12. Enrollment / Checkout

| | |
|---|---|
| **Page / URL** | `jsp/student/session_confirm.jsp` — `GET/POST /student/enroll-confirm`, also `POST /student/sessions` |
| **Action values** | POST with no `action` (or `action=checkout`) → checkout; any other value → HTTP 400 |
| **Note** | A **GET** to `/student/enroll-confirm?sessionId=X` skips the confirmation page and immediately performs checkout |
| **Rules** (`EnrollmentService.enroll`) | Session must be `SCHEDULED`; student level must match; duplicate active enrollment returns success with code `already_enrolled_payment_required` / `already_enrolled_free_session` without inserting a row; a `CANCELLED`/`REJECTED` enrollment is reactivated to `PENDING` (re-checking capacity); capacity is enforced when `capacity > 0 && activeEnrollments >= capacity` |
| **Routing after enroll** | fee = 0 → `PaymentService.confirmFreeEnrollment` → redirect `/student/enrollments?notice=free_confirmed`; fee > 0 → `PaymentService.beginPaymentFlow` → redirect `/student/payments/qr?notice=checkout_ready&enrollmentId=…` |
| **Errors** | `"This session is not available for enrollment."`, `"You can only enroll in sessions that match your level."`, `"This session has already reached its capacity."` |
| **DB** | `enrollment` INSERT/UPDATE, `payment` INSERT, `notifications` INSERT |

### S13. My Sessions (Enrollments)

| | |
|---|---|
| **Page / URL** | `jsp/student/enrollments.jsp` — `GET/POST /student/enrollments` |
| **Action values** | `withdraw` (with `enrollmentId`). **Implemented in the servlet but there is no withdraw button in the current JSP.** |
| **Visibility** | Session not cancelled + enrollment `APPROVED` + (fee = 0 or payment `APPROVED`) |
| **Join readiness** | enrollment `APPROVED` + payment satisfied + a usable live URL |
| **Params** | `notice`, `joinError` (codes mapped to messages in the JSP) |
| **Client JS** | `my-sessions.js` — tab toggling only |

### S14. Join Live Session (router)

| | |
|---|---|
| **URL** | `GET /student/joinLive?sessionId=X[&external=1][&embed=1]` |
| **Servlet** | `StudentJoinLiveServlet` → `LiveSessionAccessService.validateStudentJoin` |
| **Access rules** | Student must be enrolled; enrollment `APPROVED`; if fee > 0 payment must be `APPROVED`; session must have a participant join URL; provider `ZOOM` or `MANUAL`; the URL must resolve to a valid http(s) link |
| **Decision** | Embed when not forced external **and** session is `ZOOM` with a `zoom_meeting_id` and SDK credentials are configured **and** the browser context is secure (HTTPS / `X-Forwarded-Proto: https` / localhost) |
| **Side effect** | On the external path, `AttendanceService.markStudentJoined` runs |
| **Errors** | Redirect to `/student/enrollments?joinError={code}` — codes include `access_denied`, `join_url_missing`, `embed_unavailable` |

### S15. Live Session Embed (Zoom Web SDK)

`GET /student/live/embed?sessionId=X` → `jsp/student/live-embed.jsp`. Re-validates access, marks attendance, and forwards Zoom client config. The page loads the Zoom Web SDK from `source.zoom.us` (default version 3.11.2).

### S16. Zoom Meeting Signature API (student)

| | |
|---|---|
| **URL** | `GET/POST /student/api/zoomMeetingSignature?sessionId=X` |
| **Returns** | `{ok, signature, sdkKey, meetingNumber, passWord, userName, jwtRole:0}` |
| **JWT** | HS256, claims `appKey`, `sdkKey`, `mn`, `role=0` (participant), `iat = now−30s`, `exp/tokenExp = iat+7200s`, `video_webrtc_mode=1`. Signed server-side with `ZOOM_MEETING_SDK_SECRET` — **the secret never reaches the client** |
| **Errors** | `unauthenticated` 401, `invalid_request` 400, `sdk_not_configured` 503, `access_denied` 403, `embed_unavailable` 400, `meeting_id_missing` 400, `passcode_missing` 400, `signature_failed` 500 |

### S17. Auto-Attendance on Join

`AttendanceService.markStudentJoined` — fires only when the session status is `ONGOING`. UPSERTs into `attendance` with status `PRESENT`, `marked_by_instructor_id = session.instructor_id`, and note *"Auto-recorded when the student opened the live session join link."* Students cannot mark their own attendance manually, and instructors have no manual attendance UI.

### S18. QR Payment Checkout

| | |
|---|---|
| **Page / URL** | `jsp/student/qr_payment.jsp` — `GET/POST /student/payments/qr` (alias `/student/payments/submit`) |
| **Servlet** | `StudentQrPaymentServlet`, `@MultipartConfig` maxFileSize 10 MB, maxRequestSize 12 MB |
| **Displays** | Checkout summary, the instructor's QR image, bank details, amount, and the verification timeline from `payment_verification_history` |
| **Disabled when** | The instructor's payment settings are unusable (no QR **and** no bank name + account holder, or inactive), or the payment is already `APPROVED` / `AWAITING_VERIFICATION` |
| **Upload field** | `receipt` — required, ≤10 MB, JPG/PNG/WEBP/GIF/PDF |
| **Also accepted by the servlet but absent from the current form** | `reference` (max 120 chars), `note` (max 500 chars) |
| **Storage** | Cloudinary folder `receipts/` (`resource_type=raw` for PDF, `image` otherwise) or local `/uploads/receipts/` |
| **Effect** | `payment.receipt_file_path` set, status → `AWAITING_VERIFICATION`, history row `SUBMITTED`/`RESUBMITTED`, notifications to both student and instructor |
| **Errors** | `"Please choose your payment receipt…"`, `"Receipt files must be 10 MB or smaller."`, `"Please upload a clear image…"` |

### S19. Payment History

`GET/POST /student/payments` → `jsp/student/payments.jsp`. Lists payments with `amount > 0` for active instructors. POST with `action=cancel` withdraws the enrollment (**no cancel button exists in the current UI**); POST without an action re-runs `beginPaymentFlow`. Reference format `ETP-{paymentId}`.

### S20. Payment Receipt (view / PDF download)

`GET /student/payments/receipt?paymentId=X[&download=1]` — `StudentPaymentReceiptServlet`. Requires payment `APPROVED`, `amount > 0`, and enrollment ownership; otherwise HTTP 404. The PDF is **hand-constructed PDF 1.4 bytes in the servlet** — it does not use PDFBox.

### S21. Recitation Studio — Record

`jsp/student/recitations.jsp` + `assets/js/recitation-studio.js`. In-browser recording via `MediaRecorder`, submitted as multipart to `POST /student/recitations` with `ajax=1`.

### S22. Recitation Studio — Upload

Same page/endpoint, file-picker path.

| | |
|---|---|
| **Servlet** | `StudentRecitationServlet`, `@MultipartConfig` maxFileSize **200 MB** |
| **Params** | `enrollmentId` (required), `audio` (file), optional `ajax=1`. The JS also sends `duration`, which the servlet does not read. |
| **Accepted formats** | webm, mp3, wav, m4a, ogg, aac, mp4, mov, mpeg, mpga; MIME must be `audio/*` or `video/*` |
| **Eligibility** | Enrollment `APPROVED`, session not `CANCELLED`, and if fee > 0 payment `APPROVED` |
| **Storage** | Cloudinary `recitations/` (uploaded via the **video** resource type) or local `/uploads/recitations/` |
| **DB** | INSERT `recitation (enrollment_id, audio_file_path)`; notifications to student and instructor |
| **AI** | **None.** No AI analysis is triggered on student submission. |
| **JSON responses** | `200 {"ok":true,"redirect":"/student/recitations?submitted=1"}` / `400 {"ok":false,"error":"…"}` |

### S23. Recitation History & Feedback

Rendered on the same page. Status is derived at request time, not stored: no evaluation row → "Pending review"; score ≥85 → "Excellent"; ≥60 → "Reviewed"; otherwise "Needs improvement".

### S24. Recitation Audio Playback

`GET /student/recitation-audio?id={recitationId}` — `StudentRecitationAudioServlet`. Ownership verified via `enrollment.student_id`. Serves the local file or redirects to the Cloudinary URL. Errors: 400 bad id, 404 not found, 403 not owner.

### S25. Progress Tracking

| | |
|---|---|
| **Page / URL** | `jsp/student/progress.jsp` — `GET /student/progress` |
| **Stored metric** | `progress.completion_rate` = `AVG(evaluation.score)` across all evaluations of the student's recitations. Upserted on first visit if absent, and recomputed when an instructor submits an evaluation. |
| **Live summary** | Total/approved enrollments, completed sessions, successful vs pending payments, recitations submitted vs evaluated, attendance `PRESENT` count — all computed per request, not stored |

### S26. Notifications Inbox

`GET /student/notifications` → `jsp/student/notifications.jsp`. Read-only list from `notifications` ordered by `created_at DESC`. **No mark-as-read and no delete** — the table has no read flag.

### S27. Student Profile — View & Edit

`GET/POST /student/profile`. Actions: `profile` (`fullName`, `phone` — both required), `password` (≥8 with upper+lower+digit, current password verified), `photo`. Updates `user`; refreshes the session `displayName`.

### S28. Profile Photo Upload

`action=photo` on `/student/profile`. `@MultipartConfig` maxFileSize 2 MB. JPG/PNG/WEBP, validated with `ImageIO`. Stored at Cloudinary `profile/user_{userId}` (overwrite + invalidate) or local `/uploads/profile/`. Writes `user.profile_image_url`.

### S29. Profile Photo Serve

`GET /student/profile-photo` — cache-busted redirect to the stored image URL. 401 without session, 404 when no image.

### S30. Instructor Photo Serve

`GET /student/instructor-photo?instructorId=X` — used on session cards. Any logged-in student may call it.

### S31. Quran Library

`GET /student/quran-library` → `jsp/student/quran_library.jsp` + `assets/js/quran-library.js`. A single-page mushaf reader inside the student shell with hash routing (`#/surah/:n`, reciters, "My Quran"). Backed by 10 JSON endpoints under `/student/api/quran-library/*` proxying the Quran Foundation Content API. Full detail in `08_AI_SYSTEM_DOCUMENTATION.md`.

### S32. AI Quran Assistant — Text Chat

Floating panel injected into **every** student page via `student_header.jspf` → `student_quran_assistant.jspf`. `POST /student/api/quran-assistant/chat`. Modes: tafsir, tajweed, meaning, memorization, word, asbab, tips, general. Reads Quran Library context when available. History is kept in browser memory only.

### S33. AI Quran Assistant — Voice

`POST /student/api/quran-assistant/voice/turn` (multipart) and `/voice/speak` (JSON). Speech-to-text → LLM → text-to-speech, returning base64 MP3. Max upload 25 MB; client caps a turn at 20 s with 650 ms silence detection.

---

## PART B — INSTRUCTOR FEATURES (16)

All `/instructor/*` routes require `role = INSTRUCTOR` **and** `instructor.verification_status = APPROVED`. A pending/rejected instructor is forwarded to `jsp/auth/pendingApproval.jsp`.

---

### I1. Instructor Registration

Same endpoint as S1 with `role=INSTRUCTOR`. Additional required fields: `bio` and a `qualification` PDF.

| | |
|---|---|
| **Qualification file rules** | PDF only; max **10 MB**; content-type must be `application/pdf` or `application/octet-stream`; filename sanitized; path traversal blocked by `normalize()` + `startsWith(directory)` |
| **Storage** | `{catalina.base or java.io.tmpdir}/etasmi_uploads/qualifications/` (local filesystem, **not** Cloudinary at registration time) |
| **DB writes** | `user` (role `INSTRUCTOR`, `status=INACTIVE`); `instructor` (`qualification_file`, `bio`, `verification_status=PENDING`); `notifications` |
| **Errors** | `"Qualification PDF exceeds 10 MB."`, `"Only PDF qualification files are allowed."`, `"Bio is required for instructors."` |

### I2. Instructor Verification Lifecycle (instructor side)

After email verification the account remains `INACTIVE` with `verification_status=PENDING`. The instructor may log in, but `AuthFilter` forwards every `/instructor/*` request to `pendingApproval.jsp`, which shows different copy for `PENDING` vs `REJECTED`. On admin approval, `verification_status → APPROVED` and `user.status → ACTIVE`.

### I3. Instructor Dashboard

| | |
|---|---|
| **Page / URL** | `jsp/instructor/dashboard.jsp` — `GET /instructor/dashboard` |
| **Displays** | Greeting; next-session hero with countdown; week/day/month calendar; KPI tiles (today's sessions, upcoming, pending evaluations, active students); activity feed; session drawer |
| **Rules** | "Next session" prefers an `ONGOING` session inside the window, otherwise the earliest upcoming one. "Still upcoming" uses end-time plus a 60-second grace period. Expired `ONGOING` sessions are auto-completed on load. |
| **Tables** | `instructor`, `tasmi_session`, `enrollment`, `recitation`, `evaluation`, `notifications` |

### I4. Dashboard Calendar Feed

`GET /instructor/dashboard/calendar.json?from=YYYY-MM-DD&to=YYYY-MM-DD`. Defaults to today−7 through today+21. Errors: `{"error":"unauthorized"}`, `{"error":"server_error"}`.

### I5. Dashboard Heartbeat

`GET /instructor/dashboard/heartbeat.json` — polled every **30 seconds** by `instructor-dashboard.js` to refresh live session state.

### I6. Session Creation

| | |
|---|---|
| **URL** | `POST /instructor/sessions` with `action=create` (**also the default when `action` is absent**) |
| **Servlet** | `InstructorSessionsServlet`, `@MultipartConfig` maxFileSize 3 MB, maxRequestSize 5 MB |
| **Fields** | `title`*, `description`, `level`*, `sessionDate`*, `sessionTime`*, `durationMinutes`* (UI 15–360, default 60 on parse failure), `quranPortion`, `fee`* (≥0; 0 = free), `capacity`* (>0), `bannerImage` |
| **Preconditions** | Instructor `APPROVED`; Zoom API configured; a resolvable Zoom host email (`instructor.zoom_email` else `ZOOM_DEFAULT_HOST_EMAIL`) |
| **Zoom** | `POST /v2/users/{hostEmail}/meetings` with `type=2`, `join_before_host=false`, `waiting_room=true`, `auto_recording="none"`, `mute_upon_entry=true` |
| **DB** | INSERT `tasmi_session` with `mode=ONLINE`, `live_provider=ZOOM`, `status=SCHEDULED`, `is_password_visible=0`, plus `meeting_link`, `meeting_password`, `zoom_meeting_id`, `zoom_start_url` |
| **Banner** | Cloudinary `session_banner/user_{userId}_sess_{id}` or local `/uploads/banners/` → `banner_image_url` |
| **Errors** | `"Your account is not approved yet. You cannot create sessions."`, `"Zoom integration is not configured…"`, `"No Zoom host email available…"`, `"Please enter a valid title, target student level, date, time, duration, fee, and capacity."` |

### I7. Session Editing

`action=update`. **Only `SCHEDULED` sessions may be edited.** Updates the Zoom meeting via `PATCH /v2/meetings/{id}` when a `zoom_meeting_id` exists, clears live/recording timestamps, and notifies all enrolled students (`APPROVED` and `PENDING`).

### I8. Session Deletion / Cancellation

`action=delete`. Requires ownership (`deleteByIdAndInstructorId`). Best-effort Zoom `DELETE /v2/meetings/{id}` (404 ignored). Notifies students that the session was cancelled, then **hard-deletes** the `tasmi_session` row. Note: `enrollment.session_id` is `ON DELETE RESTRICT`, so deletion fails with an FK message if enrollments exist. There is no separate "cancel" action — deletion is the cancellation path, even though `TasmiSessionStatus.CANCELLED` exists in the enum.

### I9. Start Session

`action=start`. Requires status `SCHEDULED`, **at least one `APPROVED` enrollment**, and a valid meeting link. Auto-completes any other `ONGOING` session for the same instructor. Sets `status=ONGOING` + `live_started_at`, notifies approved students, then redirects to `/instructor/live-session?sessionId=…`.

### I10. Complete Session

`action=complete`. Only from `ONGOING`. Sets `status=COMPLETED` + `live_ended_at` and notifies approved students.

### I11. Toggle Meeting Password Visibility

`action=togglepassword` — sets `tasmi_session.is_password_visible`. **Backend only: no UI control invokes it.**

### I12. Participant Roster

Embedded in session detail rows on `/instructor/sessions`. Shows **only `APPROVED`** enrollments with name, email, registration number, and payment status. The instructor **cannot** approve or reject enrollments directly — enrollment confirmation happens through payment verification.

### I13. Live Session Hosting

`GET /instructor/live-session?sessionId=X[&external=1][&embed=1]`. Validates ownership. Embeds `jsp/instructor/live-embed.jsp` with the Meeting SDK (`role=1`, host) or redirects to `zoom_start_url`. The signature endpoint `GET/POST /instructor/api/zoomMeetingSignature` additionally returns a **ZAK token** fetched from `GET /v2/users/{hostEmail}/token?type=zak`, which is required to start a meeting as host from the Web SDK.

### I14. Recitation Evaluation

| | |
|---|---|
| **Page / URL** | `jsp/instructor/evaluations.jsp` — `GET/POST /instructor/evaluations` |
| **Action values** | `analyze`, `mark_reviewed`, `reopen`, `save` (the implicit default for any other value) |
| **Grading model** | A single integer `score` **0–100** plus free-text `feedback`. **There is no rubric, no criteria table, and no weighted sub-scores.** |
| **One-shot** | `evaluation.recitation_id` is UNIQUE and `EvaluationService` only inserts — a recitation cannot be re-evaluated through the UI (the DAO has an `update()` method with no service call site) |
| **Ownership** | The recitation must appear in `recitationDao.listForInstructor()` (joined through enrollment → session → instructor) |
| **Side effect** | Upserts `progress.completion_rate` for the student |
| **Session review flag** | `mark_reviewed` sets `tasmi_session.evaluation_reviewed_at = now()`, moving the session to the "Reviewed" tab; `reopen` clears it back to NULL. A session may be marked reviewed **without** every student having been evaluated. |
| **Errors** | `"Score must be between 0 and 100."`, `"This recitation has already been evaluated."`, `"You cannot evaluate this recitation."`, `"Your instructor account is not approved."` |
| **PDF report** | **Not implemented.** The "Recitation Analysis Report" is an HTML card on the page, not a downloadable PDF. |

### I15. AI-Assisted Evaluation

`action=analyze`. Requires the session's `quran_portion` to be set (the button is disabled otherwise). Reads the audio from a local `/uploads/` path or an HTTPS URL, then runs the two-step OpenAI pipeline (transcription → structured evaluation). The result is stored **only in the HTTP session** under `etasmi.aiAnalysis.{recitationId}` and rendered from a request attribute on the POST forward — **it does not survive a page refresh**, and it is never persisted to the database. It is cleared when the instructor saves the evaluation. Full detail in doc 08.

### I16. Instructor Payments (settings + verification queue + earnings)

| | |
|---|---|
| **Page / URL** | `jsp/instructor/payments.jsp` — `GET/POST /instructor/payments` |
| **Action values** | `saveSettings`, `approve`, `reject` |
| **`@MultipartConfig`** | maxFileSize 8 MB, maxRequestSize 10 MB |
| **saveSettings** | `qrImage` (file, ≤8 MB, JPG/PNG/WEBP), `removeQr=1`, `bankName`, `accountHolderName`. Must supply a QR **or** both bank name and account holder. Writes `instructor_payment_settings` (upsert). The current UI is QR-centric; the bank fields are accepted by the servlet but not exposed as form inputs. |
| **approve** | `paymentId` → payment `APPROVED`, enrollment `APPROVED`, history row, student notified |
| **reject** | `paymentId` + `reason` (**required**, max 500) → payment `REJECTED`, enrollment back to `PENDING`, history row, student notified |
| **Guards** | The payment's session must belong to this instructor; a receipt file must exist |
| **Stats** | Transaction counts by status, total revenue (`SUM(amount) WHERE APPROVED`), revenue by session |
| **Notices** | `payment_approved`, `payment_rejected`, `settings_saved`, `verify_error`, `settings_error` |

**Additional instructor routes:** `GET/POST /instructor/profile` (actions `profile`, `photo`; also accepts `zoomEmail`, which has no form field); `GET /instructor/profile-photo`; `GET /instructor/materials/open?sessionId=&materialId=&download=1` (serves `session_material` files — **serve only, no upload exists**); `GET/POST /instructor/payment-settings` (legacy redirect to `/instructor/payments`).

---

## PART C — ADMIN FEATURES (12)

All `/admin/*` routes require `role = ADMIN`.

---

### A1. Admin Login

Uses the shared `/auth/login`. Admins **bypass the email-verification gate** entirely.

### A2. Admin Dashboard

`GET /admin/dashboard` → `jsp/admin/dashboard.jsp`. Metrics and their exact computations:

| Metric | Computation |
|---|---|
| Total users | `COUNT(*) FROM user WHERE status <> 'DELETED'` |
| Students | same with `role='STUDENT'` |
| Instructors | same with `role='INSTRUCTOR'` |
| Pending verification | size of `instructor WHERE verification_status='PENDING'` filtered to active, non-deleted users |
| Total sessions | scheduled + ongoing + completed + cancelled, from `SELECT status, COUNT(*) FROM tasmi_session GROUP BY status` |
| Total enrollments | pending + approved + rejected + cancelled, from `GROUP BY enrollment_status` |
| Total payments | pending + success + failed, from `GROUP BY payment_status` |
| Recitations / Evaluations | `COUNT(*) FROM recitation` and `COUNT(*) FROM evaluation` |

⚠ **Known defect:** the payment "Done"/"Open" chips query the legacy status names `SUCCESS` and `FAILED`, but the live enum uses `APPROVED` / `REJECTED` / `AWAITING_VERIFICATION`. These chips therefore usually display 0. The `/admin/payments` page uses the correct statuses.

### A3. User Management — List

`GET /admin/users?search=&role=` → `SELECT … FROM user WHERE status <> 'DELETED' [AND (full_name LIKE ? OR email LIKE ?)] [AND role=?] ORDER BY created_at DESC LIMIT 100`.

### A4. User Management — Create

`GET/POST /admin/users/create`. Validation: fullName 3–150; email regex `^[^@\s]+@[^@\s]+\.[^@\s]+$`, max 191, lowercased; phone 7–30; password ≥8 (the JSP's `minlength=6` is weaker than the server rule); role in `{STUDENT, INSTRUCTOR, ADMIN}`. Creates the `user` row with `is_active=1`, `email_verified=1`, `status=ACTIVE`, then the role profile row — an admin-created **instructor is inserted with `verification_status=APPROVED`**, bypassing the queue. Audit: `USER_CREATED`. Note: this page is not linked from the user list; the URL must be navigated to directly.

### A5. User Management — Update

`POST /admin/users/update`. **Role changes are blocked**: `"Changing role for existing users is not supported yet"`. Email uniqueness is checked excluding self. Only the `user` row is updated — profile tables are not synced. Audit: `USER_UPDATED`.

### A6. User Management — Activate / Deactivate

`POST /admin/users/toggle`. Cannot deactivate yourself; cannot deactivate the last active admin. Sets both `is_active` and `status`. Audit: `USER_ACTIVATED` / `USER_DEACTIVATED`. No cascade to child profile tables.

### A7. User Management — Delete (soft)

`POST /admin/users/delete`. **Soft delete only** — the row is anonymized, never removed:

```sql
UPDATE user SET is_active = 0, status = 'DELETED',
  email = CONCAT('deleted+', user_id, '+', UNIX_TIMESTAMP(), '@deleted.etasmi.local'),
  email_verified = 0, email_verified_at = NULL,
  email_verification_token_hash = NULL, email_verification_token_expires_at = NULL
WHERE user_id = ? AND status <> 'DELETED'
```

Guards: cannot delete self; cannot delete the last active admin. Audit: `USER_DELETED`. All subsequent queries exclude the user via `status <> 'DELETED'`.

### A8. Instructor Verification

| | |
|---|---|
| **URLs** | `GET /admin/instructors/verification?status=PENDING`, `GET /admin/instructors/pending` (redirect), `GET /admin/instructors/view?id=`, `POST /admin/instructors/verification` with `action=approve\|reject`, plus `POST /admin/instructors/approve` and `/reject` |
| **Approve** | Requires `user.email_verified = true` (otherwise rolled back). Sets `instructor.verification_status=APPROVED` and `user.status=ACTIVE`. In-app notification. Audit `INSTRUCTOR_APPROVED`. |
| **Reject** | Sets `verification_status=REJECTED`, `user.status=INACTIVE`. Audit `INSTRUCTOR_REJECTED`. |
| **Rejection reason** | **Not captured** — no form field, no DB column, only a generic notification |
| **Email** | **None sent** on approve or reject — in-app notification only |

### A9. Qualification Document Download

`GET /admin/instructors/qualification/download?instructorId=X[&inline=1]`. If the stored value is an HTTP(S) URL it redirects (generating a **signed, 300-second** Cloudinary download URL where applicable); if it is a local path it must pass `QualificationFileUtil.isAllowedQualificationPath` before being streamed. Sets `X-Content-Type-Options: nosniff`.

### A10. Payment Monitoring (read-only)

| | |
|---|---|
| **URL** | `GET /admin/payments` (+ `?ajax=detail&paymentId=`, `?export={type}`) |
| **Nature** | Explicitly a monitor. **There is no admin approve, reject, or refund endpoint** — money moves directly between student and instructor. |
| **Filters** | `studentId`, `instructorId`, `sessionId`, `status`, `fromDate`, `toDate`, `minAmount`, `maxAmount`, `search` (student name/email, session title, instructor name, payment reference), `sort`, `dir`, `page` (25/page) |
| **Base constraint** | `p.amount > 0` on all queries |
| **Stats** | total transactions, awaiting verification, approved, rejected, total revenue (`SUM(CASE WHEN APPROVED THEN amount ELSE 0 END)`), currency = `MAX(p.currency)` |
| **Charts** | Revenue by session (top 8) and by instructor (top 8), `WHERE payment_status='APPROVED'`, rendered with Chart.js 4.4.1 |
| **PDF exports** | `pdf`, `session`, `student`, `instructor`, `revenue-session`, `revenue-instructor`, `financial` — generated with **Apache PDFBox** via `AdminReportPdfUtil` |

### A11. Reports & Analytics

`GET /admin/reports?type=&dateFrom=&dateTo=&status=&role=&sessionMode=&export=csv|pdf`.

| `type` | Date column filtered | Status column | Metrics reported |
|---|---|---|---|
| `sessions` (default) | `ts.session_date` | `ts.status` | Total, Scheduled, Ongoing, Completed |
| `enrollments` | `ts.session_date` | `e.enrollment_status` | Total, Pending, Approved, Cancelled |
| `payments` | `DATE(p.payment_date)` | `p.payment_status` | Total, Approved, Awaiting Verification, Rejected |
| `verification` | `DATE(u.created_at)` | `i.verification_status` | Total, Pending, Approved, Rejected |
| `evaluations` | `DATE(r.submission_date)` | review existence | Total Submissions, Reviewed, Pending Review, Average Score |
| `users` | `DATE(created_at)` | `status` | Total, Students, Instructors, Active |

CSV export is UTF-8 with quoted fields; PDF via PDFBox with automatic portrait/landscape selection by column count. Filename pattern `etasmi-{type}-{date}.pdf`. ⚠ The "Master System Report" block in `reports.jsp` is a **static placeholder with hardcoded numbers**, not backend data.

### A12. Audit Logs

`GET /admin/logs?filterAction=&filterRole=&dateFrom=&dateTo=`. Newest first, **LIMIT 100**. Only **7 action types are ever written** by the code: `USER_CREATED`, `USER_UPDATED`, `USER_ACTIVATED`, `USER_DEACTIVATED`, `USER_DELETED`, `INSTRUCTOR_APPROVED`, `INSTRUCTOR_REJECTED`. The filter dropdown offers additional values (LOGIN, LOGOUT, SESSION_CREATED, …) that **no code path ever inserts**.

**Also:** `GET/POST /admin/profile` — actions `profile` (fullName, phone) and `password` (current verified, new ≥8 with upper+lower+digit). Email is read-only.

---

## PART D — SHARED / SYSTEM FEATURES (12)

### X1. Public Landing Page

`GET /home` → `home.jsp`. Live stats: `statStudents`, `statSessions` (scheduled + ongoing + completed, excluding cancelled), `statCompletionPct` (`ROUND(AVG(evaluation.score))` clamped 0–100, null when there are no evaluations). Sends `no-store, no-cache`. Public.

### X2. Index Redirect

`web/index.html` — meta refresh + `location.replace` to `/home`.

### X3. Dashboard Role Router

`GET /dashboard` — `DashboardRouterServlet`. Forwards `STUDENT` → `/student/dashboard`, `INSTRUCTOR` → `/instructor/dashboard`, `ADMIN` → `/admin/dashboard`; redirects to `/auth/login` when there is no role.

### X4. Shared Notifications Page

`GET /notifications` — `NotificationServlet`. Students are redirected to `/student/notifications`; instructors and admins get `jsp/common/notifications.jsp`. Read-only; **no POST, no mark-read, no delete, no JSON API**.

### X5. Notification Creation (system-wide)

`notifications` rows are created at 16 distinct points across `RegistrationService`, `InstructorVerificationService`, `EnrollmentService`, `PaymentService`, `RecitationService`, and `TasmiSessionService`. Full message templates are catalogued in `07_EXTERNAL_APIS_AND_INTEGRATIONS.md`. `NotificationService.createForUser` exists but has no call sites.

### X6. Uploaded File Serving

`GET /uploads/*` — `UploadedFileServlet`. Strips `..` and `\`, resolves under `ETASMI_UPLOADS_DIR` (default `/usr/local/tomcat/etasmi_uploads`), and verifies the resolved path stays under the root (403 if it escapes, 404 if missing). Sets `Cache-Control: public, max-age=86400`. ⚠ **This path is NOT in `AuthFilter` — every uploaded file is publicly readable by anyone who knows or guesses the URL**, including payment receipts and recitation audio.

### X7. Character Encoding Filter

`CharacterEncodingFilter` on `/*` for REQUEST, FORWARD, INCLUDE, ERROR — forces UTF-8 before any other filter reads parameters.

### X8. URL Localization Filter

`UrlLocalizationFilter` on `/*`. Strips `/en`, `/ar`, `/ms` prefixes and sets the locale + text direction. Unprefixed **GET** requests are 302-redirected to `/{locale}{path}`; non-GET requests pass through. Bypasses `/assets/`, `/css/`, and static file extensions.

### X9. Authentication Filter

`AuthFilter` on `/jsp/student/*`, `/jsp/instructor/*`, `/jsp/admin/*`, `/jsp/common/*`, `/student/*`, `/instructor/*`, `/admin/*`, `/notifications`, `/live/*`. Full rules in doc 06.

### X10. Internationalization

Three locales: `en` (LTR), `ar` (RTL), `ms` (LTR). Locale precedence: request attribute `currentLocale` → request attribute `locale` → cookie `etasmi.locale` (1 year, path `/`, **HttpOnly = false**) → `Accept-Language` → default `en`. Client dictionaries at `/assets/locales/{locale}.json` (~1,626 keys in `en`; 1,609 in `ar` and `ms`) consumed by `assets/js/i18n/etasmi-i18n.js`. Server-side bundles at `src/conf/messages_{en,ar,ms}.properties`. JSP binding uses `data-i18n`, `data-i18n-attr`, `data-i18n-count`, `data-i18n-vars`.

### X11. Theming

Light/dark toggle via `[data-etasmi-theme-toggle]` → `assets/js/etasmi-theme.js`, sets `html[data-theme]`, syncs Bootstrap's `data-bs-theme`, persists to `localStorage['etasmi.theme']`. An inline script in `etasmi_theme_init.jspf` applies the theme before paint to avoid a flash.

### X12. Startup Listeners

`SchemaBootstrap` (creates the full schema on an empty DB; initializes the bundled Quran catalog) and `DBSeeder` (incremental column/table migrations, admin seeding). **There are no scheduled jobs, timers, or background threads anywhere in the codebase** — verified by searching for `Timer`, `ScheduledExecutorService`, and `new Thread(`.

---

## PART E — BACKEND CAPABILITIES WITH NO USER INTERFACE

These must **not** be treated as working features, but they must also not be deleted from the data model, since the tables and columns exist and are referenced.

| Capability | What exists | What is missing |
|---|---|---|
| Teaching material upload | `session_material` table, `SessionMaterialDaoJdbc.insert()`, and the `/instructor/materials/open` serving servlet | No upload endpoint, no form. `insert()` has zero call sites. |
| Recitation Studio v2 | `recitation_submissions` table, `RecitationSubmission*` entities, `RecitationSubmissionService`, `RecitationLanguage` seeded with 6 languages | Not wired to `StudentRecitationServlet`; the live studio uses the legacy enrollment-bound `recitation` table |
| Enrollment withdrawal | `action=withdraw` in `StudentEnrollmentsServlet`, `EnrollmentService` support | No button in `enrollments.jsp` |
| Payment cancellation | `action=cancel` in `StudentPaymentsServlet` | No button in `payments.jsp` |
| Receipt reference / note | `reference` and `note` params accepted by `StudentQrPaymentServlet` | No inputs in `qr_payment.jsp` |
| Meeting password visibility toggle | `action=togglepassword` | No UI control |
| Instructor Zoom email | Written by `InstructorProfileServlet` | No field in `profile.jsp` |
| Instructor bank details | Validated by `InstructorPaymentsServlet` | UI is QR-only |
| Evaluation editing | `EvaluationDaoJdbc.update()` | `EvaluationService` only inserts |
| Manual attendance | `attendance` table with `ABSENT` status and `marked_by_instructor_id` | No instructor attendance UI; only auto-`PRESENT` on join |
| Session recording | `recording_status`, `recording_url`, `recording_synced_at` columns; `SessionRecordingStatus` enum | Zoom meetings are created with `auto_recording="none"`; no sync code |
| Admin payment verification | `payment.verified_by_admin_id` column + FK to `admin` | Only instructors verify; no admin path writes this column |
| Session mode `PHYSICAL` | `SessionMode` enum and `tasmi_session.mode` column | Creation always hardcodes `ONLINE`; students only see `ONLINE` |
| `instructor` payment columns | `payment_account_holder`, `payment_method_name`, `payment_account_details`, `payment_qr_url`, `payment_instructions` | Superseded by `instructor_payment_settings`; legacy |

---

## PART F — DEAD CODE

| File | Status | Evidence |
|---|---|---|
| `e-Tasmi/src/java/InstructorVerificationServlet.java` | Deliberately emptied stub — no package, no class | File contains only the comment *"Legacy duplicate servlet intentionally left blank."* The live one is `controller/admin/InstructorVerificationServlet` |
| `ZoomHostProbe.java` (repo root) | Would not compile — calls `getZakToken()`, which does not exist (actual: `getUserZakToken`) | Not in the build path |
| `ZoomHostResolutionProbe.java` (repo root) | Would not compile — references `TasmiSessionService.resolveZoomHostIdentity`, which does not exist | Not in the build path |
| `LiveSessionProbe.java` (repo root) | Would not compile — references `getZoomJoinUrl()`, `ZoomMeetingLinkUtil` | Not in the build path |
| `RegisterServlet.isAdminSession()` | Never called | — |
| `NotificationService.createForUser` | Never called | — |
| `web/js/lottie.min.js`, `assets/js/landing.js` | No script references | — |
| `web/mockups/*.html` | Static design mockups, never served | No references to `mockups/` anywhere |
| 7 orphan JSPF fragments | `student_sidebar_dashboard.jspf`, `student_header_dashboard.jspf`, `instructor_nav.jspf`, `admin_nav.jspf`, `public_header.jspf`, `public_footer.jspf`, `instructor_page_hero.jspf` | Never included |
| ~8 orphan CSS files | `landing-package.css`, `learnhub-landing.css`, `student-dashboard-classic.css`, `student-workspace.css`, `student-upload-recitation.css`, `student-sessions-redesign.css`, `admin-payments-control.css`, `instructor-financial-dashboard.css` | No JSP/JS reference |
| `db/etasmi.sql` | Legacy 10-table dump containing an `instructor_profile` table that does not exist in the canonical schema | Superseded by `e-Tasmi/setup/etasmi_schema.sql` |
