<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.time.ZoneId" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.util.List" %>
<%@ page import="model.entity.Payment" %>
<%@ page import="model.entity.PaymentStatus" %>
<%@ page import="model.entity.PaymentVerificationHistory" %>
<%@ page import="model.entity.InstructorPaymentSettings" %>
<%@ page import="model.entity.SessionMode" %>
<%@ page import="model.entity.StudentLevel" %>
<%@ page import="model.entity.TasmiSession" %>
<%@ page import="model.service.PaymentService" %>
<%!
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("h:mm a");
    private static final DateTimeFormatter TIMELINE_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a").withZone(ZoneId.systemDefault());

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String text(Object val) {
        return val == null ? "" : val.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String amountText(BigDecimal amount) {
        if (amount == null) return "RM 0.00";
        return "RM " + amount.stripTrailingZeros().toPlainString();
    }

    private static String statusLabel(PaymentStatus status) {
        if (status == null) return "Awaiting Payment";
        return status.displayLabel();
    }

    private static String statusChipClass(PaymentStatus status) {
        if (status == null) return "qrpay-chip--neutral";
        switch (status) {
            case APPROVED: return "qrpay-chip--success";
            case REJECTED: return "qrpay-chip--danger";
            case AWAITING_VERIFICATION: return "qrpay-chip--warning";
            default: return "qrpay-chip--neutral";
        }
    }

    private static String actionLabel(String action) {
        if (action == null) return "Updated";
        switch (action.toUpperCase()) {
            case "SUBMITTED": return "Receipt submitted";
            case "RESUBMITTED": return "Receipt resubmitted";
            case "APPROVED": return "Payment approved";
            case "REJECTED": return "Payment rejected";
            default: return action;
        }
    }
%>
<%
    request.setAttribute("activeMenu", "payments");
    String ctx = request.getContextPath();
    PaymentService.QrPaymentCheckout checkout = (PaymentService.QrPaymentCheckout) request.getAttribute("checkout");

    String success = (String) request.getAttribute("success");
    String error = (String) request.getAttribute("error");

    TasmiSession tasmiSession = checkout == null ? null : checkout.getSession();
    Payment payment = checkout == null ? null : checkout.getPayment();
    InstructorPaymentSettings settings = checkout == null ? null : checkout.getSettings();
    String instructorName = checkout == null ? "Instructor" : checkout.getInstructorName();
    String qrUrl = (String) request.getAttribute("qrUrl");
    String receiptUrl = (String) request.getAttribute("receiptUrl");
    List<PaymentVerificationHistory> timeline = checkout == null ? null : checkout.getTimeline();

    PaymentStatus status = payment == null ? null : payment.getPaymentStatus();
    boolean approved = status == PaymentStatus.APPROVED;
    boolean rejected = status == PaymentStatus.REJECTED;
    boolean awaiting = status == PaymentStatus.AWAITING_VERIFICATION;
    boolean hasReceipt = trimToNull(receiptUrl) != null;

    boolean settingsUsable = settings != null && settings.isUsable();
    boolean hasQr = trimToNull(qrUrl) != null;
    boolean canUpload = settingsUsable && !approved && !awaiting;
    boolean receiptFormOpen = Boolean.TRUE.equals(request.getAttribute("receiptFormOpen")) || rejected || (!hasReceipt && !approved);

    String bankName = settings == null ? null : trimToNull(settings.getBankName());
    String accountHolder = settings == null ? null : trimToNull(settings.getAccountHolderName());
    if (accountHolder == null) accountHolder = instructorName;
    String accountNumber = settings == null ? null : trimToNull(settings.getAccountNumber());
    String paymentNotes = settings == null ? null : trimToNull(settings.getPaymentNotes());
    String rejectionReason = payment == null ? null : trimToNull(payment.getVerificationNote());
    String submittedReference = payment == null ? null : trimToNull(payment.getPaymentReference());
    String submittedNote = payment == null ? null : trimToNull(payment.getStudentNote());

    String sessionTitle = tasmiSession == null ? "" :
            (trimToNull(tasmiSession.getTitle()) == null ? ("Session #" + tasmiSession.getSessionId()) : tasmiSession.getTitle());
    String sessionDateText = (tasmiSession == null || tasmiSession.getSessionDate() == null) ? "-" : DATE_FMT.format(tasmiSession.getSessionDate());
    String sessionTimeText = (tasmiSession == null || tasmiSession.getSessionTime() == null) ? "-" : TIME_FMT.format(tasmiSession.getSessionTime());
    StudentLevel sessionLevel = tasmiSession == null ? null : tasmiSession.getLevel();
    SessionMode sessionMode = tasmiSession == null ? null : tasmiSession.getMode();
    String sessionLevelText = sessionLevel == null ? null : sessionLevel.getDisplayName();
    String sessionModeText = sessionMode == null ? null : (sessionMode == SessionMode.ONLINE ? "Online" : "Physical");
    Integer sessionDuration = tasmiSession == null ? null : tasmiSession.getDurationMinutes();
    String referenceNumber = payment == null ? "-" : ("ETP-" + payment.getPaymentId());
    long enrollmentId = checkout == null ? 0 : checkout.getEnrollment().getEnrollmentId();
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.studentQrPaymentTitle">Complete Your Payment - e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <%@ include file="/jsp/common/student_ui_head.jspf" %>
    <link rel="stylesheet" href="<%= ctx %>/css/qr-payment.css?v=20261003-receipt-contain">
    <script defer src="<%= ctx %>/assets/js/app.js"></script>
    <style>
        .qrpay-file-hidden{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap;border:0}
        .qrpay-receipt-preview{display:flex;align-items:center;gap:14px;width:100%;max-width:100%;min-width:0;box-sizing:border-box;overflow:hidden;padding:14px 16px;border:1px solid #e2e8f0;border-radius:16px;background:#fff;box-shadow:0 8px 20px rgba(15,23,42,.05);margin-bottom:14px}
        .qrpay-receipt-preview__thumb{position:relative;display:block;width:60px;height:60px;min-width:60px;max-width:60px;min-height:60px;max-height:60px;flex:0 0 60px;border-radius:12px;overflow:hidden;background:#eef2f7;color:#dc2626}
        .qrpay-receipt-preview__thumb img{position:absolute;inset:0;width:100%;height:100%;max-width:100%;max-height:100%;object-fit:cover;object-position:center;display:block}
        .qrpay-receipt-preview__thumb svg{width:30px;height:30px}
        .qrpay-receipt-preview__meta{display:flex;flex-direction:column;min-width:0;flex:1 1 auto;gap:2px}
        .qrpay-receipt-preview__name{font-weight:700;color:#0f172a;font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
        .qrpay-receipt-preview__size{font-size:12px;color:#64748b}
        .qrpay-receipt-preview__status{display:inline-flex;align-items:center;gap:5px;font-size:11px;font-weight:700;letter-spacing:.03em;color:#15803d;margin-top:4px;text-transform:uppercase}
        .qrpay-receipt-preview__status svg{width:13px;height:13px}
        .qrpay-receipt-preview__remove{flex:0 0 auto;align-self:center;border:1px solid #fecaca;background:#fff;color:#b91c1c;font-size:12px;font-weight:600;padding:7px 14px;border-radius:9px;cursor:pointer;transition:all .15s ease}
        .qrpay-receipt-preview__remove:hover{background:#fef2f2;border-color:#f87171}
        .qrpay-field{display:flex;flex-direction:column;gap:6px;margin-bottom:14px}
        .qrpay-field label{font-size:13px;font-weight:600;color:#334155}
        .qrpay-field input,.qrpay-field textarea{border:1px solid #cbd5e1;border-radius:10px;padding:10px 12px;font-size:14px;font-family:inherit;color:#0f172a;background:#fff}
        .qrpay-field input:focus,.qrpay-field textarea:focus{outline:none;border-color:#6366f1;box-shadow:0 0 0 3px rgba(99,102,241,.15)}
        .qrpay-field textarea{resize:vertical;min-height:64px}
        .qrpay-bank{margin-top:12px;border:1px solid #e2e8f0;border-radius:14px;overflow:hidden}
        .qrpay-bank__row{display:flex;justify-content:space-between;gap:12px;padding:9px 14px;font-size:13.5px}
        .qrpay-bank__row+.qrpay-bank__row{border-top:1px solid #eef2f7}
        .qrpay-bank__row dt{color:#64748b;font-weight:600;margin:0}
        .qrpay-bank__row dd{color:#0f172a;font-weight:700;margin:0;text-align:right;word-break:break-word}
        .qrpay-notes{margin-top:14px;padding:12px 16px;background:#f8fafc;border:1px dashed #cbd5e1;border-radius:12px;font-size:13px;color:#475569;white-space:pre-wrap}
        .qrpay-timeline{list-style:none;margin:14px 0 0;padding:0;display:flex;flex-direction:column;gap:0}
        .qrpay-timeline__item{position:relative;padding:0 0 18px 26px}
        .qrpay-timeline__item:before{content:"";position:absolute;left:6px;top:4px;width:10px;height:10px;border-radius:50%;background:#6366f1;box-shadow:0 0 0 3px rgba(99,102,241,.18)}
        .qrpay-timeline__item:after{content:"";position:absolute;left:10px;top:14px;bottom:0;width:2px;background:#e2e8f0}
        .qrpay-timeline__item:last-child{padding-bottom:0}
        .qrpay-timeline__item:last-child:after{display:none}
        .qrpay-timeline__item--approved:before{background:#16a34a;box-shadow:0 0 0 3px rgba(22,163,74,.18)}
        .qrpay-timeline__item--rejected:before{background:#dc2626;box-shadow:0 0 0 3px rgba(220,38,38,.18)}
        .qrpay-timeline__title{font-weight:700;color:#0f172a;font-size:14px}
        .qrpay-timeline__meta{font-size:12px;color:#64748b;margin-top:2px}
        .qrpay-timeline__reason{font-size:13px;color:#b91c1c;margin-top:4px}
        .qrpay-reject-banner{margin-top:14px;padding:14px 16px;border-radius:14px;background:#fef2f2;border:1px solid #fecaca;color:#991b1b;font-size:14px}
        .qrpay-reject-banner strong{display:block;margin-bottom:4px}
        .qrpay-timeline-wrap{margin-top:20px;padding-top:18px;border-top:1px solid #eef2f7}
        .qrpay-timeline-heading{font-size:13px;font-weight:700;color:#475569;text-transform:uppercase;letter-spacing:.04em;margin:0 0 8px}
    </style>
</head>
<body class="student-premium-page student-package-page student-module-page">
<div class="app-shell">
    <%@ include file="/jsp/common/student_header.jspf" %>
    <div class="app-main">
        <div class="container sd-container student-workspace-shell">
            <div class="student-shell-layout">
                <%@ include file="/jsp/student/student_sidebar.jspf" %>

                <main class="student-shell-content" role="main">
                    <div class="student-workspace-view qrpay-page">
                        <% if (success != null) { %>
                        <div class="qrpay-alert qrpay-alert--success" role="status"><%= text(success) %></div>
                        <% } %>
                        <% if (error != null) { %>
                        <div class="qrpay-alert qrpay-alert--error" role="alert"><%= text(error) %></div>
                        <% } %>

                        <% if (checkout != null && tasmiSession != null && payment != null) { %>

                        <% if (!settingsUsable) { %>
                        <section class="qrpay-card qrpay-card--unavailable" aria-label="Payment unavailable">
                            <div class="qrpay-card__icon" aria-hidden="true">
                                <svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8"/><path d="M12 8v5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><circle cx="12" cy="16" r="1" fill="currentColor"/></svg>
                            </div>
                            <h2 class="qrpay-card__heading" data-i18n="student.qr.unavailableTitle">Payment is not available yet</h2>
                            <p class="qrpay-card__copy" data-i18n="student.qr.unavailableSub">This instructor has not set up their QR payment yet. Please try again later.</p>
                            <div class="qrpay-actions">
                                <a class="qrpay-btn qrpay-btn--ghost" href="<%= LocaleSupport.localizedUrl(request, "/student/enrollments") %>" data-i18n="student.liveSession.back">Back</a>
                                <a class="qrpay-btn qrpay-btn--ghost" href="<%= LocaleSupport.localizedUrl(request, "/student/payments") %>" data-i18n="student.qr.paymentHistory">Payment History</a>
                            </div>
                        </section>
                        <% } else { %>

                        <div class="qrpay-grid">

                            <!-- LEFT — payment area -->
                            <section class="qrpay-card qrpay-card--pay" aria-label="Scan to pay">
                                <header class="qrpay-card__head">
                                    <div class="qrpay-card__head-text">
                                        <h2 class="qrpay-card__title" data-i18n="student.qr.scanToPay">Scan to Pay</h2>
                                        <span class="qrpay-secure">
                                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M7 11V8a5 5 0 0 1 10 0v3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/><rect x="5" y="11" width="14" height="10" rx="2" stroke="currentColor" stroke-width="1.6"/><circle cx="12" cy="16" r="1.2" fill="currentColor"/></svg>
                                            <span data-i18n="student.qr.directTransfer">Direct bank transfer</span>
                                        </span>
                                    </div>
                                    <span class="qrpay-method__tag" data-i18n="student.qr.qrPay">QR Transfer</span>
                                </header>

                                <% if (hasQr) { %>
                                <div class="qrpay-qr">
                                    <div class="qrpay-qr__frame">
                                        <img class="qrpay-qr__img" src="<%= text(qrUrl) %>" alt="Instructor payment QR code">
                                    </div>
                                    <p class="qrpay-qr__hint" data-i18n="student.qr.scanHint">Scan this QR using your banking app</p>
                                </div>
                                <% } %>

                                <% if (awaiting) { %>
                                <div class="qrpay-state qrpay-state--pending" data-i18n="student.qr.pendingState">
                                    Receipt submitted. Waiting for instructor verification.
                                </div>
                                <% } else if (approved) { %>
                                <div class="qrpay-state qrpay-state--success" data-i18n="student.qr.approvedState">
                                    Payment approved. Your enrollment is confirmed.
                                </div>
                                <% } %>

                                <% if (rejected && rejectionReason != null) { %>
                                <div class="qrpay-reject-banner">
                                    <strong data-i18n="student.qr.rejectedTitle">Your previous receipt was rejected</strong>
                                    <span><%= text(rejectionReason) %></span>
                                </div>
                                <% } %>

                                <% if (hasReceipt) { %>
                                <a class="qrpay-receipt-link" href="<%= text(receiptUrl) %>" target="_blank" rel="noopener">
                                    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M8 5h7l4 4v10a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z" stroke="currentColor" stroke-width="1.6"/><path d="M14 5v5h5" stroke="currentColor" stroke-width="1.6"/></svg>
                                    <span data-i18n="student.qr.viewSubmittedReceipt">View submitted receipt</span>
                                </a>
                                <% } %>

                                <% if (canUpload) { %>
                                <form class="qrpay-upload-form<%= receiptFormOpen ? " is-open" : "" %>" method="post"
                                      action="<%= ctx %>/student/payments/qr" enctype="multipart/form-data" data-receipt-form>
                                    <input type="hidden" name="enrollmentId" value="<%= enrollmentId %>">
                                    <input type="hidden" name="action" value="submitReceipt">
                                    <input id="receipt" name="receipt" type="file" accept="image/*,application/pdf" required class="qrpay-file-hidden" data-receipt-input>

                                    <div class="qrpay-receipt-preview" data-receipt-preview hidden>
                                        <span class="qrpay-receipt-preview__thumb" data-receipt-thumb aria-hidden="true"></span>
                                        <span class="qrpay-receipt-preview__meta">
                                            <span class="qrpay-receipt-preview__name" data-receipt-name></span>
                                            <span class="qrpay-receipt-preview__size" data-receipt-size></span>
                                            <span class="qrpay-receipt-preview__status" data-receipt-status>
                                                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 12l4 4L19 6" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                                <span data-i18n="student.qr.readyToSubmit">Ready to Submit</span>
                                            </span>
                                        </span>
                                        <button type="button" class="qrpay-receipt-preview__remove" data-receipt-remove aria-label="Remove selected receipt">Remove</button>
                                    </div>

                                    <div class="qrpay-actions">
                                        <a class="qrpay-btn qrpay-btn--ghost" href="<%= LocaleSupport.localizedUrl(request, "/student/enrollments") %>" data-i18n="student.liveSession.back">Back</a>
                                        <button class="qrpay-btn qrpay-btn--secondary" type="button" data-receipt-trigger>
                                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 16V4m0 0l-4 4m4-4l4 4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><path d="M4 17v1a3 3 0 0 0 3 3h10a3 3 0 0 0 3-3v-1" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                                            <span data-i18n="<%= hasReceipt ? "student.qr.replaceReceipt" : "student.qr.uploadReceipt" %>"><%= hasReceipt ? "Replace Receipt" : "Upload Receipt" %></span>
                                        </button>
                                        <button class="qrpay-btn qrpay-btn--primary" type="submit" data-receipt-submit hidden>
                                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 12l4 4L19 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                            <span data-i18n="student.qr.submitReceipt">Submit Receipt</span>
                                        </button>
                                    </div>
                                </form>
                                <% } else { %>
                                <div class="qrpay-actions">
                                    <a class="qrpay-btn qrpay-btn--ghost" href="<%= LocaleSupport.localizedUrl(request, "/student/enrollments") %>" data-i18n="student.liveSession.back">Back</a>
                                    <a class="qrpay-btn qrpay-btn--ghost" href="<%= LocaleSupport.localizedUrl(request, "/student/payments") %>" data-i18n="student.qr.paymentHistory">Payment History</a>
                                </div>
                                <% } %>
                            </section>

                            <!-- RIGHT — session details + status -->
                            <aside class="qrpay-card qrpay-card--details" aria-label="Session details">
                                <header class="qrpay-card__head">
                                    <div class="qrpay-card__head-text">
                                        <h2 class="qrpay-card__title" data-i18n="student.qr.sessionDetails">Session Details</h2>
                                        <span class="qrpay-card__subtitle" data-i18n="student.qr.bookingSummary">Booking summary</span>
                                    </div>
                                    <span class="qrpay-chip <%= statusChipClass(status) %>"><%= text(statusLabel(status)) %></span>
                                </header>

                                <h3 class="qrpay-session-title"><%= text(sessionTitle) %></h3>

                                <dl class="qrpay-info">
                                    <div class="qrpay-info__row">
                                        <dt>
                                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="12" cy="8" r="4" stroke="currentColor" stroke-width="1.7"/><path d="M4 21a8 8 0 0 1 16 0" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
                                            <span data-i18n="common.instructor">Instructor</span>
                                        </dt>
                                        <dd><%= text(instructorName) %></dd>
                                    </div>
                                    <div class="qrpay-info__row">
                                        <dt>
                                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="4" y="5" width="16" height="15" rx="2" stroke="currentColor" stroke-width="1.7"/><path d="M4 9h16M9 3v4m6-4v4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
                                            <span data-i18n="common.date">Date</span>
                                        </dt>
                                        <dd><%= text(sessionDateText) %></dd>
                                    </div>
                                    <div class="qrpay-info__row">
                                        <dt>
                                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.7"/><path d="M12 7v5l3 2" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
                                            <span data-i18n="common.time">Time</span><% if (sessionDuration != null && sessionDuration > 0) { %> <span class="qrpay-info__hint">(<%= sessionDuration %> min)</span><% } %>
                                        </dt>
                                        <dd><%= text(sessionTimeText) %></dd>
                                    </div>
                                    <% if (sessionLevelText != null) { %>
                                    <div class="qrpay-info__row">
                                        <dt>
                                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M3 12l9-7 9 7-9 7-9-7z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/><path d="M7 14v4a5 5 0 0 0 10 0v-4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
                                            <span data-i18n="student.profile.level">Level</span>
                                        </dt>
                                        <dd><%= text(sessionLevelText) %></dd>
                                    </div>
                                    <% } %>
                                    <% if (sessionModeText != null) { %>
                                    <div class="qrpay-info__row">
                                        <dt>
                                            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="3" y="5" width="18" height="12" rx="2" stroke="currentColor" stroke-width="1.7"/><path d="M8 21h8M12 17v4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
                                            <span data-i18n="student.qr.mode">Mode</span>
                                        </dt>
                                        <dd><%= text(sessionModeText) %></dd>
                                    </div>
                                    <% } %>
                                </dl>

                                <div class="qrpay-amount-section">
                                    <div class="qrpay-amount-section__head">
                                        <span class="qrpay-amount-eyebrow" data-i18n="student.qr.totalAmount">Total Amount</span>
                                        <span class="qrpay-amount-currency">MYR</span>
                                    </div>
                                    <span class="qrpay-amount"><%= amountText(checkout.getAmount()) %></span>
                                    <span class="qrpay-amount-holder"><span data-i18n="student.qr.payTo">Pay to</span> <%= text(accountHolder) %></span>
                                </div>

                                <% if (timeline != null && !timeline.isEmpty()) { %>
                                <div class="qrpay-timeline-wrap">
                                    <h4 class="qrpay-timeline-heading" data-i18n="student.qr.timeline">Verification timeline</h4>
                                    <ul class="qrpay-timeline">
                                        <% for (PaymentVerificationHistory h : timeline) {
                                            String act = h.getAction() == null ? "" : h.getAction().toUpperCase();
                                            String itemClass = "APPROVED".equals(act) ? " qrpay-timeline__item--approved"
                                                    : ("REJECTED".equals(act) ? " qrpay-timeline__item--rejected" : "");
                                            String when = h.getCreatedAt() == null ? "" : TIMELINE_FMT.format(h.getCreatedAt());
                                            String actor = trimToNull(h.getActorName());
                                            String reason = trimToNull(h.getReason());
                                        %>
                                        <li class="qrpay-timeline__item<%= itemClass %>">
                                            <div class="qrpay-timeline__title"><%= text(actionLabel(h.getAction())) %></div>
                                            <div class="qrpay-timeline__meta"><%= text(when) %><% if (actor != null) { %> · <%= text(actor) %><% } %></div>
                                            <% if (reason != null) { %><div class="qrpay-timeline__reason"><%= text(reason) %></div><% } %>
                                        </li>
                                        <% } %>
                                    </ul>
                                </div>
                                <% } %>

                                <footer class="qrpay-card__foot">
                                    <span class="qrpay-ref"><span data-i18n="student.qr.ref">Ref.</span> <strong><%= text(referenceNumber) %></strong></span>
                                    <span class="qrpay-poweredby"><span data-i18n="student.qr.poweredBy">Powered by</span> <strong>e-Tasmi</strong></span>
                                </footer>
                            </aside>

                        </div>

                        <% } %>
                        <% } else { %>
                        <section class="qrpay-card qrpay-card--unavailable" aria-label="Payment unavailable">
                            <div class="qrpay-card__icon" aria-hidden="true">
                                <svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8"/><path d="M12 8v5" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><circle cx="12" cy="16" r="1" fill="currentColor"/></svg>
                            </div>
                            <h2 class="qrpay-card__heading" data-i18n="student.qr.unavailableAltTitle">Payment is unavailable</h2>
                            <p class="qrpay-card__copy" data-i18n="student.qr.unavailableAltSub">Start from Available Sessions first.</p>
                            <div class="qrpay-actions">
                                <a class="qrpay-btn qrpay-btn--primary" href="<%= LocaleSupport.localizedUrl(request, "/student/available-sessions") %>" data-i18n="student.qr.openAvailableSessions">Open Available Sessions</a>
                            </div>
                        </section>
                        <% } %>
                    </div>
                    <%@ include file="/jsp/common/app_footer.jspf" %>
                </main>
            </div>
        </div>
    </div>
</div>
<script>
(() => {
  const form = document.querySelector('[data-receipt-form]');
  if (!form) return;
  const input = form.querySelector('[data-receipt-input]');
  const trigger = form.querySelector('[data-receipt-trigger]');
  const submitBtn = form.querySelector('[data-receipt-submit]');
  const preview = form.querySelector('[data-receipt-preview]');
  const thumb = form.querySelector('[data-receipt-thumb]');
  const nameEl = form.querySelector('[data-receipt-name]');
  const sizeEl = form.querySelector('[data-receipt-size]');
  const removeBtn = form.querySelector('[data-receipt-remove]');

  const PDF_ICON = '<svg viewBox="0 0 24 24" fill="none"><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><path d="M14 3v5h5" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/><text x="12" y="18" text-anchor="middle" font-size="6" font-weight="700" fill="currentColor">PDF</text></svg>';
  let previewUrl = '';
  const clearPreviewUrl = () => {
    if (previewUrl) {
      URL.revokeObjectURL(previewUrl);
      previewUrl = '';
    }
  };

  const fmtSize = (bytes) => {
    if (!bytes) return '';
    const u = ['B', 'KB', 'MB']; let i = 0, n = bytes;
    while (n >= 1024 && i < u.length - 1) { n /= 1024; i++; }
    return (n < 10 && i > 0 ? n.toFixed(1) : Math.round(n)) + ' ' + u[i];
  };

  const resetSelection = () => {
    clearPreviewUrl();
    if (input) input.value = '';
    if (thumb) thumb.innerHTML = '';
    if (nameEl) nameEl.textContent = '';
    if (sizeEl) sizeEl.textContent = '';
    if (preview) preview.hidden = true;
    if (submitBtn) submitBtn.hidden = true;
    if (trigger) trigger.hidden = false;
  };

  if (trigger) trigger.addEventListener('click', () => { if (input) input.click(); });
  if (removeBtn) removeBtn.addEventListener('click', resetSelection);

  if (input) input.addEventListener('change', () => {
    if (!input.files || !input.files.length) { resetSelection(); return; }
    const file = input.files[0];
    const isImage = file.type && file.type.indexOf('image/') === 0;

    if (nameEl) nameEl.textContent = file.name;
    if (sizeEl) sizeEl.textContent = fmtSize(file.size);
    if (thumb) {
      clearPreviewUrl();
      thumb.innerHTML = '';
      if (isImage) {
        previewUrl = URL.createObjectURL(file);
        const img = document.createElement('img');
        img.src = previewUrl;
        img.alt = 'Receipt preview';
        thumb.appendChild(img);
      } else {
        thumb.innerHTML = PDF_ICON;
      }
    }
    if (preview) preview.hidden = false;
    if (trigger) trigger.hidden = true;
    if (submitBtn) submitBtn.hidden = false;
  });
})();
</script>
</body>
</html>
