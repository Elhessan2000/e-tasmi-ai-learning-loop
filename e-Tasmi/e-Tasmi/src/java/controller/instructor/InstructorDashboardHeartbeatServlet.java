package controller.instructor;

import model.entity.InstructorDashboardStats;
import model.entity.TasmiSession;
import model.entity.TasmiSessionStatus;
import model.service.InstructorWorkspaceService;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Lightweight 30-second polling endpoint for the instructor cockpit.
 *
 * Returns just enough to (a) flip the countdown into "Live" without a page
 * reload and (b) keep the KPI tiles fresh.
 *
 * Response shape:
 * {
 *   "serverNowEpochMs": ...,
 *   "nextSession": { "id":..., "status":"SCHEDULED|ONGOING",
 *                    "startEpochMs":..., "endEpochMs":..., "title":..., "portion":... } | null,
 *   "kpi": {
 *     "todaySessions":..., "upcomingSessions":..., "ongoingSessions":...,
 *     "pendingEvaluations":..., "activeStudents":...
 *   }
 * }
 */
@WebServlet(name = "InstructorDashboardHeartbeatServlet",
        urlPatterns = {"/instructor/dashboard/heartbeat.json"})
public class InstructorDashboardHeartbeatServlet extends HttpServlet {

    private final InstructorWorkspaceService workspaceService = new InstructorWorkspaceService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        Long userId = currentUserId(request);
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"unauthorized\"}");
            return;
        }

        InstructorDashboardStats stats = workspaceService.buildDashboard(userId);
        ZoneId zone = util.DateTimeFormats.appZone();

        StringBuilder out = new StringBuilder(256);
        out.append('{');
        out.append("\"serverNowEpochMs\":").append(Instant.now().toEpochMilli()).append(',');

        out.append("\"nextSession\":");
        TasmiSession next = stats.getNextSession();
        if (next == null) {
            out.append("null");
        } else {
            long startMs = -1;
            long endMs = -1;
            if (next.getSessionDate() != null && next.getSessionTime() != null) {
                LocalDateTime ldt = LocalDateTime.of(next.getSessionDate(), next.getSessionTime());
                startMs = ldt.atZone(zone).toInstant().toEpochMilli();
                int durationMinutes = next.getDurationMinutes() == null ? 60 : next.getDurationMinutes();
                endMs = startMs + (long) durationMinutes * 60_000L;
            }
            TasmiSessionStatus status = next.getStatus();
            out.append('{');
            out.append("\"id\":").append(next.getSessionId()).append(',');
            out.append("\"status\":").append(jsonString(status == null ? "SCHEDULED" : status.name())).append(',');
            out.append("\"title\":").append(jsonString(next.getTitle())).append(',');
            out.append("\"portion\":").append(jsonString(next.getQuranPortion())).append(',');
            out.append("\"durationMinutes\":").append(next.getDurationMinutes() == null ? 60 : next.getDurationMinutes()).append(',');
            out.append("\"startEpochMs\":").append(startMs).append(',');
            out.append("\"endEpochMs\":").append(endMs);
            out.append('}');
        }

        out.append(',');
        out.append("\"kpi\":{");
        out.append("\"todaySessions\":").append(stats.getTodaySessions().size()).append(',');
        out.append("\"upcomingSessions\":").append(stats.getUpcomingSessions()).append(',');
        out.append("\"ongoingSessions\":").append(stats.getOngoingSessions()).append(',');
        out.append("\"pendingEvaluations\":").append(stats.getPendingEvaluationCount()).append(',');
        out.append("\"activeStudents\":").append(stats.getEnrolledStudentCount());
        out.append('}');
        out.append('}');

        try (PrintWriter writer = response.getWriter()) {
            writer.write(out.toString());
        }
    }

    private static Long currentUserId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        Object raw = session.getAttribute("userId");
        if (raw instanceof Long) return (Long) raw;
        if (raw instanceof Integer) return ((Integer) raw).longValue();
        return null;
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
