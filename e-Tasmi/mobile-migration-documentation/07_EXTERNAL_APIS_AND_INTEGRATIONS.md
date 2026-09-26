# 07 — EXTERNAL APIS AND INTEGRATIONS

Five external services are integrated. All calls are made **server-side only** using `java.net.HttpURLConnection` — there is no third-party HTTP client library, and no external service is called directly from the browser except the Zoom Web SDK and the Quran CDN audio files.

| Service | Purpose | Auth | Configured via |
|---|---|---|---|
| **Zoom** | Meeting creation + embedded live sessions | Server-to-Server OAuth + Meeting SDK JWT | 5 env vars |
| **Cloudinary** | Media storage (images, audio, PDFs) | Signed upload (API key + secret) | 3 env vars |
| **OpenAI** | Quran Assistant, voice, recitation analysis, translation | Bearer API key | 6 env vars |
| **Quran Foundation Content API** | Chapters, verses, translations, tafsir, audio | OAuth2 client credentials | 4 env vars |
| **Email (Brevo / Resend / SMTP)** | Verification + password reset only | API key or SMTP auth | 8 env vars |

**There is no payment gateway.** ToyyibPay, Stripe, Billplz, PayPal, and Razorpay appear nowhere in the codebase — verified by exhaustive search. See §5.

---

## 1. ZOOM INTEGRATION

Zoom is used in **two distinct ways** with **two distinct credential sets** that must not be confused.

| | Server-to-Server OAuth | Meeting SDK |
|---|---|---|
| **Purpose** | Create / update / delete meetings from the backend | Embed the live meeting in a web page |
| **Credentials** | `ZOOM_ACCOUNT_ID`, `ZOOM_CLIENT_ID`, `ZOOM_CLIENT_SECRET` | `ZOOM_MEETING_SDK_KEY`, `ZOOM_MEETING_SDK_SECRET` |
| **Token** | OAuth access token from Zoom | JWT minted locally by e-Tasmi |
| **Called by** | `ZoomMeetingService` (server) | Browser, using a signature fetched from our API |

### 1.1 Server-to-Server OAuth

```
POST https://zoom.us/oauth/token?grant_type=account_credentials&account_id={ZOOM_ACCOUNT_ID}
Authorization: Basic base64(ZOOM_CLIENT_ID + ":" + ZOOM_CLIENT_SECRET)
```

The access token is **cached in memory** with a 60-second safety margin before expiry. The cache is per-JVM and lost on restart.

### 1.2 Meeting lifecycle

| Operation | Zoom API call | Triggered by |
|---|---|---|
| **Create** | `POST /v2/users/{hostEmail}/meetings` | Instructor creates a session |
| **Update** | `PATCH /v2/meetings/{meetingId}` | Instructor edits a `SCHEDULED` session |
| **Delete** | `DELETE /v2/meetings/{meetingId}` | Instructor deletes a session (best-effort; failure is swallowed) |
| **ZAK token** | `GET /v2/users/{hostEmail}/token?type=zak` | Instructor requests a host SDK signature |

**Create request body:**
```json
{
  "topic": "<session title>",
  "type": 2,
  "start_time": "<ISO-8601 in APP_ZONE>",
  "duration": <minutes>,
  "timezone": "Asia/Kuala_Lumpur",
  "agenda": "<description>",
  "settings": {
    "join_before_host": false,
    "waiting_room": false,
    "approval_type": 2,
    "auto_recording": "none",
    "host_video": true,
    "participant_video": false,
    "mute_upon_entry": true
  }
}
```

**`auto_recording` is hard-coded to `"none"`.** This is why `tasmi_session.recording_url`, `recording_status`, and `recording_synced_at` are permanently null despite existing in the schema. No recording feature exists.

**Response fields persisted:**

| Zoom field | Database column | Sensitivity |
|---|---|---|
| `id` | `tasmi_session.zoom_meeting_id` | Low |
| `join_url` | `tasmi_session.meeting_link` | Medium |
| `password` | `tasmi_session.meeting_password` | Medium |
| `start_url` | `tasmi_session.zoom_start_url` | **HIGH — grants host rights, stored in plaintext** |

`live_provider` is set to `'ZOOM'`. The value `'MANUAL'` is accepted by the join validator but **no code path writes it**, so it is effectively dead.

### 1.3 Host email resolution

`instructor.zoom_email` → `ZOOM_DEFAULT_HOST_EMAIL`. If neither is available, session creation fails with *"No Zoom host email available…"*. **The Zoom account must have a licensed user matching that email.**

### 1.4 Meeting SDK signature

`GET|POST /student/api/zoomMeetingSignature` and `/instructor/api/zoomMeetingSignature` mint an HS256 JWT signed with `ZOOM_MEETING_SDK_SECRET`:

```json
{
  "appKey": "<ZOOM_MEETING_SDK_KEY>",
  "sdkKey": "<ZOOM_MEETING_SDK_KEY>",
  "mn": "<meeting number>",
  "role": 0,
  "iat": <now - 30>,
  "exp": <iat + 7200>,
  "tokenExp": <iat + 7200>,
  "video_webrtc_mode": 1
}
```

`role = 0` for students, `role = 1` for the instructor. The instructor response additionally carries a **`zak`** token, which is what allows the Web SDK to *start* rather than merely join the meeting. **The SDK secret never leaves the server** — this is correct and must be preserved.

### 1.5 Join workflows

```
STUDENT                                    INSTRUCTOR
  │                                           │
  ▼                                           ▼
GET /student/joinLive?sessionId=X        GET /instructor/live-session?sessionId=X
  │                                           │
  ├─ validateStudentJoin()                    ├─ verify session.instructor_id == me
  │   • enrolled?                             │
  │   • enrollment APPROVED?                  │
  │   • payment APPROVED (if fee > 0)?        │
  │   • join URL present & valid http(s)?     │
  │                                           │
  ├─ mark attendance PRESENT (upsert)         │
  │                                           │
  ├── embed ──▶ /student/live/embed           ├── embed ──▶ live-embed.jsp
  │             live-embed.jsp                │             (role 1 + zak)
  │             → GET signature (role 0)      │
  │             → Zoom Web SDK joins          │
  │                                           │
  └── external=1 ──▶ 302 meeting_link         └── external=1 ──▶ 302 zoom_start_url
```

### 1.6 Environment variables

| Variable | Purpose | Required | Mobile exposure |
|---|---|---|---|
| `ZOOM_ACCOUNT_ID` | S2S OAuth account | Yes (for sessions) | **NO** |
| `ZOOM_CLIENT_ID` | S2S OAuth client | Yes | **NO** |
| `ZOOM_CLIENT_SECRET` | S2S OAuth secret | Yes | **NO** |
| `ZOOM_MEETING_SDK_KEY` | SDK app key | Yes (for embed) | Yes — public by design |
| `ZOOM_MEETING_SDK_SECRET` | SDK signing secret | Yes | **NO — never** |
| `ZOOM_DEFAULT_HOST_EMAIL` | Fallback host | Recommended | **NO** |

### 1.7 Mobile migration notes

- The Zoom **Web** SDK does not apply to Flutter. The mobile app needs the **Zoom Meeting SDK for Android/iOS**, or must open the `join_url` in an external browser/Zoom app.
- The signature endpoint transfers to PHP essentially unchanged (`firebase/php-jwt`, HS256). The claim set must match exactly.
- `zoom_start_url` must never be sent to a student client.
- Recording is off. If the mobile product wants recordings, that is a **new feature** requiring `auto_recording` changes plus new columns — out of scope for this phase.

---

## 2. CLOUDINARY / FILE STORAGE

### 2.1 Dual-mode storage

`FileStorageService` decides per upload at runtime:

```
Are CLOUDINARY_CLOUD_NAME + CLOUDINARY_API_KEY + CLOUDINARY_API_SECRET all present?
   ├── YES → upload to Cloudinary, store the returned secure_url
   └── NO  → write to ETASMI_UPLOADS_DIR, store "/uploads/{folder}/{filename}"
```

**Both forms coexist in the same column.** Any consumer must handle an absolute `https://res.cloudinary.com/...` URL *and* a relative `/uploads/...` path. This is a real migration hazard — the PHP backend must replicate the same branching.

### 2.2 Upload signature

Unsigned upload presets are **not** used. Each upload is signed server-side:

```
signature = SHA1( sorted_params + CLOUDINARY_API_SECRET )
POST https://api.cloudinary.com/v1_1/{cloud_name}/{resource_type}/upload
```

`resource_type` selection:

| Content | resource_type | Reason |
|---|---|---|
| Images (photos, QR, banners, image receipts) | `image` | Standard |
| Recitation audio | **`video`** | Cloudinary handles audio through the video pipeline |
| PDFs (qualifications, PDF receipts) | `raw` | Non-media binary |

### 2.3 What is uploaded

| Asset | Folder | Who | Limit | Types | DB column |
|---|---|---|---|---|---|
| Profile photo | `profile/` | Student, Instructor | 2 MB | JPG, PNG, WEBP | `user.profile_image_url` |
| Qualification PDF | `qualifications/` | Instructor (registration) | 10 MB | PDF | `instructor.qualification_file` |
| Payment QR | `payment-qr/` | Instructor | 8 MB | JPG, PNG, WEBP | `instructor_payment_settings.qr_image_url` |
| Payment receipt | `receipts/` | Student | 10 MB | JPG, PNG, WEBP, GIF, PDF | `payment.receipt_file_path` |
| Recitation audio | `recitations/` | Student | **200 MB** | webm, mp3, wav, m4a, ogg, aac, mp4, mov, mpeg, mpga | `recitation.audio_file_path` |
| Session banner | `session-banners/` | Instructor | 3 MB | JPG, PNG, WEBP | `tasmi_session.banner_image_url` |
| Teaching material | `materials/` | — | — | — | `session_material.file_path` (**no upload path exists**) |

### 2.4 Validation

Images are validated by actually decoding them with `javax.imageio.ImageIO.read()` — a failed decode rejects the file. This defeats extension-spoofing. Non-image types are checked by extension and declared MIME.

### 2.5 Access control

| Asset | How it is served | Protected? |
|---|---|---|
| Qualification PDF | `/admin/instructors/qualification/download` → **signed 300-second Cloudinary URL** | ✅ Yes — the only signed-URL use in the system |
| Everything else (Cloudinary) | Raw `secure_url` stored in the DB and emitted in HTML | ❌ **Public to anyone with the URL** |
| Everything else (local) | `GET /uploads/*` — an unauthenticated servlet | ❌ **Public** |

Local file serving does strip `..` and `\` and verify containment under the uploads root, so there is no traversal vulnerability — but there is also no authentication.

### 2.6 Delete behavior

**No Cloudinary `destroy` call exists anywhere in the codebase.** Replacing a profile photo, QR image, or banner overwrites the database column and orphans the previous asset permanently. Local files are likewise never deleted. Storage grows monotonically.

### 2.7 Environment variables

| Variable | Required | Mobile exposure |
|---|---|---|
| `CLOUDINARY_CLOUD_NAME` | For cloud mode | Yes (public identifier) |
| `CLOUDINARY_API_KEY` | For cloud mode | **NO** |
| `CLOUDINARY_API_SECRET` | For cloud mode | **NO — never** |
| `ETASMI_UPLOADS_DIR` | For local mode | **NO** |

### 2.8 Mobile migration notes

- **Local storage mode cannot survive on Railway** — the container filesystem is ephemeral, so any file written locally is lost on redeploy. Cloudinary must be mandatory in production.
- The 200 MB recitation limit is unrealistic over mobile data. Consider client-side compression or chunked upload — but note that changing the limit is a *configuration* decision, not a business-rule change.
- Signed, expiring URLs should be extended to receipts and recitation audio.
- `recitation.audio_file_path` is `VARCHAR(255)`; long Cloudinary URLs risk truncation.

---

## 3. OPENAI INTEGRATION

Fully documented in **doc 08**. Summary here for completeness.

| Feature | Endpoint | Model variable | Default |
|---|---|---|---|
| Quran Assistant chat | `POST /v1/chat/completions` | `OPENAI_ASSISTANT_MODEL` | `gpt-4o-mini` |
| Voice transcription | `POST /v1/audio/transcriptions` | `OPENAI_VOICE_STT_MODEL` | `gpt-4o-transcribe` |
| Text-to-speech | `POST /v1/audio/speech` | `OPENAI_VOICE_TTS_MODEL` | `gpt-4o-mini-tts` |
| TTS voice | — | `OPENAI_VOICE_NAME` | `alloy` |
| Recitation transcription | `POST /v1/audio/transcriptions` | `OPENAI_RECITATION_MODEL` | `gpt-4o-transcribe` |
| Recitation evaluation | `POST /v1/chat/completions` | `OPENAI_EVALUATOR_MODEL` (legacy fallback `OPENAI_CLASSIFIER_MODEL`) | **`gpt-4o`** |
| Verse translation | `POST /v1/chat/completions` | `OPENAI_TRANSLATION_MODEL` | `gpt-4o-mini` |

Auth: `Authorization: Bearer {OPENAI_API_KEY}`. **Server-side only.** There is no organization-ID variable.

Note that recitation evaluation is the one feature that defaults to the **full `gpt-4o`** rather than `gpt-4o-mini` — it is the most expensive call in the system and the most quality-sensitive.

| Variable | Required | Mobile exposure |
|---|---|---|
| `OPENAI_API_KEY` | For all AI features | **NO — never** |
| `OPENAI_ASSISTANT_MODEL` | Optional | Safe |
| `OPENAI_VOICE_STT_MODEL` | Optional | Safe |
| `OPENAI_VOICE_TTS_MODEL` | Optional | Safe |
| `OPENAI_VOICE_NAME` | Optional | Safe |
| `OPENAI_RECITATION_MODEL` | Optional | Safe |
| `OPENAI_EVALUATOR_MODEL` | Optional | Safe |
| `OPENAI_CLASSIFIER_MODEL` | Optional (legacy alias) | Safe |
| `OPENAI_TRANSLATION_MODEL` | Optional | Safe |

**No cost controls exist:** no per-user quota, no spend cap, no request budget. Any authenticated student can issue unlimited assistant, voice, and translation calls. This is a genuine financial risk on mobile, where usage typically increases.

---

## 4. QURAN FOUNDATION CONTENT API

### 4.1 OAuth2 client credentials

```
POST {authBase}/oauth2/token
Authorization: Basic base64(QF_CLIENT_ID + ":" + QF_CLIENT_SECRET)
grant_type=client_credentials&scope=content
```

`authBase` resolution: `QF_AUTH_ENDPOINT` when set, otherwise inferred from `QF_ENV`:

| `QF_ENV` | OAuth base |
|---|---|
| `prelive` | `https://prelive-oauth2.quran.foundation` |
| `production`, `production_test`, `production_live` | `https://oauth2.quran.foundation` |

If `QF_AUTH_ENDPOINT` is unset and `QF_ENV` is unrecognisable, configuration validation fails with *"QF_ENV must be recognizable… or set QF_AUTH_ENDPOINT."*

Scope defaults to `content`. `QF_OAUTH_SCOPE` can widen it (e.g. `content search`) but **only when the Search API is actually provisioned** — an over-broad scope makes the OAuth server return `400 invalid_scope`.

The token is cached in memory with a safety margin. On upstream `401`, the cached token is cleared and the request retried once.

### 4.2 Endpoints consumed

Base: `QF_API_ENDPOINT` (must be the Content API host, e.g. `.../content/api/v4`). Every request carries `x-auth-token` and `x-client-id`.

The config validator explicitly detects the common mistake of pasting the OAuth origin into `QF_API_ENDPOINT` and reports the pseudo-variable `QF_API_ENDPOINT_USE_CONTENT_APIS_HOST`.

Verse requests pin an explicit field list — `id, chapter_id, verse_number, verse_key, verse_index, page_number, juz_number, hizb_number, rub_el_hizb_number, text_uthmani_tajweed, code_v1` — plus word fields `text_uthmani, text_uthmani_simple, text_imlaei, text_imlaei_simple, code_v1, code_v2`.

| e-Tasmi endpoint | Upstream |
|---|---|
| `/chapters` | `GET /chapters` |
| `/verses/by-chapter` | `GET /verses/by_chapter/{n}` |
| `/verses/by-key` | `GET /verses/by_key/{key}` |
| `/search` | `GET /search` |
| `/resources/translations` | `GET /resources/translations` |
| `/resources/tafsirs` | `GET /resources/tafsirs` |
| `/audio/chapter-reciters` | `GET /resources/chapter_reciters` |
| `/audio/chapter-file` | `GET /chapter_recitations/{reciter}/{chapter}` |
| `/reciter-portrait` | Image proxy over the allowed CDN hosts |

### 4.3 Fallback

`QuranBundledCatalog` ships a bundled 114-surah catalog loaded at startup by `SchemaBootstrap`. When Quran Foundation is unconfigured or unreachable, `/chapters` still returns the full surah list. **Verses, tafsir, search, and audio have no fallback** and fail with an upstream error code.

### 4.4 Safety measures

- Response bodies capped at 400 KB (search, reciters).
- Tafsir HTML passed through an **allowlist sanitizer** before reaching the browser.
- The reciter-portrait proxy restricts hosts, caps at 2 MB, and validates content-type by magic bytes.
- Upstream failures are mapped to stable codes: `not_configured`, `oauth_token_failed`, `upstream_forbidden`, `upstream_not_found`, `upstream_unauthorized`, `upstream_error`, `load_failed`.

### 4.5 Environment variables

| Variable | Required | Mobile exposure |
|---|---|---|
| `QF_CLIENT_ID` | **Yes** — for anything beyond the bundled chapter list | **NO** |
| `QF_CLIENT_SECRET` | **Yes** | **NO — never** |
| `QF_API_ENDPOINT` | **Yes** — Content API base, HTTPS | Safe |
| `QF_ENV` | Yes unless `QF_AUTH_ENDPOINT` is set | Safe |
| `QF_AUTH_ENDPOINT` | Optional override | Safe |
| `QF_OAUTH_SCOPE` | Optional | Safe |
| `QF_SEARCH_API_BASE` | Optional — required when the Search host differs | Safe |

### 4.6 Mobile migration notes

- Chapter audio URLs point at the upstream CDN and are loaded **directly by the client**, not proxied. Flutter can stream them the same way.
- The bundled catalog fallback should be reproduced so the surah list works offline.
- Entitlements matter: a `403` from upstream usually means the credentials lack the requested resource scope, not that the code is wrong.

---

## 5. PAYMENT — NO GATEWAY EXISTS

**This is the single most important correction to the original project brief.**

The brief assumed a ToyyibPay integration. There is none. Exhaustive search across all `.java`, `.jsp`, `.js`, `.xml`, `.sql`, and configuration files found **zero occurrences** of ToyyibPay, Stripe, Billplz, PayPal, Razorpay, Senangpay, iPay88, or any other payment gateway — no SDK, no API client, no callback endpoint, no webhook handler, no transaction ID column, no merchant credentials, and no gateway environment variable.

### 5.1 The actual payment flow

```
  STUDENT                          SYSTEM                        INSTRUCTOR
     │                                │                               │
     ├─ enroll in a paid session ────▶│                               │
     │                                ├─ enrollment = PENDING         │
     │                                ├─ payment    = PENDING         │
     │                                │  amount ← session.fee         │
     │                                │                               │
     ├─ GET /student/payments/qr ────▶│                               │
     │◀─ instructor's QR / bank ──────┤  from instructor_payment_      │
     │   details displayed            │  settings (must be usable)    │
     │                                │                               │
     ├─ pay OUTSIDE the system ───────┼──── bank app / DuitNow ──────▶│
     │   (no software involvement)    │                               │
     │                                │                               │
     ├─ upload receipt image/PDF ────▶│                               │
     │                                ├─ payment → AWAITING_          │
     │                                │            VERIFICATION       │
     │                                ├─ history: SUBMITTED           │
     │                                ├─ notify both parties ────────▶│
     │                                │                               │
     │                                │◀── reviews receipt manually ──┤
     │                                │                               │
     │                    ┌───────────┴───────────┐                   │
     │                    ▼                       ▼                   │
     │              APPROVE                   REJECT (reason required)│
     │        payment  → APPROVED        payment    → REJECTED        │
     │        payment_date = now         enrollment → PENDING         │
     │        enrollment → APPROVED      history: REJECTED            │
     │        history: APPROVED          notify student               │
     │        notify student                     │                    │
     │                    │                      │                    │
     │◀── access granted ─┘                      └──▶ may resubmit ───┤
```

### 5.2 Consequences

| Question from the brief | Actual answer |
|---|---|
| Sandbox/production mode | **N/A** — no gateway |
| Payment API endpoints | **None** |
| Payment callback / webhook | **None** — verification is a human action |
| Transaction ID | **None** — the only reference is `ETP-{paymentId}`, generated for the receipt PDF |
| Payment verification | **Manual, by the owning instructor** |
| Failed payment behavior | The instructor rejects with a reason; enrollment reverts to `PENDING`; the student may resubmit |
| Cancelled payment behavior | `action=cancel` exists on `POST /student/payments` but **has no UI control** |
| Duplicate payment behavior | Prevented by `uq_payment_enrollment` — one payment row per enrollment. A duplicate *bank transfer* is invisible to the system |
| Refunds | **Not implemented** |
| Payment expiry | **None** — a `PENDING` payment stays pending forever |
| Admin involvement | **Read-only.** `payment.verified_by_admin_id` exists and is never written |

### 5.3 Mobile migration notes

Reproduce this flow exactly. Adding a real gateway would be a **new feature** and is explicitly out of scope. The mobile-specific needs are: camera capture for the receipt, image compression before upload, and a clear status timeline built from `payment_verification_history`.

---

## 6. EMAIL

### 6.1 Transport chain

`EmailService` selects the **first configured** transport:

```
1. Brevo  (POST https://api.brevo.com/v3/smtp/email,  header: api-key)
2. Resend (POST https://api.resend.com/emails,        header: Authorization: Bearer)
3. SMTP   (jakarta.mail, host/port/user/password)
4. none   → logged as a warning; the calling flow continues
```

### 6.2 Emails actually sent

| Email | Trigger | Content |
|---|---|---|
| Verification | Registration | 6-digit code (10 min) **and** a link token (24 h) |
| Resend verification | `POST /auth/resend-verification` | New code |
| Password reset | `POST /auth/forgot-password` | Reset link (30 min) |

**That is the complete list.** There is **no** email for enrollment confirmation, payment submission, payment approval or rejection, session reminders, session changes, evaluation results, or instructor approval/rejection. Everything else is an in-app notification row only.

### 6.3 Failure handling

Email failure never blocks registration — the user is redirected with a `warn=` parameter and can use resend. This is correct behavior and should be preserved.

### 6.4 Environment variables

| Variable | Purpose |
|---|---|
| `BREVO_API_KEY` | Brevo transport |
| `RESEND_API_KEY` | Resend transport |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | SMTP transport |
| `MAIL_FROM`, `MAIL_FROM_NAME` | Sender identity |
| `APP_BASE_URL` | Absolute link construction in email bodies |

`web.xml` also declares SMTP defaults as context parameters. **`APP_BASE_URL` must be correct in production or verification and reset links will point at the wrong host.**

---

## 7. INTEGRATION ARCHITECTURE

### Current

```
   ┌──────────┐
   │ Browser  │
   └────┬─────┘
        │ HTML / fetch()
        ▼
   ┌────────────────────────────────────────────────┐
   │  Tomcat 9  ·  e-Tasmi WAR                      │
   │                                                │
   │  CharacterEncodingFilter                       │
   │        ▼                                       │
   │  UrlLocalizationFilter   (/en /ar /ms)         │
   │        ▼                                       │
   │  AuthFilter              (session + role)      │
   │        ▼                                       │
   │  Servlets (64)  ──▶  Services  ──▶  DAOs       │
   │                          │                     │
   └──────────────────────────┼─────────────────────┘
             │                │
             │                ▼
             │          ┌──────────┐
             │          │  MySQL 8 │
             │          └──────────┘
             │
             │  all outbound calls are SERVER-SIDE
             ├────────▶ Zoom API           (S2S OAuth)
             ├────────▶ Cloudinary         (signed upload)
             ├────────▶ OpenAI             (Bearer key)
             ├────────▶ Quran Foundation   (OAuth2 client credentials)
             └────────▶ Brevo / Resend / SMTP

   Two exceptions where the BROWSER talks outward directly:
      • Zoom Web SDK  (using a signature minted by our server)
      • Quran chapter audio files from the upstream CDN
```

### Target

```
   ┌─────────────────┐
   │  Flutter App    │   ← holds NO third-party secret
   └────────┬────────┘
            │ HTTPS + Bearer token
            ▼
   ┌──────────────────────────────────────────┐
   │  PHP REST API                            │
   │  auth · rate limiting · validation       │
   │  ▼                                       │
   │  Services  ──▶  Repositories             │
   └───────┬──────────────────┬───────────────┘
           │                  ▼
           │            ┌──────────┐
           │            │  MySQL   │  ← SAME SCHEMA
           │            └──────────┘
           │
           ├────────▶ Zoom / Cloudinary / OpenAI / Quran Foundation / Email
           │          (all secrets stay here)
           │
   Flutter may talk directly to:
      • Zoom Meeting SDK for Android/iOS (signature from our API)
      • Quran CDN audio (public URLs)
      • Cloudinary CDN for reads (ideally signed & expiring)
```

**The invariant to preserve:** no third-party secret may ever be embedded in the Flutter binary. A shipped app is fully decompilable; any key inside it is public.

---

## 8. PRODUCTION READINESS AUDIT

| Integration | Status | Mode | Blocking issue for mobile | Required action |
|---|---|---|---|---|
| **MySQL** | ⚠ Functional, not hardened | Production (Railway) | **Unpooled** — the Railway path opens a new `DriverManager` connection per call. `useSSL=false` in Compose | Add pooling in PHP (PDO persistent or an external pooler); enable TLS |
| **Zoom** | ✅ Production-ready | Production S2S OAuth | Web SDK does not work in Flutter | Adopt the native Zoom Mobile SDK or hand off to the Zoom app |
| **Cloudinary** | ✅ Production-ready | Production, signed uploads | Local fallback silently loses files on Railway; no delete → unbounded growth | Make Cloudinary mandatory in production; add `destroy` on replace; sign read URLs |
| **OpenAI** | ⚠ Functional, ungoverned | Production | **No quota, no spend cap, no per-user limit**; results not persisted | Add per-user rate limits and a spend cap; persist AI analysis results |
| **Quran Foundation** | ✅ Production-ready | Production OAuth2 | Verses/tafsir/search/audio have no fallback | Reproduce the bundled-catalog fallback; verify credential entitlements |
| **Email** | ⚠ Depends on config | Production (whichever key is set) | `APP_BASE_URL` must be correct or links break; only 3 email types exist | Verify sender-domain authentication (SPF/DKIM); decide whether payment/enrollment emails are needed |
| **Payment** | ✅ Working as designed | Manual — no gateway | Not a technical risk; a product expectation risk | Confirm with the client that manual verification is intended for mobile |
| **File storage** | ⚠ Insecure | Mixed | `/uploads/*` unauthenticated; receipts publicly readable | Authenticate or sign every media URL |
| **Hosting (Railway)** | ✅ Working | Production | Ephemeral filesystem; no CDN in front; unpooled DB | Cloudinary-only storage; review scaling before mobile launch |
| **Authentication** | ⚠ Not mobile-compatible | Production | Cookie sessions, 15-min idle timeout, no refresh, no CSRF protection | Add a token layer in PHP (see doc 06 §9) |
