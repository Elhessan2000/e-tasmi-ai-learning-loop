<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.forgotPasswordTitle">Forgot Password | e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <%@ include file="/jsp/common/ui_head.jspf" %>
    <script defer src="<%= request.getContextPath() %>/assets/js/auth.js"></script>
</head>
<body class="auth-body">
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
                <a class="auth-nav-link is-active" href="<%= LocaleSupport.localizedUrl(request, "/auth/forgot-password") %>" data-i18n="nav.passwordReset">Password Reset</a>
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
                            <div class="auth-kicker" data-i18n="auth.passwordRecovery">Password Recovery</div>
                            <h1 class="auth-title" data-i18n="auth.requestResetLink">Request a reset link</h1>
                        </div>
                    </div>
                    <span class="auth-chip auth-chip--light" data-i18n="auth.secureRecovery">Secure Recovery</span>
                </div>

                <p class="auth-subtitle" data-i18n="auth.forgotSubtitle">Enter the email linked to your account. If it exists in the system, the backend will send a reset link without exposing account details publicly.</p>

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

                <form class="auth-form" method="post" action="<%= request.getContextPath() %>/auth/forgot-password" data-ajax="1">
                    <div class="auth-field">
                        <label class="auth-label" for="forgotEmail" data-i18n="auth.emailAddress">Email Address <span class="required">*</span></label>
                        <div class="auth-control">
                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 6.75C4 5.784 4.784 5 5.75 5h12.5C19.216 5 20 5.784 20 6.75v10.5c0 .966-.784 1.75-1.75 1.75H5.75C4.784 19 4 18.216 4 17.25V6.75Z" stroke="currentColor" stroke-width="1.6"/><path d="M6 8l6 4 6-4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                            <input id="forgotEmail" type="email" name="email" data-i18n="auth.emailPlaceholder" data-i18n-attr="placeholder" placeholder="your.email@example.com" required>
                        </div>
                    </div>

                    <button class="auth-btn" type="submit" data-i18n="auth.sendResetLink">Send Reset Link</button>

                    <div class="auth-inline-panel">
                        <strong data-i18n="auth.whatHappensNext">What happens next?</strong>
                        <p class="auth-help" data-i18n="auth.resetEmailHelp">You will receive a password reset email with a secure link if your address is registered in the system.</p>
                    </div>

                    <div class="auth-divider"></div>
                    <div class="auth-meta">
                        <a class="auth-link" href="<%= LocaleSupport.localizedUrl(request, "/auth/login") %>" data-i18n="auth.returnToLogin">Return to login</a>
                    </div>
                </form>
            </section>
        </div>
    </div>
</div>
</body>
</html>
