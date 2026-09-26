<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.time.Instant" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<%@ page import="model.entity.Notification" %>
<%!
    private static String notifEsc(Object val) {
        if (val == null) return "";
        return String.valueOf(val)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String notifAgo(Instant instant) {
        if (instant == null) return "-";
        long diffMs = Instant.now().toEpochMilli() - instant.toEpochMilli();
        if (diffMs < 60000L) return "Just now";
        long minutes = diffMs / 60000L;
        if (minutes < 60L) return minutes == 1L ? "1 minute ago" : minutes + " minutes ago";
        long hours = minutes / 60L;
        if (hours < 24L) return hours == 1L ? "1 hour ago" : hours + " hours ago";
        long days = hours / 24L;
        return days == 1L ? "1 day ago" : days + " days ago";
    }

    private static String notifTitle(String message) {
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (m.contains("evaluat")) return "New Evaluation Available";
        if (m.contains("reminder") || m.contains("starts in") || m.contains("24 hour")) return "Session Reminder";
        if (m.contains("complete") || m.contains("marked as complete")) return "Session Completed";
        if (m.contains("payment") || m.contains("receipt") || m.contains("qr")) return "Payment Update";
        if (m.contains("session")) return "Session Update";
        return "Notification";
    }

    private static String notifKind(String message) {
        String t = notifTitle(message);
        if ("Session Completed".equals(t)) return "star";
        if ("Payment Update".equals(t)) return "clock";
        return "bell";
    }
%>
<%
    Object roleObj = session == null ? null : session.getAttribute("role");
    String role = roleObj == null ? "" : String.valueOf(roleObj);
    boolean adminRole = "ADMIN".equalsIgnoreCase(role);
    boolean instructorRole = "INSTRUCTOR".equalsIgnoreCase(role);
    request.setAttribute("activeMenu", "notifications");
    List<Notification> notifications = (List<Notification>) request.getAttribute("notifications");
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.notificationsTitle">Notifications - e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <% if (instructorRole) { %>
    <%@ include file="/jsp/common/instructor_ui_head.jspf" %>
    <% } else if (adminRole) { %>
    <%@ include file="/jsp/common/admin_ui_head.jspf" %>
    <% } else { %>
    <%@ include file="/jsp/common/ui_head.jspf" %>
    <% } %>
    <% if (adminRole) { %>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/admin-redesign.css">
    <script defer src="<%= request.getContextPath() %>/assets/js/admin.js"></script>
    <% } else { %>
    <script defer src="<%= request.getContextPath() %>/assets/js/app.js"></script>
    <% } %>
</head>
<body class="<%= adminRole ? "admin-body admin-package-page admin-module-page" : (instructorRole ? "instructor-premium-page instructor-package-page instructor-module-page" : "sd-page") %>">
<% if (adminRole) { %>
<div class="admin-shell">
    <%@ include file="/jsp/common/admin_header.jspf" %>
    <div class="admin-layout">
        <%@ include file="/jsp/admin/admin_sidebar.jspf" %>
        <main class="admin-main">
            <div class="admin-workspace-view">

                <div class="ar-breadcrumb">
                    <a href="<%= request.getContextPath() %>/admin/dashboard" data-i18n="admin.nav.dashboard">Dashboard</a>
                    <span class="sep">&rsaquo;</span>
                    <span data-i18n="admin.notifications.title">Notifications</span>
                </div>

                <div class="ar-hero">
                    <div>
                        <div class="ar-hero__eyebrow" data-i18n="admin.notifications.title">Notifications</div>
                        <h1 class="ar-hero__title" data-i18n="admin.notifications.pageTitle">Stay on top of platform updates.</h1>
                        <p class="ar-hero__sub" data-i18n="admin.notifications.subtitle">Track admin-facing system messages, approval-related signals, and operational reminders from one place.</p>
                    </div>
                </div>

                <div class="ar-section">
                    <div class="ar-section__head">
                        <div>
                            <div class="ar-section__kicker" data-i18n="admin.common.inbox">Inbox</div>
                            <div class="ar-section__title" data-i18n="admin.common.recentNotifications">Recent notifications</div>
                        </div>
                        <span class="ar-badge ar-badge--neutral" data-i18n="admin.common.itemsCount" data-i18n-vars='{"count":"<%= notifications == null ? 0 : notifications.size() %>"}'><%= notifications == null ? 0 : notifications.size() %> item<%= notifications != null && notifications.size() == 1 ? "" : "s" %></span>
                    </div>

                    <% if (notifications == null || notifications.isEmpty()) { %>
                    <div class="ar-empty">
                        <div class="ar-empty__title" data-i18n="admin.common.noNotificationsTitle">No notifications yet</div>
                        <div class="ar-empty__sub" data-i18n="admin.common.noNotificationsSub">New system events will appear here when the platform has something worth your attention.</div>
                    </div>
                    <% } else { %>
                    <div class="ar-notification-list">
                        <% for (Notification n : notifications) { %>
                        <div class="ar-notification-item">
                            <div class="ar-notification-item__message"><%= n.getMessage() == null ? "Notification" : n.getMessage() %></div>
                            <div class="ar-notification-item__meta"><%= n.getCreatedAt() == null ? "-" : n.getCreatedAt() %></div>
                        </div>
                        <% } %>
                    </div>
                    <% } %>
                </div>

            </div>
            <%@ include file="/jsp/common/app_footer.jspf" %>
        </main>
    </div>
</div>
<% } else { %>
<div class="app-shell">
    <% if (instructorRole) { %>
    <%@ include file="/jsp/common/instructor_header.jspf" %>
    <% } else { %>
    <%@ include file="/jsp/common/app_header.jspf" %>
    <% } %>

    <div class="app-main">
        <% if (instructorRole) { %>
        <div class="container sd-container instructor-workspace-shell">
            <div class="instructor-shell-layout">
                <%@ include file="/jsp/instructor/instructor_sidebar.jspf" %>

                <main class="instructor-shell-content" role="main">
                    <div class="instructor-workspace-view iup-fade-in">

                    <header class="iup-notif-banner" aria-labelledby="instrNotifBannerHeading">
                        <div class="iup-notif-banner__left">
                            <span class="iup-notif-banner__icon" aria-hidden="true">
                                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                    <path d="M15 17H9m9-1.5V10a6 6 0 1 0-12 0v5.5L4.5 17v1h15v-1L18 15.5Z" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round"/>
                                    <path d="M10 19a2 2 0 0 0 4 0" stroke="currentColor" stroke-width="1.65" stroke-linecap="round"/>
                                </svg>
                            </span>
                            <div>
                                <h1 id="instrNotifBannerHeading" data-i18n="instructor.notifications.title">Notifications</h1>
                                <p data-i18n="instructor.notifications.bannerDesc">Stay informed about account events, workflow updates, and messages connected to your teaching workspace.</p>
                            </div>
                        </div>
                        <span class="iup-notif-banner__count" data-i18n="admin.common.itemsCount" data-i18n-vars='{"count":"<%= notifications == null ? 0 : notifications.size() %>"}'><%= notifications == null ? 0 : notifications.size() %> item<%= notifications != null && notifications.size() == 1 ? "" : "s" %></span>
                    </header>

                    <% if (notifications == null || notifications.isEmpty()) { %>
                    <div class="iup-notif-empty">
                        <strong data-i18n="instructor.notifications.emptyTitle">Nothing here yet</strong>
                        <span data-i18n="instructor.notifications.emptySub">You will see new activity as your sessions move forward.</span>
                    </div>
                    <% } else { %>
                    <div class="iup-notif-list">
                        <% for (Notification n : notifications) {
                               if (n == null) continue;
                               String msg = n.getMessage();
                               String title = notifTitle(msg);
                               String kind = notifKind(msg);
                               String iconCls = "star".equals(kind) ? "iup-notif-card__icon iup-notif-card__icon--star"
                                       : ("clock".equals(kind) ? "iup-notif-card__icon iup-notif-card__icon--clock"
                                       : "iup-notif-card__icon");
                        %>
                        <article class="iup-notif-card">
                            <div class="<%= iconCls %>" aria-hidden="true">
                                <% if ("star".equals(kind)) { %>
                                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="m12 3 2.6 5.3 5.9.9-4.25 4.1 1 5.85L12 16.9 6.75 19.15l1-5.85L3.5 9.2l5.9-.9L12 3Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/></svg>
                                <% } else if ("clock".equals(kind)) { %>
                                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.65"/><path d="M12 7v5l3 3" stroke="currentColor" stroke-width="1.65" stroke-linecap="round"/></svg>
                                <% } else { %>
                                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M15 17H9m9-1.5V10a6 6 0 1 0-12 0v5.5L4.5 17v1h15v-1L18 15.5Z" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round"/><path d="M10 19a2 2 0 0 0 4 0" stroke="currentColor" stroke-width="1.65" stroke-linecap="round"/></svg>
                                <% } %>
                            </div>
                            <div class="iup-notif-card__body">
                                <div class="iup-notif-card__head">
                                    <h2 class="iup-notif-card__title"><%= notifEsc(title) %></h2>
                                    <span class="iup-notif-card__time"><%= notifEsc(notifAgo(n.getCreatedAt())) %></span>
                                </div>
                                <p class="iup-notif-card__msg"><%= notifEsc(msg == null ? "" : msg) %></p>
                            </div>
                        </article>
                        <% } %>
                    </div>
                    <% } %>

                    </div>
                    <%@ include file="/jsp/common/app_footer.jspf" %>
                </main>
            </div>
        </div>
        <% } else { %>
        <div class="container sd-container">
            <div class="dash-content">
                <section class="sd-hero" aria-label="Notifications">
                    <div class="sd-hero-blur sd-hero-blur--a"></div>
                    <div class="sd-hero-blur sd-hero-blur--b"></div>
                    <div class="sd-hero-inner">
                        <div class="sd-hero-head">
                            <div>
                                <div class="sd-hero-kicker" data-i18n="student.nav.dashboard">Workspace</div>
                                <h2 class="sd-hero-title" data-i18n="student.notifications.title">Notifications</h2>
                                <p class="sd-hero-subtitle" data-i18n="student.notifications.bannerDesc">Stay informed about account events, workflow updates, and messages connected to your role.</p>
                            </div>
                            <div class="sd-hero-actions">
                                <a class="btn btn-ghost" href="<%= request.getContextPath() %>/dashboard">Dashboard</a>
                            </div>
                        </div>
                    </div>
                </section>

                <section class="sd-card" aria-label="Notification feed">
                    <div class="sd-card-head sd-card-head--plain">
                        <div>
                            <div class="sd-card-eyebrow">Inbox</div>
                            <div class="sd-card-title">Recent Updates</div>
                        </div>
                        <span class="status-pill status-pill--neutral"><%= notifications == null ? 0 : notifications.size() %> items</span>
                    </div>
                    <div class="sd-card-body">
                        <% if (notifications == null || notifications.isEmpty()) { %>
                        <div class="sd-empty">
                            <div class="sd-empty-title">No notifications yet</div>
                            <div class="sd-empty-sub">New workflow events will appear here when the system has something worth your attention.</div>
                        </div>
                        <% } else { %>
                        <div class="notification-list">
                            <% for (Notification n : notifications) { %>
                            <article class="notification-item">
                                <h3><%= n.getMessage() == null ? "Notification" : n.getMessage() %></h3>
                                <div class="notification-item-meta">Created: <%= n.getCreatedAt() == null ? "-" : n.getCreatedAt() %></div>
                            </article>
                            <% } %>
                        </div>
                        <% } %>
                    </div>
                </section>

                </div>
                    <%@ include file="/jsp/common/app_footer.jspf" %>
            </div>
        </div>
        <% } %>
    </div>
</div>
<% } %>
</body>
</html>
