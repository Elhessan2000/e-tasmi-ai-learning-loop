# 04 — COMPLETE API / ENDPOINT INVENTORY

**Total:** 64 servlet classes exposing **76 distinct URL patterns**.

**Two critical conventions that apply to every endpoint:**

1. **Locale prefix.** `UrlLocalizationFilter` intercepts everything. An unprefixed **GET** is 302-redirected to `/{en|ar|ms}{path}`. So `GET /student/dashboard` becomes `GET /en/student/dashboard`. Non-GET requests pass through unprefixed. Servlets always see the *stripped* path. **A mobile client must either send the locale prefix or follow the redirect.**

2. **Authentication is a session cookie.** All protected endpoints — including every JSON endpoint — authenticate via `JSESSIONID`. There are no tokens, no `Authorization` header support, and no CORS headers anywhere in the codebase.

**Response convention for protected routes:**

| Condition | HTML routes | `/student/api/*` and `/instructor/api/*` |
|---|---|---|
| No session | `302` → `/auth/login?expired=1` | `401 {"ok":false,"error":"unauthenticated","message":"Login required."}` |
| Wrong role | `302` → `/dashboard?forbidden=1` | `403 {"ok":false,"error":"forbidden","message":"You do not have access to this resource."}` |

Forbidden HTML responses also return the JSON form when the request carries `X-Requested-With: XMLHttpRequest` or `Accept: application/json`.

---

## SECTION 1 — PUBLIC ENDPOINTS (no authentication)

### 1.1 `GET /home`

| | |
|---|---|
| **Purpose** | Public marketing landing page |
| **Auth** | None |
| **Response** | `text/html` (`home.jsp`) with `Cache-Control: no-store, no-cache` |
| **Data** | `statStudents`, `statSessions` (scheduled+ongoing+completed), `statCompletionPct` (`ROUND(AVG(evaluation.score))` clamped 0–100, null when no evaluations) |
| **Tables** | `user`, `tasmi_session`, `evaluation` |
| **Verified live** | `GET /en/home` → `200`, 71,719 bytes |

### 1.2 `GET /auth/register/role`

Role-selection landing (`RoleSelectionServlet`). Presents Student vs Instructor cards.

### 1.3 `GET /auth/register` · `POST /auth/register`

| | |
|---|---|
| **Auth** | None |
| **Content-Type** | `multipart/form-data` (`@MultipartConfig`) |
| **Body (both roles)** | `role` (`STUDENT`\|`INSTRUCTOR`), `fullName`, `email`, `phone`, `password`, `confirmPassword` |
| **Body (student)** | `+ studentLevel` ∈ `{PRIMARY_SCHOOL, SECONDARY_SCHOOL, HIGH_SCHOOL, UNIVERSITY}` |
| **Body (instructor)** | `+ bio`, `+ qualification` (PDF ≤ 10 MB) |
| **Validation** | fullName non-blank; email non-blank (no regex on this path); phone 8–15 digits; password ≥8 with upper+lower+digit; passwords match; role must be STUDENT or INSTRUCTOR (**ADMIN is rejected at both servlet and service layers**) |
| **Rate limit** | 10 / 10 min / IP |
| **Tables** | `user` INSERT, `student` **or** `instructor` INSERT, `notifications` INSERT — one transaction |
| **External** | Verification email (Brevo → Resend → SMTP) |
| **Success** | `302` → `/auth/verify-email?email=…&msg=…` (or `&warn=…` if the email failed to send) |
| **Errors** | `"Email is already registered."`, `"Invalid phone number format."`, `"Weak password. Use 8+ chars with upper, lower, and a number."`, `"Passwords do not match"`, `"Please select your education level."`, `"Bio is required for instructors."`, `"Qualification PDF exceeds 10 MB."`, `"Only PDF qualification files are allowed."`, `"Too many registration attempts. Please try again later."` |

### 1.4 `GET /auth/login` · `POST /auth/login`

| | |
|---|---|
| **Body** | `email`, `password` |
| **Rate limit** | 5 / 5 min per `{ip}:{email}`; cleared on success; bypassed on localhost or `ETASMI_DISABLE_RATE_LIMIT=true` |
| **Session set** | `userId` (long), `role` (String), `displayName`, `email`, `instructorVerificationStatus` (instructors only). Prior session invalidated first. `maxInactiveInterval = 900` |
| **Success (HTML)** | `302` → `/admin/dashboard` for ADMIN, else `/dashboard` |
| **Success (AJAX)** | `200 {"success":true,"redirect":"…"}` when `X-Requested-With: XMLHttpRequest` or `Accept: application/json` |
| **Errors** | `"Email and password are required."`, `"Invalid email or password."` (generic, covers both unknown user and bad password), `"Your account has been deactivated…"`, email-verification message, `"Too many login attempts. Please try again later."` |

### 1.5 `GET /auth/logout`

Invalidates the session and redirects to `/auth/login`. **GET-only.**

### 1.6 `GET /auth/verify-email` · `POST /auth/verify-email`

| Variant | Behavior |
|---|---|
| `GET ?token=…` | Verifies the 24-hour link token → forwards `verify_success.jsp` or `verify_failed.jsp` |
| `GET ?email=…` | Renders the 6-digit code entry page |
| `POST` (`email`, `code`) | Verifies the code. Must match `^[0-9]{6}$`, TTL 10 min, **max 5 attempts** |

On success: `user.email_verified=1`, `email_verified_at` set, `email_verification` row deleted. Student → `status=ACTIVE`. Instructor → `ACTIVE` only if already `APPROVED`, otherwise stays `INACTIVE` with a pending/rejected message.

Errors: `"Incorrect verification code."` (also returned for unknown emails — anti-enumeration), `"Verification code expired. Please resend a new code."`

### 1.7 `POST /auth/resend-verification`

Rate limit 5 / 10 min / IP. Always returns a generic success. Only sends when the user exists and is unverified.

### 1.8 `GET /auth/verify-status?email=…`

| | |
|---|---|
| **Auth** | **None** |
| **Response** | `200 {"known":bool,"verified":bool}` |
| **⚠ Weakness** | Unauthenticated email enumeration oracle. Used by `auth.js` to poll after registration. |

### 1.9 `GET /auth/forgot-password` · `POST /auth/forgot-password`

Body `email`. Rate limit 10 / 10 min / IP. Token = 32 random bytes URL-safe Base64, SHA-256 stored, 30-minute TTL, prior tokens invalidated. Always generic success.

### 1.10 `GET /auth/reset-password?token=…` · `POST /auth/reset-password`

GET validates the token is active. POST body `token`, `password`, `confirmPassword` — same strength rules as registration. Marks `used_at` (single-use).

### 1.11 `GET /uploads/*`

| | |
|---|---|
| **Auth** | **NONE — this path is not covered by `AuthFilter`** |
| **Behavior** | Strips `..` and `\`, resolves under `ETASMI_UPLOADS_DIR`, verifies containment |
| **Headers** | `Cache-Control: public, max-age=86400` |
| **Errors** | `403` if the path escapes the root, `404` if missing |
| **⚠ Security** | Payment receipts, recitation audio, profile photos, and session banners are all publicly readable by anyone with the URL. See doc 11. |

---

## SECTION 2 — SHARED AUTHENTICATED ENDPOINTS

### 2.1 `GET /dashboard`

Role router. Forwards `STUDENT` → `/student/dashboard`, `INSTRUCTOR` → `/instructor/dashboard`, `ADMIN` → `/admin/dashboard`. No role → `302` `/auth/login`.

### 2.2 `GET /notifications`

Any authenticated role. Students are redirected to `/student/notifications`; instructors and admins get `jsp/common/notifications.jsp`. **Read-only — no POST, no mark-read, no delete, no JSON variant.**

---

## SECTION 3 — STUDENT ENDPOINTS

All require `role = STUDENT`.

### 3.1 `GET /student/dashboard`
Renders `dashboard.jsp`. No parameters. Tables: `student`, `enrollment`, `payment`, `tasmi_session`, `recitation`, `evaluation`, `notifications`, `instructor`, `user`, `progress`.

### 3.2 `GET /student/sessions` · `GET /student/available-sessions` · `POST`

| | |
|---|---|
| **GET params** | `q` (search), `notice` (flash), `sessionId` (redirects to enroll-confirm) |
| **POST `action`** | absent or `checkout` → checkout; **any other value → `400`** |
| **POST body** | `sessionId`, optional `q`, `from` |
| **Success** | free → `302 /student/enrollments?notice=free_confirmed`; paid → `302 /student/payments/qr?notice=checkout_ready&enrollmentId=…` |
| **Errors** | `"This session is not available for enrollment."`, `"You can only enroll in sessions that match your level."`, `"This session has already reached its capacity."` |
| **Notice codes** | `payment_required`, `reactivated_payment_required`, `already_enrolled_payment_required`, `already_enrolled_free_session`, `free_session`, `free_confirmed`, `payment_cancelled`, `qr_checkout_unavailable`, `payment_setup_missing`, `checkout_ready`, `checkout_pending`, `already_paid` |

### 3.3 `GET /student/enroll-confirm` · `POST`
Same servlet as 3.2. **A GET here performs checkout immediately** rather than showing the confirmation page.

### 3.4 `GET /student/enrollments` · `POST`

| | |
|---|---|
| **POST `action`** | `withdraw` (with `enrollmentId`) — **implemented, but no UI control exists** |
| **GET params** | `notice`, `joinError` |
| **Success** | `"Enrollment withdrawn successfully."` |
| **Error** | `"Unable to withdraw this enrollment right now."` |

### 3.5 `GET /student/payments` · `POST`

POST with `action=cancel` withdraws the enrollment (**no UI control**); POST without an action re-runs `beginPaymentFlow(enrollmentId)`. GET lists payments with `amount > 0` for active instructors.

### 3.6 `GET /student/payments/qr` · `POST` (alias `/student/payments/submit`)

| | |
|---|---|
| **Content-Type** | `multipart/form-data`; maxFileSize **10 MB**, maxRequestSize 12 MB |
| **GET params** | `enrollmentId`, `notice` |
| **POST body** | `enrollmentId`, `receipt` (file, required). Also accepted but absent from the form: `reference` (≤120), `note` (≤500). The JSP sends `action=submitReceipt`, which the servlet ignores. |
| **Accepted files** | JPG, PNG, WEBP, GIF, PDF |
| **Effect** | `payment.receipt_file_path` set, status → `AWAITING_VERIFICATION`, history row `SUBMITTED`/`RESUBMITTED`, notifications to student + instructor |
| **External** | Cloudinary `receipts/` (`resource_type=raw` for PDF, `image` otherwise) or local `/uploads/receipts/` |
| **Success** | `302` same page with `notice=receipt_submitted` \| `receipt_resubmitted` |
| **Errors** | `"Please choose your payment receipt…"`, `"Receipt files must be 10 MB or smaller."`, `"Please upload a clear image…"`, plus service errors (already approved, already awaiting) |
| **Blocked when** | Payment is `APPROVED` or already `AWAITING_VERIFICATION`; resubmission is allowed only from `REJECTED` |

### 3.7 `GET /student/payments/receipt?paymentId=X[&download=1]`

Requires payment `APPROVED`, `amount > 0`, enrollment ownership — otherwise `404`. `download=1` returns a **hand-built PDF 1.4 document generated inline in the servlet** (not PDFBox). Reference format `ETP-{paymentId}`.

### 3.8 `GET /student/progress`
Returns the stored `progress.completion_rate` plus a live-computed summary (enrollments, completed sessions, payments, recitations, attendance).

### 3.9 `GET /student/recitations` · `POST`

| | |
|---|---|
| **Content-Type** | `multipart/form-data`; maxFileSize **200 MB** |
| **POST body** | `enrollmentId` (required), `audio` (file), optional `ajax=1`. The JS also sends `duration`, which is **not read**. |
| **Formats** | webm, mp3, wav, m4a, ogg, aac, mp4, mov, mpeg, mpga; MIME `audio/*` or `video/*` |
| **Preconditions** | Enrollment `APPROVED`, session not `CANCELLED`, payment `APPROVED` when fee > 0 |
| **Storage** | Cloudinary `recitations/` via the **video** upload endpoint, or local |
| **JSON success** | `200 {"ok":true,"redirect":"/student/recitations?submitted=1"}` |
| **JSON error** | `400 {"ok":false,"error":"…"}` |
| **AI** | **None triggered** |

### 3.10 `GET /student/recitation-audio?id={recitationId}`
Ownership via `enrollment.student_id`. Serves the file or redirects to Cloudinary. `400` bad id, `404` not found, `403` not owner.

### 3.11 `GET /student/notifications`
Read-only list ordered `created_at DESC`.

### 3.12 `GET /student/profile` · `POST`

| `action` | Body | Rules |
|---|---|---|
| `profile` | `fullName`, `phone` | Both required |
| `password` | `currentPassword`, `newPassword`, `confirmPassword` | Current verified; new ≥8 with upper+lower+digit |
| `photo` | `photo` (file) | ≤2 MB; JPG/PNG/WEBP; validated with `ImageIO` |

Unauthenticated POST redirects to login. Updates session `displayName`.

### 3.13 `GET /student/profile-photo`
Cache-busted redirect to the user's image. `401` no session, `404` no image.

### 3.14 `GET /student/instructor-photo?instructorId=X`
`401` / `400` / `404`.

### 3.15 `GET /student/joinLive?sessionId=X[&external=1][&embed=1]`

Validates via `LiveSessionAccessService.validateStudentJoin`: enrolled, enrollment `APPROVED`, payment `APPROVED` when fee > 0, join URL present, provider `ZOOM`/`MANUAL`, valid http(s) link. Redirects to `/student/live/embed` or the external Zoom URL. Marks attendance on the external path. Errors redirect to `/student/enrollments?joinError={code}` — codes include `access_denied`, `join_url_missing`, `embed_unavailable`.

### 3.16 `GET /student/live/embed?sessionId=X`
Re-validates, marks attendance, forwards `live-embed.jsp` with Zoom client config.

### 3.17 `GET /student/quran-library`
Renders the Quran Library SPA shell.

---

## SECTION 4 — STUDENT JSON APIs (`/student/api/*`)

All return `application/json;charset=UTF-8` and `401` when unauthenticated.

### 4.1 `GET|POST /student/api/zoomMeetingSignature?sessionId=X`

```json
{ "ok": true, "signature": "<JWT>", "sdkKey": "...", "meetingNumber": "...",
  "passWord": "...", "userName": "...", "jwtRole": 0 }
```

JWT: HS256; claims `appKey`, `sdkKey`, `mn`, `role=0`, `iat = now−30s`, `exp`/`tokenExp = iat+7200s`, `video_webrtc_mode=1`. Signed with `ZOOM_MEETING_SDK_SECRET` **server-side only**.

Errors: `unauthenticated` 401 · `invalid_request` 400 · `sdk_not_configured` 503 · `access_denied` 403 · `embed_unavailable` 400 · `meeting_id_missing` 400 · `passcode_missing` 400 · `signature_failed` 500 · `server_error` 500.

### 4.2 `POST /student/api/quran-assistant/chat`

**Request** (max body 64 KB):
```json
{ "mode": "tafsir", "message": "…",
  "history": [{"role":"user","content":"…"},{"role":"assistant","content":"…"}],
  "context": { "surahId": 2, "surahName": "Al-Baqarah", "verseKey": "2:255", "ayahNumber": 255 } }
```
Modes: `tafsir`, `tajweed`, `meaning`, `memorization`, `word`, `asbab`, `tips`, `general`.

**Response:** `{ "ok": true, "reply": "…", "mode": "tafsir", "scopeReminder": false }`

Limits: user message truncated at 2,400 chars; **max 12 history messages**; reply truncated at 12,000 chars. External: `POST https://api.openai.com/v1/chat/completions`, model `OPENAI_ASSISTANT_MODEL` (default `gpt-4o-mini`), `temperature 0.35`, `max_tokens 1800`.

Errors: `503 ai_not_configured` · `502 ai_upstream_failed` · `400` validation.

### 4.3 `POST /student/api/quran-assistant/voice/turn`

`multipart/form-data`, max 25 MB. Fields: audio blob, `lang` (`en`\|`ar`\|`ms`), `mode`. Three OpenAI calls: transcription → chat → speech.

```json
{ "ok": true, "transcript": "…", "reply": "…", "mode": "general",
  "scopeReminder": false, "audio": "<base64 mp3>", "audioMime": "audio/mpeg" }
```
No speech detected: `{ "ok": true, "transcript": "", "noSpeech": true }`. If TTS fails, the text reply is still returned with `audio` omitted.

### 4.4 `POST /student/api/quran-assistant/voice/speak`
JSON `{text, lang}` → base64 MP3. Replay-only TTS.

### 4.5 `GET /student/api/quran-library/chapters`
`{ "ok": true, "chapters": [{id, nameArabic, nameSimple, nameComplex, translatedName?, versesCount?, revelationPlace?}] }`. `Cache-Control: private, max-age=300`. Falls back to the bundled 114-surah catalog when Quran Foundation is not configured.

### 4.6 `GET /student/api/quran-library/verses/by-chapter`
Params: `chapter` (1–114, required), `merged` (default `1`), `page`, `per_page` (default 1, 50), `translations` (CSV resource IDs), `translate` (truthy → resource `131`), `tafsirs` (CSV).
Response includes `verses[]` with `id`, `chapterId`, `verseNumber`, `verseKey`, `verseIndex`, `textArabic`, `translationText?`, `tafsirPreview?`, `tafsirHtmlSafe?`, `pageNumber?`, `juzNumber?`, plus optional `pagination`. `Cache-Control: private, max-age=120`.

### 4.7 `GET /student/api/quran-library/verses/by-key`
Params: `verse_key`/`verseKey` (required), `tafsirs` (**required**), optional `translations`, `translate`. Returns a single verse with full sanitized tafsir HTML.

### 4.8 `GET /student/api/quran-library/search?q=…`
Pass-through of the upstream Quran Foundation quick search (`mode=quick`, `navigationalResultsNumber=10`, `versesResultsNumber=25`), capped at 400 KB. `Cache-Control: private, max-age=60`.

### 4.9 `GET /student/api/quran-library/resources/translations[?language=]`
`{ "ok": true, "translations": [{id, name?, authorName?, slug?, languageName?}] }`. `max-age=600`.

### 4.10 `GET /student/api/quran-library/resources/tafsirs[?language=]`
Same shape with a `tafsirs` array. `max-age=600`.

### 4.11 `GET /student/api/quran-library/audio/chapter-reciters`
Raw upstream JSON, capped at 400 KB. `max-age=600`.

### 4.12 `GET /student/api/quran-library/audio/chapter-file?chapter=&reciter_id=`
`{ "ok": true, "chapterNumber", "reciterId", "audioUrl", "durationSec?", "verseTimings": [{verseKey, from, to}] }`. Protocol-relative URLs are prefixed with `https:`. `max-age=300`. **The browser then loads `audioUrl` directly from the upstream CDN — it is not proxied.**

### 4.13 `GET /student/api/quran-library/reciter-portrait?rel=images/reciters/…`
Server-side image proxy over the allowed Quran CDN hosts. Max 2 MB, content-type validated by magic bytes. `Cache-Control: public, max-age=86400`.

### 4.14 `POST /student/api/quran-library/translate-ai`
Body `{ "verses": [{"verseKey":"2:255","arabic":"…"}] }`. Max body 2 MB, **max 320 verses per request**, processed in server-side batches of 30. Response `{ "ok": true, "source": "openai", "translations": {"2:255":"…"} }`. Results cached in a process-wide `ConcurrentHashMap` (JVM-local, lost on restart). Partial batch failures return whatever succeeded.

### 4.15 `GET /student/api/quran-foundation/health[?live=1]`
Configuration snapshot with **no secrets**; `live=1` additionally probes the token endpoint and the chapters call. **Verified live:** returns `401` when unauthenticated.

**Quran Library upstream error codes** (mapped by `QuranLibraryUpstreamErrors`): `not_configured`, `oauth_token_failed`, `upstream_forbidden`, `upstream_not_found`, `upstream_unauthorized`, `upstream_error`, `load_failed` — mostly surfaced as HTTP `502` (403 passes through for entitlement failures).

---

## SECTION 5 — INSTRUCTOR ENDPOINTS

All require `role = INSTRUCTOR` **and** `verification_status = APPROVED`; otherwise `AuthFilter` forwards to `pendingApproval.jsp`.

### 5.1 `GET /instructor/dashboard`
Renders the cockpit. No parameters.

### 5.2 `GET /instructor/dashboard/calendar.json?from=&to=`
Dates as `YYYY-MM-DD`; defaults today−7 → today+21. Errors `{"error":"unauthorized"}`, `{"error":"server_error"}`.

### 5.3 `GET /instructor/dashboard/heartbeat.json`
Polled every 30 s for live session state.

### 5.4 `GET /instructor/sessions` · `POST`

`multipart/form-data`; maxFileSize 3 MB, maxRequestSize 5 MB.

| `action` | Required params | Effect |
|---|---|---|
| *(absent)* or `create` | `title`, `level`, `sessionDate`, `sessionTime`, `durationMinutes`, `fee`, `capacity` (+ optional `description`, `quranPortion`, `bannerImage`) | Creates the Zoom meeting, then inserts `tasmi_session` |
| `update` | `sessionId` + the same fields | **`SCHEDULED` only.** Patches the Zoom meeting, clears live/recording timestamps, notifies enrolled students |
| `delete` | `sessionId` | Best-effort Zoom delete, notifies students, hard-deletes the row |
| `start` | `sessionId` | Requires ≥1 `APPROVED` enrollment and a valid link. `SCHEDULED` → `ONGOING`. Auto-completes other ongoing sessions. Redirects to `/instructor/live-session` |
| `complete` | `sessionId` | `ONGOING` → `COMPLETED` |
| `togglepassword` | `sessionId` | Flips `is_password_visible`. **No UI caller** |

**GET params:** `editId`, and flash flags `created=1`, `updated=1`, `deleted=1`, `started=1`, `completed=1`, `errorMessage=`.

**Validation:** title/date/time required; `capacity > 0`; `durationMinutes > 0` (UI 15–360, defaults to 60 on parse failure); `fee ≥ 0`; `level` ∈ the four enum values; banner must be `image/*` (jpg/jpeg/png/webp).

**Errors:** `"Your account is not approved yet. You cannot create sessions."` · `"Zoom integration is not configured…"` · `"No Zoom host email available…"` · `"At least one approved student must be registered…"` · `"Please enter a valid title, target student level, date, time, duration, fee, and capacity."` · FK violation message when deletion is blocked by existing enrollments.

### 5.5 `GET /instructor/evaluations` · `POST`

| `action` | Params | Effect |
|---|---|---|
| `analyze` | `recitationId` | Runs the OpenAI pipeline; stores the result in the **HTTP session** under `etasmi.aiAnalysis.{recitationId}` and renders it from a request attribute. Requires the session's `quran_portion`. |
| `save` (default for any unrecognised value) | `recitationId`, `score` (0–100), `feedback` | INSERTs `evaluation`, upserts `progress`, clears the AI session key |
| `mark_reviewed` | `sessionId` | Sets `tasmi_session.evaluation_reviewed_at = now()` |
| `reopen` | `sessionId` | Clears `evaluation_reviewed_at` |

**GET params:** `saved=1`, `reviewed=1|0&session={id}`.

**Errors:** `"Score must be between 0 and 100."` · `"This recitation has already been evaluated."` · `"You cannot evaluate this recitation."` · `"Your instructor account is not approved."`

**AI statuses:** `OK`, `REJECTED` (high-confidence non-Quran), `CANNOT_EVALUATE` (empty/failed transcription), `FAILED` (no API key, file too large/small, missing expected text).

### 5.6 `GET /instructor/payments` · `POST`

`multipart/form-data`; maxFileSize 8 MB, maxRequestSize 10 MB.

| `action` | Params | Effect |
|---|---|---|
| `saveSettings` | `qrImage` (≤8 MB, JPG/PNG/WEBP), `removeQr=1`, `bankName`, `accountHolderName`, `sessionId` | Upserts `instructor_payment_settings`. Requires a QR **or** both bank name and account holder |
| `approve` | `paymentId`, `sessionId` | Payment → `APPROVED`, enrollment → `APPROVED`, history row, student notified |
| `reject` | `paymentId`, `sessionId`, `reason` (**required**, ≤500) | Payment → `REJECTED`, enrollment → `PENDING`, history row, student notified |

**Guards:** the payment's session must belong to this instructor; a receipt file must exist.
**Notices:** `payment_approved`, `payment_rejected`, `settings_saved`, `verify_error`, `settings_error`.

### 5.7 `GET|POST /instructor/payment-settings`
Legacy — `302` redirect to `/instructor/payments`.

### 5.8 `GET /instructor/profile` · `POST`

| `action` | Params |
|---|---|
| `profile` | `namePrefix`, `fullName`*, `phone`*, `qualification`, and `zoomEmail` (**accepted but has no form field**) |
| `photo` | `photo` (≤2 MB; JPG/PNG/WEBP) |

maxFileSize 2 MB, maxRequestSize 3 MB. Messages: `"Profile updated successfully."`, `"Profile photo updated."`, `"Full name and phone are required."`, `"Allowed formats: JPG, PNG, WEBP."`

### 5.9 `GET /instructor/profile-photo`
Cache-busted redirect.

### 5.10 `GET /instructor/live-session?sessionId=X[&external=1][&embed=1]`
Validates host access (`session.instructor_id` must match). Embeds `live-embed.jsp` or redirects to `zoom_start_url`. Errors: `"Invalid live session request."`, `"You do not have access to host this session."`, `"Live meeting details are not ready."`

### 5.11 `GET|POST /instructor/api/zoomMeetingSignature?sessionId=X`
Same shape as 4.1 but with `jwtRole: 1` (host) and an additional best-effort **`zak`** field fetched from `GET /v2/users/{hostEmail}/token?type=zak`. The ZAK is what allows the Web SDK to *start* (not merely join) the meeting.

### 5.12 `GET /instructor/materials/open?sessionId=&materialId=[&download=1]`
Ownership: instructor user → `instructor_id` → `session.instructor_id`. Cloudinary URLs redirect (as an attachment when `download=1`); local paths are restricted to the `uploads/materials` directories. Sets `X-Content-Type-Options: nosniff`. **Serve only — no upload endpoint exists.**

---

## SECTION 6 — ADMIN ENDPOINTS

All require `role = ADMIN`.

### 6.1 `GET /admin/dashboard`
Platform KPIs. See doc 02 A2 for each metric's SQL, including the known `SUCCESS`/`FAILED` legacy-status defect.

### 6.2 `GET /admin/users?search=&role=`
`LIMIT 100`, ordered `created_at DESC`, excludes `status='DELETED'`.

### 6.3 `GET|POST /admin/users/create`
Body `fullName`, `email`, `phone`, `password`, `role`. Validation: fullName 3–150; email regex `^[^@\s]+@[^@\s]+\.[^@\s]+$` (≤191, lowercased); phone 7–30; password ≥8 (**the JSP declares `minlength=6`, weaker than the server**); role ∈ the three enum values. Creates the user as `is_active=1, email_verified=1, status=ACTIVE`, plus the role profile row — an **admin-created instructor is inserted as `APPROVED`**. Audit `USER_CREATED`. Success: `302 /admin/users?success=User+created`.

### 6.4 `GET /admin/users/edit?id={userId}`
Renders the edit form; role is displayed read-only.

### 6.5 `POST /admin/users/update`
Body `id`, `fullName`, `email`, `phone`, `role` (hidden; must match the existing value). **Role changes are rejected**: `"Changing role for existing users is not supported yet"`. Email uniqueness checked excluding self. Audit `USER_UPDATED`.

### 6.6 `POST /admin/users/toggle`
Body `id`, `active`. Cannot deactivate self or the last active admin. Sets `is_active` + `status`. Audit `USER_ACTIVATED` / `USER_DEACTIVATED`.

### 6.7 `POST /admin/users/delete`
Body `id`. Soft delete with email anonymization (full SQL in doc 03 §3.1 / doc 02 A7). Cannot delete self or the last active admin. Audit `USER_DELETED`.

### 6.8 `GET /admin/instructors/verification?status=PENDING` · `POST`
POST body `instructorId`, `action=approve|reject`.
**Aliases:** `GET /admin/instructors/pending` (redirect), `GET /admin/instructors/view?id=`, `POST /admin/instructors/approve`, `POST /admin/instructors/reject` (each accepting `instructorId` or `id`).
**Approve requires `user.email_verified = true`**, else the transaction is rolled back. Approve → `verification_status=APPROVED`, `user.status=ACTIVE`. Reject → `REJECTED`, `user.status=INACTIVE`. In-app notification only — **no email is sent**, and **no rejection reason is captured**.

### 6.9 `GET /admin/instructors/qualification/download?instructorId=X[&inline=1]`
Redirects to a **signed 300-second** Cloudinary URL for remote files, or streams local files after `QualificationFileUtil.isAllowedQualificationPath` validation.

### 6.10 `GET /admin/payments`

| Mode | Parameters |
|---|---|
| Page | `studentId`, `instructorId`, `sessionId`, `status`, `fromDate`, `toDate`, `minAmount`, `maxAmount`, `search`, `sort` (`date`\|`amount`\|`student`\|`session`\|`instructor`\|`status`), `dir` (`asc`\|`desc`), `page` (25/page) |
| AJAX detail | `?ajax=detail&paymentId=X` → JSON transaction + verification history |
| Export | `?export=pdf\|session\|sessionpdf\|student\|studentpdf\|instructor\|instructorpdf\|revenue-session\|revenue-instructor\|financial\|financial-summary\|summary` |

All queries constrain `p.amount > 0`. **Read-only — no POST, no approve/reject/refund.**

### 6.11 `GET /admin/reports`
Params `type` (`sessions`\|`enrollments`\|`payments`\|`verification`\|`evaluations`\|`users`), `dateFrom`, `dateTo`, `status`, `role`, `sessionMode`, `generated`, `export=csv|pdf`. Per-type date columns and metrics are tabulated in doc 02 A11. **No POST.**

### 6.12 `GET /admin/logs`
Params `filterAction` (legacy alias `action`), `filterRole` (alias `role`), `dateFrom`, `dateTo`. `LIMIT 100`, newest first. Invalid dates are ignored and surface an error message.

### 6.13 `GET /admin/profile` · `POST`
`action=profile` (`fullName`, `phone` — both required) or `action=password` (current verified; new ≥8 with upper+lower+digit). Email is read-only.

---

## SECTION 7 — ENDPOINT SUMMARY TABLE

| # | Method(s) | Path | Auth | Role | JSON? |
|---|---|---|---|---|---|
| 1 | GET | `/home` | No | — | No |
| 2 | GET | `/auth/register/role` | No | — | No |
| 3 | GET POST | `/auth/register` | No | — | No |
| 4 | GET POST | `/auth/login` | No | — | Optional |
| 5 | GET | `/auth/logout` | Yes | Any | No |
| 6 | GET POST | `/auth/verify-email` | No | — | No |
| 7 | POST | `/auth/resend-verification` | No | — | No |
| 8 | GET | `/auth/verify-status` | **No** | — | **Yes** |
| 9 | GET POST | `/auth/forgot-password` | No | — | No |
| 10 | GET POST | `/auth/reset-password` | No | — | No |
| 11 | GET | `/uploads/*` | **No** | — | No |
| 12 | GET | `/dashboard` | Yes | Any | No |
| 13 | GET | `/notifications` | Yes | Any | No |
| 14 | GET | `/student/dashboard` | Yes | STUDENT | No |
| 15 | GET POST | `/student/sessions` | Yes | STUDENT | No |
| 16 | GET POST | `/student/available-sessions` | Yes | STUDENT | No |
| 17 | GET POST | `/student/enroll-confirm` | Yes | STUDENT | No |
| 18 | GET POST | `/student/enrollments` | Yes | STUDENT | No |
| 19 | GET POST | `/student/payments` | Yes | STUDENT | No |
| 20 | GET POST | `/student/payments/qr` | Yes | STUDENT | No |
| 21 | GET POST | `/student/payments/submit` | Yes | STUDENT | No |
| 22 | GET | `/student/payments/receipt` | Yes | STUDENT | No (PDF) |
| 23 | GET | `/student/progress` | Yes | STUDENT | No |
| 24 | GET POST | `/student/recitations` | Yes | STUDENT | Optional |
| 25 | GET | `/student/recitation-audio` | Yes | STUDENT | No |
| 26 | GET | `/student/notifications` | Yes | STUDENT | No |
| 27 | GET POST | `/student/profile` | Yes | STUDENT | No |
| 28 | GET | `/student/profile-photo` | Yes | STUDENT | No |
| 29 | GET | `/student/instructor-photo` | Yes | STUDENT | No |
| 30 | GET | `/student/joinLive` | Yes | STUDENT | No |
| 31 | GET | `/student/live/embed` | Yes | STUDENT | No |
| 32 | GET | `/student/quran-library` | Yes | STUDENT | No |
| 33 | GET POST | `/student/api/zoomMeetingSignature` | Yes | STUDENT | **Yes** |
| 34 | POST | `/student/api/quran-assistant/chat` | Yes | STUDENT | **Yes** |
| 35 | POST | `/student/api/quran-assistant/voice/turn` | Yes | STUDENT | **Yes** |
| 36 | POST | `/student/api/quran-assistant/voice/speak` | Yes | STUDENT | **Yes** |
| 37 | GET | `/student/api/quran-library/chapters` | Yes | STUDENT | **Yes** |
| 38 | GET | `/student/api/quran-library/verses/by-chapter` | Yes | STUDENT | **Yes** |
| 39 | GET | `/student/api/quran-library/verses/by-key` | Yes | STUDENT | **Yes** |
| 40 | GET | `/student/api/quran-library/search` | Yes | STUDENT | **Yes** |
| 41 | GET | `/student/api/quran-library/resources/translations` | Yes | STUDENT | **Yes** |
| 42 | GET | `/student/api/quran-library/resources/tafsirs` | Yes | STUDENT | **Yes** |
| 43 | GET | `/student/api/quran-library/audio/chapter-reciters` | Yes | STUDENT | **Yes** |
| 44 | GET | `/student/api/quran-library/audio/chapter-file` | Yes | STUDENT | **Yes** |
| 45 | GET | `/student/api/quran-library/reciter-portrait` | Yes | STUDENT | No (image) |
| 46 | POST | `/student/api/quran-library/translate-ai` | Yes | STUDENT | **Yes** |
| 47 | GET | `/student/api/quran-foundation/health` | Yes | STUDENT | **Yes** |
| 48 | GET | `/instructor/dashboard` | Yes | INSTRUCTOR✓ | No |
| 49 | GET | `/instructor/dashboard/calendar.json` | Yes | INSTRUCTOR✓ | **Yes** |
| 50 | GET | `/instructor/dashboard/heartbeat.json` | Yes | INSTRUCTOR✓ | **Yes** |
| 51 | GET POST | `/instructor/sessions` | Yes | INSTRUCTOR✓ | No |
| 52 | GET POST | `/instructor/evaluations` | Yes | INSTRUCTOR✓ | No |
| 53 | GET POST | `/instructor/payments` | Yes | INSTRUCTOR✓ | No |
| 54 | GET POST | `/instructor/payment-settings` | Yes | INSTRUCTOR✓ | No |
| 55 | GET POST | `/instructor/profile` | Yes | INSTRUCTOR✓ | No |
| 56 | GET | `/instructor/profile-photo` | Yes | INSTRUCTOR✓ | No |
| 57 | GET | `/instructor/live-session` | Yes | INSTRUCTOR✓ | No |
| 58 | GET POST | `/instructor/api/zoomMeetingSignature` | Yes | INSTRUCTOR✓ | **Yes** |
| 59 | GET | `/instructor/materials/open` | Yes | INSTRUCTOR✓ | No |
| 60 | GET | `/admin/dashboard` | Yes | ADMIN | No |
| 61 | GET | `/admin/users` | Yes | ADMIN | No |
| 62 | GET POST | `/admin/users/create` | Yes | ADMIN | No |
| 63 | GET | `/admin/users/edit` | Yes | ADMIN | No |
| 64 | POST | `/admin/users/update` | Yes | ADMIN | No |
| 65 | POST | `/admin/users/toggle` | Yes | ADMIN | No |
| 66 | POST | `/admin/users/delete` | Yes | ADMIN | No |
| 67 | GET POST | `/admin/instructors/verification` | Yes | ADMIN | No |
| 68 | GET | `/admin/instructors/pending` | Yes | ADMIN | No |
| 69 | GET | `/admin/instructors/view` | Yes | ADMIN | No |
| 70 | POST | `/admin/instructors/approve` | Yes | ADMIN | No |
| 71 | POST | `/admin/instructors/reject` | Yes | ADMIN | No |
| 72 | GET | `/admin/instructors/qualification/download` | Yes | ADMIN | No (file) |
| 73 | GET | `/admin/payments` | Yes | ADMIN | Optional |
| 74 | GET | `/admin/reports` | Yes | ADMIN | No (CSV/PDF) |
| 75 | GET | `/admin/logs` | Yes | ADMIN | No |
| 76 | GET POST | `/admin/profile` | Yes | ADMIN | No |

✓ = also requires `verification_status = APPROVED`.

---

## SECTION 8 — WHAT A MOBILE CLIENT CANNOT USE TODAY

| Gap | Consequence |
|---|---|
| **No token authentication** | Every endpoint depends on `JSESSIONID`. A Flutter client would have to manage cookies and endure a 15-minute inactivity timeout with no refresh mechanism. |
| **No CORS headers** | No `Access-Control-*` anywhere. Any non-same-origin client is blocked by the browser; a native app is unaffected but still has the cookie problem. |
| **~74% of endpoints return HTML** | Only 20 of 76 patterns emit JSON. Everything else is a JSP render or a redirect, so a mobile app cannot consume them. |
| **Flash state lives in query parameters** | Success/error is communicated via `?notice=`, `?success=`, `?error=`, `?joinError=` on a redirect, not in a response body. |
| **No API versioning** | No `/v1/` prefix; any change is breaking. |
| **No pagination on most lists** | Fixed `LIMIT 100` (users, audit logs) or unbounded. Only `/admin/payments` paginates (25/page). |
| **No consistent error envelope** | JSON endpoints use `{ok:false,error,message}`; HTML endpoints use redirects; some use `sendError` with the container's default page. |
| **`/uploads/*` is unauthenticated** | Private media is publicly addressable. |
