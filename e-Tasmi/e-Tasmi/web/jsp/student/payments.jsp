<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.time.Instant" %>
<%@ page import="java.time.ZoneId" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="model.entity.Enrollment" %>
<%@ page import="model.entity.Payment" %>
<%@ page import="model.entity.PaymentStatus" %>
<%@ page import="model.entity.TasmiSession" %>
<%!
    private static String esc(Object val) {
        if (val == null) {
            return "";
        }
        return String.valueOf(val)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String amountText(BigDecimal amount) {
        if (amount == null) {
            return "RM 0";
        }
        return "RM " + amount.stripTrailingZeros().toPlainString();
    }

    private static String receiptDisplayId(Payment payment) {
        if (payment == null) {
            return "—";
        }
        Instant inst = payment.getPaymentDate() != null ? payment.getPaymentDate() : payment.getReceiptSubmittedAt();
        if (inst == null) {
            inst = Instant.now();
        }
        int year = java.time.ZonedDateTime.ofInstant(inst, ZoneId.systemDefault()).getYear();
        long pid = payment.getPaymentId();
        return String.format("RCP-%d-%03d", year, pid % 1000);
    }

    private static String tableDateIso(Payment payment) {
        if (payment == null) {
            return "—";
        }
        Instant inst = payment.getPaymentDate() != null ? payment.getPaymentDate() : payment.getReceiptSubmittedAt();
        if (inst == null) {
            return "—";
        }
        return DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault()).format(inst);
    }

    private static String payBadgeClass(Payment payment) {
        if (payment == null || payment.getPaymentStatus() == null) {
            return "pay-badge pay-badge--pending";
        }
        switch (payment.getPaymentStatus()) {
            case APPROVED:
                return "pay-badge pay-badge--paid";
            case REJECTED:
                return "pay-badge pay-badge--reject";
            case AWAITING_VERIFICATION:
                return "pay-badge pay-badge--verification";
            case PENDING:
            default:
                return "pay-badge pay-badge--pending";
        }
    }

    private static String payStatusShort(Payment payment) {
        if (payment == null || payment.getPaymentStatus() == null) {
            return "Pending";
        }
        switch (payment.getPaymentStatus()) {
            case APPROVED:
                return "Paid";
            case REJECTED:
                return "Rejected";
            case AWAITING_VERIFICATION:
                return "Verification pending";
            case PENDING:
            default:
                return "Pending";
        }
    }
%>
<%
    request.setAttribute("activeMenu", "payments");
    String ctx = request.getContextPath();

    List<Map<String, Object>> historyCards = (List<Map<String, Object>>) request.getAttribute("historyCards");
    int total = historyCards == null ? 0 : historyCards.size();
    String success = (String) request.getAttribute("success");
    String error = (String) request.getAttribute("error");

    BigDecimal sumPaid = BigDecimal.ZERO;
    BigDecimal sumPending = BigDecimal.ZERO;
    if (historyCards != null) {
        for (Map<String, Object> card : historyCards) {
            Payment p = (Payment) card.get("payment");
            BigDecimal amt = card.get("amountPaid") instanceof BigDecimal
                    ? (BigDecimal) card.get("amountPaid")
                    : (p != null ? p.getAmount() : BigDecimal.ZERO);
            if (amt == null) {
                amt = BigDecimal.ZERO;
            }
            if (p != null && p.getPaymentStatus() == PaymentStatus.APPROVED) {
                sumPaid = sumPaid.add(amt);
            }
            if (p != null && (p.getPaymentStatus() == PaymentStatus.PENDING
                    || p.getPaymentStatus() == PaymentStatus.AWAITING_VERIFICATION
                    || p.getPaymentStatus() == PaymentStatus.REJECTED)) {
                sumPending = sumPending.add(amt);
            }
        }
    }
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.studentPaymentsTitle">Payment History - e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <%@ include file="/jsp/common/student_ui_head.jspf" %>
    <link rel="stylesheet" href="<%= ctx %>/css/student-payments.css?v=20260614-pay-minihead1">
    <link rel="stylesheet" href="<%= ctx %>/css/student-ui-refine.css?v=20260420-student-sidebar1">
    <script defer src="<%= ctx %>/assets/js/app.js"></script>
</head>
<body class="student-premium-page student-package-page student-module-page student-payments-page">
<div class="app-shell">
    <%@ include file="/jsp/common/student_header.jspf" %>
    <div class="app-main">
        <div class="container sd-container student-workspace-shell">
            <div class="student-shell-layout">
                <%@ include file="/jsp/student/student_sidebar.jspf" %>

                <main class="student-shell-content" role="main">
                    <div class="student-workspace-view">
                        <%@ include file="/jsp/common/student_breadcrumb.jspf" %>
                        <header class="student-hero student-hero--payments">
                            <div class="student-hero__copy">
                                <h1 class="student-hero__title" data-i18n="student.payments.title">Payment History</h1>
                                <p class="student-hero__sub" data-i18n="student.payments.subtitle">Track all your payment transactions</p>
                            </div>
                            <div class="student-hero__visual" aria-hidden="true">
                                <span class="student-hero__badge">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                        <rect x="3" y="6" width="18" height="13" rx="2" stroke="currentColor" stroke-width="1.7"/>
                                        <path d="M3 10h18" stroke="currentColor" stroke-width="1.7"/>
                                        <path d="M7 15h4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>
                                    </svg>
                                </span>
                            </div>
                        </header>

                        <% if (success != null) { %>
                        <div class="pay-alert pay-alert--success" role="status">
                            <svg viewBox="0 0 24 24" fill="none"><path d="M9 12l2 2 4-4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/></svg>
                            <%= esc(success) %>
                        </div>
                        <% } %>
                        <% if (error != null) { %>
                        <div class="pay-alert pay-alert--error" role="alert">
                            <svg viewBox="0 0 24 24" fill="none"><path d="M12 8v4m0 4h.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/></svg>
                            <%= esc(error) %>
                        </div>
                        <% } %>

                        <section class="pay-summary" data-i18n="student.payments.summaryAria" data-i18n-attr="aria-label" aria-label="Payment summary">
                            <article class="pay-stat pay-stat--teal">
                                <div class="pay-stat__head">
                                    <span class="pay-stat__label" data-i18n="student.payments.totalPaid">Total paid</span>
                                    <span class="pay-stat__icon" aria-hidden="true">
                                        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                    </span>
                                </div>
                                <div class="pay-stat__value"><%= esc(amountText(sumPaid)) %></div>
                                <div class="pay-stat__footer">
                                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M20 6 9 17l-5-5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                    <span data-i18n="student.payments.confirmedPayments">Confirmed payments</span>
                                </div>
                            </article>
                            <article class="pay-stat pay-stat--orange">
                                <div class="pay-stat__head">
                                    <span class="pay-stat__label" data-i18n="student.payments.pending">Pending</span>
                                    <span class="pay-stat__icon" aria-hidden="true">
                                        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2"/><path d="M12 7v5l3 3" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                                    </span>
                                </div>
                                <div class="pay-stat__value"><%= esc(amountText(sumPending)) %></div>
                                <div class="pay-stat__footer">
                                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="2"/><path d="M12 7v5l3 3" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                                    <span data-i18n="student.payments.awaitingVerification">Awaiting verification</span>
                                </div>
                            </article>
                            <article class="pay-stat pay-stat--blue">
                                <div class="pay-stat__head">
                                    <span class="pay-stat__label" data-i18n="student.payments.totalTransactions">Total transactions</span>
                                    <span class="pay-stat__icon" aria-hidden="true">
                                        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                                    </span>
                                </div>
                                <div class="pay-stat__value"><%= total %></div>
                                <div class="pay-stat__footer">
                                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="3" fill="currentColor"/></svg>
                                    <span data-i18n="student.payments.allTimeRecords">All time records</span>
                                </div>
                            </article>
                        </section>

                        <section class="pay-table-card" aria-labelledby="payHistoryTitle">
                            <div class="pay-table-card__head">
                                <svg width="22" height="22" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                                <h2 id="payHistoryTitle" class="pay-table-card__title" data-i18n="student.payments.transactionHistory">Transaction History</h2>
                            </div>

                            <% if (historyCards == null || historyCards.isEmpty()) { %>
                            <div class="pay-empty">
                                <strong data-i18n="student.payments.emptyTitle">No payment records yet</strong>
                                <span data-i18n="student.payments.emptySub">Once you enroll in a paid session, payment status and receipts will appear here.</span>
                            </div>
                            <% } else { %>
                            <div class="pay-table-wrap">
                                <table class="pay-table">
                                    <thead>
                                    <tr>
                                        <th scope="col" data-i18n="student.payments.colDate">Date</th>
                                        <th scope="col" data-i18n="student.payments.colSession">Session</th>
                                        <th scope="col" data-i18n="student.payments.colAmount">Amount</th>
                                        <th scope="col" data-i18n="student.payments.colStatus">Status</th>
                                        <th scope="col" data-i18n="student.payments.colReceipt">Receipt</th>
                                        <th scope="col" data-i18n="student.payments.colActions">Actions</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <% for (Map<String, Object> card : historyCards) {
                                           TasmiSession tasmiSession = (TasmiSession) card.get("session");
                                           Payment payment = (Payment) card.get("payment");
                                           Enrollment enrollment = (Enrollment) card.get("enrollment");
                                           String sessionTitle = tasmiSession == null || tasmiSession.getTitle() == null ? "—" : tasmiSession.getTitle();
                                           BigDecimal amountPaid = card.get("amountPaid") instanceof BigDecimal
                                                   ? (BigDecimal) card.get("amountPaid")
                                                   : (payment == null ? BigDecimal.ZERO : payment.getAmount());
                                           if (amountPaid == null) {
                                               amountPaid = BigDecimal.ZERO;
                                           }
                                           String receiptCode = receiptDisplayId(payment);
                                           String rowDate = tableDateIso(payment);
                                    %>
                                    <tr>
                                        <td class="pay-table__date"><%= esc(rowDate) %></td>
                                        <td class="pay-table__session"><%= esc(sessionTitle) %></td>
                                        <td class="pay-table__amount"><%= esc(amountText(amountPaid)) %></td>
                                        <td><span class="<%= payBadgeClass(payment) %>"><%= esc(payStatusShort(payment)) %></span></td>
                                        <td class="pay-table__receipt"><%= esc(receiptCode) %></td>
                                        <td>
                                            <% if (payment != null && payment.getPaymentStatus() == PaymentStatus.APPROVED) { %>
                                            <a class="pay-download" href="<%= ctx %>/student/payments/receipt?paymentId=<%= payment.getPaymentId() %>&amp;download=1">
                                                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M12 4v12m0 0 4-4m-4 4-4-4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><path d="M5 19h14" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                                                <span data-i18n="student.payments.download">Download</span>
                                            </a>
                                            <% } else { %>
                                            <a class="pay-action-muted" href="<%= LocaleSupport.localizedUrl(request, "/student/payments/qr?enrollmentId=" + (enrollment == null ? 0 : enrollment.getEnrollmentId())) %>">
                                                <% if (payment != null && payment.getPaymentStatus() == PaymentStatus.REJECTED) { %>
                                                <span data-i18n="student.payments.resubmit">Retry payment</span>
                                                <% } else if (payment != null && payment.getPaymentStatus() == PaymentStatus.AWAITING_VERIFICATION) { %>
                                                <span data-i18n="student.payments.viewStatus">View status</span>
                                                <% } else if (payment != null && payment.getPaymentStatus() == PaymentStatus.PENDING) { %>
                                                <span data-i18n="student.payments.viewStatus">Complete payment</span>
                                                <% } else { %>
                                                <span data-i18n="student.payments.payNow">Pay now</span>
                                                <% } %>
                                            </a>
                                            <% } %>
                                        </td>
                                    </tr>
                                    <% } %>
                                    </tbody>
                                </table>
                            </div>
                            <% } %>
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
