<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="model.entity.Progress" %>
<%@ page import="model.entity.StudentProgressSummary" %>
<%
  request.setAttribute("activeMenu", "progress");

  Progress p = (Progress) request.getAttribute("progress");
  StudentProgressSummary summary = (StudentProgressSummary) request.getAttribute("progressSummary");

  double completion = 0.0;
  String lastUpdatedText = "-";
  if (p != null) {
      java.math.BigDecimal rate = p.getCompletionRate();
      completion = (rate == null) ? 0.0 : rate.doubleValue();
      if (completion < 0) completion = 0;
      if (completion > 100) completion = 100;
      if (p.getLastUpdated() != null) {
          lastUpdatedText = String.valueOf(p.getLastUpdated());
      }
  }

  if (summary == null) {
      summary = new StudentProgressSummary();
  }

  int attendanceRate = 0;
  if (summary.getAttendanceMarked() > 0) {
      attendanceRate = (int) Math.round((summary.getAttendancePresent() * 100.0) / summary.getAttendanceMarked());
  }
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.studentProgressTitle">My Progress - e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <%@ include file="/jsp/common/student_ui_head.jspf" %>
    <script defer src="<%= request.getContextPath() %>/assets/js/app.js"></script>
</head>
<body class="student-premium-page student-package-page student-module-page student-page--progress">
<div class="app-shell">
    <%@ include file="/jsp/common/student_header.jspf" %>
    <div class="app-main">
        <div class="container sd-container student-workspace-shell">
            <div class="student-shell-layout">
                <%@ include file="/jsp/student/student_sidebar.jspf" %>

                <main class="student-shell-content" role="main">
                    <div class="student-workspace-view">
                <%@ include file="/jsp/common/student_breadcrumb.jspf" %>
                <header class="student-hero student-hero--progress" data-i18n="student.progress.heroAria" data-i18n-attr="aria-label" aria-label="Progress">
                    <div class="student-hero__copy">
                        <h1 class="student-hero__title" data-i18n="student.progress.heroTitle">Your learning snapshot</h1>
                        <p class="student-hero__sub" data-i18n="student.progress.heroSubtitle">Completion, sessions, recitations, and attendance in one place.</p>
                    </div>
                    <div class="student-hero__visual" aria-hidden="true">
                        <span class="student-hero__badge">
                            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                <path d="M3 20h18" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>
                                <path d="m3 16 5-5 4 3 8-8" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/>
                                <path d="M15 6h5v5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/>
                            </svg>
                        </span>
                    </div>
                </header>

                <section class="sd-card" id="progressOverview" data-i18n="student.progress.overviewAria" data-i18n-attr="aria-label" aria-label="Progress overview" data-counters="1">
                    <div class="sd-card-head sd-card-head--progress">
                        <div>
                            <div class="sd-card-eyebrow" data-i18n="student.progress.overview">Overview</div>
                            <div class="sd-card-title" data-i18n="student.progress.completionRate">Completion rate</div>
                        </div>
                        <div class="sd-progress-rate" data-counter="1" data-target="<%= (int) Math.round(completion) %>">0</div>
                    </div>
                    <div class="sd-card-body">
                        <div class="sd-progressbar" data-progress="<%= (int) completion %>">
                            <span style="width: <%= (int) completion %>%"></span>
                        </div>
                        <p class="sd-empty-sub sd-empty-sub--tight"><span data-i18n="student.progress.lastUpdated">Last updated:</span> <%= lastUpdatedText %></p>

                        <div class="sd-progress-metrics" data-i18n="student.progress.detailsAria" data-i18n-attr="aria-label" aria-label="Progress details">
                            <div class="sd-progress-metric">
                                <span class="sd-progress-metric__label" data-i18n="student.progress.completedSessions">Completed sessions</span>
                                <strong class="sd-progress-metric__value" data-counter="1" data-target="<%= summary.getCompletedSessions() %>">0</strong>
                            </div>
                            <div class="sd-progress-metric">
                                <span class="sd-progress-metric__label" data-i18n="student.progress.recitationsSubmitted">Recitations submitted</span>
                                <strong class="sd-progress-metric__value" data-counter="1" data-target="<%= summary.getRecitationsSubmitted() %>">0</strong>
                            </div>
                            <div class="sd-progress-metric">
                                <span class="sd-progress-metric__label" data-i18n="student.progress.reviewedByInstructor">Reviewed by instructor</span>
                                <strong class="sd-progress-metric__value" data-counter="1" data-target="<%= summary.getEvaluatedRecitations() %>">0</strong>
                            </div>
                            <div class="sd-progress-metric">
                                <span class="sd-progress-metric__label" data-i18n="student.progress.attendanceRate">Attendance (present rate)</span>
                                <strong class="sd-progress-metric__value" data-counter="1" data-target="<%= attendanceRate %>">0</strong>
                                <span class="sd-progress-metric__hint"><%= summary.getAttendancePresent() %> / <%= summary.getAttendanceMarked() %> marked</span>
                            </div>
                        </div>
                    </div>
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



