<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
<%
    String status = request.getAttribute("verificationStatus") == null ? "PENDING" : String.valueOf(request.getAttribute("verificationStatus"));
    boolean rejected = "REJECTED".equalsIgnoreCase(status);
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.pendingApprovalTitle">Pending Approval | e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <%@ include file="/jsp/common/ui_head.jspf" %>
</head>
<body class="auth-body">
<div class="etasmi-auth-topbar">
  <%@ include file="/jsp/common/etasmi_language_switcher.jspf" %>
  <%@ include file="/jsp/common/auth_theme_toggle.jspf" %>
</div>
<div class="auth-page">
    <div class="auth-shell">
        <div class="auth-layout auth-layout--single">
            <section class="auth-card">
                <div class="auth-center-stack">
                    <div class="auth-icon-badge">
                        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 8v4l2 2" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.6"/></svg>
                    </div>
                    <span class="auth-chip auth-chip--light" data-i18n="auth.pendingApproval.instructorStatus">Instructor Status</span>
                    <% if (rejected) { %>
                    <h1 class="auth-title" data-i18n="auth.pendingApproval.rejectedTitle">Your instructor request was not approved.</h1>
                    <p class="auth-subtitle" data-i18n="auth.pendingApproval.rejectedSubtitle">The admin review did not approve your current submission. Please contact the administrator if you believe this should be reviewed again.</p>
                    <% } else { %>
                    <h1 class="auth-title" data-i18n="auth.pendingApproval.pendingTitle">Your instructor account is still under review.</h1>
                    <p class="auth-subtitle" data-i18n="auth.pendingApproval.pendingSubtitle">Your qualifications and profile are being reviewed by the administrator before instructor access can be activated.</p>
                    <% } %>
                    <span class="auth-status-badge <%= rejected ? "auth-status-badge--danger" : "" %>"><span data-i18n="common.status">Status</span>: <%= status %></span>
                </div>

                <div class="auth-inline-panel">
                    <strong data-i18n="auth.pendingApproval.whatThisMeans">What this means</strong>
                    <% if (rejected) { %>
                    <p class="auth-help" data-i18n="auth.pendingApproval.rejectedHelp">You can return later after contacting the administrator for guidance on resubmission or clarification.</p>
                    <% } else { %>
                    <p class="auth-help" data-i18n="auth.pendingApproval.pendingHelp">You can log out safely and come back later. Once the admin approves your account, the normal instructor access flow will continue.</p>
                    <% } %>
                </div>

                <div class="auth-actions">
                    <a class="auth-btn" href="<%= LocaleSupport.localizedUrl(request, "/auth/login") %>" data-i18n="auth.backToLogin">Back to Login</a>
                    <a class="auth-btn-secondary" href="<%= LocaleSupport.localizedUrl(request, "/auth/logout") %>" data-i18n="auth.logout">Logout</a>
                    <a class="auth-btn-ghost" href="<%= LocaleSupport.localizedUrl(request, "/home") %>" data-i18n="nav.home">Home</a>
                </div>
            </section>
        </div>
    </div>
</div>
</body>
</html>
