# 13 — COMPLETE MIGRATION REFERENCE

**The master technical reference for building the e-Tasmi mobile application.**

This document is written so that a senior developer who has never seen e-Tasmi can read it and reproduce the system using Flutter and PHP without repeatedly inspecting the Java codebase. Documents 01–12 contain the full detail; this document is the authoritative synthesis and, where it matters, points to the deeper source.

**Status:** Phase 0 complete. No mobile project has been created, no PHP backend written, and no existing code, configuration, or data modified.

---

## TABLE OF CONTENTS

| Part | Title | Detail in |
|---|---|---|
| 1 | System overview | doc 01 |
| 2 | Current architecture | doc 01 |
| 3 | Complete feature inventory | doc 02 |
| 4 | User roles and permissions | doc 06 |
| 5 | Complete database architecture | doc 03 |
| 6 | API inventory | doc 04 |
| 7 | Business rules | doc 05 |
| 8 | Authentication | doc 06 |
| 9 | External integrations | doc 07 |
| 10 | AI architecture | doc 08 |
| 11 | Payment architecture | doc 07 §5 |
| 12 | Zoom architecture | doc 07 §1 |
| 13 | Cloudinary / media architecture | doc 07 §2 |
| 14 | UI / page inventory | doc 09 |
| 15 | Mobile architecture proposal | doc 10 |
| 16 | Java → PHP mapping | doc 10 §3 |
| 17 | Flutter screen mapping | doc 09 §8 |
| 18 | Security requirements | doc 11 |
| 19 | Testing requirements | doc 12 |
| 20 | Migration risks | this document |
| 21 | Optional future improvements | this document |

---

# PART 1 — SYSTEM OVERVIEW

**e-Tasmi** is a Quran-memorization learning platform. *Tasmī'* (تسميع) is the traditional practice of reciting memorized Quran aloud to a qualified teacher for correction. The platform digitises that relationship: instructors schedule live online Tasmi sessions, students enrol and pay, they meet over Zoom, students submit recorded recitations, and instructors evaluate them — optionally assisted by AI.

### Roles

| Role | Description |
|---|---|
| **Student** | Enrols in sessions, pays, attends, submits recitations, receives evaluations, uses the Quran Library and AI assistant |
| **Instructor** | Creates and hosts sessions, verifies payments, evaluates recitations. **Requires admin approval before any access** |
| **Admin** | Manages users, approves instructors, views payments and reports. **Cannot create sessions and cannot verify payments** |

### Technology

| Layer | Technology |
|---|---|
| Frontend | JSP + JSTL, vanilla ES6, ~2 MB CSS across 62 files |
| Backend | Java 17 Servlets on Tomcat 9 |
| Data access | Direct JDBC — no ORM |
| Database | MySQL 8.x, InnoDB, `utf8mb4` |
| Build | **Ant / NetBeans — not Maven** |
| Deployment | Docker → Railway |
| Auth | `HttpSession` + `JSESSIONID` cookie |

### Scale

64 servlets · 76 URL patterns · 31 JSP pages · 27 JSPF fragments · 20 JS files · 20 database tables · 5 external services · 3 locales (en, ar, ms with RTL).

### What e-Tasmi is **not**

These corrections matter because several contradict the original project brief.

| Assumption | Reality |
|---|---|
| Uses ToyyibPay or any payment gateway | **No gateway exists.** Manual bank/QR transfer with instructor verification |
| Built with Maven | **Ant/NetBeans.** Dependencies are JARs committed to the repo |
| Has a PHP backend | **Java Servlets.** PHP is the future target, not the present |
| Records Zoom sessions | **`auto_recording="none"`.** Recording columns are permanently null |
| Sends email notifications for activity | **Only** verification and password reset. Everything else is in-app only |
| Has push notifications | **None** |
| Persists AI results | **None.** All AI output is ephemeral |
| Has scheduled jobs | **None.** No cron, no scheduler, no background worker |

---

# PART 2 — CURRENT ARCHITECTURE

```
   ┌──────────────────────────────────────────────────────┐
   │  Browser  ·  JSP + JSTL · vanilla ES6 · 62 CSS files │
   └──────────────────────────┬───────────────────────────┘
                              │  HTTP  (form posts + fetch)
                              ▼
   ┌──────────────────────────────────────────────────────┐
   │                Tomcat 9  ·  e-Tasmi WAR              │
   │                                                      │
   │   CharacterEncodingFilter        UTF-8               │
   │             ▼                                        │
   │   UrlLocalizationFilter          /en /ar /ms         │
   │             ▼                                        │
   │   AuthFilter                     session + role      │
   │             ▼                                        │
   │   64 Servlets  ──▶  Service layer  ──▶  DAO layer    │
   │                     (ALL business rules)   (JDBC)    │
   └───────────────────────────┬──────────────────────────┘
               │               │
               │               ▼
               │        ┌─────────────┐
               │        │   MySQL 8   │  20 tables
               │        └─────────────┘
               │
               │  all outbound calls are SERVER-SIDE
               ├──▶ Zoom              S2S OAuth + Meeting SDK
               ├──▶ Cloudinary        signed uploads
               ├──▶ OpenAI            chat · STT · TTS
               ├──▶ Quran Foundation  OAuth2 client credentials
               └──▶ Brevo / Resend / SMTP

   Browser talks outward directly in only two places:
      • Zoom Web SDK        (signature minted by our server)
      • Quran chapter audio (public CDN)
```

### Request pipeline

1. **`CharacterEncodingFilter`** — forces UTF-8.
2. **`UrlLocalizationFilter`** — redirects unprefixed GETs to `/{en|ar|ms}{path}`; non-GET passes through. Servlets always see the stripped path.
3. **`AuthFilter`** — public-path check → session check → prefix/role check → instructor approval re-read from the database.
4. **Servlet** → **Service** (all business rules) → **DAO** (`PreparedStatement`) → **MySQL**.
5. Response: JSP forward, or JSON for `/student/api/*` and `/instructor/api/*`.

### Startup

`SchemaBootstrap` creates the full schema **only when the `user` table is missing**, then `DBSeeder` applies incremental patches and seeds an admin. There are no formal migrations — see Risk R-4.

---

# PART 3 — COMPLETE FEATURE INVENTORY

### Counts

| Role | Features |
|---|---|
| **Student** | **24** |
| **Instructor** | **18** |
| **Admin** | **13** |
| **Shared / System** | **11** |
| **Total** | **66** |

### Student (24)

Registration · email verification (code + link) · login · logout · password reset · dashboard · browse sessions (level-filtered) · session details · enrol (free) · enrol (paid) · view payments · view QR/bank details · upload receipt · resubmit after rejection · download receipt PDF · view enrollments · join live session · auto-attendance · submit recitation (record or upload) · play own recitation · view evaluations and feedback · view progress · Quran Library (browse, search, translations, tafsir, audio, AI translate) · AI Quran Assistant (text + voice) · notifications · profile and photo.

### Instructor (18)

Registration with qualification PDF · verification gate · login · cockpit dashboard with calendar and 30-second heartbeat · create session (with Zoom) · edit session (SCHEDULED only) · delete session · start session · complete session · host live session · view participants · evaluation queue (Active / Reviewed tabs) · play student recitation · **AI recitation analysis** · save evaluation (one-shot) · mark reviewed / reopen · configure payment settings (QR, bank) · **verify payments (approve/reject)** · open teaching materials (read-only) · profile and photo · notifications.

### Admin (13)

Login · dashboard KPIs · list users · create user (any role) · edit user · activate/deactivate · soft-delete · instructor verification queue · download qualification PDF (signed URL) · approve/reject instructor · payments console (read-only, 10 filters, 12 exports) · reports (6 types, CSV/PDF) · audit logs · profile.

### Shared / System (11)

Locale routing (en/ar/ms + RTL) · dark/light theme · role-based dashboard router · notifications (all roles) · session timeout handling · rate limiting · email transport chain · dual-mode file storage · audit logging · schema bootstrap/seeding · public landing page with live stats.

### Implemented but with **no user interface**

| Capability | Where |
|---|---|
| Withdraw enrollment | `POST /student/enrollments` `action=withdraw` |
| Cancel payment | `POST /student/payments` `action=cancel` |
| Toggle meeting-password visibility | `POST /instructor/sessions` `action=togglepassword` |
| Payment reference / student note | `POST /student/payments/qr` |
| Instructor Zoom email | `POST /instructor/profile` |
| Teaching material **upload** | `SessionMaterialDaoJdbc.insert()` — **zero call sites** |
| Recitation Studio v2 | `recitation_submissions` + `recitation_language` |
| Admin payment verification | `payment.verified_by_admin_id` — never written |
| Attendance `ABSENT` | Enum value never written |
| Meeting recording | Disabled at creation |

**These require a client decision — keep hidden, expose, or remove.** See U-4 and U-5 in doc 12.

---

# PART 4 — USER ROLES AND PERMISSIONS

The complete matrix is in doc 06 §6. The rules that most often surprise people:

| Rule | Why it matters |
|---|---|
| **Admins cannot verify payments** | Only the owning instructor can. `verified_by_admin_id` exists and is never written |
| **Admins cannot create or manage sessions** | They see aggregate data through reports only |
| **Instructors have no AI assistant** | Chat and voice are student-only; analysis is instructor-only |
| **A PENDING instructor can log in** | Approval is an *authorization* gate, not an authentication one |
| **Nobody can change a user's role** | Explicitly rejected for everyone, including admins |
| **Nobody can hard-delete a user** | Soft delete only |
| **Students cannot delete a recitation** | Submissions are permanent |
| **Instructors cannot edit an evaluation** | Grading is one-shot, enforced by a unique constraint |

---

# PART 5 — COMPLETE DATABASE ARCHITECTURE

**20 tables**, MySQL 8, InnoDB, `utf8mb4_unicode_ci`. Authoritative file: `e-Tasmi/setup/etasmi_schema.sql`. Full column-level detail in doc 03.

### Tables

| # | Table | Purpose |
|---|---|---|
| 1 | `user` | Root identity for all three roles |
| 2 | `student` | Student profile (level, registration number) |
| 3 | `instructor` | Instructor profile, verification status |
| 4 | `admin` | Admin profile (minimal) |
| 5 | `instructor_payment_settings` | Live QR/bank payment configuration |
| 6 | `tasmi_session` | The central business entity |
| 7 | `enrollment` | Student ↔ session junction |
| 8 | `payment` | One per enrollment |
| 9 | `payment_verification_history` | Payment audit trail |
| 10 | `recitation` | Submitted audio, bound to an enrollment |
| 11 | `evaluation` | One per recitation, score 0–100 |
| 12 | `progress` | Aggregate completion rate per student |
| 13 | `attendance` | Auto-recorded on join |
| 14 | `session_material` | Schema present, **no upload path** |
| 15 | `notifications` | In-app only, **no read state** |
| 16 | `email_verification` | 6-digit code, 10-minute TTL |
| 17 | `password_reset` | Single-use token, 30-minute TTL |
| 18 | `audit_log` | 7 action types only |
| 19 | `recitation_language` | Studio v2, 6 seeded rows, **no UI** |
| 20 | `recitation_submissions` | Studio v2, **no UI** |

### ERD

```
                          ┌──────────────────┐
                          │       user       │
                          │ PK user_id       │
                          │ UQ email         │
                          │    role, status  │
                          └─┬────┬────┬────┬─┘
              1:0..1 ┌──────┘    │    │    └──────┐ 1:N
                     ▼      1:0..1│    │1:0..1     ▼
              ┌───────────┐       ▼    ▼    ┌──────────────────┐
              │  student  │  ┌──────────┐┌──────┐ notifications │
              │PK student_│  │instructor││admin │ email_verif.  │
              │   id      │  │ PK instr ││      │ password_reset│
              │UQ user_id │  │ verifica-││      │ audit_log     │
              │   level   │  │ tion_    ││      │ recitation_   │
              └──┬─────┬──┘  │ status   ││      │   submissions │
                 │     │     └──┬────┬──┘└──────┘───────────────┘
          1:0..1 │     │  1:0..1│    │ 1:N
                 ▼     │        ▼    │
         ┌───────────┐ │ ┌──────────────────┐
         │ progress  │ │ │instructor_payment│
         │PK student_│ │ │    _settings     │
         │   id      │ │ │ UQ instructor_id │
         │completion_│ │ │ qr_image_url     │
         │  rate     │ │ │ bank_name        │
         │CHECK 0-100│ │ └──────────────────┘
         └───────────┘ │        │
                       │        ▼
                       │  ┌────────────────────────────┐
                       │  │      tasmi_session         │
                       │  │ PK session_id              │
                       │  │ FK instructor_id  RESTRICT │
                       │  │    level / mode / status   │
                       │  │    fee / capacity          │
                       │  │    quran_portion  ◄── AI   │
                       │  │    zoom_meeting_id         │
                       │  │    evaluation_reviewed_at  │
                       │  └──┬──────┬──────────┬───────┘
                 M:N   │     │      │1:N       │1:N
          ┌────────────┴─────▼──┐   ▼          ▼
          │      enrollment     │ ┌──────────┐ ┌─────────────┐
          │ PK enrollment_id    │ │attendance│ │session_     │
          │ FK student_id  RESTR│ │UQ(session│ │  material   │
          │ FK session_id  RESTR│ │ ,student)│ │(no upload)  │
          │ UQ(student,session)◄┼─┤ PRESENT  │ └─────────────┘
          │    enrollment_status│ └──────────┘
          └───┬──────────────┬──┘
       1:0..1 │              │ 1:N
              ▼              ▼
    ┌─────────────────┐  ┌──────────────────┐
    │     payment     │  │    recitation    │
    │ UQ enrollment_id│  │ FK enrollment_id │
    │    amount       │  │ audio_file_path  │
    │    payment_     │  └────────┬─────────┘
    │      status     │           │ 1:0..1
    │ receipt_file_   │           ▼
    │      path       │  ┌──────────────────┐
    │ FK verified_by_ │  │    evaluation    │
    │  instructor_id  │  │ UQ recitation_id │
    │ FK verified_by_ │  │ FK instructor_id │
    │  admin_id       │  │  score 0-100     │
    │  (NEVER WRITTEN)│  │     CHECK        │
    └────────┬────────┘  └──────────────────┘
             │ 1:N
             ▼
   ┌──────────────────────────┐
   │payment_verification_     │
   │        history           │
   │ SUBMITTED / RESUBMITTED  │
   │ APPROVED  / REJECTED     │
   └──────────────────────────┘

   Standalone (Studio v2, no UI):
      recitation_language ──1:N──▶ recitation_submissions ──FK──▶ user
```

### The constraints that encode business rules

| Constraint | Rule it enforces |
|---|---|
| `uq_enrollment_student_session` | No duplicate enrollment |
| `uq_payment_enrollment` | Exactly one payment per enrollment |
| `uq_evaluation_recitation` | One evaluation per recitation — grading is one-shot |
| `uq_attendance_session_student` | Enables the join upsert |
| `chk_evaluation_score` | Score 0–100 |
| `chk_progress_completion_rate` | Completion rate 0–100 |
| `uq_user_email` | Globally unique email; why soft delete anonymizes it |
| FK RESTRICT `tasmi_session → enrollment` | Cannot delete a session that has any enrollment |
| FK RESTRICT `student → enrollment` | Why user deletion must be soft |

### Known schema issues

1. `recitation_submissions.student_id` references **`user`**, not `student` — the only such inconsistency.
2. `recitation.audio_file_path` is `VARCHAR(255)` while other media columns are 500–1024. Long Cloudinary URLs risk truncation.
3. `enrollment`, `evaluation`, and `student` have **no timestamp columns** — enrollment dates must be inferred from the related payment.
4. `notifications` has no `is_read`, no type, and no deep link.
5. `DBSeeder` does not add `tasmi_session.description`, `.level`, `.capacity`, or `.banner_image_url` — a long-lived database may lack them. **Verify with `SHOW COLUMNS` before migrating.**

---

# PART 6 — API INVENTORY

**76 URL patterns across 64 servlets.** Complete per-endpoint detail in doc 04.

| Group | Patterns | JSON? |
|---|---|---|
| Public / auth | 11 | 1 |
| Shared authenticated | 2 | 0 |
| Student HTML | 19 | 0 |
| Student JSON API | 15 | 14 |
| Instructor | 12 | 3 |
| Admin | 17 | 1 partial |

**Only ~20 of 76 return JSON.** The rest render JSP or redirect, so a mobile client cannot consume them. This is the central reason a PHP REST layer is required.

### Conventions a mobile client must know

- **Locale prefix:** GETs are redirected to `/{en|ar|ms}{path}`.
- **Auth:** `JSESSIONID` cookie only. No `Authorization` header is read anywhere.
- **Unauthenticated:** HTML → `302 /auth/login?expired=1`; JSON → `401 {"ok":false,"error":"unauthenticated"}`.
- **Forbidden:** HTML → `302 /dashboard?forbidden=1`; JSON → `403 {"ok":false,"error":"forbidden"}`.
- **Flash messages travel in query parameters** on a redirect, not in a response body.
- **No CORS headers, no API versioning, and pagination on only one endpoint.**

---

# PART 7 — BUSINESS RULES

**147 rules catalogued** in doc 05, each with its enforcement point. The ones that define the product:

### Enrollment and payment

1. Free session (`fee = 0`) → enrollment created **`APPROVED`**, **no payment row**. Immediate access.
2. Paid session (`fee > 0`) → enrollment **`PENDING`** + payment **`PENDING`**, amount copied from `session.fee`.
3. **Payment approval is what flips the enrollment to `APPROVED`.** That is the moment access is granted.
4. Rejection **requires a reason** and reverts the enrollment to **`PENDING`** (not `REJECTED`), so the student can resubmit.
5. **Only the owning instructor can verify a payment.** Admins cannot.
6. A student may hold only one enrollment row per session. Re-enrolling after cancelling **reactivates the existing row**.
7. Capacity is blocked when `capacity > 0 AND count(PENDING|APPROVED) >= capacity`. **`capacity = 0` disables the check.**
8. Currency is always MYR. No refunds, no partial payments, no payment expiry.

### Session visibility

A session appears to a student only when **all** hold: `status = SCHEDULED` · `mode = ONLINE` · `session_date >= today` · instructor `APPROVED` · instructor user active and not deleted · **`session.level = student.level`** (applied in Java after the query).

### Sessions

9. Only an `APPROVED` instructor can create a session.
10. **Session creation requires a working Zoom integration.** If Zoom fails, no row is created — there is no fallback.
11. Editing is allowed **only** while `SCHEDULED`.
12. Starting requires ≥1 `APPROVED` enrollment and **auto-completes any other ongoing session** by that instructor.
13. Sessions never auto-transition by time. There is no scheduler anywhere in the system.

### Recitation and evaluation

14. A recitation is bound to an **enrollment**, not a session or student.
15. Submission requires enrollment `APPROVED` and payment `APPROVED` when fee > 0.
16. Multiple recitations per enrollment are allowed; none can be deleted.
17. **One evaluation per recitation, not editable.** Enforced by a unique constraint.
18. Score 0–100, enforced by both the service and a CHECK.
19. Saving recomputes `progress.completion_rate = AVG(evaluation.score)` across all the student's evaluated recitations.
20. **AI never blocks grading** and **AI never sets the score.**

### Identity

21. Self-registration is limited to STUDENT and INSTRUCTOR; **ADMIN is rejected**.
22. Password ≥8 with upper, lower, and digit. BCrypt cost 12.
23. Instructor approval **requires a verified email**.
24. Deletion is always soft, with email anonymization.
25. Cannot deactivate or delete **self** or the **last active admin**.
26. **Role can never be changed** after creation.

### Deliberately absent

No payment gateway · no scheduler · no push · no email beyond verification and reset · no chat · no waitlist · no recurring sessions · no certificates · no ratings · no instructor payouts · no refunds · no self-deletion.

---

# PART 8 — AUTHENTICATION

Session-based. `HttpSession` + `JSESSIONID`, **15-minute idle timeout**, no refresh, no remember-me, no token auth. BCrypt cost 12 with legacy PBKDF2 verification (never upgraded on login).

Authorization is prefix-based in `AuthFilter`: `/admin/*` → ADMIN, `/instructor/*` → INSTRUCTOR, `/student/*` → STUDENT, plus a live database re-read of `verification_status` on every instructor request. Ownership is checked separately in each service.

**JWT exists in the codebase but is never used for application login** — only for Zoom Meeting SDK signatures and Zoom OAuth.

### For the mobile migration

Add a token layer in PHP, and preserve:

- BCrypt cost 12 — PHP `password_verify` accepts the existing hashes, so **no password reset campaign is needed**.
- Login preconditions: `status <> 'DELETED'` · `is_active = 1` · `email_verified = 1`.
- Instructor approval **re-read per request**, never cached in a token.
- Generic anti-enumeration error messages.
- Rate limits — and extend them to AI and upload endpoints, which currently have none.

---

# PART 9 — EXTERNAL INTEGRATIONS

| Service | Purpose | Auth | Client-visible? |
|---|---|---|---|
| Zoom | Meetings + embedded live sessions | S2S OAuth + SDK JWT | SDK key only |
| Cloudinary | Media storage | Signed upload | Cloud name only |
| OpenAI | All AI | Bearer key | **Never** |
| Quran Foundation | Quran content | OAuth2 client credentials | **Never** |
| Brevo / Resend / SMTP | Verification + reset email | API key or SMTP auth | **Never** |

All calls are server-side via `HttpURLConnection`. The only direct client-to-external connections are the Zoom Web SDK (with a server-minted signature) and Quran CDN audio (public URLs).

---

# PART 10 — AI ARCHITECTURE

Four OpenAI-backed subsystems. Full detail in doc 08.

| # | Subsystem | Role | Model (default) |
|---|---|---|---|
| 1 | Quran Assistant chat | Student | `gpt-4o-mini` |
| 2 | Voice assistant (STT → chat → TTS) | Student | `gpt-4o-transcribe` → `gpt-4o-mini` → `gpt-4o-mini-tts` |
| 3 | AI verse translation | Student | `gpt-4o-mini` |
| 4 | **Recitation analysis** | Instructor | `gpt-4o-transcribe` → **`gpt-4o`** |

### The two facts that shape everything

1. **No AI output is ever persisted.** Analysis lives in the HTTP session under `etasmi.aiAnalysis.{recitationId}` and is deleted when the evaluation is saved. Chat history lives in browser memory, capped at 12 messages.
2. **AI is never triggered automatically.** Submission does not invoke AI; an instructor must press "Analyze".

### Recitation analysis flow

```
Instructor presses Analyze
  → requires OPENAI_API_KEY and session.quran_portion (the EXPECTED text)
  → transcribe the audio          (gpt-4o-transcribe, Arabic hint)
  → compare expected vs actual    (gpt-4o)
      → word differences · tajwīd notes · suggested score · feedback
  → store in the HTTP session only
  → statuses: OK · REJECTED · CANNOT_EVALUATE · FAILED
  → failure NEVER blocks manual grading
Instructor types a score and saves
  → the AI suggestion is ADVISORY; only the typed value is stored
  → the session key is cleared — THE ANALYSIS IS GONE
```

**Limits to reproduce exactly:** 12-message history · 2,400-char message · 12,000-char reply · `temperature 0.35` · `max_tokens 1800` · 90-second timeout · 320-verse translation cap in batches of 30 · 25 MB voice upload.

**There are no cost controls of any kind** — no quota, no spend cap, no per-user budget.

---

# PART 11 — PAYMENT ARCHITECTURE

**There is no payment gateway.** ToyyibPay, Stripe, Billplz, PayPal, and Razorpay appear nowhere — no SDK, no callback, no webhook, no transaction ID, no merchant credentials.

```
Student enrols in a paid session
   → enrollment PENDING · payment PENDING (amount ← session.fee)
Student views the instructor's QR / bank details
   → requires instructor_payment_settings.isUsable()
Student pays OUTSIDE the system  (bank app / DuitNow)
Student uploads a receipt (≤10 MB, image or PDF)
   → payment AWAITING_VERIFICATION · history SUBMITTED · notify both
Instructor reviews the receipt manually
   ├─ APPROVE → payment APPROVED · ENROLLMENT APPROVED ← access granted
   └─ REJECT (reason required) → payment REJECTED · enrollment back to PENDING
                                 → student may resubmit
```

No callbacks, no automatic verification, no refunds, no expiry. Duplicate enrollment payments are prevented by `uq_payment_enrollment`; a duplicate *bank transfer* is invisible to the system. **Admins are read-only.**

**Reproduce this exactly.** Adding a gateway is a new feature and out of scope — see U-7.

---

# PART 12 — ZOOM ARCHITECTURE

Two credential sets for two purposes:

| | Server-to-Server OAuth | Meeting SDK |
|---|---|---|
| Purpose | Create / update / delete meetings | Embed the live meeting |
| Credentials | `ZOOM_ACCOUNT_ID`, `ZOOM_CLIENT_ID`, `ZOOM_CLIENT_SECRET` | `ZOOM_MEETING_SDK_KEY`, `ZOOM_MEETING_SDK_SECRET` |
| Token | OAuth token from Zoom (cached, 60 s margin) | HS256 JWT minted by us |

**Persisted per session:** `zoom_meeting_id`, `meeting_link`, `meeting_password`, and `zoom_start_url` (**sensitive — grants host rights, stored in plaintext**).

`auto_recording` is hard-coded to `"none"`, which is why all recording columns are permanently null.

Signatures: students get `role=0`; instructors get `role=1` plus a **ZAK token** fetched from `GET /v2/users/{host}/token?type=zak`, which is what allows the SDK to *start* the meeting. **The SDK secret never leaves the server.**

**Mobile:** the Web SDK is unusable in Flutter. Use the native Zoom Meeting SDK for Android/iOS, or hand off to the installed Zoom app. **Prototype this early — it is the highest-uncertainty item in the migration.**

---

# PART 13 — CLOUDINARY / MEDIA ARCHITECTURE

Dual-mode: Cloudinary when all three credentials are present, otherwise the local `ETASMI_UPLOADS_DIR`. **Both URL forms coexist in the same database columns** — absolute `https://res.cloudinary.com/...` and relative `/uploads/...`. Any consumer must handle both.

| Asset | Folder | Limit | `resource_type` |
|---|---|---|---|
| Profile photo | `profile/` | 2 MB | image |
| Qualification PDF | `qualifications/` | 10 MB | raw |
| Payment QR | `payment-qr/` | 8 MB | image |
| Payment receipt | `receipts/` | 10 MB | image or raw |
| Recitation audio | `recitations/` | **200 MB** | **video** |
| Session banner | `session-banners/` | 3 MB | image |

**Two problems to fix on mobile:**

1. **`/uploads/*` is unauthenticated.** Receipts and recitation audio are publicly readable by URL. Only qualification PDFs use signed (300 s) URLs.
2. **No Cloudinary `destroy` call exists anywhere.** Replaced assets are orphaned permanently.

Also note: **local storage mode cannot survive on Railway** — the filesystem is ephemeral, so any locally written file is lost on redeploy. Cloudinary must be mandatory in production.

---

# PART 14 — UI / PAGE INVENTORY

31 JSP pages: 8 auth/public, 13 student, 7 instructor, 7 admin, 1 shared. Full inventory in doc 09.

### Design tokens for the Flutter theme

| Token | Light | Dark |
|---|---|---|
| Primary | `#0f766e` | `#2dd4bf` |
| Accent | `#d97706` | `#f59e0b` |
| Success | `#16a34a` | `#4ade80` |
| Warning | `#d97706` | `#fbbf24` |
| Danger | `#dc2626` | `#f87171` |
| Background | `#f5f7fb` | `#0b1220` |
| Surface | `#ffffff` | `#111b32` |
| Border | `#e2e8f0` | `#1f2c48` |
| Text | `#0f172a` | `#e6eaf3` |

**Typography:** Poppins for display/headings, Inter for body. **No dedicated Quranic Arabic font is loaded** — the mobile app should improve on this for verse rendering.

The site is responsive (breakpoints at 1100/900/768/600/480/360) but **desktop-first**, which is part of why a native rebuild is justified over a WebView wrapper.

---

# PART 15 — MOBILE ARCHITECTURE PROPOSAL

```
   Flutter App          holds NO third-party secret
        │  HTTPS · Bearer token · JSON
        ▼
   PHP REST API         holds ALL secrets
   middleware → controllers → services (all rules) → repositories
        │                              │
        ▼                              ▼
   External services            MySQL — SAME SCHEMA, unchanged
```

### Invariants

1. The database schema does not change. Both systems read the same data.
2. No secret ships in the Flutter binary.
3. Business rules stay in the service layer, exactly as in Java.
4. Every rule in doc 05 is reproduced verbatim unless the client approves a change.

### Coexistence

The Java web app keeps running against the same database. Consequences: two independent auth models (acceptable), **schema changes must be additive only**, shared BCrypt hashes (no reset campaign), and a real risk of business-rule drift. **Freezing Java feature work during migration is strongly advised.**

### Phases

0 — documentation (**this package**) · 1 — PHP skeleton + auth middleware · 2 — auth API · 3–4 — student APIs · 5 — instructor APIs · 6 — admin APIs · 7 — Quran/AI APIs · 8 — Zoom signatures + native SDK · 9 — Flutter shell · 10–13 — Flutter modules · 14 — live sessions · 15 — parity testing · 16 — release.

---

# PART 16 — JAVA → PHP MAPPING

| Java | PHP |
|---|---|
| `@WebServlet` | Controller + route |
| `doGet` / `doPost` with an `action` param | Distinct REST routes |
| `forward` → JSP | `JsonResponse` |
| `sendRedirect(?notice=x)` | `{ "ok": true, "message": "…" }` |
| Service class | Service class — **port logic 1:1** |
| `*DaoJdbc` | Repository with PDO |
| Java `enum` | PHP 8.1 backed enum, **same string values** |
| `AuthFilter` | Auth + role middleware |
| `UrlLocalizationFilter` | **Drop** — locale becomes a header |
| `SchemaBootstrap` / `DBSeeder` | Real migrations (Phinx/Doctrine); **remove the boot-time admin password reset** |
| `util/Db` | PDO factory **with pooling** |

Full servlet-to-route table in doc 10 §3.2.

---

# PART 17 — FLUTTER SCREEN MAPPING

| Role | Bottom tabs | Drawer |
|---|---|---|
| **Student** | Home · Browse · My Sessions · Recite · Quran | Profile, Payments, Progress, Notifications, Settings |
| **Instructor** | Cockpit · Sessions · Evaluate · Payments | Profile, Notifications, Settings |
| **Admin** | Dashboard · Users · Verification · Payments | Reports, Audit Logs, Profile, Settings |

Full page-by-page mapping in doc 09 §8. The largest single conversion is the **Quran Library** (101 KB of JS, 116 KB of CSS), which becomes a multi-screen module: surah list, reader, search, audio player, assistant.

---

# PART 18 — SECURITY REQUIREMENTS

**~62 configuration variables, 13 of them secrets.** None may ever appear in a Flutter binary, mobile config file, API response, or client log.

### Findings: 3 HIGH, 9 MEDIUM, 6 LOW

| Severity | Finding |
|---|---|
| **HIGH** | No CSRF protection anywhere |
| **HIGH** | `/uploads/*` is unauthenticated — receipts and recitation audio are public |
| **HIGH** | Dev-override flags can disable security controls in production |
| MEDIUM | Bootstrap admin password reset on every boot · default seeded admin credentials · session cookie flags unset · no security headers · email enumeration via `/auth/verify-status` · `zoom_start_url` in plaintext · no AI cost controls · `useSSL=false` to MySQL · rate limiting too narrow |
| LOW | Files never deleted · no account lockout · legacy hashes never upgraded · GET logout · thin audit coverage · admin form weaker than the server |

### Three variables that must be verified unset in production

`ETASMI_DISABLE_RATE_LIMIT` · `ETASMI_ALLOW_LOGIN_WITHOUT_EMAIL_VERIFICATION` · `ETASMI_BOOTSTRAP_ADMIN_PASSWORD`

Each individually defeats a security control. Full checklist in doc 11 §6.

### What is already correct

BCrypt cost 12 · `PreparedStatement` everywhere with no injection vector · session-fixation defence · path-traversal containment · `ImageIO` file validation · no secret reaching any client · ownership checks with no IDOR found.

---

# PART 19 — TESTING REQUIREMENTS

**Verified live (read-only):** locale routing, the authentication gate on all three role prefixes, the JSON-vs-HTML error contract, and public page rendering. All behaved exactly as documented.

**Not tested, and why:** every business workflow, database write, external integration, and AI call. Executing any of them would create or modify data, mutate external state, or incur charges — all forbidden in a read/analyze/document phase. Each is documented with its evidence base and a verification method in doc 12 §2.

**Static analysis coverage is complete:** every servlet, service, DAO, filter, listener, JSP, and JS file was inspected, and every one of the 147 business rules traces to a specific enforcement point.

**The migration gate:** 50 priority parity cases in doc 12 §4.2. The mobile app is not release-ready until every rule in doc 05 is verified reproduced.

---

# PART 20 — MIGRATION RISKS

| ID | Risk | Severity | Why | Current behavior | Recommended approach |
|---|---|---|---|---|---|
| **R-1** | **Business-rule drift between two live systems** | **CRITICAL** | Java and PHP will share one database. Any rule changed in one and not the other silently corrupts data | 147 rules embedded in Java services | Freeze Java feature work; port rules 1:1 against a written checklist; parity-test all 50 priority cases |
| **R-2** | **Zoom Web SDK is unusable in Flutter** | **HIGH** | Live sessions are the core delivery mechanism; the Web SDK has no Flutter equivalent | Web SDK embed with a server-minted signature | Native Zoom Meeting SDK for Android/iOS, or hand off to the Zoom app. **Prototype in Phase 1** — highest uncertainty |
| **R-3** | **Authentication model change** | **HIGH** | Cookie sessions with a 15-minute idle timeout are unusable on mobile | `HttpSession`, no refresh, no tokens | Token layer in PHP; keep BCrypt cost 12 so existing passwords work; re-read instructor approval per request |
| **R-4** | **Production schema may not match the schema file** | **HIGH** | `DBSeeder` never adds four `tasmi_session` columns; `SchemaBootstrap` only runs on an empty database | No formal migration system | **`SHOW COLUMNS` diff against `etasmi_schema.sql` before writing any code** |
| **R-5** | **200 MB recitation uploads on mobile data** | **HIGH** | Users will abandon or exhaust data plans; failures mid-upload lose the recording | 200 MB limit, single-shot multipart | Client-side compression, chunked/resumable upload, progress UI, network-aware retry |
| **R-6** | **No AI cost controls** | **HIGH** | Mobile increases usage; unlimited `gpt-4o` evaluator calls are financially unbounded | No quota, cap, or budget | Per-user rate limits and a global spend cap **before** launch |
| **R-7** | **Unauthenticated media URLs** | **HIGH** | Payment receipts contain personal financial data and are publicly readable | `/uploads/*` is a public path | Authenticate or sign every media URL |
| **R-8** | **Java → PHP behavioral differences** | MEDIUM | Decimal handling, timezone handling, string comparison, and enum coercion differ subtly | `BigDecimal`, `ZoneId`, Java enums | Use PHP `decimal` strings for money, explicit `DateTimeZone`, backed enums; test with production-like data |
| **R-9** | **Unpooled database connections** | MEDIUM | Production opens a new connection per call; mobile increases concurrency | Railway path bypasses the JNDI pool | PDO with pooling or an external pooler |
| **R-10** | **In-memory state does not scale horizontally** | MEDIUM | Rate limits, OAuth tokens, and the AI translation cache are per-JVM; replicas multiply the effective limit | Single-instance assumption | Move to Redis or the database |
| **R-11** | **Ephemeral filesystem on Railway** | MEDIUM | Local-mode uploads are lost on every redeploy | Silent fallback when Cloudinary is unconfigured | Make Cloudinary mandatory; fail loudly if unconfigured |
| **R-12** | **AI results are not persisted** | MEDIUM | An expensive analysis is lost on session timeout; no quality history exists | HTTP session only | Persist results — **an additive schema change requiring client approval** |
| **R-13** | **No push notification infrastructure** | MEDIUM | Mobile users expect payment and evaluation alerts; none exists | In-app rows only, no read state | FCM/APNs plus an additive `is_read` column — both new features |
| **R-14** | **Notification text is English-only** | MEDIUM | Arabic and Malay users see English notifications | Pre-rendered at write time | Store a key + parameters and render per locale — a data-model change |
| **R-15** | **No CORS policy** | MEDIUM | The new API needs a deliberate policy | No `Access-Control-*` headers anywhere | Explicit allowlist in PHP |
| **R-16** | **Payment expectation mismatch** | MEDIUM | The brief assumed a gateway; there is none | Manual verification by instructors | **Client decision required** before any payment work |
| **R-17** | **Audio recording on mobile** | MEDIUM | Permissions, interruptions, background limits, and format differences vs `MediaRecorder` | Browser `MediaRecorder`, webm | Native recorder; confirm the accepted format list still holds |
| **R-18** | **Offline behavior is undefined** | MEDIUM | The web app assumes connectivity | No offline support | Define per screen: cached reads, queued writes, clear messaging |
| **R-19** | **Uploaded files are never deleted** | LOW–MEDIUM | Storage grows without bound; orphans accumulate | No `destroy` call exists | Delete on replace; audit existing orphans |
| **R-20** | **AI response latency** | LOW–MEDIUM | A voice turn chains three OpenAI calls; recitation analysis is slower still | Synchronous request/response | Streaming or async job + push; clear progress UI |
| **R-21** | **Deep linking for email flows** | LOW | Verification and reset links currently open a browser | Web URLs in emails | Universal Links / App Links with a web fallback |
| **R-22** | **`VARCHAR(255)` audio path** | LOW | Long Cloudinary URLs risk truncation | 255 chars | Widen — additive and safe, but still a schema change |
| **R-23** | **Manual JAR dependencies, no scanning** | LOW | No CVE visibility in the existing app | Ant + committed JARs | Composer with a lockfile in PHP; scan the Java app separately |
| **R-24** | **No observability** | LOW | No health check, metrics, or structured logging | stdout only | Add all three to the PHP backend |

### The five to address first

**R-4** (verify the real schema) → **R-2** (prototype Zoom on mobile) → **R-3** (design the token layer) → **R-6** and **R-7** (cost controls and media authentication, both fixable now on the existing system).

---

# PART 21 — OPTIONAL FUTURE SUGGESTIONS

**None of these are part of the migration.** Each is a behavior change, not a reproduction, and each requires explicit client approval. They are listed here to keep them clearly separated from the reproduction requirement.

### Would fix a genuine defect

| # | Suggestion | Rationale |
|---|---|---|
| O-1 | Fix the admin dashboard payment chips | They query legacy `SUCCESS`/`FAILED` statuses and always show 0 |
| O-2 | Align the admin create form to `minlength="8"` | It declares 6 while the server requires 8 |
| O-3 | Remove or authenticate `/auth/verify-status` | It contradicts the anti-enumeration design used everywhere else |
| O-4 | Delete Cloudinary assets on replace | Storage currently grows without bound |
| O-5 | Widen `recitation.audio_file_path` | 255 chars risks truncating long URLs |
| O-6 | Rehash legacy PBKDF2 passwords on login | Those users keep a weaker hash indefinitely |

### Would materially improve the product

| # | Suggestion | Requires |
|---|---|---|
| O-7 | **Persist AI recitation analysis** | New columns or table |
| O-8 | **Add `is_read` to notifications** | Additive column |
| O-9 | **Push notifications** | FCM/APNs + device token table |
| O-10 | **Localise notification text** | Store key + params instead of rendered English |
| O-11 | Add `created_at` to `enrollment` and `evaluation` | Enables real "recently enrolled" features |
| O-12 | Capture a rejection reason for instructors | Currently nothing is stored |
| O-13 | Email on payment approval/rejection and enrollment | Only in-app today |
| O-14 | Per-user AI quotas and a spend cap | Financial protection |
| O-15 | A dedicated Quranic Arabic font | Verse rendering quality |
| O-16 | Session reminders | Requires a scheduler, which does not exist |
| O-17 | Instructor payout/commission tracking | Not modelled at all |
| O-18 | Ratings and reviews | Not modelled |
| O-19 | Offline Quran Library caching | Natural mobile advantage |
| O-20 | Biometric unlock | Standard mobile expectation |
| O-21 | Resumable chunked uploads | Directly addresses R-5 |
| O-22 | Streaming AI chat responses | Perceived latency |

### Requires a product decision first

| # | Question |
|---|---|
| O-23 | Should a real payment gateway replace manual verification? (U-7) |
| O-24 | Should the UI-less capabilities be exposed, kept hidden, or removed? (U-4) |
| O-25 | Is Recitation Studio v2 abandoned or planned? (U-3) |
| O-26 | Should teaching material upload be built? (U-5) |
| O-27 | Will the Java web app be retired after mobile launch? (U-10) |

---

# APPENDIX A — CONFIGURATION VARIABLE NAMES

Names only. **No values.** Full purpose/requirement/exposure table in doc 11 §1.

**Database (9):** `MYSQLHOST` · `MYSQLPORT` · `MYSQLDATABASE` · `MYSQLUSER` · `MYSQLPASSWORD` · `MYSQL_URL` · `ETASMI_JDBC_URL` · `ETASMI_JDBC_USER` · `ETASMI_JDBC_PASSWORD`

**Application (5):** `APP_BASE_URL` · `APP_URL` · `APP_TIME_ZONE` · `ETASMI_UPLOADS_DIR` · `PORT`

**Zoom (12):** `ZOOM_ACCOUNT_ID` · `ZOOM_CLIENT_ID` · `ZOOM_CLIENT_SECRET` · `ZOOM_MEETING_SDK_KEY` · `ZOOM_MEETING_SDK_SECRET` · `ZOOM_MEETING_SDK_CLIENT_ID` · `ZOOM_MEETING_SDK_CLIENT_SECRET` · `ZOOM_DEFAULT_HOST_EMAIL` · `ZOOM_TIMEZONE` · `ZOOM_EMBED_ENABLED` · `ZOOM_WEB_SDK_VERSION` · `ETASMI_ZOOM_WEB_DEBUG`

**Cloudinary (3):** `CLOUDINARY_CLOUD_NAME` · `CLOUDINARY_API_KEY` · `CLOUDINARY_API_SECRET`

**OpenAI (9):** `OPENAI_API_KEY` · `OPENAI_ASSISTANT_MODEL` · `OPENAI_TRANSLATION_MODEL` · `OPENAI_VOICE_STT_MODEL` · `OPENAI_VOICE_TTS_MODEL` · `OPENAI_VOICE_NAME` · `OPENAI_RECITATION_MODEL` · `OPENAI_EVALUATOR_MODEL` · `OPENAI_CLASSIFIER_MODEL`

**Quran Foundation (7):** `QF_CLIENT_ID` · `QF_CLIENT_SECRET` · `QF_API_ENDPOINT` · `QF_ENV` · `QF_AUTH_ENDPOINT` · `QF_OAUTH_SCOPE` · `QF_SEARCH_API_BASE`

**Email (11):** `BREVO_API_KEY` · `BREVO_SENDER_NAME` · `RESEND_API_KEY` · `SMTP_HOST` · `SMTP_PORT` · `SMTP_USER` · `SMTP_USERNAME` · `SMTP_PASS` · `SMTP_PASSWORD` · `SMTP_FROM` · `SMTP_STARTTLS`

**Dev/override flags (6) — verify unset in production:** `ETASMI_DISABLE_RATE_LIMIT` · `ETASMI_ALLOW_LOGIN_WITHOUT_EMAIL_VERIFICATION` · `ETASMI_BOOTSTRAP_ADMIN_PASSWORD` · `DEBUG_ERRORS` · `SMTP_DEBUG` · `ETASMI_ZOOM_WEB_DEBUG`

**The 13 secrets:** `MYSQLPASSWORD` · `ETASMI_JDBC_PASSWORD` · `MYSQL_URL` · `ZOOM_CLIENT_SECRET` · `ZOOM_MEETING_SDK_SECRET` · `ZOOM_MEETING_SDK_CLIENT_SECRET` · `CLOUDINARY_API_KEY` · `CLOUDINARY_API_SECRET` · `OPENAI_API_KEY` · `QF_CLIENT_SECRET` · `BREVO_API_KEY` · `RESEND_API_KEY` · `SMTP_PASSWORD`

---

# APPENDIX B — UNKNOWN / REQUIRES CLARIFICATION

Ten items, each documented with evidence and a verification method in doc 12 §5.

| ID | Unknown | Blocking? |
|---|---|---|
| U-1 | Whether the production database matches `etasmi_schema.sql` | **Yes** |
| U-2 | Whether the dev-override flags are set in production | **Yes** |
| U-3 | Whether Recitation Studio v2 is abandoned or planned | No |
| U-4 | Whether the UI-less capabilities should be exposed | No |
| U-5 | Whether teaching material upload is planned | No |
| U-6 | Expected AI recitation accuracy | No |
| U-7 | Whether manual payment is intended long-term | **Yes** |
| U-8 | Instructor title enum vs the UI's honorific options | No |
| U-9 | Production data volume and concurrency | No |
| U-10 | Whether the Java web app will be retired | **Yes** |

**Four are blocking.** U-1 and U-2 are answerable by inspection; U-7 and U-10 require a client decision and shape the entire migration plan.

---

# APPENDIX C — FINAL AUDIT CHECKLIST

- [x] Entire source code · [x] Directory structure · [x] Database schema · [x] SQL queries
- [x] All user roles · [x] All pages · [x] All endpoints · [x] Authentication · [x] Authorization
- [x] Business rules · [x] AI functionality · [x] OpenAI integration · [x] Zoom integration
- [x] Cloudinary integration · [x] **ToyyibPay integration — confirmed NOT PRESENT**
- [x] Quran APIs · [x] Email · [x] Notifications · [x] File uploads
- [x] Configuration · [x] Environment variables · [x] Deployment configuration
- [x] Docker configuration · [x] Railway configuration · [x] Error handling · [x] Validation
- [x] Security · [x] Frontend behavior · [x] Backend behavior · [x] Database relationships
- [x] Production workflows · [x] Mobile migration requirements

**Constraints honoured:** no source code modified · no schema changed · no data created, altered, or deleted · no configuration touched · no secret value written into any document · no mobile project created · no PHP backend written.
