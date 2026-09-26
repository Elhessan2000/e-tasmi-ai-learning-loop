# e-Tasmi Current System Audit

**Audit date:** 24 September 2026  
**Scope:** Current repository only (`c:\Users\ASUS\Documents\GitHub\e-Tasmi`). Application source: `e-Tasmi/`.  
**Method:** Source, schema, configuration, routes, and wiring. Runtime (Docker up, live HTTP, production host) was **not** executed in this audit.  
**Rule:** Documentation and papers (`docs/`, `mobile-migration-documentation/`) were treated as claims, not as proof of implementation.

Status vocabulary used throughout:

| Status | Meaning |
|--------|---------|
| WORKING | Implemented and wired (UI → servlet → service → DB or external API). Runtime not re-tested here. |
| PARTIAL | Implemented but configuration-dependent, incomplete, or not persisted. |
| UNUSED | Code or tables exist but no servlet/UI calls them. |
| STATIC/MOCK | Marketing or static UI copy only. |
| PLANNED | Documented in specs/docs, not found in app source. |
| BROKEN | Code path exists but is internally inconsistent. |
| NOT VERIFIED | Cannot confirm without running the stack or accessing private env. |

---

## Executive Summary

e-Tasmi is a **Java Servlet/JSP** Tasmi’ (Qur’an recitation) web platform with three roles: **STUDENT**, **INSTRUCTOR**, and **ADMIN**. It is packaged as a **Tomcat 9 WAR** and developed with **Docker Compose + MySQL 8**. There is **no Maven `pom.xml`** and **no application semantic version** in config.

The live product path is: instructor creates a **tasmi session** → student enrols → optional **manual QR/receipt payment** → student **uploads/records recitation audio** (Cloudinary or local `/uploads`) → instructor **plays audio and writes a score (0–100) + feedback** → result is stored in `evaluation` and shown to the student. Optional **Zoom** live class and **Quran Foundation** library are env-gated.

**Existing AI (OpenAI only, no RAG/embeddings):**

1. Student **Quran Assistant** text chat (`gpt-4o-mini`).
2. Student **voice assistant** (STT → GPT → TTS).
3. Student **ayah English translation fallback** (`gpt-4o-mini`).
4. Instructor **on-demand recitation analysis** (STT + `gpt-4o` JSON report; local token-diff fallback). AI output is **not stored in MySQL**.

The strongest instructor extension surface is **`/instructor/evaluations`** (`InstructorEvaluationServlet` + `RecitationAiAnalysisService` + `evaluations.jsp`).

**Not verified:** whether Docker currently starts on this machine; whether a public production URL exists; whether OpenAI / Quran Foundation / Zoom keys in the local `.env` are valid.

---

## 1. Project Overview

| # | Item | Finding | Evidence |
|---|------|---------|----------|
| 1 | Exact name | **e-Tasmi** | `e-Tasmi/web/WEB-INF/web.xml` `<display-name>`; `e-Tasmi/nbproject/project.xml`; WAR `e-Tasmi.war` |
| 2 | Version | **Not found** (no 1.x/2.x in pom/web.xml/README) | No `pom.xml`; `web.xml` has no version |
| 3 | Type / style | Java EE **Servlet 3.1 / JSP** monolith: controller → service → JDBC DAO | `e-Tasmi/src/java/controller`, `model/service`, `model/dao` |
| 4 | Purpose | Instructor-led Tasmi’ session management, recitation submission, evaluation, plus student Qur’an library and assistive AI | Servlets and schema below |
| 5 | Intended users | Students, instructors, platform admin | `UserRole` enum |
| 6 | Roles | `STUDENT`, `INSTRUCTOR`, `ADMIN` | `e-Tasmi/src/java/model/entity/UserRole.java`; `user.role` ENUM |
| 7 | Main use cases | Register/login; create/browse sessions; enrol; pay by QR; join live class; submit recitation; evaluate; notify; Quran library/assistant | Route inventory §13 |
| 8 | Modules | Auth, sessions, enrollment, payment, recitation, evaluation, progress, notifications, Zoom, Quran library, student AI, instructor AI, admin users/reports | Feature table §5 |
| 9 | System status | Feature-complete FYP web app in repo; runtime **NOT VERIFIED** this session | — |
| 10 | Deployment status | Docker/Tomcat/MySQL documented; Railway env patterns in code | `Dockerfile`, `docker-compose.yml`, `util/Db.java` |
| 11 | Runnable | Designed to run via `docker compose up -d --build` | `README.md` |
| 12 | Production/live | **Not verified.** README advertises only `localhost:8080` / `:8443` | `README.md` |
| 13 | Local dev | Yes: Docker Desktop + VS Code workflow | `README.md` |
| 14 | URL/domain | Local: `http://localhost:8080/`. Demo emails `*.etasmi.local` / `admin@etasmi.com`. No public production hostname in README | `README.md`, `DBSeeder.java` |
| 15 | Repo structure | App in `e-Tasmi/`; Docker/Caddy at root; `docs/`, `db/`, `mobile-migration-documentation/`, `scripts/`, `tools/` | Repository listing |

**Architectural style:** Server-rendered JSP + annotation servlets + session cookie auth (`JSESSIONID`). JSON APIs exist for Quran library, assistant, Zoom signatures, and some instructor dashboard feeds.

---

## 2. Technology Stack

### A. Frontend

| Item | Actual | Evidence |
|------|--------|----------|
| Framework | JSP + JSP fragments (no React/Vue app) | `e-Tasmi/web/jsp/**/*.jsp` (38 pages) |
| Language | HTML, CSS, JavaScript | `e-Tasmi/web/css`, `e-Tasmi/web/assets/js` |
| CSS | Custom CSS; Bootstrap 5.3.8 on landing; Tailwind CDN on instructor evaluations | `home.jsp`, `learnhub-dist`, `evaluations.jsp` |
| JS libraries | Vanilla app JS; Chart.js 4.4.1 (admin payments); Lottie; Zoom Meeting SDK Web (CDN) | `admin/payments.jsp`, `js/lottie.min.js`, `live-embed.jsp` |
| i18n | `en` / `ar` / `ms` | `web/assets/locales/*.json`, `src/conf/messages_*.properties` |
| Build tools | **None for frontend** (no app `package.json`). Docs-only npm under `docs/` | — |

### B. Backend

| Item | Actual | Evidence |
|------|--------|----------|
| Language | Java 17 | `Dockerfile`; `nbproject/project.properties` `javac.source=17` |
| Framework | Servlet API `javax.servlet` 3.1, **not Spring**, **not Jakarta** | `web.xml`; servlet imports |
| Architecture | Servlet → Service → JDBC DAO | Package layout |
| API style | Form POST + JSP forward; selected JSON REST-ish endpoints | Student/instructor `api/*` servlets |
| Auth | Session attributes `userId`, `role`, `email`; BCrypt (preferred) + legacy PBKDF2 | `LoginServlet.java`, `PasswordUtil.java`, `SessionUtil.java` (15 min timeout) |
| Middleware | `CharacterEncodingFilter`, `UrlLocalizationFilter`, `AuthFilter` | `web.xml`, `AuthFilter.java` |
| Email | SMTP / Resend / Brevo via env | `docker-compose.yml`, `.env.example` |

### C. Database

| Item | Actual |
|------|--------|
| Engine | MySQL **8.4** in Compose (`mysql:8.4`) |
| Database name | `etasmi` (Compose `MYSQL_DATABASE`) |
| Tables (canonical) | **20** CREATE TABLE statements in `e-Tasmi/setup/railway_schema.sql` |
| Version | Schema has no version table. **Not verified** against a live instance. |
| Triggers | None besides `ON UPDATE CURRENT_TIMESTAMP` columns |
| Stored procedures | **Not found** in schema files |

### D. Infrastructure

| Item | Actual |
|------|--------|
| Docker | `Dockerfile` (multi-stage javac → `ROOT.war`), `docker-compose.yml` |
| Web server | Apache Tomcat 9 (`tomcat:9.0-jdk17-temurin`) |
| Reverse proxy / TLS | Optional Caddy (`Caddyfile`, port 8443) |
| Hosting hints | Railway-oriented JDBC env in `Db.java` / `context.xml` |
| Storage | Volume `etasmi-uploads`; optional Cloudinary |
| Cache | In-memory map in `QuranAiTranslationService` only |
| Queue | **Not found** |
| Monitoring | **Not found** (no APM). Admin `audit_log` is application audit, not infra monitoring |

### E. External APIs / services

| Name | Purpose | Where used | Config | Required for core app? | Status |
|------|---------|------------|--------|------------------------|--------|
| **OpenAI** | Chat, STT, TTS, translation fallback, recitation analysis | `QuranAssistantService`, `QuranVoiceService`, `QuranAiTranslationService`, `RecitationAiAnalysisService` | `OPENAI_API_KEY` and model env vars | No (core Tasmi works without it; AI features fail/hide) | PARTIAL |
| **Quran Foundation Content/Search API** | Chapters, verses, tafsir/translation resources, search | `QuranFoundationClient`, library servlets | `QF_CLIENT_ID`, `QF_CLIENT_SECRET`, `QF_API_ENDPOINT`, optional `QF_*` | No (library health check fails if incomplete) | PARTIAL |
| **api.qurancdn.com** | Chapter recitation audio / reciter portraits | `quran-library.js`, reciter portrait servlet | Hardcoded CDN paths in JS/servlet | No | PARTIAL |
| **Cloudinary** | Recitation + profile + receipt + banner uploads | `CloudinaryUtil`, `StudentRecitationServlet` | `CLOUDINARY_*` | No (falls back to local disk) | PARTIAL |
| **Zoom REST + Meeting SDK** | Create meetings, embed join | `ZoomApiClient`, `TasmiSessionService`, live-embed JSPs | `ZOOM_*` | No (manual `meeting_link` / physical mode) | PARTIAL |
| **SMTP / Resend / Brevo** | Email verification, password reset | Auth services | `SMTP_*`, `RESEND_API_KEY`, `BREVO_*` | No if `ETASMI_ALLOW_LOGIN_WITHOUT_EMAIL_VERIFICATION` | PARTIAL |
| **ToyyibPay / Stripe** | Card payment | **No Java integration** | — | — | UNUSED |

---

## 3. System Architecture

```
Browser (JSP + vanilla JS)
    ↓  HTTP + JSESSIONID
UrlLocalizationFilter → CharacterEncodingFilter → AuthFilter (role paths)
    ↓
@WebServlet controllers
    ↓
model.service.*
    ↓
model.dao.impl.*Jdbc  ──►  MySQL (JNDI jdbc/ETasmiDS or env JDBC)
    ↓
Optional HTTPS:
    OpenAI  |  Quran Foundation OAuth+API  |  Cloudinary  |  Zoom  |  SMTP
```

**Frontend → backend:** Most pages are servlet `forward` to `/jsp/...`. JSON `fetch` is used for Quran assistant, voice, library, Zoom signatures, instructor calendar/heartbeat.

**Auth flow:** `LoginServlet` validates password (`PasswordUtil`), invalidates old session, sets `userId`/`role`/`email`. `AuthFilter` rejects unauthenticated `/student|/*/instructor|/*/admin/*`. Instructors with `verification_status != APPROVED` are sent to `pendingApproval.jsp`.

**File/audio flow:** Student multipart upload → Cloudinary URL **or** `LocalFileUtil` under `ETASMI_UPLOADS_DIR` → `recitation.audio_file_path`. Instructor JSP plays URL or `/uploads/*` (`UploadedFileServlet`).

**AI flow:** Student chat/voice/translate: request → OpenAI → JSON/MP3 to browser; **not written to MySQL**. Instructor analyze: POST `action=analyze` → download media → STT → GPT JSON → request attribute + session attribute; official score still `EvaluationService.evaluate()`.

---

## 4. Users and Permissions

### STUDENT

| Item | Detail |
|------|--------|
| Login | Email + password, `/auth/login` |
| Registration | Self-register `/auth/register` (STUDENT or INSTRUCTOR only) |
| Dashboard | `/student/dashboard` → `jsp/student/dashboard.jsp` |
| Can | Browse/enrol sessions, pay (QR), submit recitation, view own evaluations, progress, notifications, profile, Quran library, assistant, join live |
| Cannot | Create sessions, evaluate others, admin users, instructor AI analyze |
| Evidence | `AuthFilter` path `/student`; `RegisterServlet`; student servlets |

### INSTRUCTOR

| Item | Detail |
|------|--------|
| Login | Same login servlet |
| Registration | Self-register as INSTRUCTOR → `verification_status=PENDING` until admin approves |
| Dashboard | `/instructor/dashboard` |
| Can | CRUD own sessions, upload materials, host/join live, list enrolled recitations, play audio, optional AI analyze, save score/feedback, verify student payments, profile/QR settings |
| Cannot | Admin user delete, approve themselves |
| Evidence | `InstructorVerificationServlet`; `AuthFilter` instructor gate |

### ADMIN

| Item | Detail |
|------|--------|
| Login | Same; redirect to `/admin/dashboard` |
| Registration | **Not** via public register. Seeded `admin@etasmi.com` (`DBSeeder`) or created in `/admin/users/create` |
| Can | Users CRUD, instructor approve/reject, payment **monitor**, reports, audit logs, admin profile |
| Cannot | Official recitation evaluation (no admin evaluation servlet) |
| Evidence | `AdminUserServlet`, `AdminPaymentsServlet` javadoc (read-only payments) |

No other roles exist in `UserRole` or `user.role` ENUM.

---

## 5. Complete Feature Inventory

| Feature | Module | Role | Status | Main files | Tables | Route | Current behavior | Notes |
|---------|--------|------|--------|------------|--------|-------|------------------|-------|
| Home / landing | Public | Public | WORKING | `HomeServlet`, `home.jsp` | — | `/home` | Marketing + login/register links | AI slogans on home are STATIC/MOCK copy |
| Login / logout | Auth | All | WORKING | `LoginServlet`, `LogoutServlet` | `user` | `/auth/login`, `/auth/logout` | Session auth | Rate limit bypassable in Docker via env |
| Register + email verify | Auth | Student/Instructor | PARTIAL | `RegisterServlet`, `VerifyEmailServlet` | `user`, `email_verification` | `/auth/register`, `/auth/verify-email` | Needs SMTP unless verification skipped | |
| Password reset | Auth | Public | PARTIAL | `ForgotPasswordServlet` | `password_reset` | `/auth/forgot-password` | Email-dependent | |
| Role dashboard router | Auth | All | WORKING | `DashboardRouterServlet` | — | `/dashboard` | Forwards by role | |
| Student dashboard | Student | Student | WORKING | `StudentDashboardServlet` | mixed queries | `/student/dashboard` | Overview | |
| Browse/enrol sessions | Sessions | Student | WORKING | `StudentSessionsServlet` | `tasmi_session`, `enrollment` | `/student/sessions` | Enrol creates enrollment | |
| My enrollments | Sessions | Student | WORKING | `StudentEnrollmentsServlet` | `enrollment` | `/student/enrollments` | List | |
| QR payment + receipt | Payment | Student | WORKING | `StudentQrPaymentServlet`, `PaymentService` | `payment` | `/student/payments/qr` | Manual transfer | Not a card gateway |
| Payment receipt view | Payment | Student | WORKING | `StudentPaymentReceiptServlet` | `payment` | `/student/payments/receipt` | | |
| Recitation submit | Recitation | Student | WORKING | `StudentRecitationServlet`, `RecitationService` | `recitation` | `/student/recitations` | Multipart up to 200 MB | Live path |
| Recitation playback (student) | Recitation | Student | WORKING | `StudentRecitationAudioServlet` | `recitation` | `/student/recitation-audio` | Owner check | |
| Recitation studio v2 | Recitation | Student | UNUSED | `RecitationSubmissionService` | `recitation_submissions` | **no servlet** | Tables exist | UI comment still points at enrollment servlet |
| Progress | Progress | Student | WORKING | `StudentProgressServlet`, `ProgressService` | `progress`, attendance | `/student/progress` | Completion rate | |
| Notifications | Notify | Student | WORKING | `StudentNotificationsServlet` | `notifications` | `/student/notifications` | | |
| Profile + photo | Profile | Student | WORKING | `StudentProfileServlet` | `user`, `student` | `/student/profile` | Cloudinary/local | |
| Quran library | Quran | Student | PARTIAL | Library servlets + `quran-library.js` | — | `/student/quran-library` | Needs QF credentials | |
| Quran assistant chat | AI | Student | PARTIAL | `QuranAssistantService` | none | `POST /student/api/quran-assistant/chat` | Needs OpenAI | Not persisted |
| Quran voice | AI | Student | PARTIAL | `QuranVoiceService` | none | `/student/api/quran-assistant/voice/*` | 25 MB audio | Not persisted |
| AI translation fallback | AI | Student | PARTIAL | `QuranAiTranslationService` | JVM cache | `POST .../translate-ai` | | |
| Join live class | Zoom | Student | PARTIAL | `StudentJoinLiveServlet` | `tasmi_session`, `attendance` | `/student/joinLive` | Auto-marks PRESENT | Needs Zoom or meeting_link |
| Instructor dashboard | Instructor | Instructor | WORKING | `InstructorDashboardServlet` | mixed | `/instructor/dashboard` | + calendar/heartbeat JSON | |
| Session CRUD | Sessions | Instructor | WORKING | `InstructorSessionsServlet`, `TasmiSessionService` | `tasmi_session` | `/instructor/sessions` | Zoom create if configured | |
| Session materials | Sessions | Instructor | WORKING | `InstructorMaterialServlet` | `session_material` | `/instructor/materials/open` | | |
| Live host embed | Zoom | Instructor | PARTIAL | `InstructorLiveSessionServlet` | `tasmi_session` | `/instructor/live-session` | | |
| Recitation evaluation | Evaluation | Instructor | WORKING | `InstructorEvaluationServlet`, `EvaluationService` | `recitation`, `evaluation` | `/instructor/evaluations` | Manual score 0–100 | Unique one evaluation per recitation |
| AI recitation analysis | AI | Instructor | PARTIAL | `RecitationAiAnalysisService` | none | POST `action=analyze` | Not persisted; GET does not reload session into JSP | Official grade remains human |
| Payment verification | Payment | Instructor | WORKING | `InstructorPaymentsServlet` | `payment`, `payment_verification_history` | `/instructor/payments` | Approve/reject | |
| Instructor profile / QR | Profile | Instructor | WORKING | `InstructorProfileServlet` | `instructor`, `instructor_payment_settings` | `/instructor/profile` | | |
| Admin users | Admin | Admin | WORKING | `AdminUserServlet` | `user` + role tables | `/admin/users*` | | |
| Instructor verification | Admin | Admin | WORKING | `InstructorVerificationServlet` | `instructor` | `/admin/instructors/*` | | |
| Admin payments monitor | Admin | Admin | WORKING | `AdminPaymentsServlet` | `payment` | `/admin/payments` | No verify action | |
| Reports | Admin | Admin | WORKING | `AdminReportsServlet` | mixed | `/admin/reports` | CSV/PDF (pdfbox) | |
| Audit logs | Admin | Admin | WORKING | `AdminAuditLogsServlet` | `audit_log` | `/admin/logs` | | |
| Local file serve | Infra | Public | WORKING | `UploadedFileServlet` | — | `/uploads/*` | **No AuthFilter** | Local files only |
| Attendance | Sessions | System | PARTIAL | `AttendanceService` | `attendance` | Called from join/embed | Auto PRESENT on join | No dedicated instructor mark-absent UI found |
| Card payments | Payment | — | UNUSED | — | — | — | No ToyyibPay/Stripe Java | |
| RAG / embeddings | AI | — | UNUSED / not found | — | — | — | — | |
| `/live/*` | Routing | — | UNUSED | `AuthFilter` only | — | No servlet | | |

---

## 6. Student Experience

| Step | UI | Backend | DB | API | Status |
|------|----|---------|----|-----|--------|
| 1. Registration | `register.jsp` | `RegisterServlet` → `RegistrationService` | `user`, `student` | Form POST | WORKING |
| 2. Login | `login.jsp` | `LoginServlet` | `user` | Form POST | WORKING |
| 3. Profile | `profile.jsp` | `StudentProfileServlet` | `user`, `student` | Form + photo | WORKING |
| 4–5. Access / enrol | `sessions.jsp`, `session_confirm.jsp` | `StudentSessionsServlet` | `enrollment` | Form | WORKING |
| 6. Payment | `payments.jsp`, `qr_payment.jsp` | `PaymentService` | `payment` | Form + upload | WORKING (manual) |
| 7. Quran selection | `quran_library.jsp` | Library servlets | none (upstream) | `/student/api/quran-library/*` | PARTIAL |
| 8–10. Recitation | `recitations.jsp` + studio JS | `StudentRecitationServlet` | `recitation` | Multipart POST | WORKING |
| 11–12. Result / feedback | Same recitations page | Loads `evaluation` | `evaluation` | GET | WORKING |
| 13. Progress | `progress.jsp` | `StudentProgressServlet` | `progress` | GET | WORKING |
| 14. Notifications | `notifications.jsp` | `StudentNotificationsServlet` | `notifications` | GET | WORKING |
| 15. Session / live | join + `live-embed.jsp` | `StudentJoinLiveServlet` | `attendance` | Zoom signature API | PARTIAL |
| 16. AI | Header widget `student_quran_assistant.jspf` | Assistant/voice/translate servlets | none | JSON | PARTIAL |
| 17. Other | Instructor photo | `StudentInstructorPhotoServlet` | — | GET | WORKING |

There is **no student “course” entity**. Access is **session-based** (`tasmi_session` + `enrollment`), not a LMS course catalog.

---

## 7. Instructor Experience

### Current workflow (confirmed)

```
Instructor registers → Admin approves
→ Instructor creates tasmi_session (portion, fee, mode, optional Zoom)
→ Student enrols (+ pays if fee > 0)
→ Student submits recitation audio
→ Instructor opens /instructor/evaluations
→ Instructor plays audio (HTML5 audio/video)
→ Optional: clicks “Analyze with eTasmi AI”
      → RecitationAiAnalysisService (STT + GPT or local fallback)
      → On-screen report (transcript, word diffs, suggested score)
      → Instructor does NOT have score fields auto-filled
→ Instructor types score + feedback
→ POST EvaluationService.evaluate()
→ Row in evaluation (unique per recitation)
→ Student sees result; progress updated
```

### Audit points

| # | Area | Status | Evidence |
|---|------|--------|----------|
| 1 | Register/login | WORKING | Auth servlets + pending approval |
| 2 | Dashboard | WORKING | `InstructorDashboardServlet` + calendar/heartbeat |
| 3 | Student management | PARTIAL | Via enrollments on sessions/evaluations/payments, not a standalone CRM |
| 4 | Assignment | WORKING | Enrollment to session |
| 5 | Session management | WORKING | `InstructorSessionsServlet` |
| 6–9 | Review / play / evaluate / feedback | WORKING | `evaluations.jsp`, `InstructorEvaluationServlet` |
| 10 | Tajweed evaluation | PARTIAL | AI prompt discusses Tajweed but **transcript-based**; no acoustic Tajweed engine |
| 11 | Progress | PARTIAL | `evaluation_reviewed_at` on session; `ProgressService` |
| 12 | Notifications | WORKING | Created by services on enrol/pay/eval |
| 13 | Reports | UNUSED for instructor | Admin has `/admin/reports` only |
| 14 | AI assist | PARTIAL | On-demand analyze; not persisted |
| 15 | Manual workflow | WORKING | Human score/feedback is official |
| 16 | Inefficient steps | Evidence | Must re-run AI after refresh; no DB history of AI reports; expected portion required as session `quran_portion` |

### Best current extension point (identification only)

**Page:** Instructor Evaluations — `e-Tasmi/web/jsp/instructor/evaluations.jsp`  
**Route:** `GET/POST /instructor/evaluations`  
**Service:** `RecitationAiAnalysisService.analyze()`  
**Why:** Already has media load, STT, structured GPT JSON, UI cards, and a hard separation from official `evaluation` rows. A 3-day challenge can extend this page without inventing a new role or upload pipeline.

---

## 8. Admin Experience

| Function | Route | Status |
|----------|-------|--------|
| Login | `/auth/login` → `/admin/dashboard` | WORKING |
| Dashboard | `/admin/dashboard` | WORKING |
| User management | `/admin/users*` | WORKING |
| Instructor verification | `/admin/instructors/*` | WORKING |
| Qualification download | `/admin/instructors/qualification/download` | WORKING |
| Payments | `/admin/payments` (monitor only) | WORKING |
| Reports | `/admin/reports` | WORKING |
| Audit logs | `/admin/logs` | WORKING |
| Profile | `/admin/profile` | WORKING |
| Content CMS | — | **Not found** (no surah CMS; Quran is API-backed) |
| AI controls | — | **Not found** (env vars only) |
| System settings UI | — | **Not found** |

---

## 9. Recitation and Audio System

**Live (wired) pipeline**

```
Student mic/file (webm/mp3/wav/m4a/ogg/aac/mp4/…)
    → StudentRecitationServlet.validateMediaPart / saveUpload
    → CloudinaryUtil.uploadVideo  OR  LocalFileUtil.saveFile("recitations")
    → RecitationService.submit → recitation(enrollment_id, audio_file_path, submission_date)
    → Instructor evaluations.jsp <audio>/<video>
    → Optional RecitationAiAnalysisService.read media (URL or /uploads/…)
         → OpenAI transcriptions (ar, temperature 0)
         → gpt-4o JSON evaluate (temperature 0, seed 1234567)
         → or buildFallbackResult() local Arabic token diff
    → Display only
    → Human EvaluationService.evaluate → evaluation(score, feedback)
    → ProgressDao.upsert
```

| Topic | Finding |
|-------|---------|
| Recording | Browser MediaRecorder in student recitation UI (`recitation-studio.js` exists; backend still `StudentRecitationServlet`) |
| Upload max | **200 MB** on live recitation servlet; AI analyze / voice turn **25 MB** |
| Formats | Listed in `StudentRecitationServlet` |
| Storage | Cloudinary video URL or `/uploads/recitations/...` |
| Transcription | Instructor AI path only (`gpt-4o-transcribe` default) |
| Alignment | Expected text = `tasmi_session.quran_portion` (free-text portion string, not ayah IDs) |
| Persistence of AI | **None** in schema |
| Unused studio | `recitation_submissions` + `RecitationSubmissionService` — no controller |

`UploadedFileServlet` (`/uploads/*`) is **not** behind `AuthFilter` — local files are world-reachable if the path is guessed (**security issue**, §16).

---

## 10. Existing AI Features

### 10.1 Student Quran Assistant (text)

| Field | Value |
|-------|--------|
| Purpose | Scoped Qur’an learning chat (modes: tafsir, tajweed, meaning, memorization, word, asbab, tips, general) |
| Provider / model | OpenAI `gpt-4o-mini` (`OPENAI_ASSISTANT_MODEL`) |
| Input | JSON message, mode, history, optional library context |
| Prompt | `QuranAssistantService` system prompts (Qur’an-only, Hafs framing, off-topic refusal) |
| Output | JSON `reply` |
| Display | Header widget `student_quran_assistant.jspf` + `quran-assistant.js` |
| Stored | **No** |
| Endpoint | `POST /student/api/quran-assistant/chat` |
| Functional | PARTIAL (needs `OPENAI_API_KEY`) |
| Limits | 2400 user chars, 12 history msgs, 1800 max_tokens, temperature 0.35 |
| Evidence | `QuranAssistantService.java`, `StudentQuranAssistantServlet.java` |

### 10.2 Student voice assistant

| Field | Value |
|-------|--------|
| Purpose | Spoken question → transcript → reply → MP3 |
| STT | `gpt-4o-transcribe`, temperature 0 |
| Chat | Same assistant, conversational, temperature 0.6, max_tokens 320 |
| TTS | `gpt-4o-mini-tts`, voice `alloy` |
| Endpoints | `POST .../voice/turn`, `POST .../voice/speak` |
| Stored | **No** |
| Limits | 25 MB upload |
| Evidence | `QuranVoiceService.java`, `StudentQuranVoiceServlet.java`, `quran-voice.js` |

### 10.3 AI translation fallback

| Field | Value |
|-------|--------|
| Purpose | English ayah text when Quran Foundation translation missing |
| Model | `gpt-4o-mini`, temperature 0, JSON object |
| Stored | In-memory `ConcurrentHashMap` only |
| Endpoint | `POST /student/api/quran-library/translate-ai` |
| Evidence | `QuranAiTranslationService.java` |

### 10.4 Instructor recitation AI analysis

| Field | Value |
|-------|--------|
| Purpose | Preliminary transcript + lexical comparison + suggested score |
| STT | `OPENAI_RECITATION_MODEL` default `gpt-4o-transcribe` |
| Evaluator | `gpt-4o` (`OPENAI_EVALUATOR_MODEL` / `OPENAI_CLASSIFIER_MODEL`) |
| Guardrail | Non-Qur’anic reject if `is_quran=false` and confidence ≥ 0.85 and no passages |
| Fallback | Local Arabic token diff if GPT fails |
| Display | `evaluations.jsp` analysis cards |
| Stored | HTTP session key + request attribute; **cleared after official evaluate**; **GET does not rehydrate** |
| Production-ready | PARTIAL — assistive only; no persistence; depends on OpenAI + readable media |
| Evidence | `RecitationAiAnalysisService.java`, `InstructorEvaluationServlet.handleAnalyze` |

### Not found in `e-Tasmi/src`

Embeddings, vector DB, RAG, Pinecone/Chroma, Whisper as default model name, admin AI, autonomous grading persisted as official score, acoustic Tajweed classifier.

---

## 11. Quran Content and Knowledge Sources

| Source | Type | Location | Purpose | Attribution in UI | Status |
|--------|------|----------|---------|-------------------|--------|
| Quran Foundation Content API | External HTTPS + OAuth | `QuranFoundationClient`, `QuranFoundationConfig` | Chapters, verses, tafsir/translation resource lists, search | **Not verified** as visible citation on every ayah | PARTIAL |
| api.qurancdn.com | External CDN | `quran-library.js`, reciter portrait servlet | Reciter audio / portraits | CDN | PARTIAL |
| OpenAI generated text | Model output | Assistant / translation / analysis | Learning replies, fallback translation, analysis | Not a mushaf source | PARTIAL |
| Session `quran_portion` | Free-text VARCHAR(150) | `tasmi_session` | Expected passage for AI compare | Instructor-entered | WORKING |
| Local mushaf database | — | **Not found** | — | — | — |
| Hadith corpus | — | **Not found** in app Java | Assistant may mention Hadith in prompts only | Unverified accuracy | — |
| RAG / embeddings | — | **Not found** | — | — | — |

License of Quran Foundation / CDN content: **human verification required** (API terms). No LICENSE file in repo root.

---

## 12. Database Audit

Canonical file: `e-Tasmi/setup/railway_schema.sql` (copied to `web/WEB-INF/db/railway_schema.sql` for `SchemaBootstrap`). Also `etasmi_schema.sql`, patches under `e-Tasmi/setup/`, seeder `DBSeeder.java`. Legacy `db/etasmi.sql` is an older subset.

| Table | Purpose | Key fields |
|-------|---------|------------|
| `user` | Accounts | email, password_hash, role, status, email_verified |
| `student` | Profile | registration_number, level |
| `instructor` | Profile + verification + Zoom email + payment hints | verification_status, qualification_file |
| `instructor_payment_settings` | Bank/QR | qr_image_url, account_number |
| `admin` | Admin profile | user_id |
| `tasmi_session` | Class | portion, mode, fee, Zoom fields, evaluation_reviewed_at |
| `enrollment` | Student↔session | enrollment_status |
| `payment` | Fees | payment_status, receipt_file_path, verified_by_instructor_id |
| `payment_verification_history` | Payment audit | actor_role, action |
| `notifications` | In-app | user_id, message |
| `progress` | Completion | student_id, completion_rate |
| `email_verification` | OTP | code_hash |
| `password_reset` | Reset | token_hash |
| `recitation` | **Live audio** | enrollment_id, audio_file_path |
| `evaluation` | Official score | recitation_id UNIQUE, score 0–100, feedback |
| `recitation_language` | Studio languages | seeded |
| `recitation_submissions` | Unused studio | user_id, file_path |
| `attendance` | Live join auto-mark | PRESENT/ABSENT |
| `session_material` | Files | file_path |
| `audit_log` | Admin actions | actor, action, entity |

**AI-related tables:** none.  
**FKs / indexes / CHECKs:** defined in the same SQL (e.g. `chk_evaluation_score`).  
**Seeders:** `DBSeeder` default admin; `sample_data.sql`, `seed_qademo_student.sql` (manual).

---

## 13. API and Route Inventory

Important endpoints (all servlet-mapped). Auth = session unless PUBLIC.

| Method | Path | Purpose | Controller | Role | Status |
|--------|------|---------|------------|------|--------|
| GET | `/home` | Landing | `HomeServlet` | PUBLIC | WORKING |
| GET/POST | `/auth/login` | Login | `LoginServlet` | PUBLIC | WORKING |
| GET/POST | `/auth/register` | Register | `RegisterServlet` | PUBLIC | WORKING |
| GET | `/dashboard` | Role router | `DashboardRouterServlet` | AUTH | WORKING |
| GET/POST | `/student/sessions` | Browse/enrol | `StudentSessionsServlet` | STUDENT | WORKING |
| GET/POST | `/student/recitations` | Submit/list | `StudentRecitationServlet` | STUDENT | WORKING |
| GET | `/student/recitation-audio` | Play own audio | `StudentRecitationAudioServlet` | STUDENT | WORKING |
| GET/POST | `/student/payments*` | Pay | payment servlets | STUDENT | WORKING |
| GET | `/student/quran-library` | Library page | `StudentQuranLibraryServlet` | STUDENT | PARTIAL |
| GET | `/student/api/quran-library/*` | Proxy QF/CDN | multiple | STUDENT | PARTIAL |
| POST | `/student/api/quran-assistant/chat` | Chat | `StudentQuranAssistantServlet` | STUDENT | PARTIAL |
| POST | `/student/api/quran-assistant/voice/turn` | Voice | `StudentQuranVoiceServlet` | STUDENT | PARTIAL |
| GET/POST | `/instructor/sessions` | Sessions | `InstructorSessionsServlet` | INSTRUCTOR | WORKING |
| GET/POST | `/instructor/evaluations` | Evaluate + analyze | `InstructorEvaluationServlet` | INSTRUCTOR | WORKING / PARTIAL AI |
| GET/POST | `/instructor/payments` | Verify pay | `InstructorPaymentsServlet` | INSTRUCTOR | WORKING |
| GET/POST | `/admin/users*` | Users | `AdminUserServlet` | ADMIN | WORKING |
| GET | `/uploads/*` | Local files | `UploadedFileServlet` | PUBLIC | WORKING (open) |

Full ~75 patterns: see `@WebServlet` classes under `e-Tasmi/src/java/controller/`.

---

## 14. Frontend/UI Audit

| Screen | Role | Route | Purpose | Status |
|--------|------|-------|---------|--------|
| Landing | Public | `/home` | Marketing | WORKING |
| Login / register / verify / reset | Public | `/auth/*` | Auth | WORKING |
| Pending approval | Instructor | forwarded | Wait for admin | WORKING |
| Student dashboard | Student | `/student/dashboard` | Home | WORKING |
| Sessions / confirm / enrollments | Student | `/student/sessions` etc. | Enrol | WORKING |
| Payments / QR / receipt | Student | `/student/payments*` | Pay | WORKING |
| Recitations | Student | `/student/recitations` | Upload + results | WORKING |
| Progress / notifications / profile | Student | matching routes | | WORKING |
| Quran library | Student | `/student/quran-library` | Read/listen | PARTIAL |
| Live embed | Student/Instructor | `.../live/embed` | Zoom SDK | PARTIAL |
| Instructor dashboard | Instructor | `/instructor/dashboard` | Overview | WORKING |
| Instructor sessions | Instructor | `/instructor/sessions` | CRUD | WORKING |
| **Evaluations** | Instructor | `/instructor/evaluations` | Play + AI + grade | WORKING |
| Instructor payments / profile | Instructor | matching | | WORKING |
| Admin dashboard / users / verification / payments / reports / logs / profile | Admin | `/admin/*` | Ops | WORKING |

Screenshots: **Not verified** as a maintained screenshot folder in-repo.

---

## 15. Current System Workflows

**A. Registration/login**

```
register.jsp → RegisterServlet → user + student/instructor row
→ email verify (if SMTP) → login.jsp → LoginServlet → /dashboard
```

**B. Student recitation**

```
enrol (+ pay if fee) → recitations.jsp record/upload
→ StudentRecitationServlet → recitation row → instructor queue
```

**C. Instructor evaluation**

```
evaluations.jsp → play audio → optional analyze → type score/feedback
→ EvaluationService → evaluation row → student recitations page
```

**D. Feedback**

```
evaluation.feedback TEXT → shown to student on recitations UI
```

**E. AI**

```
Student widget → OpenAI → browser only
Instructor Analyze → OpenAI → JSP request scope
```

**F. Session**

```
InstructorSessionsServlet create → optional ZoomApiClient
→ student enrol → joinLive → AttendanceService PRESENT
```

**G. Payment**

```
fee>0 → QR + receipt upload → AWAITING_VERIFICATION
→ instructor approve → enrollment APPROVED
```

---

## 16. Current Problems and Limitations

| Problem | Class | Evidence | Impact | Role | Severity |
|---------|-------|----------|--------|------|----------|
| Instructor AI report not in DB; GET does not reload session into JSP | Functional / UX | `InstructorEvaluationServlet` sets session attr; GET builds groups without reading it | Refresh loses analysis | Instructor | Medium |
| Official evaluation has no AI fields | Missing functionality | `evaluation` table: score + feedback only | Cannot audit AI vs human | Instructor | Medium |
| `/uploads/*` unauthenticated | Security | `UploadedFileServlet` + `AuthFilter` patterns omit it | Local audio/receipts guessable | All | High if local storage used |
| Recitation studio tables unused | Maintainability | `RecitationSubmissionService` unused | Dual schema confusion | Dev | Low |
| Quran library / AI / Zoom env-gated | Reliability | Config `isComplete()` / `isConfigured()` | Features disappear without keys | Student/Instructor | Medium |
| `quran_portion` is VARCHAR(150) free text | Data quality | `tasmi_session.quran_portion` | Weak verse identity for AI compare | Instructor | Medium |
| Transcript-only Tajweed | AI limitation | `RecitationAiAnalysisService` prompts | Cannot claim acoustic Tajweed | Instructor | High if oversold |
| Default MySQL passwords in Compose | Security/config | `docker-compose.yml` | Unsafe if port 3306 exposed | Ops | Medium (local default) |
| No root LICENSE | Legal process | No `LICENSE` file | Public GitHub needs human review | Owner | Medium |
| `.env` present locally | Secret hygiene | gitignored `.env` | Fine if never committed | Dev | — |
| `nbproject/private` tracked despite gitignore | Config leak risk | `git ls-files` | Machine paths in repo | Dev | Low |
| Runtime / production URL | — | Not executed | Runnable/live **NOT VERIFIED** | — | — |
| Attendance ABSENT UI | Missing | Only auto PRESENT on join | Incomplete attendance | Instructor | Low |

---

## 17. Potential AI Extension Points

Analysis only. No design chosen.

| # | Existing page/module | Current workflow | Current limitation | Possible AI role | Input | Output | Reuse | Complexity | Dependencies | Risks |
|---|----------------------|------------------|--------------------|------------------|-------|--------|-------|------------|--------------|-------|
| 1 | Instructor evaluations | Manual grade after optional analyze | AI ephemeral; no persist; no auto-fill | Persist/compare AI vs official; structured Tajweed checklist from existing JSON | Audio + `quran_portion` | Stored report + UI | `RecitationAiAnalysisService`, `evaluations.jsp` | Low–medium | OpenAI, media fetch | Over-claiming accuracy |
| 2 | Student recitations | Upload then wait for instructor | No pre-check | Student-facing preliminary transcript/diff (same service, student-scoped) | Same audio | Preview only | Same AI service | Medium | OpenAI, auth | Students treating it as a grade |
| 3 | Quran assistant | Uncited chat | No retrieval | Cite ayahs already loaded from QF context | Chat + verse context | Cited replies | `QuranAssistantService`, library context | Medium | QF + OpenAI | Hallucinated citations |
| 4 | Session `quran_portion` | Free text | Poor alignment | Map portion string → verse keys via QF search | Portion text | Structured expected ayahs | `QuranLibrarySearchServlet` | Medium | QF | Wrong passage mapping |
| 5 | Voice assistant | Ephemeral | No history/safety log | Persist turns / add refusal metrics | Audio/text | DB log | Voice servlets | Medium | New tables | Privacy of voice |

---

## 18. 3-Day Technical Feasibility

Reusable: Tomcat session auth, `recitation` + `evaluation` tables, Cloudinary/local media, `RecitationAiAnalysisService`, evaluations JSP, OpenAI HTTP helpers, Quran Foundation proxies.

**Illustrative small end-to-end (feasibility only):** persist instructor AI analysis next to a recitation and show last report on GET.

| Day | Work |
|-----|------|
| Day 1 | Schema (`ai_analysis` or JSON column) + DAO + save in `handleAnalyze` |
| Day 2 | Load on GET; JSP last-report panel; keep human score authoritative |
| Day 3 | Empty/fail states, instructor-only access check, simple “re-run” |

**Would need creating:** table + DAO + GET wiring (JSP already renders `analysisByRecitationId`).  
**Highest-risk dependency:** OpenAI availability + ability to fetch Cloudinary/local bytes in the server environment.

---

## 19. Existing Project Baseline

### PRE-EXISTING / BASELINE (in source today)

- Role-based Tasmi’ web app (student / instructor / admin)
- Session, enrollment, manual QR payment, notifications, progress
- Recitation upload/playback and **human** score/feedback
- Optional Zoom live class + auto attendance PRESENT
- Quran Foundation–backed library (when configured)
- OpenAI student assistant (text + voice) and translation fallback
- OpenAI instructor **preliminary** recitation analysis (not official grade)
- Docker/Tomcat/MySQL packaging
- Admin users, instructor verification, reports, audit log

### NOT baseline (possible later challenge work)

- Any **new** AI capability not listed above
- Persisted AI evaluation history
- Acoustic Tajweed scoring
- RAG / embeddings
- Card payment gateways
- Mobile app (`docs/` specs only)

---

## 20. Ownership / License / Third-Party Audit

| Evidence | Finding |
|----------|---------|
| Root `LICENSE` | **Not found** |
| University / FYP ownership in app source | **Not found** as a license header. FYP/UMT language appears in `docs/MJCSC` manuscripts, not as app LICENSE |
| Third-party JARs | Tracked under `e-Tasmi/web/WEB-INF/lib/` (mysql-connector-j, jbcrypt, pdfbox, jsoup, jstl, mail, servlet-api, etc.) — each has its own license; **human review needed** |
| npm | Only under `docs/` and `docs/latex/docx-build/` — not the runtime app |
| Quran Foundation / QuranCDN / OpenAI / Zoom / Cloudinary | Used via APIs; **terms not stored in repo** — human verification before public release |
| Copyright notices in Java | Sparse / not systematically applied (**Not verified** as complete) |

No legal conclusion.

---

## 21. Security / Secrets Audit

**Do not treat this as a pentest.** Values are not printed.

| Item | Result | Path (no values) |
|------|--------|------------------|
| Local `.env` | **FOUND** (gitignored) | `.env` at repo root |
| `.env.example` | **FOUND** (tracked template; keys only) | `.env.example` |
| Compose default DB user/password literals | **FOUND** | `docker-compose.yml` (`db.environment`) |
| OpenAI / Zoom / QF / Cloudinary keys in source | **NOT FOUND** as hardcoded production keys in Java (read from `System.getenv`) | `QuranFoundationConfig`, `CloudinaryUtil`, OpenAI services |
| `nbproject/private` | **FOUND** tracked IDE paths | `e-Tasmi/nbproject/private/private.properties` |
| Service account JSON | **NOT FOUND** | — |
| Whether `.env` was ever committed | **NOT VERIFIED** (history not fully walked) | use `git log -- .env` before public push |

---

## 22. Deployment / Live Demo Readiness

| Topic | Finding |
|-------|---------|
| Current hosting | Documented local Docker; Railway JDBC **patterns** in code. Live host **NOT VERIFIED** |
| Domain | localhost only in README |
| Build | `docker compose up -d --build` or Ant/NetBeans |
| Database | MySQL 8, schema via `etasmi_schema.sql` / `SchemaBootstrap` |
| Storage | Disk volume and/or Cloudinary |
| External APIs for a **full** demo | OpenAI + QF + (optional) Zoom + SMTP + Cloudinary |
| Minimal Tasmi demo | App + MySQL may run **without** OpenAI/QF/Zoom (AI/library/embed degraded) — **NOT VERIFIED** by running |
| Env | `.env` from `.env.example` |
| Auth | Seeded admin; email verify may be skipped via env |
| Blockers | Missing JARs in a clean tree would break Ant builds (Docker compiles from source); secrets; `/uploads` exposure; no LICENSE |
| Reproducible from repo | **Likely yes** with Docker + `.env` — **NOT VERIFIED** this session |

---

## 23. Evidence and Code References

| Conclusion | Reference |
|------------|-----------|
| App name / servlet 3.1 | `e-Tasmi/web/WEB-INF/web.xml` |
| Roles | `e-Tasmi/src/java/model/entity/UserRole.java`; `AuthFilter.java` |
| Schema tables | `e-Tasmi/setup/railway_schema.sql` `CREATE TABLE` |
| Recitation insert | `RecitationService` / `RecitationDaoJdbc` / `StudentRecitationServlet` |
| Official grade | `EvaluationService.evaluate()` / `evaluation` table |
| Instructor AI | `InstructorEvaluationServlet.handleAnalyze` ; `RecitationAiAnalysisService.analyze` |
| Student chat | `QuranAssistantService.chat` ; `StudentQuranAssistantServlet` |
| Voice | `QuranVoiceService.transcribe` / `synthesize` |
| QF config | `QuranFoundationConfig.load` |
| Unused studio | `RecitationSubmissionService` (no controller references) |
| Docker | `Dockerfile`, `docker-compose.yml` |
| Local URLs | `README.md` |

---

## 24. Final Summary

1. **What e-Tasmi is today:** A session-based Tasmi’ web app (Java/JSP/Tomcat/MySQL) for students, instructors, and admins, with optional Zoom, Quran library, and OpenAI assistance.
2. **Main features:** Auth, sessions, enrolment, QR payment, recitation upload, human evaluation, progress, notifications, admin verification/reports.
3. **Existing AI:** Student chat + voice + translation fallback; instructor on-demand recitation analysis. No RAG. No official autonomous grading.
4. **Strongest instructor workflow:** `/instructor/evaluations` — play audio, optional AI report, manual score/feedback.
5. **Audio pipeline:** Multipart upload → Cloudinary or `/uploads` → `recitation` → HTML5 play → optional OpenAI STT/GPT → human `evaluation`.
6. **Quran sources:** Quran Foundation API + QuranCDN; session portion free text; no local mushaf DB.
7. **Limitations:** Env-gated integrations; AI not persisted; `/uploads` public; unused studio schema; no root LICENSE; runtime/production **NOT VERIFIED**.
8. **Reusable for a new AI feature:** Auth, recitation media, `RecitationAiAnalysisService`, evaluations UI, QF verse APIs, OpenAI HTTP layer.
9. **Realistic 3-day extensions:** Persist instructor AI reports; student pre-check; cited assistant answers; structured portion→ayah mapping; voice-turn logging.
10. **Most feasible technically:** Persist and re-display the **existing** instructor analysis JSON (smallest new surface). Not a competition-strategy recommendation.
11. **Still missing / unverified:** Live Docker health, production URL, validity of local API keys, git history of `.env`, Quran/API licenses, whether JARs exist on disk in every checkout, screenshot set.

---

### Final checklist

| Question | Answered? |
|----------|-----------|
| What exists today? | Yes — §1, §5, §19 |
| What AI already exists? | Yes — §10 |
| What does the instructor do? | Yes — §7 |
| What does the student do? | Yes — §6 |
| Recitation/audio pipeline? | Yes — §9 |
| Quran/content sources? | Yes — §11 |
| What can be reused? | Yes — §17–18 |
| 3-day extension points? | Yes — §17–18 |
| Baseline vs new? | Yes — §19 |
| What remains unverified? | Yes — runtime, production, licenses, key validity |
