# 11 — SECURITY AND CONFIGURATION AUDIT

**No secret value appears anywhere in this document.** Variables are documented by name, purpose, and presence only, exactly as required.

---

## 1. CONFIGURATION INVENTORY

All configuration is read from **environment variables** via `System.getenv()`. There is no properties file, no YAML, and no database-backed settings table. A handful of SMTP defaults are additionally declared as `<context-param>` entries in `web.xml`.

**Legend — Mobile exposure:** `NO` = must stay server-side · `SAFE` = non-secret, may be sent to a client

### 1.1 Database

| Variable | Service | Purpose | Required | Used by | Mobile exposure |
|---|---|---|---|---|---|
| `MYSQLHOST` | MySQL | Railway host | Yes (Railway) | `util.Db`, `RailwayDataSourceFactory` | **NO** |
| `MYSQLPORT` | MySQL | Port (default 3306) | No | same | **NO** |
| `MYSQLDATABASE` | MySQL | Schema name | Yes (Railway) | same | **NO** |
| `MYSQLUSER` | MySQL | Username | Yes (Railway) | same | **NO** |
| `MYSQLPASSWORD` | MySQL | Password | Yes (Railway) | same | **NO** |
| `MYSQL_URL` | MySQL | Combined `mysql://` URL, strategy 2 | No | `util.Db` | **NO** |
| `ETASMI_JDBC_URL` | MySQL | Explicit JDBC URL, strategy 3 | No (local) | `util.Db` | **NO** |
| `ETASMI_JDBC_USER` | MySQL | JDBC username | No (local) | `util.Db` | **NO** |
| `ETASMI_JDBC_PASSWORD` | MySQL | JDBC password | No (local) | `util.Db` | **NO** |

Resolution order and the unpooled-production consequence are documented in doc 03 §1.

### 1.2 Application

| Variable | Purpose | Required | Mobile exposure |
|---|---|---|---|
| `APP_BASE_URL` | Absolute base for links in emails | **Yes in production** | SAFE |
| `APP_URL` | Alternate/legacy base URL | No | SAFE |
| `APP_TIME_ZONE` | Application timezone (default `Asia/Kuala_Lumpur`) | No | SAFE |
| `ETASMI_UPLOADS_DIR` | Local upload root (default `etasmi_uploads`) | Only in local storage mode | **NO** |
| `PORT` | HTTP port injected by Railway | Yes (Railway) | **NO** |

**If `APP_BASE_URL` is wrong, every verification and password-reset link points at the wrong host.** It is the highest-impact non-secret variable in the system.

### 1.3 Zoom

| Variable | Purpose | Required | Mobile exposure |
|---|---|---|---|
| `ZOOM_ACCOUNT_ID` | Server-to-Server OAuth account | Yes for sessions | **NO** |
| `ZOOM_CLIENT_ID` | S2S OAuth client id | Yes | **NO** |
| `ZOOM_CLIENT_SECRET` | S2S OAuth client secret | Yes | **NO — never** |
| `ZOOM_MEETING_SDK_KEY` | Meeting SDK app key | Yes for embed | SAFE (public by design) |
| `ZOOM_MEETING_SDK_SECRET` | Signature signing secret | Yes for embed | **NO — never** |
| `ZOOM_MEETING_SDK_CLIENT_ID` | Alternate SDK key variable name | No | SAFE |
| `ZOOM_MEETING_SDK_CLIENT_SECRET` | Alternate SDK secret variable name | No | **NO — never** |
| `ZOOM_DEFAULT_HOST_EMAIL` | Fallback licensed host | Strongly recommended | **NO** |
| `ZOOM_TIMEZONE` | Timezone sent on meeting creation | No | SAFE |
| `ZOOM_EMBED_ENABLED` | Feature flag for the embedded SDK | No | SAFE |
| `ZOOM_WEB_SDK_VERSION` | Pinned Web SDK version | No | SAFE |
| `ETASMI_ZOOM_WEB_DEBUG` | Verbose SDK logging | No — **dev only** | SAFE |

Both `ZOOM_MEETING_SDK_KEY`/`SECRET` and `ZOOM_MEETING_SDK_CLIENT_ID`/`CLIENT_SECRET` are accepted, reflecting Zoom's own rename of these fields. Either pair works.

### 1.4 Cloudinary

| Variable | Purpose | Required | Mobile exposure |
|---|---|---|---|
| `CLOUDINARY_CLOUD_NAME` | Account cloud name | For cloud storage mode | SAFE (public identifier) |
| `CLOUDINARY_API_KEY` | Upload signing key | For cloud storage mode | **NO** |
| `CLOUDINARY_API_SECRET` | Upload signing secret | For cloud storage mode | **NO — never** |

All three must be present or the system silently falls back to local disk storage — which is **data-losing on Railway's ephemeral filesystem**.

### 1.5 OpenAI

| Variable | Purpose | Required | Default | Mobile exposure |
|---|---|---|---|---|
| `OPENAI_API_KEY` | Bearer credential for every AI call | Yes for all AI | — | **NO — never** |
| `OPENAI_ASSISTANT_MODEL` | Quran Assistant chat | No | `gpt-4o-mini` | SAFE |
| `OPENAI_TRANSLATION_MODEL` | AI verse translation | No | `gpt-4o-mini` | SAFE |
| `OPENAI_VOICE_STT_MODEL` | Voice speech-to-text | No | `gpt-4o-transcribe` | SAFE |
| `OPENAI_VOICE_TTS_MODEL` | Voice text-to-speech | No | `gpt-4o-mini-tts` | SAFE |
| `OPENAI_VOICE_NAME` | TTS voice | No | `alloy` | SAFE |
| `OPENAI_RECITATION_MODEL` | Recitation transcription | No | `gpt-4o-transcribe` | SAFE |
| `OPENAI_EVALUATOR_MODEL` | Recitation evaluation | No | **`gpt-4o`** | SAFE |
| `OPENAI_CLASSIFIER_MODEL` | Legacy alias for the evaluator | No | — | SAFE |

### 1.6 Quran Foundation

| Variable | Purpose | Required | Mobile exposure |
|---|---|---|---|
| `QF_CLIENT_ID` | OAuth2 client id | Yes beyond the bundled catalog | **NO** |
| `QF_CLIENT_SECRET` | OAuth2 client secret | Yes | **NO — never** |
| `QF_API_ENDPOINT` | Content API base (HTTPS) | Yes | SAFE |
| `QF_ENV` | Tier: `prelive`, `production`, `production_test`, `production_live` | Yes unless `QF_AUTH_ENDPOINT` is set | SAFE |
| `QF_AUTH_ENDPOINT` | OAuth origin or full token URL override | No | SAFE |
| `QF_OAUTH_SCOPE` | Space-separated scopes (default `content`) | No | SAFE |
| `QF_SEARCH_API_BASE` | Search host when it differs from Content | No | SAFE |

### 1.7 Email

| Variable | Purpose | Required | Mobile exposure |
|---|---|---|---|
| `BREVO_API_KEY` | Brevo transport (priority 1) | One transport required | **NO** |
| `BREVO_SENDER_NAME` | Brevo sender display name | No | SAFE |
| `RESEND_API_KEY` | Resend transport (priority 2) | One transport required | **NO** |
| `SMTP_HOST` | SMTP server (priority 3) | One transport required | **NO** |
| `SMTP_PORT` | SMTP port | With SMTP | **NO** |
| `SMTP_USER` / `SMTP_USERNAME` | SMTP username (both accepted) | With SMTP | **NO** |
| `SMTP_PASS` / `SMTP_PASSWORD` | SMTP password (both accepted) | With SMTP | **NO — never** |
| `SMTP_FROM` | Sender address | With SMTP | SAFE |
| `SMTP_STARTTLS` | Enable STARTTLS | No | SAFE |
| `SMTP_DEBUG` | Verbose mail logging | No — **dev only** | SAFE |

### 1.8 Development and override flags — **AUDIT THESE IN PRODUCTION**

| Variable | Effect when set | Production risk |
|---|---|---|
| `ETASMI_DISABLE_RATE_LIMIT` | Disables **all** login/registration rate limiting | **HIGH** — enables unlimited credential stuffing |
| `ETASMI_ALLOW_LOGIN_WITHOUT_EMAIL_VERIFICATION` | Bypasses the email-verification login gate | **HIGH** — defeats account-ownership proof |
| `ETASMI_BOOTSTRAP_ADMIN_PASSWORD` | `DBSeeder` resets the bootstrap admin's password on **every application start** | **HIGH** — a permanent known-credential backdoor while set |
| `DEBUG_ERRORS` | Verbose error output | **MEDIUM** — potential stack-trace disclosure |
| `SMTP_DEBUG` | Verbose mail protocol logging | LOW–MEDIUM — may log recipient addresses |
| `ETASMI_ZOOM_WEB_DEBUG` | Verbose Zoom SDK logging | LOW |

**These six variables must be explicitly verified as unset in production.** Each is a legitimate development convenience and a genuine production vulnerability. The first three individually defeat a security control.

### 1.9 Configuration totals

| Category | Count |
|---|---|
| Database | 9 |
| Application | 5 |
| Zoom | 12 |
| Cloudinary | 3 |
| OpenAI | 9 |
| Quran Foundation | 7 |
| Email | 11 |
| Dev/override flags | 6 |
| **Total distinct variables** | **~62** |
| **Of which are secrets** | **13** |

**The 13 secrets:** `MYSQLPASSWORD`, `ETASMI_JDBC_PASSWORD`, `MYSQL_URL` (embeds credentials), `ZOOM_CLIENT_SECRET`, `ZOOM_MEETING_SDK_SECRET`, `ZOOM_MEETING_SDK_CLIENT_SECRET`, `CLOUDINARY_API_SECRET`, `CLOUDINARY_API_KEY`, `OPENAI_API_KEY`, `QF_CLIENT_SECRET`, `BREVO_API_KEY`, `RESEND_API_KEY`, `SMTP_PASSWORD`/`SMTP_PASS`.

**None of these 13 may ever appear in a Flutter binary, a mobile config file, an API response, or a client-side log.**

---

## 2. SECRET-HANDLING ARCHITECTURE

### Correct — the current design

```
   Browser ──▶ e-Tasmi server ──▶ OpenAI / Zoom / Cloudinary / Quran Foundation
                     ▲
                secrets live here, and only here
```

Verified: no secret is emitted into any JSP, JSON response, JavaScript file, or HTML attribute. The Zoom Meeting SDK signature is minted server-side and only the resulting JWT — which is scoped to one meeting and expires in 2 hours — reaches the browser. `/student/api/quran-foundation/health` deliberately returns presence booleans rather than values.

### Forbidden — what must never happen on mobile

```
   Flutter App ──▶ (embedded API secret) ──▶ External API      ❌ NEVER
```

A shipped mobile binary is fully decompilable. Any key inside it is a published key. Every external call must be proxied through the PHP backend.

### Mobile-safe values

`ZOOM_MEETING_SDK_KEY` (public by design), `CLOUDINARY_CLOUD_NAME` (a public identifier), all model-name variables, `APP_BASE_URL`, `APP_TIME_ZONE`, and `QF_API_ENDPOINT`. Everything else stays server-side.

---

## 3. SECURITY FINDINGS

### 3.1 Controls that are correct

| Control | Assessment |
|---|---|
| Password hashing | **Strong** — BCrypt cost 12 |
| SQL injection | **Strong** — `PreparedStatement` throughout; dynamic SQL concatenates only constant column names and binds all user input. No injection vector found |
| Session fixation | **Handled** — session invalidated and recreated at login |
| Path traversal | **Handled** — `..` and `\` stripped, containment verified against the uploads root |
| File type validation | **Good** — images decoded with `ImageIO` rather than trusting the declared MIME type |
| Secret exposure | **Good** — no secret reaches any client |
| Ownership / IDOR | **Good** — every resource is ownership-checked in the service layer; no IDOR found |
| Account enumeration | **Mostly good** — generic messages everywhere except `/auth/verify-status` |
| Output encoding | **Mostly good** — JSTL escaping in JSPs; tafsir HTML passes an allowlist sanitizer |
| Upstream response limits | **Good** — 400 KB caps on proxied Quran responses, 2 MB on the image proxy with magic-byte validation |

### 3.2 Findings

| # | Finding | Severity | Detail | Recommendation |
|---|---|---|---|---|
| **S-1** | **No CSRF protection anywhere** | **HIGH** | No token, no `SameSite` attribute, no origin check. Every state-changing POST is forgeable — including payment approval, user deletion, and instructor rejection | Add synchroniser tokens to the web app. A token-authenticated mobile API is not CSRF-exposed |
| **S-2** | **`/uploads/*` is unauthenticated** | **HIGH** | Payment receipts and recitation audio are readable by anyone with the URL. `AuthFilter` treats the path as public | Authenticate the route, or move to signed expiring URLs as already done for qualification PDFs |
| **S-3** | **Dev-override flags can disable security controls** | **HIGH** | `ETASMI_DISABLE_RATE_LIMIT`, `ETASMI_ALLOW_LOGIN_WITHOUT_EMAIL_VERIFICATION`, `ETASMI_BOOTSTRAP_ADMIN_PASSWORD` each defeat a control | Verify all three are unset in production; consider refusing to start when they are set with a production profile |
| **S-4** | **Bootstrap admin password reset on every boot** | **MEDIUM** | While `ETASMI_BOOTSTRAP_ADMIN_PASSWORD` is set, `DBSeeder` rewrites that admin's password at every start, so rotation is impossible | Make it one-shot, or remove after initial setup |
| **S-5** | **Default seeded admin credentials** | **MEDIUM** | `admin@etasmi.com` with a known default password is created when no admin exists | Force a password change on first login |
| **S-6** | **Session cookie flags unset** | **MEDIUM** | No `Secure`, `HttpOnly`, or `SameSite` in `web.xml` | Add a `<cookie-config>` block |
| **S-7** | **No security headers** | **MEDIUM** | No CSP, HSTS, `X-Frame-Options`, or `Referrer-Policy`. `X-Content-Type-Options` is set on only two endpoints | Add them at the container or reverse proxy |
| **S-8** | **Email enumeration via `/auth/verify-status`** | **MEDIUM** | Unauthenticated `{"known":true\|false}` oracle, contradicting the generic messaging used everywhere else | Require a session, or drop the endpoint |
| **S-9** | **`zoom_start_url` stored in plaintext** | **MEDIUM** | Grants host rights to anyone who can read the row or a backup | Encrypt at rest, or fetch on demand from Zoom instead of persisting |
| **S-10** | **No AI cost controls** | **MEDIUM** | No per-user quota, no spend cap, no request budget. An authenticated student can issue unlimited OpenAI calls — including the `gpt-4o` evaluator path | Add per-user rate limits and a global spend cap before mobile launch |
| **S-11** | **`useSSL=false` to MySQL** | **MEDIUM** | The Compose JDBC URL disables transport encryption to the database | Enable TLS, especially for any non-colocated database |
| **S-12** | **Rate limiting is narrow** | **MEDIUM** | Only login, registration, forgot-password, and resend. **Uploads and all AI endpoints are unthrottled** | Extend to uploads and AI |
| **S-13** | **Uploaded files are never deleted** | **LOW–MEDIUM** | No Cloudinary `destroy` call exists. Replaced photos, QR images, and banners are orphaned permanently | Delete on replace; audit existing orphans |
| **S-14** | **No account lockout** | **LOW** | Rate limiting only; a distributed attack across IPs is unthrottled | Add progressive delays or lockout |
| **S-15** | **Legacy PBKDF2 hashes never upgraded** | **LOW** | A pre-BCrypt user keeps the weaker hash indefinitely, since no rehash-on-login exists | Rehash on successful login |
| **S-16** | **Logout is GET-only** | **LOW** | CSRF-triggerable logout | Require POST |
| **S-17** | **Audit coverage is thin** | **LOW** | Only 7 actions logged. Authentication events, password resets, session CRUD, payments, and evaluations are **not** audited | Extend coverage, especially to financial actions |
| **S-18** | **Admin create form is weaker than the server** | **LOW** | JSP declares `minlength="6"`; the server requires 8 | Align the form to 8 |
| **S-19** | **No CORS configuration** | **INFO** | No `Access-Control-*` headers anywhere. Not currently a vulnerability, but the mobile API will need a deliberate policy | Define an explicit allowlist in PHP |

### 3.3 Severity summary

| Severity | Count |
|---|---|
| HIGH | 3 |
| MEDIUM | 9 |
| LOW | 6 |
| INFO | 1 |

**The three HIGH findings share a theme: things that should be protected are reachable without proof of identity or intent.** All three are fixable without any schema change.

---

## 4. DEPLOYMENT CONFIGURATION

### 4.1 Build

| Aspect | Detail |
|---|---|
| Build system | **Ant / NetBeans** — `build.xml` + `nbproject/`. **Maven is not used**, contrary to the original brief |
| Java | 17 |
| Compilation | `javac` driven by Ant inside the Docker build |
| Artifact | A WAR deployed into Tomcat 9 |
| Dependency management | **Manual JARs committed to the repository** — no `pom.xml`, no lockfile, no dependency scanning |

Manual JAR management means **no automated CVE scanning of dependencies**. This should be addressed regardless of the migration; the PHP backend should use Composer with a lockfile.

### 4.2 Docker

`Dockerfile`: Java 17 + Ant build stage, Tomcat 9 runtime, port binding driven by Railway's `PORT`.

`docker-compose.yml` (local development only): `db` (MySQL 8, with `etasmi_schema.sql` mounted as an init script), `app`, `caddy` (reverse proxy), `phpmyadmin`.

**`phpmyadmin` must never be exposed in production.** It is a local-development convenience.

### 4.3 Railway

Environment variables are supplied by the platform. `MYSQL*` variables are injected by the managed MySQL plugin, and `PORT` by the runtime.

| Constraint | Consequence |
|---|---|
| **Ephemeral filesystem** | Anything written to `ETASMI_UPLOADS_DIR` is **lost on every redeploy**. Cloudinary is effectively mandatory in production |
| **Unpooled connections** | The Railway variable path takes precedence over the JNDI DataSource, so production opens a new `DriverManager` connection per call |
| **No CDN** | Static assets — including ~2 MB of CSS — are served directly by Tomcat |
| **Single instance assumed** | In-memory caches (OAuth tokens, AI translation cache, rate-limit counters) are per-JVM and would diverge across replicas |

That last point matters for mobile: **rate limiting is in-memory, so horizontal scaling silently multiplies the effective limit.**

### 4.4 Startup sequence

1. Tomcat starts, deploys the WAR.
2. `SchemaBootstrap` (`@WebListener`) — creates the full schema **only if the `user` table is missing**; performs no incremental migration. Also initialises `QuranBundledCatalog`.
3. `DBSeeder` (`@WebListener`) — incremental column/table patches, admin repair, admin seeding, and the boot-time password reset when `ETASMI_BOOTSTRAP_ADMIN_PASSWORD` is set.
4. Filters register: `CharacterEncodingFilter` → `UrlLocalizationFilter` → `AuthFilter`.
5. Servlets register via annotations.

**There is no health-check endpoint, no readiness probe, no metrics endpoint, and no structured logging.** Observability is limited to container stdout.

---

## 5. PRODUCTION READINESS SUMMARY

| Area | Status | Blocking for mobile? |
|---|---|---|
| Database schema | ✅ Complete and consistent | No |
| Database connection handling | ⚠ Unpooled, unencrypted | **Yes** — fix in PHP |
| Authentication | ⚠ Works, but cookie-only | **Yes** — needs a token layer |
| Authorization | ✅ Correct and well-structured | No |
| CSRF | ❌ Absent | Web-only concern |
| File storage | ⚠ Works, but unauthenticated and never cleaned up | **Yes** |
| Zoom | ✅ Production-ready | Web SDK needs a native replacement |
| Cloudinary | ✅ Production-ready | Make it mandatory |
| OpenAI | ⚠ Works, ungoverned cost | **Yes** — add quotas |
| Quran Foundation | ✅ Production-ready | No |
| Email | ⚠ Config-dependent | Verify `APP_BASE_URL` and sender authentication |
| Payment | ✅ Works as designed (manual) | Confirm the product expectation |
| Logging / monitoring | ❌ Minimal | Recommended before launch |
| Dependency management | ❌ Manual JARs, no scanning | Fix in PHP with Composer |
| Secrets management | ✅ Env vars, none leaked | Keep the same discipline |

---

## 6. PRE-MOBILE-LAUNCH SECURITY CHECKLIST

- [ ] Confirm `ETASMI_DISABLE_RATE_LIMIT` is **unset** in production
- [ ] Confirm `ETASMI_ALLOW_LOGIN_WITHOUT_EMAIL_VERIFICATION` is **unset**
- [ ] Confirm `ETASMI_BOOTSTRAP_ADMIN_PASSWORD` is **unset**, and rotate that admin's password
- [ ] Confirm `DEBUG_ERRORS`, `SMTP_DEBUG`, `ETASMI_ZOOM_WEB_DEBUG` are **unset**
- [ ] Change the default `admin@etasmi.com` password if it is still in place
- [ ] Authenticate or sign every `/uploads/*` URL
- [ ] Add CSRF protection to the web application
- [ ] Set `Secure`, `HttpOnly`, `SameSite` on the session cookie
- [ ] Add CSP, HSTS, `X-Frame-Options`, `Referrer-Policy`
- [ ] Remove or authenticate `/auth/verify-status`
- [ ] Enable TLS to MySQL
- [ ] Add per-user AI rate limits and a spend cap
- [ ] Extend rate limiting to uploads and AI endpoints
- [ ] Verify `phpmyadmin` is not exposed in production
- [ ] Verify `APP_BASE_URL` points at the production host
- [ ] Verify SPF/DKIM for the sending domain
- [ ] Confirm Cloudinary is configured so local storage is never used in production
- [ ] Establish dependency vulnerability scanning
- [ ] Rotate every one of the 13 secrets before mobile launch
- [ ] Confirm no secret is present in the Flutter binary or any mobile config file
