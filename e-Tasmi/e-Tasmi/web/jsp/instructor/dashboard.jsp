<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
<%@ page import="java.util.List" %>
<%@ page import="java.time.Duration" %>
<%@ page import="java.time.Instant" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.LocalDateTime" %>
<%@ page import="java.time.LocalTime" %>
<%@ page import="java.time.ZoneId" %>
<%@ page import="model.entity.InstructorActivityItem" %>
<%@ page import="model.entity.InstructorDashboardStats" %>
<%@ page import="model.entity.TasmiSession" %>
<%@ page import="model.entity.TasmiSessionStatus" %>
<%!
    private static String esc(Object value) {
        if (value == null) return "";
        return String.valueOf(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
    private static String escJson(String value) {
        if (value == null) return "null";
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '<': sb.append("\\u003c"); break;
                case '>': sb.append("\\u003e"); break;
                case '/': sb.append("\\/"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
    private static long sessionStartEpochMs(TasmiSession s, ZoneId zone) {
        if (s == null || s.getSessionDate() == null || s.getSessionTime() == null) return -1L;
        return LocalDateTime.of(s.getSessionDate(), s.getSessionTime()).atZone(zone).toInstant().toEpochMilli();
    }
    private static long sessionEndEpochMs(TasmiSession s, ZoneId zone) {
        long start = sessionStartEpochMs(s, zone);
        if (start < 0) return -1L;
        int dur = s.getDurationMinutes() == null ? 60 : s.getDurationMinutes();
        return start + (long) dur * 60_000L;
    }
    private static String sessionToJson(TasmiSession s, ZoneId zone) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(192);
        sb.append('{');
        sb.append("\"id\":").append(s.getSessionId()).append(',');
        sb.append("\"title\":").append(escJson(s.getTitle())).append(',');
        sb.append("\"portion\":").append(escJson(s.getQuranPortion())).append(',');
        sb.append("\"level\":").append(escJson(s.getLevel() == null ? null : s.getLevel().name())).append(',');
        sb.append("\"date\":").append(escJson(s.getSessionDate() == null ? null : s.getSessionDate().toString())).append(',');
        sb.append("\"time\":").append(escJson(s.getSessionTime() == null ? null : String.format("%02d:%02d", s.getSessionTime().getHour(), s.getSessionTime().getMinute()))).append(',');
        sb.append("\"durationMinutes\":").append(s.getDurationMinutes() == null ? 60 : s.getDurationMinutes()).append(',');
        sb.append("\"capacity\":").append(s.getCapacity()).append(',');
        sb.append("\"status\":").append(escJson(s.getStatus() == null ? "SCHEDULED" : s.getStatus().name())).append(',');
        sb.append("\"startEpochMs\":").append(sessionStartEpochMs(s, zone)).append(',');
        sb.append("\"endEpochMs\":").append(sessionEndEpochMs(s, zone));
        sb.append('}');
        return sb.toString();
    }
    private static String relativeTime(Instant when, Instant nowInstant) {
        if (when == null || nowInstant == null) return "";
        long secs = Duration.between(when, nowInstant).getSeconds();
        if (secs < 0) {
            long ahead = -secs;
            if (ahead < 90) return "in moments";
            if (ahead < 3600) return "in " + (ahead / 60) + " min";
            if (ahead < 86400) return "in " + (ahead / 3600) + " h";
            return "in " + (ahead / 86400) + " d";
        }
        if (secs < 60) return "just now";
        if (secs < 3600) return (secs / 60) + " min ago";
        if (secs < 86400) return (secs / 3600) + " h ago";
        if (secs < 86400 * 7) return (secs / 86400) + " d ago";
        return when.atZone(util.DateTimeFormats.appZone()).toLocalDate().toString();
    }
    private static String activityIconClass(InstructorActivityItem.Kind kind) {
        if (kind == null) return "";
        switch (kind) {
            case SESSION_LIVE: return "idash-activity__icon--live";
            case SESSION_REMINDER: return "";
            case EVALUATIONS_PENDING: return "idash-activity__icon--amber";
            case RECITATION_SUBMITTED: return "idash-activity__icon--violet";
            default: return "";
        }
    }
    private static String activityIconSvg(InstructorActivityItem.Kind kind) {
        if (kind == null) kind = InstructorActivityItem.Kind.NOTIFICATION;
        switch (kind) {
            case SESSION_LIVE:
                return "<svg width='16' height='16' viewBox='0 0 24 24' fill='none' xmlns='http://www.w3.org/2000/svg'><circle cx='12' cy='12' r='4' fill='currentColor'/><circle cx='12' cy='12' r='9' stroke='currentColor' stroke-width='1.6'/></svg>";
            case SESSION_REMINDER:
                return "<svg width='16' height='16' viewBox='0 0 24 24' fill='none' xmlns='http://www.w3.org/2000/svg'><circle cx='12' cy='12' r='9' stroke='currentColor' stroke-width='1.6'/><path d='M12 7v5l4 2' stroke='currentColor' stroke-width='1.6' stroke-linecap='round'/></svg>";
            case EVALUATIONS_PENDING:
                return "<svg width='16' height='16' viewBox='0 0 24 24' fill='none' xmlns='http://www.w3.org/2000/svg'><path d='m9 12 2 2 4-4' stroke='currentColor' stroke-width='1.6' stroke-linecap='round' stroke-linejoin='round'/><path d='M4 5a2 2 0 0 1 2-2h9l5 5v13a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5Z' stroke='currentColor' stroke-width='1.6' stroke-linejoin='round'/></svg>";
            case RECITATION_SUBMITTED:
                return "<svg width='16' height='16' viewBox='0 0 24 24' fill='none' xmlns='http://www.w3.org/2000/svg'><path d='M3 12a9 9 0 1 0 18 0 9 9 0 0 0-18 0Z' stroke='currentColor' stroke-width='1.6'/><path d='M10 8v8l6-4-6-4Z' fill='currentColor'/></svg>";
            default:
                return "<svg width='16' height='16' viewBox='0 0 24 24' fill='none' xmlns='http://www.w3.org/2000/svg'><path d='M15 17H9m9-1.5V10a6 6 0 1 0-12 0v5.5L4.5 17v1h15v-1L18 15.5Z' stroke='currentColor' stroke-width='1.6' stroke-linecap='round' stroke-linejoin='round'/></svg>";
        }
    }
    private static String greetingFor(LocalTime now) {
        int h = now.getHour();
        if (h < 12) return "Good morning";
        if (h < 17) return "Good afternoon";
        if (h < 22) return "Good evening";
        return "Good night";
    }
    private static String firstNonBlank(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
%>
<%
    request.setAttribute("activeMenu", "overview");

    Object displayNameObj = session.getAttribute("displayName");
    String displayName = displayNameObj == null ? "Instructor" : String.valueOf(displayNameObj);
    String verificationStatus = (String) session.getAttribute("instructorVerificationStatus");
    InstructorDashboardStats stats = (InstructorDashboardStats) request.getAttribute("dashboardStats");
    if (stats == null) {
        stats = new InstructorDashboardStats();
    }
    ZoneId zone = ZoneId.of(firstNonBlank(stats.getServerZoneId(), util.DateTimeFormats.appZone().getId()));
    LocalDate todayLocal = LocalDate.now(zone);
    LocalTime nowLocal = LocalTime.now(zone);
    Instant nowInstant = Instant.now();
    String greeting = greetingFor(nowLocal);
    String idashInitials;
    {
        String dnTrim = displayName == null ? "" : displayName.trim();
        if (dnTrim.isEmpty()) {
            idashInitials = "I";
        } else {
            String[] dnParts = dnTrim.split("\\s+");
            if (dnParts.length == 1) {
                idashInitials = dnParts[0].substring(0, 1).toUpperCase();
            } else {
                String a = dnParts[0].isEmpty() ? "" : dnParts[0].substring(0, 1).toUpperCase();
                String b = dnParts[dnParts.length - 1].isEmpty() ? "" : dnParts[dnParts.length - 1].substring(0, 1).toUpperCase();
                String combined = (a + b).trim();
                idashInitials = combined.isEmpty() ? "I" : combined;
            }
        }
    }

    TasmiSession nextSession = stats.getNextSession();
    List<TasmiSession> weekSessions = stats.getWeekSessions();
    List<InstructorActivityItem> activity = stats.getRecentActivity();
    int todayCount = stats.getTodaySessions().size();
    int upcomingCount = stats.getUpcomingSessions();
    int pendingEval = stats.getPendingEvaluationCount();
    int activeStudents = stats.getEnrolledStudentCount();

    String contextPath = request.getContextPath();

    StringBuilder weekJsonSb = new StringBuilder("[");
    if (weekSessions != null) {
        boolean first = true;
        for (TasmiSession s : weekSessions) {
            if (s == null) continue;
            if (!first) weekJsonSb.append(',');
            first = false;
            weekJsonSb.append(sessionToJson(s, zone));
        }
    }
    weekJsonSb.append(']');

    String bootstrapJson = "{"
            + "\"contextPath\":" + escJson(contextPath) + ","
            + "\"serverNowEpochMs\":" + (stats.getServerNowEpochMs() > 0 ? stats.getServerNowEpochMs() : System.currentTimeMillis()) + ","
            + "\"nextSession\":" + sessionToJson(nextSession, zone) + ","
            + "\"weekSessions\":" + weekJsonSb.toString()
            + "}";
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.instructorDashboardTitle">Instructor Dashboard - e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <%@ include file="/jsp/common/instructor_ui_head.jspf" %>
    <link rel="stylesheet" href="<%= contextPath %>/css/instructor-dashboard-cockpit.css?v=20260509-roll24h">
    <link rel="stylesheet" href="<%= contextPath %>/css/instructor-page-headers.css?v=20260625-student-theme">
    <link rel="stylesheet" href="<%= contextPath %>/css/instructor-dashboard-greeting.css?v=20260610-greet-premium1">
    <script defer src="<%= contextPath %>/assets/js/app.js"></script>
    <script defer src="<%= contextPath %>/assets/js/instructor-dashboard.js?v=20260610-greet-premium1"></script>
</head>
<body class="instructor-premium-page instructor-package-page instructor-module-page instructor-dashboard-page">
<div class="app-shell">
    <%@ include file="/jsp/common/instructor_header.jspf" %>

    <div class="app-main">
        <div class="container sd-container instructor-workspace-shell">
            <div class="instructor-shell-layout">
                <%@ include file="/jsp/instructor/instructor_sidebar.jspf" %>

                <main class="instructor-shell-content" role="main">
                    <div class="idash-page idash-stagger">
                        <%-- Premium greeting card — two-column welcome + live clock --%>
                        <section class="idash-greet idash-greet--premium iup-page-hero iup-page-hero--welcome" data-i18n="instructor.dashboard.welcomeAria" data-i18n-attr="aria-label" aria-label="Welcome">
                            <span class="iup-page-hero__glow" aria-hidden="true"></span>
                            <div class="idash-greet__grid">
                                <div class="idash-greet__welcome">
                                    <div class="idash-greet__identity">
                                        <span class="idash-greet__avatar" aria-hidden="true"><%= esc(idashInitials) %></span>
                                        <div class="idash-greet__copy">
                                            <p class="idash-greet__eyebrow" data-i18n="dashboard.welcomeBack">Welcome back</p>
                                            <h1 class="idash-greet__title">
                                                <span class="idash-greet__salutation"><%= esc(greeting) %>,</span>
                                                <span class="idash-greet__name"><%= esc(displayName) %></span>
                                            </h1>
                                        </div>
                                    </div>
                                    <p class="idash-greet__date"><%= esc(todayLocal.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy"))) %></p>
                                </div>
                                <div class="idash-greet__time">
                                    <span class="idash-greet__time-label" data-i18n="dashboard.localTime">Local time</span>
                                    <div class="idash-greet__clock idash-greet__clock--digits" data-idash-clock aria-live="polite" aria-label="Current time">
                                        <span class="idash-greet__clock-h" data-idash-clock-h>--</span>
                                        <span class="idash-greet__clock-colon" data-idash-clock-colon aria-hidden="true">:</span>
                                        <span class="idash-greet__clock-m" data-idash-clock-m>--</span>
                                        <span class="idash-greet__clock-secs" data-idash-clock-s></span>
                                    </div>
                                </div>
                            </div>
                        </section>

                        <% if (verificationStatus != null && !"APPROVED".equalsIgnoreCase(verificationStatus)) { %>
                        <div class="alert alert-error" role="alert" data-i18n="instructor.dashboard.verificationLimited" data-i18n-vars='{"status":"<%= esc(verificationStatus) %>"}'>
                            Your instructor account is currently <strong><%= esc(verificationStatus) %></strong>. Session creation and meeting-sharing actions stay limited until admin approval is complete.
                        </div>
                        <% } %>

                        <%-- HERO ROW: Countdown + KPI stack --%>
                        <section class="idash-hero" aria-label="Next session and today's metrics" data-i18n="dashboard.progressSnapshot" data-i18n-attr="aria-label">
                            <%-- COUNTDOWN HERO --%>
                            <article class="idash-countdown" data-state="far" aria-live="polite">
                                <header class="idash-countdown__head">
                                    <span class="idash-pill" data-cd="pill">
                                        <span class="idash-pill__dot" data-cd="pill-dot"></span>
                                        <span data-cd="pill-text" data-i18n="instructor.dashboard.nextUp">Next up</span>
                                    </span>
                                    <span class="idash-countdown__meta" data-cd="meta">
                                        <%
                                            if (nextSession != null && nextSession.getSessionDate() != null) {
                                        %>
                                            <%= esc(nextSession.getSessionDate().toString()) %>
                                            <% if (nextSession.getSessionTime() != null) { %>
                                                · <%= esc(String.format("%02d:%02d", nextSession.getSessionTime().getHour(), nextSession.getSessionTime().getMinute())) %>
                                            <% } %>
                                        <% } %>
                                    </span>
                                </header>

                                <h2 class="idash-countdown__title" data-cd="title">
                                    <%= esc(nextSession == null ? "No upcoming sessions" : (nextSession.getTitle() == null ? "Untitled session" : nextSession.getTitle())) %>
                                </h2>
                                <p class="idash-countdown__sub" data-cd="sub">
                                    <% if (nextSession != null) { %>
                                        <% if (nextSession.getQuranPortion() != null && !nextSession.getQuranPortion().isBlank()) { %>
                                            <span><%= esc(nextSession.getQuranPortion()) %></span>
                                        <% } %>
                                        <% if (nextSession.getDurationMinutes() != null) { %>
                                            <span><%= nextSession.getDurationMinutes() %> min</span>
                                        <% } %>
                                    <% } else { %>
                                        You have no scheduled sessions yet.
                                    <% } %>
                                </p>

                                <div class="idash-countdown__body">
                                    <div>
                                        <div class="idash-countdown__caption" data-cd="caption" data-i18n="instructor.dashboard.startsIn">Starts in</div>
                                        <div class="idash-countdown__digits" data-cd="digits">
                                            <span>00<span class="idash-countdown__unit" data-cd="unit">h</span></span>
                                            <span class="idash-d-sep">:</span>
                                            <span>00<span class="idash-countdown__unit">m</span></span>
                                            <span class="idash-d-sep">:</span>
                                            <span>00<span class="idash-countdown__unit">s</span></span>
                                        </div>
                                    </div>
                                    <div class="idash-countdown__ring" aria-hidden="true">
                                        <svg viewBox="0 0 138 138">
                                            <circle class="idash-countdown__ring-bg" cx="69" cy="69" r="60" fill="none" stroke-width="8"/>
                                            <circle class="idash-countdown__ring-fg" cx="69" cy="69" r="60" fill="none" stroke-width="8" stroke-dasharray="377" stroke-dashoffset="377" data-cd="ring-fg"/>
                                        </svg>
                                        <span class="idash-countdown__ring-pct" data-cd="ring-pct">0%</span>
                                    </div>
                                </div>

                                <div class="idash-countdown__overrun" aria-hidden="true">
                                    <div class="idash-countdown__overrun-bar" data-cd="overrun-bar"></div>
                                </div>

                                <footer class="idash-countdown__foot" data-cd="foot">
                                    <%-- Server-rendered fallback for no-JS users --%>
                                    <% if (nextSession == null) { %>
                                        <a class="idash-btn idash-btn--primary" href="<%= contextPath %>/instructor/sessions" data-i18n="instructor.dashboard.scheduleSession">Schedule a Session</a>
                                    <% } else if (nextSession.getStatus() == TasmiSessionStatus.ONGOING) { %>
                                        <a class="idash-btn idash-btn--live"
                                           href="<%= contextPath %>/instructor/live-session?sessionId=<%= nextSession.getSessionId() %>"
                                           target="_blank" rel="noopener" data-i18n="dashboard.openLiveSession">Open Live Session</a>
                                        <a class="idash-btn idash-btn--ghost" href="<%= contextPath %>/instructor/sessions" data-i18n="instructor.dashboard.manage">Manage</a>
                                    <% } else { %>
                                        <form method="post" action="<%= contextPath %>/instructor/sessions" style="margin:0;">
                                            <input type="hidden" name="action" value="start">
                                            <input type="hidden" name="sessionId" value="<%= nextSession.getSessionId() %>">
                                            <button class="idash-btn idash-btn--primary" type="submit" data-i18n="instructor.dashboard.startSession">Start Session</button>
                                        </form>
                                        <a class="idash-btn idash-btn--ghost" href="<%= contextPath %>/instructor/sessions" data-i18n="instructor.dashboard.manage">Manage</a>
                                    <% } %>
                                </footer>
                            </article>

                            <%-- KPI STACK --%>
                            <div class="idash-kpi" role="group" aria-label="At-a-glance metrics" data-i18n="dashboard.progressSnapshot" data-i18n-attr="aria-label">
                                <a class="idash-kpi-card" href="<%= LocaleSupport.localizedUrl(request, "/instructor/sessions") %>">
                                    <div class="idash-kpi-card__head">
                                        <span class="idash-kpi-card__label" data-i18n="dashboard.liveSessions">Live Sessions</span>
                                        <span class="idash-kpi-card__icon" aria-hidden="true">
                                            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.7"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
                                        </span>
                                    </div>
                                    <div class="idash-kpi-card__value" data-target="<%= todayCount %>" data-kpi="todaySessions"><%= todayCount %></div>
                                    <div class="idash-kpi-card__hint" data-i18n="instructor.dashboard.scheduledToday">scheduled for today</div>
                                </a>

                                <a class="idash-kpi-card" href="<%= contextPath %>/instructor/sessions">
                                    <div class="idash-kpi-card__head">
                                        <span class="idash-kpi-card__label" data-i18n="instructor.dashboard.upcoming">Upcoming</span>
                                        <span class="idash-kpi-card__icon" aria-hidden="true">
                                            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.7"/><path d="M12 7v5l4 2" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
                                        </span>
                                    </div>
                                    <div class="idash-kpi-card__value" data-target="<%= upcomingCount %>" data-kpi="upcomingSessions"><%= upcomingCount %></div>
                                    <div class="idash-kpi-card__hint" data-i18n="instructor.dashboard.onSchedule">on your schedule</div>
                                </a>

                                <a class="idash-kpi-card" data-tone="amber" href="<%= LocaleSupport.localizedUrl(request, "/instructor/evaluations") %>">
                                    <div class="idash-kpi-card__head">
                                        <span class="idash-kpi-card__label" data-i18n="dashboard.instructorFeedback">Instructor Feedback</span>
                                        <span class="idash-kpi-card__icon" aria-hidden="true">
                                            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="m9 12 2 2 4-4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/><path d="M4 5a2 2 0 0 1 2-2h9l5 5v13a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/></svg>
                                        </span>
                                    </div>
                                    <div class="idash-kpi-card__value" data-target="<%= pendingEval %>" data-kpi="pendingEvaluations"><%= pendingEval %></div>
                                    <div class="idash-kpi-card__hint" data-i18n="dashboard.awaitingReview">awaiting review</div>
                                </a>

                                <a class="idash-kpi-card" data-tone="violet" href="<%= contextPath %>/instructor/sessions">
                                    <div class="idash-kpi-card__head">
                                        <span class="idash-kpi-card__label" data-i18n="instructor.dashboard.activeStudentsLabel">Active students</span>
                                        <span class="idash-kpi-card__icon" aria-hidden="true">
                                            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="8" r="4" stroke="currentColor" stroke-width="1.7"/><path d="M4 21a8 8 0 0 1 16 0" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
                                        </span>
                                    </div>
                                    <div class="idash-kpi-card__value" data-target="<%= activeStudents %>" data-kpi="activeStudents"><%= activeStudents %></div>
                                    <div class="idash-kpi-card__hint" data-i18n="instructor.dashboard.acrossClasses">across your classes</div>
                                </a>
                            </div>
                        </section>

                        <%-- SMART CALENDAR --%>
                        <section class="idash-cal" data-i18n="instructor.dashboard.teachingCalendar" data-i18n-attr="aria-label" aria-label="Teaching calendar">
                            <div class="idash-cal__head">
                                <h2 class="idash-cal__title" data-i18n="instructor.dashboard.teachingCalendar">Teaching calendar</h2>
                                <div class="idash-cal__nav">
                                    <button class="idash-cal__today" type="button" data-cal-today data-i18n="instructor.dashboard.today">Today</button>
                                    <button class="idash-cal__nav-btn" type="button" data-cal-prev data-i18n="common.previous" data-i18n-attr="aria-label" aria-label="Previous">
                                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="m15 18-6-6 6-6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                    </button>
                                    <span class="idash-cal__range" data-cal="range">—</span>
                                    <button class="idash-cal__nav-btn" type="button" data-cal-next data-i18n="common.next" data-i18n-attr="aria-label" aria-label="Next">
                                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="m9 6 6 6-6 6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                    </button>
                                </div>
                                <div class="idash-cal__views" role="tablist" data-i18n="instructor.dashboard.calendarViewAria" data-i18n-attr="aria-label" aria-label="Calendar view">
                                    <button class="idash-cal__view-btn" role="tab" data-cal-view="day" aria-selected="false" data-i18n="instructor.dashboard.day">Day</button>
                                    <button class="idash-cal__view-btn" role="tab" data-cal-view="week" aria-selected="true" data-i18n="instructor.dashboard.week">Week</button>
                                    <button class="idash-cal__view-btn" role="tab" data-cal-view="month" aria-selected="false" data-i18n="instructor.dashboard.month">Month</button>
                                </div>
                            </div>
                            <div class="idash-cal__grid" data-cal="grid"></div>
                        </section>

                        <%-- ROW C: ACTIVITY --%>
                        <section class="idash-bottom" data-i18n="instructor.dashboard.activityReminders" data-i18n-attr="aria-label" aria-label="Activity and reminders">
                            <article class="idash-activity">
                                <h2 class="idash-activity__title">
                                    <span data-i18n="instructor.dashboard.activityReminders">Activity &amp; reminders</span>
                                    <a class="idash-activity__action" href="<%= contextPath %>/notifications" data-i18n="instructor.dashboard.viewAll">View all</a>
                                </h2>
                                <% if (activity == null || activity.isEmpty()) { %>
                                    <div class="idash-activity__empty" data-i18n="instructor.dashboard.noNewActivity">No new activity.</div>
                                <% } else { %>
                                <ul class="idash-activity__list">
                                    <% for (InstructorActivityItem item : activity) {
                                           if (item == null) continue;
                                           String iconExtra = activityIconClass(item.getKind());
                                           String iconSvg = activityIconSvg(item.getKind());
                                           String when = item.getOccurredAt() == null ? "" : relativeTime(item.getOccurredAt(), nowInstant);
                                    %>
                                    <li class="idash-activity__item">
                                        <span class="idash-activity__icon <%= iconExtra %>" aria-hidden="true"><%= iconSvg %></span>
                                        <div>
                                            <div class="idash-activity__title-line"><%= esc(item.getTitle()) %></div>
                                            <% if (item.getBody() != null && !item.getBody().isBlank()) { %>
                                                <div class="idash-activity__sub"><%= esc(item.getBody()) %><% if (when != null && !when.isEmpty()) { %> · <%= esc(when) %><% } %></div>
                                            <% } else if (when != null && !when.isEmpty()) { %>
                                                <div class="idash-activity__sub"><%= esc(when) %></div>
                                            <% } %>
                                        </div>
                                        <% if (item.getActionLabel() != null && item.getActionHref() != null) { %>
                                            <a class="idash-activity__action" href="<%= contextPath %><%= esc(item.getActionHref()) %>"><%= esc(item.getActionLabel()) %></a>
                                        <% } %>
                                    </li>
                                    <% } %>
                                </ul>
                                <% } %>
                            </article>

                            <article class="idash-activity">
                                <h2 class="idash-activity__title" data-i18n="instructor.dashboard.todayAtGlance">Today at a glance</h2>
                                <% if (stats.getTodaySessions() == null || stats.getTodaySessions().isEmpty()) { %>
                                    <div class="idash-activity__empty" data-i18n="instructor.dashboard.nothingScheduledToday">Nothing scheduled for today.</div>
                                <% } else { %>
                                <ul class="idash-activity__list">
                                    <% for (TasmiSession s : stats.getTodaySessions()) {
                                           if (s == null) continue;
                                           String when = s.getSessionTime() == null ? "—" : String.format("%02d:%02d", s.getSessionTime().getHour(), s.getSessionTime().getMinute());
                                           String statusLabel = s.getStatus() == null ? "Scheduled" : s.getStatus().name().charAt(0) + s.getStatus().name().substring(1).toLowerCase();
                                           String iconCls = s.getStatus() == TasmiSessionStatus.ONGOING ? "idash-activity__icon--live" : "";
                                    %>
                                    <li class="idash-activity__item">
                                        <span class="idash-activity__icon <%= iconCls %>" aria-hidden="true">
                                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.6"/><path d="M12 7v5l4 2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                                        </span>
                                        <div>
                                            <div class="idash-activity__title-line"><%= esc(s.getTitle() == null ? "Untitled session" : s.getTitle()) %></div>
                                            <div class="idash-activity__sub"><%= esc(when) %> · <%= esc(statusLabel) %></div>
                                        </div>
                                        <a class="idash-activity__action" href="<%= contextPath %>/instructor/sessions" data-i18n="instructor.dashboard.open">Open</a>
                                    </li>
                                    <% } %>
                                </ul>
                                <% } %>
                            </article>
                        </section>
                    </div>

                    <%@ include file="/jsp/common/app_footer.jspf" %>

                    <%-- Overlays (position:fixed) — kept OUTSIDE .idash-stagger so the
                         entrance animation can never override their own transform.
                         At runtime, JS portals these to <body> so they are not
                         confined by any ancestor's containing block. --%>
                    <div class="idash-drawer__scrim" data-drawer-scrim></div>
                    <aside class="idash-drawer" role="dialog" aria-labelledby="idash-drawer-title" aria-hidden="true" data-status="SCHEDULED"
                        data-i18n="instructor.dashboard.sessionDetails" data-i18n-attr="aria-label">
                        <div class="idash-drawer__head">
                            <div class="idash-drawer__head-left">
                                <span class="idash-drawer__pill" data-drawer="status-pill" data-status="SCHEDULED" data-i18n="instructor.dashboard.drawerUpcoming">Upcoming</span>
                                <h3 id="idash-drawer-title" class="idash-drawer__title" data-drawer="title" data-i18n="instructor.dashboard.sessionDetails">Session details</h3>
                            </div>
                            <button class="idash-drawer__close" type="button" data-i18n="instructor.dashboard.closeSessionDetails" data-i18n-attr="aria-label" aria-label="Close session details" data-drawer-close>
                                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="m6 6 12 12M18 6 6 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
                            </button>
                        </div>
                        <div class="idash-drawer__body" data-drawer="body"></div>
                        <div class="idash-drawer__foot" data-drawer="foot"></div>
                    </aside>

                    <script type="application/json" id="idash-bootstrap"><%= bootstrapJson %></script>
                </main>
            </div>
        </div>
    </div>
</div>
</body>
</html>
