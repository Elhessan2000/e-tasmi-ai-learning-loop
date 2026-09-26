<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="util.LocaleSupport" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="model.entity.Enrollment" %>
<%@ page import="model.entity.Payment" %>
<%@ page import="model.entity.SessionMode" %>
<%@ page import="model.entity.TasmiSession" %>
<%@ page import="model.entity.TasmiSessionStatus" %>
<%!
    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception ex) {
            return "";
        }
    }

    private static String text(Object val) {
        return val == null ? "" : val.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }

    private static String modeDisplay(TasmiSession s) {
        if (s == null || s.getMode() == null) return "—";
        return s.getMode() == SessionMode.ONLINE ? "Live" : "Physical";
    }

    private static String filterStatusKey(boolean pending, TasmiSessionStatus st) {
        if (pending) return "pending";
        if (st == TasmiSessionStatus.ONGOING) return "active";
        return "upcoming";
    }

    private static String cardLevelLine(TasmiSession session) {
        if (session == null || session.getLevel() == null) return "—";
        return session.getLevel().getDisplayName();
    }

    /** Public URL for instructor banner; matches instructor sessions list behavior. */
    private static String resolveBannerSrc(javax.servlet.http.HttpServletRequest req, String bannerUrl) {
        String t = trimToNull(bannerUrl);
        if (t == null) {
            return null;
        }
        if (t.startsWith("http://") || t.startsWith("https://")) {
            return t;
        }
        String ctx = req.getContextPath() == null ? "" : req.getContextPath();
        if (t.startsWith("/")) {
            return ctx + t;
        }
        return ctx + "/" + t;
    }

    private static String capacityDisplay(TasmiSession session, int availableSeats) {
        if (session == null) {
            return "—";
        }
        int cap = session.getCapacity();
        if (cap <= 0) {
            return "No fixed seat limit";
        }
        if (availableSeats <= 0) {
            return "Full · " + cap + " seats";
        }
        return availableSeats + " of " + cap + " seats available";
    }

    /** Minimal seats metric for compact cards: "10/10 Seats", "Full", or "Open seats". */
    private static String compactSeats(TasmiSession session, int availableSeats) {
        if (session == null) {
            return "—";
        }
        int cap = session.getCapacity();
        if (cap <= 0) {
            return "Open seats";
        }
        if (availableSeats <= 0) {
            return "Full";
        }
        return availableSeats + "/" + cap + " Seats";
    }

    /** Combine date + time concisely, e.g. "09 Jun, 01:39 PM". */
    private static String compactWhen(String compactDate, String time) {
        boolean hasDate = compactDate != null && !"—".equals(compactDate);
        boolean hasTime = time != null && !"—".equals(time);
        if (hasDate && hasTime) return compactDate + ", " + time;
        if (hasDate) return compactDate;
        if (hasTime) return time;
        return "—";
    }

    /**
     * Full session overview for the enrollment modal (untruncated). Prefers the
     * authored description, falls back to quran portion, then the servlet snippet.
     */
    private static String modalDescription(Map<String, Object> card, TasmiSession session) {
        if (session != null && trimToNull(session.getDescription()) != null) {
            return session.getDescription().trim();
        }
        if (session != null && trimToNull(session.getQuranPortion()) != null) {
            return session.getQuranPortion().trim();
        }
        if (card != null) {
            Object ds = card.get("descriptionSnippet");
            if (ds != null) {
                String s = trimToNull(ds.toString());
                if (s != null) return s;
            }
        }
        return "";
    }
%>
<%
    request.setAttribute("activeMenu", "sessions");
    String ctx = request.getContextPath();
    List<Map<String, Object>> sessionCards = (List<Map<String, Object>>) request.getAttribute("sessionCards");
    String searchQuery = request.getAttribute("searchQuery") == null ? "" : String.valueOf(request.getAttribute("searchQuery"));
    boolean hasSearch = trimToNull(searchQuery) != null;
    String success = (String) request.getAttribute("success");
    String error = (String) request.getAttribute("error");

    DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("hh:mm a");
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}" data-locale="${empty currentLocale ? 'en' : currentLocale}" data-dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="meta.studentBrowseSessionsTitle">Browse Sessions - e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <%@ include file="/jsp/common/student_ui_head.jspf" %>
    <link rel="stylesheet" href="<%= ctx %>/css/student-browse-sessions.css?v=20260628-dark-browse1">
    <script defer src="<%= ctx %>/assets/js/app.js"></script>
    <script defer src="<%= ctx %>/assets/js/browse-sessions.js?v=20260624-filters3"></script>
</head>
<body class="student-premium-page student-package-page student-module-page student-browse-sessions-page">
<div class="app-shell">
    <%@ include file="/jsp/common/student_header.jspf" %>
    <div class="app-main">
        <div class="container sd-container student-workspace-shell">
            <div class="student-shell-layout">
                <%@ include file="/jsp/student/student_sidebar.jspf" %>

                <main class="student-shell-content" role="main">
                    <div class="student-workspace-view">
                        <header class="student-hero student-hero--sessions" aria-labelledby="browseSsBannerTitle">
                            <div class="student-hero__copy">
                                <h1 class="student-hero__title" id="browseSsBannerTitle" data-i18n="student.browse.title">Browse Sessions</h1>
                                <p class="student-hero__sub" data-i18n="student.browse.subtitle">Discover and enroll in Qur'an recitation sessions</p>
                            </div>
                            <div class="student-hero__visual" aria-hidden="true">
                                <span class="student-hero__badge">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                        <rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.7"/>
                                        <path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/>
                                    </svg>
                                </span>
                            </div>
                        </header>

                        <% if (success != null) { %>
                        <div class="browse-ss-alert browse-ss-alert--success" role="status">
                            <svg viewBox="0 0 24 24" fill="none"><path d="M9 12l2 2 4-4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/></svg>
                            <%= success %>
                        </div>
                        <% } %>
                        <% if (error != null) { %>
                        <div class="browse-ss-alert browse-ss-alert--error" role="alert">
                            <svg viewBox="0 0 24 24" fill="none"><path d="M12 8v4m0 4h.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/><circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2"/></svg>
                            <%= error %>
                        </div>
                        <% } %>

                        <% int totalAvailable = (sessionCards == null) ? 0 : sessionCards.size(); %>
                        <section class="browse-ss-toolbar" aria-label="Session discovery filters">
                            <form class="browse-ss-search-form" method="get" action="<%= LocaleSupport.localizedUrl(request, "/student/available-sessions") %>" role="search" data-i18n="student.browse.searchAria" data-i18n-attr="aria-label">
                                <svg class="browse-ss-search-form__icon" width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="1.75"/><path d="m20 20-4.3-4.3" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/></svg>
                                <input type="text" name="q" value="<%= text(searchQuery) %>" placeholder="Search by title, instructor, or surah..." data-i18n="student.browse.searchPlaceholder" data-i18n-attr="placeholder" autocomplete="off"/>
                                <button type="submit" class="browse-ss-search-form__submit" data-i18n="student.browse.searchAria" data-i18n-attr="aria-label" aria-label="Search sessions">
                                    <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><circle cx="11" cy="11" r="7" stroke="currentColor" stroke-width="2"/><path d="m20 20-4.3-4.3" stroke="currentColor" stroke-width="2" stroke-linecap="round"/></svg>
                                </button>
                            </form>
                            <div class="browse-ss-filter-wrap">
                                <svg class="browse-ss-filter-wrap__icon" width="18" height="18" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M4 6h16M7 12h10M10 18h4" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/></svg>
                                <select id="browseSsFilter" class="browse-ss-filter-select" data-browse-ss-filter data-i18n="student.browse.sessionFilter" data-i18n-attr="aria-label" aria-label="Session filter">
                                    <option value="all:all" selected data-i18n="student.browse.filterAll">All Sessions</option>
                                    <option value="status:upcoming" data-i18n="student.browse.filterUpcoming">Upcoming Sessions</option>
                                    <option value="status:pending" data-i18n="student.browse.filterPending">Pending Sessions</option>
                                </select>
                            </div>
                            <div class="browse-ss-sort">
                                <label class="browse-ss-sort__label" for="browseSsSort" data-i18n="student.browse.sortLabel">Sort by</label>
                                <select id="browseSsSort" class="browse-ss-sort__select" data-browse-ss-sort data-i18n="student.browse.sortLabel" data-i18n-attr="aria-label" aria-label="Sort by">
                                    <option value="default" data-i18n="student.browse.sortDefault">Default Sorting</option>
                                    <option value="newest" data-i18n="student.browse.sortNewest">Newest</option>
                                    <option value="oldest" data-i18n="student.browse.sortOldest">Oldest</option>
                                    <option value="popular" data-i18n="student.browse.sortPopular">Most Popular</option>
                                    <option value="price_asc" data-i18n="student.browse.sortPriceLow">Price: Low to High</option>
                                    <option value="price_desc" data-i18n="student.browse.sortPriceHigh">Price: High to Low</option>
                                    <option value="az" data-i18n="student.browse.sortAZ">A-Z</option>
                                    <option value="za" data-i18n="student.browse.sortZA">Z-A</option>
                                </select>
                            </div>
                            <div class="browse-ss-chips" role="group" aria-hidden="true" hidden>
                                <button type="button" class="browse-ss-chip-btn is-active" data-chip data-chip-type="all" data-chip-value="all" aria-pressed="true"></button>
                            </div>
                            <div class="browse-ss-count" aria-live="polite" hidden>
                                <strong data-browse-ss-count><%= totalAvailable %></strong>
                            </div>
                        </section>

                        <% if (sessionCards == null || sessionCards.isEmpty()) { %>
                        <div class="browse-ss-empty">
                            <div class="browse-ss-empty__title"><% if (hasSearch) { %><span data-i18n="student.browse.emptyNoSearch">No matching sessions found</span><% } else { %><span data-i18n="student.browse.emptyNoSessions">No sessions available right now</span><% } %></div>
                            <div class="browse-ss-empty__sub">
                                <% if (hasSearch) { %>
                                <span data-i18n="student.browse.emptySearchHint">Try a different session title, surah, topic, or instructor.</span>
                                <% } else { %>
                                <span data-i18n="student.browse.emptyDefaultHint">When instructors publish Tasmi sessions, they will appear here for enrollment.</span>
                                <% } %>
                            </div>
                            <% if (hasSearch) { %>
                            <div class="browse-ss-empty__actions">
                                <a href="<%= LocaleSupport.localizedUrl(request, "/student/available-sessions") %>" data-i18n="student.browse.clearSearch">Clear search</a>
                            </div>
                            <% } %>
                        </div>
                        <% } else { %>
                        <div class="browse-ss-grid">
                            <% for (Map<String, Object> card : sessionCards) {
                                   TasmiSession tasmiSession = (TasmiSession) card.get("session");
                                   if (tasmiSession == null) continue;

                                   Enrollment existingEnrollment = (Enrollment) card.get("enrollment");
                                   Payment payment = (Payment) card.get("payment");
                                   String instructorName = card.get("instructorName") == null ? "Instructor" : String.valueOf(card.get("instructorName"));
                                   String feeLabel = card.get("feeLabel") == null ? "Free session" : String.valueOf(card.get("feeLabel"));
                                   String sessionModeLabel = modeDisplay(tasmiSession);
                                   int availableSeats = card.get("availableSeats") instanceof Number ? ((Number) card.get("availableSeats")).intValue() : 0;
                                   boolean hasReservedSeat = existingEnrollment != null
                                           && existingEnrollment.getEnrollmentStatus() != model.entity.EnrollmentStatus.CANCELLED
                                           && existingEnrollment.getEnrollmentStatus() != model.entity.EnrollmentStatus.REJECTED;
                                   boolean hasCapacity = tasmiSession.getCapacity() <= 0 || availableSeats > 0 || hasReservedSeat;
                                   String enrollHref = ctx + "/student/enroll-confirm?sessionId=" + tasmiSession.getSessionId();
                                   if (hasSearch) {
                                       enrollHref += "&q=" + urlEncode(searchQuery);
                                   }
                                   boolean isFree = "Free session".equals(feeLabel);
                                   boolean pendingVerification = Boolean.TRUE.equals(card.get("pendingVerification"));
                                   TasmiSessionStatus st = tasmiSession.getStatus() != null ? tasmiSession.getStatus() : TasmiSessionStatus.SCHEDULED;

                                   String badgeClass;
                                   String badgeText;
                                   if (pendingVerification) {
                                       badgeClass = "browse-ss-card__badge browse-ss-card__badge--pending";
                                       badgeText = "Pending Verification";
                                   } else if (st == TasmiSessionStatus.ONGOING) {
                                       badgeClass = "browse-ss-card__badge browse-ss-card__badge--active";
                                       badgeText = "Active";
                                   } else {
                                       badgeClass = "browse-ss-card__badge browse-ss-card__badge--upcoming";
                                       badgeText = "Upcoming";
                                   }

                                   String fStat = filterStatusKey(pendingVerification, st);
                                   String dateShown = tasmiSession.getSessionDate() == null ? "—" : dateFmt.format(tasmiSession.getSessionDate());
                                   String timeShown = tasmiSession.getSessionTime() == null ? "—" : timeFmt.format(tasmiSession.getSessionTime());
                                   String cardTitle = trimToNull(tasmiSession.getTitle()) == null ? ("Session #" + tasmiSession.getSessionId()) : tasmiSession.getTitle();
                                   String levelShown = cardLevelLine(tasmiSession);
                                   String bannerSrc = resolveBannerSrc(request, tasmiSession.getBannerImageUrl());
                                   String seatsShown = capacityDisplay(tasmiSession, availableSeats);
                                   String aboutShown = modalDescription(card, tasmiSession);
                                   int enrolledCount = card.get("enrolledCount") instanceof Number ? ((Number) card.get("enrolledCount")).intValue() : 0;
                                   String sortDate = tasmiSession.getSessionDate() == null ? "" : tasmiSession.getSessionDate().toString();
                                   String sortPrice = isFree || tasmiSession.getFee() == null ? "0" : tasmiSession.getFee().toPlainString();
                                   String sortTitle = cardTitle == null ? "" : cardTitle.toLowerCase();
                                   String modeKey = tasmiSession.getMode() == null ? "" : tasmiSession.getMode().name().toLowerCase();
                            %>
                            <article class="browse-ss-card"
                                     data-filter-status="<%= text(fStat) %>"
                                     data-mode="<%= text(modeKey) %>"
                                     data-sort-date="<%= text(sortDate) %>"
                                     data-sort-price="<%= text(sortPrice) %>"
                                     data-sort-popular="<%= enrolledCount %>"
                                     data-sort-title="<%= text(sortTitle) %>">
                                <div class="browse-ss-card__media">
                                    <% if (bannerSrc != null) { %>
                                    <img class="browse-ss-card__img"
                                         src="<%= text(bannerSrc) %>"
                                         alt="<%= text(cardTitle) %> — session image"
                                         loading="lazy"
                                         decoding="async"/>
                                    <% } else { %>
                                    <div class="browse-ss-card__placeholder" aria-hidden="true">
                                        <svg width="56" height="56" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                                            <rect x="3" y="5" width="18" height="14" rx="2.5" stroke="currentColor" stroke-width="1.5"/>
                                            <circle cx="8.5" cy="10.5" r="1.75" fill="currentColor"/>
                                            <path d="M21 15l-4.5-4.5L11 17" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
                                        </svg>
                                        <span class="browse-ss-card__placeholder-label" data-i18n="student.browse.sessionImage">Session image</span>
                                    </div>
                                    <% } %>
                                </div>
                                <div class="browse-ss-card__body">
                                    <div class="browse-ss-card__head">
                                        <div class="browse-ss-card__titles">
                                            <h2 class="browse-ss-card__title"><%= text(cardTitle) %></h2>
                                            <div class="browse-ss-card__instructor">
                                                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M20 21a8 8 0 1 0-16 0" stroke="currentColor" stroke-width="1.75" stroke-linecap="round"/><circle cx="12" cy="7" r="4" stroke="currentColor" stroke-width="1.75"/></svg>
                                                <span><%= text(instructorName) %></span>
                                            </div>
                                        </div>
                                        <span class="<%= badgeClass %>"><%= text(badgeText) %></span>
                                    </div>

                                    <div class="browse-ss-card__topic">
                                        <span class="browse-ss-card__topic-label" data-i18n="student.browse.sessionTopic">Session Topic</span>
                                        <% if (aboutShown != null && !aboutShown.isEmpty()) { %>
                                        <p class="browse-ss-card__topic-text"><%= text(aboutShown) %></p>
                                        <% } else { %>
                                        <p class="browse-ss-card__topic-text browse-ss-card__topic-text--muted" data-i18n="student.browse.noTopic">No topic description provided.</p>
                                        <% } %>
                                    </div>

                                    <div class="browse-ss-card__schedule">
                                        <span class="browse-ss-schedule-item">
                                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><rect x="3" y="5" width="18" height="16" rx="2" stroke="currentColor" stroke-width="1.7"/><path d="M3 10h18M8 3v4M16 3v4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
                                            <%= text(dateShown) %>
                                        </span>
                                        <span class="browse-ss-schedule-item">
                                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><circle cx="12" cy="12" r="9" stroke="currentColor" stroke-width="1.7"/><path d="M12 7v5l3 2" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
                                            <%= text(timeShown) %>
                                        </span>
                                        <% if (sessionModeLabel != null && !"—".equals(sessionModeLabel)) { %>
                                        <span class="browse-ss-schedule-item browse-ss-schedule-item--live">
                                            <svg width="15" height="15" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><circle cx="12" cy="12" r="4" fill="currentColor"/><path d="M12 2a10 10 0 0 1 0 20" stroke="currentColor" stroke-width="1.7" stroke-linecap="round"/></svg>
                                            <%= text(sessionModeLabel) %>
                                        </span>
                                        <% } %>
                                    </div>

                                    <div class="browse-ss-card__stats">
                                        <div class="browse-ss-stat">
                                            <span class="browse-ss-stat__label" data-i18n="student.browse.level">Level</span>
                                            <span class="browse-ss-stat__value">
                                                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M22 10v6M2 10l10-5 10 5-10 5z" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round"/><path d="M6 12v5c0 1.1 2.7 2 6 2s6-.9 6-2v-5" stroke="currentColor" stroke-width="1.7"/></svg>
                                                <%= text(levelShown) %>
                                            </span>
                                        </div>
                                        <div class="browse-ss-stat">
                                            <span class="browse-ss-stat__label" data-i18n="student.browse.seats">Seats</span>
                                            <span class="browse-ss-stat__value">
                                                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/><circle cx="9" cy="7" r="4" stroke="currentColor" stroke-width="1.7"/></svg>
                                                <%= text(seatsShown) %>
                                            </span>
                                        </div>
                                    </div>
                                </div>
                                <div class="browse-ss-card__price-band">
                                    <div class="browse-ss-card__price-icon" aria-hidden="true">
                                        <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="M12 2v20M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                    </div>
                                    <div class="browse-ss-card__price-copy">
                                        <span class="browse-ss-card__price-label" data-i18n="student.browse.price">Price</span>
                                        <span class="browse-ss-card__price-value"><%= text(feeLabel) %></span>
                                    </div>
                                </div>
                                <div class="browse-ss-card__foot">
                                    <% if (pendingVerification) { %>
                                    <a class="browse-ss-card__cta browse-ss-card__cta--outline" href="<%= LocaleSupport.localizedUrl(request, "/student/payments?enrollmentId=" + existingEnrollment.getEnrollmentId()) %>" data-i18n="student.browse.viewPayment">View payment</a>
                                    <% } else if (!hasCapacity) { %>
                                    <span class="browse-ss-card__cta browse-ss-card__cta--muted" aria-disabled="true" data-i18n="student.browse.full">Session full</span>
                                    <% } else { %>
                                    <button type="button"
                                            class="browse-ss-card__cta browse-ss-card__cta--primary"
                                            data-browse-open-modal
                                            data-checkout-url="<%= text(enrollHref) %>"
                                            data-modal-title="<%= text(cardTitle) %>"
                                            data-modal-instructor="<%= text(instructorName) %>"
                                            data-modal-date="<%= text(dateShown) %>"
                                            data-modal-time="<%= text(timeShown) %>"
                                            data-modal-mode="<%= text(sessionModeLabel) %>"
                                            data-modal-fee="<%= text(feeLabel) %>"
                                            data-modal-description="<%= text(aboutShown) %>"
                                            data-is-free="<%= isFree %>"
                                            data-i18n="student.browse.enrollNow">
                                        Enroll Now
                                    </button>
                                    <% } %>
                                </div>
                            </article>
                            <% } %>
                        </div>
                        <% } %>

                    </div>

                    <div class="browse-ss-modal-overlay" data-browse-ss-modal aria-hidden="true" role="dialog" aria-modal="true" aria-labelledby="browseSsModalTitleEl">
                        <div class="browse-ss-modal">
                            <button type="button" class="browse-ss-modal__close" data-i18n="student.browse.closeDialog" data-i18n-attr="aria-label" aria-label="Close dialog">
                                <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg"><path d="m6 6 12 12M18 6 6 18" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>
                            </button>
                            <div class="browse-ss-modal__pad">
                                <h2 id="browseSsModalTitleEl" class="browse-ss-modal__title" data-i18n="student.browse.modalTitle">Enroll in Session</h2>
                                <p class="browse-ss-modal__subtitle" data-modal-subtitle data-i18n="student.browse.modalSubtitle">Review session details and proceed with payment</p>
                                <div class="browse-ss-modal__panel">
                                    <p class="browse-ss-modal__session-title" data-modal-field="title">&nbsp;</p>
                                    <div class="browse-ss-modal__about" data-modal-about hidden>
                                        <span class="browse-ss-modal__about-label" data-i18n="student.browse.aboutSession">About this Session</span>
                                        <p class="browse-ss-modal__about-text" data-modal-field="description"></p>
                                    </div>
                                    <dl class="browse-ss-modal__dl">
                                        <div><dt data-i18n="common.instructor">Instructor</dt><dd data-modal-field="instructor"></dd></div>
                                        <div><dt data-i18n="common.date">Date</dt><dd data-modal-field="date"></dd></div>
                                        <div><dt data-i18n="common.time">Time</dt><dd data-modal-field="time"></dd></div>
                                        <div><dt data-i18n="student.recitations.sessionType">Session Type</dt><dd data-modal-field="mode"></dd></div>
                                    </dl>
                                </div>
                                <div class="browse-ss-modal__pay-row">
                                    <span class="browse-ss-modal__pay-label" data-i18n="student.browse.totalAmount">Total Amount</span>
                                    <span class="browse-ss-modal__pay-amt" data-modal-field="amount">&nbsp;</span>
                                </div>
                                <div class="browse-ss-modal__pay-row browse-ss-modal__pay-row--method" data-modal-payment-method-row>
                                    <span class="browse-ss-modal__pay-label" data-i18n="student.browse.paymentMethod">Payment Method</span>
                                    <span class="browse-ss-modal__method-value" data-i18n="student.browse.qrPayment">QR Transfer</span>
                                </div>
                                <a href="#" class="browse-ss-modal__confirm" data-i18n="student.browse.confirmPayment">Confirm Payment</a>
                            </div>
                        </div>
                    </div>

                    <%@ include file="/jsp/common/app_footer.jspf" %>
                </main>
            </div>
        </div>
    </div>
</div>
</body>
</html>
