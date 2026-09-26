<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.math.RoundingMode" %>
<%@ page import="java.time.ZoneId" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.util.List" %>
<%@ page import="model.entity.PaymentStats" %>
<%@ page import="model.entity.PaymentStatus" %>
<%@ page import="model.entity.PaymentTransactionRow" %>
<%!
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm").withZone(ZoneId.systemDefault());
    private static String esc(Object val) {
        if (val == null) return "";
        return String.valueOf(val).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
    private static String money(BigDecimal amount, String currency) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        String cur = currency == null || currency.isBlank() ? "MYR" : currency.trim().toUpperCase();
        return cur + " " + value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
    private static String fmt(java.time.Instant i) { return i == null ? "—" : DT.format(i); }
    private static String badge(PaymentStatus s) {
        if (s == null) return "apm-badge apm-badge--pending";
        switch (s) {
            case APPROVED: return "apm-badge apm-badge--approved";
            case REJECTED: return "apm-badge apm-badge--rejected";
            case AWAITING_VERIFICATION: return "apm-badge apm-badge--awaiting";
            default: return "apm-badge apm-badge--pending";
        }
    }
    private static String statusLabel(PaymentStatus s) { return s == null ? "Awaiting Payment" : s.displayLabel(); }
%>
<%
    request.setAttribute("activeMenu", "payments");
    String ctx = request.getContextPath();
    PaymentStats stats = (PaymentStats) request.getAttribute("stats");
    if (stats == null) stats = new PaymentStats();
    List<PaymentTransactionRow> rows = (List<PaymentTransactionRow>) request.getAttribute("rows");
    int currentPage = request.getAttribute("page") == null ? 1 : ((Number) request.getAttribute("page")).intValue();
    int totalPages = request.getAttribute("totalPages") == null ? 1 : ((Number) request.getAttribute("totalPages")).intValue();
    long totalCount = request.getAttribute("totalCount") == null ? 0 : ((Number) request.getAttribute("totalCount")).longValue();
    String sortBy = (String) request.getAttribute("sortBy");
    String sortDir = (String) request.getAttribute("sortDir");
    String fStatus = String.valueOf(request.getAttribute("filterStatus"));
    String fSearch = String.valueOf(request.getAttribute("filterSearch"));
    String fFrom = String.valueOf(request.getAttribute("filterFromDate"));
    String fTo = String.valueOf(request.getAttribute("filterToDate"));
    String fMin = String.valueOf(request.getAttribute("filterMinAmount"));
    String fMax = String.valueOf(request.getAttribute("filterMaxAmount"));
    String sessionChartJson = (String) request.getAttribute("sessionChartJson");
    String instructorChartJson = (String) request.getAttribute("instructorChartJson");
    if (sessionChartJson == null) sessionChartJson = "[]";
    if (instructorChartJson == null) instructorChartJson = "[]";
    if ("null".equals(fStatus)) fStatus = "";
    if ("null".equals(fSearch)) fSearch = "";
    if ("null".equals(fFrom)) fFrom = "";
    if ("null".equals(fTo)) fTo = "";
    if ("null".equals(fMin)) fMin = "";
    if ("null".equals(fMax)) fMax = "";
    String currency = stats.getCurrency();
    String error = (String) request.getAttribute("error");

    StringBuilder qs = new StringBuilder();
    if (!fStatus.isEmpty()) qs.append("&status=").append(java.net.URLEncoder.encode(fStatus, "UTF-8"));
    if (!fSearch.isEmpty()) qs.append("&search=").append(java.net.URLEncoder.encode(fSearch, "UTF-8"));
    if (!fFrom.isEmpty()) qs.append("&fromDate=").append(java.net.URLEncoder.encode(fFrom, "UTF-8"));
    if (!fTo.isEmpty()) qs.append("&toDate=").append(java.net.URLEncoder.encode(fTo, "UTF-8"));
    if (!fMin.isEmpty()) qs.append("&minAmount=").append(java.net.URLEncoder.encode(fMin, "UTF-8"));
    if (!fMax.isEmpty()) qs.append("&maxAmount=").append(java.net.URLEncoder.encode(fMax, "UTF-8"));
    if (sortBy != null) qs.append("&sort=").append(java.net.URLEncoder.encode(sortBy, "UTF-8"));
    if (sortDir != null) qs.append("&dir=").append(java.net.URLEncoder.encode(sortDir, "UTF-8"));
    String filterQs = qs.toString();
    String filterQsNoSort = filterQs.replaceAll("&sort=[^&]*", "").replaceAll("&dir=[^&]*", "");
%>
<!DOCTYPE html>
<html lang="en" dir="ltr">
<head>
    <title>Payments Monitoring - e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <%@ include file="/jsp/common/admin_ui_head.jspf" %>
    <link rel="stylesheet" href="<%= ctx %>/css/admin-redesign.css">
    <link rel="stylesheet" href="<%= ctx %>/css/admin-ui-refine.css?v=20260623-admin-payments1">
    <link rel="stylesheet" href="<%= ctx %>/css/admin-payments-dashboard.css?v=20260623-admin-payments1">
    <script defer src="<%= ctx %>/assets/js/admin.js"></script>
    <script defer src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
</head>
<body class="admin-body admin-package-page admin-module-page">
<div class="admin-shell">
    <%@ include file="/jsp/common/admin_header.jspf" %>
    <div class="admin-layout">
        <%@ include file="/jsp/admin/admin_sidebar.jspf" %>
        <main class="admin-main">
            <div class="admin-workspace-view apm-page" data-context-path="<%= ctx %>">
                <div class="ar-breadcrumb">
                    <a href="<%= ctx %>/admin/dashboard">Dashboard</a>
                    <span class="sep">&rsaquo;</span>
                    <span>Payments Monitoring</span>
                </div>

                <div class="ar-hero" style="margin-bottom:14px">
                    <div>
                        <div class="ar-hero__eyebrow">Finance</div>
                        <h1 class="ar-hero__title">Payments Monitoring</h1>
                        <p class="ar-hero__sub">Track every QR transfer across the platform. Students pay instructors directly — this is a read-only monitor.</p>
                    </div>
                    <div class="apm-hero-actions">
                        <a class="apm-btn apm-btn--ghost" href="<%= ctx %>/admin/payments?export=pdf<%= esc(filterQs) %>">Export PDF</a>
                    </div>
                </div>

                <% if (error != null) { %>
                <div class="apm-alert" role="alert"><%= esc(error) %></div>
                <% } %>

                <div class="apm-stats">
                    <div class="apm-stat">
                        <p class="apm-stat__label">Total transactions</p>
                        <p class="apm-stat__value"><%= stats.getTotalTransactions() %></p>
                    </div>
                    <div class="apm-stat apm-stat--await">
                        <p class="apm-stat__label">Awaiting verification</p>
                        <p class="apm-stat__value"><%= stats.getAwaitingCount() %></p>
                    </div>
                    <div class="apm-stat">
                        <p class="apm-stat__label">Approved</p>
                        <p class="apm-stat__value"><%= stats.getApprovedCount() %></p>
                    </div>
                    <div class="apm-stat apm-stat--rej">
                        <p class="apm-stat__label">Rejected</p>
                        <p class="apm-stat__value"><%= stats.getRejectedCount() %></p>
                    </div>
                    <div class="apm-stat apm-stat--rev">
                        <p class="apm-stat__label">Total revenue</p>
                        <p class="apm-stat__value"><%= esc(money(stats.getTotalRevenue(), currency)) %></p>
                    </div>
                </div>

                <div class="apm-charts">
                    <div class="apm-card">
                        <h2 class="apm-card__title">Revenue by session</h2>
                        <div class="apm-chart-wrap"><canvas id="chartSession"></canvas></div>
                    </div>
                    <div class="apm-card">
                        <h2 class="apm-card__title">Revenue by instructor</h2>
                        <div class="apm-chart-wrap"><canvas id="chartInstructor"></canvas></div>
                    </div>
                </div>

                <div class="apm-card">
                    <form class="apm-filters" method="get" action="<%= ctx %>/admin/payments">
                        <div class="apm-field">
                            <label for="search">Search</label>
                            <input id="search" name="search" type="text" value="<%= esc(fSearch) %>" placeholder="Student, instructor, session, reference">
                        </div>
                        <div class="apm-field">
                            <label for="status">Status</label>
                            <select id="status" name="status">
                                <option value="">All statuses</option>
                                <option value="AWAITING_VERIFICATION" <%= "AWAITING_VERIFICATION".equals(fStatus) ? "selected" : "" %>>Pending verification</option>
                                <option value="APPROVED" <%= "APPROVED".equals(fStatus) ? "selected" : "" %>>Approved</option>
                                <option value="REJECTED" <%= "REJECTED".equals(fStatus) ? "selected" : "" %>>Rejected</option>
                                <option value="PENDING" <%= "PENDING".equals(fStatus) ? "selected" : "" %>>Awaiting payment</option>
                            </select>
                        </div>
                        <div class="apm-field">
                            <label for="fromDate">From</label>
                            <input id="fromDate" name="fromDate" type="date" value="<%= esc(fFrom) %>">
                        </div>
                        <div class="apm-field">
                            <label for="toDate">To</label>
                            <input id="toDate" name="toDate" type="date" value="<%= esc(fTo) %>">
                        </div>
                        <div class="apm-field">
                            <label for="minAmount">Min amount</label>
                            <input id="minAmount" name="minAmount" type="number" step="0.01" value="<%= esc(fMin) %>" style="width:100px">
                        </div>
                        <div class="apm-field">
                            <label for="maxAmount">Max amount</label>
                            <input id="maxAmount" name="maxAmount" type="number" step="0.01" value="<%= esc(fMax) %>" style="width:100px">
                        </div>
                        <button type="submit" class="apm-btn apm-btn--primary">Apply filters</button>
                        <a class="apm-btn apm-btn--ghost" href="<%= ctx %>/admin/payments">Reset</a>
                    </form>

                    <% if (rows == null || rows.isEmpty()) { %>
                    <div class="apm-empty"><p>No transactions match your filters.</p></div>
                    <% } else {
                        String base = ctx + "/admin/payments?";
                        String otherDir = "asc".equals(sortDir) ? "desc" : "asc";
                    %>
                    <div class="apm-table-wrap">
                    <table class="apm-table">
                        <thead>
                            <tr>
                                <th class="<%= "date".equals(sortBy) ? "is-sorted" : "" %>">
                                    <a href="<%= base %>sort=date&dir=<%= "date".equals(sortBy) ? otherDir : "desc" %><%= esc(filterQsNoSort) %>">Date</a>
                                </th>
                                <th class="<%= "student".equals(sortBy) ? "is-sorted" : "" %>">
                                    <a href="<%= base %>sort=student&dir=<%= "student".equals(sortBy) ? otherDir : "asc" %><%= esc(filterQsNoSort) %>">Student</a>
                                </th>
                                <th>Instructor</th>
                                <th>Session</th>
                                <th class="<%= "amount".equals(sortBy) ? "is-sorted" : "" %>">
                                    <a href="<%= base %>sort=amount&dir=<%= "amount".equals(sortBy) ? otherDir : "desc" %><%= esc(filterQsNoSort) %>">Amount</a>
                                </th>
                                <th>Status</th>
                            </tr>
                        </thead>
                        <tbody>
                        <% for (PaymentTransactionRow r : rows) { %>
                            <tr data-detail="<%= r.getPaymentId() %>">
                                <td><%= esc(fmt(r.getCreatedAt())) %></td>
                                <td>
                                    <%= esc(r.getStudentName()) %>
                                    <div class="apm-cell-sub"><%= esc(r.getStudentEmail()) %></div>
                                </td>
                                <td><%= esc(r.getInstructorName()) %></td>
                                <td><%= esc(r.getSessionTitle()) %></td>
                                <td><%= esc(money(r.getAmount(), r.getCurrency())) %></td>
                                <td><span class="<%= badge(r.getStatus()) %>"><%= esc(statusLabel(r.getStatus())) %></span></td>
                            </tr>
                        <% } %>
                        </tbody>
                    </table>
                    </div>

                    <div class="apm-pager">
                        <span><%= totalCount %> results</span>
                        <% if (currentPage > 1) { %>
                        <a href="<%= ctx %>/admin/payments?page=<%= currentPage - 1 %><%= esc(filterQs) %>">Previous</a>
                        <% } else { %><span class="is-disabled">Previous</span><% } %>
                        <span><%= currentPage %> / <%= totalPages %></span>
                        <% if (currentPage < totalPages) { %>
                        <a href="<%= ctx %>/admin/payments?page=<%= currentPage + 1 %><%= esc(filterQs) %>">Next</a>
                        <% } else { %><span class="is-disabled">Next</span><% } %>
                    </div>
                    <% } %>
                </div>

            </div>
        </main>
    </div>
</div>

<div class="apm-drawer" id="apmDrawer" role="dialog" aria-modal="true" aria-labelledby="apmDrawerTitle">
  <div class="apm-drawer__panel">
    <h3 class="apm-drawer__title" id="apmDrawerTitle">Transaction detail</h3>
    <div data-detail-body></div>
    <button type="button" class="apm-btn apm-btn--ghost" data-detail-close style="margin-top:16px">Close</button>
  </div>
</div>

<script>
(() => {
  const sessionData = <%= sessionChartJson %>;
  const instructorData = <%= instructorChartJson %>;

  function drawChart(id, data, color) {
    const el = document.getElementById(id);
    if (!el || typeof Chart === 'undefined') return;
    if (!data || !data.length) {
      el.parentElement.innerHTML = '<p style="color:#9ca3af;font-size:13px;padding:24px 0;text-align:center">No approved revenue yet.</p>';
      return;
    }
    new Chart(el, {
      type: 'bar',
      data: {
        labels: data.map(d => d.label),
        datasets: [{ data: data.map(d => Number(d.value)), backgroundColor: color, borderRadius: 6 }]
      },
      options: {
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: true } },
        maintainAspectRatio: false
      }
    });
  }

  function startCharts() {
    drawChart('chartSession', sessionData, '#6366f1');
    drawChart('chartInstructor', instructorData, '#10b981');
  }
  if (typeof Chart !== 'undefined') startCharts();
  else window.addEventListener('load', startCharts);

  const ctx = '<%= ctx %>';
  const drawer = document.getElementById('apmDrawer');
  const body = drawer ? drawer.querySelector('[data-detail-body]') : null;

  function esc(s) {
    const d = document.createElement('div');
    d.textContent = s == null ? '' : s;
    return d.innerHTML;
  }

  document.querySelectorAll('[data-detail]').forEach(tr => {
    tr.addEventListener('click', async () => {
      const id = tr.getAttribute('data-detail');
      if (!drawer || !body) return;
      body.innerHTML = '<p style="color:#9ca3af">Loading…</p>';
      drawer.classList.add('is-open');
      try {
        const res = await fetch(ctx + '/admin/payments?ajax=detail&paymentId=' + encodeURIComponent(id));
        const data = await res.json();
        if (!data.success) {
          body.innerHTML = '<p style="color:#b91c1c">' + esc(data.error || 'Could not load transaction.') + '</p>';
          return;
        }
        const t = data.transaction;
        let html = '<dl>';
        html += '<div class="apm-drawer__row"><dt>Student</dt><dd>' + esc(t.studentName) + '<br><span style="color:#9ca3af">' + esc(t.studentEmail) + '</span></dd></div>';
        html += '<div class="apm-drawer__row"><dt>Instructor</dt><dd>' + esc(t.instructorName) + '<br><span style="color:#9ca3af">' + esc(t.instructorEmail) + '</span></dd></div>';
        html += '<div class="apm-drawer__row"><dt>Session</dt><dd>' + esc(t.sessionTitle) + '</dd></div>';
        html += '<div class="apm-drawer__row"><dt>Amount</dt><dd>' + esc(t.currency + ' ' + t.amount) + '</dd></div>';
        html += '<div class="apm-drawer__row"><dt>Status</dt><dd>' + esc(t.statusLabel) + '</dd></div>';
        if (t.reference) html += '<div class="apm-drawer__row"><dt>Reference</dt><dd>' + esc(t.reference) + '</dd></div>';
        if (t.studentNote) html += '<div class="apm-drawer__row"><dt>Student note</dt><dd>' + esc(t.studentNote) + '</dd></div>';
        if (t.verificationNote) html += '<div class="apm-drawer__row"><dt>Rejection reason</dt><dd>' + esc(t.verificationNote) + '</dd></div>';
        html += '<div class="apm-drawer__row"><dt>Created</dt><dd>' + esc(t.createdAt) + '</dd></div>';
        html += '</dl>';
        if (t.receiptUrl) html += '<a class="apm-receipt-link" href="' + esc(t.receiptUrl) + '" target="_blank" rel="noopener">View receipt</a>';
        if (data.history && data.history.length) {
          html += '<h4 style="margin:18px 0 0;font-size:13px;color:#6b7280;text-transform:uppercase;letter-spacing:.04em">Verification history</h4><ul class="apm-tl">';
          data.history.forEach(h => {
            html += '<li><div class="apm-tl__title">' + esc(h.action) + '</div><div class="apm-tl__meta">' + esc(h.at) + (h.actor ? ' · ' + esc(h.actor) : '') + '</div>' + (h.reason ? '<div class="apm-tl__reason">' + esc(h.reason) + '</div>' : '') + '</li>';
          });
          html += '</ul>';
        }
        body.innerHTML = html;
      } catch (e) {
        body.innerHTML = '<p style="color:#b91c1c">Could not load details.</p>';
      }
    });
  });

  if (drawer) {
    drawer.addEventListener('click', e => { if (e.target === drawer) drawer.classList.remove('is-open'); });
  }
  document.querySelectorAll('[data-detail-close]').forEach(b => {
    b.addEventListener('click', () => drawer && drawer.classList.remove('is-open'));
  });
})();
</script>
</body>
</html>
