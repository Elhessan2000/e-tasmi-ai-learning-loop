<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.util.List" %>
<%@ page import="model.entity.AuditLog" %>
<%
    List<AuditLog> logs = (List<AuditLog>) request.getAttribute("logs");
    String filterAction = (String) request.getAttribute("filterAction");
    String filterRole = (String) request.getAttribute("filterRole");
    String dateFrom = (String) request.getAttribute("dateFrom");
    String dateTo = (String) request.getAttribute("dateTo");
    int total = logs == null ? 0 : logs.size();
    String ctx = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.adminLogsTitle">Audit Logs - e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <%@ include file="/jsp/common/admin_ui_head.jspf" %>
    <link rel="stylesheet" href="<%= ctx %>/css/admin-redesign.css">
    <link rel="stylesheet" href="<%= ctx %>/css/admin-ui-refine.css?v=20260518-student-sidebar-parity1">
    <script defer src="<%= ctx %>/assets/js/admin.js"></script>
</head>
<body class="admin-body admin-package-page admin-module-page">
<div class="admin-shell">
    <%@ include file="/jsp/common/admin_header.jspf" %>
    <div class="admin-layout">
        <%@ include file="/jsp/admin/admin_sidebar.jspf" %>
        <main class="admin-main">
            <div class="admin-workspace-view">

                <div class="ar-breadcrumb">
                    <a href="<%= ctx %>/admin/dashboard" data-i18n="admin.nav.dashboard">Dashboard</a>
                    <span class="sep">&rsaquo;</span>
                    <span data-i18n="admin.nav.logs">Audit Logs</span>
                </div>

                <div class="ar-hero">
                    <div>
                        <div class="ar-hero__eyebrow" data-i18n="admin.logs.eyebrow">System Logs</div>
                        <h1 class="ar-hero__title" data-i18n="admin.logs.pageTitle">Audit trail & activity feed.</h1>
                        <p class="ar-hero__sub" data-i18n="admin.logs.subtitle">Track every significant action performed on the platform. Filter by action type, role, or date range.</p>
                    </div>
                </div>

                <% if (request.getAttribute("error") != null) { %>
                <div class="ar-alert ar-alert--error">
                    <svg viewBox="0 0 24 24" fill="none"><path d="M12 8v4m0 4h.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/></svg>
                    <%= request.getAttribute("error") %>
                </div>
                <% } %>

                <div class="ar-section" style="margin-bottom:1rem;">
                    <div class="ar-section__head" style="margin-bottom:.5rem;">
                        <div class="ar-section__title" data-i18n="admin.common.filters">Filters</div>
                    </div>
                    <form class="ar-filter-form" method="get" action="<%= ctx %>/admin/logs">
                        <div class="ar-field">
                            <label for="filterAction" data-i18n="admin.common.action">Action</label>
                            <select class="ar-select" id="filterAction" name="filterAction">
                                <option value="" <%= filterAction == null || filterAction.isBlank() ? "selected" : "" %> data-i18n="admin.logs.allActions">All Actions</option>
                                <option value="USER_CREATED" <%= "USER_CREATED".equalsIgnoreCase(filterAction) ? "selected" : "" %>>USER_CREATED</option>
                                <option value="USER_UPDATED" <%= "USER_UPDATED".equalsIgnoreCase(filterAction) ? "selected" : "" %>>USER_UPDATED</option>
                                <option value="USER_DELETED" <%= "USER_DELETED".equalsIgnoreCase(filterAction) ? "selected" : "" %>>USER_DELETED</option>
                                <option value="USER_ACTIVATED" <%= "USER_ACTIVATED".equalsIgnoreCase(filterAction) ? "selected" : "" %>>USER_ACTIVATED</option>
                                <option value="USER_DEACTIVATED" <%= "USER_DEACTIVATED".equalsIgnoreCase(filterAction) ? "selected" : "" %>>USER_DEACTIVATED</option>
                                <option value="LOGIN" <%= "LOGIN".equalsIgnoreCase(filterAction) ? "selected" : "" %>>LOGIN</option>
                                <option value="LOGOUT" <%= "LOGOUT".equalsIgnoreCase(filterAction) ? "selected" : "" %>>LOGOUT</option>
                                <option value="INSTRUCTOR_APPROVED" <%= "INSTRUCTOR_APPROVED".equalsIgnoreCase(filterAction) ? "selected" : "" %>>INSTRUCTOR_APPROVED</option>
                                <option value="INSTRUCTOR_REJECTED" <%= "INSTRUCTOR_REJECTED".equalsIgnoreCase(filterAction) ? "selected" : "" %>>INSTRUCTOR_REJECTED</option>
                                <option value="SESSION_CREATED" <%= "SESSION_CREATED".equalsIgnoreCase(filterAction) ? "selected" : "" %>>SESSION_CREATED</option>
                                <option value="ENROLLMENT_CREATED" <%= "ENROLLMENT_CREATED".equalsIgnoreCase(filterAction) ? "selected" : "" %>>ENROLLMENT_CREATED</option>
                                <option value="PAYMENT_CREATED" <%= "PAYMENT_CREATED".equalsIgnoreCase(filterAction) ? "selected" : "" %>>PAYMENT_CREATED</option>
                                <option value="PASSWORD_RESET" <%= "PASSWORD_RESET".equalsIgnoreCase(filterAction) ? "selected" : "" %>>PASSWORD_RESET</option>
                            </select>
                        </div>
                        <div class="ar-field">
                            <label for="filterRole">Role</label>
                            <select class="ar-select" id="filterRole" name="filterRole">
                                <option value="" <%= filterRole == null || filterRole.isBlank() ? "selected" : "" %>>All Roles</option>
                                <option value="STUDENT" <%= "STUDENT".equalsIgnoreCase(filterRole) ? "selected" : "" %>>Student</option>
                                <option value="INSTRUCTOR" <%= "INSTRUCTOR".equalsIgnoreCase(filterRole) ? "selected" : "" %>>Instructor</option>
                                <option value="ADMIN" <%= "ADMIN".equalsIgnoreCase(filterRole) ? "selected" : "" %>>Admin</option>
                            </select>
                        </div>
                        <div class="ar-field">
                            <label for="dateFrom" data-i18n="admin.reports.dateFrom">Date From</label>
                            <input class="ar-input" id="dateFrom" type="date" name="dateFrom" value="<%= dateFrom == null ? "" : dateFrom %>">
                        </div>
                        <div class="ar-field">
                            <label for="dateTo" data-i18n="admin.reports.dateTo">Date To</label>
                            <input class="ar-input" id="dateTo" type="date" name="dateTo" value="<%= dateTo == null ? "" : dateTo %>">
                        </div>
                        <button class="ar-btn ar-btn--primary" type="submit" data-i18n="admin.common.apply">Apply</button>
                        <a class="ar-btn ar-btn--ghost" href="<%= ctx %>/admin/logs" data-i18n="common.clear">Clear</a>
                    </form>
                </div>

                <div class="ar-section">
                    <div class="ar-section__head">
                        <div>
                            <div class="ar-section__kicker" data-i18n="admin.logs.activityFeed">Activity Feed</div>
                            <div class="ar-section__title">
                                <% if (total == 0) { %>
                                No audit records matched the current filters.
                                <% } else { %>
                                Showing <%= total %> audit record<%= total != 1 ? "s" : "" %>
                                <% } %>
                            </div>
                        </div>
                        <span class="ar-badge ar-badge--neutral"><%= total %> records</span>
                    </div>

                    <div class="ar-table-wrap">
                        <table class="ar-table">
                            <thead>
                            <tr>
                                <th data-i18n="admin.common.when">When</th>
                                <th data-i18n="admin.common.actor">Actor</th>
                                <th data-i18n="admin.common.action">Action</th>
                                <th data-i18n="admin.common.entity">Entity</th>
                                <th data-i18n="admin.common.detail">Detail</th>
                            </tr>
                            </thead>
                            <tbody>
                            <% if (total == 0) { %>
                            <tr>
                                <td colspan="5" class="cell-empty" data-i18n="admin.logs.noLogsFound">No audit logs found.</td>
                            </tr>
                            <% } else { %>
                            <% for (AuditLog log : logs) {
                                String a = log.getAction() == null ? "" : log.getAction();
                                String actionBadge;
                                if (a.contains("APPROVED") || a.contains("SUCCESS") || a.contains("CREATED")) {
                                    actionBadge = "ar-badge--success";
                                } else if (a.contains("REJECTED") || a.contains("FAILED") || a.contains("DELETE")) {
                                    actionBadge = "ar-badge--danger";
                                } else if (a.contains("LOGIN") || a.contains("LOGOUT")) {
                                    actionBadge = "ar-badge--info";
                                } else {
                                    actionBadge = "ar-badge--neutral";
                                }
                            %>
                            <tr>
                                <td style="white-space:nowrap;"><%= log.getCreatedAt() %></td>
                                <td>
                                    <span class="ar-badge ar-badge--neutral"><%= log.getActorRole() == null ? "-" : log.getActorRole() %></span>
                                    <div class="cell-sub">User #<%= log.getActorUserId() == null ? "-" : log.getActorUserId() %></div>
                                </td>
                                <td><span class="ar-badge <%= actionBadge %>"><%= a %></span></td>
                                <td class="cell-strong"><%= log.getEntityType() %> #<%= log.getEntityId() %></td>
                                <td><%= log.getDetail() == null ? "-" : log.getDetail() %></td>
                            </tr>
                            <% } %>
                            <% } %>
                            </tbody>
                        </table>
                    </div>
                </div>

            </div>
            <%@ include file="/jsp/common/app_footer.jspf" %>
        </main>
    </div>
</div>
</body>
</html>
