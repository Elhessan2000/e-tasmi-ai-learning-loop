# 10 — MOBILE MIGRATION ARCHITECTURE

**Scope note:** this document is architectural planning only. No code is written in Phase 0, and nothing here changes the existing system.

---

## 1. TARGET ARCHITECTURE

```
┌───────────────────────────────────────────────────────────────┐
│                      FLUTTER MOBILE APP                       │
│                                                               │
│   Presentation   screens · widgets · role-based navigation    │
│   State          Riverpod / Bloc                              │
│   Domain         entities · use cases · validators            │
│   Data           repositories · Dio client · secure storage   │
│                                                               │
│   Holds NO third-party secret. Ever.                          │
└──────────────────────────┬────────────────────────────────────┘
                           │  HTTPS · Bearer token · JSON
                           ▼
┌───────────────────────────────────────────────────────────────┐
│                        PHP REST API                           │
│                                                               │
│   Middleware     auth · role guard · rate limit · CORS · log  │
│   Controllers    thin — parse, delegate, format               │
│   Services       ALL business rules (ported 1:1 from Java)    │
│   Repositories   PDO prepared statements                      │
│   Integrations   Zoom · Cloudinary · OpenAI · Quran · Email   │
│                                                               │
│   Holds ALL secrets.                                          │
└──────────────┬────────────────────────────┬───────────────────┘
               │                            │
               ▼                            ▼
      ┌─────────────────┐        ┌──────────────────────────┐
      │    MySQL 8      │        │   External services      │
      │  SAME SCHEMA    │        │   Zoom · Cloudinary      │
      │  20 tables      │        │   OpenAI · Quran Fdn     │
      │  unchanged      │        │   Brevo/Resend/SMTP      │
      └─────────────────┘        └──────────────────────────┘

Direct client → external connections (no secret involved):
   • Zoom Meeting SDK for Android/iOS  — signature minted by our API
   • Quran chapter audio               — public CDN URLs
   • Cloudinary media reads            — should be signed & expiring
```

### Non-negotiable invariants

1. **The database schema does not change.** Same 20 tables, same columns, same meanings, same constraints. The web application and the mobile backend read the same data.
2. **No secret ships in the Flutter binary.** A released app is decompilable; an embedded key is a published key.
3. **Business rules live in the service layer**, exactly as they do in Java today. Controllers stay thin, and the client is never trusted to enforce a rule.
4. **Every rule in doc 05 is reproduced verbatim** unless the client explicitly approves a change.

---

## 2. COEXISTENCE STRATEGY

The existing Java web application must keep running. Two systems will share one database.

```
   Browser  ──▶  Java/Tomcat (existing, unchanged)  ──┐
                                                      ├──▶  MySQL
   Flutter  ──▶  PHP REST API (new)                ──┘
```

**Consequences to plan for:**

| Concern | Handling |
|---|---|
| **Two auth models** | Java keeps `HttpSession`; PHP uses tokens. They do not share state — a user logged in on web is not logged in on mobile. This is acceptable and is the simplest correct answer |
| **Schema changes** | Must be **additive only** while both run. A new column with a default is safe; a rename or type change breaks the Java app |
| **Password hashes** | Shared. PHP `password_hash(..., PASSWORD_BCRYPT, ['cost'=>12])` produces `$2y$` hashes that `password_verify` accepts against the existing `$2a$` hashes. **No password reset campaign is needed** |
| **Concurrent writes** | Both systems can write the same rows. Preserve the existing transaction boundaries; consider optimistic locking on payment verification |
| **File storage** | Both must use the same Cloudinary account and folder structure, and both must tolerate the dual URL format |
| **Business-rule drift** | The greatest long-term risk. Any rule change must be applied to both, or the systems diverge. Freezing feature work on the Java app during migration is strongly advisable |

---

## 3. JAVA → PHP COMPONENT MAPPING

### 3.1 Layer mapping

| Java | PHP | Notes |
|---|---|---|
| `@WebServlet` class | Controller class + route | One controller per resource; actions become methods |
| `doGet` / `doPost` | Route handlers (`index`, `store`, `update`, …) | The `action` parameter pattern becomes distinct routes |
| `HttpServletRequest.getParameter` | Validated request object | Centralise validation |
| `RequestDispatcher.forward` → JSP | `JsonResponse` | No server-side rendering |
| `response.sendRedirect(?notice=x)` | `{ "ok": true, "message": "…" }` | **Flash-in-query-parameter must become flash-in-body** |
| Service class | Service class | **Port logic 1:1** |
| `*DaoJdbc` | Repository with PDO | Prepared statements throughout |
| Model / entity | Model class or DTO | Same field names |
| Java `enum` | PHP 8.1 backed `enum` | Same string values as the DB enums |
| `AuthFilter` | Auth + role middleware | Preserve the prefix→role mapping |
| `CharacterEncodingFilter` | UTF-8 default | `utf8mb4` end to end |
| `UrlLocalizationFilter` | **Drop** | Locale becomes a header or user preference |
| `@WebListener` `SchemaBootstrap` | Migration tool (Phinx/Doctrine) | Replace ad-hoc bootstrap with real migrations |
| `DBSeeder` | Seeder command | **Remove the boot-time admin password reset** |
| `util/Db` | PDO factory with **connection pooling** | Fixes the current unpooled production path |

### 3.2 Servlet → endpoint mapping

| Java servlet | PHP route | Method |
|---|---|---|
| `LoginServlet` | `/api/v1/auth/login` | POST |
| `RegisterServlet` | `/api/v1/auth/register` | POST |
| `VerifyEmailServlet` | `/api/v1/auth/verify-email` | POST |
| `ResendVerificationServlet` | `/api/v1/auth/resend-verification` | POST |
| `ForgotPasswordServlet` | `/api/v1/auth/forgot-password` | POST |
| `ResetPasswordServlet` | `/api/v1/auth/reset-password` | POST |
| `LogoutServlet` | `/api/v1/auth/logout` | POST (**not GET**) |
| — *(new)* | `/api/v1/auth/refresh` | POST |
| `StudentDashboardServlet` | `/api/v1/student/dashboard` | GET |
| `StudentSessionsServlet` | `/api/v1/student/sessions` · `/enrollments` | GET · POST |
| `StudentEnrollmentsServlet` | `/api/v1/student/enrollments` · `/{id}/withdraw` | GET · POST |
| `StudentPaymentsServlet` | `/api/v1/student/payments` | GET |
| `StudentQrPaymentServlet` | `/api/v1/student/payments/{id}/qr` · `/receipt` | GET · POST |
| `StudentPaymentReceiptServlet` | `/api/v1/student/payments/{id}/receipt-pdf` | GET |
| `StudentRecitationServlet` | `/api/v1/student/recitations` | GET · POST |
| `StudentRecitationAudioServlet` | `/api/v1/student/recitations/{id}/audio` | GET (signed) |
| `StudentProgressServlet` | `/api/v1/student/progress` | GET |
| `StudentNotificationsServlet` | `/api/v1/notifications` | GET |
| `StudentProfileServlet` | `/api/v1/student/profile` · `/password` · `/photo` | GET · PUT · POST |
| `StudentJoinLiveServlet` | `/api/v1/student/sessions/{id}/join` | POST |
| `StudentZoomMeetingSignatureServlet` | `/api/v1/student/sessions/{id}/zoom-signature` | GET |
| `StudentQuranAssistantServlet` | `/api/v1/quran/assistant/chat` | POST |
| `StudentQuranVoiceServlet` | `/api/v1/quran/assistant/voice/turn` · `/speak` | POST |
| 11 × `StudentQuranLibrary*Servlet` | `/api/v1/quran/library/*` | GET |
| `InstructorDashboardServlet` | `/api/v1/instructor/dashboard` | GET |
| `InstructorDashboardCalendarFeedServlet` | `/api/v1/instructor/calendar` | GET |
| `InstructorDashboardHeartbeatServlet` | `/api/v1/instructor/live-status` | GET |
| `InstructorSessionsServlet` | `/api/v1/instructor/sessions[/{id}][/start\|/complete]` | GET · POST · PUT · DELETE |
| `InstructorEvaluationServlet` | `/api/v1/instructor/recitations` · `/{id}/analyze` · `/{id}/evaluate` | GET · POST |
| `InstructorPaymentsServlet` | `/api/v1/instructor/payments[/{id}/approve\|/reject]` · `/payment-settings` | GET · POST · PUT |
| `InstructorProfileServlet` | `/api/v1/instructor/profile` · `/photo` | GET · PUT · POST |
| `InstructorLiveSessionServlet` | `/api/v1/instructor/sessions/{id}/host` | POST |
| `InstructorZoomMeetingSignatureServlet` | `/api/v1/instructor/sessions/{id}/zoom-signature` | GET |
| `InstructorMaterialServlet` | `/api/v1/instructor/sessions/{id}/materials` | GET |
| `AdminDashboardServlet` | `/api/v1/admin/dashboard` | GET |
| `AdminUserServlet` | `/api/v1/admin/users[/{id}][/toggle]` | GET · POST · PUT · DELETE |
| `InstructorVerificationServlet` | `/api/v1/admin/instructors[/{id}/approve\|/reject]` | GET · POST |
| `InstructorQualificationDownloadServlet` | `/api/v1/admin/instructors/{id}/qualification` | GET (signed) |
| `AdminPaymentsServlet` | `/api/v1/admin/payments[/{id}]` · `/export` | GET |
| `AdminReportsServlet` | `/api/v1/admin/reports` · `/export` | GET |
| `AdminAuditLogsServlet` | `/api/v1/admin/audit-logs` | GET |
| `AdminProfileServlet` | `/api/v1/admin/profile` · `/password` | GET · PUT |
| `UploadedFileServlet` | `/api/v1/files/{token}` | GET (**authenticated / signed**) |
| `DashboardRouterServlet` | *(dropped)* | The client routes by role |
| `HomeServlet` | `/api/v1/public/stats` | GET |
| `RoleSelectionServlet` | *(dropped)* | Client-side screen |
| `VerifyStatusServlet` | *(dropped)* | Closes an enumeration hole |

### 3.3 Service mapping

Every Java service ports to a PHP service of the same name with the same behavior: `AuthService`, `UserService`, `RegistrationService`, `EmailVerificationService`, `PasswordResetService`, `SessionService`, `EnrollmentService`, `PaymentService`, `PaymentVerificationService`, `RecitationService`, `EvaluationService`, `ProgressService`, `NotificationService`, `AttendanceService`, `LiveSessionAccessService`, `InstructorVerificationService`, `AuditLogService`, `ReportService`, `FileStorageService`, `ZoomMeetingService`, `ZoomSignatureService`, `OpenAiService`, `RecitationAnalysisService`, `QuranAssistantService`, `QuranFoundationClient`, `EmailService`, `RateLimitService`.

**Rule: if a Java service enforces a check, the PHP service enforces the same check in the same place.** Do not relocate a rule to a controller or to the client.

---

## 4. AUTHENTICATION REDESIGN

Cookie sessions with a 15-minute idle timeout cannot work for mobile.

```
   POST /api/v1/auth/login  { email, password }
        │
        ├─ SAME preconditions as Java:
        │     status <> 'DELETED' · is_active = 1 · email_verified = 1
        │     password_verify against the EXISTING BCrypt hash
        │
        ├─ issue access token   (JWT, 15–60 min, claims: sub, role, iat, exp)
        ├─ issue refresh token  (opaque random, 30 days, HASHED in the DB)
        │
        ▼
   { "accessToken": "...", "refreshToken": "...", "expiresIn": 3600,
     "user": { "id":…, "role":…, "name":…, "email":…,
               "instructorVerificationStatus": … } }
```

**Rules that must survive the redesign:**

| Rule | Requirement |
|---|---|
| BCrypt cost 12 | Reuse existing hashes; no reset campaign |
| Legacy PBKDF2 | Either port the verifier or force those users to reset |
| Instructor approval | **Re-read `verification_status` from the database on every `/instructor/*` request.** Never trust a token claim, or a rejected instructor keeps access until expiry |
| Generic error messages | Preserve anti-enumeration wording |
| Rate limiting | Preserve the limits; **also apply them to AI endpoints, which currently have none** |
| Session fixation | Not applicable to tokens, but rotate the refresh token on use |

**New requirement:** a refresh-token table. This is additive and does not disturb the Java app.

---

## 5. API DESIGN STANDARDS

### Response envelope

```json
{ "ok": true,  "data": { }, "message": "optional" }
{ "ok": false, "error": "machine_code", "message": "Human readable",
  "fields": { "email": "Email is already registered." } }
```

### Standard error codes

`unauthenticated` (401) · `forbidden` (403) · `not_found` (404) · `validation_failed` (422) · `rate_limited` (429) · `conflict` (409) · `upstream_failed` (502) · `not_configured` (503) · `server_error` (500)

### Conventions

- Version prefix `/api/v1/` on everything.
- Cursor or page/limit pagination on **every** list, replacing today's `LIMIT 100`.
- Timestamps as ISO-8601 with an offset, computed in `Asia/Kuala_Lumpur`.
- Money as a decimal string with an explicit `currency` field, never a float.
- Enums returned as the exact DB string values.
- Localised messages driven by `Accept-Language` — **an improvement over today's English-only notifications**.

---

## 6. FLUTTER APPLICATION STRUCTURE

```
lib/
├── main.dart
├── core/
│   ├── config/        environment, API base URL
│   ├── network/       Dio client, interceptors, refresh handling
│   ├── storage/       flutter_secure_storage for tokens
│   ├── theme/         ThemeData built from the doc 09 §1.1 tokens
│   ├── i18n/          en · ar (RTL) · ms
│   └── errors/        failure types
├── features/
│   ├── auth/          login · register · verify · reset
│   ├── student/       dashboard · sessions · enrollment · payment
│   │                  recitation · progress · quran library · assistant
│   ├── instructor/    cockpit · sessions · evaluation · payments
│   ├── admin/         dashboard · users · verification · payments
│   │                  reports · logs
│   └── shared/        profile · notifications · live session
└── routing/           role-aware router with guards
```

Each feature follows `data/` (models, remote data source, repository impl) → `domain/` (entities, repository interface, use cases) → `presentation/` (screens, widgets, state).

### Suggested packages

| Need | Package |
|---|---|
| State | `flutter_riverpod` or `flutter_bloc` |
| HTTP | `dio` + `retrofit` |
| Secure storage | `flutter_secure_storage` |
| Routing | `go_router` |
| Audio record | `record` |
| Audio play | `just_audio` |
| File pick | `file_picker`, `image_picker` |
| Image compress | `flutter_image_compress` |
| Zoom | Zoom Meeting SDK for Android/iOS (native bridge) |
| PDF | `flutter_pdfview` or `open_filex` |
| Localisation | `flutter_localizations` + `intl` |
| Charts | `fl_chart` |

---

## 7. PHASED MIGRATION PLAN

| Phase | Deliverable | Depends on |
|---|---|---|
| **0** | **This documentation package** | — |
| 1 | PHP project skeleton, DB connection with pooling, migration baseline reflecting the existing schema, auth middleware, response envelope | 0 |
| 2 | Auth API — login, register, verify, resend, forgot, reset, refresh, logout — validated against existing BCrypt hashes | 1 |
| 3 | Student read APIs — dashboard, sessions, enrollments, payments, progress, notifications, profile | 2 |
| 4 | Student write APIs — enroll, receipt upload, recitation submit — with Cloudinary integration | 3 |
| 5 | Instructor APIs — sessions CRUD with Zoom, evaluation queue, AI analysis, payment verification | 4 |
| 6 | Admin APIs — users, verification, payments, reports, audit logs | 5 |
| 7 | Quran + AI APIs — library, assistant, voice, translation | 4 |
| 8 | Zoom signature endpoints + native SDK integration | 5 |
| 9 | Flutter shell — theme, routing, auth flows, secure storage | 2 |
| 10 | Flutter student module | 3, 4, 9 |
| 11 | Flutter instructor module | 5, 9 |
| 12 | Flutter admin module | 6, 9 |
| 13 | Flutter Quran + AI module | 7, 10 |
| 14 | Live session module | 8, 10, 11 |
| 15 | End-to-end testing against the parity checklist in doc 12 | all |
| 16 | Store release, monitoring, rollout | 15 |

**Parity gate:** the app is not release-ready until every rule in doc 05 is verified reproduced.

---

## 8. RISKS TO ARCHITECT AROUND

Full register in doc 13. The five that shape the architecture itself:

| Risk | Architectural response |
|---|---|
| **Business-rule drift between the two systems** | Freeze Java feature work during migration; port rules 1:1 with a written checklist |
| **Auth model change** | Preserve BCrypt hashes; re-read instructor approval per request; add refresh tokens |
| **Zoom Web SDK is unusable in Flutter** | Native Zoom Mobile SDK, or hand off to the installed Zoom app. Prototype this **early** — it is the highest-uncertainty item |
| **200 MB recitation uploads on mobile data** | Client-side compression, chunked/resumable upload, progress UI, network-aware retry |
| **AI results are not persisted and there are no cost controls** | Persist analysis results (additive schema change, needs approval); add per-user rate limits and a spend cap |

---

## 9. WHAT MUST NOT CHANGE

A checklist to re-read before every merge.

- [ ] All 20 tables, their columns, types, and meanings
- [ ] All foreign keys and their CASCADE / RESTRICT behavior
- [ ] All unique constraints — especially `uq_enrollment_student_session`, `uq_payment_enrollment`, `uq_evaluation_recitation`
- [ ] All CHECK constraints — `evaluation.score` 0–100, `progress.completion_rate` 0–100
- [ ] Every enum value, spelled exactly as today
- [ ] The permission matrix in doc 06, including **admins cannot verify payments**
- [ ] Free session → enrollment auto-approved, no payment row
- [ ] Paid session → enrollment `PENDING` + payment `PENDING`
- [ ] Payment approval is what flips the enrollment to `APPROVED`
- [ ] Rejection requires a reason and reverts the enrollment to `PENDING`
- [ ] Level matching controls session visibility and enrollment eligibility
- [ ] `capacity = 0` disables the capacity check
- [ ] One evaluation per recitation, not editable
- [ ] Progress = `AVG(evaluation.score)`
- [ ] Attendance is auto-marked `PRESENT` on join, never `ABSENT`
- [ ] Instructor approval requires a verified email
- [ ] Soft delete with email anonymization; never a hard `DELETE`
- [ ] Cannot delete or deactivate self or the last active admin
- [ ] Role can never be changed after creation
- [ ] AI failure never blocks manual grading
- [ ] `quran_portion` is required for AI analysis
- [ ] BCrypt cost 12
- [ ] Currency is MYR
- [ ] No payment gateway
