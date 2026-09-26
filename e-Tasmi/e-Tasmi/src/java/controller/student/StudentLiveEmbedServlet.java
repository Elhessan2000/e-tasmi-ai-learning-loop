package controller.student;

import model.entity.TasmiSession;
import model.service.AttendanceService;
import model.service.LiveSessionAccess;
import model.service.LiveSessionAccessService;
import util.JsonUtil;
import util.SessionUtil;
import util.ZoomMeetingSdkConfig;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Full-page embedded Zoom Meeting SDK (student participant only).
 */
@WebServlet(name = "StudentLiveEmbedServlet", urlPatterns = {"/student/live/embed"})
public class StudentLiveEmbedServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(StudentLiveEmbedServlet.class.getName());

    private final LiveSessionAccessService liveSessionAccessService = new LiveSessionAccessService();
    private final AttendanceService attendanceService = new AttendanceService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = SessionUtil.readUserId(session);

        long sessionId = 0;
        try {
            sessionId = Long.parseLong(request.getParameter("sessionId"));
        } catch (Exception ignored) {
        }

        if (userId <= 0 || sessionId <= 0) {
            response.sendRedirect(request.getContextPath() + "/student/enrollments?joinError=invalid_request");
            return;
        }

        try {
            LiveSessionAccess access = liveSessionAccessService.validateStudentJoin(userId, sessionId);
            if (!access.isAllowed()) {
                response.sendRedirect(request.getContextPath() + "/student/enrollments?joinError=" + errorCode(access.getError()));
                return;
            }

            TasmiSession tasmiSession = access.getTasmiSession();
            if (!liveSessionAccessService.canEmbed(tasmiSession)) {
                String fallback = access.getFallbackUrl();
                if (fallback != null && !fallback.isBlank()) {
                    attendanceService.markStudentJoined(userId, sessionId);
                    response.sendRedirect(fallback);
                    return;
                }
                response.sendRedirect(request.getContextPath() + "/student/enrollments?joinError=embed_unavailable");
                return;
            }

            Long zoomId = tasmiSession.getZoomMeetingId();
            if (zoomId == null || zoomId <= 0) {
                response.sendRedirect(request.getContextPath() + "/student/enrollments?joinError=meeting_id_missing");
                return;
            }

            attendanceService.markStudentJoined(userId, sessionId);

            String rawTitle = tasmiSession.getTitle();
            String pageTitle = rawTitle == null || rawTitle.isBlank() ? "Live session" : rawTitle;
            pageTitle = pageTitle.replace("<", " ").replace(">", " ");
            request.setAttribute("zoomPageTitle", pageTitle);

            Map<String, Object> clientCfg = new LinkedHashMap<>();
            clientCfg.put("contextPath", request.getContextPath());
            clientCfg.put("sessionId", sessionId);
            clientCfg.put("leavePath", "/student/enrollments");
            clientCfg.put("leaveUrl", absoluteUrl(request, "/student/enrollments"));
            clientCfg.put("fallbackUrl", access.getFallbackUrl());
            String webSdkVer = ZoomMeetingSdkConfig.getWebSdkVersion();
            clientCfg.put("sdkVersion", webSdkVer);
            request.setAttribute("zoomWebSdkVersion", webSdkVer);
            clientCfg.put("signatureUrl", request.getContextPath() + "/student/api/zoomMeetingSignature?sessionId=" + sessionId);
            String dn = access.getDisplayName() == null ? "Student" : access.getDisplayName().replace('<', ' ').replace('>', ' ');
            clientCfg.put("defaultUserName", dn);
            clientCfg.put("zoomWebDebug", ZoomMeetingSdkConfig.isWebClientDebug());
            request.setAttribute("zoomClientConfigJson", JsonUtil.obj(clientCfg));

            request.getRequestDispatcher("/jsp/student/live-embed.jsp").forward(request, response);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load live embed page", ex);
            response.sendRedirect(request.getContextPath() + "/student/enrollments?joinError=server_error");
        }
    }

    private static String absoluteUrl(HttpServletRequest request, String pathWithinContext) {
        String ctx = request.getContextPath() == null ? "" : request.getContextPath();
        String suffix = pathWithinContext.startsWith("/") ? pathWithinContext : ("/" + pathWithinContext);

        String scheme = request.getHeader("X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        } else {
            scheme = scheme.split(",")[0].trim();
        }
        String host = request.getHeader("X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            host = request.getServerName();
        } else {
            host = host.split(",")[0].trim();
        }

        int port = request.getServerPort();
        String forwardedPort = request.getHeader("X-Forwarded-Port");
        if (forwardedPort != null && !forwardedPort.isBlank()) {
            try {
                port = Integer.parseInt(forwardedPort.split(",")[0].trim());
            } catch (NumberFormatException ignored) {
            }
        }

        boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                || ("https".equalsIgnoreCase(scheme) && port == 443);
        String portPart = defaultPort ? "" : (":" + port);

        return scheme + "://" + host + portPart + ctx + suffix;
    }

    private String errorCode(String error) {
        if (error == null || error.isBlank()) {
            return "access_denied";
        }
        String normalized = error.toLowerCase().replaceAll("[^a-z0-9]+", "_");
        return normalized.isBlank() ? "access_denied" : normalized;
    }
}
