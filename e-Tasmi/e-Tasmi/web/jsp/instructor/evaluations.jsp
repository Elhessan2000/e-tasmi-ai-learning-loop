<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.LinkedHashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="model.entity.Evaluation" %>
<%@ page import="model.entity.Recitation" %>
<%@ page import="model.entity.RecitationAnalysisJobState" %>
<%@ page import="model.entity.RecitationFindingRecord" %>
<%@ page import="model.service.RecitationAiAnalysisService" %>
<%@ page import="model.service.RecitationAiAnalysisService.Status" %>
<%@ page import="model.service.analysis.FindingAiStatus" %>
<%@ page import="model.service.analysis.FindingInstructorStatus" %>
<%@ page import="model.service.analysis.FindingType" %>
<%@ page import="model.service.quran.TrustedReference" %>
<%!
  private String escapeHtml(Object value) {
    if (value == null) {
      return "";
    }
    return String.valueOf(value)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
  }

  private String highlightHeard(String transcript, List<RecitationFindingRecord> findings) {
    if (transcript == null || transcript.isBlank()) {
      return "<span class=\"lx-note\">No speech detected.</span>";
    }
    String html = escapeHtml(transcript);
    if (findings == null) {
      return html;
    }
    for (RecitationFindingRecord finding : findings) {
      if (finding == null) continue;
      String heard = finding.getHeardText();
      if (heard == null || heard.isBlank() || !transcript.contains(heard)) continue;
      String token = escapeHtml(heard);
      if (!token.isEmpty() && html.contains(token)) {
        html = html.replace(token, "<mark class=\"lx-mark\">" + token + "</mark>");
      }
    }
    return html;
  }

  private int ayahNumber(TrustedReference.Verse verse) {
    if (verse == null || verse.getVerseKey() == null) return 0;
    int colon = verse.getVerseKey().lastIndexOf(':');
    if (colon < 0 || colon + 1 >= verse.getVerseKey().length()) return 0;
    try {
      return Integer.parseInt(verse.getVerseKey().substring(colon + 1));
    } catch (NumberFormatException ex) {
      return 0;
    }
  }

  private boolean studentFacing(FindingInstructorStatus status) {
    return status == FindingInstructorStatus.ACCEPTED
            || status == FindingInstructorStatus.EDITED
            || status == FindingInstructorStatus.INSTRUCTOR_ADDED;
  }

  private String focusText(FindingInstructorStatus status, String aiText, String instructorText) {
    if (status == FindingInstructorStatus.ACCEPTED) {
      return aiText == null || aiText.isBlank() ? null : aiText;
    }
    if (status == FindingInstructorStatus.INSTRUCTOR_ADDED) {
      return instructorText == null || instructorText.isBlank() ? null : instructorText;
    }
    if (instructorText != null && !instructorText.isBlank()) return instructorText;
    return aiText == null || aiText.isBlank() ? null : aiText;
  }

  private String findingTypeLabel(FindingType type) {
    if (type == null) return "Finding";
    switch (type) {
      case MISSING_WORD: return "Missing word";
      case INCORRECT_WORD: return "Incorrect word";
      case EXTRA_WORD: return "Extra word";
      case PASSAGE_MISMATCH: return "Passage mismatch";
      case PRONUNCIATION_OBSERVATION: return "Pronunciation observation";
      default: return "Other";
    }
  }

  private String findingStatusLabel(FindingInstructorStatus status) {
    if (status == null) return "Pending";
    switch (status) {
      case ACCEPTED: return "Accepted";
      case EDITED: return "Edited";
      case REJECTED: return "Rejected";
      case INSTRUCTOR_ADDED: return "Instructor added";
      default: return "Pending";
    }
  }

  private String studentInitials(String name) {
    if (name == null || name.isBlank()) {
      return "ST";
    }
    String[] parts = name.trim().split("\\s+");
    if (parts.length >= 2) {
      return ("" + Character.toUpperCase(parts[0].charAt(0))
              + Character.toUpperCase(parts[parts.length - 1].charAt(0)));
    }
    String single = parts[0];
    if (single.length() >= 2) {
      return single.substring(0, 2).toUpperCase();
    }
    return single.toUpperCase();
  }

  private String sttDisplay(RecitationAiAnalysisService.AnalysisResult analysis) {
    if (analysis == null) return "—";
    String provider = analysis.getSttProvider();
    String model = analysis.getSttModel();
    if (provider == null || provider.isBlank()) return "—";
    String label = provider.trim();
    if ("elevenlabs".equalsIgnoreCase(label)) label = "ElevenLabs";
    else if ("openai".equalsIgnoreCase(label)) label = "OpenAI";
    if (model != null && !model.isBlank()) {
      return escapeHtml(label + " (" + model.trim() + ")");
    }
    return escapeHtml(label);
  }

  private String referenceSourceDisplay(RecitationAiAnalysisService.AnalysisResult analysis) {
    if (analysis == null || analysis.getReferenceSource() == null || analysis.getReferenceSource().isBlank()) {
      return "—";
    }
    if (TrustedReference.SOURCE_QURANPEDIA.equals(analysis.getReferenceSource())) {
      return "Quranpedia";
    }
    if (TrustedReference.SOURCE_QURAN_FOUNDATION.equals(analysis.getReferenceSource())) {
      return "Quran Foundation";
    }
    return escapeHtml(analysis.getReferenceSource());
  }

  private String evaluatorDisplay(RecitationAiAnalysisService.AnalysisResult analysis) {
    if (analysis == null || analysis.getAnalysisModel() == null || analysis.getAnalysisModel().isBlank()) {
      return "—";
    }
    String model = analysis.getAnalysisModel().trim();
    if (model.toLowerCase().startsWith("gpt-")) {
      return escapeHtml("OpenAI (" + model + ")");
    }
    return escapeHtml(model);
  }

  private int transcriptCharCount(RecitationAiAnalysisService.AnalysisResult analysis) {
    if (analysis == null || analysis.getTranscript() == null) return 0;
    return analysis.getTranscript().length();
  }

  private static final java.time.format.DateTimeFormatter SUBMITTED_AT_FMT =
          java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", java.util.Locale.ENGLISH)
                  .withZone(java.time.ZoneId.systemDefault());

  private static final java.time.format.DateTimeFormatter SESSION_DATE_FMT =
          java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", java.util.Locale.ENGLISH);

  private String submittedLabel(java.time.Instant at) {
    return at == null ? "—" : SUBMITTED_AT_FMT.format(at);
  }

  private String sessionWhen(Object date, Object time) {
    String d = date == null ? "" : String.valueOf(date).trim();
    String t = time == null ? "" : String.valueOf(time).trim();
    try {
      if (!d.isEmpty()) d = SESSION_DATE_FMT.format(java.time.LocalDate.parse(d));
    } catch (RuntimeException ignored) {
    }
    if (t.length() >= 5) t = t.substring(0, 5);
    if (d.isEmpty()) return t;
    return t.isEmpty() ? d : d + " · " + t;
  }

  private String ayahLabel(String verseKey) {
    if (verseKey == null || verseKey.isBlank()) return "";
    int colon = verseKey.lastIndexOf(':');
    if (colon < 0 || colon + 1 >= verseKey.length()) return escapeHtml(verseKey);
    return "Ayah " + escapeHtml(verseKey.substring(colon + 1));
  }

  private int intOf(Object value) {
    return value instanceof Number ? ((Number) value).intValue() : 0;
  }

  private int pendingFindings(List<RecitationFindingRecord> findings) {
    int pending = 0;
    if (findings == null) return 0;
    for (RecitationFindingRecord f : findings) {
      if (f != null && f.getInstructorStatus() == FindingInstructorStatus.PENDING) pending++;
    }
    return pending;
  }

  /** One review state per submission, shared by the session cards, the student list and the evaluation header. */
  private String reviewState(Recitation rec, Evaluation ev, RecitationAiAnalysisService.AnalysisResult a, int pending) {
    if (ev != null) return ev.getPublishedAt() != null ? "published" : "saved";
    if (rec.getAnalysisJobState() == RecitationAnalysisJobState.IN_PROGRESS) return "processing";
    if (a == null || a.getStatus() == null) return "awaiting";
    switch (a.getStatus()) {
      case REFERENCE_UNAVAILABLE: return "reference";
      case FAILED: return "failed";
      case CANNOT_EVALUATE: return "cannot";
      case REJECTED: return "rejected";
      default: return pending > 0 ? "review" : "ready";
    }
  }

  private boolean isDone(String state) {
    return "published".equals(state) || "saved".equals(state);
  }

  private boolean isWaiting(String state) {
    return "processing".equals(state) || "awaiting".equals(state);
  }

  private String stateTone(String state) {
    switch (state) {
      case "published": case "saved": return "ok";
      case "processing": return "ai";
      case "ready": return "info";
      case "failed": case "cannot": case "rejected": return "bad";
      default: return "wait";
    }
  }

  private String stateKey(String state) {
    switch (state) {
      case "published": return "statusPublished";
      case "saved": return "statusSaved";
      case "processing": return "statusProcessing";
      case "awaiting": return "statusAwaitingAnalysis";
      case "reference": return "statusReferenceShort";
      case "failed": return "statusFailedShort";
      case "cannot": return "statusCannotShort";
      case "rejected": return "statusRejectedShort";
      case "ready": return "statusReadyToPublish";
      default: return "statusPendingReview";
    }
  }

  private String stateLabel(String state) {
    switch (state) {
      case "published": return "Published";
      case "saved": return "Evaluation saved";
      case "processing": return "Processing";
      case "awaiting": return "Awaiting analysis";
      case "reference": return "Reference unavailable";
      case "failed": return "Analysis failed";
      case "cannot": return "Cannot evaluate";
      case "rejected": return "Rejected";
      case "ready": return "Ready to publish";
      default: return "Needs review";
    }
  }

  private String toneIcon(String tone) {
    String p;
    switch (tone) {
      case "ok": p = "<path d=\"M12 3l7 3v5.5c0 4.2-2.9 7.9-7 9.5-4.1-1.6-7-5.3-7-9.5V6z\" stroke-linejoin=\"round\"/><path d=\"M8.8 12.2l2.2 2.2 4.2-4.4\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>"; break;
      case "ai": p = "<path d=\"M12 3.5l1.9 4.6 4.6 1.9-4.6 1.9L12 16.5l-1.9-4.6L5.5 10l4.6-1.9z\" stroke-linejoin=\"round\"/>"; break;
      case "info": p = "<circle cx=\"12\" cy=\"12\" r=\"8.5\"/><path d=\"M8.5 12.3l2.4 2.4 4.6-4.9\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>"; break;
      case "bad": p = "<circle cx=\"12\" cy=\"12\" r=\"8.5\"/><path d=\"M12 8v4.5M12 15.8h.01\" stroke-linecap=\"round\"/>"; break;
      case "none": p = "<circle cx=\"12\" cy=\"12\" r=\"8.5\" stroke-dasharray=\"3 3\"/>"; break;
      default: p = "<path d=\"M2.5 12s3.5-6.5 9.5-6.5 9.5 6.5 9.5 6.5-3.5 6.5-9.5 6.5S2.5 12 2.5 12z\" stroke-linejoin=\"round\"/><circle cx=\"12\" cy=\"12\" r=\"2.5\"/>";
    }
    return "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" aria-hidden=\"true\">" + p + "</svg>";
  }

  private String statePill(String state) {
    String tone = stateTone(state);
    return "<span class=\"lx-pill lx-pill--sm lx-pill--" + tone + "\">" + toneIcon(tone)
            + "<span data-i18n=\"instructor.evaluations." + stateKey(state) + "\">" + stateLabel(state) + "</span></span>";
  }
%>
<%
  request.setAttribute("activeMenu", "evaluations");
  String ctx = request.getContextPath();
  List<Map<String, Object>> recitationRows = (List<Map<String, Object>>) request.getAttribute("recitationRows");

  List<Map<String, Object>> activeSessionGroups =
          (List<Map<String, Object>>) request.getAttribute("activeSessionGroups");
  if (activeSessionGroups == null) activeSessionGroups = java.util.Collections.emptyList();
  List<Map<String, Object>> reviewedSessionGroups =
          (List<Map<String, Object>>) request.getAttribute("reviewedSessionGroups");
  if (reviewedSessionGroups == null) reviewedSessionGroups = java.util.Collections.emptyList();
  List<Map<String, Object>> sessionGroups = new ArrayList<>(activeSessionGroups);
  sessionGroups.addAll(reviewedSessionGroups);
  boolean hasAnySession = !sessionGroups.isEmpty();

  Map<Long, RecitationAiAnalysisService.AnalysisResult> analysisByRecitationId =
          (Map<Long, RecitationAiAnalysisService.AnalysisResult>) request.getAttribute("analysisByRecitationId");
  if (analysisByRecitationId == null) analysisByRecitationId = java.util.Collections.emptyMap();
  Map<Long, List<RecitationFindingRecord>> findingsByRecitationId =
          (Map<Long, List<RecitationFindingRecord>>) request.getAttribute("findingsByRecitationId");
  if (findingsByRecitationId == null) findingsByRecitationId = java.util.Collections.emptyMap();
  Map<Long, List<TrustedReference.Verse>> displayVersesByRecitationId =
          (Map<Long, List<TrustedReference.Verse>>) request.getAttribute("displayVersesByRecitationId");
  if (displayVersesByRecitationId == null) displayVersesByRecitationId = java.util.Collections.emptyMap();

  /* Navigation lives in the URL: no params -> sessions, ?session -> students, ?session&recitation -> evaluation.
     A failed POST is forwarded here, so fall back to the posted ids. */
  String selectedSessionParam = request.getParameter("session") == null ? "" : request.getParameter("session").trim();
  String selectedRecitationParam = request.getParameter("recitation") == null ? "" : request.getParameter("recitation").trim();
  if ("POST".equalsIgnoreCase(request.getMethod())) {
    if (selectedSessionParam.isEmpty() && request.getParameter("sessionId") != null) selectedSessionParam = request.getParameter("sessionId").trim();
    if (selectedRecitationParam.isEmpty() && request.getParameter("recitationId") != null) selectedRecitationParam = request.getParameter("recitationId").trim();
  }
  boolean showReviewedTab = "reviewed".equals(request.getParameter("tab"));

  Map<String, Object> selectedGroup = null;
  for (Map<String, Object> g : sessionGroups) {
    if (String.valueOf(g.get("sessionId")).equals(selectedSessionParam)) { selectedGroup = g; break; }
  }
  Map<String, Object> selectedRow = null;
  if (recitationRows != null && !selectedRecitationParam.isEmpty()) {
    for (Map<String, Object> row : recitationRows) {
      Recitation rr = row == null ? null : (Recitation) row.get("recitation");
      if (rr != null && String.valueOf(rr.getRecitationId()).equals(selectedRecitationParam)) { selectedRow = row; break; }
    }
  }
  if (selectedRow != null && selectedGroup == null) {
    for (Map<String, Object> g : sessionGroups) {
      if (String.valueOf(g.get("sessionId")).equals(String.valueOf(selectedRow.get("sessionId")))) { selectedGroup = g; break; }
    }
  }
  String view = selectedRow != null ? "review" : selectedGroup != null ? "students" : "sessions";

  String success = (String) request.getAttribute("success");
  String successKey = null;
  if ("1".equals(request.getParameter("saved"))) successKey = "flashSaved";
  else if ("1".equals(request.getParameter("reviewed"))) successKey = "flashMarkedReviewed";
  else if ("0".equals(request.getParameter("reviewed"))) successKey = "flashReopened";
  else if ("1".equals(request.getParameter("verified"))) successKey = "flashFindingSaved";
  else if ("added".equals(request.getParameter("verified"))) successKey = "flashFindingAdded";
  else if ("1".equals(request.getParameter("analysis_retry"))) successKey = "flashRetryStarted";
  if ("flashSaved".equals(successKey) && selectedRow != null) {
    Evaluation savedEv = (Evaluation) selectedRow.get("evaluation");
    if (savedEv != null && savedEv.getPublishedAt() != null) {
      successKey = "flashPublished";
      success = "Result published. The student can see it now.";
    }
  }
  String error = (String) request.getAttribute("error");
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
  <title data-i18n="meta.instructorEvaluationsTitle">Evaluate Recitations - e-Tasmi</title>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <%@ include file="/jsp/common/instructor_ui_head.jspf" %>
  <link rel="preconnect" href="https://fonts.googleapis.com">
  <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Noto+Naskh+Arabic:wght@500;600&family=Amiri+Quran&display=swap">
  <link rel="stylesheet" href="<%= ctx %>/css/learning-loop-experience.css?v=20261003-ux2">
  <script defer src="<%= ctx %>/assets/js/ll-player.js?v=20261003-ux2"></script>
</head>
<body class="instructor-premium-page instructor-package-page instructor-module-page instructor-evaluations-page lx-shell">
<div class="app-shell">
  <%@ include file="/jsp/common/instructor_header.jspf" %>

  <div class="app-main">
    <div class="container sd-container instructor-workspace-shell">
      <div class="instructor-shell-layout">
        <%@ include file="/jsp/instructor/instructor_sidebar.jspf" %>

        <main class="instructor-shell-content" role="main">
          <div class="lx lx-page lx-page--wide" id="lx-evaluations" data-view="<%= view %>">

            <% if (error != null) { %>
            <div class="lx-alert lx-alert--bad" role="alert">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7.5v5.5M12 16.5h.01" stroke-linecap="round"/></svg>
              <span><%= escapeHtml(error) %></span>
            </div>
            <% } %>

            <% if (!hasAnySession) { %>
            <header class="lx-pagehead">
              <p class="lx-eyebrow" data-i18n="instructor.evaluations.loopEyebrow">Learning loop</p>
              <h1 class="lx-title" data-i18n="instructor.evaluations.title">Evaluations</h1>
            </header>
            <section class="lx-card lx-blank">
              <span class="lx-blank__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4" stroke-linecap="round"/></svg></span>
              <h2 class="lx-card__title" data-i18n="instructor.evaluations.noSessionsTitle">No sessions to evaluate yet</h2>
              <p class="lx-card__sub" data-i18n="instructor.evaluations.noSessionsSub">Create a session and approve enrollments - the session will appear here automatically so you can review submissions and track pending students.</p>
              <div class="lx-actions lx-actions--center">
                <a class="lx-btn lx-btn--primary" href="<%= ctx %>/instructor/sessions" data-i18n="instructor.evaluations.goToSessions">Go to Sessions</a>
              </div>
            </section>

            <% } else if ("sessions".equals(view)) {
                 List<Map<String, Object>> shownGroups = showReviewedTab ? reviewedSessionGroups : activeSessionGroups;
            %>
            <%-- ================= 1. Sessions ================= --%>
            <header class="lx-pagehead">
              <p class="lx-eyebrow" data-i18n="instructor.evaluations.loopEyebrow">Learning loop</p>
              <h1 class="lx-title" data-i18n="instructor.evaluations.title">Evaluations</h1>
              <p class="lx-pagehead__sub" data-i18n="instructor.evaluations.sessionsIntro">Choose a session, then a student, to verify the AI analysis and publish their result.</p>
            </header>

            <nav class="lx-tabs" aria-label="Session status">
              <a class="lx-tabs__tab<%= showReviewedTab ? "" : " is-active" %>" href="?"<%= showReviewedTab ? "" : " aria-current=\"page\"" %>>
                <span data-i18n="instructor.evaluations.tabActive">Active</span>
                <span class="lx-tabs__count"><%= activeSessionGroups.size() %></span>
              </a>
              <a class="lx-tabs__tab<%= showReviewedTab ? " is-active" : "" %>" href="?tab=reviewed"<%= showReviewedTab ? " aria-current=\"page\"" : "" %>>
                <span data-i18n="instructor.evaluations.tabReviewed">Reviewed</span>
                <span class="lx-tabs__count"><%= reviewedSessionGroups.size() %></span>
              </a>
            </nav>

            <% if (shownGroups.isEmpty()) { %>
            <section class="lx-card lx-blank">
              <% if (showReviewedTab) { %>
              <h2 class="lx-card__title" data-i18n="instructor.evaluations.noReviewedTitle">No reviewed sessions yet</h2>
              <p class="lx-card__sub" data-i18n="instructor.evaluations.noReviewedSub">When every submission in a session is handled, mark the session as reviewed from its student list.</p>
              <% } else { %>
              <h2 class="lx-card__title" data-i18n="instructor.evaluations.noActiveTitle">No active sessions</h2>
              <p class="lx-card__sub" data-i18n="instructor.evaluations.noActiveSub">All of your sessions are marked as reviewed.</p>
              <% } %>
            </section>
            <% } else { %>
            <ul class="lx-sessions">
              <% int cardIndex = 0;
                 for (Map<String, Object> g : shownGroups) {
                   Object gid = g.get("sessionId");
                   if (gid == null) continue;
                   List<Map<String, Object>> sRows = (List<Map<String, Object>>) g.get("submittedRows");
                   java.util.Set<Object> submittedStudents = new java.util.HashSet<>();
                   int toVerify = 0, processing = 0, published = 0;
                   if (sRows != null) {
                     for (Map<String, Object> row : sRows) {
                       Recitation rec = (Recitation) row.get("recitation");
                       if (rec == null) continue;
                       submittedStudents.add(row.get("studentId") == null ? "r" + rec.getRecitationId() : row.get("studentId"));
                       String st = reviewState(rec, (Evaluation) row.get("evaluation"),
                               analysisByRecitationId.get(rec.getRecitationId()),
                               pendingFindings(findingsByRecitationId.get(rec.getRecitationId())));
                       if (isDone(st)) published++;
                       else if (isWaiting(st)) processing++;
                       else toVerify++;
                     }
                   }
                   int recTotal = published + processing + toVerify;
                   int total = submittedStudents.size() + intOf(g.get("pendingCount"));
                   boolean reviewedSession = g.get("evaluationReviewedAt") != null;
                   String portion = g.get("quranPortion") == null ? "" : String.valueOf(g.get("quranPortion"));
                   String when = sessionWhen(g.get("sessionDate"), g.get("sessionTime"));
              %>
              <li style="--lx-i:<%= cardIndex++ %>">
                <a class="lx-session lx-session--<%= toVerify > 0 ? "wait" : (recTotal > 0 && published == recTotal) ? "ok" : processing > 0 ? "ai" : "none" %>" href="?session=<%= gid %>">
                  <span class="lx-session__top">
                    <span class="lx-session__title"><%= escapeHtml(g.get("sessionTitle") == null ? "Session" : g.get("sessionTitle")) %></span>
                    <% if (reviewedSession) { %>
                    <span class="lx-pill lx-pill--sm lx-pill--ok"><%= toneIcon("ok") %><span data-i18n="instructor.evaluations.sessionReviewed">Reviewed</span></span>
                    <% } else if (toVerify > 0) { %>
                    <span class="lx-pill lx-pill--sm lx-pill--wait"><%= toneIcon("wait") %><span data-i18n="instructor.evaluations.toVerifyCount" data-i18n-count="<%= toVerify %>"><%= toVerify %> to verify</span></span>
                    <% } else if (processing > 0) { %>
                    <span class="lx-pill lx-pill--sm lx-pill--ai lx-pill--live"><%= toneIcon("ai") %><span data-i18n="instructor.evaluations.statusProcessing">Processing</span></span>
                    <% } else if (recTotal > 0) { %>
                    <span class="lx-pill lx-pill--sm lx-pill--ok"><%= toneIcon("ok") %><span data-i18n="instructor.evaluations.allPublished">All published</span></span>
                    <% } else { %>
                    <span class="lx-pill lx-pill--sm"><%= toneIcon("none") %><span data-i18n="instructor.evaluations.noSubmissionsYet">No submissions yet</span></span>
                    <% } %>
                  </span>
                  <span class="lx-session__meta">
                    <% if (!portion.isEmpty()) { %>
                    <span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M12 6.5C10 5 7 4.5 4 5v13c3-.5 6 0 8 1.5 2-1.5 5-2 8-1.5V5c-3-.5-6 0-8 1.5zM12 6.5v13" stroke-linejoin="round"/></svg><%= escapeHtml(portion) %></span>
                    <% } else { %>
                    <span class="is-warn"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7.5v5.5M12 16.5h.01" stroke-linecap="round"/></svg><span data-i18n="instructor.evaluations.noPortionShort">No passage set</span></span>
                    <% } %>
                    <% if (!when.isEmpty()) { %>
                    <span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><rect x="3.5" y="5" width="17" height="15" rx="2"/><path d="M3.5 10h17M8 3v4M16 3v4" stroke-linecap="round"/></svg><%= escapeHtml(when) %></span>
                    <% } %>
                  </span>
                  <span class="lx-session__stats">
                    <span class="lx-stat">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="9" cy="8" r="3.5"/><path d="M3 20a6 6 0 0112 0" stroke-linecap="round"/><path d="M16 4.5a3.5 3.5 0 010 7M21 20a6 6 0 00-4-5.6" stroke-linecap="round"/></svg>
                      <strong><%= total %></strong><small data-i18n="instructor.evaluations.statStudents">Students</small>
                    </span>
                    <span class="lx-stat">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><rect x="9" y="3" width="6" height="11" rx="3"/><path d="M6 11a6 6 0 0012 0M12 17v3" stroke-linecap="round"/></svg>
                      <strong><%= recTotal %></strong><small data-i18n="instructor.evaluations.statSubmissions">Submissions</small>
                    </span>
                    <span class="lx-stat<%= toVerify > 0 ? " is-wait" : "" %>">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M2.5 12s3.5-6.5 9.5-6.5 9.5 6.5 9.5 6.5-3.5 6.5-9.5 6.5S2.5 12 2.5 12z" stroke-linejoin="round"/><circle cx="12" cy="12" r="2.5"/></svg>
                      <strong><%= toVerify %></strong><small data-i18n="instructor.evaluations.statToVerify">To verify</small>
                    </span>
                    <span class="lx-stat<%= published > 0 ? " is-ok" : "" %>">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M12 3l7 3v5.5c0 4.2-2.9 7.9-7 9.5-4.1-1.6-7-5.3-7-9.5V6z" stroke-linejoin="round"/><path d="M8.8 12.2l2.2 2.2 4.2-4.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
                      <strong><%= published %></strong><small data-i18n="instructor.evaluations.statPublished">Published</small>
                    </span>
                  </span>
                  <span class="lx-session__foot">
                    <% if (recTotal > 0) { %>
                    <span class="lx-session__progress">
                      <span class="lx-session__plabel">
                        <span data-i18n="instructor.evaluations.progressVerified">Verified</span>
                        <strong><%= published %>/<%= recTotal %></strong>
                      </span>
                      <span class="lx-meter" aria-hidden="true">
                        <span class="lx-meter__ok" style="width:<%= Math.round(published * 100.0 / recTotal) %>%"></span>
                        <span class="lx-meter__ai" style="width:<%= Math.round(processing * 100.0 / recTotal) %>%"></span>
                        <span class="lx-meter__wait" style="width:<%= Math.round(toVerify * 100.0 / recTotal) %>%"></span>
                      </span>
                    </span>
                    <% } else { %>
                    <span class="lx-session__progress"></span>
                    <% } %>
                    <span class="lx-session__go" aria-hidden="true"><svg class="lx-i-dir" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14M13 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round"/></svg></span>
                  </span>
                </a>
              </li>
              <% } %>
            </ul>
            <% } %>

            <% } else if ("students".equals(view)) {
                 /* ================= 2. Students in one session ================= */
                 Object gid = selectedGroup.get("sessionId");
                 String sTitle = selectedGroup.get("sessionTitle") == null ? "Session" : String.valueOf(selectedGroup.get("sessionTitle"));
                 String portion = selectedGroup.get("quranPortion") == null ? "" : String.valueOf(selectedGroup.get("quranPortion"));
                 String when = sessionWhen(selectedGroup.get("sessionDate"), selectedGroup.get("sessionTime"));
                 boolean reviewedSession = selectedGroup.get("evaluationReviewedAt") != null;
                 List<Map<String, Object>> sRows = (List<Map<String, Object>>) selectedGroup.get("submittedRows");
                 List<Map<String, Object>> pendingStudents = (List<Map<String, Object>>) selectedGroup.get("pendingStudents");
                 if (pendingStudents == null) pendingStudents = java.util.Collections.emptyList();

                 Map<Object, List<Map<String, Object>>> attemptsByStudent = new LinkedHashMap<>();
                 if (sRows != null) {
                   List<Map<String, Object>> sorted = new ArrayList<>(sRows);
                   sorted.removeIf(new java.util.function.Predicate<Map<String, Object>>() {
                     public boolean test(Map<String, Object> m) { return m == null || m.get("recitation") == null; }
                   });
                   sorted.sort(new java.util.Comparator<Map<String, Object>>() {
                     public int compare(Map<String, Object> a, Map<String, Object> b) {
                       java.time.Instant ta = ((Recitation) a.get("recitation")).getSubmissionDate();
                       java.time.Instant tb = ((Recitation) b.get("recitation")).getSubmissionDate();
                       if (ta == null || tb == null) return ta == tb ? 0 : ta == null ? 1 : -1;
                       return tb.compareTo(ta);
                     }
                   });
                   for (Map<String, Object> row : sorted) {
                     Recitation rec = (Recitation) row.get("recitation");
                     Object key = row.get("studentId") == null ? "r" + rec.getRecitationId() : row.get("studentId");
                     List<Map<String, Object>> bucket = attemptsByStudent.get(key);
                     if (bucket == null) { bucket = new ArrayList<>(); attemptsByStudent.put(key, bucket); }
                     bucket.add(row);
                   }
                 }
                 int toVerify = 0, processing = 0, published = 0;
                 int studentsNeeding = 0, studentsPublished = 0;
                 for (List<Map<String, Object>> attempts : attemptsByStudent.values()) {
                   boolean needs = false, done = false;
                   for (Map<String, Object> row : attempts) {
                     Recitation rec = (Recitation) row.get("recitation");
                     String st = reviewState(rec, (Evaluation) row.get("evaluation"),
                             analysisByRecitationId.get(rec.getRecitationId()),
                             pendingFindings(findingsByRecitationId.get(rec.getRecitationId())));
                     if (isDone(st)) { published++; done = true; }
                     else if (isWaiting(st)) processing++;
                     else { toVerify++; needs = true; }
                   }
                   if (needs) studentsNeeding++;
                   if (done) studentsPublished++;
                 }
                 int total = attemptsByStudent.size() + pendingStudents.size();
                 int recTotal = toVerify + processing + published;
            %>
            <a class="lx-back" href="?<%= reviewedSession ? "tab=reviewed" : "" %>">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M19 12H5M11 18l-6-6 6-6" stroke-linecap="round" stroke-linejoin="round"/></svg>
              <span data-i18n="instructor.evaluations.crumbSessions">Sessions</span>
            </a>

            <section class="lx-card lx-enter" aria-labelledby="lx-session-title">
              <div class="lx-head__top">
                <div>
                  <p class="lx-eyebrow" data-i18n="instructor.evaluations.sessionEyebrow">Session</p>
                  <h1 class="lx-title" id="lx-session-title"><%= escapeHtml(sTitle) %></h1>
                </div>
                <div class="lx-head__status">
                  <% if (reviewedSession) { %>
                  <span class="lx-pill lx-pill--ok" data-i18n="instructor.evaluations.sessionReviewed">Reviewed</span>
                  <button type="button" class="lx-btn lx-btn--ghost lx-btn--sm" data-lx-dialog-open="lx-session-dialog">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M3 12a9 9 0 103-6.7L3 8M3 4v4h4" stroke-linecap="round" stroke-linejoin="round"/></svg>
                    <span data-i18n="instructor.evaluations.reopenSession">Reopen for review</span>
                  </button>
                  <% } else { %>
                  <button type="button" class="lx-btn lx-btn--ghost lx-btn--sm" data-lx-dialog-open="lx-session-dialog">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
                    <span data-i18n="instructor.evaluations.markSessionReviewed">Mark session reviewed</span>
                  </button>
                  <% } %>
                </div>
              </div>
              <ul class="lx-meta">
                <li>
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M12 6.5C10 5 7 4.5 4 5v13c3-.5 6 0 8 1.5 2-1.5 5-2 8-1.5V5c-3-.5-6 0-8 1.5zM12 6.5v13" stroke-linejoin="round"/></svg>
                  <% if (portion.isEmpty()) { %><span class="is-warn" data-i18n="instructor.evaluations.noPortionShort">No passage set</span><% } else { %><strong><%= escapeHtml(portion) %></strong><% } %>
                </li>
                <% if (!when.isEmpty()) { %>
                <li>
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><rect x="3.5" y="5" width="17" height="15" rx="2"/><path d="M3.5 10h17M8 3v4M16 3v4" stroke-linecap="round"/></svg>
                  <span><%= escapeHtml(when) %></span>
                </li>
                <% } %>
              </ul>
              <div class="lx-stats">
                <div class="lx-stats__item">
                  <span class="lx-stats__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="9" cy="8" r="3.5"/><path d="M3 20a6 6 0 0112 0" stroke-linecap="round"/><path d="M16 4.5a3.5 3.5 0 010 7M21 20a6 6 0 00-4-5.6" stroke-linecap="round"/></svg></span>
                  <div><strong><%= total %></strong><span data-i18n="instructor.evaluations.statStudents">Students</span></div>
                </div>
                <div class="lx-stats__item">
                  <span class="lx-stats__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><rect x="9" y="3" width="6" height="11" rx="3"/><path d="M6 11a6 6 0 0012 0M12 17v3" stroke-linecap="round"/></svg></span>
                  <div><strong><%= recTotal %></strong><span data-i18n="instructor.evaluations.statSubmissions">Submissions</span></div>
                </div>
                <div class="lx-stats__item lx-stats__item--wait">
                  <span class="lx-stats__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M2.5 12s3.5-6.5 9.5-6.5 9.5 6.5 9.5 6.5-3.5 6.5-9.5 6.5S2.5 12 2.5 12z" stroke-linejoin="round"/><circle cx="12" cy="12" r="2.5"/></svg></span>
                  <div><strong><%= toVerify %></strong><span data-i18n="instructor.evaluations.filterNeedsReview">Needs review</span></div>
                </div>
                <div class="lx-stats__item lx-stats__item--ok">
                  <span class="lx-stats__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 3l7 3v5.5c0 4.2-2.9 7.9-7 9.5-4.1-1.6-7-5.3-7-9.5V6z" stroke-linejoin="round"/><path d="M8.8 12.2l2.2 2.2 4.2-4.4" stroke-linecap="round" stroke-linejoin="round"/></svg></span>
                  <div><strong><%= published %></strong><span data-i18n="instructor.evaluations.statPublished">Published</span></div>
                </div>
              </div>
              <% if (recTotal > 0) { %>
              <div class="lx-session__progress lx-session__progress--wide">
                <span class="lx-session__plabel">
                  <span data-i18n="instructor.evaluations.progressVerified">Verified</span>
                  <strong><%= published %>/<%= recTotal %></strong>
                </span>
                <span class="lx-meter" aria-hidden="true">
                  <span class="lx-meter__ok" style="width:<%= Math.round(published * 100.0 / recTotal) %>%"></span>
                  <span class="lx-meter__ai" style="width:<%= Math.round(processing * 100.0 / recTotal) %>%"></span>
                  <span class="lx-meter__wait" style="width:<%= Math.round(toVerify * 100.0 / recTotal) %>%"></span>
                </span>
              </div>
              <% } %>
              <% if (processing > 0) { %>
              <p class="lx-note lx-note--inline"><span class="lx-spinner lx-spinner--xs" aria-hidden="true"></span><span data-i18n="instructor.evaluations.processingCount" data-i18n-count="<%= processing %>"><%= processing %> submission(s) still being analysed.</span></p>
              <% } %>
            </section>

            <section class="lx-card lx-enter" aria-labelledby="lx-students-title" data-lx-roster>
              <div class="lx-card__head">
                <span class="lx-card__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="9" cy="8" r="3.5"/><path d="M3 20a6 6 0 0112 0" stroke-linecap="round"/><path d="M16 4.5a3.5 3.5 0 010 7M21 20a6 6 0 00-4-5.6" stroke-linecap="round"/></svg></span>
                <div>
                  <h2 class="lx-card__title" id="lx-students-title" data-i18n="instructor.evaluations.studentsTitle">Students</h2>
                  <p class="lx-card__sub" data-i18n="instructor.evaluations.studentsSub">Open a submission to verify the AI findings, score it and publish the result.</p>
                </div>
              </div>

              <div class="lx-toolbar">
                <div class="lx-chips" role="group" aria-label="Filter students">
                  <button type="button" class="lx-chip is-active" data-lx-filter="all" aria-pressed="true"><span data-i18n="instructor.evaluations.filterAll">All</span><span class="lx-chip__n"><%= total %></span></button>
                  <button type="button" class="lx-chip" data-lx-filter="needs" aria-pressed="false"><span data-i18n="instructor.evaluations.filterNeedsReview">Needs review</span><span class="lx-chip__n"><%= studentsNeeding %></span></button>
                  <button type="button" class="lx-chip" data-lx-filter="published" aria-pressed="false"><span data-i18n="instructor.evaluations.filterPublished">Published</span><span class="lx-chip__n"><%= studentsPublished %></span></button>
                  <button type="button" class="lx-chip" data-lx-filter="missing" aria-pressed="false"><span data-i18n="instructor.evaluations.filterNotSubmitted">Not submitted</span><span class="lx-chip__n"><%= pendingStudents.size() %></span></button>
                </div>
                <label class="lx-search">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="11" cy="11" r="7"/><path d="M20 20l-3.5-3.5" stroke-linecap="round"/></svg>
                  <input type="search" data-lx-search data-i18n="instructor.evaluations.searchStudent" data-i18n-attr="placeholder|aria-label" placeholder="Search student" aria-label="Search student">
                </label>
              </div>

              <div class="lx-roster__head" aria-hidden="true">
                <span data-i18n="instructor.evaluations.colStudent">Student</span>
                <span data-i18n="instructor.evaluations.colLatest">Latest attempt</span>
                <span data-i18n="instructor.evaluations.metaStatus">Status</span>
                <span data-i18n="instructor.evaluations.score">Score</span>
                <span></span>
              </div>
              <ul class="lx-roster">
                <% int rowIndex = 0;
                   for (List<Map<String, Object>> attempts : attemptsByStudent.values()) {
                     Map<String, Object> latest = attempts.get(0);
                     Recitation latestRec = (Recitation) latest.get("recitation");
                     Evaluation latestEval = (Evaluation) latest.get("evaluation");
                     int latestPending = pendingFindings(findingsByRecitationId.get(latestRec.getRecitationId()));
                     String latestState = reviewState(latestRec, latestEval, analysisByRecitationId.get(latestRec.getRecitationId()), latestPending);
                     boolean anyNeeds = false, anyPublished = false;
                     for (Map<String, Object> row : attempts) {
                       Recitation rec = (Recitation) row.get("recitation");
                       String st = reviewState(rec, (Evaluation) row.get("evaluation"), analysisByRecitationId.get(rec.getRecitationId()),
                               pendingFindings(findingsByRecitationId.get(rec.getRecitationId())));
                       if (isDone(st)) anyPublished = true; else if (!isWaiting(st)) anyNeeds = true;
                     }
                     String name = latest.get("studentName") == null ? "Student" : String.valueOf(latest.get("studentName"));
                     String ident = latest.get("studentIdentifier") == null ? "" : String.valueOf(latest.get("studentIdentifier"));
                     String filters = (anyNeeds ? " needs" : "") + (anyPublished ? " published" : "");
                %>
                <li class="lx-student" data-lx-row data-filters="all<%= filters %>" data-search="<%= escapeHtml((name + " " + ident).toLowerCase()) %>" style="--lx-i:<%= rowIndex++ %>">
                  <a class="lx-student__main" href="?session=<%= gid %>&amp;recitation=<%= latestRec.getRecitationId() %>">
                    <span class="lx-student__who">
                      <span class="lx-avatar" aria-hidden="true"><%= escapeHtml(studentInitials(name)) %></span>
                      <span class="lx-student__id">
                        <span class="lx-student__name"><%= escapeHtml(name) %></span>
                        <% if (!ident.isEmpty() && !"-".equals(ident)) { %><span class="lx-student__meta"><%= escapeHtml(ident) %></span><% } %>
                        <% if (Boolean.TRUE.equals(latest.get("accountRemoved"))) { %><span class="lx-student__meta" data-i18n="instructor.evaluations.accountRemoved">Account removed</span><% } %>
                      </span>
                    </span>
                    <span class="lx-student__when">
                      <span data-i18n="instructor.evaluations.attemptLabel" data-i18n-n="<%= latestRec.getAttemptNumber() %>">Attempt <%= latestRec.getAttemptNumber() %></span>
                      <small><%= escapeHtml(submittedLabel(latestRec.getSubmissionDate())) %></small>
                    </span>
                    <span class="lx-student__state">
                      <%= statePill(latestState) %>
                      <% if ("review".equals(latestState)) { %><span class="lx-student__hint" data-i18n="instructor.evaluations.pendingFindingsCount" data-i18n-count="<%= latestPending %>"><%= latestPending %> pending</span><% } %>
                    </span>
                    <span class="lx-student__score<%= latestEval == null ? " is-empty" : "" %>"><% if (latestEval != null) { %><%= latestEval.getScore() %><small>/100</small><% } else { %>—<% } %></span>
                    <span class="lx-student__go<%= isDone(latestState) || isWaiting(latestState) ? "" : " is-primary" %>">
                      <span data-i18n="instructor.evaluations.<%= isDone(latestState) ? "openResult" : "review" %>"><%= isDone(latestState) ? "Open" : "Review" %></span>
                      <svg class="lx-i-dir" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                    </span>
                  </a>
                  <% if (attempts.size() > 1) { %>
                  <details class="lx-attempts">
                    <summary><span data-i18n="instructor.evaluations.earlierAttempts" data-i18n-count="<%= attempts.size() - 1 %>"><%= attempts.size() - 1 %> earlier attempt(s)</span></summary>
                    <ul>
                      <% for (int ai = 1; ai < attempts.size(); ai++) {
                           Map<String, Object> row = attempts.get(ai);
                           Recitation rec = (Recitation) row.get("recitation");
                           Evaluation ev = (Evaluation) row.get("evaluation");
                           String st = reviewState(rec, ev, analysisByRecitationId.get(rec.getRecitationId()),
                                   pendingFindings(findingsByRecitationId.get(rec.getRecitationId())));
                      %>
                      <li>
                        <a href="?session=<%= gid %>&amp;recitation=<%= rec.getRecitationId() %>">
                          <span data-i18n="instructor.evaluations.attemptLabel" data-i18n-n="<%= rec.getAttemptNumber() %>">Attempt <%= rec.getAttemptNumber() %></span>
                          <span class="lx-note"><%= escapeHtml(submittedLabel(rec.getSubmissionDate())) %></span>
                          <% if (ev != null) { %><span class="lx-student__score lx-student__score--sm"><%= ev.getScore() %><small>/100</small></span><% } %>
                          <%= statePill(st) %>
                        </a>
                      </li>
                      <% } %>
                    </ul>
                  </details>
                  <% } %>
                </li>
                <% } %>
                <% for (Map<String, Object> pst : pendingStudents) {
                     String name = pst.get("name") == null ? "Student" : String.valueOf(pst.get("name"));
                     String ident = pst.get("identifier") == null ? "" : String.valueOf(pst.get("identifier"));
                %>
                <li class="lx-student is-missing" data-lx-row data-filters="all missing" data-search="<%= escapeHtml((name + " " + ident).toLowerCase()) %>" style="--lx-i:<%= rowIndex++ %>">
                  <div class="lx-student__main">
                    <span class="lx-student__who">
                      <span class="lx-avatar lx-avatar--muted" aria-hidden="true"><%= escapeHtml(studentInitials(name)) %></span>
                      <span class="lx-student__id">
                        <span class="lx-student__name"><%= escapeHtml(name) %></span>
                        <% if (!ident.isEmpty() && !"-".equals(ident)) { %><span class="lx-student__meta"><%= escapeHtml(ident) %></span><% } %>
                      </span>
                    </span>
                    <span class="lx-student__when"><span class="lx-note">—</span></span>
                    <span class="lx-student__state"><span class="lx-pill lx-pill--sm lx-pill--none"><%= toneIcon("none") %><span data-i18n="instructor.evaluations.statusNotSubmitted">Not submitted</span></span></span>
                    <span class="lx-student__score is-empty">—</span>
                    <span class="lx-student__go is-placeholder" aria-hidden="true"></span>
                  </div>
                </li>
                <% } %>
              </ul>
              <% if (attemptsByStudent.isEmpty() && pendingStudents.isEmpty()) { %>
              <p class="lx-empty" data-i18n="instructor.evaluations.noStudentsEnrolled">No approved students are enrolled in this session yet.</p>
              <% } %>
              <p class="lx-empty" data-lx-empty hidden data-i18n="instructor.evaluations.noStudentsMatch">No students match this view.</p>
            </section>

            <dialog class="lx-dialog" id="lx-session-dialog" aria-labelledby="lx-session-dialog-title">
              <form method="post" action="<%= ctx %>/instructor/evaluations">
                <input type="hidden" name="action" value="<%= reviewedSession ? "reopen" : "mark_reviewed" %>"/>
                <input type="hidden" name="sessionId" value="<%= gid %>"/>
                <% if (reviewedSession) { %>
                <h2 class="lx-dialog__title" id="lx-session-dialog-title" data-i18n="instructor.evaluations.reopenDialogTitle">Reopen this session for review?</h2>
                <p class="lx-dialog__body" data-i18n="instructor.evaluations.reopenDialogBody">The session moves back to Active so you can keep verifying submissions.</p>
                <% } else { %>
                <h2 class="lx-dialog__title" id="lx-session-dialog-title" data-i18n="instructor.evaluations.markDialogTitle">Mark this session as reviewed?</h2>
                <p class="lx-dialog__body" data-i18n="instructor.evaluations.markDialogBody">The session moves to the Reviewed tab. You can reopen it at any time.</p>
                <ul class="lx-dialog__list">
                  <% if (toVerify + processing > 0) { %>
                  <li class="is-warn" data-i18n="instructor.evaluations.markWarnUnverified" data-i18n-count="<%= toVerify + processing %>"><%= toVerify + processing %> submission(s) are not published yet and will stay unscored.</li>
                  <% } %>
                  <% if (!pendingStudents.isEmpty()) { %>
                  <li class="is-warn" data-i18n="instructor.evaluations.markWarnMissing" data-i18n-count="<%= pendingStudents.size() %>"><%= pendingStudents.size() %> student(s) have not submitted.</li>
                  <% } %>
                  <% if (toVerify + processing == 0 && pendingStudents.isEmpty()) { %>
                  <li class="is-ok" data-i18n="instructor.evaluations.markAllDone">Every submission in this session is published.</li>
                  <% } %>
                </ul>
                <% } %>
                <div class="lx-dialog__actions">
                  <button type="button" class="lx-btn lx-btn--ghost" data-lx-dialog-close data-i18n="instructor.evaluations.cancel">Cancel</button>
                  <button type="submit" class="lx-btn lx-btn--primary" data-lx-busy>
                    <span class="lx-btn__spinner" aria-hidden="true"></span>
                    <span data-i18n="instructor.evaluations.<%= reviewedSession ? "reopenSession" : "markSessionReviewed" %>"><%= reviewedSession ? "Reopen for review" : "Mark session reviewed" %></span>
                  </button>
                </div>
              </form>
            </dialog>

            <% } else {
                 /* ================= 3. Evaluation of one submission ================= */
                 Map<String, Object> row = selectedRow;
                 Recitation r = (Recitation) row.get("recitation");
                 Evaluation evaluation = (Evaluation) row.get("evaluation");
                 String studentName = row.get("studentName") == null ? "Student" : String.valueOf(row.get("studentName"));
                 boolean studentAccountRemoved = Boolean.TRUE.equals(row.get("accountRemoved"));
                 String studentIdentifier = row.get("studentIdentifier") == null ? "-" : String.valueOf(row.get("studentIdentifier"));
                 String sessionTitle = row.get("sessionTitle") == null ? "Session" : String.valueOf(row.get("sessionTitle"));
                 String quranPortion = row.get("quranPortion") == null ? "" : String.valueOf(row.get("quranPortion"));
                 RecitationAiAnalysisService.AnalysisResult analysis = analysisByRecitationId.get(r.getRecitationId());
                 boolean hasExpectedPortion = quranPortion != null && !quranPortion.trim().isEmpty();
                 boolean analysisInProgress = r.getAnalysisJobState() == RecitationAnalysisJobState.IN_PROGRESS;
                 boolean showRetryAnalysis = evaluation == null && analysis != null && (
                         analysis.getStatus() == Status.FAILED
                         || analysis.getStatus() == Status.CANNOT_EVALUATE
                         || analysis.getStatus() == Status.REFERENCE_UNAVAILABLE);
                 List<RecitationFindingRecord> storedFindings =
                         findingsByRecitationId.getOrDefault(r.getRecitationId(), java.util.Collections.emptyList());
                 int pendingFindingCount = pendingFindings(storedFindings);
                 Object sessionIdObj = row.get("sessionId");
                 String sessionIdValue = sessionIdObj == null ? "" : String.valueOf(sessionIdObj);
                 String reviewStateKey = reviewState(r, evaluation, analysis, pendingFindingCount);

                 /* The next submission in this session that still needs the instructor. */
                 String nextHref = null;
                 String nextName = null;
                 int nextAttempt = 0;
                 if (selectedGroup != null && selectedGroup.get("submittedRows") != null) {
                   for (Map<String, Object> other : (List<Map<String, Object>>) selectedGroup.get("submittedRows")) {
                     Recitation orec = (Recitation) other.get("recitation");
                     if (orec == null || orec.getRecitationId() == r.getRecitationId()) continue;
                     String ost = reviewState(orec, (Evaluation) other.get("evaluation"), analysisByRecitationId.get(orec.getRecitationId()),
                             pendingFindings(findingsByRecitationId.get(orec.getRecitationId())));
                     if (isDone(ost) || isWaiting(ost)) continue;
                     nextHref = "?session=" + sessionIdValue + "&recitation=" + orec.getRecitationId();
                     nextName = other.get("studentName") == null ? "Student" : String.valueOf(other.get("studentName"));
                     nextAttempt = orec.getAttemptNumber();
                     break;
                   }
                 }
            %>
            <nav class="lx-trail" aria-label="Breadcrumb">
              <a href="?" data-i18n="instructor.evaluations.crumbSessions">Sessions</a>
              <span aria-hidden="true">/</span>
              <a href="?session=<%= escapeHtml(sessionIdValue) %>"><%= escapeHtml(sessionTitle) %></a>
              <span aria-hidden="true">/</span>
              <span aria-current="page"><%= escapeHtml(studentName) %></span>
            </nav>
            <%@ include file="/jsp/instructor/recitation-review-workspace.jspf" %>

            <dialog class="lx-dialog" id="lx-publish-dialog" aria-labelledby="lx-publish-title">
              <span class="lx-dialog__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 3l7 3v5.5c0 4.2-2.9 7.9-7 9.5-4.1-1.6-7-5.3-7-9.5V6z" stroke-linejoin="round"/><path d="M8.8 12.2l2.2 2.2 4.2-4.4" stroke-linecap="round" stroke-linejoin="round"/></svg></span>
              <h2 class="lx-dialog__title" id="lx-publish-title" data-i18n="instructor.evaluations.statusReadyToPublish">Ready to publish</h2>
              <p class="lx-dialog__body" data-i18n="instructor.evaluations.publishVisible">This result will become visible to the student.</p>
              <dl class="lx-dialog__summary">
                <div><dt data-i18n="instructor.evaluations.finalScoreLabel">Final score</dt><dd><span data-lx-publish-score>—</span> <small>/ 100</small></dd></div>
                <div><dt data-i18n="instructor.evaluations.verifiedFocusItems">Verified focus items</dt><dd data-lx-publish-focus>0</dd></div>
              </dl>
              <ul class="lx-dialog__list">
                <li class="is-ok" data-i18n="instructor.evaluations.publishIncluded">Included: accepted, edited, and instructor-added findings.</li>
                <li data-i18n="instructor.evaluations.publishExcluded">Excluded: rejected and pending findings.</li>
              </ul>
              <div class="lx-dialog__actions">
                <button type="button" class="lx-btn lx-btn--ghost" data-lx-dialog-close data-i18n="instructor.evaluations.cancel">Cancel</button>
                <button type="button" class="lx-btn lx-btn--primary" data-lx-publish-go>
                  <span class="lx-btn__spinner" aria-hidden="true"></span>
                  <svg class="lx-btn__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" aria-hidden="true"><path d="M4.5 12L20 4.5 16 20l-4.2-6.3L4.5 12z" stroke-linejoin="round"/><path d="M11.8 13.7L20 4.5" stroke-linecap="round"/></svg>
                  <span data-i18n="instructor.evaluations.publishResult">Publish result</span>
                </button>
              </div>
            </dialog>
            <% } %>
          </div>
          <%@ include file="/jsp/common/app_footer.jspf" %>
        </main>
      </div>
    </div>
  </div>
</div>

<% if (success != null) { %>
<div class="lx-toast" role="status" data-lx-toast>
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
  <span<% if (successKey != null) { %> data-i18n="instructor.evaluations.<%= successKey %>"<% } %>><%= escapeHtml(success) %></span>
</div>
<% } %>

<script>
(function () {
  var root = document.getElementById('lx-evaluations');
  if (!root) return;
  var reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  var STORE_KEY = 'lx-eval-last-action';

  function t(key, vars) {
    return (window.EtasmiI18n && EtasmiI18n.t(key, vars)) || '';
  }

  /* ---------- toast ---------- */
  var toast = document.querySelector('[data-lx-toast]');
  if (toast) {
    requestAnimationFrame(function () { toast.classList.add('is-in'); });
    setTimeout(function () { toast.classList.remove('is-in'); }, 4200);
  }

  /* ---------- dialogs ---------- */
  function openDialog(dialog) {
    if (!dialog) return;
    if (typeof dialog.showModal === 'function') dialog.showModal(); else dialog.setAttribute('open', '');
  }
  function closeDialog(dialog) {
    if (!dialog) return;
    if (typeof dialog.close === 'function') dialog.close(); else dialog.removeAttribute('open');
  }
  root.addEventListener('click', function (e) {
    var opener = e.target.closest('[data-lx-dialog-open]');
    if (opener) { openDialog(document.getElementById(opener.getAttribute('data-lx-dialog-open'))); return; }
    var closer = e.target.closest('[data-lx-dialog-close]');
    if (closer) closeDialog(closer.closest('dialog'));
  });
  root.querySelectorAll('dialog').forEach(function (dialog) {
    dialog.addEventListener('click', function (e) { if (e.target === dialog) closeDialog(dialog); });
  });

  /* Busy state on every POST so a slow round-trip never looks like a dead click. */
  root.addEventListener('submit', function (e) {
    var form = e.target;
    var button = e.submitter || form.querySelector('[type="submit"]');
    if (button) { button.classList.add('is-busy'); button.disabled = true; }
    var finding = form.closest('[data-rr-finding]');
    var action = form.querySelector('input[name="action"]');
    var progress = root.querySelector('[data-lx-progress]');
    try {
      sessionStorage.setItem(STORE_KEY, JSON.stringify({
        recitation: (form.querySelector('input[name="recitationId"]') || {}).value || '',
        finding: finding ? finding.getAttribute('data-finding-id') : '',
        action: action ? action.value : '',
        scrollY: window.scrollY,
        pct: progress ? progress.getAttribute('data-pct') : null
      }));
    } catch (err) {}
  });

  /* ---------- student list: filter + search ---------- */
  var roster = root.querySelector('[data-lx-roster]');
  if (roster) {
    var filter = 'all';
    var query = '';
    var search = roster.querySelector('[data-lx-search]');
    var empty = roster.querySelector('[data-lx-empty]');
    var rows = roster.querySelectorAll('[data-lx-row]');
    var apply = function () {
      var shown = 0;
      rows.forEach(function (row) {
        var tags = ' ' + (row.getAttribute('data-filters') || '') + ' ';
        var ok = tags.indexOf(' ' + filter + ' ') !== -1
          && (!query || (row.getAttribute('data-search') || '').indexOf(query) !== -1);
        row.hidden = !ok;
        if (ok) shown++;
      });
      if (empty) empty.hidden = shown !== 0 || rows.length === 0;
    };
    roster.querySelectorAll('[data-lx-filter]').forEach(function (chip) {
      chip.addEventListener('click', function () {
        filter = chip.getAttribute('data-lx-filter');
        roster.querySelectorAll('[data-lx-filter]').forEach(function (c) {
          var on = c === chip;
          c.classList.toggle('is-active', on);
          c.setAttribute('aria-pressed', on ? 'true' : 'false');
        });
        apply();
      });
    });
    if (search) search.addEventListener('input', function () { query = search.value.trim().toLowerCase(); apply(); });
  }

  /* ---------- evaluation ---------- */
  var detail = root.querySelector('[data-recitation-detail]');
  if (!detail) return;

  detail.addEventListener('click', function (e) {
    var editToggle = e.target.closest('[data-lx-edit-toggle]');
    if (editToggle) {
      var editForm = document.getElementById(editToggle.getAttribute('aria-controls'));
      if (!editForm) return;
      var opening = editForm.hidden;
      editForm.hidden = !opening;
      editToggle.setAttribute('aria-expanded', opening ? 'true' : 'false');
      var card = editToggle.closest('[data-rr-finding]');
      if (card) card.classList.toggle('is-editing', opening);
      if (opening) {
        var first = editForm.querySelector('input:not([type="hidden"]), textarea');
        if (first) first.focus();
      }
      return;
    }
    var editCancel = e.target.closest('[data-lx-edit-cancel]');
    if (editCancel) {
      var cancelForm = editCancel.closest('[data-rr-edit-form]');
      if (!cancelForm) return;
      cancelForm.hidden = true;
      var owner = cancelForm.closest('[data-rr-finding]');
      if (owner) owner.classList.remove('is-editing');
      var toggle = owner && owner.querySelector('[data-lx-edit-toggle]');
      if (toggle) { toggle.setAttribute('aria-expanded', 'false'); toggle.focus(); }
      return;
    }
    var addToggle = e.target.closest('[data-rr-add-toggle]');
    if (addToggle) {
      var form = document.getElementById(addToggle.getAttribute('aria-controls'));
      if (!form) return;
      var open = form.hidden;
      form.hidden = !open;
      var mainToggle = detail.querySelector('.lx-add[aria-controls="' + form.id + '"]');
      if (mainToggle) mainToggle.setAttribute('aria-expanded', open ? 'true' : 'false');
      if (open) {
        var firstField = form.querySelector('select, input:not([type="hidden"])');
        if (firstField) firstField.focus();
      } else if (mainToggle) {
        mainToggle.focus();
      }
      return;
    }
    var retry = e.target.closest('[data-ai-analyze]');
    if (retry && !retry.disabled) {
      e.preventDefault();
      var retryForm = document.getElementById(retry.getAttribute('form'));
      if (!retryForm) return;
      var actionInput = retryForm.querySelector('[data-action-input]');
      if (actionInput) actionInput.value = 'retry_analysis';
      retry.classList.add('is-busy');
      retry.disabled = true;
      retryForm.submit();
    }
  });

  /* Publish: confirm first, showing exactly what the student will receive. */
  var publishDialog = document.getElementById('lx-publish-dialog');
  var evalForm = detail.querySelector('form[data-lx-eval-form]');
  if (evalForm && publishDialog) {
    evalForm.addEventListener('submit', function (e) {
      if (evalForm.getAttribute('data-confirmed') === '1') return;
      e.preventDefault();
      e.stopImmediatePropagation();
      var save = evalForm.querySelector('[data-ai-save]');
      if (save) { save.classList.remove('is-busy'); save.disabled = false; }
      var score = evalForm.querySelector('input[name="score"]');
      publishDialog.querySelector('[data-lx-publish-score]').textContent = score && score.value !== '' ? score.value : '—';
      publishDialog.querySelector('[data-lx-publish-focus]').textContent = evalForm.getAttribute('data-focus-count') || '0';
      openDialog(publishDialog);
    }, true);
    publishDialog.querySelector('[data-lx-publish-go]').addEventListener('click', function () {
      var go = this;
      go.classList.add('is-busy');
      go.disabled = true;
      evalForm.querySelector('[data-action-input]').value = 'save';
      evalForm.setAttribute('data-confirmed', '1');
      try { sessionStorage.setItem(STORE_KEY, JSON.stringify({ recitation: detail.getAttribute('data-recitation-detail'), action: 'save' })); } catch (err) {}
      evalForm.submit();
    });
  }

  /* After a reload, bring the instructor back to what they just decided. */
  var last = null;
  try { last = JSON.parse(sessionStorage.getItem(STORE_KEY) || 'null'); sessionStorage.removeItem(STORE_KEY); } catch (err) {}
  if (last && String(last.recitation) === detail.getAttribute('data-recitation-detail')) {
    var target = null;
    if (last.finding) target = detail.querySelector('[data-finding-id="' + last.finding + '"]');
    else if (last.action === 'add_finding') {
      var all = detail.querySelectorAll('[data-rr-finding]');
      target = all.length ? all[all.length - 1] : null;
    } else if (last.action === 'save') target = detail.querySelector('[data-lx-done]');
    if ('scrollRestoration' in history) history.scrollRestoration = 'manual';
    if (last.action === 'save' && target) {
      target.scrollIntoView({ behavior: 'auto', block: 'center' });
    } else if (typeof last.scrollY === 'number') {
      window.scrollTo(0, last.scrollY);
      if (target) {
        var box = target.getBoundingClientRect();
        if (box.bottom < 80 || box.top > window.innerHeight - 40) target.scrollIntoView({ behavior: 'auto', block: 'center' });
      }
    }
    if (target) {
      target.classList.add('is-just-decided');
      setTimeout(function () { target.classList.remove('is-just-decided'); }, 2400);
    }
    var bar = detail.querySelector('[data-lx-progress] .lx-progress__track > span');
    if (bar && last.pct !== null && last.pct !== undefined && !reduceMotion) {
      var to = bar.style.getPropertyValue('--lx-pct');
      bar.style.transition = 'none';
      bar.style.setProperty('--lx-pct', last.pct + '%');
      void bar.offsetWidth;
      bar.style.transition = '';
      requestAnimationFrame(function () { bar.style.setProperty('--lx-pct', to); });
    }
  }

  /* While the analysis runs, refresh quietly unless the instructor is listening or typing. */
  if (detail.hasAttribute('data-lx-analysing')) {
    var tries = 0;
    var timer = setInterval(function () {
      tries++;
      if (tries > 12) { clearInterval(timer); return; }
      var audio = detail.querySelector('[data-rr-audio]');
      if (audio && !audio.paused) return;
      var active = document.activeElement;
      if (active && /INPUT|TEXTAREA|SELECT/.test(active.tagName)) return;
      window.location.reload();
    }, 10000);
  }
})();
</script>
</body>
</html>
