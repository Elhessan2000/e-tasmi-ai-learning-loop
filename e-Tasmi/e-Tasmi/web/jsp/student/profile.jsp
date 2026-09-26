<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
<%@ page import="model.entity.User" %>
<%@ page import="model.entity.Student" %>
<%
  request.setAttribute("activeMenu", "profile");
  User user = (User) request.getAttribute("user");
  Student student = (Student) request.getAttribute("student");
  String profileImageUrl = (String) request.getAttribute("profileImageUrl");
  String success = (String) request.getAttribute("success");
  String error = (String) request.getAttribute("error");

  String displayName = (user == null || user.getFullName() == null || user.getFullName().trim().isEmpty()) ? "Student" : user.getFullName().trim();
  String initial = displayName.substring(0, 1).toUpperCase();
  boolean hasProfileImage = profileImageUrl != null && !profileImageUrl.trim().isEmpty();
  String resolvedProfileImg = hasProfileImage ? (profileImageUrl.startsWith("http") ? profileImageUrl : request.getContextPath() + profileImageUrl) : "";
  String statusLabel = (user != null && user.isActive()) ? "ACTIVE" : "INACTIVE";
  String statusClass = (user != null && user.isActive()) ? "status-badge--active" : "status-badge--inactive";
  String regNumber = (student == null || student.getRegistrationNumber() == null) ? "-" : student.getRegistrationNumber();
  String levelLabel = (student != null && student.getLevel() != null) ? student.getLevel().getDisplayName() : "-";
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
  <title data-i18n="meta.studentProfileTitle">My Profile - e-Tasmi</title>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <%@ include file="/jsp/common/student_ui_head.jspf" %>
  <link rel="stylesheet" href="<%= request.getContextPath() %>/css/profile-redesign.css?v=20260624-student-redesign1">
  <script defer src="<%= request.getContextPath() %>/assets/js/app.js"></script>
</head>
<body class="student-premium-page student-package-page student-module-page">
<div class="app-shell">
  <%@ include file="/jsp/common/student_header.jspf" %>
  <div class="app-main">
    <div class="container sd-container student-workspace-shell">
      <div class="student-shell-layout">
        <%@ include file="/jsp/student/student_sidebar.jspf" %>
        <main class="student-shell-content" role="main">
          <div class="student-workspace-view">
            <%@ include file="/jsp/common/student_breadcrumb.jspf" %>
            <header class="student-hero student-hero--profile">
              <div class="student-hero__copy">
                <h1 class="student-hero__title" data-i18n="student.profile.title">Profile</h1>
                <p class="student-hero__sub" data-i18n="student.profile.subtitle">Your details, photo, and password in one place.</p>
              </div>
              <div class="student-hero__visual" aria-hidden="true">
                <span class="student-hero__badge">
                  <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <circle cx="12" cy="8" r="4" stroke="currentColor" stroke-width="1.7"/>
                    <path d="M4 21a8 8 0 0 1 16 0" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>
                  </svg>
                </span>
              </div>
            </header>

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

            <div class="profile-stats-row">
              <div class="profile-stat-card">
                <span class="profile-stat-card__icon profile-stat-card__icon--level">
                  <svg viewBox="0 0 24 24" fill="none"><path d="m3 16 5-5 4 3 8-8" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><path d="M15 6h5v5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                </span>
                <div class="profile-stat-card__body">
                  <div class="stat-value"><%= levelLabel %></div>
                  <div class="stat-label" data-i18n="student.profile.level">Level</div>
                </div>
              </div>
              <div class="profile-stat-card">
                <span class="profile-stat-card__icon profile-stat-card__icon--id">
                  <svg viewBox="0 0 24 24" fill="none"><rect x="3" y="5" width="18" height="14" rx="2" stroke="currentColor" stroke-width="2"/><path d="M7 9h4M7 13h7" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                </span>
                <div class="profile-stat-card__body">
                  <div class="stat-value"><%= regNumber %></div>
                  <div class="stat-label" data-i18n="student.profile.studentId">Student ID</div>
                </div>
              </div>
              <div class="profile-stat-card">
                <span class="profile-stat-card__icon profile-stat-card__icon--status">
                  <svg viewBox="0 0 24 24" fill="none"><path d="M9 12l2 2 4-4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2"/></svg>
                </span>
                <div class="profile-stat-card__body">
                  <div class="stat-value"><span class="status-badge <%= statusClass %>"><%= statusLabel %></span></div>
                  <div class="stat-label" data-i18n="student.profile.status">Status</div>
                </div>
              </div>
            </div>

            <div class="profile-content">
              <div class="profile-sidebar-card">
                <div class="profile-avatar-wrap">
                  <% if (hasProfileImage) { %>
                  <img src="<%= resolvedProfileImg %>" alt="Profile photo" data-i18n="student.profile.profilePhotoAlt" data-i18n-attr="alt" onerror="this.style.display='none'; this.parentElement.querySelector('.avatar-fallback').style.display='grid';">
                  <span class="avatar-fallback" style="display:none;"><%= initial %></span>
                  <% } else { %>
                  <span class="avatar-fallback"><%= initial %></span>
                  <% } %>
                </div>
                <div class="profile-sidebar-name"><%= displayName %></div>
                <div class="profile-sidebar-role" data-i18n="student.profile.studentRole">Student</div>

                <label class="profile-upload-btn" for="photoFileInput">
                  <svg viewBox="0 0 24 24" fill="none"><path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                  <span data-i18n="student.profile.uploadPhoto">Upload Photo</span>
                </label>
                <form class="photo-upload-form" id="photoUploadForm" method="post" action="<%= request.getContextPath() %>/student/profile" enctype="multipart/form-data">
                  <input type="hidden" name="action" value="photo">
                  <input type="file" id="photoFileInput" name="photo" accept="image/*" onchange="document.getElementById('photoUploadForm').submit();">
                </form>

                <div class="profile-sidebar-info">
                  <div class="info-row">
                    <div class="info-label" data-i18n="student.profile.fullName">Full Name</div>
                    <div class="info-value"><%= displayName %></div>
                  </div>
                  <div class="info-row">
                    <div class="info-label" data-i18n="student.profile.studentId">Student ID</div>
                    <div class="info-value"><%= regNumber %></div>
                  </div>
                  <div class="info-row">
                    <div class="info-label" data-i18n="student.profile.level">Level</div>
                    <div class="info-value"><%= levelLabel %></div>
                  </div>
                  <div class="info-row">
                    <div class="info-label" data-i18n="student.profile.status">Status</div>
                    <div class="info-value"><span class="status-badge <%= statusClass %>"><%= statusLabel %></span></div>
                  </div>
                </div>
              </div>

              <div class="profile-form-card">
                <form method="post" action="<%= request.getContextPath() %>/student/profile">
                  <input type="hidden" name="action" value="profile">

                  <div class="form-section">
                    <div class="form-section-title form-section-title--bordered" data-i18n="student.profile.personalInfo">Personal Information</div>
                    <div class="profile-form-grid-2col">
                      <div class="pf-field">
                        <label class="pf-label" for="pfFullName"><span data-i18n="student.profile.fullName">Full Name</span> <span class="required-star">*</span></label>
                        <input id="pfFullName" name="fullName" type="text" required value="<%= user == null ? "" : user.getFullName() %>">
                      </div>
                      <div class="pf-field">
                        <label class="pf-label" for="pfEmail" data-i18n="student.profile.emailAddress">Email Address</label>
                        <input id="pfEmail" type="email" readonly value="<%= user == null ? "" : user.getEmail() %>">
                      </div>
                      <div class="pf-field">
                        <label class="pf-label" for="pfPhone"><span data-i18n="student.profile.phoneNumber">Phone Number</span> <span class="required-star">*</span></label>
                        <input id="pfPhone" name="phone" type="text" required value="<%= user == null ? "" : user.getPhone() %>">
                      </div>
                      <div class="pf-field">
                        <label class="pf-label" data-i18n="student.profile.registrationNumber">Registration Number</label>
                        <input type="text" readonly value="<%= regNumber %>">
                      </div>
                    </div>
                  </div>

                  <div class="profile-actions-row">
                    <button class="pbtn pbtn-primary" type="submit">
                      <svg viewBox="0 0 24 24" fill="none"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><path d="M17 21v-8H7v8M7 3v5h8" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>
                      <span data-i18n="student.profile.saveChanges">Save Changes</span>
                    </button>
                    <button class="pbtn pbtn-outline" type="button" onclick="document.getElementById('pwdModal').classList.add('active')">
                      <svg viewBox="0 0 24 24" fill="none"><path d="M12 17a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z" stroke="currentColor" stroke-width="2"/><rect x="3" y="11" width="18" height="11" rx="2" stroke="currentColor" stroke-width="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                      <span data-i18n="student.profile.changePassword">Change Password</span>
                    </button>
                    <a class="pbtn pbtn-ghost etasmi-link-back" href="<%= LocaleSupport.localizedUrl(request, "/student/dashboard") %>" data-i18n="student.profile.backToDashboard">Back to Dashboard</a>
                  </div>
                </form>
              </div>
            </div>

          </div>
          <%@ include file="/jsp/common/app_footer.jspf" %>
        </main>
      </div>
    </div>
  </div>
</div>

<div class="pwd-modal-overlay" id="pwdModal">
  <div class="pwd-modal">
    <div class="pwd-modal__header">
      <h3 data-i18n="student.profile.changePassword">Change Password</h3>
      <button class="pwd-modal__close" type="button" onclick="document.getElementById('pwdModal').classList.remove('active')">
        <svg viewBox="0 0 24 24" fill="none"><path d="M18 6 6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
      </button>
    </div>
    <form method="post" action="<%= request.getContextPath() %>/student/profile">
      <input type="hidden" name="action" value="password">
      <div class="pwd-modal__body">
        <div class="pf-field">
          <label class="pf-label" data-i18n="student.profile.currentPassword">Current Password</label>
          <input name="currentPassword" type="password" required autocomplete="current-password">
        </div>
        <div class="pf-field">
          <label class="pf-label" data-i18n="student.profile.newPassword">New Password</label>
          <input name="newPassword" type="password" required autocomplete="new-password">
          <span class="pf-hint" data-i18n="student.profile.passwordHint">At least 8 characters with uppercase, lowercase, and a number.</span>
        </div>
        <div class="pf-field">
          <label class="pf-label" data-i18n="student.profile.confirmNewPassword">Confirm New Password</label>
          <input name="confirmPassword" type="password" required autocomplete="new-password">
        </div>
      </div>
      <div class="pwd-modal__actions">
        <button class="pbtn pbtn-outline" type="button" onclick="document.getElementById('pwdModal').classList.remove('active')" data-i18n="student.profile.cancel">Cancel</button>
        <button class="pbtn pbtn-primary" type="submit" data-i18n="student.profile.updatePassword">Update Password</button>
      </div>
    </form>
  </div>
</div>

</body>
</html>
