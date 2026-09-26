<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.verifyEmailTitle">Verify Email | e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <%@ include file="/jsp/common/ui_head.jspf" %>
    <script defer src="<%= request.getContextPath() %>/assets/js/auth.js"></script>
</head>
<body class="auth-body">
<%
    String successMsg = (request.getAttribute("success") == null) ? null : String.valueOf(request.getAttribute("success"));
    if (successMsg == null || successMsg.isBlank()) { successMsg = request.getParameter("msg"); }
    String warningMsg = (request.getAttribute("warning") == null) ? null : String.valueOf(request.getAttribute("warning"));
    if (warningMsg == null || warningMsg.isBlank()) { warningMsg = request.getParameter("warn"); }
    String errorMsg = (request.getAttribute("error") == null) ? null : String.valueOf(request.getAttribute("error"));
    if (errorMsg == null || errorMsg.isBlank()) { errorMsg = request.getParameter("error"); }
    String pendingEmail = (String) request.getAttribute("pendingEmail");
    if (pendingEmail == null || pendingEmail.isBlank()) { pendingEmail = request.getParameter("email"); }

    // Priority: error > warning > success. Only one alert is ever rendered to
    // avoid conflicting messages like "success" + "could not send email".
    boolean hasError   = errorMsg   != null && !errorMsg.isBlank();
    boolean hasWarning = warningMsg != null && !warningMsg.isBlank();
    boolean hasSuccess = successMsg != null && !successMsg.isBlank();
    if (hasError)   { hasWarning = false; hasSuccess = false; }
    if (hasWarning) { hasSuccess = false; }
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
                <a class="auth-nav-link is-active" href="<%= LocaleSupport.localizedUrl(request, "/auth/verify-email") %>" data-i18n="nav.verifyEmail">Verify Email</a>
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
                            <div class="auth-kicker" data-i18n="auth.verify.kicker">Email Verification</div>
                            <h1 class="auth-title" data-i18n="auth.verify.enterCode">Enter your 6-digit code</h1>
                        </div>
                    </div>
                    <span class="auth-chip auth-chip--light" data-i18n="auth.verify.verificationRequired">Verification Required</span>
                </div>

                <p class="auth-subtitle" data-i18n="auth.verify.pageSubtitle">We sent a verification code to your email address. Confirm it below to activate access.</p>

                <div data-alert-host="1">
                    <% if (hasSuccess) { %>
                    <div class="auth-alert" data-tone="success">
                        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M20 12a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z" stroke="currentColor" stroke-width="1.6"/><path d="m8.5 12.3 2.2 2.2 4.8-5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                        <div><%= successMsg %></div>
                    </div>
                    <% } else if (hasWarning) { %>
                    <div class="auth-alert" data-tone="warning">
                        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 9v4m0 3.5h.01M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                        <div><%= warningMsg %></div>
                    </div>
                    <% } else if (hasError) { %>
                    <div class="auth-alert" data-tone="error">
                        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 8v4m0 3h.01M21 12A9 9 0 1 1 3 12a9 9 0 0 1 18 0Z" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                        <div><%= errorMsg %></div>
                    </div>
                    <% } %>
                </div>

                <% if (pendingEmail != null && !pendingEmail.isBlank()) { %>
                <div class="auth-status">
                    <strong data-i18n="auth.verify.verificationEmail">Verification email</strong>
                    <%= pendingEmail %>
                </div>
                <div data-verify-status="1" data-email="<%= pendingEmail %>" data-context-path="<%= request.getContextPath() %>"></div>
                <% } %>

                <form class="auth-form" method="post" action="<%= request.getContextPath() %>/auth/verify-email" data-ajax="1">
                    <input type="hidden" name="email" value="<%= pendingEmail == null ? "" : pendingEmail %>">

                    <div class="auth-field">
                        <label class="auth-label" for="code"><span data-i18n="auth.verify.codeLabel">Verification code</span> <span class="required">*</span></label>
                        <div class="auth-control">
                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M8 11h8" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><path d="M8 15h5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><path d="M7 4h10a2 2 0 0 1 2 2v14l-4-2-4 2-4-2-4 2V6a2 2 0 0 1 2-2Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/></svg>
                            <input id="code" class="auth-code" type="text" name="code" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" data-i18n="auth.verify.codePlaceholderExample" data-i18n-attr="placeholder" placeholder="123456" required>
                        </div>
                    </div>

                    <button class="auth-btn" type="submit" data-i18n="auth.verify.submit">Verify email</button>
                </form>

                <div class="auth-inline-panel">
                    <strong data-i18n="auth.verify.didNotReceive">Did not receive the code?</strong>
                    <p class="auth-help" data-i18n="auth.verify.resendHelp">Use the resend option below. The backend resend flow remains connected to the same servlet.</p>
                    <form method="post" action="<%= request.getContextPath() %>/auth/resend-verification" data-ajax="1">
                        <input type="hidden" name="email" value="<%= pendingEmail == null ? "" : pendingEmail %>">
                        <button class="auth-btn-secondary" type="submit" data-i18n="auth.resendVerificationCode">Resend verification code</button>
                    </form>
                </div>
            </section>
        </div>
    </div>
</div>
</body>
</html>
