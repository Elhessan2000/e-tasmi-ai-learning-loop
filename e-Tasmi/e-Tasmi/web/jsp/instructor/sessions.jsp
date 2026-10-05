<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="model.entity.SessionParticipantView" %>
<%@ page import="model.entity.TasmiSession" %>
<%@ page import="model.entity.TasmiSessionStatus" %>
<%!
    private static String text(Object value) {
        if (value == null) return "-";
        String raw = String.valueOf(value);
        if (raw.trim().isEmpty()) return "-";
        return raw.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private static String rawText(Object value) {
        if (value == null) return "";
        return String.valueOf(value).trim();
    }
    private static String statusLabel(TasmiSession s) {
        return s == null || s.getStatus() == null ? "UNKNOWN" : s.getStatus().name();
    }
    private static String statusPillClass(TasmiSession s) {
        if (s == null || s.getStatus() == null) return "isess-pill--scheduled";
        if (s.getStatus() == TasmiSessionStatus.ONGOING)   return "isess-pill--ongoing";
        if (s.getStatus() == TasmiSessionStatus.COMPLETED) return "isess-pill--completed";
        if (s.getStatus() == TasmiSessionStatus.CANCELLED) return "isess-pill--cancelled";
        return "isess-pill--scheduled";
    }
%>
<%
    request.setAttribute("activeMenu", "sessions");
    List<TasmiSession> sessions = (List<TasmiSession>) request.getAttribute("sessions");
    Map<Long, List<SessionParticipantView>> participantsBySession = (Map<Long, List<SessionParticipantView>>) request.getAttribute("participantsBySession");
    TasmiSession editSession = (TasmiSession) request.getAttribute("editSession");
    String verificationStatus = (String) request.getAttribute("verificationStatus");
    String success = (String) request.getAttribute("success");
    String error   = (String) request.getAttribute("error");

    boolean approvedInstructor = "APPROVED".equalsIgnoreCase(verificationStatus == null ? "" : verificationStatus);
    boolean showEditModal = editSession != null;

    List<TasmiSession> activeSessions  = new ArrayList<>();
    List<TasmiSession> historySessions = new ArrayList<>();
    int scheduledCount = 0, ongoingCount = 0, totalStudents = 0;
    if (sessions != null) {
        for (TasmiSession row : sessions) {
            if (row == null) continue;
            if (row.getStatus() == TasmiSessionStatus.COMPLETED || row.getStatus() == TasmiSessionStatus.CANCELLED) {
                historySessions.add(row);
            } else {
                activeSessions.add(row);
                if (row.getStatus() == TasmiSessionStatus.SCHEDULED) scheduledCount++;
                if (row.getStatus() == TasmiSessionStatus.ONGOING)   ongoingCount++;
            }
            List<SessionParticipantView> sp = participantsBySession == null ? null : participantsBySession.get(row.getSessionId());
            if (sp != null) totalStudents += sp.size();
        }
    }
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}"
      data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.instructorSessionsTitle">Instructor Sessions - e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <%@ include file="/jsp/common/instructor_ui_head.jspf" %>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/instructor-sessions-redesign.css?v=20260625-start-fix">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/instructor-page-headers.css?v=20260625-student-theme">
    <script defer src="<%= request.getContextPath() %>/assets/js/app.js"></script>
</head>
<body class="instructor-premium-page instructor-package-page instructor-module-page instructor-sessions-page">
<div class="app-shell">
    <%@ include file="/jsp/common/instructor_header.jspf" %>
    <div class="app-main">
        <div class="container sd-container instructor-workspace-shell">
            <div class="instructor-shell-layout">
                <%@ include file="/jsp/instructor/instructor_sidebar.jspf" %>

                <main class="instructor-shell-content" role="main">
                    <div class="instructor-workspace-view">

                    <%-- Hero --%>
                    <section class="isess-hero iup-page-hero iup-page-hero--sessions" data-i18n="instructor.sessions.title" data-i18n-attr="aria-label" aria-label="Sessions overview">
                        <span class="iup-page-hero__glow" aria-hidden="true"></span>
                        <div class="iup-page-hero__inner">
                            <span class="iup-page-hero__icon" aria-hidden="true">
                                <svg viewBox="0 0 24 24" fill="none"><rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><path d="M12 14v4M10 16h4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
                            </span>
                            <div class="iup-page-hero__text">
                                <p class="iup-page-hero__eyebrow" data-i18n="instructor.sessions.title">Sessions</p>
                                <h1 class="iup-page-hero__title" data-i18n="instructor.sessions.pageTitle">Manage Sessions</h1>
                                <p class="iup-page-hero__desc" data-i18n="instructor.sessions.heroDesc">Plan, launch and track your Quran recitation sessions from one place.</p>
                            </div>
                        </div>
                        <div class="iup-page-hero__actions">
                            <button class="isess-create-btn" type="button" onclick="document.getElementById('createSessionModal').classList.add('active')">
                                <svg viewBox="0 0 24 24" fill="none"><path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/></svg>
                                <span data-i18n="instructor.sessions.createNew">New Session</span>
                            </button>
                        </div>
                    </section>

                    <%-- Alerts --%>
                    <% if (success != null) { %>
                    <div class="isess-toast isess-toast--success">
                        <svg viewBox="0 0 24 24" fill="none"><path d="M9 12l2 2 4-4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/></svg>
                        <span><%= text(success) %></span>
                        <button class="isess-toast__close" onclick="this.closest('.isess-toast').remove()" aria-label="Dismiss"><svg viewBox="0 0 24 24" fill="none"><path d="M18 6 6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg></button>
                    </div>
                    <% } %>
                    <% if (error != null) { %>
                    <div class="isess-toast isess-toast--error">
                        <svg viewBox="0 0 24 24" fill="none"><path d="M12 8v4m0 4h.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/></svg>
                        <span><%= text(error) %></span>
                        <button class="isess-toast__close" onclick="this.closest('.isess-toast').remove()" aria-label="Dismiss"><svg viewBox="0 0 24 24" fill="none"><path d="M18 6 6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg></button>
                    </div>
                    <% } %>
                    <% if (!approvedInstructor) { %>
                    <div class="isess-verification-banner">
                        <div class="isess-verification-banner__icon"><svg viewBox="0 0 24 24" fill="none"><path d="M12 9v4" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><path d="M12 17h.01" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"/><path d="M10.3 3.2 1.5 18a2 2 0 0 0 1.7 3h17.6a2 2 0 0 0 1.7-3L13.7 3.2a2 2 0 0 0-3.4 0Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg></div>
                        <div class="isess-verification-banner__body"><strong data-i18n="instructor.sessions.verificationPending">Verification pending</strong><span><span data-i18n="instructor.sessions.verificationStatusPrefix">Status:</span> <em><%= text(verificationStatus) %></em> — <span data-i18n="instructor.sessions.verificationActionsLimited">session actions are limited.</span></span></div>
                        <span class="isess-verification-banner__status"><%= text(verificationStatus) %></span>
                    </div>
                    <% } %>

                    <%-- KPI Strip removed per UI simplification --%>

                    <%-- View toggle --%>
                    <div class="iup-view-toggle" role="tablist" aria-label="Switch between scheduled and history">
                        <div class="iup-view-toggle__inner">
                            <button class="iup-view-toggle__btn" type="button" role="tab" aria-selected="true"
                                    aria-controls="iup-view-scheduled" data-iup-toggle="scheduled">
                                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.7"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
                                <span data-i18n="instructor.sessions.tabScheduled">Scheduled</span>
                                <span class="iup-view-toggle__count"><%= activeSessions.size() %></span>
                            </button>
                            <button class="iup-view-toggle__btn" type="button" role="tab" aria-selected="false"
                                    aria-controls="iup-view-completed" data-iup-toggle="completed">
                                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="m9 12 2 2 4-4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.7"/></svg>
                                <span data-i18n="instructor.sessions.tabHistory">History</span>
                                <span class="iup-view-toggle__count"><%= historySessions.size() %></span>
                            </button>
                        </div>
                    </div>

                    <%-- ═══ SCHEDULED VIEW — premium table ═══ --%>
                    <div class="iup-view iup-fade-in" id="iup-view-scheduled" role="tabpanel" data-iup-view="scheduled">

                    <% if (activeSessions.isEmpty()) { %>
                    <div class="isess-empty-state">
                        <div class="isess-empty-state__art" aria-hidden="true">
                            <svg viewBox="0 0 120 100" fill="none"><rect x="10" y="20" width="100" height="70" rx="10" fill="#F1F5F9"/><rect x="10" y="20" width="100" height="22" rx="10" fill="#E2E8F0"/><circle cx="30" cy="31" r="6" fill="#CBD5E1"/><rect x="42" y="27" width="40" height="4" rx="2" fill="#CBD5E1"/><rect x="42" y="33" width="25" height="3" rx="1.5" fill="#E2E8F0"/><rect x="20" y="54" width="80" height="6" rx="3" fill="#E2E8F0"/><rect x="20" y="66" width="60" height="6" rx="3" fill="#E2E8F0"/><rect x="20" y="78" width="70" height="6" rx="3" fill="#E2E8F0"/><circle cx="96" cy="26" r="16" fill="#89F336" opacity=".15"/><path d="M96 20v6l4 4" stroke="#89F336" stroke-width="2.5" stroke-linecap="round"/><circle cx="96" cy="26" r="9" stroke="#89F336" stroke-width="2"/></svg>
                        </div>
                        <h3 class="isess-empty-state__title" data-i18n="instructor.sessions.noSessionsFound">No upcoming sessions</h3>
                        <p class="isess-empty-state__desc" data-i18n="instructor.sessions.noSessionsSub">Create your first session to start accepting students and running live recitations.</p>
                        <button class="isess-create-btn isess-create-btn--center" type="button" onclick="document.getElementById('createSessionModal').classList.add('active')">
                            <svg viewBox="0 0 24 24" fill="none"><path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/></svg>
                            <span data-i18n="instructor.sessions.create">Create your first session</span>
                        </button>
                    </div>
                    <% } else { %>

                    <div class="isess-table-wrap">
                        <table class="isess-table">
                            <thead>
                                <tr>
                                    <th class="isess-th--toggle"></th>
                                    <th data-i18n="instructor.sessions.colSession">Session</th>
                                    <th data-i18n="instructor.sessions.colDateTime">Date &amp; Time</th>
                                    <th data-i18n="instructor.sessions.colLevel">Level</th>
                                    <th data-i18n="instructor.sessions.colDuration">Duration</th>
                                    <th data-i18n="instructor.sessions.colCapacity">Capacity</th>
                                    <th data-i18n="instructor.sessions.colFee">Fee</th>
                                    <th data-i18n="instructor.sessions.colStatus">Status</th>
                                    <th data-i18n="instructor.sessions.colActions">Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                            <% for (TasmiSession sessionRow : activeSessions) {
                                   List<SessionParticipantView> participants = participantsBySession == null ? null : participantsBySession.get(sessionRow.getSessionId());
                                   int participantCount = participants == null ? 0 : participants.size();
                                   int capacity = sessionRow.getCapacity();
                                   int pct = (capacity > 0) ? Math.min(100, (int)((participantCount * 100.0) / capacity)) : 0;
                                   boolean isFull    = participantCount >= capacity && capacity > 0;
                                   boolean isOngoing = sessionRow.getStatus() == TasmiSessionStatus.ONGOING;
                                   String detailId   = "active-detail-" + sessionRow.getSessionId();
                            %>
                            <tr class="isess-summary-row <%= isOngoing ? "isess-row--live" : "" %>"
                                onclick="toggleSessionDetail('<%= detailId %>',this)" style="cursor:pointer;">
                                <td class="isess-th--toggle">
                                    <button class="isess-toggle-btn" type="button" aria-label="Toggle details">
                                        <svg viewBox="0 0 24 24" fill="none"><path d="M6 9l6 6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                    </button>
                                </td>
                                <td>
                                    <div class="isess-session-info">
                                        <div class="isess-session-thumb">
                                            <% if (sessionRow.getBannerImageUrl() != null && !sessionRow.getBannerImageUrl().trim().isEmpty()) {
                                                String bSrc = sessionRow.getBannerImageUrl().startsWith("http") ? sessionRow.getBannerImageUrl() : request.getContextPath() + sessionRow.getBannerImageUrl();
                                            %><img src="<%= text(bSrc) %>" alt="">
                                            <% } else { %><svg class="thumb-icon" viewBox="0 0 24 24" fill="none"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" stroke="currentColor" stroke-width="1.7"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z" stroke="currentColor" stroke-width="1.7"/></svg><% } %>
                                        </div>
                                        <div>
                                            <div class="isess-session-title-text isess-session-title-text--primary">
                                                <% if (isOngoing) { %><span class="isess-live-dot" aria-label="Live"></span><% } %>
                                                <%= text(sessionRow.getTitle()) %>
                                            </div>
                                            <div class="isess-session-meta"><%= participantCount %> student<%= participantCount != 1 ? "s" : "" %><% if (sessionRow.getQuranPortion() != null && !sessionRow.getQuranPortion().trim().isEmpty()) { %> &bull; <%= text(sessionRow.getQuranPortion()) %><% } %></div>
                                        </div>
                                    </div>
                                </td>
                                <td class="isess-col-meta">
                                    <div><%= sessionRow.getSessionDate() == null ? "-" : sessionRow.getSessionDate() %></div>
                                    <div class="isess-col-time"><%= sessionRow.getSessionTime() == null ? "" : sessionRow.getSessionTime() %></div>
                                </td>
                                <td class="isess-col-meta"><%= sessionRow.getLevel() == null ? "-" : text(sessionRow.getLevel().getDisplayName()) %></td>
                                <td class="isess-col-meta"><%= sessionRow.getDurationMinutes() == null ? "-" : sessionRow.getDurationMinutes() + " min" %></td>
                                <td>
                                    <div class="isess-cap-cell">
                                        <span class="isess-cap-cell__label <%= isFull ? "isess-cap-cell__label--full" : "" %>"><%= participantCount %>/<%= capacity %></span>
                                        <div class="isess-capacity__track">
                                            <div class="isess-capacity__bar <%= isFull ? "isess-capacity__bar--full" : (pct >= 75 ? "isess-capacity__bar--high" : "") %>" style="width:<%= pct %>%;"></div>
                                        </div>
                                    </div>
                                </td>
                                <td class="isess-col-meta"><%= sessionRow.getFee() == null ? "Free" : sessionRow.getFee() %></td>
                                <td>
                                    <% if (isOngoing) { %>
                                    <span class="isess-pill isess-pill--soft isess-pill--ongoing"><span class="isess-live-dot" aria-hidden="true"></span>Live</span>
                                    <% } else { %>
                                    <span class="isess-pill isess-pill--soft isess-pill--scheduled">Scheduled</span>
                                    <% } %>
                                </td>
                                <td>
                                    <div class="isess-actions-cell isess-actions-cell--icons" onclick="event.stopPropagation();">
                                        <% if (sessionRow.getStatus() == TasmiSessionStatus.SCHEDULED) { %>
                                        <%-- Start Session button --%>
                                        <form method="post" action="<%= request.getContextPath() %>/instructor/sessions" class="isess-action-form">
                                            <input type="hidden" name="action" value="start">
                                            <input type="hidden" name="sessionId" value="<%= sessionRow.getSessionId() %>">
                                            <button class="isess-icon-btn isess-icon-btn--start isess-icon-btn--labeled" type="submit"
                                                    aria-label="Start Session">
                                                <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><polygon points="5,3 19,12 5,21"/></svg>
                                                <span>Start</span>
                                            </button>
                                        </form>
                                        <%-- Edit Session icon button --%>
                                        <a class="isess-icon-btn isess-icon-btn--edit"
                                           href="<%= request.getContextPath() %>/instructor/sessions?editId=<%= sessionRow.getSessionId() %>"
                                           data-tooltip="Edit Session" aria-label="Edit Session">
                                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5Z" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                        </a>
                                        <%-- Delete Session icon button --%>
                                        <form method="post" action="<%= request.getContextPath() %>/instructor/sessions"
                                              class="isess-action-form" onsubmit="return confirm('Delete this session? This cannot be undone.');">
                                            <input type="hidden" name="action" value="delete">
                                            <input type="hidden" name="sessionId" value="<%= sessionRow.getSessionId() %>">
                                            <button class="isess-icon-btn isess-icon-btn--delete" type="submit"
                                                    data-tooltip="Delete Session" aria-label="Delete Session">
                                                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><path d="M10 11v6M14 11v6" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                                            </button>
                                        </form>
                                        <% } else if (isOngoing) { %>
                                        <%-- Open Live icon button --%>
                                        <a class="isess-icon-btn isess-icon-btn--start"
                                           href="<%= request.getContextPath() %>/instructor/live-session?sessionId=<%= sessionRow.getSessionId() %>"
                                           target="_blank" rel="noopener"
                                           data-tooltip="Open Live Session" aria-label="Open Live Session">
                                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="12" cy="12" r="4" fill="currentColor"/><path d="M5.6 5.6A9 9 0 0 0 3 12a9 9 0 0 0 2.6 6.4M18.4 5.6A9 9 0 0 1 21 12a9 9 0 0 1-2.6 6.4" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                                        </a>
                                        <%-- End Session icon button --%>
                                        <form method="post" action="<%= request.getContextPath() %>/instructor/sessions" class="isess-action-form">
                                            <input type="hidden" name="action" value="complete">
                                            <input type="hidden" name="sessionId" value="<%= sessionRow.getSessionId() %>">
                                            <button class="isess-icon-btn isess-icon-btn--end" type="submit"
                                                    data-tooltip="End Session" aria-label="End Session">
                                                <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>
                                            </button>
                                        </form>
                                        <% } %>
                                    </div>
                                </td>
                            </tr>
                            <tr class="isess-detail-row" id="<%= detailId %>">
                                <td colspan="9">
                                    <div class="isess-detail-inner">
                                        <div class="isess-detail-grid">
                                            <div class="isess-detail-item"><div class="isess-detail-label" data-i18n="instructor.sessions.colDate">Date</div><div class="isess-detail-value"><%= sessionRow.getSessionDate() == null ? "-" : sessionRow.getSessionDate() %></div></div>
                                            <div class="isess-detail-item"><div class="isess-detail-label" data-i18n="instructor.sessions.colTime">Time</div><div class="isess-detail-value"><%= sessionRow.getSessionTime() == null ? "-" : sessionRow.getSessionTime() %></div></div>
                                            <div class="isess-detail-item"><div class="isess-detail-label" data-i18n="instructor.sessions.colDuration">Duration</div><div class="isess-detail-value"><%= sessionRow.getDurationMinutes() == null ? "-" : sessionRow.getDurationMinutes() + " min" %></div></div>
                                            <div class="isess-detail-item"><div class="isess-detail-label" data-i18n="instructor.sessions.colCapacity">Capacity</div><div class="isess-detail-value"><%= capacity %> max · <%= participantCount %> enrolled</div></div>
                                            <div class="isess-detail-item"><div class="isess-detail-label" data-i18n="instructor.sessions.colFee">Fee</div><div class="isess-detail-value"><%= sessionRow.getFee() == null ? "Free" : sessionRow.getFee() %></div></div>
                                            <div class="isess-detail-item"><div class="isess-detail-label" data-i18n="instructor.sessions.colLevel">Level</div><div class="isess-detail-value"><%= sessionRow.getLevel() == null ? "-" : text(sessionRow.getLevel().getDisplayName()) %></div></div>
                                            <div class="isess-detail-item"><div class="isess-detail-label" data-i18n="instructor.sessions.colSurah">Surah / Portion</div><div class="isess-detail-value"><%= text(sessionRow.getQuranPortion()) %></div></div>
                                            <% if (sessionRow.getZoomMeetingId() != null) { %><div class="isess-detail-item"><div class="isess-detail-label">Zoom</div><div class="isess-detail-value">#<%= sessionRow.getZoomMeetingId() %></div></div><% } %>
                                        </div>
                                        <% if (sessionRow.getDescription() != null && !sessionRow.getDescription().trim().isEmpty()) { %>
                                        <div class="isess-detail-desc"><%= text(sessionRow.getDescription()) %></div>
                                        <% } %>
                                        <div class="isess-detail-actions">
                                            <% if (sessionRow.getStatus() == TasmiSessionStatus.SCHEDULED) { %>
                                            <form method="post" action="<%= request.getContextPath() %>/instructor/sessions" style="display:inline;">
                                                <input type="hidden" name="action" value="start">
                                                <input type="hidden" name="sessionId" value="<%= sessionRow.getSessionId() %>">
                                                <button class="isess-btn isess-btn--start" type="submit">
                                                    <svg viewBox="0 0 24 24" fill="none"><polygon points="5,3 19,12 5,21" fill="currentColor"/></svg>
                                                    <span data-i18n="instructor.sessions.startSessionBtn">Start Session</span>
                                                </button>
                                            </form>
                                            <a class="isess-btn isess-btn--edit" href="<%= request.getContextPath() %>/instructor/sessions?editId=<%= sessionRow.getSessionId() %>" data-i18n="instructor.sessions.editSession">Edit Session</a>
                                            <% } else if (isOngoing) { %>
                                            <a class="isess-btn isess-btn--start" href="<%= request.getContextPath() %>/instructor/live-session?sessionId=<%= sessionRow.getSessionId() %>" target="_blank" rel="noopener" data-i18n="instructor.sessions.openLiveSession">Open Live Session</a>
                                            <form method="post" action="<%= request.getContextPath() %>/instructor/sessions" style="display:inline;">
                                                <input type="hidden" name="action" value="complete">
                                                <input type="hidden" name="sessionId" value="<%= sessionRow.getSessionId() %>">
                                                <button class="isess-btn isess-btn--edit" type="submit" data-i18n="instructor.sessions.endSession">End Session</button>
                                            </form>
                                            <% } %>
                                        </div>
                                        <div class="isess-participants-section">
                                            <h4 class="isess-participants-section__title">
                                                <svg viewBox="0 0 24 24" fill="none"><circle cx="9" cy="7" r="4" stroke="currentColor" stroke-width="1.7"/><path d="M3 21a6 6 0 0 1 12 0" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
                                                <span data-i18n="instructor.sessions.enrolledStudents">Enrolled Students</span> <span class="isess-participants-section__count"><%= participantCount %></span>
                                            </h4>
                                            <% if (participants == null || participants.isEmpty()) { %>
                                            <div class="isess-participants-empty" data-i18n="instructor.sessions.noStudentsEnrolled">No students enrolled yet.</div>
                                            <% } else { %>
                                            <table class="isess-participants-table">
                                                <thead><tr><th data-i18n="instructor.sessions.colStudent">Student</th><th data-i18n="instructor.sessions.colRegistrationNo">Reg. No.</th><th data-i18n="instructor.sessions.colStatus">Status</th></tr></thead>
                                                <tbody>
                                                <% for (SessionParticipantView p : participants) { if (p == null) continue; %>
                                                <tr>
                                                    <td>
                                                        <div class="isess-participant-row">
                                                            <span class="isess-participant-avatar"><%= p.getFullName() == null ? "?" : p.getFullName().substring(0,1).toUpperCase() %></span>
                                                            <div><div class="isess-participant-name"><%= text(p.getFullName()) %></div><div class="isess-participant-email"><% if (p.isAccountRemoved()) { %><span data-i18n="instructor.sessions.accountRemoved">Account removed</span><% } else { %><%= text(p.getEmail()) %><% } %></div></div>
                                                        </div>
                                                    </td>
                                                    <td><%= text(p.getRegistrationNumber()) %></td>
                                                    <td><span class="isess-enroll-pill isess-enroll-pill--<%= p.getEnrollmentStatus() == null ? "unknown" : p.getEnrollmentStatus().name().toLowerCase() %>"><%= p.getEnrollmentStatus() == null ? "-" : p.getEnrollmentStatus().name() %></span></td>
                                                </tr>
                                                <% } %>
                                                </tbody>
                                            </table>
                                            <% } %>
                                        </div>
                                    </div>
                                </td>
                            </tr>
                            <% } %>
                            </tbody>
                        </table>
                    </div>
                    <% } %>
                    </div><%-- end #iup-view-scheduled --%>

                    <%-- ═══ HISTORY VIEW ═══ --%>
                    <div class="iup-view iup-fade-in" id="iup-view-completed" role="tabpanel" data-iup-view="completed" hidden>

                    <div class="isess-history-bar">
                        <div class="isess-history-bar__search">
                            <svg class="isess-history-bar__search-icon" viewBox="0 0 24 24" fill="none"><circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="1.8"/><path d="m21 21-4.35-4.35" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
                            <input type="search" placeholder="Search session name…" data-history-search class="isess-history-bar__input" data-i18n="instructor.sessions.searchPlaceholder" data-i18n-attr="placeholder">
                        </div>
                        <div class="isess-history-bar__filters">
                            <div class="isess-filter-group"><label class="isess-filter-group__label" data-i18n="instructor.sessions.filterDate">Date</label><input type="date" data-history-date class="isess-filter-input"></div>
                            <div class="isess-filter-group"><label class="isess-filter-group__label" data-i18n="instructor.sessions.filterStatus">Status</label>
                                <select data-history-status class="isess-filter-input">
                                    <option value="" data-i18n="instructor.sessions.filterAll">All</option>
                                    <option value="COMPLETED" data-i18n="instructor.sessions.statusCompleted">Completed</option>
                                    <option value="CANCELLED" data-i18n="instructor.sessions.statusCancelled">Cancelled</option>
                                </select>
                            </div>
                        </div>
                    </div>

                    <% if (historySessions.isEmpty()) { %>
                    <div class="isess-empty-state">
                        <div class="isess-empty-state__art" aria-hidden="true"><svg viewBox="0 0 120 100" fill="none"><rect x="20" y="15" width="80" height="70" rx="8" fill="#F1F5F9"/><rect x="30" y="30" width="60" height="6" rx="3" fill="#E2E8F0"/><rect x="30" y="44" width="45" height="5" rx="2.5" fill="#E2E8F0"/><rect x="30" y="56" width="52" height="5" rx="2.5" fill="#E2E8F0"/><rect x="30" y="68" width="38" height="5" rx="2.5" fill="#E2E8F0"/><circle cx="90" cy="22" r="14" fill="#89F336" opacity=".12"/><path d="m86 22 3 3 5-5" stroke="#89F336" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/><circle cx="90" cy="22" r="8" stroke="#89F336" stroke-width="1.8"/></svg></div>
                        <h3 class="isess-empty-state__title" data-i18n="instructor.sessions.noCompletedYet">No completed sessions yet</h3>
                        <p class="isess-empty-state__desc" data-i18n="instructor.sessions.noCompletedSub">Completed and cancelled sessions appear here automatically.</p>
                    </div>
                    <% } else { %>
                    <div class="isess-table-wrap">
                        <table class="isess-table isess-history-table" data-history-table>
                            <thead><tr><th style="width:36px;"></th><th data-i18n="instructor.sessions.colSession">Session</th><th data-i18n="instructor.sessions.colDate">Date</th><th data-i18n="instructor.sessions.colDuration">Duration</th><th data-i18n="instructor.sessions.colStudents">Students</th><th data-i18n="instructor.sessions.colStatus">Status</th></tr></thead>
                            <tbody>
                            <% for (TasmiSession sessionRow : historySessions) {
                                   List<SessionParticipantView> participants = participantsBySession == null ? null : participantsBySession.get(sessionRow.getSessionId());
                                   int participantCount = participants == null ? 0 : participants.size();
                                   String hDetailId = "history-detail-" + sessionRow.getSessionId();
                                   String titleData  = rawText(sessionRow.getTitle()).toLowerCase();
                                   String dateData   = sessionRow.getSessionDate() == null ? "" : String.valueOf(sessionRow.getSessionDate());
                                   String statusData = statusLabel(sessionRow);
                            %>
                            <tr class="isess-summary-row isess-history-row" data-history-row
                                data-detail-id="<%= hDetailId %>" data-title="<%= text(titleData) %>" data-date="<%= text(dateData) %>" data-status="<%= text(statusData) %>"
                                onclick="toggleSessionDetail('<%= hDetailId %>',this)" style="cursor:pointer;">
                                <td><button class="isess-toggle-btn" type="button" aria-label="Toggle details"><svg viewBox="0 0 24 24" fill="none"><path d="M6 9l6 6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg></button></td>
                                <td>
                                    <div class="isess-session-title-text"><%= text(sessionRow.getTitle()) %></div>
                                    <div class="isess-session-desc"><%= text(sessionRow.getQuranPortion()) %></div>
                                </td>
                                <td class="isess-col-meta"><%= sessionRow.getSessionDate() == null ? "-" : sessionRow.getSessionDate() %></td>
                                <td class="isess-col-meta"><%= sessionRow.getDurationMinutes() == null ? "-" : sessionRow.getDurationMinutes() + " min" %></td>
                                <td class="isess-col-meta"><%= participantCount %></td>
                                <td><span class="isess-pill isess-pill--soft <%= statusPillClass(sessionRow) %>"><%= statusLabel(sessionRow).toLowerCase() %></span></td>
                            </tr>
                            <tr class="isess-detail-row" id="<%= hDetailId %>" data-history-detail>
                                <td colspan="6">
                                    <div class="isess-detail-inner isess-history-detail">
                                        <div class="isess-detail-grid">
                                            <div class="isess-detail-item"><div class="isess-detail-label" data-i18n="instructor.sessions.colDate">Date</div><div class="isess-detail-value"><%= sessionRow.getSessionDate() == null ? "-" : sessionRow.getSessionDate() %></div></div>
                                            <div class="isess-detail-item"><div class="isess-detail-label" data-i18n="instructor.sessions.colTime">Time</div><div class="isess-detail-value"><%= sessionRow.getSessionTime() == null ? "-" : sessionRow.getSessionTime() %></div></div>
                                            <div class="isess-detail-item"><div class="isess-detail-label" data-i18n="instructor.sessions.colDuration">Duration</div><div class="isess-detail-value"><%= sessionRow.getDurationMinutes() == null ? "-" : sessionRow.getDurationMinutes() + " min" %></div></div>
                                            <div class="isess-detail-item"><div class="isess-detail-label" data-i18n="instructor.sessions.colStudents">Students</div><div class="isess-detail-value"><%= participantCount %></div></div>
                                            <div class="isess-detail-item"><div class="isess-detail-label" data-i18n="instructor.sessions.colFee">Fee</div><div class="isess-detail-value"><%= sessionRow.getFee() == null ? "Free" : sessionRow.getFee() %></div></div>
                                            <div class="isess-detail-item"><div class="isess-detail-label" data-i18n="instructor.sessions.colEndedAt">Ended At</div><div class="isess-detail-value"><%= sessionRow.getLiveEndedAt() == null ? "-" : sessionRow.getLiveEndedAt() %></div></div>
                                            <% if (sessionRow.getZoomMeetingId() != null) { %><div class="isess-detail-item"><div class="isess-detail-label">Zoom</div><div class="isess-detail-value">#<%= sessionRow.getZoomMeetingId() %></div></div><% } %>
                                        </div>
                                        <% if (sessionRow.getDescription() != null && !sessionRow.getDescription().trim().isEmpty()) { %><div class="isess-detail-desc"><%= text(sessionRow.getDescription()) %></div><% } %>
                                        <div class="isess-history-note" data-i18n="instructor.sessions.archivedNote">Archived for enrollment and payment records.</div>
                                    </div>
                                </td>
                            </tr>
                            <% } %>
                            </tbody>
                        </table>
                        <div class="isess-empty-state isess-history-no-results" data-history-empty hidden>
                            <h3 class="isess-empty-state__title" data-i18n="instructor.sessions.noMatchingCompleted">No matching sessions</h3>
                            <p class="isess-empty-state__desc" data-i18n="instructor.sessions.noMatchingSub">Try a different name, date, or status filter.</p>
                        </div>
                    </div>
                    <% } %>
                    </div><%-- end #iup-view-completed --%>

                    </div>
                    <%@ include file="/jsp/common/app_footer.jspf" %>
                </main>
            </div>
        </div>
    </div>
</div>

<%-- Modal --%>
<div class="isess-modal-overlay <%= showEditModal ? "active" : "" %>" id="createSessionModal">
    <div class="isess-modal">
        <div class="isess-modal__header">
            <div class="isess-modal__header-left">
                <span class="isess-modal__header-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none"><rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg></span>
                <div>
                    <h3 class="isess-modal__title"><% if (editSession == null) { %><span data-i18n="instructor.sessions.newSession">New Session</span><% } else { %><span data-i18n="instructor.sessions.editSessionTitle" data-i18n-id="<%= editSession.getSessionId() %>">Edit Session #<%= editSession.getSessionId() %></span><% } %></h3>
                    <p class="isess-modal__subtitle" data-i18n="instructor.sessions.modalSubtitle">Fill in the details to schedule a live recitation session.</p>
                </div>
            </div>
            <% if (editSession != null) { %>
            <a class="isess-modal__close" href="<%= request.getContextPath() %>/instructor/sessions" data-i18n="instructor.sessions.close" data-i18n-attr="aria-label" aria-label="Close"><svg viewBox="0 0 24 24" fill="none"><path d="M18 6 6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg></a>
            <% } else { %>
            <button class="isess-modal__close" type="button" data-i18n="instructor.sessions.close" data-i18n-attr="aria-label" aria-label="Close" onclick="document.getElementById('createSessionModal').classList.remove('active')"><svg viewBox="0 0 24 24" fill="none"><path d="M18 6 6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg></button>
            <% } %>
        </div>
        <form method="post" action="<%= request.getContextPath() %>/instructor/sessions" enctype="multipart/form-data">
            <input type="hidden" name="action" value="<%= editSession == null ? "create" : "update" %>">
            <% if (editSession != null) { %><input type="hidden" name="sessionId" value="<%= editSession.getSessionId() %>"><% } %>
            <div class="isess-modal__body">
                <div class="isess-modal__section">
                    <div class="isess-modal__section-label"><span class="isess-modal__section-num">1</span><span data-i18n="instructor.sessions.sectionBasicInfo">Basic Information</span></div>
                    <div class="isess-form-grid">
                        <div class="isess-field full-width"><label class="isess-label"><span data-i18n="instructor.sessions.labelSessionTitle">Session Title</span> <span class="req">*</span></label><input type="text" name="title" required value="<%= editSession == null || editSession.getTitle() == null ? "" : editSession.getTitle() %>" placeholder="e.g. Quran memorization — Surah Al-Baqarah" data-i18n="instructor.sessions.placeholderSessionTitle" data-i18n-attr="placeholder"></div>
                        <div class="isess-field full-width"><label class="isess-label" data-i18n="instructor.sessions.labelDescription">Description</label><textarea name="description" placeholder="Outline session objectives, recitation scope, or teaching notes." data-i18n="instructor.sessions.placeholderDescription" data-i18n-attr="placeholder"><%= editSession == null || editSession.getDescription() == null ? "" : editSession.getDescription() %></textarea></div>
                        <div class="isess-field full-width"><label class="isess-label"><span data-i18n="instructor.sessions.labelTargetLevel">Target Level</span> <span class="req">*</span></label>
                            <select name="level" required>
                                <option value="" disabled <%= (editSession == null || editSession.getLevel() == null) ? "selected" : "" %> data-i18n="instructor.sessions.optionSelectTargetLevel">Select level</option>
                                <option value="PRIMARY_SCHOOL"   <%= (editSession != null && editSession.getLevel() != null && "PRIMARY_SCHOOL".equals(editSession.getLevel().name()))   ? "selected" : "" %> data-i18n="instructor.sessions.levelPrimarySchool">Primary School</option>
                                <option value="SECONDARY_SCHOOL" <%= (editSession != null && editSession.getLevel() != null && "SECONDARY_SCHOOL".equals(editSession.getLevel().name())) ? "selected" : "" %> data-i18n="instructor.sessions.levelSecondarySchool">Secondary School</option>
                                <option value="HIGH_SCHOOL"      <%= (editSession != null && editSession.getLevel() != null && "HIGH_SCHOOL".equals(editSession.getLevel().name()))      ? "selected" : "" %> data-i18n="instructor.sessions.levelHighSchool">High School</option>
                                <option value="UNIVERSITY"       <%= (editSession != null && editSession.getLevel() != null && "UNIVERSITY".equals(editSession.getLevel().name()))       ? "selected" : "" %> data-i18n="instructor.sessions.levelUniversity">University</option>
                            </select>
                        </div>
                        <div class="isess-field"><label class="isess-label" data-i18n="instructor.sessions.labelRecitationTopic">Recitation / Surah</label><input type="text" name="quranPortion" value="<%= editSession == null || editSession.getQuranPortion() == null ? "" : editSession.getQuranPortion() %>" placeholder="e.g. Surah Al-Baqarah 1–20" data-i18n="instructor.sessions.placeholderRecitationTopic" data-i18n-attr="placeholder"></div>
                        <div class="isess-field isess-field--range"><label class="isess-label"><span data-i18n="instructor.sessions.labelSurah">Surah</span> <span class="req">*</span></label><input type="number" name="surahNumber" min="1" max="114" inputmode="numeric" value="<%= editSession == null || editSession.getSurahNumber() == null ? "" : editSession.getSurahNumber() %>" placeholder="1–114"></div>
                        <div class="isess-field isess-field--range"><label class="isess-label"><span data-i18n="instructor.sessions.labelAyahStart">Ayah start</span> <span class="req">*</span></label><input type="number" name="ayahStart" min="1" max="286" inputmode="numeric" value="<%= editSession == null || editSession.getAyahStart() == null ? "" : editSession.getAyahStart() %>" placeholder="First ayah"></div>
                        <div class="isess-field isess-field--range"><label class="isess-label"><span data-i18n="instructor.sessions.labelAyahEnd">Ayah end</span> <span class="req">*</span></label><input type="number" name="ayahEnd" min="1" max="286" inputmode="numeric" value="<%= editSession == null || editSession.getAyahEnd() == null ? "" : editSession.getAyahEnd() %>" placeholder="Last ayah"></div>
                        <p class="isess-range-hint full-width" data-i18n="instructor.sessions.structuredRangeHint">Required for AI recitation analysis. Sessions without a structured Surah and ayah range cannot be auto-analyzed.</p>
                    </div>
                </div>
                <div class="isess-modal__section">
                    <div class="isess-modal__section-label"><span class="isess-modal__section-num">2</span><span data-i18n="instructor.sessions.sectionSchedule">Schedule &amp; Duration</span></div>
                    <div class="isess-form-grid">
                        <div class="isess-field"><label class="isess-label"><span data-i18n="instructor.sessions.labelSessionDate">Date</span> <span class="req">*</span></label><input type="date" name="sessionDate" required value="<%= editSession == null || editSession.getSessionDate() == null ? "" : editSession.getSessionDate() %>"></div>
                        <div class="isess-field"><label class="isess-label"><span data-i18n="instructor.sessions.labelSessionTime">Time</span> <span class="req">*</span></label><input type="time" name="sessionTime" required value="<%= editSession == null || editSession.getSessionTime() == null ? "" : editSession.getSessionTime() %>"></div>
                        <div class="isess-field"><label class="isess-label"><span data-i18n="instructor.sessions.labelDurationMinutes">Duration (Minutes)</span> <span class="req">*</span></label><input type="number" name="durationMinutes" min="15" max="360" required value="<%= editSession == null || editSession.getDurationMinutes() == null ? "60" : editSession.getDurationMinutes() %>"></div>
                    </div>
                </div>
                <div class="isess-modal__section">
                    <div class="isess-modal__section-label"><span class="isess-modal__section-num">3</span><span data-i18n="instructor.sessions.sectionCapacity">Capacity &amp; Pricing</span></div>
                    <div class="isess-form-grid">
                        <div class="isess-field"><label class="isess-label"><span data-i18n="instructor.sessions.labelMaxStudents">Max Students</span> <span class="req">*</span></label><input type="number" name="capacity" min="1" max="500" required value="<%= editSession == null ? "10" : editSession.getCapacity() %>"></div>
                        <div class="isess-field"><label class="isess-label"><span data-i18n="instructor.sessions.labelFee">Fee</span> <span class="req">*</span></label><input type="number" step="0.01" min="0" name="fee" required value="<%= editSession == null || editSession.getFee() == null ? "0.00" : editSession.getFee() %>"></div>
                        <div class="isess-field full-width"><label class="isess-label" data-i18n="instructor.sessions.labelSessionBanner">Session Banner (optional)</label>
                            <div class="isess-banner-zone" id="bannerZone">
                                <input type="file" id="bannerFileInput" name="bannerImage" accept="image/jpeg,image/png,image/webp" onchange="previewBanner(this);" style="position:absolute;inset:0;opacity:0;cursor:pointer;">
                                <svg class="zone-icon" id="bannerZoneIcon" viewBox="0 0 24 24" fill="none"><rect x="3" y="3" width="18" height="18" rx="2" stroke="currentColor" stroke-width="2"/><circle cx="8.5" cy="8.5" r="1.5" stroke="currentColor" stroke-width="2"/><path d="m21 15-5-5L5 21" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                <div class="zone-text" id="bannerZoneText" data-i18n="instructor.sessions.bannerUploadText">Click to upload a banner image</div>
                                <div class="zone-hint" id="bannerZoneHint" data-i18n="instructor.sessions.bannerUploadHint">JPG, PNG, or WEBP · max 3 MB</div>
                                <img id="bannerPreview" style="display:none;max-width:100%;max-height:120px;border-radius:8px;margin-top:0.5rem;" alt="Banner preview">
                            </div>
                        </div>
                    </div>
                    <div class="isess-zoom-hint"><svg viewBox="0 0 24 24" fill="none"><rect x="2" y="7" width="13" height="10" rx="2" stroke="currentColor" stroke-width="1.7"/><path d="m22 8-5 4 5 4V8Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/></svg><span><span data-i18n="instructor.sessions.zoomHint">A Zoom meeting is created automatically. Configure your Zoom email in</span> <a href="<%= request.getContextPath() %>/instructor/profile" data-i18n="instructor.sessions.profileSettingsLink">profile settings</a>.</span></div>
                </div>
            </div>
            <div class="isess-modal__footer">
                <% if (editSession != null) { %><a class="isess-modal-btn isess-modal-btn--cancel" href="<%= request.getContextPath() %>/instructor/sessions" data-i18n="instructor.payments.cancel">Cancel</a>
                <% } else { %><button class="isess-modal-btn isess-modal-btn--cancel" type="button" onclick="document.getElementById('createSessionModal').classList.remove('active')" data-i18n="instructor.payments.cancel">Cancel</button><% } %>
                <button class="isess-modal-btn isess-modal-btn--submit" type="submit">
                    <svg viewBox="0 0 24 24" fill="none"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><path d="M17 21v-8H7v8M7 3v5h8" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>
                    <% if (editSession == null) { %><span data-i18n="instructor.sessions.createSessionBtn">Create Session</span><% } else { %><span data-i18n="instructor.sessions.saveChanges">Save Changes</span><% } %>
                </button>
            </div>
        </form>
    </div>
</div>

<script>
function toggleSessionDetail(id, row) {
    var detail = document.getElementById(id);
    if (!detail) return;
    var open = detail.classList.contains('is-open');
    document.querySelectorAll('.isess-detail-row.is-open').forEach(function(r){r.classList.remove('is-open');});
    document.querySelectorAll('.isess-summary-row.is-expanded').forEach(function(r){r.classList.remove('is-expanded');});
    document.querySelectorAll('.isess-toggle-btn.is-open').forEach(function(b){b.classList.remove('is-open');});
    if (!open) {
        detail.classList.add('is-open');
        row.classList.add('is-expanded');
        var btn = row.querySelector('.isess-toggle-btn');
        if (btn) btn.classList.add('is-open');
    }
}
function previewBanner(input) {
    var preview = document.getElementById('bannerPreview');
    var icon = document.getElementById('bannerZoneIcon');
    var text = document.getElementById('bannerZoneText');
    var hint = document.getElementById('bannerZoneHint');
    if (input.files && input.files[0]) {
        var reader = new FileReader();
        reader.onload = function(e) { preview.src = e.target.result; preview.style.display='block'; icon.style.display='none'; text.textContent=input.files[0].name; hint.textContent='Click to choose a different image.'; };
        reader.readAsDataURL(input.files[0]);
    }
}
function toggleMoreMenu(btn) {
    var wrap = btn.closest('.isess-more-wrap');
    var menu = wrap ? wrap.querySelector('.isess-more-menu') : null;
    if (!menu) return;
    var showing = menu.classList.contains('is-open');
    document.querySelectorAll('.isess-more-menu.is-open').forEach(function(m){m.classList.remove('is-open');m.setAttribute('aria-hidden','true');});
    document.querySelectorAll('.isess-more-wrap > button[aria-expanded]').forEach(function(b){b.setAttribute('aria-expanded','false');});
    if (!showing) {
        menu.classList.add('is-open'); btn.setAttribute('aria-expanded','true'); menu.setAttribute('aria-hidden','false');
        setTimeout(function(){document.addEventListener('click',function closeMenu(e){if(!wrap.contains(e.target)){menu.classList.remove('is-open');btn.setAttribute('aria-expanded','false');menu.setAttribute('aria-hidden','true');document.removeEventListener('click',closeMenu);}});},0);
    }
}
document.addEventListener('keydown',function(e){if(e.key==='Escape'){document.getElementById('createSessionModal').classList.remove('active');document.querySelectorAll('.isess-more-menu.is-open').forEach(function(m){m.classList.remove('is-open');});}});
document.getElementById('createSessionModal').addEventListener('click',function(e){if(e.target===this)this.classList.remove('active');});
(function(){
    var form=document.querySelector('#createSessionModal form');
    if(!form)return;
    form.addEventListener('submit',function(){
        var btn=form.querySelector('button[type="submit"]');
        if(!btn||btn.disabled)return;
        btn.disabled=true;
    });
})();
(function(){
    var btns=document.querySelectorAll('[data-iup-toggle]'), views=document.querySelectorAll('[data-iup-view]');
    if (!btns.length||!views.length) return;
    function activate(t){btns.forEach(function(b){b.setAttribute('aria-selected',b.getAttribute('data-iup-toggle')===t?'true':'false');});views.forEach(function(v){var m=v.getAttribute('data-iup-view')===t;v.hidden=!m;if(m){v.classList.remove('iup-fade-in');void v.offsetWidth;v.classList.add('iup-fade-in');}});}
    btns.forEach(function(b){b.addEventListener('click',function(){var t=b.getAttribute('data-iup-toggle');if(t)activate(t);});});
})();
(function(){
    var search=document.querySelector('[data-history-search]'),date=document.querySelector('[data-history-date]'),status=document.querySelector('[data-history-status]');
    var rows=Array.prototype.slice.call(document.querySelectorAll('[data-history-row]'));
    var empty=document.querySelector('[data-history-empty]');
    if(!rows.length)return;
    function apply(){
        var q=search&&search.value?search.value.trim().toLowerCase():'',sd=date&&date.value?date.value:'',ss=status&&status.value?status.value:'',vis=0;
        rows.forEach(function(r){
            var detail=document.getElementById(r.dataset.detailId||'');
            var ok=(!q||(r.dataset.title||'').indexOf(q)!==-1)&&(!sd||r.dataset.date===sd)&&(!ss||r.dataset.status===ss);
            r.hidden=!ok; if(!ok)r.classList.remove('is-expanded');
            if(detail){detail.hidden=!ok;if(!ok)detail.classList.remove('is-open');}
            if(ok)vis++;
        });
        if(empty)empty.hidden=vis!==0;
    }
    [search,date,status].forEach(function(c){if(c){c.addEventListener('input',apply);c.addEventListener('change',apply);}});
})();
</script>
</body>
</html>
