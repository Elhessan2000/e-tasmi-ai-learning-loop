<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%
    String success = (String) request.getAttribute("success");
    String error = (String) request.getAttribute("error");
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.adminCreateUserTitle">Create User - e-Tasmi Admin</title>
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
                <span class="admin-kicker" data-i18n="admin.users.title">User Management</span>
                <h1 data-i18n="admin.users.createTitle">Create a new platform account.</h1>
                <p data-i18n="admin.users.createSub">
                    This form creates a user directly from the admin side. In this workflow, the account is created as active and already email-verified so the admin can manage controlled access immediately.
                </p>
            </section>

            <section class="admin-panel admin-card">
                <div class="admin-section-head">
                    <div>
                        <span class="admin-kicker" data-i18n="admin.common.createUser">Create User</span>
                        <h2 class="admin-section-title" data-i18n="admin.users.createSectionTitle">Enter the account details carefully.</h2>
                    </div>
                    <p class="admin-section-copy" data-i18n="admin.users.createSectionSub">Use this for controlled account setup, not as a replacement for the normal public registration flow.</p>
                </div>

                <% if (success != null && !success.isBlank()) { %>
                    <div class="admin-alert admin-alert-success"><%= success %></div>
                <% } %>
                <% if (error != null && !error.isBlank()) { %>
                    <div class="admin-alert admin-alert-error"><%= error %></div>
                <% } %>

                <form class="admin-form" method="post" action="<%= request.getContextPath() %>/admin/users/create">
                    <div class="admin-form-grid">
                        <div class="admin-field">
                            <label for="fullName" data-i18n="admin.common.fullName">Full Name</label>
                            <input class="admin-input" id="fullName" type="text" name="fullName" required />
                        </div>
                        <div class="admin-field">
                            <label for="email" data-i18n="admin.common.emailAddress">Email Address</label>
                            <input class="admin-input" id="email" type="email" name="email" required />
                        </div>
                        <div class="admin-field">
                            <label for="phone" data-i18n="admin.common.phoneNumber">Phone Number</label>
                            <input class="admin-input" id="phone" type="text" name="phone" required />
                        </div>
                        <div class="admin-field">
                            <label for="password" data-i18n="admin.common.temporaryPassword">Temporary Password</label>
                            <input class="admin-input" id="password" type="password" name="password" minlength="6" required />
                        </div>
                        <div class="admin-field">
                            <label for="role" data-i18n="admin.common.role">Role</label>
                            <select class="admin-select" id="role" name="role" required>
                                <option value="STUDENT" data-i18n="admin.common.student">Student</option>
                                <option value="INSTRUCTOR" data-i18n="admin.common.instructor">Instructor</option>
                                <option value="ADMIN" data-i18n="admin.common.adminRole">Admin</option>
                            </select>
                        </div>
                    </div>

                    <div class="admin-actions">
                        <button class="admin-btn admin-btn-primary" type="submit" data-i18n="admin.common.createUser">Create User</button>
                        <a class="admin-btn admin-btn-secondary" href="<%= request.getContextPath() %>/admin/users" data-i18n="common.cancel">Cancel</a>
                    </div>
                </form>
            </section>

            </div>
            <%@ include file="/jsp/common/app_footer.jspf" %>
        </main>
    </div>
</div>
</body>
</html>
