<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="model.entity.Instructor" %>
<%@ page import="model.entity.InstructorPaymentSettings" %>
<%
  request.setAttribute("activeMenu", "payment-settings");
  String ctx = request.getContextPath();
  Instructor instructor = (Instructor) request.getAttribute("instructor");
  InstructorPaymentSettings settings = (InstructorPaymentSettings) request.getAttribute("settings");
  String qrPreviewUrl = (String) request.getAttribute("qrPreviewUrl");
  String success = (String) request.getAttribute("success");
  String error = (String) request.getAttribute("error");

  String bankName = settings == null || settings.getBankName() == null ? "" : settings.getBankName();
  String accountHolder = settings == null || settings.getAccountHolderName() == null ? "" : settings.getAccountHolderName();
  String accountNumber = settings == null || settings.getAccountNumber() == null ? "" : settings.getAccountNumber();
  String paymentNotes = settings == null || settings.getPaymentNotes() == null ? "" : settings.getPaymentNotes();
  boolean hasQr = qrPreviewUrl != null && !qrPreviewUrl.trim().isEmpty();
  boolean configured = settings != null && settings.isUsable();
%>
<%!
  private static String esc(Object val) {
    if (val == null) return "";
    return String.valueOf(val).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
  }
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
  <title data-i18n="meta.instructorPaymentSettingsTitle">Payment Settings - e-Tasmi</title>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <%@ include file="/jsp/common/instructor_ui_head.jspf" %>
  <link rel="stylesheet" href="<%= ctx %>/css/instructor-page-headers.css?v=20260625-student-theme">
  <link rel="stylesheet" href="<%= ctx %>/css/instructor-theme-dark.css?v=20260609-instructor-dark">
  <script defer src="<%= ctx %>/assets/js/app.js"></script>
  <style>
    /* Payment settings — centered QR-only layout */
    .ips-wrap{max-width:580px;margin:0 auto}
    .ips-qr-center{display:flex;flex-direction:column;align-items:center;gap:0}
    .ips-card{background:#fff;border:1px solid #e2e8f0;border-radius:20px;padding:32px 36px;box-shadow:0 8px 28px rgba(15,23,42,.07);text-align:center}
    @media(max-width:640px){.ips-card{padding:22px 18px}}
    .ips-card__icon{width:52px;height:52px;margin:0 auto 12px;background:linear-gradient(135deg,#ecfdf5,#d1fae5);border-radius:16px;display:flex;align-items:center;justify-content:center;color:#059669}
    .ips-card__icon svg{width:28px;height:28px}
    .ips-card__title{font-size:20px;font-weight:800;color:#0f172a;margin:0 0 6px}
    .ips-card__sub{font-size:14px;color:#64748b;margin:0 0 24px;max-width:400px;margin-inline:auto;margin-bottom:24px}
    /* Drop zone */
    .ips-qr-drop{display:flex;flex-direction:column;align-items:center;gap:10px;
      border:2px dashed #cbd5e1;border-radius:18px;padding:28px 24px;
      background:#f8fafc;cursor:pointer;transition:border-color .2s,background .2s;
      position:relative;overflow:hidden}
    .ips-qr-drop:hover,.ips-qr-drop:focus{border-color:#059669;background:#f0fdf4;outline:none}
    .ips-qr-drop--has-image{border-style:solid;border-color:#a7f3d0;background:#f0fdf4}
    .ips-qr-frame-lg{width:180px;height:180px;border-radius:16px;overflow:hidden;
      display:flex;align-items:center;justify-content:center;
      background:#fff;border:1px solid #e2e8f0;box-shadow:0 4px 14px rgba(0,0,0,.06)}
    .ips-qr-frame-lg img{width:100%;height:100%;object-fit:contain}
    .ips-qr-frame-lg svg{width:64px;height:64px;color:#94a3b8}
    .ips-qr-drop__label{font-size:14px;font-weight:700;color:#334155}
    .ips-qr-drop__hint{font-size:12px;color:#94a3b8}
    .ips-file-hidden{position:absolute;inset:0;width:100%;height:100%;opacity:0;cursor:pointer}
    .ips-qr-filename{font-size:12px;color:#059669;font-weight:600;margin-top:4px;min-height:16px;display:block}
    .ips-remove-row{display:flex;align-items:center;justify-content:center;gap:8px;margin-top:14px;font-size:13px;color:#64748b;cursor:pointer}
    .ips-remove-row input{accent-color:#ef4444}
    /* Save button */
    .ips-btn--save{width:100%;display:flex;align-items:center;justify-content:center;gap:10px;
      margin-top:22px;padding:13px 24px;border-radius:14px;border:none;
      background:linear-gradient(135deg,#059669,#047857);color:#fff;
      font-size:15px;font-weight:700;cursor:pointer;
      box-shadow:0 4px 18px rgba(5,150,105,.3);
      transition:transform .15s ease,box-shadow .15s ease}
    .ips-btn--save:hover{transform:translateY(-1px);box-shadow:0 6px 24px rgba(5,150,105,.4)}
    .ips-btn--save svg{width:18px;height:18px}
    /* Alert */
    .ips-alert{padding:13px 16px;border-radius:12px;font-size:14px;font-weight:600;margin-bottom:18px}
    .ips-alert--success{background:#ecfdf5;border:1px solid #a7f3d0;color:#047857}
    .ips-alert--error{background:#fef2f2;border:1px solid #fecaca;color:#b91c1c}
    /* Status badge */
    .ips-status{display:inline-flex;align-items:center;gap:7px;font-size:13px;font-weight:700;padding:6px 13px;border-radius:999px}
    .ips-status--ok{background:#ecfdf5;color:#047857}
    .ips-status--off{background:#fef3c7;color:#92400e}
    /* Dark mode */
    html[data-theme="dark"] .ips-card{background:#1e2533;border-color:rgba(255,255,255,.08);box-shadow:none}
    html[data-theme="dark"] .ips-card__title{color:#f1f5f9}
    html[data-theme="dark"] .ips-card__sub{color:#94a3b8}
    html[data-theme="dark"] .ips-qr-drop{background:#0f172a;border-color:rgba(255,255,255,.12)}
    html[data-theme="dark"] .ips-qr-drop:hover{background:#1e293b;border-color:#059669}
    html[data-theme="dark"] .ips-qr-frame-lg{background:#1e2533;border-color:rgba(255,255,255,.1)}
    html[data-theme="dark"] .ips-qr-drop__label{color:#e2e8f0}
    html[data-theme="dark"] .ips-qr-drop__hint{color:#64748b}
  </style>
</head>
<body class="instructor-premium-page instructor-package-page instructor-module-page">
<div class="app-shell">
  <%@ include file="/jsp/common/instructor_header.jspf" %>
  <div class="app-main">
    <div class="container sd-container instructor-workspace-shell">
      <div class="instructor-shell-layout">
        <%@ include file="/jsp/instructor/instructor_sidebar.jspf" %>
        <main class="instructor-shell-content" role="main">
          <div class="instructor-workspace-view iup-fade-in">

            <section class="iup-page-hero" aria-label="Payment settings">
              <span class="iup-page-hero__glow" aria-hidden="true"></span>
              <nav class="iup-page-hero__breadcrumb" aria-label="Breadcrumb">
                <a href="<%= ctx %>/instructor/dashboard" data-i18n="instructor.nav.dashboard">Dashboard</a>
                <span class="sep">&rsaquo;</span>
                <span data-i18n="instructor.nav.paymentSettings">Payment Settings</span>
              </nav>
              <div class="iup-page-hero__inner">
                <span class="iup-page-hero__icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none"><rect x="3" y="6" width="18" height="13" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M3 10h18" stroke="currentColor" stroke-width="1.8"/></svg>
                </span>
                <div class="iup-page-hero__text">
                  <p class="iup-page-hero__eyebrow" data-i18n="instructor.nav.paymentSettings">Payment Settings</p>
                  <h1 class="iup-page-hero__title" data-i18n="instructor.paymentSettings.pageTitle">Set up how students pay you</h1>
                  <p class="iup-page-hero__desc" data-i18n="instructor.paymentSettings.heroDesc">Publish your QR code and bank details. Students transfer the session fee directly to you and upload their receipt for your verification.</p>
                </div>
              </div>
              <div class="iup-page-hero__actions">
                <% if (configured) { %>
                <span class="ips-status ips-status--ok" data-i18n="instructor.paymentSettings.statusActive">Payments ready</span>
                <% } else { %>
                <span class="ips-status ips-status--off" data-i18n="instructor.paymentSettings.statusIncomplete">Setup incomplete</span>
                <% } %>
              </div>
            </section>

            <% if (success != null) { %>
            <div class="ips-alert ips-alert--success" role="status"><%= esc(success) %></div>
            <% } %>
            <% if (error != null) { %>
            <div class="ips-alert ips-alert--error" role="alert"><%= esc(error) %></div>
            <% } %>

            <div class="ips-wrap">
              <form method="post" action="<%= ctx %>/instructor/payment-settings" enctype="multipart/form-data">

                <div class="ips-card">
                  <div class="ips-card__icon" aria-hidden="true">
                    <svg viewBox="0 0 24 24" fill="none"><rect x="4" y="4" width="6" height="6" rx="1" stroke="currentColor" stroke-width="2"/><rect x="14" y="4" width="6" height="6" rx="1" stroke="currentColor" stroke-width="2"/><rect x="4" y="14" width="6" height="6" rx="1" stroke="currentColor" stroke-width="2"/><path d="M14 14h3v3M20 14v6M17 20h3" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                  </div>
                  <h2 class="ips-card__title" data-i18n="instructor.paymentSettings.detailsTitle">Your Payment QR Code</h2>
                  <p class="ips-card__sub" data-i18n="instructor.paymentSettings.detailsSub">Upload your QR code so students can scan and transfer the session fee directly to you.</p>

                  <%-- QR Drop Zone --%>
                  <div class="ips-qr-drop <%= hasQr ? "ips-qr-drop--has-image" : "" %>" data-qr-drop
                       tabindex="0" role="button"
                       aria-label="<%= hasQr ? "Replace QR code" : "Upload QR code" %>">
                    <input id="qrImage" name="qrImage" type="file" accept="image/*"
                           class="ips-file-hidden" data-qr-input aria-label="Choose QR image file">
                    <div class="ips-qr-frame-lg" data-qr-frame>
                      <% if (hasQr) { %>
                      <img src="<%= esc(qrPreviewUrl) %>" alt="Current payment QR" data-qr-img>
                      <% } else { %>
                      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><rect x="4" y="4" width="6" height="6" rx="1" stroke="currentColor" stroke-width="1.6"/><rect x="14" y="4" width="6" height="6" rx="1" stroke="currentColor" stroke-width="1.6"/><rect x="4" y="14" width="6" height="6" rx="1" stroke="currentColor" stroke-width="1.6"/><path d="M14 14h3v3M20 14v6M17 20h3" stroke="currentColor" stroke-width="1.6" stroke-linecap="round"/></svg>
                      <% } %>
                    </div>
                    <p class="ips-qr-drop__label"><%= hasQr ? "Click to replace your QR code" : "Click or drag & drop your QR code here" %></p>
                    <p class="ips-qr-drop__hint">PNG, JPG or WEBP &middot; up to 8 MB</p>
                    <span class="ips-qr-filename" data-qr-filename></span>
                  </div>

                  <% if (hasQr) { %>
                  <label class="ips-remove-row">
                    <input type="checkbox" name="removeQr" value="1">
                    <span data-i18n="instructor.paymentSettings.removeQr">Remove current QR code</span>
                  </label>
                  <% } %>

                  <button type="submit" class="ips-btn--save">
                    <svg viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/><path d="M17 21v-8H7v8M7 3v5h8" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg>
                    <span data-i18n="instructor.paymentSettings.save">Save QR Code</span>
                  </button>
                </div>

              </form>
            </div>

          </div>
          <%@ include file="/jsp/common/app_footer.jspf" %>
        </main>
      </div>
    </div>
  </div>
</div>
<script>
(() => {
  const input    = document.querySelector('[data-qr-input]');
  const drop     = document.querySelector('[data-qr-drop]');
  const frame    = document.querySelector('[data-qr-frame]');
  const filename = document.querySelector('[data-qr-filename]');
  if (!input) return;

  function previewFile(file) {
    if (!file || !file.type.startsWith('image/')) return;
    if (filename) filename.textContent = file.name;
    if (drop) drop.classList.add('ips-qr-drop--has-image');
    const url = URL.createObjectURL(file);
    if (frame) {
      frame.innerHTML = '';
      const img = document.createElement('img');
      img.src = url;
      img.alt = 'Selected QR preview';
      img.onload = () => URL.revokeObjectURL(url);
      frame.appendChild(img);
    }
  }

  input.addEventListener('change', () => {
    if (input.files && input.files[0]) previewFile(input.files[0]);
  });

  /* Drag & drop */
  if (drop) {
    drop.addEventListener('dragover', e => { e.preventDefault(); drop.classList.add('ips-qr-drop--hover'); });
    drop.addEventListener('dragleave', () => drop.classList.remove('ips-qr-drop--hover'));
    drop.addEventListener('drop', e => {
      e.preventDefault();
      drop.classList.remove('ips-qr-drop--hover');
      const file = e.dataTransfer && e.dataTransfer.files[0];
      if (file) { previewFile(file); /* assign to hidden input via DataTransfer */ }
    });
    drop.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); input.click(); } });
  }
})();
</script>
</body>
</html>
