<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.resetPasswordTitle">Reset Password | e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <%@ include file="/jsp/common/ui_head.jspf" %>
    <script defer src="<%= request.getContextPath() %>/assets/js/auth.js"></script>
</head>
<body class="auth-body">
<%
    String token = (String) request.getAttribute("token");
    boolean showForm = token != null && !token.isBlank() && request.getAttribute("success") == null;
%>
<div class="auth-page">
    <div class="auth-shell">
        <nav class="auth-nav">
            <a class="auth-brand" href="<%= LocaleSupport.localizedUrl(request, "/home") %>">
                <img src="<%= request.getContextPath() %>/assets/img/logo.svg" alt="e-Tasmi">
                <span class="auth-brand-mark">
                    <strong>e-Tasmi</strong>
                    <span data-i18n="auth.brandTagline">Digital Qur'an recitation management</span>
                </span>
            </a>
            <div class="auth-nav-links">
                <a class="auth-nav-link" href="<%= LocaleSupport.localizedUrl(request, "/home") %>" data-i18n="nav.home">Home</a>
                <a class="auth-nav-link is-active" href="<%= LocaleSupport.localizedUrl(request, "/auth/reset-password") %>" data-i18n="nav.passwordReset">Reset Password</a>
            </div>
            <div class="auth-nav-actions">
                <%@ include file="/jsp/common/etasmi_language_switcher.jspf" %>
                <%@ include file="/jsp/common/auth_theme_toggle.jspf" %>
                <a class="auth-btn-secondary" href="<%= LocaleSupport.localizedUrl(request, "/auth/login") %>" data-i18n="auth.backToLogin">Back to Login</a>
            </div>
        </nav>

        <div class="auth-layout auth-layout--single">
            <section class="auth-card">
                <div class="auth-card-head">
                    <div class="auth-card-brand">
                        <img src="<%= request.getContextPath() %>/assets/img/logo.svg" alt="e-Tasmi">
                        <div>
                            <div class="auth-kicker" data-i18n="auth.passwordResetKicker">Password Reset</div>
                            <h1 class="auth-title" data-i18n="auth.chooseNewPassword">Choose a new password</h1>
                        </div>
                    </div>
                    <span class="auth-chip auth-chip--light" data-i18n="auth.secureUpdate">Secure Update</span>
                </div>

                <p class="auth-subtitle" data-i18n="auth.resetSubtitle">Enter a new password for your account. The backend token validation and reset process remain unchanged.</p>

                <div data-alert-host="1">
                    <% if (request.getAttribute("success") != null) { %>
                    <div class="auth-alert" data-tone="success">
                        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M20 12a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z" stroke="currentColor" stroke-width="1.6"/><path d="m8.5 12.3 2.2 2.2 4.8-5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                        <div><%= request.getAttribute("success") %></div>
                    </div>
                    <% } %>
                    <% if (request.getAttribute("error") != null) { %>
                    <div class="auth-alert" data-tone="error">
                        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 8v4m0 3h.01M21 12A9 9 0 1 1 3 12a9 9 0 0 1 18 0Z" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                        <div><%= request.getAttribute("error") %></div>
                    </div>
                    <% } %>
                </div>

                <% if (showForm) { %>
                <form class="auth-form" method="post" action="<%= request.getContextPath() %>/auth/reset-password" data-ajax="1" data-password-check="1">
                    <input type="hidden" name="token" value="<%= token == null ? "" : token %>">

                    <div class="auth-field">
                        <label class="auth-label" for="newPassword" data-i18n="auth.newPassword">New Password <span class="required">*</span></label>
                        <div class="auth-control">
                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M7 10V8a5 5 0 0 1 10 0v2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><path d="M6.5 10h11A2.5 2.5 0 0 1 20 12.5v5A2.5 2.5 0 0 1 17.5 20h-11A2.5 2.5 0 0 1 4 17.5v-5A2.5 2.5 0 0 1 6.5 10Z" stroke="currentColor" stroke-width="1.6"/></svg>
                            <input id="newPassword" type="password" name="newPassword" data-i18n="auth.newPasswordPlaceholder" data-i18n-attr="placeholder" placeholder="Minimum 8 characters" autocomplete="new-password" required>
                        </div>
                        <div class="auth-help" data-i18n="auth.passwordRulesHelp">Use uppercase, lowercase, and at least one number.</div>
                    </div>

                    <div class="auth-field">
                        <label class="auth-label" for="confirmPassword" data-i18n="auth.confirmPassword">Confirm Password <span class="required">*</span></label>
                        <div class="auth-control">
                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M7 10V8a5 5 0 0 1 10 0v2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><path d="M6.5 10h11A2.5 2.5 0 0 1 20 12.5v5A2.5 2.5 0 0 1 17.5 20h-11A2.5 2.5 0 0 1 4 17.5v-5A2.5 2.5 0 0 1 6.5 10Z" stroke="currentColor" stroke-width="1.6"/></svg>
                            <input id="confirmPassword" type="password" name="confirmPassword" data-i18n="auth.confirmPasswordPlaceholder" data-i18n-attr="placeholder" placeholder="Re-enter your password" autocomplete="new-password" required>
                        </div>
                    </div>

                    <button class="auth-btn" type="submit" data-i18n="auth.updatePassword">Update Password</button>

                    <div class="auth-divider"></div>
                    <div class="auth-meta">
                        <a class="auth-link" href="<%= LocaleSupport.localizedUrl(request, "/auth/login") %>" data-i18n="auth.returnToLogin">Back to login</a>
                    </div>
                </form>
                <% } else { %>
                <div class="auth-inline-panel">
                    <strong data-i18n="auth.resetLinkStatus">Reset link status</strong>
                    <p class="auth-help">
                        <% if (request.getAttribute("success") != null) { %>
                        Your password has already been updated. Continue to login with your new password.
                        <% } else { %>
                        This reset link is invalid or has expired. Request a new link from the forgot password page.
                        <% } %>
                    </p>
                    <a class="auth-link" href="<%= LocaleSupport.localizedUrl(request, "/auth/forgot-password") %>" data-i18n="auth.requestResetLink">Request a reset link</a>
                </div>
                <% } %>
            </section>
        </div>
    </div>
</div>
</body>
</html>
