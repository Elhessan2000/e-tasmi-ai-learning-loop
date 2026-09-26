# 06 — AUTHENTICATION & AUTHORIZATION

**One-line summary:** classic server-side `HttpSession` authentication with a `JSESSIONID` cookie, BCrypt password hashing, mandatory email verification, and prefix-based role authorization in a single filter. **There is no JWT, no OAuth for application login, no API token, and no remember-me.**

---

## 1. Authentication Mechanism

| Aspect | Implementation |
|---|---|
| **Mechanism** | Java EE `HttpSession` |
| **Transport** | `JSESSIONID` cookie, set by Tomcat |
| **Cookie flags** | Container defaults. **`Secure`, `HttpOnly`, and `SameSite` are not explicitly configured** in `web.xml` — no `<session-config><cookie-config>` block exists |
| **Timeout** | `session.setMaxInactiveInterval(900)` = **15 minutes** of inactivity, set explicitly at login |
| **Refresh** | None. There is no sliding renewal beyond the container's own activity reset, and no refresh token |
| **Remember-me** | **Not implemented** — searched exhaustively, no persistent-login cookie exists |
| **Token auth** | **None.** No `Authorization` header is read anywhere. No JWT library is used for application auth |
| **Multi-device** | Supported implicitly (independent sessions), with no device registry or session listing |
| **Logout** | `GET /auth/logout` → `session.invalidate()` → redirect to `/auth/login`. **GET-only, so it is CSRF-triggerable** (low impact) |

### Session attributes set at login

| Attribute | Type | Purpose |
|---|---|---|
| `userId` | `Long` | Primary identity |
| `role` | `String` | `STUDENT` \| `INSTRUCTOR` \| `ADMIN` |
| `displayName` | `String` | Header greeting; refreshed on profile update |
| `email` | `String` | Display |
| `instructorVerificationStatus` | `String` | Instructors only. **Advisory — `AuthFilter` re-reads the live value from the database on every request rather than trusting this** |

### JWT — where it *is* used

The codebase does generate JWTs, but **never for application authentication**:

1. **Zoom Meeting SDK signature** — HS256, signed with `ZOOM_MEETING_SDK_SECRET`, minted per request at `/student/api/zoomMeetingSignature` and `/instructor/api/zoomMeetingSignature`. Claims: `appKey`, `sdkKey`, `mn` (meeting number), `role` (0 student / 1 host), `iat = now−30s`, `exp = iat+7200`, `tokenExp`, `video_webrtc_mode=1`. Consumed only by the Zoom Web SDK.
2. **Zoom Server-to-Server OAuth** — the access token obtained from Zoom, cached in memory with a 60-second safety margin.

Neither is ever accepted as a credential by e-Tasmi itself.

---

## 2. Password Handling

| Aspect | Implementation |
|---|---|
| **Primary algorithm** | **BCrypt, cost factor 12** |
| **Legacy support** | PBKDF2 hashes still verify. **They are not upgraded to BCrypt on successful login** — a legacy user keeps a weaker hash forever |
| **Storage** | `user.password_hash VARCHAR(255)` |
| **Strength rule** | ≥8 characters with at least one uppercase, one lowercase, and one digit. Applied at registration, reset, and profile change |
| **Discrepancy** | `admin/user_form.jsp` sets `minlength="6"`, weaker than the server's 8. The server wins; the form just produces a confusing error |
| **Change flow** | Requires the current password to be verified first |
| **History / expiry / lockout** | **None.** No password history, no forced rotation, no account lockout after N failures — only IP/email rate limiting |

---

## 3. Registration and Verification Flow

```
   ┌──────────────────────────────────────────────────────────────────┐
   │  GET /auth/register/role   →  choose STUDENT or INSTRUCTOR       │
   └────────────────────────────┬─────────────────────────────────────┘
                                ▼
   ┌──────────────────────────────────────────────────────────────────┐
   │  POST /auth/register  (multipart)                                │
   │  • rate limit 10 / 10 min / IP                                   │
   │  • validate name, email, phone, password strength, role          │
   │  • reject role = ADMIN                                           │
   │  • BCrypt(12) the password                                       │
   │  ┌──────────────── ONE TRANSACTION ────────────────┐             │
   │  │ INSERT user   (status=INACTIVE, verified=0)      │             │
   │  │ INSERT student  OR  instructor (PENDING)         │             │
   │  │ INSERT notifications (welcome)                   │             │
   │  └──────────────────────────────────────────────────┘            │
   │  • generate 6-digit code  → SHA-256(userId + ":" + code), 10 min │
   │  • generate link token    → SHA-256, 24 h                        │
   │  • send email: Brevo → Resend → SMTP (first configured wins)     │
   └────────────────────────────┬─────────────────────────────────────┘
                                ▼
        ┌───────────────────────┴────────────────────────┐
        ▼                                                ▼
  POST /auth/verify-email                        GET /auth/verify-email?token=
  (email + 6-digit code)                         (24-hour link)
  max 5 attempts, 10-min TTL
        └───────────────────────┬────────────────────────┘
                                ▼
              user.email_verified = 1, email_verified_at = now
              DELETE FROM email_verification WHERE user_id = ?
                                │
              ┌─────────────────┴──────────────────┐
              ▼                                    ▼
        role = STUDENT                       role = INSTRUCTOR
        status → ACTIVE                      status → ACTIVE only if
        can log in immediately               verification_status = APPROVED,
                                             otherwise stays INACTIVE
                                                     │
                                                     ▼
                                        Admin approves at
                                        /admin/instructors/verification
                                        (requires email_verified = true)
                                                     │
                                                     ▼
                                        verification_status = APPROVED
                                        user.status = ACTIVE
```

---

## 4. Login Flow

```
   POST /auth/login  { email, password }
        │
        ├─ rate limit: 5 failures / 5 min per {ip}:{email}
        │    (skipped on localhost or ETASMI_DISABLE_RATE_LIMIT=true)
        │
        ├─ SELECT ... FROM user WHERE email = ? AND status <> 'DELETED'
        │       ↓ not found → "Invalid email or password."
        │
        ├─ verify password (BCrypt, or legacy PBKDF2)
        │       ↓ mismatch → "Invalid email or password."   ← same message
        │
        ├─ is_active = 0 ? → "Your account has been deactivated…"
        │
        ├─ email_verified = 0 ? → verification-required message
        │
        ├─ role = INSTRUCTOR → SELECT verification_status FROM instructor
        │       (stored in session as advisory only)
        │
        ├─ session.invalidate()  ← session-fixation defence
        ├─ request.getSession(true)
        ├─ set userId, role, displayName, email [, instructorVerificationStatus]
        ├─ setMaxInactiveInterval(900)
        └─ clear the rate-limit counter
             │
             ├─ ADMIN → 302 /admin/dashboard
             └─ else  → 302 /dashboard  (DashboardRouterServlet re-routes by role)

   AJAX variant (X-Requested-With: XMLHttpRequest or Accept: application/json)
        → 200 {"success":true,"redirect":"…"}
```

---

## 5. Authorization — `AuthFilter`

A single filter mapped to `/*` performs all authorization. It runs after `CharacterEncodingFilter` and `UrlLocalizationFilter`, so it always sees a locale-stripped path.

### Decision order

```
1.  Is the path public?  ──yes──▶ chain.doFilter()   (no session required)
2.  Is there a session with userId?  ──no──▶ 401 JSON  or  302 /auth/login?expired=1
3.  Does the path prefix match the session role?
        /admin/*      requires ADMIN
        /instructor/* requires INSTRUCTOR
        /student/*    requires STUDENT
    ──no──▶ 403 JSON  or  302 /dashboard?forbidden=1
4.  Is the path /instructor/* ?
        re-read instructor.verification_status from the DATABASE
        ──not APPROVED──▶ forward to pendingApproval.jsp
5.  chain.doFilter()
```

### Public paths (no session required)

`/home` · `/auth/login` · `/auth/register` · `/auth/register/role` · `/auth/logout` · `/auth/verify-email` · `/auth/verify-status` · `/auth/resend-verification` · `/auth/forgot-password` · `/auth/reset-password` · `/uploads/*` · static assets (`/css/*`, `/js/*`, `/images/*`, `/assets/*`) · `/favicon.ico`

**`/uploads/*` being public is the single most consequential authorization decision in the system.** Payment receipts, recitation audio, profile photos, and session banners are all readable by any unauthenticated party who has the URL. Cloudinary-hosted assets are equally public — only qualification PDFs use signed URLs.

### Response shape

| Condition | HTML request | JSON request |
|---|---|---|
| No session | `302 /auth/login?expired=1` | `401 {"ok":false,"error":"unauthenticated","message":"Login required."}` |
| Wrong role | `302 /dashboard?forbidden=1` | `403 {"ok":false,"error":"forbidden","message":"You do not have access to this resource."}` |

A request is treated as JSON when the path starts with `/student/api/` or `/instructor/api/`, or when it carries `X-Requested-With: XMLHttpRequest` or `Accept: application/json`.

### Instructor approval gate

The filter re-queries `instructor.verification_status` **on every `/instructor/*` request** rather than trusting the session copy. The practical effect: an admin who rejects an instructor mid-session locks them out on their very next page load. This is a deliberate, correct design choice and must be preserved — a mobile/PHP rewrite that caches approval in a JWT would silently weaken it.

### Second-layer ownership checks

`AuthFilter` proves *role*, never *ownership*. Each servlet/service enforces its own resource ownership:

| Resource | Ownership check |
|---|---|
| Recitation audio | `recitation → enrollment.student_id = current student` |
| Evaluation | `recitation → enrollment → tasmi_session.instructor_id = current instructor` |
| Payment verification | `payment → enrollment → tasmi_session.instructor_id = current instructor` |
| Live session host | `tasmi_session.instructor_id = current instructor` |
| Live session join | enrollment `APPROVED` + payment `APPROVED` when fee > 0 |
| Session edit/delete | `tasmi_session.instructor_id = current instructor` |
| Materials | instructor user → `instructor_id` → `session.instructor_id` |
| Payment receipt PDF | `payment → enrollment.student_id = current student` |

No IDOR vulnerability was found in any of these paths.

---

## 6. Permission Matrix

**Legend:** ✅ full · 🔸 own records only · ❌ none · — not applicable

### Account and identity

| Function | Student | Instructor | Admin |
|---|---|---|---|
| Self-register | ✅ | ✅ | ❌ (admin-created only) |
| Log in | ✅ | ✅ (any status) | ✅ |
| Verify email | ✅ | ✅ | — (auto-verified) |
| Reset password | ✅ | ✅ | ✅ |
| Change own password | ✅ | ✅ | ✅ |
| Edit own name/phone | ✅ | ✅ | ✅ |
| Change own email | ❌ | ❌ | ❌ |
| Upload own photo | ✅ | ✅ | ❌ |
| Edit own qualification | ❌ | ✅ | ❌ |
| Delete own account | ❌ | ❌ | ❌ |

### User administration

| Function | Student | Instructor | Admin |
|---|---|---|---|
| List all users | ❌ | ❌ | ✅ |
| Create a user (any role) | ❌ | ❌ | ✅ |
| Edit another user | ❌ | ❌ | ✅ |
| Change a user's role | ❌ | ❌ | ❌ (blocked for everyone) |
| Activate / deactivate | ❌ | ❌ | ✅ (not self, not last admin) |
| Soft-delete a user | ❌ | ❌ | ✅ (not self, not last admin) |
| View audit logs | ❌ | ❌ | ✅ |

### Instructor verification

| Function | Student | Instructor | Admin |
|---|---|---|---|
| Submit qualification | — | ✅ (at registration) | — |
| View pending instructors | ❌ | ❌ | ✅ |
| Download qualification PDF | ❌ | ❌ | ✅ (signed 300 s URL) |
| Approve / reject | ❌ | ❌ | ✅ |

### Sessions

| Function | Student | Instructor | Admin |
|---|---|---|---|
| Create a session | ❌ | ✅ (APPROVED only) | ❌ |
| Edit a session | ❌ | 🔸 (SCHEDULED only) | ❌ |
| Delete a session | ❌ | 🔸 | ❌ |
| Start / complete a session | ❌ | 🔸 | ❌ |
| Browse sessions | ✅ (level-filtered) | ❌ | ❌ |
| View session details | ✅ | 🔸 | ❌ (reports only) |
| Host a live session | ❌ | 🔸 | ❌ |
| Join a live session | ✅ (if approved & paid) | ❌ | ❌ |

**Note:** admins have **no session management capability whatsoever** — they can only read aggregate session data through reports.

### Enrollment and payment

| Function | Student | Instructor | Admin |
|---|---|---|---|
| Enroll in a session | ✅ | ❌ | ❌ |
| View own enrollments | ✅ | — | — |
| View session participants | ❌ | 🔸 | ❌ |
| Withdraw an enrollment | ✅ (API only, no UI) | ❌ | ❌ |
| Upload a payment receipt | 🔸 | ❌ | ❌ |
| **Approve / reject a payment** | ❌ | 🔸 | **❌** |
| Configure payment details (QR/bank) | ❌ | ✅ | ❌ |
| View all payments | ❌ | 🔸 (own sessions) | ✅ (read-only) |
| Download a payment receipt | 🔸 (approved only) | ❌ | ❌ |
| Issue a refund | ❌ | ❌ | ❌ (not implemented) |

**The most important row in this matrix:** payment verification is an **instructor-only** capability. The admin can see every transaction but cannot approve, reject, or reverse a single one.

### Recitation and evaluation

| Function | Student | Instructor | Admin |
|---|---|---|---|
| Submit a recitation | ✅ | ❌ | ❌ |
| Play own recitation | 🔸 | — | ❌ |
| Play a student's recitation | ❌ | 🔸 | ❌ |
| Delete a recitation | ❌ | ❌ | ❌ |
| Run AI analysis | ❌ | 🔸 | ❌ |
| Save an evaluation | ❌ | 🔸 (one-shot) | ❌ |
| Edit an evaluation | ❌ | ❌ | ❌ |
| View own results | ✅ | — | ❌ |
| Mark a session reviewed / reopen | ❌ | 🔸 | ❌ |
| View student progress | 🔸 (own) | 🔸 (own students) | ❌ |

### AI, Quran, and media

| Function | Student | Instructor | Admin |
|---|---|---|---|
| Quran Assistant chat | ✅ | ❌ | ❌ |
| Voice assistant (STT + TTS) | ✅ | ❌ | ❌ |
| Quran Library browse/search | ✅ | ❌ | ❌ |
| AI verse translation | ✅ | ❌ | ❌ |
| Chapter audio / reciters | ✅ | ❌ | ❌ |
| Upload teaching materials | ❌ | ❌ (no UI) | ❌ |
| Open teaching materials | ❌ | 🔸 | ❌ |

**Every AI feature is student-exclusive on the student side except recitation analysis, which is instructor-exclusive.** Admins have no AI access at all.

### Notifications and reporting

| Function | Student | Instructor | Admin |
|---|---|---|---|
| View own notifications | ✅ | ✅ | ✅ |
| Mark notifications read | ❌ | ❌ | ❌ (no such feature) |
| Send a notification manually | ❌ | ❌ | ❌ (system-generated only) |
| Generate reports | ❌ | ❌ | ✅ |
| Export CSV / PDF | ❌ | ❌ | ✅ |
| View platform analytics | ❌ | 🔸 (own cockpit) | ✅ |

---

## 7. Security Controls Present

| Control | Status | Detail |
|---|---|---|
| Password hashing | ✅ Strong | BCrypt cost 12 |
| SQL injection defence | ✅ Strong | `PreparedStatement` everywhere; dynamic SQL concatenates only constant column names |
| Session fixation | ✅ Handled | Session invalidated and recreated at login |
| Rate limiting | ✅ Partial | Login, registration, forgot-password, resend. **Not applied to AI endpoints, uploads, or any other route** |
| Account enumeration | ✅ Mostly | Generic messages everywhere — **except `/auth/verify-status`, which leaks it outright** |
| Path traversal | ✅ Handled | `..`/`\` stripped and containment-verified on every local file serve |
| File type validation | ✅ Good | `ImageIO` decode for images; extension + MIME checks elsewhere |
| Secret exposure | ✅ Good | No secret reaches the client. Zoom SDK signatures are minted server-side; OpenAI and Cloudinary are called only from the server |
| Ownership checks | ✅ Good | Enforced per resource in the service layer; no IDOR found |
| Output encoding | ✅ Mostly | JSTL `<c:out>` and `fn:escapeXml` in JSPs; tafsir HTML is sanitized through an allowlist |

## 8. Security Gaps

| # | Gap | Severity | Detail |
|---|---|---|---|
| S-1 | **No CSRF protection anywhere** | **HIGH** | No token, no `SameSite` cookie attribute, no origin check. Every state-changing POST — payment approval, user deletion, instructor rejection — is forgeable |
| S-2 | **`/uploads/*` is unauthenticated** | **HIGH** | Payment receipts and recitation audio are publicly readable by URL |
| S-3 | **Cookie flags unset** | **MEDIUM** | No `Secure`, `HttpOnly`, or `SameSite` configured in `web.xml` |
| S-4 | **Email enumeration via `/auth/verify-status`** | MEDIUM | Unauthenticated `{"known":true}` oracle |
| S-5 | **No security headers** | MEDIUM | No CSP, HSTS, `X-Frame-Options`, or `Referrer-Policy`. `X-Content-Type-Options` is set on exactly two file-serving endpoints |
| S-6 | **`zoom_start_url` stored in plaintext** | MEDIUM | Grants host rights to anyone who reads the row |
| S-7 | **Bootstrap admin password reset on every boot** | MEDIUM | When `ETASMI_BOOTSTRAP_ADMIN_PASSWORD` is set, `DBSeeder` resets that admin's password on **every** application start |
| S-8 | **Default seeded admin credentials** | MEDIUM | `admin@etasmi.com` / `Admin123!` is created when no admin exists |
| S-9 | **`useSSL=false` to MySQL** | MEDIUM | The Compose JDBC URL disables TLS to the database |
| S-10 | **No account lockout** | LOW | Only rate limiting; a distributed attack across IPs is unthrottled |
| S-11 | **Legacy PBKDF2 hashes never upgraded** | LOW | A pre-BCrypt user keeps the weaker hash indefinitely |
| S-12 | **Logout is GET-only** | LOW | CSRF-triggerable logout |
| S-13 | **Audit coverage is thin** | LOW | Only 7 actions logged; authentication events and financial actions are not audited |

---

## 9. What This Means for the Mobile Migration

The current authentication model **cannot be reused by a mobile client as-is**. Cookie-based sessions with a 15-minute idle timeout and no refresh path would force a Flutter user to re-enter their password several times a day.

The PHP backend will need a token layer, and it must reproduce these behaviors exactly:

1. **Preserve BCrypt cost 12.** PHP's `password_hash($p, PASSWORD_BCRYPT, ['cost' => 12])` produces `$2y$` hashes that verify against the Java `$2a$` hashes. Existing passwords keep working with no reset campaign.
2. **Keep the legacy PBKDF2 verification path**, or accept that pre-BCrypt users must reset their passwords.
3. **Re-read `verification_status` on every instructor request.** Do not embed approval in a token, or a rejected instructor keeps access until the token expires.
4. **Preserve the exact login preconditions:** `is_active = 1`, `status <> 'DELETED'`, `email_verified = 1`, plus the instructor approval gate as an *authorization* step rather than an authentication one.
5. **Keep the generic error messages** and close the `/auth/verify-status` enumeration hole rather than porting it.
6. **Authenticate `/uploads/*`.** Signed, expiring URLs for receipts and recitation audio — the pattern already used for qualification PDFs — is the model to follow.
7. **Add CSRF protection** for any retained browser surface. A pure token-authenticated mobile API is not CSRF-exposed, but the existing web app remains.
8. **Preserve the permission matrix verbatim**, including the counter-intuitive rule that admins cannot verify payments.
