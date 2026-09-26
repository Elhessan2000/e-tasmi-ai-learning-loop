<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
<%@ page import="java.time.ZoneId" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.util.Map" %>
<%@ page import="model.entity.Enrollment" %>
<%@ page import="model.entity.Payment" %>
<%@ page import="model.entity.TasmiSession" %>
<%!
    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String amountText(java.math.BigDecimal amount) {
        if (amount == null) return "RM 0.00";
        return "RM " + amount.stripTrailingZeros().toPlainString();
    }

    private static String formatInstant(java.time.Instant instant) {
        if (instant == null) return "-";
        return DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }
%>
<%
    Map<String, Object> receipt = (Map<String, Object>) request.getAttribute("receipt");
    boolean downloadMode = Boolean.TRUE.equals(request.getAttribute("downloadMode"));

    Payment payment = receipt == null ? null : (Payment) receipt.get("payment");
    Enrollment enrollment = receipt == null ? null : (Enrollment) receipt.get("enrollment");
    TasmiSession tasmiSession = receipt == null ? null : (TasmiSession) receipt.get("session");
    String studentName = receipt == null ? "Student" : String.valueOf(receipt.get("studentName"));
    String studentEmail = receipt == null ? null : (String) receipt.get("studentEmail");
    String instructorName = receipt == null ? "Instructor" : String.valueOf(receipt.get("instructorName"));
    String referenceNumber = receipt == null ? "-" : String.valueOf(receipt.get("referenceNumber"));
    String sessionType = receipt == null ? "Guided session" : String.valueOf(receipt.get("sessionType"));
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.studentPaymentReceiptTitle">Payment Receipt - e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <%@ include file="/jsp/common/student_ui_head.jspf" %>
</head>
<body class="student-package-page student-receipt-page">
<div class="receipt-shell">
    <section class="receipt-card" data-i18n="student.receipt.aria" data-i18n-attr="aria-label" aria-label="Payment receipt">
        <div class="receipt-header">
            <div>
                <div class="receipt-eyebrow">e-Tasmi</div>
                <h1 data-i18n="student.receipt.title">Payment Receipt</h1>
                <p class="receipt-subtitle" data-i18n="student.receipt.subtitle">This receipt was generated from the current payment and session records stored in your account.</p>
            </div>
            <div class="receipt-badge"><span data-i18n="student.receipt.status">Status:</span> <strong><%= payment == null ? "-" : payment.getPaymentStatus() %></strong></div>
        </div>

        <% if (!downloadMode) { %>
        <div class="receipt-actions">
            <a class="btn btn-ghost" href="<%= LocaleSupport.localizedUrl(request, "/student/payments") %>" data-i18n="student.receipt.backToHistory">Back to Payment History</a>
            <button class="btn btn-ghost" type="button" onclick="window.print()" data-i18n="student.receipt.printReceipt">Print Receipt</button>
            <a class="btn btn-primary" href="<%= request.getContextPath() %>/student/payments/receipt?paymentId=<%= payment == null ? 0 : payment.getPaymentId() %>&download=1" data-i18n="student.receipt.downloadReceipt">Download Receipt</a>
        </div>
        <% } %>

        <div class="receipt-grid">
            <section class="receipt-section">
                <div class="receipt-section-title" data-i18n="student.receipt.receiptDetails">Receipt Details</div>
                <div class="receipt-row"><span data-i18n="student.receipt.referenceNumber">Reference Number</span><strong><%= referenceNumber %></strong></div>
                <div class="receipt-row"><span data-i18n="student.receipt.paymentDate">Payment Date</span><strong><%= payment == null ? "-" : formatInstant(payment.getPaymentDate()) %></strong></div>
                <div class="receipt-row"><span data-i18n="student.receipt.paymentStatus">Payment Status</span><strong><%= payment == null ? "-" : payment.getPaymentStatus() %></strong></div>
                <div class="receipt-row"><span data-i18n="student.receipt.amountPaid">Amount Paid</span><strong><%= payment == null ? "RM 0.00" : amountText(payment.getAmount()) %></strong></div>
            </section>

            <section class="receipt-section">
                <div class="receipt-section-title" data-i18n="student.receipt.studentSection">Student</div>
                <div class="receipt-row"><span data-i18n="student.receipt.name">Name</span><strong><%= studentName %></strong></div>
                <div class="receipt-row"><span data-i18n="student.receipt.email">Email</span><strong><%= studentEmail == null ? "-" : studentEmail %></strong></div>
                <div class="receipt-row"><span data-i18n="student.receipt.enrollmentId">Enrollment ID</span><strong><%= enrollment == null ? "-" : enrollment.getEnrollmentId() %></strong></div>
                <div class="receipt-row"><span data-i18n="student.receipt.registrationStatus">Registration Status</span><strong><%= enrollment == null ? "-" : enrollment.getEnrollmentStatus() %></strong></div>
            </section>
        </div>

        <section class="receipt-section receipt-section--full">
            <div class="receipt-section-title" data-i18n="student.receipt.sessionInfo">Session Information</div>
            <div class="receipt-grid receipt-grid--session">
                <div class="receipt-row"><span data-i18n="student.receipt.sessionTitle">Session Title</span><strong><%= tasmiSession == null || trimToNull(tasmiSession.getTitle()) == null ? "-" : tasmiSession.getTitle() %></strong></div>
                <div class="receipt-row"><span data-i18n="common.instructor">Instructor</span><strong><%= instructorName %></strong></div>
                <div class="receipt-row"><span data-i18n="student.receipt.topicSurah">Topic / Surah</span><strong><%= tasmiSession == null || trimToNull(tasmiSession.getQuranPortion()) == null ? "-" : tasmiSession.getQuranPortion() %></strong></div>
                <div class="receipt-row"><span data-i18n="student.receipt.sessionDate">Session Date</span><strong><%= tasmiSession == null || tasmiSession.getSessionDate() == null ? "-" : tasmiSession.getSessionDate() %></strong></div>
                <div class="receipt-row"><span data-i18n="student.receipt.sessionTime">Session Time</span><strong><%= tasmiSession == null || tasmiSession.getSessionTime() == null ? "-" : tasmiSession.getSessionTime() %></strong></div>
                <div class="receipt-row"><span data-i18n="student.receipt.sessionType">Session Type</span><strong><%= sessionType %></strong></div>
                <div class="receipt-row"><span data-i18n="student.receipt.duration">Duration</span><strong><%= tasmiSession == null || tasmiSession.getDurationMinutes() == null ? "-" : tasmiSession.getDurationMinutes() + " minutes" %></strong></div>
                <div class="receipt-row"><span data-i18n="student.receipt.sessionStatus">Session Status</span><strong><%= tasmiSession == null || tasmiSession.getStatus() == null ? "-" : tasmiSession.getStatus() %></strong></div>
            </div>
        </section>

        <section class="receipt-total">
            <span data-i18n="student.receipt.totalPaid">Total Paid</span>
            <strong><%= payment == null ? "RM 0.00" : amountText(payment.getAmount()) %></strong>
        </section>
    </section>
</div>
</body>
</html>
