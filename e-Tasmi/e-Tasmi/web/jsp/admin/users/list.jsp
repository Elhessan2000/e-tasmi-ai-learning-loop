<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.util.List" %>
<%@ page import="model.entity.User" %>
<%
    String search = (String) request.getAttribute("search");
    String role = (String) request.getAttribute("role");
    String success = (String) request.getAttribute("success");
    String error = (String) request.getAttribute("error");
    List<User> users = (List<User>) request.getAttribute("users");
    int total = users == null ? 0 : users.size();
    String ctx = request.getContextPath();
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.adminUsersTitle">Manage Users - e-Tasmi Admin</title>
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
                    <span data-i18n="admin.users.title">User Management</span>
                </div>

                <div class="ar-hero">
                    <div>
                        <div class="ar-hero__eyebrow" data-i18n="admin.users.title">User Management</div>
                        <h1 class="ar-hero__title" data-i18n="admin.users.listTitle">Manage all platform accounts.</h1>
                        <p class="ar-hero__sub" data-i18n="admin.users.listSub">View, edit, activate, deactivate, or delete user accounts across all roles.</p>
                    </div>
                </div>

                <% if (success != null && !success.isBlank()) { %>
                <div class="ar-alert ar-alert--success">
                    <svg viewBox="0 0 24 24" fill="none"><path d="M9 12l2 2 4-4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/></svg>
                    <%= success %>
                </div>
                <% } %>
                <% if (error != null && !error.isBlank()) { %>
                <div class="ar-alert ar-alert--error">
                    <svg viewBox="0 0 24 24" fill="none"><path d="M12 8v4m0 4h.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/></svg>
                    <%= error %>
                </div>
                <% } %>

                <div class="ar-section" style="margin-bottom:1rem;">
                    <div class="ar-section__head" style="margin-bottom:.5rem;">
                        <div class="ar-section__title" data-i18n="admin.common.filters">Filters</div>
                    </div>
                    <form class="ar-filter-form" method="get" action="<%= ctx %>/admin/users">
                        <div class="ar-field">
                            <label for="userSearch" data-i18n="common.search">Search</label>
                            <input class="ar-input" id="userSearch" type="text" name="search" value="<%= search == null ? "" : search %>" data-i18n="admin.common.nameOrEmail" data-i18n-attr="placeholder" placeholder="Name or email...">
                        </div>
                        <div class="ar-field">
                            <label for="roleFilter" data-i18n="admin.common.role">Role</label>
                            <select class="ar-select" id="roleFilter" name="role">
                                <option value="" <%= role == null || role.isBlank() ? "selected" : "" %> data-i18n="admin.common.allRoles">All Roles</option>
                                <option value="STUDENT" <%= "STUDENT".equalsIgnoreCase(role) ? "selected" : "" %> data-i18n="admin.common.student">Student</option>
                                <option value="INSTRUCTOR" <%= "INSTRUCTOR".equalsIgnoreCase(role) ? "selected" : "" %> data-i18n="admin.common.instructor">Instructor</option>
                                <option value="ADMIN" <%= "ADMIN".equalsIgnoreCase(role) ? "selected" : "" %> data-i18n="admin.common.adminRole">Admin</option>
                            </select>
                        </div>
                        <button class="ar-btn ar-btn--primary" type="submit" data-i18n="admin.common.apply">Apply</button>
                        <a class="ar-btn ar-btn--ghost" href="<%= ctx %>/admin/users" data-i18n="common.clear">Clear</a>
                    </form>
                </div>

                <div class="ar-section">
                    <div class="ar-section__head">
                        <div class="ar-section__title" data-i18n="admin.common.accounts">Accounts</div>
                        <span class="ar-badge ar-badge--neutral" data-i18n="admin.common.accountsCount" data-i18n-vars='{"count":"<%= total %>"}'><%= total %> accounts</span>
                    </div>

                    <div class="ar-table-wrap">
                        <table class="ar-table">
                            <thead>
                            <tr>
                                <th data-i18n="admin.users.colUser">User</th>
                                <th data-i18n="admin.users.colRole">Role</th>
                                <th data-i18n="admin.users.colStatus">Status</th>
                                <th data-i18n="admin.users.colEmail">Email</th>
                                <th data-i18n="admin.users.colActive">Active</th>
                                <th data-i18n="admin.users.colActions">Actions</th>
                            </tr>
                            </thead>
                            <tbody>
                            <% if (users == null || users.isEmpty()) { %>
                            <tr>
                                <td colspan="6" class="cell-empty" data-i18n="admin.common.noUsersFound">No users found.</td>
                            </tr>
                            <% } else { %>
                            <% for (User user : users) {
                                String roleStr = user.getRole() == null ? "-" : user.getRole().name();
                                String roleBadge = "ADMIN".equals(roleStr) ? "ar-badge--dark" : "INSTRUCTOR".equals(roleStr) ? "ar-badge--info" : "ar-badge--neutral";
                            %>
                            <tr>
                                <td>
                                    <div class="cell-strong"><%= user.getFullName() %></div>
                                    <div class="cell-sub"><%= user.getEmail() %><%= user.getPhone() == null ? "" : " | " + user.getPhone() %></div>
                                </td>
                                <td><span class="ar-badge <%= roleBadge %>"><%= roleStr %></span></td>
                                <td><span class="ar-badge ar-badge--neutral"><%= user.getStatus() == null ? "-" : user.getStatus() %></span></td>
                                <td>
                                    <span class="ar-badge <%= user.isEmailVerified() ? "ar-badge--success" : "ar-badge--warning" %>">
                                        <%= user.isEmailVerified() ? "Verified" : "Pending" %>
                                    </span>
                                </td>
                                <td>
                                    <span class="ar-badge <%= user.isActive() ? "ar-badge--success" : "ar-badge--danger" %>">
                                        <%= user.isActive() ? "Active" : "Inactive" %>
                                    </span>
                                </td>
                                <td>
                                    <div class="ar-row-actions">
                                        <a class="ar-btn ar-btn--secondary ar-btn--sm" href="<%= ctx %>/admin/users/edit?id=<%= user.getUserId() %>" data-i18n="common.edit">Edit</a>
                                        <form method="post" action="<%= ctx %>/admin/users/toggle" data-confirm="Change this account's active state?" data-i18n="admin.common.confirmToggleActive" data-i18n-attr="data-confirm">
                                            <input type="hidden" name="id" value="<%= user.getUserId() %>">
                                            <input type="hidden" name="active" value="<%= !user.isActive() %>">
                                            <button class="ar-btn ar-btn--sm <%= user.isActive() ? "ar-btn--secondary" : "ar-btn--primary" %>" type="submit"><%= user.isActive() ? "Deactivate" : "Activate" %></button>
                                        </form>
                                        <form method="post" action="<%= ctx %>/admin/users/delete" data-confirm="Delete this user account?" data-i18n="admin.common.confirmDeleteUser" data-i18n-attr="data-confirm">
                                            <input type="hidden" name="id" value="<%= user.getUserId() %>">
                                            <button class="ar-btn ar-btn--danger ar-btn--sm" type="submit" data-i18n="common.delete">Delete</button>
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
