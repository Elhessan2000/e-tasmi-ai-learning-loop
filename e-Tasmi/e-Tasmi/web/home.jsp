<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
  <% String ctx=request.getContextPath(); int statStudents=request.getAttribute("statStudents")==null ? 0 : (Integer)
    request.getAttribute("statStudents"); long statSessions=0L; Object
    statSessionsObj=request.getAttribute("statSessions"); if (statSessionsObj instanceof Long) statSessions=(Long)
    statSessionsObj; else if (statSessionsObj instanceof Integer) statSessions=((Integer) statSessionsObj).longValue();
    Integer statCompletionPct=(Integer) request.getAttribute("statCompletionPct"); %>
    <!DOCTYPE html>
    <html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">

    <head>
      <%@ include file="/jsp/common/etasmi_i18n_head.jspf" %>
      <%@ include file="/jsp/common/etasmi_theme_init.jspf" %>
      <meta charset="UTF-8" />
      <title data-i18n="meta.siteTitle">e-Tasmi — Quran recitation &amp; Tasmi sessions</title>
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <link rel="alternate" hreflang="en" href="<%= request.getScheme() %>://<%= request.getServerName() %><%= request.getContextPath() %>/en/home">
      <link rel="alternate" hreflang="ar" href="<%= request.getScheme() %>://<%= request.getServerName() %><%= request.getContextPath() %>/ar/home">
      <link rel="alternate" hreflang="ms" href="<%= request.getScheme() %>://<%= request.getServerName() %><%= request.getContextPath() %>/ms/home">
      <link rel="alternate" hreflang="x-default" href="<%= request.getScheme() %>://<%= request.getServerName() %><%= request.getContextPath() %>/en/home">
      <link rel="apple-touch-icon" sizes="180x180" href="<%= ctx %>/learnhub-dist/assets/images/apple-touch-icon.png">
      <link rel="icon" type="image/png" sizes="32x32" href="<%= ctx %>/learnhub-dist/assets/images/favicon-32x32.png">
      <link rel="icon" type="image/png" sizes="16x16" href="<%= ctx %>/learnhub-dist/assets/images/favicon-16x16.png">
      <link rel="manifest" href="<%= ctx %>/learnhub-dist/assets/site.webmanifest">

      <script type="module" crossorigin src="<%= ctx %>/learnhub-dist/assets/js/index.js"></script>
      <link rel="stylesheet" crossorigin href="<%= ctx %>/learnhub-dist/assets/css/index.css">
      <link rel="stylesheet" href="<%= ctx %>/css/etasmi-theme.css?v=20260609-theme-unified">
      <link rel="stylesheet" href="<%= ctx %>/css/etasmi-dark-polish.css?v=20260609-theme-unified">
      <link rel="stylesheet" href="<%= ctx %>/css/home-landing-bridge.css?v=20261005-instructors2">
      <link rel="stylesheet" href="<%= ctx %>/css/home-scroll-experience.css?v=20260614-scroll">
      <script defer src="<%= ctx %>/assets/js/etasmi-theme.js?v=20260609-theme-unified"></script>
      <script defer src="<%= ctx %>/assets/js/home-scroll-reveal.js?v=20260614-scroll"></script>
    </head>

    <body>
      <!-- Navbar Start -->
      <nav class="navbar navbar-expand-lg bg-white shadow-sm sticky-top etasmi-landing-navbar">
        <div class="container-xxl">
          <a class="navbar-brand d-inline-flex gap-2 align-items-center lh-1 flex-shrink-0" href="<%= LocaleSupport.localizedUrl(request, "/home") %>">
            <span class="text-primary"><svg xmlns="http://www.w3.org/2000/svg" width="28" height="28"
                viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-book">
                <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                <path d="M3 19a9 9 0 0 1 9 0a9 9 0 0 1 9 0" />
                <path d="M3 6a9 9 0 0 1 9 0a9 9 0 0 1 9 0" />
                <path d="M3 6l0 13" />
                <path d="M12 6l0 13" />
                <path d="M21 6l0 13" />
              </svg></span>
            <span class="fw-bold">e-Tasmi</span>
          </a>
          <button class="navbar-toggler" type="button" data-bs-toggle="collapse"
            data-bs-target="#navbarSupportedContent" aria-controls="navbarSupportedContent" aria-expanded="false"
            aria-label="Toggle navigation" data-i18n="nav.toggleNav" data-i18n-attr="aria-label">
            <span class="navbar-toggler-icon"></span>
          </button>
          <div class="collapse navbar-collapse" id="navbarSupportedContent">
            <ul class="navbar-nav mb-2 mb-lg-0 etasmi-landing-navbar__nav">
              <li class="nav-item"><a href="#hero" class="nav-link" data-i18n="nav.home">Home</a></li>
              <li class="nav-item"><a href="#courses" class="nav-link" data-i18n="nav.sessions">Sessions</a></li>
              <li class="nav-item"><a href="#mentor" class="nav-link" data-i18n="nav.instructors">Instructors</a></li>
              <li class="nav-item"><a href="#group" class="nav-link" data-i18n="nav.community">Community</a></li>
              <li class="nav-item"><a href="#about" class="nav-link" data-i18n="nav.about">About</a></li>
              <li class="nav-item"><a href="#pricing" class="nav-link" data-i18n="nav.enrolment">Enrolment</a></li>
            </ul>
            <div class="d-flex flex-shrink-0 flex-wrap gap-2 gap-lg-3 align-items-center etasmi-landing-navbar__actions ms-lg-auto">
              <div class="etasmi-language-switcher" data-language-switcher>
                <label class="etasmi-language-switcher__label" for="etasmi-site-language" data-i18n="common.language">Language</label>
                <select id="etasmi-site-language" class="etasmi-language-switcher__select" name="language"
                  aria-label="Switch language" data-i18n="common.switchLanguage" data-i18n-attr="aria-label">
                  <option value="en" lang="en">English</option>
                  <option value="ar" lang="ar">العربية</option>
                  <option value="ms" lang="ms">Bahasa Melayu</option>
                </select>
              </div>
              <button type="button" class="etasmi-theme-toggle" data-etasmi-theme-toggle
                aria-label="Switch to dark mode" aria-pressed="false" title="Switch to dark mode"
                data-i18n="theme.switchToDark" data-i18n-attr="aria-label,title">
                <svg class="etasmi-theme-toggle__moon" viewBox="0 0 24 24" fill="none"
                  xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <path d="M20 15.5A7.5 7.5 0 0 1 8.5 4a8.5 8.5 0 1 0 11.5 11.5Z" stroke="currentColor"
                    stroke-width="1.8" stroke-linejoin="round" />
                </svg>
                <svg class="etasmi-theme-toggle__sun" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"
                  aria-hidden="true">
                  <circle cx="12" cy="12" r="4.2" stroke="currentColor" stroke-width="1.8" />
                  <path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M5.6 18.4L7 17M17 7l1.4-1.4"
                    stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
                </svg>
              </button>
              <a href="<%= LocaleSupport.localizedUrl(request, "/auth/login") %>" class="btn btn-light" data-i18n="nav.login">Login</a>
              <a href="<%= LocaleSupport.localizedUrl(request, "/auth/register") %>" class="btn btn-primary" data-i18n="nav.getStarted">Get Started</a>
            </div>
          </div>
        </div>
      </nav>

      <section class="py-lg-13 py-8 bg-white position-relative" id="hero">
        <div class="circle-bg d-none d-lg-block"></div>
        <div class="container-xxl ">
          <div class="row align-items-center gy-8">
            <div class="col-lg-6" data-reveal="left">
              <span
                class="badge bg-primary bg-opacity-10 text-primary px-4 py-3 fw-normal border border-primary rounded-pill">
                <span><svg xmlns="http://www.w3.org/2000/svg" width="8" height="8" viewBox="0 0 24 24"
                    fill="currentColor" class="icon icon-tabler icons-tabler-filled icon-tabler-circle">
                    <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                    <path d="M7 3.34a10 10 0 1 1 -4.995 8.984l-.005 -.324l.005 -.324a10 10 0 0 1 4.995 -8.336z" />
                  </svg></span>
                <span class="etasmi-badge-gap" data-i18n="hero.badge">Live Tasmi sessions</span></span>
              <h1 class="display-4 fw-bold  mt-4">
                <span data-i18n="hero.titleLead">Memorise the Quran with</span>
                <span class="text-primary" data-i18n="hero.titleHighlight">guided online Tasmi,</span>
                <span data-i18n="hero.titleTail">step by step</span>
              </h1>

              <p class="my-6 lead fw-normal" data-i18n="hero.subtitle">
                Join live Tasmi sessions, submit recitations, and receive instructor feedback in one platform.
              </p>
              <div class="d-flex flex-md-row flex-column justify-content-start   gap-3">
                <a href="<%= LocaleSupport.localizedUrl(request, "/auth/login") %>" class="btn btn-primary">

                  <span data-i18n="hero.ctaLogin">Login</span>
                  <span>
                    <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none"
                      stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                      class="icon icon-tabler icons-tabler-arrow-right etasmi-icon-inline-after etasmi-icon-flip-inline">
                      <line x1="5" y1="12" x2="19" y2="12"></line>
                      <line x1="13" y1="18" x2="19" y2="12"></line>
                      <line x1="13" y1="6" x2="19" y2="12"></line>
                    </svg>
                  </span>
                </a>
              </div>
              <div class="etasmi-hero-metrics mt-8">
                <div class="d-flex align-items-center gap-2">
                  <span>
                    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none"
                      stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                      class="icon icon-tabler icons-tabler-users text-primary">
                      <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                      <circle cx="9" cy="7" r="4" />
                      <path d="M3 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2" />
                      <path d="M16 3.13a4 4 0 0 1 0 7.75" />
                      <path d="M21 21v-2a4 4 0 0 0 -3 -3.85" />
                    </svg>
                  </span>
                  <small class="mb-0" data-i18n="metrics.students" data-i18n-count="<%= statStudents %>"><span class="fw-bold etasmi-metric-value">
                      <%= statStudents %>
                    </span> Students</small>
                </div>
                <div class="d-flex align-items-center gap-2">
                  <span>
                    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none"
                      stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                      class="icon icon-tabler icons-tabler-outline icon-tabler-book text-primary">
                      <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                      <path d="M3 19a9 9 0 0 1 9 0a9 9 0 0 1 9 0" />
                      <path d="M3 6a9 9 0 0 1 9 0a9 9 0 0 1 9 0" />
                      <path d="M3 6l0 13" />
                      <path d="M12 6l0 13" />
                      <path d="M21 6l0 13" />
                    </svg>

                  </span>
                  <small class="mb-0" data-i18n="metrics.sessions" data-i18n-count="<%= statSessions %>"><span class="fw-bold etasmi-metric-value">
                      <%= statSessions %>
                    </span> Sessions</small>
                </div>
                <div class="d-flex align-items-center gap-2">
                  <span>
                    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none"
                      stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                      class="icon icon-tabler icons-tabler-outline icon-tabler-award text-primary">
                      <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                      <path d="M12 9m-6 0a6 6 0 1 0 12 0a6 6 0 1 0 -12 0" />
                      <path d="M12 15l3.4 5.89l1.598 -3.233l3.598 .232l-3.4 -5.889" />
                      <path d="M6.802 12l-3.4 5.89l3.598 -.233l1.598 3.232l3.4 -5.889" />
                    </svg>

                  </span>
                  <small class="mb-0" data-i18n="metrics.avgScore" data-i18n-value="<%= statCompletionPct !=null ? statCompletionPct + "%" : "—" %>"><span class="fw-bold etasmi-metric-value">
                      <%= statCompletionPct !=null ? statCompletionPct + "%" : "—" %>
                    </span> Avg. score</small>
                </div>



              </div>
            </div>
            <div class="col-lg-6" data-reveal="right">
              <div class="card p-3 rounded-5  shadow-sm">
                <div class="position-relative">
                  <img
                    src="https://res.cloudinary.com/dd5yqvxhf/image/upload/f_auto,q_auto,c_limit,w_1600,dpr_auto/etasmi/landing/home-hero.jpg"
                    alt="Online Tasmi and Qur&apos;an recitation instruction" data-i18n="hero.heroImageAlt" data-i18n-attr="alt"
                    class="rounded-5 img-fluid etasmi-landing-hero-photo" sizes="(max-width: 991px) 100vw, 50vw"
                    decoding="async" fetchpriority="high" />

                  <div class="position-absolute bottom-0 start-0  ms-n8 mb-n8">
                    <div class="bg-white shadow-sm rounded-pill d-flex align-items-center gap-2 px-3 py-2  mb-4 border"
                      style="width: 170px;">
                      <div
                        class="icon-shape icon-md rounded-circle bg-primary bg-opacity-10 text-primary d-flex align-items-center justify-content-center">
                        <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none"
                          stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                          class="icon icon-tabler icons-tabler-outline icon-tabler-book text-primary">
                          <path stroke="none" d="M0 0h24v24H0z" fill="none"></path>
                          <path d="M3 19a9 9 0 0 1 9 0a9 9 0 0 1 9 0"></path>
                          <path d="M3 6a9 9 0 0 1 9 0a9 9 0 0 1 9 0"></path>
                          <path d="M3 6l0 13"></path>
                          <path d="M12 6l0 13"></path>
                          <path d="M21 6l0 13"></path>
                        </svg>
                      </div>
                      <div class="d-flex flex-column text-xs lh-sm">
                        <span class="fw-bold" data-i18n="hero.openSessions">Open sessions</span>
                        <span data-i18n="hero.onCatalogue">On catalogue</span>
                      </div>

                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
      <section class="py-lg-13 py-8" id="courses">
        <div class="container-xxl">
          <div class="row text-center">
            <div class="col-lg-6 mx-auto">
              <div class="mb-10" data-reveal="fade">
                <span class="text-primary text-uppercase small fw-semibold"
                  style="letter-spacing: .125rem;" data-i18n="landing.sessions.eyebrow">Sessions</span>
                <h2 class="fw-bold mt-4 mb-4"><span data-i18n="landing.sessions.title">Explore Guided</span> <span class="text-primary" data-i18n="landing.sessions.titleHighlight">Tasmi Sessions</span></h2>
                <p class="mb-0 etasmi-session-lead" data-i18n="landing.sessions.lead">
                  How e‑Tasmi supports guided Qur&apos;an recitation—four session-style categories to organise your
                  pathway on the platform.
                </p>
              </div>
            </div>
          </div>
          <div>
            <div class="row g-4" data-reveal-group>
              <!-- Session category card 1 -->
              <div class="col-lg-3 col-md-6" data-reveal>
                <div class="card shadow-sm h-100 rounded-5 border-0 etasmi-session-card">
                  <div
                    class="position-relative overflow-hidden rounded-top-5 etasmi-session-card-cover etasmi-session-card-cover--infographic">
                    <img
                      src="https://res.cloudinary.com/dd5yqvxhf/image/upload/f_auto,q_auto,c_limit,w_728,h_546,dpr_auto/etasmi/landing/sessions/tajwid-quran"
                      alt="Tajweed learning card: makhraj and pronunciation guide for Arabic letter Hā&apos; (ح)"
                      data-i18n="landing.sessions.tajwid.imageAlt" data-i18n-attr="alt"
                      class="etasmi-session-card-img etasmi-session-card-img--contain rounded-top-5" width="728"
                      height="546" sizes="(max-width: 767px) 100vw, (max-width: 991px) 50vw, 25vw" loading="lazy"
                      decoding="async" />
                    <div class="position-absolute top-0 start-0 p-3">
                      <span class="badge bg-primary rounded-pill fw-semibold text-white text-xs" data-i18n="landing.sessions.tajwid.badge">Tajwid</span>
                    </div>
                  </div>
                  <div class="card-body p-6 d-flex flex-column etasmi-session-card-body">
                    <h3 class="etasmi-session-card-title mb-2" data-i18n="landing.sessions.tajwid.title">Guided Tajwid Practice</h3>
                    <p class="etasmi-session-card-desc mb-3 mb-md-4" data-i18n="landing.sessions.tajwid.desc">
                      Build clear pronunciation and Tajweed awareness with guided drills, rule reminders, and
                      instructor-led correction.
                    </p>
                    <div class="d-flex flex-wrap gap-2 mb-2">
                      <span class="badge rounded-pill etasmi-session-tag" data-i18n="landing.sessions.tajwid.tagLive">Live Session</span>
                      <span class="badge rounded-pill etasmi-session-tag" data-i18n="landing.sessions.tajwid.tagFeedback">Feedback</span>
                      <span class="badge rounded-pill etasmi-session-tag" data-i18n="landing.sessions.tajwid.tagProgress">Progress tracking</span>
                    </div>
                    <div class="mt-auto pt-3">
                      <a href="<%= LocaleSupport.localizedUrl(request, "/auth/register") %>" class="btn btn-outline-primary btn-sm rounded-pill w-100" data-i18n="landing.sessions.tajwid.cta">
                        Get started
                      </a>
                    </div>
                  </div>
                </div>
              </div>
              <!-- Session category card 2 -->
              <div class="col-lg-3 col-md-6" data-reveal>
                <div class="card shadow-sm h-100 rounded-5 border-0 etasmi-session-card">
                  <div class="position-relative overflow-hidden rounded-top-5 etasmi-session-card-cover">
                    <img
                      src="https://res.cloudinary.com/dd5yqvxhf/image/upload/f_auto,q_auto,c_fill,g_auto,w_728,h_546,dpr_auto/etasmi/landing/sessions/hifz-murajaah"
                      alt="Learner reciting from the Qur&apos;an during a live online session"
                      data-i18n="landing.sessions.hifz.imageAlt" data-i18n-attr="alt"
                      class="etasmi-session-card-img rounded-top-5" width="728" height="546"
                      sizes="(max-width: 767px) 100vw, (max-width: 991px) 50vw, 25vw" loading="lazy" decoding="async" />
                    <div class="position-absolute top-0 start-0 p-3">
                      <span class="badge text-bg-warning rounded-pill fw-semibold text-xs" data-i18n="landing.sessions.hifz.badge">Hifz</span>
                    </div>
                  </div>
                  <div class="card-body p-6 d-flex flex-column etasmi-session-card-body">
                    <h3 class="etasmi-session-card-title mb-2" data-i18n="landing.sessions.hifz.title">Hifz &amp; Muraja&apos;ah Support</h3>
                    <p class="etasmi-session-card-desc mb-3 mb-md-4" data-i18n="landing.sessions.hifz.desc">
                      Strengthen memorisation and paced revision alongside your teacher—with listening checks and
                      repeatable routines.
                    </p>
                    <div class="d-flex flex-wrap gap-2 mb-2">
                      <span class="badge rounded-pill etasmi-session-tag" data-i18n="landing.sessions.hifz.tagQr">QR enrolment</span>
                      <span class="badge rounded-pill etasmi-session-tag" data-i18n="landing.sessions.hifz.tagUpload">Recitation upload</span>
                      <span class="badge rounded-pill etasmi-session-tag" data-i18n="landing.sessions.hifz.tagProgress">Progress tracking</span>
                    </div>
                    <div class="mt-auto pt-3">
                      <a href="<%= LocaleSupport.localizedUrl(request, "/auth/register") %>" class="btn btn-outline-primary btn-sm rounded-pill w-100" data-i18n="landing.sessions.hifz.cta">
                        Create account
                      </a>
                    </div>
                  </div>
                </div>
              </div>
              <!-- Session category card 3 -->
              <div class="col-lg-3 col-md-6" data-reveal>
                <div class="card shadow-sm h-100 rounded-5 border-0 etasmi-session-card">
                  <div class="position-relative overflow-hidden rounded-top-5 etasmi-session-card-cover">
                    <img
                      src="https://res.cloudinary.com/dd5yqvxhf/image/upload/f_auto,q_auto,c_fill,g_auto,w_728,h_546,dpr_auto/etasmi/landing/sessions/live-tasmi-call"
                      alt="Live Zoom-style video session for guided Tasmi between learner and instructor"
                      data-i18n="landing.sessions.live.imageAlt" data-i18n-attr="alt"
                      class="etasmi-session-card-img rounded-top-5" width="728" height="546"
                      sizes="(max-width: 767px) 100vw, (max-width: 991px) 50vw, 25vw" loading="lazy" decoding="async" />
                    <div class="position-absolute top-0 start-0 p-3">
                      <span class="badge bg-info rounded-pill fw-semibold text-white text-xs" data-i18n="landing.sessions.live.badge">Live</span>
                    </div>
                  </div>
                  <div class="card-body p-6 d-flex flex-column etasmi-session-card-body">
                    <h3 class="etasmi-session-card-title mb-2" data-i18n="landing.sessions.live.title">Live Zoom Tasmi Sessions</h3>
                    <p class="etasmi-session-card-desc mb-3 mb-md-4" data-i18n="landing.sessions.live.desc">
                      Attend scheduled Tasmi blocks over live video—recite, listen, and adjust in real time with your
                      instructor.
                    </p>
                    <div class="d-flex flex-wrap gap-2 mb-2">
                      <span class="badge rounded-pill etasmi-session-tag" data-i18n="landing.sessions.live.tagLive">Live Session</span>
                      <span class="badge rounded-pill etasmi-session-tag" data-i18n="landing.sessions.live.tagFeedback">Feedback</span>
                      <span class="badge rounded-pill etasmi-session-tag" data-i18n="landing.sessions.live.tagQr">QR enrolment</span>
                    </div>
                    <div class="mt-auto pt-3">
                      <a href="#pricing" class="btn btn-outline-primary btn-sm rounded-pill w-100" data-i18n="landing.sessions.live.cta">
                        Explore more
                      </a>
                    </div>
                  </div>
                </div>
              </div>
              <!-- Session category card 4 -->
              <div class="col-lg-3 col-md-6" data-reveal>
                <div class="card shadow-sm h-100 rounded-5 border-0 etasmi-session-card">
                  <div class="position-relative overflow-hidden rounded-top-5 etasmi-session-card-cover">
                    <img
                      src="https://res.cloudinary.com/dd5yqvxhf/image/upload/f_auto,q_auto,c_fill,g_auto,w_728,h_546,dpr_auto/etasmi/landing/sessions/review-progress"
                      alt="Progress overview with recitation metrics, feedback, and next steps"
                      data-i18n="landing.sessions.review.imageAlt" data-i18n-attr="alt"
                      class="etasmi-session-card-img rounded-top-5" width="728" height="546"
                      sizes="(max-width: 767px) 100vw, (max-width: 991px) 50vw, 25vw" loading="lazy" decoding="async" />
                    <div class="position-absolute top-0 start-0 p-3">
                      <span class="badge bg-success rounded-pill fw-semibold text-white text-xs" data-i18n="landing.sessions.review.badge">Review</span>
                    </div>
                  </div>
                  <div class="card-body p-6 d-flex flex-column etasmi-session-card-body">
                    <h3 class="etasmi-session-card-title mb-2" data-i18n="landing.sessions.review.title">Recitation Review &amp; Progress</h3>
                    <p class="etasmi-session-card-desc mb-3 mb-md-4" data-i18n="landing.sessions.review.desc">
                      Submit recordings, read instructor remarks, and see trends that show where your recitation is
                      improving.
                    </p>
                    <div class="d-flex flex-wrap gap-2 mb-2">
                      <span class="badge rounded-pill etasmi-session-tag" data-i18n="landing.sessions.review.tagUpload">Recitation upload</span>
                      <span class="badge rounded-pill etasmi-session-tag" data-i18n="landing.sessions.review.tagFeedback">Feedback</span>
                      <span class="badge rounded-pill etasmi-session-tag" data-i18n="landing.sessions.review.tagProgress">Progress tracking</span>
                    </div>
                    <div class="mt-auto pt-3">
                      <a href="<%= LocaleSupport.localizedUrl(request, "/auth/register") %>" class="btn btn-outline-primary btn-sm rounded-pill w-100" data-i18n="landing.sessions.review.cta">
                        Get started
                      </a>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div class="row">
              <div class="col-12 text-center mt-10" data-reveal="fade">
                <a href="<%= LocaleSupport.localizedUrl(request, "/auth/register") %>" class="btn btn-outline-secondary rounded-pill px-4">
                  <span data-i18n="landing.sessions.ctaAccount">Create your account</span>
                  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none"
                    stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                    class="icon icon-tabler icons-tabler-arrow-right ms-1 align-text-bottom">
                    <line x1="5" y1="12" x2="19" y2="12"></line>
                    <line x1="13" y1="18" x2="19" y2="12"></line>
                    <line x1="13" y1="6" x2="19" y2="12"></line>
                  </svg>
                </a>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="py-lg-13 py-8 bg-light bg-opacity-25 etasmi-blend" id="mentor">
        <div class="container-xxl">
          <div class="row text-center">
            <div class="col-lg-5 mx-auto">
              <div class="mb-10" data-reveal="fade">
                <span class="text-primary text-uppercase small fw-semibold"
                  style="letter-spacing: .125rem;" data-i18n="landing.instructors.eyebrow">Instructors</span>
                <h2 class="fw-bold mt-4 mb-4"><span data-i18n="landing.instructors.title">Learn Quran with</span> <span class="text-primary" data-i18n="landing.instructors.titleHighlight">Qualified Instructors</span>
                </h2>
                <p class="mb-0" data-i18n="landing.instructors.lead">
                  Experienced teachers in Tajwīd, Hifdh review, live sessions, and Tasmi—using e‑Tasmi for structured
                  progress and feedback.
                </p>
              </div>
            </div>
          </div>

          <div class="row g-4" data-reveal-group>
            <!-- Instructor card 1 · demo -->
            <div class="col-lg-3 col-md-6 mb-4" data-reveal>
              <div class="card shadow-sm h-100 rounded-5 overflow-hidden border-0 etasmi-instructor-card card-lift">
                <div class="etasmi-instructor-card-media">
                  <img src="<%= ctx %>/assets/img/instructors/instructor-abass.png" alt="Demo instructor avatar"
                    data-i18n="landing.instructors.abass.alt" data-i18n-attr="alt"
                    class="etasmi-instructor-card-img" width="640" height="800"
                    onerror="this.onerror=null;this.style.display='none';this.parentElement.classList.add('is-empty');"
                    sizes="(max-width: 767px) 100vw, (max-width: 991px) 50vw, 25vw" loading="lazy" decoding="async" />
                </div>
                <div class="etasmi-instructor-card-footer">
                  <h3 class="etasmi-instructor-card-name mb-0 text-center" data-i18n="landing.instructors.abass.name">Demo Instructor 1</h3>
                  <p class="etasmi-instructor-card-country mb-0 text-center" data-i18n="landing.instructors.abass.country">Demo profile</p>
                </div>
              </div>
            </div>
            <!-- Instructor card 2 · demo -->
            <div class="col-lg-3 col-md-6 mb-4" data-reveal>
              <div class="card shadow-sm h-100 rounded-5 overflow-hidden border-0 etasmi-instructor-card card-lift">
                <div class="etasmi-instructor-card-media">
                  <img src="<%= ctx %>/assets/img/instructors/instructor-shoaib.png" alt="Demo instructor avatar"
                    data-i18n="landing.instructors.shoaib.alt" data-i18n-attr="alt"
                    class="etasmi-instructor-card-img" width="640" height="800"
                    onerror="this.onerror=null;this.style.display='none';this.parentElement.classList.add('is-empty');"
                    sizes="(max-width: 767px) 100vw, (max-width: 991px) 50vw, 25vw" loading="lazy" decoding="async" />
                </div>
                <div class="etasmi-instructor-card-footer">
                  <h3 class="etasmi-instructor-card-name mb-0 text-center" data-i18n="landing.instructors.shoaib.name">Demo Instructor 2</h3>
                  <p class="etasmi-instructor-card-country mb-0 text-center" data-i18n="landing.instructors.shoaib.country">Demo profile</p>
                </div>
              </div>
            </div>
            <!-- Instructor card 3 · demo -->
            <div class="col-lg-3 col-md-6 mb-4" data-reveal>
              <div class="card shadow-sm h-100 rounded-5 overflow-hidden border-0 etasmi-instructor-card card-lift">
                <div class="etasmi-instructor-card-media">
                  <img src="<%= ctx %>/assets/img/instructors/instructor-ayman.png" alt="Demo instructor avatar"
                    data-i18n="landing.instructors.ayman.alt" data-i18n-attr="alt"
                    class="etasmi-instructor-card-img" width="640" height="800"
                    onerror="this.onerror=null;this.style.display='none';this.parentElement.classList.add('is-empty');"
                    sizes="(max-width: 767px) 100vw, (max-width: 991px) 50vw, 25vw" loading="lazy" decoding="async" />
                </div>
                <div class="etasmi-instructor-card-footer">
                  <h3 class="etasmi-instructor-card-name mb-0 text-center" data-i18n="landing.instructors.ayman.name">Demo Instructor 3</h3>
                  <p class="etasmi-instructor-card-country mb-0 text-center" data-i18n="landing.instructors.ayman.country">Demo profile</p>
                </div>
              </div>
            </div>
            <!-- Instructor card 4 · demo -->
            <div class="col-lg-3 col-md-6 mb-4" data-reveal>
              <div class="card shadow-sm h-100 rounded-5 overflow-hidden border-0 etasmi-instructor-card card-lift">
                <div class="etasmi-instructor-card-media">
                  <img src="<%= ctx %>/assets/img/instructors/instructor-ziaul-haq.png" alt="Demo instructor avatar"
                    data-i18n="landing.instructors.ziaulHaq.alt" data-i18n-attr="alt"
                    class="etasmi-instructor-card-img" width="640" height="800"
                    onerror="this.onerror=null;this.style.display='none';this.parentElement.classList.add('is-empty');"
                    sizes="(max-width: 767px) 100vw, (max-width: 991px) 50vw, 25vw" loading="lazy" decoding="async" />
                </div>
                <div class="etasmi-instructor-card-footer">
                  <h3 class="etasmi-instructor-card-name mb-0 text-center" data-i18n="landing.instructors.ziaulHaq.name">Demo Instructor 4</h3>
                  <p class="etasmi-instructor-card-country mb-0 text-center" data-i18n="landing.instructors.ziaulHaq.country">Demo profile</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="py-lg-13 py-8 position-relative" id="group">
        <div class="circle-bg d-none d-lg-block"></div>
        <div class="container-xxl py-lg-13">
          <div class="row  align-items-center gy-8">
            <div class="col-xl-6" data-reveal="left">
              <div class="mb-10 pe-lg-12">
                <span class="text-primary text-uppercase small fw-semibold"
                  style="letter-spacing: .125rem;" data-i18n="landing.community.eyebrow">Community</span>
                <h2 class="fw-bold mt-4 mb-4"><span data-i18n="landing.community.title">Students, instructors &amp; Tasmi in</span> <span class="text-primary" data-i18n="landing.community.titleHighlight">one
                    place</span></h2>
                <p class="mb-0" data-i18n="landing.community.lead">
                  e‑Tasmi links learners and teachers around guided online Tasmi sessions: enrol, attend, submit
                  recitations, read instructor feedback, and follow your progress toward the next milestone—without
                  switching tools.
                </p>
              </div>
              <div class="row" data-reveal-group>
                <div class="col-md-6">
                  <div class="d-flex gap-4" data-reveal>
                    <div class="icon-shape icon-md bg-primary bg-opacity-10 rounded-circle text-primary flex-shrink-0">
                      <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none"
                        stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                        class="icon icon-tabler icons-tabler-users">
                        <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                        <circle cx="9" cy="7" r="4" />
                        <path d="M3 21v-2a4 4 0 0 1 4 -4h4a4 4 0 0 1 4 4v2" />
                        <path d="M16 3.13a4 4 0 0 1 0 7.75" />
                        <path d="M21 21v-2a4 4 0 0 0 -3 -3.85" />
                      </svg>
                    </div>
                    <div>
                      <h4 class="h6" data-i18n="landing.community.features.studentsInstructors.title">Students &amp; instructors</h4>
                      <p class="mb-0 small" data-i18n="landing.community.features.studentsInstructors.desc">Stay on the same roster: who is teaching, who is memorising, and which
                        session runs next.</p>
                    </div>

                  </div>

                </div>

                <div class="col-md-6 mt-6 mt-md-0">
                  <div class="d-flex gap-4" data-reveal>
                    <div class="icon-shape icon-md bg-primary bg-opacity-10 rounded-circle text-primary flex-shrink-0">
                      <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none"
                        stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                        class="icon icon-tabler icons-tabler-outline icon-tabler-message-circle">
                        <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                        <path
                          d="M3 20l1.3 -3.9c-2.324 -3.437 -1.426 -7.872 2.1 -10.374c3.526 -2.501 8.59 -2.296 11.845 .48c3.255 2.777 3.695 7.266 1.029 10.501c-2.666 3.235 -7.615 4.215 -11.574 2.293l-4.7 1" />
                      </svg>
                    </div>
                    <div>
                      <h4 class="h6" data-i18n="landing.community.features.recitationSubmission.title">Recitation submission</h4>
                      <p class="mb-0 small" data-i18n="landing.community.features.recitationSubmission.desc">When your instructor opens a window, upload audio or files from your
                        dashboard—everything stays tied to the right session.</p>
                    </div>

                  </div>
                </div>


                <div class="col-md-6 mt-6">
                  <div class="d-flex gap-4" data-reveal>
                    <div class="icon-shape icon-md bg-primary bg-opacity-10 rounded-circle text-primary flex-shrink-0">
                      <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none"
                        stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                        class="icon icon-tabler icons-tabler-calendar-event">
                        <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                        <rect x="4" y="5" width="16" height="16" rx="2" />
                        <line x1="16" y1="3" x2="16" y2="7" />
                        <line x1="8" y1="3" x2="8" y2="7" />
                        <line x1="4" y1="11" x2="20" y2="11" />
                        <rect x="8" y="15" width="2" height="2" />
                      </svg>
                    </div>
                    <div>
                      <h4 class="h6" data-i18n="landing.community.features.guidedSessions.title">Guided Tasmi sessions</h4>
                      <p class="mb-0 small" data-i18n="landing.community.features.guidedSessions.desc">Join scheduled listening circles and reviews—live or blended—aligned to each
                        cohort&apos;s goals.</p>
                    </div>
                  </div>
                </div>
                <div class="col-md-6 mt-6">
                  <div class="d-flex gap-4" data-reveal>
                    <div class="icon-shape icon-md bg-primary bg-opacity-10 rounded-circle text-primary flex-shrink-0">
                      <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none"
                        stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                        class="icon icon-tabler icons-tabler-outline icon-tabler-bell-ringing">
                        <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                        <path
                          d="M10 5a2 2 0 0 1 4 0a7 7 0 0 1 4 6v3a4 4 0 0 0 2 3h-16a4 4 0 0 0 2 -3v-3a7 7 0 0 1 4 -6" />
                        <path d="M9 17v1a3 3 0 0 0 6 0v-1" />
                        <path d="M21 6.727a11.05 11.05 0 0 0 -2.794 -3.727" />
                        <path d="M3 6.727a11.05 11.05 0 0 1 2.792 -3.727" />
                      </svg>
                    </div>
                    <div>
                      <h4 class="h6" data-i18n="landing.community.features.instructorFeedback.title">Instructor feedback</h4>
                      <p class="mb-0 small" data-i18n="landing.community.features.instructorFeedback.desc">See remarks, grades, and rubric notes beside each submission so you know
                        exactly what to improve next.</p>
                    </div>
                  </div>
                </div>

                <div class="col-md-6 mt-6">
                  <div class="d-flex gap-4" data-reveal>
                    <div class="icon-shape icon-md bg-primary bg-opacity-10 rounded-circle text-primary flex-shrink-0">
                      <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none"
                        stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                        class="icon icon-tabler icons-tabler-world">
                        <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                        <circle cx="12" cy="12" r="9" />
                        <line x1="3.6" y1="9" x2="20.4" y2="9" />
                        <line x1="3.6" y1="15" x2="20.4" y2="15" />
                        <path d="M12 3a17.3 17.3 0 0 0 0 18" />
                        <path d="M12 3a17.3 17.3 0 0 1 0 18" />
                      </svg>
                    </div>
                    <div>
                      <h4 class="h6" data-i18n="landing.community.features.learningProgress.title">Learning progress</h4>
                      <p class="mb-0 small" data-i18n="landing.community.features.learningProgress.desc">Follow milestones—surah, juz, and session completion—toward Tasmi readiness
                        in one learner timeline.</p>
                    </div>
                  </div>
                </div>


                <div class="col-md-6 mt-6">
                  <div class="d-flex gap-4" data-reveal>
                    <div class="icon-shape icon-md bg-primary bg-opacity-10 rounded-circle text-primary flex-shrink-0">
                      <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none"
                        stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                        class="icon icon-tabler icons-tabler-outline icon-tabler-heart-handshake">
                        <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                        <path d="M19.5 12.572l-7.5 7.428l-7.5 -7.428a5 5 0 1 1 7.5 -6.566a5 5 0 1 1 7.5 6.572" />
                        <path
                          d="M12 6l-3.293 3.293a1 1 0 0 0 0 1.414l.543 .543c.69 .69 1.81 .69 2.5 0l1 -1a3.182 3.182 0 0 1 4.5 0l2.25 2.25" />
                        <path d="M12.5 15.5l2 2" />
                        <path d="M15 13l2 2" />
                      </svg>
                    </div>
                    <div>
                      <h4 class="h6" data-i18n="landing.community.features.madrasahsFamilies.title">Madrasahs &amp; families</h4>
                      <p class="mb-0 small" data-i18n="landing.community.features.madrasahsFamilies.desc">Circles and coordinators share the same view of enrolments, submissions, and
                        who still needs follow-up.</p>
                    </div>
                  </div>
                </div>

              </div>
              <div class="row mt-10">
                <div class="col-lg-12">
                  <a href="<%= LocaleSupport.localizedUrl(request, "/auth/register") %>" class="btn btn-primary" data-i18n="landing.community.cta">Join Community</a>
                </div>

              </div>
            </div>
            <div class="col-xl-6" data-reveal="right">
              <div class="etasmi-community-stage">
                <%-- Ambient glowing rings simulate live recitation audio streaming. --%>
                <div class="etasmi-community-rings" aria-hidden="true">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>

                <%-- Dashboard-style window with a stylised Muslim student (kufi + thobe) reciting. --%>
                <div class="etasmi-community-window" role="img"
                  aria-label="Illustration of a student reciting the Qur'an during a live Tasmi session"
                  data-i18n="landing.community.stage.ariaLabel" data-i18n-attr="aria-label">
                  <div class="etasmi-community-window__bar">
                    <span class="etasmi-community-window__dot"></span>
                    <span class="etasmi-community-window__dot"></span>
                    <span class="etasmi-community-window__dot"></span>
                    <span class="etasmi-community-window__title" data-i18n="landing.community.stage.windowTitle">Live Tasmi · recitation</span>
                    <span class="etasmi-community-window__live">
                      <span class="etasmi-community-window__live-dot"></span><span data-i18n="landing.community.stage.live">LIVE</span>
                    </span>
                  </div>

                  <div class="etasmi-community-window__body">
                    <div class="etasmi-community-figure">
                      <svg viewBox="0 0 260 230" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"
                        class="etasmi-community-figure__svg">
                        <defs>
                          <linearGradient id="ecRobeGrad" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="0%" stop-color="#0f766e" />
                            <stop offset="55%" stop-color="#047857" />
                            <stop offset="100%" stop-color="#065f46" />
                          </linearGradient>
                          <linearGradient id="ecCapGrad" x1="0" y1="0" x2="0" y2="1">
                            <stop offset="0%" stop-color="#10b981" />
                            <stop offset="100%" stop-color="#0f766e" />
                          </linearGradient>
                          <radialGradient id="ecHaloGrad" cx="50%" cy="42%" r="60%">
                            <stop offset="0%" stop-color="rgba(45,212,191,0.28)" />
                            <stop offset="100%" stop-color="rgba(15,118,110,0)" />
                          </radialGradient>
                        </defs>
                        <circle class="ec-halo" cx="130" cy="120" r="92" />
                        <%-- Thobe / jalabiya (robe) --%>
                        <path class="ec-robe"
                          d="M130 138c17 0 25 13 31 38l11 52c-27 11-57 11-84 0l11-52c6-25 14-38 31-38z" />
                        <%-- Folded sleeves --%>
                        <path class="ec-robe-cuff"
                          d="M99 152c-13 7-20 22-21 44l19 1c1-19 5-33 12-42zM161 152c13 7 20 22 21 44l-19 1c-1-19-5-33-12-42z" />
                        <%-- Neck + face --%>
                        <rect class="ec-skin" x="121" y="124" width="18" height="22" rx="8" />
                        <circle class="ec-skin" cx="130" cy="104" r="28" />
                        <%-- Kufi / prayer cap --%>
                        <path class="ec-cap" d="M101 108c0-32 58-32 58 0z" />
                        <path class="ec-cap-band" d="M100 106h60v7a5 5 0 0 1-5 5H105a5 5 0 0 1-5-5z" />
                        <%-- Open mushaf held in front --%>
                        <g class="ec-book">
                          <path class="ec-book-page" d="M130 182l-30-8v30l30 8z" />
                          <path class="ec-book-page" d="M130 182l30-8v30l-30 8z" />
                          <line class="ec-book-line" x1="111" y1="184" x2="124" y2="187" />
                          <line class="ec-book-line" x1="111" y1="192" x2="124" y2="195" />
                          <line class="ec-book-line" x1="136" y1="187" x2="149" y2="184" />
                          <line class="ec-book-line" x1="136" y1="195" x2="149" y2="192" />
                          <line class="ec-book-spine" x1="130" y1="182" x2="130" y2="212" />
                        </g>
                      </svg>
                    </div>

                    <%-- Rhythmic waveform = dynamic audio recitation streaming. --%>
                    <div class="etasmi-community-wave" aria-hidden="true">
                      <span></span><span></span><span></span><span></span><span></span><span></span>
                      <span></span><span></span><span></span><span></span><span></span><span></span>
                      <span></span><span></span><span></span><span></span><span></span>
                    </div>
                    <p class="etasmi-community-caption" data-i18n="landing.community.stage.caption">Reciting · Surah Al-Mulk</p>
                  </div>
                </div>

                <div class="etasmi-community-badge etasmi-community-badge--tr">
                  <span class="etasmi-community-badge__icon">
                    <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none"
                      stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                      <path
                        d="M3 20l1.3 -3.9c-2.324 -3.437 -1.426 -7.872 2.1 -10.374c3.526 -2.501 8.59 -2.296 11.845 .48c3.255 2.777 3.695 7.266 1.029 10.501c-2.666 3.235 -7.615 4.215 -11.574 2.293l-4.7 1" />
                    </svg>
                  </span>
                  <span class="etasmi-community-badge__copy">
                    <span class="fw-bold" data-i18n="landing.community.stage.feedbackBadgeTitle">Instructor feedback</span>
                    <small data-i18n="landing.community.stage.feedbackBadgeSub">On every upload</small>
                  </span>
                </div>

                <div class="etasmi-community-badge etasmi-community-badge--bl">
                  <span class="etasmi-community-badge__icon">
                    <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none"
                      stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                      <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                      <path d="M3 12a9 9 0 1 0 18 0a9 9 0 0 0 -18 0" />
                      <path d="M3.6 9h16.8" />
                      <path d="M3.6 15h16.8" />
                      <path d="M11.5 3a17 17 0 0 0 0 18" />
                      <path d="M12.5 3a17 17 0 0 1 0 18" />
                    </svg>
                  </span>
                  <span class="etasmi-community-badge__copy">
                    <span class="fw-bold" data-i18n="landing.community.stage.progressBadgeTitle">Progress snapshot</span>
                    <small data-i18n="landing.community.stage.progressBadgeSub">Session by session</small>
                  </span>
                </div>
              </div>

            </div>
          </div>
        </div>
      </section>
      <section class="py-lg-13 py-8 bg-light bg-opacity-50 etasmi-blend" id="about">
        <div class="container-xxl">
          <div class="row align-items-center gy-8 gx-lg-5">
            <div class="col-lg-6" data-reveal="left">
              <span class="text-primary text-uppercase small fw-semibold etasmi-about-eyebrow"
                style="letter-spacing: .125rem;" data-i18n="landing.about.eyebrow">About Our History</span>
              <h2 class="fw-bold mt-4 mb-4 etasmi-about-title" data-i18n="landing.about.title">
                Islamic Center For Muslims To Achieve Spiritual Goals
              </h2>
              <p class="etasmi-about-motto fst-italic mb-4" data-i18n="landing.about.motto">
                Our Promise To Uphold The Trust Placed.
              </p>
              <p class="text-muted mb-0" data-i18n="landing.about.body">
                e&#8209;Tasmi began in neighbourhood halaqah circles that tracked recitation on paper timetables and
                whispered checkpoints. We built an integrated, transparent, audited workflow so Malaysian madrasahs,
                families, and state panels share one view&mdash;Surah by Surah, Juz by Juz, milestone by milestone&mdash;without
                losing the sanctity of guided Tasmi.
              </p>
              <a href="<%= LocaleSupport.localizedUrl(request, "/auth/register") %>"
                class="btn btn-primary rounded-pill px-4 mt-6 d-inline-flex align-items-center gap-2">
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="currentColor"
                  aria-hidden="true">
                  <path d="M12 21.35l-1.45-1.32C5.4 15.36 2 12.28 2 8.5 2 5.42 4.42 3 7.5 3c1.74 0 3.41.81 4.5 2.09C13.09 3.81 14.76 3 16.5 3 19.58 3 22 5.42 22 8.5c0 3.78-3.4 6.86-8.55 11.54L12 21.35z" />
                </svg>
                <span data-i18n="landing.about.cta">Discover our mission</span>
              </a>
            </div>
            <div class="col-lg-6" data-reveal="right">
              <div class="etasmi-about-media">
                <div class="etasmi-about-media__glow" aria-hidden="true"></div>
                <div class="etasmi-about-media__frame">
                  <img
                    src="<%= ctx %>/assets/img/landing/about-history.jpg"
                    srcset="<%= ctx %>/assets/img/landing/about-history-800.jpg 800w, <%= ctx %>/assets/img/landing/about-history.jpg 1152w"
                    alt="Guided online Qur&apos;an recitation session with learner and instructor"
                    data-i18n="landing.about.imageAlt" data-i18n-attr="alt"
                    class="img-fluid w-100" width="1200" height="900"
                    sizes="(max-width: 991px) 100vw, 50vw" loading="lazy" decoding="async" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
      <section class="py-lg-13 py-8 " id="pricing">

        <div class="container-xxl">
          <div class="row">
            <div class="col-12">
              <div class="text-center mb-10" data-reveal="fade">
                <span class="text-primary text-uppercase small fw-semibold"
                  style="letter-spacing: .125rem;" data-i18n="landing.pricing.eyebrow">Enrolment</span>
                <h2 class="fw-bold mt-4 mb-4"><span data-i18n="landing.pricing.title">Pay · verify ·</span> <span class="text-primary" data-i18n="landing.pricing.titleHighlight">start Tasmi</span></h2>
                <p class="mx-auto text-muted px-3 mb-0" style="max-width: 40rem;" data-i18n="landing.pricing.lead">
                  Fees stay with your programme—e‑Tasmi is where you register, attach proof of payment when asked, then
                  join sessions once an admin confirms you.
                </p>
              </div>

            </div>

          </div>
          <div class="row justify-content-center">
            <div class="col-lg-10 col-xl-9" data-reveal="fade">
              <div class="card shadow-sm rounded-5 h-100 border-primary overflow-hidden etasmi-lift">
                <div class="card-body p-6 p-lg-8">
                  <div class="row align-items-start gy-6">
                    <div class="col-lg-7">
                      <span class="text-primary small fw-semibold text-uppercase"
                        style="letter-spacing: .125rem;" data-i18n="landing.pricing.flowEyebrow">Enrolment flow</span>
                      <h3 class="fw-bold h3 mt-2 mb-3" data-i18n="landing.pricing.flowTitle">QR or link, receipt, verification—then enrol.</h3>
                      <p class="text-muted mb-5" data-i18n="landing.pricing.flowDesc">
                        Your madrasah, school, or organiser publishes how to pay. Follow their steps outside e‑Tasmi
                        when needed,
                        keep your slip or screenshot, then complete the confirmations inside your new account.
                      </p>
                      <ul class="list-unstyled mb-0 d-flex flex-column gap-4">
                        <li class="d-flex gap-4">
                          <span
                            class="icon-shape icon-lg bg-primary bg-opacity-10 text-primary rounded-3 flex-shrink-0">
                            <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24"
                              fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                              stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-qrcode">
                              <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                              <rect x="4" y="4" width="4" height="4" rx="1" />
                              <rect x="16" y="4" width="4" height="4" rx="1" />
                              <rect x="4" y="16" width="4" height="4" rx="1" />
                              <path d="M16 16h4" />
                              <path d="M16 18h4" />
                              <path d="M18 14l0 4" />
                            </svg>
                          </span>
                          <div>
                            <h4 class="h6 fw-bold mb-1" data-i18n="landing.pricing.step1.title">Start from their QR</h4>
                            <p class="small text-muted mb-0" data-i18n="landing.pricing.step1.desc">
                              Scan or open the enrolment QR or personalised link so you land in the correct cohort
                              intake without retyping programme codes.
                            </p>
                          </div>
                        </li>
                        <li class="d-flex gap-4">
                          <span
                            class="icon-shape icon-lg bg-primary bg-opacity-10 text-primary rounded-3 flex-shrink-0">
                            <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24"
                              fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                              stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-receipt">
                              <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                              <path d="M5 21v-16a2 2 0 0 1 2 -2h10a2 2 0 0 1 2 2v16l-3 -2l-2 2l-2 -2l-2 2l-2 -2l-3 2" />
                              <path d="M14 9l0 6" />
                              <path d="M10 15l11 0" />
                            </svg>
                          </span>
                          <div>
                            <h4 class="h6 fw-bold mb-1" data-i18n="landing.pricing.step2.title">Attach your receipt</h4>
                            <p class="small text-muted mb-0" data-i18n="landing.pricing.step2.desc">
                              Pay through the channels your organisers specify, then upload a clear receipt snapshot
                              from the dashboard wherever proof is requested.
                            </p>
                          </div>
                        </li>
                        <li class="d-flex gap-4">
                          <span
                            class="icon-shape icon-lg bg-primary bg-opacity-10 text-primary rounded-3 flex-shrink-0">
                            <svg xmlns="http://www.w3.org/2000/svg" width="22" height="22" viewBox="0 0 24 24"
                              fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                              stroke-linejoin="round"
                              class="icon icon-tabler icons-tabler-outline icon-tabler-circle-check">
                              <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                              <path d="M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0 -18 0" />
                              <path d="M9 12l2 2l4 -4" />
                            </svg>
                          </span>
                          <div>
                            <h4 class="h6 fw-bold mb-1" data-i18n="landing.pricing.step3.title">Verification unlocks enrolment</h4>
                            <p class="small text-muted mb-0" data-i18n="landing.pricing.step3.desc">
                              After organisers review payment, you enrol in Tasmi rounds, submit recitations inside each
                              window, and read instructor remarks in context.
                            </p>
                          </div>
                        </li>
                      </ul>
                    </div>
                    <div class="col-lg-5">
                      <div class="rounded-5 border bg-light bg-opacity-50 p-5 h-100 d-flex flex-column">
                        <p class="small fw-semibold text-uppercase text-primary mb-3" style="letter-spacing: .08rem;"
                          data-i18n="landing.pricing.afterSignIn">
                          After you sign in</p>
                        <ul class="list-unstyled mb-0 d-flex flex-column gap-2 small">
                          <li class="d-flex gap-3">
                            <span class="icon-shape icon-xs bg-primary bg-opacity-10 text-primary rounded-circle">
                              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24"
                                fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                                stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-check">
                                <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                                <path d="M5 12l5 5l10 -10" />
                              </svg>
                            </span>
                            <span data-i18n="landing.pricing.benefit1">Role-aware dashboards for students &amp; instructors</span>
                          </li>
                          <li class="d-flex gap-3">
                            <span class="icon-shape icon-xs bg-primary bg-opacity-10 text-primary rounded-circle">
                              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24"
                                fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                                stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-check">
                                <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                                <path d="M5 12l5 5l10 -10" />
                              </svg>
                            </span>
                            <span data-i18n="landing.pricing.benefit2">Session catalogue, reminders, enrolment checkpoints</span>
                          </li>
                          <li class="d-flex gap-3">
                            <span class="icon-shape icon-xs bg-primary bg-opacity-10 text-primary rounded-circle">
                              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24"
                                fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                                stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-check">
                                <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                                <path d="M5 12l5 5l10 -10" />
                              </svg>
                            </span>
                            <span data-i18n="landing.pricing.benefit3">Receipt uploads tied to your intake record</span>
                          </li>
                          <li class="d-flex gap-3">
                            <span class="icon-shape icon-xs bg-primary bg-opacity-10 text-primary rounded-circle">
                              <svg xmlns="http://www.w3.org/2000/svg" width="14" height="14" viewBox="0 0 24 24"
                                fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                                stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-check">
                                <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                                <path d="M5 12l5 5l10 -10" />
                              </svg>
                            </span>
                            <span data-i18n="landing.pricing.benefit4">Recitation uploads, instructor notes, feedback timelines</span>
                          </li>
                        </ul>
                        <div class="d-grid gap-2 mt-auto pt-5">
                          <a href="<%= LocaleSupport.localizedUrl(request, "/auth/register") %>" class="btn btn-primary btn-lg" data-i18n="nav.getStarted">Get Started</a>
                          <a href="<%= LocaleSupport.localizedUrl(request, "/auth/login") %>" class="btn btn-outline-secondary" data-i18n="nav.login">Login</a>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <div class="row mt-8">
            <div class="col-12 text-center">


              <p class="mb-0 small text-muted" data-i18n="landing.pricing.footnote">Questions about dues or subsidies go to your organiser first—admin tools
                inside e‑Tasmi decide when you can join live sessions.</p>
            </div>
          </div>
        </div>
      </section>

      <footer class="pt-lg-13 bg-light py-8 etasmi-blend">
        <div class="container-xxl">
          <div class="row gy-8">
            <div class="col-md-4">
              <div class="">
                <a class="d-inline-flex gap-2 align-items-center lh-1" href="<%= LocaleSupport.localizedUrl(request, "/home") %>">
                  <span class="text-primary"><svg xmlns="http://www.w3.org/2000/svg" width="28" height="28"
                      viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"
                      stroke-linejoin="round" class="icon icon-tabler icons-tabler-outline icon-tabler-book">
                      <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                      <path d="M3 19a9 9 0 0 1 9 0a9 9 0 0 1 9 0" />
                      <path d="M3 6a9 9 0 0 1 9 0a9 9 0 0 1 9 0" />
                      <path d="M3 6l0 13" />
                      <path d="M12 6l0 13" />
                      <path d="M21 6l0 13" />
                    </svg></span>
                  <span class="fw-bold">e‑Tasmi</span>
                </a>
                <p class="mt-4 mb-6" data-i18n="footer.tagline">Coordinate Quran memorisation audits, guardians, instructors, and state panels on
                  one audited workspace built for Malaysian Tasmi practice.</p>
                <div class="d-flex flex-column gap-2">
                  <a class="d-flex align-items-center gap-2 text-body text-decoration-none"
                    href="mailto:support@etasmi.my">
                    <span class="text-primary">
                      <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none"
                        stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
                        class="icon icon-tabler icons-tabler-outline icon-tabler-mail">
                        <path stroke="none" d="M0 0h24v24H0z" fill="none" />
                        <path d="M3 7a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-10z" />
                        <path d="M3 7l9 6l9 -6" />
                      </svg>

                    </span>
                    <span>support@etasmi.my</span>
                  </a>
                </div>

              </div>

            </div>
            <div class="col-md-8">
              <div class="row">
                <div class="col-lg-3 col-md-6">
                  <div class="">
                    <h4 class="fs-5 mb-4" data-i18n="footer.platform">Platform</h4>
                    <ul class="list-unstyled lh-lg small">
                      <li><a href="<%= LocaleSupport.localizedUrl(request, "/home") %>#courses" data-i18n="footer.sessions">Sessions</a>


                      </li>
                      <li><a href="<%= LocaleSupport.localizedUrl(request, "/home") %>#pricing" data-i18n="footer.enrolment">Enrolment</a></li>
                      <li><a href="<%= LocaleSupport.localizedUrl(request, "/home") %>#mentor" data-i18n="footer.instructors">Instructors</a></li>
                      <li><a href="<%= LocaleSupport.localizedUrl(request, "/home") %>#group" data-i18n="footer.community">Community</a></li>
                    </ul>
                  </div>

                </div>
                <div class="col-lg-3 col-md-6">
                  <div>
                    <h4 class="fs-5 mb-4" data-i18n="footer.company">Company</h4>
                    <ul class="list-unstyled lh-lg small">
                      <li><a href="<%= LocaleSupport.localizedUrl(request, "/home") %>" data-i18n="footer.about">About e‑Tasmi</a>


                      </li>
                      <li><a href="<%= LocaleSupport.localizedUrl(request, "/home") %>#about" data-i18n="footer.ourHistory">Our history</a></li>
                      <li><a href="<%= LocaleSupport.localizedUrl(request, "/home") %>#pricing" data-i18n="footer.feesVerification">Fees &amp; verification</a></li>
                    </ul>
                  </div>

                </div>
                <div class="col-lg-3 col-md-6">
                  <div>
                    <h4 class="fs-5 mb-4" data-i18n="footer.support">Support</h4>
                    <ul class="list-unstyled lh-lg small">
                      <li><a href="javascript:void(0)" class="etasmi-footer-link--inert" role="link"
                          aria-disabled="true" data-i18n="footer.helpDesk">Help desk</a></li>
                      <li><a href="<%= LocaleSupport.localizedUrl(request, "/auth/login") %>" data-i18n="footer.signIn">Sign in</a></li>
                      <li><a href="<%= LocaleSupport.localizedUrl(request, "/auth/register") %>" data-i18n="footer.createAccount">Create account</a></li>
                      <li><a href="javascript:void(0)" class="etasmi-footer-link--inert" role="link"
                          aria-disabled="true" data-i18n="footer.policies">Policies</a></li>
                    </ul>
                  </div>

                </div>
                <div class="col-lg-3 col-md-6">
                  <div>
                    <h4 class="fs-5 mb-4" data-i18n="footer.tracks">Tracks</h4>
                    <ul class="list-unstyled lh-lg small">
                      <li><a href="<%= LocaleSupport.localizedUrl(request, "/home") %>#courses" data-i18n="footer.hifzIntensives">Hifz intensives</a>


                      </li>
                      <li><a href="<%= LocaleSupport.localizedUrl(request, "/home") %>#courses" data-i18n="footer.tahsinRemediation">Tahsin remediation</a></li>
                      <li><a href="<%= LocaleSupport.localizedUrl(request, "/home") %>#courses" data-i18n="footer.panelReadiness">Panel readiness</a></li>
                    </ul>
                  </div>

                </div>

              </div>

            </div>

          </div>
          <div class="border-top mt-8 pt-6 etasmi-landing-footer-bottom">
            <p class="mb-0 text-center text-muted mx-auto px-3" data-i18n="footer.landingCopyright" data-i18n-number="2026">
              © 2026 eTasmi. Empowering guided Quran recitation learning.
            </p>
          </div>

        </div>
      </footer>








      <!-- Navbar End -->

      <!-- Bootstrap JS -->



    </body>

    </html>