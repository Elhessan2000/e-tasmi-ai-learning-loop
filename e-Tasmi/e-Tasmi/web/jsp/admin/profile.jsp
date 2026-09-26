<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="model.entity.User" %>
<%
  User user = (User) request.getAttribute("user");
  String success = (String) request.getAttribute("success");
  String error = (String) request.getAttribute("error");

  String displayName = (user == null || user.getFullName() == null || user.getFullName().trim().isEmpty()) ? "Admin" : user.getFullName().trim();
  String initial = displayName.substring(0, 1).toUpperCase();
  String statusLabel = (user != null && user.isActive()) ? "ACTIVE" : "INACTIVE";
  String statusClass = (user != null && user.isActive()) ? "status-badge--active" : "status-badge--inactive";
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
  <title data-i18n="meta.adminProfileTitle">Admin Profile - e-Tasmi</title>
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <%@ include file="/jsp/common/admin_ui_head.jspf" %>
  <link rel="stylesheet" href="<%= request.getContextPath() %>/css/profile-redesign.css">
  <link rel="stylesheet" href="<%= request.getContextPath() %>/css/admin-ui-refine.css?v=20260518-student-sidebar-parity1">
  <script defer src="<%= request.getContextPath() %>/assets/js/admin.js"></script>
</head>
<body class="admin-body admin-package-page admin-module-page">
<div class="admin-shell">
  <%@ include file="/jsp/common/admin_header.jspf" %>
  <div class="admin-layout">
    <%@ include file="/jsp/admin/admin_sidebar.jspf" %>
    <main class="admin-main">
      <div class="admin-workspace-view">

        <div class="profile-breadcrumb">
          <a href="<%= request.getContextPath() %>/admin/dashboard" data-i18n="admin.nav.dashboard">Dashboard</a>
          <span class="sep">&rsaquo;</span>
          <span data-i18n="admin.profile.title">Profile</span>
        </div>

        <div class="profile-hero">
          <div class="profile-hero__text">
            <div class="profile-hero__eyebrow" data-i18n="admin.common.accountCenter">Account Center</div>
            <h1 data-i18n="admin.profile.pageTitle">Keep your profile complete and up to date.</h1>
            <p data-i18n="admin.profile.subtitle">Update your personal details, contact information, and account settings from one clean workspace.</p>
          </div>
          <div class="profile-hero__actions">
            <button class="pbtn pbtn-primary" type="button" onclick="document.getElementById('pwdModal').classList.add('active')" data-i18n="admin.common.changePassword">Change Password</button>
            <a class="pbtn pbtn-outline" href="<%= request.getContextPath() %>/admin/dashboard" data-i18n="instructor.profile.backToDashboard">Back to Dashboard</a>
          </div>
        </div>

        <% if (success != null) { %>
        <div class="profile-alert profile-alert--success">
          <svg viewBox="0 0 24 24" fill="none"><path d="M9 12l2 2 4-4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/></svg>
          <%= success %>
        </div>
        <% } %>
        <% if (error != null) { %>
        <div class="profile-alert profile-alert--error">
          <svg viewBox="0 0 24 24" fill="none"><path d="M12 8v4m0 4h.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/></svg>
          <%= error %>
        </div>
        <% } %>

        <div class="profile-content">
          <div class="profile-sidebar-card">
            <div class="profile-avatar-wrap">
              <span class="avatar-fallback"><%= initial %></span>
            </div>
            <div class="profile-sidebar-name"><%= displayName %></div>
            <div class="profile-sidebar-role" data-i18n="admin.profile.adminRole">Admin</div>

            <div class="profile-sidebar-info">
              <div class="info-row">
                <div class="info-label" data-i18n="admin.common.fullName">Full Name</div>
                <div class="info-value"><%= displayName %></div>
              </div>
              <div class="info-row">
                <div class="info-label" data-i18n="admin.common.email">Email</div>
                <div class="info-value"><%= user == null || user.getEmail() == null ? "-" : user.getEmail() %></div>
              </div>
              <div class="info-row">
                <div class="info-label" data-i18n="instructor.profile.status">Status</div>
                <div class="info-value"><span class="status-badge <%= statusClass %>"><%= statusLabel %></span></div>
              </div>
            </div>
          </div>

          <div class="profile-form-card">
            <form method="post" action="<%= request.getContextPath() %>/admin/profile">
              <input type="hidden" name="action" value="profile">

              <div class="form-section">
                <div class="form-section-title form-section-title--bordered" data-i18n="admin.common.personalInfo">Personal Information</div>
                <div class="profile-form-grid-2col">
                  <div class="pf-field">
                    <label class="pf-label" for="adminFullName" data-i18n="admin.common.fullName">Full Name <span class="required-star">*</span></label>
                    <input id="adminFullName" name="fullName" type="text" required value="<%= user == null ? "" : user.getFullName() %>">
                  </div>
                  <div class="pf-field">
                    <label class="pf-label" for="adminEmail" data-i18n="admin.common.emailAddress">Email Address</label>
                    <input id="adminEmail" type="email" readonly value="<%= user == null ? "" : user.getEmail() %>">
                  </div>
                  <div class="pf-field">
                    <label class="pf-label" for="adminPhone" data-i18n="admin.common.phoneNumber">Phone Number <span class="required-star">*</span></label>
                    <input id="adminPhone" name="phone" type="text" required value="<%= user == null || user.getPhone() == null ? "" : user.getPhone() %>">
                  </div>
                </div>
              </div>

              <div class="profile-actions-row">
                <button class="pbtn pbtn-primary" type="submit">
                  <svg viewBox="0 0 24 24" fill="none"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><path d="M17 21v-8H7v8M7 3v5h8" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>
                  <span data-i18n="admin.common.saveChanges">Save Changes</span>
                </button>
                <button class="pbtn pbtn-outline" type="button" onclick="document.getElementById('pwdModal').classList.add('active')">
                  <svg viewBox="0 0 24 24" fill="none"><path d="M12 17a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z" stroke="currentColor" stroke-width="2"/><rect x="3" y="11" width="18" height="11" rx="2" stroke="currentColor" stroke-width="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                  <span data-i18n="admin.common.changePassword">Change Password</span>
                </button>
                <a class="pbtn pbtn-ghost" href="<%= request.getContextPath() %>/admin/dashboard">
                  &larr; <span data-i18n="instructor.profile.backToDashboard">Back to Dashboard</span>
                </a>
              </div>
            </form>
          </div>
        </div>

      </div>
      <%@ include file="/jsp/common/app_footer.jspf" %>
    </main>
  </div>
</div>

<div class="pwd-modal-overlay" id="pwdModal">
  <div class="pwd-modal">
    <div class="pwd-modal__header">
      <h3 data-i18n="admin.common.changePassword">Change Password</h3>
      <button class="pwd-modal__close" type="button" onclick="document.getElementById('pwdModal').classList.remove('active')">
        <svg viewBox="0 0 24 24" fill="none"><path d="M18 6 6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
      </button>
    </div>
    <form method="post" action="<%= request.getContextPath() %>/admin/profile">
      <input type="hidden" name="action" value="password">
      <div class="pwd-modal__body">
        <div class="pf-field">
          <label class="pf-label" data-i18n="admin.common.currentPassword">Current Password</label>
          <input name="currentPassword" type="password" required autocomplete="current-password">
        </div>
        <div class="pf-field">
          <label class="pf-label" data-i18n="admin.common.newPassword">New Password</label>
          <input name="newPassword" type="password" required autocomplete="new-password">
          <span class="pf-hint" data-i18n="admin.common.passwordHint">At least 8 characters with uppercase, lowercase, and a number.</span>
        </div>
        <div class="pf-field">
          <label class="pf-label" data-i18n="admin.common.confirmNewPassword">Confirm New Password</label>
          <input name="confirmPassword" type="password" required autocomplete="new-password">
        </div>
      </div>
      <div class="pwd-modal__actions">
        <button class="pbtn pbtn-outline" type="button" onclick="document.getElementById('pwdModal').classList.remove('active')" data-i18n="common.cancel">Cancel</button>
        <button class="pbtn pbtn-primary" type="submit" data-i18n="admin.common.updatePassword">Update Password</button>
      </div>
    </form>
  </div>
</div>

</body>
</html>
