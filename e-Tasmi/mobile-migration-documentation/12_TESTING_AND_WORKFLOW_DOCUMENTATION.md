# 12 — TESTING AND WORKFLOW DOCUMENTATION

---

## 1. WHAT WAS ACTUALLY TESTED

### 1.1 Method

The application was running locally in Docker during the audit. Testing was **strictly read-only**: `Invoke-WebRequest` probes against public and protected endpoints to verify routing, authentication, and authorization behavior. **No data was created, modified, or deleted, and no application code or configuration was changed**, in accordance with the Phase 0 constraint.

### 1.2 Results

| # | Test | Expected | Result |
|---|---|---|---|
| 1 | `GET /home` | Redirect to a locale prefix | ✅ `302` → `/en/home` |
| 2 | `GET /en/home` | Landing page renders with live stats | ✅ `200`, 71,719 bytes |
| 3 | `GET /student/dashboard` (no session) | Redirect to login | ✅ `302` → `/auth/login?expired=1` |
| 4 | `GET /instructor/dashboard` (no session) | Redirect to login | ✅ `302` → `/auth/login?expired=1` |
| 5 | `GET /admin/dashboard` (no session) | Redirect to login | ✅ `302` → `/auth/login?expired=1` |
| 6 | `GET /student/api/quran-foundation/health` (no session) | JSON 401, not a redirect | ✅ `401 {"ok":false,"error":"unauthenticated"}` |
| 7 | `GET /en/auth/login` | Login page renders | ✅ `200` |
| 8 | `GET /en/auth/register` | Registration page renders | ✅ `200` |
| 9 | Locale prefix handling | GET redirected, non-GET passes through | ✅ Confirmed |
| 10 | Static assets | Served without authentication | ✅ `200` |

**Conclusion:** the documented routing, locale, authentication, and authorization behavior is confirmed against the running system. In particular, the split response contract — HTML routes redirect, `/student/api/*` returns JSON `401` — behaves exactly as `AuthFilter` describes.

---

## 2. WHAT WAS **NOT** TESTED

Per the instruction not to guess, each item below is marked explicitly.

### 2.1 Student workflows

```
NOT TESTED: Registration → Login → Browse → Enroll → Payment → Session → Recitation → Evaluation → Progress
Reason: Executing this flow requires creating real user accounts, enrollments,
        payments, and recitation records in the database. Phase 0 is explicitly
        a read/analyze/document phase and forbids modifying the existing system.
Evidence used instead: end-to-end source trace of every servlet, service, and DAO
        in the path, plus the SQL schema constraints that enforce each step.
How to verify: on a dedicated staging database seeded from setup/sample_data.sql,
        run the flow manually with a throwaway student account.
```

### 2.2 Instructor workflows

```
NOT TESTED: Registration → Admin verification → Session creation → Zoom meeting → Evaluation
Reason: Session creation issues a real Zoom API call and creates a real meeting on
        the connected Zoom account. Instructor approval mutates account state.
Evidence used instead: full source trace of ZoomMeetingService, InstructorSessionsServlet,
        InstructorVerificationService, and the persisted Zoom response fields.
How to verify: staging environment with a separate Zoom app credential set.
```

### 2.3 Admin workflows

```
NOT TESTED: Login → User management → Instructor verification → Payments → Reports
Reason: Requires admin credentials and would mutate user records and audit logs.
Evidence used instead: source trace of all 7 admin servlets and their SQL.
How to verify: staging environment with a dedicated admin account.
```

### 2.4 AI features

```
NOT TESTED: Quran Assistant chat, voice turn, recitation analysis, AI translation
Reason: Every path requires an authenticated student or instructor session AND a
        valid OPENAI_API_KEY. Executing them would incur real API charges and,
        for analysis, requires an existing recitation with a session quran_portion.
Evidence used instead: complete source trace of QuranAssistantService, QuranVoiceService,
        QuranAiTranslationService, and RecitationAiAnalysisService — including exact
        endpoints, model defaults, limits, prompt construction, and every error path.
How to verify: staging with a separate, budget-capped OpenAI key.
```

### 2.5 Integrations

```
NOT TESTED (live): Zoom meeting creation, Cloudinary upload, OpenAI calls,
                   Quran Foundation content calls, email delivery
Reason: All five mutate external state or incur charges. Zoom would create a real
        meeting; Cloudinary would store real assets; email would send real messages.
Evidence used instead: source trace of every client class, including auth mechanism,
        endpoints, request bodies, response handling, caching, and error mapping.
How to verify: staging credentials for each service.
```

### 2.6 Payment flow

```
NOT TESTED: Enrollment → receipt upload → instructor verification → access granted
Reason: Requires a paid session, a student account, a real uploaded file, and an
        instructor with usable payment settings — all of which are data mutations.
Note: There is NO payment gateway to test. Verification is a human action.
Evidence used instead: source trace plus the database constraints
        (uq_payment_enrollment, the payment_status enum, the history table).
How to verify: staging with a small-fee test session.
```

### 2.7 Coverage summary

| Area | Static analysis | Live verification |
|---|---|---|
| Routing & locale | ✅ | ✅ |
| Authentication gate | ✅ | ✅ |
| Authorization gate | ✅ | ✅ |
| JSON vs HTML error contract | ✅ | ✅ |
| Public pages | ✅ | ✅ |
| Business logic | ✅ | ❌ (would mutate data) |
| Database writes | ✅ | ❌ |
| External integrations | ✅ | ❌ (would mutate external state / incur cost) |
| AI subsystems | ✅ | ❌ (would incur cost) |

**Static analysis coverage is complete.** Every servlet, service, DAO, filter, listener, JSP, and JavaScript file was inspected, and every business rule in doc 05 is traceable to a specific enforcement point in the code.

---

## 3. WORKFLOW REFERENCE

These are the canonical end-to-end flows, derived from source. They are the specification the mobile app must reproduce and the basis for the parity tests in §4.

### 3.1 Student registration → first login

```
Register (role, name, email, phone, password, level)
   → validate · reject ADMIN role · BCrypt(12)
   → TRANSACTION: user (INACTIVE, unverified) + student (STD-{id}) + welcome notification
   → generate 6-digit code (10 min) and link token (24 h)
   → send email via Brevo → Resend → SMTP
Verify (code or link, max 5 attempts)
   → email_verified = 1 · status = ACTIVE · delete the code row
Login
   → checks: not DELETED · is_active · email_verified
   → session created, 15-minute idle timeout
   → 302 /dashboard → student dashboard
```

### 3.2 Instructor registration → approval

```
Register (+ bio, + qualification PDF ≤ 10 MB)
   → user (INACTIVE) + instructor (PENDING) + notification
Verify email
   → email_verified = 1, but status stays INACTIVE while PENDING
Login
   → succeeds, but every /instructor/* route forwards to pendingApproval.jsp
Admin approves
   → REQUIRES email_verified = true, else the transaction rolls back
   → verification_status = APPROVED · user.status = ACTIVE
   → notification + audit_log INSTRUCTOR_APPROVED   (NO email is sent)
Instructor logs in again
   → full access
```

### 3.3 Session creation

```
Instructor submits (title, level, date, time, duration, fee, capacity,
                    optional description / quran_portion / banner)
   → verify APPROVED
   → resolve host email: instructor.zoom_email → ZOOM_DEFAULT_HOST_EMAIL
   → Zoom S2S OAuth token (cached)
   → POST /v2/users/{host}/meetings   (auto_recording = "none")
   → persist zoom_meeting_id · meeting_link · meeting_password · zoom_start_url
   → INSERT tasmi_session (SCHEDULED, ONLINE, live_provider = ZOOM)

If Zoom fails at any point → NO session row is created. There is no fallback.
```

### 3.4 Enrollment — free session

```
Student enrolls (fee = 0)
   → visibility: SCHEDULED · ONLINE · future · instructor APPROVED & active
   → level match: session.level = student.level
   → capacity: blocked when capacity > 0 AND count(PENDING|APPROVED) >= capacity
   → INSERT enrollment status = APPROVED       ← immediate access
   → NO payment row is created
   → 302 /student/enrollments?notice=free_confirmed
```

### 3.5 Enrollment + payment — paid session

```
Student enrolls (fee > 0)
   → same visibility, level, and capacity checks
   → INSERT enrollment = PENDING
   → INSERT payment    = PENDING, amount ← session.fee, currency MYR
   → 302 /student/payments/qr

Student opens the QR page
   → requires instructor_payment_settings.isUsable()
     (active AND (QR image OR (bank name AND account holder)))
   → displays QR / bank details / amount

Student pays OUTSIDE the system (bank app, DuitNow) — no software involvement

Student uploads a receipt (≤ 10 MB; JPG/PNG/WEBP/GIF/PDF)
   → Cloudinary receipts/ (raw for PDF, image otherwise) or local
   → payment → AWAITING_VERIFICATION
   → history: SUBMITTED (or RESUBMITTED)
   → notify student + instructor

Instructor reviews
   ├─ APPROVE → payment APPROVED · payment_date = now
   │            · verified_by_instructor_id = me
   │            · ENROLLMENT → APPROVED        ← access is granted HERE
   │            · history APPROVED · notify student
   └─ REJECT (reason REQUIRED, ≤ 500)
                → payment REJECTED
                · ENROLLMENT → PENDING (not REJECTED)
                · history REJECTED · notify student
                · student may resubmit
```

### 3.6 Live session

```
Instructor: start
   → requires ≥ 1 APPROVED enrollment AND a valid join link
   → auto-completes any other ONGOING session by this instructor
   → status ONGOING · live_started_at = now
   → embed (role 1 + ZAK) or redirect to zoom_start_url

Student: join
   → enrolled · enrollment APPROVED · payment APPROVED when fee > 0
   → join URL present and valid http(s)
   → UPSERT attendance PRESENT
     note: "Auto-recorded when the student opened the live session join link."
   → embed (role 0) or redirect to meeting_link

Instructor: complete
   → status COMPLETED · live_ended_at = now
```

### 3.7 Recitation → evaluation

```
Student submits audio (≤ 200 MB)
   → requires enrollment APPROVED · session not CANCELLED
     · payment APPROVED when fee > 0
   → Cloudinary recitations/ via the VIDEO endpoint, or local
   → INSERT recitation (enrollment_id, audio_file_path)
   → NO AI is triggered

Instructor opens the evaluation queue
   → ownership via recitation → enrollment → tasmi_session.instructor_id

Instructor presses "Analyze"  (OPTIONAL)
   → requires OPENAI_API_KEY and session.quran_portion
   → transcribe  (gpt-4o-transcribe)
   → evaluate    (gpt-4o) with expected vs actual text
   → store in the HTTP SESSION only → etasmi.aiAnalysis.{recitationId}
   → statuses: OK · REJECTED · CANNOT_EVALUATE · FAILED
   → failure NEVER blocks manual grading

Instructor saves the evaluation (score 0-100, feedback)
   → INSERT evaluation      ← UNIQUE(recitation_id) makes this one-shot
   → recompute progress.completion_rate = AVG(evaluation.score) for the student
   → UPSERT progress
   → clear the AI session key   ← THE ANALYSIS IS NOW PERMANENTLY GONE

Instructor marks the session reviewed
   → evaluation_reviewed_at = now  (moves it to the "Reviewed Sessions" tab)
   → reopening sets it back to NULL
```

### 3.8 Admin soft delete

```
Admin deletes a user
   → cannot delete self
   → cannot delete the last active admin
   → UPDATE user SET status='DELETED', is_active=0,
                     email = CONCAT('deleted_', id, '_', email)
   → audit_log USER_DELETED

NO row is ever physically removed. The email is anonymized so the address can be
reused. This design exists because user → student → enrollment mixes CASCADE and
RESTRICT, so a real DELETE would fail for any student who has ever enrolled.
```

---

## 4. TEST PLAN FOR THE MOBILE MIGRATION

### 4.1 Parity testing — the primary gate

For every rule BR-1 … BR-147 in doc 05, the PHP backend must produce **the same outcome** as the Java application for the same input. This is the acceptance criterion for the migration.

Recommended method: run both systems against the same staging database, issue equivalent requests, and compare the resulting database state and response semantics.

### 4.2 Priority parity cases

| # | Case | Expected |
|---|---|---|
| P-1 | Login with an existing BCrypt hash | Succeeds with **no password reset** |
| P-2 | Login while unverified | Refused with the verification message |
| P-3 | Login while `is_active = 0` | Refused with the deactivation message |
| P-4 | Login as a PENDING instructor | **Succeeds**, but instructor routes are gated |
| P-5 | Wrong password vs unknown email | **Identical** generic message |
| P-6 | 6 failed logins in 5 minutes | Rate-limited on the 6th |
| P-7 | Register with `role=ADMIN` | Rejected |
| P-8 | Register a duplicate email | Rejected |
| P-9 | Verification code, 6th attempt | Refused even if correct |
| P-10 | Verification code after 10 minutes | Expired |
| P-11 | Reset token reused | Refused |
| P-12 | Student browses sessions | Sees **only** level-matching, SCHEDULED, ONLINE, future sessions from approved active instructors |
| P-13 | Enroll in a free session | Enrollment `APPROVED`, **no payment row** |
| P-14 | Enroll in a paid session | Enrollment `PENDING` + payment `PENDING` |
| P-15 | Enroll twice in the same session | Blocked by the unique constraint |
| P-16 | Re-enroll after cancelling | Existing row reactivated to `PENDING`, **not** a new row |
| P-17 | Enroll in a full session | Blocked |
| P-18 | Enroll when `capacity = 0` | **Allowed** — the check is disabled |
| P-19 | Enroll in a level-mismatched session | Blocked |
| P-20 | Upload a receipt when the instructor has no payment settings | Blocked |
| P-21 | Upload a receipt twice | Second blocked while `AWAITING_VERIFICATION` |
| P-22 | Resubmit after rejection | Allowed |
| P-23 | Instructor approves a payment | Payment `APPROVED` **and** enrollment `APPROVED` |
| P-24 | Instructor rejects without a reason | Rejected — reason is required |
| P-25 | Instructor rejects with a reason | Payment `REJECTED`, enrollment back to **`PENDING`** |
| P-26 | **Admin attempts to verify a payment** | **No such capability exists** |
| P-27 | Instructor verifies another instructor's payment | Forbidden |
| P-28 | Submit a recitation with an unapproved payment | Blocked |
| P-29 | Evaluate the same recitation twice | Second blocked by the unique constraint |
| P-30 | Evaluate with score 101 or −1 | Rejected by validation and CHECK |
| P-31 | Evaluate another instructor's recitation | Forbidden |
| P-32 | Save an evaluation | `progress.completion_rate` = AVG of all that student's scores |
| P-33 | AI analysis without `quran_portion` | Status `FAILED`, manual grading still available |
| P-34 | AI analysis with no API key | Status `FAILED`, manual grading still available |
| P-35 | Student joins a live session unpaid | Blocked |
| P-36 | Student joins a live session paid | Allowed, attendance `PRESENT` recorded |
| P-37 | Student joins twice | **One** attendance row (upsert) |
| P-38 | Instructor hosts another's session | Forbidden |
| P-39 | Instructor starts a session with no approved enrollments | Blocked |
| P-40 | Instructor starts a second session | The first is auto-completed |
| P-41 | Edit a COMPLETED session | Blocked — SCHEDULED only |
| P-42 | Delete a session that has enrollments | Blocked by the FK RESTRICT |
| P-43 | Create a session while Zoom is down | **No session row created** |
| P-44 | Admin approves an unverified instructor | Blocked — email verification required |
| P-45 | Admin deletes their own account | Blocked |
| P-46 | Admin deletes the last active admin | Blocked |
| P-47 | Admin changes a user's role | Blocked |
| P-48 | Admin deletes a user | **Soft delete** with email anonymization |
| P-49 | Deleted user attempts login | Refused; the user is invisible platform-wide |
| P-50 | Cross-role access attempt | Forbidden |

### 4.3 Mobile-specific test areas

| Area | What to test |
|---|---|
| **Token lifecycle** | Access-token expiry, refresh, refresh-token rotation, revocation on logout |
| **Instructor approval gate** | Rejecting an instructor mid-session locks them out on the **next request**, not at token expiry |
| **Audio recording** | Permission denial, interruption by a call, background, long recordings, low storage |
| **Large uploads** | 200 MB on mobile data, network loss mid-upload, resume, progress accuracy |
| **Zoom native SDK** | Join, host, background, call interruption, rotation, permission handling |
| **Camera receipt capture** | Capture, gallery pick, compression, orientation, low light |
| **Offline behavior** | Graceful degradation, cached reads, queued writes, clear messaging |
| **RTL layout** | Arabic across every screen |
| **Push notifications** | Only if added — currently there are **none** |
| **Deep links** | Email verification and password reset links opening the app |
| **Biometric unlock** | Only if added |
| **Battery** | The instructor cockpit currently polls every 30 s; verify the mobile replacement |

### 4.4 Regression protection for the web application

Because both systems share one database, every mobile release must be checked against the web app.

- [ ] Web login still works after any auth-related change
- [ ] Data written by mobile displays correctly on web
- [ ] Data written by web displays correctly on mobile
- [ ] Payment verification from either client produces identical state
- [ ] Evaluation from either client updates `progress` identically
- [ ] Schema changes remain **additive only**
- [ ] File URLs written by either client resolve in both

### 4.5 Recommended automated coverage

| Layer | Coverage |
|---|---|
| PHP unit tests | Every service, one test per business rule in doc 05 |
| PHP integration tests | Every endpoint: happy path, validation failure, auth failure, role failure, ownership failure |
| Repository tests | Constraint behavior — unique violations, CHECK violations, FK RESTRICT |
| Flutter widget tests | Every screen's states: loading, empty, error, populated |
| Flutter integration tests | The critical journeys in §3 |
| Contract tests | API response shape stability |
| Load tests | Concurrent recitation uploads, AI request bursts |

---

## 5. UNKNOWN / REQUIRES CLARIFICATION

Documented rather than guessed, as instructed.

### U-1 — Live production database shape
```
Unknown: whether the production database matches etasmi_schema.sql exactly.
Why: SchemaBootstrap only creates the full schema when the `user` table is missing.
     DBSeeder handles incremental patches but does NOT add tasmi_session.description,
     .level, .capacity, or .banner_image_url. A long-lived database may be missing them.
Evidence: SchemaBootstrap.java, DBSeeder, etasmi_schema.sql.
How to verify: run SHOW COLUMNS on the production database and diff against
     etasmi_schema.sql before migrating.
```

### U-2 — Whether the dev-override flags are set in production
```
Unknown: the production values of ETASMI_DISABLE_RATE_LIMIT,
     ETASMI_ALLOW_LOGIN_WITHOUT_EMAIL_VERIFICATION, ETASMI_BOOTSTRAP_ADMIN_PASSWORD.
Why: each individually defeats a security control, and environment values are not
     visible from the repository.
Evidence: the code paths that read them.
How to verify: inspect the Railway environment configuration.
```

### U-3 — Intended purpose of Recitation Studio v2
```
Unknown: whether recitation_submissions + recitation_language are abandoned or planned.
Why: both tables, their DAOs, and lazy schema creation exist, and 6 languages are
     seeded — but no UI writes to them. The live studio writes to `recitation`.
     recitation_submissions.student_id also references `user`, not `student`,
     unlike every other table.
Evidence: the DAOs, the schema, and the absence of call sites.
How to verify: ask the client. Keep the tables; do not build against them without
     a product decision.
```

### U-4 — Whether the UI-less capabilities should be exposed on mobile
```
Unknown: whether enrollment withdrawal, payment cancellation, meeting-password
     toggle, payment reference/note, and instructor Zoom email are intentionally
     hidden or simply unfinished.
Why: all are fully implemented server-side with no UI control.
Evidence: doc 05 §19.
How to verify: client decision per item — keep hidden, expose, or remove.
```

### U-5 — Whether teaching material upload is planned
```
Unknown: session_material has a full schema, a DAO, and a read endpoint, but
     SessionMaterialDaoJdbc.insert() has ZERO call sites. Materials can be read
     but can never be created.
Evidence: call-site analysis.
How to verify: client decision. If wanted on mobile, it is effectively a NEW feature.
```

### U-6 — Expected AI recitation accuracy
```
Unknown: whether current AI analysis quality is acceptable to instructors.
Why: analysis is never persisted, so there is no historical data to evaluate,
     and no instructor feedback mechanism on AI output exists.
Evidence: RecitationAiAnalysisService; the absence of any AI columns.
How to verify: persist results (an additive schema change requiring approval)
     and measure, or survey instructors.
```

### U-7 — Whether manual payment is intended long-term
```
Unknown: whether the client expects a real payment gateway on mobile.
Why: the original brief assumed ToyyibPay, which does not exist in the codebase.
     Manual verification is a deliberate, fully-implemented design.
Evidence: exhaustive search — no gateway of any kind.
How to verify: CLIENT DECISION REQUIRED. Adding a gateway is a new feature,
     explicitly out of scope for this phase.
```

### U-8 — Instructor title enum mismatch
```
Unknown: how instructor.title (SHEIKH | USTADH) relates to the profile UI's
     namePrefix options ("Austaz" / "Astazh"), which map to neither enum value.
Evidence: the schema enum vs instructor/profile.jsp.
How to verify: client clarification on the intended honorifics.
```

### U-9 — Production data volume
```
Unknown: row counts, media storage size, and peak concurrency.
Why: needed to size the PHP backend, choose pagination defaults, and estimate
     Cloudinary and OpenAI cost.
How to verify: query the production database and the Cloudinary dashboard.
```

### U-10 — Whether the web application will be retired
```
Unknown: whether the Java app remains in service after mobile launch.
Why: it determines the coexistence strategy. Two live systems on one database
     means every business-rule change must be applied twice, and schema changes
     must stay additive indefinitely.
How to verify: CLIENT DECISION REQUIRED — this shapes the entire migration plan.
```
