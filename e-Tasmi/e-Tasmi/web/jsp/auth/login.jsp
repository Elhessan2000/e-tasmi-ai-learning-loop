<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
<%
    String ctx = request.getContextPath();
    String error = (String) request.getAttribute("error");
    String success = (String) request.getAttribute("success");
    String emailValue = (String) request.getAttribute("emailValue");
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="ltr" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="ltr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Cache-Control" content="no-store, no-cache, must-revalidate">
    <meta http-equiv="Pragma" content="no-cache">
    <title data-i18n="meta.loginTitle">Login | e-Tasmi</title>
    <%@ include file="/jsp/common/etasmi_i18n_head.jspf" %>
    <script>
      (function () {
        var root = document.documentElement;
        root.setAttribute('dir', 'ltr');
        root.setAttribute('data-dir', 'ltr');
        root.classList.remove('etasmi-rtl-root');
      })();
    </script>
    <%@ include file="/jsp/common/etasmi_theme_init.jspf" %>
    <link rel="stylesheet" href="https://unpkg.com/boxicons@2.1.4/css/boxicons.min.css">
    <link rel="stylesheet" href="<%= ctx %>/css/etasmi-login-dual.css?v=20260612-login-ltr-lock">
    <link rel="stylesheet" href="<%= ctx %>/css/etasmi-theme.css?v=20260609-theme-unified">
    <link rel="stylesheet" href="<%= ctx %>/css/etasmi-dark-polish.css?v=20260609-theme-unified">
    <link rel="stylesheet" href="<%= ctx %>/css/etasmi-mobile-responsive.css?v=20260511-no-dash-float-qc">
    <script defer src="<%= ctx %>/assets/js/auth.js"></script>
    <script defer src="<%= ctx %>/assets/js/etasmi-theme.js?v=20260609-theme-unified"></script>
</head>
<body class="etasmi-login-dual etasmi-auth-v2" data-layout-dir="ltr">
<div class="etasmi-auth-topbar">
  <%@ include file="/jsp/common/etasmi_language_switcher.jspf" %>
  <%@ include file="/jsp/common/auth_theme_toggle.jspf" %>
</div>
<a class="etasmi-dual-home" href="<%= LocaleSupport.localizedUrl(request, "/home") %>" data-i18n="nav.backToHome">← Back to home</a>

<div id="container" class="container auth-card">
    <div class="row">
        <div class="col align-items-center flex-col sign-up">
            <div class="form-wrapper align-items-center">
                <div class="form sign-up">
                    <p class="etasmi-dual-signup-hint" data-i18n="auth.signupHint">Create your e-Tasmi account on the registration page (student or instructor).</p>
                    <a class="etasmi-dual-signup-link" href="<%= LocaleSupport.localizedUrl(request, "/auth/register") %>" data-i18n="auth.createAccount">Create account</a>
                    <p>
                        <span data-i18n="auth.alreadyHaveAccount">Already have an account?</span>
                        <b onclick="toggle()" class="pointer" data-i18n="auth.signInHere">Sign in here</b>
                    </p>
                </div>
            </div>
        </div>
        <div class="col align-items-center flex-col sign-in">
            <div class="form-wrapper align-items-center">
                <div class="etasmi-dual-alert-host" data-alert-host="1">
                    <% if (success != null) { %>
                    <div class="auth-alert" data-tone="success">
                        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M20 12a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z" stroke="currentColor" stroke-width="1.6"/><path d="m8.5 12.3 2.2 2.2 4.8-5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                        <div><%= success %></div>
                    </div>
                    <% } %>
                    <% if (error != null) { %>
                    <div class="auth-alert" data-tone="error">
                        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 8v4m0 3h.01M21 12A9 9 0 1 1 3 12a9 9 0 0 1 18 0Z" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                        <div><%= error %></div>
                    </div>
                    <% } %>
                </div>
                <form class="form sign-in" method="post" action="<%= ctx %>/auth/login" data-ajax="1">
                    <div class="input-group">
                        <i class='bx bxs-user'></i>
                        <input type="email" name="email" id="loginEmail" data-login-email="1" value="<%= emailValue == null ? "" : emailValue %>" data-i18n="auth.username" data-i18n-attr="placeholder" placeholder="Username" required>
                    </div>
                    <div class="input-group">
                        <i class='bx bxs-lock-alt'></i>
                        <input type="password" name="password" id="loginPassword" data-i18n="auth.password" data-i18n-attr="placeholder" placeholder="Password" required>
                    </div>
                    <button type="submit" data-i18n="auth.signIn">Sign in</button>
                    <p>
                        <b>
                            <a href="<%= LocaleSupport.localizedUrl(request, "/auth/forgot-password") %>" data-i18n="auth.forgotPassword">Forgot password?</a>
                        </b>
                    </p>
                    <p>
                        <span data-i18n="auth.dontHaveAccount">Don't have an account?</span>
                        <b><a class="etasmi-dual-register-inline" href="<%= LocaleSupport.localizedUrl(request, "/auth/register") %>" data-i18n="auth.signUpHere">Sign up here</a></b>
                    </p>
                </form>
                <div class="auth-inline-panel" data-resend-panel="1" <%= (error != null && error.toLowerCase().contains("verify your email")) ? "" : "hidden" %>>
                    <strong data-i18n="auth.resendVerificationTitle">Need a new verification code?</strong>
                    <p class="auth-help" data-i18n="auth.resendVerificationHelp">Request another verification code for your registered email.</p>
                    <form method="post" action="<%= ctx %>/auth/resend-verification" data-ajax="1">
                        <input type="hidden" name="email" data-resend-email="1" value="<%= emailValue == null ? "" : emailValue %>">
                        <button class="auth-btn-secondary" type="submit" data-i18n="auth.resendVerificationCode">Resend verification code</button>
                    </form>
                    <a class="auth-btn-secondary" data-enter-code-link data-base-href="<%= ctx %>/auth/verify-email" href="<%= LocaleSupport.localizedUrl(request, "/auth/verify-email") %><%= emailValue == null || emailValue.isBlank() ? "" : "?email=" + java.net.URLEncoder.encode(emailValue, java.nio.charset.StandardCharsets.UTF_8) %>" data-i18n="auth.enterReceivedCode">Enter received code</a>
                </div>
            </div>
            <div class="form-wrapper"></div>
        </div>
    </div>
    <div class="row content-row">
        <div class="col align-items-center flex-col">
            <div class="text sign-in">
                <h2 data-i18n="auth.welcome">Welcome</h2>
            </div>
            <div class="img sign-in"></div>
        </div>
        <div class="col align-items-center flex-col">
            <div class="img sign-up"></div>
            <div class="text sign-up">
                <h2 data-i18n="auth.joinWithUs">Join with us</h2>
            </div>
        </div>
    </div>
</div>

<script>
(function () {
    var container = document.getElementById('container');
    window.toggle = function () {
        if (!container) return;
        container.classList.toggle('sign-in');
        container.classList.toggle('sign-up');
    };
    setTimeout(function () {
        if (container) container.classList.add('sign-in');
    }, 200);
})();
</script>
</body>
</html>
