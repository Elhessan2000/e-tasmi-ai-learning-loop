package controller.instructor;

import model.dao.InstructorDao;
import model.dao.impl.InstructorDaoJdbc;
import model.entity.Instructor;
import model.entity.TasmiSession;
import model.entity.TasmiSessionStatus;
import model.service.TasmiSessionService;
import util.Db;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Range-based JSON feed for the instructor dashboard's smart calendar.
 *
 * GET /instructor/dashboard/calendar.json?from=YYYY-MM-DD&to=YYYY-MM-DD
 *
 * Returns: {
 *   "serverNowEpochMs": ...,
 *   "from": "...", "to": "...",
 *   "sessions": [
 *     { "id":..., "title":..., "portion":..., "level":...,
 *       "date":"YYYY-MM-DD", "time":"HH:mm",
 *       "durationMinutes":..., "status":"SCHEDULED|ONGOING|COMPLETED|CANCELLED",
 *       "startEpochMs":..., "endEpochMs":...,
 *       "capacity":..., "manageHref":"..." }
 *   ]
 * }
 */
@WebServlet(name = "InstructorDashboardCalendarFeedServlet",
        urlPatterns = {"/instructor/dashboard/calendar.json"})
public class InstructorDashboardCalendarFeedServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(InstructorDashboardCalendarFeedServlet.class.getName());

    private final InstructorDao instructorDao = new InstructorDaoJdbc();
    private final TasmiSessionService tasmiSessionService = new TasmiSessionService();

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

        ZoneId zone = util.DateTimeFormats.appZone();
        LocalDate today = LocalDate.now(zone);
        LocalDate from = parseDate(request.getParameter("from"), today.minusDays(7));
        LocalDate to = parseDate(request.getParameter("to"), today.plusDays(21));
        if (to.isBefore(from)) {
            LocalDate swap = from;
            from = to;
            to = swap;
        }

        try (Connection connection = Db.getConnection()) {
            Optional<Instructor> instructorOpt = instructorDao.findByUserId(connection, userId);
            if (instructorOpt.isEmpty()) {
                response.getWriter().write("{\"sessions\":[],\"serverNowEpochMs\":" + Instant.now().toEpochMilli() + "}");
                return;
            }

            List<TasmiSession> sessions = tasmiSessionService.listInstructorSessions(instructorOpt.get().getInstructorId());
            StringBuilder out = new StringBuilder();
            out.append('{');
            out.append("\"serverNowEpochMs\":").append(Instant.now().toEpochMilli()).append(',');
            out.append("\"from\":\"").append(from).append("\",");
            out.append("\"to\":\"").append(to).append("\",");
            out.append("\"sessions\":[");

            boolean first = true;
            for (TasmiSession s : sessions) {
                if (s == null || s.getSessionDate() == null) {
                    continue;
                }
                if (s.getSessionDate().isBefore(from) || s.getSessionDate().isAfter(to)) {
                    continue;
                }
                if (!first) {
                    out.append(',');
                }
                first = false;
                appendSessionJson(out, s, request.getContextPath(), zone);
            }
            out.append("]}");

            try (PrintWriter writer = response.getWriter()) {
                writer.write(out.toString());
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to load instructor calendar feed", ex);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"server_error\"}");
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

    private static LocalDate parseDate(String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            return fallback;
        }
    }

    private static void appendSessionJson(StringBuilder out, TasmiSession s, String contextPath, ZoneId zone) {
        long startMs = -1;
        long endMs = -1;
        if (s.getSessionDate() != null && s.getSessionTime() != null) {
            LocalDateTime ldt = LocalDateTime.of(s.getSessionDate(), s.getSessionTime());
            startMs = ldt.atZone(zone).toInstant().toEpochMilli();
            int durationMinutes = s.getDurationMinutes() == null ? 60 : s.getDurationMinutes();
            endMs = startMs + (long) durationMinutes * 60_000L;
        }
        TasmiSessionStatus status = s.getStatus();
        out.append('{');
        out.append("\"id\":").append(s.getSessionId()).append(',');
        out.append("\"title\":").append(jsonString(s.getTitle())).append(',');
        out.append("\"portion\":").append(jsonString(s.getQuranPortion())).append(',');
        out.append("\"level\":").append(jsonString(s.getLevel() == null ? null : s.getLevel().name())).append(',');
        out.append("\"date\":").append(jsonString(s.getSessionDate() == null ? null : s.getSessionDate().toString())).append(',');
        out.append("\"time\":").append(jsonString(s.getSessionTime() == null ? null : formatTime(s.getSessionTime()))).append(',');
        out.append("\"durationMinutes\":").append(s.getDurationMinutes() == null ? 60 : s.getDurationMinutes()).append(',');
        out.append("\"capacity\":").append(s.getCapacity()).append(',');
        out.append("\"status\":").append(jsonString(status == null ? "SCHEDULED" : status.name())).append(',');
        out.append("\"startEpochMs\":").append(startMs).append(',');
        out.append("\"endEpochMs\":").append(endMs).append(',');
        out.append("\"manageHref\":").append(jsonString(contextPath + "/instructor/sessions"));
        out.append('}');
    }

    private static String formatTime(LocalTime t) {
        return String.format("%02d:%02d", t.getHour(), t.getMinute());
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
