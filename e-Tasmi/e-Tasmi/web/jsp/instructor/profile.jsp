<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="model.entity.User" %>
<%@ page import="model.entity.Instructor" %>
<%
  request.setAttribute("activeMenu", "profile");
  User user = (User) request.getAttribute("user");
  Instructor instructor = (Instructor) request.getAttribute("instructor");
  String profileImageUrl = (String) request.getAttribute("profileImageUrl");
  String success = (String) request.getAttribute("success");
  String error = (String) request.getAttribute("error");

  String rawFullName = user == null || user.getFullName() == null ? "" : user.getFullName().trim();
  String namePrefix = "";
  String baseName = rawFullName;
  if (rawFullName.toLowerCase().startsWith("austaz ")) {
    namePrefix = "Austaz";
    baseName = rawFullName.substring(7).trim();
  } else if (rawFullName.toLowerCase().startsWith("astazh ")) {
    namePrefix = "Astazh";
    baseName = rawFullName.substring(7).trim();
  }

  String displayName = rawFullName.isEmpty() ? "Instructor" : rawFullName;
  String initial = baseName.isEmpty() ? "I" : baseName.substring(0, 1).toUpperCase();
  boolean hasProfileImage = profileImageUrl != null && !profileImageUrl.trim().isEmpty();
  String resolvedProfileImg = hasProfileImage ? (profileImageUrl.startsWith("http") ? profileImageUrl : request.getContextPath() + profileImageUrl) : "";
  String statusLabel = (user != null && user.isActive()) ? "ACTIVE" : "INACTIVE";
  String statusClass = (user != null && user.isActive()) ? "status-badge--active" : "status-badge--inactive";
  String verificationLabel = (instructor != null && instructor.getVerificationStatus() != null) ? instructor.getVerificationStatus().name() : "PENDING";
  String verificationClass = "APPROVED".equals(verificationLabel) ? "status-badge--active" : "status-badge--pending";
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
  <title data-i18n="meta.instructorProfileTitle">Instructor Profile - e-Tasmi</title>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <%@ include file="/jsp/common/instructor_ui_head.jspf" %>
  <link rel="stylesheet" href="<%= request.getContextPath() %>/css/profile-redesign.css?v=20260609-instructor-dark">
  <link rel="stylesheet" href="<%= request.getContextPath() %>/css/instructor-page-headers.css?v=20260625-student-theme">
  <link rel="stylesheet" href="<%= request.getContextPath() %>/css/instructor-theme-dark.css?v=20260609-instructor-dark">
  <script defer src="<%= request.getContextPath() %>/assets/js/app.js"></script>
</head>
<body class="instructor-premium-page instructor-package-page instructor-module-page">
<div class="app-shell">
  <%@ include file="/jsp/common/instructor_header.jspf" %>
  <div class="app-main">
    <div class="container sd-container instructor-workspace-shell">
      <div class="instructor-shell-layout">
        <%@ include file="/jsp/instructor/instructor_sidebar.jspf" %>
        <main class="instructor-shell-content" role="main">
          <div class="instructor-workspace-view iup-fade-in">

            <section class="profile-hero iup-page-hero iup-page-hero--profile" data-i18n="instructor.profile.title" data-i18n-attr="aria-label" aria-label="Profile overview">
              <span class="iup-page-hero__glow" aria-hidden="true"></span>
              <nav class="iup-page-hero__breadcrumb profile-breadcrumb" aria-label="Breadcrumb">
                <a href="<%= request.getContextPath() %>/instructor/dashboard" data-i18n="instructor.nav.dashboard">Dashboard</a>
                <span class="sep">&rsaquo;</span>
                <span data-i18n="instructor.nav.profile">Profile</span>
              </nav>
              <div class="iup-page-hero__inner">
                <span class="iup-page-hero__icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="8" r="4" stroke="currentColor" stroke-width="1.8"/><path d="M4 21a8 8 0 0 1 16 0" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
                </span>
                <div class="iup-page-hero__text profile-hero__text">
                  <p class="iup-page-hero__eyebrow profile-hero__eyebrow" data-i18n="instructor.nav.profile">Profile</p>
                  <h1 class="iup-page-hero__title" data-i18n="instructor.profile.pageTitle">Your instructor profile</h1>
                  <p class="iup-page-hero__desc" data-i18n="instructor.profile.heroDesc">Keep your personal details, contact information, and account settings complete and up to date.</p>
                </div>
              </div>
              <div class="iup-page-hero__actions profile-hero__actions">
                <a class="pbtn pbtn-outline" href="<%= request.getContextPath() %>/instructor/dashboard" data-i18n="instructor.profile.backToDashboard">Back to Dashboard</a>
              </div>
            </section>

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
                  <% if (hasProfileImage) { %>
                  <img src="<%= resolvedProfileImg %>" alt="Profile photo" data-i18n="instructor.profile.profilePhotoAlt" data-i18n-attr="alt" onerror="this.style.display='none'; this.parentElement.querySelector('.avatar-fallback').style.display='grid';">
                  <span class="avatar-fallback" style="display:none;"><%= initial %></span>
                  <% } else { %>
                  <span class="avatar-fallback"><%= initial %></span>
                  <% } %>
                </div>
                <div class="profile-sidebar-name"><%= displayName %></div>
                <div class="profile-sidebar-role" data-i18n="common.instructor">Instructor</div>

                <label class="profile-upload-btn" for="instrPhotoInput">
                  <svg viewBox="0 0 24 24" fill="none"><path d="M12 5v14M5 12h14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                  <span data-i18n="instructor.profile.uploadPhoto">Upload Photo</span>
                </label>
                <form class="photo-upload-form" id="instrPhotoForm" method="post" action="<%= request.getContextPath() %>/instructor/profile" enctype="multipart/form-data">
                  <input type="hidden" name="action" value="photo">
                  <input type="file" id="instrPhotoInput" name="photo" accept="image/*" onchange="document.getElementById('instrPhotoForm').submit();">
                </form>

                <div class="profile-sidebar-info">
                  <div class="info-row">
                    <div class="info-label" data-i18n="instructor.profile.fullName">Full Name</div>
                    <div class="info-value"><%= displayName %></div>
                  </div>
                  <div class="info-row">
                    <div class="info-label" data-i18n="instructor.profile.verification">Verification</div>
                    <div class="info-value"><span class="status-badge <%= verificationClass %>"><%= verificationLabel %></span></div>
                  </div>
                  <div class="info-row">
                    <div class="info-label" data-i18n="instructor.profile.status">Status</div>
                    <div class="info-value"><span class="status-badge <%= statusClass %>"><%= statusLabel %></span></div>
                  </div>
                </div>
              </div>

              <div class="profile-form-card">
                <form method="post" action="<%= request.getContextPath() %>/instructor/profile">
                  <input type="hidden" name="action" value="profile">

                  <div class="form-section">
                    <div class="form-section-title form-section-title--bordered" data-i18n="instructor.profile.personalInfo">Personal Information</div>
                    <div class="profile-form-grid-2col">
                      <div class="pf-field">
                        <label class="pf-label" data-i18n="instructor.profile.titleLabel">Title</label>
                        <select name="namePrefix">
                          <option value="" <%= namePrefix.isEmpty() ? "selected" : "" %> data-i18n="instructor.profile.noTitle">No title</option>
                          <option value="Austaz" <%= "Austaz".equalsIgnoreCase(namePrefix) ? "selected" : "" %>>Austaz</option>
                          <option value="Astazh" <%= "Astazh".equalsIgnoreCase(namePrefix) ? "selected" : "" %>>Astazh</option>
                        </select>
                      </div>
                      <div class="pf-field">
                        <label class="pf-label" data-i18n="instructor.profile.fullNameRequired">Full Name <span class="required-star">*</span></label>
                        <input name="fullName" type="text" required value="<%= baseName %>" data-i18n="auth.fullNamePlaceholder" data-i18n-attr="placeholder" placeholder="Full name">
                      </div>
                      <div class="pf-field">
                        <label class="pf-label" data-i18n="instructor.profile.emailAddress">Email Address</label>
                        <input type="email" readonly value="<%= user == null || user.getEmail() == null ? "" : user.getEmail() %>">
                      </div>
                      <div class="pf-field">
                        <label class="pf-label" data-i18n="instructor.profile.phoneNumber">Phone Number <span class="required-star">*</span></label>
                        <input name="phone" type="text" required value="<%= user == null || user.getPhone() == null ? "" : user.getPhone() %>" data-i18n="instructor.profile.phonePlaceholder" data-i18n-attr="placeholder" placeholder="Phone number">
                      </div>
                    </div>
                  </div>

                  <div class="form-section">
                    <div class="form-section-title form-section-title--bordered" data-i18n="instructor.profile.professionalInfo">Professional Information</div>
                    <div class="profile-form-grid-2col">
                      <div class="pf-field full-width">
                        <label class="pf-label" data-i18n="instructor.profile.qualification">Qualification</label>
                        <input name="qualification" type="text" value="<%= instructor == null || instructor.getQualification() == null ? "" : instructor.getQualification() %>" data-i18n="instructor.profile.qualificationPlaceholder" data-i18n-attr="placeholder" placeholder="e.g. Hafiz, Tajweed Certified">
                      </div>
                    </div>
                  </div>

                  <div class="profile-actions-row">
                    <button class="pbtn pbtn-primary" type="submit">
                      <svg viewBox="0 0 24 24" fill="none"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><path d="M17 21v-8H7v8M7 3v5h8" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>
                      <span data-i18n="instructor.profile.saveChanges">Save Changes</span>
                    </button>
                    <a class="pbtn pbtn-outline" href="<%= request.getContextPath() %>/auth/forgot-password">
                      <svg viewBox="0 0 24 24" fill="none"><path d="M12 17a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z" stroke="currentColor" stroke-width="2"/><rect x="3" y="11" width="18" height="11" rx="2" stroke="currentColor" stroke-width="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                      <span data-i18n="instructor.profile.resetPassword">Reset Password</span>
                    </a>
                    <a class="pbtn pbtn-ghost" href="<%= request.getContextPath() %>/instructor/dashboard">
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
  </div>
</div>
</body>
</html>
