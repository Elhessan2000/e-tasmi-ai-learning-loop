<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.util.List" %>
<%@ page import="model.service.InstructorVerificationItem" %>
<%
    request.setAttribute("activeMenu", "verification");
    List<InstructorVerificationItem> items = (List<InstructorVerificationItem>) request.getAttribute("items");
    int total = items == null ? 0 : items.size();
    String ctx = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.adminVerificationTitle">Instructor Verification - e-Tasmi</title>
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
                    <span data-i18n="admin.verification.title">Instructor Verification</span>
                </div>

                <div class="ar-hero">
                    <div>
                        <div class="ar-hero__eyebrow" data-i18n="admin.verification.queueEyebrow">Verification Queue</div>
                        <h1 class="ar-hero__title" data-i18n="admin.verification.pageTitle">Pending instructor verification</h1>
                        <p class="ar-hero__sub" data-i18n="admin.verification.subtitle">Review real instructor applications waiting for admin approval.</p>
                    </div>
                </div>

                <% String updated = request.getParameter("updated"); %>
                <% String message = request.getParameter("message"); %>
                <% if ("1".equals(updated)) { %>
                <div class="ar-alert ar-alert--success">
                    <svg viewBox="0 0 24 24" fill="none"><path d="M9 12l2 2 4-4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/></svg>
                    <%= message == null || message.isBlank() ? "Verification updated successfully." : message.replace('+', ' ') %>
                </div>
                <% } else if ("0".equals(updated)) { %>
                <div class="ar-alert ar-alert--error">
                    <svg viewBox="0 0 24 24" fill="none"><path d="M12 8v4m0 4h.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/></svg>
                    <%= message == null || message.isBlank() ? "Failed to update verification." : message.replace('+', ' ') %>
                </div>
                <% } %>

                <div class="ar-section">
                    <div class="ar-section__head">
                        <div>
                            <div class="ar-section__kicker" data-i18n="admin.verification.pendingOnly">Pending Only</div>
                            <div class="ar-section__title" data-i18n="admin.verification.waitingCount" data-i18n-vars='{"count":"<%= total %>"}'><%= total %> instructor<%= total == 1 ? "" : "s" %> waiting for verification</div>
                        </div>
                    </div>

                    <div class="ar-table-wrap">
                        <table class="ar-table">
                            <thead>
                            <tr>
                                <th data-i18n="admin.verification.colId">ID</th>
                                <th data-i18n="admin.verification.colName">Name</th>
                                <th data-i18n="admin.verification.colEmail">Email</th>
                                <th data-i18n="admin.verification.colStatus">Status</th>
                                <th data-i18n="admin.verification.colQualification">Qualification</th>
                                <th data-i18n="admin.verification.colActions">Actions</th>
                            </tr>
                            </thead>
                            <tbody id="verificationTableBody">
                            <% if (items == null || items.isEmpty()) { %>
                            <tr>
                                <td colspan="6" class="cell-empty" data-i18n="admin.verification.noPending">No pending instructors for verification</td>
                            </tr>
                            <% } else { %>
                            <% for (InstructorVerificationItem item : items) { %>
                            <tr>
                                <td class="cell-strong">#<%= item.getInstructor().getInstructorId() %></td>
                                <td class="cell-strong"><%= item.getUser().getFullName() %></td>
                                <td>
                                    <div class="cell-strong"><%= item.getUser().getEmail() %></div>
                                    <div class="cell-sub"><%= item.getUser().getPhone() == null ? "-" : item.getUser().getPhone() %></div>
                                </td>
                                <td><span class="ar-badge ar-badge--warning">PENDING</span></td>
                                <td>
                                    <% String q = item.getInstructor().getQualificationFile(); %>
                                    <% if (q == null || q.trim().isEmpty()) { %>
                                    <span class="ar-badge ar-badge--neutral" data-i18n="admin.common.noFile">No file</span>
                                    <% } else if (!item.isQualificationFileAvailable()) { %>
                                    <span class="ar-badge ar-badge--warning" data-i18n="admin.common.unavailable">Unavailable</span>
                                    <% } else { %>
                                    <div class="ar-row-actions">
                                        <a class="ar-btn ar-btn--secondary ar-btn--sm" target="_blank" href="<%= ctx %>/admin/instructors/qualification/download?inline=1&instructorId=<%= item.getInstructor().getInstructorId() %>" data-i18n="admin.common.view">View</a>
                                        <a class="ar-btn ar-btn--secondary ar-btn--sm" href="<%= ctx %>/admin/instructors/qualification/download?instructorId=<%= item.getInstructor().getInstructorId() %>" data-i18n="admin.common.download">Download</a>
                                    </div>
                                    <% } %>
                                </td>
                                <td>
                                    <div class="ar-row-actions">
                                        <form method="post" action="<%= ctx %>/admin/instructors/verification" data-confirm="Approve this instructor for teaching access?" data-i18n="admin.verification.confirmApprove" data-i18n-attr="data-confirm">
                                            <input type="hidden" name="instructorId" value="<%= item.getInstructor().getInstructorId() %>">
                                            <input type="hidden" name="action" value="approve">
                                            <button class="ar-btn ar-btn--primary ar-btn--sm" type="submit">
                                                <svg viewBox="0 0 24 24" fill="none"><path d="M20 6L9 17l-5-5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                                <span data-i18n="admin.common.approve">Approve</span>
                                            </button>
                                        </form>
                                        <form method="post" action="<%= ctx %>/admin/instructors/verification" data-confirm="Reject this instructor application?" data-i18n="admin.verification.confirmReject" data-i18n-attr="data-confirm">
                                            <input type="hidden" name="instructorId" value="<%= item.getInstructor().getInstructorId() %>">
                                            <input type="hidden" name="action" value="reject">
                                            <button class="ar-btn ar-btn--danger ar-btn--sm" type="submit">
                                                <svg viewBox="0 0 24 24" fill="none"><path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                                                <span data-i18n="admin.common.reject">Reject</span>
                                            </button>
                                        </form>
                                    </div>
                                </td>
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
