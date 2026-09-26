package controller.student;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import model.service.AttendanceService;
import model.service.LiveSessionAccess;
import model.service.LiveSessionAccessService;
import util.SessionUtil;
import util.ZoomWebEmbedSupport;

/**
 * Student live join: validates access, then prefers the in-browser embedded Zoom Meeting SDK
 * ({@code /student/live/embed}) when available; otherwise redirects to the stored participant URL.
 */
@WebServlet(name = "StudentJoinLiveServlet", urlPatterns = {"/student/joinLive"})
public class StudentJoinLiveServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(StudentJoinLiveServlet.class.getName());

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

            String participantJoinUrl = access.getFallbackUrl();
            if (participantJoinUrl == null || participantJoinUrl.isBlank()) {
                response.sendRedirect(request.getContextPath() + "/student/enrollments?joinError=join_url_missing");
                return;
            }

            boolean forceExternal = "1".equalsIgnoreCase(trim(request.getParameter("external")));
            boolean forceEmbed = "1".equalsIgnoreCase(trim(request.getParameter("embed")));
            boolean useEmbed = !forceExternal
                    && liveSessionAccessService.canEmbed(access.getTasmiSession())
                    && (forceEmbed || ZoomWebEmbedSupport.requestAllowsInBrowserSdk(request));
            if (useEmbed) {
                response.sendRedirect(request.getContextPath() + "/student/live/embed?sessionId=" + sessionId);
                return;
            }

            attendanceService.markStudentJoined(userId, sessionId);
            response.sendRedirect(participantJoinUrl);
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to join live session", ex);
            response.sendRedirect(request.getContextPath() + "/student/enrollments?joinError=server_error");
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private String errorCode(String error) {
        if (error == null || error.isBlank()) {
            return "access_denied";
        }
        String normalized = error.toLowerCase().replaceAll("[^a-z0-9]+", "_");
        return normalized.isBlank() ? "access_denied" : normalized;
    }
}
