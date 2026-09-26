# 09 — UI / UX PAGE INVENTORY

**Scale:** 31 renderable JSP pages, 27 `.jspf` include fragments, 20 JavaScript files (~296 KB hand-written), 62 CSS files (~2 MB), plus a bundled `learnhub-dist` theme (~773 KB) used by the landing page.

**Rendering model:** server-side JSP with JSTL. There is no SPA framework, no React/Vue/Angular, and no build step for the application's own JavaScript — every `.js` file is plain ES6 loaded with a `<script>` tag. The Quran Library is the closest thing to an SPA, but it is a 101 KB vanilla-JS module rendered into a server-delivered shell.

---

## 1. DESIGN SYSTEM

### 1.1 Colour tokens

Defined as CSS custom properties in `etasmi-theme.css`, with a full dark-mode override. **Every colour in the application resolves through these tokens** — the Flutter theme should be built directly from this table.

| Token | Light | Dark | Use |
|---|---|---|---|
| `--theme-primary` | `#0f766e` (teal 700) | `#2dd4bf` | Brand, primary buttons, active nav |
| `--theme-primary-strong` | `#115e59` | `#14b8a6` | Hover / pressed |
| `--theme-primary-soft` | `rgba(15,118,110,.10)` | `rgba(45,212,191,.16)` | Tinted backgrounds, chips |
| `--theme-accent` | `#d97706` (amber 600) | `#f59e0b` | Highlights, secondary CTAs |
| `--theme-success` | `#16a34a` | `#4ade80` | Approved, completed |
| `--theme-warning` | `#d97706` | `#fbbf24` | Pending, awaiting verification |
| `--theme-danger` | `#dc2626` | `#f87171` | Rejected, cancelled, destructive |
| `--theme-bg` | `#f5f7fb` | `#0b1220` | Page background |
| `--theme-bg-soft` | `#eef2f7` | `#0d1629` | Secondary background |
| `--theme-surface` | `#ffffff` | `#111b32` | Cards, panels |
| `--theme-surface-raised` | `#ffffff` | `#17223d` | Elevated cards, modals |
| `--theme-surface-muted` | `#f8fafc` | `#0d1629` | Table headers, inset areas |
| `--theme-surface-hover` | `#f1f5f9` | `#18243f` | Row/button hover |
| `--theme-border` | `#e2e8f0` | `#1f2c48` | Default borders |
| `--theme-border-strong` | `#cbd5e1` | `#2c3c5e` | Emphasised borders |
| `--theme-text` | `#0f172a` | `#e6eaf3` | Primary text |
| `--theme-text-soft` | `#475569` | `#b7c1d4` | Secondary text |
| `--theme-text-muted` | `#64748b` | `#8592aa` | Tertiary / captions |
| `--theme-overlay` | `rgba(15,23,42,.52)` | `rgba(2,6,17,.72)` | Modal scrim |

**Identity:** deep teal primary with amber accent — an intentionally calm, scholarly palette. The Slate-family neutrals are Tailwind's scale, though Tailwind itself is not used.

### 1.2 Typography

| Token | Stack |
|---|---|
| `--etasmi-font-sans` | `"Inter", system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif` |
| `--etasmi-font-display` | `"Poppins", var(--etasmi-font-sans)` |

Poppins for headings and display, Inter for body and UI. Arabic text renders through the system Arabic fallback — **no dedicated Quranic font (Amiri, Scheherazade, KFGQPC) is loaded**, which the Flutter app should improve on for verse display.

### 1.3 Theming

`etasmi-theme.js` (4.5 KB) toggles `data-theme="light|dark"` on `<html>`, persists the choice in `localStorage`, and honours `prefers-color-scheme` on first visit. `etasmi_theme_init.jspf` applies the stored theme **before paint** to avoid a flash. A toggle appears in every header, including the auth pages.

### 1.4 Responsive behaviour

Breakpoints observed in `etasmi-mobile-responsive.css`, all `max-width` (desktop-first):

| Breakpoint | Frequency | Purpose |
|---|---|---|
| `768px` | 11 | Primary tablet/mobile switch — sidebar collapses to an off-canvas drawer |
| `600px` | 8 | Small phone — tables become stacked cards |
| `1100px` | 6 | Small laptop — grid column reduction |
| `480px` | 4 | Compact phone |
| `360px` | 1 | Minimum supported |
| `(hover:none) and (pointer:coarse)` | 1 | Touch-target enlargement |

The site is **responsive but not mobile-first**. Desktop is the design baseline and mobile is an adaptation — a key reason a native rebuild is justified rather than a WebView wrapper.

### 1.5 Internationalization

`etasmi-i18n.js` (13.7 KB) plus `etasmi-i18n.css` (10.9 KB) handle three locales — `en`, `ar`, `ms`. Arabic sets `dir="rtl"` on the document and mirrors layout. A language switcher fragment appears in every header. **Server-generated strings — notification text and most validation messages — remain English regardless of locale.**

### 1.6 Shared fragments

| Fragment | Role |
|---|---|
| `ui_head.jspf`, `student_ui_head.jspf`, `instructor_ui_head.jspf`, `admin_ui_head.jspf` | Per-role CSS/JS bundles |
| `app_header.jspf`, `student_header.jspf`, `instructor_header.jspf`, `admin_header.jspf` | Role headers |
| `student_sidebar.jspf`, `instructor_sidebar.jspf`, `admin_sidebar.jspf` | Role navigation |
| `public_header.jspf`, `public_footer.jspf` | Landing/auth chrome |
| `student_quran_assistant.jspf` | The floating AI assistant panel, injected into student pages |
| `etasmi_theme_init.jspf`, `etasmi_i18n_head.jspf`, `etasmi_i18n_init.jspf`, `etasmi_language_switcher.jspf` | Theme and locale bootstrapping |
| `student_breadcrumb.jspf`, `instructor_page_hero.jspf` | Page headers |

---

## 2. PUBLIC AND AUTH PAGES

### 2.1 `home.jsp` — Landing page

| | |
|---|---|
| **URL** | `/{locale}/home` |
| **Role** | Public |
| **Purpose** | Marketing, feature showcase, statistics, calls to action |
| **Live data** | `statStudents`, `statSessions`, `statCompletionPct` |
| **Components** | Hero with Lottie animation, feature grid, animated stat counters, testimonial/CTA sections, public footer |
| **JS** | `landing.js`, `home-scroll-reveal.js`, `lottie.min.js` (298 KB), `learnhub-dist` bundle |
| **States** | Scroll-reveal animations; stats show a dash when null |
| **Verified live** | `200`, 71,719 bytes |
| **Mobile note** | Replaced by an onboarding/splash flow. The stats can feed a simple intro screen |

### 2.2 `login.jsp`

| | |
|---|---|
| **URL** | `/{locale}/auth/login` |
| **Components** | Split-screen dual-panel layout (`etasmi-login-dual.css`), email + password, show/hide toggle, forgot-password link, register link, theme toggle, language switcher |
| **Validation** | HTML5 `required` + `type="email"`; real strength/format checks are server-side |
| **JS** | `auth.js` (12.8 KB) — supports an AJAX submit path returning `{success, redirect}` |
| **States** | Idle · submitting · error banner (`Invalid email or password.`) · rate-limited · deactivated · unverified |
| **Query flags** | `?expired=1` (session timeout), `?forbidden=1` |

### 2.3 `register.jsp`

| | |
|---|---|
| **URL** | `/{locale}/auth/register` |
| **Components** | Role tabs (Student / Instructor), shared identity fields, conditional level selector for students, conditional bio + PDF upload for instructors, password strength meter |
| **Conditional UI** | Selecting Instructor reveals bio and qualification upload; selecting Student reveals the education-level dropdown |
| **Validation** | Client-side strength meter and match check, both re-enforced server-side |
| **States** | Idle · uploading · field errors · duplicate email · weak password · file-too-large |

### 2.4 `verify_email.jsp`

Six-box OTP input with auto-advance and paste handling, a countdown to resend, and an attempts-remaining indicator (max 5). `auth.js` polls `/auth/verify-status` to auto-advance when the link is clicked in another tab.

### 2.5 `verify_success.jsp` / `verify_failed.jsp`

Terminal confirmation screens for the link-token path.

### 2.6 `forgot_password.jsp` / `reset_password.jsp`

Single email field, always reporting generic success. The reset page validates the token on GET and enforces the same strength rules with a live meter.

### 2.7 `pendingApproval.jsp`

Shown to any `PENDING` or `REJECTED` instructor who reaches an `/instructor/*` URL. Explains the verification status; there is **no resubmission control and no rejection reason displayed** — because none is stored.

---

## 3. STUDENT PAGES (13)

### 3.1 `student/dashboard.jsp`

| | |
|---|---|
| **URL** | `/{locale}/student/dashboard` |
| **Components** | Greeting hero, KPI stat cards (enrollments, completed sessions, recitations, average score), upcoming-sessions list, recent notifications, progress ring, quick actions |
| **Data** | `student`, `enrollment`, `tasmi_session`, `payment`, `recitation`, `evaluation`, `progress`, `notifications` |
| **JS** | `app.js`, `student-workspace.js` |
| **States** | Empty ("no enrollments yet") · populated · loading skeletons |
| **Mobile** | → Home tab with a scrollable card feed |

### 3.2 `student/sessions.jsp` — Browse sessions

| | |
|---|---|
| **URL** | `/{locale}/student/sessions`, `/student/available-sessions` |
| **Components** | Search bar, session cards (banner, title, instructor + photo, date/time, duration, level badge, fee or FREE badge, capacity indicator), enroll CTA, notice banner |
| **JS** | `browse-sessions.js` (7.4 KB) — client-side filtering |
| **Business rule visible in UI** | Only level-matching, `SCHEDULED`, `ONLINE`, future sessions from approved active instructors appear |
| **States** | Empty ("no sessions match your level") · results · 12 distinct `notice` banner variants |
| **Mobile** | → Browse tab with pull-to-refresh and filter sheet |

### 3.3 `student/session_confirm.jsp`

Enrollment confirmation with the fee summary and a free-vs-paid branch explanation.

### 3.4 `student/enrollments.jsp` — My sessions

Status-grouped cards with `PENDING` / `APPROVED` / `REJECTED` / `CANCELLED` chips, a Join Live button gated on approval and payment, and payment-status chips. `my-sessions.js`. Errors arrive as `?joinError=` codes. **No withdraw button exists** despite the endpoint supporting it.

### 3.5 `student/payments.jsp`

Payment list (only `amount > 0`) with status chips, amounts in `RM`, a link to the QR upload page, and a receipt download for approved payments.

### 3.6 `student/qr_payment.jsp` — Receipt upload

| | |
|---|---|
| **URL** | `/{locale}/student/payments/qr` |
| **Components** | Instructor's QR image, bank name / account holder / account number, payment notes, amount due, file picker with preview, submit button, verification-history timeline |
| **CSS** | `qr-payment.css` (20.6 KB) |
| **Validation** | Client checks type and 10 MB size; server re-validates |
| **States** | No payment method configured (blocked) · ready to upload · uploading · awaiting verification (upload locked) · rejected with reason (resubmission allowed) · approved (locked) |
| **Mobile** | → Camera capture + gallery picker, client-side compression, QR displayed full-screen for scanning from a banking app |

### 3.7 `student/payment_receipt.jsp`

Printable receipt view. `?download=1` returns a **hand-built PDF generated inline by the servlet** — not a library.

### 3.8 `student/recitations.jsp` — Recitation Studio

| | |
|---|---|
| **Components** | Enrollment selector, in-browser recorder (`MediaRecorder`), waveform/timer, playback preview, file-upload alternative, submission history with scores and feedback |
| **JS** | `recitation-studio.js` (22.5 KB) |
| **CSS** | `student-recitation-studio.css` (46.5 KB), `student-upload-recitation.css` |
| **States** | Idle · requesting mic permission · recording · preview · uploading (progress) · success · error |
| **Note** | The client computes and sends `duration`, which the servlet **does not read** |
| **Mobile** | → Native recorder. The 200 MB limit is unrealistic on mobile data; compression is advisable |

### 3.9 `student/progress.jsp`

Completion-rate ring from `progress.completion_rate`, plus live-computed counters and a score history per recitation.

### 3.10 `student/quran_library.jsp` — The largest page in the system

| | |
|---|---|
| **JS** | `quran-library.js` (**101 KB**), `quran-assistant.js` (34.8 KB), `quran-voice.js` (15.1 KB), `reciter-portrait-fallbacks.js` |
| **CSS** | `quran-library.css` (115.9 KB) + `quran-library-dark.css` (22.8 KB) |
| **Components** | Surah list, verse reader with Arabic + translation + tafsir, translation/tafsir resource pickers, search, reciter selector with portraits, audio player with verse-timing highlight, AI translate button, the assistant panel |
| **API calls** | All 11 `/student/api/quran-library/*` endpoints plus the assistant endpoints |
| **States** | Loading · loaded · upstream error (7 mapped codes) · fallback catalog when Quran Foundation is unconfigured · playing · paused |
| **Mobile** | → This is the largest single conversion. Expect a dedicated multi-screen module: surah list, reader, search, audio player, assistant |

### 3.11 `student_quran_assistant.jspf` — AI assistant panel

Floating panel injected into student pages. Mode selector (8 modes), chat transcript, text input, microphone button for a voice turn, scope-reminder notice, typing indicator. History is browser-only and capped at 12 messages.

### 3.12 `student/notifications.jsp`

Chronological list. **No unread state, no filters, no actions** — the schema has no `is_read` column.

### 3.13 `student/profile.jsp`

Three cards: profile info, password change, photo upload with crop preview. `profile-redesign.css`.

### 3.14 `student/live-embed.jsp`

Zoom Web SDK container. Fetches the signature from `/student/api/zoomMeetingSignature`, joins with `role=0`. States: connecting · in-meeting · error · unsupported browser.

---

## 4. INSTRUCTOR PAGES (7)

### 4.1 `instructor/dashboard.jsp` — The cockpit

| | |
|---|---|
| **JS** | `instructor-dashboard.js` (**37.9 KB** — the largest single JS file after the Quran Library) |
| **CSS** | `instructor-dashboard-cockpit.css` (37.7 KB) + greeting/financial modules |
| **Components** | Greeting hero, KPI cards, **calendar view** fed by `calendar.json`, live-session status panel polled every 30 s via `heartbeat.json`, pending-payment alerts, recent recitations queue, revenue summary |
| **Polling** | `heartbeat.json` every 30 s; the calendar reloads on range change |
| **Mobile** | → Home tab. Replace 30-second polling with push or a longer interval to protect battery |

### 4.2 `instructor/sessions.jsp`

| | |
|---|---|
| **CSS** | `instructor-sessions-redesign.css` (32.9 KB) |
| **Components** | Create/edit form (title, description, level, date, time, duration, fee, capacity, Quran portion, banner upload), session cards with status chips, action buttons (Edit / Delete / Start / Complete), enrollment counts, Zoom link display |
| **Conditional UI** | Edit is offered only for `SCHEDULED`; Start requires ≥1 approved enrollment; Complete appears only for `ONGOING` |
| **States** | Empty · list · edit mode (`?editId=`) · 5 flash flags · error banner |
| **Missing UI** | The `togglepassword` action has no control |

### 4.3 `instructor/evaluations.jsp`

| | |
|---|---|
| **CSS** | `instructor-evaluations-redesign.css` (16.3 KB) |
| **Components** | Two tabs — **Active Evaluations** (`evaluation_reviewed_at IS NULL`) and **Reviewed Sessions** — recitation cards with an audio player, an **Analyze with AI** button, the AI report panel (transcript, differences, tajwīd notes, suggested score), score input 0–100, feedback textarea, Mark Reviewed / Reopen |
| **AI states** | Not analyzed · analyzing · `OK` (report) · `REJECTED` · `CANNOT_EVALUATE` · `FAILED` |
| **Critical UX fact** | The AI report is **session-scoped**. Navigating away or timing out loses it permanently, and the score field is not auto-filled |
| **Locked state** | An evaluated recitation shows the saved score read-only — grading is one-shot |

### 4.4 `instructor/payments.jsp`

| | |
|---|---|
| **CSS** | `instructor-payments-dashboard.css` (29.1 KB), `instructor-financial-dashboard.css` |
| **Components** | Payment-settings card (QR upload with preview, remove QR, bank name, account holder), pending-verification queue with receipt thumbnails, a receipt lightbox, Approve / Reject buttons, a required rejection-reason modal, revenue summary |
| **States** | Settings incomplete (students cannot pay) · settings saved · queue empty · pending items · approving · rejecting (reason required) |
| **This is the only place in the system where money is authorised** |

### 4.5 `instructor/payment_settings.jsp`
Legacy page; the servlet redirects to `/instructor/payments`.

### 4.6 `instructor/profile.jsp`
Name prefix, full name, phone, qualification text, photo upload. **The `zoomEmail` parameter is accepted by the servlet but has no form field** — an instructor cannot set their own Zoom host email through the UI.

### 4.7 `instructor/live-embed.jsp`
Zoom SDK container with `role=1` plus the ZAK token, so the instructor can *start* the meeting.

---

## 5. ADMIN PAGES (7)

Admin uses its own visual shell — `admin-package.css` (73.5 KB), `admin-ui-refine.css` (33.8 KB), `admin-shell-override.css` (23.5 KB), `admin-dashboard-saas.css` (18.7 KB) — a denser, SaaS-console aesthetic distinct from the student and instructor shells.

### 5.1 `admin/dashboard.jsp`
KPI tiles (users by role, sessions by status, enrollments, payments, recitations, evaluations), animated counters (`admin-dashboard-counters.js`), quick links. **Known defect:** the payment chips query legacy `SUCCESS`/`FAILED` statuses and always render 0 (doc 02 A2).

### 5.2 `admin/users/list.jsp`
Search + role filter, table with avatar/name/email/role/status/created, row actions (Edit / Toggle / Delete). Capped at `LIMIT 100` with **no pagination**. Destructive actions use confirm modals.

### 5.3 `admin/users/create.jsp` / `edit.jsp`
Create sets name, email, phone, password, role. Edit shows role read-only. **The create form declares `minlength="6"` while the server requires 8** — a genuine mismatch.

### 5.4 `admin/instructor_verification.jsp`
Pending-instructor cards with bio, qualification-PDF link (signed 300 s URL), Approve / Reject. **No rejection-reason field** — the action captures nothing.

### 5.5 `admin/payments.jsp`
The most complex admin page: 10 filters, 6 sort columns, 25-per-page pagination, an AJAX detail drawer (`?ajax=detail`) showing the verification history, and 12 export variants. **Read-only — no approve/reject/refund control exists anywhere on this page.** `admin-payments-control.css` (11.8 KB), `admin-payments-dashboard.css`.

### 5.6 `admin/reports.jsp`
Report-type selector (6 types), date range, status/role/mode filters, a results table, and CSV/PDF export.

### 5.7 `admin/logs.jsp`
Audit trail filtered by action, role, and date. `LIMIT 100`, newest first. Only the 7 audited actions ever appear.

### 5.8 `admin/profile.jsp`
Name/phone and password change. Email read-only.

---

## 6. SHARED PAGE

`common/notifications.jsp` — instructor and admin notification list. Read-only, identical constraints to the student version.

---

## 7. UI STATE CATALOGUE

| State | How it is expressed | Mobile equivalent |
|---|---|---|
| **Loading** | Skeleton cards, spinners, disabled submit buttons | Shimmer placeholders |
| **Empty** | Illustrated empty cards with a suggested action | Empty-state widget with CTA |
| **Error** | Red banner from a `?error=` / `?errorMessage=` query parameter | SnackBar or inline error |
| **Success** | Green banner from `?success=` / `?notice=` / `?saved=1` | SnackBar |
| **Validation** | Inline field messages, HTML5 constraint bubbles | `TextFormField` validators |
| **Locked** | Disabled controls with an explanation (payment awaiting verification, recitation already evaluated) | Disabled widget + helper text |
| **Forbidden** | Redirect to `/dashboard?forbidden=1` | Route guard |
| **Expired** | Redirect to `/auth/login?expired=1` | Token-refresh or re-login prompt |
| **Pending approval** | Full-page `pendingApproval.jsp` interstitial | Gate screen |

**Migration-relevant:** flash messages are carried in **query parameters on a redirect**, not in a response body. A JSON API must return them in the payload instead.

---

## 8. MOBILE CONVERSION MAP

| Web page | Mobile screen | Nav location | API needed | Tables | Notes |
|---|---|---|---|---|---|
| `home.jsp` | Onboarding / Splash | Pre-auth | Public stats | `user`, `tasmi_session`, `evaluation` | Simplify heavily |
| `login.jsp` | Login | Pre-auth | `POST /auth/login` → **token** | `user`, `instructor` | Add biometric unlock |
| `register.jsp` | Register (2-step wizard) | Pre-auth | `POST /auth/register` | `user`, `student`/`instructor` | Split role → details |
| `verify_email.jsp` | OTP entry | Pre-auth | verify + resend | `email_verification` | Auto-read SMS/email code |
| `forgot`/`reset` | Password recovery | Pre-auth | forgot + reset | `password_reset` | Deep link the reset URL |
| `pendingApproval.jsp` | Verification pending gate | Post-auth | status check | `instructor` | Blocking screen |
| **student/dashboard** | **Home tab** | Tab 1 | dashboard summary | many | Card feed |
| **student/sessions** | **Browse tab** | Tab 2 | list + enroll | `tasmi_session`, `enrollment` | Filter sheet, pull-to-refresh |
| `session_confirm` | Enroll bottom sheet | Modal | enroll | `enrollment`, `payment` | |
| **student/enrollments** | **My Sessions tab** | Tab 3 | list + join | `enrollment`, `payment` | Status chips |
| `student/payments` | Payments | Under My Sessions | list | `payment` | |
| **student/qr_payment** | **Payment / Receipt upload** | Modal flow | QR details + upload | `payment`, `instructor_payment_settings` | **Camera + compression** |
| `payment_receipt` | Receipt viewer | Detail | receipt | `payment` | Native PDF share |
| **student/recitations** | **Recitation Studio** | Tab 4 | submit + history | `recitation`, `evaluation` | **Native recorder** |
| `student/progress` | Progress | Under Home | progress | `progress`, `evaluation` | Charts |
| **student/quran_library** | **Quran module (multi-screen)** | Tab 5 | 11 library APIs | — | **Largest conversion** |
| `quran_assistant.jspf` | AI Assistant | Within Quran module | chat + voice | — | Streaming recommended |
| `student/notifications` | Notifications | Bell icon | list | `notifications` | Needs `is_read` to be useful |
| `student/profile` | Profile | Drawer | profile CRUD | `user` | |
| `student/live-embed` | Live session | Full-screen | Zoom signature | `tasmi_session`, `attendance` | **Native Zoom SDK** |
| **instructor/dashboard** | **Cockpit tab** | Tab 1 | summary + calendar + heartbeat | many | Replace polling with push |
| **instructor/sessions** | **Sessions tab** | Tab 2 | session CRUD | `tasmi_session` | Date/time pickers |
| **instructor/evaluations** | **Evaluate tab** | Tab 3 | queue + analyze + save | `recitation`, `evaluation` | **Persist AI results** |
| **instructor/payments** | **Payments tab** | Tab 4 | settings + verify | `payment`, `instructor_payment_settings` | Receipt zoom viewer |
| `instructor/profile` | Profile | Drawer | profile | `user`, `instructor` | **Add the missing Zoom email field** |
| `instructor/live-embed` | Host live session | Full-screen | signature + ZAK | `tasmi_session` | **Native Zoom SDK** |
| **admin/dashboard** | **Admin home** | Tab 1 | KPIs | many | **Fix the legacy-status defect** |
| **admin/users** | **Users tab** | Tab 2 | user CRUD | `user`, `student`, `instructor` | **Add pagination** |
| **admin/instructor_verification** | **Verification tab** | Tab 3 | approve/reject | `instructor` | PDF viewer |
| **admin/payments** | **Payments tab** | Tab 4 | filtered list + detail | `payment` | Read-only, paginated |
| `admin/reports` | Reports | Drawer | report generation | many | Share exports |
| `admin/logs` | Audit logs | Drawer | logs | `audit_log` | **Add pagination** |
| `admin/profile` | Profile | Drawer | profile | `user` | |

### Proposed navigation

| Role | Bottom tabs | Drawer |
|---|---|---|
| **Student** | Home · Browse · My Sessions · Recite · Quran | Profile, Payments, Progress, Notifications, Settings |
| **Instructor** | Cockpit · Sessions · Evaluate · Payments | Profile, Notifications, Settings |
| **Admin** | Dashboard · Users · Verification · Payments | Reports, Audit Logs, Profile, Settings |

### Web functionality with no natural mobile equivalent

| Item | Recommendation |
|---|---|
| Locale URL prefixes (`/en`, `/ar`, `/ms`) | Replace with an in-app locale setting sent as `Accept-Language` |
| Query-parameter flash messages | Return messages in the JSON response body |
| Server-rendered PDF receipt | Keep server-side generation; the app downloads and shares the file |
| 12 CSV/PDF export variants | Generate server-side, deliver via a share sheet |
| 30-second heartbeat polling | Push notification or a much longer interval |
| Zoom **Web** SDK | Native Zoom Meeting SDK for Android/iOS, or hand off to the Zoom app |
| 2 MB of CSS across 62 files | Rebuilt as a Flutter `ThemeData` from the token table in §1.1 |
