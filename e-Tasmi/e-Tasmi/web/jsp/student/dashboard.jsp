<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ page import="util.LocaleSupport" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="model.entity.Enrollment" %>
<%@ page import="model.entity.EnrollmentStatus" %>
<%@ page import="model.entity.Payment" %>
<%@ page import="model.entity.PaymentStatus" %>
<%@ page import="model.entity.Progress" %>
<%@ page import="model.entity.TasmiSession" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%!
    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String text(Object val) {
        return val == null ? "" : val.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
%>
<%
    request.setAttribute("activeMenu", "overview");

    String ctx = request.getContextPath();
    String heroDateLabel = java.time.LocalDate.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d", java.util.Locale.ENGLISH));

    List<Map<String, Object>> upcomingCards = (List<Map<String, Object>>) request.getAttribute("upcomingSessionCards");
    if (upcomingCards == null) upcomingCards = Collections.emptyList();
    int enrollmentsCount = 0;
    int pendingEvaluationsCount = 0;
    try { enrollmentsCount = ((Number) request.getAttribute("enrollmentsCount")).intValue(); } catch (Exception ignored) {}
    try { pendingEvaluationsCount = ((Number) request.getAttribute("pendingEvaluationsCount")).intValue(); } catch (Exception ignored) {}

    Progress progress = (Progress) request.getAttribute("progress");
    int completionInt = 0;
    if (progress != null && progress.getCompletionRate() != null) {
        double completion = progress.getCompletionRate().doubleValue();
        if (completion < 0) completion = 0;
        if (completion > 100) completion = 100;
        completionInt = (int) Math.round(completion);
    }

    int completedApprox = Math.max(0, enrollmentsCount - pendingEvaluationsCount);
    int upcomingCount = upcomingCards.size();

    /* Reliable, data-driven stat descriptors. No fabricated deltas. */
    String totalDesc       = enrollmentsCount == 0 ? "No enrollments yet"
                                                   : (enrollmentsCount == 1 ? "1 enrolment so far"
                                                                            : enrollmentsCount + " total enrolments");
    String completedDesc   = pendingEvaluationsCount == 0
                                ? (completedApprox == 0 ? "Nothing reviewed yet" : "All evaluations up to date")
                                : (pendingEvaluationsCount == 1 ? "1 pending review"
                                                                : pendingEvaluationsCount + " pending review");
    String avgScoreDesc    = progress == null ? "No data yet" : "Overall completion rate";
    String activeDesc      = upcomingCount == 0 ? "No upcoming session"
                                                : (upcomingCount == 1 ? "1 session in progress"
                                                                       : upcomingCount + " sessions in progress");

    Map<String, Object> featuredCard = !upcomingCards.isEmpty() ? upcomingCards.get(0) : null;

    DateTimeFormatter dashboardDateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    DateTimeFormatter dashboardTimeFmt = DateTimeFormatter.ofPattern("hh:mm a");

    /* ── Feature 3: countdown — embed ISO date-time for the first upcoming session ── */
    String countdownIso = "";
    if (featuredCard != null) {
        TasmiSession fsess = (TasmiSession) featuredCard.get("session");
        if (fsess != null && fsess.getSessionDate() != null) {
            java.time.LocalTime lt = fsess.getSessionTime() != null ? fsess.getSessionTime() : java.time.LocalTime.of(0, 0);
            countdownIso = fsess.getSessionDate().toString() + "T" + lt.toString();
        }
    }

    /* ── Feature 2: heatmap — collect activity dates from available session data ── */
    java.util.List<String> activityDates = new java.util.ArrayList<>();
    @SuppressWarnings("unchecked")
    java.util.List<String> rawActivityDates = (java.util.List<String>) request.getAttribute("recitationActivityDates");
    if (rawActivityDates != null) {
        activityDates.addAll(rawActivityDates);
    } else {
        /* Fallback: use enrolled session dates as proxy activity markers */
        for (Map<String, Object> card : upcomingCards) {
            TasmiSession s = (TasmiSession) card.get("session");
            if (s != null && s.getSessionDate() != null) {
                activityDates.add(s.getSessionDate().toString());
            }
        }
    }
    /* Build JSON array for embedding */
    StringBuilder activityJson = new StringBuilder("[");
    for (int _i = 0; _i < activityDates.size(); _i++) {
        if (_i > 0) activityJson.append(",");
        activityJson.append("\"").append(activityDates.get(_i)).append("\"");
    }
    activityJson.append("]");
    int activityTotal = activityDates.size();
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.studentDashboardTitle">Student Dashboard - e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <%@ include file="/jsp/common/student_ui_head.jspf" %>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/student-my-sessions.css?v=20260428-mys-cards-ui2">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/student-dashboard-layout.css?v=20260607-dash-spatial-polish">
    <script defer src="<%= request.getContextPath() %>/assets/js/app.js"></script>
    <style>
    /* ═══════════════════════════════════════════════════
       STUDENT DASHBOARD — SHARED TOKENS + ALL WIDGETS
    ═══════════════════════════════════════════════════ */
    :root {
      --sdash-lime: #89F336;
      --sdash-lime-dark: #6DC527;
      --sdash-lime-soft: rgba(137,243,54,.12);
      --sdash-lime-text: #1a2e05;
      --sdash-surface: #fff;
      --sdash-border: #e2e8f0;
      --sdash-muted: #64748b;
      --sdash-shadow: 0 4px 18px rgba(15,23,42,.06);
    }

    /* ════════════════════════════════════════
       HERO GREETING — minimal, sits on canvas
    ════════════════════════════════════════ */
    /* Kill any inherited banner chrome from legacy rules */
    body.student-dashboard-page.student-package-page .sdash-hero {
      background: transparent !important;
      border: none !important;
      box-shadow: none !important;
      padding: 0 !important;
      display: block !important;
      min-height: 0 !important;
    }
    .sdash-hero__title {
      margin: 0 !important;
      color: #1e293b !important;
      font-size: 2.25rem !important;
      font-weight: 700 !important;
      letter-spacing: -0.025em !important;
      line-height: 1.15 !important;
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .sdash-hero__wave {
      display: inline-block;
      transform-origin: 70% 70%;
      animation: sdash-wave 2.4s ease-in-out 1;
    }
    @keyframes sdash-wave {
      0%,60%,100% { transform: rotate(0deg); }
      10%,30% { transform: rotate(14deg); }
      20% { transform: rotate(-8deg); }
      40% { transform: rotate(10deg); }
      50% { transform: rotate(-4deg); }
    }
    .sdash-hero__sub {
      margin: 4px 0 0 !important;
      color: #64748b !important;
      font-size: 1rem !important;
      font-weight: 400 !important;
    }
    .sdash-hero__date {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      margin-top: 14px;
      padding: 6px 14px;
      background: #fff;
      border: 1px solid #e2e8f0;
      border-radius: 9999px;
      font-size: .82rem;
      font-weight: 600;
      color: #475569;
      box-shadow: 0 1px 2px rgba(15,23,42,.04);
    }
    .sdash-hero__date svg { width: 15px; height: 15px; color: var(--sdash-lime-dark); }
    html[data-theme="dark"] .sdash-hero__title { color: #e2e8f0 !important; }
    html[data-theme="dark"] .sdash-hero__sub { color: #94a3b8 !important; }
    html[data-theme="dark"] .sdash-hero__date {
      background: #1e2533; border-color: rgba(255,255,255,.08); color: #cbd5e1;
    }

    /* ════════════════════════════════════════
       STAT CARDS — override old bootstrap colors
    ════════════════════════════════════════ */
    .student-dashboard-clean__stats {
      display: grid !important;
      grid-template-columns: repeat(4, 1fr) !important;
      gap: 14px !important;
    }
    @media (max-width: 900px) {
      .student-dashboard-clean__stats { grid-template-columns: repeat(2, 1fr) !important; }
    }
    @media (max-width: 520px) {
      .student-dashboard-clean__stats { grid-template-columns: 1fr !important; }
    }
    .student-dashboard-clean__stat-card {
      background: #fff !important;
      border: 1px solid #e2e8f0 !important;
      border-top: 3px solid var(--sdash-lime) !important;
      border-radius: 14px !important;
      padding: 18px 18px 16px !important;
      box-shadow: 0 2px 8px rgba(15,23,42,.05) !important;
      transition: box-shadow .18s ease, transform .18s ease !important;
    }
    .student-dashboard-clean__stat-card:hover {
      box-shadow: 0 8px 24px rgba(137,243,54,.15) !important;
      transform: translateY(-2px) !important;
    }
    /* All icon wraps: lime soft bg */
    .student-dashboard-clean__stat-icon-wrap {
      background: var(--sdash-lime-soft) !important;
      box-shadow: none !important;
      color: var(--sdash-lime-dark) !important;
      border-radius: 10px !important;
      width: 40px !important; height: 40px !important;
    }
    .student-dashboard-clean__stat-icon-wrap svg {
      width: 20px !important; height: 20px !important;
      stroke: var(--sdash-lime-dark) !important;
    }
    /* Override all color-variant top borders to lime */
    .student-dashboard-clean__stat-card--teal,
    .student-dashboard-clean__stat-card--emerald,
    .student-dashboard-clean__stat-card--blue,
    .student-dashboard-clean__stat-card--violet {
      border-top-color: var(--sdash-lime) !important;
    }
    .student-dashboard-clean__stat-card--teal .student-dashboard-clean__stat-icon-wrap,
    .student-dashboard-clean__stat-card--emerald .student-dashboard-clean__stat-icon-wrap,
    .student-dashboard-clean__stat-card--blue .student-dashboard-clean__stat-icon-wrap,
    .student-dashboard-clean__stat-card--violet .student-dashboard-clean__stat-icon-wrap {
      background: var(--sdash-lime-soft) !important;
      box-shadow: none !important;
      color: var(--sdash-lime-dark) !important;
    }
    .student-dashboard-clean__stat-value {
      font-size: 26px !important;
      font-weight: 800 !important;
      color: #1e293b !important;
      margin: 10px 0 4px !important;
      letter-spacing: -.02em !important;
    }
    .student-dashboard-clean__stat-kicker {
      font-size: 12px !important;
      font-weight: 600 !important;
      color: #64748b !important;
    }
    .student-dashboard-clean__stat-desc {
      font-size: 11.5px !important;
      color: #94a3b8 !important;
      margin: 0 !important;
    }
    .student-dashboard-clean__stat-desc::before { display: none !important; }

    /* ════════════════════════════════════════
       UPCOMING SESSION — premium spotlight card
    ════════════════════════════════════════ */
    .sdash-upcoming {
      display: flex;
      flex-direction: column;
      gap: 0;
    }
    .sdash-upcoming__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 14px;
    }
    .sdash-upcoming__title {
      font-size: .7rem;
      font-weight: 700;
      letter-spacing: .07em;
      text-transform: uppercase;
      color: #64748b;
    }
    .sdash-upcoming__browse {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      font-size: .75rem;
      font-weight: 600;
      color: var(--sdash-lime-dark);
      text-decoration: none;
      padding: 4px 10px;
      border: 1px solid var(--sdash-lime-soft);
      border-radius: 20px;
      background: var(--sdash-lime-soft);
      transition: background .14s, border-color .14s;
    }
    .sdash-upcoming__browse:hover {
      background: rgba(137,243,54,.22);
      border-color: var(--sdash-lime);
    }

    /* Empty state */
    .sdash-upcoming__empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      padding: 36px 20px;
      background: transparent;
      border: 1.5px dashed var(--sdash-border);
      border-radius: 16px;
      text-align: center;
    }
    .sdash-upcoming__empty-icon {
      width: 52px; height: 52px;
      border-radius: 50%;
      background: var(--sdash-lime-soft);
      display: flex; align-items: center; justify-content: center;
    }
    .sdash-upcoming__empty-icon svg { width: 26px; height: 26px; color: var(--sdash-lime-dark); }
    .sdash-upcoming__empty h4 {
      font-size: 15px; font-weight: 700; color: #1e293b; margin: 0;
    }
    .sdash-upcoming__empty p { font-size: 13px; color: #64748b; margin: 0; max-width: 320px; }
    .sdash-upcoming__empty a {
      display: inline-block;
      background: var(--sdash-lime);
      color: var(--sdash-lime-text);
      font-size: 13px;
      font-weight: 700;
      padding: 9px 22px;
      border-radius: 10px;
      text-decoration: none;
      transition: background .14s;
    }
    .sdash-upcoming__empty a:hover { background: var(--sdash-lime-dark); }

    /* Session spotlight card */
    .sdash-spot {
      background: #fff;
      border: 1px solid #e2e8f0;
      border-radius: 18px;
      overflow: hidden;
      box-shadow: 0 4px 20px rgba(15,23,42,.07);
      transition: box-shadow .2s;
    }
    .sdash-spot:hover { box-shadow: 0 8px 32px rgba(137,243,54,.18); }

    /* Gradient header bar */
    .sdash-spot__header {
      background: linear-gradient(135deg, #1a2e05 0%, #2d5008 50%, #3d6b0a 100%);
      padding: 20px 22px 18px;
      position: relative;
      overflow: hidden;
    }
    .sdash-spot__header::before {
      content: "";
      position: absolute;
      top: -30px; right: -30px;
      width: 120px; height: 120px;
      border-radius: 50%;
      background: rgba(137,243,54,.15);
    }
    .sdash-spot__header::after {
      content: "";
      position: absolute;
      bottom: -20px; right: 60px;
      width: 70px; height: 70px;
      border-radius: 50%;
      background: rgba(137,243,54,.08);
    }
    .sdash-spot__badge {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      background: var(--sdash-lime);
      color: var(--sdash-lime-text);
      font-size: 11px;
      font-weight: 700;
      letter-spacing: .04em;
      text-transform: uppercase;
      padding: 3px 10px;
      border-radius: 20px;
      margin-bottom: 10px;
    }
    .sdash-spot__badge--live {
      background: #ef4444;
      color: #fff;
    }
    .sdash-spot__session-title {
      font-size: 18px;
      font-weight: 800;
      color: #fff;
      margin: 0 0 6px;
      line-height: 1.3;
      position: relative; z-index: 1;
    }
    .sdash-spot__instructor {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 13px;
      color: rgba(255,255,255,.65);
      position: relative; z-index: 1;
    }
    .sdash-spot__instructor svg { width: 14px; height: 14px; }

    /* Body */
    .sdash-spot__body { padding: 18px 22px; }

    .sdash-spot__meta-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;
      margin-bottom: 16px;
    }
    @media (max-width: 600px) {
      .sdash-spot__meta-grid { grid-template-columns: repeat(2, 1fr); }
    }
    .sdash-spot__meta-item {
      display: flex;
      flex-direction: column;
      gap: 3px;
    }
    .sdash-spot__meta-label {
      font-size: 10.5px;
      font-weight: 600;
      letter-spacing: .05em;
      text-transform: uppercase;
      color: #94a3b8;
    }
    .sdash-spot__meta-value {
      font-size: 14px;
      font-weight: 700;
      color: #1e293b;
      display: flex;
      align-items: center;
      gap: 5px;
    }
    .sdash-spot__meta-value svg { width: 15px; height: 15px; color: var(--sdash-lime-dark); }

    /* Countdown inside card */
    .sdash-spot__countdown {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 12px 16px;
      margin-bottom: 16px;
      display: flex;
      align-items: center;
      gap: 12px;
    }
    .sdash-spot__cd-label {
      font-size: 11px;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: .06em;
      color: #64748b;
      white-space: nowrap;
      flex-shrink: 0;
    }
    .sdash-spot__cd-units {
      display: flex;
      gap: 8px;
      flex: 1;
      align-items: center;
    }
    .sdash-spot__cd-unit {
      display: flex;
      flex-direction: column;
      align-items: center;
      background: #fff;
      border: 1px solid #e2e8f0;
      border-radius: 8px;
      padding: 6px 10px;
      min-width: 46px;
    }
    .sdash-spot__cd-val {
      font-size: 1.25rem;
      font-weight: 800;
      color: var(--sdash-lime-dark);
      line-height: 1;
      font-variant-numeric: tabular-nums;
    }
    .sdash-spot__cd-name {
      font-size: .58rem;
      font-weight: 600;
      color: #94a3b8;
      text-transform: uppercase;
      letter-spacing: .05em;
      margin-top: 2px;
    }
    .sdash-spot__cd-sep {
      font-size: 1.1rem;
      font-weight: 700;
      color: #cbd5e1;
      flex-shrink: 0;
    }
    /* Ended state */
    .sdash-spot__countdown--ended .sdash-spot__cd-val { color: #f59e0b; }
    .sdash-spot__countdown--ended-msg {
      font-size: .85rem;
      font-weight: 600;
      color: #f59e0b;
    }

    /* Footer CTA */
    .sdash-spot__footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
    }
    .sdash-spot__action {
      display: inline-flex;
      align-items: center;
      gap: 7px;
      padding: 10px 22px;
      border-radius: 11px;
      font-size: 14px;
      font-weight: 700;
      text-decoration: none;
      border: none;
      cursor: pointer;
      transition: background .15s, transform .1s, box-shadow .15s;
    }
    .sdash-spot__action--primary {
      background: var(--sdash-lime);
      color: var(--sdash-lime-text);
    }
    .sdash-spot__action--primary:hover {
      background: var(--sdash-lime-dark);
      transform: translateY(-1px);
      box-shadow: 0 6px 18px rgba(137,243,54,.35);
    }
    .sdash-spot__action--zoom {
      background: linear-gradient(135deg, #2563eb, #1d4ed8);
      color: #fff;
    }
    .sdash-spot__action--zoom:hover {
      transform: translateY(-1px);
      box-shadow: 0 6px 18px rgba(37,99,235,.35);
    }
    .sdash-spot__action--ghost {
      background: transparent;
      color: #64748b;
      border: 1px solid #e2e8f0;
    }
    .sdash-spot__action--ghost:hover {
      background: #f1f5f9;
      color: #1e293b;
    }
    .sdash-spot__action svg { width: 17px; height: 17px; }

    /* ── Widget row container ── */
    .sdash-widgets-row {
      display: grid;
      grid-template-columns: 260px 1fr;
      gap: 18px;
      margin-bottom: 20px;
    }
    @media (max-width: 860px) {
      .sdash-widgets-row { grid-template-columns: 1fr; }
    }

    /* ════════════════════════════
       1. PROGRESS RING
    ════════════════════════════ */
    .sdash-ring-card {
      background: var(--sdash-surface);
      border: 1px solid var(--sdash-border);
      border-radius: 16px;
      box-shadow: var(--sdash-shadow);
      padding: 22px 20px 18px;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 16px;
    }
    .sdash-ring-card__title {
      font-size: .75rem;
      font-weight: 600;
      letter-spacing: .06em;
      text-transform: uppercase;
      color: var(--sdash-muted);
      align-self: flex-start;
    }
    .sdash-ring-wrap {
      position: relative;
      width: 150px;
      height: 150px;
      flex-shrink: 0;
    }
    .sdash-ring-center {
      position: absolute;
      inset: 0;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      pointer-events: none;
    }
    .sdash-ring-pct {
      font-size: 1.85rem;
      font-weight: 800;
      color: #1e293b;
      line-height: 1;
    }
    .sdash-ring-sub {
      font-size: .67rem;
      font-weight: 500;
      color: var(--sdash-muted);
      letter-spacing: .04em;
      margin-top: 2px;
    }
    .sdash-ring-legend {
      width: 100%;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .sdash-ring-legend__item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: .78rem;
    }
    .sdash-ring-legend__dot {
      width: 8px; height: 8px;
      border-radius: 50%;
      flex-shrink: 0;
    }
    .sdash-ring-legend__dot--lime  { background: var(--sdash-lime); }
    .sdash-ring-legend__dot--gray  { background: #e2e8f0; }
    .sdash-ring-legend__label { color: var(--sdash-muted); }
    .sdash-ring-legend__val   { margin-left: auto; font-weight: 700; color: #1e293b; }

    /* ════════════════════════════
       2. ACTIVITY HEATMAP
    ════════════════════════════ */
    .sdash-heatmap-card {
      background: var(--sdash-surface);
      border: 1px solid var(--sdash-border);
      border-radius: 16px;
      box-shadow: var(--sdash-shadow);
      padding: 22px 22px 18px;
    }
    .sdash-heatmap-card__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 14px;
    }
    .sdash-heatmap-card__title {
      font-size: .75rem;
      font-weight: 600;
      letter-spacing: .06em;
      text-transform: uppercase;
      color: var(--sdash-muted);
    }
    .sdash-heatmap-card__count {
      font-size: .78rem;
      font-weight: 600;
      color: var(--sdash-lime-dark);
      background: var(--sdash-lime-soft);
      padding: 2px 10px;
      border-radius: 20px;
    }
    .sdash-heatmap__month-labels {
      display: grid;
      grid-template-columns: repeat(13, 1fr);
      margin-bottom: 4px;
      padding-left: 24px;
    }
    .sdash-heatmap__month-lbl {
      font-size: .65rem;
      color: var(--sdash-muted);
      font-weight: 500;
    }
    .sdash-heatmap__body {
      display: flex;
      gap: 6px;
      align-items: flex-start;
    }
    .sdash-heatmap__day-labels {
      display: flex;
      flex-direction: column;
      gap: 3px;
      padding-top: 2px;
    }
    .sdash-heatmap__day-lbl {
      height: 13px;
      font-size: .62rem;
      color: var(--sdash-muted);
      font-weight: 500;
      line-height: 13px;
    }
    .sdash-heatmap__day-lbl.sdash-heatmap__day-lbl--hidden { visibility: hidden; }
    .sdash-heatmap__grid {
      display: flex;
      gap: 3px;
    }
    .sdash-heatmap__week {
      display: flex;
      flex-direction: column;
      gap: 3px;
    }
    .sdash-heatmap__cell {
      width: 13px; height: 13px;
      border-radius: 3px;
      background: #f1f5f9;
      cursor: default;
      transition: transform .1s;
    }
    .sdash-heatmap__cell:hover { transform: scale(1.35); }
    .sdash-heatmap__cell[data-level="1"] { background: #d4faa0; }
    .sdash-heatmap__cell[data-level="2"] { background: #aef04d; }
    .sdash-heatmap__cell[data-level="3"] { background: var(--sdash-lime); }
    .sdash-heatmap__cell[data-level="4"] { background: var(--sdash-lime-dark); }
    .sdash-heatmap__legend-row {
      display: flex;
      align-items: center;
      gap: 4px;
      margin-top: 10px;
      justify-content: flex-end;
    }
    .sdash-heatmap__legend-lbl { font-size: .65rem; color: var(--sdash-muted); }
    .sdash-heatmap__legend-cell {
      width: 11px; height: 11px;
      border-radius: 2px;
    }

    /* ════════════════════════════════════════
       DASHBOARD GRID — correct section order
       hero → stats → upcoming → actions (widgets row removed)
    ════════════════════════════════════════ */
    body.student-dashboard-page.student-package-page .student-dashboard-clean {
      grid-template-areas:
        "hero"
        "stats"
        "upcoming"
        "actions" !important;
    }
    .sdash-upcoming { grid-area: upcoming; }
    body.student-dashboard-page.student-package-page .student-dashboard-clean__actions {
      grid-area: actions !important;
    }
    /* Hide widgets row completely if still in DOM */
    .sdash-widgets-row { display: none !important; }

    /* ════════════════════════════════════════
       UPCOMING SESSION — compact SaaS panel
       Flat 1px-border card, inline split-text
       live timer + ultra-thin progress track
    ════════════════════════════════════════ */
    .sdash-upcoming {
      background: #ffffff;
      border: 1px solid var(--sdash-border);
      border-radius: 16px;
      box-shadow: 0 1px 2px rgba(15,23,42,.04);
      overflow: hidden;
      padding: 16px 18px 18px;
    }
    .sdash-upcoming__head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0;
      height: auto;
      overflow: visible;
      margin-bottom: 14px;
    }

    /* ── Flat compact panel (no gradient block) ── */
    .sdash-spot {
      background: transparent;
      border: none;
      border-radius: 0;
      box-shadow: none;
      overflow: visible;
      display: flex;
      flex-direction: column;
      gap: 14px;
    }

    /* status pill with pulsing dot */
    .sdash-spot__top { display: flex; align-items: center; }
    .sdash-spot__pill {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: var(--sdash-lime-soft);
      border: 1px solid rgba(109,197,39,.3);
      color: var(--sdash-lime-dark);
      border-radius: 999px;
      font-size: 10px;
      font-weight: 700;
      letter-spacing: .08em;
      text-transform: uppercase;
      padding: 4px 11px 4px 9px;
    }
    .sdash-spot__pill-dot {
      width: 7px; height: 7px;
      border-radius: 50%;
      background: var(--sdash-lime-dark);
      animation: sdash-pill-pulse 2s ease-out infinite;
    }
    @keyframes sdash-pill-pulse {
      0%   { box-shadow: 0 0 0 0 rgba(109,197,39,.45); }
      70%  { box-shadow: 0 0 0 6px rgba(109,197,39,0); }
      100% { box-shadow: 0 0 0 0 rgba(109,197,39,0); }
    }
    .sdash-spot__pill--live {
      background: rgba(239,68,68,.1);
      border-color: rgba(239,68,68,.35);
      color: #dc2626;
    }
    .sdash-spot__pill--live .sdash-spot__pill-dot {
      background: #ef4444;
      animation: sdash-pill-pulse-live 1.4s ease-out infinite;
    }
    @keyframes sdash-pill-pulse-live {
      0%   { box-shadow: 0 0 0 0 rgba(239,68,68,.5); }
      70%  { box-shadow: 0 0 0 7px rgba(239,68,68,0); }
      100% { box-shadow: 0 0 0 0 rgba(239,68,68,0); }
    }

    /* identity: title + instructor + dot-separated facts */
    .sdash-spot__id { display: flex; flex-direction: column; gap: 6px; }
    .sdash-spot__session-title {
      color: #1e293b;
      font-size: 1.25rem;
      font-weight: 700;
      letter-spacing: -.02em;
      line-height: 1.25;
      margin: 0;
    }
    .sdash-spot__instructor {
      display: flex;
      align-items: center;
      gap: 6px;
      color: #64748b;
      font-size: 13px;
      font-weight: 500;
      margin: 0;
    }
    .sdash-spot__instructor svg { width: 14px; height: 14px; color: #94a3b8; flex-shrink: 0; }
    .sdash-spot__facts {
      list-style: none;
      margin: 4px 0 0;
      padding: 0;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 6px 14px;
    }
    .sdash-spot__fact {
      display: inline-flex;
      align-items: center;
      gap: 5px;
      font-size: 12.5px;
      font-weight: 600;
      color: #475569;
      position: relative;
    }
    .sdash-spot__fact:not(:last-child)::after {
      content: "";
      position: absolute;
      right: -8px;
      width: 3px; height: 3px;
      border-radius: 50%;
      background: #cbd5e1;
    }
    .sdash-spot__fact svg { width: 14px; height: 14px; color: var(--sdash-lime-dark); flex-shrink: 0; }

    /* ── Inline split-text countdown + progress track ── */
    .sdash-spot__countdown {
      background: #f8fafc;
      border: 1px solid var(--sdash-border);
      border-radius: 12px;
      padding: 12px 14px 13px;
      display: flex;
      flex-direction: column;
      align-items: stretch;
      gap: 10px;
      margin: 0;
    }
    .sdash-spot__cd-label {
      font-size: 10px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: .1em;
      color: #94a3b8;
    }
    .sdash-spot__cd-row {
      display: flex;
      align-items: baseline;
      gap: 2px;
    }
    .sdash-spot__cd-unit {
      display: inline-flex;
      align-items: baseline;
      gap: 2px;
    }
    .sdash-spot__cd-val {
      font-size: 1.5rem;
      font-weight: 800;
      color: #1e293b;
      line-height: 1;
      letter-spacing: -.03em;
      font-variant-numeric: tabular-nums;
      font-feature-settings: "tnum";
      min-width: 1.5ch;
      text-align: right;
    }
    .sdash-spot__cd-name {
      font-size: .62rem;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: .04em;
      color: #94a3b8;
    }
    .sdash-spot__cd-sep {
      font-size: 1.2rem;
      font-weight: 700;
      font-style: normal;
      color: #cbd5e1;
      line-height: 1;
      padding: 0 5px;
    }
    .sdash-spot__cd-val--tick { animation: sdash-cd-tick .4s ease; }
    @keyframes sdash-cd-tick {
      0%   { transform: translateY(-2px); opacity: .35; }
      100% { transform: translateY(0); opacity: 1; }
    }
    /* ultra-thin animated gradient track */
    .sdash-spot__cd-track {
      position: relative;
      height: 3px;
      border-radius: 999px;
      background: #e2e8f0;
      overflow: hidden;
    }
    .sdash-spot__cd-fill {
      position: absolute;
      inset: 0 auto 0 0;
      width: 0%;
      border-radius: 999px;
      background: linear-gradient(90deg, var(--sdash-lime-dark), var(--sdash-lime), var(--sdash-lime-dark));
      background-size: 200% 100%;
      transition: width .6s cubic-bezier(.4,0,.2,1);
      animation: sdash-cd-sheen 2.4s linear infinite;
    }
    @keyframes sdash-cd-sheen {
      0%   { background-position: 0% 50%; }
      100% { background-position: 200% 50%; }
    }
    /* ended state */
    .sdash-spot__countdown--ended { border-color: #fde68a; background: #fffbeb; }
    .sdash-spot__countdown--ended .sdash-spot__cd-fill { animation: none; width: 100% !important; background: #f59e0b; }
    .sdash-spot__countdown--ended-msg { font-size: 14px; font-weight: 700; color: #f59e0b; }

    /* ── Action buttons ── */
    .sdash-spot__footer {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }
    .sdash-spot__action {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      padding: 9px 16px;
      border-radius: 10px;
      font-size: 13.5px;
      font-weight: 600;
      text-decoration: none;
      cursor: pointer;
      transition: background .15s ease, transform .15s ease, box-shadow .15s ease;
      border: 1px solid transparent;
    }
    .sdash-spot__action--primary {
      background: var(--sdash-lime);
      color: var(--sdash-lime-text);
      border-color: transparent;
    }
    .sdash-spot__action--primary:hover { background: var(--sdash-lime-dark); transform: translateY(-1px); box-shadow: 0 4px 12px rgba(109,197,39,.3); }
    .sdash-spot__action--ghost {
      background: #ffffff;
      color: #475569;
      border-color: var(--sdash-border);
    }
    .sdash-spot__action--ghost:hover { background: #f8fafc; color: #1e293b; }
    .sdash-spot__action--zoom {
      background: rgba(137,243,54,.1);
      color: #4a7c1a;
      border-color: rgba(109,197,39,.3);
    }
    .sdash-spot__action--zoom:hover { background: rgba(137,243,54,.18); }
    .sdash-spot__action svg { width: 16px; height: 16px; }

    /* ── Empty state ── */
    .sdash-upcoming__empty {
      padding: 48px 28px;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      text-align: center;
    }
    .sdash-upcoming__empty-icon {
      width: 56px; height: 56px;
      background: var(--sdash-lime-soft);
      border-radius: 16px;
      display: flex; align-items: center; justify-content: center;
    }
    .sdash-upcoming__empty-icon svg { width: 26px; height: 26px; color: var(--sdash-lime-dark); }
    .sdash-upcoming__empty h4 { font-size: 16px; font-weight: 700; color: #1e293b; margin: 0; }
    .sdash-upcoming__empty p { font-size: 13px; color: #64748b; margin: 0; max-width: 320px; }
    .sdash-upcoming__empty a {
      display: inline-flex; align-items: center; gap: 8px;
      margin-top: 4px;
      padding: 10px 20px; border-radius: 10px;
      background: #0f172a; color: #f8fafc;
      font-size: 13.5px; font-weight: 600; letter-spacing: -.01em;
      text-decoration: none;
      border: 1px solid #0f172a;
      box-shadow: 0 1px 2px rgba(15,23,42,.12);
      transition: background .15s ease, border-color .15s ease, transform .15s ease, box-shadow .15s ease;
    }
    .sdash-upcoming__empty a svg { width: 16px; height: 16px; }
    .sdash-upcoming__empty a:hover {
      background: #1e293b; border-color: #1e293b;
      transform: translateY(-1px);
      box-shadow: 0 6px 16px rgba(15,23,42,.18);
    }
    .sdash-upcoming__empty a:focus-visible { outline: 2px solid var(--sdash-lime-dark); outline-offset: 2px; }

    /* ── Quick Action cards: horizontal 3-column row ── */
    body.student-dashboard-page.student-package-page .student-dashboard-clean__actions {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 16px;
    }
    @media (max-width: 680px) {
      body.student-dashboard-page.student-package-page .student-dashboard-clean__actions {
        grid-template-columns: 1fr;
      }
      .sdash-spot__cd-val { font-size: 1.35rem; }
      .sdash-spot__cd-sep { padding: 0 3px; }
      .sdash-spot__footer { flex-direction: column; align-items: stretch; }
      .sdash-spot__action { width: 100%; }
    }

    /* ── Dark mode ──
       Align the Upcoming Session card with the dark stat cards
       (--theme-surface #1e293b / --theme-border #334155) instead of
       inheriting the light-theme white background. */
    html[data-theme="dark"] .sdash-upcoming {
      background: var(--theme-surface, #1e293b) !important;
      border-color: var(--theme-border, #334155) !important;
      box-shadow: 0 8px 24px rgba(0,0,0,.35);
    }
    html[data-theme="dark"] .sdash-upcoming__title { color: #94a3b8; }
    html[data-theme="dark"] .sdash-upcoming__browse {
      background: rgba(137,243,54,.12);
      border-color: rgba(137,243,54,.28);
      color: #86efac;
    }
    html[data-theme="dark"] .sdash-upcoming__browse:hover {
      background: rgba(137,243,54,.2);
      border-color: rgba(137,243,54,.4);
      color: #bbf7d0;
    }
    html[data-theme="dark"] .sdash-upcoming__empty {
      background: rgba(15,23,42,.35);
      border-color: rgba(255,255,255,.12);
    }
    html[data-theme="dark"] .sdash-spot__session-title { color: #f8fafc; }
    html[data-theme="dark"] .sdash-spot__instructor { color: #94a3b8; }
    html[data-theme="dark"] .sdash-spot__instructor svg { color: #64748b; }
    html[data-theme="dark"] .sdash-spot__fact { color: #cbd5e1; }
    html[data-theme="dark"] .sdash-spot__fact:not(:last-child)::after { background: #475569; }
    html[data-theme="dark"] .sdash-spot__countdown { background: #0f172a; border-color: rgba(255,255,255,.08); }
    html[data-theme="dark"] .sdash-spot__cd-unit { background: #1e293b; border-color: rgba(255,255,255,.1); }
    html[data-theme="dark"] .sdash-spot__cd-val { color: #f8fafc; }
    html[data-theme="dark"] .sdash-spot__cd-name,
    html[data-theme="dark"] .sdash-spot__cd-label { color: #94a3b8; }
    html[data-theme="dark"] .sdash-spot__cd-sep { color: #475569; }
    html[data-theme="dark"] .sdash-spot__cd-track { background: rgba(255,255,255,.1); }
    html[data-theme="dark"] .sdash-spot__countdown--ended { background: rgba(245,158,11,.12); border-color: rgba(251,191,36,.3); }
    html[data-theme="dark"] .sdash-spot__countdown--ended .sdash-spot__cd-unit { background: rgba(245,158,11,.15); border-color: rgba(251,191,36,.25); }
    html[data-theme="dark"] .sdash-spot__action--ghost { background: #1e2d3d; border-color: rgba(255,255,255,.12); color: #e2e8f0; }
    html[data-theme="dark"] .sdash-spot__action--ghost:hover { background: #243449; color: #f8fafc; }
    /* Empty state: dark surface + legible contrast */
    html[data-theme="dark"] .sdash-upcoming__empty-icon { background: rgba(137,243,54,.14); }
    html[data-theme="dark"] .sdash-upcoming__empty h4 { color: #f8fafc; }
    html[data-theme="dark"] .sdash-upcoming__empty p { color: #94a3b8; }
    html[data-theme="dark"] .sdash-upcoming__empty a {
      background: var(--sdash-lime);
      color: var(--sdash-lime-text);
      border-color: var(--sdash-lime);
      box-shadow: none;
    }
    html[data-theme="dark"] .sdash-upcoming__empty a:hover {
      background: var(--sdash-lime-dark);
      border-color: var(--sdash-lime-dark);
      color: var(--sdash-lime-text);
      box-shadow: 0 6px 16px rgba(0,0,0,.35);
    }

    /* ════════════════════════════════════════
       SESSION EXPIRY / IDLE TIMEOUT — modern status toast
       State-driven accent: emerald (healthy) → amber → crimson.
       All colors flow from CSS custom properties set per state.
    ════════════════════════════════════════ */
    .setimer {
      --se-accent: #10b981;
      --se-accent-deep: #059669;
      --se-accent-2: #34d399;
      --se-text-accent: #047857;
      --se-glow: rgba(16,185,129,.30);
      --se-track: rgba(16,185,129,.14);

      position: fixed;
      right: 20px; bottom: 20px;
      z-index: 1200;
      width: 328px; max-width: calc(100vw - 32px);
      padding: 16px 16px 14px;
      background: rgba(255,255,255,.86);
      -webkit-backdrop-filter: blur(14px) saturate(160%);
      backdrop-filter: blur(14px) saturate(160%);
      border: 1px solid rgba(255,255,255,.6);
      border-radius: 18px;
      box-shadow:
        0 1px 0 rgba(255,255,255,.7) inset,
        0 18px 48px -12px var(--se-glow),
        0 12px 32px rgba(15,23,42,.18);
      overflow: hidden;
      opacity: 0;
      transform: translateY(14px) scale(.96);
      transition: opacity .28s ease, transform .35s cubic-bezier(.22,1,.36,1), box-shadow .35s ease;
    }
    /* soft accent aura glowing from the corner */
    .setimer::before {
      content: "";
      position: absolute;
      top: -45px; right: -35px;
      width: 140px; height: 140px;
      border-radius: 50%;
      background: radial-gradient(circle, var(--se-glow), transparent 70%);
      pointer-events: none;
      transition: background .3s ease;
    }
    .setimer.setimer--in { opacity: 1; transform: translateY(0) scale(1); }
    [dir="rtl"] .setimer { right: auto; left: 20px; }

    .setimer__main {
      position: relative;
      display: flex; align-items: center; gap: 12px;
      margin-bottom: 13px;
    }
    .setimer__icon {
      flex-shrink: 0;
      width: 42px; height: 42px; border-radius: 13px;
      display: flex; align-items: center; justify-content: center;
      color: #fff;
      background: linear-gradient(135deg, var(--se-accent-2), var(--se-accent-deep));
      box-shadow: 0 6px 16px -4px var(--se-glow);
      transition: background .3s ease, box-shadow .3s ease;
    }
    .setimer__icon svg { width: 22px; height: 22px; }
    .setimer__text { min-width: 0; flex: 1; }
    .setimer__kicker {
      display: inline-flex; align-items: center; gap: 6px;
      font-size: 10px; font-weight: 800; letter-spacing: .09em; text-transform: uppercase;
      color: var(--se-text-accent);
      transition: color .3s ease;
    }
    .setimer__dot {
      width: 6px; height: 6px; border-radius: 50%;
      background: var(--se-accent);
      animation: se-dot 1.8s ease-out infinite;
    }
    @keyframes se-dot {
      0%   { box-shadow: 0 0 0 0 var(--se-glow); }
      70%  { box-shadow: 0 0 0 7px transparent; }
      100% { box-shadow: 0 0 0 0 transparent; }
    }
    .setimer__title {
      margin: 3px 0 0; font-size: 13.5px; font-weight: 700; color: #1e293b; letter-spacing: -.01em; line-height: 1.3;
    }

    /* hero monospace clock — tabular nums prevent layout shift on each tick */
    .setimer__countdown {
      position: relative;
      display: flex; align-items: baseline; gap: 8px;
      margin-bottom: 12px;
    }
    .setimer__clock {
      font-family: ui-monospace, "SF Mono", "JetBrains Mono", "Roboto Mono", Menlo, Consolas, monospace;
      font-size: 2.15rem; font-weight: 800; line-height: 1;
      letter-spacing: .01em;
      font-variant-numeric: tabular-nums; font-feature-settings: "tnum" 1;
      color: var(--se-text-accent);
      transition: color .3s ease;
    }
    .setimer__caption { font-size: 11.5px; font-weight: 600; color: #94a3b8; }

    /* gradient progress track with animated sheen */
    .setimer__bar {
      position: relative;
      height: 6px; border-radius: 999px;
      background: var(--se-track);
      overflow: hidden;
      margin-bottom: 14px;
      transition: background .3s ease;
    }
    .setimer__bar-fill {
      position: absolute; inset: 0 0 0 0;
      width: 100%;
      border-radius: 999px;
      background: linear-gradient(90deg, var(--se-accent-deep), var(--se-accent-2), var(--se-accent-deep));
      background-size: 200% 100%;
      animation: se-sheen 2.2s linear infinite;
      transition: width 1s linear;
    }
    @keyframes se-sheen { 0% { background-position: 0% 0; } 100% { background-position: 200% 0; } }

    .setimer__actions { display: flex; gap: 8px; }
    .setimer__btn {
      flex: 1;
      display: inline-flex; align-items: center; justify-content: center;
      height: 36px; padding: 0 14px;
      border-radius: 10px;
      font-size: 13px; font-weight: 700; letter-spacing: -.01em;
      cursor: pointer;
      border: 1px solid transparent;
      transition: transform .12s ease, box-shadow .18s ease, background .18s ease, color .18s ease, border-color .18s ease;
    }
    .setimer__btn:active { transform: translateY(1px); }
    .setimer__btn--primary {
      color: #fff;
      background: linear-gradient(135deg, var(--se-accent), var(--se-accent-deep));
      box-shadow: 0 6px 16px -5px var(--se-glow);
    }
    .setimer__btn--primary:hover { transform: translateY(-1px); box-shadow: 0 10px 22px -6px var(--se-glow); }
    .setimer__btn--ghost { background: rgba(255,255,255,.5); color: #475569; border-color: var(--sdash-border); }
    .setimer__btn--ghost:hover { background: #fff; color: #1e293b; }
    .setimer__btn:focus-visible { outline: 2px solid var(--se-accent); outline-offset: 2px; }

    /* escalation — amber under 60s */
    .setimer--warn {
      --se-accent: #f59e0b; --se-accent-deep: #d97706; --se-accent-2: #fbbf24;
      --se-text-accent: #b45309; --se-glow: rgba(245,158,11,.34); --se-track: rgba(245,158,11,.16);
    }
    /* escalation — crimson under 20s */
    .setimer--danger {
      --se-accent: #ef4444; --se-accent-deep: #dc2626; --se-accent-2: #f87171;
      --se-text-accent: #dc2626; --se-glow: rgba(239,68,68,.42); --se-track: rgba(239,68,68,.16);
    }
    .setimer--danger .setimer__icon { animation: se-pulse 1.1s ease-in-out infinite; }
    .setimer--danger .setimer__clock { animation: se-blink 1s steps(1,end) infinite; }
    @keyframes se-pulse {
      0%,100% { box-shadow: 0 0 0 0 var(--se-glow); }
      50%     { box-shadow: 0 0 0 9px transparent; }
    }
    @keyframes se-blink { 0%,62% { opacity: 1; } 63%,100% { opacity: .5; } }
    @media (prefers-reduced-motion: reduce) {
      .setimer, .setimer__bar-fill, .setimer__icon, .setimer__dot, .setimer__clock { transition: none; animation: none; }
    }

    /* timer — dark mode (brighter accent text for legibility) */
    html[data-theme="dark"] .setimer {
      background: rgba(30,41,59,.82);
      border-color: rgba(255,255,255,.08);
      box-shadow:
        0 1px 0 rgba(255,255,255,.06) inset,
        0 18px 50px -12px var(--se-glow),
        0 16px 40px rgba(0,0,0,.55);
      --se-text-accent: #34d399;
    }
    html[data-theme="dark"] .setimer--warn { --se-text-accent: #fbbf24; }
    html[data-theme="dark"] .setimer--danger { --se-text-accent: #f87171; }
    html[data-theme="dark"] .setimer__title { color: #f8fafc; }
    html[data-theme="dark"] .setimer__caption { color: #94a3b8; }
    html[data-theme="dark"] .setimer__btn--ghost { background: rgba(255,255,255,.04); color: #cbd5e1; border-color: rgba(255,255,255,.12); }
    html[data-theme="dark"] .setimer__btn--ghost:hover { background: rgba(255,255,255,.09); color: #f8fafc; }
    </style>
</head>
<body class="student-premium-page student-package-page student-module-page student-dashboard-page">
<div class="app-shell">
    <%@ include file="/jsp/common/student_header.jspf" %>

    <div class="app-main">
        <div class="container sd-container student-workspace-shell">
            <div class="student-shell-layout">
                <%@ include file="/jsp/student/student_sidebar.jspf" %>

                <main class="student-shell-content" role="main">
                    <div class="student-workspace-view">
                    <%@ include file="/jsp/common/student_breadcrumb.jspf" %>
                    <div class="student-dashboard-clean">
                    <fmt:setLocale value="${jstlLocale}" />
                    <fmt:setBundle basename="messages" var="msg" />
                    <c:set var="greetingKey" value="student.dashboard.greeting.${timeOfDay}" />
                    <section class="student-dashboard-clean__hero sdash-hero" aria-label="Welcome">
                        <h1 id="dashGreetTitle" class="sdash-hero__title">
                            <fmt:message key="${greetingKey}" bundle="${msg}">
                                <fmt:param value="${heroFirstName}" />
                            </fmt:message>
                            <span class="sdash-hero__wave" aria-hidden="true">&#128075;</span>
                        </h1>
                        <p class="sdash-hero__sub">
                            <fmt:message key="student.dashboard.subtitle" bundle="${msg}" />
                        </p>
                        <span class="sdash-hero__date">
                            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.7"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
                            <%= text(heroDateLabel) %>
                        </span>
                    </section>

                    <section class="student-dashboard-clean__stats" aria-label="Summary statistics">
                        <article class="student-dashboard-clean__stat-card student-dashboard-clean__stat-card--teal">
                            <div class="student-dashboard-clean__stat-card-top">
                                <span class="student-dashboard-clean__stat-kicker" data-i18n="dashboard.totalSessions">Total Sessions</span>
                                <div class="student-dashboard-clean__stat-icon-wrap" aria-hidden="true">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"/><path d="M8 7h8M8 11h6" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/></svg>
                                </div>
                            </div>
                            <p class="student-dashboard-clean__stat-value"><%= enrollmentsCount %></p>
                            <p class="student-dashboard-clean__stat-desc"><%= text(totalDesc) %></p>
                        </article>
                        <article class="student-dashboard-clean__stat-card student-dashboard-clean__stat-card--emerald">
                            <div class="student-dashboard-clean__stat-card-top">
                                <span class="student-dashboard-clean__stat-kicker" data-i18n="dashboard.completed">Completed</span>
                                <div class="student-dashboard-clean__stat-icon-wrap" aria-hidden="true">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"/><path d="M22 4 12 14.01l-3-3" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                </div>
                            </div>
                            <p class="student-dashboard-clean__stat-value"><%= completedApprox %></p>
                            <p class="student-dashboard-clean__stat-desc"><%= text(completedDesc) %></p>
                        </article>
                        <article class="student-dashboard-clean__stat-card student-dashboard-clean__stat-card--blue">
                            <div class="student-dashboard-clean__stat-card-top">
                                <span class="student-dashboard-clean__stat-kicker" data-i18n="dashboard.averageScore">Average Score</span>
                                <div class="student-dashboard-clean__stat-icon-wrap" aria-hidden="true">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M23 6 13.5 15.5 8.5 10.5 1 18" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"/><path d="M17 6h6v6" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                </div>
                            </div>
                            <p class="student-dashboard-clean__stat-value"><%= completionInt %>%</p>
                            <p class="student-dashboard-clean__stat-desc"><%= text(avgScoreDesc) %></p>
                        </article>
                        <article class="student-dashboard-clean__stat-card student-dashboard-clean__stat-card--violet">
                            <div class="student-dashboard-clean__stat-card-top">
                                <span class="student-dashboard-clean__stat-kicker" data-i18n="dashboard.liveSessions">Live Sessions</span>
                                <div class="student-dashboard-clean__stat-icon-wrap" aria-hidden="true">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="m23 7-7 5 7 5V7Z" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"/><rect x="1" y="5" width="15" height="14" rx="2" stroke="currentColor" stroke-width="1.75"/></svg>
                                </div>
                            </div>
                            <p class="student-dashboard-clean__stat-value"><%= upcomingCount %></p>
                            <p class="student-dashboard-clean__stat-desc"><%= text(activeDesc) %></p>
                        </article>
                    </section>

                    <!-- ══ UPCOMING SESSION (Premium Spotlight Card) ══ -->
                    <div class="sdash-upcoming">
                        <div class="sdash-upcoming__head">
                            <span class="sdash-upcoming__title">Upcoming Session</span>
                            <a class="sdash-upcoming__browse" href="<%= ctx %>/student/available-sessions">
                                <svg width="13" height="13" viewBox="0 0 24 24" fill="none"><path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/></svg>
                                Browse Sessions
                            </a>
                        </div>

                        <% if (upcomingCards.isEmpty()) { %>
                        <div class="sdash-upcoming__empty">
                            <span class="sdash-upcoming__empty-icon" aria-hidden="true">
                                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"/></svg>
                            </span>
                            <h4>No upcoming session</h4>
                            <p>You have no scheduled sessions yet. Browse the catalogue to enrol and start your next recitation.</p>
                            <a href="<%= ctx %>/student/available-sessions">
                                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="1.9"/><path d="m20 20-3.2-3.2" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"/></svg>
                                Browse Available Sessions
                            </a>
                        </div>
                        <% } else {
                               Map<String, Object> card = featuredCard;
                               TasmiSession sessionCard = (TasmiSession) card.get("session");
                               Enrollment enrollmentCard = (Enrollment) card.get("enrollment");
                               Payment paymentCard = (Payment) card.get("payment");
                               boolean paymentRequired = Boolean.TRUE.equals(card.get("paymentRequired"));
                               boolean joinReady = Boolean.TRUE.equals(card.get("joinReady"));
                               String meetingLink = trimToNull(sessionCard.getResolvableParticipantJoinUrl());
                               boolean showMeetingLink = joinReady && meetingLink != null;
                               String instructorName = card.get("instructorName") == null ? "Instructor" : String.valueOf(card.get("instructorName"));
                               String sessionActionHref = ctx + "/student/enroll-confirm?sessionId=" + sessionCard.getSessionId() + "&from=dashboard";
                               String sessionActionText = "Enroll";
                               if (joinReady) {
                                   sessionActionHref = ctx + "/student/joinLive?sessionId=" + sessionCard.getSessionId();
                                   sessionActionText = "Join Live Session";
                               } else if (enrollmentCard != null && paymentRequired && (paymentCard == null || paymentCard.getPaymentStatus() != PaymentStatus.APPROVED)) {
                                   sessionActionHref = ctx + "/student/payments/qr?enrollmentId=" + enrollmentCard.getEnrollmentId();
                                   sessionActionText = paymentCard != null && paymentCard.getPaymentStatus() == PaymentStatus.REJECTED
                                           ? "Resubmit Receipt"
                                           : (paymentCard != null && paymentCard.getPaymentStatus() == PaymentStatus.AWAITING_VERIFICATION ? "View Payment Status" : "Pay with QR");
                               } else if (enrollmentCard != null && enrollmentCard.getEnrollmentStatus() == EnrollmentStatus.APPROVED) {
                                   sessionActionHref = ctx + "/student/enrollments";
                                   sessionActionText = "View My Sessions";
                               }
                               String spotBadgeLabel = showMeetingLink ? "Live Now" : (joinReady ? "Confirmed" : "Upcoming");
                               boolean isLiveNow = showMeetingLink;
                               String cardTitle = trimToNull(sessionCard.getTitle()) == null ? ("Session #" + sessionCard.getSessionId()) : sessionCard.getTitle();
                               String dateShown = sessionCard.getSessionDate() == null ? "—" : sessionCard.getSessionDate().format(dashboardDateFmt);
                               String timeShown = sessionCard.getSessionTime() == null ? "—" : sessionCard.getSessionTime().format(dashboardTimeFmt);
                               String joinHref = ctx + "/student/joinLive?sessionId=" + sessionCard.getSessionId();
                               Integer durMins = sessionCard.getDurationMinutes();
                               String durShown = durMins == null ? "—" : durMins + " min";
                        %>
                        <article class="sdash-spot" aria-label="<%= text(cardTitle) %>">
                            <div class="sdash-spot__top">
                                <span class="sdash-spot__pill <%= isLiveNow ? "sdash-spot__pill--live" : "" %>">
                                    <span class="sdash-spot__pill-dot" aria-hidden="true"></span>
                                    <%= spotBadgeLabel %>
                                </span>
                            </div>

                            <div class="sdash-spot__id">
                                <h2 class="sdash-spot__session-title"><%= text(cardTitle) %></h2>
                                <p class="sdash-spot__instructor">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M20 21a8 8 0 1 0-16 0" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/><circle cx="12" cy="7" r="4" stroke="currentColor" stroke-width="1.75"/></svg>
                                    <span>with <%= text(instructorName) %></span>
                                </p>
                                <ul class="sdash-spot__facts">
                                    <li class="sdash-spot__fact">
                                        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.6"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                                        <span><%= text(dateShown) %></span>
                                    </li>
                                    <li class="sdash-spot__fact">
                                        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.6"/><path d="M12 7v5l4 2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                                        <span><%= text(timeShown) %></span>
                                    </li>
                                    <li class="sdash-spot__fact">
                                        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M12 2C6.477 2 2 6.477 2 12s4.477 10 10 10 10-4.477 10-10S17.523 2 12 2Z" stroke="currentColor" stroke-width="1.6"/><path d="M12 8v4l3 3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                                        <span><%= text(durShown) %></span>
                                    </li>
                                </ul>
                            </div>

                            <% if (!countdownIso.isEmpty() && !isLiveNow) { %>
                            <div class="sdash-spot__countdown" id="sdash-spot-cd" aria-live="polite">
                                <span class="sdash-spot__cd-label">Starts in</span>
                                <div class="sdash-spot__cd-row">
                                    <span class="sdash-spot__cd-unit"><b class="sdash-spot__cd-val" id="sdash-cd-days">--</b><small class="sdash-spot__cd-name">days</small></span>
                                    <i class="sdash-spot__cd-sep" aria-hidden="true">:</i>
                                    <span class="sdash-spot__cd-unit"><b class="sdash-spot__cd-val" id="sdash-cd-hrs">--</b><small class="sdash-spot__cd-name">hrs</small></span>
                                    <i class="sdash-spot__cd-sep" aria-hidden="true">:</i>
                                    <span class="sdash-spot__cd-unit"><b class="sdash-spot__cd-val" id="sdash-cd-min">--</b><small class="sdash-spot__cd-name">min</small></span>
                                    <i class="sdash-spot__cd-sep" aria-hidden="true">:</i>
                                    <span class="sdash-spot__cd-unit"><b class="sdash-spot__cd-val" id="sdash-cd-sec">--</b><small class="sdash-spot__cd-name">sec</small></span>
                                </div>
                                <div class="sdash-spot__cd-track" aria-hidden="true"><span class="sdash-spot__cd-fill" id="sdash-cd-fill"></span></div>
                            </div>
                            <% } %>

                            <div class="sdash-spot__footer">
                                <% if (showMeetingLink) { %>
                                <a class="sdash-spot__action sdash-spot__action--zoom" href="<%= joinHref %>" target="_blank" rel="noopener noreferrer">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="m23 7-7 5 7 5V7Z" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round"/><rect x="1" y="5" width="15" height="14" rx="2" stroke="currentColor" stroke-width="1.65"/></svg>
                                    Join Zoom Now
                                </a>
                                <% } else { %>
                                <a class="sdash-spot__action sdash-spot__action--primary" href="<%= sessionActionHref %>">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M5 12h14M13 6l6 6-6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                    <%= sessionActionText %>
                                </a>
                                <% } %>
                            </div>
                        </article>
                        <% } %>
                    </div>
                    <!-- ══ END UPCOMING SESSION ══ -->

                    <section class="student-dashboard-clean__actions" aria-label="Quick actions">
                        <a class="student-dashboard-clean__action-card student-dashboard-clean__action-card--teal" href="<%= LocaleSupport.localizedUrl(request, "/student/available-sessions") %>">
                            <span class="student-dashboard-clean__action-icon student-dashboard-clean__action-icon--teal" aria-hidden="true">
                                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2Z" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"/></svg>
                            </span>
                            <span class="student-dashboard-clean__action-text">
                                <strong data-i18n="dashboard.browseSessions">Browse Sessions</strong>
                                <span data-i18n="dashboard.findNewSessions">Find new sessions</span>
                            </span>
                            <svg class="student-dashboard-clean__action-arrow etasmi-icon-flip-inline" width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                        </a>
                        <a class="student-dashboard-clean__action-card student-dashboard-clean__action-card--violet" href="<%= LocaleSupport.localizedUrl(request, "/student/recitations") %>">
                            <span class="student-dashboard-clean__action-icon student-dashboard-clean__action-icon--violet" aria-hidden="true">
                                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"/></svg>
                            </span>
                            <span class="student-dashboard-clean__action-text">
                                <strong data-i18n="dashboard.submitRecitation">Submit Recitation</strong>
                                <span data-i18n="dashboard.submitForReview">Submit for review</span>
                            </span>
                            <svg class="student-dashboard-clean__action-arrow etasmi-icon-flip-inline" width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                        </a>
                        <a class="student-dashboard-clean__action-card student-dashboard-clean__action-card--amber" href="<%= LocaleSupport.localizedUrl(request, "/student/progress") %>">
                            <span class="student-dashboard-clean__action-icon student-dashboard-clean__action-icon--amber" aria-hidden="true">
                                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M3 20h18" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/><path d="m3 16 5-5 4 3 8-8" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"/><path d="M15 6h5v5" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"/></svg>
                            </span>
                            <span class="student-dashboard-clean__action-text">
                                <strong data-i18n="dashboard.progressSnapshot">Progress Snapshot</strong>
                                <span data-i18n="dashboard.trackImprovement">Track improvement</span>
                            </span>
                            <svg class="student-dashboard-clean__action-arrow etasmi-icon-flip-inline" width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                        </a>
                    </section>

                    </div>
                    </div>
                    <%@ include file="/jsp/common/app_footer.jspf" %>
                </main>
            </div>
        </div>
    </div>
</div>

<!-- ══ SESSION EXPIRY / IDLE TIMEOUT WARNING ══
     data-idle     : total idle seconds allowed before sign-out
     data-duration : length of the warning countdown (seconds)
     data-logout-url    : where to send the user on expiry
     data-keepalive-url : optional endpoint pinged on "Extend session" -->
<div class="setimer" id="se-timer" hidden role="alertdialog" aria-modal="false"
     aria-labelledby="se-timer-title" aria-describedby="se-timer-sub"
     data-idle="1500" data-duration="120"
     data-logout-url="<%= ctx %>/login?expired=1">
    <div class="setimer__main">
        <span class="setimer__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.9"/><path d="M12 7.5v5l3 2" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </span>
        <div class="setimer__text">
            <span class="setimer__kicker"><span class="setimer__dot" aria-hidden="true"></span> Session expiring</span>
            <p class="setimer__title" id="se-timer-title">You&rsquo;ll be signed out soon</p>
        </div>
    </div>
    <div class="setimer__countdown">
        <time class="setimer__clock" id="se-timer-clock">02:00</time>
        <span class="setimer__caption" id="se-timer-sub">until automatic sign-out</span>
    </div>
    <div class="setimer__bar" aria-hidden="true"><span class="setimer__bar-fill" id="se-timer-fill"></span></div>
    <div class="setimer__actions">
        <button class="setimer__btn setimer__btn--ghost" id="se-timer-dismiss" type="button">Dismiss</button>
        <button class="setimer__btn setimer__btn--primary" id="se-timer-extend" type="button">Extend session</button>
    </div>
</div>

<script>
(function () {
  "use strict";

  /* ════════════════════════════════════════════════
     UPCOMING SESSION COUNTDOWN TIMER
  ════════════════════════════════════════════════ */
  (function initCountdown() {
    const isoStr = "<%= countdownIso %>";
    if (!isoStr) return;
    const strip  = document.getElementById("sdash-spot-cd");
    const elDays = document.getElementById("sdash-cd-days");
    const elHrs  = document.getElementById("sdash-cd-hrs");
    const elMin  = document.getElementById("sdash-cd-min");
    const elSec  = document.getElementById("sdash-cd-sec");
    const elFill = document.getElementById("sdash-cd-fill");
    if (!elDays) return;   /* countdown section only rendered when !isLiveNow */

    const target = new Date(isoStr).getTime();
    /* Progress reference: fill the track over the final stretch toward the
       session, clamped to a 7-day window so a far-off session reads as ~empty
       and a near one visibly fills as it approaches. */
    const MAX_WINDOW = 7 * 86400 * 1000;
    const windowMs = Math.max(1, Math.min(target - Date.now(), MAX_WINDOW));
    function pad(n) { return String(n).padStart(2, "0"); }

    let lastSec = -1;
    function tick() {
      const diff = target - Date.now();
      if (diff <= 0) {
        if (strip) {
          strip.classList.add("sdash-spot__countdown--ended");
          const rowEl = strip.querySelector(".sdash-spot__cd-row");
          if (rowEl) {
            rowEl.innerHTML = '<span class="sdash-spot__countdown--ended-msg">Session In Progress</span>';
          }
          if (elFill) elFill.style.width = "100%";
        }
        return;
      }
      const totalSec = Math.floor(diff / 1000);
      elDays.textContent = pad(Math.floor(totalSec / 86400));
      elHrs.textContent  = pad(Math.floor((totalSec % 86400) / 3600));
      elMin.textContent  = pad(Math.floor((totalSec % 3600) / 60));
      const sec = totalSec % 60;
      elSec.textContent  = pad(sec);

      /* re-trigger the per-second tick animation on the seconds digit */
      if (sec !== lastSec) {
        lastSec = sec;
        elSec.classList.remove("sdash-spot__cd-val--tick");
        void elSec.offsetWidth;
        elSec.classList.add("sdash-spot__cd-val--tick");
      }

      if (elFill) {
        const pct = Math.max(0, Math.min(100, (1 - diff / windowMs) * 100));
        elFill.style.width = pct.toFixed(2) + "%";
      }
    }

    tick();
    setInterval(tick, 1000);
  })();

  /* ════════════════════════════════════════════════
     SESSION EXPIRY / IDLE TIMEOUT WARNING
     - Warns the user before the (server) session lapses.
     - "Extend session" resets the idle window (optional keep-alive ping).
     - "Dismiss" hides the toast; the countdown keeps running and will
       redirect to the login page on expiry.
  ════════════════════════════════════════════════ */
  (function initSessionTimer() {
    const el = document.getElementById("se-timer");
    if (!el) return;

    const clockEl   = document.getElementById("se-timer-clock");
    const fillEl    = document.getElementById("se-timer-fill");
    const extendBtn = document.getElementById("se-timer-extend");
    const dismissBtn = document.getElementById("se-timer-dismiss");

    const WARN = Math.max(10, parseInt(el.dataset.duration || "120", 10));     // warning countdown (s)
    const IDLE = Math.max(WARN + 30, parseInt(el.dataset.idle || "1500", 10)); // total idle allowed (s)
    const LOGOUT_URL = el.dataset.logoutUrl || null;
    const KEEPALIVE_URL = el.dataset.keepaliveUrl || null;

    let idleTimer = null;    // schedules when the warning appears
    let ticker = null;       // 1s ticker while the warning is visible
    let remaining = WARN;
    let visible = false;

    const pad = (n) => String(n).padStart(2, "0");
    const fmt = (s) => pad(Math.floor(s / 60)) + ":" + pad(s % 60);

    function render() {
      clockEl.textContent = fmt(Math.max(0, remaining));
      if (fillEl) fillEl.style.width = (Math.max(0, remaining) / WARN * 100).toFixed(2) + "%";
      el.classList.toggle("setimer--warn", remaining <= 60 && remaining > 20);
      el.classList.toggle("setimer--danger", remaining <= 20);
    }

    function showWarning() {
      if (visible) return;
      visible = true;
      remaining = WARN;
      render();
      el.hidden = false;
      requestAnimationFrame(() => el.classList.add("setimer--in"));
      ticker = setInterval(() => {
        remaining -= 1;
        render();
        if (remaining <= 0) expire();
      }, 1000);
    }

    function visualHide() {
      el.classList.remove("setimer--in");
      setTimeout(() => { el.hidden = true; }, 240);
    }

    function expire() {
      clearInterval(ticker);
      if (LOGOUT_URL) window.location.href = LOGOUT_URL;
    }

    function armIdle() {
      clearTimeout(idleTimer);
      idleTimer = setTimeout(showWarning, (IDLE - WARN) * 1000);
    }

    function extend() {
      clearInterval(ticker);
      visible = false;
      visualHide();
      el.classList.remove("setimer--warn", "setimer--danger");
      armIdle();
      if (KEEPALIVE_URL) {
        fetch(KEEPALIVE_URL, { method: "POST", credentials: "same-origin" }).catch(() => {});
      }
    }

    if (extendBtn)  extendBtn.addEventListener("click", extend);
    if (dismissBtn) dismissBtn.addEventListener("click", visualHide); // countdown keeps running

    /* Any activity re-arms the idle clock while no warning is showing */
    ["mousemove", "mousedown", "keydown", "scroll", "touchstart"].forEach((evt) =>
      window.addEventListener(evt, () => { if (!visible) armIdle(); }, { passive: true }));

    /* Manual hooks for previewing / testing the design from the console */
    window.eTasmiSessionTimer = { show: showWarning, extend: extend, hide: visualHide };

    armIdle();
  })();

})();
</script>
</body>
</html>
