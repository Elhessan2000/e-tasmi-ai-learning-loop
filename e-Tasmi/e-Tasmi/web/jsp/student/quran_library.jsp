<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<% String ctx = request.getContextPath(); %>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.studentQuranLibraryTitle">Quran Library — eTasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="etasmi-context-path" content="<%= ctx %>">
    <%@ include file="/jsp/common/student_ui_head.jspf" %>
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Amiri+Quran&family=Noto+Naskh+Arabic:wght@400;600&family=Scheherazade+New:wght@400;500;700&display=swap">
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Poppins:wght@500;600;700&display=swap">
    <link rel="stylesheet" href="<%= ctx %>/css/quran-library.css?v=20260624-qlx-green-head">
    <link rel="stylesheet" href="<%= ctx %>/css/quran-library-dark.css?v=20260624-qlx-dark3">
    <script defer src="<%= ctx %>/assets/js/app.js"></script>
    <script defer src="<%= ctx %>/assets/js/reciter-portrait-fallbacks.js?v=20260520-qlx-header"></script>
    <script defer src="<%= ctx %>/assets/js/quran-library.js?v=20260612-qlx-i18n"></script>
</head>
<body class="student-premium-page student-package-page student-module-page quran-library-page">
<div class="app-shell">
    <%@ include file="/jsp/common/student_header.jspf" %>
    <div class="app-main">
        <div class="container sd-container student-workspace-shell">
            <div class="student-shell-layout">
                <% request.setAttribute("activeMenu", "quran-library"); %>
                <%@ include file="/jsp/student/student_sidebar.jspf" %>

                <main class="student-shell-content qlx-main" role="main" data-i18n="student.quranLibrary.mainAria" data-i18n-attr="aria-label" aria-label="Quran Library">
                    <div class="student-workspace-view student-workspace-view--quran">
                    <%@ include file="/jsp/common/student_breadcrumb.jspf" %>
                    <%--
                        Single root mounted by quran-library.js. Chapters load once; a fixed left sidebar and
                        [.qlx-content] main pane host Home, Reader (#/surah/:n), Reciters, My Quran. Routes
                        fill only [.qlx-content]; audio dock persists. Endpoints are data-* attributes —
                        JS does not hard-code context paths; Quran Foundation secrets stay server-side.
                    --%>
                    <div class="qlx-root"
                         data-qlx-root
                         data-ctx="<%= ctx %>"
                         data-api-chapters="<%= ctx %>/student/api/quran-library/chapters"
                         data-api-verses="<%= ctx %>/student/api/quran-library/verses/by-chapter"
                         data-api-verse-by-key="<%= ctx %>/student/api/quran-library/verses/by-key"
                         data-api-search="<%= ctx %>/student/api/quran-library/search"
                         data-api-reciters="<%= ctx %>/student/api/quran-library/audio/chapter-reciters"
                         data-api-audio="<%= ctx %>/student/api/quran-library/audio/chapter-file"
                         data-api-translations="<%= ctx %>/student/api/quran-library/resources/translations"
                         data-api-tafsirs="<%= ctx %>/student/api/quran-library/resources/tafsirs"
                         data-api-translate-ai="<%= ctx %>/student/api/quran-library/translate-ai"
                         data-default-translation="131"
                         data-default-language="en">

                        <%--
                            Single global header (.qlx-topbar): title/subtitle update per tab
                            (Library / Reciters / My Quran). Sub-views must not render a second
                            page-level banner — only content tools (search, filters, grids) below.
                        --%>
                        <header class="qlx-topbar" data-qlx-topbar></header>
                        <div class="qlx-stage" data-qlx-stage aria-live="polite"></div>
                        <div class="qlx-toast" data-qlx-toast role="status" aria-live="polite"></div>

                        <%--
                            Audio dock is global: it persists when navigating between views so playback
                            continues smoothly and verse-timings keep highlighting the current ayah.
                        --%>
                        <div class="qlx-dock" data-qlx-dock hidden data-i18n="student.quranLibrary.playerAria" data-i18n-attr="aria-label" aria-label="Recitation player"></div>
                    </div>
                    </div>
                    <%-- Footer intentionally omitted on Quran Library pages
                         (Library / Reciters / Reciter profile / My Quran / Reader)
                         to keep the workspace immersive and avoid layout artifacts
                         around the fixed audio dock. --%>
                </main>
            </div>
        </div>
    </div>
</div>
</body>
</html>
