<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="model.entity.Progress" %>
<%@ page import="model.entity.StudentProgressSummary" %>
<%@ page import="model.service.StudentProgressHistory" %>
<%@ page import="model.service.VerifiedFocusItem" %>
<%@ page import="java.util.List" %>
<%!
    private static String esc(Object raw) {
        if (raw == null) return "";
        return String.valueOf(raw)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String typeLabel(String key) {
        if ("missingWord".equals(key)) return "Missing word";
        if ("differentWord".equals(key)) return "Different word";
        if ("extraWord".equals(key)) return "Extra word";
        if ("assignedPassage".equals(key)) return "Assigned passage";
        if ("listenAgain".equals(key)) return "Listen again";
        return "Note from your instructor";
    }

    private static String practiceAction(String key) {
        if ("missingWord".equals(key)) return "Practise this word until it comes easily.";
        if ("differentWord".equals(key)) return "Repeat this word the way your instructor expects.";
        if ("extraWord".equals(key)) return "Leave this extra word out next time.";
        if ("assignedPassage".equals(key)) return "Practise the assigned passage for this session.";
        if ("listenAgain".equals(key)) return "Listen to this spot again and repeat it slowly.";
        return "Review this point before your next attempt.";
    }

    private static String focusCountHtml(int count) {
        if (count <= 0) {
            return "<span data-i18n=\"student.progress.focusCountZero\">No focus items</span>";
        }
        if (count == 1) {
            return "<span data-i18n=\"student.progress.focusCountOne\">1 focus item</span>";
        }
        return "<span data-i18n=\"student.progress.focusCountMany\" data-i18n-count=\"" + count + "\">" + count + " focus items</span>";
    }

    private static String itemsHtml(int count) {
        if (count == 1) {
            return "<span data-i18n=\"student.progress.itemsOne\">1 item</span>";
        }
        return "<span data-i18n=\"student.progress.itemsMany\" data-i18n-count=\"" + count + "\">" + count + " items</span>";
    }

    private static final String CHECK_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" aria-hidden=\"true\"><circle cx=\"12\" cy=\"12\" r=\"9\"/><path class=\"sp-check\" d=\"M8.5 12.3l2.4 2.4 4.6-4.9\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg>";
    private static final String PRACTICE_SVG = "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" aria-hidden=\"true\"><path d=\"M4 12a8 8 0 0113.7-5.6M20 4v5h-5\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg>";
    private static final String CHEVRON_SVG = "<svg class=\"lx-i-dir\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" aria-hidden=\"true\"><path d=\"M9 6l6 6-6 6\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg>";
%>
<%
  request.setAttribute("activeMenu", "progress");
  String ctx = request.getContextPath();

  Progress p = (Progress) request.getAttribute("progress");
  StudentProgressSummary summary = (StudentProgressSummary) request.getAttribute("progressSummary");
  StudentProgressHistory history = (StudentProgressHistory) request.getAttribute("progressHistory");

  double completion = 0.0;
  String lastUpdatedIso = "";
  String lastUpdatedText = "";
  if (p != null) {
      java.math.BigDecimal rate = p.getCompletionRate();
      completion = (rate == null) ? 0.0 : rate.doubleValue();
      if (completion < 0) completion = 0;
      if (completion > 100) completion = 100;
      if (p.getLastUpdated() != null) {
          java.time.ZonedDateTime updated = p.getLastUpdated().atZone(java.time.ZoneId.systemDefault());
          lastUpdatedIso = updated.toLocalDateTime().withNano(0).toString();
          lastUpdatedText = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy").format(updated);
      }
  }

  if (summary == null) {
      summary = new StudentProgressSummary();
  }
  if (history == null) {
      history = StudentProgressHistory.empty();
  }

  int attendanceRate = 0;
  if (summary.getAttendanceMarked() > 0) {
      attendanceRate = (int) Math.round((summary.getAttendancePresent() * 100.0) / summary.getAttendanceMarked());
  }
  int completionRounded = (int) Math.round(completion);

  List<StudentProgressHistory.Entry> entries = history.getEntries();
  StudentProgressHistory.LatestFocus latestFocus = history.getLatestFocus();
  StudentProgressHistory.Comparison comparison = history.getComparison();
  final int initialRows = 8;
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.studentProgressTitle">My Progress - e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <%@ include file="/jsp/common/student_ui_head.jspf" %>
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&display=swap">
    <link rel="stylesheet" href="<%= ctx %>/css/learning-loop-experience.css?v=20261003-ux2">
    <link rel="stylesheet" href="<%= ctx %>/css/student-progress.css?v=20261005-history2">
    <script defer src="<%= ctx %>/assets/js/app.js"></script>
    <script defer src="<%= ctx %>/assets/js/student-progress.js?v=20261005-history1"></script>
</head>
<body class="student-premium-page student-package-page student-module-page student-page--progress lx-shell">
<div class="app-shell">
    <%@ include file="/jsp/common/student_header.jspf" %>
    <div class="app-main">
        <div class="container sd-container student-workspace-shell">
            <div class="student-shell-layout">
                <%@ include file="/jsp/student/student_sidebar.jspf" %>

                <main class="student-shell-content" role="main">
                    <div class="student-workspace-view lx sp" data-sp>
                <%@ include file="/jsp/common/student_breadcrumb.jspf" %>

                <header class="sp-head sp-reveal" style="--i:0">
                    <div class="sp-head__copy">
                        <p class="lx-eyebrow" data-i18n="student.progress.eyebrow">My learning</p>
                        <h1 class="sp-title" data-i18n="student.progress.pageHeading">Student Progress</h1>
                        <p class="sp-sub" data-i18n="student.progress.pageSubtitle">Follow your recitation history and see how you're improving.</p>
                    </div>
                    <% if (!lastUpdatedText.isEmpty()) { %>
                    <div class="sp-chip">
                        <span class="sp-chip__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2" stroke-linecap="round" stroke-linejoin="round"/></svg></span>
                        <span class="sp-chip__text">
                            <span class="sp-chip__label" data-i18n="student.progress.lastUpdatedChip">Last updated</span>
                            <time class="sp-chip__value" datetime="<%= esc(lastUpdatedIso) %>" data-sp-date="full"><%= esc(lastUpdatedText) %></time>
                        </span>
                    </div>
                    <% } %>
                </header>

                <section class="lx-card sp-snapshot sp-reveal" style="--i:1" id="progressOverview" aria-labelledby="sp-snapshot-title">
                    <div class="sp-snapshot__intro">
                        <h2 class="sp-snapshot__title" id="sp-snapshot-title" data-i18n="student.progress.snapshotTitle">Learning Snapshot</h2>
                        <p class="sp-snapshot__sub" data-i18n="student.progress.snapshotSub">Your progress at a glance</p>
                    </div>
                    <dl class="sp-metrics">
                        <div class="sp-metric sp-metric--rate">
                            <dt class="sp-metric__label" data-i18n="student.progress.completionRate">Completion rate</dt>
                            <dd class="sp-metric__value"><bdi><span data-sp-count="<%= completionRounded %>"><%= completionRounded %></span>%</bdi></dd>
                            <dd class="sp-bar" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="<%= completionRounded %>" aria-labelledby="sp-snapshot-title">
                                <span style="--sp-w: <%= completionRounded %>%"></span>
                            </dd>
                        </div>
                        <div class="sp-metric">
                            <dt class="sp-metric__label" data-i18n="student.progress.completedSessions">Completed sessions</dt>
                            <dd class="sp-metric__value"><span data-sp-count="<%= summary.getCompletedSessions() %>"><%= summary.getCompletedSessions() %></span></dd>
                        </div>
                        <div class="sp-metric">
                            <dt class="sp-metric__label" data-i18n="student.progress.recitationsSubmitted">Recitations submitted</dt>
                            <dd class="sp-metric__value"><span data-sp-count="<%= summary.getRecitationsSubmitted() %>"><%= summary.getRecitationsSubmitted() %></span></dd>
                        </div>
                        <div class="sp-metric">
                            <dt class="sp-metric__label" data-i18n="student.progress.reviewedByInstructor">Reviewed by instructor</dt>
                            <dd class="sp-metric__value"><span data-sp-count="<%= summary.getEvaluatedRecitations() %>"><%= summary.getEvaluatedRecitations() %></span></dd>
                        </div>
                        <div class="sp-metric">
                            <dt class="sp-metric__label" data-i18n="student.progress.attendance">Attendance</dt>
                            <dd class="sp-metric__value"><bdi><span data-sp-count="<%= attendanceRate %>"><%= attendanceRate %></span>%</bdi></dd>
                            <dd class="sp-metric__hint" data-i18n="student.progress.marked" data-i18n-present="<%= summary.getAttendancePresent() %>" data-i18n-marked="<%= summary.getAttendanceMarked() %>"><%= summary.getAttendancePresent() %> / <%= summary.getAttendanceMarked() %> marked</dd>
                        </div>
                    </dl>
                </section>

                <div class="sp-pair">
                    <section class="lx-card sp-focus sp-reveal" style="--i:2" aria-labelledby="sp-focus-title">
                        <p class="lx-eyebrow" id="sp-focus-title" data-i18n="student.progress.focusEyebrow">Latest verified focus</p>
                        <% if (latestFocus == null) { %>
                        <div class="sp-focus__row">
                            <span class="sp-focus__badge sp-focus__badge--idle" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="4.5"/><circle cx="12" cy="12" r="1" fill="currentColor"/></svg></span>
                            <div class="sp-focus__body">
                                <p class="sp-focus__title" data-i18n="student.progress.focusEmptyTitle">No verified learning focus yet.</p>
                                <p class="sp-focus__sub" data-i18n="student.progress.focusEmptySub">Your focus will appear after an instructor reviews and publishes a recitation.</p>
                            </div>
                        </div>
                        <% } else {
                               StudentProgressHistory.Entry source = latestFocus.getSource();
                               String practiceHref = ctx + "/student/recitations?practice=" + source.getRecitationId();
                               VerifiedFocusItem primary = latestFocus.getPrimary();
                        %>
                        <div class="sp-focus__row">
                            <span class="sp-focus__badge" aria-hidden="true"><%= CHECK_SVG %></span>
                            <div class="sp-focus__body">
                                <% if (primary == null) { %>
                                <p class="sp-focus__title"><%= esc(latestFocus.getPrimaryLocation()) %></p>
                                <p class="sp-focus__sub" data-i18n="student.progress.focusNoneSub">Nothing extra to practise from your latest published result.</p>
                                <% } else {
                                       String key = primary.getTypeKey() == null ? "instructorNote" : primary.getTypeKey();
                                %>
                                <p class="sp-focus__title"><bdi><%= esc(latestFocus.getPrimaryLocation()) %></bdi><% if (latestFocus.getPrimaryAyah() != null) { %> &middot; <span data-i18n="student.recitations.ayahLabel" data-i18n-n="<%= latestFocus.getPrimaryAyah() %>">Ayah <%= latestFocus.getPrimaryAyah() %></span><% } %></p>
                                <p class="sp-focus__sub">
                                    <span data-i18n="student.recitations.practice.<%= esc(key) %>"><%= esc(practiceAction(key)) %></span>
                                </p>
                                <p class="sp-focus__meta">
                                    <span class="sp-focus__type" data-i18n="student.recitations.focus.<%= esc(key) %>"><%= esc(typeLabel(key)) %></span>
                                    <% if (latestFocus.getItemCount() > 1) { %>
                                    <span class="sp-focus__more" data-i18n="student.progress.focusMore" data-i18n-count="<%= latestFocus.getItemCount() - 1 %>">+<%= latestFocus.getItemCount() - 1 %> more</span>
                                    <% } %>
                                </p>
                                <% } %>
                            </div>
                            <a class="lx-btn lx-btn--primary lx-btn--sm sp-practice" href="<%= esc(practiceHref) %>">
                                <%= PRACTICE_SVG %>
                                <span data-i18n="student.recitations.practiceAgain">Practice Again</span>
                            </a>
                        </div>
                        <% } %>
                    </section>

                    <section class="lx-card sp-compare sp-reveal" style="--i:3" aria-labelledby="sp-compare-title">
                        <div class="sp-compare__intro">
                            <p class="lx-eyebrow" data-i18n="student.progress.compareEyebrow">Learning progress</p>
                            <h2 class="sp-compare__title" id="sp-compare-title" data-i18n="student.progress.compareTitle">Latest practice comparison</h2>
                        </div>
                        <% if (comparison == null) { %>
                        <p class="sp-compare__empty" data-i18n="student.progress.compareEmpty">A comparison appears once you have two published results for the same session.</p>
                        <% } else if (!comparison.hasPrevious()) { %>
                        <div class="sp-compare__first">
                            <div class="sp-stat">
                                <span class="sp-stat__label" data-i18n="student.progress.latestScore">Latest score</span>
                                <strong class="sp-stat__value"><bdi data-sp-count="<%= comparison.getLatest().getScore() %>"><%= comparison.getLatest().getScore() %></bdi></strong>
                            </div>
                            <div class="sp-compare__note">
                                <p class="sp-compare__notetitle" data-i18n="student.progress.firstResultTitle">First recorded result.</p>
                                <p data-i18n="student.progress.firstResultSub">Practise this passage again to see a comparison here.</p>
                            </div>
                        </div>
                        <% } else {
                               int delta = comparison.getScoreDelta();
                               int focusDelta = comparison.getFocusDelta();
                               String tone = delta > 0 ? "up" : delta < 0 ? "down" : "same";
                               String deltaText = delta > 0 ? "+" + delta : delta < 0 ? "\u2212" + Math.abs(delta) : "0";
                        %>
                        <div class="sp-compare__grid">
                            <span class="sp-delta sp-delta--<%= tone %>">
                                <% if (delta > 0) { %>
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M4 16l6-6 4 4 6-7" stroke-linecap="round" stroke-linejoin="round"/><path d="M15 7h5v5" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                <% } else if (delta < 0) { %>
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M4 8l6 6 4-4 6 7" stroke-linecap="round" stroke-linejoin="round"/><path d="M15 17h5v-5" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                <% } else { %>
                                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M5 12h14" stroke-linecap="round"/></svg>
                                <% } %>
                                <bdi><%= deltaText %></bdi>
                                <span class="lx-visually-hidden" data-i18n="student.progress.<%= delta > 0 ? "scoreImprovement" : delta < 0 ? "scoreLower" : "scoreSame" %>"><%= delta > 0 ? "Score improvement" : delta < 0 ? "Lower than previous score" : "Same score as before" %></span>
                            </span>
                            <div class="sp-stat">
                                <span class="sp-stat__label" data-i18n="student.progress.previousScore">Previous score</span>
                                <strong class="sp-stat__value sp-stat__value--muted"><bdi data-sp-count="<%= comparison.getPrevious().getScore() %>"><%= comparison.getPrevious().getScore() %></bdi></strong>
                            </div>
                            <span class="sp-arrow" aria-hidden="true"><svg class="lx-i-dir" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M9 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round"/></svg></span>
                            <div class="sp-stat">
                                <span class="sp-stat__label" data-i18n="student.progress.latestScore">Latest score</span>
                                <strong class="sp-stat__value"><bdi data-sp-count="<%= comparison.getLatest().getScore() %>"><%= comparison.getLatest().getScore() %></bdi></strong>
                            </div>
                            <div class="sp-stat sp-stat--focus">
                                <span class="sp-stat__label" data-i18n="student.progress.verifiedFocus">Verified focus</span>
                                <span class="sp-stat__focus">
                                    <%= itemsHtml(comparison.getPrevious().getFocusCount()) %>
                                    <svg class="lx-i-dir" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                    <strong><%= itemsHtml(comparison.getLatest().getFocusCount()) %></strong>
                                </span>
                            </div>
                        </div>
                        <p class="sp-compare__caption">
                            <span data-i18n="student.progress.<%= delta > 0 ? "scoreImprovement" : delta < 0 ? "scoreLower" : "scoreSame" %>"><%= delta > 0 ? "Score improvement" : delta < 0 ? "Lower than previous score" : "Same score as before" %></span>
                            <span aria-hidden="true">·</span>
                            <span data-i18n="student.progress.<%= focusDelta < 0 ? "fewerFocus" : focusDelta > 0 ? "moreFocus" : "sameFocus" %>"><%= focusDelta < 0 ? "Fewer verified focus items" : focusDelta > 0 ? "More verified focus items" : "Same number of focus items" %></span>
                            <span aria-hidden="true">·</span>
                            <span data-i18n="student.progress.compareBasis">Compared with your previous published result for this session</span>
                        </p>
                        <% } %>
                    </section>
                </div>

                <section class="lx-card sp-history sp-reveal" style="--i:4" aria-labelledby="sp-history-title">
                    <div class="sp-history__head">
                        <div>
                            <p class="lx-eyebrow" data-i18n="student.progress.historyEyebrow">Published results</p>
                            <h2 class="sp-history__title" id="sp-history-title" data-i18n="student.progress.historyTitle">Recitation History</h2>
                            <p class="sp-history__sub" data-i18n="student.progress.historySub">Review your published recitation results and track your progress over time.</p>
                        </div>
                        <span class="sp-verified-note"><%= CHECK_SVG %><span data-i18n="student.progress.reviewedOnly">Instructor-reviewed only</span></span>
                    </div>

                    <% if (history.isEmpty()) { %>
                    <div class="sp-empty">
                        <span class="sp-empty__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 6.5C10 5 7 4.5 4 5v13c3-.5 6 0 8 1.5 2-1.5 5-2 8-1.5V5c-3-.5-6 0-8 1.5zM12 6.5v13" stroke-linejoin="round"/></svg></span>
                        <p class="sp-empty__title" data-i18n="student.progress.historyEmptyTitle">Your learning history starts here.</p>
                        <p class="sp-empty__sub" data-i18n="student.progress.historyEmptySub">Once an instructor reviews and publishes your first recitation, your results will appear here.</p>
                        <a class="lx-btn lx-btn--ghost lx-btn--sm" href="<%= ctx %>/student/recitations" data-i18n="student.progress.goToStudio">Go to Recitation Studio</a>
                    </div>
                    <% } else { %>
                    <div class="sp-table-wrap">
                        <table class="sp-table">
                            <caption class="lx-visually-hidden" data-i18n="student.progress.historyTitle">Recitation History</caption>
                            <thead>
                                <tr>
                                    <th scope="col" data-i18n="student.progress.colDate">Date</th>
                                    <th scope="col" data-i18n="student.progress.colSession">Session</th>
                                    <th scope="col" data-i18n="student.progress.colPassage">Passage</th>
                                    <th scope="col" data-i18n="student.progress.colScore">Score</th>
                                    <th scope="col" data-i18n="student.progress.colFocus">Learning focus</th>
                                    <th scope="col" data-i18n="student.progress.colStatus">Status</th>
                                    <th scope="col"><span class="lx-visually-hidden" data-i18n="student.progress.colAction">Action</span></th>
                                </tr>
                            </thead>
                            <tbody>
                            <% for (int i = 0; i < entries.size(); i++) {
                                   StudentProgressHistory.Entry e = entries.get(i);
                                   String resultHref = ctx + "/student/recitation-result?id=" + e.getRecitationId();
                                   String band = e.getScore() >= 80 ? "high" : e.getScore() >= 60 ? "mid" : "low";
                            %>
                                <tr class="sp-row"<%= i >= initialRows ? " hidden data-sp-more" : "" %> style="--r:<%= Math.min(i, initialRows) %>">
                                    <td class="sp-cell sp-cell--date"><time datetime="<%= esc(e.getDateIso()) %>" data-sp-date="short"><%= esc(e.getDateLabel()) %></time></td>
                                    <td class="sp-cell sp-cell--session">
                                        <span class="sp-session"><%= esc(e.getSessionTitle()) %></span>
                                        <% if (e.getAttemptNumber() > 1) { %>
                                        <span class="sp-attempt" data-i18n="student.progress.attempt" data-i18n-n="<%= e.getAttemptNumber() %>">Attempt <%= e.getAttemptNumber() %></span>
                                        <% } %>
                                    </td>
                                    <td class="sp-cell sp-cell--passage"><bdi><%= esc(e.getPassage()) %></bdi></td>
                                    <td class="sp-cell sp-cell--score">
                                        <span class="sp-score sp-score--<%= band %>"><bdi><strong><%= e.getScore() %></strong><small>/100</small></bdi></span>
                                    </td>
                                    <td class="sp-cell sp-cell--focus"><%= focusCountHtml(e.getFocusCount()) %></td>
                                    <td class="sp-cell sp-cell--status"><span class="sp-status"><%= CHECK_SVG %><span data-i18n="student.progress.statusReviewed">Reviewed</span></span></td>
                                    <td class="sp-cell sp-cell--action">
                                        <a class="sp-view" href="<%= esc(resultHref) %>">
                                            <span data-i18n="student.progress.viewResult">View Result</span><%= CHEVRON_SVG %>
                                        </a>
                                    </td>
                                </tr>
                            <% } %>
                            </tbody>
                        </table>
                    </div>
                    <div class="sp-history__foot">
                        <p class="sp-history__count" aria-live="polite">
                            <span data-sp-shown-label data-i18n="student.progress.showingOf" data-i18n-shown="<%= Math.min(initialRows, entries.size()) %>" data-i18n-total="<%= history.getTotalPublished() %>">Showing <%= Math.min(initialRows, entries.size()) %> of <%= history.getTotalPublished() %></span>
                        </p>
                        <% if (entries.size() > initialRows) { %>
                        <button type="button" class="lx-btn lx-btn--ghost lx-btn--sm sp-more" data-sp-more-btn data-step="<%= initialRows %>">
                            <span data-i18n="student.progress.showMore">Show more</span>
                        </button>
                        <% } %>
                    </div>
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
