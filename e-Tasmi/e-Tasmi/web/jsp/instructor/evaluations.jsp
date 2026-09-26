<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="model.entity.Evaluation" %>
<%@ page import="model.entity.Recitation" %>
<%@ page import="model.service.RecitationAiAnalysisService" %>
<%@ page import="model.service.RecitationAiAnalysisService.Status" %>
<%!
  private String escapeHtml(Object value) {
    if (value == null) {
      return "";
    }
    return String.valueOf(value)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
  }

  /** JSON-safe string encoder for embedding inside <script type="application/json">. */
  private String jsonStr(Object value) {
    if (value == null) return "null";
    String s = String.valueOf(value);
    StringBuilder sb = new StringBuilder("\"");
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"':  sb.append("\\\""); break;
        case '\\': sb.append("\\\\"); break;
        case '/':  sb.append("\\/"); break;
        case '\b': sb.append("\\b"); break;
        case '\f': sb.append("\\f"); break;
        case '\n': sb.append("\\n"); break;
        case '\r': sb.append("\\r"); break;
        case '\t': sb.append("\\t"); break;
        default:
          if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
          else sb.append(c);
      }
    }
    return sb.append("\"").toString();
  }

  private String accuracyBand(double percent) {
    if (percent >= 90.0) return "Excellent";
    if (percent >= 75.0) return "Good";
    if (percent >= 50.0) return "Fair";
    return "Needs Improvement";
  }

  private String accuracyBandClass(double percent) {
    if (percent >= 90.0) return "ai-badge--excellent";
    if (percent >= 75.0) return "ai-badge--good";
    if (percent >= 50.0) return "ai-badge--fair";
    return "ai-badge--needs";
  }

  private String buildSummary(double percent, int missing, int extra, int substitutions) {
    StringBuilder sb = new StringBuilder();
    if (percent >= 90.0) {
      sb.append("The student recited the text almost entirely correctly.");
    } else if (percent >= 75.0) {
      sb.append("The student recited most parts correctly with a few noticeable issues.");
    } else if (percent >= 50.0) {
      sb.append("The student recited part of the text correctly but made several mistakes.");
    } else {
      sb.append("The recitation has many mismatches with the expected text and needs more practice.");
    }
    if (missing > 0) sb.append(" Some expected words appear to be missing.");
    if (substitutions > 0) sb.append(" A few words were recited incorrectly.");
    if (extra > 0) sb.append(" Some extra or repeated words were detected.");
    return sb.toString();
  }

  private String renderChipList(java.util.List<String> values, String cssClass, int max) {
    if (values == null || values.isEmpty()) {
      return "<span class=\"ai-muted\">None detected</span>";
    }
    int limit = Math.min(values.size(), max);
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < limit; i++) {
      sb.append("<span class=\"ai-chip ").append(cssClass).append("\">")
        .append(escapeHtml(values.get(i)))
        .append("</span>");
    }
    if (values.size() > limit) {
      sb.append("<span class=\"ai-chip ai-chip--more\">+")
        .append(values.size() - limit)
        .append(" more</span>");
    }
    return sb.toString();
  }
%>
<%
  request.setAttribute("activeMenu", "evaluations");
  List<Map<String, Object>> recitationRows = (List<Map<String, Object>>) request.getAttribute("recitationRows");

  // Active vs Reviewed split — populated by InstructorEvaluationServlet#doGet.
  List<Map<String, Object>> activeSessionGroups =
          (List<Map<String, Object>>) request.getAttribute("activeSessionGroups");
  if (activeSessionGroups == null) activeSessionGroups = java.util.Collections.emptyList();

  List<Map<String, Object>> reviewedSessionGroups =
          (List<Map<String, Object>>) request.getAttribute("reviewedSessionGroups");
  if (reviewedSessionGroups == null) reviewedSessionGroups = java.util.Collections.emptyList();

  // Legacy union — kept so any downstream consumer that still reads
  // sessionGroups (e.g. AI analyze auto-open) keeps working.
  List<Map<String, Object>> sessionGroups = (List<Map<String, Object>>) request.getAttribute("sessionGroups");
  if (sessionGroups == null) {
    java.util.ArrayList<Map<String, Object>> mergedGroups = new java.util.ArrayList<>(activeSessionGroups);
    mergedGroups.addAll(reviewedSessionGroups);
    sessionGroups = mergedGroups;
  }
  boolean hasAnySession = !sessionGroups.isEmpty();

  Map<Long, RecitationAiAnalysisService.AnalysisResult> analysisByRecitationId =
          (Map<Long, RecitationAiAnalysisService.AnalysisResult>) request.getAttribute("analysisByRecitationId");
  if (analysisByRecitationId == null) analysisByRecitationId = java.util.Collections.emptyMap();
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
  <title data-i18n="meta.instructorEvaluationsTitle">Evaluate Recitations - e-Tasmi</title>
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <%@ include file="/jsp/common/instructor_ui_head.jspf" %>
  <link rel="stylesheet" href="<%= request.getContextPath() %>/css/instructor-page-headers.css?v=20260625-student-theme">
  <link rel="stylesheet" href="<%= request.getContextPath() %>/css/instructor-evaluations-redesign.css?v=20260625-rtl-drawer">

  <%-- Tailwind via Play CDN. Preflight is disabled so the global app shell, sidebar,
       header, and other modules keep their existing CSS rules. Tailwind utilities are
       only used inside #eval-redesign. --%>
  <script src="https://cdn.tailwindcss.com"></script>
  <script>
    tailwind.config = {
      corePlugins: { preflight: false },
      // Bind Tailwind's `dark:` variant to the platform's <html data-theme="dark"> attribute
      // so the redesigned Evaluations page picks up the same dark mode the rest of the app uses.
      darkMode: ['variant', '[data-theme="dark"] &'],
      theme: {
        extend: {
          fontFamily: {
            sans: ['Inter', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif']
          },
          boxShadow: {
            'soft': '0 1px 2px rgba(6, 78, 59, 0.05), 0 1px 3px rgba(6, 78, 59, 0.06)',
            'lift': '0 18px 36px -18px rgba(6, 78, 59, 0.35), 0 2px 6px rgba(6, 78, 59, 0.08)',
            'mint': '0 10px 28px -10px rgba(16, 185, 129, 0.45), 0 2px 6px rgba(16, 185, 129, 0.18)',
            'forest': '0 22px 50px -22px rgba(2, 44, 34, 0.55), 0 2px 6px rgba(2, 44, 34, 0.25)'
          },
          backgroundImage: {
            'mint-glow': 'radial-gradient(120% 90% at 100% 0%, rgba(16, 185, 129, 0.18) 0%, rgba(16, 185, 129, 0) 55%)',
            'mint-glow-soft': 'radial-gradient(120% 90% at 100% 0%, rgba(16, 185, 129, 0.10) 0%, rgba(16, 185, 129, 0) 55%)',
            'forest-grain': 'linear-gradient(180deg, rgba(255,255,255,0.02) 0%, rgba(255,255,255,0) 100%)'
          },
          keyframes: {
            'mint-ping': {
              '0%':   { transform: 'scale(1)',   opacity: '0.65' },
              '80%':  { transform: 'scale(2.2)', opacity: '0' },
              '100%': { transform: 'scale(2.2)', opacity: '0' }
            }
          },
          animation: {
            'mint-ping': 'mint-ping 1.8s cubic-bezier(0, 0, 0.2, 1) infinite'
          }
        }
      }
    };
  </script>

  <style>
    /* =====================================================================
     * Scoped overrides for the redesigned Evaluations module.
     *
     *  - Color system uses ONLY emerald shades (light mint #6ee7b7, mid
     *    #10b981, deep forest #047857 / #064e3b) plus neutral surfaces
     *    that defer to the platform theme tokens (--theme-surface, etc.).
     *  - Light-mode keeps subtle white surfaces with mint accent borders.
     *  - Dark-mode uses deep navy/forest surfaces with mint glow accents.
     *  - The AI Report keeps its layout but every blue/indigo accent is
     *    replaced with emerald.
     * ================================================================== */

    #eval-redesign { font-family: 'Inter', system-ui, -apple-system, 'Segoe UI', sans-serif; }
    #eval-redesign button { font-family: inherit; cursor: pointer; }
    #eval-redesign [hidden] { display: none !important; }

    /* Drawer scrollbar — soft mint tint */
    .eval-drawer-scroll::-webkit-scrollbar { width: 10px; }
    .eval-drawer-scroll::-webkit-scrollbar-thumb { background: rgba(16, 185, 129, 0.25); border-radius: 999px; }
    .eval-drawer-scroll::-webkit-scrollbar-thumb:hover { background: rgba(16, 185, 129, 0.45); }
    .eval-drawer-scroll { scrollbar-width: thin; scrollbar-color: rgba(16, 185, 129, 0.30) transparent; }

    /* Drawer reveal animations */
    @keyframes eval-drawer-in  { from { transform: translateX(40px); opacity: 0; } to { transform: translateX(0); opacity: 1; } }
    @keyframes eval-scrim-in   { from { opacity: 0; } to { opacity: 1; } }
    .eval-drawer[data-open="true"] .eval-drawer__panel { animation: eval-drawer-in 320ms cubic-bezier(.32,.72,.21,1) both; }
    .eval-drawer[data-open="true"] .eval-drawer__scrim { animation: eval-scrim-in 220ms ease-out both; }

    /* Card reveal */
    @keyframes eval-card-in { from { opacity: 0; transform: translateY(6px); } to { opacity: 1; transform: translateY(0); } }
    .eval-card-in { animation: eval-card-in 360ms ease-out both; }

    /* Session card top accent (a thin emerald gradient line that softly glows on hover) */
    .eval-card-accent::before {
      content: ""; position: absolute; left: 14px; right: 14px; top: 0; height: 1px;
      background: linear-gradient(90deg, transparent 0%, rgba(16, 185, 129, 0.55) 30%, rgba(110, 231, 183, 0.95) 50%, rgba(16, 185, 129, 0.55) 70%, transparent 100%);
      opacity: 0.35;
      transition: opacity 280ms ease;
      border-radius: 999px;
      pointer-events: none;
    }
    .eval-card-accent:hover::before,
    .eval-card-accent:focus-visible::before { opacity: 1; }

    /* ===== Audio / video player polish — neutral surface that adapts to theme ===== */
    #eval-redesign audio,
    #eval-redesign video {
      width: 100%;
      max-width: 480px;
      border-radius: 12px;
      background: rgba(15, 23, 42, 0.04);
    }
    :root[data-theme="dark"] #eval-redesign audio,
    :root[data-theme="dark"] #eval-redesign video { background: rgba(255, 255, 255, 0.04); }
    #eval-redesign video { max-height: 280px; }

    /* ============================================================
     * AI Recitation Analysis Report — emerald-only palette.
     * Light: cream-mint surfaces. Dark: deep forest with subtle glow.
     * ============================================================ */
    .ai-report {
      padding: 18px 20px;
      border: 1px solid rgba(16, 185, 129, 0.18);
      border-radius: 16px;
      background:
        radial-gradient(120% 90% at 100% 0%, rgba(16, 185, 129, 0.10) 0%, rgba(16, 185, 129, 0) 55%),
        linear-gradient(180deg, #ffffff 0%, #f0fdf4 100%);
      box-shadow: 0 1px 2px rgba(6, 78, 59, 0.05), 0 8px 24px -16px rgba(6, 78, 59, 0.20);
      display: flex; flex-direction: column; gap: 14px;
      animation: ai-report-reveal 320ms ease-out both;
    }
    :root[data-theme="dark"] .ai-report {
      border-color: rgba(45, 212, 191, 0.22);
      background:
        radial-gradient(120% 90% at 100% 0%, rgba(45, 212, 191, 0.10) 0%, rgba(45, 212, 191, 0) 55%),
        linear-gradient(180deg, var(--theme-surface) 0%, var(--theme-surface-raised) 100%);
      box-shadow: 0 1px 2px rgba(0, 0, 0, 0.45), 0 18px 40px -20px rgba(0, 0, 0, 0.55);
    }
    @keyframes ai-report-reveal {
      from { opacity: 0; transform: translateY(6px); }
      to   { opacity: 1; transform: translateY(0); }
    }

    .ai-status-card {
      padding: 14px 16px; border-radius: 12px; display: flex; gap: 12px;
      align-items: flex-start; border: 1px solid;
    }
    .ai-status-card__icon {
      flex-shrink: 0; width: 34px; height: 34px; border-radius: 8px;
      display: inline-flex; align-items: center; justify-content: center;
      font-weight: 700; font-size: 0.7rem; letter-spacing: 0.04em;
    }
    .ai-status-card__body { display: flex; flex-direction: column; gap: 4px; flex: 1; }
    .ai-status-card__title { font-weight: 700; font-size: 0.95rem; }
    .ai-status-card__reason { font-size: 0.85rem; line-height: 1.55; }
    .ai-status-card__transcript {
      margin-top: 6px; padding: 8px 10px;
      background: rgba(255,255,255,0.55); border-radius: 8px;
      font-family: "Noto Naskh Arabic", "Amiri", serif; font-size: 0.95rem;
      color: #064e3b; max-height: 120px; overflow-y: auto; word-break: break-word;
    }
    :root[data-theme="dark"] .ai-status-card__transcript {
      background: rgba(255, 255, 255, 0.04); color: var(--theme-text-soft);
    }
    .ai-status-card--rejected { background: #fef2f2; border-color: #fecaca; color: #7f1d1d; }
    .ai-status-card--rejected .ai-status-card__icon { background: #b91c1c; color: #fff; }
    .ai-status-card--invalid  { background: #fffbeb; border-color: #fde68a; color: #78350f; }
    .ai-status-card--invalid  .ai-status-card__icon { background: #b45309; color: #fff; }
    .ai-status-card--cannot   { background: #f1f5f9; border-color: #cbd5e1; color: #334155; }
    .ai-status-card--cannot   .ai-status-card__icon { background: #475569; color: #fff; }
    .ai-status-card--failed   { background: #fef2f2; border-color: #fecaca; color: #7f1d1d; }
    .ai-status-card--failed   .ai-status-card__icon { background: #991b1b; color: #fff; }
    /* Dark mode for the status cards keeps the semantic accent colors but
       softens them against the navy surface. */
    :root[data-theme="dark"] .ai-status-card--rejected,
    :root[data-theme="dark"] .ai-status-card--failed {
      background: rgba(248, 113, 113, 0.10); border-color: rgba(248, 113, 113, 0.32); color: #fca5a5;
    }
    :root[data-theme="dark"] .ai-status-card--invalid {
      background: rgba(251, 191, 36, 0.10); border-color: rgba(251, 191, 36, 0.32); color: #fcd34d;
    }
    :root[data-theme="dark"] .ai-status-card--cannot {
      background: var(--theme-surface-muted); border-color: var(--theme-border); color: var(--theme-text-soft);
    }

    .ai-report__head {
      display: flex; align-items: center; justify-content: space-between;
      gap: 12px; flex-wrap: wrap;
    }
    .ai-report__title { display: flex; align-items: center; gap: 10px; }
    .ai-report__icon {
      display: inline-flex; align-items: center; justify-content: center;
      width: 36px; height: 36px; border-radius: 10px;
      background: linear-gradient(135deg, #10b981, #047857);
      color: #fff; font-weight: 700;
      font-size: 0.72rem; letter-spacing: 0.04em;
      box-shadow: 0 6px 14px rgba(16, 185, 129, 0.30);
    }
    .ai-report__heading { font-weight: 700; font-size: 0.98rem; color: #064e3b; }
    .ai-report__sub { font-size: 0.78rem; color: #4b5563; margin-top: 2px; }
    :root[data-theme="dark"] .ai-report__heading { color: var(--theme-text); }
    :root[data-theme="dark"] .ai-report__sub { color: var(--theme-text-muted); }
    .ai-report__score { display: flex; align-items: center; gap: 10px; }
    .ai-score-value { font-weight: 700; font-size: 1.45rem; color: #064e3b; line-height: 1; }
    :root[data-theme="dark"] .ai-score-value { color: var(--theme-text); }

    .ai-badge {
      display: inline-flex; align-items: center;
      padding: 4px 10px; border-radius: 999px;
      font-size: 0.72rem; font-weight: 600; letter-spacing: 0.02em;
    }
    /* Score band — emerald spectrum: light mint → mid → deeper → muted neutral. No blue. */
    .ai-badge--excellent { background: #d1fae5; color: #065f46; border: 1px solid #6ee7b7; }
    .ai-badge--good      { background: #ecfdf5; color: #047857; border: 1px solid #a7f3d0; }
    .ai-badge--fair      { background: #fef9c3; color: #854d0e; border: 1px solid #fde68a; }
    .ai-badge--needs     { background: #fee2e2; color: #991b1b; border: 1px solid #fecaca; }
    :root[data-theme="dark"] .ai-badge--excellent {
      background: rgba(45, 212, 191, 0.18); color: #6ee7d6; border-color: rgba(45, 212, 191, 0.40);
    }
    :root[data-theme="dark"] .ai-badge--good {
      background: rgba(45, 212, 191, 0.10); color: #99f6e4; border-color: rgba(45, 212, 191, 0.28);
    }
    :root[data-theme="dark"] .ai-badge--fair {
      background: rgba(251, 191, 36, 0.14); color: #fcd34d; border-color: rgba(251, 191, 36, 0.32);
    }
    :root[data-theme="dark"] .ai-badge--needs {
      background: rgba(248, 113, 113, 0.14); color: #fca5a5; border-color: rgba(248, 113, 113, 0.32);
    }

    .ai-report__stats {
      display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px;
    }
    .ai-stat {
      display: flex; flex-direction: column; gap: 2px;
      padding: 10px 12px; border-radius: 12px;
      background: #ffffff; border: 1px solid rgba(16, 185, 129, 0.18);
    }
    :root[data-theme="dark"] .ai-stat {
      background: var(--theme-surface-raised); border-color: var(--theme-border);
    }
    .ai-stat__label {
      font-size: 0.7rem; color: #4b5563;
      text-transform: uppercase; letter-spacing: 0.06em; font-weight: 600;
    }
    .ai-stat__value { font-size: 1.1rem; font-weight: 700; color: #0f172a; }
    :root[data-theme="dark"] .ai-stat__label { color: var(--theme-text-muted); }
    :root[data-theme="dark"] .ai-stat__value { color: var(--theme-text); }
    .ai-stat--ok    { border-left: 3px solid #10b981; }
    .ai-stat--miss  { border-left: 3px solid #ef4444; }
    .ai-stat--sub   { border-left: 3px solid #f59e0b; }
    .ai-stat--extra { border-left: 3px solid #64748b; }

    .ai-report__grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .ai-section {
      background: #ffffff;
      border: 1px solid rgba(16, 185, 129, 0.16);
      border-radius: 12px; padding: 12px 14px;
    }
    :root[data-theme="dark"] .ai-section {
      background: var(--theme-surface-raised); border-color: var(--theme-border);
    }
    /* Section accents — replaced indigo/cyan with emerald spectrum. */
    .ai-section--summary {
      background: linear-gradient(180deg, #ecfdf5 0%, #f0fdfa 100%);
      border-color: #a7f3d0;
    }
    .ai-section--match {
      background: linear-gradient(180deg, #f0fdf4 0%, #ecfdf5 100%);
      border-color: #86efac;
    }
    .ai-section--reference {
      background: #f0fdf4;
      border-color: #bbf7d0;
    }
    :root[data-theme="dark"] .ai-section--summary {
      background: linear-gradient(180deg, rgba(45, 212, 191, 0.08) 0%, var(--theme-surface-raised) 100%);
      border-color: rgba(45, 212, 191, 0.25);
    }
    :root[data-theme="dark"] .ai-section--match {
      background: linear-gradient(180deg, rgba(110, 231, 214, 0.06) 0%, var(--theme-surface-raised) 100%);
      border-color: rgba(45, 212, 191, 0.22);
    }
    :root[data-theme="dark"] .ai-section--reference {
      background: var(--theme-surface-raised); border-color: var(--theme-border);
    }
    .ai-section__title {
      margin: 0 0 8px; font-size: 0.85rem;
      color: #064e3b; font-weight: 700; letter-spacing: 0.01em;
    }
    :root[data-theme="dark"] .ai-section__title { color: var(--theme-text); }
    .ai-section__hint { font-weight: 400; color: #4b5563; font-size: 0.76rem; }
    :root[data-theme="dark"] .ai-section__hint { color: var(--theme-text-muted); }
    .ai-section__body {
      font-size: 0.9rem; color: #0f172a; line-height: 1.6;
      max-height: 160px; overflow-y: auto; padding-right: 4px; word-break: break-word;
    }
    :root[data-theme="dark"] .ai-section__body { color: var(--theme-text-soft); }
    .ai-text-arabic {
      font-family: "Noto Naskh Arabic", "Amiri", "Scheherazade New", serif;
      font-size: 1.05rem; line-height: 1.9;
    }
    .ai-mistakes { display: flex; flex-direction: column; gap: 8px; }
    .ai-mistakes__row { display: grid; grid-template-columns: 140px 1fr; gap: 10px; align-items: start; }
    .ai-mistakes__label { font-size: 0.78rem; color: #475569; font-weight: 600; padding-top: 4px; }
    :root[data-theme="dark"] .ai-mistakes__label { color: var(--theme-text-muted); }
    .ai-chip-list { display: flex; flex-wrap: wrap; gap: 6px; }
    .ai-chip {
      display: inline-flex; align-items: center;
      padding: 3px 9px; border-radius: 999px;
      font-size: 0.82rem; border: 1px solid transparent;
      font-family: "Noto Naskh Arabic", "Amiri", inherit;
    }
    .ai-chip--miss  { background: #fee2e2; color: #991b1b; border-color: #fecaca; }
    .ai-chip--sub   { background: #fef3c7; color: #854d0e; border-color: #fde68a; }
    .ai-chip--extra { background: #f1f5f9; color: #334155; border-color: #cbd5e1; }
    .ai-chip--more  { background: #f1f5f9; color: #475569; border-color: #cbd5e1; font-size: 0.72rem; }
    :root[data-theme="dark"] .ai-chip--miss  { background: rgba(248, 113, 113, 0.14); color: #fca5a5; border-color: rgba(248, 113, 113, 0.32); }
    :root[data-theme="dark"] .ai-chip--sub   { background: rgba(251, 191, 36, 0.14); color: #fcd34d; border-color: rgba(251, 191, 36, 0.32); }
    :root[data-theme="dark"] .ai-chip--extra,
    :root[data-theme="dark"] .ai-chip--more  { background: var(--theme-surface-muted); color: var(--theme-text-muted); border-color: var(--theme-border); }
    .ai-muted { color: #94a3b8; font-size: 0.85rem; }
    :root[data-theme="dark"] .ai-muted { color: var(--theme-text-muted); }
    .ai-summary-text { margin: 0; font-size: 0.92rem; color: #0f172a; line-height: 1.55; }
    .ai-summary-text--feedback { margin-top: 8px; color: #0f172a; }
    :root[data-theme="dark"] .ai-summary-text,
    :root[data-theme="dark"] .ai-summary-text--feedback { color: var(--theme-text); }
    .ai-note-list { margin: 0; padding-left: 20px; font-size: 0.9rem; color: #0f172a; line-height: 1.6; }
    :root[data-theme="dark"] .ai-note-list { color: var(--theme-text-soft); }
    .ai-note-list li { margin-bottom: 4px; }

    .ai-conf {
      display: flex; align-items: center; gap: 10px;
      margin-top: 10px; font-size: 0.78rem; color: #475569;
    }
    :root[data-theme="dark"] .ai-conf { color: var(--theme-text-muted); }
    .ai-conf__label { font-weight: 600; }
    .ai-conf__bar { flex: 1; min-width: 100px; height: 6px; background: #e5e7eb; border-radius: 999px; overflow: hidden; }
    :root[data-theme="dark"] .ai-conf__bar { background: var(--theme-border); }
    /* Confidence bar — pure emerald gradient, no blue. */
    .ai-conf__fill { display: block; height: 100%; background: linear-gradient(90deg, #6ee7b7, #10b981, #047857); border-radius: 999px; }
    .ai-conf__value { font-weight: 700; color: #064e3b; }
    :root[data-theme="dark"] .ai-conf__value { color: var(--theme-text); }

    /* Passage-mismatch banner — kept warm because it's a contextual warning,
       but in dark mode it shifts to a muted teal so we don't introduce blue. */
    .ai-passage-banner {
      display: flex; gap: 12px; padding: 14px 16px;
      background: #fffbeb; border: 1px solid #fde68a;
      border-left: 4px solid #d97706; border-radius: 10px; color: #78350f;
    }
    :root[data-theme="dark"] .ai-passage-banner {
      background: rgba(45, 212, 191, 0.08); border: 1px solid rgba(45, 212, 191, 0.22);
      border-left: 4px solid #2dd4bf; color: var(--theme-text-soft);
    }
    .ai-passage-banner__icon {
      flex: 0 0 32px; height: 32px; border-radius: 50%;
      background: #d97706; color: #fff; font-weight: 700;
      display: inline-flex; align-items: center; justify-content: center; font-size: 1rem;
    }
    :root[data-theme="dark"] .ai-passage-banner__icon { background: #2dd4bf; color: #0b1220; }
    .ai-passage-banner__body { flex: 1; display: flex; flex-direction: column; gap: 6px; }
    .ai-passage-banner__title { font-weight: 700; font-size: 0.95rem; color: #78350f; }
    :root[data-theme="dark"] .ai-passage-banner__title { color: var(--theme-text); }
    .ai-passage-banner__text { font-size: 0.88rem; line-height: 1.55; }
    .ai-passage-banner__chips { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-top: 4px; }
    .ai-passage-banner__chiplabel {
      font-size: 0.78rem; font-weight: 600; color: #78350f;
      letter-spacing: 0.02em; text-transform: uppercase;
    }
    :root[data-theme="dark"] .ai-passage-banner__chiplabel { color: var(--theme-text-muted); }
    .ai-passage-chip {
      display: inline-block; padding: 3px 10px;
      border-radius: 999px; background: #fff;
      border: 1px solid #fde68a; font-size: 0.8rem;
      color: #92400e; font-weight: 600;
    }
    :root[data-theme="dark"] .ai-passage-chip {
      background: var(--theme-surface); border-color: var(--theme-border); color: var(--theme-text-soft);
    }
    .ai-passage-banner__note { font-size: 0.82rem; font-style: italic; color: #92400e; }
    .ai-passage-banner__hint { font-size: 0.78rem; color: #78350f; margin-top: 2px; }
    :root[data-theme="dark"] .ai-passage-banner__note,
    :root[data-theme="dark"] .ai-passage-banner__hint { color: var(--theme-text-muted); }

    /* "Analyze with eTasmi AI" button — emerald only. */
    .ai-analyze-btn {
      position: relative; display: inline-flex; align-items: center; gap: 10px;
      padding: 10px 18px; border-radius: 12px;
      border: 1px solid rgba(16, 185, 129, 0.30);
      background: rgba(236, 253, 245, 0.85);
      color: #047857;
      font-weight: 600; font-size: 0.85rem; line-height: 1; user-select: none;
      transition: background 180ms ease, border-color 180ms ease, transform 180ms ease, box-shadow 220ms ease;
      box-shadow: 0 1px 2px rgba(6, 78, 59, 0.05);
    }
    .ai-analyze-btn:hover, .ai-analyze-btn:focus-visible {
      background: #d1fae5; border-color: rgba(16, 185, 129, 0.55);
      transform: translateY(-1px);
      box-shadow: 0 8px 20px -8px rgba(16, 185, 129, 0.40), 0 1px 2px rgba(6, 78, 59, 0.05);
      outline: none;
    }
    .ai-analyze-btn[disabled], .ai-analyze-btn--disabled {
      background: #f1f5f9; color: #94a3b8; border-color: #e2e8f0;
      cursor: not-allowed; transform: none; box-shadow: none;
    }
    :root[data-theme="dark"] .ai-analyze-btn {
      background: rgba(45, 212, 191, 0.10);
      border-color: rgba(45, 212, 191, 0.32);
      color: #6ee7d6;
    }
    :root[data-theme="dark"] .ai-analyze-btn:hover,
    :root[data-theme="dark"] .ai-analyze-btn:focus-visible {
      background: rgba(45, 212, 191, 0.18);
      border-color: rgba(45, 212, 191, 0.55);
      box-shadow: 0 10px 24px -8px rgba(45, 212, 191, 0.40);
    }
    :root[data-theme="dark"] .ai-analyze-btn[disabled],
    :root[data-theme="dark"] .ai-analyze-btn--disabled {
      background: var(--theme-surface-muted); color: var(--theme-text-muted); border-color: var(--theme-border);
    }
    .ai-analyze-btn__icon {
      display: inline-flex; align-items: center; justify-content: center;
      width: 18px; height: 18px; flex-shrink: 0; color: #10b981;
    }
    :root[data-theme="dark"] .ai-analyze-btn__icon { color: #6ee7d6; }
    .ai-analyze-btn__spinner {
      display: none; width: 14px; height: 14px; border-radius: 50%;
      border: 2px solid rgba(16, 185, 129, 0.30); border-top-color: #10b981;
      animation: ai-spinner-rotate 0.75s linear infinite; flex-shrink: 0;
    }
    .ai-analyze-btn.is-loading { cursor: progress; pointer-events: none; }
    .ai-analyze-btn.is-loading .ai-analyze-btn__spinner { display: inline-block; }
    .ai-analyze-btn.is-loading .ai-analyze-btn__icon    { display: none; }
    @keyframes ai-spinner-rotate { to { transform: rotate(360deg); } }

    @media (prefers-reduced-motion: reduce) {
      .ai-report, .eval-card-in,
      .eval-drawer[data-open="true"] .eval-drawer__panel,
      .eval-drawer[data-open="true"] .eval-drawer__scrim { animation: none; }
    }

    @media (max-width: 900px) {
      .ai-report__stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
      .ai-report__grid { grid-template-columns: 1fr; }
      .ai-mistakes__row { grid-template-columns: 1fr; }
    }
  </style>
</head>
<body class="instructor-premium-page instructor-package-page instructor-module-page instructor-evaluations-page">
<div class="app-shell">
  <%@ include file="/jsp/common/instructor_header.jspf" %>

  <div class="app-main">
    <div class="container sd-container instructor-workspace-shell">
      <div class="instructor-shell-layout">
        <%@ include file="/jsp/instructor/instructor_sidebar.jspf" %>

        <main class="instructor-shell-content" role="main">
          <div id="eval-redesign" class="text-slate-700 dark:text-slate-200">

            <section class="iup-page-hero iup-page-hero--evaluations" data-i18n="instructor.evaluations.title" data-i18n-attr="aria-label" aria-label="Evaluations overview">
              <span class="iup-page-hero__glow" aria-hidden="true"></span>
              <div class="iup-page-hero__inner">
                <span class="iup-page-hero__icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none"><path d="m9 12 2 2 4-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><path d="M4 5a2 2 0 0 1 2-2h9l5 5v13a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/><path d="M14 3v5h5" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round"/></svg>
                </span>
                <div class="iup-page-hero__text">
                  <p class="iup-page-hero__eyebrow" data-i18n="instructor.evaluations.title">Evaluations</p>
                  <h1 class="iup-page-hero__title" data-i18n="instructor.evaluations.title">Evaluations</h1>
                  <p class="iup-page-hero__desc" data-i18n="instructor.evaluations.heroDesc">Review student performance and provide meaningful feedback on every recitation.</p>
                </div>
              </div>
              <div class="iup-page-hero__aside">
                <div class="iup-page-hero__chips" data-i18n="instructor.evaluations.legendAria" data-i18n-attr="aria-label" aria-label="Submission status legend">
                  <span class="iup-page-hero__chip"><span class="iup-page-hero__chip-dot"></span> <span data-i18n="instructor.evaluations.chipSubmitted">Submitted</span></span>
                  <span class="iup-page-hero__chip"><span class="iup-page-hero__chip-dot iup-page-hero__chip-dot--deep"></span> <span data-i18n="instructor.evaluations.chipEvaluated">Evaluated</span></span>
                  <span class="iup-page-hero__chip"><span class="iup-page-hero__chip-dot iup-page-hero__chip-dot--muted"></span> <span data-i18n="instructor.evaluations.chipAwaiting">Awaiting Submission</span></span>
                </div>
              </div>
            </section>

            <%-- ============================================================
                 FLASH MESSAGES — Mint tinted in dark mode
                 ============================================================ --%>
            <% String success = (String) request.getAttribute("success"); %>
            <% if (success != null) { %>
            <div class="mb-5 rounded-2xl bg-emerald-50 border border-emerald-200 dark:bg-emerald-500/10 dark:border-emerald-500/30 px-4 py-3 text-sm text-emerald-800 dark:text-emerald-200 flex items-start gap-2.5">
              <svg class="h-4 w-4 mt-0.5 flex-shrink-0 text-emerald-600 dark:text-emerald-400" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/></svg>
              <%= success %>
            </div>
            <% } %>
            <% String error = (String) request.getAttribute("error"); %>
            <% if (error != null) { %>
            <div class="mb-5 rounded-2xl bg-rose-50 border border-rose-200 dark:bg-rose-500/10 dark:border-rose-500/30 px-4 py-3 text-sm text-rose-800 dark:text-rose-200 flex items-start gap-2.5">
              <svg class="h-4 w-4 mt-0.5 flex-shrink-0 text-rose-500" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z" clip-rule="evenodd"/></svg>
              <%= error %>
            </div>
            <% } %>

            <%
              /* ── KPI aggregates ── */
              int kpiTotalSessions  = sessionGroups.size();
              int kpiActionNeeded   = 0;
              int kpiTotalSubmitted = 0;
              int kpiTotalEvaluated = 0;
              int kpiTotalStudents  = 0;
              for (java.util.Map<String, Object> g : sessionGroups) {
                int sub  = g.get("submittedCount") == null ? 0 : ((Number) g.get("submittedCount")).intValue();
                int eval = g.get("evaluatedCount") == null ? 0 : ((Number) g.get("evaluatedCount")).intValue();
                int tot  = g.get("totalCount")     == null ? 0 : ((Number) g.get("totalCount")).intValue();
                kpiTotalSubmitted += sub;
                kpiTotalEvaluated += eval;
                kpiTotalStudents  += tot;
                if (sub > eval) kpiActionNeeded++;
              }
            %>
            <div class="eval-kpi-strip">
              <div class="eval-kpi">
                <div class="eval-kpi__icon eval-kpi__icon--sessions"><svg viewBox="0 0 24 24" fill="none"><rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.8"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg></div>
                <div class="eval-kpi__body"><div class="eval-kpi__value"><%= kpiTotalSessions %></div><div class="eval-kpi__label">Total Sessions</div></div>
              </div>
              <div class="eval-kpi">
                <div class="eval-kpi__icon eval-kpi__icon--students"><svg viewBox="0 0 24 24" fill="none"><circle cx="9" cy="7" r="4" stroke="currentColor" stroke-width="1.8"/><path d="M3 21a6 6 0 0 1 12 0" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg></div>
                <div class="eval-kpi__body"><div class="eval-kpi__value"><%= kpiTotalStudents %></div><div class="eval-kpi__label">Total Students</div></div>
              </div>
              <div class="eval-kpi">
                <div class="eval-kpi__icon eval-kpi__icon--pending"><svg viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8"/><path d="M12 7v5l3.5 3.5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg></div>
                <div class="eval-kpi__body"><div class="eval-kpi__value"><%= kpiTotalSubmitted %></div><div class="eval-kpi__label">Submissions</div></div>
              </div>
              <div class="eval-kpi">
                <div class="eval-kpi__icon eval-kpi__icon--action"><svg viewBox="0 0 24 24" fill="none"><path d="M12 9v4m0 4h.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><path d="M10.3 3.2 1.5 18a2 2 0 0 0 1.7 3h17.6a2 2 0 0 0 1.7-3L13.7 3.2a2 2 0 0 0-3.4 0Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round"/></svg></div>
                <div class="eval-kpi__body"><div class="eval-kpi__value"><%= kpiActionNeeded %></div><div class="eval-kpi__label">Needs Action</div></div>
              </div>
              <div class="eval-kpi">
                <div class="eval-kpi__icon eval-kpi__icon--eval"><svg viewBox="0 0 24 24" fill="none"><path d="m9 12 2 2 4-4" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.8"/></svg></div>
                <div class="eval-kpi__body"><div class="eval-kpi__value"><%= kpiTotalEvaluated %></div><div class="eval-kpi__label">Evaluated</div></div>
              </div>
            </div>

            <% if (!hasAnySession) { %>
            <%-- ============================================================
                 EMPTY STATE — instructor has no sessions at all yet.
                 ============================================================ --%>
            <section class="relative overflow-hidden rounded-3xl border border-emerald-200/50 dark:border-emerald-500/20 bg-white dark:bg-[color:var(--theme-surface)] shadow-soft p-12 text-center">
              <span aria-hidden="true" class="pointer-events-none absolute inset-0 bg-mint-glow-soft dark:bg-mint-glow"></span>
              <div class="relative mx-auto h-16 w-16 rounded-2xl bg-emerald-50 dark:bg-emerald-500/10 ring-1 ring-emerald-200 dark:ring-emerald-500/30 grid place-items-center mb-5">
                <svg class="h-7 w-7 text-emerald-600 dark:text-emerald-300" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7">
                  <path d="M4 5a2 2 0 0 1 2-2h9l5 5v13a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5Z" stroke-linejoin="round"/>
                  <path d="M14 3v5h5" stroke-linejoin="round"/>
                  <path d="m9 14 2 2 4-4" stroke-linecap="round" stroke-linejoin="round"/>
                </svg>
              </div>
              <h2 class="relative text-base font-semibold text-slate-900 dark:text-white" data-i18n="instructor.evaluations.noSessionsTitle">No sessions to evaluate yet</h2>
              <p class="relative mt-1.5 text-sm text-slate-500 dark:text-slate-400 max-w-md mx-auto" data-i18n="instructor.evaluations.noSessionsSub">Create a session and approve enrollments — the session will appear here automatically so you can review submissions and track pending students.</p>
              <a href="<%= request.getContextPath() %>/instructor/sessions"
                 class="relative mt-6 inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 dark:bg-emerald-500 dark:hover:bg-emerald-400 dark:text-emerald-950 text-white text-xs font-semibold px-4 py-2 shadow-mint transition-all">
                <svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 5v14M5 12h14" stroke-linecap="round"/></svg>
                <span data-i18n="instructor.evaluations.goToSessions">Go to Sessions</span>
              </a>
            </section>
            <% } else { %>

            <%
              /* If the previous request was an "Analyze" action, the AI result is keyed
                 by a single recitation. Use it to auto-open the corresponding session
                 view AND auto-open the drawer for that submission. */
              String autoOpenSessionId = "";
              String autoOpenRecitationId = "";
              if (!analysisByRecitationId.isEmpty() && recitationRows != null) {
                for (Map<String, Object> aRow : recitationRows) {
                  Recitation aR = aRow == null ? null : (Recitation) aRow.get("recitation");
                  if (aR == null) continue;
                  if (analysisByRecitationId.containsKey(aR.getRecitationId())) {
                    Long aSid = (Long) aRow.get("sessionId");
                    if (aSid != null) {
                      autoOpenSessionId = String.valueOf(aSid);
                      autoOpenRecitationId = String.valueOf(aR.getRecitationId());
                      break;
                    }
                  }
                }
              }
            %>

            <%-- ============================================================
                 SESSION OVERVIEW — segmented tabs:
                   • Active Evaluations — sessions still being reviewed
                   • Reviewed Sessions  — sessions the instructor has finalized
                 Each tab renders a scannable data table (stats + toolbar + rows).
                 ============================================================ --%>
            <%!
              /* Premium SaaS table-row renderer. Emits a single <tr> for the
                 redesigned Evaluations list. Keeps the `data-iup-eval-session`
                 contract so the existing roster-open / deep-link JS keeps working,
                 and adds data-status / data-search / data-sort hooks consumed by
                 the client-side filter, search and sort controller. */
              private void writeSessionRow(javax.servlet.jsp.JspWriter out,
                                           javax.servlet.http.HttpServletRequest request,
                                           java.util.Map<String, Object> group,
                                           boolean reviewed) throws java.io.IOException {
                Long gSessionId = (Long) group.get("sessionId");
                String gTitle = group.get("sessionTitle") == null ? "Untitled session" : String.valueOf(group.get("sessionTitle"));
                String gDate = group.get("sessionDate") == null ? "" : String.valueOf(group.get("sessionDate"));
                String gTime = group.get("sessionTime") == null ? "" : String.valueOf(group.get("sessionTime"));
                int submittedCount = group.get("submittedCount") == null ? 0 : ((Number) group.get("submittedCount")).intValue();
                int pendingCount   = group.get("pendingCount")   == null ? 0 : ((Number) group.get("pendingCount")).intValue();
                int evaluatedCount = group.get("evaluatedCount") == null ? 0 : ((Number) group.get("evaluatedCount")).intValue();
                int totalCount     = group.get("totalCount")     == null ? (submittedCount + pendingCount)
                                                                         : ((Number) group.get("totalCount")).intValue();
                int evalPending = Math.max(0, submittedCount - evaluatedCount);
                int progressPct = totalCount == 0 ? 0 : (int) Math.round(submittedCount * 100.0 / totalCount);

                /* Semantic status — drives both the pill badge and the filter key. */
                String statusKey;
                String statusLabel;
                String badgeClass;
                String dotClass;
                if (reviewed) {
                  statusKey = "evaluated";
                  statusLabel = "Reviewed";
                  badgeClass = "bg-emerald-50 text-emerald-700 border-emerald-200/80 dark:bg-emerald-500/10 dark:text-emerald-300 dark:border-emerald-500/30";
                  dotClass   = "bg-emerald-500";
                } else if (submittedCount == 0) {
                  statusKey = "awaiting";
                  statusLabel = "Awaiting Submission";
                  badgeClass = "bg-slate-100 text-slate-600 border-slate-200 dark:bg-white/5 dark:text-slate-300 dark:border-white/10";
                  dotClass   = "bg-slate-400 dark:bg-slate-500";
                } else if (evaluatedCount < submittedCount) {
                  statusKey = "action";
                  statusLabel = "Action Needed";
                  badgeClass = "bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-500/10 dark:text-amber-300 dark:border-amber-500/30";
                  dotClass   = "bg-amber-500";
                } else {
                  statusKey = "evaluated";
                  statusLabel = "Evaluated";
                  badgeClass = "bg-emerald-50 text-emerald-700 border-emerald-200/80 dark:bg-emerald-500/10 dark:text-emerald-300 dark:border-emerald-500/30";
                  dotClass   = "bg-emerald-500";
                }

                /* ISO date+time sorts lexicographically, so it doubles as the sort key. */
                String sortKey = (gDate.isEmpty() ? "0000-00-00" : gDate) + " " + (gTime.isEmpty() ? "00:00" : gTime);
                String searchKey = gTitle.toLowerCase();

                StringBuilder sb = new StringBuilder();
                sb.append("<tr role=\"button\" tabindex=\"0\" data-iup-eval-session=\"").append(gSessionId).append("\"")
                  .append(" data-status=\"").append(statusKey).append("\"")
                  .append(" data-search=\"").append(escapeHtml(searchKey)).append("\"")
                  .append(" data-sort=\"").append(escapeHtml(sortKey)).append("\"")
                  .append(" class=\"group cursor-pointer transition-colors hover:bg-emerald-50/60 dark:hover:bg-emerald-500/[0.06] focus:outline-none focus-visible:bg-emerald-50/80 dark:focus-visible:bg-emerald-500/[0.08]\"")
                  .append(" aria-label=\"Open roster for ").append(escapeHtml(gTitle)).append("\">");

                /* SESSION DETAILS — icon + bold title + date/time subtext */
                sb.append("<td class=\"px-5 py-4 align-middle\">")
                  .append("<div class=\"flex items-center gap-3\">")
                  .append("<span class=\"hidden sm:grid h-9 w-9 flex-shrink-0 place-items-center rounded-xl bg-emerald-50 text-emerald-600 ring-1 ring-emerald-100 dark:bg-emerald-500/10 dark:text-emerald-300 dark:ring-emerald-500/20\">")
                  .append("<svg class=\"h-[18px] w-[18px]\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\"><rect x=\"3\" y=\"5\" width=\"18\" height=\"16\" rx=\"2\"/><path d=\"M3 10h18M8 3v4M16 3v4\" stroke-linecap=\"round\"/></svg>")
                  .append("</span>")
                  .append("<div class=\"min-w-0\">")
                  .append("<div class=\"font-semibold text-slate-900 dark:text-white truncate\">").append(escapeHtml(gTitle)).append("</div>")
                  .append("<div class=\"mt-0.5 text-xs text-slate-500 dark:text-slate-400 truncate\">");
                if (gDate.isEmpty() && gTime.isEmpty()) {
                  sb.append("No schedule set");
                } else {
                  sb.append(escapeHtml(gDate.isEmpty() ? "\u2014" : gDate));
                  if (!gTime.isEmpty()) sb.append(" &middot; ").append(escapeHtml(gTime));
                }
                sb.append("</div></div></div></td>");

                /* SUBMISSION PROGRESS — "3 / 3 submitted" + slim progress bar */
                sb.append("<td class=\"px-5 py-4 align-middle\">")
                  .append("<div class=\"text-[13px] font-semibold text-slate-700 dark:text-slate-200 tabular-nums whitespace-nowrap\">")
                  .append(submittedCount).append(" / ").append(totalCount)
                  .append(" <span class=\"font-normal text-slate-400 dark:text-slate-500\">submitted</span></div>")
                  .append("<div class=\"mt-1.5 h-1.5 w-28 max-w-full rounded-full bg-slate-100 dark:bg-white/10 overflow-hidden\">")
                  .append("<div class=\"h-full rounded-full bg-emerald-500/80 dark:bg-emerald-400/80 transition-all duration-500\" style=\"width:").append(progressPct).append("%\"></div></div>")
                  .append("</td>");

                /* EVALUATION PROGRESS — "2 evaluated, 1 pending" */
                sb.append("<td class=\"px-5 py-4 align-middle hidden lg:table-cell\">");
                if (submittedCount == 0) {
                  sb.append("<span class=\"text-[13px] text-slate-400 dark:text-slate-500\">No submissions yet</span>");
                } else {
                  sb.append("<div class=\"text-[13px] text-slate-600 dark:text-slate-300 whitespace-nowrap\">")
                    .append("<span class=\"font-semibold text-slate-700 dark:text-slate-200 tabular-nums\">").append(evaluatedCount).append("</span> evaluated");
                  if (evalPending > 0) {
                    sb.append(", <span class=\"font-semibold text-amber-600 dark:text-amber-300 tabular-nums\">").append(evalPending).append("</span> pending");
                  }
                  sb.append("</div>");
                }
                sb.append("</td>");

                /* STATUS — low-saturation pill with semantic dot */
                sb.append("<td class=\"px-5 py-4 align-middle\">")
                  .append("<span class=\"inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-[11px] font-semibold whitespace-nowrap ").append(badgeClass).append("\">")
                  .append("<span class=\"h-1.5 w-1.5 rounded-full ").append(dotClass).append("\"></span>")
                  .append(escapeHtml(statusLabel)).append("</span></td>");

                /* ACTION — minimalist chevron link */
                sb.append("<td class=\"px-5 py-4 align-middle text-right\">")
                  .append("<span class=\"inline-flex items-center gap-1 text-[13px] font-semibold text-emerald-700 dark:text-emerald-300 group-hover:gap-1.5 transition-all\">")
                  .append(reviewed ? "View" : "Open Roster")
                  .append("<svg class=\"h-4 w-4\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><path d=\"M9 6l6 6-6 6\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg>")
                  .append("</span></td>");

                sb.append("</tr>");
                out.write(sb.toString());
              }
            %>
            <section data-eval-view="sessions"
                     class="eval-card-in"
                     <%= autoOpenSessionId.isEmpty() ? "" : "hidden" %>>

              <%-- Tab strip: Active vs Reviewed. Counts stay in sync with the
                   server-rendered groups so the user sees true totals at a glance. --%>
              <div class="mb-5 flex items-center justify-between gap-4 flex-wrap">
                <div role="tablist" aria-label="Evaluation buckets"
                     class="inline-flex items-center gap-1 rounded-xl bg-emerald-50/70 dark:bg-white/5 border border-emerald-100 dark:border-white/10 p-1">
                  <button type="button" role="tab" data-eval-tab="active" aria-selected="true"
                          class="px-4 py-2 rounded-lg text-sm font-semibold text-emerald-800 dark:text-emerald-100 bg-white dark:bg-emerald-500/15 shadow-sm transition-all">
                    Active Evaluations
                    <span class="ml-1.5 inline-flex items-center justify-center min-w-[1.25rem] h-5 px-1.5 rounded-full text-[11px] font-bold bg-emerald-600 text-white tabular-nums"><%= activeSessionGroups.size() %></span>
                  </button>
                  <button type="button" role="tab" data-eval-tab="reviewed" aria-selected="false"
                          class="px-4 py-2 rounded-lg text-sm font-semibold text-slate-500 dark:text-slate-400 hover:text-emerald-700 dark:hover:text-emerald-300 transition-colors">
                    Reviewed Sessions
                    <span class="ml-1.5 inline-flex items-center justify-center min-w-[1.25rem] h-5 px-1.5 rounded-full text-[11px] font-bold bg-slate-200 text-slate-700 dark:bg-white/10 dark:text-slate-200 tabular-nums"><%= reviewedSessionGroups.size() %></span>
                  </button>
                </div>
                <span class="inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-700 dark:text-emerald-300 bg-emerald-50 dark:bg-emerald-500/10 border border-emerald-200 dark:border-emerald-500/30 px-3 py-1 rounded-full">
                  <%= sessionGroups.size() %> total
                </span>
              </div>

              <%-- ====== Active panel ====== --%>
              <%
                /* Headline metrics for the stats row. "Action Needed" = sessions
                   with unreviewed submissions; "Fully Evaluated" = every
                   submission reviewed. Awaiting-submission sessions belong to
                   neither, so the three figures intentionally need not sum. */
                int statTotalActive  = activeSessionGroups.size();
                int statActionNeeded = 0;
                int statEvaluated    = 0;
                for (Map<String, Object> g : activeSessionGroups) {
                  int sc = g.get("submittedCount") == null ? 0 : ((Number) g.get("submittedCount")).intValue();
                  int ec = g.get("evaluatedCount") == null ? 0 : ((Number) g.get("evaluatedCount")).intValue();
                  if (sc > 0 && ec < sc) statActionNeeded++;
                  else if (sc > 0 && ec >= sc) statEvaluated++;
                }
              %>
              <div data-eval-tab-panel="active">
                <div class="mb-6">
                  <div class="text-[11px] font-semibold uppercase tracking-[0.20em] text-emerald-700 dark:text-emerald-300 mb-1.5">Choose a session</div>
                  <h2 class="text-lg md:text-xl font-bold text-slate-900 dark:text-white tracking-tight" data-i18n="instructor.evaluations.activeSessionsTitle">Active sessions awaiting review</h2>
                  <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">Every scheduled session shows up here. Open one to review submissions and track who hasn't submitted yet.</p>
                </div>

                <% if (activeSessionGroups.isEmpty()) { %>
                <div class="relative overflow-hidden rounded-2xl border border-emerald-200/50 dark:border-emerald-500/20 bg-white dark:bg-[color:var(--theme-surface)] shadow-soft p-10 text-center">
                  <span aria-hidden="true" class="pointer-events-none absolute inset-0 bg-mint-glow-soft dark:bg-mint-glow"></span>
                  <div class="relative mx-auto h-12 w-12 rounded-2xl bg-emerald-50 dark:bg-emerald-500/10 ring-1 ring-emerald-200 dark:ring-emerald-500/30 grid place-items-center mb-4">
                    <svg class="h-5 w-5 text-emerald-600 dark:text-emerald-300" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m9 12 2 2 4-4" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="12" r="9"/></svg>
                  </div>
                  <h3 class="relative text-base font-semibold text-slate-900 dark:text-white">All caught up</h3>
                  <p class="relative mt-1.5 text-sm text-slate-500 dark:text-slate-400 max-w-md mx-auto">Every active session has been reviewed. Check the Reviewed Sessions tab for finalized records.</p>
                </div>
                <% } else { %>

                <%-- Summary stats cards removed per UI simplification --%>

                <%-- ====== FILTER & SEARCH TOOLBAR + DATA TABLE ====== --%>
                <div data-table-scope class="overflow-hidden rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-[color:var(--theme-surface)] shadow-soft">
                  <div class="flex flex-col gap-3 border-b border-slate-100 dark:border-white/10 p-4 md:flex-row md:items-center md:justify-between">
                    <%-- Search — modern field + prominent button --%>
                    <div class="eval-search md:flex-1 md:max-w-md">
                      <div class="eval-search__field">
                        <svg class="eval-search__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3" stroke-linecap="round"/></svg>
                        <input type="search" data-table-search placeholder="Search sessions by name…" aria-label="Search sessions" class="eval-search__input" data-i18n="instructor.evaluations.searchPlaceholder" data-i18n-attr="placeholder">
                      </div>
                      <button type="button" class="eval-search__btn" data-search-trigger data-i18n="instructor.evaluations.searchBtn" data-i18n-attr="aria-label" aria-label="Search">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3" stroke-linecap="round"/></svg>
                        <span data-i18n="instructor.evaluations.searchBtn">Search</span>
                      </button>
                    </div>
                    <%-- Filters --%>
                    <div class="flex items-center gap-2">
                      <div class="relative">
                        <select data-table-status aria-label="Filter by status"
                                class="appearance-none rounded-lg border border-slate-200 dark:border-white/10 bg-white dark:bg-white/5 py-2 pl-3 pr-9 text-sm font-medium text-slate-600 dark:text-slate-300 focus:outline-none focus:border-emerald-400 focus:ring-2 focus:ring-emerald-500/30 transition cursor-pointer">
                          <option value="all">All statuses</option>
                          <option value="awaiting">Awaiting Submission</option>
                          <option value="action">Action Needed</option>
                          <option value="evaluated">Evaluated</option>
                        </select>
                        <svg class="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m6 9 6 6 6-6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                      </div>
                      <div class="relative">
                        <select data-table-sort aria-label="Sort sessions"
                                class="appearance-none rounded-lg border border-slate-200 dark:border-white/10 bg-white dark:bg-white/5 py-2 pl-3 pr-9 text-sm font-medium text-slate-600 dark:text-slate-300 focus:outline-none focus:border-emerald-400 focus:ring-2 focus:ring-emerald-500/30 transition cursor-pointer">
                          <option value="newest">Newest First</option>
                          <option value="oldest">Oldest First</option>
                        </select>
                        <svg class="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m6 9 6 6 6-6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                      </div>
                    </div>
                  </div>

                  <div class="overflow-x-auto">
                    <table class="w-full border-collapse text-left">
                      <thead>
                        <tr class="border-b border-slate-100 dark:border-white/10 bg-slate-50/70 dark:bg-white/[0.02]">
                          <th scope="col" class="px-5 py-3 text-[11px] font-semibold uppercase tracking-[0.08em] text-slate-500 dark:text-slate-400">Session Details</th>
                          <th scope="col" class="px-5 py-3 text-[11px] font-semibold uppercase tracking-[0.08em] text-slate-500 dark:text-slate-400">Submission Progress</th>
                          <th scope="col" class="px-5 py-3 text-[11px] font-semibold uppercase tracking-[0.08em] text-slate-500 dark:text-slate-400 hidden lg:table-cell">Evaluation Progress</th>
                          <th scope="col" class="px-5 py-3 text-[11px] font-semibold uppercase tracking-[0.08em] text-slate-500 dark:text-slate-400">Status</th>
                          <th scope="col" class="px-5 py-3 text-right text-[11px] font-semibold uppercase tracking-[0.08em] text-slate-500 dark:text-slate-400">Action</th>
                        </tr>
                      </thead>
                      <tbody data-table-body class="divide-y divide-slate-100 dark:divide-white/5">
                        <% for (Map<String, Object> group : activeSessionGroups) { writeSessionRow(out, request, group, false); } %>
                        <tr data-table-empty hidden>
                          <td colspan="5" class="px-5 py-12 text-center">
                            <p class="text-sm font-medium text-slate-500 dark:text-slate-400">No sessions match your filters</p>
                            <p class="mt-1 text-xs text-slate-400 dark:text-slate-500">Try clearing the search or selecting a different status.</p>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
                <% } %>
              </div>

              <%-- ====== Reviewed panel ====== --%>
              <div data-eval-tab-panel="reviewed" hidden>
                <div class="flex items-end justify-between gap-4 flex-wrap mb-5">
                  <div class="min-w-0">
                    <div class="text-[11px] font-semibold uppercase tracking-[0.20em] text-emerald-700 dark:text-emerald-300 mb-1.5">History</div>
                    <h2 class="text-lg md:text-xl font-bold text-slate-900 dark:text-white tracking-tight" data-i18n="instructor.evaluations.reviewedSessionsTitle">Reviewed sessions</h2>
                    <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">Sessions you have finalized. Open one to view evaluations or reopen it for further review.</p>
                  </div>
                </div>

                <% if (reviewedSessionGroups.isEmpty()) { %>
                <div class="relative overflow-hidden rounded-2xl border border-emerald-200/50 dark:border-emerald-500/20 bg-white dark:bg-[color:var(--theme-surface)] shadow-soft p-10 text-center">
                  <div class="relative mx-auto h-12 w-12 rounded-2xl bg-emerald-50 dark:bg-emerald-500/10 ring-1 ring-emerald-200 dark:ring-emerald-500/30 grid place-items-center mb-4">
                    <svg class="h-5 w-5 text-emerald-600 dark:text-emerald-300" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 8v13H3V8M1 3h22v5H1zM10 12h4" stroke-linejoin="round" stroke-linecap="round"/></svg>
                  </div>
                  <h3 class="relative text-base font-semibold text-slate-900 dark:text-white">No reviewed sessions yet</h3>
                  <p class="relative mt-1.5 text-sm text-slate-500 dark:text-slate-400 max-w-md mx-auto">Once you finish reviewing a session, mark it as <span class="font-medium text-emerald-700 dark:text-emerald-300">Reviewed</span> from inside its roster — it'll move here automatically.</p>
                </div>
                <% } else { %>
                <div data-table-scope class="overflow-hidden rounded-2xl border border-slate-200 dark:border-white/10 bg-white dark:bg-[color:var(--theme-surface)] shadow-soft">
                  <div class="flex flex-col gap-3 border-b border-slate-100 dark:border-white/10 p-4 md:flex-row md:items-center md:justify-between">
                    <div class="eval-search md:flex-1 md:max-w-md">
                      <div class="eval-search__field">
                        <svg class="eval-search__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3" stroke-linecap="round"/></svg>
                        <input type="search" data-table-search placeholder="Search sessions by name…" aria-label="Search reviewed sessions" class="eval-search__input" data-i18n="instructor.evaluations.searchPlaceholder" data-i18n-attr="placeholder">
                      </div>
                      <button type="button" class="eval-search__btn" data-search-trigger data-i18n="instructor.evaluations.searchBtn" data-i18n-attr="aria-label" aria-label="Search">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3" stroke-linecap="round"/></svg>
                        <span data-i18n="instructor.evaluations.searchBtn">Search</span>
                      </button>
                    </div>
                    <div class="relative">
                      <select data-table-sort aria-label="Sort reviewed sessions"
                              class="appearance-none rounded-lg border border-slate-200 dark:border-white/10 bg-white dark:bg-white/5 py-2 pl-3 pr-9 text-sm font-medium text-slate-600 dark:text-slate-300 focus:outline-none focus:border-emerald-400 focus:ring-2 focus:ring-emerald-500/30 transition cursor-pointer">
                        <option value="newest">Newest First</option>
                        <option value="oldest">Oldest First</option>
                      </select>
                      <svg class="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="m6 9 6 6 6-6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                    </div>
                  </div>

                  <div class="overflow-x-auto">
                    <table class="w-full border-collapse text-left">
                      <thead>
                        <tr class="border-b border-slate-100 dark:border-white/10 bg-slate-50/70 dark:bg-white/[0.02]">
                          <th scope="col" class="px-5 py-3 text-[11px] font-semibold uppercase tracking-[0.08em] text-slate-500 dark:text-slate-400">Session Details</th>
                          <th scope="col" class="px-5 py-3 text-[11px] font-semibold uppercase tracking-[0.08em] text-slate-500 dark:text-slate-400">Submission Progress</th>
                          <th scope="col" class="px-5 py-3 text-[11px] font-semibold uppercase tracking-[0.08em] text-slate-500 dark:text-slate-400 hidden lg:table-cell">Evaluation Progress</th>
                          <th scope="col" class="px-5 py-3 text-[11px] font-semibold uppercase tracking-[0.08em] text-slate-500 dark:text-slate-400">Status</th>
                          <th scope="col" class="px-5 py-3 text-right text-[11px] font-semibold uppercase tracking-[0.08em] text-slate-500 dark:text-slate-400">Action</th>
                        </tr>
                      </thead>
                      <tbody data-table-body class="divide-y divide-slate-100 dark:divide-white/5">
                        <% for (Map<String, Object> group : reviewedSessionGroups) { writeSessionRow(out, request, group, true); } %>
                        <tr data-table-empty hidden>
                          <td colspan="5" class="px-5 py-12 text-center">
                            <p class="text-sm font-medium text-slate-500 dark:text-slate-400">No sessions match your search</p>
                            <p class="mt-1 text-xs text-slate-400 dark:text-slate-500">Try a different keyword.</p>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </div>
                <% } %>
              </div>
            </section>

            <%-- ============================================================
                 SESSION ROSTER VIEW
                 ============================================================ --%>
            <section data-eval-view="roster"
                     class="eval-card-in"
                     <%= autoOpenSessionId.isEmpty() ? "hidden" : "" %>
                     <%= autoOpenSessionId.isEmpty() ? "" : "data-iup-auto-open=\"" + autoOpenSessionId + "\"" %>
                     <%= autoOpenRecitationId.isEmpty() ? "" : "data-iup-auto-open-recitation=\"" + autoOpenRecitationId + "\"" %>>

              <button type="button"
                      data-back-to-sessions
                      class="eval-back-btn mb-5">
                <span class="eval-back-btn__arrow">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M19 12H5M11 18l-6-6 6-6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                </span>
                <span class="eval-back-btn__label" data-i18n="instructor.evaluations.backToAll">Back to all sessions</span>
              </button>

              <header class="relative overflow-hidden rounded-2xl bg-white dark:bg-[color:var(--theme-surface)] border border-emerald-200/60 dark:border-emerald-500/20 shadow-soft p-6 md:p-7 mb-5">
                <span aria-hidden="true" class="pointer-events-none absolute inset-0 bg-mint-glow-soft dark:bg-mint-glow"></span>

                <%-- Reviewed banner — only visible when the open session is in
                     the Reviewed bucket. JS toggles [hidden] based on session state. --%>
                <div data-roster-reviewed-banner hidden
                     class="relative mb-4 flex items-start gap-2.5 rounded-xl bg-emerald-50 dark:bg-emerald-500/10 border border-emerald-200 dark:border-emerald-500/30 px-4 py-2.5 text-sm">
                  <svg class="h-4 w-4 mt-0.5 flex-shrink-0 text-emerald-600 dark:text-emerald-300" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/></svg>
                  <div class="text-emerald-800 dark:text-emerald-200">
                    <span class="font-semibold">Reviewed</span><span data-roster-reviewed-when></span>. This session is in the Reviewed Sessions tab — reopen it to make further changes.
                  </div>
                </div>

                <div class="relative flex items-start justify-between gap-4 flex-wrap">
                  <div class="min-w-0">
                    <div class="text-[11px] font-semibold uppercase tracking-[0.20em] text-emerald-700 dark:text-emerald-300 mb-1.5">Session</div>
                    <h2 class="text-xl md:text-2xl font-bold text-slate-900 dark:text-white tracking-tight" data-roster-title>Session</h2>
                    <div class="mt-2 flex items-center gap-2 text-sm text-slate-500 dark:text-slate-400">
                      <svg class="h-4 w-4 text-emerald-500/80 dark:text-emerald-300/80" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4" stroke-linecap="round"/></svg>
                      <span data-roster-meta>—</span>
                    </div>
                  </div>
                  <div class="flex items-start gap-3 flex-wrap">
                    <div class="grid grid-cols-3 gap-3">
                      <div class="rounded-xl bg-emerald-50/60 dark:bg-emerald-500/10 border border-emerald-200/60 dark:border-emerald-500/25 px-3.5 py-2.5 min-w-[92px]">
                        <div class="text-[10px] font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">Total</div>
                        <div class="mt-0.5 text-lg font-bold text-slate-900 dark:text-white tabular-nums" data-roster-total-count>0</div>
                      </div>
                      <div class="rounded-xl bg-emerald-50 dark:bg-emerald-500/15 border border-emerald-200 dark:border-emerald-500/40 px-3.5 py-2.5 min-w-[92px]">
                        <div class="text-[10px] font-semibold uppercase tracking-wider text-emerald-700 dark:text-emerald-300">Submitted</div>
                        <div class="mt-0.5 text-lg font-bold text-emerald-800 dark:text-emerald-200 tabular-nums" data-roster-submitted-count>0</div>
                      </div>
                      <div class="rounded-xl bg-slate-50 dark:bg-white/5 border border-slate-200 dark:border-white/10 px-3.5 py-2.5 min-w-[92px]">
                        <div class="text-[10px] font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400">Awaiting</div>
                        <div class="mt-0.5 text-lg font-bold text-slate-700 dark:text-slate-200 tabular-nums" data-roster-pending-count>0</div>
                      </div>
                    </div>

                    <%-- Primary action: Mark as Reviewed (active) / Reopen (reviewed). --%>
                    <button type="button" data-roster-review-action
                            class="inline-flex items-center gap-1.5 rounded-lg
                                   bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800
                                   dark:bg-emerald-500 dark:hover:bg-emerald-400 dark:active:bg-emerald-600 dark:text-emerald-950
                                   text-white text-xs font-semibold px-4 py-2.5
                                   shadow-mint hover:shadow-lg hover:-translate-y-0.5 transition-all
                                   focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:ring-offset-2 dark:focus-visible:ring-offset-[color:var(--theme-surface)]">
                      <svg class="h-3.5 w-3.5" data-roster-review-icon viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M5 13l4 4L19 7" stroke-linecap="round" stroke-linejoin="round"/></svg>
                      <span data-roster-review-label>Mark as Reviewed</span>
                    </button>
                  </div>
                </div>

                <div class="relative mt-5 flex items-center justify-between gap-3 flex-wrap">
                  <%-- Filter pill — emerald active state, no blue. --%>
                  <div class="inline-flex items-center gap-1 rounded-xl bg-emerald-50/80 dark:bg-white/5 border border-emerald-100 dark:border-white/10 p-1" role="tablist" aria-label="Filter roster">
                    <button type="button" role="tab"
                            class="px-3.5 py-1.5 rounded-lg text-xs font-semibold text-emerald-800 dark:text-emerald-200 bg-white dark:bg-emerald-500/15 shadow-sm transition-all"
                            data-roster-filter="all" aria-selected="true">All</button>
                    <button type="button" role="tab"
                            class="px-3.5 py-1.5 rounded-lg text-xs font-semibold text-slate-500 dark:text-slate-400 hover:text-emerald-700 dark:hover:text-emerald-300 transition-colors"
                            data-roster-filter="submitted" aria-selected="false">Submitted</button>
                    <button type="button" role="tab"
                            class="px-3.5 py-1.5 rounded-lg text-xs font-semibold text-slate-500 dark:text-slate-400 hover:text-emerald-700 dark:hover:text-emerald-300 transition-colors"
                            data-roster-filter="pending" aria-selected="false">Awaiting Submission</button>
                  </div>
                  <%-- Search — emerald focus ring + integrated icon. --%>
                  <div class="relative">
                    <svg class="absolute left-3.5 top-1/2 -translate-y-1/2 h-4 w-4 text-slate-400 dark:text-slate-500 transition-colors" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5" stroke-linecap="round"/></svg>
                    <input type="search" data-roster-search placeholder="Search students…"
                           class="w-full sm:w-72 pl-10 pr-3 py-2.5 text-sm rounded-lg
                                  border border-emerald-100 dark:border-white/10
                                  bg-white dark:bg-white/5
                                  text-slate-900 dark:text-slate-100
                                  placeholder:text-slate-400 dark:placeholder:text-slate-500
                                  focus:outline-none focus:border-emerald-400 dark:focus:border-emerald-500/60
                                  focus:ring-2 focus:ring-emerald-400/30 dark:focus:ring-emerald-500/30
                                  transition-all">
                  </div>
                </div>
              </header>

              <div class="relative overflow-hidden rounded-2xl bg-white dark:bg-[color:var(--theme-surface)] border border-emerald-200/60 dark:border-emerald-500/20 shadow-soft">
                <div class="overflow-x-auto">
                  <table class="w-full text-sm">
                    <thead>
                      <tr class="bg-emerald-50/40 dark:bg-white/[0.03] border-b border-emerald-100 dark:border-white/10">
                        <th class="text-left px-6 py-4 text-[10px] font-semibold uppercase tracking-[0.14em] text-slate-500 dark:text-slate-400">Student</th>
                        <th class="text-left px-6 py-4 text-[10px] font-semibold uppercase tracking-[0.14em] text-slate-500 dark:text-slate-400 hidden sm:table-cell">ID</th>
                        <th class="text-left px-6 py-4 text-[10px] font-semibold uppercase tracking-[0.14em] text-slate-500 dark:text-slate-400">Status</th>
                        <th class="text-left px-6 py-4 text-[10px] font-semibold uppercase tracking-[0.14em] text-slate-500 dark:text-slate-400 hidden md:table-cell">Submitted on</th>
                        <th class="text-right px-6 py-4 text-[10px] font-semibold uppercase tracking-[0.14em] text-slate-500 dark:text-slate-400">Action</th>
                      </tr>
                    </thead>
                    <tbody data-roster-tbody class="divide-y divide-emerald-100/70 dark:divide-white/5"></tbody>
                  </table>
                </div>
                <div hidden data-roster-empty class="px-6 py-14 text-center">
                  <div class="mx-auto h-12 w-12 rounded-full bg-emerald-50 dark:bg-emerald-500/10 ring-1 ring-emerald-200 dark:ring-emerald-500/30 grid place-items-center mb-3">
                    <svg class="h-5 w-5 text-emerald-500/80 dark:text-emerald-300/70" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5" stroke-linecap="round"/></svg>
                  </div>
                  <p class="text-sm font-medium text-slate-700 dark:text-slate-200">No students match this filter</p>
                  <p class="text-xs text-slate-500 dark:text-slate-400 mt-1">Try a different filter or clear the search.</p>
                </div>
              </div>
            </section>

            <%-- ============================================================
                 SUBMISSION DETAIL DRAWER
                 ============================================================ --%>
            <aside class="eval-drawer fixed inset-0 z-[60]" data-eval-drawer hidden data-open="false" aria-hidden="true">
              <%-- Scrim with backdrop blur for the glassmorphism feel. --%>
              <div class="eval-drawer__scrim absolute inset-0 bg-slate-950/55 dark:bg-slate-950/70 backdrop-blur-[3px]" data-drawer-scrim></div>
              <div class="eval-drawer__panel absolute right-0 top-0 h-full w-full sm:w-[640px] lg:w-[760px]
                          bg-emerald-50/40 dark:bg-[color:var(--theme-bg)]
                          border-l border-emerald-200/60 dark:border-emerald-500/20
                          shadow-2xl shadow-emerald-900/10 dark:shadow-black/60
                          backdrop-blur-md
                          flex flex-col"
                   role="dialog" aria-modal="true" aria-labelledby="eval-drawer-title">
                <%-- Drawer header — frosted glass surface with mint accent --%>
                <header class="relative flex items-center justify-between gap-3 px-6 py-4
                               bg-white/90 dark:bg-[color:var(--theme-surface)]/90
                               border-b border-emerald-200/60 dark:border-emerald-500/20
                               backdrop-blur-md">
                  <span aria-hidden="true" class="pointer-events-none absolute inset-x-12 top-0 h-px bg-gradient-to-r from-transparent via-emerald-400 to-transparent"></span>
                  <div class="min-w-0 flex items-center gap-3">
                    <div class="h-10 w-10 rounded-xl bg-emerald-100 text-emerald-700 ring-1 ring-emerald-200 dark:bg-emerald-500/15 dark:text-emerald-200 dark:ring-emerald-500/30 grid place-items-center text-sm font-bold flex-shrink-0" data-drawer-avatar>ST</div>
                    <div class="min-w-0">
                      <div class="text-[10px] font-semibold uppercase tracking-[0.16em] text-emerald-700 dark:text-emerald-300" data-drawer-eyebrow>Evaluation</div>
                      <div id="eval-drawer-title" class="font-semibold text-slate-900 dark:text-white truncate" data-drawer-title>Student</div>
                    </div>
                  </div>
                  <button type="button" data-drawer-close
                          class="h-9 w-9 rounded-lg hover:bg-emerald-50 dark:hover:bg-white/5 grid place-items-center text-slate-500 dark:text-slate-400 hover:text-emerald-700 dark:hover:text-emerald-300 transition-colors"
                          aria-label="Close evaluation panel">
                    <svg class="h-5 w-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M6 6l12 12M18 6 6 18" stroke-linecap="round"/></svg>
                  </button>
                </header>
                <div class="eval-drawer-scroll flex-1 overflow-y-auto px-5 sm:px-6 py-5">
                  <div data-drawer-body>

                    <% for (Map<String, Object> row : recitationRows) {
                         Recitation r = row == null ? null : (Recitation) row.get("recitation");
                         if (r == null) continue;
                         Evaluation evaluation = (Evaluation) row.get("evaluation");
                         String studentName = row.get("studentName") == null ? "Student" : String.valueOf(row.get("studentName"));
                         String studentIdentifier = row.get("studentIdentifier") == null ? "-" : String.valueOf(row.get("studentIdentifier"));
                         String sessionTitle = row.get("sessionTitle") == null ? "Session" : String.valueOf(row.get("sessionTitle"));
                         String quranPortion = row.get("quranPortion") == null ? "" : String.valueOf(row.get("quranPortion"));
                         RecitationAiAnalysisService.AnalysisResult analysis = analysisByRecitationId.get(r.getRecitationId());
                         boolean hasExpectedPortion = quranPortion != null && !quranPortion.trim().isEmpty();
                    %>
                    <article data-recitation-detail="<%= r.getRecitationId() %>" hidden class="space-y-4">

                      <%-- Submission media card --%>
                      <section class="rounded-2xl bg-white dark:bg-[color:var(--theme-surface)] border border-emerald-200/60 dark:border-emerald-500/20 p-5 shadow-soft">
                        <div class="flex items-center justify-between gap-3 mb-3 flex-wrap">
                          <div>
                            <div class="text-[10px] font-semibold uppercase tracking-[0.16em] text-emerald-700 dark:text-emerald-300">Submission</div>
                            <div class="text-sm font-semibold text-slate-900 dark:text-white mt-0.5"><%= escapeHtml(sessionTitle) %></div>
                          </div>
                          <div class="text-xs text-slate-500 dark:text-slate-400">
                            Submitted <span class="font-medium text-slate-700 dark:text-slate-200"><%= r.getSubmissionDate() %></span>
                          </div>
                        </div>
                        <% String p = r.getAudioFilePath(); %>
                        <% String lowerMediaPath = p == null ? "" : p.toLowerCase(); %>
                        <% boolean isVideo = lowerMediaPath.endsWith(".mp4") || lowerMediaPath.endsWith(".webm") || lowerMediaPath.endsWith(".mov") || lowerMediaPath.contains("/video/upload/"); %>
                        <% String mediaSource = null; %>
                        <% if (p != null && !p.trim().isEmpty()) {
                             if (p.startsWith("http://") || p.startsWith("https://")) {
                                 mediaSource = p;
                             } else if (p.startsWith("/")) {
                                 mediaSource = request.getContextPath() + p;
                             }
                           }
                        %>
                        <% if (p == null || p.trim().isEmpty()) { %>
                          <div class="rounded-lg bg-emerald-50/50 dark:bg-white/5 border border-emerald-100 dark:border-white/10 px-4 py-3 text-sm text-slate-500 dark:text-slate-400">Audio file unavailable.</div>
                        <% } else if (mediaSource != null) { %>
                          <% if (isVideo) { %>
                          <video controls preload="none">
                            <source src="<%= mediaSource %>">
                          </video>
                          <% } else { %>
                          <audio controls preload="none">
                            <source src="<%= mediaSource %>">
                          </audio>
                          <% } %>
                        <% } else { %>
                          <div class="rounded-lg bg-emerald-50/50 dark:bg-white/5 border border-emerald-100 dark:border-white/10 px-4 py-3 text-sm text-slate-500 dark:text-slate-400">Audio file unavailable.</div>
                        <% } %>
                      </section>

                      <% if (evaluation != null) {
                           String feedback = evaluation.getFeedback() == null || evaluation.getFeedback().trim().isEmpty()
                                   ? "Feedback recorded."
                                   : evaluation.getFeedback();
                      %>
                      <%-- Already-evaluated card — deeper forest accent. --%>
                      <section class="relative overflow-hidden rounded-2xl bg-gradient-to-br from-emerald-50 to-emerald-100/40 dark:from-emerald-500/10 dark:to-emerald-500/5 border border-emerald-200 dark:border-emerald-500/30 p-5">
                        <span aria-hidden="true" class="pointer-events-none absolute inset-0 bg-mint-glow-soft dark:bg-mint-glow"></span>
                        <div class="relative flex items-start justify-between gap-4 flex-wrap">
                          <div>
                            <div class="inline-flex items-center gap-1.5 rounded-full bg-white dark:bg-emerald-500/15 border border-emerald-200 dark:border-emerald-500/40 px-3 py-1 text-[11px] font-semibold text-emerald-700 dark:text-emerald-200 mb-3 shadow-sm">
                              <svg class="h-3 w-3" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/></svg>
                              Evaluation Completed
                            </div>
                            <div class="flex items-baseline gap-1">
                              <span class="text-3xl font-bold text-slate-900 dark:text-white tabular-nums"><%= evaluation.getScore() %></span>
                              <span class="text-base text-slate-400 dark:text-slate-500">/100</span>
                            </div>
                          </div>
                        </div>
                        <p class="relative mt-3 text-sm text-slate-700 dark:text-slate-200 leading-relaxed"><%= escapeHtml(feedback) %></p>
                      </section>
                      <% } else { %>
                      <%-- Evaluation form (and AI report if available) --%>
                      <form method="post" action="<%= request.getContextPath() %>/instructor/evaluations" class="space-y-4">
                        <input type="hidden" name="recitationId" value="<%= r.getRecitationId() %>"/>
                        <input type="hidden" name="action" value="save" data-action-input/>

                        <% if (!hasExpectedPortion) { %>
                        <div class="rounded-xl bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/30 px-4 py-3 text-sm text-amber-800 dark:text-amber-200 flex items-start gap-2.5">
                          <svg class="h-4 w-4 mt-0.5 flex-shrink-0" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M8.485 2.495c.673-1.167 2.357-1.167 3.03 0l6.28 10.875c.673 1.167-.17 2.625-1.516 2.625H3.72c-1.347 0-2.189-1.458-1.515-2.625L8.485 2.495zM10 6a.75.75 0 01.75.75v3.5a.75.75 0 01-1.5 0v-3.5A.75.75 0 0110 6zm0 9a1 1 0 100-2 1 1 0 000 2z" clip-rule="evenodd"/></svg>
                          <div>
                            <strong class="font-semibold">No expected Quran portion defined for this session.</strong>
                            <span class="block text-amber-700 dark:text-amber-300/80 text-[13px]">AI analysis is disabled until the session's portion is set.</span>
                          </div>
                        </div>
                        <% } %>

                        <% if (analysis != null && analysis.isSuccess()) {
                             double acc = analysis.getAccuracyPercent();
                             int missCount = analysis.getMissingWords().size();
                             int extraCount = analysis.getExtraWords().size();
                             int subCount = analysis.getReplacedWords().size();
                             int correctCount = analysis.getCorrectWords().size();
                             int suggestedScore = analysis.getScore();
                             String aiSummary = analysis.getSummary();
                             String aiFeedback = analysis.getFeedback();
                             String summary = (aiSummary != null && !aiSummary.trim().isEmpty())
                                     ? aiSummary
                                     : buildSummary(acc, missCount, subCount, extraCount);
                             String matchNote = analysis.getMatchedPassageNote();
                             java.util.List<String> pronunciationNotes = analysis.getPronunciationNotes();
                             double isQuranConf = analysis.getIsQuranConfidence();
                             boolean passageMatches = analysis.isMatchesExpectedPassage();
                             boolean mixedPassages = analysis.isMixedPassages();
                             java.util.List<String> detectedPassages = analysis.getDetectedPassages();
                             String referenceText = analysis.getReferenceText();
                             String band = accuracyBand(acc);
                             String bandClass = accuracyBandClass(acc);
                        %>
                        <div class="ai-report" role="region" aria-label="AI recitation analysis report">
                          <div class="ai-report__head">
                            <div class="ai-report__title">
                              <span class="ai-report__icon" aria-hidden="true">AI</span>
                              <div>
                                <div class="ai-report__heading">Recitation Analysis Report</div>
                                <div class="ai-report__sub">Automated report based on the audio transcript and the session's expected text. Reference only.</div>
                              </div>
                            </div>
                            <div class="ai-report__score">
                              <div class="ai-score-value"><%= suggestedScore %>/100</div>
                              <span class="ai-badge <%= bandClass %>"><%= band %></span>
                            </div>
                          </div>

                          <% if (!passageMatches) { %>
                          <div class="ai-passage-banner" role="alert">
                            <span class="ai-passage-banner__icon" aria-hidden="true">!</span>
                            <div class="ai-passage-banner__body">
                              <div class="ai-passage-banner__title">Passage mismatch &mdash; this is Quran recitation, but not the expected lesson.</div>
                              <div class="ai-passage-banner__text">
                                <%= escapeHtml(matchNote != null && !matchNote.trim().isEmpty()
                                        ? matchNote
                                        : "This submission appears to be Quran recitation, but it does not match the expected target passage.") %>
                              </div>
                              <% if (detectedPassages != null && !detectedPassages.isEmpty()) { %>
                              <div class="ai-passage-banner__chips">
                                <span class="ai-passage-banner__chiplabel">Detected:</span>
                                <% for (String detectedPassage : detectedPassages) { %>
                                <span class="ai-passage-chip"><%= escapeHtml(detectedPassage) %></span>
                                <% } %>
                              </div>
                              <% } %>
                              <% if (mixedPassages) { %>
                              <div class="ai-passage-banner__note">The student mixed &#257;y&#257;t from multiple s&#363;rahs.</div>
                              <% } %>
                              <div class="ai-passage-banner__hint">The mistake analysis below is based on the correct text of what the student actually recited, so you still see real recitation errors.</div>
                            </div>
                          </div>
                          <% } %>

                          <div class="ai-report__stats">
                            <div class="ai-stat ai-stat--ok">
                              <span class="ai-stat__label">Correct</span>
                              <span class="ai-stat__value"><%= correctCount %></span>
                            </div>
                            <div class="ai-stat ai-stat--miss">
                              <span class="ai-stat__label">Missing</span>
                              <span class="ai-stat__value"><%= missCount %></span>
                            </div>
                            <div class="ai-stat ai-stat--sub">
                              <span class="ai-stat__label">Incorrect</span>
                              <span class="ai-stat__value"><%= subCount %></span>
                            </div>
                            <div class="ai-stat ai-stat--extra">
                              <span class="ai-stat__label">Extra / Repeated</span>
                              <span class="ai-stat__value"><%= extraCount %></span>
                            </div>
                          </div>

                          <div class="ai-report__grid">
                            <section class="ai-section">
                              <h4 class="ai-section__title">Transcript <span class="ai-section__hint">(what the student said)</span></h4>
                              <div class="ai-section__body ai-text-arabic">
                                <%= analysis.getTranscript() == null || analysis.getTranscript().trim().isEmpty()
                                        ? "<span class=\"ai-muted\">No speech detected.</span>"
                                        : escapeHtml(analysis.getTranscript()) %>
                              </div>
                            </section>

                            <section class="ai-section">
                              <h4 class="ai-section__title">Expected Text <span class="ai-section__hint">(assigned lesson)</span></h4>
                              <div class="ai-section__body ai-text-arabic">
                                <%= analysis.getExpectedText() == null || analysis.getExpectedText().trim().isEmpty()
                                        ? "<span class=\"ai-muted\">Not provided.</span>"
                                        : escapeHtml(analysis.getExpectedText()) %>
                              </div>
                            </section>
                          </div>

                          <% if (!passageMatches && referenceText != null && !referenceText.trim().isEmpty()
                                 && !referenceText.trim().equals(analysis.getExpectedText() == null ? "" : analysis.getExpectedText().trim())) { %>
                          <section class="ai-section ai-section--reference">
                            <h4 class="ai-section__title">Reference Used for Diff <span class="ai-section__hint">(correct text of what the student actually recited)</span></h4>
                            <div class="ai-section__body ai-text-arabic"><%= escapeHtml(referenceText) %></div>
                          </section>
                          <% } %>

                          <section class="ai-section">
                            <h4 class="ai-section__title">Mistakes Detected</h4>
                            <div class="ai-mistakes">
                              <div class="ai-mistakes__row">
                                <span class="ai-mistakes__label">Missing words</span>
                                <div class="ai-chip-list"><%= renderChipList(analysis.getMissingWords(), "ai-chip--miss", 10) %></div>
                              </div>
                              <div class="ai-mistakes__row">
                                <span class="ai-mistakes__label">Incorrect words</span>
                                <div class="ai-chip-list"><%= renderChipList(analysis.getReplacedWords(), "ai-chip--sub", 10) %></div>
                              </div>
                              <div class="ai-mistakes__row">
                                <span class="ai-mistakes__label">Extra / repeated</span>
                                <div class="ai-chip-list"><%= renderChipList(analysis.getExtraWords(), "ai-chip--extra", 10) %></div>
                              </div>
                            </div>
                          </section>

                          <% if (matchNote != null && !matchNote.trim().isEmpty()) { %>
                          <section class="ai-section ai-section--match">
                            <h4 class="ai-section__title">Passage Match</h4>
                            <p class="ai-summary-text"><%= escapeHtml(matchNote) %></p>
                          </section>
                          <% } %>

                          <% if (pronunciationNotes != null && !pronunciationNotes.isEmpty()) { %>
                          <section class="ai-section">
                            <h4 class="ai-section__title">Pronunciation &amp; Tajwīd Notes</h4>
                            <ul class="ai-note-list">
                              <% for (String note : pronunciationNotes) { %>
                              <li><%= escapeHtml(note) %></li>
                              <% } %>
                            </ul>
                          </section>
                          <% } %>

                          <section class="ai-section ai-section--summary">
                            <h4 class="ai-section__title">AI Summary</h4>
                            <p class="ai-summary-text"><%= escapeHtml(summary) %></p>
                            <% if (aiFeedback != null && !aiFeedback.trim().isEmpty() && !aiFeedback.equals(summary)) { %>
                            <p class="ai-summary-text ai-summary-text--feedback"><strong>Feedback:</strong> <%= escapeHtml(aiFeedback) %></p>
                            <% } %>
                            <% if (isQuranConf > 0) { %>
                            <div class="ai-conf"><span class="ai-conf__label">Quran-match confidence</span>
                              <span class="ai-conf__bar"><span class="ai-conf__fill" style="width: <%= (int) Math.round(isQuranConf * 100) %>%"></span></span>
                              <span class="ai-conf__value"><%= (int) Math.round(isQuranConf * 100) %>%</span>
                            </div>
                            <% } %>
                          </section>
                        </div>
                        <% } else if (analysis != null && !analysis.isSuccess()) {
                             Status st = analysis.getStatus();
                             String cardClass;
                             String cardIcon;
                             String cardTitle;
                             if (st == Status.REJECTED) {
                                 cardClass = "ai-status-card--rejected";
                                 cardIcon = "NO";
                                 cardTitle = "Not a Quran recitation";
                             } else if (st == Status.CANNOT_EVALUATE) {
                                 cardClass = "ai-status-card--cannot";
                                 cardIcon = "?";
                                 cardTitle = "Cannot evaluate this audio";
                             } else {
                                 cardClass = "ai-status-card--failed";
                                 cardIcon = "X";
                                 cardTitle = "AI analysis unavailable";
                             }
                             String cardReason = analysis.getReason();
                             if (cardReason == null || cardReason.trim().isEmpty()) {
                                 cardReason = "No additional details were provided.";
                             }
                             String cardTranscript = analysis.getTranscript();
                        %>
                        <div class="ai-status-card <%= cardClass %>" role="region" aria-label="AI recitation status">
                          <span class="ai-status-card__icon" aria-hidden="true"><%= cardIcon %></span>
                          <div class="ai-status-card__body">
                            <div class="ai-status-card__title"><%= escapeHtml(cardTitle) %></div>
                            <div class="ai-status-card__reason"><%= escapeHtml(cardReason) %></div>
                            <% if (cardTranscript != null && !cardTranscript.trim().isEmpty()) { %>
                            <div class="ai-status-card__transcript" dir="rtl">
                              <strong style="font-size:0.75rem;letter-spacing:0.03em;color:#64748b;display:block;margin-bottom:4px;" dir="ltr">Detected transcript:</strong>
                              <%= escapeHtml(cardTranscript) %>
                            </div>
                            <% } %>
                          </div>
                        </div>
                        <% } %>

                        <%-- Instructor evaluation card — emerald palette throughout. --%>
                        <section class="rounded-2xl bg-white dark:bg-[color:var(--theme-surface)] border border-emerald-200/60 dark:border-emerald-500/20 p-6 shadow-soft">
                          <div class="mb-5">
                            <div class="text-[10px] font-semibold uppercase tracking-[0.16em] text-emerald-700 dark:text-emerald-300">Instructor evaluation</div>
                            <h3 class="text-base font-semibold text-slate-900 dark:text-white mt-1">Score &amp; feedback</h3>
                            <p class="text-xs text-slate-500 dark:text-slate-400 mt-1">The AI report above is a reference only. Write your own feedback and set the final score.</p>
                          </div>
                          <div class="grid grid-cols-1 md:grid-cols-3 gap-4">
                            <div class="md:col-span-1">
                              <label for="score-<%= r.getRecitationId() %>" class="block text-[11px] font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400 mb-1.5">Score (0-100)</label>
                              <input id="score-<%= r.getRecitationId() %>" type="number" name="score" min="0" max="100" required
                                     class="w-full rounded-lg
                                            border border-emerald-100 dark:border-white/10
                                            bg-white dark:bg-white/5
                                            px-3 py-2 text-sm
                                            text-slate-900 dark:text-slate-100
                                            placeholder:text-slate-400 dark:placeholder:text-slate-500
                                            focus:outline-none
                                            focus:border-emerald-400 dark:focus:border-emerald-500/60
                                            focus:ring-2 focus:ring-emerald-400/30 dark:focus:ring-emerald-500/30
                                            transition-all" />
                            </div>
                            <div class="md:col-span-2">
                              <label for="feedback-<%= r.getRecitationId() %>" class="block text-[11px] font-semibold uppercase tracking-wider text-slate-500 dark:text-slate-400 mb-1.5">Feedback</label>
                              <textarea id="feedback-<%= r.getRecitationId() %>" name="feedback" rows="4" placeholder="Write your feedback for the student"
                                        class="w-full rounded-lg
                                               border border-emerald-100 dark:border-white/10
                                               bg-white dark:bg-white/5
                                               px-3 py-2 text-sm
                                               text-slate-900 dark:text-slate-100
                                               placeholder:text-slate-400 dark:placeholder:text-slate-500
                                               focus:outline-none
                                               focus:border-emerald-400 dark:focus:border-emerald-500/60
                                               focus:ring-2 focus:ring-emerald-400/30 dark:focus:ring-emerald-500/30
                                               resize-y transition-all"></textarea>
                            </div>
                          </div>
                          <div class="mt-5 flex items-center gap-2.5 flex-wrap">
                            <% if (hasExpectedPortion) { %>
                            <button class="ai-analyze-btn" type="button" data-ai-analyze>
                              <span class="ai-analyze-btn__icon" aria-hidden="true">
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                  <path d="M12 3.5l1.6 4.3a3 3 0 001.8 1.8l4.3 1.6-4.3 1.6a3 3 0 00-1.8 1.8L12 18.9l-1.6-4.3a3 3 0 00-1.8-1.8L4.3 11.2l4.3-1.6a3 3 0 001.8-1.8L12 3.5z" fill="currentColor"/>
                                  <circle cx="19" cy="5" r="1.4" fill="currentColor" opacity="0.7"/>
                                  <circle cx="5.5" cy="18" r="1" fill="currentColor" opacity="0.55"/>
                                </svg>
                              </span>
                              <span class="ai-analyze-btn__label">Analyze with eTasmi AI</span>
                              <span class="ai-analyze-btn__spinner" aria-hidden="true"></span>
                            </button>
                            <% } else { %>
                            <button class="ai-analyze-btn ai-analyze-btn--disabled" type="button" disabled title="Session has no expected Quran portion defined">
                              <span class="ai-analyze-btn__icon" aria-hidden="true">
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                  <path d="M12 3.5l1.6 4.3a3 3 0 001.8 1.8l4.3 1.6-4.3 1.6a3 3 0 00-1.8 1.8L12 18.9l-1.6-4.3a3 3 0 00-1.8-1.8L4.3 11.2l4.3-1.6a3 3 0 001.8-1.8L12 3.5z" fill="currentColor"/>
                                </svg>
                              </span>
                              <span class="ai-analyze-btn__label">Analyze with eTasmi AI</span>
                            </button>
                            <% } %>
                            <%-- Save button — primary emerald with mint glow shadow. --%>
                            <button type="submit" data-ai-save
                                    class="inline-flex items-center gap-1.5 rounded-lg
                                           bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800
                                           dark:bg-emerald-500 dark:hover:bg-emerald-400 dark:active:bg-emerald-600 dark:text-emerald-950
                                           text-white text-sm font-semibold px-4 py-2.5
                                           shadow-mint
                                           transition-all
                                           focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:ring-offset-2 dark:focus-visible:ring-offset-[color:var(--theme-surface)]">
                              <svg class="h-4 w-4" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/></svg>
                              Save Evaluation
                            </button>
                          </div>
                        </section>
                      </form>
                      <% } %>
                    </article>
                    <% } %>

                  </div>
                </div>
              </div>
            </aside>

            <%-- ============================================================
                 SESSION DATA (consumed by client-side roster renderer)
                 ============================================================ --%>
            <%
              StringBuilder sgJson = new StringBuilder("{");
              boolean firstGroup = true;
              for (Map<String, Object> group : sessionGroups) {
                Long gSessionId = (Long) group.get("sessionId");
                if (gSessionId == null) continue;
                if (!firstGroup) sgJson.append(",");
                firstGroup = false;

                java.time.Instant reviewedAtInstant = (java.time.Instant) group.get("evaluationReviewedAt");
                int gSubmitted = group.get("submittedCount") == null ? 0 : ((Number) group.get("submittedCount")).intValue();
                int gPending   = group.get("pendingCount")   == null ? 0 : ((Number) group.get("pendingCount")).intValue();
                int gEvaluated = group.get("evaluatedCount") == null ? 0 : ((Number) group.get("evaluatedCount")).intValue();
                int gTotal     = group.get("totalCount")     == null ? (gSubmitted + gPending)
                                                                     : ((Number) group.get("totalCount")).intValue();

                sgJson.append("\"").append(gSessionId).append("\":{");
                sgJson.append("\"title\":").append(jsonStr(group.get("sessionTitle") == null ? "Session" : group.get("sessionTitle"))).append(",");
                sgJson.append("\"date\":").append(jsonStr(group.get("sessionDate") == null ? "" : group.get("sessionDate"))).append(",");
                sgJson.append("\"time\":").append(jsonStr(group.get("sessionTime") == null ? "" : group.get("sessionTime"))).append(",");
                sgJson.append("\"reviewed\":").append(reviewedAtInstant != null).append(",");
                sgJson.append("\"reviewedAt\":").append(jsonStr(reviewedAtInstant == null ? "" : reviewedAtInstant.toString())).append(",");
                sgJson.append("\"submittedCount\":").append(gSubmitted).append(",");
                sgJson.append("\"pendingCount\":").append(gPending).append(",");
                sgJson.append("\"evaluatedCount\":").append(gEvaluated).append(",");
                sgJson.append("\"totalCount\":").append(gTotal).append(",");

                sgJson.append("\"submitted\":[");
                List<Map<String, Object>> sRows = (List<Map<String, Object>>) group.get("submittedRows");
                if (sRows != null) {
                  boolean firstSub = true;
                  for (Map<String, Object> sRow : sRows) {
                    Recitation sRec = (Recitation) sRow.get("recitation");
                    if (sRec == null) continue;
                    if (!firstSub) sgJson.append(",");
                    firstSub = false;
                    Evaluation sEval = (Evaluation) sRow.get("evaluation");
                    boolean evaluated = sEval != null;
                    sgJson.append("{");
                    sgJson.append("\"name\":").append(jsonStr(sRow.get("studentName") == null ? "Student" : sRow.get("studentName"))).append(",");
                    sgJson.append("\"id\":").append(jsonStr(sRow.get("studentIdentifier") == null ? "-" : sRow.get("studentIdentifier"))).append(",");
                    sgJson.append("\"recitationId\":").append(sRec.getRecitationId()).append(",");
                    sgJson.append("\"submittedAt\":").append(jsonStr(sRec.getSubmissionDate() == null ? "" : String.valueOf(sRec.getSubmissionDate()))).append(",");
                    sgJson.append("\"evaluated\":").append(evaluated).append(",");
                    if (evaluated) {
                      sgJson.append("\"score\":").append(sEval.getScore()).append(",");
                    }
                    sgJson.append("\"photoUrl\":").append(jsonStr(sRow.get("studentPhotoUrl"))).append(",");
                    sgJson.append("\"initials\":").append(jsonStr(sRow.get("studentInitials") == null ? "ST" : sRow.get("studentInitials")));
                    sgJson.append("}");
                  }
                }
                sgJson.append("],\"pending\":[");
                List<Map<String, Object>> pStudents = (List<Map<String, Object>>) group.get("pendingStudents");
                if (pStudents != null) {
                  boolean firstPen = true;
                  for (Map<String, Object> pst : pStudents) {
                    if (!firstPen) sgJson.append(",");
                    firstPen = false;
                    sgJson.append("{");
                    sgJson.append("\"name\":").append(jsonStr(pst.get("name") == null ? "Student" : pst.get("name"))).append(",");
                    sgJson.append("\"id\":").append(jsonStr(pst.get("identifier") == null ? "-" : pst.get("identifier"))).append(",");
                    sgJson.append("\"photoUrl\":").append(jsonStr(pst.get("photoUrl"))).append(",");
                    sgJson.append("\"initials\":").append(jsonStr(pst.get("initials") == null ? "ST" : pst.get("initials")));
                    sgJson.append("}");
                  }
                }
                sgJson.append("]}");
              }
              sgJson.append("}");
            %>
            <script type="application/json" id="iup-eval-session-data"><%= sgJson.toString() %></script>

            <% } %>

          </div>
          <%@ include file="/jsp/common/app_footer.jspf" %>
        </main>
      </div>
    </div>
  </div>
</div>

<%-- ============================================================
     CLIENT-SIDE BEHAVIOR
     - Session card click -> renders the roster table for that session
     - Roster row "Evaluate" click -> opens the side drawer
     - Filter tabs + search refine the visible rows
     - AI analyze / Save buttons keep the original POST contract
     ============================================================ --%>
<script>
  (function () {
    var root = document.getElementById('eval-redesign');
    if (!root) return;

    var sessionsView   = root.querySelector('[data-eval-view="sessions"]');
    var rosterView     = root.querySelector('[data-eval-view="roster"]');
    var drawerEl       = root.querySelector('[data-eval-drawer]');
    var dataNode       = root.querySelector('#iup-eval-session-data');

    if (!sessionsView || !rosterView || !drawerEl || !dataNode) return;

    var titleEl            = rosterView.querySelector('[data-roster-title]');
    var metaEl             = rosterView.querySelector('[data-roster-meta]');
    var totalEl            = rosterView.querySelector('[data-roster-total-count]');
    var submittedEl        = rosterView.querySelector('[data-roster-submitted-count]');
    var pendingEl          = rosterView.querySelector('[data-roster-pending-count]');
    var tbody              = rosterView.querySelector('[data-roster-tbody]');
    var emptyEl            = rosterView.querySelector('[data-roster-empty]');
    var backBtn            = rosterView.querySelector('[data-back-to-sessions]');
    var filterButtons      = rosterView.querySelectorAll('[data-roster-filter]');
    var searchInput        = rosterView.querySelector('[data-roster-search]');

    var sessionData = {};
    try { sessionData = JSON.parse(dataNode.textContent || '{}'); } catch (e) { sessionData = {}; }

    var currentFilter = 'all';
    var currentSearch = '';
    var currentSessionId = null;

    function escHtml(s) {
      return String(s == null ? '' : s)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    /* Emerald-tinted avatar (light + dark variants).
       Falls back from photo to initials chip on image error. */
    function buildAvatar(student) {
      var chip =
        'h-9 w-9 rounded-full ' +
        'bg-emerald-100 text-emerald-700 ring-1 ring-emerald-200 ' +
        'dark:bg-emerald-500/15 dark:text-emerald-200 dark:ring-emerald-500/30 ' +
        'grid place-items-center text-[11px] font-bold';
      if (student.photoUrl) {
        return '<img src="' + escHtml(student.photoUrl) + '" alt="" class="h-9 w-9 rounded-full object-cover ring-1 ring-emerald-200 dark:ring-emerald-500/30" loading="lazy" '
             + 'onerror="this.style.display=\'none\';this.nextElementSibling.style.display=\'grid\';">'
             + '<span class="' + chip + '" style="display:none">' + escHtml(student.initials || 'ST') + '</span>';
      }
      return '<span class="' + chip + '">' + escHtml(student.initials || 'ST') + '</span>';
    }

    function renderRow(s, status) {
      var isSubmitted = status === 'submitted';
      var statusBadge;
      if (isSubmitted && s.evaluated) {
        /* Evaluated — solid forest emerald, no animation. */
        statusBadge =
          '<span class="inline-flex items-center gap-1.5 rounded-full ' +
            'bg-emerald-100 border border-emerald-300 text-emerald-800 ' +
            'dark:bg-emerald-500/15 dark:border-emerald-500/40 dark:text-emerald-200 ' +
            'px-2.5 py-1 text-[11px] font-semibold">' +
            '<svg class="h-3 w-3" viewBox="0 0 20 20" fill="currentColor"><path fill-rule="evenodd" d="M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z" clip-rule="evenodd"/></svg> Evaluated' +
          '</span>';
      } else if (isSubmitted) {
        /* Submitted — "Mint Glow" pulsing dot in a low-opacity emerald pill. */
        statusBadge =
          '<span class="inline-flex items-center gap-2 rounded-full ' +
            'bg-emerald-500/10 border border-emerald-500/30 text-emerald-700 ' +
            'dark:text-emerald-300 dark:border-emerald-500/40 ' +
            'px-2.5 py-1 text-[11px] font-semibold">' +
            '<span class="relative flex h-2 w-2">' +
              '<span class="absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75 animate-mint-ping"></span>' +
              '<span class="relative inline-flex h-2 w-2 rounded-full bg-emerald-500"></span>' +
            '</span> Submitted' +
          '</span>';
      } else {
        /* Awaiting Submission — sophisticated muted neutral, no green/no warning color. */
        statusBadge =
          '<span class="inline-flex items-center gap-1.5 rounded-full ' +
            'bg-slate-100 border border-slate-200 text-slate-600 ' +
            'dark:bg-white/5 dark:border-white/10 dark:text-slate-300 ' +
            'px-2.5 py-1 text-[11px] font-semibold">' +
            '<span class="h-1.5 w-1.5 rounded-full bg-slate-400 dark:bg-slate-500"></span> Awaiting Submission' +
          '</span>';
      }

      var actionCell;
      if (isSubmitted) {
        if (s.evaluated) {
          /* View — outline ghost button (sleek), still emerald hover state. */
          actionCell =
            '<button type="button" data-row-evaluate ' +
              'class="inline-flex items-center gap-1.5 rounded-lg ' +
                'bg-white hover:bg-emerald-50 border border-emerald-200 hover:border-emerald-400 text-emerald-700 ' +
                'dark:bg-white/5 dark:hover:bg-emerald-500/10 dark:border-white/10 dark:hover:border-emerald-500/40 dark:text-emerald-200 ' +
                'text-xs font-semibold px-3 py-1.5 shadow-sm transition-all ' +
                'focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:ring-offset-2 dark:focus-visible:ring-offset-[color:var(--theme-surface)]">' +
              /* Document icon for "view existing review" */
              '<svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" stroke-linejoin="round"/><path d="M14 2v6h6M9 13h6M9 17h4" stroke-linecap="round" stroke-linejoin="round"/></svg>' +
              'View' +
            '</button>';
        } else {
          /* Evaluate — solid emerald with mint shadow, play icon for action. */
          actionCell =
            '<button type="button" data-row-evaluate ' +
              'class="inline-flex items-center gap-1.5 rounded-lg ' +
                'bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800 ' +
                'dark:bg-emerald-500 dark:hover:bg-emerald-400 dark:active:bg-emerald-600 dark:text-emerald-950 ' +
                'text-white text-xs font-semibold px-3 py-1.5 ' +
                'shadow-mint hover:shadow-lg hover:-translate-y-0.5 transition-all ' +
                'focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 focus-visible:ring-offset-2 dark:focus-visible:ring-offset-[color:var(--theme-surface)]">' +
              /* Play icon — universal "review the recording" affordance */
              '<svg class="h-3.5 w-3.5" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>' +
              'Evaluate' +
            '</button>';
        }
      } else {
        actionCell = '<span class="text-xs text-slate-400 dark:text-slate-500 italic">No submission yet</span>';
      }

      var dateText = isSubmitted && s.submittedAt
        ? '<span class="text-slate-700 dark:text-slate-200">' + escHtml(s.submittedAt) + '</span>'
        : '<span class="text-slate-400 dark:text-slate-500">—</span>';

      var rowAttrs =
        'data-row="1" ' +
        'data-status="' + status + '" ' +
        (isSubmitted ? 'data-recitation-id="' + s.recitationId + '" ' : '') +
        'data-name="' + escHtml((s.name || '') + ' ' + (s.id || '')).toLowerCase() + '"';

      /* Soft mint hover tint requested by the brief (bg-emerald-500/5).
         Pending rows get a softer hover so they stay visually quieter. */
      var rowClass = 'transition-colors ' +
        (isSubmitted
          ? 'cursor-pointer hover:bg-emerald-500/[0.06] dark:hover:bg-emerald-500/[0.07]'
          : 'hover:bg-slate-50/70 dark:hover:bg-white/[0.03]') +
        ' bg-transparent dark:bg-transparent';

      return (
        '<tr class="' + rowClass + '" ' + rowAttrs + '>' +
          '<td class="px-6 py-4 align-middle">' +
            '<div class="flex items-center gap-3">' +
              buildAvatar(s) +
              '<div class="min-w-0">' +
                '<div class="font-semibold text-slate-900 dark:text-white truncate">' + escHtml(s.name || 'Student') + '</div>' +
                '<div class="sm:hidden text-xs text-slate-500 dark:text-slate-400 mt-0.5">' + escHtml(s.id || '-') + '</div>' +
              '</div>' +
            '</div>' +
          '</td>' +
          '<td class="px-6 py-4 align-middle hidden sm:table-cell">' +
            '<span class="inline-flex items-center rounded-md ' +
              'bg-emerald-50/70 dark:bg-white/5 ' +
              'px-2 py-0.5 text-[11px] font-mono font-medium ' +
              'text-emerald-700 dark:text-slate-300">' + escHtml(s.id || '-') + '</span>' +
          '</td>' +
          '<td class="px-6 py-4 align-middle">' + statusBadge + '</td>' +
          '<td class="px-6 py-4 align-middle hidden md:table-cell text-[13px]">' + dateText + '</td>' +
          '<td class="px-6 py-4 align-middle text-right">' + actionCell + '</td>' +
        '</tr>'
      );
    }

    function renderRoster() {
      if (!currentSessionId) return;
      var info = sessionData[String(currentSessionId)];
      if (!info) return;

      var submitted = info.submitted || [];
      var pending = info.pending || [];
      var rows = [];
      if (currentFilter === 'all' || currentFilter === 'submitted') {
        submitted.forEach(function (s) { rows.push(renderRow(s, 'submitted')); });
      }
      if (currentFilter === 'all' || currentFilter === 'pending') {
        pending.forEach(function (s) { rows.push(renderRow(s, 'pending')); });
      }

      tbody.innerHTML = rows.join('');

      if (currentSearch) {
        var q = currentSearch.trim().toLowerCase();
        var visible = 0;
        tbody.querySelectorAll('tr[data-row]').forEach(function (tr) {
          var hay = tr.getAttribute('data-name') || '';
          var match = hay.indexOf(q) !== -1;
          tr.hidden = !match;
          if (match) visible++;
        });
        emptyEl.hidden = visible !== 0;
      } else {
        emptyEl.hidden = rows.length !== 0;
      }
    }

    function openSession(sessionId) {
      var info = sessionData[String(sessionId)];
      if (!info) return;
      currentSessionId = sessionId;

      sessionsView.hidden = true;
      rosterView.hidden = false;
      // Communicate the open session id to the workflow script (modal +
      // Mark/Reopen button) via a data-attribute MutationObserver hook.
      rosterView.setAttribute('data-roster-session-id', String(sessionId));
      rosterView.classList.remove('eval-card-in');
      void rosterView.offsetWidth;
      rosterView.classList.add('eval-card-in');

      if (titleEl) titleEl.textContent = info.title || 'Session';
      if (metaEl) {
        var pieces = [];
        if (info.date) pieces.push(info.date);
        if (info.time) pieces.push(info.time);
        metaEl.textContent = pieces.length ? pieces.join(' \u2022 ') : '—';
      }

      var subCount = (info.submitted || []).length;
      var penCount = (info.pending || []).length;
      if (totalEl) totalEl.textContent = String(subCount + penCount);
      if (submittedEl) submittedEl.textContent = String(subCount);
      if (pendingEl) pendingEl.textContent = String(penCount);

      renderRoster();
      try { window.scrollTo({ top: 0, behavior: 'smooth' }); } catch (e) { window.scrollTo(0, 0); }
    }

    function showSessionList() {
      currentSessionId = null;
      rosterView.hidden = true;
      rosterView.removeAttribute('data-roster-session-id');
      sessionsView.hidden = false;
      sessionsView.classList.remove('eval-card-in');
      void sessionsView.offsetWidth;
      sessionsView.classList.add('eval-card-in');
    }

    /* ====== Drawer ====== */
    var drawerTitle  = drawerEl.querySelector('[data-drawer-title]');
    var drawerEyebrow = drawerEl.querySelector('[data-drawer-eyebrow]');
    var drawerAvatar = drawerEl.querySelector('[data-drawer-avatar]');
    var drawerScrim  = drawerEl.querySelector('[data-drawer-scrim]');
    var drawerClose  = drawerEl.querySelector('[data-drawer-close]');

    function findStudentByRecitationId(recitationId) {
      if (!currentSessionId) return null;
      var info = sessionData[String(currentSessionId)];
      if (!info) return null;
      var arr = info.submitted || [];
      for (var i = 0; i < arr.length; i++) {
        if (String(arr[i].recitationId) === String(recitationId)) return arr[i];
      }
      return null;
    }

    function openDrawer(recitationId) {
      var details = drawerEl.querySelectorAll('[data-recitation-detail]');
      details.forEach(function (d) { d.hidden = String(d.getAttribute('data-recitation-detail')) !== String(recitationId); });

      var student = findStudentByRecitationId(recitationId);
      if (drawerTitle) drawerTitle.textContent = student ? student.name : 'Submission';
      if (drawerEyebrow) {
        drawerEyebrow.textContent = student
          ? (student.evaluated ? 'Evaluation • Completed' : 'Evaluation • Pending review')
          : 'Evaluation';
      }
      if (drawerAvatar) {
        drawerAvatar.innerHTML = '';
        if (student && student.photoUrl) {
          var img = document.createElement('img');
          img.src = student.photoUrl;
          img.alt = '';
          img.className = 'h-10 w-10 rounded-full object-cover';
          img.onerror = function () {
            drawerAvatar.innerHTML = '';
            drawerAvatar.textContent = (student && student.initials) || 'ST';
          };
          drawerAvatar.appendChild(img);
        } else {
          drawerAvatar.textContent = (student && student.initials) || 'ST';
        }
      }

      drawerEl.hidden = false;
      drawerEl.setAttribute('data-open', 'true');
      drawerEl.setAttribute('aria-hidden', 'false');
      document.body.style.overflow = 'hidden';
    }

    function closeDrawer() {
      drawerEl.setAttribute('data-open', 'false');
      drawerEl.setAttribute('aria-hidden', 'true');
      drawerEl.hidden = true;
      document.body.style.overflow = '';
    }

    if (drawerScrim) drawerScrim.addEventListener('click', closeDrawer);
    if (drawerClose) drawerClose.addEventListener('click', closeDrawer);
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && drawerEl.getAttribute('data-open') === 'true') closeDrawer();
    });

    /* ====== Wire-ups ====== */
    sessionsView.addEventListener('click', function (e) {
      var card = e.target.closest('[data-iup-eval-session]');
      if (!card) return;
      var id = card.getAttribute('data-iup-eval-session');
      if (id) openSession(id);
    });

    if (backBtn) {
      backBtn.addEventListener('click', function (e) { e.preventDefault(); showSessionList(); });
    }

    tbody && tbody.addEventListener('click', function (e) {
      var tr = e.target.closest('tr[data-row]');
      if (!tr) return;
      if (tr.getAttribute('data-status') !== 'submitted') return;
      var rid = tr.getAttribute('data-recitation-id');
      if (rid) openDrawer(rid);
    });

    /* Filter pill — keep classes in lock-step with the JSP markup
       (so server-side initial state and client-side toggles agree). */
    var ACTIVE_CLASSES   = ['text-emerald-800', 'dark:text-emerald-200', 'bg-white', 'dark:bg-emerald-500/15', 'shadow-sm'];
    var INACTIVE_CLASSES = ['text-slate-500', 'dark:text-slate-400', 'hover:text-emerald-700', 'dark:hover:text-emerald-300'];

    filterButtons.forEach(function (btn) {
      btn.addEventListener('click', function () {
        currentFilter = btn.getAttribute('data-roster-filter') || 'all';
        filterButtons.forEach(function (b) {
          var active = b === btn;
          b.setAttribute('aria-selected', active ? 'true' : 'false');
          if (active) {
            ACTIVE_CLASSES.forEach(function (c) { b.classList.add(c); });
            INACTIVE_CLASSES.forEach(function (c) { b.classList.remove(c); });
          } else {
            ACTIVE_CLASSES.forEach(function (c) { b.classList.remove(c); });
            INACTIVE_CLASSES.forEach(function (c) { b.classList.add(c); });
          }
        });
        renderRoster();
      });
    });

    if (searchInput) {
      var t;
      searchInput.addEventListener('input', function () {
        clearTimeout(t);
        t = setTimeout(function () {
          currentSearch = searchInput.value || '';
          renderRoster();
        }, 80);
      });
    }

    /* ====== AI analyze / Save buttons (preserve original contract) ====== */
    document.addEventListener('click', function (event) {
      var analyzeBtn = event.target.closest('[data-ai-analyze]');
      if (analyzeBtn && !analyzeBtn.disabled) {
        event.preventDefault();
        var form = analyzeBtn.form || analyzeBtn.closest('form');
        if (!form) return;
        var actionInput = form.querySelector('[data-action-input]');
        if (actionInput) actionInput.value = 'analyze';
        var label = analyzeBtn.querySelector('.ai-analyze-btn__label');
        if (label) label.textContent = 'Analyzing with eTasmi AI...';
        analyzeBtn.classList.add('is-loading');
        analyzeBtn.disabled = true;
        form.submit();
        return;
      }
      var saveBtn = event.target.closest('[data-ai-save]');
      if (saveBtn) {
        var saveForm = saveBtn.form || saveBtn.closest('form');
        if (!saveForm) return;
        var saveActionInput = saveForm.querySelector('[data-action-input]');
        if (saveActionInput) saveActionInput.value = 'save';
      }
    }, true);

    /* ====== Auto-open after Analyze postback ====== */
    var autoOpenSession = rosterView.getAttribute('data-iup-auto-open');
    if (autoOpenSession) {
      openSession(autoOpenSession);
      var autoOpenRecitation = rosterView.getAttribute('data-iup-auto-open-recitation');
      if (autoOpenRecitation) {
        setTimeout(function () { openDrawer(autoOpenRecitation); }, 80);
      }
    }
  })();
</script>

<%-- ============================================================
     EVALUATION REVIEW WORKFLOW — confirmation modal + form
     Lives outside transformed ancestors so position:fixed is reliable.
     ============================================================ --%>
<div data-eval-confirm
     class="fixed inset-0 z-[70] hidden items-center justify-center"
     aria-hidden="true" role="dialog" aria-modal="true" aria-labelledby="eval-confirm-title">
  <div data-eval-confirm-scrim class="absolute inset-0 bg-slate-950/55 dark:bg-slate-950/70 backdrop-blur-[3px]"></div>
  <div class="relative w-full max-w-md mx-4 rounded-2xl
              bg-white dark:bg-[color:var(--theme-surface)]
              border border-emerald-200/60 dark:border-emerald-500/25
              shadow-2xl shadow-emerald-900/10 dark:shadow-black/60
              p-6">
    <div class="flex items-start gap-3">
      <span aria-hidden="true"
            class="h-10 w-10 rounded-xl bg-emerald-100 dark:bg-emerald-500/15 ring-1 ring-emerald-200 dark:ring-emerald-500/30 grid place-items-center flex-shrink-0">
        <svg class="h-5 w-5 text-emerald-700 dark:text-emerald-200" data-eval-confirm-icon viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M5 13l4 4L19 7" stroke-linecap="round" stroke-linejoin="round"/></svg>
      </span>
      <div class="min-w-0">
        <h3 id="eval-confirm-title" data-eval-confirm-title class="text-base font-bold text-slate-900 dark:text-white">Mark this session as reviewed?</h3>
        <p data-eval-confirm-subtitle class="mt-1 text-sm text-slate-500 dark:text-slate-400">This will move the session into the Reviewed Sessions tab.</p>
      </div>
    </div>

    <%-- Dynamic warning lines populated by JS based on session state. --%>
    <ul data-eval-confirm-warnings class="mt-4 space-y-2 text-sm"></ul>

    <form method="post" action="<%= request.getContextPath() %>/instructor/evaluations" class="mt-6 flex items-center justify-end gap-2.5 flex-wrap">
      <input type="hidden" name="action" value="mark_reviewed" data-eval-confirm-action />
      <input type="hidden" name="sessionId" value="" data-eval-confirm-session-id />
      <button type="button" data-eval-confirm-cancel
              class="inline-flex items-center justify-center rounded-lg border border-slate-200 dark:border-white/10 bg-white dark:bg-white/5 hover:bg-slate-50 dark:hover:bg-white/10 text-slate-700 dark:text-slate-200 text-sm font-semibold px-4 py-2 transition-colors">
        Cancel
      </button>
      <button type="submit" data-eval-confirm-submit
              class="inline-flex items-center gap-1.5 rounded-lg
                     bg-emerald-600 hover:bg-emerald-700 active:bg-emerald-800
                     dark:bg-emerald-500 dark:hover:bg-emerald-400 dark:active:bg-emerald-600 dark:text-emerald-950
                     text-white text-sm font-semibold px-4 py-2 shadow-mint transition-all">
        <span data-eval-confirm-submit-label>Mark as Reviewed</span>
      </button>
    </form>
  </div>
</div>

<script>
  /* ============================================================
     Workflow scripts: tab switching, review action, modal logic
     ============================================================ */
  (function () {
    var root = document.getElementById('eval-redesign');
    if (!root) return;

    /* ---------- Tabs ---------- */
    var tabButtons = root.querySelectorAll('[data-eval-tab]');
    var tabPanels  = root.querySelectorAll('[data-eval-tab-panel]');
    var TAB_ACTIVE = ['text-emerald-800', 'dark:text-emerald-100', 'bg-white', 'dark:bg-emerald-500/15', 'shadow-sm'];
    var TAB_INACTIVE = ['text-slate-500', 'dark:text-slate-400', 'hover:text-emerald-700', 'dark:hover:text-emerald-300'];
    function setActiveTab(name) {
      tabButtons.forEach(function (btn) {
        var on = btn.getAttribute('data-eval-tab') === name;
        btn.setAttribute('aria-selected', on ? 'true' : 'false');
        if (on) {
          TAB_ACTIVE.forEach(function (c) { btn.classList.add(c); });
          TAB_INACTIVE.forEach(function (c) { btn.classList.remove(c); });
        } else {
          TAB_ACTIVE.forEach(function (c) { btn.classList.remove(c); });
          TAB_INACTIVE.forEach(function (c) { btn.classList.add(c); });
        }
      });
      tabPanels.forEach(function (panel) {
        panel.hidden = panel.getAttribute('data-eval-tab-panel') !== name;
      });
      try { sessionStorage.setItem('etasmi.evalActiveTab', name); } catch (e) { /* ignore */ }
    }
    tabButtons.forEach(function (btn) {
      btn.addEventListener('click', function () { setActiveTab(btn.getAttribute('data-eval-tab') || 'active'); });
    });
    /* Restore the last viewed tab on revisit. */
    try {
      var savedTab = sessionStorage.getItem('etasmi.evalActiveTab');
      if (savedTab === 'reviewed') setActiveTab('reviewed');
    } catch (e) { /* ignore */ }

    /* ---------- Session data (shared with the older script via #iup-eval-session-data) ---------- */
    var sessionData = {};
    var dataNode = document.getElementById('iup-eval-session-data');
    try { sessionData = JSON.parse((dataNode && dataNode.textContent) || '{}'); } catch (e) { sessionData = {}; }

    /* ---------- Roster review action button ---------- */
    var rosterView = root.querySelector('[data-eval-view="roster"]');
    if (!rosterView) return;
    var reviewBtn      = rosterView.querySelector('[data-roster-review-action]');
    var reviewLabel    = rosterView.querySelector('[data-roster-review-label]');
    var reviewIcon     = rosterView.querySelector('[data-roster-review-icon]');
    var reviewedBanner = rosterView.querySelector('[data-roster-reviewed-banner]');
    var reviewedWhen   = rosterView.querySelector('[data-roster-reviewed-when]');

    function currentSessionFromUI() {
      var openCard = root.querySelector('[data-eval-view="roster"]');
      var sid = openCard ? openCard.getAttribute('data-roster-session-id') : null;
      return sid && sessionData[sid] ? { id: sid, info: sessionData[sid] } : null;
    }

    /** Adjust the action button + reviewed banner based on the open session. */
    function syncReviewActionForSession(sessionId) {
      if (!sessionId || !sessionData[String(sessionId)]) return;
      var info = sessionData[String(sessionId)];
      var reviewed = !!info.reviewed;
      if (reviewLabel) reviewLabel.textContent = reviewed ? 'Reopen for Review' : 'Mark as Reviewed';
      if (reviewBtn) {
        reviewBtn.setAttribute('data-action', reviewed ? 'reopen' : 'mark_reviewed');
        reviewBtn.setAttribute('aria-label', reviewed ? 'Reopen this session for review' : 'Mark this session as reviewed');
      }
      if (reviewIcon) {
        reviewIcon.innerHTML = reviewed
          ? '<path d="M3 12a9 9 0 1 0 3-6.7L3 8" stroke-linecap="round" stroke-linejoin="round"/><path d="M3 4v4h4" stroke-linecap="round" stroke-linejoin="round"/>'
          : '<path d="M5 13l4 4L19 7" stroke-linecap="round" stroke-linejoin="round"/>';
      }
      if (reviewedBanner) {
        reviewedBanner.hidden = !reviewed;
      }
      if (reviewedWhen) {
        if (reviewed && info.reviewedAt) {
          var when = formatReviewedWhen(info.reviewedAt);
          reviewedWhen.textContent = when ? (' on ' + when) : '';
        } else {
          reviewedWhen.textContent = '';
        }
      }
    }

    function formatReviewedWhen(iso) {
      if (!iso) return '';
      try {
        var d = new Date(iso);
        if (isNaN(d.getTime())) return '';
        return d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' });
      } catch (e) { return ''; }
    }

    /* Hook into the existing openSession() by observing roster reveals.
       The old script doesn't expose a callback, but it sets data-iup-auto-open
       on the rosterView when it opens. We re-sync whenever the rosterView's
       data-roster-session-id changes via a tiny MutationObserver. */
    var observer = new MutationObserver(function () {
      var sid = rosterView.getAttribute('data-roster-session-id');
      if (sid) syncReviewActionForSession(sid);
    });
    observer.observe(rosterView, { attributes: true, attributeFilter: ['data-roster-session-id'] });

    /* ---------- Confirmation modal ---------- */
    var modal       = document.querySelector('[data-eval-confirm]');
    var modalScrim  = modal ? modal.querySelector('[data-eval-confirm-scrim]') : null;
    var modalCancel = modal ? modal.querySelector('[data-eval-confirm-cancel]') : null;
    var modalTitle  = modal ? modal.querySelector('[data-eval-confirm-title]') : null;
    var modalSub    = modal ? modal.querySelector('[data-eval-confirm-subtitle]') : null;
    var modalWarns  = modal ? modal.querySelector('[data-eval-confirm-warnings]') : null;
    var modalAction = modal ? modal.querySelector('[data-eval-confirm-action]') : null;
    var modalSid    = modal ? modal.querySelector('[data-eval-confirm-session-id]') : null;
    var modalLabel  = modal ? modal.querySelector('[data-eval-confirm-submit-label]') : null;
    var modalIcon   = modal ? modal.querySelector('[data-eval-confirm-icon]') : null;

    function openModalFor(sessionId, action) {
      if (!modal) return;
      var info = sessionData[String(sessionId)];
      if (!info) return;

      var reopening = action === 'reopen';
      if (modalTitle) modalTitle.textContent = reopening
        ? 'Reopen this session for review?'
        : 'Mark this session as reviewed?';
      if (modalSub) modalSub.textContent = reopening
        ? 'The session will move back to Active Evaluations and become editable.'
        : 'The session will move into the Reviewed Sessions tab. You can reopen it any time.';
      if (modalIcon) {
        modalIcon.innerHTML = reopening
          ? '<path d="M3 12a9 9 0 1 0 3-6.7L3 8" stroke-linecap="round" stroke-linejoin="round"/><path d="M3 4v4h4" stroke-linecap="round" stroke-linejoin="round"/>'
          : '<path d="M5 13l4 4L19 7" stroke-linecap="round" stroke-linejoin="round"/>';
      }
      if (modalLabel) modalLabel.textContent = reopening ? 'Reopen for Review' : 'Mark as Reviewed';
      if (modalAction) modalAction.value = reopening ? 'reopen' : 'mark_reviewed';
      if (modalSid) modalSid.value = String(sessionId);

      if (modalWarns) {
        modalWarns.innerHTML = '';
        if (!reopening) {
          var unevaluated = Math.max(0, (info.submittedCount || 0) - (info.evaluatedCount || 0));
          var awaiting   = info.pendingCount || 0;
          if (unevaluated > 0) {
            modalWarns.appendChild(buildWarn(
              unevaluated + ' submission' + (unevaluated === 1 ? '' : 's') + ' have not been evaluated yet. They will remain unscored.',
              'amber'
            ));
          }
          if (awaiting > 0) {
            modalWarns.appendChild(buildWarn(
              awaiting + ' student' + (awaiting === 1 ? '' : 's') + ' have not submitted a recitation. They will be recorded as not submitted.',
              'amber'
            ));
          }
          if (!unevaluated && !awaiting) {
            modalWarns.appendChild(buildWarn(
              'All students have been reviewed for this session.',
              'emerald'
            ));
          }
        }
      }

      modal.classList.remove('hidden');
      modal.classList.add('flex');
      modal.setAttribute('aria-hidden', 'false');
      document.body.style.overflow = 'hidden';
    }

    function buildWarn(text, tone) {
      var li = document.createElement('li');
      var bgClass, dotClass, textClass;
      if (tone === 'emerald') {
        bgClass = 'bg-emerald-50 dark:bg-emerald-500/10 border-emerald-200 dark:border-emerald-500/30';
        dotClass = 'bg-emerald-500';
        textClass = 'text-emerald-800 dark:text-emerald-200';
      } else {
        bgClass = 'bg-amber-50 dark:bg-amber-500/10 border-amber-200 dark:border-amber-500/30';
        dotClass = 'bg-amber-500';
        textClass = 'text-amber-800 dark:text-amber-200';
      }
      li.className = 'flex items-start gap-2 rounded-lg border px-3 py-2 ' + bgClass;
      li.innerHTML =
        '<span class="mt-1.5 h-1.5 w-1.5 rounded-full flex-shrink-0 ' + dotClass + '"></span>' +
        '<span class="text-[13px] ' + textClass + '">' + escHtml(text) + '</span>';
      return li;
    }

    function closeModal() {
      if (!modal) return;
      modal.classList.add('hidden');
      modal.classList.remove('flex');
      modal.setAttribute('aria-hidden', 'true');
      document.body.style.overflow = '';
    }

    function escHtml(s) {
      return String(s == null ? '' : s)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    if (modalScrim) modalScrim.addEventListener('click', closeModal);
    if (modalCancel) modalCancel.addEventListener('click', closeModal);
    document.addEventListener('keydown', function (e) {
      if (e.key === 'Escape' && modal && !modal.classList.contains('hidden')) closeModal();
    });

    /* Wire the roster's primary action -> modal. */
    if (reviewBtn) {
      reviewBtn.addEventListener('click', function () {
        var sid = rosterView.getAttribute('data-roster-session-id');
        if (!sid) return;
        var action = reviewBtn.getAttribute('data-action') || 'mark_reviewed';
        openModalFor(sid, action);
      });
    }

    /* If we landed here via ?session=ID (after mark/reopen redirect), open it. */
    try {
      var qs = new URLSearchParams(window.location.search);
      var openId = qs.get('session');
      if (openId && sessionData[openId]) {
        var info = sessionData[openId];
        if (info.reviewed) setActiveTab('reviewed');
        var card = root.querySelector('[data-iup-eval-session="' + openId + '"]');
        if (card) {
          setTimeout(function () { card.click(); }, 30);
        }
      }
    } catch (e) { /* ignore */ }
  })();
</script>

<script>
  /* ============================================================
     Session table controller — search, status filter, sort.
     Operates independently on every [data-table-scope] block
     (Active + Reviewed panels). Rows keep their data-iup-eval-session
     hook, so the existing roster-open delegation is untouched.
     ============================================================ */
  (function () {
    var root = document.getElementById('eval-redesign');
    if (!root) return;

    root.querySelectorAll('[data-table-scope]').forEach(function (scope) {
      var body      = scope.querySelector('[data-table-body]');
      if (!body) return;
      var searchEl  = scope.querySelector('[data-table-search]');
      var statusEl  = scope.querySelector('[data-table-status]');
      var sortEl    = scope.querySelector('[data-table-sort]');
      var emptyRow  = scope.querySelector('[data-table-empty]');

      function apply() {
        var q  = (searchEl && searchEl.value || '').trim().toLowerCase();
        var st = (statusEl && statusEl.value) || 'all';
        var dir = (sortEl && sortEl.value) || 'newest';

        var rows = Array.prototype.slice.call(body.querySelectorAll('tr[data-iup-eval-session]'));
        var visible = 0;

        rows.forEach(function (tr) {
          var matchText   = !q || (tr.getAttribute('data-search') || '').indexOf(q) !== -1;
          var matchStatus = st === 'all' || (tr.getAttribute('data-status') === st);
          var show = matchText && matchStatus;
          tr.hidden = !show;
          if (show) visible++;
        });

        rows.sort(function (a, b) {
          var av = a.getAttribute('data-sort') || '';
          var bv = b.getAttribute('data-sort') || '';
          if (av === bv) return 0;
          if (dir === 'oldest') return av < bv ? -1 : 1;
          return av < bv ? 1 : -1;
        });
        rows.forEach(function (tr) { body.appendChild(tr); });
        if (emptyRow) {
          body.appendChild(emptyRow);
          emptyRow.hidden = visible !== 0;
        }
      }

      if (searchEl) {
        searchEl.addEventListener('input', apply);
        searchEl.addEventListener('keydown', function (e) {
          if (e.key === 'Enter') { e.preventDefault(); apply(); }
        });
      }
      if (statusEl) statusEl.addEventListener('change', apply);
      if (sortEl)   sortEl.addEventListener('change', apply);

      var triggerEl = scope.querySelector('[data-search-trigger]');
      if (triggerEl) triggerEl.addEventListener('click', apply);

      /* Keyboard activation for the role="button" rows. */
      body.addEventListener('keydown', function (e) {
        if (e.key !== 'Enter' && e.key !== ' ' && e.key !== 'Spacebar') return;
        var tr = e.target.closest('tr[data-iup-eval-session]');
        if (!tr) return;
        e.preventDefault();
        tr.click();
      });

      apply();
    });
  })();
</script>
</body>
</html>
