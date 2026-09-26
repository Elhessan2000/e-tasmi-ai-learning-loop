# 01 — SYSTEM OVERVIEW

**Project:** e-Tasmi — Online Quran Tasmi (recitation) Learning Platform
**Document purpose:** Establish what the existing system is, who uses it, and how it is built.
**Status of this document:** Reverse-engineered from source code. The existing web application is the SOURCE OF TRUTH.
**Audit date:** 4 September 2026

---

## 1. What e-Tasmi Is

e-Tasmi is a **Java Servlet/JSP web application** that connects Quran students with verified Quran instructors for scheduled online *Tasmi* sessions (recitation-to-a-teacher sessions). Students enroll in sessions, pay the instructor, attend a live Zoom class, submit audio recitations, and receive scored evaluations with feedback. Instructors create and host sessions, verify payments, and grade recitations with optional AI assistance. Admins verify instructor credentials, manage users, and monitor platform activity.

The platform additionally ships a **Quran Library** (full mushaf reader with translations, tafsir, and reciter audio) and an **AI Quran Assistant** (text and voice chat), both available to students.

### Domain vocabulary

| Term | Meaning in this system |
|---|---|
| **Tasmi** | The act of reciting Quran to a teacher for correction. The core unit of the platform. |
| **Tasmi Session** | A scheduled class created by an instructor, backed by a Zoom meeting. Table: `tasmi_session`. |
| **Recitation** | An audio submission by a student against an enrollment. Table: `recitation`. |
| **Evaluation** | An instructor's score (0–100) + feedback on one recitation. Table: `evaluation`. |
| **Quran Portion** | The assigned passage for a session (`tasmi_session.quran_portion`). Used as the expected text for AI evaluation. |
| **Verification** | Two distinct meanings: (a) admin approving an instructor's credentials, (b) instructor approving a student's payment receipt. |

---

## 2. Technology Stack (as actually built)

| Layer | Technology | Evidence |
|---|---|---|
| Language | Java 17 | `Dockerfile` (`javac -source 17 -target 17`); `nbproject/project.properties:61-62` |
| Web framework | **Java Servlet 4.0 + JSP/JSTL** — no Spring, no JAX-RS, no ORM | `web.xml` version 3.1; `@WebServlet` annotations |
| Servlet container | **Apache Tomcat 9.0** (JDK 17 base image) | `Dockerfile` |
| Database | **MySQL 8.x**, InnoDB, `utf8mb4_unicode_ci` | `etasmi_schema.sql`; `docker-compose.yml` (mysql:8.4) |
| DB access | **Raw JDBC + PreparedStatement**, hand-written DAO layer | `model/dao/impl/*Jdbc.java` |
| Build | **Apache Ant / NetBeans** (NOT Maven, NOT Gradle) | `e-Tasmi/build.xml`, `e-Tasmi/nbproject/` |
| Packaging | WAR built by direct `javac` in Docker, deployed as `ROOT.war` | `Dockerfile` |
| Templating | JSP + JSPF fragments + scriptlets | `e-Tasmi/web/jsp/**` |
| Frontend | Vanilla JavaScript (no framework), hand-written CSS | `e-Tasmi/web/assets/js/`, `e-Tasmi/web/css/` |
| Session state | **HttpSession + JSESSIONID cookie** (15-minute inactivity timeout) | `util/SessionUtil.java:9,15` |
| Password hashing | **BCrypt cost 12** (jBCrypt), legacy PBKDF2-HmacSHA256 (120k iters) verify-only | `util/PasswordUtil.java` |
| Hosting target | Docker → Railway (also runs under local Docker Compose) | `Dockerfile`, `docker-compose.yml` |

### Third-party JARs bundled in `WEB-INF/lib`

| JAR | Version | Consumed by |
|---|---|---|
| `mysql-connector-j` | 9.0.0 | All JDBC DAOs via `util/Db.java` |
| `jbcrypt` | 0.4 | `util/PasswordUtil.java` |
| `javax.mail` + `javax.activation` | 1.6.2 / 1.2.0 | `util/EmailUtil.java` SMTP transport |
| `pdfbox` + `fontbox` + `commons-logging` | 2.0.31 / 2.0.31 / 1.2 | `util/AdminReportPdfUtil.java` (admin/payment report PDFs) |
| `jsoup` | 1.17.2 | `model/service/quran/QuranTafsirHtmlSanitize.java` |
| `jstl` | 1.2 | JSP tag libraries |
| `servlet-api` | 4.0.1 | Compile-time API (**note:** it is also packaged inside the WAR, which is normally incorrect — see `11_SECURITY_AND_CONFIGURATION_AUDIT.md`) |

**There is no dependency manager.** JARs are committed binaries. Any migration must account for the absence of a lockfile or dependency manifest.

---

## 3. Codebase Size and Shape

| Metric | Value |
|---|---|
| Tracked source files (Java/JSP/JS/CSS/SQL/XML/properties) | 408 |
| Total lines across those files | ~118,800 |
| Java servlets (`@WebServlet`) | 58 |
| Java DAO interfaces / JDBC implementations | 22 / 22 |
| Service classes | 44 (incl. 20 in `model/service/quran/`) |
| Entity/model classes | 40 |
| Utility classes | 27 |
| Servlet filters | 3 |
| ServletContextListeners | 2 |
| JSP pages | 39 |
| JSPF fragments | 26 |
| CSS files | 66 |
| JavaScript files | 22 |
| Database tables (canonical schema) | 20 |
| i18n locales / keys | 3 (`en`, `ar`, `ms`) / ~1,626 keys |

### Directory layout

```
e-Tasmi/                              (repo root)
├── Dockerfile                        Multi-stage WAR build → Tomcat 9
├── docker-compose.yml                Local stack: db, app, caddy, phpmyadmin
├── Caddyfile                         Optional local HTTPS front-end
├── .env.example                      Documented env template
├── .env                              REAL local secrets (untracked, gitignored)
├── .env.template-overwrite-backup    ⚠ TRACKED, contains literal Zoom credentials
├── db/etasmi.sql                     LEGACY 10-table dump — do not use
├── docs/                             Prior reports, LaTeX papers, node_modules
├── scripts/                          Build-time Node/Python helper scripts
└── e-Tasmi/                          The web application module
    ├── build.xml, nbproject/         Ant/NetBeans build
    ├── setup/*.sql                   Schema + patch + seed/reset scripts
    ├── src/conf/                     messages_{en,ar,ms}.properties
    ├── src/java/
    │   ├── controller/               Servlets (auth, student, instructor, admin, filter)
    │   ├── model/dao/ + dao/impl/    DAO interfaces + JDBC implementations
    │   ├── model/entity/             POJOs + enums
    │   ├── model/service/            Business logic
    │   │   └── quran/                Quran Foundation + OpenAI services
    │   ├── setup/                    DBSeeder, legacy media migrations
    │   └── util/                     Db, PasswordUtil, ZoomApiClient, CloudinaryUtil, EmailUtil…
    └── web/
        ├── WEB-INF/web.xml, lib/, db/railway_schema.sql
        ├── jsp/{auth,student,instructor,admin,common}/
        ├── assets/{js,locales,quran,img,media}/
        ├── css/                      66 stylesheets
        ├── home.jsp, index.html
        ├── learnhub-dist/            Bootstrap landing template (CSS/JS reused by home.jsp)
        └── mockups/                  Static design mockups (DEAD — not served)
```

---

## 4. User Roles

The system has exactly **three roles**, defined by the MySQL enum `user.role`:

```
ENUM('ADMIN','INSTRUCTOR','STUDENT')
```

| Role | How it is created | Gating before full access |
|---|---|---|
| **STUDENT** | Public self-registration at `/auth/register?role=STUDENT` | Must verify email → `user.status` becomes `ACTIVE` |
| **INSTRUCTOR** | Public self-registration at `/auth/register?role=INSTRUCTOR` with a mandatory qualification PDF | Must verify email **AND** be approved by an admin (`instructor.verification_status = APPROVED`) |
| **ADMIN** | **Cannot self-register.** Created only by an existing admin at `/admin/users/create`, or seeded on first boot by `DBSeeder` | None — created pre-verified and `ACTIVE` |

A pending or rejected instructor **can log in** but every `/instructor/*` URL is intercepted by `AuthFilter` and forwarded to `pendingApproval.jsp`.

---

## 5. Current Architecture

### 5.1 Request pipeline

```
                          Browser (desktop/mobile web)
                                     │
                                     │  HTTP/HTTPS
                                     ▼
                        ┌────────────────────────┐
                        │  Tomcat 9 (ROOT.war)   │
                        └────────────┬───────────┘
                                     ▼
                  ① CharacterEncodingFilter   (/*  — forces UTF-8)
                                     ▼
                  ② UrlLocalizationFilter     (/*  — /en|/ar|/ms prefix routing)
                                     ▼
                  ③ AuthFilter                (/student/*, /instructor/*,
                                               /admin/*, /jsp/*, /notifications, /live/*)
                                     ▼
                        ┌────────────────────────┐
                        │   Servlet (Controller) │  @WebServlet annotations
                        └────────────┬───────────┘
                                     ▼
                        ┌────────────────────────┐
                        │     Service Layer      │  business rules, transactions
                        └────────────┬───────────┘
                                     ▼
                        ┌────────────────────────┐
                        │       DAO Layer        │  raw JDBC + PreparedStatement
                        └────────────┬───────────┘
                                     ▼
                        ┌────────────────────────┐
                        │      MySQL 8.x         │  20 tables, InnoDB
                        └────────────────────────┘
                                     │
                    ┌────────────────┴────────────────┐
                    ▼                                 ▼
        Response: RequestDispatcher            Response: JSON
        .forward() → JSP → HTML                (util/JsonUtil) for /*/api/* routes
```

**Key architectural fact:** this is a **server-rendered, session-cookie application**, not an API-first application. Approximately 90% of interactions are form POST → redirect → JSP render. Only the Quran Library, Quran Assistant, Zoom signature, instructor dashboard feeds, and the recitation studio use JSON endpoints — and even those authenticate with the `JSESSIONID` cookie, not a token.

### 5.2 External service topology

```
                    ┌──────────────────────────────────────────────┐
                    │              e-Tasmi (Tomcat)                │
                    │                                              │
   Browser ────────▶│  Servlets ──▶ Services ──▶ DAO ──▶ MySQL     │
                    │                  │                           │
                    └──────────────────┼───────────────────────────┘
                                       │  (all calls server-side; secrets never leave the server)
          ┌────────────┬───────────────┼────────────────┬──────────────┐
          ▼            ▼               ▼                ▼              ▼
   ┌────────────┐ ┌──────────┐  ┌─────────────┐  ┌───────────┐  ┌──────────────┐
   │  OpenAI    │ │   Zoom   │  │ Cloudinary  │  │   Quran   │  │  Email:      │
   │            │ │          │  │             │  │Foundation │  │  Brevo /     │
   │ chat/      │ │ S2S OAuth│  │ signed      │  │ Content + │  │  Resend /    │
   │ transcript-│ │ REST +   │  │ upload,     │  │ Search    │  │  SMTP        │
   │ ions/speech│ │ Meeting  │  │ destroy,    │  │ API       │  │              │
   │            │ │ SDK JWT  │  │ signed dl   │  │ (OAuth CC)│  │              │
   └────────────┘ └──────────┘  └─────────────┘  └───────────┘  └──────────────┘

   ⚠ NOT PRESENT: any payment gateway (no ToyyibPay, Billplz, Stripe, PayPal, FPX).
     Payment is manual bank/QR transfer with instructor verification of an uploaded receipt.
```

Only **two** external calls originate from the browser rather than the server:

1. The **Zoom Web SDK** loads from `source.zoom.us` and joins a meeting using a JWT that the server signed (the SDK *secret* stays server-side; only the short-lived signature reaches the browser).
2. The Quran Library fetches the **QuranCDN reciter catalog** (`api.qurancdn.com`) directly for profile pictures, and plays reciter audio directly from the upstream `audioUrl`.

### 5.3 Startup sequence

```
Tomcat deploys ROOT.war
        ↓
① SchemaBootstrap (@WebListener)
   ├─ QuranBundledCatalog.init(ctx)   loads offline Quran metadata
   └─ if table `user` MISSING → execute WEB-INF/db/railway_schema.sql (all 20 tables)
      if table `user` EXISTS   → no-op (redeploy-safe; performs NO incremental migration)
        ↓
② DBSeeder (@WebListener)
   ├─ adds missing columns / creates missing tables on an EXISTING database
   ├─ seeds admin@etasmi.com (password Admin123!) if no admin exists
   └─ if ETASMI_BOOTSTRAP_ADMIN_PASSWORD set → resets that admin's password every boot
        ↓
Servlets ready
```

**This split matters for migration:** `SchemaBootstrap` is all-or-nothing on an empty DB; `DBSeeder` performs the incremental migrations. `DBSeeder` does **not** cover every column present in `etasmi_schema.sql` (notably `tasmi_session.description`, `level`, `capacity`, `banner_image_url`), so a database evolved purely through `DBSeeder` may not match the canonical schema.

---

## 6. Feature Domains

| Domain | Roles | Summary |
|---|---|---|
| Authentication & account lifecycle | All | Register, email verification (6-digit code + 24h link), login, password reset, logout |
| Instructor verification | Instructor, Admin | Qualification PDF upload → admin approve/reject → workspace unlock |
| Session management | Instructor | Create/edit/delete/start/complete sessions; auto-provisions a Zoom meeting |
| Session discovery & enrollment | Student | Browse level-matched scheduled sessions, enroll, capacity-limited |
| Payment | Student, Instructor, Admin | Manual QR/bank transfer → receipt upload → instructor approves/rejects → enrollment confirmed. Admin is read-only monitor. |
| Live sessions | Student, Instructor | Zoom embed (Meeting SDK) or external redirect; auto-attendance on join |
| Recitation submission | Student | Record in-browser or upload audio against an approved, paid enrollment |
| Evaluation | Instructor | Score 0–100 + feedback, one evaluation per recitation, optional AI analysis |
| AI Quran Assistant | Student | Text chat + voice (STT→LLM→TTS), scoped to Quranic topics |
| Quran Library | Student | Mushaf reader, translations, tafsir, search, reciter audio, AI translation fallback |
| Progress tracking | Student | Completion rate = average evaluation score; live-computed summary stats |
| Notifications | All | In-app database rows only — **no push, no email fan-out, no websockets** |
| User management | Admin | CRUD, activate/deactivate, soft delete |
| Reports & analytics | Admin | 6 report types, CSV + PDF export (PDFBox), payment monitoring with charts |
| Audit logging | Admin | 7 action types recorded (user CRUD + instructor approve/reject) |
| Internationalization | All | English, Arabic (RTL), Bahasa Melayu; URL-prefix routing + client JSON dictionaries |

---

## 7. Feature Counts

| Role | Distinct features | Servlets |
|---|---|---|
| **Student** | 33 | 32 |
| **Instructor** | 16 | 12 |
| **Admin** | 12 | 8 |
| **Shared / System** | 12 | 4 + 3 filters + 2 listeners |
| **Total** | **73** | **58** |

Detailed enumeration is in `02_FEATURE_INVENTORY.md`.

---

## 8. What This System Is NOT

Stated explicitly because several of these were assumed in the migration brief and are **not true** of the actual codebase:

| Assumption | Reality | Evidence |
|---|---|---|
| Uses ToyyibPay / a payment gateway | **No gateway of any kind.** Manual QR/bank transfer + receipt image + instructor approval. | Repo-wide search of `src/java` returns zero matches for toyyibpay/billplz/stripe/paypal/fpx/razorpay |
| Has a PHP backend | **No.** It is Java Servlet/JSP. PHP appears only in the `phpmyadmin` container. | `Dockerfile`, all of `src/java` |
| Built with Maven | **No.** Ant/NetBeans; the production WAR is built by a raw `javac` call in the Dockerfile. | `build.xml`, `Dockerfile` |
| Has a REST API for clients | **Partially.** ~20 JSON endpoints exist but all authenticate via session cookie and none are versioned or CORS-enabled. | `AuthFilter.java:116-118`; no CORS filter anywhere |
| Admin approves payments | **No.** The *instructor* approves payments. Admin `/admin/payments` is explicitly a read-only monitor. | `AdminPaymentsServlet.java:43-44`; `PaymentService.verifyInstructorPayment` |
| Sends email notifications for events | **No.** Email is used only for verification and password reset. All other notifications are in-app DB rows. | `EmailUtil` has exactly 3 templates; `NotificationService` never calls `EmailUtil` |
| Instructors upload teaching materials | **Not implemented in the UI.** The `session_material` table, DAO, and a *serving* servlet exist, but no upload endpoint or form exists. | `SessionMaterialDaoJdbc.insert()` has zero call sites |

---

## 9. Deployment Model

| Aspect | Detail |
|---|---|
| Container image | `tomcat:9.0-jdk17-temurin`, app deployed as `ROOT.war` (context path `/`) |
| Port binding | `server.xml` patched to `${port.http}`; `CATALINA_OPTS` injects `-Dport.http=${PORT:-8080}` so Railway's dynamic `PORT` works |
| Database resolution order | Railway `MYSQLHOST`+vars → `MYSQL_URL` → `ETASMI_JDBC_*` / system properties → JNDI `jdbc/ETasmiDS` |
| Persistent uploads | Docker volume `etasmi-uploads` → `/usr/local/tomcat/etasmi_uploads`. **This is compose-only; on Railway the container filesystem is ephemeral, so Cloudinary is required in production.** |
| Application timezone | `APP_TIME_ZONE` → `TZ` → default `Asia/Kuala_Lumpur`, resolved in code (`util/DateTimeFormats.APP_ZONE`) so session math is host-clock independent |
| Local extras | `caddy` (HTTPS on :8443, needed to test Zoom embed which requires a secure context), `phpmyadmin` (:8081) |
| Fonts in image | `fonts-noto` installed for Arabic rendering in generated PDFs |

---

## 10. Verification of This Document

The running application was smoke-tested read-only at `http://localhost:8080` on 4 Sep 2026 (container `etasmi-app`, image `etasmi-tomcat:latest`). Observed behavior matched the documented design exactly:

| Request | Result | Confirms |
|---|---|---|
| `GET /` , `/home`, `/auth/login` | `302` → `/en/...` | `UrlLocalizationFilter` prefixes every unprefixed GET |
| `GET /en/home` | `200`, 71,719 bytes | Public landing renders |
| `GET /en/auth/login` | `200`, 8,727 bytes | Public auth page renders |
| `GET /en/auth/register` | `200`, 16,073 bytes | Public registration renders |
| `GET /en/student/dashboard` | `302` → login | `AuthFilter` blocks unauthenticated role routes |
| `GET /en/admin/dashboard` | `302` → login | Same |
| `GET /en/instructor/dashboard` | `302` → login | Same |
| `GET /en/jsp/student/dashboard.jsp` | `302` | Direct JSP access blocked |
| `GET /en/student/api/quran-library/chapters` | `401` JSON | JSON APIs return 401, not a redirect |
| `GET /en/student/api/quran-foundation/health` | `401` JSON | Same |

No write operations were performed. The existing system was not modified in any way during this audit.

---

## 11. Reading Order for This Package

| # | Document | Read it for |
|---|---|---|
| 01 | System Overview | *(this document)* Orientation |
| 02 | Feature Inventory | Every capability, per role, with endpoints |
| 03 | Database Architecture | All 20 tables, columns, FKs, ERD |
| 04 | API Documentation | Every endpoint with params, responses, errors |
| 05 | Business Rules | Every enforced rule and state machine |
| 06 | Authentication & Authorization | Auth flows, session model, permission matrix |
| 07 | External APIs & Integrations | Zoom, Cloudinary, payment, email, notifications |
| 08 | AI System Documentation | OpenAI pipelines, prompts, Quran Foundation |
| 09 | UI/UX Page Inventory | Every page, component, design token |
| 10 | Mobile Migration Architecture | Flutter + backend target design, screen mapping |
| 11 | Security & Configuration Audit | Env vars, secrets, weaknesses, production readiness |
| 12 | Testing & Workflow Documentation | End-to-end workflows, what was/wasn't tested |
| **13** | **Complete Migration Reference** | **Master document — the single source of truth for the mobile build** |
