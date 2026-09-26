<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="model.entity.User" %>
<%
    User user = (User) request.getAttribute("user");
    String success = (String) request.getAttribute("success");
    String error = (String) request.getAttribute("error");
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.adminEditUserTitle">Edit User - e-Tasmi Admin</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <%@ include file="/jsp/common/admin_ui_head.jspf" %>
    
    <script defer src="<%= request.getContextPath() %>/assets/js/admin.js"></script>
</head>
<body class="admin-body admin-package-page admin-module-page">
<div class="admin-shell">
    <%@ include file="/jsp/common/admin_header.jspf" %>
    <div class="admin-layout">
        <%@ include file="/jsp/admin/admin_sidebar.jspf" %>

        <main class="admin-main">
            <div class="admin-workspace-view">
            <section class="admin-panel admin-hero">
                <h1 data-i18n="admin.users.editTitle">Edit User</h1>
            </section>

            <section class="admin-panel admin-card">
                <div class="admin-section-head">
                    <div>
                        <span class="admin-kicker" data-i18n="admin.users.editTitle">Edit User</span>
                        <h2 class="admin-section-title"><%= user == null ? "User not found" : user.getFullName() %></h2>
                    </div>
                    <p class="admin-section-copy" data-i18n="admin.users.editSectionSub">Update the account identity fields. Use Activate or Deactivate from the user list to manage access.</p>
                </div>

                <% if (success != null && !success.isBlank()) { %>
                    <div class="admin-alert admin-alert-success"><%= success %></div>
                <% } %>
                <% if (error != null && !error.isBlank()) { %>
                    <div class="admin-alert admin-alert-error"><%= error %></div>
                <% } %>

                <% if (user == null) { %>
                    <div class="admin-alert admin-alert-error" data-i18n="admin.common.userNotLoaded">The selected user record could not be loaded.</div>
                    <div class="admin-actions">
                        <a class="admin-btn admin-btn-secondary" href="<%= request.getContextPath() %>/admin/users" data-i18n="admin.common.backToUsers">Back to Users</a>
                    </div>
                <% } else { %>
                <form class="admin-form" method="post" action="<%= request.getContextPath() %>/admin/users/update">
                    <input type="hidden" name="id" value="<%= user.getUserId() %>" />
                    <div class="admin-form-grid">
                        <div class="admin-field">
                            <label for="fullName" data-i18n="admin.common.fullName">Full Name</label>
                            <input class="admin-input" id="fullName" type="text" name="fullName" value="<%= user.getFullName() %>" required />
                        </div>
                        <div class="admin-field">
                            <label for="email" data-i18n="admin.common.emailAddress">Email Address</label>
                            <input class="admin-input" id="email" type="email" name="email" value="<%= user.getEmail() %>" required />
                        </div>
                        <div class="admin-field">
                            <label for="phone" data-i18n="admin.common.phoneNumber">Phone Number</label>
                            <input class="admin-input" id="phone" type="text" name="phone" value="<%= user.getPhone() == null ? "" : user.getPhone() %>" required />
                        </div>
                        <input type="hidden" name="role" value="<%= user.getRole() == null ? "" : user.getRole().name() %>" />
                        <div class="admin-detail-item">
                            <strong data-i18n="admin.common.role">Role</strong>
                            <%= user.getRole() == null ? "-" : user.getRole() %>
                        </div>
                        <div class="admin-detail-item">
                            <strong data-i18n="admin.common.currentStatus">Current Status</strong>
                            <%= user.getStatus() == null ? "-" : user.getStatus() %>
                        </div>
                        <div class="admin-detail-item">
                            <strong data-i18n="admin.common.emailVerification">Email Verification</strong>
                            <%= user.isEmailVerified() ? "Verified" : "Pending" %>
                        </div>
                    </div>

                    <div class="admin-actions">
                        <button class="admin-btn admin-btn-primary" type="submit" data-i18n="admin.common.updateUser">Update User</button>
                        <a class="admin-btn admin-btn-secondary" href="<%= request.getContextPath() %>/admin/users" data-i18n="common.cancel">Cancel</a>
                    </div>
                </form>
                <% } %>
            </section>

            </div>
            <%@ include file="/jsp/common/app_footer.jspf" %>
        </main>
    </div>
</div>
</body>
</html>
