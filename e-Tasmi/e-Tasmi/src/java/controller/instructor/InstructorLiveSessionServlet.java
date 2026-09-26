package controller.instructor;

import model.entity.TasmiSession;
import model.service.LiveSessionAccess;
import model.service.LiveSessionAccessService;
import util.JsonUtil;
import util.ZoomMeetingSdkConfig;
import util.ZoomWebEmbedSupport;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Instructor live hosting: prefers the embedded in-browser Zoom Meeting SDK (host role) when
 * available; otherwise redirects to the Zoom {@code start_url}.
 */
@WebServlet(name = "InstructorLiveSessionServlet", urlPatterns = {"/instructor/live-session"})
public class InstructorLiveSessionServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(InstructorLiveSessionServlet.class.getName());

    private final LiveSessionAccessService liveSessionAccessService = new LiveSessionAccessService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        long userId = readUserId(session);
        long sessionId = parseLong(request.getParameter("sessionId"));

        if (userId <= 0 || sessionId <= 0) {
            response.sendRedirect(request.getContextPath() + "/instructor/sessions?errorMessage=Invalid live session request.");
            return;
        }

        try {
            LiveSessionAccess access = liveSessionAccessService.validateInstructorHost(userId, sessionId);
            if (!access.isAllowed()) {
                response.sendRedirect(request.getContextPath() + "/instructor/sessions?errorMessage=" + encode(access.getError()));
                return;
            }

            String fallbackUrl = access.getFallbackUrl();
            if (fallbackUrl == null || fallbackUrl.isBlank()) {
                response.sendRedirect(request.getContextPath() + "/instructor/sessions?errorMessage=Live meeting details are not ready.");
                return;
            }

            boolean forceExternal = "1".equalsIgnoreCase(trim(request.getParameter("external")));
            boolean forceEmbed = "1".equalsIgnoreCase(trim(request.getParameter("embed")));
            boolean useEmbed = !forceExternal
                    && liveSessionAccessService.canEmbed(access.getTasmiSession())
                    && (forceEmbed || ZoomWebEmbedSupport.requestAllowsInBrowserSdk(request));
            if (useEmbed) {
                forwardToEmbed(request, response, access, sessionId);
                return;
            }

            response.sendRedirect(fallbackUrl);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to open instructor live session", ex);
            response.sendRedirect(request.getContextPath() + "/instructor/sessions?errorMessage=Failed to load live session.");
        }
    }

    private void forwardToEmbed(HttpServletRequest request, HttpServletResponse response,
                                LiveSessionAccess access, long sessionId) throws ServletException, IOException {
        TasmiSession tasmiSession = access.getTasmiSession();

        String rawTitle = tasmiSession.getTitle();
        String pageTitle = rawTitle == null || rawTitle.isBlank() ? "Live session" : rawTitle;
        pageTitle = pageTitle.replace("<", " ").replace(">", " ");
        request.setAttribute("zoomPageTitle", pageTitle);

        Map<String, Object> clientCfg = new LinkedHashMap<>();
        clientCfg.put("contextPath", request.getContextPath());
        clientCfg.put("sessionId", sessionId);
        clientCfg.put("leavePath", "/instructor/sessions");
        clientCfg.put("leaveUrl", absoluteUrl(request, "/instructor/sessions"));
        clientCfg.put("fallbackUrl", access.getFallbackUrl());
        String webSdkVer = ZoomMeetingSdkConfig.getWebSdkVersion();
        clientCfg.put("sdkVersion", webSdkVer);
        request.setAttribute("zoomWebSdkVersion", webSdkVer);
        clientCfg.put("signatureUrl", request.getContextPath() + "/instructor/api/zoomMeetingSignature?sessionId=" + sessionId);
        String dn = access.getDisplayName() == null ? "Instructor" : access.getDisplayName().replace('<', ' ').replace('>', ' ');
        clientCfg.put("defaultUserName", dn);
        clientCfg.put("zoomWebDebug", ZoomMeetingSdkConfig.isWebClientDebug());
        request.setAttribute("zoomClientConfigJson", JsonUtil.obj(clientCfg));

        request.getRequestDispatcher("/jsp/instructor/live-embed.jsp").forward(request, response);
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

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private String encode(String value) {
        if (value == null) {
            return "Access denied.";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }

    private long readUserId(HttpSession session) {
        if (session == null) {
            return 0;
        }
        Object userIdObj = session.getAttribute("userId");
        if (userIdObj instanceof Long) {
            return (Long) userIdObj;
        }
        if (userIdObj instanceof Integer) {
            return ((Integer) userIdObj).longValue();
        }
        return 0;
    }
}
