package controller.instructor;

import model.dao.InstructorDao;
import model.dao.impl.InstructorDaoJdbc;
import model.entity.Instructor;
import model.entity.TasmiSession;
import model.service.LiveSessionAccess;
import model.service.LiveSessionAccessService;
import util.Db;
import util.JsonUtil;
import util.SessionUtil;
import util.ZoomApiClient;
import util.ZoomJoinLinkUtil;
import util.ZoomMeetingSdkConfig;
import util.ZoomMeetingSdkJwt;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Returns a host (role 1) Meeting SDK JWT for the session's own instructor, plus a best-effort
 * ZAK token so the Web SDK can <em>start</em> the meeting in the browser. Never exposes secrets.
 */
@WebServlet(name = "InstructorZoomMeetingSignatureServlet", urlPatterns = {"/instructor/api/zoomMeetingSignature"})
public class InstructorZoomMeetingSignatureServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(InstructorZoomMeetingSignatureServlet.class.getName());

    private final LiveSessionAccessService liveSessionAccessService = new LiveSessionAccessService();
    private final InstructorDao instructorDao = new InstructorDaoJdbc();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        serve(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        serve(request, response);
    }

    private void serve(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        HttpSession session = request.getSession(false);
        long userId = SessionUtil.readUserId(session);

        long sessionId = 0;
        try {
            sessionId = Long.parseLong(request.getParameter("sessionId"));
        } catch (Exception ignored) {
        }

        if (userId <= 0) {
            writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                    JsonUtil.obj(errorBody("unauthenticated", "Login required.")));
            return;
        }
        if (sessionId <= 0) {
            writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                    JsonUtil.obj(errorBody("invalid_request", "Missing or invalid session id.")));
            return;
        }

        if (!ZoomMeetingSdkConfig.isEmbeddingAvailable()) {
            writeJson(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    JsonUtil.obj(errorBody("sdk_not_configured", "Embedded meetings are not available.")));
            return;
        }

        try {
            LiveSessionAccess access = liveSessionAccessService.validateInstructorHost(userId, sessionId);
            if (!access.isAllowed()) {
                writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                        JsonUtil.obj(errorBody("access_denied", access.getError())));
                return;
            }

            if (!liveSessionAccessService.canEmbed(access.getTasmiSession())) {
                writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                        JsonUtil.obj(errorBody("embed_unavailable", "This session cannot be opened in the embedded viewer.")));
                return;
            }

            TasmiSession tasmiSession = access.getTasmiSession();
            Long zoomId = tasmiSession.getZoomMeetingId();
            if (zoomId == null || zoomId <= 0) {
                writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                        JsonUtil.obj(errorBody("meeting_id_missing", "Meeting is not ready.")));
                return;
            }

            String sdkKey = ZoomMeetingSdkConfig.getSdkKey();
            String sdkSecret = ZoomMeetingSdkConfig.getSdkSecret();
            String meetingNumber = String.valueOf(zoomId);

            String pass = tasmiSession.getMeetingPassword();
            if (pass == null) {
                pass = "";
            }
            pass = pass.trim();
            if (pass.isBlank()) {
                pass = ZoomJoinLinkUtil.extractPwdFromJoinUrl(tasmiSession.getMeetingLink()).trim();
            }

            final String signature;
            try {
                signature = ZoomMeetingSdkJwt.sign(sdkKey, sdkSecret, meetingNumber, 1);
            } catch (IllegalArgumentException ex) {
                LOGGER.log(Level.WARNING, "Invalid Zoom SDK host signature request", ex);
                writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                        JsonUtil.obj(errorBody("invalid_request", ex.getMessage())));
                return;
            }

            String zak = fetchZakBestEffort(userId);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", Boolean.TRUE);
            body.put("signature", signature);
            body.put("sdkKey", sdkKey);
            body.put("meetingNumber", meetingNumber);
            body.put("passWord", pass);
            body.put("userName", access.getDisplayName());
            body.put("jwtRole", 1);
            body.put("signedWith", "meeting_sdk_secret");
            if (zak != null && !zak.isBlank()) {
                body.put("zak", zak);
            }

            writeJson(response, HttpServletResponse.SC_OK, JsonUtil.obj(body));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            LOGGER.log(Level.SEVERE, "Failed to sign host Meeting SDK JWT", ex);
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    JsonUtil.obj(errorBody("signature_failed", "Could not authorize the meeting.")));
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Database error in instructor Zoom signature servlet", ex);
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    JsonUtil.obj(errorBody("server_error", "Server error.")));
        }
    }

    /**
     * Resolves the Zoom host account the meeting was created under (instructor's zoom_email,
     * else the platform default host) and fetches its ZAK. Failure is non-fatal: without a ZAK
     * the host can still join a meeting that is already running, or use the external start_url.
     */
    private String fetchZakBestEffort(long instructorUserId) {
        if (!ZoomApiClient.isConfigured()) {
            return null;
        }
        try {
            String hostEmail = null;
            try (Connection connection = Db.getConnection()) {
                Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, instructorUserId);
                if (instructorOpt.isPresent()) {
                    String zoomEmail = instructorOpt.get().getZoomEmail();
                    if (zoomEmail != null && !zoomEmail.isBlank()) {
                        hostEmail = zoomEmail.trim();
                    }
                }
            }
            if (hostEmail == null || hostEmail.isBlank()) {
                hostEmail = ZoomApiClient.getDefaultHostEmail();
            }
            if (hostEmail == null || hostEmail.isBlank()) {
                return null;
            }
            return new ZoomApiClient().getUserZakToken(hostEmail);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Could not fetch host ZAK token (host can still join a running meeting)", ex);
            return null;
        }
    }

    private static Map<String, Object> errorBody(String code, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", Boolean.FALSE);
        m.put("error", code);
        m.put("message", message == null ? "" : message);
        return m;
    }

    private void writeJson(HttpServletResponse response, int status, String json) throws IOException {
        response.setStatus(status);
        PrintWriter w = response.getWriter();
        w.write(json);
    }
}
