<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="model.service.VerifiedFocusItem" %>
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
    private static String typeLabel(String key) {
        if ("missingWord".equals(key)) return "Missing word";
        if ("differentWord".equals(key)) return "Different word";
        if ("extraWord".equals(key)) return "Extra word";
        if ("assignedPassage".equals(key)) return "Assigned passage";
        if ("listenAgain".equals(key)) return "Listen again";
        return "Note from your instructor";
    }
    private static String practiceLabel(String key) {
        if ("missingWord".equals(key)) return "Practise this word until it comes easily.";
        if ("differentWord".equals(key)) return "Repeat this word the way your instructor expects.";
        if ("extraWord".equals(key)) return "Leave this extra word out next time.";
        if ("assignedPassage".equals(key)) return "Practise the assigned passage for this session.";
        if ("listenAgain".equals(key)) return "Listen to this spot again and repeat it slowly.";
        return "Review this point before your next attempt.";
    }
    private static String focusIcon(String key) {
        String p;
        if ("missingWord".equals(key)) p = "<circle cx=\"12\" cy=\"12\" r=\"8.5\"/><path d=\"M12 8.5v7M8.5 12h7\" stroke-linecap=\"round\"/>";
        else if ("differentWord".equals(key)) p = "<path d=\"M7 7.5h11l-3-3M17 16.5H6l3 3\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>";
        else if ("extraWord".equals(key)) p = "<circle cx=\"12\" cy=\"12\" r=\"8.5\"/><path d=\"M8.5 12h7\" stroke-linecap=\"round\"/>";
        else if ("assignedPassage".equals(key)) p = "<path d=\"M12 6.5C10 5 7 4.5 4 5v13c3-.5 6 0 8 1.5 2-1.5 5-2 8-1.5V5c-3-.5-6 0-8 1.5zM12 6.5v13\" stroke-linejoin=\"round\"/>";
        else if ("listenAgain".equals(key)) p = "<path d=\"M4 15v-3a8 8 0 0116 0v3\" stroke-linecap=\"round\"/><rect x=\"3.5\" y=\"14\" width=\"4\" height=\"6\" rx=\"1.5\"/><rect x=\"16.5\" y=\"14\" width=\"4\" height=\"6\" rx=\"1.5\"/>";
        else p = "<path d=\"M5 5h14v10H9l-4 4z\" stroke-linejoin=\"round\"/>";
        return "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" aria-hidden=\"true\">" + p + "</svg>";
    }
    private static String phaseTone(String phase) {
        if ("ANALYSIS_IN_PROGRESS".equals(phase) || "SUBMITTED".equals(phase)) return "ai";
        if ("ANALYSIS_FAILED".equals(phase) || "CANNOT_EVALUATE".equals(phase)) return "bad";
        return "wait";
    }
    private static String phaseIcon(String tone) {
        if ("ai".equals(tone)) return "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" aria-hidden=\"true\"><path d=\"M12 3.5l1.9 4.6 4.6 1.9-4.6 1.9L12 16.5l-1.9-4.6L5.5 10l4.6-1.9z\" stroke-linejoin=\"round\"/><path d=\"M18.5 16v4M16.5 18h4\" stroke-linecap=\"round\"/></svg>";
        if ("bad".equals(tone)) return "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" aria-hidden=\"true\"><circle cx=\"12\" cy=\"12\" r=\"8.5\"/><path d=\"M12 8v4.5M12 15.8h.01\" stroke-linecap=\"round\"/></svg>";
        return "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" aria-hidden=\"true\"><circle cx=\"12\" cy=\"12\" r=\"8.5\"/><path d=\"M12 7.5V12l3 2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg>";
    }
%>
<%
    request.setAttribute("activeMenu", "recitations");

    String ctx = request.getContextPath();

    List<Map<String, Object>> eligibleSessions = (List<Map<String, Object>>) request.getAttribute("eligibleSessions");
    List<Map<String, Object>> historyItems = (List<Map<String, Object>>) request.getAttribute("historyItems");

    String error = (String) request.getAttribute("error");
    String success = (String) request.getAttribute("success");
    String successKey = (String) request.getAttribute("successKey");
    boolean hasSessions = eligibleSessions != null && !eligibleSessions.isEmpty();

    Object practiceParent = request.getAttribute("practiceParentId");
    Object practiceEnrollment = request.getAttribute("practiceEnrollmentId");
    List<VerifiedFocusItem> practiceFocus = (List<VerifiedFocusItem>) request.getAttribute("practiceFocus");
    boolean practiceMode = practiceParent != null;
    int historyVisible = 5;
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.studentRecitationsTitle">Upload Recitation - e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <%@ include file="/jsp/common/student_ui_head.jspf" %>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Noto+Naskh+Arabic:wght@500;600&display=swap">
    <link rel="stylesheet" href="<%= ctx %>/css/learning-loop-experience.css?v=20261003-stable">
    <script defer src="<%= ctx %>/assets/js/ll-player.js?v=20261003-ux2"></script>
    <script defer src="<%= ctx %>/assets/js/recitation-studio.js?v=20261003-ux2"></script>
</head>
<body class="student-premium-page student-package-page student-module-page student-recitations-page lx-shell">
<div class="app-shell">
    <%@ include file="/jsp/common/student_header.jspf" %>
    <div class="app-main">
        <div class="container sd-container student-workspace-shell">
            <div class="student-shell-layout">
                <%@ include file="/jsp/student/student_sidebar.jspf" %>

                <main class="student-shell-content" role="main"
                      data-rec-root
                      data-rec-endpoint="<%= ctx %>/student/recitations"
                      <%= practiceParent == null ? "" : "data-practice-parent=\"" + esc(practiceParent) + "\"" %>
                      <%= practiceEnrollment == null ? "" : "data-practice-enrollment=\"" + esc(practiceEnrollment) + "\"" %>>
                    <div class="student-workspace-view lx lx-page">
                        <%@ include file="/jsp/common/student_breadcrumb.jspf" %>

                        <header class="lx-pagehead">
                            <p class="lx-eyebrow" data-i18n="student.recitations.kicker">Recitations</p>
                            <% if (practiceMode) { %>
                            <h1 class="lx-title" data-i18n="student.recitations.practiceAgain">Practice Again</h1>
                            <p class="lx-pagehead__sub" data-i18n="student.recitations.practiceBannerTitle">Practice the same passage using your verified learning focus.</p>
                            <% } else { %>
                            <h1 class="lx-title" data-i18n="student.recitations.title">Recitations</h1>
                            <p class="lx-pagehead__sub" data-i18n="student.recitations.subtitle">Submit tilawah recordings for instructor review.</p>
                            <% } %>
                        </header>

                        <% if (success != null) { %>
                        <div class="lx-alert lx-alert--ok" role="status">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M8.5 12.5l2.5 2.5 4.5-5" stroke-linecap="round" stroke-linejoin="round"/></svg>
                            <span<% if (successKey != null && !successKey.isEmpty()) { %> data-i18n="<%= esc(successKey) %>"<% } %>><%= esc(success) %></span>
                        </div>
                        <% } %>
                        <% if (error != null) { %>
                        <div class="lx-alert lx-alert--bad" role="alert">
                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7.5v5.5M12 16.5h.01" stroke-linecap="round"/></svg>
                            <span><%= esc(error) %></span>
                        </div>
                        <% } %>

                        <% if (practiceFocus != null) { %>
                        <section class="lx-card lx-practice" aria-labelledby="lx-practice-title">
                            <div class="lx-card__head">
                                <span class="lx-card__icon lx-card__icon--gold" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="4.5"/><circle cx="12" cy="12" r="1" fill="currentColor"/></svg></span>
                                <div>
                                    <h2 class="lx-card__title" id="lx-practice-title" data-i18n="student.recitations.focusTitle">Your verified learning focus</h2>
                                    <p class="lx-card__sub" data-i18n="student.recitations.focusSub">Points your instructor confirmed for your next attempt.</p>
                                </div>
                            </div>
                            <% if (practiceFocus.isEmpty()) { %>
                            <p class="lx-empty" data-i18n="student.recitations.focusEmpty">This recitation is complete. There is nothing extra to practise from this review.</p>
                            <% } else { %>
                            <ol class="lx-focus lx-focus--compact">
                                <% for (VerifiedFocusItem item : practiceFocus) {
                                       String typeKey = item.getTypeKey() == null ? "instructorNote" : item.getTypeKey();
                                       String key = item.getPracticeKey() == null ? "instructorNote" : item.getPracticeKey();
                                %>
                                <li class="lx-focus__item lx-focus__item--<%= esc(typeKey) %>">
                                    <span class="lx-focus__icon"><%= focusIcon(typeKey) %></span>
                                    <div class="lx-focus__body">
                                        <p class="lx-focus__title"><span data-i18n="student.recitations.focus.<%= esc(typeKey) %>"><%= esc(typeLabel(typeKey)) %></span></p>
                                        <p class="lx-focus__action" data-i18n="student.recitations.practice.<%= esc(key) %>"><%= esc(practiceLabel(key)) %></p>
                                        <% if (item.getExpectedPhrase() != null && !item.getExpectedPhrase().isBlank()) { %>
                                        <p class="lx-arabic lx-focus__phrase" lang="ar"><%= esc(item.getExpectedPhrase()) %></p>
                                        <% } %>
                                    </div>
                                </li>
                                <% } %>
                            </ol>
                            <% } %>
                        </section>
                        <% } %>

                        <% if (!hasSessions) { %>
                        <section class="lx-card lx-blank">
                            <span class="lx-blank__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4" stroke-linecap="round"/></svg></span>
                            <h2 class="lx-card__title" data-i18n="student.recitations.emptyTitle">No session is ready for submission</h2>
                            <p class="lx-card__sub" data-i18n="student.recitations.emptySub">You need a confirmed enrollment (and a successful payment for paid sessions) before you can submit a recitation.</p>
                            <div class="lx-actions lx-actions--center">
                                <a class="lx-btn lx-btn--primary" href="<%= LocaleSupport.localizedUrl(request, "/student/available-sessions") %>" data-i18n="student.recitations.browseSessions">Browse sessions</a>
                                <a class="lx-btn lx-btn--ghost" href="<%= LocaleSupport.localizedUrl(request, "/student/enrollments") %>" data-i18n="student.recitations.mySessions">My sessions</a>
                            </div>
                        </section>
                        <% } else { %>

                        <section class="lx-card lx-studio" data-rec-studio aria-labelledby="recStudioTitle">
                            <div class="lx-studio__inner">
                            <div class="lx-studio__atmosphere">
                                <div class="lx-studio__hero">
                                    <div class="lx-card__head lx-studio__brand">
                                        <div>
                                            <p class="lx-studio__eyebrow" data-i18n="student.recitations.studioEyebrow">Recitation studio</p>
                                            <h2 class="lx-card__title" id="recStudioTitle" data-i18n="student.recitations.workspaceAria">Submit a recitation</h2>
                                            <p class="lx-card__sub" data-i18n="student.recitations.studioSub">Choose a session, record or upload, then review before you submit.</p>
                                        </div>
                                    </div>
                                    <div class="lx-studio__wavepanel" aria-hidden="true">
                                        <div class="lx-studio__wavepanel-core">
                                            <svg class="lx-studio__wavepanel-mic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="9" y="3" width="6" height="11" rx="3"/><path d="M6 11a6 6 0 0012 0M12 17v3" stroke-linecap="round"/></svg>
                                            <span class="lx-studio__spectrum"><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i></span>
                                        </div>
                                        <svg class="lx-studio__arc" viewBox="0 0 140 48" fill="none" stroke="currentColor" stroke-width="1.2" aria-hidden="true"><path d="M8 36 C35 8, 105 8, 132 36" stroke-linecap="round" opacity="0.35"/><path d="M20 40 C42 18, 98 18, 120 40" stroke-linecap="round" opacity="0.22"/></svg>
                                    </div>
                                </div>
                                <ol class="lx-steps lx-studio__steps" data-rec-steps aria-label="Submission steps">
                                    <li class="is-current" data-step="1" aria-current="step">
                                        <span class="lx-steps__n" aria-hidden="true"><span class="lx-steps__num">1</span><svg class="lx-steps__check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round"/></svg></span>
                                        <span class="lx-steps__label" data-i18n="student.recitations.stepSession">Choose session</span>
                                    </li>
                                    <li data-step="2">
                                        <span class="lx-steps__n" aria-hidden="true"><span class="lx-steps__num">2</span><svg class="lx-steps__check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round"/></svg></span>
                                        <span class="lx-steps__label" data-i18n="student.recitations.stepRecord">Record</span>
                                    </li>
                                    <li data-step="3">
                                        <span class="lx-steps__n" aria-hidden="true"><span class="lx-steps__num">3</span><svg class="lx-steps__check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round"/></svg></span>
                                        <span class="lx-steps__label" data-i18n="student.recitations.stepSubmit">Review &amp; submit</span>
                                    </li>
                                </ol>
                            </div>

                            <div class="lx-field lx-studio__session" id="recStep1">
                                <span class="lx-label" id="recSessionLabel" data-i18n="student.recitations.selectSession">Select a session</span>
                                <div class="lx-select" data-rec-select>
                                    <button type="button" class="lx-select__trigger" id="recSessionTrigger"
                                            aria-haspopup="listbox" aria-expanded="false" aria-controls="recSessionList"
                                            aria-labelledby="recSessionLabel recSessionValue">
                                        <span class="lx-select__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 6.5C10 5 7 4.5 4 5v13c3-.5 6 0 8 1.5 2-1.5 5-2 8-1.5V5c-3-.5-6 0-8 1.5zM12 6.5v13" stroke-linejoin="round"/></svg></span>
                                        <span class="lx-select__value" id="recSessionValue">
                                            <span class="lx-select__placeholder" data-i18n="student.recitations.choosePlaceholder">Choose a session…</span>
                                            <span class="lx-select__current" data-rec-current hidden></span>
                                        </span>
                                        <svg class="lx-select__chev" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M6 9l6 6 6-6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                    </button>
                                    <ul class="lx-select__list" id="recSessionList" role="listbox" aria-labelledby="recSessionLabel" tabindex="-1" hidden>
                                        <% int optIndex = 0;
                                           for (Map<String, Object> s : eligibleSessions) {
                                               String title = str(s, "title");
                                               String portion = str(s, "portion");
                                               String instructor = str(s, "instructor");
                                               String schedule = str(s, "schedule");
                                        %>
                                        <li class="lx-select__option" role="option" id="recOpt-<%= optIndex++ %>" aria-selected="false"
                                            data-rec-session
                                            data-enrollment-id="<%= esc(str(s, "enrollmentId")) %>"
                                            data-title="<%= esc(title) %>"
                                            data-portion="<%= esc(portion) %>"
                                            data-instructor="<%= esc(instructor) %>"
                                            data-schedule="<%= esc(schedule) %>">
                                            <span class="lx-select__otext">
                                                <strong><%= esc(title) %></strong>
                                                <% if (!portion.isEmpty()) { %><span class="lx-select__passage"><%= esc(portion) %></span><% } %>
                                                <span class="lx-select__ometa">
                                                    <% if (!schedule.isEmpty()) { %><span><%= esc(schedule) %></span><% } %>
                                                    <% if (!instructor.isEmpty()) { %><span><%= esc(instructor) %></span><% } %>
                                                </span>
                                            </span>
                                            <svg class="lx-select__check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                        </li>
                                        <% } %>
                                    </ul>
                                </div>

                                <div class="lx-chosen" id="recChosen" hidden>
                                    <div class="lx-chosen__top">
                                        <span class="lx-chosen__badge">
                                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                            <span data-i18n="student.recitations.selectedSession">Session selected</span>
                                        </span>
                                        <% if (practiceMode) { %>
                                        <span class="lx-pill lx-pill--info lx-pill--sm" data-i18n="student.recitations.linkedAttempt">Linked attempt</span>
                                        <% } %>
                                    </div>
                                    <dl class="lx-chosen__facts">
                                        <div><dt data-i18n="student.recitations.infoPortion">Surah / portion</dt><dd id="recChosenPortion" class="lx-chosen__passage">—</dd></div>
                                        <div><dt data-i18n="student.recitations.infoInstructor">Instructor</dt><dd id="recChosenInstructor">—</dd></div>
                                        <div><dt data-i18n="student.recitations.dateTime">Date &amp; Time</dt><dd id="recChosenSchedule">—</dd></div>
                                    </dl>
                                </div>
                            </div>

                            <div class="lx-stage" id="recStep2" hidden>
                                <div class="lx-stage__bar">
                                    <h3 class="lx-stage__title" data-i18n="student.recitations.recordTitle">Record your recitation</h3>
                                    <div class="lx-seg" role="tablist" aria-label="Recording method">
                                        <button type="button" class="lx-seg__btn is-active" data-rec-method="record" role="tab" aria-selected="true">
                                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><rect x="9" y="3" width="6" height="11" rx="3"/><path d="M6 11a6 6 0 0012 0M12 17v3" stroke-linecap="round"/></svg>
                                            <span data-i18n="student.recitations.recordTab">Record</span>
                                        </button>
                                        <button type="button" class="lx-seg__btn" data-rec-method="upload" role="tab" aria-selected="false">
                                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M12 16V5m0 0l4 4m-4-4L8 9" stroke-linecap="round" stroke-linejoin="round"/><path d="M5 16v2a2 2 0 002 2h10a2 2 0 002-2v-2" stroke-linecap="round"/></svg>
                                            <span data-i18n="student.recitations.uploadAudio">Upload audio</span>
                                        </button>
                                    </div>
                                </div>

                                <div class="lx-recorder" id="recStudio" data-rec-pane="record" data-state="idle">
                                    <div class="lx-recorder__status" aria-live="polite">
                                        <span class="lx-recorder__dot" aria-hidden="true"></span>
                                        <span id="recStateLabel" data-i18n="student.recitations.readyToRecord">Ready to record</span>
                                    </div>
                                    <div class="lx-recorder__timer" id="recTimer">0:00</div>
                                    <canvas class="lx-recorder__wave" id="recWave" width="640" height="96" aria-hidden="true"></canvas>
                                    <div class="lx-recorder__controls">
                                        <span class="lx-recorder__slot">
                                            <button type="button" class="lx-round" data-rec-pause hidden>
                                                <svg class="lx-i-pause" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M7 5h4v14H7zM13 5h4v14h-4z"/></svg>
                                                <svg class="lx-i-play" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5v14l11-7z"/></svg>
                                                <span class="lx-round__label" id="recPauseLabel" data-i18n="student.recitations.pause">Pause</span>
                                            </button>
                                        </span>
                                        <span class="lx-recorder__main">
                                            <button type="button" class="lx-mic" data-rec-start data-i18n="student.recitations.tapToStart" data-i18n-attr="aria-label" aria-label="Tap to start recording">
                                                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="9" y="3" width="6" height="11" rx="3" fill="currentColor"/><path d="M5 11a7 7 0 0014 0M12 18v3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
                                            </button>
                                            <button type="button" class="lx-mic lx-mic--stop" data-rec-stop hidden data-i18n="student.recitations.stopAndReview" data-i18n-attr="aria-label" aria-label="Stop and review">
                                                <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><rect x="7" y="7" width="10" height="10" rx="2"/></svg>
                                            </button>
                                        </span>
                                        <span class="lx-recorder__slot" aria-hidden="true"></span>
                                    </div>
                                    <p class="lx-recorder__title" id="recCaptureTitle" data-i18n="student.recitations.tapToStart">Tap to start recording</p>
                                    <p class="lx-recorder__hint" id="recCaptureHint" data-i18n="student.recitations.tapToStartHint">Find a quiet place and recite clearly.</p>
                                </div>

                                <div class="lx-upload" data-rec-pane="upload" hidden>
                                    <label class="lx-drop" for="recFile" id="recDropzone">
                                        <input id="recFile" type="file" class="lx-drop__input"
                                               accept=".webm,.mp3,.wav,.m4a,.ogg,.oga,.aac,.mp4,.mov,audio/*,video/*">
                                        <span class="lx-drop__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 16V5m0 0l4 4m-4-4L8 9" stroke-linecap="round" stroke-linejoin="round"/><path d="M5 16v2a2 2 0 002 2h10a2 2 0 002-2v-2" stroke-linecap="round"/></svg></span>
                                        <span class="lx-drop__title" data-i18n="student.recitations.dropzoneTitle">Click to upload or drag &amp; drop</span>
                                        <span class="lx-drop__hint" id="recFileHint" data-i18n="student.recitations.fileFormatsHint">webm, mp3, wav, m4a, ogg, aac (max 200MB)</span>
                                    </label>
                                    <div class="lx-actions">
                                        <button type="button" class="lx-btn lx-btn--primary" data-rec-review-file disabled>
                                            <span data-i18n="student.recitations.reviewAudio">Review audio</span>
                                            <svg class="lx-i-dir" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                        </button>
                                    </div>
                                </div>
                            </div>

                            <div class="lx-stage" id="recReview" hidden>
                                <div class="lx-stage__bar">
                                    <div>
                                        <h3 class="lx-stage__title" data-i18n="student.recitations.reviewTitle">Review your recitation</h3>
                                        <p class="lx-card__sub" data-i18n="student.recitations.listenBeforeSubmit">Listen before submitting. You can go back to replace this audio.</p>
                                    </div>
                                </div>
                                <dl class="lx-review-facts">
                                    <div>
                                        <dt data-i18n="student.recitations.durationLabel">Duration</dt>
                                        <dd id="recReviewDuration">—</dd>
                                    </div>
                                    <div>
                                        <dt data-i18n="student.recitations.sessionLabel">Session</dt>
                                        <dd id="recReviewSession">—</dd>
                                    </div>
                                    <div>
                                        <dt data-i18n="student.recitations.infoPortion">Surah / portion</dt>
                                        <dd id="recReviewPassage">—</dd>
                                    </div>
                                </dl>
                                <div class="lx-player lx-player--wave" data-rr-player dir="ltr">
                                    <audio data-rr-audio id="recPlayback" preload="metadata"></audio>
                                    <button type="button" class="lx-player__play" data-rr-play aria-label="Play" data-label-play="Play" data-label-pause="Pause">
                                        <svg class="lx-i-play" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5v14l11-7z"/></svg>
                                        <svg class="lx-i-pause" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M7 5h4v14H7zM13 5h4v14h-4z"/></svg>
                                    </button>
                                    <div class="lx-player__track">
                                        <canvas class="lx-player__wave" data-rr-wave aria-hidden="true"></canvas>
                                        <input class="lx-player__seek" data-rr-seek type="range" min="0" max="1000" value="0" aria-label="Seek">
                                    </div>
                                    <span class="lx-player__time" data-rr-time>0:00</span>
                                    <button type="button" class="lx-player__rate" data-rr-rate aria-label="Playback speed">1x</button>
                                </div>
                                <p class="lx-note" id="recReviewMeta" hidden></p>
                                <div class="lx-callout">
                                    <span class="lx-callout__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01" stroke-linecap="round"/></svg></span>
                                    <div>
                                        <strong data-i18n="student.recitations.whatHappensNext">What happens next?</strong>
                                        <p data-i18n="student.recitations.whatHappensNextBody">After submission, automatic analysis begins and your instructor reviews the result. You will only see a final score and Learning Focus after your instructor publishes the verified result.</p>
                                    </div>
                                </div>
                                <div class="lx-actions lx-actions--split">
                                    <button type="button" class="lx-btn lx-btn--ghost" data-rec-discard>
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M4 12a8 8 0 0113.7-5.6M20 4v5h-5" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                        <span data-i18n="student.recitations.replaceRecording">Replace recording</span>
                                    </button>
                                    <button type="button" class="lx-btn lx-btn--primary lx-btn--lg" data-rec-submit id="recSubmitBtn">
                                        <span class="lx-btn__spinner" aria-hidden="true"></span>
                                        <svg class="lx-btn__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" aria-hidden="true"><path d="M4.5 12L20 4.5 16 20l-4.2-6.3L4.5 12z" stroke-linejoin="round"/><path d="M11.8 13.7L20 4.5" stroke-linecap="round"/></svg>
                                        <span id="recSubmitLabel" data-i18n="student.recitations.submitRecitationBtn">Submit recitation</span>
                                    </button>
                                </div>
                                <p class="lx-form-status" id="recSubmitStatus" aria-live="polite"></p>
                            </div>

                            <div class="lx-stage lx-processing" id="recProcessing" hidden role="status">
                                <div class="lx-processing__orb" aria-hidden="true">
                                    <span></span><span></span>
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 3.5l1.9 4.6 4.6 1.9-4.6 1.9L12 16.5l-1.9-4.6L5.5 10l4.6-1.9z" stroke-linejoin="round"/><path d="M18.5 16v4M16.5 18h4" stroke-linecap="round"/></svg>
                                </div>
                                <h3 class="lx-processing__title" data-i18n="student.recitations.processingTitle">Your recitation is being analysed…</h3>
                                <p class="lx-processing__sub" data-i18n="student.recitations.processingSub">We are preparing it for your instructor. Opening your result page…</p>
                                <ol class="lx-processing__steps">
                                    <li class="is-done"><span class="lx-processing__mark" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.6"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round"/></svg></span><span data-i18n="student.recitations.journeySubmitted">Submitted</span></li>
                                    <li class="is-current"><span class="lx-processing__mark" aria-hidden="true"></span><span data-i18n="student.recitations.journeyAnalysis">AI analysis</span></li>
                                    <li><span class="lx-processing__mark" aria-hidden="true"></span><span data-i18n="student.recitations.journeyInstructor">Instructor review</span></li>
                                </ol>
                            </div>
                            </div>
                        </section>
                        <% } %>

                        <section class="lx-card" aria-labelledby="lx-history-title">
                            <div class="lx-card__head">
                                <span class="lx-card__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M9 18V5l12-2v13" stroke-linecap="round" stroke-linejoin="round"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/></svg></span>
                                <div>
                                    <h2 class="lx-card__title" id="lx-history-title" data-i18n="student.recitations.recentTitle">Recent submissions</h2>
                                    <p class="lx-card__sub" data-i18n="student.recitations.recentSub">Track the status of your recitations and view instructor feedback.</p>
                                </div>
                            </div>

                            <% if (historyItems == null || historyItems.isEmpty()) { %>
                            <p class="lx-empty" data-i18n="student.recitations.noSubmissionsSub">Once you submit a recitation it will appear here with its status and feedback.</p>
                            <% } else { %>
                            <ul class="lx-list" data-rec-history>
                                <% int rowIdx = 0;
                                   for (Map<String, Object> item : historyItems) {
                                       boolean published = Boolean.TRUE.equals(item.get("published"));
                                       String portion = str(item, "portion");
                                       String sessionTitle = str(item, "sessionTitle");
                                       String date = str(item, "date");
                                       String statusLabel = str(item, "statusLabel");
                                       String statusI18nKey = str(item, "statusI18nKey");
                                       String tone = published ? "ok" : phaseTone(str(item, "analysisPhase"));
                                       Object score = item.get("score");
                                       boolean extra = rowIdx >= historyVisible;
                                       rowIdx++;
                                %>
                                <li<%= extra ? " hidden data-rec-more" : "" %>>
                                    <a class="lx-list__row" href="<%= esc(str(item, "resultUrl")) %>">
                                        <span class="lx-list__icon is-<%= tone %>" aria-hidden="true">
                                            <% if (published) { %>
                                            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 3l7 3v5.5c0 4.2-2.9 7.9-7 9.5-4.1-1.6-7-5.3-7-9.5V6z" stroke-linejoin="round"/><path d="M8.8 12.2l2.2 2.2 4.2-4.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                            <% } else { %>
                                            <%= phaseIcon(tone) %>
                                            <% } %>
                                        </span>
                                        <span class="lx-list__body">
                                            <span class="lx-list__title"><%= esc(portion.isEmpty() ? sessionTitle : portion) %></span>
                                            <span class="lx-list__meta">
                                                <% if (!portion.isEmpty()) { %><span><%= esc(sessionTitle) %></span><% } %>
                                                <% if (!date.isEmpty()) { %><span><%= esc(date) %></span><% } %>
                                            </span>
                                        </span>
                                        <span class="lx-list__end">
                                            <% if (published) { %>
                                            <% if (score != null) { %><span class="lx-list__score"><%= esc(score) %><small>/100</small></span><% } %>
                                            <span class="lx-pill lx-pill--ok lx-pill--sm" data-i18n="student.recitations.instructorVerified">Instructor verified</span>
                                            <% } else { %>
                                            <span class="lx-pill lx-pill--<%= tone %> lx-pill--dot lx-pill--sm"<% if (!statusI18nKey.isEmpty()) { %> data-i18n="<%= esc(statusI18nKey) %>"<% } %>><%= esc(statusLabel.isEmpty() ? "In progress" : statusLabel) %></span>
                                            <% } %>
                                            <svg class="lx-list__chev lx-i-dir" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M9 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                        </span>
                                    </a>
                                </li>
                                <% } %>
                            </ul>
                            <% if (historyItems.size() > historyVisible) { %>
                            <button type="button" class="lx-more" data-rec-viewall>
                                <span data-i18n="student.recitations.viewAllSubmissions">View all submissions</span>
                                <span class="lx-pill lx-pill--sm"><%= historyItems.size() %></span>
                            </button>
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
</body>
</html>
