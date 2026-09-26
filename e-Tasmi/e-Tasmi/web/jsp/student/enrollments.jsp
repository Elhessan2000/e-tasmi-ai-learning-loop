<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="model.entity.TasmiSession" %>
<%@ page import="model.entity.TasmiSessionStatus" %>
<%!
    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String text(Object val) {
        return val == null ? "" : val.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    private static String joinErrorMessage(String code) {
        if (code == null || code.trim().isEmpty()) {
            return "We could not open the meeting link right now.";
        }
        switch (code.trim()) {
            case "invalid_request":
                return "The meeting request was invalid.";
            case "you_are_not_enrolled_in_this_session_":
                return "You are not enrolled in this session.";
            case "your_enrollment_is_not_approved_yet_":
                return "Your enrollment is not approved yet.";
            case "successful_payment_is_required_before_joining_this_live_session_":
                return "Payment must be completed successfully before you can open this meeting link.";
            case "this_live_session_is_not_running_right_now_":
                return "The instructor has not shared access to this meeting yet.";
            case "this_live_session_does_not_have_a_participant_join_link_yet_":
            case "join_url_missing":
                return "Meeting link is not available yet. Please wait for the instructor to start the session.";
            case "meeting_id_missing":
                return "This session does not have a Zoom meeting number yet. Please try again later.";
            case "embed_unavailable":
            case "embed_disabled":
                return "Open this session using Join session to launch Zoom in your browser or app.";
            case "passcode_missing":
                return "This meeting needs a passcode that is not stored in the system. Open the session in Zoom instead, or contact support.";
            case "access_denied":
                return "You do not have access to join this meeting.";
            case "server_error":
                return "The server could not open the meeting link right now. Please try again.";
            default:
                return "We could not open the meeting link right now.";
        }
    }

    private static String formatEnrollmentFee(TasmiSession session) {
        if (session == null || session.getFee() == null
                || session.getFee().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return "Free session";
        }
        return "RM " + session.getFee().stripTrailingZeros().toPlainString();
    }
%>
<%
    request.setAttribute("activeMenu", "enrollments");
    String ctx = request.getContextPath();
    List<Map<String, Object>> enrollmentCards = (List<Map<String, Object>>) request.getAttribute("enrollmentCards");
    String success = (String) request.getAttribute("success");
    String error = (String) request.getAttribute("error");
    String joinError = request.getParameter("joinError");

    List<Map<String, Object>> upcomingCards = new ArrayList<>();
    List<Map<String, Object>> completedCards = new ArrayList<>();
    if (enrollmentCards != null) {
        for (Map<String, Object> card : enrollmentCards) {
            TasmiSession s = (TasmiSession) card.get("session");
            if (s == null) {
                continue;
            }
            if (s.getStatus() == TasmiSessionStatus.COMPLETED) {
                completedCards.add(card);
            } else {
                upcomingCards.add(card);
            }
        }
    }

    Collections.sort(completedCards, new Comparator<Map<String, Object>>() {
        @Override
        public int compare(Map<String, Object> a, Map<String, Object> b) {
            TasmiSession sa = (TasmiSession) a.get("session");
            TasmiSession sb = (TasmiSession) b.get("session");
            if (sa == null && sb == null) {
                return 0;
            }
            if (sa == null) {
                return 1;
            }
            if (sb == null) {
                return -1;
            }
            LocalDate da = sa.getSessionDate();
            LocalDate db = sb.getSessionDate();
            if (da == null && db == null) {
                return Long.compare(sb.getSessionId(), sa.getSessionId());
            }
            if (da == null) {
                return 1;
            }
            if (db == null) {
                return -1;
            }
            int c = db.compareTo(da);
            if (c != 0) {
                return c;
            }
            return Long.compare(sb.getSessionId(), sa.getSessionId());
        }
    });

    boolean defaultCompletedTab = upcomingCards.isEmpty() && !completedCards.isEmpty();

    DateTimeFormatter mysDateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    DateTimeFormatter mysTimeFmt = DateTimeFormatter.ofPattern("hh:mm a");
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
  <title data-i18n="meta.studentMySessionsTitle">My Sessions - e-Tasmi</title>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <%@ include file="/jsp/common/student_ui_head.jspf" %>
  <link rel="stylesheet" href="<%= ctx %>/css/student-my-sessions.css?v=20260428-mys-cards-ui2-saas">
  <link rel="stylesheet" href="<%= ctx %>/css/student-ui-refine.css?v=20260420-student-sidebar1">
  <script defer src="<%= ctx %>/assets/js/app.js"></script>
  <script defer src="<%= ctx %>/assets/js/my-sessions.js?v=20260429-mys1"></script>
</head>
<body class="student-premium-page student-package-page student-module-page student-my-sessions-page">
<div class="app-shell">
  <%@ include file="/jsp/common/student_header.jspf" %>
  <div class="app-main">
    <div class="container sd-container student-workspace-shell">
      <div class="student-shell-layout">
        <%@ include file="/jsp/student/student_sidebar.jspf" %>

        <main class="student-shell-content" role="main">
          <div class="student-workspace-view">
            <%@ include file="/jsp/common/student_breadcrumb.jspf" %>
            <header class="student-hero student-hero--enrollments" aria-labelledby="mysBannerTitle">
              <div class="student-hero__copy">
                <h1 id="mysBannerTitle" class="student-hero__title" data-i18n="student.enrollments.title">My Sessions</h1>
                <p class="student-hero__sub" data-i18n="student.enrollments.subtitle">Manage your enrolled sessions and view evaluations</p>
              </div>
              <div class="student-hero__actions">
                <a class="student-hero__btn" href="<%= LocaleSupport.localizedUrl(request, "/student/available-sessions") %>" data-i18n="student.enrollments.browseCta">Browse Available Sessions</a>
              </div>
            </header>

            <% if (joinError != null && !joinError.trim().isEmpty()) { %>
            <div class="mys-alert mys-alert--warning" role="alert">
                <svg viewBox="0 0 24 24" fill="none"><path d="M12 9v4m0 4h.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>
                <%= joinErrorMessage(joinError) %>
            </div>
            <% } %>

            <% if (success != null) { %>
            <div class="mys-alert mys-alert--success" role="status">
                <svg viewBox="0 0 24 24" fill="none"><path d="M9 12l2 2 4-4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/></svg>
                <%= success %>
            </div>
            <% } %>
            <% if (error != null) { %>
            <div class="mys-alert mys-alert--error" role="alert">
                <svg viewBox="0 0 24 24" fill="none"><path d="M12 8v4m0 4h.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/></svg>
                <%= error %>
            </div>
            <% } %>

            <% if (enrollmentCards == null || enrollmentCards.isEmpty()) { %>
            <div class="mys-empty">
                <div class="mys-empty__title" data-i18n="student.enrollments.emptyTitle">No confirmed sessions yet</div>
                <div class="mys-empty__sub" data-i18n="student.enrollments.emptySub">After enrollment and payment confirmation are completed, your registered sessions will appear here.</div>
                <div class="mys-empty__actions">
                  <a href="<%= LocaleSupport.localizedUrl(request, "/student/available-sessions") %>" data-i18n="student.enrollments.browseCta">Browse Available Sessions</a>
                </div>
            </div>
            <% } else { %>

            <div class="mys-tabs-wrap">
              <div class="mys-tabs" role="tablist" data-i18n="student.enrollments.scheduleAria" data-i18n-attr="aria-label" aria-label="Session schedule">
                <button type="button"
                        class="mys-tabs__btn"
                        role="tab"
                        id="mys-tab-upcoming"
                        aria-controls="mys-panel-upcoming"
                        aria-selected="<%= defaultCompletedTab ? "false" : "true" %>"
                        data-mys-tab="upcoming"
                        data-i18n="student.enrollments.tabUpcoming">Upcoming</button>
                <button type="button"
                        class="mys-tabs__btn"
                        role="tab"
                        id="mys-tab-completed"
                        aria-controls="mys-panel-completed"
                        aria-selected="<%= defaultCompletedTab ? "true" : "false" %>"
                        data-mys-tab="completed"
                        data-i18n="student.enrollments.tabCompleted">Completed</button>
              </div>
            </div>

            <div id="mys-panel-upcoming"
                 role="tabpanel"
                 aria-labelledby="mys-tab-upcoming"
                 <%= defaultCompletedTab ? "hidden" : "" %>>
              <% if (upcomingCards.isEmpty()) { %>
              <div class="mys-tab-empty" data-i18n="student.enrollments.tabEmptyUpcoming">No upcoming sessions in this list.</div>
              <% } else { %>
              <div class="mys-grid">
                <% for (Map<String, Object> card : upcomingCards) {
                       TasmiSession tasmiSession = (TasmiSession) card.get("session");
                       if (tasmiSession == null) continue;
                       boolean joinReady = Boolean.TRUE.equals(card.get("joinReady"));
                       String instructorName = card.get("instructorName") == null ? "Instructor" : String.valueOf(card.get("instructorName"));
                       String meetingLink = trimToNull(tasmiSession.getResolvableParticipantJoinUrl());
                       boolean showMeetingLink = joinReady && meetingLink != null;
                       String cardTitle = trimToNull(tasmiSession.getTitle()) == null ? ("Session #" + tasmiSession.getSessionId()) : tasmiSession.getTitle();
                       String dateShown = tasmiSession.getSessionDate() == null ? "—" : mysDateFmt.format(tasmiSession.getSessionDate());
                       String timeShown = tasmiSession.getSessionTime() == null ? "—" : mysTimeFmt.format(tasmiSession.getSessionTime());
                       String joinHref = ctx + "/student/joinLive?sessionId=" + tasmiSession.getSessionId();
                       String uploadHref = ctx + "/student/recitations";
                       String upcomingBadgeClass;
                       String upcomingBadgeLabel;
                       if (showMeetingLink) {
                           upcomingBadgeClass = "mys-badge mys-badge--ready";
                           upcomingBadgeLabel = "Ready to Join";
                       } else if (joinReady) {
                           upcomingBadgeClass = "mys-badge mys-badge--slot-confirmed";
                           upcomingBadgeLabel = "Confirmed";
                       } else {
                           upcomingBadgeClass = "mys-badge mys-badge--upcoming";
                           upcomingBadgeLabel = "Upcoming";
                       }
                       String upcomingBadgeI18n = showMeetingLink ? "student.enrollments.badgeReadyToJoin"
                               : (joinReady ? "student.enrollments.badgeConfirmed" : "student.enrollments.badgeUpcoming");
                %>
                <article class="mys-card mys-card--upcoming<%= showMeetingLink ? " mys-card--join-ready" : "" %>">
                  <div class="mys-card__accent" aria-hidden="true"></div>
                  <div class="mys-card__inner">
                    <div class="mys-card__head">
                      <div>
                        <h2 class="mys-card__title"><%= text(cardTitle) %></h2>
                        <div class="mys-card__instructor">
                          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M20 21a8 8 0 1 0-16 0" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/><circle cx="12" cy="7" r="4" stroke="currentColor" stroke-width="1.75"/></svg>
                          <span><%= text(instructorName) %></span>
                        </div>
                      </div>
                      <span class="<%= upcomingBadgeClass %>" data-i18n="<%= upcomingBadgeI18n %>"><%= upcomingBadgeLabel %></span>
                    </div>
                    <div class="mys-card__meta-block">
                      <div class="mys-meta-row">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.6"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                        <span><span class="mys-meta-row__label" data-i18n="common.date">Date</span> <%= text(dateShown) %></span>
                      </div>
                      <div class="mys-meta-row">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.6"/><path d="M12 7v5l4 2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                        <span><span class="mys-meta-row__label" data-i18n="common.time">Time</span> <%= text(timeShown) %></span>
                      </div>
                    </div>
                    <div class="mys-card__foot">
                      <% if (showMeetingLink) { %>
                      <a class="mys-btn mys-btn--zoom" href="<%= joinHref %>" target="_blank" rel="noopener noreferrer">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="m23 7-7 5 7 5V7Z" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round"/><rect x="1" y="5" width="15" height="14" rx="2" stroke="currentColor" stroke-width="1.65"/></svg>
                        <span data-i18n="student.enrollments.joinZoom">Join Zoom</span>
                      </a>
                      <% } else { %>
                      <a class="mys-btn mys-btn--primary" href="<%= LocaleSupport.localizedUrl(request, "/student/recitations") %>">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round"/></svg>
                        <span data-i18n="student.enrollments.uploadRecitation">Upload Recitation</span>
                      </a>
                      <% } %>
                    </div>
                  </div>
                </article>
                <% } %>
              </div>
              <% } %>
            </div>

            <div id="mys-panel-completed"
                 role="tabpanel"
                 aria-labelledby="mys-tab-completed"
                 <%= defaultCompletedTab ? "" : "hidden" %>>
              <% if (completedCards.isEmpty()) { %>
              <div class="mys-tab-empty" data-i18n="student.enrollments.tabEmptyCompleted">No completed sessions yet.</div>
              <% } else { %>
              <div class="mys-grid">
                <% for (Map<String, Object> card : completedCards) {
                       TasmiSession tasmiSession = (TasmiSession) card.get("session");
                       if (tasmiSession == null) continue;
                       String instructorName = card.get("instructorName") == null ? "Instructor" : String.valueOf(card.get("instructorName"));
                       String cardTitle = trimToNull(tasmiSession.getTitle()) == null ? ("Session #" + tasmiSession.getSessionId()) : tasmiSession.getTitle();
                       String dateShown = tasmiSession.getSessionDate() == null ? "—" : mysDateFmt.format(tasmiSession.getSessionDate());
                       String timeShown = tasmiSession.getSessionTime() == null ? "—" : mysTimeFmt.format(tasmiSession.getSessionTime());
                       String feeShown = formatEnrollmentFee(tasmiSession);
                %>
                <article class="mys-card mys-card--completed">
                  <div class="mys-card__accent" aria-hidden="true"></div>
                  <div class="mys-card__inner">
                    <div class="mys-card__head">
                      <div>
                        <h2 class="mys-card__title"><%= text(cardTitle) %></h2>
                        <div class="mys-card__instructor">
                          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M20 21a8 8 0 1 0-16 0" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/><circle cx="12" cy="7" r="4" stroke="currentColor" stroke-width="1.75"/></svg>
                          <span><%= text(instructorName) %></span>
                        </div>
                      </div>
                      <span class="mys-badge mys-badge--completed" data-i18n="student.enrollments.badgeCompleted">Completed</span>
                    </div>
                    <ul class="mys-card__facts">
                      <li>
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.6"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                        <span><strong data-i18n="common.date">Date</strong> <%= text(dateShown) %></span>
                      </li>
                      <li>
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.6"/><path d="M12 7v5l4 2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                        <span><strong data-i18n="common.time">Time</strong> <%= text(timeShown) %></span>
                      </li>
                      <li>
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                        <span><strong data-i18n="common.fee">Fee</strong> <%= text(feeShown) %></span>
                      </li>
                    </ul>
                    <p class="mys-card__history-note" data-i18n="student.enrollments.sessionCompletedNote">Session completed successfully.</p>
                  </div>
                </article>
                <% } %>
              </div>
              <% } %>
            </div>

            <% } %>

          </div>
          <%@ include file="/jsp/common/app_footer.jspf" %>
        </main>
      </div>
    </div>
  </div>
</div>
</body>
</html>
