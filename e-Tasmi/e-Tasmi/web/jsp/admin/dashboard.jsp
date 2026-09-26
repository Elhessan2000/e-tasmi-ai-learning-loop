<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="model.entity.ReportSummary" %>
<%
    ReportSummary s = (ReportSummary) request.getAttribute("summary");
    if (s == null) s = new ReportSummary();
    long totalUsers = request.getAttribute("totalUsers") == null ? 0L : ((Number) request.getAttribute("totalUsers")).longValue();
    long totalStudents = request.getAttribute("totalStudents") == null ? 0L : ((Number) request.getAttribute("totalStudents")).longValue();
    long totalInstructors = request.getAttribute("totalInstructors") == null ? 0L : ((Number) request.getAttribute("totalInstructors")).longValue();
    long pendingInstructors = request.getAttribute("pendingInstructors") == null ? 0L : ((Number) request.getAttribute("pendingInstructors")).longValue();
    long totalSessions = request.getAttribute("totalSessions") == null ? 0L : ((Number) request.getAttribute("totalSessions")).longValue();
    long totalPayments = request.getAttribute("totalPayments") == null ? 0L : ((Number) request.getAttribute("totalPayments")).longValue();
    long totalEnrollments = request.getAttribute("totalEnrollments") == null ? 0L : ((Number) request.getAttribute("totalEnrollments")).longValue();
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.adminDashboardTitle">Admin Dashboard - e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <%@ include file="/jsp/common/admin_ui_head.jspf" %>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/admin-redesign.css">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/admin-dashboard-saas.css?v=20260502-white-bg">
    <script defer src="<%= request.getContextPath() %>/assets/js/admin.js"></script>
    <script defer src="<%= request.getContextPath() %>/assets/js/admin-dashboard-counters.js?v=20260502"></script>
</head>
<body class="admin-body admin-package-page admin-module-page admin-dashboard-page">
<div class="admin-shell">
    <%@ include file="/jsp/common/admin_header.jspf" %>
    <div class="admin-layout">
        <%@ include file="/jsp/admin/admin_sidebar.jspf" %>
        <main class="admin-main">
            <div class="admin-workspace-view">

                <div class="ar-breadcrumb">
                    <span data-i18n="admin.common.admin">Admin</span>
                    <span class="sep">&rsaquo;</span>
                    <span data-i18n="admin.nav.dashboard">Dashboard</span>
                </div>

                <div class="ar-hero ar-hero--dashboard">
                    <div>
                        <h1 class="ar-hero__title" data-i18n="admin.dashboard.title">Dashboard</h1>
                        <p class="ar-hero__sub" data-i18n="admin.dashboard.subtitle">A calm overview of users, sessions, payments, and verification.</p>
                    </div>
                </div>

                <% if (request.getAttribute("error") != null) { %>
                <div class="ar-alert ar-alert--error">
                    <svg viewBox="0 0 24 24" fill="none"><path d="M12 8v4m0 4h.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/></svg>
                    <%= request.getAttribute("error") %>
                </div>
                <% } %>

                <div class="ar-stats ar-stats--4 ar-dashboard-row-users">
                    <div class="ar-stat ar-stat--accent">
                        <div class="ar-stat__icon" aria-hidden="true">
                            <svg viewBox="0 0 24 24" fill="none"><path d="M16 11a4 4 0 1 0-8 0" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><path d="M4 20a7 7 0 0 1 7-4h0a7 7 0 0 1 7 4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
                        </div>
                        <div class="ar-stat__label" data-i18n="admin.dashboard.totalUsers">Total users</div>
                        <div class="ar-stat__value"><span class="counter-value" data-target="<%= totalUsers %>">0</span></div>
                        <div class="ar-stat__micro ar-stat__micro--success" data-i18n="admin.dashboard.allRoles">All roles</div>
                    </div>
                    <div class="ar-stat">
                        <div class="ar-stat__icon" aria-hidden="true">
                            <svg viewBox="0 0 24 24" fill="none"><path d="M2.5 9.5 12 4 21.5 9.5 12 15 2.5 9.5Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="M6 10.3V16.8l6 2.2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
                        </div>
                        <div class="ar-stat__label" data-i18n="admin.dashboard.totalStudents">Students</div>
                        <div class="ar-stat__value"><span class="counter-value" data-target="<%= totalStudents %>">0</span></div>
                        <div class="ar-stat__micro ar-stat__micro--neutral" data-i18n="admin.dashboard.enrolled">Enrolled</div>
                    </div>
                    <div class="ar-stat">
                        <div class="ar-stat__icon" aria-hidden="true">
                            <svg viewBox="0 0 24 24" fill="none"><path d="M12 3 4 6v6c0 4.5 3.2 8.4 8 9 4.8-.6 8-4.5 8-9V6l-8-3Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg>
                        </div>
                        <div class="ar-stat__label" data-i18n="admin.dashboard.totalInstructors">Instructors</div>
                        <div class="ar-stat__value"><span class="counter-value" data-target="<%= totalInstructors %>">0</span></div>
                        <div class="ar-stat__micro ar-stat__micro--neutral" data-i18n="admin.dashboard.teaching">Teaching</div>
                    </div>
                    <div class="ar-stat ar-stat--warn">
                        <div class="ar-stat__icon" aria-hidden="true">
                            <svg viewBox="0 0 24 24" fill="none"><path d="M10 2h4a2 2 0 0 1 2 2v.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/><path d="M4 6h7l2-2h3a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg>
                        </div>
                        <div class="ar-stat__label" data-i18n="admin.dashboard.pendingVerifications">Pending verification</div>
                        <div class="ar-stat__value"><span class="counter-value" data-target="<%= pendingInstructors %>">0</span></div>
                        <div class="ar-stat__micro ar-stat__micro--warning" data-i18n="admin.dashboard.reviewQueue">Review queue</div>
                    </div>
                </div>

                <div class="ar-stats ar-stats--4 ar-dashboard-row-ops">
                    <div class="ar-stat">
                        <div class="ar-stat__icon" aria-hidden="true">
                            <svg viewBox="0 0 24 24" fill="none"><rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M3 9h18M8 2v3M16 2v3" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
                        </div>
                        <div class="ar-stat__label" data-i18n="admin.dashboard.totalSessions">Total sessions</div>
                        <div class="ar-stat__value"><span class="counter-value" data-target="<%= totalSessions %>">0</span></div>
                        <div class="ar-stat__sub">
                            <span class="ar-stat__chip ar-stat__chip--em">Sched. <%= s.getSessionsScheduled() %></span>
                            <span class="ar-stat__chip ar-stat__chip--blue">Live <%= s.getSessionsOngoing() %></span>
                        </div>

                    </div>
                    <div class="ar-stat">
                        <div class="ar-stat__icon" aria-hidden="true">
                            <svg viewBox="0 0 24 24" fill="none"><path d="M4 6.5A2.5 2.5 0 0 1 6.5 4H8l2-2h4l2 2h1.5A2.5 2.5 0 0 1 20 6.5V19a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6.5Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg>
                        </div>
                        <div class="ar-stat__label" data-i18n="admin.dashboard.totalEnrollments">Total enrollments</div>
                        <div class="ar-stat__value"><span class="counter-value" data-target="<%= totalEnrollments %>">0</span></div>
                        <div class="ar-stat__sub">
                            <span class="ar-stat__chip ar-stat__chip--em">OK <%= s.getEnrollmentsApproved() %></span>
                            <span class="ar-stat__chip ar-stat__chip--amber">Wait <%= s.getEnrollmentsPending() %></span>
                        </div>
                    </div>
                    <div class="ar-stat ar-stat--info">
                        <div class="ar-stat__icon" aria-hidden="true">
                            <svg viewBox="0 0 24 24" fill="none"><rect x="1" y="4" width="22" height="16" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M1 10h22" stroke="currentColor" stroke-width="1.8"/></svg>
                        </div>
                        <div class="ar-stat__label" data-i18n="admin.dashboard.totalPayments">Total payments</div>
                        <div class="ar-stat__value"><span class="counter-value" data-target="<%= totalPayments %>">0</span></div>
                        <div class="ar-stat__sub">
                            <span class="ar-stat__chip ar-stat__chip--em">Done <%= s.getPaymentsSuccess() %></span>
                            <span class="ar-stat__chip ar-stat__chip--amber">Open <%= s.getPaymentsPending() %></span>
                        </div>
                    </div>
                    <div class="ar-stat">
                        <div class="ar-stat__icon" aria-hidden="true">
                            <svg viewBox="0 0 24 24" fill="none"><path d="M4 4h6v6H4V4Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="M14 4h6v6h-6V4Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg>
                        </div>
                        <div class="ar-stat__label" data-i18n="admin.dashboard.recitationsEvaluations">Recitations &middot; evaluations</div>
                        <div class="ar-stat__value ar-stat__value--duo"><span class="counter-value" data-target="<%= s.getTotalRecitations() %>">0</span><span class="ar-stat__slash">/</span><span class="counter-value" data-target="<%= s.getTotalEvaluations() %>">0</span></div>
                        <div class="ar-stat__micro ar-stat__micro--neutral" data-i18n="admin.dashboard.allTime">All time</div>
                    </div>
                </div>

                <div class="ar-quick-actions">
                    <a class="ar-quick-card" href="<%= request.getContextPath() %>/admin/instructors/verification?status=PENDING">
                        <div class="ar-quick-card__icon ar-quick-card__icon--amber">
                            <svg viewBox="0 0 24 24" fill="none"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2" stroke="currentColor" stroke-width="2"/><circle cx="9" cy="7" r="4" stroke="currentColor" stroke-width="2"/><path d="m22 10.5-3.5 3.5-2-2" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                        </div>
                        <div class="ar-quick-card__body">
                            <div class="ar-quick-card__text" data-i18n="admin.dashboard.instructorVerification">Instructor verification</div>
                            <div class="ar-quick-card__sub" data-i18n="admin.dashboard.inQueue" data-i18n-vars='{"count":"<%= pendingInstructors %>"}'><%= pendingInstructors %> in queue</div>
                        </div>
                        <svg class="ar-quick-card__chev" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M9 6l6 6-6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                    </a>
                    <a class="ar-quick-card" href="<%= request.getContextPath() %>/admin/payments">
                        <div class="ar-quick-card__icon ar-quick-card__icon--blue">
                            <svg viewBox="0 0 24 24" fill="none"><rect x="1" y="4" width="22" height="16" rx="2" stroke="currentColor" stroke-width="2"/><path d="M1 10h22" stroke="currentColor" stroke-width="2"/></svg>
                        </div>
                        <div class="ar-quick-card__body">
                            <div class="ar-quick-card__text" data-i18n="admin.dashboard.paymentMonitoring">Payment monitoring</div>
                            <div class="ar-quick-card__sub" data-i18n="admin.dashboard.needAttention" data-i18n-vars='{"count":"<%= s.getPaymentsPending() %>"}'><%= s.getPaymentsPending() %> need attention</div>
                        </div>
                        <svg class="ar-quick-card__chev" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M9 6l6 6-6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                    </a>
                    <a class="ar-quick-card" href="<%= request.getContextPath() %>/admin/reports">
                        <div class="ar-quick-card__icon ar-quick-card__icon--green">
                            <svg viewBox="0 0 24 24" fill="none"><path d="M18 20V10M12 20V4M6 20v-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                        </div>
                        <div class="ar-quick-card__body">
                            <div class="ar-quick-card__text" data-i18n="admin.nav.reports">Reports</div>
                            <div class="ar-quick-card__sub" data-i18n="admin.dashboard.exportsSummaries">Exports and summaries</div>
                        </div>
                        <svg class="ar-quick-card__chev" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M9 6l6 6-6 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                    </a>
                </div>

            </div>
            <%@ include file="/jsp/common/app_footer.jspf" %>
        </main>
    </div>
</div>
</body>
</html>
