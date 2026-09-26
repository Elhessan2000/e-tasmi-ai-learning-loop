<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="model.entity.ReportSummary" %>
<%@ page import="model.entity.AdminReportResult" %>
<%@ page import="java.util.List" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%!
    private static String esc(Object value) {
        if (value == null) return "";
        return value.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String selected(String current, String value) {
        if (current == null) current = "";
        if (value == null) value = "";
        return current.equalsIgnoreCase(value) ? "selected" : "";
    }

    private static String badgeClass(String value) {
        if (value == null) return "ar-badge ar-badge--neutral";
        String v = value.toUpperCase();
        if (v.contains("SUCCESS") || v.contains("APPROVED") || v.contains("ACTIVE") || v.contains("COMPLETED") || v.contains("YES")) {
            return "ar-badge ar-badge--success";
        }
        if (v.contains("PENDING") || v.contains("SCHEDULED") || v.contains("ONGOING")) {
            return "ar-badge ar-badge--warning";
        }
        if (v.contains("FAILED") || v.contains("REJECTED") || v.contains("CANCELLED") || v.contains("DELETED") || v.contains("NO")) {
            return "ar-badge ar-badge--danger";
        }
        return "ar-badge ar-badge--neutral";
    }

    private static String labelForType(String type) {
        if (type == null) return "Sessions";
        switch (type.toLowerCase()) {
            case "enrollments": return "Enrollments";
            case "payments": return "Payments";
            case "verification": return "Instructor Verification";
            case "evaluations": return "Evaluations";
            case "users": return "Users";
            case "sessions":
            default: return "Sessions";
        }
    }
%>
<%
    request.setAttribute("activeMenu", "reports");
    String ctx = request.getContextPath();
    AdminReportResult report = (AdminReportResult) request.getAttribute("report");
    if (report == null) report = new AdminReportResult();

    String type = report.getType() == null || report.getType().isBlank() ? "sessions" : report.getType();
    boolean reportGenerated = request.getParameter("generated") != null || request.getParameter("type") != null;
    String statusValue = report.getStatus() == null ? "" : report.getStatus();
    String roleValue = report.getRole() == null ? "" : report.getRole();
    String dateFromValue = report.getDateFrom() == null ? "" : report.getDateFrom();
    String dateToValue = report.getDateTo() == null ? "" : report.getDateTo();
    String enc = StandardCharsets.UTF_8.name();
    StringBuilder pdfQuery = new StringBuilder("export=pdf&generated=1");
    pdfQuery.append("&type=").append(URLEncoder.encode(type, enc));
    if (!statusValue.isEmpty()) {
        pdfQuery.append("&status=").append(URLEncoder.encode(statusValue, enc));
    }
    if (!roleValue.isEmpty()) {
        pdfQuery.append("&role=").append(URLEncoder.encode(roleValue, enc));
    }
    if (!dateFromValue.isEmpty()) {
        pdfQuery.append("&dateFrom=").append(URLEncoder.encode(dateFromValue, enc));
    }
    if (!dateToValue.isEmpty()) {
        pdfQuery.append("&dateTo=").append(URLEncoder.encode(dateToValue, enc));
    }
    String reportPdfQuery = pdfQuery.toString();
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.adminReportsTitle">Reports - e-Tasmi Admin</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <%@ include file="/jsp/common/admin_ui_head.jspf" %>
    <link rel="stylesheet" href="<%= ctx %>/css/admin-redesign.css">
    <link rel="stylesheet" href="<%= ctx %>/css/admin-ui-refine.css?v=20260518-student-sidebar-parity1">
    <script defer src="<%= ctx %>/assets/js/admin.js"></script>
</head>
<body class="admin-body admin-package-page admin-module-page admin-report-page admin-report-page--simple">
<div class="admin-shell">
    <%@ include file="/jsp/common/admin_header.jspf" %>
    <div class="admin-layout">
        <%@ include file="/jsp/admin/admin_sidebar.jspf" %>
        <main class="admin-main">
            <div class="admin-workspace-view">

                <section class="ar-hero report-simple-header">
                    <div>
                        <div class="ar-hero__eyebrow" data-i18n="admin.reports.reportingCenter">Reporting Center</div>
                        <h1 class="ar-hero__title" data-i18n="admin.reports.title">Reports</h1>
                        <p class="ar-hero__sub" data-i18n="admin.reports.subtitle">Generate focused reports from real eTasmi system data.</p>
                    </div>
                </section>

                <section class="ar-section report-generator" aria-label="Generate report">
                    <div class="report-generator__intro">
                        <div>
                            <div class="ar-section__kicker" data-i18n="admin.reports.generateReport">Generate Report</div>
                            <div class="ar-section__title" data-i18n="admin.reports.createReport">Create a report</div>
                            <div class="ar-section__sub" data-i18n="admin.reports.createReportSub">Choose one category, apply relevant filters, then generate the report.</div>
                        </div>
                        <button class="ar-btn ar-btn--primary" type="button" data-report-open data-i18n="admin.reports.generateReportBtn">Generate Report</button>
                    </div>

                    <form class="report-selection-panel <%= reportGenerated ? "is-open" : "" %>" method="get" action="<%= ctx %>/admin/reports" data-report-panel>
                        <input type="hidden" name="generated" value="1">

                        <div class="ar-field report-field--category">
                            <label for="type" data-i18n="admin.reports.reportCategory">Report Category</label>
                            <select class="ar-select" id="type" name="type" data-report-type>
                                <option value="" disabled <%= type == null || type.isBlank() ? "selected" : "" %>>Select category</option>
                                <option value="sessions" <%= selected(type, "sessions") %>>Sessions</option>
                                <option value="enrollments" <%= selected(type, "enrollments") %>>Enrollments</option>
                                <option value="payments" <%= selected(type, "payments") %>>Payments</option>
                                <option value="verification" <%= selected(type, "verification") %>>Instructor Verification</option>
                                <option value="evaluations" <%= selected(type, "evaluations") %>>Evaluations</option>
                                <option value="users" <%= selected(type, "users") %>>Users</option>
                            </select>
                        </div>

                        <div class="report-filter-row" data-report-filters>
                            <div class="ar-field" data-filter="status">
                                <label for="status">Status</label>
                                <select class="ar-select" id="status" name="status" data-status-select data-current-status="<%= esc(statusValue) %>">
                                    <option value="">All statuses</option>
                                </select>
                            </div>

                            <div class="ar-field" data-filter="role">
                                <label for="role">Role</label>
                                <select class="ar-select" id="role" name="role">
                                    <option value="">All roles</option>
                                    <option value="STUDENT" <%= selected(roleValue, "STUDENT") %>>Student</option>
                                    <option value="INSTRUCTOR" <%= selected(roleValue, "INSTRUCTOR") %>>Instructor</option>
                                    <option value="ADMIN" <%= selected(roleValue, "ADMIN") %>>Admin</option>
                                </select>
                            </div>

                            <div class="ar-field" data-filter="date">
                                <label for="dateFrom">From</label>
                                <input class="ar-input" id="dateFrom" type="date" name="dateFrom" value="<%= esc(dateFromValue) %>">
                            </div>

                            <div class="ar-field" data-filter="date">
                                <label for="dateTo">To</label>
                                <input class="ar-input" id="dateTo" type="date" name="dateTo" value="<%= esc(dateToValue) %>">
                            </div>
                        </div>

                        <div class="report-panel-actions">
                            <button class="ar-btn ar-btn--primary" type="submit">Generate</button>
                            <a class="ar-btn ar-btn--outline" href="<%= ctx %>/admin/reports">Reset</a>
                        </div>
                    </form>
                </section>

                <% if (reportGenerated) { %>
                <section class="ar-section report-results report-results--simple" aria-label="Report results">
                    <div class="ar-section__head">
                        <div>
                            <div class="ar-section__kicker">Report Result</div>
                            <div class="ar-section__title"><%= esc(report.getTitle()) %></div>
                            <div class="ar-section__sub"><%= esc(report.getSubtitle()) %></div>
                        </div>
                        <div class="report-result-actions">
                            <a class="ar-btn ar-btn--outline" href="<%= ctx %>/admin/reports?<%= reportPdfQuery %>">Download PDF</a>
                            <span class="status-pill status-pill--neutral"><%= report.getTotalRows() %> records</span>
                        </div>
                    </div>

                    <div class="report-applied-filters">
                        <span><%= esc(labelForType(type)) %></span>
                        <% if (!statusValue.isBlank()) { %><span>Status: <%= esc(statusValue) %></span><% } %>
                        <% if (!roleValue.isBlank()) { %><span>Role: <%= esc(roleValue) %></span><% } %>
                        <% if (!dateFromValue.isBlank()) { %><span>From: <%= esc(dateFromValue) %></span><% } %>
                        <% if (!dateToValue.isBlank()) { %><span>To: <%= esc(dateToValue) %></span><% } %>
                    </div>

                    <div class="ar-table-wrap report-table-wrap">
                        <table class="ar-table report-table">
                            <thead>
                            <tr>
                                <% for (String column : report.getColumns()) { %>
                                <th><%= esc(column) %></th>
                                <% } %>
                            </tr>
                            </thead>
                            <tbody>
                            <% if (report.getRows().isEmpty()) { %>
                            <tr>
                                <td class="cell-empty" colspan="<%= Math.max(1, report.getColumns().size()) %>">No records match the selected report criteria.</td>
                            </tr>
                            <% } else { %>
                            <% for (List<String> row : report.getRows()) { %>
                            <tr>
                                <% for (int i = 0; i < row.size(); i++) {
                                       String value = row.get(i);
                                       String columnName = report.getColumns().size() > i ? report.getColumns().get(i).toLowerCase() : "";
                                       boolean isStatusColumn = columnName.contains("status") || columnName.equals("active") || columnName.contains("verified");
                                %>
                                <td>
                                    <% if (isStatusColumn) { %>
                                    <span class="<%= badgeClass(value) %>"><%= esc(value) %></span>
                                    <% } else { %>
                                    <%= esc(value) %>
                                    <% } %>
                                </td>
                                <% } %>
                            </tr>
                            <% } %>
                            <% } %>
                            </tbody>
                        </table>
                    </div>
                </section>
                <% } %>

                <%-- ============ NEW FEATURE: Master System Report ============ --%>
                <section class="ar-section msr-cta-section" aria-label="Master System Report">
                    <div class="report-generator__intro">
                        <div>
                            <div class="ar-section__kicker">New</div>
                            <div class="ar-section__title">Master System Report</div>
                            <div class="ar-section__sub">A comprehensive, all-in-one platform health &amp; activity digest — analytics, finance, security, and user growth in one document.</div>
                        </div>
                        <button class="ar-btn ar-btn--primary" type="button" id="genMasterBtn">Generate Master System Report</button>
                    </div>
                </section>

                <section class="ar-section" id="masterSystemReport" hidden aria-label="System report">
                    <div class="msr-head">
                        <div class="msr-head__row">
                            <div>
                                <div class="msr-head__eyebrow">System Report · Generated <span id="msrStamp"></span></div>
                                <div class="msr-head__title">Platform health &amp; activity digest</div>
                                <div class="msr-head__sub">All roles · All modules · e-Tasmi platform</div>
                            </div>
                            <div class="msr-actions">
                                <a class="msr-btn msr-btn--light" href="<%= ctx %>/admin/reports?export=pdf&amp;generated=1&amp;type=sessions">Download Master PDF Document</a>
                                <button type="button" class="msr-btn msr-btn--ghost">Export Comprehensive CSV Pack</button>
                            </div>
                        </div>
                    </div>

                    <div class="msr-kpis">
                        <div class="msr-card"><div class="msr-card__label">Total revenue</div><div class="msr-card__value">RM 1,284</div><div class="msr-card__meta">▲ 12% MoM</div></div>
                        <div class="msr-card"><div class="msr-card__label">Active users</div><div class="msr-card__value">11</div><div class="msr-card__meta">8 students · 2 instructors</div></div>
                        <div class="msr-card"><div class="msr-card__label">Payment success</div><div class="msr-card__value">94%</div><div class="msr-card__meta">last 30 days</div></div>
                        <div class="msr-card"><div class="msr-card__label">Admin actions</div><div class="msr-card__value">128</div><div class="msr-card__meta">1 security flag</div></div>
                    </div>

                    <div class="msr-break"><span class="num">01</span><h4>Executive analytics</h4><span class="line"></span></div>

                    <div class="msr-charts">
                        <div class="msr-card">
                            <div class="msr-card__label">Platform Growth Trends</div>
                            <div class="msr-card__value">+38%</div>
                            <svg viewBox="0 0 320 130" style="width:100%;height:128px;margin-top:12px" preserveAspectRatio="none">
                                <defs><linearGradient id="msrGrowth" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#10b981" stop-opacity="0.30"/><stop offset="100%" stop-color="#10b981" stop-opacity="0"/></linearGradient></defs>
                                <line x1="0" y1="40" x2="320" y2="40" stroke="#f1f5f9" stroke-width="1"/><line x1="0" y1="80" x2="320" y2="80" stroke="#f1f5f9" stroke-width="1"/>
                                <path d="M0,110 C45,98 70,72 100,76 C135,80 150,40 190,46 C230,52 255,20 320,14 L320,130 L0,130 Z" fill="url(#msrGrowth)"/>
                                <path d="M0,110 C45,98 70,72 100,76 C135,80 150,40 190,46 C230,52 255,20 320,14" fill="none" stroke="#059669" stroke-width="2.5" stroke-linecap="round"/>
                            </svg>
                        </div>
                        <div class="msr-card" style="display:flex;flex-direction:column">
                            <div class="msr-card__label">Payment Success vs Failure</div>
                            <div style="flex:1;display:grid;place-items:center;padding:8px 0">
                                <div style="position:relative;width:128px;height:128px">
                                    <svg style="width:128px;height:128px;transform:rotate(-90deg)" viewBox="0 0 36 36">
                                        <circle cx="18" cy="18" r="15.5" fill="none" stroke="#fee2e2" stroke-width="4"/>
                                        <circle cx="18" cy="18" r="15.5" fill="none" stroke="#059669" stroke-width="4" stroke-linecap="round" stroke-dasharray="97.4" stroke-dashoffset="5.8"/>
                                    </svg>
                                    <div style="position:absolute;inset:0;display:grid;place-items:center;text-align:center"><div><div style="font-size:20px;font-weight:800;color:#0f172a">94%</div><div style="font-size:10px;color:#64748b">success</div></div></div>
                                </div>
                            </div>
                            <div style="display:flex;justify-content:center;gap:16px;font-size:12px;color:#475569"><span>● Success 47</span><span style="color:#fca5a5">● Failed 3</span></div>
                        </div>
                        <div class="msr-card">
                            <div class="msr-card__label">Admin Operational Velocity</div>
                            <div class="msr-card__value">128 <span style="font-size:14px;font-weight:500;color:#94a3b8">actions</span></div>
                            <div class="msr-bars">
                                <div class="b" style="height:45%;background:#a7f3d0"></div>
                                <div class="b" style="height:70%;background:#6ee7b7"></div>
                                <div class="b" style="height:55%;background:#a7f3d0"></div>
                                <div class="b" style="height:85%;background:#34d399"></div>
                                <div class="b" style="height:60%;background:#6ee7b7"></div>
                                <div class="b" style="height:100%;background:#059669"></div>
                            </div>
                        </div>
                    </div>

                    <div class="msr-break"><span class="num">02</span><h4>Cross-functional data</h4><span class="line"></span></div>

                    <div class="ar-table-wrap msr-mt">
                        <div class="msr-table-title"><strong>Table A · Financial Summary Ledger</strong><span class="ar-badge ar-badge--neutral">RM 1,284 total</span></div>
                        <table class="ar-table">
                            <thead><tr><th>Date</th><th>Transaction Type</th><th>Reference</th><th>Total</th><th>Status</th></tr></thead>
                            <tbody>
                                <tr><td>2026-06-09</td><td>Session enrolment</td><td>RCP-2026-015</td><td>RM 8.00</td><td><span class="ar-badge ar-badge--warning">Pending</span></td></tr>
                                <tr><td>2026-05-21</td><td>Session enrolment</td><td>RCP-2026-014</td><td>RM 10.00</td><td><span class="ar-badge ar-badge--success">Paid</span></td></tr>
                                <tr><td>2026-05-16</td><td>Session enrolment</td><td>RCP-2026-013</td><td>RM 2.00</td><td><span class="ar-badge ar-badge--success">Paid</span></td></tr>
                                <tr><td>2026-05-08</td><td>Refund</td><td>RCP-2026-011</td><td>RM 5.00</td><td><span class="ar-badge ar-badge--danger">Failed</span></td></tr>
                            </tbody>
                        </table>
                    </div>

                    <div class="ar-table-wrap msr-mt">
                        <div class="msr-table-title"><strong>Table B · Administrative Action Log</strong><span class="ar-badge ar-badge--danger">1 critical</span></div>
                        <table class="ar-table">
                            <thead><tr><th>Timestamp</th><th>Admin</th><th>Action</th><th>Security Flag</th></tr></thead>
                            <tbody>
                                <tr><td>2026-06-10 19:42</td><td>Admin (AU)</td><td>Approved instructor · Muaz #5</td><td><span class="ar-badge ar-badge--neutral">Routine</span></td></tr>
                                <tr><td>2026-06-10 14:21</td><td>System</td><td>Multiple failed logins · admin@etasmi.com</td><td><span class="ar-badge ar-badge--danger">Critical</span></td></tr>
                                <tr><td>2026-06-10 11:12</td><td>Admin (AU)</td><td>Generated revenue report · Q2</td><td><span class="ar-badge ar-badge--neutral">Routine</span></td></tr>
                                <tr><td>2026-06-09 17:05</td><td>Admin (AU)</td><td>Deactivated account · student#22</td><td><span class="ar-badge ar-badge--warning">Review</span></td></tr>
                            </tbody>
                        </table>
                    </div>

                    <div class="ar-table-wrap msr-mt">
                        <div class="msr-table-title"><strong>Table C · User Accounts Velocity</strong><span class="ar-badge ar-badge--neutral">3 new</span></div>
                        <table class="ar-table">
                            <thead><tr><th>User</th><th>Role</th><th>Joined</th><th>Verification</th></tr></thead>
                            <tbody>
                                <tr><td>Eman</td><td>STUDENT</td><td>2026-06-02</td><td><span class="ar-badge ar-badge--success">Verified</span></td></tr>
                                <tr><td>Muaz</td><td>INSTRUCTOR</td><td>2026-06-07</td><td><span class="ar-badge ar-badge--warning">Pending</span></td></tr>
                                <tr><td>Bilal Rahman</td><td>STUDENT</td><td>2026-05-28</td><td><span class="ar-badge ar-badge--success">Verified</span></td></tr>
                            </tbody>
                        </table>
                    </div>
                </section>

                <style>
                    .msr-cta-section .report-generator__intro{align-items:center}
                    #masterSystemReport .msr-head{position:relative;overflow:hidden;background:linear-gradient(90deg,#065f46,#022c22);color:#fff;border-radius:16px;padding:24px}
                    .msr-head__row{display:flex;flex-wrap:wrap;gap:16px;align-items:center;justify-content:space-between}
                    .msr-head__eyebrow{font-size:11px;font-weight:600;letter-spacing:.14em;text-transform:uppercase;color:rgba(209,250,229,.6)}
                    .msr-head__title{font-size:22px;font-weight:700;margin:4px 0 2px}
                    .msr-head__sub{font-size:14px;color:rgba(209,250,229,.75)}
                    .msr-actions{display:flex;flex-wrap:wrap;gap:8px}
                    .msr-btn{display:inline-flex;align-items:center;gap:8px;padding:10px 16px;border-radius:12px;font-size:14px;font-weight:600;cursor:pointer;border:0;text-decoration:none;transition:all .2s ease}
                    .msr-btn:hover{transform:scale(1.01);opacity:.92}
                    .msr-btn--light{background:#fff;color:#064e3b}
                    .msr-btn--ghost{background:rgba(255,255,255,.12);color:#fff;border:1px solid rgba(255,255,255,.25)}
                    .msr-kpis{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin-top:16px}
                    .msr-card{background:#fff;border:1px solid #eef0f3;border-radius:16px;padding:24px;box-shadow:0 1px 2px rgba(15,23,42,.04)}
                    .msr-card__label{font-size:11px;font-weight:600;letter-spacing:.1em;text-transform:uppercase;color:#64748b}
                    .msr-card__value{font-size:26px;font-weight:800;color:#0f172a;margin-top:8px}
                    .msr-card__meta{font-size:12px;color:#64748b;margin-top:4px}
                    .msr-break{display:flex;align-items:center;gap:12px;margin:24px 0 16px}
                    .msr-break .num{display:grid;place-items:center;width:28px;height:28px;border-radius:8px;background:#022c22;color:#fff;font-size:12px;font-weight:700}
                    .msr-break h4{font-size:15px;font-weight:700;color:#0f172a;margin:0}
                    .msr-break .line{flex:1;height:1px;background:#e2e8f0}
                    .msr-charts{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}
                    .msr-bars{height:112px;display:flex;align-items:flex-end;gap:8px;margin-top:16px}
                    .msr-bars .b{flex:1;border-radius:6px 6px 0 0}
                    .msr-table-title{display:flex;align-items:center;justify-content:space-between;padding:16px 20px;border-bottom:1px solid #eef0f3;font-size:14px;color:#0f172a}
                    .msr-mt{margin-top:16px}
                    @media (max-width:900px){.msr-kpis,.msr-charts{grid-template-columns:repeat(2,1fr)}}
                    @media (max-width:560px){.msr-kpis,.msr-charts{grid-template-columns:1fr}}
                </style>
                <script>
                    (function(){
                        var btn=document.getElementById('genMasterBtn');
                        var rpt=document.getElementById('masterSystemReport');
                        var stamp=document.getElementById('msrStamp');
                        if(btn&&rpt){btn.addEventListener('click',function(){
                            if(stamp){stamp.textContent=new Date().toLocaleString();}
                            rpt.hidden=false;
                            rpt.scrollIntoView({behavior:'smooth',block:'start'});
                            btn.textContent='Report ready below';
                        });}
                    })();
                </script>
            </div>
            <%@ include file="/jsp/common/app_footer.jspf" %>
        </main>
    </div>
</div>

<script>
    (function () {
        const openButton = document.querySelector('[data-report-open]');
        const panel = document.querySelector('[data-report-panel]');
        const typeSelect = document.querySelector('[data-report-type]');
        const statusSelect = document.querySelector('[data-status-select]');
        const currentStatus = statusSelect ? statusSelect.dataset.currentStatus || '' : '';
        const filterGroups = document.querySelectorAll('[data-filter]');

        const statusOptions = {
            sessions: [['SCHEDULED', 'Scheduled'], ['ONGOING', 'Ongoing'], ['COMPLETED', 'Completed'], ['CANCELLED', 'Cancelled']],
            enrollments: [['PENDING', 'Pending'], ['APPROVED', 'Approved'], ['REJECTED', 'Rejected'], ['CANCELLED', 'Cancelled']],
            payments: [['PENDING', 'Pending'], ['SUCCESS', 'Success'], ['FAILED', 'Failed']],
            verification: [['PENDING', 'Pending'], ['APPROVED', 'Approved'], ['REJECTED', 'Rejected']],
            evaluations: [['PENDING', 'Pending Review'], ['COMPLETED', 'Completed Review']],
            users: [['ACTIVE', 'Active'], ['INACTIVE', 'Inactive'], ['DELETED', 'Deleted']]
        };

        const filtersByType = {
            sessions: ['status', 'date'],
            enrollments: ['status', 'date'],
            payments: ['status', 'date'],
            verification: ['status', 'date'],
            evaluations: ['status', 'date'],
            users: ['status', 'role', 'date']
        };

        function setPanelOpen() {
            if (panel) panel.classList.add('is-open');
        }

        function updateFilters() {
            if (!typeSelect || !statusSelect) return;
            const type = typeSelect.value || 'sessions';
            const visibleFilters = filtersByType[type] || [];

            statusSelect.innerHTML = '<option value="">All statuses</option>';
            (statusOptions[type] || []).forEach(function (option) {
                const item = document.createElement('option');
                item.value = option[0];
                item.textContent = option[1];
                if (option[0] === currentStatus) item.selected = true;
                statusSelect.appendChild(item);
            });

            filterGroups.forEach(function (group) {
                const name = group.dataset.filter;
                group.hidden = visibleFilters.indexOf(name) === -1;
            });
        }

        if (openButton) {
            openButton.addEventListener('click', setPanelOpen);
        }

        if (typeSelect) {
            typeSelect.addEventListener('change', function () {
                setPanelOpen();
                updateFilters();
            });
        }
        updateFilters();
    })();
</script>
</body>
</html>
