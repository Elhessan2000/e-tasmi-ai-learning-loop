<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.time.Instant" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<%@ page import="model.entity.Notification" %>
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

    private static String ago(Instant instant) {
        if (instant == null) {
            return "-";
        }
        long diffMs = Instant.now().toEpochMilli() - instant.toEpochMilli();
        if (diffMs < 60000L) {
            return "Just now";
        }
        long minutes = diffMs / 60000L;
        if (minutes < 60L) {
            return minutes == 1L ? "1 minute ago" : minutes + " minutes ago";
        }
        long hours = minutes / 60L;
        if (hours < 24L) {
            return hours == 1L ? "1 hour ago" : hours + " hours ago";
        }
        long days = hours / 24L;
        return days == 1L ? "1 day ago" : days + " days ago";
    }

    /**
     * Display title derived from message text (single message field from backend).
     */
    private static String cardTitle(String message) {
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);
        if (m.contains("evaluat")) {
            return "New Evaluation Available";
        }
        if (m.contains("reminder") || m.contains("starts in") || m.contains("24 hour")) {
            return "Session Reminder";
        }
        if (m.contains("complete") || m.contains("marked as complete")) {
            return "Session Completed";
        }
        if ((m.contains("pending") && m.contains("payment")) || (m.contains("payment") && m.contains("pending"))) {
            return "Payment Pending";
        }
        if (m.contains("receipt") || m.contains("qr") || (m.contains("payment") && !m.contains("session"))) {
            return "Payment Pending";
        }
        if (m.contains("session")) {
            return "Session Update";
        }
        return "Notification";
    }

    private static String iconKind(String message) {
        String t = cardTitle(message);
        if ("Session Completed".equals(t)) {
            return "star";
        }
        if ("Payment Pending".equals(t)) {
            return "clock";
        }
        return "bell";
    }
%>
<%
    request.setAttribute("activeMenu", "notifications");
    List<Notification> notifications = (List<Notification>) request.getAttribute("notifications");
    String ctx = request.getContextPath();
    int notifCount = notifications == null ? 0 : notifications.size();
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.studentNotificationsTitle">Notifications - e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <%@ include file="/jsp/common/student_ui_head.jspf" %>
    <link rel="stylesheet" href="<%= ctx %>/css/student-notifications.css?v=20260614-minimal-head">
    <link rel="stylesheet" href="<%= ctx %>/css/student-ui-refine.css?v=20260420-student-sidebar1">
    <script defer src="<%= ctx %>/assets/js/app.js"></script>
</head>
<body class="student-premium-page student-package-page student-module-page student-notifications-page">
<div class="app-shell">
    <%@ include file="/jsp/common/student_header.jspf" %>
    <div class="app-main">
        <div class="container sd-container student-workspace-shell">
            <div class="student-shell-layout">
                <%@ include file="/jsp/student/student_sidebar.jspf" %>

                <main class="student-shell-content" role="main">
                    <div class="student-workspace-view">
                        <%@ include file="/jsp/common/student_breadcrumb.jspf" %>
                        <header class="student-hero student-hero--notifications">
                            <div class="student-hero__copy">
                                <h1 id="notifBannerHeading" class="student-hero__title" data-i18n="student.notifications.title">Notifications</h1>
                                <p class="student-hero__sub" id="notifUnreadLine">You have <span id="notifUnreadCount"><%= notifCount %></span> unread notification<span id="notifUnreadPlural"><%= notifCount == 1 ? "" : "s" %></span></p>
                            </div>
                            <% if (notifCount > 0) { %>
                            <div class="student-hero__actions">
                                <button type="button" class="student-hero__btn notif-head__mark" id="notifMarkAllRead" data-i18n="student.notifications.markAllRead" data-i18n-attr="aria-label" aria-label="Mark all as read">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M20 6 9 17l-5-5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                    <span data-i18n="student.notifications.markAllRead">Mark all as read</span>
                                </button>
                            </div>
                            <% } %>
                        </header>

                        <% if (notifications == null || notifications.isEmpty()) { %>
                        <div class="notif-empty">
                            <strong data-i18n="student.notifications.emptyTitle">Nothing here yet</strong>
                            <span data-i18n="student.notifications.emptySub">You will see new activity as your sessions move forward.</span>
                        </div>
                        <% } else { %>
                        <div class="notif-list" id="notifList" data-notify-storage-read="etasmi_notif_read_ids" data-notify-storage-dismiss="etasmi_notif_dismissed_ids">
                            <% for (Notification n : notifications) {
                                   if (n == null) continue;
                                   long nid = n.getNotificationId();
                                   String msg = n.getMessage();
                                   String title = cardTitle(msg);
                                   String kind = iconKind(msg);
                                   String wrapClass = "notif-card__icon-wrap--" + ("star".equals(kind) ? "star" : ("clock".equals(kind) ? "clock" : "bell"));
                            %>
                            <article class="notif-card notif-card--unread"
                                     data-notification-id="<%= nid %>"
                                     data-notif-kind="<%= esc(kind) %>">
                                <div class="notif-card__icon-wrap <%= wrapClass %>" aria-hidden="true">
                                    <% if ("star".equals(kind)) { %>
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="m12 3 2.6 5.3 5.9.9-4.25 4.1 1 5.85L12 16.9 6.75 19.15l1-5.85L3.5 9.2l5.9-.9L12 3Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/></svg>
                                    <% } else if ("clock".equals(kind)) { %>
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.65"/><path d="M12 7v5l3 3" stroke="currentColor" stroke-width="1.65" stroke-linecap="round"/></svg>
                                    <% } else { %>
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M15 17H9m9-1.5V10a6 6 0 1 0-12 0v5.5L4.5 17v1h15v-1L18 15.5Z" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round"/><path d="M10 19a2 2 0 0 0 4 0" stroke="currentColor" stroke-width="1.65" stroke-linecap="round"/></svg>
                                    <% } %>
                                </div>
                                <div class="notif-card__body">
                                    <div class="notif-card__head">
                                        <div class="notif-card__title-row">
                                            <h2 class="notif-card__title"><%= esc(title) %></h2>
                                            <span class="notif-card__new" data-notif-new-badge data-i18n="student.notifications.new">New</span>
                                        </div>
                                        <span class="notif-card__time"><%= esc(ago(n.getCreatedAt())) %></span>
                                    </div>
                                    <p class="notif-card__msg"><%= esc(msg == null ? "" : msg) %></p>
                                </div>
                                <div class="notif-card__actions">
                                    <button type="button" class="notif-card__btn notif-card__btn--read" data-notif-read="<%= nid %>" data-i18n="student.notifications.markAsRead" data-i18n-attr="aria-label|title" aria-label="Mark as read" title="Mark as read">
                                        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M20 6 9 17l-5-5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                    </button>
                                    <button type="button" class="notif-card__btn notif-card__btn--delete" data-notif-dismiss="<%= nid %>" data-i18n="student.notifications.dismiss" data-i18n-attr="aria-label|title" aria-label="Dismiss" title="Dismiss">
                                        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M3 6h18M8 6V4h8v2m-1 0v14a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V6h10zM10 11v6M14 11v6" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/></svg>
                                    </button>
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
    </div>
</div>
<% if (notifCount > 0) { %>
<script>
(function () {
  var list = document.getElementById('notifList');
  if (!list) return;

  var KEY_READ = list.getAttribute('data-notify-storage-read') || 'etasmi_notif_read_ids';
  var KEY_DISMISS = list.getAttribute('data-notify-storage-dismiss') || 'etasmi_notif_dismissed_ids';

  function parseIds(key) {
    try {
      var raw = sessionStorage.getItem(key);
      var arr = raw ? JSON.parse(raw) : [];
      return Array.isArray(arr) ? arr.map(Number).filter(function (x) { return !isNaN(x); }) : [];
    } catch (e) {
      return [];
    }
  }

  function saveIds(key, ids) {
    try {
      sessionStorage.setItem(key, JSON.stringify(ids));
    } catch (e) {}
  }

  function cardEl(id) {
    return list.querySelector('[data-notification-id="' + id + '"]');
  }

  function applyReadState(id, read) {
    var el = cardEl(id);
    if (!el) return;
    if (read) {
      el.classList.remove('notif-card--unread');
      el.classList.add('notif-card--read');
      var nb = el.querySelector('[data-notif-new-badge]');
      if (nb) nb.style.display = 'none';
      var chk = el.querySelector('[data-notif-read="' + id + '"]');
      if (chk) chk.style.display = 'none';
    } else {
      el.classList.add('notif-card--unread');
      el.classList.remove('notif-card--read');
      var nb2 = el.querySelector('[data-notif-new-badge]');
      if (nb2) nb2.style.display = '';
      var chk2 = el.querySelector('[data-notif-read="' + id + '"]');
      if (chk2) chk2.style.display = '';
    }
  }

  function applyDismiss(id) {
    var el = cardEl(id);
    if (el) el.setAttribute('data-dismissed', 'true');
  }

  function refreshBannerCount() {
    var cards = list.querySelectorAll('[data-notification-id]');
    var readArr = parseIds(KEY_READ);
    var unread = 0;
    for (var i = 0; i < cards.length; i++) {
      var c = cards[i];
      if (c.getAttribute('data-dismissed') === 'true') continue;
      var nid = Number(c.getAttribute('data-notification-id'));
      if (readArr.indexOf(nid) === -1) unread++;
    }
    var line = document.getElementById('notifUnreadLine');
    if (line && unread === 0) {
      line.textContent = "You're all caught up";
      return;
    }
    if (!line || unread <= 0) return;
    if (!document.getElementById('notifUnreadCount')) {
      line.innerHTML = 'You have <span id="notifUnreadCount"></span> unread notification<span id="notifUnreadPlural"></span>';
    }
    var nEl = document.getElementById('notifUnreadCount');
    var pEl = document.getElementById('notifUnreadPlural');
    if (nEl) nEl.textContent = String(unread);
    if (pEl) pEl.textContent = unread === 1 ? '' : 's';
  }

  function syncFromStorage() {
    var readArr = parseIds(KEY_READ);
    var dismissArr = parseIds(KEY_DISMISS);
    dismissArr.forEach(function (id) {
      applyDismiss(id);
    });
    readArr.forEach(function (id) {
      applyReadState(id, true);
    });
    refreshBannerCount();
  }

  list.addEventListener('click', function (e) {
    var t = e.target;
    if (!t.closest) return;
    var readBtn = t.closest('[data-notif-read]');
    if (readBtn) {
      var id = Number(readBtn.getAttribute('data-notif-read'));
      var arr = parseIds(KEY_READ);
      if (arr.indexOf(id) === -1) arr.push(id);
      saveIds(KEY_READ, arr);
      applyReadState(id, true);
      refreshBannerCount();
      return;
    }
    var disBtn = t.closest('[data-notif-dismiss]');
    if (disBtn) {
      var did = Number(disBtn.getAttribute('data-notif-dismiss'));
      var darr = parseIds(KEY_DISMISS);
      if (darr.indexOf(did) === -1) darr.push(did);
      saveIds(KEY_DISMISS, darr);
      applyDismiss(did);
      refreshBannerCount();
    }
  });

  var markAll = document.getElementById('notifMarkAllRead');
  if (markAll) {
    markAll.addEventListener('click', function () {
      var cards = list.querySelectorAll('[data-notification-id]');
      var arr = parseIds(KEY_READ);
      for (var i = 0; i < cards.length; i++) {
        var nid = Number(cards[i].getAttribute('data-notification-id'));
        if (cards[i].getAttribute('data-dismissed') === 'true') continue;
        if (arr.indexOf(nid) === -1) arr.push(nid);
      }
      saveIds(KEY_READ, arr);
      for (var j = 0; j < cards.length; j++) {
        var id = Number(cards[j].getAttribute('data-notification-id'));
        if (cards[j].getAttribute('data-dismissed') !== 'true') applyReadState(id, true);
      }
      refreshBannerCount();
    });
  }

  syncFromStorage();
})();
</script>
<% } %>
</body>
</html>
