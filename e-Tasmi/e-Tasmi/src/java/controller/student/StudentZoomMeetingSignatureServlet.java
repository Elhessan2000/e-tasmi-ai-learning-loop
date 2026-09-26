package controller.student;

import model.entity.TasmiSession;
import model.service.LiveSessionAccess;
import model.service.LiveSessionAccessService;
import util.JsonUtil;
import util.SessionUtil;
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
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Returns a Meeting SDK JWT (signature) for authenticated students only. Never exposes the SDK secret.
 * The JWT is signed with the Meeting SDK app's Client Secret ({@code ZOOM_MEETING_SDK_SECRET}),
 * not the Server-to-Server OAuth secret.
 */
@WebServlet(name = "StudentZoomMeetingSignatureServlet", urlPatterns = {"/student/api/zoomMeetingSignature"})
public class StudentZoomMeetingSignatureServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(StudentZoomMeetingSignatureServlet.class.getName());

    private final LiveSessionAccessService liveSessionAccessService = new LiveSessionAccessService();

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
            LiveSessionAccess access = liveSessionAccessService.validateStudentJoin(userId, sessionId);
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
            if (pass.isBlank() && ZoomJoinLinkUtil.joinUrlQueryImpliesPasscode(tasmiSession.getMeetingLink())) {
                writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                        JsonUtil.obj(errorBody("passcode_missing", "Meeting passcode is not available. Open in Zoom instead.")));
                return;
            }

            final String signature;
            try {
                signature = ZoomMeetingSdkJwt.sign(sdkKey, sdkSecret, meetingNumber, 0);
            } catch (IllegalArgumentException ex) {
                LOGGER.log(Level.WARNING, "Invalid Zoom SDK signature request", ex);
                writeJson(response, HttpServletResponse.SC_BAD_REQUEST,
                        JsonUtil.obj(errorBody("invalid_request", ex.getMessage())));
                return;
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", Boolean.TRUE);
            body.put("signature", signature);
            body.put("sdkKey", sdkKey);
            body.put("meetingNumber", meetingNumber);
            body.put("passWord", pass);
            body.put("userName", access.getDisplayName());
            body.put("jwtRole", 0);
            body.put("signedWith", "meeting_sdk_secret");

            writeJson(response, HttpServletResponse.SC_OK, JsonUtil.obj(body));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            LOGGER.log(Level.SEVERE, "Failed to sign Meeting SDK JWT", ex);
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    JsonUtil.obj(errorBody("signature_failed", "Could not authorize the meeting.")));
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Database error in Zoom signature servlet", ex);
            writeJson(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    JsonUtil.obj(errorBody("server_error", "Server error.")));
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
