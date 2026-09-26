<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.math.RoundingMode" %>
<%@ page import="java.time.ZoneId" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.util.List" %>
<%@ page import="model.entity.Instructor" %>
<%@ page import="model.entity.InstructorPaymentSettings" %>
<%@ page import="model.entity.PaymentStats" %>
<%@ page import="model.entity.PaymentStatus" %>
<%@ page import="model.entity.PaymentTransactionRow" %>
<%@ page import="model.entity.RevenueBucket" %>
<%@ page import="util.LocaleSupport" %>
<%!
    private static final DateTimeFormatter TABLE_DATE =
            DateTimeFormatter.ofPattern("MMM d, yyyy • HH:mm").withZone(ZoneId.systemDefault());

    private static String esc(Object val) {
        if (val == null) return "";
        return String.valueOf(val).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String money(BigDecimal amount, String currency) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        String cur = currency == null || currency.isBlank() ? "MYR" : currency.trim().toUpperCase();
        return cur + " " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String fmtDate(java.time.Instant i) {
        return i == null ? "—" : TABLE_DATE.format(i);
    }

    private static String pillClass(PaymentStatus s) {
        if (s == null) return "ipd-pill ipd-pill--pending";
        switch (s) {
            case APPROVED: return "ipd-pill ipd-pill--approved";
            case REJECTED: return "ipd-pill ipd-pill--rejected";
            case AWAITING_VERIFICATION: return "ipd-pill ipd-pill--awaiting";
            default: return "ipd-pill ipd-pill--pending";
        }
    }

    private static String statusLabel(PaymentStatus s) {
        if (s == null) return "Awaiting Payment";
        return s.displayLabel();
    }

    private static String initials(String name) {
        if (name == null || name.isBlank()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return ("" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
        }
        return ("" + name.trim().charAt(0)).toUpperCase();
    }
%>
<%
    request.setAttribute("activeMenu", "payments");
    String ctx = request.getContextPath();
    String payUrl = LocaleSupport.localizedUrl(request, "/instructor/payments");
    Instructor instructor = (Instructor) request.getAttribute("instructor");
    InstructorPaymentSettings settings = (InstructorPaymentSettings) request.getAttribute("settings");
    String qrPreviewUrl = (String) request.getAttribute("qrPreviewUrl");
    List<RevenueBucket> sessionOptions = (List<RevenueBucket>) request.getAttribute("sessionOptions");
    List<PaymentTransactionRow> queue = (List<PaymentTransactionRow>) request.getAttribute("queue");
    PaymentStats stats = (PaymentStats) request.getAttribute("stats");
    long selectedSessionId = request.getAttribute("selectedSessionId") == null ? 0L : ((Number) request.getAttribute("selectedSessionId")).longValue();
    String success = (String) request.getAttribute("success");
    String error = (String) request.getAttribute("error");
    if (stats == null) stats = new PaymentStats();
    String currency = stats.getCurrency();

    String bankName = settings == null || settings.getBankName() == null ? "" : settings.getBankName();
    String accountHolder = settings == null || settings.getAccountHolderName() == null ? "" : settings.getAccountHolderName();
    boolean hasQr = qrPreviewUrl != null && !qrPreviewUrl.trim().isEmpty();
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
  <title data-i18n="meta.instructorPaymentsTitle">Payments - e-Tasmi</title>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <%@ include file="/jsp/common/instructor_ui_head.jspf" %>
  <link rel="stylesheet" href="<%= ctx %>/css/instructor-page-headers.css?v=20260625-student-theme">
  <link rel="stylesheet" href="<%= ctx %>/css/instructor-theme-dark.css?v=20260609-instructor-dark">
  <link rel="stylesheet" href="<%= ctx %>/css/instructor-payments-dashboard.css?v=20260710-i18n-fix">
  <script defer src="<%= ctx %>/assets/js/app.js"></script>
</head>
<body class="instructor-premium-page instructor-package-page instructor-module-page">
<div class="app-shell">
  <%@ include file="/jsp/common/instructor_header.jspf" %>
  <div class="app-main">
    <div class="container sd-container instructor-workspace-shell">
      <div class="instructor-shell-layout">
        <%@ include file="/jsp/instructor/instructor_sidebar.jspf" %>
        <main class="instructor-shell-content" role="main">
          <div class="instructor-workspace-view ipd-page iup-fade-in">

            <%-- Green page hero header --%>
            <section class="iup-page-hero" data-i18n="instructor.payments.title" data-i18n-attr="aria-label" aria-label="Payments">
              <span class="iup-page-hero__glow" aria-hidden="true"></span>
              <div class="iup-page-hero__inner">
                <span class="iup-page-hero__icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none"><rect x="3" y="6" width="18" height="13" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M3 10h18" stroke="currentColor" stroke-width="1.8"/><path d="M7 15h4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
                </span>
                <div class="iup-page-hero__text">
                  <p class="iup-page-hero__eyebrow" data-i18n="instructor.payments.eyebrow">Instructor</p>
                  <h1 class="iup-page-hero__title" data-i18n="instructor.payments.title">Payments</h1>
                  <p class="iup-page-hero__desc" data-i18n="instructor.payments.heroDesc">Manage your QR code and review student payment receipts.</p>
                </div>
              </div>
            </section>

            <% if (success != null) { %>
            <div class="ipd-alert ipd-alert--success" role="status"><%= esc(success) %></div>
            <% } %>
            <% if (error != null) { %>
            <div class="ipd-alert ipd-alert--error" role="alert"><%= esc(error) %></div>
            <% } %>

            <%-- ═══ CENTERED QR HERO ═══ --%>
            <div class="ipd-qr-hero">
              <div class="ipd-qr-hero__inner">
                <%-- QR frame --%>
                <div class="ipd-qr-hero__frame" data-qr-frame>
                  <% if (hasQr) { %>
                  <img src="<%= esc(qrPreviewUrl) %>" alt="Payment QR code" data-qr-img>
                  <% } else { %>
                  <div class="ipd-qr-hero__placeholder" aria-hidden="true">
                    <svg viewBox="0 0 80 80" fill="none">
                      <rect x="8" y="8" width="26" height="26" rx="3" stroke="currentColor" stroke-width="3"/>
                      <rect x="14" y="14" width="14" height="14" rx="1" fill="currentColor" opacity=".35"/>
                      <rect x="46" y="8" width="26" height="26" rx="3" stroke="currentColor" stroke-width="3"/>
                      <rect x="52" y="14" width="14" height="14" rx="1" fill="currentColor" opacity=".35"/>
                      <rect x="8" y="46" width="26" height="26" rx="3" stroke="currentColor" stroke-width="3"/>
                      <rect x="14" y="52" width="14" height="14" rx="1" fill="currentColor" opacity=".35"/>
                      <rect x="46" y="46" width="8" height="8" rx="1" fill="currentColor" opacity=".35"/>
                      <rect x="58" y="46" width="14" height="8" rx="1" fill="currentColor" opacity=".35"/>
                      <rect x="46" y="58" width="26" height="14" rx="1" fill="currentColor" opacity=".35"/>
                    </svg>
                    <p data-i18n="instructor.payments.noQrUploaded">No QR code uploaded yet</p>
                  </div>
                  <% } %>
                </div>

                <%-- Upload form --%>
                <form method="post" action="<%= ctx %>/instructor/payments" enctype="multipart/form-data" class="ipd-qr-hero__form">
                  <input type="hidden" name="action" value="saveSettings">
                  <input type="hidden" name="sessionId" value="<%= selectedSessionId %>">
                  <input id="qrImage" name="qrImage" type="file" accept="image/*" class="ipd-file-hidden" data-qr-input>
                  <button type="button" class="ipd-qr-hero__upload-btn" data-qr-drop
                          data-i18n="<%= hasQr ? "instructor.payments.replaceQr" : "instructor.payments.uploadQr" %>" data-i18n-attr="aria-label"
                          aria-label="<%= hasQr ? "Replace QR code" : "Upload QR code" %>">
                    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 16V4m0 0L8 8m4-4 4 4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><path d="M4 17v1a3 3 0 0 0 3 3h10a3 3 0 0 0 3-3v-1" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                    <span data-i18n="<%= hasQr ? "instructor.payments.replaceQr" : "instructor.payments.uploadQr" %>"><%= hasQr ? "Replace QR Code" : "Upload QR Code" %></span>
                  </button>
                  <button type="submit" class="ipd-qr-hero__save-btn">
                    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><path d="M17 21v-8H7v8M7 3v5h8" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>
                    <span data-i18n="instructor.payments.save">Save</span>
                  </button>
                </form>

                <p class="ipd-qr-hero__hint" data-i18n="instructor.payments.qrHint">PNG, JPG or WEBP &middot; up to 8 MB &middot; Students scan this to pay</p>
              </div>
            </div>

            <%-- Stats row removed --%>

            <%-- ═══ SESSION FILTER ═══ --%>
            <div class="ipd-session-filter-wrap">
              <form class="ipd-session-filter" method="get" action="<%= payUrl %>" id="sessionFilterForm">
                <label class="ipd-session-filter__label" for="sessionFilter">
                  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
                  <span data-i18n="instructor.payments.filterBySession">Filter by Session</span>
                </label>
                <div class="ipd-session-filter__select-wrap">
                  <select id="sessionFilter" name="sessionId" class="ipd-session-filter__select"
                          onchange="this.form.submit()">
                    <option value="0" <%= selectedSessionId == 0 ? "selected" : "" %> data-i18n="instructor.payments.allSessions">All Sessions</option>
                    <% if (sessionOptions != null) {
                         for (RevenueBucket b : sessionOptions) { %>
                    <option value="<%= b.getId() %>" <%= selectedSessionId == b.getId() ? "selected" : "" %>><%= esc(b.getLabel()) %></option>
                    <%   }
                       } %>
                  </select>
                  <span class="ipd-session-filter__chevron" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none"><path d="M6 9l6 6 6-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                  </span>
                </div>
              </form>
            </div>

            <!-- Data table -->
            <section class="ipd-table-wrap" aria-label="Payment transactions">
              <% if (queue == null || queue.isEmpty()) { %>
              <div class="ipd-empty">
                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="4" y="5" width="16" height="15" rx="2" stroke="currentColor" stroke-width="1.6"/><path d="M8 11h8M8 15h5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                <p data-i18n="instructor.payments.noRecords">No payment records for this session view.</p>
              </div>
              <% } else { %>
              <div class="ipd-table-scroll">
                <table class="ipd-table">
                  <thead>
                    <tr>
                      <th data-i18n="instructor.payments.colStudent">Student</th>
                      <th data-i18n="instructor.payments.colSessionName">Session Name</th>
                      <th data-i18n="instructor.payments.colDateSubmitted">Date Submitted</th>
                      <th data-i18n="instructor.payments.colAmount">Amount</th>
                      <th data-i18n="instructor.payments.colStatus">Status</th>
                      <th data-i18n="instructor.payments.colAction">Action</th>
                    </tr>
                  </thead>
                  <tbody>
                  <% for (PaymentTransactionRow r : queue) {
                       PaymentStatus st = r.getStatus();
                       String receipt = r.getReceiptFilePath();
                       String receiptUrl = null;
                       if (receipt != null && !receipt.trim().isEmpty()) {
                           receiptUrl = (receipt.startsWith("http://") || receipt.startsWith("https://")) ? receipt
                                   : (receipt.startsWith("/") ? ctx + receipt : ctx + "/" + receipt);
                       }
                       boolean canVerify = st == PaymentStatus.AWAITING_VERIFICATION;
                       java.time.Instant submitted = r.getReceiptSubmittedAt() != null ? r.getReceiptSubmittedAt() : r.getCreatedAt();
                  %>
                    <tr>
                      <td>
                        <div class="ipd-student">
                          <span class="ipd-avatar" aria-hidden="true"><%= esc(initials(r.getStudentName())) %></span>
                          <div>
                            <p class="ipd-student__name"><%= esc(r.getStudentName()) %></p>
                            <p class="ipd-student__email"><%= esc(r.getStudentEmail()) %></p>
                          </div>
                        </div>
                      </td>
                      <td><span class="ipd-session"><%= esc(r.getSessionTitle()) %></span></td>
                      <td><span class="ipd-date"><%= esc(fmtDate(submitted)) %></span></td>
                      <td><span class="ipd-amount"><%= esc(money(r.getAmount(), r.getCurrency())) %></span></td>
                      <td><span class="<%= pillClass(st) %>"><%= esc(statusLabel(st)) %></span></td>
                      <td>
                        <% if (receiptUrl != null) { %>
                        <button type="button" class="ipd-link"
                                data-receipt-view
                                data-receipt-url="<%= esc(receiptUrl) %>"
                                data-payment-id="<%= r.getPaymentId() %>"
                                data-can-verify="<%= canVerify ? "1" : "0" %>"
                                data-student="<%= esc(r.getStudentName()) %>"
                                data-amount="<%= esc(money(r.getAmount(), r.getCurrency())) %>"
                                data-session="<%= esc(r.getSessionTitle()) %>"
                                data-date="<%= esc(fmtDate(submitted)) %>"
                                data-status="<%= esc(statusLabel(st)) %>"
                                data-receipt-ref="<%= esc(r.getPaymentReference()) %>"
                                data-receipt-note="<%= esc(r.getStudentNote()) %>" data-i18n="instructor.payments.viewReceipt">View Receipt</button>
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

<!-- Payment verification modal -->
<div class="ipd-modal ipd-verify-modal" id="receiptModal" role="dialog" aria-modal="true" aria-labelledby="receiptModalTitle" aria-hidden="true">
  <div class="ipd-modal__backdrop" data-receipt-close aria-hidden="true"></div>
  <div class="ipd-modal__panel ipd-verify-panel">
    <button type="button" class="ipd-verify-close" data-receipt-close aria-label="Close" data-i18n="instructor.payments.close" data-i18n-attr="aria-label">
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
    </button>

    <header class="ipd-verify-header">
      <p class="ipd-verify-eyebrow" data-i18n="instructor.payments.verifyTitle">Payment Verification</p>
      <h2 class="ipd-verify-student" id="receiptModalTitle" data-receipt-student></h2>
      <p class="ipd-verify-note" data-receipt-note hidden></p>
    </header>

    <div class="ipd-verify-meta" data-receipt-meta aria-label="Payment summary">
      <div class="ipd-verify-meta__grid">
        <div class="ipd-verify-meta__cell">
          <div class="ipd-verify-meta__label">
            <svg class="ipd-verify-meta__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
            <span data-i18n="instructor.payments.metaAmountPaid">Amount Paid</span>
          </div>
          <p class="ipd-verify-meta__value ipd-verify-meta__value--amount" data-meta-amount>—</p>
        </div>
        <div class="ipd-verify-meta__cell">
          <div class="ipd-verify-meta__label">
            <svg class="ipd-verify-meta__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="4" y="5" width="16" height="14" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M8 10h8M8 14h5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
            <span data-i18n="instructor.payments.metaClassName">Class Name</span>
          </div>
          <p class="ipd-verify-meta__value" data-meta-session>—</p>
        </div>
        <div class="ipd-verify-meta__cell">
          <div class="ipd-verify-meta__label">
            <svg class="ipd-verify-meta__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
            <span data-i18n="instructor.payments.metaDateSubmitted">Date Submitted</span>
          </div>
          <p class="ipd-verify-meta__value ipd-verify-meta__value--date" data-meta-date>—</p>
        </div>
        <div class="ipd-verify-meta__cell">
          <div class="ipd-verify-meta__label">
            <svg class="ipd-verify-meta__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M12 3 4 6v6c0 5 3.5 7.5 8 9 4.5-1.5 8-4 8-9V6Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="m9 12 2 2 4-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>
            <span data-i18n="instructor.payments.metaPaymentStatus">Payment Status</span>
          </div>
          <p class="ipd-verify-meta__value"><span class="ipd-verify-status-pill" data-meta-status>—</span></p>
        </div>
      </div>
      <div class="ipd-verify-meta__ref" data-meta-ref-wrap hidden>
        <div class="ipd-verify-meta__label">
          <svg class="ipd-verify-meta__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M7 7h10v10H7z" stroke="currentColor" stroke-width="1.8"/><path d="M7 12h10" stroke="currentColor" stroke-width="1.8"/></svg>
          <span data-i18n="instructor.payments.metaReference">Reference</span>
        </div>
        <p class="ipd-verify-meta__value ipd-verify-meta__value--mono" data-meta-ref>—</p>
      </div>
    </div>

    <div class="ipd-verify-receipt-card">
      <div class="ipd-verify-receipt-card__inner" data-receipt-body>
        <div class="ipd-verify-loading" data-receipt-loading aria-hidden="true">
          <span class="ipd-verify-loading__spinner"></span>
          <span data-i18n="instructor.payments.loadingReceipt">Loading receipt…</span>
        </div>
      </div>
    </div>

    <footer class="ipd-verify-footer" data-receipt-actions hidden>
      <button type="button" class="ipd-verify-btn ipd-verify-btn--reject" data-open-reject>
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
        <span data-i18n="instructor.payments.reject">Reject</span>
      </button>
      <form method="post" action="<%= ctx %>/instructor/payments" class="ipd-verify-approve-form">
        <input type="hidden" name="action" value="approve">
        <input type="hidden" name="paymentId" data-approve-id>
        <input type="hidden" name="sessionId" value="<%= selectedSessionId %>">
        <button type="submit" class="ipd-verify-btn ipd-verify-btn--approve">
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 13l4 4L19 7" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/></svg>
          <span data-i18n="instructor.payments.approve">Approve</span>
        </button>
      </form>
    </footer>

    <footer class="ipd-verify-footer ipd-verify-footer--readonly" data-receipt-close-only>
      <a class="ipd-verify-btn ipd-verify-btn--outline" data-receipt-open target="_blank" rel="noopener">
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M14 3h7v7M10 14 21 3M21 14v7H3V3h7" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
        <span data-i18n="instructor.payments.openOriginal">Open Original</span>
      </a>
    </footer>
  </div>
</div>

<!-- Reject modal -->
<div class="ipd-modal ipd-verify-modal" id="rejectModal" role="dialog" aria-modal="true" aria-labelledby="rejectModalTitle" aria-hidden="true">
  <div class="ipd-modal__backdrop" data-reject-cancel aria-hidden="true"></div>
  <div class="ipd-modal__panel ipd-verify-panel ipd-verify-panel--compact">
    <button type="button" class="ipd-verify-close" data-reject-cancel aria-label="Close">
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
    </button>
    <h3 class="ipd-modal__title" id="rejectModalTitle" data-i18n="instructor.payments.rejectTitle">Reject Payment</h3>
    <p class="ipd-modal__sub" data-i18n="instructor.payments.rejectSub">Provide a reason so the student can resubmit a correct receipt.</p>
    <form method="post" action="<%= ctx %>/instructor/payments">
      <input type="hidden" name="action" value="reject">
      <input type="hidden" name="paymentId" id="rejectPaymentId">
      <input type="hidden" name="sessionId" value="<%= selectedSessionId %>">
      <div class="ipd-modal__quick">
        <button type="button" data-reason="The transferred amount does not match the fee." data-i18n="instructor.payments.reasonIncorrectAmount">Incorrect amount</button>
        <button type="button" data-reason="The receipt image is unclear or unreadable." data-i18n="instructor.payments.reasonUnclearReceipt">Unclear receipt</button>
        <button type="button" data-reason="This is not a valid proof of payment." data-i18n="instructor.payments.reasonInvalidProof">Invalid proof</button>
        <button type="button" data-reason="This receipt was already used for another payment." data-i18n="instructor.payments.reasonDuplicate">Duplicate receipt</button>
      </div>
      <textarea name="reason" id="rejectReason" maxlength="500" required placeholder="Rejection reason (required)" data-i18n="instructor.payments.rejectionPlaceholder" data-i18n-attr="placeholder"></textarea>
      <div class="ipd-verify-footer ipd-verify-footer--form">
        <button type="button" class="ipd-verify-btn ipd-verify-btn--outline" data-reject-cancel data-i18n="instructor.payments.cancel">Cancel</button>
        <button type="submit" class="ipd-verify-btn ipd-verify-btn--reject-solid" data-i18n="instructor.payments.confirmRejection">Confirm Rejection</button>
      </div>
    </form>
  </div>
</div>

<script>
(() => {
  /* QR upload */
  const qrInput = document.querySelector('[data-qr-input]');
  const qrDrop = document.querySelector('[data-qr-drop]');
  const qrFrame = document.querySelector('[data-qr-frame]');
  if (qrDrop && qrInput) {
    qrDrop.addEventListener('click', () => qrInput.click());
    qrDrop.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); qrInput.click(); } });
    qrDrop.addEventListener('dragover', e => { e.preventDefault(); qrDrop.classList.add('is-dragover'); });
    qrDrop.addEventListener('dragleave', () => qrDrop.classList.remove('is-dragover'));
    qrDrop.addEventListener('drop', e => {
      e.preventDefault();
      qrDrop.classList.remove('is-dragover');
      if (e.dataTransfer.files && e.dataTransfer.files.length) {
        qrInput.files = e.dataTransfer.files;
        previewQr(e.dataTransfer.files[0]);
      }
    });
    qrInput.addEventListener('change', () => {
      if (qrInput.files && qrInput.files[0]) previewQr(qrInput.files[0]);
    });
  }
  function previewQr(file) {
    if (!qrFrame || !file.type.startsWith('image/')) return;
    const url = URL.createObjectURL(file);
    qrFrame.innerHTML = '';
    const img = document.createElement('img');
    img.src = url;
    img.alt = 'QR preview';
    img.onload = () => URL.revokeObjectURL(url);
    qrFrame.appendChild(img);
  }

  /* Payment verification modal */
  const receiptModal = document.getElementById('receiptModal');
  const rejectModal = document.getElementById('rejectModal');
  const receiptBody = receiptModal ? receiptModal.querySelector('[data-receipt-body]') : null;
  const receiptLoading = receiptModal ? receiptModal.querySelector('[data-receipt-loading]') : null;
  const receiptStudent = receiptModal ? receiptModal.querySelector('[data-receipt-student]') : null;
  const receiptNoteEl = receiptModal ? receiptModal.querySelector('[data-receipt-note]') : null;
  const metaAmount = receiptModal ? receiptModal.querySelector('[data-meta-amount]') : null;
  const metaSession = receiptModal ? receiptModal.querySelector('[data-meta-session]') : null;
  const metaDate = receiptModal ? receiptModal.querySelector('[data-meta-date]') : null;
  const metaStatus = receiptModal ? receiptModal.querySelector('[data-meta-status]') : null;
  const metaRef = receiptModal ? receiptModal.querySelector('[data-meta-ref]') : null;
  const metaRefWrap = receiptModal ? receiptModal.querySelector('[data-meta-ref-wrap]') : null;
  const receiptOpen = receiptModal ? receiptModal.querySelector('[data-receipt-open]') : null;
  const receiptActions = receiptModal ? receiptModal.querySelector('[data-receipt-actions]') : null;
  const receiptCloseOnly = receiptModal ? receiptModal.querySelector('[data-receipt-close-only]') : null;
  const approveIdInput = receiptModal ? receiptModal.querySelector('[data-approve-id]') : null;
  const rejectPaymentId = document.getElementById('rejectPaymentId');
  const rejectReason = document.getElementById('rejectReason');
  let pendingRejectPaymentId = '';
  let lastReceiptContext = {};

  function statusPillClass(label) {
    const v = (label || '').toLowerCase();
    if (v.includes('await') || v.includes('pending') || v.includes('verif')) {
      return 'ipd-verify-status-pill--awaiting';
    }
    if (v.includes('approv')) return 'ipd-verify-status-pill--approved';
    if (v.includes('reject')) return 'ipd-verify-status-pill--rejected';
    return 'ipd-verify-status-pill--pending';
  }

  function refreshModalI18n() {
    if (window.EtasmiI18n && typeof window.EtasmiI18n.apply === 'function' && receiptModal) {
      window.EtasmiI18n.apply(receiptModal);
    }
  }

  function escHtml(val) {
    return String(val == null ? '' : val)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function buildReceiptSlip(ctx) {
    const wrap = document.createElement('div');
    wrap.className = 'ipd-receipt-slip';
    wrap.setAttribute('role', 'img');
    wrap.setAttribute('aria-label', 'Bank transfer receipt preview');
    const txId = ctx.ref || ('TXN-' + String(ctx.paymentId || '000000').padStart(6, '0'));
    wrap.innerHTML =
      '<div class="ipd-receipt-slip__top">' +
        '<div class="ipd-receipt-slip__brand">' +
          '<span class="ipd-receipt-slip__icon" aria-hidden="true">' +
            '<svg viewBox="0 0 24 24" fill="none"><rect x="3" y="6" width="18" height="14" rx="2" stroke="currentColor" stroke-width="1.6"/><path d="M3 10h18" stroke="currentColor" stroke-width="1.6"/><circle cx="7" cy="15" r="1" fill="currentColor"/><circle cx="11" cy="15" r="1" fill="currentColor"/></svg>' +
          '</span>' +
          '<div><p class="ipd-receipt-slip__bank">DuitNow Transfer</p><p class="ipd-receipt-slip__type">Payment slip</p></div>' +
        '</div>' +
        '<span class="ipd-receipt-slip__badge">' + escHtml(ctx.status || 'Awaiting Verification') + '</span>' +
      '</div>' +
      '<div class="ipd-receipt-slip__body">' +
        '<div class="ipd-receipt-slip__row"><span>Transaction ID</span><strong>' + escHtml(txId) + '</strong></div>' +
        '<div class="ipd-receipt-slip__row"><span>Date &amp; Time</span><strong>' + escHtml(ctx.date || '—') + '</strong></div>' +
        '<div class="ipd-receipt-slip__row"><span>Recipient</span><strong>Session Fee</strong></div>' +
        '<div class="ipd-receipt-slip__row ipd-receipt-slip__row--amount"><span>Amount</span><strong>' + escHtml(ctx.amount || '—') + '</strong></div>' +
      '</div>' +
      '<div class="ipd-receipt-slip__foot"><span>Reference only — verify against uploaded proof</span></div>';
    return wrap;
  }

  function openReceiptModal() {
    if (!receiptModal) return;
    refreshModalI18n();
    receiptModal.classList.add('is-open');
    receiptModal.setAttribute('aria-hidden', 'false');
    document.body.classList.add('ipd-modal-open');
    const closeBtn = receiptModal.querySelector('.ipd-verify-close');
    if (closeBtn) closeBtn.focus();
  }

  function closeReceiptModal() {
    if (!receiptModal) return;
    receiptModal.classList.remove('is-open');
    receiptModal.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('ipd-modal-open');
  }

  function closeRejectModal() {
    if (!rejectModal) return;
    rejectModal.classList.remove('is-open');
    rejectModal.setAttribute('aria-hidden', 'true');
    document.body.classList.remove('ipd-modal-open');
  }

  function renderReceiptMedia(url, ctx) {
    if (!receiptBody) return;
    const loaderTemplate = receiptLoading ? receiptLoading.cloneNode(true) : null;
    receiptBody.innerHTML = '';
    receiptBody.classList.add('is-loading');
    if (loaderTemplate) {
      loaderTemplate.hidden = false;
      receiptBody.appendChild(loaderTemplate);
    }

    function clearLoading() {
      receiptBody.classList.remove('is-loading');
      const loader = receiptBody.querySelector('[data-receipt-loading]');
      if (loader) loader.remove();
    }

    if (url && /\.pdf($|\?)/i.test(url)) {
      clearLoading();
      const slip = buildReceiptSlip(ctx);
      slip.classList.add('ipd-receipt-slip--pdf');
      receiptBody.appendChild(slip);
      const link = document.createElement('a');
      link.href = url;
      link.target = '_blank';
      link.rel = 'noopener';
      link.className = 'ipd-verify-pdf-link';
      link.textContent = 'Open PDF receipt in new tab';
      receiptBody.appendChild(link);
      return;
    }

    if (!url) {
      clearLoading();
      receiptBody.appendChild(buildReceiptSlip(ctx));
      return;
    }

    const img = document.createElement('img');
    img.className = 'ipd-receipt-img';
    img.alt = 'Payment receipt uploaded by ' + (ctx.student || 'student');
    img.decoding = 'async';
    img.addEventListener('load', clearLoading);
    img.addEventListener('error', () => {
      clearLoading();
      receiptBody.innerHTML = '';
      receiptBody.appendChild(buildReceiptSlip(ctx));
    });
    receiptBody.appendChild(img);
    img.src = url;
    if (img.complete) {
      if (img.naturalWidth > 0) clearLoading();
      else {
        clearLoading();
        receiptBody.innerHTML = '';
        receiptBody.appendChild(buildReceiptSlip(ctx));
      }
    }
  }

  document.querySelectorAll('[data-receipt-view]').forEach(btn => {
    btn.addEventListener('click', () => {
      const url = btn.getAttribute('data-receipt-url');
      const ref = btn.getAttribute('data-receipt-ref');
      const note = btn.getAttribute('data-receipt-note');
      const student = btn.getAttribute('data-student');
      const amount = btn.getAttribute('data-amount');
      const session = btn.getAttribute('data-session');
      const date = btn.getAttribute('data-date');
      const status = btn.getAttribute('data-status');
      const paymentId = btn.getAttribute('data-payment-id');
      const canVerify = btn.getAttribute('data-can-verify') === '1';

      lastReceiptContext = { ref, student, amount, session, date, status, paymentId };

      if (receiptOpen) receiptOpen.href = url || '#';
      if (receiptStudent) receiptStudent.textContent = student || 'Student';
      if (receiptNoteEl) {
        if (note) {
          receiptNoteEl.textContent = 'Student note: ' + note;
          receiptNoteEl.hidden = false;
        } else {
          receiptNoteEl.textContent = '';
          receiptNoteEl.hidden = true;
        }
      }
      if (metaAmount) metaAmount.textContent = amount || '—';
      if (metaSession) metaSession.textContent = session || '—';
      if (metaDate) metaDate.textContent = date || '—';
      if (metaStatus) {
        metaStatus.textContent = status || '—';
        metaStatus.className = 'ipd-verify-status-pill ' + statusPillClass(status);
      }
      if (metaRef && metaRefWrap) {
        if (ref) {
          metaRef.textContent = ref;
          metaRefWrap.hidden = false;
        } else {
          metaRefWrap.hidden = true;
        }
      }

      renderReceiptMedia(url, lastReceiptContext);
      pendingRejectPaymentId = paymentId || '';
      if (approveIdInput) approveIdInput.value = paymentId || '';
      if (receiptActions) receiptActions.hidden = !canVerify;
      if (receiptCloseOnly) receiptCloseOnly.hidden = canVerify;
      openReceiptModal();
    });
  });

  document.querySelectorAll('[data-receipt-close]').forEach(b =>
    b.addEventListener('click', closeReceiptModal));

  receiptModal && receiptModal.querySelector('[data-open-reject]')?.addEventListener('click', () => {
    if (rejectPaymentId) rejectPaymentId.value = pendingRejectPaymentId;
    if (rejectReason) rejectReason.value = '';
    closeReceiptModal();
    if (rejectModal) {
      rejectModal.classList.add('is-open');
      rejectModal.setAttribute('aria-hidden', 'false');
      document.body.classList.add('ipd-modal-open');
    }
  });

  document.querySelectorAll('[data-reason]').forEach(btn => {
    btn.addEventListener('click', () => { if (rejectReason) rejectReason.value = btn.getAttribute('data-reason') || ''; });
  });
  document.querySelectorAll('[data-reject-cancel]').forEach(b =>
    b.addEventListener('click', closeRejectModal));

  [receiptModal, rejectModal].forEach(m => m && m.addEventListener('click', e => {
    if (e.target === m || e.target.classList.contains('ipd-modal__backdrop')) {
      if (m === receiptModal) closeReceiptModal();
      else closeRejectModal();
    }
  }));

  document.addEventListener('keydown', e => {
    if (e.key !== 'Escape') return;
    if (receiptModal && receiptModal.classList.contains('is-open')) closeReceiptModal();
    else if (rejectModal && rejectModal.classList.contains('is-open')) closeRejectModal();
  });
})();
</script>
</body>
</html>
