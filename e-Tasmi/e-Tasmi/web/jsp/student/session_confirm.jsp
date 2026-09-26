<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.util.Map" %>
<%@ page import="model.entity.TasmiSession" %>
<%!
    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception ex) {
            return "";
        }
    }
%>
<%
    request.setAttribute("activeMenu", "sessions");

    String ctx = request.getContextPath();
    Map<String, Object> selectedSessionCard = (Map<String, Object>) request.getAttribute("selectedSessionCard");
    TasmiSession selectedSession = selectedSessionCard == null ? null : (TasmiSession) selectedSessionCard.get("session");
    String selectedInstructorName = selectedSessionCard == null || selectedSessionCard.get("instructorName") == null ? "Instructor" : String.valueOf(selectedSessionCard.get("instructorName"));
    String selectedFeeLabel = selectedSessionCard == null || selectedSessionCard.get("feeLabel") == null ? "Free session" : String.valueOf(selectedSessionCard.get("feeLabel"));
    String searchQuery = request.getAttribute("searchQuery") == null ? "" : String.valueOf(request.getAttribute("searchQuery"));
    String from = request.getAttribute("from") == null ? null : String.valueOf(request.getAttribute("from"));
    String success = (String) request.getAttribute("success");
    String error = (String) request.getAttribute("error");

    String backHref = ctx + "/student/available-sessions";
    if ("dashboard".equalsIgnoreCase(from)) {
        backHref = ctx + "/student/dashboard";
    } else if (trimToNull(searchQuery) != null) {
        backHref = ctx + "/student/available-sessions?q=" + urlEncode(searchQuery);
    }
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.studentSessionConfirmTitle">Session Confirmation - e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <%@ include file="/jsp/common/student_ui_head.jspf" %>
    <link rel="stylesheet" href="<%= ctx %>/css/qr-payment.css?v=20260417-qrpay1">
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
                    <div class="student-workspace-view qrbank-page">
                        <section class="qrbank-shell qrbank-shell--narrow" data-i18n="student.sessionConfirm.aria" data-i18n-attr="aria-label" aria-label="Session confirmation">
                            <div class="qrbank-step" data-i18n="student.sessionConfirm.step">Enroll</div>
                            <h1 class="qrbank-title" data-i18n="student.sessionConfirm.title">Session payment</h1>
                            <p class="qrbank-subtitle" data-i18n="student.sessionConfirm.subtitle">Review your session before continuing.</p>
                        </section>

                        <% if (success != null) { %>
                        <div class="alert alert-success"><%= success %></div>
                        <% } %>

                        <% if (error != null) { %>
                        <div class="alert alert-error"><%= error %></div>
                        <% } %>

                        <section class="qrbank-entry" data-i18n="student.sessionConfirm.paymentAria" data-i18n-attr="aria-label" aria-label="Selected session payment">
                            <div class="qrbank-card qrbank-entry-card">
                                <div class="qrbank-card-head">
                                    <div>
                                        <span data-i18n="student.sessionConfirm.selectedSession">Selected Session</span>
                                        <h2><%= selectedSession == null || trimToNull(selectedSession.getTitle()) == null ? "Session" : selectedSession.getTitle() %></h2>
                                    </div>
                                </div>

                                <div class="qrbank-amount"><%= selectedFeeLabel %></div>

                                <div class="qrbank-detail-list">
                                    <div><span data-i18n="student.sessionConfirm.sessionName">Session Name</span><strong><%= selectedSession == null || trimToNull(selectedSession.getTitle()) == null ? "Session" : selectedSession.getTitle() %></strong></div>
                                    <div><span data-i18n="common.instructor">Instructor</span><strong><%= selectedInstructorName %></strong></div>
                                    <div><span data-i18n="student.payments.colAmount">Amount</span><strong><%= selectedFeeLabel %></strong></div>
                                    <div><span data-i18n="student.recitations.dateTime">Date & Time</span><strong><%= selectedSession == null || selectedSession.getSessionDate() == null ? "-" : selectedSession.getSessionDate() %> at <%= selectedSession == null || selectedSession.getSessionTime() == null ? "-" : selectedSession.getSessionTime() %></strong></div>
                                </div>

                                <div class="qrbank-actions qrbank-actions--end">
                                    <form class="inline-form" method="post" action="<%= ctx %>/student/enroll-confirm">
                                        <input type="hidden" name="action" value="checkout"/>
                                        <input type="hidden" name="sessionId" value="<%= selectedSession == null ? 0 : selectedSession.getSessionId() %>"/>
                                        <% if (trimToNull(searchQuery) != null) { %>
                                        <input type="hidden" name="q" value="<%= searchQuery %>"/>
                                        <% } %>
                                        <% if (trimToNull(from) != null) { %>
                                        <input type="hidden" name="from" value="<%= from %>"/>
                                        <% } %>
                                        <button class="btn btn-primary" type="submit"><% if ("Free session".equals(selectedFeeLabel)) { %><span data-i18n="student.sessionConfirm.confirmEnrollment">Confirm Enrollment</span><% } else { %><span data-i18n="student.sessionConfirm.pay">Pay</span><% } %></button>
                                    </form>
                                    <a class="btn btn-ghost" href="<%= backHref %>" data-i18n="common.back">Back</a>
                                </div>
                            </div>
                        </section>
                    </div>
                    <%@ include file="/jsp/common/app_footer.jspf" %>
                </main>
            </div>
        </div>
    </div>
</div>
</body>
</html>
