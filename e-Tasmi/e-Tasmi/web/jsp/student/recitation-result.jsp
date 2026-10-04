<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page import="model.service.StudentRecitationAnalysisPhase" %>
<%@ page import="model.service.StudentRecitationResultPage" %>
<%@ page import="model.service.VerifiedFocusItem" %>
<%@ page import="model.service.VerifiedRecitationView" %>
<%@ page import="model.service.quran.TrustedReference" %>
<%!
    private static String esc(Object raw) {
        if (raw == null) return "";
        return String.valueOf(raw)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String typeLabel(String key) {
        if ("missingWord".equals(key)) return "Missing word";
        if ("differentWord".equals(key)) return "Different word";
        if ("extraWord".equals(key)) return "Extra word";
        if ("assignedPassage".equals(key)) return "Assigned passage";
        if ("listenAgain".equals(key)) return "Listen again";
        return "Note from your instructor";
    }

    private static String practiceAction(String key) {
        if ("missingWord".equals(key)) return "Practise this word until it comes easily.";
        if ("differentWord".equals(key)) return "Repeat this word the way your instructor expects.";
        if ("extraWord".equals(key)) return "Leave this extra word out next time.";
        if ("assignedPassage".equals(key)) return "Practise the assigned passage for this session.";
        if ("listenAgain".equals(key)) return "Listen to this spot again and repeat it slowly.";
        return "Review this point before your next attempt.";
    }

    private static String focusIcon(String key) {
        String p;
        if ("missingWord".equals(key)) p = "<circle cx=\"12\" cy=\"12\" r=\"8.5\"/><path d=\"M12 8.5v7M8.5 12h7\" stroke-linecap=\"round\"/>";
        else if ("differentWord".equals(key)) p = "<path d=\"M7 7.5h11l-3-3M17 16.5H6l3 3\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>";
        else if ("extraWord".equals(key)) p = "<circle cx=\"12\" cy=\"12\" r=\"8.5\"/><path d=\"M8.5 12h7\" stroke-linecap=\"round\"/>";
        else if ("assignedPassage".equals(key)) p = "<path d=\"M12 6.5C10 5 7 4.5 4 5v13c3-.5 6 0 8 1.5 2-1.5 5-2 8-1.5V5c-3-.5-6 0-8 1.5zM12 6.5v13\" stroke-linejoin=\"round\"/>";
        else if ("listenAgain".equals(key)) p = "<path d=\"M4 15v-3a8 8 0 0116 0v3\" stroke-linecap=\"round\"/><rect x=\"3.5\" y=\"14\" width=\"4\" height=\"6\" rx=\"1.5\"/><rect x=\"16.5\" y=\"14\" width=\"4\" height=\"6\" rx=\"1.5\"/>";
        else p = "<path d=\"M5 5h14v10H9l-4 4z\" stroke-linejoin=\"round\"/>";
        return "<svg viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" aria-hidden=\"true\">" + p + "</svg>";
    }

    private static int ayahFromVerseKey(String verseKey) {
        if (verseKey == null || !verseKey.matches("\\d{1,3}:\\d{1,3}")) return 0;
        try {
            return Integer.parseInt(verseKey.substring(verseKey.indexOf(':') + 1));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static String journeyClass(int step, int active) {
        if (step < active) return "is-done";
        if (step == active) return "is-current";
        return "";
    }

    private static String pendingPhaseBodyKey(StudentRecitationAnalysisPhase phase) {
        if (phase == null) return "student.recitations.awaitingReviewSub";
        switch (phase) {
            case ANALYSIS_IN_PROGRESS: return "student.recitations.phaseAnalysisSub";
            case SUBMITTED: return "student.recitations.phaseSubmittedSub";
            case REFERENCE_UNAVAILABLE: return "student.recitations.phaseReferenceSub";
            case ANALYSIS_FAILED: return "student.recitations.phaseFailedSub";
            case CANNOT_EVALUATE: return "student.recitations.phaseCannotEvaluateSub";
            default: return "student.recitations.awaitingReviewSub";
        }
    }

    private static String pendingPhaseTitleKey(StudentRecitationAnalysisPhase phase) {
        if (phase == null) return "student.recitations.awaitingReview";
        switch (phase) {
            case ANALYSIS_IN_PROGRESS: return "student.recitations.phaseAnalysisTitle";
            case SUBMITTED: return "student.recitations.phaseSubmittedTitle";
            case REFERENCE_UNAVAILABLE: return "student.recitations.phaseReferenceTitle";
            case ANALYSIS_FAILED: return "student.recitations.phaseFailedTitle";
            case CANNOT_EVALUATE: return "student.recitations.phaseCannotEvaluateTitle";
            default: return "student.recitations.awaitingReview";
        }
    }

    private static String pendingPhaseTitleDefault(StudentRecitationAnalysisPhase phase) {
        if (phase == null) return "Awaiting instructor review";
        switch (phase) {
            case ANALYSIS_IN_PROGRESS: return "AI analysis in progress";
            case SUBMITTED: return "Submitted";
            case REFERENCE_UNAVAILABLE: return "Reference temporarily unavailable";
            case ANALYSIS_FAILED: return "Analysis could not be completed";
            case CANNOT_EVALUATE: return "Could not evaluate submission";
            default: return "Awaiting instructor review";
        }
    }

    private static String pendingPhaseBodyDefault(StudentRecitationAnalysisPhase phase) {
        if (phase == null) {
            return "Your recitation has been submitted. You'll see the score and feedback here once your instructor completes the evaluation.";
        }
        switch (phase) {
            case ANALYSIS_IN_PROGRESS:
                return "Your recitation is being prepared for instructor review. Scores and feedback appear here after your instructor publishes the result.";
            case SUBMITTED:
                return "Your recitation was received. Analysis will begin shortly.";
            case REFERENCE_UNAVAILABLE:
                return "We could not load the Qur'an reference for this passage right now. Your instructor will still review your submission.";
            case ANALYSIS_FAILED:
                return "Automatic analysis did not finish. Your instructor can still review your audio when ready.";
            case CANNOT_EVALUATE:
                return "This submission could not be evaluated automatically. Your instructor will review it manually.";
            default:
                return "Your recitation has been submitted. You'll see the score and feedback here once your instructor completes the evaluation.";
        }
    }
%>
<%
    String ctx = request.getContextPath();
    StudentRecitationResultPage resultPage = (StudentRecitationResultPage) request.getAttribute("resultPage");
    VerifiedRecitationView result = resultPage != null ? resultPage.getView()
            : (VerifiedRecitationView) request.getAttribute("result");
    if (result == null) {
        response.sendError(404);
        return;
    }
    if (resultPage == null) {
        resultPage = new StudentRecitationResultPage(result, "", null, result.isPublished() ? 4 : 2, List.of());
    }
    boolean published = result.isPublished();
    int journeyActive = resultPage.getJourneyActiveStep();
    String feedback = result.getFeedback() == null || result.getFeedback().isBlank() ? "" : result.getFeedback().trim();
    String portion = result.getPortion() == null ? "" : result.getPortion();
    String instructorName = result.getInstructorName() == null ? "" : result.getInstructorName();
    List<TrustedReference.Verse> displayVerses = resultPage.getDisplayVerses();
    List<VerifiedFocusItem> focusItems = published ? result.getFocusItems() : List.of();
    Set<String> focusVerseKeys = new HashSet<>();
    for (VerifiedFocusItem item : focusItems) {
        if (item.getVerseLabel() != null && item.getVerseLabel().matches("\\d{1,3}:\\d{1,3}")) {
            focusVerseKeys.add(item.getVerseLabel());
        }
    }
    StudentRecitationAnalysisPhase phase = resultPage.getAnalysisPhase();
    String storedReferenceText = resultPage.getStoredReferenceText();
    boolean showPassageFallback = displayVerses.isEmpty() && storedReferenceText != null && !storedReferenceText.isEmpty();
    boolean analyzing = !published && (phase == StudentRecitationAnalysisPhase.SUBMITTED
            || phase == StudentRecitationAnalysisPhase.ANALYSIS_IN_PROGRESS);
    boolean needsNewSubmission = !published && (phase == StudentRecitationAnalysisPhase.ANALYSIS_FAILED
            || phase == StudentRecitationAnalysisPhase.CANNOT_EVALUATE);
    String stateTone = analyzing ? "progress" : needsNewSubmission ? "bad" : "wait";
    Integer score = result.getScore();
    int scoreValue = score == null ? 0 : Math.max(0, Math.min(100, score));
    double ringCircumference = 2 * Math.PI * 52;
    String ringLength = String.format(java.util.Locale.ROOT, "%.2f", ringCircumference);
    String ringOffset = String.format(java.util.Locale.ROOT, "%.2f", ringCircumference * (100 - scoreValue) / 100.0);
    String scoreBand = scoreValue >= 80 ? "high" : scoreValue >= 60 ? "mid" : "low";
    String audioUrl = ctx + "/student/recitation-audio?id=" + result.getRecitationId();
    String practiceHref = ctx + "/student/recitations?practice=" + result.getRecitationId();
%>
<!DOCTYPE html>
<html lang="${empty currentLocale ? 'en' : currentLocale}" dir="${empty currentDir ? 'ltr' : currentDir}">
<head>
    <title data-i18n="student.recitations.resultTitle">Your recitation result - e-Tasmi</title>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <%@ include file="/jsp/common/student_ui_head.jspf" %>
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Noto+Naskh+Arabic:wght@500;600&display=swap">
    <link rel="stylesheet" href="<%= ctx %>/css/learning-loop-experience.css?v=20261003-ux2">
    <script defer src="<%= ctx %>/assets/js/ll-player.js?v=20261003-ux2"></script>
</head>
<body class="student-premium-page student-package-page student-module-page student-recitations-page lx-shell">
<div class="app-shell">
    <%@ include file="/jsp/common/student_header.jspf" %>
    <div class="app-main">
        <div class="container sd-container student-workspace-shell">
            <div class="student-shell-layout">
                <%@ include file="/jsp/student/student_sidebar.jspf" %>
                <main class="student-shell-content" role="main">
                    <div class="student-workspace-view lx lx-page">

                        <a class="lx-back" href="<%= ctx %>/student/recitations">
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M19 12H5M11 18l-6-6 6-6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                            <span data-i18n="student.recitations.backToRecitations">Back to recitations</span>
                        </a>

                        <section class="lx-card lx-hero" aria-labelledby="lx-report-title">
                            <div class="lx-head__top">
                                <div>
                                    <p class="lx-eyebrow" data-i18n="student.recitations.resultHeroEyebrow">Your recitation result</p>
                                    <h1 class="lx-title" id="lx-report-title"><%= esc(result.getSessionTitle()) %></h1>
                                </div>
                                <div class="lx-head__status">
                                    <% if (published) { %>
                                    <span class="lx-pill lx-pill--ok">
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 3l7 3v5.5c0 4.2-2.9 7.9-7 9.5-4.1-1.6-7-5.3-7-9.5V6z" stroke-linejoin="round"/><path d="M8.8 12.2l2.2 2.2 4.2-4.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                        <span data-i18n="student.recitations.instructorVerified">Instructor verified</span>
                                    </span>
                                    <% } else if (analyzing) { %>
                                    <span class="lx-pill lx-pill--ai lx-pill--dot" data-i18n="student.recitations.phaseAnalysisTitle">AI analysis in progress</span>
                                    <% } else { %>
                                    <span class="lx-pill lx-pill--wait lx-pill--dot" data-i18n="student.recitations.statusPending">Pending review</span>
                                    <% } %>
                                </div>
                            </div>

                            <ul class="lx-meta">
                                <% if (!portion.isEmpty()) { %>
                                <li>
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M12 6.5C10 5 7 4.5 4 5v13c3-.5 6 0 8 1.5 2-1.5 5-2 8-1.5V5c-3-.5-6 0-8 1.5zM12 6.5v13" stroke-linejoin="round"/></svg>
                                    <span class="lx-visually-hidden" data-i18n="student.recitations.infoPortion">Surah / portion</span>
                                    <strong><%= esc(portion) %></strong>
                                </li>
                                <% } %>
                                <% if (!resultPage.getSubmittedDateLabel().isEmpty()) { %>
                                <li>
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><rect x="3.5" y="5" width="17" height="15" rx="2"/><path d="M3.5 10h17M8 3v4M16 3v4" stroke-linecap="round"/></svg>
                                    <span class="lx-visually-hidden" data-i18n="student.recitations.infoSubmitted">Submitted</span>
                                    <span><%= esc(resultPage.getSubmittedDateLabel()) %></span>
                                </li>
                                <li>
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M4 12a8 8 0 0113.7-5.6M20 4v5h-5" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                    <span data-i18n="student.recitations.attemptLabel" data-i18n-n="<%= result.getAttemptNumber() %>">Attempt <%= result.getAttemptNumber() %></span>
                                </li>
                                <% } %>
                                <% if (!instructorName.isEmpty()) { %>
                                <li>
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0116 0" stroke-linecap="round"/></svg>
                                    <span class="lx-visually-hidden" data-i18n="student.recitations.infoInstructor">Instructor</span>
                                    <span><%= esc(instructorName) %></span>
                                </li>
                                <% } %>
                            </ul>

                            <% if (published) { %>
                            <ol class="lx-trackline" aria-label="Recitation progress">
                                <li class="is-done"><span class="lx-trackline__dot" aria-hidden="true"></span><span data-i18n="student.recitations.journeySubmitted">Submitted</span></li>
                                <li class="is-done"><span class="lx-trackline__dot" aria-hidden="true"></span><span data-i18n="student.recitations.journeyAnalysis">AI analysis</span></li>
                                <li class="is-done"><span class="lx-trackline__dot" aria-hidden="true"></span><span data-i18n="student.recitations.instructorVerified">Instructor verified</span></li>
                                <li class="is-next"><a href="<%= practiceHref %>"><span class="lx-trackline__dot" aria-hidden="true"></span><span data-i18n="student.recitations.practiceAgain">Practice Again</span></a></li>
                            </ol>
                            <% } %>

                            <div class="lx-player lx-player--wave" data-rr-player dir="ltr">
                                <audio data-rr-audio preload="metadata" src="<%= audioUrl %>"></audio>
                                <button type="button" class="lx-player__play" data-rr-play aria-label="Play your recording" data-label-play="Play your recording" data-label-pause="Pause">
                                    <svg class="lx-i-play" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5v14l11-7z"/></svg>
                                    <svg class="lx-i-pause" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M7 5h4v14H7zM13 5h4v14h-4z"/></svg>
                                </button>
                                <div class="lx-player__track">
                                    <canvas class="lx-player__wave" data-rr-wave aria-hidden="true"></canvas>
                                    <input class="lx-player__seek" data-rr-seek type="range" min="0" max="1000" value="0" aria-label="Seek">
                                </div>
                                <span class="lx-player__time" data-rr-time>0:00</span>
                                <button type="button" class="lx-player__mute" data-rr-mute aria-label="Mute" aria-pressed="false">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M4 10v4h4l5 4V6L8 10H4zM16 9.5a5 5 0 010 5" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                </button>
                                <button type="button" class="lx-player__rate" data-rr-rate aria-label="Playback speed">1x</button>
                            </div>
                        </section>

                        <% if (!published) { %>
                        <section class="lx-card lx-pending" aria-live="polite">
                            <ol class="lx-journey" aria-label="Recitation progress">
                                <li class="<%= journeyClass(1, journeyActive) %>"<%= journeyActive == 1 ? " aria-current=\"step\"" : "" %>><span class="lx-journey__dot"></span><span data-i18n="student.recitations.journeySubmitted">Submitted</span></li>
                                <li class="<%= journeyClass(2, journeyActive) %>"<%= journeyActive == 2 ? " aria-current=\"step\"" : "" %>><span class="lx-journey__dot"></span><span data-i18n="student.recitations.journeyAnalysis">AI analysis</span></li>
                                <li class="<%= journeyClass(3, journeyActive) %>"<%= journeyActive == 3 ? " aria-current=\"step\"" : "" %>><span class="lx-journey__dot"></span><span data-i18n="student.recitations.journeyInstructor">Instructor review</span></li>
                                <li class="<%= journeyClass(4, journeyActive) %>"<%= journeyActive == 4 ? " aria-current=\"step\"" : "" %>><span class="lx-journey__dot"></span><span data-i18n="student.recitations.journeyVerified">Verified result</span></li>
                            </ol>
                            <div class="lx-state lx-state--<%= stateTone %>">
                                <% if ("progress".equals(stateTone)) { %>
                                <span class="lx-processing__orb lx-processing__orb--sm" aria-hidden="true"><span></span><span></span><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7"><path d="M12 3.5l1.9 4.6 4.6 1.9-4.6 1.9L12 16.5l-1.9-4.6L5.5 10l4.6-1.9z" stroke-linejoin="round"/></svg></span>
                                <% } else if ("bad".equals(stateTone)) { %>
                                <span class="lx-card__icon lx-card__icon--gold" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><path d="M12 7.5v5.5M12 16.5h.01" stroke-linecap="round"/></svg></span>
                                <% } else { %>
                                <span class="lx-card__icon lx-card__icon--gold" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2" stroke-linecap="round" stroke-linejoin="round"/></svg></span>
                                <% } %>
                                <div>
                                    <h2 data-i18n="<%= esc(pendingPhaseTitleKey(phase)) %>"><%= esc(pendingPhaseTitleDefault(phase)) %></h2>
                                    <p data-i18n="<%= esc(pendingPhaseBodyKey(phase)) %>"><%= esc(pendingPhaseBodyDefault(phase)) %></p>
                                    <% if (needsNewSubmission) { %>
                                    <a class="lx-btn lx-btn--primary" href="<%= ctx %>/student/recitations" data-i18n="student.recitations.createNewSubmission">Create new submission</a>
                                    <% } %>
                                </div>
                            </div>
                        </section>
                        <% } else { %>

                        <div class="lx-row lx-row--pair">
                            <section class="lx-card lx-scorecard lx-scorecard--<%= scoreBand %>" aria-labelledby="lx-score-title">
                                <div class="lx-ring" role="img" aria-label="Score <%= scoreValue %> out of 100" data-i18n="student.recitations.scoreAria" data-i18n-attr="aria-label" data-i18n-n="<%= scoreValue %>"
                                     data-lx-ring data-score="<%= score == null ? "" : String.valueOf(scoreValue) %>">
                                    <svg viewBox="0 0 120 120" aria-hidden="true">
                                        <circle class="lx-ring__track" cx="60" cy="60" r="52" fill="none" stroke-width="9"/>
                                        <% if (score != null) { %>
                                        <circle class="lx-ring__value" cx="60" cy="60" r="52" fill="none" stroke-width="9"
                                                stroke-dasharray="<%= ringLength %>" stroke-dashoffset="<%= ringOffset %>"
                                                data-length="<%= ringLength %>" data-offset="<%= ringOffset %>"/>
                                        <% } %>
                                    </svg>
                                    <div class="lx-ring__label" aria-hidden="true">
                                        <strong data-lx-count><%= score == null ? "—" : String.valueOf(score) %></strong>
                                        <span>/ 100</span>
                                    </div>
                                </div>
                                <div class="lx-scorecard__body">
                                    <h2 class="lx-scorecard__label" id="lx-score-title" data-i18n="student.recitations.yourScore">Your score</h2>
                                    <% if (score != null) { %>
                                    <p class="lx-scorecard__band" data-i18n="student.recitations.scoreBand<%= "high".equals(scoreBand) ? "High" : "mid".equals(scoreBand) ? "Mid" : "Low" %>"><%= "high".equals(scoreBand) ? "Strong recitation" : "mid".equals(scoreBand) ? "Good progress" : "Keep practising" %></p>
                                    <% } %>
                                    <span class="lx-verified">
                                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M12 3l7 3v5.5c0 4.2-2.9 7.9-7 9.5-4.1-1.6-7-5.3-7-9.5V6z" stroke-linejoin="round"/><path d="M8.8 12.2l2.2 2.2 4.2-4.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                        <% if (!instructorName.isEmpty()) { %>
                                        <span data-i18n="student.recitations.scoreVerifiedBy" data-i18n-name="<%= esc(instructorName) %>">Verified by <%= esc(instructorName) %></span>
                                        <% } else { %>
                                        <span data-i18n="student.recitations.verifiedByInstructor">Verified by instructor</span>
                                        <% } %>
                                    </span>
                                </div>
                            </section>

                            <section class="lx-card lx-feedback" aria-labelledby="lx-feedback-title">
                                <div class="lx-card__head">
                                    <span class="lx-card__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 5h16v11H9l-5 4V5z" stroke-linejoin="round"/><path d="M8.5 9.5h7M8.5 12.5h4.5" stroke-linecap="round"/></svg></span>
                                    <div><h2 class="lx-card__title" id="lx-feedback-title" data-i18n="student.recitations.instructorFeedback">Instructor feedback</h2></div>
                                </div>
                                <% if (feedback.isEmpty()) { %>
                                <div class="lx-feedback__empty">
                                    <p class="lx-feedback__emptytitle" data-i18n="student.recitations.feedbackEmptyTitle">No written note this time</p>
                                    <p data-i18n="student.recitations.feedbackEmptySub">Your score and learning focus were still verified by your instructor.</p>
                                </div>
                                <% } else { %>
                                <blockquote class="lx-quote">
                                    <p><%= esc(feedback) %></p>
                                    <% if (!instructorName.isEmpty()) { %><footer>— <%= esc(instructorName) %></footer><% } %>
                                </blockquote>
                                <% } %>
                            </section>
                        </div>

                        <div class="lx-row lx-row--focus">
                            <section class="lx-card" aria-labelledby="lx-focus-title">
                                <div class="lx-card__head">
                                    <span class="lx-card__icon lx-card__icon--gold" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="4.5"/><circle cx="12" cy="12" r="1" fill="currentColor"/></svg></span>
                                    <div>
                                        <h2 class="lx-card__title" id="lx-focus-title" data-i18n="student.recitations.focusTitle">Your verified learning focus</h2>
                                        <p class="lx-card__sub" data-i18n="student.recitations.focusSub">Points your instructor confirmed for your next attempt.</p>
                                    </div>
                                    <% if (focusItems.isEmpty()) { %>
                                    <span class="lx-pill lx-pill--ok lx-pill--sm" data-i18n="student.recitations.focusCountNone">Nothing extra to practise</span>
                                    <% } else if (focusItems.size() == 1) { %>
                                    <span class="lx-pill lx-pill--gold lx-pill--sm" data-i18n="student.recitations.focusCountOne">1 point to practise</span>
                                    <% } else { %>
                                    <span class="lx-pill lx-pill--gold lx-pill--sm" data-i18n="student.recitations.focusCountMany" data-i18n-count="<%= focusItems.size() %>"><%= focusItems.size() %> points to practise</span>
                                    <% } %>
                                </div>
                                <% if (focusItems.isEmpty()) { %>
                                <p class="lx-empty lx-empty--ok" data-i18n="student.recitations.focusEmpty">This recitation is complete. There is nothing extra to practise from this review.</p>
                                <% } else { %>
                                <ol class="lx-focus">
                                    <% for (VerifiedFocusItem item : focusItems) {
                                           String key = item.getTypeKey() == null ? "instructorNote" : item.getTypeKey();
                                           String action = item.getGuidance() != null && !item.getGuidance().isBlank()
                                                   ? item.getGuidance()
                                                   : practiceAction(key);
                                           int ayah = ayahFromVerseKey(item.getVerseLabel());
                                    %>
                                    <li class="lx-focus__item lx-focus__item--<%= esc(key) %>">
                                        <span class="lx-focus__icon"><%= focusIcon(key) %></span>
                                        <div class="lx-focus__body">
                                            <p class="lx-focus__title">
                                                <span data-i18n="student.recitations.focus.<%= esc(key) %>"><%= esc(typeLabel(key)) %></span>
                                                <% if (ayah > 0) { %>
                                                <span class="lx-focus__ayah" data-i18n="student.recitations.ayahLabel" data-i18n-n="<%= ayah %>">Ayah <%= ayah %></span>
                                                <% } %>
                                            </p>
                                            <p class="lx-focus__action"><%= esc(action) %></p>
                                            <% if (item.getExpectedPhrase() != null && !item.getExpectedPhrase().isBlank()) { %>
                                            <p class="lx-arabic lx-focus__phrase" lang="ar"><%= esc(item.getExpectedPhrase()) %></p>
                                            <% } %>
                                            <% if (item.getHint() != null && !item.getHint().isBlank()) { %>
                                            <p class="lx-focus__hint"><b data-i18n="student.recitations.hintLabel">Instructor note</b>: <%= esc(item.getHint()) %></p>
                                            <% } %>
                                        </div>
                                    </li>
                                    <% } %>
                                </ol>
                                <% } %>
                            </section>

                            <section class="lx-card lx-next" aria-labelledby="lx-next-title">
                                <span class="lx-next__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M4 12a8 8 0 0113.7-5.6M20 4v5h-5M20 12a8 8 0 01-13.7 5.6M4 20v-5h5" stroke-linecap="round" stroke-linejoin="round"/></svg></span>
                                <h2 class="lx-next__title" id="lx-next-title" data-i18n="student.recitations.continueTitle">Continue your learning</h2>
                                <p class="lx-next__sub" data-i18n="student.recitations.practiceBannerTitle">Practice the same passage using your verified learning focus.</p>
                                <a class="lx-btn lx-btn--light lx-btn--lg lx-btn--block" href="<%= practiceHref %>">
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M4 12a8 8 0 0113.7-5.6M20 4v5h-5" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                    <span data-i18n="student.recitations.practiceAgain">Practice Again</span>
                                </a>
                                <a class="lx-link-row" href="<%= ctx %>/student/progress">
                                    <div>
                                        <strong data-i18n="student.recitations.trackProgress">Track your progress</strong>
                                        <span data-i18n="student.recitations.trackProgressSub">See how your recitations improve over time.</span>
                                    </div>
                                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M9 6l6 6-6 6" stroke-linecap="round" stroke-linejoin="round"/></svg>
                                </a>
                            </section>
                        </div>
                        <% } %>

                        <% if (!displayVerses.isEmpty() || showPassageFallback) { %>
                        <section class="lx-card" aria-labelledby="lx-passage-title">
                            <div class="lx-card__head">
                                <span class="lx-card__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M12 6.5C10 5 7 4.5 4 5v13c3-.5 6 0 8 1.5 2-1.5 5-2 8-1.5V5c-3-.5-6 0-8 1.5zM12 6.5v13" stroke-linejoin="round"/></svg></span>
                                <div>
                                    <h2 class="lx-card__title" id="lx-passage-title" data-i18n="student.recitations.passageSectionTitle">Relevant Qur'an passage</h2>
                                    <% if (!focusVerseKeys.isEmpty() && !displayVerses.isEmpty()) { %>
                                    <p class="lx-card__sub"><span class="lx-legend" data-i18n="student.recitations.passageFocusLegend">Highlighted ayahs are in your learning focus.</span></p>
                                    <% } %>
                                </div>
                            </div>
                            <div class="lx-passage">
                                <% if (!displayVerses.isEmpty()) { %>
                                <p class="lx-arabic lx-quran" lang="ar">
                                    <% for (TrustedReference.Verse verse : displayVerses) {
                                           int ayah = ayahFromVerseKey(verse.getVerseKey());
                                           boolean focus = focusVerseKeys.contains(verse.getVerseKey());
                                    %><span class="lx-ayah<%= focus ? " lx-ayah--focus" : "" %>"><%= esc(verse.getUthmaniText()) %><% if (ayah > 0) { %><span class="lx-ayah__n" dir="ltr"><%= ayah %></span><% } %></span> <% } %>
                                </p>
                                <% } else { %>
                                <p class="lx-arabic lx-quran" lang="ar"><%= esc(storedReferenceText) %></p>
                                <% } %>
                            </div>
                        </section>
                        <% } %>

                    </div>
                    <%@ include file="/jsp/common/app_footer.jspf" %>
                </main>
            </div>
        </div>
    </div>
</div>
<% if (published && score != null) { %>
<script>
(function () {
  var ring = document.querySelector('[data-lx-ring]');
  if (!ring || !window.requestAnimationFrame) return;
  if (window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  var value = ring.querySelector('.lx-ring__value');
  var count = ring.querySelector('[data-lx-count]');
  var target = Number(ring.getAttribute('data-score')) || 0;
  var length = Number(value.getAttribute('data-length'));
  var finalOffset = Number(value.getAttribute('data-offset'));
  value.style.strokeDashoffset = String(length);
  count.textContent = '0';
  function run() {
    var start = null, duration = 1100;
    function frame(ts) {
      if (start === null) start = ts;
      var p = Math.min(1, (ts - start) / duration);
      var eased = 1 - Math.pow(1 - p, 3);
      value.style.strokeDashoffset = String(length - (length - finalOffset) * eased);
      count.textContent = String(Math.round(target * eased));
      if (p < 1) requestAnimationFrame(frame);
    }
    requestAnimationFrame(frame);
  }
  if ('IntersectionObserver' in window) {
    var io = new IntersectionObserver(function (entries) {
      if (entries[0].isIntersecting) { io.disconnect(); run(); }
    }, { threshold: 0.4 });
    io.observe(ring);
  } else {
    run();
  }
})();
</script>
<% } %>
<% if (analyzing) { %>
<script>
(function () {
  var tries = 0;
  var maxTries = 10;
  var timer = window.setInterval(function () {
    tries += 1;
    if (tries > maxTries) {
      window.clearInterval(timer);
      return;
    }
    var audio = document.querySelector('[data-rr-audio]');
    if (audio && !audio.paused) return;
    window.location.reload();
  }, 8000);
})();
</script>
<% } %>
</body>
</html>
