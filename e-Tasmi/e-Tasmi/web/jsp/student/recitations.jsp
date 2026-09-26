<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%!
    private static String esc(Object raw) {
        if (raw == null) return "";
        String s = String.valueOf(raw);
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : String.valueOf(v);
    }
%>
<%
    request.setAttribute("activeMenu", "recitations");

    String ctx = request.getContextPath();

    List<Map<String, Object>> eligibleSessions = (List<Map<String, Object>>) request.getAttribute("eligibleSessions");
    List<Map<String, Object>> historyItems = (List<Map<String, Object>>) request.getAttribute("historyItems");

    Object statTotal = request.getAttribute("statTotal");
    Object statReviewed = request.getAttribute("statReviewed");
    Object statPending = request.getAttribute("statPending");
    String totalStr = statTotal == null ? "0" : String.valueOf(statTotal);
    String reviewedStr = statReviewed == null ? "0" : String.valueOf(statReviewed);
    String pendingStr = statPending == null ? "0" : String.valueOf(statPending);

    String error = (String) request.getAttribute("error");
    String success = (String) request.getAttribute("success");
    boolean hasSessions = eligibleSessions != null && !eligibleSessions.isEmpty();
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.studentRecitationsTitle">Upload Recitation - e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <%@ include file="/jsp/common/student_ui_head.jspf" %>
    <link rel="stylesheet" href="<%= ctx %>/css/student-recitation-studio.css?v=20260624-student-redesign1">
    <script defer src="<%= ctx %>/assets/js/recitation-studio.js?v=20260607-hero5"></script>
</head>
<body class="student-premium-page student-package-page student-module-page student-recitations-page rec-studio-page">
<div class="app-shell">
    <%@ include file="/jsp/common/student_header.jspf" %>
    <div class="app-main">
        <div class="container sd-container student-workspace-shell">
            <div class="student-shell-layout">
                <%@ include file="/jsp/student/student_sidebar.jspf" %>

                <main class="student-shell-content" role="main"
                      data-rec-root
                      data-rec-endpoint="<%= ctx %>/student/recitations">
                    <div class="student-workspace-view">
                        <%@ include file="/jsp/common/student_breadcrumb.jspf" %>
                        <!-- ====== Page header ====== -->
                        <header class="student-hero student-hero--recitation">
                            <div class="student-hero__copy">
                                <h1 class="student-hero__title" data-i18n="student.recitations.title">Upload Recitation</h1>
                                <p class="student-hero__sub" data-i18n="student.recitations.subtitle">Record live or upload an audio file of your recitation and send it to your instructor for review.</p>
                            </div>
                            <div class="student-hero__visual" aria-hidden="true">
                                <span class="student-hero__badge student-hero__badge--wave">
                                    <span class="student-hero__bars">
                                        <span></span><span></span><span></span><span></span><span></span>
                                    </span>
                                </span>
                            </div>
                        </header>

                        <% if (success != null) { %>
                        <div class="rec-flash rec-flash--success" role="status">
                            <svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/><path d="M9 12l2 2 4-4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                            <span><%= esc(success) %></span>
                        </div>
                        <% } %>
                        <% if (error != null) { %>
                        <div class="rec-flash rec-flash--error" role="alert">
                            <svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/><path d="M12 8v4m0 4h.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                            <span><%= esc(error) %></span>
                        </div>
                        <% } %>

                        <% if (!hasSessions) { %>
                        <section class="rec-empty-state">
                            <span class="rec-empty-state__icon" aria-hidden="true">
                                <svg viewBox="0 0 24 24" fill="none"><rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.6"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                            </span>
                            <h2 class="rec-empty-state__title" data-i18n="student.recitations.emptyTitle">No session is ready for submission</h2>
                            <p class="rec-empty-state__sub" data-i18n="student.recitations.emptySub">You need a confirmed enrollment (and a successful payment for paid sessions) before you can submit a recitation.</p>
                            <div class="rec-empty-state__actions">
                                <a class="rec-btn rec-btn--primary" href="<%= LocaleSupport.localizedUrl(request, "/student/available-sessions") %>" data-i18n="student.recitations.browseSessions">Browse sessions</a>
                                <a class="rec-btn rec-btn--ghost" href="<%= LocaleSupport.localizedUrl(request, "/student/enrollments") %>" data-i18n="student.recitations.mySessions">My sessions</a>
                            </div>
                        </section>
                        <% } else { %>

                        <!-- ====== Workspace ====== -->
                        <section class="rec-workspace" data-i18n="student.recitations.workspaceAria" data-i18n-attr="aria-label" aria-label="Submit a recitation">
                            <!-- Choose a session -->
                            <div class="rec-panel">
                                <div class="rec-panel__head">
                                    <span class="rec-step-dot">1</span>
                                    <div>
                                        <h2 class="rec-panel__title" data-i18n="student.recitations.chooseSession">Choose a session</h2>
                                        <p class="rec-panel__sub" data-i18n="student.recitations.chooseSessionSub">Select the session for this recitation.</p>
                                    </div>
                                </div>

                                <div class="rec-select" id="recSelect">
                                    <button type="button" class="rec-select__trigger" data-rec-select-trigger
                                            aria-haspopup="listbox" aria-expanded="false">
                                        <span class="rec-select__icon" aria-hidden="true">
                                            <svg viewBox="0 0 24 24" fill="none"><rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.6"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                                        </span>
                                        <span class="rec-select__label" id="recSelectLabel" data-i18n="student.recitations.selectSession">Select a session</span>
                                        <span class="rec-select__chev" aria-hidden="true">
                                            <svg viewBox="0 0 24 24" fill="none"><path d="m6 9 6 6 6-6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                        </span>
                                    </button>

                                    <div class="rec-select__panel" id="recSelectPanel" role="listbox" hidden>
                                        <div class="rec-options">
                                            <% for (Map<String, Object> s : eligibleSessions) {
                                                   String enrollmentId = str(s, "enrollmentId");
                                                   String title = str(s, "title");
                                                   String instructor = str(s, "instructor");
                                                   String schedule = str(s, "schedule");
                                                   String portion = str(s, "portion");
                                                   String mode = str(s, "mode");
                                                   String fee = str(s, "fee");
                                                   String searchKey = (title + " " + instructor + " " + mode).toLowerCase();
                                            %>
                                            <button type="button" class="rec-option" role="option" aria-selected="false"
                                                    data-rec-session
                                                    data-search="<%= esc(searchKey) %>"
                                                    data-enrollment-id="<%= esc(enrollmentId) %>"
                                                    data-title="<%= esc(title) %>"
                                                    data-instructor="<%= esc(instructor) %>"
                                                    data-schedule="<%= esc(schedule) %>"
                                                    data-portion="<%= esc(portion) %>"
                                                    data-mode="<%= esc(mode) %>"
                                                    data-fee="<%= esc(fee) %>">
                                                <span class="rec-option__title"><%= esc(title) %></span>
                                                <span class="rec-option__meta">
                                                    <span><%= esc(instructor) %></span>
                                                    <span class="rec-option__sep">&middot;</span>
                                                    <span><%= esc(mode) %></span>
                                                    <span class="rec-option__sep">&middot;</span>
                                                    <span><%= esc(fee) %></span>
                                                </span>
                                                <span class="rec-option__check" aria-hidden="true">
                                                    <svg viewBox="0 0 24 24" fill="none"><path d="M5 12l4 4 10-10" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                                </span>
                                            </button>
                                            <% } %>
                                        </div>
                                    </div>
                                </div>

                                <!-- Session info strip -->
                                <div class="rec-info" id="recSummary" hidden aria-live="polite">
                                    <div class="rec-info__cell">
                                        <span class="rec-info__icon"><svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="8" r="3.4" stroke="currentColor" stroke-width="1.7"/><path d="M5 20a7 7 0 0 1 14 0" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg></span>
                                        <div><span class="rec-info__label" data-i18n="common.instructor">Instructor</span><span class="rec-info__value" id="recInfoInstructor">-</span></div>
                                    </div>
                                    <div class="rec-info__cell">
                                        <span class="rec-info__icon"><svg viewBox="0 0 24 24" fill="none"><rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.7"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.7"/></svg></span>
                                        <div><span class="rec-info__label" data-i18n="student.recitations.dateTime">Date &amp; Time</span><span class="rec-info__value" id="recInfoSchedule">-</span></div>
                                    </div>
                                    <div class="rec-info__cell">
                                        <span class="rec-info__icon"><svg viewBox="0 0 24 24" fill="none"><rect x="3" y="6" width="18" height="12" rx="2" stroke="currentColor" stroke-width="1.7"/><path d="M8 21h8M12 18v3" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg></span>
                                        <div><span class="rec-info__label" data-i18n="student.recitations.sessionType">Session Type</span><span class="rec-info__value" id="recInfoMode">-</span></div>
                                    </div>
                                    <div class="rec-info__cell">
                                        <span class="rec-info__icon"><svg viewBox="0 0 24 24" fill="none"><path d="M5 4a2 2 0 0 1 2-2h11v18H7a2 2 0 0 0-2 2V4Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/></svg></span>
                                        <div><span class="rec-info__label" data-i18n="student.recitations.surahPortion">Surah / Portion</span><span class="rec-info__value" id="recInfoPortion">-</span></div>
                                    </div>
                                </div>
                            </div>

                            <!-- Submit your recitation -->
                            <div class="rec-panel rec-panel--actions" id="recStep2" hidden>
                                <div class="rec-panel__head">
                                    <span class="rec-step-dot">2</span>
                                    <div>
                                        <h2 class="rec-panel__title" data-i18n="student.recitations.submitRecitation">Submit your recitation</h2>
                                        <p class="rec-panel__sub" data-i18n="student.recitations.submitRecitationSub">Record live or upload an audio file.</p>
                                    </div>
                                </div>

                                <div class="rec-actions">
                                    <article class="rec-action rec-action--record">
                                        <span class="rec-action__art" aria-hidden="true">
                                            <span class="rec-action__art-ring"></span>
                                            <svg class="rec-action__art-wave" viewBox="0 0 96 96" fill="none">
                                                <g stroke="currentColor" stroke-width="3.4" stroke-linecap="round" opacity=".55">
                                                    <path d="M10 48v0"/><path d="M22 40v16"/><path d="M74 40v16"/><path d="M86 48v0"/>
                                                </g>
                                            </svg>
                                            <span class="rec-action__art-icon">
                                                <svg viewBox="0 0 24 24" fill="none"><rect x="9" y="3" width="6" height="11" rx="3" fill="currentColor"/><path d="M5 11a7 7 0 0 0 14 0M12 18v3M9 21h6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                            </span>
                                        </span>
                                        <div class="rec-action__body">
                                            <h3 class="rec-action__title" data-i18n="student.recitations.recordLive">Record Live Recitation</h3>
                                            <p class="rec-action__desc" data-i18n="student.recitations.recordLiveDesc">Record directly from your microphone with live waveform.</p>
                                            <button type="button" class="rec-btn rec-btn--primary rec-action__btn" data-rec-start>
                                                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="12" cy="12" r="6" fill="currentColor"/></svg>
                                                <span data-i18n="student.recitations.startRecording">Start Recording</span>
                                            </button>
                                        </div>
                                    </article>

                                    <article class="rec-action rec-action--upload">
                                        <span class="rec-action__art rec-action__art--alt" aria-hidden="true">
                                            <span class="rec-action__art-ring"></span>
                                            <span class="rec-action__art-icon">
                                                <svg viewBox="0 0 24 24" fill="none"><path d="M7 18a4 4 0 0 1-.5-7.97A6 6 0 0 1 18 9.5a3.5 3.5 0 0 1 .5 6.97" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/><path d="M12 21V11m0 0-2.5 2.5M12 11l2.5 2.5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                            </span>
                                        </span>
                                        <div class="rec-action__body">
                                            <h3 class="rec-action__title" data-i18n="student.recitations.uploadExisting">Upload Existing Recitation</h3>
                                            <p class="rec-action__desc" data-i18n="student.recitations.uploadExistingDesc">Upload MP3, WAV, M4A or supported formats.</p>
                                            <button type="button" class="rec-btn rec-btn--ghost rec-action__btn" data-rec-upload>
                                                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 16V4m0 0 4 4m-4-4-4 4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/><path d="M5 16v2a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-2" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
                                                <span data-i18n="student.recitations.chooseFile">Choose File</span>
                                            </button>
                                        </div>
                                    </article>
                                </div>

                                <p class="rec-note">
                                    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.6"/><path d="M12 11v5M12 8h.01" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
                                    <span data-i18n="student.recitations.formatNote">Supported formats: MP3, WAV, M4A, WebM · Up to 200MB · Record in a quiet space for the clearest audio.</span>
                                </p>
                            </div>
                        </section>

                        <!-- ====== Statistics ====== -->
                        <section class="rec-stats" data-i18n="student.recitations.statsAria" data-i18n-attr="aria-label" aria-label="Submission statistics">
                            <div class="rec-stat rec-stat--total">
                                <span class="rec-stat__icon rec-stat__icon--total"><svg viewBox="0 0 24 24" fill="none"><path d="M4 14v5M9 9v10M14 5v14M19 11v8" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg></span>
                                <div class="rec-stat__body">
                                    <span class="rec-stat__label" data-i18n="student.recitations.totalSubmissions">Total Submissions</span>
                                    <span class="rec-stat__value"><%= esc(totalStr) %></span>
                                </div>
                            </div>
                            <div class="rec-stat rec-stat--reviewed">
                                <span class="rec-stat__icon rec-stat__icon--reviewed"><svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8"/><path d="M8.5 12.5l2.5 2.5 4.5-5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg></span>
                                <div class="rec-stat__body">
                                    <span class="rec-stat__label" data-i18n="student.recitations.reviewed">Reviewed</span>
                                    <span class="rec-stat__value"><%= esc(reviewedStr) %></span>
                                </div>
                            </div>
                            <div class="rec-stat rec-stat--pending">
                                <span class="rec-stat__icon rec-stat__icon--pending"><svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8"/><path d="M12 7v5l3 2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg></span>
                                <div class="rec-stat__body">
                                    <span class="rec-stat__label" data-i18n="student.recitations.pendingReview">Pending Review</span>
                                    <span class="rec-stat__value"><%= esc(pendingStr) %></span>
                                </div>
                            </div>
                        </section>
                        <% } %>

                        <!-- ====== Recent submissions ====== -->
                        <section class="rec-recent" aria-label="Recent submissions">
                            <div class="rec-recent__head">
                                <span class="rec-step-dot rec-step-dot--plain">3</span>
                                <div>
                                    <h2 class="rec-recent__title" data-i18n="student.recitations.recentTitle">Recent submissions</h2>
                                    <p class="rec-recent__sub" data-i18n="student.recitations.recentSub">Track the status of your recitations and view instructor feedback.</p>
                                </div>
                            </div>

                            <% if (historyItems == null || historyItems.isEmpty()) { %>
                            <div class="rec-empty">
                                <span class="rec-empty__icon" aria-hidden="true">
                                    <svg viewBox="0 0 24 24" fill="none"><path d="M9 18V5l12-2v13" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/><circle cx="6" cy="18" r="3" stroke="currentColor" stroke-width="1.6"/><circle cx="18" cy="16" r="3" stroke="currentColor" stroke-width="1.6"/></svg>
                                </span>
                                <p class="rec-empty__title" data-i18n="student.recitations.noSubmissions">No submissions yet</p>
                                <p class="rec-empty__sub" data-i18n="student.recitations.noSubmissionsSub">Once you submit a recitation it will appear here with its status and feedback.</p>
                            </div>
                            <% } else { %>
                            <div class="rec-rows">
                                <% int rowIdx = -1;
                                   for (Map<String, Object> item : historyItems) {
                                       rowIdx++;
                                       boolean rowHidden = rowIdx >= 5;
                                       boolean evaluated = Boolean.TRUE.equals(item.get("evaluated"));
                                       String sessionTitle = str(item, "sessionTitle");
                                       String instructor = str(item, "instructor");
                                       String date = str(item, "date");
                                       String schedule = str(item, "schedule");
                                       String portion = str(item, "portion");
                                       String url = str(item, "audioUrl");
                                       String feedback = str(item, "feedback");
                                       String statusKind = str(item, "statusKind");
                                       String statusLabel = str(item, "statusLabel");
                                       Object score = item.get("score");
                                       String scoreStr = score == null ? "" : String.valueOf(score);
                                       String rowTitle = (portion != null && !portion.isEmpty()) ? portion : sessionTitle;
                                %>
                                <article class="rec-row<%= rowHidden ? " rec-row--more" : "" %>"<%= rowHidden ? " hidden" : "" %> data-rec-feedback
                                         data-title="<%= esc(sessionTitle) %>"
                                         data-instructor="<%= esc(instructor) %>"
                                         data-date="<%= esc(date) %>"
                                         data-schedule="<%= esc(schedule) %>"
                                         data-portion="<%= esc(portion) %>"
                                         data-mode="<%= esc(str(item, "mode")) %>"
                                         data-evaluated="<%= evaluated %>"
                                         data-score="<%= esc(scoreStr) %>"
                                         data-feedback="<%= esc(feedback) %>"
                                         data-status-kind="<%= esc(statusKind) %>"
                                         data-status-label="<%= esc(statusLabel) %>"
                                         data-audio="<%= esc(url) %>"
                                         tabindex="0" role="button">
                                    <div class="rec-row__left">
                                        <span class="rec-row__icon" aria-hidden="true">
                                            <svg viewBox="0 0 24 24" fill="none"><path d="M12 3v10.55A4 4 0 1 0 14 17V7h4V3h-6Z" fill="currentColor"/></svg>
                                        </span>
                                        <div class="rec-row__lines">
                                            <h3 class="rec-row__title"><%= esc(rowTitle) %></h3>
                                            <p class="rec-row__date"><% if (!date.isEmpty()) { %><span data-i18n="student.recitations.submittedOn">Submitted on</span> <%= esc(date) %><% } else { %><span data-i18n="student.recitations.submitted">Submitted</span><% } %></p>
                                        </div>
                                    </div>
                                    <div class="rec-row__center">
                                        <span class="rec-row__meta-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="8" r="3.2" stroke="currentColor" stroke-width="1.7"/><path d="M5 20a7 7 0 0 1 14 0" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg></span>
                                        <div class="rec-row__lines">
                                            <span class="rec-row__meta-label" data-i18n="common.instructor">Instructor</span>
                                            <span class="rec-row__meta-value"><%= esc(instructor) %></span>
                                        </div>
                                    </div>
                                    <div class="rec-row__right">
                                        <% if (evaluated) { %>
                                        <span class="rec-badge rec-badge--reviewed"><span data-i18n="student.recitations.reviewed">Reviewed</span>
                                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 12l4 4 10-10" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                        </span>
                                        <span class="rec-row__btn"><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7Z" stroke="currentColor" stroke-width="1.7"/><circle cx="12" cy="12" r="3" stroke="currentColor" stroke-width="1.7"/></svg><span data-i18n="student.recitations.viewFeedback">View Feedback</span></span>
                                        <% } else { %>
                                        <span class="rec-badge rec-badge--pending"><span data-i18n="student.recitations.pendingReview">Pending Review</span>
                                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2"/><path d="M12 7v5l3 2" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                        </span>
                                        <span class="rec-row__btn"><svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M14 3v4a1 1 0 0 0 1 1h4" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/><path d="M5 4a2 2 0 0 1 2-2h7l5 5v13a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V4Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/></svg><span data-i18n="student.recitations.viewDetails">View Details</span></span>
                                        <% } %>
                                        <span class="rec-row__chev" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none"><path d="M9 6l6 6-6 6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg></span>
                                    </div>
                                </article>
                                <% } %>
                            </div>
                            <% if (historyItems.size() > 5) { %>
                            <div class="rec-recent__foot">
                                <button type="button" class="rec-viewall" data-rec-viewall><span data-i18n="student.recitations.viewAllSubmissions">View all submissions</span>
                                    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 12h14m-6-6 6 6-6 6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                </button>
                            </div>
                            <% } %>
                            <% } %>
                        </section>

                    </div>
                    <%@ include file="/jsp/common/app_footer.jspf" %>
                </main>
            </div>
        </div>
    </div>
</div>

<!-- ====== Upload modal ====== -->
<div class="rec-modal" id="recUploadModal" hidden>
    <div class="rec-modal__scrim" data-rec-modal-dismiss></div>
    <div class="rec-modal__panel" role="dialog" aria-modal="true" aria-labelledby="recUploadTitle">
        <header class="rec-modal__head">
            <h2 class="rec-modal__title" id="recUploadTitle" data-i18n="student.recitations.uploadModalTitle">Upload a recitation</h2>
            <button type="button" class="rec-modal__close" data-rec-modal-dismiss data-i18n="common.close" data-i18n-attr="aria-label" aria-label="Close">
                <svg viewBox="0 0 24 24" fill="none"><path d="m6 6 12 12M18 6 6 18" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
            </button>
        </header>
        <p class="rec-modal__for" id="recUploadFor"></p>
        <label class="rec-dropzone" for="recFile" id="recDropzone">
            <input id="recFile" type="file" class="rec-dropzone__input"
                   accept=".webm,.mp3,.wav,.m4a,.ogg,.oga,.aac,.mp4,.mov,audio/*,video/*">
            <span class="rec-dropzone__icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none"><path d="M12 16V4m0 0 4 4m-4-4-4 4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/><path d="M5 16v2a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-2" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
            </span>
            <span class="rec-dropzone__title" data-i18n="student.recitations.dropzoneTitle">Click to upload or drag &amp; drop</span>
            <span class="rec-dropzone__hint" id="recFileHint">webm, mp3, wav, m4a, ogg, aac (max 200MB)</span>
        </label>
        <button type="button" class="rec-btn rec-btn--primary rec-btn--block" id="recUploadSubmitBtn" data-rec-upload-submit disabled data-i18n="student.recitations.submitRecitationBtn">Submit recitation</button>
        <p class="rec-modal__status" id="recUploadStatus" aria-live="polite"></p>
    </div>
</div>

<!-- ====== Active recitation studio ====== -->
<div class="rec-studio-screen" id="recStudio" hidden>
    <div class="rec-studio-screen__inner">
        <button type="button" class="rec-studio__back" data-rec-cancel data-i18n="student.recitations.back" data-i18n-attr="aria-label" aria-label="Back">
            <svg viewBox="0 0 24 24" fill="none"><path d="M15 18 9 12l6-6" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </button>
        <p class="rec-studio__for" id="recStudioFor"></p>

        <div class="rec-orb" id="recOrb" aria-hidden="true">
            <span class="rec-orb__blob"></span>
            <span class="rec-orb__blob rec-orb__blob--2"></span>
            <span class="rec-orb__ring"></span>
        </div>

        <div class="rec-transcript" id="recTranscript">
            <span class="rec-transcript__badge" data-i18n="student.recitations.transcriptBadge">Transcript &middot; coming soon</span>
            <p class="rec-transcript__text" data-i18n="student.recitations.transcriptText">Live transcription will appear here in a future phase. For now, focus on a clear, calm recitation.</p>
        </div>

        <canvas class="rec-wave" id="recWave" width="640" height="90" aria-hidden="true"></canvas>
        <div class="rec-timer" id="recTimer">0:00</div>

        <div class="rec-controls">
            <button type="button" class="rec-ctrl" data-rec-pause id="recPauseBtn">
                <span class="rec-ctrl__glyph" id="recPauseGlyph" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none"><rect x="7" y="5" width="3.5" height="14" rx="1" fill="currentColor"/><rect x="13.5" y="5" width="3.5" height="14" rx="1" fill="currentColor"/></svg>
                </span>
                <span class="rec-ctrl__label" id="recPauseLabel" data-i18n="student.recitations.pause">Pause</span>
            </button>

            <button type="button" class="rec-ctrl rec-ctrl--record" data-rec-indicator>
                <span class="rec-ctrl__dot" aria-hidden="true"></span>
                <span class="rec-ctrl__label" id="recElapsed">0:00</span>
            </button>

            <button type="button" class="rec-ctrl rec-ctrl--stop" data-rec-stop>
                <span class="rec-ctrl__glyph" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none"><rect x="6" y="6" width="12" height="12" rx="2.5" fill="currentColor"/></svg>
                </span>
                <span class="rec-ctrl__label" data-i18n="student.recitations.stop">Stop</span>
            </button>
        </div>
    </div>

    <div class="rec-review" id="recReview" hidden>
        <div class="rec-review__card">
            <h3 class="rec-review__title" data-i18n="student.recitations.reviewTitle">Review your recitation</h3>
            <p class="rec-review__sub" id="recReviewMeta"></p>
            <audio class="rec-review__audio" id="recPlayback" controls></audio>
            <div class="rec-review__actions">
                <button type="button" class="rec-btn rec-btn--ghost" data-rec-discard data-i18n="student.recitations.deleteRerecord">Delete &amp; re-record</button>
                <button type="button" class="rec-btn rec-btn--primary" data-rec-submit id="recSubmitBtn" data-i18n="student.recitations.submitRecitationBtn">Submit recitation</button>
            </div>
            <p class="rec-review__status" id="recSubmitStatus" aria-live="polite"></p>
        </div>
    </div>
</div>

<!-- ====== Evaluation feedback modal ====== -->
<div class="rec-modal rec-fb" id="recFeedbackModal" hidden>
    <div class="rec-modal__scrim" data-rec-fb-dismiss></div>
    <div class="rec-fb__panel" role="dialog" aria-modal="true" aria-labelledby="recFbTitle">
        <header class="rec-fb__head">
            <div>
                <span class="rec-fb__eyebrow" data-i18n="student.recitations.evaluationReport">Evaluation report</span>
                <h2 class="rec-fb__title" id="recFbTitle"></h2>
            </div>
            <button type="button" class="rec-modal__close" data-rec-fb-dismiss data-i18n="common.close" data-i18n-attr="aria-label" aria-label="Close">
                <svg viewBox="0 0 24 24" fill="none"><path d="m6 6 12 12M18 6 6 18" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
            </button>
        </header>

        <div class="rec-fb__info" id="recFbInfo"></div>

        <div class="rec-fb__audio">
            <span class="rec-fb__label" data-i18n="student.recitations.yourSubmission">Your submission</span>
            <audio id="recFbAudio" controls preload="none"></audio>
        </div>

        <div class="rec-fb__result" id="recFbResult" hidden>
            <div class="rec-fb__score">
                <div class="rec-fb__ring" id="recFbRing">
                    <span class="rec-fb__ring-num" id="recFbScore">0</span>
                    <span class="rec-fb__ring-of">/100</span>
                </div>
                <div class="rec-fb__score-text">
                    <span class="rec-badge" id="recFbBadge"></span>
                    <p class="rec-fb__score-desc" id="recFbScoreDesc"></p>
                </div>
            </div>

            <div class="rec-fb__section">
                <span class="rec-fb__label" data-i18n="student.recitations.instructorFeedback">Instructor feedback</span>
                <p class="rec-fb__feedback" id="recFbFeedback"></p>
            </div>

            <div class="rec-fb__section rec-fb__ai" id="recFbAi" hidden>
                <span class="rec-fb__label rec-fb__label--ai" data-i18n="student.recitations.aiInsights">AI insights</span>
                <div id="recFbAiBody"></div>
            </div>
        </div>

        <div class="rec-fb__pending" id="recFbPending" hidden>
            <span class="rec-fb__pending-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.6"/><path d="M12 7v5l3 2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
            </span>
            <h3 class="rec-fb__pending-title" data-i18n="student.recitations.awaitingReview">Awaiting instructor review</h3>
            <p class="rec-fb__pending-sub" data-i18n="student.recitations.awaitingReviewSub">Your recitation has been submitted. You'll see the score and feedback here once your instructor completes the evaluation.</p>
        </div>
    </div>
</div>
</body>
</html>
