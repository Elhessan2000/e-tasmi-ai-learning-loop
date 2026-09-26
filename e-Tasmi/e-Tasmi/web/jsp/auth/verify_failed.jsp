<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
<%
    Object errorAttr = request.getAttribute("error");
    String error = (errorAttr == null) ? null : String.valueOf(errorAttr);
    boolean hasError = error != null && !error.isBlank();
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.verifyFailedTitle">Verification Failed | e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <%@ include file="/jsp/common/ui_head.jspf" %>
    <script defer src="<%= request.getContextPath() %>/assets/js/auth.js"></script>
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
                        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 8v4m0 3h.01M21 12A9 9 0 1 1 3 12a9 9 0 0 1 18 0Z" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                    </div>
                    <span class="auth-chip auth-chip--light" data-i18n="auth.verify.issueChip">Verification Issue</span>
                    <h1 class="auth-title" data-i18n="auth.verify.linkFailedHeading">This verification link did not work.</h1>
                    <% if (hasError) { %>
                    <p class="auth-subtitle"><%= error %></p>
                    <% } else { %>
                    <p class="auth-subtitle" data-i18n="auth.verify.defaultFailedMessage">Verification link is invalid or expired.</p>
                    <% } %>
                </div>

                <form class="auth-form" method="post" action="<%= request.getContextPath() %>/auth/resend-verification" data-ajax="1">
                    <div class="auth-field">
                        <label class="auth-label" for="resendEmail"><span data-i18n="auth.emailAddress">Email Address</span> <span class="required">*</span></label>
                        <div class="auth-control">
                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 6.75C4 5.784 4.784 5 5.75 5h12.5C19.216 5 20 5.784 20 6.75v10.5c0 .966-.784 1.75-1.75 1.75H5.75C4.784 19 4 18.216 4 17.25V6.75Z" stroke="currentColor" stroke-width="1.6"/><path d="M6 8l6 4 6-4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                            <input id="resendEmail" type="email" name="email" data-i18n="auth.emailPlaceholder" data-i18n-attr="placeholder" placeholder="your.email@example.com" required>
                        </div>
                    </div>

                    <div class="auth-actions">
                        <button class="auth-btn" type="submit" data-i18n="auth.resendVerificationCode">Resend verification code</button>
                        <a class="auth-btn-secondary" href="<%= LocaleSupport.localizedUrl(request, "/auth/login") %>" data-i18n="auth.backToLogin">Back to Login</a>
                    </div>
                </form>
            </section>
        </div>
    </div>
</div>
</body>
</html>
