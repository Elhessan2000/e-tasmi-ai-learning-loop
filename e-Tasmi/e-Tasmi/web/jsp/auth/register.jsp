<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
<%
    String ctx = request.getContextPath();
    String error = (String) request.getAttribute("error");
    String fullNameValue = (String) request.getAttribute("fullNameValue");
    String emailValue = (String) request.getAttribute("emailValue");
    String phoneValue = (String) request.getAttribute("phoneValue");
    String roleValue = (String) request.getAttribute("roleValue");
    String bioValue = (String) request.getAttribute("bioValue");
    String studentLevelValue = (String) request.getAttribute("studentLevelValue");
    String selectedRole = (roleValue == null || roleValue.isBlank()) ? "STUDENT" : roleValue.toUpperCase();
    boolean instructorMode = "INSTRUCTOR".equals(selectedRole);
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title data-i18n="meta.registerTitle">Create account | e-Tasmi</title>
    <%@ include file="/jsp/common/ui_head.jspf" %>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Poppins:wght@400;500;600;700&display=swap">
    <link rel="stylesheet" href="<%= ctx %>/css/etasmi-auth-shell.css?v=20260613-register-dark">
    <script defer src="<%= ctx %>/assets/js/auth.js"></script>
</head>
<body class="auth-body etasmi-auth-v2 etasmi-auth-v2--register <%= instructorMode ? "etasmi-auth-v2--instructor" : "etasmi-auth-v2--student" %>">
<div class="auth-page">
    <div class="auth-shell etasmi-auth-shell">
        <div class="etasmi-auth-home-row">
            <a class="etasmi-auth-home-link" href="<%= LocaleSupport.localizedUrl(request, "/home") %>" data-i18n="nav.backToHome">&larr; Back to home</a>
            <%@ include file="/jsp/common/etasmi_language_switcher.jspf" %>
            <%@ include file="/jsp/common/auth_theme_toggle.jspf" %>
        </div>

        <section class="auth-card auth-card--register etasmi-auth-card">
            <div class="etasmi-auth-card__hero">
                <div class="etasmi-auth-card__hero-inner etasmi-auth-card__hero-inner--row">
                    <div class="etasmi-auth-card__logo" aria-hidden="true">
                        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                            <path d="M4.75 6.75A1.75 1.75 0 0 1 6.5 5h4.75c1.1 0 2.13.48 2.84 1.3.71-.82 1.74-1.3 2.84-1.3h.57a1.75 1.75 0 0 1 1.75 1.75v10.5A1.75 1.75 0 0 1 18.5 19h-1.07a3.5 3.5 0 0 0-2.48 1.02l-.45.45a.7.7 0 0 1-.99 0l-.45-.45A3.5 3.5 0 0 0 10.57 19H6.5a1.75 1.75 0 0 1-1.75-1.75V6.75Z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/>
                            <path d="M12 6.75v11.5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>
                        </svg>
                    </div>
                    <div class="etasmi-auth-card__hero-text">
                        <h1 class="etasmi-auth-card__brand">e-Tasmi</h1>
                        <p class="etasmi-auth-card__tagline" data-i18n="auth.registerTagline">Choose your account type and create your profile</p>
                    </div>
                </div>
            </div>

            <div class="etasmi-auth-card__body">
                <div class="etasmi-auth-card__body-head">
                    <h2 data-role-title="1" data-i18n="<%= instructorMode ? "auth.instructorSignup" : "auth.studentSignup" %>"><%= instructorMode ? "Instructor signup" : "Student signup" %></h2>
                    <p data-role-copy="1" data-i18n="<%= instructorMode ? "auth.instructorSignupCopy" : "auth.studentSignupCopy" %>"><%= instructorMode
                            ? "Add your profile and qualification. We verify instructors before they can teach."
                            : "Create your learner profile to join sessions and track progress." %></p>
                </div>

                <div class="etasmi-auth-form-surface">
                    <h1 class="etasmi-sr-only" data-role-sr-title="1">Create <%= instructorMode ? "instructor" : "student" %> account | e-Tasmi</h1>

                    <div data-alert-host="1">
                        <% if (error != null) { %>
                        <div class="auth-alert" data-tone="error">
                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 8v4m0 3h.01M21 12A9 9 0 1 1 3 12a9 9 0 0 1 18 0Z" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                            <div><%= error %></div>
                        </div>
                        <% } %>
                    </div>

                    <form class="auth-form auth-form--clean" method="post" enctype="multipart/form-data" action="<%= ctx %>/auth/register" data-ajax="1" data-password-check="1" data-password-strength="1" data-phone-check="1">
                        <input type="hidden" name="role" value="<%= selectedRole %>" data-role-input="1">

                        <div class="etasmi-role-switch" role="group" aria-label="Choose registration type">
                            <button class="etasmi-role-switch__option <%= !instructorMode ? "is-active" : "" %>" type="button" data-role-card="STUDENT" aria-pressed="<%= !instructorMode ? "true" : "false" %>" data-i18n="auth.student">Student</button>
                            <button class="etasmi-role-switch__option <%= instructorMode ? "is-active" : "" %>" type="button" data-role-card="INSTRUCTOR" aria-pressed="<%= instructorMode ? "true" : "false" %>" data-i18n="auth.instructor">Instructor</button>
                        </div>
                        <% String roleError = (String) request.getAttribute("roleError"); if (roleError != null) { %><div class="auth-field-error"><%= roleError %></div><% } %>

                        <div class="etasmi-auth-reg-fields">
                            <div class="auth-field">
                                <label class="auth-label" for="fullName" data-i18n="auth.fullName">Full name</label>
                                <div class="auth-control">
                                    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 12a4 4 0 1 0-4-4 4 4 0 0 0 4 4Z" stroke="currentColor" stroke-width="1.6"/><path d="M4 20a8 8 0 0 1 16 0" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                                    <input id="fullName" type="text" name="fullName" value="<%= fullNameValue == null ? "" : fullNameValue %>" data-i18n="auth.fullNamePlaceholder" data-i18n-attr="placeholder" placeholder="Your full name" required>
                                </div>
                                <% String fullNameError = (String) request.getAttribute("fullNameError"); if (fullNameError != null) { %><div class="auth-field-error"><%= fullNameError %></div><% } %>
                            </div>

                            <div class="auth-field">
                                <label class="auth-label" for="email" data-i18n="auth.email">Email</label>
                                <div class="auth-control">
                                    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 6.75C4 5.784 4.784 5 5.75 5h12.5C19.216 5 20 5.784 20 6.75v10.5c0 .966-.784 1.75-1.75 1.75H5.75C4.784 19 4 18.216 4 17.25V6.75Z" stroke="currentColor" stroke-width="1.6"/><path d="M6 8l6 4 6-4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                    <input id="email" type="email" name="email" value="<%= emailValue == null ? "" : emailValue %>" placeholder="you@example.com" required>
                                </div>
                                <% String emailError = (String) request.getAttribute("emailError"); if (emailError != null) { %><div class="auth-field-error"><%= emailError %></div><% } %>
                            </div>

                            <div class="auth-field auth-field--span-full">
                                <label class="auth-label" for="phone" data-i18n="auth.phone">Phone</label>
                                <div class="auth-control">
                                    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M8.5 3.5h7A2.5 2.5 0 0 1 18 6v12a2.5 2.5 0 0 1-2.5 2.5h-7A2.5 2.5 0 0 1 6 18V6A2.5 2.5 0 0 1 8.5 3.5Z" stroke="currentColor" stroke-width="1.6"/><path d="M10 17.5h4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                                    <input id="phone" type="text" name="phone" value="<%= phoneValue == null ? "" : phoneValue %>" placeholder="+60 12 345 6789" required>
                                </div>
                                <% String phoneError = (String) request.getAttribute("phoneError"); if (phoneError != null) { %><div class="auth-field-error"><%= phoneError %></div><% } %>
                            </div>

                            <div class="auth-field">
                                <label class="auth-label" for="password" data-i18n="auth.password">Password</label>
                                <div class="auth-control">
                                    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M7 10V8a5 5 0 0 1 10 0v2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><path d="M6.5 10h11A2.5 2.5 0 0 1 20 12.5v5A2.5 2.5 0 0 1 17.5 20h-11A2.5 2.5 0 0 1 4 17.5v-5A2.5 2.5 0 0 1 6.5 10Z" stroke="currentColor" stroke-width="1.6"/></svg>
                                    <input id="password" type="password" name="password" placeholder="At least 8 characters" required>
                                </div>
                                <% String passwordError = (String) request.getAttribute("passwordError"); if (passwordError != null) { %><div class="auth-field-error"><%= passwordError %></div><% } %>
                            </div>

                            <div class="auth-field">
                                <label class="auth-label" for="confirmPassword" data-i18n="auth.confirmPassword">Confirm password</label>
                                <div class="auth-control">
                                    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M7 10V8a5 5 0 0 1 10 0v2" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><path d="M6.5 10h11A2.5 2.5 0 0 1 20 12.5v5A2.5 2.5 0 0 1 17.5 20h-11A2.5 2.5 0 0 1 4 17.5v-5A2.5 2.5 0 0 1 6.5 10Z" stroke="currentColor" stroke-width="1.6"/></svg>
                                    <input id="confirmPassword" type="password" name="confirmPassword" placeholder="Re-enter your password" required>
                                </div>
                                <% String confirmPasswordError = (String) request.getAttribute("confirmPasswordError"); if (confirmPasswordError != null) { %><div class="auth-field-error"><%= confirmPasswordError %></div><% } %>
                            </div>

                            <div class="auth-field auth-field--span-full auth-student-fields" data-student-fields="1">
                                <label class="auth-label" for="studentLevel">Education level</label>
                                <div class="auth-control auth-control--select-plain">
                                    <select id="studentLevel" name="studentLevel" data-student-required="1" required>
                                        <option value="" disabled <%= (studentLevelValue == null || studentLevelValue.isBlank()) ? "selected" : "" %>>Select your level</option>
                                        <option value="PRIMARY_SCHOOL" <%= "PRIMARY_SCHOOL".equals(studentLevelValue) ? "selected" : "" %>>Primary school</option>
                                        <option value="SECONDARY_SCHOOL" <%= "SECONDARY_SCHOOL".equals(studentLevelValue) ? "selected" : "" %>>Secondary school</option>
                                        <option value="HIGH_SCHOOL" <%= "HIGH_SCHOOL".equals(studentLevelValue) ? "selected" : "" %>>High school</option>
                                        <option value="UNIVERSITY" <%= "UNIVERSITY".equals(studentLevelValue) ? "selected" : "" %>>University</option>
                                    </select>
                                </div>
                                <% String studentLevelError = (String) request.getAttribute("studentLevelError"); if (studentLevelError != null) { %><div class="auth-field-error"><%= studentLevelError %></div><% } %>
                            </div>

                            <div class="auth-instructor-fields" data-instructor-fields="1">
                                <div class="auth-field">
                                    <label class="auth-label" for="bio">Instructor bio</label>
                                    <div class="auth-control auth-control--textarea">
                                        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M7 4h10a2 2 0 0 1 2 2v14l-4-2-4 2-4-2-4 2V6a2 2 0 0 1 2-2Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/></svg>
                                        <textarea id="bio" name="bio" data-instructor-required="1" rows="3" placeholder="Brief teaching experience and Quran background." required><%= bioValue == null ? "" : bioValue %></textarea>
                                    </div>
                                    <% String bioError = (String) request.getAttribute("bioError"); if (bioError != null) { %><div class="auth-field-error"><%= bioError %></div><% } %>
                                </div>

                                <div class="auth-field">
                                    <label class="auth-label">Qualification (PDF)</label>
                                    <label class="auth-upload" data-upload-shell="1">
                                        <input type="file" name="qualification" accept="application/pdf,.pdf" data-upload-input="1" data-instructor-required="1" required>
                                        <span class="auth-upload-icon" aria-hidden="true">
                                            <svg viewBox="0 0 24 24" fill="none"><path d="M12 16V4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><path d="M8 8l4-4 4 4" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/><path d="M4 20h16" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                                        </span>
                                        <span>
                                            <strong>Upload qualification PDF</strong>
                                            <span class="auth-help">PDF only, up to 10MB.</span>
                                            <span class="auth-upload-name" data-upload-name="1">No file selected</span>
                                        </span>
                                    </label>
                                    <% String qualificationError = (String) request.getAttribute("qualificationError"); if (qualificationError != null) { %><div class="auth-field-error"><%= qualificationError %></div><% } %>
                                </div>

                                <div class="auth-status">
                                    <strong>Verification</strong>
                                    After email verification, an admin reviews your instructor application.
                                </div>
                            </div>
                        </div>

                        <button class="auth-btn" type="submit" data-role-submit="1" data-i18n="<%= instructorMode ? "auth.createInstructorAccount" : "auth.createStudentAccount" %>"><%= instructorMode ? "Create instructor account" : "Create student account" %></button>

                        <div class="auth-meta auth-meta--center">
                            <span data-i18n="auth.alreadyHaveAccount">Already have an account?</span>
                            <a class="auth-link" href="<%= LocaleSupport.localizedUrl(request, "/auth/login") %>" data-i18n="auth.signIn">Sign in</a>
                        </div>
                    </form>
                </div>
            </div>
        </section>
    </div>
</div>
</body>
</html>
