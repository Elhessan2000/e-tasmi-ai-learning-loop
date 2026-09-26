<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
<%
    Object messageAttr = request.getAttribute("message");
    String message = (messageAttr == null) ? null : String.valueOf(messageAttr);
    boolean hasMessage = message != null && !message.isBlank();
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.verifySuccessTitle">Email Verified | e-Tasmi</title>
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
                        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M20 12a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z" stroke="currentColor" stroke-width="1.6"/><path d="m8.5 12.3 2.2 2.2 4.8-5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                    </div>
                    <span class="auth-chip auth-chip--light" data-i18n="auth.verify.successChip">Verification Complete</span>
                    <h1 class="auth-title" data-i18n="auth.verify.verifiedHeading">Your email has been verified.</h1>
                    <% if (hasMessage) { %>
                    <p class="auth-subtitle"><%= message %></p>
                    <% } else { %>
                    <p class="auth-subtitle" data-i18n="auth.verify.defaultSuccessMessage">Email verified successfully.</p>
                    <% } %>
                    <div class="auth-actions">
                        <a class="auth-btn" href="<%= LocaleSupport.localizedUrl(request, "/auth/login") %>" data-i18n="auth.continueToLogin">Continue to Login</a>
                        <a class="auth-btn-secondary" href="<%= LocaleSupport.localizedUrl(request, "/home") %>" data-i18n="nav.backToHome">← Back to home</a>
                    </div>
                </div>
            </section>
        </div>
    </div>
</div>
</body>
</html>
